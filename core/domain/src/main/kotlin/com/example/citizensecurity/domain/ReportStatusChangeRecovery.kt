package com.example.citizensecurity.domain

import java.time.Instant

/** Storage binding only. A persisted actor UUID never grants administrative permission. */
data class ReportStatusChangeScope(val ownerAccountId: String?, val actorAccountId: String) {
    init {
        ownerAccountId?.let(::requireCanonicalReportHistoryId)
        requireCanonicalReportHistoryId(actorAccountId)
    }
}

/** The complete durable intent is retained unchanged until its receipt is explicitly consumed. */
data class ReportStatusChangeRequest(
    val scope: ReportStatusChangeScope,
    val command: ReportStatusChangeCommand,
    val requestedAt: Instant,
)

data class ReportStatusChangeReceipt(val reportId: String, val event: ReportStatusEvent) {
    init {
        requireCanonicalReportHistoryId(reportId)
        require(event.kind == ReportStatusEventKind.STATUS_CHANGED)
    }

    fun matches(request: ReportStatusChangeRequest): Boolean {
        val command = request.command
        return reportId == command.reportId && event.operationId == command.operationId &&
            event.sequence == command.expectedSequence + 1L && event.fromStatus == command.expectedStatus &&
            event.toStatus == command.targetStatus && event.reason == command.reason &&
            event.actorAccountId == request.scope.actorAccountId
    }
}

interface ReportStatusChangeJournal {
    suspend fun read(): ReportStatusChangeRequest?
    /** Retain only into an empty journal or when every field is exactly equal. */
    suspend fun retain(request: ReportStatusChangeRequest)
    /** A confirmed consumer may clear only the entire expected body. */
    suspend fun clear(expected: ReportStatusChangeRequest)
    /** A shared process lease spans journal + backend work; cancellation cannot release a running writer. */
    suspend fun <T> exclusive(block: suspend () -> T): T
}

/** One captured owner/actor/session. Implementations authorize independently before every SQL entry. */
interface ReportStatusChangeAccess {
    val scope: ReportStatusChangeScope
    fun checkCurrent()
    suspend fun change(request: ReportStatusChangeRequest): ReportStatusEvent
    suspend fun findReceipt(request: ReportStatusChangeRequest): ReportStatusChangeReceipt?
}
