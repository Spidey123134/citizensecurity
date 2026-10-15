package com.example.citizensecurity.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/** A reviewed immutable message; preparing it never sends it or changes the report. */
data class ReportMessageDraft(val reportId: String, val contact: TrustedContact, val body: String) {
    init {
        require(TrustedContact.canonicalUuid(reportId)) { "El mensaje necesita un folio completo y canónico." }
        require(body.length in 1..MAX_BODY_LENGTH) { "El mensaje supera el límite de 3500 caracteres de texto." }
        requireMessageText(body, MAX_BODY_LENGTH, "mensaje")
        require(body.startsWith("${ReportMessageFormatter.TITLE}\nFolio: $reportId\n") &&
            body.endsWith(ReportMessageFormatter.LOCAL_NOTICE)) {
            "El mensaje no corresponde al folio y aviso local indicados."
        }
    }

    companion object {
        /** UTF-16 bound, including all labels and URLs, well below the Android Intent limit. */
        const val MAX_BODY_LENGTH = 3_500
    }
}

/** Pure composition from saved data. No clock, network, GPS or recipient information in the body. */
object ReportMessageFormatter {
    const val TITLE = "Registro local de CitizenSecurity"
    const val LOCAL_NOTICE = "Guardar este reporte no confirma su recepción por una autoridad."

    fun prepare(
        report: Report,
        contact: TrustedContact,
        includeLocation: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): ReportMessageDraft {
        require(TrustedContact.canonicalUuid(report.id)) { "El mensaje necesita un folio completo y canónico." }
        requireMessageText(report.description, 1_000, "descripción")
        require(report.description.isNotBlank()) { "La descripción guardada está vacía." }
        requireMessageText(report.location.reference, 200, "referencia")
        require(coordinateError(report.location.latitude, report.location.longitude) == null) {
            "Las coordenadas guardadas están incompletas o no son válidas."
        }
        require(!report.occurredAt.isBefore(Instant.EPOCH) && !report.createdAt.isBefore(Instant.EPOCH)) {
            "Las fechas guardadas deben ser iguales o posteriores al 1 de enero de 1970."
        }
        // Validate both saved dates without inventing a new timestamp or a chronology constraint.
        val occurredAt = runCatching { report.occurredAt.atZone(zoneId) }.getOrElse {
            throw IllegalArgumentException("La fecha del incidente no se puede representar.", it)
        }
        runCatching { report.createdAt.atZone(zoneId) }.getOrElse {
            throw IllegalArgumentException("La fecha de guardado no se puede representar.", it)
        }
        val date = DATE_FORMAT.format(occurredAt)
        val body = buildString {
            append(TITLE).append('\n')
            append("Folio: ").append(report.id).append('\n')
            append("Tipo: ").append(typeLabel(report.type)).append('\n')
            append("Prioridad indicada por el autor: ").append(priorityLabel(report.priority)).append('\n')
            append("Estado local: ").append(statusLabel(report.status)).append('\n')
            append("Fecha del incidente: ").append(date).append(" [").append(zoneId.id).append("]\n")
            append("Descripción:\n").append(report.description).append('\n')
            if (includeLocation) {
                if (report.location.reference.isNotEmpty()) {
                    append("Referencia de ubicación: ").append(report.location.reference).append('\n')
                }
                val latitude = report.location.latitude
                val longitude = report.location.longitude
                if (latitude != null && longitude != null) {
                    // Double.toString uses an ASCII decimal point regardless of the device locale.
                    append("Coordenadas: ").append(latitude).append(", ").append(longitude).append('\n')
                    append("Mapa: https://www.openstreetmap.org/?mlat=").append(latitude)
                        .append("&mlon=").append(longitude).append("#map=18/")
                        .append(latitude).append('/').append(longitude).append('\n')
                }
            }
            append(LOCAL_NOTICE)
        }
        return ReportMessageDraft(report.id, contact, body)
    }

    private val DATE_FORMAT = DateTimeFormatterBuilder()
        .appendPattern("dd/MM/uuuu HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
        .appendLiteral(' ')
        .appendOffset("+HH:MM:ss", "+00:00")
        .toFormatter(Locale.forLanguageTag("es-MX"))

    private fun typeLabel(type: IncidentType): String = when (type) {
        IncidentType.EMERGENCY -> "Emergencia"
        IncidentType.THEFT -> "Robo"
        IncidentType.ACCIDENT -> "Accidente"
        IncidentType.FIRE -> "Incendio"
        IncidentType.RISK -> "Riesgo"
        IncidentType.OTHER -> "Otro"
    }

    private fun priorityLabel(priority: Priority): String = when (priority) {
        Priority.LOW -> "Baja"
        Priority.MEDIUM -> "Media"
        Priority.HIGH -> "Alta"
    }

    private fun statusLabel(status: ReportStatus): String = when (status) {
        ReportStatus.REPORTED -> "Guardado localmente"
        ReportStatus.IN_REVIEW -> "En revisión"
        ReportStatus.ATTENDED -> "Atendido"
        ReportStatus.CLOSED -> "Cerrado"
    }
}

/** Match saved evidence text rules: preserve valid whitespace and Unicode without truncating. */
private fun requireMessageText(value: String, maximum: Int, label: String) {
    require(value.codePointCount(0, value.length) <= maximum) { "La $label supera el límite del mensaje." }
    var index = 0
    while (index < value.length) {
        val character = value[index]
        require(character != '\u0000' && (!Character.isISOControl(character) || character in "\n\r\t")) {
            "La $label contiene un carácter que no se puede representar."
        }
        if (character.isHighSurrogate()) {
            require(index + 1 < value.length && value[index + 1].isLowSurrogate()) {
                "La $label contiene un carácter Unicode incompleto."
            }
            index += 2
        } else {
            require(!character.isLowSurrogate()) { "La $label contiene un carácter Unicode incompleto." }
            index++
        }
    }
}
