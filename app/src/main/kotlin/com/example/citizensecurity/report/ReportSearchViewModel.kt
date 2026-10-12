package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.Report
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

sealed interface ReportSearchState {
    data object Idle : ReportSearchState

    // Identity-bearing states avoid comparing the whole historical reading on the UI thread.
    class Loading(val source: List<Report>, val filters: ReportSearchFilters) : ReportSearchState
    class Ready(
        val source: List<Report>,
        val filters: ReportSearchFilters,
        val reports: List<Report>,
    ) : ReportSearchState
    class Error(
        val source: List<Report>,
        val filters: ReportSearchFilters,
        val message: String,
    ) : ReportSearchState
}

/** A constant-time effect key; equal contents never substitute a different immutable reading. */
class ReportSearchInput(val source: List<Report>, val filters: ReportSearchFilters) {
    override fun equals(other: Any?): Boolean = other is ReportSearchInput &&
        source === other.source && filters === other.filters

    override fun hashCode(): Int =
        31 * System.identityHashCode(source) + System.identityHashCode(filters)
}

/** Only the exact immutable reading and validated filters may expose this completed result. */
fun currentReportSearchResult(
    source: List<Report>,
    filters: ReportSearchFilters,
    state: ReportSearchState,
): ReportSearchState.Ready? = (state as? ReportSearchState.Ready)?.takeIf {
    it.source === source && it.filters === filters
}

/** Filters an existing immutable reading on Default, without repository, GPS or write access. */
class ReportSearchViewModel(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val filter: suspend (List<Report>, ReportSearchFilters) -> List<Report> =
        ::filterLocalReportsCancellable,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportSearchState>(ReportSearchState.Idle)
    val state: StateFlow<ReportSearchState> = mutableState.asStateFlow()
    private var generation = 0L
    private var pending: Job? = null

    fun search(source: List<Report>, filters: ReportSearchFilters) {
        val scope = viewModelScope
        if (!scope.isActive) return
        val request = ++generation
        pending?.cancel()
        val loading = ReportSearchState.Loading(source, filters)
        // Reserve ownership first: a Loading collector may synchronously replace/cancel this search.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val reports = withContext(dispatcher) { filter(source, filters) }
                ensureActive()
                if (generation == request) {
                    mutableState.value = ReportSearchState.Ready(source, filters, reports)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                if (generation == request) mutableState.value = ReportSearchState.Error(
                    source, filters, "No se pudo completar la búsqueda. Intenta nuevamente.",
                )
            }
        }
        pending = job
        job.invokeOnCompletion { cause ->
            if (generation == request) {
                pending = null
                if (cause is CancellationException) {
                    mutableState.compareAndSet(loading, ReportSearchState.Idle)
                }
            }
        }
        mutableState.value = loading
        if (generation == request) job.start() else job.cancel()
    }

    /** Invalidates Ready as well as pending work; a noncooperative old worker cannot republish it. */
    fun cancelSearch() {
        ++generation
        pending?.cancel()
        pending = null
        mutableState.value = ReportSearchState.Idle
    }

    companion object {
        fun factory(dispatcher: CoroutineDispatcher = Dispatchers.Default): ViewModelProvider.Factory =
            viewModelFactory { initializer { ReportSearchViewModel(dispatcher) } }
    }
}
