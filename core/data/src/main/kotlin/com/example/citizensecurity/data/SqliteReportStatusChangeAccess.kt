package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportStatusChangeAccess
import com.example.citizensecurity.domain.ReportStatusChangeReceipt
import com.example.citizensecurity.domain.ReportStatusChangeRequest
import com.example.citizensecurity.domain.ReportStatusChangeScope
import com.example.citizensecurity.domain.ReportStatusEvent
import com.example.citizensecurity.domain.ReportStatusOperationConflictException
import com.example.citizensecurity.domain.ReportStatusPermissionDeniedException
import com.example.citizensecurity.domain.DenyAllReportAdministrationPolicy
import com.example.citizensecurity.domain.ReportAdministrationPermission
import com.example.citizensecurity.domain.ReportAdministrationPermissionDeniedException
import com.example.citizensecurity.domain.ReportAdministrationPolicy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Captured owner/actor binding. Neither its scope nor a journal grants a role.
 * Receipt lookup accepts existing history schemas 3–5 through a read-only connection, without upgrade.
 */
class SqliteReportStatusChangeAccess(
    override val scope: ReportStatusChangeScope,
    private val repository: SqliteReportRepository,
    private val policy: ReportAdministrationPolicy = DenyAllReportAdministrationPolicy,
) : ReportStatusChangeAccess {
    /** Compatibility for existing captured guards, which already authorize a status change. */
    constructor(scope: ReportStatusChangeScope, repository: SqliteReportRepository, checkAccess: () -> Unit) :
        this(scope, repository, ReportAdministrationPolicy { _, _ -> checkAccess() })

    override fun checkCurrent() {
        try {
            policy.check(scope, ReportAdministrationPermission.CHANGE_STATUS)
        } catch (_: ReportAdministrationPermissionDeniedException) {
            // Preserve the existing writer/recovery permission contract; denial is definitive.
            throw ReportStatusPermissionDeniedException()
        }
    }

    override suspend fun change(request: ReportStatusChangeRequest): ReportStatusEvent {
        checkRequest(request)
        val event = repository.changeStatusForActor(request.command, scope.actorAccountId, ::checkCurrent)
        currentCoroutineContext().ensureActive(); checkCurrent(); currentCoroutineContext().ensureActive()
        if (!ReportStatusChangeReceipt(request.command.reportId, event).matches(request)) {
            throw ReportStatusOperationConflictException()
        }
        return event
    }

    override suspend fun findReceipt(request: ReportStatusChangeRequest): ReportStatusChangeReceipt? {
        checkRequest(request)
        val receipt = repository.findStatusChange(request.command, scope.actorAccountId, ::checkCurrent)
        currentCoroutineContext().ensureActive(); checkCurrent(); currentCoroutineContext().ensureActive()
        if (receipt != null && !receipt.matches(request)) throw ReportStatusOperationConflictException()
        return receipt
    }

    private suspend fun checkRequest(request: ReportStatusChangeRequest) {
        currentCoroutineContext().ensureActive(); checkCurrent(); currentCoroutineContext().ensureActive()
        if (request.scope != scope) throw ReportStatusPermissionDeniedException()
    }
}
