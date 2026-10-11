package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.ReportPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ReportPhotoImportState {
    data object Idle : ReportPhotoImportState
    data class Importing(val token: ReportPhotoSelectionToken) : ReportPhotoImportState
    data class Imported(val photo: ReportPhoto) : ReportPhotoImportState
    data class Error(val message: String) : ReportPhotoImportState
}

/** Source strings live only inside the import job; state/SavedState contain no URI or image bytes. */
class ReportPhotoImportViewModel(
    private val draft: ReportDraftViewModel,
    private val importer: suspend (String) -> ReportPhoto,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportPhotoImportState>(ReportPhotoImportState.Idle)
    val state: StateFlow<ReportPhotoImportState> = mutableState.asStateFlow()
    private var generation = 0L
    private var pending: Job? = null

    fun importPhoto(token: ReportPhotoSelectionToken, source: String): Boolean {
        val scope = viewModelScope
        if (!scope.isActive || !draft.isCurrentPhotoSelection(token)) return false
        if ((mutableState.value as? ReportPhotoImportState.Importing)?.token === token) return false
        val request = ++generation
        pending?.cancel()
        val importing = ReportPhotoImportState.Importing(token)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                require(source.isNotBlank()) { "No photograph was selected" }
                val photo = importer(source)
                ensureActive()
                if (generation == request) {
                    mutableState.value = if (draft.applyPhotoSelection(token, photo)) {
                        ReportPhotoImportState.Imported(photo)
                    } else ReportPhotoImportState.Idle
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                if (generation == request) {
                    val stillCurrent = draft.cancelPhotoSelection(token)
                    mutableState.value = if (stillCurrent) ReportPhotoImportState.Error(
                        "No se pudo preparar la fotografía. Vuelve a elegir una imagen válida.",
                    ) else ReportPhotoImportState.Idle
                }
            }
        }
        pending = job
        job.invokeOnCompletion { cause ->
            if (generation == request) {
                pending = null
                if (cause is CancellationException) {
                    draft.cancelPhotoSelection(token)
                    mutableState.compareAndSet(importing, ReportPhotoImportState.Idle)
                }
            }
        }
        mutableState.value = importing
        if (generation == request) job.start() else job.cancel()
        return true
    }

    fun cancelSelection(token: ReportPhotoSelectionToken): Boolean {
        if (!viewModelScope.isActive || !draft.isCurrentPhotoSelection(token)) return false
        ++generation
        pending?.cancel()
        pending = null
        draft.cancelPhotoSelection(token)
        mutableState.value = ReportPhotoImportState.Idle
        return true
    }

    /** Cancels the importer after detaching/resetting/correcting a draft by an explicit gesture. */
    fun clearStatus() {
        if (!viewModelScope.isActive) return
        ++generation
        pending?.cancel()
        pending = null
        draft.currentPhotoSelection()?.let(draft::cancelPhotoSelection)
        mutableState.value = ReportPhotoImportState.Idle
    }

    companion object {
        fun factory(draft: ReportDraftViewModel, importer: suspend (String) -> ReportPhoto): ViewModelProvider.Factory =
            viewModelFactory { initializer { ReportPhotoImportViewModel(draft, importer) } }
    }
}
