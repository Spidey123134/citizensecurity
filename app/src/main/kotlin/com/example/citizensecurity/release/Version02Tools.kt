package com.example.citizensecurity.release

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportLocation
import java.time.Instant
import java.time.format.DateTimeParseException

/** Acceso público para la demostración académica; no crea sesiones ni roles administrativos. */
object DemoLogin {
    fun accepts(username: String, password: String): Boolean =
        username.trim() == "admin" && password == "admin"
}

data class ReportInput(
    val type: IncidentType,
    val priority: Priority,
    val description: String,
    val occurredAt: String,
    val reference: String,
    val latitude: String,
    val longitude: String,
)

sealed interface ReportInputResult {
    data class Received(val draft: NewReport) : ReportInputResult
    data class Invalid(val errors: Map<ReportField, String>) : ReportInputResult
}

/** Conversión de la entrada de las herramientas, sin persistencia ni acceso a servicios. */
object Version02Tools {
    fun receive(input: ReportInput): ReportInputResult {
        val errors = linkedMapOf<ReportField, String>()
        val occurredAt = try {
            Instant.parse(input.occurredAt.trim())
        } catch (_: DateTimeParseException) {
            errors[ReportField.OCCURRED_AT] = "Escribe la fecha en formato UTC, por ejemplo 2026-10-03T12:00:00Z."
            null
        }
        fun coordinate(value: String): Double? {
            val text = value.trim()
            if (text.isEmpty()) return null
            return text.toDoubleOrNull().also {
                if (it == null) errors[ReportField.LOCATION_COORDINATES] =
                    "Escribe las coordenadas como números; usa punto decimal."
            }
        }
        val latitude = coordinate(input.latitude)
        val longitude = coordinate(input.longitude)
        if (errors.isNotEmpty()) return ReportInputResult.Invalid(errors)
        return ReportInputResult.Received(
            NewReport(
                input.type,
                input.priority,
                input.description,
                checkNotNull(occurredAt),
                ReportLocation(input.reference, latitude, longitude),
            ),
        )
    }
}
