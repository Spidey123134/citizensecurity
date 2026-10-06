package com.example.citizensecurity.examples

import com.example.citizensecurity.domain.IncidentLocationSelection
import com.example.citizensecurity.domain.LocationConfirmation
import com.example.citizensecurity.domain.ReportLocation

/** Demostración con puntos ficticios, sin mapa, archivos ni repositorio de reportes. */
object SeleccionarUbicacion {
    @JvmStatic
    fun main(args: Array<String>) {
        val original = ReportLocation("Parque de ejemplo", 19.4326077, -99.1332088)
        val selection = IncidentLocationSelection(original)
        val proposed = selection.propose(20.123456789012345, -98.98765432109876)

        println("Ubicación original: $original")
        println("Confirmación sin seleccionar: ${selection.confirm()}")
        println("Punto provisional: ${proposed.proposal}")
        println("Ubicación original después de proponer: ${proposed.originalLocation}")
        when (val result = proposed.confirm()) {
            is LocationConfirmation.Confirmed -> println("Ubicación confirmada: ${result.location}")
            else -> error("La demostración esperaba confirmar el punto válido: $result")
        }
        println("Al cancelar se conserva: ${proposed.cancel()}")
        println("Propuesta inválida posterior: ${proposed.propose(91.0, 0.0).confirm()}")
        println("No se guardó ni consultó ningún reporte. El mapa real sigue pendiente.")
    }
}
