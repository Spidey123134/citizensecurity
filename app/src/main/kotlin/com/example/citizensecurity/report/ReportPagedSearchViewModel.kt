package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportListCursor
import com.example.citizensecurity.domain.ReportListPage
import com.example.citizensecurity.domain.ReportListPageQuery
import com.example.citizensecurity.domain.ReportListPageRepository
import java.util.Collections
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

sealed interface ReportPagedSearchState {
    object Idle : ReportPagedSearchState

    // Identity avoids comparing even the bounded page when StateFlow is updated on Main.
    class Loading(val filters: ReportSearchFilters) : ReportPagedSearchState
    class Ready(
        val filters: ReportSearchFilters,
        reports: List<Report>,
        val nextCursor: ReportListCursor?,
        val examined: Int,
        val pageNumber: Int,
        val hasPrevious: Boolean,
    ) : ReportPagedSearchState {
        val reports: List<Report> = Collections.unmodifiableList(ArrayList(reports))

        init {
            require(reports.size <= RESULTS_PER_PAGE)
            require(examined in 0..MAX_EXAMINED_PER_PAGE)
            require(pageNumber >= 1)
        }
    }
    class Error(
        val filters: ReportSearchFilters,
        val message: String,
    ) : ReportPagedSearchState
}

/** A completed page belongs only to the exact validated criteria that requested it. */
fun currentPagedResult(
    filters: ReportSearchFilters,
    state: ReportPagedSearchState,
): ReportPagedSearchState.Ready? = (state as? ReportPagedSearchState.Ready)?.takeIf {
    it.filters === filters
}

private const val RESULTS_PER_PAGE = 50
private const val FETCH_BATCH_SIZE = 100
private const val MAX_EXAMINED_PER_PAGE = 500

