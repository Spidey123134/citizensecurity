package com.example.citizensecurity.administration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.account.ReportAdministrationBinding
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.report.ReportStatusRecoveryState
import com.example.citizensecurity.report.ReportStatusRecoveryViewModel
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AdministrationStatusSnapshot(val report: Report, val sequence: Long) {
    val nextStatus: ReportStatus? get() = when (report.status) {
        ReportStatus.REPORTED -> ReportStatus.IN_REVIEW
        ReportStatus.IN_REVIEW -> ReportStatus.ATTENDED
        ReportStatus.ATTENDED -> ReportStatus.CLOSED
        ReportStatus.CLOSED -> null
    }
}

sealed interface AdministrationStatusChangeState {
    data object Idle : AdministrationStatusChangeState
    class Loading(val reportId: String) : AdministrationStatusChangeState
    class Editing(val snapshot: AdministrationStatusSnapshot, val reason: String = "", val message: String? = null) : AdministrationStatusChangeState {
        val validReason: Boolean get() = administrationReasonValid(reason)
    }
    class Reviewing(val snapshot: AdministrationStatusSnapshot, val reason: String) : AdministrationStatusChangeState
    class Missing(val reportId: String) : AdministrationStatusChangeState
    class Closed(val snapshot: AdministrationStatusSnapshot) : AdministrationStatusChangeState
    data object Invalid : AdministrationStatusChangeState
    class Error(val reportId: String, val message: String) : AdministrationStatusChangeState
    data object Denied : AdministrationStatusChangeState
}

