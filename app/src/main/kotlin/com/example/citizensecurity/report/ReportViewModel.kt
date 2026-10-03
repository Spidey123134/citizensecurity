package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ReportSaveState {
    data object Idle : ReportSaveState
    data object Saving : ReportSaveState
    data class Saved(val report: Report) : ReportSaveState
    data class Invalid(val errors: Map<ReportField, String>) : ReportSaveState
    data class Error(val message: String) : ReportSaveState
}

class ReportViewModel(private val repository: ReportRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportSaveState>(ReportSaveState.Idle)
    val state: StateFlow<ReportSaveState> = mutableState.asStateFlow()

    fun save(draft: NewReport) {
        val scope = viewModelScope
        if (!scope.isActive) return
        val previous = mutableState.value
        if (previous == ReportSaveState.Saving ||
            !mutableState.compareAndSet(previous, ReportSaveState.Saving)
        ) return

        scope.launch {
            try {
                mutableState.value = ReportSaveState.Saved(repository.create(draft))
            } catch (cancelled: CancellationException) {
                mutableState.value = ReportSaveState.Idle
                throw cancelled
            } catch (invalid: InvalidReportException) {
                mutableState.value = ReportSaveState.Invalid(invalid.errors.errors.toMap())
            } catch (_: Exception) {
                mutableState.value = ReportSaveState.Error(
                    "No se pudo guardar el reporte. Intenta nuevamente.",
                )
            }
        }
    }

    companion object {
        fun factory(repository: ReportRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportViewModel(repository) }
        }
    }
}
