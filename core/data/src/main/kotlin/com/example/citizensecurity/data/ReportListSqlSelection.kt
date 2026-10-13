package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportListPageQuery
import java.util.Collections

internal const val REPORT_LIST_PAGE_ORDER = "created_at_seconds DESC, created_at_nanos DESC, id DESC"

/** Selección pura compartida por la consulta Android y las pruebas SQLite del host. */
internal class ReportListSqlSelection(val selection: String, arguments: List<String>) {
    val arguments: List<String> = Collections.unmodifiableList(arguments.toList())
}

/** Los filtros y el cursor se aplican antes de LIMIT + 1, sin OFFSET ni comparación textual. */
internal fun listPageSelection(query: ReportListPageQuery): ReportListSqlSelection {
    val selections = mutableListOf<String>()
    val arguments = mutableListOf<String>()
    query.type?.let { type ->
        selections += "incident_type = ?"
        arguments += type.name
    }
    query.status?.let { status ->
        selections += "status = ?"
        arguments += status.name
    }
    query.priority?.let { priority ->
        selections += "priority = ?"
        arguments += priority.name
    }
    query.startInclusive?.let { start ->
        selections += "(occurred_at_seconds > ? OR (occurred_at_seconds = ? AND occurred_at_nanos >= ?))"
        arguments += listOf(start.epochSecond.toString(), start.epochSecond.toString(), start.nano.toString())
    }
    query.endExclusive?.let { end ->
        selections += "(occurred_at_seconds < ? OR (occurred_at_seconds = ? AND occurred_at_nanos < ?))"
        arguments += listOf(end.epochSecond.toString(), end.epochSecond.toString(), end.nano.toString())
    }
    query.before?.let { before ->
        selections += "(created_at_seconds < ? OR (created_at_seconds = ? AND created_at_nanos < ?) " +
            "OR (created_at_seconds = ? AND created_at_nanos = ? AND id < ?))"
        arguments += listOf(
            before.createdAt.epochSecond.toString(), before.createdAt.epochSecond.toString(),
            before.createdAt.nano.toString(), before.createdAt.epochSecond.toString(),
            before.createdAt.nano.toString(), before.id,
        )
    }
    return ReportListSqlSelection(selections.ifEmpty { listOf("1") }.joinToString(" AND "), arguments)
}
