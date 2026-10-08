package com.example.citizensecurity.maps

import androidx.annotation.MainThread
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.ReportLocation
import org.maplibre.android.maps.MapLibreMap

/**
 * Nombre anterior conservado para el código del equipo; delega al SDK MapLibre actual.
 * Las nuevas conexiones usan [MapLibreIncidentBinding]. No incorpora el SDK de Google.
 */
@MainThread
@Deprecated("Usa MapLibreIncidentBinding con MapLibreMap.")
class GoogleMapIncidentBinding(
    map: MapLibreMap,
    session: IncidentMapSession,
    onProposal: (LocationProposal) -> Unit,
) : AutoCloseable {
    private val delegate = MapLibreIncidentBinding(map, session, onProposal)

    fun confirm(): NearbyLocationConfirmation = delegate.confirm()
    fun cancel(): ReportLocation = delegate.cancel()
    override fun close() = delegate.close()
}
