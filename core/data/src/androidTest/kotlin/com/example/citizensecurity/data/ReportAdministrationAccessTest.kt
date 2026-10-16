package com.example.citizensecurity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Only random test databases and test-only authority; never assigns a product account a role. */
@RunWith(AndroidJUnit4::class)
class ReportAdministrationAccessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = mutableListOf<String>()
    private val scope = ReportStatusChangeScope(UUID.randomUUID().toString(), UUID.randomUUID().toString())
    private val id = UUID.randomUUID().toString()
    private val now = Instant.parse("2026-10-15T19:00:00Z")
    private fun name() = "administration_test_${UUID.randomUUID()}.db".also(names::add)
    private fun repository(name: String) = SqliteReportRepository(context, name,
        Clock.fixed(now, ZoneOffset.UTC), authorAccountId = scope.actorAccountId,
        statusAuthority = { scope.actorAccountId })
    private fun draft() = NewReport(IncidentType.RISK, Priority.HIGH, "Árbol caído frente al parque.",
        now.minusSeconds(60), ReportLocation("Frente al parque", 19.0, -99.0))
    private fun request() = ReportStatusChangeRequest(scope, ReportStatusChangeCommand(
        UUID.randomUUID().toString(), id, 1, ReportStatus.REPORTED, ReportStatus.IN_REVIEW,
        "Revisión académica autorizada"), now)
    private fun policy(permission: ReportAdministrationPermission) = ReportAdministrationPolicy { requested, action ->
        if (requested != scope || action != permission) throw ReportAdministrationPermissionDeniedException()
    }
    private fun reader(repo: SqliteReportRepository, policy: ReportAdministrationPolicy,
        sourcesScope: ReportStatusChangeScope = scope) = AuthorizedReportAdministration(scope, policy, {
        ReportAdministrationSources(sourcesScope, repo::findById, repo, repo)
    })

    @After fun cleanOwnedFiles() { names.forEach { context.deleteDatabase(it) } }

    @Test fun deniedReaderDoesNotEvenConstructSqliteSources() = runBlocking {
        val name = name(); var opened = false
        val denied = AuthorizedReportAdministration(scope, sources = {
            opened = true
            error("An unauthorized reader must not initialize storage")
        })
        expect<ReportAdministrationPermissionDeniedException> { denied.listPage(ReportListPageQuery()) }
        expect<ReportAdministrationPermissionDeniedException> { denied.findById(id) }
        expect<ReportAdministrationPermissionDeniedException> { denied.readHistory(ReportHistoryQuery(id)) }
        assertFalse(opened); assertFalse(context.getDatabasePath(name).exists())
    }

    @Test fun readOnlyPermissionCannotWriteOrRecoverAChangeEvenWithSqlAuthority() = runBlocking {
        val name = name()
        repository(name).use { repo ->
            val access = SqliteReportStatusChangeAccess(scope, repo, policy(ReportAdministrationPermission.READ_REPORTS))
            expect<ReportStatusPermissionDeniedException> { access.change(request()) }
            expect<ReportStatusPermissionDeniedException> { access.findReceipt(request()) }
        }
        assertFalse(context.getDatabasePath(name).exists())
    }

    @Test fun changePermissionDoesNotAuthorizeAdministrativeReading() = runBlocking {
        val name = name()
        repository(name).use { repo ->
            val denied = reader(repo, policy(ReportAdministrationPermission.CHANGE_STATUS))
            expect<ReportAdministrationPermissionDeniedException> { denied.findById(id) }
            expect<ReportAdministrationPermissionDeniedException> { denied.listPage(ReportListPageQuery()) }
            expect<ReportAdministrationPermissionDeniedException> { denied.readHistory(ReportHistoryQuery(id)) }
        }
        assertFalse(context.getDatabasePath(name).exists())
    }

    @Test fun authorizedSqliteReaderReturnsRealFolioPageAndHistoryWithoutChanges() = runBlocking {
        val name = name()
        val report = repository(name).use { it.createOnce(id, draft()) }
        val bytes = context.getDatabasePath(name).readBytes()
        repository(name).use { repo ->
            val access = reader(repo, policy(ReportAdministrationPermission.READ_REPORTS))
            assertEquals(report, access.findById(id))
            assertEquals(listOf(report), access.listPage(ReportListPageQuery(limit = 1)).reports)
            val history = access.readHistory(ReportHistoryQuery(id, limit = 1))!!
            assertEquals(id, history.reportId); assertEquals(1L, history.latestSequence)
            assertEquals(ReportStatusEventKind.CREATED, history.events.single().kind)
        }
        assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
    }

    @Test fun wrongOwnerOrActorSourcesCannotOpenSqliteData() = runBlocking {
        val name = name()
        repository(name).use { repo ->
            listOf(scope.copy(ownerAccountId = null), scope.copy(actorAccountId = UUID.randomUUID().toString())).forEach {
                val denied = reader(repo, policy(ReportAdministrationPermission.READ_REPORTS), it)
                expect<ReportAdministrationPermissionDeniedException> { denied.listPage(ReportListPageQuery()) }
            }
        }
        assertFalse(context.getDatabasePath(name).exists())
    }

    @Test fun policyReceivesExactCapturedActorOwnerAndActionForWriteAndReceipt() = runBlocking {
        val name = name(); val request = request(); val observations = mutableListOf<ReportAdministrationPermission>()
        val authority = ReportAdministrationPolicy { requested, permission ->
            assertEquals(scope, requested); observations.add(permission)
            if (permission != ReportAdministrationPermission.CHANGE_STATUS) throw ReportAdministrationPermissionDeniedException()
        }
        repository(name).use { repo ->
            repo.createOnce(id, draft())
            val access = SqliteReportStatusChangeAccess(scope, repo, authority)
            val event = access.change(request)
            assertEquals(event, access.findReceipt(request)!!.event)
            assertEquals(scope.actorAccountId, event.actorAccountId)
            assertEquals(2, repo.readHistory(ReportHistoryQuery(id))!!.events.size)
        }
        assertTrue(observations.size >= 5)
        assertTrue(observations.all { it == ReportAdministrationPermission.CHANGE_STATUS })
    }

    @Test fun policyRevokedBeforeCommitRollsBackStatusAndEvent() = runBlocking {
        val name = name(); var checks = 0
        val authority = ReportAdministrationPolicy { _, _ ->
            if (++checks >= 4) throw ReportAdministrationPermissionDeniedException()
        }
        repository(name).use { repo ->
            repo.createOnce(id, draft())
            expect<ReportStatusPermissionDeniedException> { SqliteReportStatusChangeAccess(scope, repo, authority).change(request()) }
            assertEquals(ReportStatus.REPORTED, repo.findById(id)!!.status)
            assertEquals(1L, repo.readHistory(ReportHistoryQuery(id))!!.latestSequence)
        }
        assertEquals(4, checks)
    }

    @Test fun policyRevokedAfterCommitKeepsExactReceiptForExplicitRecovery() = runBlocking {
        val name = name(); val request = request(); var checks = 0
        val authority = ReportAdministrationPolicy { _, _ ->
            if (++checks >= 5) throw ReportAdministrationPermissionDeniedException()
        }
        repository(name).use { repo ->
            repo.createOnce(id, draft())
            expect<ReportStatusPermissionDeniedException> { SqliteReportStatusChangeAccess(scope, repo, authority).change(request) }
        }
        repository(name).use { repo ->
            val access = SqliteReportStatusChangeAccess(scope, repo, policy(ReportAdministrationPermission.CHANGE_STATUS))
            assertTrue(access.findReceipt(request)!!.matches(request))
            assertEquals(2, repo.readHistory(ReportHistoryQuery(id))!!.events.size)
        }
    }

    private suspend inline fun <reified T : Throwable> expect(noinline action: suspend () -> Any?) {
        try { action(); fail("Expected ${T::class.java.simpleName}") }
        catch (error: Throwable) { if (error !is T) throw error }
    }
}
