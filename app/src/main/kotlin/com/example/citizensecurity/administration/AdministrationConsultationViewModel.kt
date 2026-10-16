package com.example.citizensecurity.administration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.account.ReportAdministrationBinding
import com.example.citizensecurity.domain.*
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AdministrationConsultationRequest

/** Request identities also protect queued UI gestures. Only cursors, never previous rows, are retained. */
class AdministrationPageRequest internal constructor(
    val query: ReportListPageQuery,
    internal val previous: List<ReportListPageQuery> = emptyList(),
) : AdministrationConsultationRequest

class AdministrationReportRequest internal constructor(
    val reportId: String,
    val historyQuery: ReportHistoryQuery,
    internal val previous: List<ReportHistoryQuery> = emptyList(),
) : AdministrationConsultationRequest

sealed interface AdministrationConsultationState {
    data object Idle : AdministrationConsultationState
    class Loading(val request: AdministrationConsultationRequest) : AdministrationConsultationState
    class ReadyPage(val request: AdministrationPageRequest, val page: ReportListPage,
        val canPrevious: Boolean) : AdministrationConsultationState
    class ReadyReport(val request: AdministrationReportRequest, val report: Report,
        val history: ReportHistoryPage, val canPreviousHistory: Boolean) : AdministrationConsultationState
    class Missing(val request: AdministrationReportRequest) : AdministrationConsultationState
    data object Invalid : AdministrationConsultationState
    class Error(val request: AdministrationConsultationRequest, val message: String) : AdministrationConsultationState
    data object Denied : AdministrationConsultationState
}

