package com.example.citizensecurity.account

import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.data.ReportStorageNames
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real account workspaces and SQLite; only this test supplies an administrative grant. */
class ReportAdministrationBindingAndroidTest {
    @get:Rule val fixtureRule = ReportTestFixtureRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ReportTestApplication
    private val workspaces = mutableListOf<ReportWorkspace>()
    private val alice = LocalAccount(UUID.randomUUID().toString(), "alumna_1")
    private val bob = LocalAccount(UUID.randomUUID().toString(), "alumno_2")

    @After fun retireOnlyCapturedTestWorkspaces() {
        app.accountSession.signOut()
        workspaces.forEach(ReportWorkspace::close)
    }

    @Test fun defaultDenialDoesNotCreateAnAccountDatabaseOrFallBackToAcademicStorage() = runBlocking {
        app.accountSession.signIn(alice)
        val database = app.getDatabasePath(ReportStorageNames.fromAccountId(alice.id).databaseName)
        val academicDatabase = app.getDatabasePath(fixtureRule.fixture.databaseName)
        assertFalse(database.exists())
        assertFalse(academicDatabase.exists())
        val workspace = workspace()
        val binding = ReportAdministrationBinding.capture(workspace)
        assertEquals(ReportStatusChangeScope(alice.id, alice.id), binding.scope)
        denied { binding.reader.findById(UUID.randomUUID().toString()) }
        denied { binding.reader.listPage(ReportListPageQuery()) }
        denied { binding.reader.readHistory(ReportHistoryQuery(UUID.randomUUID().toString())) }
        denied { binding.policy.check(binding.scope, ReportAdministrationPermission.CHANGE_STATUS) }
        assertFalse(database.exists())
        assertFalse(academicDatabase.exists())
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun academicFactoryRejectsEvenAPermissiveFixtureWithoutCallingItOrOpeningStorage() {
        app.accountSession.signOut()
        val database = app.getDatabasePath(fixtureRule.fixture.databaseName)
        assertFalse(database.exists())
        val workspace = workspace()
        var checks = 0
        try {
            ReportAdministrationBinding.capture(workspace, ReportAdministrationPolicy { _, _ -> checks++ })
            fail("Academic access became administrative identity")
        } catch (_: ReportAdministrationPermissionDeniedException) { }
        assertEquals(0, checks)
        assertFalse(database.exists())
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun capturedFactoryReadsOnlyItsAccountAndCannotSurviveReplacementReloginOrClosure() = runBlocking {
        fixtureRule.fixture.publishFreshFix()
        val permitReadOnly = ReportAdministrationPolicy { scope, permission ->
            if (scope.ownerAccountId != scope.actorAccountId ||
                scope.actorAccountId !in setOf(alice.id, bob.id) ||
                permission != ReportAdministrationPermission.READ_REPORTS) throw ReportAdministrationPermissionDeniedException()
        }
        app.accountSession.signIn(alice)
        val a = workspace()
        val aReport = a.repository.createOnce(UUID.randomUUID().toString(), draft("Reporte conservado de la cuenta A"))
        val original = ReportAdministrationBinding.capture(a, permitReadOnly)
        assertEquals(aReport, original.reader.findById(aReport.id))
        assertEquals(listOf(aReport), original.reader.listPage(ReportListPageQuery()).reports)
        assertEquals(aReport.id, original.reader.readHistory(ReportHistoryQuery(aReport.id))?.reportId)

        app.accountSession.signIn(bob)
        val b = workspace()
        val bReport = b.repository.createOnce(UUID.randomUUID().toString(), draft("Reporte conservado de la cuenta B"))
        revoked { original.reader.findById(bReport.id) }
        revoked { original.policy.check(original.scope, ReportAdministrationPermission.READ_REPORTS) }
        val bBinding = ReportAdministrationBinding.capture(b, permitReadOnly)
        assertNull(bBinding.reader.findById(aReport.id))
        assertEquals(listOf(bReport), bBinding.reader.listPage(ReportListPageQuery()).reports)

        app.accountSession.signOut()
        app.accountSession.signIn(alice)
        revoked { original.reader.findById(aReport.id) }
        revoked { original.policy.check(original.scope, ReportAdministrationPermission.READ_REPORTS) }
        revoked { bBinding.reader.findById(bReport.id) }
        val reopenedA = workspace()
        val current = ReportAdministrationBinding.capture(reopenedA, permitReadOnly)
        assertEquals(aReport, current.reader.findById(aReport.id))
        assertNull(current.reader.findById(bReport.id))
        reopenedA.close()
        revoked { current.reader.listPage(ReportListPageQuery()) }
        revoked { current.policy.check(current.scope, ReportAdministrationPermission.READ_REPORTS) }
        revoked { ReportAdministrationBinding.capture(reopenedA, permitReadOnly) }
        assertEquals(alice, app.accountSession.account)
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    private fun workspace() = app.openReportWorkspace(app.accountSession.capture()).also(workspaces::add)
    private fun draft(description: String) = NewReport(IncidentType.RISK, Priority.MEDIUM, description,
        Instant.now().minusSeconds(30), ReportLocation("Frente al parque", 19.4326, -99.1332))

    private suspend fun denied(block: suspend () -> Any?) {
        try { block(); fail("Default administration policy allowed storage access") }
        catch (_: ReportAdministrationPermissionDeniedException) { }
    }

    private suspend fun revoked(block: suspend () -> Any?) {
        try { block(); fail("An obsolete workspace was accepted") }
        catch (failure: IllegalStateException) {
            assertFalse("A revoked session reached the authority policy", failure is ReportAdministrationPermissionDeniedException)
        }
    }
}
