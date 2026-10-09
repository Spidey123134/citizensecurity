package com.example.citizensecurity.report

import androidx.annotation.MainThread
import com.example.citizensecurity.maps.MapLibreMapClickHost
import com.example.citizensecurity.maps.IncidentMapClickHost
import org.maplibre.android.maps.MapLibreMap

/**
 * Adjunto que reserva [ReportLocationFlowBinding.prepareMap] antes de solicitar getMapAsync.
 * El callback usa [attachMap]: una vista reemplazada no puede adjuntar su mapa tardíamente.
 * Cerrar antes de destruir
 * la vista; un handle ya reemplazado no retira el listener ni detiene la captura del nuevo mapa.
 * No crea una sesión, conserva evidencia ni aplica coordenadas por sí mismo.
 */
@MainThread
class ReportLocationMapBinding internal constructor(
    canAttach: () -> Boolean,
    onClick: (Double, Double) -> Boolean,
    onClose: (ReportLocationMapBinding) -> Unit,
) : AutoCloseable {
    private var clicks: IncidentMapClickHost? = null
    private var mayAttach: (() -> Boolean)? = canAttach
    private var receiveClick: ((Double, Double) -> Boolean)? = onClick
    private var receiveClose: ((ReportLocationMapBinding) -> Unit)? = onClose
    private var closed = false

    /** false no modifica el listener: el callback perdió su vista, token o dueño vigente. */
    fun attachMap(map: MapLibreMap): Boolean = attachMap(MapLibreMapClickHost(map))

    internal fun attachMap(host: IncidentMapClickHost): Boolean {
        if (closed || clicks != null || mayAttach?.invoke() != true) return false
        clicks = host
        try {
            host.setListener { latitude, longitude ->
                !closed && mayAttach?.invoke() == true && receiveClick?.invoke(latitude, longitude) == true
            }
        } catch (failure: Exception) {
            try { close() } catch (closeFailure: Exception) { failure.addSuppressed(closeFailure) }
            throw failure
        }
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        val host = clicks
        val notifyClose = receiveClose
        clicks = null
        mayAttach = null
        receiveClick = null
        receiveClose = null
        try {
            host?.setListener(null)
        } finally {
            notifyClose?.invoke(this)
        }
    }
}
