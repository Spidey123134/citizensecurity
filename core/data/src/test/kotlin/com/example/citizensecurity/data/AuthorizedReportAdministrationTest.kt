package com.example.citizensecurity.data

import com.example.citizensecurity.domain.*
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AuthorizedReportAdministrationTest {
    private val scope = ReportStatusChangeScope(id(90), id(91))
    private val at = Instant.parse("2026-10-15T15:00:00.123456789Z")
    private val report = Report(id(1), IncidentType.RISK, Priority.MEDIUM,
        "Luminaria dañada frente al parque", at, ReportLocation("Parque"), at)
    private val emptyHistory = ReportHistoryPage(report.id, ReportStatus.REPORTED, 0L, emptyList(), false, false)
    private val permitRead = ReportAdministrationPolicy { requested, permission ->
        if (requested != scope || permission != ReportAdministrationPermission.READ_REPORTS)
            throw ReportAdministrationPermissionDeniedException()
    }

    @Test fun constructionDoesNotCheckAuthorityInitializeStorageOrRead() {
        var touches = 0
        AuthorizedReportAdministration(scope, ReportAdministrationPolicy { _, _ -> touches++ },
            { touches++; sources() }, { touches++ })
        assertEquals(0, touches)
    }

    @Test fun defaultDenialRejectsEveryReadBeforeTheStorageProvider() = runTest {
        var providers = 0
        val reader = AuthorizedReportAdministration(scope, sources = { providers++; sources() })
        denied { reader.findById(report.id) }
        denied { reader.listPage(ReportListPageQuery()) }
        denied { reader.readHistory(ReportHistoryQuery(report.id)) }
        assertEquals(0, providers)
    }

    @Test fun permittedReadsPreserveTheExactScopeQueriesAndCapturedDelegates() = runTest {
        var providers = 0
        val checks = mutableListOf<Pair<ReportStatusChangeScope, ReportAdministrationPermission>>()
        val pageQuery = ReportListPageQuery(type = IncidentType.RISK, priority = Priority.MEDIUM,
            before = ReportListCursor(at.plusSeconds(1), id(2)), limit = 7)
        val historyQuery = ReportHistoryQuery(report.id, beforeSequence = 3L, limit = 4)
        val page = ReportListPage(listOf(report), false)
        val reader = AuthorizedReportAdministration(scope, ReportAdministrationPolicy { requested, permission ->
            permitRead.check(requested, permission)
            checks += requested to permission
        }, {
            providers++
            ReportAdministrationSources(scope, { requested -> assertEquals(report.id, requested); report },
                ReportListPageRepository { query -> assertSame(pageQuery, query); page },
                ReportHistoryRepository { query -> assertSame(historyQuery, query); emptyHistory })
        })
        assertSame(report, reader.findById(report.id))
        assertSame(page, reader.listPage(pageQuery))
        assertSame(emptyHistory, reader.readHistory(historyQuery))
        assertEquals(1, providers)
        assertTrue(checks.isNotEmpty())
        assertTrue(checks.all { it.first === scope && it.second == ReportAdministrationPermission.READ_REPORTS })
    }

    @Test fun mismatchedOwnerActorOrAcademicSourcesAreRejectedBeforeReading() = runTest {
        listOf(scope.copy(ownerAccountId = id(92)), scope.copy(actorAccountId = id(93)),
            scope.copy(ownerAccountId = null)).forEach { other ->
            var reads = 0
            val reader = AuthorizedReportAdministration(scope, permitRead,
                { sources(other) { reads++ } })
            denied { reader.findById(report.id) }
            assertEquals(0, reads)
        }
    }

    @Test fun revocationWhileOpeningTheProviderPreventsTheFirstRead() = runTest {
        var allowed = true
        var reads = 0
        val policy = ReportAdministrationPolicy { _, _ -> if (!allowed) throw ReportAdministrationPermissionDeniedException() }
        val reader = AuthorizedReportAdministration(scope, policy, {
            allowed = false
            sources { reads++ }
        })
        denied { reader.findById(report.id) }
        assertEquals(0, reads)
    }

    @Test fun everyNextPageRechecksAuthorityWithoutReopeningOrSwitchingStorage() = runTest {
        var allowed = true
        var providers = 0
        var reads = 0
        val reader = AuthorizedReportAdministration(scope,
            ReportAdministrationPolicy { _, _ -> if (!allowed) throw ReportAdministrationPermissionDeniedException() },
            { providers++; sources { reads++ } })
        reader.listPage(ReportListPageQuery(limit = 1))
        allowed = false
        denied { reader.listPage(ReportListPageQuery(before = ReportListCursor.from(report), limit = 1)) }
        assertEquals(1, providers)
        assertEquals(1, reads)
    }

    @Test fun revocationDuringAnySuspendedReadDiscardsItsResult() = runTest {
        ReadKind.entries.forEach { kind ->
            var allowed = true
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val reader = AuthorizedReportAdministration(scope,
                ReportAdministrationPolicy { _, _ -> if (!allowed) throw ReportAdministrationPermissionDeniedException() },
                { sources { entered.complete(Unit); finish.await() } })
            val request = async { denied { read(reader, kind) } }
            entered.await()
            allowed = false
            finish.complete(Unit)
            request.await()
        }
    }

    @Test fun aSessionRevokedDuringReadIsNotHiddenAsMissingOrEmptyData() = runTest {
        var current = true
        val revoked = IllegalStateException("Sesión revocada")
        val reader = AuthorizedReportAdministration(scope, permitRead,
            { sources { current = false } }, { if (!current) throw revoked })
        try { reader.findById(report.id); fail("A revoked session returned a report") }
        catch (failure: IllegalStateException) { assertSame(revoked, failure) }
    }

    @Test fun anAlreadyCancelledRequestNeverCallsTheProvider() = runTest {
        var providers = 0
        var entered = false
        val reader = AuthorizedReportAdministration(scope, permitRead, { providers++; sources() })
        val request = async {
            entered = true
            currentCoroutineContext().cancel()
            reader.findById(report.id)
        }
        cancelled { request.await() }
        assertTrue(entered)
        assertEquals(0, providers)
    }

    @Test fun cancellationDuringProviderInitializationDoesNotEnterADelegate() = runTest {
        var reads = 0
        lateinit var requestJob: Job
        val reader = AuthorizedReportAdministration(scope, permitRead, {
            requestJob.cancel()
            sources { reads++ }
        })
        val request = async {
            requestJob = currentCoroutineContext()[Job]!!
            reader.findById(report.id)
        }
        cancelled { request.await() }
        assertEquals(0, reads)
    }

    @Test fun cancellationDiscardsAResultFromANonCooperativeReader() = runTest {
        lateinit var completion: Continuation<Unit>
        var delivered = false
        val reader = AuthorizedReportAdministration(scope, permitRead,
            { sources { suspendCoroutine<Unit> { completion = it } } })
        val request = async(start = CoroutineStart.UNDISPATCHED) {
            reader.findById(report.id)
            delivered = true
        }
        request.cancel()
        completion.resume(Unit)
        runCurrent()
        cancelled { request.await() }
        assertFalse(delivered)
    }

    @Test fun storageFailureIsPropagatedWithoutRetryingOrReplacingItsData() = runTest {
        var reads = 0
        val unavailable = IOException("Base no disponible")
        val reader = AuthorizedReportAdministration(scope, permitRead,
            { sources { reads++; throw unavailable } })
        try { reader.findById(report.id); fail("Storage failure became an empty result") }
        catch (failure: IOException) { assertSame(unavailable, failure) }
        assertEquals(1, reads)
    }

    @Test fun legitimatelyMissingReportsAndHistoryRemainMissingWithoutSyntheticEvents() = runTest {
        val reader = AuthorizedReportAdministration(scope, permitRead, {
            ReportAdministrationSources(scope, { null }, ReportListPageRepository { ReportListPage(emptyList(), false) },
                ReportHistoryRepository { null })
        })
        assertNull(reader.findById(report.id))
        assertNull(reader.readHistory(ReportHistoryQuery(report.id)))
        assertTrue(reader.listPage(ReportListPageQuery()).reports.isEmpty())
    }

    @Test fun incompatibleFoliosAndOversizedPagesAreNotExposed() = runTest {
        val wrongReport = report.copy(id = id(2))
        val wrongHistory = ReportHistoryPage(id(2), ReportStatus.REPORTED, 0L, emptyList(), false, false)
        val reader = AuthorizedReportAdministration(scope, permitRead, {
            ReportAdministrationSources(scope, { wrongReport },
                ReportListPageRepository { ReportListPage(listOf(report, wrongReport), false) },
                ReportHistoryRepository { wrongHistory })
        })
        incompatible { reader.findById(report.id) }
        incompatible { reader.listPage(ReportListPageQuery(limit = 1)) }
        incompatible { reader.readHistory(ReportHistoryQuery(report.id)) }
    }

    private fun sources(capturedScope: ReportStatusChangeScope = scope, onRead: suspend () -> Unit = {}) =
        ReportAdministrationSources(capturedScope, { onRead(); report },
            ReportListPageRepository { onRead(); ReportListPage(listOf(report), false) },
            ReportHistoryRepository { onRead(); emptyHistory })

    private enum class ReadKind { REPORT, PAGE, HISTORY }
    private suspend fun read(reader: ReportAdministrationReader, kind: ReadKind): Any? = when (kind) {
        ReadKind.REPORT -> reader.findById(report.id)
        ReadKind.PAGE -> reader.listPage(ReportListPageQuery())
        ReadKind.HISTORY -> reader.readHistory(ReportHistoryQuery(report.id))
    }
    private fun id(number: Int) = "a0000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
    private suspend fun denied(block: suspend () -> Any?) {
        try { block(); fail("Administrative data was read without permission") }
        catch (_: ReportAdministrationPermissionDeniedException) { }
    }
    private suspend fun cancelled(block: suspend () -> Any?) {
        try { block(); fail("Cancellation was ignored") }
        catch (_: CancellationException) { }
    }
    private suspend fun incompatible(block: suspend () -> Any?) {
        try { block(); fail("An incompatible administrative result was exposed") }
        catch (_: IllegalStateException) { }
    }
}
