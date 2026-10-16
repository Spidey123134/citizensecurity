package com.example.citizensecurity.account

import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.data.ReportStatusChangeStore
import com.example.citizensecurity.data.ReportStorageNames
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Captured production SQLite wiring; grants below are test-only policies, not account roles. */
class ReportAdministrationWriterAndroidTest {
    @get:Rule val fixtureRule = ReportTestFixtureRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ReportTestApplication
    private val workspaces = mutableListOf<ReportWorkspace>()
    private val alice = LocalAccount(UUID.randomUUID().toString(), "admin_local")
    private val bob = LocalAccount(UUID.randomUUID().toString(), "cuenta_distinta")

    @After fun retireCapturedWorkspacesOnly() {
        app.accountSession.signOut()
        workspaces.forEach(ReportWorkspace::close)
    }

    @Test fun mainThreadFactoriesDoNotConsultAuthorityWriteJournalOrCreateDatabase() = runBlocking {
        app.accountSession.signIn(alice)
        val w = workspace()
        val checks = AtomicInteger()
        val files = app.filesDir.list()?.toSet()
        val database = app.getDatabasePath(ReportStorageNames.fromAccountId(alice.id).databaseName)
        lateinit var access: ReportStatusChangeAccess
        lateinit var journal: ReportStatusChangeJournal
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val binding = ReportAdministrationBinding.capture(w, ReportAdministrationPolicy { _, _ ->
                assertNotSame(Looper.getMainLooper(), Looper.myLooper())
                checks.incrementAndGet()
                throw ReportAdministrationPermissionDeniedException()
            })
            access = binding.openStatusChangeAccess { error("Denied access reached foreground check") }
            journal = app.openReportStatusChangeJournal(binding, access)
        }
        assertEquals(0, checks.get())
        assertEquals(files, app.filesDir.list()?.toSet())
        assertFalse(database.exists())
        denied { journal.read() }
        denied { access.findReceipt(request(access.scope)) }
        assertFalse(database.exists())
        assertEquals(files, app.filesDir.list()?.toSet())
    }

    @Test fun readOnlyGrantCannotWriteReadStatusReceiptOrRetainJournal() = runBlocking {
        app.accountSession.signIn(alice)
        val w = workspace()
        val binding = ReportAdministrationBinding.capture(w, ReportAdministrationPolicy { _, permission ->
            if (permission != ReportAdministrationPermission.READ_REPORTS) throw ReportAdministrationPermissionDeniedException()
        })
        val access = binding.openStatusChangeAccess()
        val journal = app.openReportStatusChangeJournal(binding, access)
        val request = request(access.scope)
        denied { access.change(request) }
        denied { access.findReceipt(request) }
        denied { journal.read() }
        denied { journal.retain(request) }
        assertFalse(app.getDatabasePath(ReportStorageNames.fromAccountId(alice.id).databaseName).exists())
        assertFalse(java.io.File(fixtureRule.fixture.contactsContext.filesDir, ReportStatusChangeStore.fileName(access.scope)).exists())
    }

    @Test fun changeUsesOriginalWorkspaceAndJournalRecoversExactSqliteReceipt() = runBlocking {
        fixtureRule.fixture.publishFreshFix()
        app.accountSession.signIn(alice)
        assignFixtureAdministrator()
        val w = workspace()
        val report = w.repository.createOnce(UUID.randomUUID().toString(), draft())
        val binding = ReportAdministrationBinding.capture(w, allow(alice))
        val access = binding.openStatusChangeAccess()
        val request = request(access.scope, report.id)
        val journal = app.openReportStatusChangeJournal(binding, access)
        assertNull(journal.read())
        journal.retain(request)
        val event = access.change(request)
        assertEquals(ReportStatus.IN_REVIEW, w.repository.findById(report.id)!!.status)
        val history = w.historyRepository.readHistory(ReportHistoryQuery(report.id))!!
        assertEquals(2L, history.latestSequence)
        assertEquals(event, history.events.first())
        assertEquals(alice.id, event.actorAccountId)
        assertEquals(request.command.reason, event.reason)
        assertEquals(request, journal.read())
        assertTrue(access.findReceipt(request)!!.matches(request))

        // A fresh capture reads the durable intent and receipt without issuing another change.
        val reopened = ReportAdministrationBinding.capture(w, allow(alice))
        val reopenedAccess = reopened.openStatusChangeAccess()
        val reopenedJournal = app.openReportStatusChangeJournal(reopened, reopenedAccess)
        assertEquals(request, reopenedJournal.read())
        assertEquals(event, reopenedAccess.findReceipt(request)!!.event)
        reopenedJournal.clear(request)
        assertNull(reopenedJournal.read())
        assertEquals(2L, w.historyRepository.readHistory(ReportHistoryQuery(report.id))!!.latestSequence)
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun anotherBindingCannotBorrowWriterForItsJournalEvenWithIdenticalUuids() = runBlocking {
        app.accountSession.signIn(alice)
        val w = workspace()
        val first = ReportAdministrationBinding.capture(w, allow(alice))
        val second = ReportAdministrationBinding.capture(w, allow(alice))
        val access = first.openStatusChangeAccess()
        val files = app.filesDir.list()?.toSet()
        val wrongJournal = app.openReportStatusChangeJournal(second, access)
        denied { wrongJournal.read() }
        assertEquals(files, app.filesDir.list()?.toSet())
        assertFalse(app.getDatabasePath(ReportStorageNames.fromAccountId(alice.id).databaseName).exists())
    }

    @Test fun replacementAndSameOwnerReloginRevokeWriterAndJournalWithoutTouchingAnotherDatabase() = runBlocking {
        fixtureRule.fixture.publishFreshFix()
        app.accountSession.signIn(alice)
        assignFixtureAdministrator()
        val a = workspace()
        val report = a.repository.createOnce(UUID.randomUUID().toString(), draft())
        val binding = ReportAdministrationBinding.capture(a, allow(alice))
        val access = binding.openStatusChangeAccess()
        val journal = app.openReportStatusChangeJournal(binding, access)
        val request = request(access.scope, report.id)
        journal.retain(request)
        app.accountSession.signIn(bob)
        denied { access.change(request) }
        denied { access.findReceipt(request) }
        denied { journal.read() }
        assertFalse(app.getDatabasePath(ReportStorageNames.fromAccountId(bob.id).databaseName).exists())
        app.accountSession.signOut()
        app.accountSession.signIn(alice)
        denied { access.change(request) }
        denied { journal.read() }
        val reopenedWorkspace = workspace()
        val reopened = ReportAdministrationBinding.capture(reopenedWorkspace, allow(alice))
        val reopenedAccess = reopened.openStatusChangeAccess()
        assertEquals(request, app.openReportStatusChangeJournal(reopened, reopenedAccess).read())
        assertNull(reopenedAccess.findReceipt(request))
        assertEquals(ReportStatus.REPORTED, reopenedWorkspace.repository.findById(report.id)!!.status)
        assertEquals(1L, reopenedWorkspace.historyRepository.readHistory(ReportHistoryQuery(report.id))!!.latestSequence)
    }

    @Test fun closingWorkspaceRevokesPreviouslyConstructedLazyWriterAndJournal() = runBlocking {
        app.accountSession.signIn(alice)
        val w = workspace()
        val binding = ReportAdministrationBinding.capture(w, allow(alice))
        val access = binding.openStatusChangeAccess()
        val journal = app.openReportStatusChangeJournal(binding, access)
        w.close()
        denied { access.findReceipt(request(access.scope)) }
        denied { journal.read() }
        assertFalse(app.getDatabasePath(ReportStorageNames.fromAccountId(alice.id).databaseName).exists())
    }

    @Test fun foregroundGuardReachesSqliteBeforeCommitAndRollsBackEventAndStatus() = runBlocking {
        fixtureRule.fixture.publishFreshFix()
        app.accountSession.signIn(alice)
        assignFixtureAdministrator()
        val w = workspace()
        val report = w.repository.createOnce(UUID.randomUUID().toString(), draft())
        val binding = ReportAdministrationBinding.capture(w, allow(alice))
        // Count only the three guards inside this SQLite transaction path, excluding facade checks.
        var deniedAtCommit = false
        var transactionChecks = 0
        val access = binding.openStatusChangeAccess {
            val insideChange = Thread.currentThread().stackTrace.any {
                it.className.startsWith("com.example.citizensecurity.data.SqliteReportRepository") &&
                    (it.methodName.contains("changeStatusWithActor") || it.className.contains("\$changeStatusWithActor\$"))
            }
            if (insideChange && ++transactionChecks == 3) {
                deniedAtCommit = true
                throw ReportStatusPermissionDeniedException()
            }
        }
        denied { access.change(request(access.scope, report.id)) }
        assertTrue("SQLite did not receive the captured foreground policy", deniedAtCommit)
        assertEquals(ReportStatus.REPORTED, w.repository.findById(report.id)!!.status)
        assertEquals(1L, w.historyRepository.readHistory(ReportHistoryQuery(report.id))!!.latestSequence)
    }

    @Test fun permissiveFacadeCannotBypassTheCapturedRepositoryRoleCheck() = runBlocking {
        fixtureRule.fixture.publishFreshFix()
        app.accountSession.signIn(alice)
        val w = workspace()
        val report = w.repository.createOnce(UUID.randomUUID().toString(), draft())
        val access = ReportAdministrationBinding.capture(w, allow(alice)).openStatusChangeAccess()
        denied { access.change(request(access.scope, report.id)) }
        assertEquals(ReportStatus.REPORTED, w.repository.findById(report.id)!!.status)
        assertEquals(1L, w.historyRepository.readHistory(ReportHistoryQuery(report.id))!!.latestSequence)
    }

    @Test fun unreadableRoleDeniesFacadeAndIndependentRepositoryBeforeOpeningSqlite() = runBlocking {
        app.accountSession.signIn(alice)
        val w = workspace()
        val authorityFile = java.io.File(fixtureRule.fixture.contactsContext.filesDir,
            com.example.citizensecurity.data.LocalAdministratorStore.FILE_NAME)
        val corruptBytes = "Unreadable authority fixture, preserve exactly".toByteArray()
        authorityFile.writeBytes(corruptBytes)
        // First use the real application authority; then bypass only that facade with a test policy.
        val realAccess = app.openReportAdministration(w).openStatusChangeAccess()
        val independentAccess = ReportAdministrationBinding.capture(w, allow(alice)).openStatusChangeAccess()
        listOf(realAccess, independentAccess).forEach { access ->
            denied { access.change(request(access.scope)) }
            denied { access.findReceipt(request(access.scope)) }
        }
        assertFalse(app.getDatabasePath(ReportStorageNames.fromAccountId(alice.id).databaseName).exists())
        assertArrayEquals(corruptBytes, authorityFile.readBytes())
    }

    private fun workspace() = app.openReportWorkspace(app.accountSession.capture()).also(workspaces::add)
    private fun assignFixtureAdministrator() {
        val captured = app.accountSession.capture()
        app.openLocalAdministratorStore().assignFirst(alice, UUID.randomUUID().toString(), Instant.now()) {
            check(app.accountSession.isCurrent(captured))
        }
    }
    private fun draft() = NewReport(IncidentType.RISK, Priority.MEDIUM, "Reporte de revisión administrativa local",
        Instant.now().minusSeconds(30), ReportLocation("Frente al parque", 19.4326, -99.1332))
    private fun allow(account: LocalAccount) = ReportAdministrationPolicy { scope, permission ->
        if (scope != ReportStatusChangeScope(account.id, account.id) || permission != ReportAdministrationPermission.CHANGE_STATUS)
            throw ReportAdministrationPermissionDeniedException()
    }
    private fun request(scope: ReportStatusChangeScope, reportId: String = UUID.randomUUID().toString()) =
        ReportStatusChangeRequest(scope, ReportStatusChangeCommand(UUID.randomUUID().toString(), reportId, 1,
            ReportStatus.REPORTED, ReportStatus.IN_REVIEW, "Comienza revisión local del reporte"), Instant.now())
    private suspend fun denied(block: suspend () -> Any?) {
        try { block(); fail("A denied status operation was accepted") }
        catch (_: ReportStatusPermissionDeniedException) { }
    }
}
