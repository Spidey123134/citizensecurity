package com.example.citizensecurity.account

import com.example.citizensecurity.domain.*
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ScopedReportHistoryTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val query = ReportHistoryQuery(id)
    private val page = ReportHistoryPage(id, ReportStatus.REPORTED, 0, emptyList(), false, false)

    @Test fun revokedBeforeHistoryDoesNotReachTheDelegate() = runTest {
        var calls = 0
        val access = access({ throw IllegalStateException("revoked") }, ReportHistoryRepository { calls++; page })
        try { access.reportHistoryRepository.readHistory(query); fail("Expected revocation") }
        catch (_: IllegalStateException) { }
        assertEquals(0, calls)
    }

    @Test fun delayedHistoryCannotReturnAfterItsAccountIsRevoked() = runTest {
        var allowed = true
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val access = access({ check(allowed) }, ReportHistoryRepository { entered.complete(Unit); gate.await(); page })
        val read = async {
            try { access.reportHistoryRepository.readHistory(query); fail("Expected revocation") }
            catch (_: IllegalStateException) { }
        }
        entered.await()
        allowed = false
        gate.complete(Unit)
        read.await()
    }

    @Test fun historyKeepsTheExactQueryAndItsMissingResult() = runTest {
        var checks = 0
        var received: ReportHistoryQuery? = null
        val access = access({ checks++ }, ReportHistoryRepository { received = it; null })
        assertNull(access.reportHistoryRepository.readHistory(query))
        assertSame(query, received)
        assertEquals(2, checks)
    }

    @Test fun historyFailureDoesNotAlterTheIndependentReportReader() = runTest {
        val failure = IllegalStateException("History unavailable")
        val access = access({}, ReportHistoryRepository { throw failure })
        try { access.reportHistoryRepository.readHistory(query); fail("Expected failure") }
        catch (actual: IllegalStateException) { assertSame(failure, actual) }
        assertEquals("El reporte sigue disponible", access.reportRepository.findById(id)?.description)
    }

    private fun access(check: () -> Unit, history: ReportHistoryRepository): ScopedReportAccess {
        val report = Report(id, IncidentType.RISK, Priority.MEDIUM, "El reporte sigue disponible",
            Instant.EPOCH, ReportLocation("Referencia del reporte"), Instant.EPOCH)
        val repository = object : IdempotentReportRepository {
            override suspend fun create(draft: NewReport) = report
            override suspend fun createOnce(requestId: String, draft: NewReport) = report
            override suspend fun findById(id: String) = report
            override suspend fun list() = listOf(report)
        }
        val submission = object : ReportSubmissionJournal {
            override suspend fun read(): ReportSubmissionRequest? = null
            override suspend fun write(request: ReportSubmissionRequest) { error("No writes in history") }
            override suspend fun clear(requestId: String) { error("No writes in history") }
        }
        val checkpoint = object : ReportDraftCheckpointJournal {
            override suspend fun read(): ReportDraftCheckpoint? = null
            override suspend fun write(checkpoint: ReportDraftCheckpoint, expectedId: String?, expectedSubmittedFolio: String?) { error("No writes in history") }
            override suspend fun markSubmitted(id: String, folio: String) { error("No writes in history") }
        }
        return ScopedReportAccess(repository, ReportListPageRepository { ReportListPage(emptyList(), false) },
            ReportMapRepository { ReportMapPage(emptyList(), false) },
            SimilarReportRepository { SimilarReportPage(emptyList(), false) }, submission, checkpoint, history, check)
    }
}
