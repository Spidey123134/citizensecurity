package com.example.citizensecurity.domain

/** Guardado y consulta local. Guardar un reporte no lo envía a ninguna autoridad. */
interface ReportRepository {
    /** Valida y guarda un reporte; rechaza datos inválidos con [InvalidReportException]. */
    suspend fun create(draft: NewReport): Report

    suspend fun findById(id: String): Report?

    /** Devuelve los reportes del más reciente al más antiguo según su fecha de creación. */
    suspend fun list(): List<Report>
}

/**
 * Guardado con identidad estable, para recuperar un resultado aunque se pierda su respuesta.
 * El dueño conserva el mismo UUID canónico y el mismo borrador durante todos los reintentos.
 * Una identidad nueva representa otro reporte; no se deduplican incidentes por su contenido.
 */
interface IdempotentReportRepository : ReportRepository {
    /**
     * Inserta como máximo un reporte con [requestId]. Si ya existe y sus campos de entrada
     * coinciden tras normalizarlos, devuelve el resultado conservado, sin renovar GPS ni escribir.
     * Una identidad utilizada con otros campos lanza [ReportRequestConflictException].
     */
    suspend fun createOnce(requestId: String, draft: NewReport): Report
}

/** La identidad pertenece a otro contenido. No modificarlo ni generar otro folio al reintentar. */
class ReportRequestConflictException(val requestId: String) : IllegalArgumentException(
    "Esta solicitud ya pertenece a otro reporte. Recupera su resultado antes de iniciar uno nuevo.",
)
