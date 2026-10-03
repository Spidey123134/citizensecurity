package com.example.citizensecurity.domain

/** Guardado y consulta local. Guardar un reporte no lo envía a ninguna autoridad. */
interface ReportRepository {
    /** Valida y guarda un reporte; rechaza datos inválidos con [InvalidReportException]. */
    suspend fun create(draft: NewReport): Report

    suspend fun findById(id: String): Report?

    /** Devuelve los reportes del más reciente al más antiguo según su fecha de creación. */
    suspend fun list(): List<Report>
}
