package com.example.citizensecurity.domain

import java.time.Instant
import java.util.Collections

/** A local declaration about the outcome, independent of the administrative report status. */
enum class ReportResolution { UNCONFIRMED, RESOLVED, UNRESOLVED }
enum class ReportResolutionActorKind { AUTHOR, LOCAL_ADMINISTRATOR }

data class ReportResolutionActor(val accountId: String, val kind: ReportResolutionActorKind) {
    init { requireCanonicalReportHistoryId(accountId) }
}

data class ReportResolutionCommand(
    val operationId: String,
    val reportId: String,
    val expectedSequence: Long,
    val expectedResolution: ReportResolution,
    val targetResolution: ReportResolution,
    val actor: ReportResolutionActor,
    val reason: String,
) {
    init {
        requireCanonicalReportHistoryId(operationId)
        requireCanonicalReportHistoryId(reportId)
        require(expectedSequence >= 0 && expectedSequence < Long.MAX_VALUE)
        require(expectedResolution != targetResolution) { "Elige un resultado diferente del actual." }
        requireStatusChangeReason(reason)
    }
}

data class ReportResolutionEvent(
    val operationId: String,
    val reportId: String,
    val sequence: Long,
    val fromResolution: ReportResolution,
    val toResolution: ReportResolution,
    val recordedAt: Instant,
    val actor: ReportResolutionActor,
    val reason: String,
) {
    init {
        requireCanonicalReportHistoryId(operationId)
        requireCanonicalReportHistoryId(reportId)
        require(sequence > 0)
        require(fromResolution != toResolution)
        require(sequence != 1L || fromResolution == ReportResolution.UNCONFIRMED)
        requireStatusChangeReason(reason)
    }

    fun matches(command: ReportResolutionCommand): Boolean = operationId == command.operationId &&
        reportId == command.reportId && sequence == command.expectedSequence + 1L &&
        fromResolution == command.expectedResolution && toResolution == command.targetResolution &&
        actor == command.actor && reason == command.reason
}

data class ReportResolutionQuery(
    val reportId: String,
    val beforeSequence: Long? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    init {
        requireCanonicalReportHistoryId(reportId)
        require(beforeSequence == null || beforeSequence > 0)
        require(limit in 1..MAX_LIMIT)
    }
    companion object { const val DEFAULT_LIMIT = 50; const val MAX_LIMIT = 100 }
}

class ReportResolutionPage(
    val reportId: String,
    val resolution: ReportResolution,
    val latestSequence: Long,
    events: List<ReportResolutionEvent>,
    val hasMore: Boolean,
    /** Computed from current authority, not a persisted grant or an identity supplied by UI. */
    val actor: ReportResolutionActor?,
) {
    val events: List<ReportResolutionEvent> = Collections.unmodifiableList(events.toList())
    init {
        requireCanonicalReportHistoryId(reportId)
        require(latestSequence >= 0)
        require(latestSequence != 0L || resolution == ReportResolution.UNCONFIRMED)
        require(this.events.size <= ReportResolutionQuery.MAX_LIMIT)
        require(this.events.all { it.reportId == reportId && it.sequence <= latestSequence })
        require(this.events.zipWithNext().all { (newer, older) -> newer.sequence > older.sequence })
        require(!hasMore || this.events.isNotEmpty())
    }
    val nextBeforeSequence: Long? get() = if (hasMore) events.last().sequence else null
}

interface ReportResolutionRepository {
    suspend fun read(query: ReportResolutionQuery): ReportResolutionPage?
    suspend fun record(command: ReportResolutionCommand): ReportResolutionEvent
    /** Read a receipt only; never retries a write. */
    suspend fun receipt(command: ReportResolutionCommand): ReportResolutionEvent?
}

class ReportResolutionPermissionDeniedException : IllegalStateException("No tienes permiso para declarar el resultado de este reporte.")
class ReportResolutionNotFoundException : IllegalStateException("Ese folio no está guardado en este espacio.")
class ReportResolutionConflictException : IllegalStateException("El resultado cambió. Consúltalo y revisa de nuevo antes de confirmar.")
class ReportResolutionOperationConflictException : IllegalStateException("Esta operación pertenece a otra declaración de resultado.")
