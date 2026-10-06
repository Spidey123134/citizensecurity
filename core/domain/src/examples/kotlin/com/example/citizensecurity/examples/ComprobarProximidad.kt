package com.example.citizensecurity.examples

import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.IncidentLocationSelection
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.ReportLocation

/** Evidencia ficticia para observar la regla; no lee GPS ni permite guardar reportes reales. */
object ComprobarProximidad {
    @JvmStatic
    fun main(args: Array<String>) {
        val now = 300_000_000_000L
        val phoneInMexico = DeviceLocationFix(19.4326077, -99.1332088, 20.0, now, false)
        val selection = IncidentLocationSelection(ReportLocation("Punto de ejemplo"))
        show("Incidente cercano en México", selection.propose(19.434, -99.132).confirmNearby(phoneInMexico, now))
        val paris = selection.propose(48.8566, 2.3522)
        show("París con el teléfono en México", paris.confirmNearby(phoneInMexico, now))
        show("París con el teléfono cerca de París", paris.confirmNearby(
            DeviceLocationFix(48.8567, 2.3523, 20.0, now, false), now,
        ))
        show("Sin ubicación del teléfono", selection.propose(19.434, -99.132).confirmNearby(null, now))
        show("Ubicación antigua", selection.propose(19.434, -99.132).confirmNearby(
            phoneInMexico, now + 120_000_000_001L,
        ))
        show("Ubicación marcada como simulada", selection.propose(19.434, -99.132).confirmNearby(
            phoneInMexico.copy(isMock = true), now,
        ))
        println("Radio: 5 km. Precisión máxima: 100 m. Vigencia: 2 minutos. No se guardó ningún reporte.")
    }

    private fun show(name: String, result: NearbyLocationConfirmation) {
        when (result) {
            is NearbyLocationConfirmation.Confirmed -> println("$name: permitido (${result.distanceMeters} m).")
            is NearbyLocationConfirmation.Blocked -> println("$name: rechazado. ${result.rejection.message}")
            else -> error("La demostración esperaba una decisión de proximidad: $result")
        }
    }
}
