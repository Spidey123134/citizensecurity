package com.example.citizensecurity.domain

import java.time.Instant
import java.util.UUID

/** Identidad y campos del intento; nunca contiene permisos ni evidencia GPS autorizada. */
data class ReportSubmissionRequest(
    val requestId: String,
    val draft: NewReport,
    val requestedAt: Instant,
) {
    init {
        require(runCatching { UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)) {
            "La solicitud necesita un UUID completo y canónico."
        }
    }

    /** Coincide con la normalización usada por el repositorio SQLite, incluido cero sin signo. */
    fun matches(report: Report): Boolean = report.id == requestId &&
        report.type == draft.type && report.priority == draft.priority &&
        report.description == draft.description.trim() && report.occurredAt == draft.occurredAt &&
        report.location == draft.location.copy(
            reference = draft.location.reference.trim(),
            latitude = draft.location.latitude?.let { if (it == 0.0) 0.0 else it },
            longitude = draft.location.longitude?.let { if (it == 0.0) 0.0 else it },
        )
}

/**
 * Diario privado de una solicitud. Se escribe antes de pedir la inserción al repositorio.
 * [write] no puede sustituir otra identidad o contenido y [clear] solo retira la identidad
 * indicada; un fallo de lectura nunca se interpreta como un diario vacío.
 */
interface ReportSubmissionJournal {
    suspend fun read(): ReportSubmissionRequest?
    suspend fun write(request: ReportSubmissionRequest)
    suspend fun clear(requestId: String)
}
