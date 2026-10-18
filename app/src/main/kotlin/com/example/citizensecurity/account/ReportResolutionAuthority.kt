package com.example.citizensecurity.account

import com.example.citizensecurity.domain.*
import kotlinx.coroutines.CancellationException

/** Captures identity only. Role files are read on the repository's IO boundary. */
internal fun reportResolutionAuthority(
    session: AccountSessionState,
    checkCurrent: () -> Unit,
    policy: () -> ReportAdministrationPolicy,
): (String, String?) -> ReportResolutionActor = { _, creatorAccountId ->
    checkCurrent()
    val accountId = session.account?.id ?: throw ReportResolutionPermissionDeniedException()
    val kind = if (creatorAccountId == accountId) ReportResolutionActorKind.AUTHOR else {
        try {
            policy().check(ReportStatusChangeScope(accountId, accountId), ReportAdministrationPermission.DECLARE_RESOLUTION)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw ReportResolutionPermissionDeniedException() }
        ReportResolutionActorKind.LOCAL_ADMINISTRATOR
    }
    checkCurrent()
    ReportResolutionActor(accountId, kind)
}
