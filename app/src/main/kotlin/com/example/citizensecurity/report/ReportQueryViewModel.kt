package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ReportQueryState {
    data object Idle : ReportQueryState
    data class Loading(val folio: String) : ReportQueryState
    data class Found(val report: Report) : ReportQueryState
    data class NotFound(val folio: String) : ReportQueryState
    data class InvalidFolio(val message: String) : ReportQueryState
    data class Error(val message: String) : ReportQueryState
}

/** A folio reading belongs to its latest request; leaving its detail invalidates that reading. */
class ReportQueryViewModel(private val repository: ReportRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportQueryState>(ReportQueryState.Idle)
    val state: StateFlow<ReportQueryState> = mutableState.asStateFlow()
    private var generation = 0L
    private var pending: Job? = null

    fun findByFolio(folio: String) {
        val scope = viewModelScope
        if (!scope.isActive) return
        val request = ++generation
        pending?.cancel()
        pending = null
        val normalized = folio.trim().lowercase()
        if (!FOLIO_PATTERN.matches(normalized)) {
            mutableState.value = ReportQueryState.InvalidFolio(
                "Escribe un folio completo de 36 caracteres, con sus guiones.",
            )
            return
        }
        val loading = ReportQueryState.Loading(normalized)
        // Reserve the job before publishing Loading: a state collector can ask for another folio.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val report = repository.findById(normalized)
                ensureActive()
                if (generation == request) {
                    mutableState.value = if (report == null) {
                        ReportQueryState.NotFound(normalized)
                    } else {
                        ReportQueryState.Found(report)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                if (generation == request) mutableState.value = ReportQueryState.Error(
                    "No se pudo consultar el reporte. Intenta nuevamente.",
                )
            }
        }
        pending = job
        job.invokeOnCompletion { cause ->
            if (generation == request) {
                pending = null
                if (cause is CancellationException) {
                    mutableState.compareAndSet(loading, ReportQueryState.Idle)
                }
            }
        }
        mutableState.value = loading
        if (generation == request) job.start() else job.cancel()
    }

    /** No writes or shared-repository closing; late results cannot reopen an abandoned detail. */
    fun cancelQuery() {
        ++generation
        pending?.cancel()
        pending = null
        mutableState.value = ReportQueryState.Idle
    }

    companion object {
        private val FOLIO_PATTERN = Regex(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        )

        fun factory(repository: ReportRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportQueryViewModel(repository) }
        }
    }
}
