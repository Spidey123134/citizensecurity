package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportRepository
import kotlinx.coroutines.CancellationException
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

class ReportQueryViewModel(private val repository: ReportRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportQueryState>(ReportQueryState.Idle)
    val state: StateFlow<ReportQueryState> = mutableState.asStateFlow()

    fun findByFolio(folio: String) {
        val scope = viewModelScope
        if (!scope.isActive) return
        val previous = mutableState.value
        if (previous is ReportQueryState.Loading) return

        val normalized = folio.trim().lowercase()
        if (!FOLIO_PATTERN.matches(normalized)) {
            mutableState.compareAndSet(
                previous,
                ReportQueryState.InvalidFolio("Escribe un folio completo de 36 caracteres, con sus guiones."),
            )
            return
        }
        val loading = ReportQueryState.Loading(normalized)
        if (!mutableState.compareAndSet(previous, loading)) return

        scope.launch {
            try {
                val report = repository.findById(normalized)
                ensureActive()
                mutableState.value = if (report == null) {
                    ReportQueryState.NotFound(normalized)
                } else {
                    ReportQueryState.Found(report)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                mutableState.value = ReportQueryState.Error(
                    "No se pudo consultar el reporte. Intenta nuevamente.",
                )
            }
        }.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                mutableState.compareAndSet(loading, ReportQueryState.Idle)
            }
        }
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
