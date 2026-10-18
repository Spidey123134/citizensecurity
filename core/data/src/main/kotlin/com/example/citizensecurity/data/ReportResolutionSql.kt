package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportResolutionQuery

/** Additive outcome declarations; the administrative status and its chronology stay independent. */
internal object ReportResolutionSql {
    const val TABLE = "report_resolution_events"
    val CREATE_TABLE = """
        CREATE TABLE report_resolution_events (
            operation_id TEXT NOT NULL UNIQUE CHECK (length(operation_id) = 36),
            report_id TEXT NOT NULL REFERENCES reports(id),
            sequence INTEGER NOT NULL CHECK (typeof(sequence) = 'integer' AND sequence > 0),
            from_resolution TEXT NOT NULL CHECK (from_resolution IN ('UNCONFIRMED','RESOLVED','UNRESOLVED')),
            to_resolution TEXT NOT NULL CHECK (to_resolution IN ('UNCONFIRMED','RESOLVED','UNRESOLVED')),
            recorded_at_seconds INTEGER NOT NULL CHECK (typeof(recorded_at_seconds) = 'integer'),
            recorded_at_nanos INTEGER NOT NULL CHECK (typeof(recorded_at_nanos) = 'integer' AND recorded_at_nanos BETWEEN 0 AND 999999999),
            actor_account_id TEXT NOT NULL CHECK (length(actor_account_id) = 36),
            actor_kind TEXT NOT NULL CHECK (actor_kind IN ('AUTHOR','LOCAL_ADMINISTRATOR')),
            reason TEXT NOT NULL CHECK (length(reason) BETWEEN 5 AND 500 AND instr(reason, char(0)) = 0),
            PRIMARY KEY (report_id, sequence),
            CHECK (from_resolution <> to_resolution),
            CHECK (sequence <> 1 OR from_resolution = 'UNCONFIRMED')
        )
    """.trimIndent()

    const val INSERT = """INSERT INTO report_resolution_events
        (operation_id,report_id,sequence,from_resolution,to_resolution,recorded_at_seconds,recorded_at_nanos,actor_account_id,actor_kind,reason)
        VALUES (?,?,?,?,?,?,?,?,?,?)"""
    const val FIND_OPERATION = "SELECT * FROM report_resolution_events WHERE operation_id = ? LIMIT 1"
    const val CREATOR = """SELECT event.* FROM report_status_events event
        WHERE report_id = ? AND sequence = 1 AND kind = 'CREATED' LIMIT 1"""
    const val LATEST = "SELECT * FROM report_resolution_events WHERE report_id = ? ORDER BY sequence DESC LIMIT 1"
    const val CHAIN = """SELECT COUNT(*) AS event_count, MIN(sequence) AS first_sequence, MAX(sequence) AS last_sequence,
        COALESCE(SUM(CASE WHEN (sequence = 1 AND from_resolution <> 'UNCONFIRMED') OR (sequence > 1 AND NOT EXISTS (
            SELECT 1 FROM report_resolution_events previous WHERE previous.report_id = current.report_id
                AND previous.sequence = current.sequence - 1 AND previous.to_resolution = current.from_resolution
        )) THEN 1 ELSE 0 END), 0) AS broken_links
        FROM report_resolution_events current WHERE report_id = ?"""
    fun page(query: ReportResolutionQuery): String = "SELECT * FROM report_resolution_events WHERE report_id = ?" +
        (if (query.beforeSequence == null) "" else " AND sequence < ?") + " ORDER BY sequence DESC LIMIT ?"
    fun pageArguments(query: ReportResolutionQuery): Array<String> = buildList {
        add(query.reportId)
        query.beforeSequence?.let { add(it.toString()) }
        add((query.limit + 1).toString())
    }.toTypedArray()
}
