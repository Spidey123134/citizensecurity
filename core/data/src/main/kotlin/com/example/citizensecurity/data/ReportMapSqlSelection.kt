package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportMapQuery
import java.util.Collections

/** Pure SQL selection shared by the Android query and SQLite verification on the host. */
internal class ReportMapSqlSelection(val selection: String, arguments: List<String>) {
    val arguments: List<String> = Collections.unmodifiableList(arguments.toList())
}

/** All filters belong to WHERE, before the repository applies its limit + 1. */
internal fun mapSelection(query: ReportMapQuery): ReportMapSqlSelection {
    val region = query.region
    val arguments = mutableListOf(region.south.toString(), region.north.toString())
    val longitudeRange = if (region.crossesAntimeridian) {
        "longitude >= ? OR longitude <= ?"
    } else {
        "longitude BETWEEN ? AND ?"
    }
    arguments += region.west.toString()
    arguments += region.east.toString()
    val longitudeSelection = if (region.includesAntimeridian) {
        "($longitudeRange OR longitude IN (-180.0, 180.0))"
    } else {
        "($longitudeRange)"
    }
    val selections = mutableListOf(
        "latitude IS NOT NULL",
        "longitude IS NOT NULL",
        "latitude BETWEEN ? AND ?",
        longitudeSelection,
    )
    if (query.types.isNotEmpty()) {
        selections += "incident_type IN (${query.types.joinToString { "?" }})"
        arguments += query.types.map { it.name }.sorted()
    }
    if (query.statuses.isNotEmpty()) {
        selections += "status IN (${query.statuses.joinToString { "?" }})"
        arguments += query.statuses.map { it.name }.sorted()
    }
    if (query.priorities.isNotEmpty()) {
        selections += "priority IN (${query.priorities.joinToString { "?" }})"
        arguments += query.priorities.map { it.name }.sorted()
    }
    query.startInclusive?.let { start ->
        selections += "(occurred_at_seconds > ? OR (occurred_at_seconds = ? AND occurred_at_nanos >= ?))"
        arguments += listOf(start.epochSecond.toString(), start.epochSecond.toString(), start.nano.toString())
    }
    query.endExclusive?.let { end ->
        selections += "(occurred_at_seconds < ? OR (occurred_at_seconds = ? AND occurred_at_nanos < ?))"
        arguments += listOf(end.epochSecond.toString(), end.epochSecond.toString(), end.nano.toString())
    }
    return ReportMapSqlSelection(selections.joinToString(" AND "), arguments)
}
