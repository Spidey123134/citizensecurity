package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.ReportActivityCursor
import com.example.citizensecurity.domain.ReportActivityEntry
import com.example.citizensecurity.domain.ReportActivityFilter
import com.example.citizensecurity.domain.ReportActivityPage
import com.example.citizensecurity.domain.ReportActivityQuery
import com.example.citizensecurity.domain.ReportActivityRepository
import com.example.citizensecurity.domain.ReportStatusEventKind
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

sealed interface ReportActivityState {
    data object Idle : ReportActivityState
    class Loading(val query: ReportActivityQuery) : ReportActivityState
    class Ready(val query: ReportActivityQuery, val page: ReportActivityPage, val canPrevious: Boolean = false) : ReportActivityState
    class Error(val query: ReportActivityQuery, val message: String) : ReportActivityState
}

/** Reads real committed activity after a gesture. Keeps one page and at most fifty past cursors;
 * neither marks events as read nor emits operating-system notifications or changes a report.
 */
class ReportActivityViewModel(
    private val repository: ReportActivityRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val checkCurrent: () -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportActivityState>(ReportActivityState.Idle)
    val state = mutableState.asStateFlow()
    private var pending: Job? = null
    private var generation = 0L
    private var closed = false
    private var previousQueries: List<ReportActivityQuery> = emptyList()
    private var lastPlan: ReadPlan? = null

    /** Reopening/rotation retains the same reading, including a failed query until explicit retry. */
    fun activate(filter: ReportActivityFilter = ReportActivityFilter.ALL): Boolean {
        if (!permitted()) return false
        if (currentQuery()?.filter == filter) return false
        return refresh(filter)
    }

    /** A fresh reading starts at the newest event. Repeated pending first-page gestures coalesce. */
    fun refresh(filter: ReportActivityFilter = currentQuery()?.filter ?: ReportActivityFilter.ALL): Boolean {
        if (!permitted()) return false
        val loading = mutableState.value as? ReportActivityState.Loading
        if (loading?.query?.filter == filter && loading.query.before == null) return false
        return read(ReadPlan(ReportActivityQuery(filter = filter, limit = PAGE_SIZE), emptyList()))
    }

    /** A widget can supply its captured query to prevent a queued gesture navigating another page. */
    fun next(expectedQuery: ReportActivityQuery? = (mutableState.value as? ReportActivityState.Ready)?.query): Boolean {
        if (!permitted()) return false
        val ready = mutableState.value as? ReportActivityState.Ready ?: return false
        if (expectedQuery !== ready.query || !ready.page.hasMore) return false
        val before = ready.page.nextBefore ?: return false
        val history = (previousQueries + ready.query).takeLast(MAX_PREVIOUS)
        return read(ReadPlan(ReportActivityQuery(ready.query.filter, before, PAGE_SIZE), history))
    }

    /** Return through a new reading of the prior position, never a retained page of private data. */
    fun previous(expectedQuery: ReportActivityQuery? = (mutableState.value as? ReportActivityState.Ready)?.query): Boolean {
        if (!permitted()) return false
        val ready = mutableState.value as? ReportActivityState.Ready ?: return false
        if (expectedQuery !== ready.query) return false
        val previous = previousQueries.lastOrNull() ?: return false
        return read(ReadPlan(previous.copy(), previousQueries.dropLast(1)))
    }

    /** Preserve the exact cursor and navigation proposal when a read fails or is cancelled. */
    fun retry(expectedQuery: ReportActivityQuery? = (mutableState.value as? ReportActivityState.Error)?.query): Boolean {
        if (!permitted()) return false
        val error = mutableState.value as? ReportActivityState.Error ?: return false
        val plan = lastPlan ?: return false
        if (expectedQuery !== error.query || plan.query !== error.query) return false
        return read(plan)
    }

    /** Only an entry object from this visible page/query may open its report; routing rechecks too. */
    fun canOpen(entry: ReportActivityEntry, expectedQuery: ReportActivityQuery? =
        (mutableState.value as? ReportActivityState.Ready)?.query): Boolean {
        if (!permitted()) return false
        val ready = mutableState.value as? ReportActivityState.Ready ?: return false
        return expectedQuery === ready.query && ready.page.entries.any { it === entry }
    }

    private fun currentQuery(): ReportActivityQuery? = when (val current = mutableState.value) {
        is ReportActivityState.Loading -> current.query
        is ReportActivityState.Ready -> current.query
        is ReportActivityState.Error -> current.query
        ReportActivityState.Idle -> null
    }

    private fun permitted(): Boolean {
        if (closed || !viewModelScope.isActive) return false
        return try { checkLease(); true } catch (_: Exception) { revoke(); false }
    }

    private fun checkLease() {
        try { checkCurrent() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw AccessRevoked() }
    }

    private fun read(plan: ReadPlan): Boolean {
        if (closed || !viewModelScope.isActive) return false
        val request = ++generation
        pending?.cancel()
        val loading = ReportActivityState.Loading(plan.query)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val page = withContext(dispatcher) {
                    ensureActive()
                    checkLease()
                    repository.readActivity(plan.query).also {
                        ensureActive()
                        validatePage(it, plan.query)
                        checkLease()
                    }
                }
                ensureActive()
                if (generation == request && !closed) {
                    checkLease()
                    previousQueries = plan.history
                    mutableState.value = ReportActivityState.Ready(plan.query, page, previousQueries.isNotEmpty())
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AccessRevoked) {
                if (generation == request && !closed) revoke()
            } catch (_: Exception) {
                ensureActive()
                if (generation == request && !closed) {
                    try {
                        checkLease()
                        mutableState.value = ReportActivityState.Error(plan.query,
                            "No se pudo leer la actividad local. Intenta nuevamente; tus reportes se conservan.")
                    } catch (_: AccessRevoked) { revoke() }
                }
            }
        }
        // Reserve both query/history and job before state collectors can replace this request.
        lastPlan = plan
        pending = job
        job.invokeOnCompletion { cause ->
            if (generation == request && !closed) {
                pending = null
                if (cause is CancellationException && permitted()) {
                    mutableState.compareAndSet(loading, ReportActivityState.Error(plan.query,
                        "Se interrumpió la lectura de actividad. Puedes reintentar esta misma página."))
                }
            }
        }
        mutableState.value = loading
        if (generation == request && !closed) job.start() else job.cancel()
        return true
    }

    private fun validatePage(page: ReportActivityPage, query: ReportActivityQuery) {
        require(page.entries.size <= query.limit)
        if (page.hasMore) require(page.entries.size == query.limit)
        if (query.filter == ReportActivityFilter.STATUS_CHANGES) {
            require(page.entries.all { it.event.kind == ReportStatusEventKind.STATUS_CHANGED })
        }
        query.before?.let { before ->
            require(page.entries.all { entry -> isBefore(entry, before) })
        }
        val expected = if (page.hasMore) page.entries.lastOrNull()?.let {
            ReportActivityCursor(it.event.recordedAt, it.event.operationId)
        } else null
        require(page.nextBefore == expected)
    }

    private fun isBefore(entry: ReportActivityEntry, cursor: ReportActivityCursor): Boolean =
        entry.event.recordedAt < cursor.recordedAt ||
            entry.event.recordedAt == cursor.recordedAt && entry.event.operationId < cursor.operationId

    private fun retire() {
        ++generation
        pending?.cancel()
        pending = null
        previousQueries = emptyList()
        lastPlan = null
        mutableState.value = ReportActivityState.Idle
    }

    private fun revoke() { closed = true; retire() }

    override fun onCleared() { closed = true; retire() }

    private class ReadPlan(val query: ReportActivityQuery, val history: List<ReportActivityQuery>)
    private class AccessRevoked : IllegalStateException()

    companion object {
        const val PAGE_SIZE = 50
        const val MAX_PREVIOUS = 50
        fun factory(repository: ReportActivityRepository, dispatcher: CoroutineDispatcher = Dispatchers.IO,
            checkCurrent: () -> Unit = {}): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportActivityViewModel(repository, dispatcher, checkCurrent) }
        }
    }
}
