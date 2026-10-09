package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.IdempotentReportRepository
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ReportSaveState {
    data object Idle : ReportSaveState
    data object Saving : ReportSaveState
    data class Saved(val report: Report) : ReportSaveState
    data class Invalid(val errors: Map<ReportField, String>) : ReportSaveState
    data class Error(val message: String) : ReportSaveState
}

class ReportViewModel(private val repository: ReportRepository) : ViewModel() {
    private var requestId = UUID.randomUUID().toString()
    private var requestedDraft: NewReport? = null
    private val mutableState = MutableStateFlow<ReportSaveState>(ReportSaveState.Idle)
    val state: StateFlow<ReportSaveState> = mutableState.asStateFlow()

    fun save(draft: NewReport) {
        val scope = viewModelScope
        if (!scope.isActive) return
        val previous = mutableState.value
        if (previous is ReportSaveState.Saved || previous == ReportSaveState.Saving) return
        if (repository is IdempotentReportRepository && requestedDraft != null && requestedDraft != draft) {
            mutableState.value = ReportSaveState.Error(
                "El resultado del intento anterior está pendiente. Reintenta con los mismos datos antes de iniciar otro reporte.",
            )
            return
        }
        if (
            !mutableState.compareAndSet(previous, ReportSaveState.Saving)
        ) return
        requestedDraft = draft

        scope.launch {
            try {
                val report = if (repository is IdempotentReportRepository) {
                    repository.createOnce(requestId, draft)
                } else repository.create(draft)
                ensureActive()
                mutableState.value = ReportSaveState.Saved(report)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (invalid: InvalidReportException) {
                ensureActive()
                // El contrato de validación rechaza antes de confirmar la escritura.
                requestedDraft = null
                mutableState.value = ReportSaveState.Invalid(invalid.errors.errors.toMap())
            } catch (_: Exception) {
                ensureActive()
                mutableState.value = ReportSaveState.Error(
                    "No se pudo guardar el reporte. Intenta nuevamente.",
                )
            }
        }.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                mutableState.compareAndSet(ReportSaveState.Saving, ReportSaveState.Idle)
            }
        }
    }

    /** Un éxito conserva su folio hasta que el dueño inicia otro reporte expresamente. */
    fun startNewReport(): Boolean {
        if (!viewModelScope.isActive) return false
        val previous = mutableState.value
        if (previous !is ReportSaveState.Saved) return false
        requestId = UUID.randomUUID().toString()
        requestedDraft = null
        return mutableState.compareAndSet(previous, ReportSaveState.Idle)
    }

    companion object {
        fun factory(repository: ReportRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportViewModel(repository) }
        }
    }
}
