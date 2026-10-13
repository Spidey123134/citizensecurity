package com.example.citizensecurity.domain

import java.time.Instant
import java.util.Collections
import java.util.UUID

enum class ReportStatusEventKind { CREATED, STATUS_CHANGED }

/** Local chronology is ordered by committed sequence, never by the phone's mutable clock. */
data class ReportStatusEvent(
    val operationId: String,
    val sequence: Long,
    val kind: ReportStatusEventKind,
    val fromStatus: ReportStatus?,
    val toStatus: ReportStatus,
    val recordedAt: Instant,
    val actorAccountId: String?,
    val reason: String = "",
) {
    init {
        requireCanonicalReportHistoryId(operationId)
        require(sequence > 0) { "La secuencia del historial debe ser positiva." }
        actorAccountId?.let(::requireCanonicalReportHistoryId)
        when (kind) {
            ReportStatusEventKind.CREATED -> require(sequence == 1L && fromStatus == null &&
                toStatus == ReportStatus.REPORTED && reason.isEmpty()) {
                "El primer evento de creación debe conservar el estado inicial real."
            }
            ReportStatusEventKind.STATUS_CHANGED -> {
                require(actorAccountId != null && fromStatus != null && canAdvanceReportStatus(fromStatus, toStatus)) {
                    "El cambio necesita un actor autorizado y una transición consecutiva."
                }
                requireStatusChangeReason(reason)
            }
        }
    }
}

data class ReportHistoryQuery(
    val reportId: String,
    val beforeSequence: Long? = null,
    val limit: Int = DEFAULT_LIMIT,
) {
    init {
        requireCanonicalReportHistoryId(reportId)
        require(beforeSequence == null || beforeSequence > 0) { "La posición de historial debe ser positiva." }
        require(limit in 1..MAX_LIMIT) { "El historial admite entre 1 y $MAX_LIMIT eventos." }
    }
    companion object { const val DEFAULT_LIMIT = 50; const val MAX_LIMIT = 100 }
}

/** No invented baseline: old rows may have a current state without a recorded initial event. */
class ReportHistoryPage(
    val reportId: String,
    val currentStatus: ReportStatus,
    val latestSequence: Long,
    events: List<ReportStatusEvent>,
    val hasMore: Boolean,
    val completeFromCreation: Boolean,
) {
    val events: List<ReportStatusEvent> = Collections.unmodifiableList(events.toList())
    init {
        requireCanonicalReportHistoryId(reportId)
        require(latestSequence >= 0)
        require(this.events.size <= ReportHistoryQuery.MAX_LIMIT)
        require(this.events.all { it.sequence <= latestSequence })
        require(this.events.zipWithNext().all { (a, b) -> a.sequence > b.sequence })
        require(!hasMore || this.events.isNotEmpty())
        require(!completeFromCreation || latestSequence > 0)
    }
    val nextBeforeSequence: Long? get() = if (hasMore) events.last().sequence else null
}

fun interface ReportHistoryRepository { suspend fun readHistory(query: ReportHistoryQuery): ReportHistoryPage? }

/** Identity/revision belong to a gesture. Authorization supplies the actor outside this command. */
data class ReportStatusChangeCommand(
    val operationId: String,
    val reportId: String,
    val expectedSequence: Long,
    val expectedStatus: ReportStatus,
    val targetStatus: ReportStatus,
    val reason: String,
) {
    init {
        requireCanonicalReportHistoryId(operationId)
        requireCanonicalReportHistoryId(reportId)
        require(expectedSequence >= 0 && expectedSequence < Long.MAX_VALUE)
        require(canAdvanceReportStatus(expectedStatus, targetStatus)) {
            "Solo se permite avanzar al estado consecutivo."
        }
        requireStatusChangeReason(reason)
    }
}

fun interface ReportStatusRepository { suspend fun changeStatus(command: ReportStatusChangeCommand): ReportStatusEvent }

class ReportStatusPermissionDeniedException : IllegalStateException("No tienes permiso para cambiar el estado del reporte.")
class ReportStatusConflictException : IllegalStateException("El reporte cambió. Consulta su estado antes de intentar otra operación.")
class ReportStatusOperationConflictException : IllegalStateException("Esta operación ya pertenece a otro cambio de estado.")
class ReportStatusNotFoundException : IllegalStateException("Ese folio no está guardado en este espacio.")

fun canAdvanceReportStatus(from: ReportStatus, to: ReportStatus): Boolean = when (from) {
    ReportStatus.REPORTED -> to == ReportStatus.IN_REVIEW
    ReportStatus.IN_REVIEW -> to == ReportStatus.ATTENDED
    ReportStatus.ATTENDED -> to == ReportStatus.CLOSED
    ReportStatus.CLOSED -> false
}

internal fun requireCanonicalReportHistoryId(value: String) {
    require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) {
        "El historial necesita un UUID completo y canónico."
    }
}

internal fun requireStatusChangeReason(reason: String) {
    require(reason == reason.trim() && reason.codePointCount(0, reason.length) in 5..500 &&
        reason.none { it == '\u0000' || Character.isISOControl(it) } && validHistoryUnicode(reason)) {
        "El motivo debe tener entre 5 y 500 caracteres válidos, sin controles ni espacios exteriores."
    }
}

private fun validHistoryUnicode(text: String): Boolean {
    var index = 0
    while (index < text.length) {
        val ch = text[index++]
        if (Character.isHighSurrogate(ch)) {
            if (index >= text.length || !Character.isLowSurrogate(text[index++])) return false
        } else if (Character.isLowSurrogate(ch)) return false
    }
    return true
}
