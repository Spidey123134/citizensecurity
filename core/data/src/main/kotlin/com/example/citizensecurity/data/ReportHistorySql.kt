package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportHistoryQuery

/** Shared production SQL also executed by the focused host SQLite verification. */
internal object ReportHistorySql {
    const val TABLE = "report_status_events"
    val CREATE_TABLE = """
        CREATE TABLE report_status_events (
            operation_id TEXT NOT NULL UNIQUE CHECK (length(operation_id) = 36),
            report_id TEXT NOT NULL REFERENCES reports(id),
            sequence INTEGER NOT NULL CHECK (typeof(sequence) = 'integer' AND sequence > 0),
            kind TEXT NOT NULL CHECK (kind IN ('CREATED', 'STATUS_CHANGED')),
            from_status TEXT,
            to_status TEXT NOT NULL CHECK (to_status IN ('REPORTED','IN_REVIEW','ATTENDED','CLOSED')),
            recorded_at_seconds INTEGER NOT NULL CHECK (typeof(recorded_at_seconds) = 'integer'),
            recorded_at_nanos INTEGER NOT NULL CHECK (typeof(recorded_at_nanos) = 'integer' AND recorded_at_nanos BETWEEN 0 AND 999999999),
            actor_account_id TEXT CHECK (actor_account_id IS NULL OR length(actor_account_id) = 36),
            reason TEXT NOT NULL CHECK (instr(reason, char(0)) = 0),
            PRIMARY KEY (report_id, sequence),
            CHECK (
                (kind = 'CREATED' AND sequence = 1 AND from_status IS NULL AND to_status = 'REPORTED' AND reason = '')
                OR
                (kind = 'STATUS_CHANGED' AND from_status IS NOT NULL AND actor_account_id IS NOT NULL AND length(reason) BETWEEN 5 AND 500 AND (
                    (from_status = 'REPORTED' AND to_status = 'IN_REVIEW') OR
                    (from_status = 'IN_REVIEW' AND to_status = 'ATTENDED') OR
                    (from_status = 'ATTENDED' AND to_status = 'CLOSED')
                ))
            )
        )
    """.trimIndent()

    const val INSERT = """INSERT INTO report_status_events
        (operation_id, report_id, sequence, kind, from_status, to_status, recorded_at_seconds, recorded_at_nanos, actor_account_id, reason)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
    const val HEAD = """SELECT status,
        COALESCE((SELECT MAX(sequence) FROM report_status_events WHERE report_id = reports.id), 0) AS latest_sequence,
        EXISTS(SELECT 1 FROM report_status_events WHERE report_id = reports.id AND sequence = 1 AND kind = 'CREATED') AS complete_from_creation
        FROM reports WHERE id = ? LIMIT 1"""
    const val FIND_OPERATION = "SELECT * FROM report_status_events WHERE operation_id = ? LIMIT 1"
    const val CAS_UPDATE = """UPDATE reports SET status = ? WHERE id = ? AND status = ?
        AND COALESCE((SELECT MAX(sequence) FROM report_status_events WHERE report_id = reports.id), 0) = ?"""
    fun page(query: ReportHistoryQuery): String = "SELECT * FROM report_status_events WHERE report_id = ?" +
        (if (query.beforeSequence == null) "" else " AND sequence < ?") + " ORDER BY sequence DESC LIMIT ?"
    fun pageArguments(query: ReportHistoryQuery): Array<String> = buildList {
        add(query.reportId)
        query.beforeSequence?.let { add(it.toString()) }
        add((query.limit + 1).toString())
    }.toTypedArray()
}
