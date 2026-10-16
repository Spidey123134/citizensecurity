package com.example.citizensecurity.administration

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.*
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdministrationConsultationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun configure() = Dispatchers.setMain(dispatcher)
    @After fun dispose() { stores.forEach { it.clear() }; Dispatchers.resetMain() }

    @Test fun constructionIsIdleAndDoesNotReadOrAskForAuthority() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = fixture.model()
        runCurrent()
        assertSame(AdministrationConsultationState.Idle, model.state.value)
        assertTrue(fixture.permissions.isEmpty())
        assertTrue(fixture.reader.calls.isEmpty())
    }

    @Test fun defaultDeniedAuthorityCannotReadAndCannotBeRevivedByLaterGrant() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.allowed = false
        val model = fixture.model()
        model.activate()
        runCurrent()
        assertSame(AdministrationConsultationState.Denied, model.state.value)
        assertTrue(fixture.reader.calls.isEmpty())
        fixture.allowed = true
        assertFalse(model.activate())
        assertFalse(model.loadFolio(FOLIO))
        assertFalse(model.checkAccess())
    }

    @Test fun activationReadsOneBoundedPageAndOnlyAsksForReadReports() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = fixture.model()
        assertTrue(model.activate())
        assertFalse(model.activate())
        runCurrent()
        assertEquals(50, ready(model).request.query.limit)
        assertEquals(listOf(ReportAdministrationPermission.READ_REPORTS), fixture.permissions.distinct())
        assertTrue(fixture.scopes.all { it == fixture.reader.scope })
        assertEquals(1, fixture.reader.pages.size)
    }

    @Test fun folioNormalizesOuterSpacesAndCaseWithoutAcceptingAbbreviationsOrSql() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = fixture.model()
        listOf("", "  ", "1-1-1-1-1", FOLIO.dropLast(1), "' OR 1=1 --", "$FOLIO extra").forEach {
            assertFalse(model.loadFolio(it))
            assertSame(AdministrationConsultationState.Invalid, model.state.value)
        }
        assertTrue(fixture.reader.calls.isEmpty())
        model.loadFolio(" \n${FOLIO.uppercase()}\t")
        runCurrent()
        val detail = detail(model)
        assertEquals(FOLIO, detail.request.reportId)
        assertEquals(listOf(FOLIO, ReportHistoryQuery(FOLIO, limit = 50), FOLIO), fixture.reader.calls)
    }

    @Test fun absentFolioIsNotAnEmptyReportAndCanReturnToListWithFilters() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.reader.onPage = { ReportListPage(emptyList(), false) }
        fixture.reader.onFind = { null }
        val model = fixture.model()
        model.setFilters(IncidentType.FIRE, ReportStatus.ATTENDED, Priority.HIGH)
        runCurrent()
        model.loadFolio(FOLIO)
        runCurrent()
        val missing = model.state.value as AdministrationConsultationState.Missing
        assertTrue(model.backToList(missing))
        runCurrent()
        assertEquals(IncidentType.FIRE, ready(model).request.query.type)
        assertEquals(ReportStatus.ATTENDED, ready(model).request.query.status)
        assertEquals(Priority.HIGH, ready(model).request.query.priority)
        assertEquals(0, fixture.reader.histories.size)
    }

    @Test fun changingFiltersQueriesTheSourceAndDoesNotFilterTheOldPageInMemory() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.reader.onPage = { query -> ReportListPage(listOf(report().copy(
            type = query.type ?: IncidentType.RISK,
            status = query.status ?: ReportStatus.REPORTED,
            priority = query.priority ?: Priority.MEDIUM,
        )), false) }
        val model = fixture.model()
        model.activate(); runCurrent()
        val old = ready(model)
        model.setFilters(IncidentType.ACCIDENT, ReportStatus.CLOSED, Priority.LOW, old)
        assertTrue(model.state.value is AdministrationConsultationState.Loading)
        runCurrent()
        val request = ready(model).request.query
        assertEquals(IncidentType.ACCIDENT, request.type)
        assertEquals(ReportStatus.CLOSED, request.status)
        assertEquals(Priority.LOW, request.priority)
        assertEquals(2, fixture.reader.pages.size)
        assertFalse(model.setFilters(null, null, null, old))
    }

    @Test fun nextAndPreviousReadExclusiveCursorsAndRefreshStartsAtNewest() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.reader.onPage = { query -> if (query.before == null) full(100) else full(50, false) }
        val model = fixture.model()
        model.activate(); runCurrent()
        val initial = ready(model)
        model.nextPage(initial); runCurrent()
        val second = ready(model)
        assertEquals(ReportListCursor.from(initial.page.reports.last()), second.request.query.before)
        assertTrue(second.canPrevious)
        assertFalse(model.nextPage(second))
        assertFalse(model.nextPage(initial))
        model.previousPage(second); runCurrent()
        assertNull(ready(model).request.query.before)
        assertFalse(ready(model).canPrevious)
        model.nextPage(ready(model)); runCurrent()
        model.refresh(ready(model)); runCurrent()
        assertNull(ready(model).request.query.before)
        assertFalse(ready(model).canPrevious)
        assertEquals(5, fixture.reader.pages.size)
    }

    @Test fun pageErrorRetriesTheIdenticalQueryAndKeepsThePreviousCursorPlan() = runTest(dispatcher) {
        val fixture = Fixture()
        var failed = false
        fixture.reader.onPage = { query ->
            if (query.before == null) full(100)
            else if (!failed) { failed = true; throw IOException("private/database/path") }
            else full(50, false)
        }
        val model = fixture.model()
        model.activate(); runCurrent()
        model.nextPage(ready(model)); runCurrent()
        val error = model.state.value as AdministrationConsultationState.Error
        assertFalse(error.message.contains("private"))
        model.retry(error); runCurrent()
        assertSame(fixture.reader.pages[1], fixture.reader.pages[2])
        assertSame(error.request, ready(model).request)
        assertTrue(ready(model).canPrevious)
        assertFalse(model.retry(error))
    }

    @Test fun atMostFiftyPreviousPositionsAreRetainedAndNoneOfTheirRows() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.reader.onPage = { query -> full(query.before?.id?.takeLast(12)?.toInt()?.minus(1) ?: 10000) }
        val model = fixture.model()
        model.activate(); runCurrent()
        repeat(60) { assertTrue(model.nextPage(ready(model))); runCurrent() }
        assertEquals(50, ready(model).request.previous.size)
        repeat(50) { assertTrue(model.previousPage(ready(model))); runCurrent() }
        assertFalse(ready(model).canPrevious)
        assertFalse(model.previousPage(ready(model)))
        assertNotNull(ready(model).request.query.before)
    }

    @Test fun staleVisibleReportCannotOpenAfterThePageWasReplacedEvenWithEqualRows() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = fixture.model()
        model.activate(); runCurrent()
        val obsolete = ready(model)
        model.refresh(obsolete); runCurrent()
        assertEquals(obsolete.page, ready(model).page)
        assertFalse(model.openReport(obsolete, FOLIO))
        assertFalse(model.openReport(ready(model), id(99)))
        assertTrue(model.openReport(ready(model), FOLIO))
        runCurrent()
        assertEquals(FOLIO, detail(model).report.id)
    }

    @Test fun obsoleteNonCooperativeReadCannotReplaceANewerFilteredReading() = runTest(dispatcher) {
        val fixture = Fixture()
        val gate = CompletableDeferred<ReportListPage>()
        fixture.reader.onPage = { query -> if (query.type == null) withContext(NonCancellable) { gate.await() }
            else ReportListPage(listOf(report().copy(type = requireNotNull(query.type))), false) }
        val model = fixture.model()
        model.activate(); runCurrent()
        model.setFilters(IncidentType.FIRE, null, null); runCurrent()
        gate.complete(ReportListPage(listOf(report()), false)); advanceUntilIdle()
        assertEquals(IncidentType.FIRE, ready(model).page.reports.single().type)
    }

    @Test fun cancellationRetiresOutstandingResultsAndDoesNotResumeAReadAutomatically() = runTest(dispatcher) {
        val fixture = Fixture()
        val gate = CompletableDeferred<ReportListPage>()
        fixture.reader.onPage = { withContext(NonCancellable) { gate.await() } }
        val model = fixture.model()
        model.activate(); runCurrent()
        model.cancel()
        gate.complete(ReportListPage(listOf(report()), false)); advanceUntilIdle()
        assertSame(AdministrationConsultationState.Idle, model.state.value)
        assertEquals(1, fixture.reader.pages.size)
    }

    @Test fun revokedSessionClearsPrivateRowsAtTheNextGestureWithoutAnotherRead() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = fixture.model()
        model.activate(); runCurrent()
        val shown = ready(model)
        fixture.current = false
        assertFalse(model.openReport(shown, FOLIO))
        assertSame(AdministrationConsultationState.Denied, model.state.value)
        assertEquals(1, fixture.reader.calls.size)
        fixture.current = true
        assertFalse(model.refresh(AdministrationConsultationState.Denied))
    }

    @Test fun revocationDuringBlockedStorageRejectsItsLateRows() = runTest(dispatcher) {
        val fixture = Fixture()
        val gate = CompletableDeferred<ReportListPage>()
        fixture.reader.onPage = { gate.await() }
        val model = fixture.model()
        model.activate(); runCurrent()
        fixture.allowed = false
        gate.complete(ReportListPage(listOf(report()), false)); runCurrent()
        assertSame(AdministrationConsultationState.Denied, model.state.value)
    }

    @Test fun accessRevalidationCanRevokeVisibleDataWithoutAQueryOrNavigation() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = fixture.model()
        model.activate(); runCurrent()
        fixture.allowed = false
        assertTrue(model.checkAccess())
        runCurrent()
        assertSame(AdministrationConsultationState.Denied, model.state.value)
        assertEquals(1, fixture.reader.calls.size)
    }

    @Test fun losingSessionAfterAuthorityLookupNeverReachesStorage() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.onPolicy = { fixture.current = false }
        val model = fixture.model()
        model.activate(); runCurrent()
        assertSame(AdministrationConsultationState.Denied, model.state.value)
        assertTrue(fixture.reader.calls.isEmpty())
    }

    @Test fun storageErrorFollowedByRevokedAuthorityDoesNotExposeRetryOrTheOldPage() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.reader.onPage = { fixture.allowed = false; throw IOException("secret") }
        val model = fixture.model()
        model.activate(); runCurrent()
        assertSame(AdministrationConsultationState.Denied, model.state.value)
    }

    @Test fun aReaderPermissionDenialIsNotTreatedAsATransientStorageError() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.reader.onPage = { throw ReportAdministrationPermissionDeniedException() }
        val model = fixture.model()
        model.activate(); runCurrent()
        assertSame(AdministrationConsultationState.Denied, model.state.value)
    }

    @Test fun repositoryRunsOnTheInjectedIoDispatcher() = runTest(dispatcher) {
        val fixture = Fixture()
        val reads = StandardTestDispatcher(testScheduler, "administration-read")
        var observed: ContinuationInterceptor? = null
        fixture.reader.onPage = { observed = currentCoroutineContext()[ContinuationInterceptor]; ReportListPage(emptyList(), false) }
        val model = fixture.model(reads)
        model.activate(); runCurrent()
        assertSame(reads, observed)
    }

    @Test fun storeClearingCancelsPendingPrivateResultsAndDisablesFutureGestures() = runTest(dispatcher) {
        val fixture = Fixture()
        val gate = CompletableDeferred<ReportListPage>()
        fixture.reader.onPage = { withContext(NonCancellable) { gate.await() } }
        val model = fixture.model()
        model.activate(); runCurrent()
        stores.last().clear()
        gate.complete(ReportListPage(listOf(report()), false)); advanceUntilIdle()
        assertSame(AdministrationConsultationState.Denied, model.state.value)
        assertFalse(model.activate())
    }

    @Test fun invalidOrderingDuplicatesOversizedOrPartialMorePagesFailClosedAsRetryableReads() = runTest(dispatcher) {
        val invalidPages = listOf(
            ReportListPage(listOf(report(1), report(2)), false),
            ReportListPage(listOf(report(2), report(2)), false),
            ReportListPage((100 downTo 50).map { report(it) }, false),
            ReportListPage(listOf(report()), true),
            ReportListPage(listOf(report().copy(id = "1-1-1-1-1")), false),
        )
        invalidPages.forEach { bad ->
            val fixture = Fixture()
            fixture.reader.onPage = { bad }
            val model = fixture.model()
            model.activate(); runCurrent()
            assertTrue(model.state.value is AdministrationConsultationState.Error)
        }
    }

    @Test fun aRepositoryCannotReturnRowsOutsideTheSelectedFiltersOrExclusiveCursor() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = fixture.model()
        model.setFilters(IncidentType.FIRE, null, null); runCurrent()
        assertTrue(model.state.value is AdministrationConsultationState.Error)
        model.setFilters(null, ReportStatus.CLOSED, null); runCurrent()
        assertTrue(model.state.value is AdministrationConsultationState.Error)
        model.setFilters(null, null, Priority.HIGH); runCurrent()
        assertTrue(model.state.value is AdministrationConsultationState.Error)
        fixture.reader.onPage = { full(100) }
        model.setFilters(null, null, null); runCurrent()
        model.nextPage(ready(model)); runCurrent()
        assertTrue(model.state.value is AdministrationConsultationState.Error)
    }

    @Test fun reportHistoryHasOneConsistentFolioStatusAndRevisionAndRetriesTheExactRequest() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.reader.onHistory = { history().let { ReportHistoryPage(FOLIO, ReportStatus.CLOSED, 0, emptyList(), false, false) } }
        val model = fixture.model()
        model.loadFolio(FOLIO); runCurrent()
        val error = model.state.value as AdministrationConsultationState.Error
        fixture.reader.onHistory = { history() }
        model.retry(error); runCurrent()
        assertSame(error.request, detail(model).request)
        assertEquals(ReportStatus.REPORTED, detail(model).history.currentStatus)
    }

    @Test fun rowChangedDuringTheHistoryReadIsNotDisplayedAsACoherentSnapshot() = runTest(dispatcher) {
        val fixture = Fixture()
        var finds = 0
        fixture.reader.onFind = { if (++finds == 1) report() else report().copy(description = "Cambio concurrente") }
        val model = fixture.model()
        model.loadFolio(FOLIO); runCurrent()
        assertTrue(model.state.value is AdministrationConsultationState.Error)
    }

    @Test fun historyFromAnotherFolioOrMissingForAnExistingReportCannotBePresented() = runTest(dispatcher) {
        listOf<ReportHistoryPage?>(null, ReportHistoryPage(id(9), ReportStatus.REPORTED, 0, emptyList(), false, false)).forEach { bad ->
            val fixture = Fixture()
            fixture.reader.onHistory = { bad }
            val model = fixture.model()
            model.loadFolio(FOLIO); runCurrent()
            assertTrue(model.state.value is AdministrationConsultationState.Error)
        }
    }

    @Test fun legacyRowWithoutEventsStaysExplicitlyIncompleteRatherThanInventingCreation() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.reader.onHistory = { ReportHistoryPage(FOLIO, ReportStatus.REPORTED, 0, emptyList(), false, false) }
        val model = fixture.model()
        model.loadFolio(FOLIO); runCurrent()
        assertTrue(detail(model).history.events.isEmpty())
        assertFalse(detail(model).history.completeFromCreation)
        assertFalse(model.nextHistory(detail(model)))
    }

    @Test fun permissionLostAfterReadingAFolioPreventsItsHistoryFromBeingRead() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.reader.onFind = { fixture.allowed = false; report() }
        val model = fixture.model()
        model.loadFolio(FOLIO); runCurrent()
        assertSame(AdministrationConsultationState.Denied, model.state.value)
        assertTrue(fixture.reader.histories.isEmpty())
    }

    @Test fun aHistoryWithMissingEventsCannotBecomeACleanDetail() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.reader.onHistory = { ReportHistoryPage(FOLIO, ReportStatus.REPORTED, 1, emptyList(), false, false) }
        val model = fixture.model()
        model.loadFolio(FOLIO); runCurrent()
        assertTrue(model.state.value is AdministrationConsultationState.Error)
    }

    private inner class Fixture {
        val reader = Reader()
        var current = true
        var allowed = true
        var onPolicy: () -> Unit = {}
        val permissions = mutableListOf<ReportAdministrationPermission>()
        val scopes = mutableListOf<ReportStatusChangeScope>()
        fun model(io: kotlinx.coroutines.CoroutineDispatcher = dispatcher): AdministrationConsultationViewModel {
            val store = ViewModelStore().also { stores.add(it) }
            val factory = viewModelFactory { initializer {
                AdministrationConsultationViewModel(reader, ReportAdministrationPolicy { scope, permission ->
                    scopes.add(scope); permissions.add(permission)
                    if (!allowed) throw ReportAdministrationPermissionDeniedException()
                    onPolicy()
                }, { check(current) }, io)
            } }
            return ViewModelProvider(store, factory)[AdministrationConsultationViewModel::class.java]
        }
    }

    private class Reader : ReportAdministrationReader {
        override val scope = ReportStatusChangeScope(ACTOR, ACTOR)
        val calls = mutableListOf<Any>()
        val pages = mutableListOf<ReportListPageQuery>()
        val histories = mutableListOf<ReportHistoryQuery>()
        var onPage: suspend (ReportListPageQuery) -> ReportListPage = { ReportListPage(listOf(report()), false) }
        var onFind: suspend (String) -> Report? = { report() }
        var onHistory: suspend (ReportHistoryQuery) -> ReportHistoryPage? = { history() }
        override suspend fun listPage(query: ReportListPageQuery): ReportListPage {
            calls.add(query); pages.add(query); return onPage(query)
        }
        override suspend fun findById(id: String): Report? { calls.add(id); return onFind(id) }
        override suspend fun readHistory(query: ReportHistoryQuery): ReportHistoryPage? {
            calls.add(query); histories.add(query); return onHistory(query)
        }
    }

    private fun ready(model: AdministrationConsultationViewModel) = model.state.value as AdministrationConsultationState.ReadyPage
    private fun detail(model: AdministrationConsultationViewModel) = model.state.value as AdministrationConsultationState.ReadyReport

    private companion object {
        const val FOLIO = "00000000-0000-4000-8000-000000000001"
        const val ACTOR = "00000000-0000-4000-8000-000000999999"
        val TIME: Instant = Instant.parse("2026-10-15T18:00:00Z")
        fun id(number: Int) = "00000000-0000-4000-8000-${number.toString().padStart(12, '0')}"
        fun report(number: Int = 1) = Report(id(number), IncidentType.RISK, Priority.MEDIUM,
            "Descripción completa del reporte local.", TIME, ReportLocation("Referencia local"), TIME)
        fun full(start: Int, more: Boolean = true) = ReportListPage((start downTo start - 49).map { report(it) }, more)
        fun history() = ReportHistoryPage(FOLIO, ReportStatus.REPORTED, 1, listOf(
            ReportStatusEvent(id(1234), 1, ReportStatusEventKind.CREATED, null, ReportStatus.REPORTED, TIME, ACTOR),
        ), false, true)
    }
}
