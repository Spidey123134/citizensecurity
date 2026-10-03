package com.example.citizensecurity.examples

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportValidator
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

/** Ejemplo de consola; reutiliza las reglas del dominio y no forma parte del APK. */
object ValidarReporte {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 7) {
            println("Entrada: indica tipo, descripción, prioridad, fecha ISO UTC, " +
                "ubicación escrita, latitud y longitud. Las coordenadas pueden quedar vacías.")
            return
        }

        val now = Clock.systemUTC().instant()
        val inputErrors = linkedMapOf<String, String>()
        val type = IncidentType.entries.firstOrNull {
            it.name == args[0].trim().uppercase(Locale.ROOT)
        }
        if (type == null) {
            inputErrors["Tipo"] = "Elige ${IncidentType.entries.joinToString { it.name }}."
        }
        val priority = Priority.entries.firstOrNull {
            it.name == args[2].trim().uppercase(Locale.ROOT)
        }
        if (priority == null) {
            inputErrors["Prioridad"] = "Elige ${Priority.entries.joinToString { it.name }}."
        }
        val occurredAt = if (args[3].isBlank()) {
            now
        } else {
            try {
                Instant.parse(args[3].trim())
            } catch (_: DateTimeParseException) {
                inputErrors["Fecha del incidente"] =
                    "Usa una fecha ISO UTC, por ejemplo 2026-10-02T23:30:00Z."
                null
            }
        }
        val latitude = parseCoordinate(args[5], "Latitud", inputErrors)
        val longitude = parseCoordinate(args[6], "Longitud", inputErrors)

        if (inputErrors.isNotEmpty()) {
            println("Datos de entrada con errores:")
            inputErrors.forEach { (field, message) -> println("$field: $message") }
            println("Corrige los datos para validar el reporte. No se guardó ningún reporte.")
            return
        }

        val report = NewReport(
            type = checkNotNull(type),
            description = args[1],
            priority = checkNotNull(priority),
            occurredAt = checkNotNull(occurredAt),
            location = ReportLocation(
                reference = args[4],
                latitude = latitude,
                longitude = longitude,
            ),
        )
        println("Fecha del incidente (UTC): ${report.occurredAt}")
        println("Ubicación escrita: ${report.location.reference.ifBlank { "Sin referencia escrita" }}")
        println("Latitud: ${report.location.latitude ?: "Sin proporcionar"}")
        println("Longitud: ${report.location.longitude ?: "Sin proporcionar"}")
        showValidation(report, now)
        println("Este paso valida los datos; todavía no guarda el reporte.")
    }

    private fun parseCoordinate(
        text: String,
        field: String,
        errors: MutableMap<String, String>,
    ): Double? {
        if (text.isBlank()) return null
        return text.trim().toDoubleOrNull().also { number ->
            if (number == null) {
                errors[field] = "Usa un número decimal con punto, por ejemplo 19.4326."
            }
        }
    }
}

internal fun showValidation(report: NewReport, now: Instant) {
    val result = ReportValidator().validate(report, now)
    if (result.isValid) {
        println("Reporte válido.")
    } else {
        println("Reporte con errores:")
        result.errors.forEach { (field, message) ->
            val fieldName = when (field) {
                ReportField.DESCRIPTION -> "Descripción"
                ReportField.LOCATION_REFERENCE -> "Ubicación escrita"
                ReportField.LOCATION_COORDINATES -> "Coordenadas"
                ReportField.OCCURRED_AT -> "Fecha del incidente"
            }
            println("$fieldName: $message")
        }
    }
}
