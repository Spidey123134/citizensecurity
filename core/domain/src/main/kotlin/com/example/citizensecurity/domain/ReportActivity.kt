package com.example.citizensecurity.domain

import java.time.Instant
import java.util.Collections

enum class ReportActivityFilter { ALL, STATUS_CHANGES }

/** Exclusive position in the recorded local chronology; never a notification receipt. */
data class ReportActivityCursor(val recordedAt: Instant, val operationId: String) {
    init { requireCanonicalReportHistoryId(operationId) }
}

data class ReportActivityQuery(
    val filter: ReportActivityFilter = ReportActivityFilter.ALL,
    val before: ReportActivityCursor? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    init { require(limit in 1..MAX_LIMIT) { "La actividad admite entre 1 y $MAX_LIMIT eventos." } }
    companion object { const val DEFAULT_LIMIT = 50; const val MAX_LIMIT = 100 }
}

data class ReportActivityEntry(
    val reportId: String,
    val incidentType: IncidentType,
    val event: ReportStatusEvent,
) {
    init { requireCanonicalReportHistoryId(reportId) }
}

/** Bounded snapshot of existing events only. Old reports may have no recorded events. */
class ReportActivityPage(entries: List<ReportActivityEntry>, val hasMore: Boolean) {
    val entries: List<ReportActivityEntry> = Collections.unmodifiableList(entries.toList())
    init {
        require(this.entries.size <= ReportActivityQuery.MAX_LIMIT)
        require(!hasMore || this.entries.isNotEmpty())
        require(this.entries.map { it.event.operationId }.toSet().size == this.entries.size)
        require(this.entries.zipWithNext().all { (a, b) ->
            a.event.recordedAt > b.event.recordedAt ||
                (a.event.recordedAt == b.event.recordedAt && a.event.operationId > b.event.operationId)
        }) { "La actividad debe mantener su orden de fecha y operación sin duplicados." }
    }
    val nextBefore: ReportActivityCursor? get() = if (hasMore) entries.last().event.let {
        ReportActivityCursor(it.recordedAt, it.operationId)
    } else null
}

fun interface ReportActivityRepository {
    suspend fun readActivity(query: ReportActivityQuery): ReportActivityPage
}
