package com.example.citizensecurity.maps

import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.IncidentLocationSelection
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.NearbyIncidentPolicy
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.ReportLocation

/**
 * Una selección de incidente: proponer no modifica el reporte ni obtiene ubicación del teléfono.
 * La pantalla serializa sus eventos y aplica el resultado únicamente al confirmar. Cada intento
 * consulta evidencia y tiempo actuales; un rechazo permite corregir el punto o renovar el GPS.
 * Confirmar correctamente o cancelar termina la sesión. No guarda ni consulta reportes.
 */
class IncidentMapSession(
    val originalLocation: ReportLocation,
    private val deviceLocation: () -> DeviceLocationFix?,
    private val elapsedRealtimeNanos: () -> Long,
    private val policy: NearbyIncidentPolicy = NearbyIncidentPolicy(),
) {
    private var selection = IncidentLocationSelection(originalLocation)
    private var terminalLocation: ReportLocation? = null

    var isClosed: Boolean = false
        private set

    /** Devuelve null cuando la sesión terminó; un evento tardío no vuelve a abrirla. */
    fun select(latitude: Double, longitude: Double): LocationProposal? {
        if (isClosed) return null
        selection = selection.propose(latitude, longitude)
        return selection.proposal
    }

    /** Una sesión terminada no vuelve a entregar una confirmación anterior. */
    fun confirm(): NearbyLocationConfirmation {
        if (isClosed) return NearbyLocationConfirmation.NoSelection
        val result = selection.confirmNearby(deviceLocation(), elapsedRealtimeNanos(), policy)
        if (result is NearbyLocationConfirmation.Confirmed) {
            terminalLocation = result.location
            isClosed = true
        }
        return result
    }

    /** Cancela una sesión abierta; un cierre tardío conserva el resultado ya terminado. */
    fun cancel(): ReportLocation {
        terminalLocation?.let { return it }
        val original = selection.cancel()
        terminalLocation = original
        isClosed = true
        return original
    }
}
