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
import com.example.citizensecurity.domain.ReportStatusConflictException
import com.example.citizensecurity.domain.ReportStatusNotFoundException
import com.example.citizensecurity.domain.ReportStatusPermissionDeniedException
import com.example.citizensecurity.domain.ReportAdministrationPermissionDeniedException
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
    data object Closed : ReportStatusRecoveryState
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
    class Rejected(
        val request: ReportStatusChangeRequest,
        val reason: ReportStatusRejectionReason,
        val message: String,
    ) : ReportStatusRecoveryState
    class Error(val retained: ReportStatusChangeRequest?, val message: String) : ReportStatusRecoveryState
}

enum class ReportStatusRejectionReason { CONFLICT, NOT_FOUND }

/**
 * Durable coordination for the explicitly authorized administrative flow.
 * Constructing a model neither reads storage nor changes a report.
 * Every recovery, retry and acknowledgement is a separate gesture with a live captured access.
 */
class ReportStatusRecoveryViewModel(
    private val journal: ReportStatusChangeJournal,
    private val access: ReportStatusChangeAccess,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.systemUTC(),
    private val operationId: () -> String = { UUID.randomUUID().toString() },
    private val checkSession: () -> Unit = {},
) : ViewModel() {
    private val scope = access.scope
    private val mutableState = MutableStateFlow<ReportStatusRecoveryState>(ReportStatusRecoveryState.AwaitingRecovery)
    val state: StateFlow<ReportStatusRecoveryState> = mutableState.asStateFlow()
    private val generation = AtomicLong()
    private val closed = AtomicBoolean()
    private val writerStarted = AtomicBoolean()
    private var pending: Job? = null
    @Volatile private var retained: ReportStatusChangeRequest? = null
    private var emptyVerified = false
    private var mayRetainMissing = false
    @Volatile private var acknowledgement: ReportStatusChangeReceipt? = null
    // A definitive exception is process-local evidence, never reconstructed from a missing receipt.
    @Volatile private var rejected: Rejection? = null
    @Volatile private var rejectedAcknowledgement: Rejection? = null

    /**
     * Checks the pure captured session immediately and schedules authority IO without blocking UI.
     * While that check owns the single-flight slot, gestures cannot begin another operation.
     * A running operation already rechecks authority around each IO boundary.
     */
    fun checkAccess(): Boolean {
        if (closed.get() || !viewModelScope.isActive) return false
        try {
            checkSession()
            check(access.scope == scope)
        } catch (_: Exception) {
            close()
            return false
        }
        if (pending != null) return true
        val token = generation.incrementAndGet()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                withContext(dispatcher) { checkpoint(token) }
                publicationCheckpoint(token)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                ensureActive()
                if (token == generation.get()) retireIfRevoked(token, failure)
            }
        }
        pending = job
        job.invokeOnCompletion { if (pending === job) pending = null }
        job.start()
        return true
    }

    /** Discards visible private data, without clearing the durable request or releasing a writer's lease. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        generation.incrementAndGet()
        emptyVerified = false
        mayRetainMissing = false
        retained = null
        acknowledgement = null
        rejected = null
        rejectedAcknowledgement = null
        mutableState.value = ReportStatusRecoveryState.Closed
        pending?.cancel()
    }

    /** Reads the journal and then an exact receipt. Never creates, changes or clears anything. */
    fun recover(expected: ReportStatusRecoveryState? = null): Boolean {
        if (!canBegin() || (expected != null && mutableState.value !== expected)) return false
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
                    saved == null && rejectedAcknowledgement != null -> {
                        val proof = checkNotNull(rejectedAcknowledgement)
                        if (local != proof.request) throw RecoveryMismatch()
                        val receipt = readReceipt(proof.request, token)
                        // A late receipt is evidence of a commit, even after a rejected clear.
                        if (receipt == null) Outcome.Empty else Outcome.Confirmed(proof.request, receipt)
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
                        when {
                            receipt != null -> Outcome.Confirmed(saved, receipt)
                            rejected?.request == saved -> Outcome.Rejected(checkNotNull(rejected))
                            else -> Outcome.Retained(saved)
                        }
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
            checkSession()
            check(access.scope == scope)
            ReportStatusChangeRequest(
                scope,
                ReportStatusChangeCommand(operationId(), reportId, expectedSequence, expectedStatus, targetStatus, reason),
                clock.instant(),
            )
        } catch (_: IllegalArgumentException) {
            if (!sessionCurrent()) close() else mutableState.value = ReportStatusRecoveryState.Invalid(INVALID_MESSAGE)
            return false
        } catch (failure: Exception) {
            emptyVerified = false
            if (failure.isPermissionDenial() || !sessionCurrent()) close()
            else mutableState.value = ReportStatusRecoveryState.Error(null, RECOVERY_MESSAGE)
            return false
        }
        retained = request
        acknowledgement = null
        rejected = null
        rejectedAcknowledgement = null
        writerStarted.set(false)
        emptyVerified = false
        // The empty read authorizes retaining this body, not replacing a concurrent body.
        mayRetainMissing = true
        return write(request)
    }

    /** Reuses all fields, including UUID, scope and requestedAt; an uncertain result needs recovery. */
    fun retry(expected: ReportStatusRecoveryState.Pending? = null): Boolean {
        if (!canBegin()) return false
        val state = mutableState.value as? ReportStatusRecoveryState.Pending ?: return false
        if (expected != null && state !== expected) return false
        if (state.requiresRecovery || state.request !== retained) return false
        return write(state.request)
    }

    /** A cancelled backend may still commit. The pending job and shared lease stay reserved. */
    fun cancel(): Boolean {
        if (closed.get() || !viewModelScope.isActive) return false
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
    fun acknowledgeConfirmed(expected: ReportStatusRecoveryState.Confirmed? = null): Boolean {
        if (!canBegin()) return false
        val confirmed = mutableState.value as? ReportStatusRecoveryState.Confirmed ?: return false
        if (expected != null && confirmed !== expected) return false
        if (confirmed.request !== retained) return false
        return begin(ReportStatusRecoveryState.Working(confirmed.request, acknowledging = true)) { token ->
            journal.exclusive {
                checkpoint(token)
                val saved = journal.read()
                checkpoint(token)
                val rejectedWasRetired = saved == null && rejectedAcknowledgement?.request == confirmed.request
                if (saved != confirmed.request && !rejectedWasRetired) throw RecoveryMismatch()
                val receipt = readReceipt(confirmed.request, token)
                if (receipt != confirmed.receipt) throw RecoveryMismatch()
                checkpoint(token)
                // Only this live explicit gesture can later resolve a lost clear acknowledgement.
                acknowledgement = confirmed.receipt
                if (saved != null) journal.clear(confirmed.request)
                checkpoint(token)
                Outcome.Empty
            }
        }
    }

    /**
     * A separate gesture retires a known rejected command. Rechecking the exact journal and receipt
     * under the shared lease is read only; this path never calls change, retry or generates a UUID.
     * The definitive exception proof is required in memory, not inferred from receipt absence.
     */
    fun acknowledgeRejected(expected: ReportStatusRecoveryState.Rejected? = null): Boolean {
        if (!canBegin()) return false
        val visible = mutableState.value as? ReportStatusRecoveryState.Rejected ?: return false
        if (expected != null && visible !== expected) return false
        val proof = rejected ?: return false
        if (visible.request !== retained || proof.request != visible.request || proof.reason != visible.reason) return false
        return begin(ReportStatusRecoveryState.Working(visible.request, acknowledging = true)) { token ->
            journal.exclusive {
                checkpoint(token)
                val saved = journal.read()
                checkpoint(token)
                if (saved != proof.request) throw RecoveryMismatch()
                val receipt = readReceipt(proof.request, token)
                if (receipt != null) return@exclusive Outcome.Confirmed(proof.request, receipt)
                checkpoint(token)
                rejectedAcknowledgement = proof
                journal.clear(proof.request)
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
                val event = try {
                    access.change(request)
                } catch (_: ReportStatusConflictException) {
                    return@exclusive verifyRejection(request, ReportStatusRejectionReason.CONFLICT, token)
                } catch (_: ReportStatusNotFoundException) {
                    return@exclusive verifyRejection(request, ReportStatusRejectionReason.NOT_FOUND, token)
                }
                checkpoint(token)
                val receipt = ReportStatusChangeReceipt(request.command.reportId, event)
                if (!receipt.matches(request)) throw RecoveryMismatch()
                Outcome.Confirmed(request, receipt)
            }
        }
    }

    private suspend fun verifyRejection(
        request: ReportStatusChangeRequest,
        reason: ReportStatusRejectionReason,
        token: Long,
    ): Outcome {
        checkpoint(token)
        if (journal.read() != request) throw RecoveryMismatch()
        checkpoint(token)
        // Even a typed error cannot hide a completed transaction. A fresh matching receipt wins.
        val receipt = readReceipt(request, token)
        return if (receipt != null) Outcome.Confirmed(request, receipt)
        else Outcome.Rejected(Rejection(request, reason))
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
        publicationCheckpoint(token)
        access.checkCurrent()
        publicationCheckpoint(token)
    }

    /** Main-safe session stamp only; authority files are read exclusively on dispatcher. */
    private suspend fun publicationCheckpoint(token: Long) {
        currentCoroutineContext().ensureActive()
        if (closed.get() || token != generation.get()) throw CancellationException("Solicitud retirada")
        checkSession()
        check(access.scope == scope)
    }

    private fun canBegin() = !closed.get() && viewModelScope.isActive && pending == null

    private fun begin(
        working: ReportStatusRecoveryState,
        block: suspend (Long) -> Outcome,
    ): Boolean {
        val token = generation.incrementAndGet()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome = withContext(dispatcher) { block(token).also { checkpoint(token) } }
                publicationCheckpoint(token)
                if (token == generation.get()) publish(outcome)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                ensureActive()
                if (token == generation.get() && !retireIfRevoked(token, failure)) {
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

    /** Unknown journal/SQL failures may retain a body only after checking live authority on IO. */
    private suspend fun retireIfRevoked(token: Long, failure: Exception): Boolean {
        if (failure.isPermissionDenial() || !sessionCurrent()) {
            close()
            return true
        }
        try {
            withContext(dispatcher) { checkpoint(token) }
            publicationCheckpoint(token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (recheck: Exception) {
            if (recheck.isPermissionDenial() || !sessionCurrent()) {
                close()
                return true
            }
            // A paused/locked window is transient. Preserve uncertainty while the captured session
            // remains live; only a later explicit recovery can resolve the durable command.
        }
        return false
    }

    private fun sessionCurrent(): Boolean = try {
        checkSession()
        access.scope == scope
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) { false }

    private fun Exception.isPermissionDenial(): Boolean =
        this is ReportStatusPermissionDeniedException || this is ReportAdministrationPermissionDeniedException

    private fun publish(outcome: Outcome) {
        when (outcome) {
            Outcome.Empty -> {
                retained = null
                acknowledgement = null
                rejected = null
                rejectedAcknowledgement = null
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
                rejected = null
                if (rejectedAcknowledgement?.request != outcome.request) rejectedAcknowledgement = null
                writerStarted.set(true)
                mutableState.value = ReportStatusRecoveryState.Confirmed(outcome.request, outcome.receipt)
            }
            is Outcome.Rejected -> {
                retained = outcome.proof.request
                rejected = outcome.proof
                writerStarted.set(true)
                mutableState.value = ReportStatusRecoveryState.Rejected(
                    outcome.proof.request, outcome.proof.reason,
                    when (outcome.proof.reason) {
                        ReportStatusRejectionReason.CONFLICT -> CONFLICT_MESSAGE
                        ReportStatusRejectionReason.NOT_FOUND -> NOT_FOUND_MESSAGE
                    },
                )
            }
        }
    }

    private sealed interface Outcome {
        data object Empty : Outcome
        class MissingBeforeWriter(val request: ReportStatusChangeRequest) : Outcome
        class Retained(val request: ReportStatusChangeRequest) : Outcome
        class Confirmed(val request: ReportStatusChangeRequest, val receipt: ReportStatusChangeReceipt) : Outcome
        class Rejected(val proof: Rejection) : Outcome
    }

    private data class Rejection(val request: ReportStatusChangeRequest, val reason: ReportStatusRejectionReason)

    private class RecoveryMismatch : IllegalStateException()

    companion object {
        private const val INVALID_MESSAGE =
            "Revisa el folio, la revisión, el estado consecutivo y el motivo de 5 a 500 caracteres."
        private const val RECOVERY_MESSAGE =
            "No se pudo verificar el resultado. Conservamos la solicitud; recupera su recibo antes de continuar."
        private const val RETRY_MESSAGE =
            "La solicitud conserva su identidad y aún no tiene un recibo verificado. Puedes reintentar el mismo cambio."
        private const val CONFLICT_MESSAGE =
            "El reporte cambió antes de aplicar esta solicitud. Cierra la solicitud rechazada y consulta de nuevo su estado."
        private const val NOT_FOUND_MESSAGE =
            "Ese folio no existe en este espacio. Cierra la solicitud rechazada antes de preparar otro cambio."

        fun factory(
            journal: ReportStatusChangeJournal,
            access: ReportStatusChangeAccess,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            clock: Clock = Clock.systemUTC(),
            checkSession: () -> Unit = {},
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportStatusRecoveryViewModel(journal, access, dispatcher, clock, checkSession = checkSession) }
        }
    }
}
