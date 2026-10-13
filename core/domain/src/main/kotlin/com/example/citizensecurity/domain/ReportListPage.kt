package com.example.citizensecurity.domain

import java.time.Instant
import java.util.Collections

/** Posición exclusiva en el orden de guardado descendente, con folio como desempate. */
data class ReportListCursor(val createdAt: Instant, val id: String) {
    init {
        require(id.isNotBlank() && id.length <= 64) {
            "El cursor necesita un folio no vacío de hasta 64 caracteres."
        }
    }

    companion object {
        fun from(report: Report): ReportListCursor = ReportListCursor(report.createdAt, report.id)
    }
}

/**
 * Lectura acotada de candidatos locales. Las fechas filtran el instante del incidente;
 * el cursor continúa por el instante de guardado. Un intervalo igual es vacío y válido.
 * El texto se normaliza y compara fuera de SQLite para conservar su semántica literal.
 */
data class ReportListPageQuery(
    val type: IncidentType? = null,
    val status: ReportStatus? = null,
    val priority: Priority? = null,
    val startInclusive: Instant? = null,
    val endExclusive: Instant? = null,
    val before: ReportListCursor? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(limit in 1..MAX_LIMIT) { "La consulta admite entre 1 y $MAX_LIMIT reportes." }
        require(startInclusive == null || endExclusive == null || startInclusive <= endExclusive) {
            "El inicio del intervalo no puede superar su final."
        }
    }

    companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 100
    }
}

/** hasMore indica que quedan candidatos; una página no representa todo el historial. */
class ReportListPage(reports: List<Report>, val hasMore: Boolean) {
    val reports: List<Report> = Collections.unmodifiableList(reports.toList())

    override fun equals(other: Any?): Boolean = other is ReportListPage &&
        reports == other.reports && hasMore == other.hasMore

    override fun hashCode(): Int = 31 * reports.hashCode() + hasMore.hashCode()
    override fun toString(): String = "ReportListPage(reports=$reports, hasMore=$hasMore)"
}

/** Consulta local sin GPS ni escrituras; continúa estrictamente después del cursor recibido. */
fun interface ReportListPageRepository {
    suspend fun listPage(query: ReportListPageQuery): ReportListPage
}
