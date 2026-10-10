package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.IdempotentReportRepository
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportSubmissionJournal
import com.example.citizensecurity.domain.ReportSubmissionRequest
import com.example.citizensecurity.domain.ReportValidator
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ReportSubmissionState {
    data object Idle : ReportSubmissionState
    data object Restoring : ReportSubmissionState
    data class Pending(val request: ReportSubmissionRequest) : ReportSubmissionState
    data class Saving(val request: ReportSubmissionRequest) : ReportSubmissionState
    data class Saved(val report: Report) : ReportSubmissionState
    data class Invalid(val errors: Map<ReportField, String>, val draft: NewReport? = null) : ReportSubmissionState
    data class Error(val message: String, val request: ReportSubmissionRequest? = null) : ReportSubmissionState
}

/**
 * Retiene el mismo folio antes de insertar y ante una respuesta incierta, incluso si muere el
 * proceso. Recuperar solo lee; reintentar exige un gesto y vuelve a evaluar el guard GPS para
 * una inserción nueva. Una fila ya confirmada se recupera sin capturar GPS ni insertar otra.
 */
class ReportSubmissionViewModel(
    private val repository: IdempotentReportRepository,
    private val journal: ReportSubmissionJournal,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportSubmissionState>(ReportSubmissionState.Restoring)
    val state: StateFlow<ReportSubmissionState> = mutableState.asStateFlow()
    private var request: ReportSubmissionRequest? = null
    private var activeJob: Job? = null
    private var recoveryComplete = false
    private var generation = 0L

    init {
        recover()
    }

    /** Lectura del diario y del folio; nunca inicia guardado o ubicación automáticamente. */
    fun recover() {
        if (!canBegin()) return
        if (mutableState.value is ReportSubmissionState.Saved) return
        launchOperation(ReportSubmissionState.Restoring) {
            recoveryComplete = false
            val persisted = journal.read()
            ensureActive()
            request = persisted
            if (persisted == null) {
                recoveryComplete = true
                mutableState.value = ReportSubmissionState.Idle
            } else {
                val report = repository.findById(persisted.requestId)
                ensureActive()
                if (report != null) {
                    check(persisted.matches(report)) {
                        "El folio guardado pertenece a datos distintos."
                    }
                    recoveryComplete = true
                    mutableState.value = ReportSubmissionState.Saved(report)
                } else {
                    recoveryComplete = true
                    mutableState.value = ReportSubmissionState.Pending(persisted)
                }
            }
        }
    }

    fun save(draft: NewReport) {
        if (!canBegin() || !recoveryComplete || mutableState.value is ReportSubmissionState.Saved) return
        val previous = request
        val normalized = draft.normalized()
        if (previous != null) {
            if (previous.draft != normalized) {
                mutableState.value = ReportSubmissionState.Error(
                    "El resultado anterior está pendiente. Recupera o reintenta los mismos datos antes de iniciar otro reporte.",
                    previous,
                )
                return
            }
            submit(previous)
            return
        }
        val errors = ReportValidator().validate(normalized, clock.instant())
        if (!errors.isValid) {
            mutableState.value = ReportSubmissionState.Invalid(errors.errors.toMap(), normalized)
            return
        }
        val next = ReportSubmissionRequest(UUID.randomUUID().toString(), normalized, clock.instant())
        request = next
        submit(next)
    }

    /** Reutiliza el UUID y todos los campos retenidos; no acepta otro borrador. */
    fun retryPending() {
        if (!canBegin() || !recoveryComplete || mutableState.value is ReportSubmissionState.Saved) return
        request?.let(::submit) ?: recover()
    }

    /**
     * La pantalla confirma que ya transfirió los campos rechazados al borrador editable.
     * Conserva los errores pero consume el snapshot para que girar la pantalla no vuelva
     * a sustituir las correcciones nuevas del usuario por el contenido del intento anterior.
     * No toca el diario, un intento pendiente ni un resultado confirmado.
     */
    fun acknowledgeCorrectionDraft(): Boolean {
        if (!viewModelScope.isActive) return false
        val previous = mutableState.value as? ReportSubmissionState.Invalid ?: return false
        if (previous.draft == null) return false
        return mutableState.compareAndSet(previous, previous.copy(draft = null))
    }

    /** Solo un resultado confirmado permite retirar su diario y empezar un reporte distinto. */
    fun startNewReport(): Boolean {
        if (!canBegin()) return false
        val saved = mutableState.value as? ReportSubmissionState.Saved ?: return false
        val previous = request ?: return false
        launchOperation(ReportSubmissionState.Restoring, onError = {
            // Sigue mostrando el folio confirmado y permite volver a pulsar Nuevo reporte.
            mutableState.value = saved
        }) {
            journal.clear(previous.requestId)
            ensureActive()
            request = null
            recoveryComplete = true
            mutableState.value = ReportSubmissionState.Idle
        }
        return true
    }

    private fun submit(pending: ReportSubmissionRequest) {
        launchOperation(ReportSubmissionState.Saving(pending)) {
            // Si esta escritura falla o es cancelada, no llega a createOnce.
            journal.write(pending)
            ensureActive()
            try {
                val report = repository.createOnce(pending.requestId, pending.draft)
                ensureActive()
                check(pending.matches(report)) { "El repositorio devolvió otro contenido o folio." }
                mutableState.value = ReportSubmissionState.Saved(report)
            } catch (invalid: InvalidReportException) {
                ensureActive()
                // El contrato createOnce rechaza antes de confirmar, o revierte su transacción.
                // No hubo fila: permite corregir campos/punto y renovar GPS por un nuevo gesto.
                journal.clear(pending.requestId)
                ensureActive()
                request = null
                mutableState.value = ReportSubmissionState.Invalid(invalid.errors.errors.toMap(), pending.draft)
            }
        }
    }

    private fun canBegin() = viewModelScope.isActive && activeJob == null

    private fun launchOperation(
        initial: ReportSubmissionState,
        onError: (() -> Unit)? = null,
        operation: suspend CoroutineScope.() -> Unit,
    ) {
        val token = ++generation
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                if (onError != null) onError() else mutableState.value = ReportSubmissionState.Error(
                    if (recoveryComplete) {
                        "No se pudo completar el guardado. Conservamos la solicitud; recupera o reintenta su resultado."
                    } else {
                        "No se pudo recuperar la solicitud anterior. Reintenta la recuperación antes de guardar otro reporte."
                    },
                    request,
                )
            }
        }
        activeJob = job
        job.invokeOnCompletion { cause ->
            if (generation == token) {
                activeJob = null
                if (cause is CancellationException && mutableState.value !is ReportSubmissionState.Saved) {
                    mutableState.value = request?.let { ReportSubmissionState.Pending(it) }
                        ?: ReportSubmissionState.Error("La recuperación se interrumpió. Vuelve a recuperar antes de guardar.")
                }
            }
        }
        // Reservar el job antes de emitir protege contra un gesto reentrante al recibir Saving.
        mutableState.value = initial
        job.start()
    }

    private fun NewReport.normalized() = copy(
        description = description.trim(),
        location = location.copy(
            reference = location.reference.trim(),
            latitude = location.latitude?.let { if (it == 0.0) 0.0 else it },
            longitude = location.longitude?.let { if (it == 0.0) 0.0 else it },
        ),
    )

    companion object {
        fun factory(
            repository: IdempotentReportRepository,
            journal: ReportSubmissionJournal,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportSubmissionViewModel(repository, journal) }
        }
    }
}
