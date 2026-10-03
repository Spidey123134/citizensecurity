package com.example.citizensecurity.examples

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import java.time.Instant
import java.util.Locale

/** Ejemplo de consola del contrato de entrada. No forma parte del APK. */
fun main(args: Array<String>) {
    require(args.size == 5) {
        "Indica tipo, descripción, prioridad, fecha ISO UTC y ubicación escrita."
    }
    val report = NewReport(
        type = IncidentType.valueOf(args[0].uppercase(Locale.ROOT)),
        description = args[1],
        priority = Priority.valueOf(args[2].uppercase(Locale.ROOT)),
        occurredAt = Instant.parse(args[3]),
        location = ReportLocation(reference = args[4]),
    )

    println("Datos recibidos del reporte")
    println("Tipo: ${report.type}")
    println("Descripción: ${report.description}")
    println("Prioridad: ${report.priority}")
    println("Fecha del incidente (UTC): ${report.occurredAt}")
    println("Ubicación escrita: ${report.location.reference}")
    println("Este paso recibe los datos; todavía no los guarda.")
}
