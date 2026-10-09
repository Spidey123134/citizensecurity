package com.example.citizensecurity.maps

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.ReportMapPage
import com.example.citizensecurity.domain.ReportMapQuery
import com.example.citizensecurity.domain.ReportMapRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ReportMapState {
    data object Idle : ReportMapState

    /** [previous] conserva su propia zona; no es el resultado de [query]. */
    data class Loading(val query: ReportMapQuery, val previous: Ready? = null) : ReportMapState

    data class Ready(val query: ReportMapQuery, val page: ReportMapPage) : ReportMapState

    /** Fallar no convierte la última consulta correcta en datos de otra zona. */
    data class Error(
        val query: ReportMapQuery,
        val message: String,
        val previous: Ready? = null,
    ) : ReportMapState
}

/**
 * Consulta marcadores locales únicamente al llamar [load]. No captura GPS ni guarda reportes.
 * La pantalla serializa sus acciones en el hilo principal. Una zona nueva cancela la petición
 * anterior; su identidad impide publicar respuestas tardías aunque el lector ignore cancelación.
 * [clear] descarta solicitudes y resultados al abandonar la consulta, sin cerrar el repositorio.
 */
@MainThread
class ReportMapViewModel(private val repository: ReportMapRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportMapState>(ReportMapState.Idle)
    val state: StateFlow<ReportMapState> = mutableState.asStateFlow()

    private var activeRequest: Request? = null
    private var queryJob: Job? = null
    private var lastReady: ReportMapState.Ready? = null
    private var cleared = false
    // Identidad pura: una vista reemplazada no cancela la lectura de su sucesora. No retiene
    // LifecycleOwner, MapView, binding ni callbacks y no sobrevive a la vida de este modelo.
    private var sceneOwner: Any? = null

    internal fun claimScene(): Any {
        val token = Any()
        sceneOwner = token
        // Reservar primero el dueño hace inertes los observadores de la escena anterior.
        clear()
        return token
    }

    internal fun isSceneOwner(token: Any): Boolean = !cleared && sceneOwner === token

    internal fun releaseScene(token: Any) {
        if (!isSceneOwner(token)) return
        sceneOwner = null
        clear()
    }

    /** Repetir una consulta terminada renueva los datos; repetir una pendiente no abre otra. */
    fun load(query: ReportMapQuery) {
        val scope = viewModelScope
        if (cleared || !scope.isActive || activeRequest?.query == query) return

        val request = Request(query)
        // Retirar primero la identidad evita que cancelar la anterior modifique este estado.
        activeRequest = request
        queryJob?.cancel()

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val page = repository.queryMarkers(query)
                ensureActive()
                if (!isCurrent(request)) return@launch
                val ready = ReportMapState.Ready(query, page)
                lastReady = ready
                mutableState.value = ready
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                if (!isCurrent(request)) return@launch
                mutableState.value = ReportMapState.Error(
                    query,
                    "No se pudieron consultar los incidentes de esta zona. Intenta nuevamente.",
                    lastReady,
                )
            }
        }
        queryJob = job
        job.invokeOnCompletion { cause ->
            // También retira Loading cuando se cancela antes de iniciar la corrutina.
            if (isCurrent(request)) {
                activeRequest = null
                queryJob = null
                if (cause is CancellationException) {
                    mutableState.value = lastReady ?: ReportMapState.Idle
                }
            }
        }
        if (!isCurrent(request)) {
            job.cancel()
            return
        }
        // Un consumidor inmediato puede limpiar o cargar otra zona al observar Loading.
        // La tarea ya reservada permite que esa reentrada la cancele antes de leer.
        mutableState.value = ReportMapState.Loading(query, lastReady)
        if (isCurrent(request)) job.start() else job.cancel()
    }

    fun clear() {
        activeRequest = null
        queryJob?.cancel()
        queryJob = null
        lastReady = null
        mutableState.value = ReportMapState.Idle
    }

    override fun onCleared() {
        cleared = true
        sceneOwner = null
        clear()
    }

    private fun isCurrent(request: Request): Boolean = !cleared && activeRequest === request

    private class Request(val query: ReportMapQuery)

    companion object {
        fun factory(repository: ReportMapRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportMapViewModel(repository) }
        }
    }
}
