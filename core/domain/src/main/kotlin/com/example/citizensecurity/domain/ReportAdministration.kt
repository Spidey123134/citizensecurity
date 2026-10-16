package com.example.citizensecurity.domain

/** Reading and changing reports are separate capabilities; neither follows from a local login. */
enum class ReportAdministrationPermission { READ_REPORTS, CHANGE_STATUS }

/**
 * Rechecks one captured owner/actor against the current authority. Constructing a scope or
 * retaining a journal never grants a capability. Implementations must throw when not allowed.
 */
fun interface ReportAdministrationPolicy {
    fun check(scope: ReportStatusChangeScope, permission: ReportAdministrationPermission)
}

/** Production default until an administrative assignment is explicitly agreed. */
object DenyAllReportAdministrationPolicy : ReportAdministrationPolicy {
    override fun check(scope: ReportStatusChangeScope, permission: ReportAdministrationPermission): Nothing =
        throw ReportAdministrationPermissionDeniedException()
}

class ReportAdministrationPermissionDeniedException : IllegalStateException(
    "No tienes permiso para realizar esta operación administrativa.",
)

/** A bounded reader for one captured workspace. No writes, photo bytes or unbounded list API. */
interface ReportAdministrationReader {
    val scope: ReportStatusChangeScope
    suspend fun findById(id: String): Report?
    suspend fun listPage(query: ReportListPageQuery): ReportListPage
    suspend fun readHistory(query: ReportHistoryQuery): ReportHistoryPage?
}
