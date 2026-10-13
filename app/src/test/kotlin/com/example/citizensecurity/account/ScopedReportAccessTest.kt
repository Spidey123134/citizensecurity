package com.example.citizensecurity.account

import com.example.citizensecurity.domain.*
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** All thirteen delegated entries must reject a revoked lease before and after an operation. */
@RunWith(Parameterized::class)
class ScopedReportAccessEntryTest(private val operation: String) {
    @Test fun revokedBeforeEntryDoesNotTouchDelegate() = runTest {
        val fixture = ScopedAccessFixture()
        fixture.allowed = false
        expectRevoked { fixture.invoke(operation) }
        assertEquals(0, fixture.calls)
    }

    @Test fun revokedWhileDelegateRunsCannotReturnItsResult() = runTest {
        val fixture = ScopedAccessFixture()
        fixture.onCall = { fixture.allowed = false }
        expectRevoked { fixture.invoke(operation) }
        assertEquals(1, fixture.calls)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun operations() = listOf("create", "createOnce", "find", "list", "page", "map", "similar",
            "submissionRead", "submissionWrite", "submissionClear", "checkpointRead", "checkpointWrite", "markSubmitted")
    }
}

class ScopedReportAccessTest {
    @Test fun delayedReadIsDiscardedAfterRevocationWithoutRetargetingToAnotherOwner() = runTest {
        val fixture = ScopedAccessFixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        fixture.onCall = { entered.complete(Unit); release.await() }
        val pending = async { expectRevoked { fixture.access.reportRepository.findById(fixture.report.id) } }
        entered.await()
        fixture.allowed = false
        release.complete(Unit)
        pending.await()
        assertEquals(1, fixture.calls)
        assertEquals("find", fixture.lastCall)
    }

    @Test fun createOncePreservesIdentityDraftAndTheConfirmedResult() = runTest {
        val fixture = ScopedAccessFixture()
        val result = fixture.access.reportRepository.createOnce(fixture.report.id, fixture.draft)
        assertSame(fixture.report, result)
        assertEquals(fixture.report.id, fixture.requestId)
        assertSame(fixture.draft, fixture.receivedDraft)
        assertEquals(2, fixture.accessChecks)
    }

    @Test fun checkpointWritePreservesBothCasValuesAndObjectIdentity() = runTest {
        val fixture = ScopedAccessFixture()
        fixture.access.reportDraftCheckpointJournal.write(fixture.checkpoint, fixture.report.id, fixture.folio)
        assertSame(fixture.checkpoint, fixture.receivedCheckpoint)
        assertEquals(fixture.report.id, fixture.expectedId)
        assertEquals(fixture.folio, fixture.expectedFolio)
        assertEquals(2, fixture.accessChecks)
    }

    @Test fun recoveryJournalKeepsItsRequestAndClearIdentity() = runTest {
        val fixture = ScopedAccessFixture()
        fixture.access.reportSubmissionJournal.write(fixture.request)
        assertSame(fixture.request, fixture.receivedRequest)
        assertSame(fixture.request, fixture.access.reportSubmissionJournal.read())
        fixture.access.reportSubmissionJournal.clear(fixture.report.id)
        assertEquals(fixture.report.id, fixture.requestId)
    }

    @Test fun queriesAndTheirResultsArePassedUnmodified() = runTest {
        val fixture = ScopedAccessFixture()
        assertSame(fixture.page, fixture.access.reportListPageRepository.listPage(fixture.pageQuery))
        assertSame(fixture.pageQuery, fixture.receivedQuery)
        assertSame(fixture.mapPage, fixture.access.reportMapRepository.queryMarkers(fixture.mapQuery))
        assertSame(fixture.mapQuery, fixture.receivedQuery)
        assertSame(fixture.similarPage, fixture.access.similarReportRepository.findSimilar(fixture.similarQuery))
        assertSame(fixture.similarQuery, fixture.receivedQuery)
    }

    @Test fun delegateFailurePropagatesWithoutAnotherOperation() = runTest {
        val fixture = ScopedAccessFixture()
        val failure = java.io.IOException("No se pudo leer.")
        fixture.onCall = { throw failure }
        try {
            fixture.access.reportRepository.list()
            fail("The delegate failure must remain visible.")
        } catch (actual: java.io.IOException) { assertSame(failure, actual) }
        assertEquals(1, fixture.calls)
    }
}

private suspend fun expectRevoked(action: suspend () -> Unit) {
    try { action(); fail("A revoked lease must not return data.") }
    catch (_: RevokedFixtureException) { /* expected */ }
}

private class RevokedFixtureException : IllegalStateException()

