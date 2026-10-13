package com.example.citizensecurity.report

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportListCursor
import com.example.citizensecurity.domain.ReportListPage
import com.example.citizensecurity.domain.ReportListPageQuery
import com.example.citizensecurity.domain.ReportListPageRepository
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportStatus
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportPagedSearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun configureDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun releaseModels() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test
    fun idleDoesNotReadAndEmptyHistoryProducesAnImmutableDefinitivePage() = runTest(dispatcher) {
        val repository = RecordingRepository(emptyList())
        val model = model(repository)
        assertSame(ReportPagedSearchState.Idle, model.state.value)
        assertTrue(repository.queries.isEmpty())
        model.nextPage()
        model.previousPage()
        model.retry()
        assertTrue(repository.queries.isEmpty())
        val selected = filters()
        model.search(selected)
        assertTrue(model.state.value is ReportPagedSearchState.Loading)
        runCurrent()
        val ready = ready(model)
        assertSame(selected, ready.filters)
        assertEquals(0, ready.examined)
        assertEquals(1, ready.pageNumber)
        assertFalse(ready.hasPrevious)
        assertNull(ready.nextCursor)
        assertTrue(ready.reports.isEmpty())
        try {
            (ready.reports as MutableList<Report>).add(report(0))
            fail("A published page must be immutable")
        } catch (_: UnsupportedOperationException) {
            // The result does not expose mutable repository or worker storage.
        }
    }

    @Test
    fun fiftyMatchesUseTheLastExaminedTieBreakerWithoutSkippingAFetchedSuffix() = runTest(dispatcher) {
        val createdAt = Instant.parse("2026-10-11T18:00:10.123456789Z")
        val source = (0 until 105).map { report(it).copy(createdAt = createdAt) }
            .sortedByDescending { it.id }
        val repository = RecordingRepository(source)
        val model = model(repository)
        model.search(filters())
        runCurrent()
        val first = ready(model)
        assertEquals(source.take(50), first.reports)
        assertSame(source[0], first.reports[0])
        assertEquals(50, first.examined)
        assertEquals(ReportListCursor.from(source[49]), first.nextCursor)
        model.nextPage()
        runCurrent()
        val second = ready(model)
        assertEquals(source.subList(50, 100), second.reports)
        assertEquals(first.nextCursor, repository.queries[1].before)
        assertEquals(2, second.pageNumber)
        assertTrue(second.hasPrevious)
        model.nextPage()
        runCurrent()
        assertEquals(source.takeLast(5), ready(model).reports)
        assertEquals(5, ready(model).examined)
        assertNull(ready(model).nextCursor)
        model.nextPage()
        assertEquals(3, repository.queries.size)
        assertTrue(repository.queries.all { it.limit == 100 })
    }

    @Test
    fun sparseMatchesStopInsideTheSecondBatchAndContinueAfterExactlyThatRow() = runTest(dispatcher) {
        val source = (0 until 150).map { index ->
            report(index, if (index < 49 || index == 124 || index == 135) "Árbol" else "Sin coincidencia")
        }
        val repository = RecordingRepository(source)
        val model = model(repository)
        model.search(filters(text = "ARBOL"))
        runCurrent()
        val first = ready(model)
        assertEquals(source.take(49) + source[124], first.reports)
        assertEquals(125, first.examined)
        assertEquals(ReportListCursor.from(source[124]), first.nextCursor)
        assertEquals(2, repository.queries.size)
        model.nextPage()
        runCurrent()
        assertEquals(listOf(source[135]), ready(model).reports)
        assertEquals(25, ready(model).examined)
        assertEquals(first.nextCursor, repository.queries[2].before)
        assertNull(ready(model).nextCursor)
    }

    @Test
    fun fiveHundredNonMatchesAreIncompleteAndTheNextGestureFindsALaterMatch() = runTest(dispatcher) {
        val source = (0 until 601).map { report(it, if (it == 600) "Coincidencia" else "Otra referencia") }
        val repository = RecordingRepository(source)
        val model = model(repository)
        model.search(filters(text = "coincidencia"))
        runCurrent()
        val first = ready(model)
        assertTrue(first.reports.isEmpty())
        assertEquals(500, first.examined)
        assertEquals(ReportListCursor.from(source[499]), first.nextCursor)
        assertEquals(5, repository.queries.size)
        model.nextPage()
        runCurrent()
        assertEquals(listOf(source.last()), ready(model).reports)
        assertEquals(101, ready(model).examined)
        assertEquals(2, ready(model).pageNumber)
        assertNull(ready(model).nextCursor)
        assertEquals(first.nextCursor, repository.queries[5].before)
        assertEquals(7, repository.queries.size)
    }

    @Test
    fun exactlyFiveHundredCandidatesWithoutMoreRowsAreDefinitivelyEmpty() = runTest(dispatcher) {
        val repository = RecordingRepository((0 until 500).map { report(it) })
        val model = model(repository)
        model.search(filters(text = "No existe"))
        runCurrent()
        assertTrue(ready(model).reports.isEmpty())
        assertEquals(500, ready(model).examined)
        assertNull(ready(model).nextCursor)
        assertEquals(5, repository.queries.size)
    }

    @Test
    fun structuralCriteriaReachTheRepositoryAndTextKeepsLiteralAccentAndReferenceSemantics() = runTest(dispatcher) {
        val source = listOf(
            report(0, "ÁRBOL AÑO %_"),
            report(1, "Árbol ano %_"),
            report(2, "Otro texto").copy(location = ReportLocation(reference = "Junto al árbol año %_")),
            report(3, "Árbol año %_").copy(priority = Priority.LOW),
            report(4, "Árbol año %_").copy(status = ReportStatus.ATTENDED),
            report(5, "Árbol año %_").copy(type = IncidentType.THEFT),
            report(6, "Árbol año %_").copy(occurredAt = Instant.parse("2026-10-12T00:00:00Z")),
            report(7, "Árbol año AB"),
        )
        val selected = filters(
            text = "  árbol año %_  ", priority = Priority.HIGH, type = IncidentType.RISK,
            status = ReportStatus.REPORTED, startDate = "11/10/2026", endDate = "11/10/2026",
        )
        val repository = RecordingRepository(source)
        val model = model(repository)
        model.search(selected)
        runCurrent()
        assertEquals(listOf(source[0], source[1], source[2]), ready(model).reports)
        val query = repository.queries.single()
        assertEquals(selected.type, query.type)
        assertEquals(selected.status, query.status)
        assertEquals(selected.priority, query.priority)
        assertEquals(selected.startInclusive, query.startInclusive)
        assertEquals(selected.endExclusive, query.endExclusive)
    }

    @Test
    fun aFailedNextRetriesItsSameStartAndOnlyThenCommitsNavigationHistory() = runTest(dispatcher) {
        val source = (0 until 120).map { report(it) }
        val queries = mutableListOf<ReportListPageQuery>()
        var failNext = true
        val model = model(ReportListPageRepository { query ->
            queries.add(query)
            if (query.before != null && failNext) {
                failNext = false
                throw IOException("Private database path")
            }
            page(source, query)
        })
        val selected = filters()
        model.search(selected)
        runCurrent()
        val first = ready(model)
        model.nextPage()
        runCurrent()
        val error = model.state.value as ReportPagedSearchState.Error
        assertSame(selected, error.filters)
        assertEquals("No se pudo completar la búsqueda. Intenta nuevamente.", error.message)
        assertNull(currentPagedResult(selected, error))
        model.retry()
        runCurrent()
        assertEquals(queries[1], queries[2])
        assertEquals(2, ready(model).pageNumber)
        assertSame(selected, ready(model).filters)
        model.previousPage()
        runCurrent()
        assertEquals(first.reports, ready(model).reports)
        assertEquals(1, ready(model).pageNumber)
        assertFalse(ready(model).hasPrevious)
        assertNull(queries.last().before)
    }

    @Test
    fun previousUsesAFreshReadOfTheStoredStartAndNextReplacesItsOldForwardBranch() = runTest(dispatcher) {
        var source = (0 until 160).map { report(it) }
        val queries = mutableListOf<ReportListPageQuery>()
        val model = model(ReportListPageRepository { query -> queries.add(query); page(source, query) })
        val selected = filters()
        model.search(selected)
        runCurrent()
        model.nextPage()
        runCurrent()
        model.nextPage()
        runCurrent()
        source = source.mapIndexed { index, report -> if (index == 50) report.copy(description = "Actualizado") else report }
        model.previousPage()
        runCurrent()
        assertEquals(2, ready(model).pageNumber)
        assertSame(source[50], ready(model).reports.first())
        assertEquals(ReportListCursor.from(source[49]), queries.last().before)
        assertSame(selected, ready(model).filters)
        model.nextPage()
        runCurrent()
        assertEquals(3, ready(model).pageNumber)
        model.previousPage()
        runCurrent()
        assertEquals(2, ready(model).pageNumber)
        model.previousPage()
        runCurrent()
        assertEquals(1, ready(model).pageNumber)
        assertFalse(ready(model).hasPrevious)
    }

    @Test
    fun onlyFiftyPageStartsAreRetainedAndExplicitSearchAlwaysRestartsAtOne() = runTest(dispatcher) {
        val repository = RecordingRepository((0 until 2550).map { report(it) })
        val model = model(repository)
        val selected = filters()
        model.search(selected)
        runCurrent()
        repeat(50) { model.nextPage(); runCurrent() }
        assertEquals(51, ready(model).pageNumber)
        repeat(49) { model.previousPage(); runCurrent() }
        assertEquals(2, ready(model).pageNumber)
        assertFalse(ready(model).hasPrevious)
        val reads = repository.queries.size
        model.previousPage()
        assertEquals(reads, repository.queries.size)
        model.search(selected)
        runCurrent()
        assertEquals(1, ready(model).pageNumber)
        assertFalse(ready(model).hasPrevious)
        assertNull(repository.queries.last().before)
    }

    @Test
    fun equivalentRecreatedCriteriaReloadTheSamePageAndRebindItsExactIdentity() = runTest(dispatcher) {
        val repository = RecordingRepository((0 until 120).map { report(it) })
        val model = model(repository)
        val original = filters()
        model.search(original)
        runCurrent()
        model.nextPage()
        runCurrent()
        val secondStart = repository.queries.last().before
        val recreated = filters()
        model.activate(recreated)
        assertNull(currentPagedResult(recreated, model.state.value))
        runCurrent()
        assertEquals(2, ready(model).pageNumber)
        assertEquals(secondStart, repository.queries.last().before)
        assertSame(recreated, ready(model).filters)
        assertSame(ready(model), currentPagedResult(recreated, model.state.value))
        assertNull(currentPagedResult(original, model.state.value))
        model.previousPage()
        runCurrent()
        assertEquals(1, ready(model).pageNumber)
        assertFalse(ready(model).hasPrevious)
    }

    @Test
    fun leavingForADetailInvalidatesThePageAndActivationResumesItsStoredStart() = runTest(dispatcher) {
        val repository = RecordingRepository((0 until 120).map { report(it) })
        val model = model(repository)
        model.search(filters())
        runCurrent()
        model.nextPage()
        runCurrent()
        val secondStart = repository.queries.last().before
        model.cancelSearch()
        assertSame(ReportPagedSearchState.Idle, model.state.value)
        val rebound = filters()
        model.activate(rebound)
        runCurrent()
        assertEquals(2, ready(model).pageNumber)
        assertEquals(secondStart, repository.queries.last().before)
        assertTrue(ready(model).hasPrevious)
        assertSame(rebound, ready(model).filters)
        model.previousPage()
        runCurrent()
        assertEquals(1, ready(model).pageNumber)
    }

    @Test
    fun changedCriteriaOnActivationDiscardThePreviousTraversal() = runTest(dispatcher) {
        val repository = RecordingRepository((0 until 120).map { report(it, if (it % 2 == 0) "Árbol" else "Calle") })
        val model = model(repository)
        val original = filters()
        model.search(original)
        runCurrent()
        model.nextPage()
        runCurrent()
        model.cancelSearch()
        val changed = filters(text = "árbol", priority = Priority.HIGH)
        model.activate(changed)
        runCurrent()
        assertEquals(1, ready(model).pageNumber)
        assertFalse(ready(model).hasPrevious)
        assertNull(repository.queries[2].before)
        assertSame(changed, ready(model).filters)
        assertNull(currentPagedResult(original, model.state.value))
    }

    @Test
    fun lateSuccessAndFailureOfNonCooperativeOldReadsCannotReplaceNewCriteria() = runTest(dispatcher) {
        for (fails in listOf(false, true)) {
            val completion = CompletableDeferred<Unit>()
            val jobs = mutableListOf<Job>()
            val oldReport = report(1).copy(priority = Priority.LOW)
            val newReport = report(0)
            val model = model(ReportListPageRepository { query ->
                jobs.add(currentCoroutineContext()[Job]!!)
                if (query.priority == Priority.LOW) {
                    withContext(NonCancellable) { completion.await() }
                    if (fails) throw IOException("Old private failure")
                    ReportListPage(listOf(oldReport), false)
                } else ReportListPage(listOf(newReport), false)
            })
            model.search(filters(priority = Priority.LOW))
            runCurrent()
            val latest = filters(priority = Priority.HIGH)
            model.search(latest)
            runCurrent()
            assertTrue(jobs.first().isCancelled)
            completion.complete(Unit)
            advanceUntilIdle()
            assertSame(latest, ready(model).filters)
            assertEquals(listOf(newReport), ready(model).reports)
        }
    }

    @Test
    fun cancellingAPendingNavigationIgnoresItsLateReadAndResumesThatSamePage() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val source = (0 until 120).map { report(it) }
        val queries = mutableListOf<ReportListPageQuery>()
        var delayNext = true
        val model = model(ReportListPageRepository { query ->
            queries.add(query)
            if (query.before != null && delayNext) {
                delayNext = false
                withContext(NonCancellable) { completion.await() }
            }
            page(source, query)
        })
        model.search(filters())
        runCurrent()
        model.nextPage()
        runCurrent()
        model.cancelSearch()
        completion.complete(Unit)
        advanceUntilIdle()
        assertSame(ReportPagedSearchState.Idle, model.state.value)
        val rebound = filters()
        model.activate(rebound)
        runCurrent()
        assertEquals(queries[1], queries[2])
        assertEquals(2, ready(model).pageNumber)
        assertTrue(ready(model).hasPrevious)
        assertSame(rebound, ready(model).filters)
    }

    @Test
    fun loadingCollectorCanReplaceAReservedRequestBeforeItsRepositoryStarts() = runTest(dispatcher) {
        val queries = mutableListOf<ReportListPageQuery>()
        val model = model(ReportListPageRepository { query ->
            queries.add(query)
            ReportListPage(emptyList(), false)
        })
        val original = filters(priority = Priority.LOW)
        val replacement = filters(priority = Priority.HIGH)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { state ->
                if (state is ReportPagedSearchState.Loading && state.filters === original) model.search(replacement)
            }
        }
        model.search(original)
        runCurrent()
        assertEquals(listOf(Priority.HIGH), queries.map { it.priority })
        assertSame(replacement, ready(model).filters)
        assertEquals(1, ready(model).pageNumber)
        collector.cancel()
    }

    @Test
    fun aLoadingCollectorCanCancelAndAClearedModelCannotRestartReads() = runTest(dispatcher) {
        val repository = RecordingRepository(listOf(report(0)))
        val store = store()
        val model = model(repository, store = store)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { state -> if (state is ReportPagedSearchState.Loading) model.cancelSearch() }
        }
        val selected = filters()
        model.search(selected)
        runCurrent()
        assertSame(ReportPagedSearchState.Idle, model.state.value)
        assertTrue(repository.queries.isEmpty())
        collector.cancel()
        model.activate(selected)
        store.clear()
        runCurrent()
        assertSame(ReportPagedSearchState.Idle, model.state.value)
        model.activate(selected)
        model.search(selected)
        runCurrent()
        assertTrue(repository.queries.isEmpty())
        val cancelled = model(ReportListPageRepository { throw CancellationException("Cancelled reader") })
        cancelled.search(selected)
        runCurrent()
        assertSame(ReportPagedSearchState.Idle, cancelled.state.value)
    }

    @Test
    fun repositoryAndTextScanningUseTheInjectedWorkerAndLeaveMainAvailable() = runTest(dispatcher) {
        val mainThread = Thread.currentThread()
        val began = CountDownLatch(1)
        val release = CountDownLatch(1)
        val workerThread = AtomicReference<Thread>()
        val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "report-paged-search-worker").also { workerThread.set(it) }
        }.asCoroutineDispatcher()
        try {
            var readingThread: Thread? = null
            val candidate = report(0, "Árbol")
            val model = model(ReportListPageRepository {
                readingThread = Thread.currentThread()
                began.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                ReportListPage(listOf(candidate), false)
            }, worker)
            model.search(filters(text = "arbol"))
            runCurrent()
            assertTrue(withContext(Dispatchers.IO) { began.await(5, TimeUnit.SECONDS) })
            assertNotEquals(mainThread, readingThread)
            assertSame(workerThread.get(), readingThread)
            var mainWorkRan = false
            launch(Dispatchers.Main) { mainWorkRan = true }
            runCurrent()
            assertTrue(mainWorkRan)
            assertTrue(model.state.value is ReportPagedSearchState.Loading)
            release.countDown()
            model.state.first { it is ReportPagedSearchState.Ready }
            assertEquals(listOf(candidate), ready(model).reports)
        } finally {
            release.countDown()
            worker.close()
        }
    }

    @Test
    fun invalidRepositorySizesEmptyContinuationAndNonProgressingOrderBecomeErrors() = runTest(dispatcher) {
        val badPages = listOf(
            ReportListPage(emptyList(), true),
            ReportListPage((0 until 101).map { report(it) }, false),
            ReportListPage(listOf(report(1), report(0)), false),
            ReportListPage(listOf(report(0), report(0)), true),
        )
        for (badPage in badPages) {
            var reads = 0
            val model = model(ReportListPageRepository { reads++; badPage })
            val selected = filters()
            model.search(selected)
            runCurrent()
            val error = model.state.value as ReportPagedSearchState.Error
            assertSame(selected, error.filters)
            assertEquals(1, reads)
        }
        val source = (0 until 100).map { report(it) }
        var reads = 0
        val model = model(ReportListPageRepository { query ->
            reads++
            if (query.before == null) ReportListPage(source, false)
            else ReportListPage(listOf(source.first()), false)
        })
        model.search(filters())
        runCurrent()
        model.nextPage()
        runCurrent()
        assertTrue(model.state.value is ReportPagedSearchState.Error)
        assertEquals(2, reads)
    }

    private fun model(
        repository: ReportListPageRepository,
        worker: CoroutineDispatcher = dispatcher,
        store: ViewModelStore = store(),
    ): ReportPagedSearchViewModel = ViewModelProvider(
        store, viewModelFactory { initializer { ReportPagedSearchViewModel(repository, worker) } },
    )[ReportPagedSearchViewModel::class.java]

    private fun store() = ViewModelStore().also { stores.add(it) }
    private fun ready(model: ReportPagedSearchViewModel) = model.state.value as ReportPagedSearchState.Ready

    private fun filters(
        text: String = "",
        priority: Priority? = null,
        type: IncidentType? = null,
        status: ReportStatus? = null,
        startDate: String = "",
        endDate: String = "",
    ): ReportSearchFilters = (parseReportSearchFilters(
        text = text, priority = priority, type = type, status = status,
        startDate = startDate, endDate = endDate, zoneId = ZoneId.of("UTC"),
    ) as ReportSearchFilterValidation.Valid).filters

    private class RecordingRepository(private val source: List<Report>) : ReportListPageRepository {
        val queries = mutableListOf<ReportListPageQuery>()
        override suspend fun listPage(query: ReportListPageQuery): ReportListPage {
            queries.add(query)
            return page(source, query)
        }
    }

    companion object {
        private fun page(source: List<Report>, query: ReportListPageQuery): ReportListPage {
            val startInclusive = query.startInclusive
            val endExclusive = query.endExclusive
            val candidates = source.filter { report ->
                val before = query.before
                (query.type == null || report.type == query.type) &&
                    (query.status == null || report.status == query.status) &&
                    (query.priority == null || report.priority == query.priority) &&
                    (startInclusive == null || report.occurredAt >= startInclusive) &&
                    (endExclusive == null || report.occurredAt < endExclusive) &&
                    (before == null || report.createdAt < before.createdAt ||
                        (report.createdAt == before.createdAt && report.id < before.id))
            }
            return ReportListPage(candidates.take(query.limit), candidates.size > query.limit)
        }

        private fun report(index: Int, description: String = "Luminaria dañada") = Report(
            id = String.format(Locale.ROOT, "00000000-0000-4000-8000-%012x", index),
            type = IncidentType.RISK, priority = Priority.HIGH,
            description = description, occurredAt = Instant.parse("2026-10-11T18:00:00Z"),
            location = ReportLocation(reference = "Frente al parque"),
            createdAt = Instant.parse("2026-10-11T18:00:10Z").minusSeconds(index.toLong()),
        )
    }
}
