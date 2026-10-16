package com.example.citizensecurity.account

import com.example.citizensecurity.data.AuthorizedReportAdministration
import com.example.citizensecurity.data.ReportAdministrationSources
import com.example.citizensecurity.domain.DenyAllReportAdministrationPolicy
import com.example.citizensecurity.domain.ReportAdministrationPermissionDeniedException
import com.example.citizensecurity.domain.ReportAdministrationPolicy
import com.example.citizensecurity.domain.ReportAdministrationReader
import com.example.citizensecurity.domain.ReportAdministrationPermission
import com.example.citizensecurity.domain.ReportStatusChangeAccess
import com.example.citizensecurity.domain.ReportStatusChangeRequest
import com.example.citizensecurity.domain.ReportStatusChangeReceipt
import com.example.citizensecurity.domain.ReportStatusEvent
import com.example.citizensecurity.domain.ReportStatusPermissionDeniedException
import com.example.citizensecurity.domain.ReportStatusChangeScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * An administrative lease over one live account and its already captured workspace.
 * Its identity is never authority: production stays denied until an assignment exists.
 * Its writer receives the same captured policy inside the transaction before committing.
 */
class ReportAdministrationBinding internal constructor(
    capturedSession: AccountSessionState,
    private val checkCurrent: () -> Unit,
    private val authority: ReportAdministrationPolicy = DenyAllReportAdministrationPolicy,
    sources: ReportAdministrationSources,
    private val statusChangeAccessFactory: ((ReportStatusChangeScope, ReportAdministrationPolicy) -> ReportStatusChangeAccess)? = null,
) {
    val scope: ReportStatusChangeScope

    init {
        checkCurrent()
        val accountId = capturedSession.account?.id ?: throw ReportAdministrationPermissionDeniedException()
        scope = ReportStatusChangeScope(accountId, accountId)
        if (sources.scope != scope) throw ReportAdministrationPermissionDeniedException()
        checkCurrent()
    }

    /** Scope and session are checked again after an authority lookup, which may block. */
    val policy = ReportAdministrationPolicy { requested, permission ->
        if (requested != scope) throw ReportAdministrationPermissionDeniedException()
        checkCurrent()
        authority.check(scope, permission)
        checkCurrent()
    }

    /** Fixed delegates; granting access never opens or follows a different workspace. */
    val reader: ReportAdministrationReader = AuthorizedReportAdministration(
        scope, policy, sources = { sources }, checkCurrent = checkCurrent,
    )

    /**
     * Pure capture for Activity construction: no authority/file/database access until IO entry.
     * [checkWrite] is carried into the SQLite before-commit policy, not just the outer gesture.
     */
    fun openStatusChangeAccess(checkWrite: () -> Unit = {}): ReportStatusChangeAccess {
        checkCurrent()
        return BoundStatusChangeAccess(this, checkWrite)
    }

    /** A journal must belong to the exact binding that issued this access, not only its UUIDs. */
    internal fun checkStatusChangeAccess(access: ReportStatusChangeAccess) {
        if (access !is BoundStatusChangeAccess || access.binding !== this || access.scope != scope) {
            throw ReportStatusPermissionDeniedException()
        }
        access.checkCurrent()
    }

    private class BoundStatusChangeAccess(
        val binding: ReportAdministrationBinding,
        private val checkWrite: () -> Unit,
    ) : ReportStatusChangeAccess {
        override val scope = binding.scope
        private val writePolicy = ReportAdministrationPolicy { requested, permission ->
            if (requested != scope || permission != ReportAdministrationPermission.CHANGE_STATUS) {
                throw ReportAdministrationPermissionDeniedException()
            }
            try { binding.policy.check(scope, permission) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                // An unreadable authority is never permission to retain/display private work.
                throw ReportAdministrationPermissionDeniedException()
            }
            checkWrite()
            binding.checkCurrent()
        }
        private val delegate: ReportStatusChangeAccess by lazy {
            authorize()
            val factory = binding.statusChangeAccessFactory ?: throw ReportStatusPermissionDeniedException()
            factory(scope, writePolicy).also {
                authorize()
                if (it.scope != scope) throw ReportStatusPermissionDeniedException()
            }
        }

        private fun authorize() {
            try { writePolicy.check(scope, ReportAdministrationPermission.CHANGE_STATUS) }
            catch (_: ReportAdministrationPermissionDeniedException) { throw ReportStatusPermissionDeniedException() }
        }

        override fun checkCurrent() {
            authorize()
            delegate.checkCurrent()
            authorize()
        }

        override suspend fun change(request: ReportStatusChangeRequest): ReportStatusEvent = checked(request) {
            delegate.change(request)
        }

        override suspend fun findReceipt(request: ReportStatusChangeRequest): ReportStatusChangeReceipt? = checked(request) {
            delegate.findReceipt(request)
        }

        private suspend fun <T> checked(request: ReportStatusChangeRequest, operation: suspend () -> T): T {
            currentCoroutineContext().ensureActive()
            if (request.scope != scope) throw ReportStatusPermissionDeniedException()
            checkCurrent()
            currentCoroutineContext().ensureActive()
            return operation().also { currentCoroutineContext().ensureActive(); checkCurrent(); currentCoroutineContext().ensureActive() }
        }
    }

    companion object {
        /** Login, academic access and construction alone grant no administrative permission. */
        fun capture(
            workspace: ReportWorkspace,
            authority: ReportAdministrationPolicy = DenyAllReportAdministrationPolicy,
        ): ReportAdministrationBinding {
            workspace.checkActive()
            val accountId = workspace.session.account?.id ?: throw ReportAdministrationPermissionDeniedException()
            val sources = ReportAdministrationSources(
                ReportStatusChangeScope(accountId, accountId),
                workspace.repository::findById,
                workspace.listPageRepository,
                workspace.historyRepository,
            )
            return ReportAdministrationBinding(workspace.session, workspace::checkActive, authority, sources,
                workspace::createStatusChangeAccess)
        }
    }
}
