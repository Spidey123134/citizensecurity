package com.example.citizensecurity.domain

import java.util.Collections

/** Local presentation preferences; a recorded ID does not prove delivery or user reading. */
@ConsistentCopyVisibility
data class ReportAlertsSnapshot private constructor(
    val revision: String?,
    val enabled: Boolean,
    val presented: Set<String>,
) {
    init {
        revision?.let(::requireCanonicalReportHistoryId)
        require(presented.size <= MAX_PRESENTED) { "El registro de avisos está lleno." }
        presented.forEach(::requireCanonicalReportHistoryId)
        require(revision != null || (!enabled && presented.isEmpty())) {
            "Las preferencias sin revisión deben conservar su estado inicial."
        }
    }

    companion object {
        const val MAX_PRESENTED = 4096
        operator fun invoke(revision: String?, enabled: Boolean = false,
            presented: Set<String> = emptySet()): ReportAlertsSnapshot = ReportAlertsSnapshot(
                revision, enabled, Collections.unmodifiableSet(LinkedHashSet(presented)),
            )
    }
}

interface ReportAlertsRepository {
    suspend fun read(): ReportAlertsSnapshot
    suspend fun setEnabled(enabled: Boolean, expectedRevision: String?): ReportAlertsSnapshot
    suspend fun recordPresented(operationIds: Set<String>, expectedRevision: String?): ReportAlertsSnapshot
}

class ReportAlertsConflictException : IllegalStateException("Los avisos cambiaron. Vuelve a consultar antes de guardar.")
class ReportAlertsCapacityException : IllegalStateException("El registro local de avisos está lleno; no se han retirado eventos anteriores.")
