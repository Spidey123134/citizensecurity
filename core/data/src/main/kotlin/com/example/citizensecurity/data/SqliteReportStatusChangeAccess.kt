package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportStatusChangeAccess
import com.example.citizensecurity.domain.ReportStatusChangeReceipt
import com.example.citizensecurity.domain.ReportStatusChangeRequest
import com.example.citizensecurity.domain.ReportStatusChangeScope
import com.example.citizensecurity.domain.ReportStatusEvent
import com.example.citizensecurity.domain.ReportStatusOperationConflictException
import com.example.citizensecurity.domain.ReportStatusPermissionDeniedException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Captured owner/actor binding. Neither its scope nor a journal grants a role. */
class SqliteReportStatusChangeAccess(
    override val scope: ReportStatusChangeScope,
    private val repository: SqliteReportRepository,
    private val checkAccess: () -> Unit = { throw ReportStatusPermissionDeniedException() },
) : ReportStatusChangeAccess {
    override fun checkCurrent() = checkAccess()

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