/** One live READ_REPORTS lease. This model has no writer, photo reader or notification capability. */
class AdministrationConsultationViewModel(
    private val reader: ReportAdministrationReader,
    private val policy: ReportAdministrationPolicy,
    private val checkCurrent: () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<AdministrationConsultationState>(AdministrationConsultationState.Idle)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var pending: Job? = null
    private var accessCheck: Job? = null
    private var closed = false
    private var filters = ReportListPageQuery(limit = PAGE_SIZE)

    fun activate(): Boolean = if (mutableState.value === AdministrationConsultationState.Idle) {
        refresh(AdministrationConsultationState.Idle)
    } else false

    fun setFilters(type: IncidentType?, status: ReportStatus?, priority: Priority?,
        expected: AdministrationConsultationState = mutableState.value): Boolean {
        if (!accept(expected)) return false
        val next = ReportListPageQuery(type = type, status = status, priority = priority, limit = PAGE_SIZE)
        val loading = mutableState.value as? AdministrationConsultationState.Loading
        if (filters == next && (loading?.request as? AdministrationPageRequest)?.query == next) return false
        filters = next
        return read(AdministrationPageRequest(next))
    }

    /** Refresh starts the current list/history at its beginning; retry alone preserves a failed cursor. */
    fun refresh(expected: AdministrationConsultationState): Boolean {
        if (!accept(expected)) return false
        val request = requestOf(expected)
        val fresh = if (request is AdministrationReportRequest) {
            AdministrationReportRequest(request.reportId, ReportHistoryQuery(request.reportId, limit = PAGE_SIZE))
        } else AdministrationPageRequest(filters.copy(before = null))
        if (expected is AdministrationConsultationState.Loading && samePosition(request, fresh)) return false
        return read(fresh)
    }

    fun loadFolio(text: String, expected: AdministrationConsultationState = mutableState.value): Boolean {
        if (!accept(expected)) return false
        val id = text.trim().lowercase(Locale.ROOT)
        if (!CANONICAL_ID.matches(id)) {
            retire()
            mutableState.value = AdministrationConsultationState.Invalid
            return false
        }
        val loading = mutableState.value as? AdministrationConsultationState.Loading
        if ((loading?.request as? AdministrationReportRequest)?.let {
                it.reportId == id && it.historyQuery.beforeSequence == null
            } == true) return false
        return read(AdministrationReportRequest(id, ReportHistoryQuery(id, limit = PAGE_SIZE)))
    }

    fun nextPage(expected: AdministrationConsultationState.ReadyPage): Boolean {
        if (!accept(expected) || !expected.page.hasMore) return false
        val last = expected.page.reports.lastOrNull() ?: return false
        return read(AdministrationPageRequest(expected.request.query.copy(before = ReportListCursor.from(last)),
            (expected.request.previous + expected.request.query).takeLast(MAX_PREVIOUS)))
    }

    fun previousPage(expected: AdministrationConsultationState.ReadyPage): Boolean {
        if (!accept(expected)) return false
        val previous = expected.request.previous.lastOrNull() ?: return false
        return read(AdministrationPageRequest(previous.copy(), expected.request.previous.dropLast(1)))
    }

    fun openReport(expected: AdministrationConsultationState.ReadyPage, reportId: String): Boolean {
        if (!accept(expected) || expected.page.reports.none { it.id == reportId }) return false
        return loadFolio(reportId, expected)
    }

    fun nextHistory(expected: AdministrationConsultationState.ReadyReport): Boolean {
        if (!accept(expected)) return false
        val before = expected.history.nextBeforeSequence ?: return false
        return read(AdministrationReportRequest(expected.report.id,
            expected.request.historyQuery.copy(beforeSequence = before),
            (expected.request.previous + expected.request.historyQuery).takeLast(MAX_PREVIOUS)))
    }

    fun previousHistory(expected: AdministrationConsultationState.ReadyReport): Boolean {
        if (!accept(expected)) return false
        val previous = expected.request.previous.lastOrNull() ?: return false
        return read(AdministrationReportRequest(expected.report.id, previous.copy(), expected.request.previous.dropLast(1)))
    }

    fun backToList(expected: AdministrationConsultationState): Boolean {
        if (!accept(expected) || requestOf(expected) !is AdministrationReportRequest) return false
        return read(AdministrationPageRequest(filters.copy(before = null)))
    }

    fun retry(expected: AdministrationConsultationState.Error): Boolean =
        if (accept(expected)) read(expected.request) else false

    /** Resume/revocation checks do not navigate, restore data or silently start a database reading. */
    fun checkAccess(): Boolean {
        if (!permitted()) return false
        if (accessCheck?.isActive == true) return false
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                withContext(dispatcher) { checkRead() }
                ensureActive()
                if (!closed) checkLease()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: AccessRevoked) { if (!closed) deny() }
        }
        accessCheck = job
        job.start()
        return true
    }

    fun cancel() { if (!closed) { retire(); mutableState.value = AdministrationConsultationState.Idle } }

    private fun accept(expected: AdministrationConsultationState): Boolean =
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

    private fun checkRead() {
        checkLease()
        try { policy.check(reader.scope, ReportAdministrationPermission.READ_REPORTS) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw AccessRevoked() }
        checkLease()
    }

    private fun read(request: AdministrationConsultationRequest): Boolean {
        if (!permitted()) return false
        val revision = ++generation
        pending?.cancel()
        val loading = AdministrationConsultationState.Loading(request)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = withContext(dispatcher) {
                    ensureActive()
                    checkRead()
                    val result = when (request) {
                        is AdministrationPageRequest -> {
                            val page = reader.listPage(request.query)
                            validatePage(page, request.query)
                            AdministrationConsultationState.ReadyPage(request, page, request.previous.isNotEmpty())
                        }
                        is AdministrationReportRequest -> readReport(request)
                    }
                    ensureActive()
                    checkRead()
                    result
                }
                ensureActive()
                if (revision == generation && !closed) { checkLease(); mutableState.value = result }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: AccessRevoked) { if (revision == generation && !closed) deny() }
            catch (_: ReportAdministrationPermissionDeniedException) { if (revision == generation && !closed) deny() }
            catch (_: Exception) {
                ensureActive()
                if (revision == generation && !closed) {
                    try {
                        withContext(dispatcher) { checkRead() }
                        ensureActive()
                        if (revision == generation && !closed) {
                            checkLease()
                            mutableState.value = AdministrationConsultationState.Error(request,
                                "No se pudo consultar la información local. Reintenta; los reportes se conservan.")
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
                    mutableState.compareAndSet(loading, AdministrationConsultationState.Error(request,
                        "Se interrumpió la consulta. Puedes reintentar la misma lectura."))
                }
            }
        }
        mutableState.value = loading
        if (revision == generation && !closed) job.start() else job.cancel()
        return true
    }

    private suspend fun readReport(request: AdministrationReportRequest): AdministrationConsultationState {
        val report = reader.findById(request.reportId) ?: return AdministrationConsultationState.Missing(request)
        checkRead()
        require(report.id == request.reportId)
        val history = requireNotNull(reader.readHistory(request.historyQuery))
        checkRead()
        validateHistory(history, request.historyQuery)
        require(history.currentStatus == report.status)
        // Two bounded reads cannot claim a snapshot if a concurrent writer changed the row in between.
        require(reader.findById(request.reportId) == report)
        checkRead()
        return AdministrationConsultationState.ReadyReport(request, report, history, request.previous.isNotEmpty())
    }

    private fun validatePage(page: ReportListPage, query: ReportListPageQuery) {
        require(page.reports.size <= query.limit)
        if (page.hasMore) require(page.reports.size == query.limit)
        require(page.reports.map { it.id }.toSet().size == page.reports.size)
        require(page.reports.all { report ->
            CANONICAL_ID.matches(report.id) && (query.type == null || report.type == query.type) &&
                (query.status == null || report.status == query.status) &&
                (query.priority == null || report.priority == query.priority) &&
                (query.before?.let { before(ReportListCursor.from(report), it) } ?: true)
        })
        require(page.reports.zipWithNext().all { (newer, older) ->
            before(ReportListCursor.from(older), ReportListCursor.from(newer))
        })
    }

    private fun validateHistory(page: ReportHistoryPage, query: ReportHistoryQuery) {
        require(page.reportId == query.reportId && page.events.size <= query.limit)
        require(page.events.map { it.operationId }.toSet().size == page.events.size)
        val beforeSequence = query.beforeSequence
        require(page.events.all { beforeSequence == null || it.sequence < beforeSequence })
        val newest = page.events.firstOrNull()
        if (newest == null) {
            require(!page.hasMore && (query.beforeSequence != null || page.latestSequence == 0L))
            return
        }
        val expected = query.beforeSequence?.let { minOf(page.latestSequence, it - 1L) } ?: page.latestSequence
        require(newest.sequence == expected)
        if (newest.sequence == page.latestSequence) require(newest.toStatus == page.currentStatus)
        require(page.events.zipWithNext().all { (newer, older) ->
            newer.sequence == older.sequence + 1L && newer.fromStatus == older.toStatus
        })
        val oldest = page.events.last()
        if (oldest.sequence == 1L) {
            require(!page.hasMore && page.completeFromCreation == (oldest.kind == ReportStatusEventKind.CREATED))
        } else require(page.hasMore)
    }

    private fun retire() {
        ++generation
        pending?.cancel()
        pending = null
        accessCheck?.cancel()
        accessCheck = null
    }

    private fun deny() {
        closed = true
        retire()
        filters = ReportListPageQuery(limit = PAGE_SIZE)
        mutableState.value = AdministrationConsultationState.Denied
    }

    override fun onCleared() { closed = true; retire(); mutableState.value = AdministrationConsultationState.Denied }

    private class AccessRevoked : IllegalStateException()

    companion object {
        const val PAGE_SIZE = 50
        const val MAX_PREVIOUS = 50
        private val CANONICAL_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private fun before(a: ReportListCursor, b: ReportListCursor): Boolean =
            a.createdAt < b.createdAt || a.createdAt == b.createdAt && a.id < b.id

        private fun requestOf(state: AdministrationConsultationState): AdministrationConsultationRequest? = when (state) {
            is AdministrationConsultationState.Loading -> state.request
            is AdministrationConsultationState.ReadyPage -> state.request
            is AdministrationConsultationState.ReadyReport -> state.request
            is AdministrationConsultationState.Missing -> state.request
            is AdministrationConsultationState.Error -> state.request
            else -> null
        }

        private fun samePosition(a: AdministrationConsultationRequest?, b: AdministrationConsultationRequest): Boolean = when {
            a is AdministrationPageRequest && b is AdministrationPageRequest -> a.query == b.query
            a is AdministrationReportRequest && b is AdministrationReportRequest -> a.historyQuery == b.historyQuery
            else -> false
        }

        fun factory(binding: ReportAdministrationBinding, checkCurrent: () -> Unit,
            dispatcher: CoroutineDispatcher = Dispatchers.IO): ViewModelProvider.Factory = viewModelFactory {
            initializer { AdministrationConsultationViewModel(binding.reader, binding.policy, checkCurrent, dispatcher) }
        }
    }
}