/** Reads and scans bounded batches on Default; keeps one page and at most 50 page starts. */
class ReportPagedSearchViewModel(
    private val repository: ReportListPageRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val savedStateHandle: SavedStateHandle? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportPagedSearchState>(ReportPagedSearchState.Idle)
    val state: StateFlow<ReportPagedSearchState> = mutableState.asStateFlow()
    private var generation = 0L
    private var pending: Job? = null
    private val history = ArrayList<ReportPageStart>(MAX_REPORT_CURSOR_HISTORY)
    private var historyIndex = 0
    private var lastRequest: ReportPageRequest? = null

    init {
        val restored = ReportSearchCheckpointCodec.decode(savedStateHandle?.get<Any?>(ReportSearchCheckpointCodec.KEY))
        if (restored != null) {
            history.addAll(restored.history)
            historyIndex = restored.historyIndex
            lastRequest = restored.request
        } else {
            savedStateHandle?.remove<Any?>(ReportSearchCheckpointCodec.KEY)
        }
        // Restoration retains only a reading position; activate supplies the screen's criteria.
    }

    /** Every explicit search starts a fresh traversal, including a repeated criteria object. */
    fun search(filters: ReportSearchFilters) {
        if (!viewModelScope.isActive) return
        history.clear()
        historyIndex = 0
        load(ReportPageRequest(filters, null, 1, ReportPageNavigation.SEARCH))
    }

    /** Recreated criteria rebind ownership while retaining this traversal's current page start. */
    fun activate(filters: ReportSearchFilters) {
        val request = lastRequest
        if (request == null || !sameCriteria(request.filters, filters)) {
            search(filters)
        } else {
            load(ReportPageRequest(
                filters, request.before, request.pageNumber, request.navigation, request.historyIndex,
            ))
        }
    }

    fun nextPage() {
        val ready = mutableState.value as? ReportPagedSearchState.Ready ?: return
        val before = ready.nextCursor ?: return
        if (ready.pageNumber == Int.MAX_VALUE) return
        load(ReportPageRequest(ready.filters, before, ready.pageNumber + 1, ReportPageNavigation.NEXT))
    }

    fun previousPage() {
        val ready = mutableState.value as? ReportPagedSearchState.Ready ?: return
        if (!ready.hasPrevious || historyIndex <= 0) return
        val targetIndex = historyIndex - 1
        val start = history[targetIndex]
        load(ReportPageRequest(ready.filters, start.before, start.pageNumber, ReportPageNavigation.PREVIOUS, targetIndex))
    }

    /** A failed navigation repeats its original start without altering the successful history. */
    fun retry() {
        val error = mutableState.value as? ReportPagedSearchState.Error ?: return
        val request = lastRequest?.takeIf { it.filters === error.filters } ?: return
        load(request)
    }

    fun cancelSearch() {
        ++generation
        pending?.cancel()
        pending = null
        // Returning from a detail resumes this same start through activate, with a fresh read.
        mutableState.value = ReportPagedSearchState.Idle
    }

    private fun load(pageRequest: ReportPageRequest) {
        val scope = viewModelScope
        if (!scope.isActive) return
        val requestGeneration = ++generation
        pending?.cancel()
        lastRequest = pageRequest
        persistCheckpoint()
        val loading = ReportPagedSearchState.Loading(pageRequest.filters)
        // Reserve ownership before Loading: its collector can replace or cancel this request.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val scanned = withContext(dispatcher) { scan(pageRequest) }
                ensureActive()
                if (generation == requestGeneration) {
                    commitHistory(pageRequest)
                    lastRequest = ReportPageRequest(
                        pageRequest.filters, pageRequest.before, pageRequest.pageNumber,
                        ReportPageNavigation.RELOAD, historyIndex,
                    )
                    persistCheckpoint()
                    mutableState.value = ReportPagedSearchState.Ready(
                        pageRequest.filters, scanned.reports, scanned.nextCursor, scanned.examined,
                        pageRequest.pageNumber, historyIndex > 0,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                if (generation == requestGeneration) mutableState.value = ReportPagedSearchState.Error(
                    pageRequest.filters, "No se pudo completar la búsqueda. Intenta nuevamente.",
                )
            }
        }
        pending = job
        job.invokeOnCompletion { cause ->
            if (generation == requestGeneration) {
                pending = null
                if (cause is CancellationException) {
                    mutableState.compareAndSet(loading, ReportPagedSearchState.Idle)
                }
            }
        }
        mutableState.value = loading
        if (generation == requestGeneration) job.start() else job.cancel()
    }

    private suspend fun scan(request: ReportPageRequest): ScannedPage {
        val context = currentCoroutineContext()
        val result = ArrayList<Report>(RESULTS_PER_PAGE)
        var before = request.before
        var examined = 0
        while (examined < MAX_EXAMINED_PER_PAGE) {
            context.ensureActive()
            val query = ReportListPageQuery(
                type = request.filters.type,
                status = request.filters.status,
                priority = request.filters.priority,
                startInclusive = request.filters.startInclusive,
                endExclusive = request.filters.endExclusive,
                before = before,
                limit = minOf(FETCH_BATCH_SIZE, MAX_EXAMINED_PER_PAGE - examined),
            )
            val page = repository.listPage(query)
            context.ensureActive()
            validatePage(page, query)
            if (page.reports.isEmpty()) return ScannedPage(result, null, examined)
            for ((index, report) in page.reports.withIndex()) {
                context.ensureActive()
                examined++
                before = ReportListCursor.from(report)
                if (filterLocalReportsCancellable(listOf(report), request.filters).isNotEmpty()) {
                    result.add(report)
                }
                if (result.size == RESULTS_PER_PAGE || examined == MAX_EXAMINED_PER_PAGE) {
                    val hasUnexaminedRows = index < page.reports.lastIndex || page.hasMore
                    // The fetched suffix is discarded; before the last examined row recovers it.
                    return ScannedPage(result, before.takeIf { hasUnexaminedRows }, examined)
                }
            }
            if (!page.hasMore) return ScannedPage(result, null, examined)
        }
        error("Unreachable bounded scan")
    }

    private suspend fun validatePage(page: ReportListPage, query: ReportListPageQuery) {
        require(page.reports.size <= query.limit)
        require(page.reports.isNotEmpty() || !page.hasMore)
        var previous = query.before
        val context = currentCoroutineContext()
        for (report in page.reports) {
            context.ensureActive()
            val cursor = ReportListCursor.from(report)
            require(previous == null || isBefore(cursor, previous))
            previous = cursor
        }
    }

    private fun isBefore(cursor: ReportListCursor, previous: ReportListCursor): Boolean =
        cursor.createdAt < previous.createdAt ||
            (cursor.createdAt == previous.createdAt && cursor.id < previous.id)

    private fun sameCriteria(first: ReportSearchFilters, second: ReportSearchFilters): Boolean =
        first.type == second.type && first.status == second.status &&
            first.priority == second.priority && first.normalizedText == second.normalizedText &&
            first.startInclusive == second.startInclusive && first.endExclusive == second.endExclusive

    private fun commitHistory(request: ReportPageRequest) {
        when (request.navigation) {
            ReportPageNavigation.SEARCH -> {
                history.clear()
                history.add(ReportPageStart(request.before, request.pageNumber))
                historyIndex = 0
            }
            ReportPageNavigation.NEXT -> {
                while (history.lastIndex > historyIndex) history.removeAt(history.lastIndex)
                history.add(ReportPageStart(request.before, request.pageNumber))
                if (history.size > MAX_REPORT_CURSOR_HISTORY) history.removeAt(0)
                historyIndex = history.lastIndex
            }
            ReportPageNavigation.PREVIOUS, ReportPageNavigation.RELOAD -> historyIndex = request.historyIndex
        }
    }

    private fun persistCheckpoint() {
        val handle = savedStateHandle ?: return
        val request = lastRequest ?: return
        val encoded = ReportSearchCheckpointCodec.encode(ReportSearchCheckpoint(request, history, historyIndex))
        if (encoded == null) handle.remove<Any?>(ReportSearchCheckpointCodec.KEY)
        else handle[ReportSearchCheckpointCodec.KEY] = encoded
    }

    private class ScannedPage(
        val reports: List<Report>,
        val nextCursor: ReportListCursor?,
        val examined: Int,
    )

    companion object {
        fun factory(
            repository: ReportListPageRepository,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportPagedSearchViewModel(repository, dispatcher, createSavedStateHandle()) }
        }
    }
}
