package com.example.citizensecurity.maps

import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.MapRegion
import com.example.citizensecurity.domain.ReportMapMarker
import com.example.citizensecurity.domain.ReportMapQuery
import com.example.citizensecurity.domain.ReportStatus
import java.io.Closeable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Una vista posee sus callbacks y sus marcadores; nunca cierra el repositorio compartido. */
@MainThread
interface ReportMapSceneHost : Closeable {
    /** null mientras no exista una proyección utilizable de la vista y su estilo. */
    fun visibleRegion(): MapRegion?
    fun setCameraIdleListener(listener: (() -> Unit)?)
    /** Sustituye únicamente los marcadores de esta conexión, incluso si la lista está vacía. */
    fun renderMarkers(markers: List<ReportMapMarker>)
    fun clearMarkers()
}

sealed interface ReportMapSceneState {
    data object Idle : ReportMapSceneState
    data class Loading(val query: ReportMapQuery) : ReportMapSceneState
    /** hasMore indica que el límite impide presentar estos marcadores como la lista completa. */
    data class Ready(val query: ReportMapQuery, val count: Int, val hasMore: Boolean) : ReportMapSceneState
    data class Error(val query: ReportMapQuery?, val message: String) : ReportMapSceneState
    data object ViewportUnavailable : ReportMapSceneState
    data object Closed : ReportMapSceneState
}

/**
 * Conecta la consulta local de una zona a una vista ya creada, sin abrir una pantalla, guardar
 * reportes o iniciar GPS. La consulta empieza únicamente por [refreshVisible] en RESUMED.
 * Después de ese gesto, camera idle sigue sus mismos filtros; detener la vista exige otro gesto.
 * El dueño conserva el modelo con ViewModelProvider y cierra este puente antes de destruir MapView.
 * No se pinta previous: una respuesta exige tanto la consulta esperada como la proyección actual.
 * Las acciones y callbacks se serializan en el hilo principal. Cada operación tiene identidad
 * propia para que una reentrada desde el host o [onState] no continúe sobre una petición nueva.
 */
