package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportStatusChangeCommand
import com.example.citizensecurity.domain.ReportStatusConflictException
import com.example.citizensecurity.domain.ReportStatusEvent
import com.example.citizensecurity.domain.ReportStatusEventKind
import com.example.citizensecurity.domain.ReportStatusNotFoundException
import com.example.citizensecurity.domain.ReportStatusOperationConflictException
import com.example.citizensecurity.domain.ReportStatusPermissionDeniedException
import com.example.citizensecurity.domain.ReportStatusRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReportStatusChangeRejection { PERMISSION, REVISION, NOT_FOUND, OPERATION }

sealed interface ReportStatusChangeState {
    data object Idle : ReportStatusChangeState
    class Invalid(val message: String) : ReportStatusChangeState
    class Writing(val command: ReportStatusChangeCommand) : ReportStatusChangeState
    class Confirmed(val command: ReportStatusChangeCommand, val event: ReportStatusEvent) : ReportStatusChangeState
    class Pending(val command: ReportStatusChangeCommand, val message: String) : ReportStatusChangeState
    class Rejected(
        val command: ReportStatusChangeCommand,
        val reason: ReportStatusChangeRejection,
        val receiptUncertain: Boolean,
        val message: String,
    ) : ReportStatusChangeState
}

/**
 * Preparation for an explicitly authorized writer; no factory is connected to the application.
 * The command survives rotation, but has no durable journal yet. Do not activate a writing UI
 * until recovery after process death and the administrative identity are implemented.
 */
class ReportStatusChangeViewModel(
    private val repository: ReportStatusRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val operationId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportStatusChangeState>(ReportStatusChangeState.Idle)
    val state: StateFlow<ReportStatusChangeState> = mutableState.asStateFlow()
    private var command: ReportStatusChangeCommand? = null
    private var pending: Job? = null
    private var generation = 0L
    private var receiptUncertain = false

    /** Accepts a reviewed revision once. Another gesture cannot replace an unresolved command. */
    fun submit(
        reportId: String,
        expectedSequence: Long,
        expectedStatus: ReportStatus,
        targetStatus: ReportStatus,
        reason: String,
    ): Boolean {
        if (!canBegin() || command != null) return false
        val next = try {
            ReportStatusChangeCommand(operationId(), reportId, expectedSequence, expectedStatus, targetStatus, reason)
        } catch (_: IllegalArgumentException) {
            mutableState.value = ReportStatusChangeState.Invalid(
                "Revisa el folio, la revisión, el estado consecutivo y el motivo de 5 a 500 caracteres.",
            )
            return false
        }
        command = next
        receiptUncertain = false
        write(next)
        return true
    }

    /** Reuses the complete command and UUID; never adopts another revision or reason. */
    fun retry(): Boolean {
        if (!canBegin() || mutableState.value is ReportStatusChangeState.Confirmed) return false
        val retained = command ?: return false
        write(retained)
        return true
    }

    /** Cancellation does not prove rollback. Keep the identity and wait for the old job to finish. */
    fun cancel(): Boolean {
        if (!viewModelScope.isActive) return false
        val job = pending ?: return false
        val retained = command ?: return false
        ++generation
        receiptUncertain = true
        mutableState.value = ReportStatusChangeState.Pending(retained, UNCERTAIN_MESSAGE)
        // A non-cooperative writer can still be committing; it keeps the single-flight reservation.
        job.cancel()
        return true
    }

    /** A definitive initial rejection permits a newly reviewed request; uncertainty never does. */
    fun discardRejected(): Boolean {
        if (!canBegin() || receiptUncertain) return false
        val rejected = mutableState.value as? ReportStatusChangeState.Rejected ?: return false
        if (rejected.receiptUncertain) return false
        reset()
        return true
    }

    /** Explicitly consumes a matching receipt; a retry cannot create a second transition. */
    fun acknowledgeConfirmed(): Boolean {
        if (!canBegin() || mutableState.value !is ReportStatusChangeState.Confirmed) return false
        reset()
        return true
    }

    private fun canBegin() = viewModelScope.isActive && pending == null

    private fun reset() {
        ++generation
        command = null
        receiptUncertain = false
        mutableState.value = ReportStatusChangeState.Idle
    }

    private fun write(retained: ReportStatusChangeCommand) {
        val request = ++generation
        val writing = ReportStatusChangeState.Writing(retained)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val event = withContext(dispatcher) { repository.changeStatus(retained) }
                ensureActive()
                requireMatchingReceipt(retained, event)
                if (request == generation) {
                    receiptUncertain = false
                    mutableState.value = ReportStatusChangeState.Confirmed(retained, event)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ReportStatusPermissionDeniedException) {
                reject(request, retained, ReportStatusChangeRejection.PERMISSION,
                    "No tienes permiso para cambiar el estado de este reporte.")
            } catch (_: ReportStatusConflictException) {
                reject(request, retained, ReportStatusChangeRejection.REVISION,
                    "El reporte cambió. Consulta de nuevo su estado antes de preparar otra solicitud.")
            } catch (_: ReportStatusNotFoundException) {
                reject(request, retained, ReportStatusChangeRejection.NOT_FOUND,
                    "Ese folio no está guardado en este espacio.")
            } catch (_: ReportStatusOperationConflictException) {
                // A UUID conflict does not establish the fate of this attempted operation.
                if (request == generation) receiptUncertain = true
                reject(request, retained, ReportStatusChangeRejection.OPERATION,
                    "No se pudo verificar la identidad de esta operación. Conservamos su solicitud.")
            } catch (_: Exception) {
                ensureActive()
                if (request == generation) {
                    receiptUncertain = true
                    mutableState.value = ReportStatusChangeState.Pending(retained, UNCERTAIN_MESSAGE)
                }
            }
        }
        // Reserve before emitting: observers may cancel or send another gesture synchronously.
        pending = job
        job.invokeOnCompletion { cause ->
            if (pending === job) pending = null
            if (request == generation && cause is CancellationException) {
                receiptUncertain = true
                mutableState.compareAndSet(writing, ReportStatusChangeState.Pending(retained, UNCERTAIN_MESSAGE))
            }
        }
        mutableState.value = writing
        if (request == generation) job.start() else job.cancel()
    }

    private suspend fun reject(
        request: Long,
        retained: ReportStatusChangeCommand,
        reason: ReportStatusChangeRejection,
        message: String,
    ) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (request == generation) mutableState.value = ReportStatusChangeState.Rejected(
            retained, reason, receiptUncertain, if (receiptUncertain) UNCERTAIN_MESSAGE else message,
        )
    }

    private fun requireMatchingReceipt(command: ReportStatusChangeCommand, event: ReportStatusEvent) {
        require(event.operationId == command.operationId && event.sequence == command.expectedSequence + 1L &&
            event.kind == ReportStatusEventKind.STATUS_CHANGED && event.fromStatus == command.expectedStatus &&
            event.toStatus == command.targetStatus && event.reason == command.reason && event.actorAccountId != null)
    }

    companion object {
        private const val UNCERTAIN_MESSAGE =
            "El cambio pudo haberse guardado. Conservamos la misma solicitud; verifica o reintenta su resultado antes de iniciar otro cambio."

        fun factory(
            repository: ReportStatusRepository,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportStatusChangeViewModel(repository, dispatcher) }
        }
    }
}
