package com.example.citizensecurity.maps

import androidx.annotation.MainThread
import com.google.android.gms.maps.GoogleMap

/**
 * Conecta el mapa real al modelo compartido, sin crear otra sesión ni guardar un reporte.
 * Los clicks pasan por el puente de la Activity y solo se aceptan en RESUMED. La pantalla dibuja
 * el pin y los controles desde el modelo compartido. Tiene uso exclusivo del listener.
 * El dueño llama close antes de destruir la vista o reemplazar el listener. El cierre detiene
 * captura y exige renovar, pero conserva el registro de permisos durante la vida de la Activity.
 * Al recrear MapView usa ese mismo flujo; al recrear Activity registra un puente nuevo en onCreate.
 */
@MainThread
class IncidentLocationMapBinding internal constructor(
    private val clicks: IncidentMapClickHost,
    private val flow: IncidentLocationFlowBinding,
) : AutoCloseable {
    constructor(map: GoogleMap, flow: IncidentLocationFlowBinding) : this(GoogleMapClickHost(map), flow)

    private var closed = false

    init {
        clicks.setListener { latitude, longitude ->
            if (!closed) flow.select(latitude, longitude)
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
    fun setListener(listener: ((Double, Double) -> Unit)?)
}

private class GoogleMapClickHost(private val map: GoogleMap) : IncidentMapClickHost {
    override fun setListener(listener: ((Double, Double) -> Unit)?) {
        map.setOnMapClickListener(listener?.let { receive ->
            GoogleMap.OnMapClickListener { point -> receive(point.latitude, point.longitude) }
        })
    }
}
