package com.example.citizensecurity.verification.maps

/** Datos observados del SDK; inventario del estilo y objetos presentes en el fotograma. */
data class NativeMapLayers(
    val roads: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val buildings: List<String> = emptyList(),
) {
    val isComplete: Boolean get() = roads.isNotEmpty() && labels.isNotEmpty() && buildings.isNotEmpty()
}

data class NativeRenderedFeatures(val roads: Int, val labels: Int, val buildings: Int) {
    val isComplete: Boolean get() = roads > 0 && labels > 0 && buildings > 0
}

enum class NativeMapFailure { SdkFailure, TimedOut, RequiredLayersMissing }

sealed interface NativeMapLoadState {
    data object Loading : NativeMapLoadState
    data class Ready(val layers: NativeMapLayers, val features: NativeRenderedFeatures) : NativeMapLoadState
    data class Failed(val reason: NativeMapFailure) : NativeMapLoadState
    data object Closed : NativeMapLoadState
}

/**
 * Prueba de carga conservadora: styleLoaded por sí solo nunca acredita un mapa visible.
 * El dueño serializa callbacks, timeout y close en el hilo principal; fallos y cierre son terminales.
 * No guarda mensajes de red que puedan contener datos ajenos al laboratorio.
 */
class NativeMapLoadMonitor {
    @Volatile
    var state: NativeMapLoadState = NativeMapLoadState.Loading
        private set

    var styleLoaded = false
        private set
    var mapLoaded = false
        private set
    var fullyRendered = false
        private set
    private var layers = NativeMapLayers()
    private var features = NativeRenderedFeatures(0, 0, 0)

    fun onStyleLoaded(observed: NativeMapLayers) {
        if (state != NativeMapLoadState.Loading) return
        styleLoaded = true
        layers = observed
        if (!observed.isComplete) state = NativeMapLoadState.Failed(NativeMapFailure.RequiredLayersMissing)
        else updateReady()
    }

    fun onMapLoaded() {
        if (state != NativeMapLoadState.Loading) return
        mapLoaded = true
        updateReady()
    }

    fun onFrameRendered(fully: Boolean, observed: NativeRenderedFeatures) {
        if (state != NativeMapLoadState.Loading || !fully) return
        fullyRendered = true
        features = observed
        updateReady()
    }

    fun onSdkFailure() {
        if (state == NativeMapLoadState.Loading) state = NativeMapLoadState.Failed(NativeMapFailure.SdkFailure)
    }

    fun onTimeout() {
        if (state == NativeMapLoadState.Loading) state = NativeMapLoadState.Failed(NativeMapFailure.TimedOut)
    }

    fun close() {
        state = NativeMapLoadState.Closed
    }

    private fun updateReady() {
        if (styleLoaded && mapLoaded && fullyRendered && layers.isComplete && features.isComplete) {
            state = NativeMapLoadState.Ready(layers, features)
        }
    }
}
