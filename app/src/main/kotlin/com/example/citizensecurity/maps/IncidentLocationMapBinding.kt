package com.example.citizensecurity.maps

import androidx.annotation.MainThread
import org.maplibre.android.maps.MapLibreMap

/**
 * Conecta el mapa real al modelo compartido, sin crear otra sesión ni guardar un reporte.
 * Los clicks pasan por el puente de la Activity y solo se aceptan en RESUMED. La pantalla dibuja
 * el pin y los controles desde el modelo compartido. Registra y retira solo su propio listener.
 * El dueño llama close antes de destruir la vista. El cierre detiene
 * captura y exige renovar, pero conserva el registro de permisos durante la vida de la Activity.
 * Al recrear MapView usa ese mismo flujo; al recrear Activity registra un puente nuevo en onCreate.
 */
@MainThread
class IncidentLocationMapBinding internal constructor(
    private val clicks: IncidentMapClickHost,
    private val flow: IncidentLocationFlowBinding,
) : AutoCloseable {
    constructor(map: MapLibreMap, flow: IncidentLocationFlowBinding) : this(MapLibreMapClickHost(map), flow)

    private var closed = false

    init {
        try {
            clicks.setListener { latitude, longitude ->
                !closed && flow.select(latitude, longitude)
            }
        } catch (failure: Exception) {
            try { close() } catch (closeFailure: Exception) { failure.addSuppressed(closeFailure) }
            throw failure
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            clicks.setListener(null)
        } finally {
            flow.onMapViewClosed()
        }
    }
}

/** Adaptador mínimo del listener: los tests controlan clicks sin fingir un mapa renderizado. */
internal fun interface IncidentMapClickHost {
    /** El callback devuelve true únicamente si entregó el gesto a su selección vigente. */
    fun setListener(listener: ((Double, Double) -> Boolean)?)
}
