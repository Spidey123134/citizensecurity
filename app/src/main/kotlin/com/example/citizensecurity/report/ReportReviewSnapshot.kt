package com.example.citizensecurity.report

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Datos de lectura para revisar un borrador. No valida cercanía, concede permisos ni guarda datos.
 * El instante se muestra en la zona elegida; una pareja ausente nunca se sustituye por 0, 0.
 */
internal data class ReportReviewSnapshot(
    val type: IncidentType,
    val priority: Priority,
    val description: String,
    val occurredAtText: String,
    val timeZoneText: String,
    val locationReference: String,
    val latitudeText: String?,
    val longitudeText: String?,
) {
    companion object {
        fun from(draft: NewReport, locale: Locale, timeZone: ZoneId): ReportReviewSnapshot {
            val latitude = draft.location.latitude
            val longitude = draft.location.longitude
            val completePoint = latitude != null && longitude != null
            val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(timeZone)
            return ReportReviewSnapshot(
                type = draft.type,
                priority = draft.priority,
                description = draft.description,
                occurredAtText = date.format(draft.occurredAt),
                timeZoneText = timeZone.id,
                locationReference = draft.location.reference,
                latitudeText = if (completePoint) String.format(locale, "%.6f", latitude) else null,
                longitudeText = if (completePoint) String.format(locale, "%.6f", longitude) else null,
            )
        }
    }
}
