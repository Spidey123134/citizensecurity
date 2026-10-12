package com.example.citizensecurity.report

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportStatus
import java.text.Normalizer
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** A validated reading filter. Its date boundaries refer to incident time, never creation time. */
class ReportSearchFilters internal constructor(
    val type: IncidentType?,
    val status: ReportStatus?,
    val normalizedText: String,
    val startInclusive: Instant?,
    val endExclusive: Instant?,
)

sealed interface ReportSearchFilterValidation {
    data class Valid(val filters: ReportSearchFilters) : ReportSearchFilterValidation
    data class Invalid(val message: String) : ReportSearchFilterValidation
}

private val incidentDateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)
private val incidentDateShape = Regex("[0-9]{2}/[0-9]{2}/[0-9]{4}")
private val combiningMarks = Regex("\\p{M}+")
private val searchWhitespace = Regex("[\\p{Z}\\s]+")

/**
 * Parses both independently optional dates before a query can run. The final date includes
 * the whole local calendar day; moving to the next date also respects short/long DST days.
 */
fun parseReportSearchFilters(
    text: String = "",
    startDate: String = "",
    endDate: String = "",
    zoneId: ZoneId,
    type: IncidentType? = null,
    status: ReportStatus? = null,
): ReportSearchFilterValidation {
    val startInput = trimSearchWhitespace(startDate)
    val endInput = trimSearchWhitespace(endDate)
    val start = if (startInput.isEmpty()) null else {
        parseIncidentDate(startInput) ?: return ReportSearchFilterValidation.Invalid(
            "Fecha inicial inválida. Usa el formato dd/MM/aaaa.",
        )
    }
    val end = if (endInput.isEmpty()) null else {
        parseIncidentDate(endInput) ?: return ReportSearchFilterValidation.Invalid(
            "Fecha final inválida. Usa el formato dd/MM/aaaa.",
        )
    }
    if (start != null && end != null && start > end) {
        return ReportSearchFilterValidation.Invalid(
            "La fecha inicial debe ser anterior o igual a la fecha final.",
        )
    }
    return try {
        ReportSearchFilterValidation.Valid(
            ReportSearchFilters(
                type = type,
                status = status,
                normalizedText = normalizeSearchText(text),
                startInclusive = start?.atStartOfDay(zoneId)?.toInstant(),
                endExclusive = end?.plusDays(1)?.atStartOfDay(zoneId)?.toInstant(),
            ),
        )
    } catch (_: DateTimeException) {
        ReportSearchFilterValidation.Invalid("No se pudo interpretar el rango de fechas.")
    }
}

/** Filters the immutable reading in its existing order, without SQLite, GPS or any writes. */
fun filterLocalReports(reports: List<Report>, filters: ReportSearchFilters): List<Report> =
    reports.filter { report ->
        (filters.type == null || report.type == filters.type) &&
            (filters.status == null || report.status == filters.status) &&
            (filters.startInclusive == null || report.occurredAt >= filters.startInclusive) &&
            (filters.endExclusive == null || report.occurredAt < filters.endExclusive) &&
            (filters.normalizedText.isEmpty() ||
                normalizeSearchText(report.description).contains(filters.normalizedText) ||
                normalizeSearchText(report.location.reference).contains(filters.normalizedText))
    }

private fun parseIncidentDate(input: String): LocalDate? {
    if (!incidentDateShape.matches(input)) return null
    return try {
        LocalDate.parse(input, incidentDateFormat).takeIf { it.year >= 1 }
    } catch (_: DateTimeException) {
        null
    }
}

private fun normalizeSearchText(input: String): String = searchWhitespace.replace(
    combiningMarks.replace(Normalizer.normalize(input, Normalizer.Form.NFD), "")
        .lowercase(Locale.ROOT),
    " ",
).trim()

private fun trimSearchWhitespace(input: String): String = input.trim {
    it.isWhitespace() || Character.isSpaceChar(it)
}
