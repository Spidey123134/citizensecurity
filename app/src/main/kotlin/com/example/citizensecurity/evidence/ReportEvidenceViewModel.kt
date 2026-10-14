package com.example.citizensecurity.evidence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.util.concurrent.atomic.AtomicLong
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

sealed interface ReportEvidenceState {
    data object Idle : ReportEvidenceState
    class Loading(val request: ReportEvidenceRequest) : ReportEvidenceState
    class Ready(val request: ReportEvidenceRequest, val artifact: ReportEvidenceArtifact,
        val message: String? = null) : ReportEvidenceState
    class Error(val request: ReportEvidenceRequest, val message: String) : ReportEvidenceState
    class Sharing(val request: ReportEvidenceRequest, val artifact: ReportEvidenceArtifact) : ReportEvidenceState
    class ChooserOpened(val request: ReportEvidenceRequest, val artifact: ReportEvidenceArtifact) : ReportEvidenceState
}

/** Explicit local export and one-time chooser reservation; no restored files or automatic sends. */
class ReportEvidenceViewModel(
    private val producer: ReportEvidenceProducer,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportEvidenceState>(ReportEvidenceState.Idle)
    val state: StateFlow<ReportEvidenceState> = mutableState.asStateFlow()
    private val generation = AtomicLong()
    private var pending: Job? = null

    fun prepare(request: ReportEvidenceRequest) {
        val scope = viewModelScope
        if (!scope.isActive) return
        val current = mutableState.value
        if (current is ReportEvidenceState.Loading && current.request == request) return
        // An already reserved chooser must finish or fail before another export is started.
        if (current is ReportEvidenceState.Sharing) return
        val revision = generation.incrementAndGet()
        pending?.cancel()
        val loading = ReportEvidenceState.Loading(request)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val artifact = withContext(dispatcher) {
                    val task = this
                    val checkCurrent = {
                        task.ensureActive()
                        if (generation.get() != revision) throw CancellationException("La ficha dejó de estar vigente.")
                    }
                    checkCurrent()
                    producer.prepare(request, checkCurrent).also {
                        checkCurrent()
                        validateEvidenceArtifact(it, request)
                    }
                }
                ensureActive()
                if (generation.get() == revision) mutableState.value = ReportEvidenceState.Ready(request, artifact)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ReportEvidenceQuotaException) {
                ensureActive()
                if (generation.get() == revision) mutableState.value = ReportEvidenceState.Error(request,
                    "El espacio temporal de fichas está lleno. En Ajustes de Android, abre CITIZENSECURITY y pulsa Borrar caché " +
                        "para liberar las fichas temporales. No pulses Borrar almacenamiento ni Borrar datos: " +
                        "eliminarían cuentas y reportes. Después vuelve a preparar la ficha.")
            } catch (_: Exception) {
                ensureActive()
                if (generation.get() == revision) mutableState.value = ReportEvidenceState.Error(request,
                    "No se pudo preparar la ficha. El reporte y su fotografía se conservan; intenta nuevamente.")
            }
        }
        pending = job
        job.invokeOnCompletion { cause ->
            if (generation.get() == revision) {
                pending = null
                if (cause is CancellationException) mutableState.compareAndSet(loading, ReportEvidenceState.Idle)
            }
        }
        mutableState.value = loading
        if (generation.get() == revision) job.start() else job.cancel()
    }

    fun cancel() = clear()

    fun clear() {
        generation.incrementAndGet()
        pending?.cancel()
        pending = null
        mutableState.value = ReportEvidenceState.Idle
    }

    /** The screen must also check its current folio, session and RESUMED lifecycle. */
    fun consumeForShare(expected: ReportEvidenceState.Ready): ReportEvidenceState.Sharing? {
        if (!viewModelScope.isActive) return null
        val reservation = ReportEvidenceState.Sharing(expected.request, expected.artifact)
        return if (mutableState.compareAndSet(expected, reservation)) reservation else null
    }

    fun checkShareCurrent(expected: ReportEvidenceState.Sharing) {
        if (!viewModelScope.isActive || mutableState.value !== expected) {
            throw CancellationException("La ficha dejó de estar vigente.")
        }
    }

    /** Opening the system chooser is not evidence that a recipient received anything. */
    fun shareComplete(expected: ReportEvidenceState.Sharing) {
        mutableState.compareAndSet(expected, ReportEvidenceState.ChooserOpened(expected.request, expected.artifact))
    }

    fun shareFailure(expected: ReportEvidenceState.Sharing) {
        mutableState.compareAndSet(expected, ReportEvidenceState.Ready(expected.request, expected.artifact,
            "No se pudo abrir el selector para compartir. La ficha preparada se conserva."))
    }

    override fun onCleared() { clear() }

    companion object {
        fun factory(producer: ReportEvidenceProducer, dispatcher: CoroutineDispatcher = Dispatchers.IO): ViewModelProvider.Factory =
            viewModelFactory { initializer { ReportEvidenceViewModel(producer, dispatcher) } }
    }
}
