package com.example.citizensecurity.examples

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Tres entradas reproducibles; la fecha del incidente usa el mismo reloj que la validación. */
object ValidarUbicaciones {
    @JvmStatic
    fun main(args: Array<String>) {
        val clock = Clock.fixed(Instant.parse("2026-10-02T23:30:00Z"), ZoneOffset.UTC)
        val now = clock.instant()
        val cases = listOf(
            "Punto completo sin referencia escrita" to
                ReportLocation(latitude = 19.4326077, longitude = -99.1332088),
            "Punto incompleto" to ReportLocation(latitude = 19.4326077),
            "Punto fuera de rango" to ReportLocation(latitude = 91.0, longitude = -99.1332088),
        )

        cases.forEach { (name, location) ->
            println(name)
            println("Latitud: ${location.latitude ?: "Sin proporcionar"}")
            println("Longitud: ${location.longitude ?: "Sin proporcionar"}")
            showValidation(
                NewReport(
                    type = IncidentType.RISK,
                    description = "Hay una luminaria dañada frente al parque.",
                    priority = Priority.MEDIUM,
                    occurredAt = now,
                    location = location,
                ),
                now,
            )
            println()
        }
        println("Estos ejemplos validan puntos; todavía no guardan reportes.")
    }
}