/** Ephemeral preparation only: the durable recovery model owns every command and write. */
class AdministrationStatusChangeViewModel(
    private val reader: ReportAdministrationReader,
    private val policy: ReportAdministrationPolicy,
    private val checkCurrent: () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<AdministrationStatusChangeState>(AdministrationStatusChangeState.Idle)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var pending: Job? = null
    private var accessCheck: Job? = null
    private var closed = false

    fun loadFolio(text: String, expected: AdministrationStatusChangeState = mutableState.value): Boolean {
        if (!accept(expected)) return false
        val id = text.trim().lowercase(Locale.ROOT)
        if (!CANONICAL_ID.matches(id)) {
            retire()
            mutableState.value = AdministrationStatusChangeState.Invalid
            return false
        }
        if (expected is AdministrationStatusChangeState.Loading && expected.reportId == id) return false
        return read(id, result = { snapshot ->
            if (snapshot == null) AdministrationStatusChangeState.Missing(id) else stateFor(snapshot)
        })
    }

    fun setReason(expected: AdministrationStatusChangeState.Editing, reason: String): Boolean {
        if (!accept(expected)) return false
        mutableState.value = AdministrationStatusChangeState.Editing(expected.snapshot, reason)
        return true
    }

    /** Preparing a review rereads the revision; it never creates a durable request. */
    fun review(expected: AdministrationStatusChangeState.Editing): Boolean {
        if (!accept(expected) || !expected.validReason || expected.snapshot.nextStatus == null) return false
        return read(expected.snapshot.report.id, result = { fresh ->
            when {
                fresh == null -> AdministrationStatusChangeState.Missing(expected.snapshot.report.id)
                fresh.nextStatus == null -> AdministrationStatusChangeState.Closed(fresh)
                fresh != expected.snapshot -> AdministrationStatusChangeState.Editing(fresh, expected.reason, CHANGED_MESSAGE)
                else -> AdministrationStatusChangeState.Reviewing(fresh, expected.reason)
            }
        })
    }

    fun editReview(expected: AdministrationStatusChangeState.Reviewing): Boolean {
        if (!accept(expected)) return false
        mutableState.value = AdministrationStatusChangeState.Editing(expected.snapshot, expected.reason)
        return true
    }

    /** A second gesture rechecks authority/revision in IO before transferring the exact reviewed body. */
    fun confirmReviewed(
        expected: AdministrationStatusChangeState.Reviewing,
        recovery: ReportStatusRecoveryViewModel,
        canApply: () -> Boolean,
    ): Boolean {
        if (!accept(expected) || recovery.state.value !== ReportStatusRecoveryState.Ready || !canApply()) return false
        return read(expected.snapshot.report.id, result = { fresh ->
            when {
                fresh == null -> AdministrationStatusChangeState.Missing(expected.snapshot.report.id)
                fresh.nextStatus == null -> AdministrationStatusChangeState.Closed(fresh)
                fresh != expected.snapshot -> AdministrationStatusChangeState.Editing(fresh, expected.reason, CHANGED_MESSAGE)
                else -> expected
            }
        }, handoff = handoff@{ result ->
            if (result !== expected || recovery.state.value !== ReportStatusRecoveryState.Ready || !canApply()) return@handoff false
            checkLease()
            val snapshot = expected.snapshot
            val next = checkNotNull(snapshot.nextStatus)
            recovery.submit(snapshot.report.id, snapshot.sequence, snapshot.report.status, next, expected.reason)
        })
    }

    /** Resume checks remove private preparation if either capability or session was revoked. */
    fun checkAccess(): Boolean {
        if (!permitted() || accessCheck?.isActive == true) return false
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                withContext(dispatcher) { checkAdministration() }
                ensureActive()
                if (!closed) checkLease()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: AccessRevoked) { if (!closed) deny() }
        }
        accessCheck = job
        job.start()
        return true
    }

    fun cancel() {
        if (!closed) { retire(); mutableState.value = AdministrationStatusChangeState.Idle }
    }

    private fun accept(expected: AdministrationStatusChangeState): Boolean =
        permitted() && mutableState.value === expected

    private fun permitted(): Boolean {
        if (closed || !viewModelScope.isActive) return false
        return try { checkLease(); true } catch (_: AccessRevoked) { deny(); false }
    }

    private fun checkLease() {
        try { checkCurrent() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw AccessRevoked() }
    }

    private fun checkAdministration() {
        checkLease()
        for (permission in listOf(ReportAdministrationPermission.READ_REPORTS, ReportAdministrationPermission.CHANGE_STATUS)) {
            try { policy.check(reader.scope, permission) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw AccessRevoked() }
            checkLease()
        }
    }

    private suspend fun snapshot(reportId: String): AdministrationStatusSnapshot? {
        checkAdministration()
        val report = reader.findById(reportId)
        currentCoroutineContext().ensureActive()
        checkAdministration()
        if (report == null) return null
        require(report.id == reportId)
        val history = requireNotNull(reader.readHistory(ReportHistoryQuery(reportId, limit = 1)))
        currentCoroutineContext().ensureActive()
        checkAdministration()
        require(history.reportId == reportId && history.currentStatus == report.status)
        require(history.latestSequence < Long.MAX_VALUE)
        require(history.events.size <= 1)
        if (history.latestSequence == 0L) require(history.events.isEmpty() && !history.hasMore)
        else {
            val newest = history.events.single()
            require(newest.sequence == history.latestSequence && newest.toStatus == report.status)
            require(history.hasMore == (history.latestSequence > 1L))
        }
        require(reader.findById(reportId) == report)
        currentCoroutineContext().ensureActive()
        checkAdministration()
        // A second sequence read catches another transition while the report body was reread.
        val latest = requireNotNull(reader.readHistory(ReportHistoryQuery(reportId, limit = 1)))
        currentCoroutineContext().ensureActive()
        checkAdministration()
        require(latest.currentStatus == history.currentStatus && latest.latestSequence == history.latestSequence &&
            latest.events == history.events && latest.reportId == reportId)
        return AdministrationStatusSnapshot(report, history.latestSequence)
    }

    private fun read(
        reportId: String,
        result: (AdministrationStatusSnapshot?) -> AdministrationStatusChangeState,
        handoff: ((AdministrationStatusChangeState) -> Boolean)? = null,
    ): Boolean {
        if (!permitted()) return false
        val revision = ++generation
        pending?.cancel()
        val loading = AdministrationStatusChangeState.Loading(reportId)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val next = withContext(dispatcher) {
                    ensureActive()
                    val fresh = snapshot(reportId)
                    ensureActive()
                    checkAdministration()
                    result(fresh)
                }
                ensureActive()
                if (revision == generation && !closed) {
                    checkLease()
                    // This callback runs on Main, after all file/database permission checks in IO.
                    val submitted = handoff?.invoke(next) == true
                    mutableState.value = if (submitted) AdministrationStatusChangeState.Idle else next
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: AccessRevoked) { if (revision == generation && !closed) deny() }
            catch (_: ReportAdministrationPermissionDeniedException) { if (revision == generation && !closed) deny() }
            catch (_: Exception) {
                ensureActive()
                if (revision == generation && !closed) {
                    try {
                        withContext(dispatcher) { checkAdministration() }
                        ensureActive()
                        if (revision == generation && !closed) {
                            checkLease()
                            mutableState.value = AdministrationStatusChangeState.Error(reportId, READ_MESSAGE)
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: AccessRevoked) { if (revision == generation && !closed) deny() }
                }
            }
        }
        pending = job
        job.invokeOnCompletion { cause ->
            if (revision == generation && !closed) {
                pending = null
                if (cause is CancellationException && permitted()) {
                    mutableState.compareAndSet(loading, AdministrationStatusChangeState.Error(reportId, READ_MESSAGE))
                }
            }
        }
        mutableState.value = loading
        if (revision == generation && !closed) job.start() else job.cancel()
        return true
    }

    private fun stateFor(snapshot: AdministrationStatusSnapshot): AdministrationStatusChangeState = when {
        snapshot.nextStatus == null -> AdministrationStatusChangeState.Closed(snapshot)
        else -> AdministrationStatusChangeState.Editing(snapshot)
    }

    private fun retire() {
        ++generation
        pending?.cancel(); pending = null
        accessCheck?.cancel(); accessCheck = null
    }
    private fun deny() { closed = true; retire(); mutableState.value = AdministrationStatusChangeState.Denied }
    override fun onCleared() { deny() }
    private class AccessRevoked : IllegalStateException()

    companion object {
        private val CANONICAL_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private const val CHANGED_MESSAGE = "El reporte cambió mientras lo revisabas. Comprueba de nuevo el estado y el motivo antes de revisar otra solicitud."
        private const val READ_MESSAGE = "No se pudo verificar el estado actual. Vuelve a consultar este folio; no se guardó un cambio desde esta preparación."
        fun factory(binding: ReportAdministrationBinding, checkCurrent: () -> Unit,
            dispatcher: CoroutineDispatcher = Dispatchers.IO): ViewModelProvider.Factory = viewModelFactory {
            initializer { AdministrationStatusChangeViewModel(binding.reader, binding.policy, checkCurrent, dispatcher) }
        }
    }
}

internal fun administrationReasonValid(reason: String): Boolean {
    if (reason != reason.trim() || reason.codePointCount(0, reason.length) !in 5..500 ||
        reason.any { it == '\u0000' || Character.isISOControl(it) }) return false
    var index = 0
    while (index < reason.length) {
        val character = reason[index++]
        if (Character.isHighSurrogate(character)) {
            if (index >= reason.length || !Character.isLowSurrogate(reason[index++])) return false
        } else if (Character.isLowSurrogate(character)) return false
    }
    return true
}
