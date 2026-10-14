package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportStatusChangeAccess
import com.example.citizensecurity.domain.ReportStatusChangeCommand
import com.example.citizensecurity.domain.ReportStatusChangeJournal
import com.example.citizensecurity.domain.ReportStatusChangeReceipt
import com.example.citizensecurity.domain.ReportStatusChangeRequest
import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReportStatusRecoveryState {
    data object AwaitingRecovery : ReportStatusRecoveryState
    class Recovering(val retained: ReportStatusChangeRequest?) : ReportStatusRecoveryState
    data object Ready : ReportStatusRecoveryState
    class Invalid(val message: String) : ReportStatusRecoveryState
    class Working(val request: ReportStatusChangeRequest, val acknowledging: Boolean = false) : ReportStatusRecoveryState
    class Pending(
        val request: ReportStatusChangeRequest,
        val requiresRecovery: Boolean,
        val message: String,
    ) : ReportStatusRecoveryState
    class Confirmed(val request: ReportStatusChangeRequest, val receipt: ReportStatusChangeReceipt) : ReportStatusRecoveryState
    class Error(val retained: ReportStatusChangeRequest?, val message: String) : ReportStatusRecoveryState
}

/**
 * Durable coordination prepared for a future authorized administrative flow. It is not composed
 * into the application. Constructing a model neither reads storage nor changes a report.
 * Every recovery, retry and acknowledgement is a separate gesture with a live captured access.
 */