@MainThread
class ReportMapSceneBinding(
    owner: LifecycleOwner,
    private val model: ReportMapViewModel,
    private val host: ReportMapSceneHost,
    private val onState: (ReportMapSceneState) -> Unit = {},
) : Closeable {
    private val lifecycle = owner.lifecycle
    private val sceneToken = run {
        check(lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "No se puede conectar el mapa a una pantalla destruida."
        }
        model.claimScene()
    }
    private var closed = false
    private var operation: Any = Any()
    private var configuration: Configuration? = null
    private var expected: Request? = null
    private var currentState: ReportMapSceneState = ReportMapSceneState.Idle

    val state: ReportMapSceneState get() = currentState

    private val observer = LifecycleEventObserver { _, event ->
        if (!closed) when (event) {
            Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> deactivate()
            Lifecycle.Event.ON_DESTROY -> close()
            else -> Unit
        }
    }
    // Reservar el job antes de callbacks externos permite cancelarlo incluso durante el registro.
    private val collection = owner.lifecycleScope.launch(start = CoroutineStart.LAZY) {
        model.state.collect(::onModelState)
    }

    init {
        try {
            lifecycle.addObserver(observer)
            host.setCameraIdleListener(::onCameraIdle)
            if (!closed) collection.start()
        } catch (failure: Exception) {
            try { close() } catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    /**
     * true significa gesto recibido; el estado distingue carga, región no disponible y error.
     * Los filtros se copian antes de llamar al SDK. Un límite inválido se rechaza sin consultar.
     * Renovar explícitamente cancela la lectura anterior incluso si sus filtros coinciden.
     */
    fun refreshVisible(
        types: Set<IncidentType> = emptySet(),
        statuses: Set<ReportStatus> = emptySet(),
        limit: Int = ReportMapQuery.DEFAULT_LIMIT,
    ): Boolean {
        if (!isInteractive()) return false
        val config = Configuration(types, statuses, limit)
        val token = Any()
        operation = token
        configuration = config
        expected = null
        readAndLoad(token, config)
        return true
    }

    private fun onCameraIdle() {
        if (!isInteractive()) return
        val config = configuration ?: return
        val token = operation
        val region = try {
            host.visibleRegion()
        } catch (_: Exception) {
            fail(token, expected?.query)
            return
        }
        if (!isCurrent(token) || configuration !== config) return
        if (region == null) {
            viewportUnavailable(token)
            return
        }
        // Repetir un evento del SDK no renueva una zona que ya está cargando o terminada.
        if (expected?.query?.region == region) return
        val next = Any()
        operation = next
        expected = null
        load(next, config.query(region))
    }

    private fun readAndLoad(token: Any, config: Configuration) {
        val region = try {
            host.visibleRegion()
        } catch (_: Exception) {
            fail(token, null)
            return
        }
        if (!isCurrent(token) || configuration !== config) return
        if (region == null) viewportUnavailable(token) else load(token, config.query(region))
    }

    private fun load(token: Any, query: ReportMapQuery) {
        if (!isCurrent(token)) return
        val request = Request(token, query)
        expected = request
        // No mantener resultados de otra zona mientras se obtiene su sustitución.
        model.clear()
        if (!isCurrent(request)) return
        try {
            host.clearMarkers()
        } catch (_: Exception) {
            fail(token, query)
            return
        }
        if (!isCurrent(request)) return
        publish(ReportMapSceneState.Loading(query))
        if (!isCurrent(request)) return
        request.issued = true
        try {
            model.load(query)
        } catch (_: Exception) {
            fail(token, query)
        }
    }

    private fun onModelState(result: ReportMapState) {
        val request = expected ?: return
        if (!request.issued || !isCurrent(request)) return
        when (result) {
            ReportMapState.Idle -> deactivate()
            is ReportMapState.Loading -> if (result.query == request.query) {
                publish(ReportMapSceneState.Loading(request.query))
            }
            is ReportMapState.Error -> if (result.query == request.query) {
                publish(ReportMapSceneState.Error(request.query, result.message))
            }
            is ReportMapState.Ready -> if (result.query == request.query) {
                val region = try {
                    host.visibleRegion()
                } catch (_: Exception) {
                    fail(request.token, request.query)
                    return
                }
                if (!isCurrent(request)) return
                if (region != request.query.region) {
                    viewportUnavailable(request.token)
                    return
                }
                try {
                    host.renderMarkers(result.page.markers)
                } catch (_: Exception) {
                    fail(request.token, request.query)
                    return
                }
                if (!isCurrent(request)) return
                publish(ReportMapSceneState.Ready(
                    request.query, result.page.markers.size, result.page.hasMore,
                ))
            }
        }
    }

    private fun viewportUnavailable(token: Any) {
        if (!isCurrent(token)) return
        expected = null
        model.clear()
        if (!isCurrent(token)) return
        try {
            host.clearMarkers()
        } catch (_: Exception) {
            fail(token, null)
            return
        }
        if (isCurrent(token)) publish(ReportMapSceneState.ViewportUnavailable)
    }

    private fun fail(token: Any, query: ReportMapQuery?) {
        if (!isCurrent(token)) return
        expected = null
        configuration = null
        model.clear()
        if (!isCurrent(token)) return
        // Un render fallido puede haber cambiado parte de la vista; intentar retirar sus recursos.
        try { host.clearMarkers() } catch (_: Exception) { /* La identidad ya está retirada. */ }
        if (isCurrent(token)) publish(ReportMapSceneState.Error(query, MAP_ERROR))
    }

    private fun deactivate() {
        if (closed || (configuration == null && expected == null && currentState == ReportMapSceneState.Idle)) return
        val token = Any()
        operation = token
        configuration = null
        expected = null
        if (model.isSceneOwner(sceneToken)) model.clear()
        if (closed || operation !== token) return
        var failed = false
        try { host.clearMarkers() } catch (_: Exception) { failed = true }
        if (closed || operation !== token) return
        publish(
            if (failed) ReportMapSceneState.Error(null, MAP_ERROR) else ReportMapSceneState.Idle,
            notify = model.isSceneOwner(sceneToken),
        )
    }

    /** Cierra únicamente la vista y su lectura. Es idempotente incluso si el SDK falla al retirar. */
    override fun close() {
        if (closed) return
        closed = true
        operation = Any()
        configuration = null
        expected = null
        collection.cancel()
        lifecycle.removeObserver(observer)
        var failure: Exception? = null
        fun cleanup(action: () -> Unit) {
            try { action() } catch (next: Exception) {
                if (failure == null) failure = next else failure.addSuppressed(next)
            }
        }
        // Notificar mientras aún somos el dueño; el callback puede crear la vista siguiente.
        // releaseScene comprueba otra vez el token y nunca limpia esa vista nueva.
        cleanup { publish(ReportMapSceneState.Closed, notify = model.isSceneOwner(sceneToken)) }
        cleanup { model.releaseScene(sceneToken) }
        cleanup { host.setCameraIdleListener(null) }
        cleanup(host::clearMarkers)
        cleanup(host::close)
        failure?.let { throw it }
    }

    private fun publish(next: ReportMapSceneState, notify: Boolean = true) {
        if (next == currentState) return
        currentState = next
        if (notify) onState(next)
    }

    private fun isInteractive() = !closed && model.isSceneOwner(sceneToken) &&
        lifecycle.currentState == Lifecycle.State.RESUMED
    private fun isCurrent(token: Any) = isInteractive() && operation === token
    private fun isCurrent(request: Request) = expected === request && isCurrent(request.token)

    private class Request(val token: Any, val query: ReportMapQuery) {
        var issued = false
    }

    private class Configuration(types: Set<IncidentType>, statuses: Set<ReportStatus>, val limit: Int) {
        private val types = types.toSet()
        private val statuses = statuses.toSet()
        init {
            require(limit in 1..ReportMapQuery.MAX_LIMIT) {
                "La consulta del mapa admite entre 1 y ${ReportMapQuery.MAX_LIMIT} marcadores."
            }
        }
        fun query(region: MapRegion) = ReportMapQuery(region, types, statuses, limit)
    }

    private companion object {
        const val MAP_ERROR = "No se pudieron mostrar los incidentes de esta zona. Intenta nuevamente."
    }
}