private class ScopedAccessFixture {
    var allowed = true
    var accessChecks = 0
    var calls = 0
    var lastCall: String? = null
    var onCall: suspend () -> Unit = {}
    var requestId: String? = null
    var receivedDraft: NewReport? = null
    var receivedCheckpoint: ReportDraftCheckpoint? = null
    var expectedId: String? = null
    var expectedFolio: String? = null
    var receivedRequest: ReportSubmissionRequest? = null
    var receivedQuery: Any? = null
    private val time = Instant.parse("2026-10-12T12:00:00Z")
    val folio = "00000000-0000-0000-0000-000000000002"
    val draft = NewReport(IncidentType.RISK, Priority.HIGH, "Descripción de un incidente local", time,
        ReportLocation("Referencia válida", 19.0, -99.0))
    val report = Report("00000000-0000-0000-0000-000000000001", draft.type, draft.priority,
        draft.description, time, draft.location, time, ReportStatus.REPORTED)
    val request = ReportSubmissionRequest(report.id, draft, time)
    val checkpoint = ReportDraftCheckpoint(folio, draft, time)
    val pageQuery = ReportListPageQuery(priority = Priority.HIGH)
    val page = ReportListPage(listOf(report), false)
    val mapQuery = ReportMapQuery(MapRegion(18.0, 20.0, -100.0, -98.0))
    val mapPage = ReportMapPage(emptyList(), false)
    val similarQuery = SimilarReportQuery(draft.type, time, 19.0, -99.0)
    val similarPage = SimilarReportPage(emptyList(), false)

    private suspend fun entered(name: String) { calls++; lastCall = name; onCall() }
    private val repository = object : IdempotentReportRepository {
        override suspend fun create(draft: NewReport): Report { entered("create"); receivedDraft = draft; return report }
        override suspend fun createOnce(requestId: String, draft: NewReport): Report {
            entered("createOnce"); this@ScopedAccessFixture.requestId = requestId; receivedDraft = draft; return report
        }
        override suspend fun findById(id: String): Report? { entered("find"); requestId = id; return report }
        override suspend fun list(): List<Report> { entered("list"); return listOf(report) }
    }
    private val submission = object : ReportSubmissionJournal {
        override suspend fun read(): ReportSubmissionRequest? { entered("submissionRead"); return request }
        override suspend fun write(request: ReportSubmissionRequest) { entered("submissionWrite"); receivedRequest = request }
        override suspend fun clear(requestId: String) { entered("submissionClear"); this@ScopedAccessFixture.requestId = requestId }
    }
    private val checkpointJournal = object : ReportDraftCheckpointJournal {
        override suspend fun read(): ReportDraftCheckpoint? { entered("checkpointRead"); return checkpoint }
        override suspend fun write(checkpoint: ReportDraftCheckpoint, expectedId: String?, expectedSubmittedFolio: String?) {
            entered("checkpointWrite"); receivedCheckpoint = checkpoint
            this@ScopedAccessFixture.expectedId = expectedId; expectedFolio = expectedSubmittedFolio
        }
        override suspend fun markSubmitted(id: String, folio: String) { entered("markSubmitted"); expectedId = id; expectedFolio = folio }
    }
    val access = ScopedReportAccess(repository,
        ReportListPageRepository { entered("page"); receivedQuery = it; page },
        ReportMapRepository { entered("map"); receivedQuery = it; mapPage },
        SimilarReportRepository { entered("similar"); receivedQuery = it; similarPage },
        submission, checkpointJournal,
    ) { accessChecks++; if (!allowed) throw RevokedFixtureException() }

    suspend fun invoke(operation: String) {
        when (operation) {
            "create" -> access.reportRepository.create(draft)
            "createOnce" -> access.reportRepository.createOnce(report.id, draft)
            "find" -> access.reportRepository.findById(report.id)
            "list" -> access.reportRepository.list()
            "page" -> access.reportListPageRepository.listPage(pageQuery)
            "map" -> access.reportMapRepository.queryMarkers(mapQuery)
            "similar" -> access.similarReportRepository.findSimilar(similarQuery)
            "submissionRead" -> access.reportSubmissionJournal.read()
            "submissionWrite" -> access.reportSubmissionJournal.write(request)
            "submissionClear" -> access.reportSubmissionJournal.clear(report.id)
            "checkpointRead" -> access.reportDraftCheckpointJournal.read()
            "checkpointWrite" -> access.reportDraftCheckpointJournal.write(checkpoint, null, null)
            "markSubmitted" -> access.reportDraftCheckpointJournal.markSubmitted(checkpoint.id, folio)
            else -> error("Unknown test entry.")
        }
    }
}
