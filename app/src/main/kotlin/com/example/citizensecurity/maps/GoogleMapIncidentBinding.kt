package com.example.citizensecurity.maps

import androidx.annotation.MainThread
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.ReportLocation
import com.google.android.gms.maps.GoogleMap

/**
 * Puente del SDK real: créalo con el GoogleMap recibido en getMapAsync, en el hilo principal.
 * La pantalla dibuja el marcador desde onProposal y aplica solo una confirmación aceptada.
 * Posee exclusivamente OnMapClickListener mientras está conectado: no registres otro listener
 * del mismo tipo hasta cerrar este puente. close lo retira con null y termina la selección.
 * El dueño de la vista llama close al abandonarla y mantiene el ciclo de MapView/MapFragment.
 * Este puente no crea vistas, pide permisos, obtiene GPS ni guarda reportes.
 */
@MainThread
class GoogleMapIncidentBinding(
    private val map: GoogleMap,
    private val session: IncidentMapSession,
    private val onProposal: (LocationProposal) -> Unit,
) : AutoCloseable {
    private var closed = session.isClosed

    private val listener = GoogleMap.OnMapClickListener { point ->
        if (!closed) {
            val proposal = session.select(point.latitude, point.longitude)
            if (proposal == null) close() else onProposal(proposal)
        }
    }

    init {
        if (!closed) map.setOnMapClickListener(listener)
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
        if (!session.isClosed) session.cancel()
        map.setOnMapClickListener(null)
    }
}
