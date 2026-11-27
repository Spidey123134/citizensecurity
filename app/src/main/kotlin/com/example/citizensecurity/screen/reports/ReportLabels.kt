package com.example.citizensecurity.screen.reports

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun typeLabel(type: IncidentType): String = when (type) {
    IncidentType.EMERGENCY -> "Emergencia"
    IncidentType.THEFT -> "Robo"
    IncidentType.ACCIDENT -> "Accidente"
    IncidentType.FIRE -> "Incendio"
    IncidentType.RISK -> "Riesgo"
    IncidentType.OTHER -> "Otro"
}

internal fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.LOW -> "Baja"
    Priority.MEDIUM -> "Media"
    Priority.HIGH -> "Alta"
}

internal fun statusLabel(status: ReportStatus): String = when (status) {
    ReportStatus.REPORTED -> "Guardado localmente"
    ReportStatus.IN_REVIEW -> "En revisión"
    ReportStatus.ATTENDED -> "Atendido"
    ReportStatus.CLOSED -> "Cerrado"
}

internal fun formatReportTime(instant: Instant, zone: ZoneId): String = DateTimeFormatter
    .ofPattern("dd/MM/yyyy HH:mm:ss z").withZone(zone).format(instant)
