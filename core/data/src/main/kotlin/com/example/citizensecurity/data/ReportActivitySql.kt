package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportActivityFilter
import com.example.citizensecurity.domain.ReportActivityQuery

/** Production SQL shared with host SQLite verification. No offset or fabricated baseline. */
internal object ReportActivitySql {
    const val INDEX_NAME = "report_status_events_activity_idx"
    const val CREATE_INDEX = "CREATE INDEX report_status_events_activity_idx ON report_status_events " +
        "(recorded_at_seconds DESC, recorded_at_nanos DESC, operation_id DESC)"

    fun page(query: ReportActivityQuery): String = buildString {
        append("SELECT e.*, r.incident_type AS incident_type FROM report_status_events e ")
        append("JOIN reports r ON r.id = e.report_id WHERE 1 = 1")
        if (query.filter == ReportActivityFilter.STATUS_CHANGES) append(" AND e.kind = 'STATUS_CHANGED'")
        if (query.before != null) append(" AND (e.recorded_at_seconds < ? OR ")
            .append("(e.recorded_at_seconds = ? AND e.recorded_at_nanos < ?) OR ")
            .append("(e.recorded_at_seconds = ? AND e.recorded_at_nanos = ? AND e.operation_id < ?))")
        append(" ORDER BY e.recorded_at_seconds DESC, e.recorded_at_nanos DESC, e.operation_id DESC LIMIT ?")
    }

    fun pageArguments(query: ReportActivityQuery): Array<String> = buildList {
        query.before?.let { before ->
            val seconds = before.recordedAt.epochSecond.toString()
            val nanos = before.recordedAt.nano.toString()
            add(seconds); add(seconds); add(nanos); add(seconds); add(nanos); add(before.operationId)
        }
        add((query.limit + 1).toString())
    }.toTypedArray()
}
