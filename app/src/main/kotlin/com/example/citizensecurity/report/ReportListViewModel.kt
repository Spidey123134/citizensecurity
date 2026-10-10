package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportRepository
import com.example.citizensecurity.domain.ReportStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ReportListState {
    data object Idle : ReportListState
    data object Loading : ReportListState
    data class Loaded(val reports: List<Report>) : ReportListState
    data class Error(val message: String) : ReportListState
}

/** Reads only. A newer refresh owns the result even if the old repository ignores cancellation. */
class ReportListViewModel(private val repository: ReportRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportListState>(ReportListState.Idle)
    val state: StateFlow<ReportListState> = mutableState.asStateFlow()
    private var generation = 0L
    private var pending: Job? = null

    fun load() {
        val scope = viewModelScope
        if (!scope.isActive) return
        val request = ++generation
        pending?.cancel()
        // Reserve ownership before publishing Loading: a collector can immediately request a refresh.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val reports = repository.list().toList()
                ensureActive()
                if (generation == request) mutableState.value = ReportListState.Loaded(reports)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                if (generation == request) mutableState.value = ReportListState.Error(
                    "No se pudieron leer los reportes. Intenta actualizar nuevamente.",
                )
            }
        }
        pending = job
        job.invokeOnCompletion { cause ->
            if (generation == request) {
                pending = null
                if (cause is CancellationException) {
                    mutableState.compareAndSet(ReportListState.Loading, ReportListState.Idle)
                }
            }
        }
        mutableState.value = ReportListState.Loading
        if (generation == request) job.start() else job.cancel()
    }

    fun cancelLoad() {
        ++generation
        pending?.cancel()
        pending = null
        mutableState.compareAndSet(ReportListState.Loading, ReportListState.Idle)
    }

    companion object {
        fun factory(repository: ReportRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportListViewModel(repository) }
        }
    }
}

/** Filters operate on the last immutable reading and never alter a report's type or status. */
fun filterLocalReports(
    reports: List<Report>,
    type: IncidentType? = null,
    status: ReportStatus? = null,
): List<Report> = reports.filter {
    (type == null || it.type == type) && (status == null || it.status == status)
}
