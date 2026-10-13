package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.ReportHistoryPage
import com.example.citizensecurity.domain.ReportHistoryQuery
import com.example.citizensecurity.domain.ReportHistoryRepository
import com.example.citizensecurity.domain.ReportStatusEventKind
import java.util.Locale
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

sealed interface ReportHistoryState {
    data object Idle : ReportHistoryState
    class Loading(val query: ReportHistoryQuery) : ReportHistoryState
    class Ready(val query: ReportHistoryQuery, val page: ReportHistoryPage) : ReportHistoryState
    class NotFound(val query: ReportHistoryQuery) : ReportHistoryState
    class InvalidFolio(val message: String) : ReportHistoryState
    class Error(val query: ReportHistoryQuery, val message: String) : ReportHistoryState
}

/** Reads one bounded page after a gesture; never changes status or restores an old result. */
class ReportHistoryViewModel(
    private val repository: ReportHistoryRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportHistoryState>(ReportHistoryState.Idle)
    val state: StateFlow<ReportHistoryState> = mutableState.asStateFlow()
    private var pending: Job? = null
    private var generation = 0L
    private var lastQuery: ReportHistoryQuery? = null

    /** A fresh reading of this folio starts at its newest event. A duplicate pending gesture is ignored. */
    fun load(reportId: String) {
        if (!viewModelScope.isActive) return
        val normalized = reportId.trim().lowercase(Locale.ROOT)
        if (!isCanonicalId(normalized)) {
            invalidate()
            mutableState.value = ReportHistoryState.InvalidFolio(
                "Escribe un folio completo de 36 caracteres, con sus guiones.",
            )
            return
        }
        val current = mutableState.value as? ReportHistoryState.Loading
        if (current?.query?.reportId == normalized) return
        read(ReportHistoryQuery(normalized))
    }

    /** Replace, rather than append, the visible page. Every continuation is a new local reading. */
    fun nextPage() {
        val ready = mutableState.value as? ReportHistoryState.Ready ?: return
        val before = ready.page.nextBeforeSequence ?: return
        read(ReportHistoryQuery(ready.query.reportId, before, ready.query.limit))
    }

    /** Retry the exact failed cursor, or refresh the currently visible page. */
    fun reload() {
        if (mutableState.value is ReportHistoryState.Loading) return
        if (mutableState.value is ReportHistoryState.Idle || mutableState.value is ReportHistoryState.InvalidFolio) return
        lastQuery?.let(::read)
    }

    /** Return to the newest events through a fresh reading, without keeping earlier pages in memory. */
    fun firstPage() {
        if (mutableState.value is ReportHistoryState.Loading) return
        val query = lastQuery ?: return
        read(ReportHistoryQuery(query.reportId, limit = query.limit))
    }

    fun cancel() = clear()

    /** Late responses cannot reopen a history that the user has left. */
    fun clear() {
        invalidate()
        mutableState.value = ReportHistoryState.Idle
    }

    private fun invalidate() {
        ++generation
        pending?.cancel()
        pending = null
        lastQuery = null
    }

    private fun read(query: ReportHistoryQuery) {
        val scope = viewModelScope
        if (!scope.isActive) return
        val request = ++generation
        pending?.cancel()
        lastQuery = query
        val loading = ReportHistoryState.Loading(query)
        // Reserve before publishing Loading: a collector may replace or cancel this reading.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val page = withContext(dispatcher) {
                    repository.readHistory(query)?.also { validatePage(it, query) }
                }
                ensureActive()
                if (generation == request) mutableState.value = if (page == null) {
                    ReportHistoryState.NotFound(query)
                } else {
                    ReportHistoryState.Ready(query, page)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                if (generation == request) mutableState.value = ReportHistoryState.Error(
                    query, "No se pudo leer el historial de este reporte. Intenta nuevamente.",
                )
            }
        }
        pending = job
        job.invokeOnCompletion { cause ->
            if (generation == request) {
                pending = null
                if (cause is CancellationException) mutableState.compareAndSet(loading, ReportHistoryState.Idle)
            }
        }
        mutableState.value = loading
        if (generation == request) job.start() else job.cancel()
    }

    private fun validatePage(page: ReportHistoryPage, query: ReportHistoryQuery) {
        require(page.reportId == query.reportId)
        require(page.events.size <= query.limit)
        val before = query.beforeSequence
        require(page.events.all { before == null || it.sequence < before })
        require(page.events.map { it.operationId }.toSet().size == page.events.size)
        val newest = page.events.firstOrNull()
        if (newest == null) {
            require(!page.hasMore)
            require(before != null || page.latestSequence == 0L)
            return
        }
        val expectedNewest = before?.let { minOf(page.latestSequence, it - 1L) } ?: page.latestSequence
        require(newest.sequence == expectedNewest)
        if (newest.sequence == page.latestSequence) require(newest.toStatus == page.currentStatus)
        require(page.events.zipWithNext().all { (newer, older) ->
            newer.sequence == older.sequence + 1L && newer.fromStatus == older.toStatus
        })
        val oldest = page.events.last()
        if (oldest.sequence == 1L) {
            require(!page.hasMore)
            require(page.completeFromCreation == (oldest.kind == ReportStatusEventKind.CREATED))
        } else {
            require(page.hasMore)
        }
    }

    companion object {
        private fun isCanonicalId(value: String): Boolean =
            runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

        fun factory(
            repository: ReportHistoryRepository,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportHistoryViewModel(repository, dispatcher) }
        }
    }
}
