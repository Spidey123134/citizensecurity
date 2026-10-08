package com.example.citizensecurity.maps

import androidx.annotation.MainThread
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.ReportLocation
import org.maplibre.android.maps.MapLibreMap

/**
 * Puente simple del SDK, separado del flujo completo de [IncidentLocationMapBinding].
 * Crear con el MapLibreMap recibido en getMapAsync, en el hilo principal. La pantalla dibuja
 * el marcador desde onProposal y aplica solo una confirmación cercana aceptada.
 * Cerrar antes de destruir la vista: retira únicamente su listener y termina la selección.
 * No crea vistas, pide permisos, obtiene GPS ni guarda reportes.
 */
@MainThread
class MapLibreIncidentBinding internal constructor(
    private val clicks: IncidentMapClickHost,
    private val session: IncidentMapSession,
    private val onProposal: (LocationProposal) -> Unit,
) : AutoCloseable {
    constructor(
        map: MapLibreMap,
        session: IncidentMapSession,
        onProposal: (LocationProposal) -> Unit,
    ) : this(MapLibreMapClickHost(map), session, onProposal)

    private var closed = session.isClosed

    init {
        if (!closed) try {
            clicks.setListener { latitude, longitude ->
                if (closed) false else {
                    val proposal = session.select(latitude, longitude)
                    if (proposal == null) {
                        close()
                        false
                    } else {
                        onProposal(proposal)
                        true
                    }
                }
            }
        } catch (failure: Exception) {
            try { close() } catch (closeFailure: Exception) { failure.addSuppressed(closeFailure) }
            throw failure
        }
    }

    fun confirm(): NearbyLocationConfirmation {
        val result = session.confirm()
        if (session.isClosed) close()
        return result
    }

    fun cancel(): ReportLocation {
        val finalLocation = session.cancel()
        close()
        return finalLocation
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            if (!session.isClosed) session.cancel()
        } finally {
            clicks.setListener(null)
        }
    }
}
