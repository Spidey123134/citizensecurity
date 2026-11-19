package com.example.citizensecurity.screen

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportValidator
import com.example.citizensecurity.domain.ValidationErrors
import java.time.Instant

internal enum class LocationExample(val title: String, val location: ReportLocation) {
    COMPLETE("Coordenadas completas", ReportLocation(latitude = 19.4326077, longitude = -99.1332088)),
    INCOMPLETE("Par incompleto", ReportLocation(latitude = 19.4326077)),
    OUT_OF_RANGE("Fuera de rango", ReportLocation(latitude = 91.0, longitude = -99.1332088));

    fun validate(): ValidationErrors {
        val draft = NewReport(
            type = IncidentType.RISK,
            priority = Priority.MEDIUM,
            description = "Hay una luminaria dañada frente al parque.",
            occurredAt = EXAMPLE_TIME,
            location = location,
        )
        return ReportValidator().validate(draft, EXAMPLE_TIME)
    }
}

private val EXAMPLE_TIME = Instant.parse("2026-10-02T23:30:00Z")
