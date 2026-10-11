package com.example.citizensecurity.domain

import java.time.Instant
import java.util.UUID

/** Copia local completa. [id] identifica esta revisión, no un reporte ni evidencia GPS. */
data class ReportDraftCheckpoint(
    val id: String,
    val draft: NewReport,
    val savedAt: Instant,
    val submittedFolio: String? = null,
) {
    init {
        require(id.isCanonicalDraftUuid()) { "El borrador necesita un UUID completo y canónico." }
        require(submittedFolio == null || submittedFolio.isCanonicalDraftUuid()) {
            "El folio del borrador necesita un UUID completo y canónico."
        }
        require(!savedAt.isBefore(Instant.EPOCH)) { "La fecha de guardado del borrador no es válida." }
    }
}

/**
 * Una copia privada por teléfono, independiente del intento de inserción y de SQLite.
 * Una lectura fallida nunca equivale a una copia ausente. [write] compara [expectedId]
 * y [expectedSubmittedFolio] con la revisión y marca almacenadas (null cuando no existen);
 * cambiar campos necesita un [ReportDraftCheckpoint.id] nuevo. Así una pantalla que no
 * ha observado el folio confirmado tampoco puede sustituirlo. Repetir la misma copia es
 * idempotente con los dos valores actuales.
 * [markSubmitted] conserva todos los campos y solo marca la revisión indicada.
 */
interface ReportDraftCheckpointJournal {
    suspend fun read(): ReportDraftCheckpoint?
    suspend fun write(
        checkpoint: ReportDraftCheckpoint,
        expectedId: String?,
        expectedSubmittedFolio: String? = null,
    )
    suspend fun markSubmitted(id: String, folio: String)
}

private fun String.isCanonicalDraftUuid(): Boolean =
    runCatching { UUID.fromString(this).toString() == this }.getOrDefault(false)
