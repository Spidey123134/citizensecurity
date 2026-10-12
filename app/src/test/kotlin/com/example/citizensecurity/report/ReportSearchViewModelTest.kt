package com.example.citizensecurity.report

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportSearchViewModelTest {
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
    fun workerPreservesSourceIdentityOrderObjectsAndFieldsAndPublishesEmptyResults() = runTest(dispatcher) {
        val first = report("first", Priority.HIGH)
        val excluded = report("excluded", Priority.LOW)
        val last = report("last", Priority.HIGH)
        val source = listOf(first, excluded, last)
        val high = filters(priority = Priority.HIGH)
        val model = model()
        model.search(source, high)
        assertTrue(model.state.value is ReportSearchState.Loading)
        runCurrent()
        val ready = ready(model)
        assertSame(source, ready.source)
        assertSame(high, ready.filters)
        assertEquals(listOf(first, last), ready.reports)
        assertSame(first, ready.reports[0])
        assertSame(last, ready.reports[1])
        assertEquals(listOf(first, excluded, last), source)
        model.search(emptyList(), filters())
        runCurrent()
        assertTrue(ready(model).reports.isEmpty())
    }

    @Test
    fun exactSourceAndFilterIdentityGatesReadyBeforeAnotherSearchStarts() = runTest(dispatcher) {
        val source = listOf(report("first"))
        val filters = filters()
        val model = model()
        model.search(source, filters)
        runCurrent()
        val ready = ready(model)
        assertSame(ready, currentReportSearchResult(source, filters, model.state.value))
        assertNull(currentReportSearchResult(source.toList(), filters, model.state.value))
        assertNull(currentReportSearchResult(source, filters(), model.state.value))
        assertNull(currentReportSearchResult(source, filters, ReportSearchState.Loading(source, filters)))
    }

    @Test
    fun effectKeyRecognizesRecreatedKeyWithSameReferencesButRejectsEqualNewSource() {
        val source = listOf(report("one"))
        val selected = filters()
        val key = ReportSearchInput(source, selected)
        val recreated = ReportSearchInput(source, selected)
        assertEquals(key, recreated)
        assertEquals(key.hashCode(), recreated.hashCode())
        assertNotEquals(key, ReportSearchInput(source.toList(), selected))
    }

    @Test
    fun effectKeyRejectsNewFiltersEvenWithTheSameParsedCriteria() {
        val source = listOf(report("one"))
        val key = ReportSearchInput(source, filters())
        assertNotEquals(key, ReportSearchInput(source, filters()))
        assertNotEquals(key, ReportSearchInput(source, filters(priority = Priority.LOW)))
    }

    @Test
    fun newestFiltersWinEvenWhenOldWorkerIgnoresCancellationAndReturnsLate() = runTest(dispatcher) {
        val oldCompletion = CompletableDeferred<List<Report>>()
        val source = listOf(report("old", Priority.LOW), report("new", Priority.HIGH))
        val oldFilters = filters(priority = Priority.LOW)
        val newFilters = filters(priority = Priority.HIGH)
        val jobs = mutableListOf<Job>()
        val model = model { reports, selected ->
            jobs.add(currentCoroutineContext()[Job]!!)
            if (selected === oldFilters) withContext(NonCancellable) { oldCompletion.await() }
            else filterLocalReportsCancellable(reports, selected)
        }
        model.search(source, oldFilters)
        runCurrent()
        model.search(source, newFilters)
        runCurrent()
        assertTrue(jobs.first().isCancelled)
        assertSame(newFilters, ready(model).filters)
        oldCompletion.complete(listOf(source.first()))
        advanceUntilIdle()
        assertEquals(listOf(source.last()), ready(model).reports)
        assertSame(newFilters, ready(model).filters)
    }

    @Test
    fun oldSourceFailureCannotReplaceNewSourceWithTheSameFilters() = runTest(dispatcher) {
        val oldCompletion = CompletableDeferred<Unit>()
        val oldSource = listOf(report("old"))
        val newSource = listOf(report("new"))
        val selected = filters()
        val model = model { source, filters ->
            if (source === oldSource) {
                withContext(NonCancellable) { oldCompletion.await() }
                throw IOException("Private old reading")
            }
            filterLocalReportsCancellable(source, filters)
        }
        model.search(oldSource, selected)
        runCurrent()
        model.search(newSource, selected)
        runCurrent()
        oldCompletion.complete(Unit)
        advanceUntilIdle()
        assertSame(newSource, ready(model).source)
        assertEquals(newSource, ready(model).reports)
    }

    @Test
    fun failureIsAssociatedWithItsSourceAndFiltersAndRetryWorks() = runTest(dispatcher) {
        val source = listOf(report("one"))
        val selected = filters()
        var attempts = 0
        val model = model { reports, filters ->
            if (++attempts == 1) throw IOException("Private file path")
            filterLocalReportsCancellable(reports, filters)
        }
        model.search(source, selected)
        runCurrent()
        val error = model.state.value as ReportSearchState.Error
        assertSame(source, error.source)
        assertSame(selected, error.filters)
        assertEquals("No se pudo completar la búsqueda. Intenta nuevamente.", error.message)
        assertNull(currentReportSearchResult(source, selected, error))
        model.search(source, selected)
        runCurrent()
        assertEquals(source, ready(model).reports)
    }

    @Test
    fun cancelInvalidatesReadyAndPendingWorkWithoutAcceptingItsLateResponse() = runTest(dispatcher) {
        val source = listOf(report("one"))
        val selected = filters()
        val completion = CompletableDeferred<List<Report>>()
        var calls = 0
        val model = model { reports, filters ->
            if (++calls == 1) filterLocalReportsCancellable(reports, filters)
            else withContext(NonCancellable) { completion.await() }
        }
        model.search(source, selected)
        runCurrent()
        assertTrue(model.state.value is ReportSearchState.Ready)
        model.cancelSearch()
        assertEquals(ReportSearchState.Idle, model.state.value)
        model.search(source, selected)
        runCurrent()
        model.cancelSearch()
        completion.complete(source)
        advanceUntilIdle()
        assertEquals(ReportSearchState.Idle, model.state.value)
    }

    @Test
    fun workerCancellationIsIdleAndClearedStoreDoesNotStartAnotherSearch() = runTest(dispatcher) {
        val store = store()
        var calls = 0
        val model = model(store = store) { _, _ ->
            calls++
            throw CancellationException("Cancelled")
        }
        val source = listOf(report("one"))
        val selected = filters()
        model.search(source, selected)
        runCurrent()
        assertEquals(ReportSearchState.Idle, model.state.value)
        model.search(source, selected)
        store.clear()
        runCurrent()
        assertEquals(ReportSearchState.Idle, model.state.value)
        model.search(source, selected)
        runCurrent()
        assertEquals(1, calls)
    }

    @Test
    fun loadingCollectorCanReplaceSearchBeforeItsWorkerStarts() = runTest(dispatcher) {
        val source = listOf(report("one"))
        val original = filters(priority = Priority.LOW)
        val replacement = filters(priority = Priority.HIGH)
        val calls = mutableListOf<ReportSearchFilters>()
        val model = model { reports, filters ->
            calls.add(filters)
            filterLocalReportsCancellable(reports, filters)
        }
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { state ->
                if (state is ReportSearchState.Loading && state.filters === original) {
                    model.search(source, replacement)
                }
            }
        }
        model.search(source, original)
        runCurrent()
        assertEquals(listOf(replacement), calls)
        assertSame(replacement, ready(model).filters)
        collector.cancel()
    }

    @Test
    fun workerUsesInjectedBackgroundDispatcherAndLeavesMainAvailable() = runTest(dispatcher) {
        val mainThread = Thread.currentThread()
        val began = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executorThread = AtomicReference<Thread>()
        val worker = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "report-search-worker").also { executorThread.set(it) }
        }.asCoroutineDispatcher()
        try {
            var workerThread: Thread? = null
            val source = listOf(report("one"))
            val model = model(worker) { reports, filters ->
                workerThread = Thread.currentThread()
                began.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                filterLocalReportsCancellable(reports, filters)
            }
            model.search(source, filters())
            runCurrent()
            assertTrue(withContext(Dispatchers.IO) { began.await(5, TimeUnit.SECONDS) })
            assertNotEquals(mainThread, workerThread)
            assertSame(executorThread.get(), workerThread)
            assertTrue(model.state.value is ReportSearchState.Loading)
            var mainWorkRan = false
            launch(Dispatchers.Main) { mainWorkRan = true }
            runCurrent()
            assertTrue(mainWorkRan)
            release.countDown()
            model.state.first { it is ReportSearchState.Ready }
            assertEquals(source, ready(model).reports)
        } finally {
            release.countDown()
            worker.close()
        }
    }

    @Test
    fun cancellableWorkerStopsBeforeReadingMoreHistoricalRows() = runTest(dispatcher) {
        var reads = 0
        lateinit var job: Job
        val source = object : AbstractList<Report>() {
            override val size = 100
            override fun get(index: Int): Report {
                reads++
                if (index == 2) job.cancel()
                return report("$index")
            }
        }
        var completed = false
        job = launch {
            filterLocalReportsCancellable(source, filters())
            completed = true
        }
        runCurrent()
        assertTrue(job.isCancelled)
        assertFalse(completed)
        assertEquals(3, reads)
    }

    private fun model(
        worker: CoroutineDispatcher = dispatcher,
        store: ViewModelStore = store(),
        filter: suspend (List<Report>, ReportSearchFilters) -> List<Report> = ::filterLocalReportsCancellable,
    ): ReportSearchViewModel = ViewModelProvider(
        store, viewModelFactory { initializer { ReportSearchViewModel(worker, filter) } },
    )[ReportSearchViewModel::class.java]

    private fun store() = ViewModelStore().also { stores.add(it) }
    private fun ready(model: ReportSearchViewModel) = model.state.value as ReportSearchState.Ready

    private fun filters(priority: Priority? = null): ReportSearchFilters =
        (parseReportSearchFilters(zoneId = ZoneId.of("UTC"), priority = priority)
            as ReportSearchFilterValidation.Valid).filters

    private fun report(id: String, priority: Priority = Priority.HIGH) = Report(
        id = id, type = IncidentType.RISK, priority = priority,
        description = "Luminaria dañada", occurredAt = Instant.parse("2026-10-11T18:00:00Z"),
        location = ReportLocation(reference = "Frente al parque"),
        createdAt = Instant.parse("2026-10-11T18:00:10Z"),
    )
}
