package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.ReportPhoto
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReportCameraState {
    data object Idle : ReportCameraState
    data object Preparing : ReportCameraState
    data object Waiting : ReportCameraState
    data object Importing : ReportCameraState
    data object Ready : ReportCameraState
    data class Error(val message: String) : ReportCameraState
}

/** Retains only this live capture over rotation. No URI/token is restored from saved state. */
class ReportCameraViewModel(
    private val draft: ReportDraftViewModel,
    private val files: ReportCameraCaptureFiles,
    private val importer: suspend (String) -> ReportPhoto,
    private val checkCurrent: () -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportCameraState>(ReportCameraState.Idle)
    val state = mutableState.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private var request: CaptureRequest? = null
    private var pending: Job? = null
    private var closed = false

    fun prepare(canLaunch: () -> Boolean, launch: (String) -> Unit): Boolean {
        if (closed || !viewModelScope.isActive || pending != null || request != null || draft.state.value.photoSelectionPending) return false
        try { checkCurrent() } catch (_: Exception) { return false }
        val token = draft.beginPhotoSelection() ?: return false
        val own = CaptureRequest(token)
        request = own
        mutableBusy.value = true
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                // Acquiring ownership must finish even if cancelled during the dispatcher handoff;
                // the finally block can then retire exactly this new temporary file.
                val capture = withContext(NonCancellable) { files.create() }
                own.capture = capture
                ensureActive()
                checkCurrent()
                check(!closed && request === own && draft.isCurrentPhotoSelection(token) && canLaunch())
                ensureActive()
                checkCurrent()
                check(!closed && request === own && draft.isCurrentPhotoSelection(token))
                own.waiting = true
                mutableState.value = ReportCameraState.Waiting
                // Publishing state can resume a collector that revokes this session or closes
                // the model. Recheck the gesture and owner after that emission before launch.
                ensureActive()
                check(canLaunch())
                checkCurrent()
                check(!closed && request === own && draft.isCurrentPhotoSelection(token))
                own.launched = true
                try { launch(capture.source) } catch (failure: Exception) { own.launched = false; throw failure }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!closed && request === own) mutableState.value = ReportCameraState.Error(
                    "No se pudo abrir la cámara. Puedes reintentar o elegir una foto de la galería.",
                )
            } finally {
                if (!own.launched && !own.consumed) {
                    draft.cancelPhotoSelection(token)
                    retire(own)
                    if (request === own) request = null
                }
            }
        }
        reserve(job)
        mutableState.value = ReportCameraState.Preparing
        job.start()
        return true
    }

    /** TakePicture's Boolean is the only accepted result; returned intent data is never imported. */
    fun complete(success: Boolean): Boolean {
        val own = request ?: return false
        if (closed || !viewModelScope.isActive || !own.waiting || !own.launched || own.consumed) return false
        val capture = own.capture ?: return false
        own.consumed = true
        own.waiting = false
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            own.resultStarted.set(true)
            try {
                if (!success) {
                    draft.cancelPhotoSelection(own.token)
                    if (!closed && request === own) mutableState.value = ReportCameraState.Idle
                } else {
                    ensureActive()
                    checkCurrent()
                    check(!closed && request === own && draft.isCurrentPhotoSelection(own.token))
                    val photo = importer(capture.source)
                    ensureActive()
                    checkCurrent()
                    check(!closed && request === own && draft.applyPhotoSelection(own.token, photo))
                    mutableState.value = ReportCameraState.Ready
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!closed && request === own) mutableState.value = ReportCameraState.Error(
                    "No se pudo preparar la captura. Se conserva la foto anterior; prueba otra foto o usa la galería.",
                )
            } finally {
                draft.cancelPhotoSelection(own.token)
                val cleaned = retire(own)
                if (!closed && request === own && !cleaned) mutableState.value = ReportCameraState.Error(
                    "No se pudo limpiar el archivo temporal de la cámara. La foto del borrador se conserva.",
                )
                if (request === own) request = null
            }
        }
        // Cancellation before a LAZY body starts skips its finally block. Reserve a fallback
        // owner first; a clear triggered by publishing Importing must still retire the output.
        job.invokeOnCompletion {
            if (!own.resultStarted.get()) {
                draft.cancelPhotoSelection(own.token)
                cleaner.launch { retire(own) }
                if (request === own) request = null
            }
        }
        reserve(job)
        mutableState.value = if (success) ReportCameraState.Importing else ReportCameraState.Waiting
        job.start()
        return true
    }

    fun clearStatus() {
        if (!closed && request == null && pending == null) mutableState.value = ReportCameraState.Idle
    }

    private fun reserve(job: Job) {
        pending = job
        job.invokeOnCompletion {
            if (pending === job) {
                pending = null
                if (request == null) mutableBusy.value = false
            }
        }
    }

    private suspend fun release(capture: ReportCameraCapture): Boolean = withContext(NonCancellable) {
        try { files.release(capture); true } catch (_: Exception) { false }
    }

    private suspend fun retire(own: CaptureRequest): Boolean {
        val capture = own.capture ?: return true
        if (!own.cleanupStarted.compareAndSet(false, true)) return true
        return release(capture)
    }

    override fun onCleared() {
        closed = true
        val own = request
        request = null
        own?.let {
            draft.cancelPhotoSelection(it.token)
            // Preparation/import jobs own their finally cleanup until fully finished. A waiting
            // external camera has no running job, so this specific file is retired on IO.
            if (it.launched && !it.resultStarted.get()) cleaner.launch { retire(it) }
        }
    }

    private class CaptureRequest(val token: ReportPhotoSelectionToken) {
        var capture: ReportCameraCapture? = null
        var waiting = false
        var launched = false
        var consumed = false
        val resultStarted = AtomicBoolean()
        val cleanupStarted = AtomicBoolean()
    }

    companion object {
        private val cleaner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun factory(draft: ReportDraftViewModel, files: ReportCameraCaptureFiles,
            importer: suspend (String) -> ReportPhoto, checkCurrent: () -> Unit): ViewModelProvider.Factory =
            viewModelFactory { initializer { ReportCameraViewModel(draft, files, importer, checkCurrent) } }
    }
}
