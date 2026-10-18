package com.example.citizensecurity.account

import com.example.citizensecurity.domain.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ReportResolutionAuthorityTest {
    private val author = LocalAccount(id(1), "ciudadana")
    private val other = id(2)

    @Test fun captureDoesNotReadAuthorityFiles() {
        val f = Fixture(); f.authority()
        assertEquals(0, f.checks); assertEquals(0, f.policyReads)
    }
    @Test fun actualCreatorDeclaresAsAuthorWithoutAdministrativePermission() {
        val f = Fixture()
        assertEquals(ReportResolutionActor(author.id, ReportResolutionActorKind.AUTHOR), f.authority()(id(3), author.id))
        assertEquals(2, f.checks); assertEquals(0, f.policyReads)
    }
    @Test fun administratorUsesDistinctPermissionAndCapturedWorkspace() {
        val f = Fixture()
        f.policy = ReportAdministrationPolicy { scope, permission ->
            assertEquals(ReportStatusChangeScope(author.id, author.id), scope)
            assertEquals(ReportAdministrationPermission.DECLARE_RESOLUTION, permission)
        }
        assertEquals(ReportResolutionActor(author.id, ReportResolutionActorKind.LOCAL_ADMINISTRATOR), f.authority()(id(3), other))
        assertEquals(1, f.policyReads); assertEquals(2, f.checks)
    }
    @Test fun anAdministratorWhoCreatedTheReportIsIdentifiedAsAuthor() {
        val f = Fixture(); f.policy = ReportAdministrationPolicy { _, _ -> }
        assertEquals(ReportResolutionActorKind.AUTHOR, f.authority()(id(3), author.id).kind)
        assertEquals(0, f.policyReads)
    }
    @Test fun unknownLegacyCreatorRequiresCurrentAdministratorPermission() {
        val f = Fixture()
        denied { f.authority()(id(3), null) }
        assertEquals(1, f.policyReads)
        f.policy = ReportAdministrationPolicy { _, _ -> }
        assertEquals(ReportResolutionActorKind.LOCAL_ADMINISTRATOR, f.authority()(id(3), null).kind)
    }
    @Test fun administrativeUsernameAloneGrantsNoPermission() {
        val f = Fixture(LocalAccount(author.id, "admin_local"))
        denied { f.authority()(id(3), other) }
    }
    @Test fun academicAccessCannotAcquireAuthorityEvenWithPermissivePolicy() {
        val f = Fixture(null); f.policy = ReportAdministrationPolicy { _, _ -> }
        denied { f.authority()(id(3), null) }
        assertEquals(0, f.policyReads)
    }
    @Test fun corruptionDoesNotFallBackToAuthorOrAdministrator() {
        val f = Fixture(); f.policy = ReportAdministrationPolicy { _, _ -> throw IOException("corrupt") }
        denied { f.authority()(id(3), other) }
    }
    @Test fun policyCancellationIsPropagated() {
        val f = Fixture(); val cancellation = CancellationException("cancelled")
        f.policy = ReportAdministrationPolicy { _, _ -> throw cancellation }
        try { f.authority()(id(3), other); fail("Expected cancellation") }
        catch (found: CancellationException) { assertSame(cancellation, found) }
    }
    @Test fun logoutAndBackIntoSameAccountCannotReviveCapture() {
        val f = Fixture(); val callback = f.authority()
        f.sessions.signOut(); f.sessions.signIn(author)
        revoked { callback(id(3), author.id) }
        assertEquals(0, f.policyReads)
    }
    @Test fun accountTransitionDuringBlockingPolicyCheckDeniesLateAuthority() {
        val f = Fixture()
        f.policy = ReportAdministrationPolicy { _, _ -> f.sessions.signOut() }
        revoked { f.authority()(id(3), other) }
        assertEquals(2, f.checks)
    }
    @Test fun sessionIsCheckedAgainForAuthorEvenWithoutRoleRead() {
        val f = Fixture()
        f.onCheck = { if (f.checks == 2) f.sessions.signOut() }
        revoked { f.authority()(id(3), author.id) }
    }

    private inner class Fixture(account: LocalAccount? = author) {
        val sessions = LocalAccountSession().apply { if (account != null) signIn(account) }
        val session = sessions.capture()
        var checks = 0
        var policyReads = 0
        var onCheck: () -> Unit = {}
        var policy: ReportAdministrationPolicy = DenyAllReportAdministrationPolicy
        fun authority() = reportResolutionAuthority(session, {
            checks++; onCheck(); check(sessions.isCurrent(session))
        }, { policyReads++; policy })
    }
    private fun denied(block: () -> Unit) {
        try { block(); fail("Expected denied") } catch (_: ReportResolutionPermissionDeniedException) { }
    }
    private fun revoked(block: () -> Unit) {
        try { block(); fail("Expected obsolete session") } catch (e: IllegalStateException) {
            assertFalse(e is ReportResolutionPermissionDeniedException)
        }
    }
    private fun id(value: Int) = "00000000-0000-4000-8000-${value.toString().padStart(12, '0')}"
}