class ReportStatusRecoveryViewModel(
    private val journal: ReportStatusChangeJournal,
    private val access: ReportStatusChangeAccess,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.systemUTC(),
    private val operationId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val scope = access.scope
    private val mutableState = MutableStateFlow<ReportStatusRecoveryState>(ReportStatusRecoveryState.AwaitingRecovery)
    val state: StateFlow<ReportStatusRecoveryState> = mutableState.asStateFlow()
    private val generation = AtomicLong()
    private val writerStarted = AtomicBoolean()
    private var pending: Job? = null
    @Volatile private var retained: ReportStatusChangeRequest? = null
    private var emptyVerified = false
    private var mayRetainMissing = false
    @Volatile private var acknowledgement: ReportStatusChangeReceipt? = null

    /** Reads the journal and then an exact receipt. Never creates, changes or clears anything. */
    fun recover(): Boolean {
        if (!canBegin()) return false
        emptyVerified = false
        mayRetainMissing = false
        val local = retained
        return begin(ReportStatusRecoveryState.Recovering(local)) { token ->
            journal.exclusive {
                checkpoint(token)
                val saved = journal.read()
                checkpoint(token)
                when {
                    saved == null && local == null -> Outcome.Empty
                    saved == null && acknowledgement != null -> {
                        val request = checkNotNull(local)
                        val expected = checkNotNull(acknowledgement)
                        val actual = readReceipt(request, token)
                        if (actual != expected) throw RecoveryMismatch()
                        Outcome.Empty
                    }
                    saved == null && !writerStarted.get() -> Outcome.MissingBeforeWriter(checkNotNull(local))
                    saved == null -> throw RecoveryMismatch()
                    saved.scope != scope || (local != null && saved != local) -> throw RecoveryMismatch()
                    else -> {
                        // Keep a validated body even if receipt reading or the old coroutine is
                        // cancelled. An empty later read must not erase an unresolved identity.
                        if (retained == null) {
                            retained = saved
                            writerStarted.set(true)
                        }
                        val receipt = readReceipt(saved, token)
                        if (receipt == null) Outcome.Retained(saved) else Outcome.Confirmed(saved, receipt)
                    }
                }
            }
        }
    }

    /** A successful explicit empty read is required before accepting a new immutable command. */
    fun submit(
        reportId: String,
        expectedSequence: Long,
        expectedStatus: ReportStatus,
        targetStatus: ReportStatus,
        reason: String,
    ): Boolean {
        if (!canBegin() || !emptyVerified || retained != null) return false
        val request = try {
            access.checkCurrent()
            check(access.scope == scope)
            ReportStatusChangeRequest(
                scope,
                ReportStatusChangeCommand(operationId(), reportId, expectedSequence, expectedStatus, targetStatus, reason),
                clock.instant(),
            )
        } catch (_: IllegalArgumentException) {
            mutableState.value = ReportStatusRecoveryState.Invalid(INVALID_MESSAGE)
            return false
        } catch (_: Exception) {
            emptyVerified = false
            mutableState.value = ReportStatusRecoveryState.Error(null, RECOVERY_MESSAGE)
            return false
        }
        retained = request
        acknowledgement = null
        writerStarted.set(false)
        emptyVerified = false
        // The empty read authorizes retaining this body, not replacing a concurrent body.
        mayRetainMissing = true
        return write(request)
    }

    /** Reuses all fields, including UUID, scope and requestedAt; an uncertain result needs recovery. */
    fun retry(): Boolean {
        if (!canBegin()) return false
        val state = mutableState.value as? ReportStatusRecoveryState.Pending ?: return false
        if (state.requiresRecovery || state.request !== retained) return false
        return write(state.request)
    }

    /** A cancelled backend may still commit. The pending job and shared lease stay reserved. */
    fun cancel(): Boolean {
        if (!viewModelScope.isActive) return false
        val job = pending ?: return false
        generation.incrementAndGet()
        emptyVerified = false
        mayRetainMissing = false
        mutableState.value = retained?.let {
            ReportStatusRecoveryState.Pending(it, true, RECOVERY_MESSAGE)
        } ?: ReportStatusRecoveryState.Error(null, RECOVERY_MESSAGE)
        job.cancel()
        return true
    }

    /** Clears only after re-reading the complete same request and the complete same confirmed event. */
    fun acknowledgeConfirmed(): Boolean {
        if (!canBegin()) return false
        val confirmed = mutableState.value as? ReportStatusRecoveryState.Confirmed ?: return false
        if (confirmed.request !== retained) return false
        return begin(ReportStatusRecoveryState.Working(confirmed.request, acknowledging = true)) { token ->
            journal.exclusive {
                checkpoint(token)
                val saved = journal.read()
                checkpoint(token)
                if (saved != confirmed.request) throw RecoveryMismatch()
                val receipt = readReceipt(confirmed.request, token)
                if (receipt != confirmed.receipt) throw RecoveryMismatch()
                checkpoint(token)
                // Only this live explicit gesture can later resolve a lost clear acknowledgement.
                acknowledgement = confirmed.receipt
                journal.clear(confirmed.request)
                checkpoint(token)
                Outcome.Empty
            }
        }
    }

    private fun write(request: ReportStatusChangeRequest): Boolean {
        val allowMissing = mayRetainMissing && !writerStarted.get()
        mayRetainMissing = false
        return begin(ReportStatusRecoveryState.Working(request)) { token ->
            journal.exclusive {
                checkpoint(token)
                val saved = journal.read()
                checkpoint(token)
                if (saved != null && saved != request) throw RecoveryMismatch()
                if (saved == null && !allowMissing) throw RecoveryMismatch()
                if (saved == null) {
                    journal.retain(request)
                    checkpoint(token)
                    // Do not enter SQL if retention did not produce the complete exact body.
                    if (journal.read() != request) throw RecoveryMismatch()
                    checkpoint(token)
                }
                val existing = readReceipt(request, token)
                if (existing != null) return@exclusive Outcome.Confirmed(request, existing)
                checkpoint(token)
                writerStarted.set(true)
                val event = access.change(request)
                checkpoint(token)
                val receipt = ReportStatusChangeReceipt(request.command.reportId, event)
                if (!receipt.matches(request)) throw RecoveryMismatch()
                Outcome.Confirmed(request, receipt)
            }
        }
    }

    private suspend fun readReceipt(request: ReportStatusChangeRequest, token: Long): ReportStatusChangeReceipt? {
        check(request.scope == scope)
        checkpoint(token)
        val receipt = access.findReceipt(request)
        checkpoint(token)
        if (receipt != null && !receipt.matches(request)) throw RecoveryMismatch()
        return receipt
    }

    private suspend fun checkpoint(token: Long) {
        currentCoroutineContext().ensureActive()
        if (token != generation.get()) throw CancellationException("Solicitud retirada")
        access.checkCurrent()
        check(access.scope == scope)
    }

    private fun canBegin() = viewModelScope.isActive && pending == null

    private fun begin(
        working: ReportStatusRecoveryState,
        block: suspend (Long) -> Outcome,
    ): Boolean {
        val token = generation.incrementAndGet()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome = withContext(dispatcher) { block(token) }
                checkpoint(token)
                if (token == generation.get()) publish(outcome)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                if (token == generation.get()) {
                    emptyVerified = false
                    mayRetainMissing = false
                    mutableState.value = retained?.let {
                        ReportStatusRecoveryState.Pending(it, true, RECOVERY_MESSAGE)
                    } ?: ReportStatusRecoveryState.Error(null, RECOVERY_MESSAGE)
                }
            }
        }
        // Reserve before exposing Working/Recovering: a synchronous observer cannot start twice.
        pending = job
        job.invokeOnCompletion { cause ->
            if (pending === job) pending = null
            if (token == generation.get() && cause is CancellationException) {
                emptyVerified = false
                mayRetainMissing = false
                mutableState.compareAndSet(working, retained?.let {
                    ReportStatusRecoveryState.Pending(it, true, RECOVERY_MESSAGE)
                } ?: ReportStatusRecoveryState.Error(null, RECOVERY_MESSAGE))
            }
        }
        mutableState.value = working
        if (token == generation.get()) job.start() else job.cancel()
        return true
    }

    private fun publish(outcome: Outcome) {
        when (outcome) {
            Outcome.Empty -> {
                retained = null
                acknowledgement = null
                writerStarted.set(false)
                mayRetainMissing = false
                emptyVerified = true
                mutableState.value = ReportStatusRecoveryState.Ready
            }
            is Outcome.MissingBeforeWriter -> {
                retained = outcome.request
                mayRetainMissing = true
                mutableState.value = ReportStatusRecoveryState.Pending(outcome.request, false, RETRY_MESSAGE)
            }
            is Outcome.Retained -> {
                retained = outcome.request
                // A reconstructed model cannot assume the former writer never ran.
                writerStarted.set(true)
                mutableState.value = ReportStatusRecoveryState.Pending(outcome.request, false, RETRY_MESSAGE)
            }
            is Outcome.Confirmed -> {
                retained = outcome.request
                writerStarted.set(true)
                mutableState.value = ReportStatusRecoveryState.Confirmed(outcome.request, outcome.receipt)
            }
        }
    }

    private sealed interface Outcome {
        data object Empty : Outcome
        class MissingBeforeWriter(val request: ReportStatusChangeRequest) : Outcome
        class Retained(val request: ReportStatusChangeRequest) : Outcome
        class Confirmed(val request: ReportStatusChangeRequest, val receipt: ReportStatusChangeReceipt) : Outcome
    }

    private class RecoveryMismatch : IllegalStateException()

    companion object {
        private const val INVALID_MESSAGE =
            "Revisa el folio, la revisión, el estado consecutivo y el motivo de 5 a 500 caracteres."
        private const val RECOVERY_MESSAGE =
            "No se pudo verificar el resultado. Conservamos la solicitud; recupera su recibo antes de continuar."
        private const val RETRY_MESSAGE =
            "La solicitud conserva su identidad y aún no tiene un recibo verificado. Puedes reintentar el mismo cambio."

        fun factory(
            journal: ReportStatusChangeJournal,
            access: ReportStatusChangeAccess,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            clock: Clock = Clock.systemUTC(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportStatusRecoveryViewModel(journal, access, dispatcher, clock) }
        }
    }
}
