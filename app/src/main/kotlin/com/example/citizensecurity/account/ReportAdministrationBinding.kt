package com.example.citizensecurity.account

import com.example.citizensecurity.data.AuthorizedReportAdministration
import com.example.citizensecurity.data.ReportAdministrationSources
import com.example.citizensecurity.domain.DenyAllReportAdministrationPolicy
import com.example.citizensecurity.domain.ReportAdministrationPermissionDeniedException
import com.example.citizensecurity.domain.ReportAdministrationPolicy
import com.example.citizensecurity.domain.ReportAdministrationReader
import com.example.citizensecurity.domain.ReportStatusChangeScope

/**
 * An administrative lease over one live account and its already captured workspace.
 * Its identity is never authority: production stays denied until an assignment exists.
 * A future writer must also use [policy] inside its transaction before committing.
 */
class ReportAdministrationBinding internal constructor(
    capturedSession: AccountSessionState,
    private val checkCurrent: () -> Unit,
    private val authority: ReportAdministrationPolicy = DenyAllReportAdministrationPolicy,
    sources: ReportAdministrationSources,
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
            return ReportAdministrationBinding(workspace.session, workspace::checkActive, authority, sources)
        }
    }
}
