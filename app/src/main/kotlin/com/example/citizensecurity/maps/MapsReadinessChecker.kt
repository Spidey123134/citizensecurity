package com.example.citizensecurity.maps

import kotlinx.coroutines.CancellationException

sealed interface MapsReadiness {
    data object Ready : MapsReadiness
    data object RendererUnavailable : MapsReadiness
    data object VerificationFailed : MapsReadiness
}

/** Inicialización local del SDK; Ready no acredita red, estilo cargado ni mapa renderizado. */
class MapsReadinessChecker(
    private val initializeMapSdk: () -> Unit,
) {
    fun check(): MapsReadiness = try {
        initializeMapSdk()
        MapsReadiness.Ready
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: LinkageError) {
        MapsReadiness.RendererUnavailable
    } catch (_: Exception) {
        MapsReadiness.VerificationFailed
    }
}
