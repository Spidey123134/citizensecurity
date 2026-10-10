package com.example.citizensecurity.report

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportRepository
import com.example.citizensecurity.domain.ReportStatus
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun configureDispatcher() { Dispatchers.setMain(dispatcher) }

    @After
    fun releaseModels() {
        try { stores.forEach { it.clear() } } finally { Dispatchers.resetMain() }
    }

    @Test
    fun readsLocalRecordsInRepositoryOrderAndNeverWrites() = runTest(dispatcher) {
        val recent = report("recent")
        val older = report("older")
        val repository = ReadRepository { listOf(recent, older) }
        val model = model(repository)
        assertEquals(ReportListState.Idle, model.state.value)
        model.load()
        assertEquals(ReportListState.Loading, model.state.value)
        runCurrent()
        assertEquals(ReportListState.Loaded(listOf(recent, older)), model.state.value)
        assertEquals(1, repository.calls)
    }

    @Test
    fun emptyReadingIsSuccessfulAndDistinctFromError() = runTest(dispatcher) {
        val model = model(ReadRepository { emptyList() })
        model.load()
        runCurrent()
        assertEquals(ReportListState.Loaded(emptyList()), model.state.value)
    }

    @Test
    fun failedReadDoesNotExposePrivateExceptionAndCanRetry() = runTest(dispatcher) {
        val repository = ReadRepository { call ->
            if (call == 1) throw IOException("Private database path and payload")
            listOf(report("saved"))
        }
        val model = model(repository)
        model.load()
        runCurrent()
        assertEquals(
            ReportListState.Error("No se pudieron leer los reportes. Intenta actualizar nuevamente."),
            model.state.value,
        )
        model.load()
        runCurrent()
        assertEquals(ReportListState.Loaded(listOf(report("saved"))), model.state.value)
        assertEquals(2, repository.calls)
    }

    @Test
    fun newerRefreshSupersedesCancelledReadEvenIfRepositoryFinishesLate() = runTest(dispatcher) {
        val first = CompletableDeferred<List<Report>>()
        val repository = ReadRepository { call ->
            if (call == 1) withContext(NonCancellable) { first.await() }
            else listOf(report("new"))
        }
        val model = model(repository)
        model.load()
        runCurrent()
        model.load()
        runCurrent()
        assertEquals(ReportListState.Loaded(listOf(report("new"))), model.state.value)
        first.complete(listOf(report("old")))
        advanceUntilIdle()
        assertEquals(ReportListState.Loaded(listOf(report("new"))), model.state.value)
        assertTrue(repository.jobs.first()!!.isCancelled)
    }

    @Test
    fun failedOldReadCannotReplaceSuccessfulNewerRead() = runTest(dispatcher) {
        val first = CompletableDeferred<Unit>()
        val repository = ReadRepository { call ->
            if (call == 1) {
                withContext(NonCancellable) { first.await() }
                throw IOException("old failure")
            }
            listOf(report("new"))
        }
        val model = model(repository)
        model.load()
        runCurrent()
        model.load()
        runCurrent()
        first.complete(Unit)
        advanceUntilIdle()
        assertEquals(ReportListState.Loaded(listOf(report("new"))), model.state.value)
    }

    @Test
    fun clearingBeforeTheLazyJobStartsPreventsDatabaseReadAndFutureLoads() = runTest(dispatcher) {
        val repository = ReadRepository { listOf(report("saved")) }
        val store = store()
        val model = model(repository, store)
        model.load()
        store.clear()
        runCurrent()
        assertEquals(ReportListState.Idle, model.state.value)
        model.load()
        runCurrent()
        assertEquals(0, repository.calls)
        assertEquals(ReportListState.Idle, model.state.value)
    }

    @Test
    fun clearingCancelsASuspendedReadWithoutPublishingItsLateValue() = runTest(dispatcher) {
        val gate = CompletableDeferred<List<Report>>()
        val repository = ReadRepository { gate.await() }
        val store = store()
        val model = model(repository, store)
        model.load()
        runCurrent()
        store.clear()
        gate.complete(listOf(report("late")))
        advanceUntilIdle()
        assertTrue(repository.jobs.single()!!.isCancelled)
        assertEquals(ReportListState.Idle, model.state.value)
    }

    @Test
    fun cancellationCanBeFollowedByAnExplicitRetry() = runTest(dispatcher) {
        val repository = ReadRepository { call ->
            if (call == 1) throw CancellationException("Cancelled reading")
            listOf(report("retry"))
        }
        val model = model(repository)
        model.load()
        runCurrent()
        assertEquals(ReportListState.Idle, model.state.value)
        model.load()
        runCurrent()
        assertEquals(ReportListState.Loaded(listOf(report("retry"))), model.state.value)
    }

    @Test
    fun explicitCancellationInvalidatesANonCooperativePendingResult() = runTest(dispatcher) {
        val gate = CompletableDeferred<List<Report>>()
        val repository = ReadRepository { withContext(NonCancellable) { gate.await() } }
        val model = model(repository)
        model.load()
        runCurrent()
        model.cancelLoad()
        assertEquals(ReportListState.Idle, model.state.value)
        gate.complete(listOf(report("late")))
        advanceUntilIdle()
        assertEquals(ReportListState.Idle, model.state.value)
    }

    @Test
    fun refreshTriggeredByLoadingCollectorOwnsTheJobBeforeDatabaseRead() = runTest(dispatcher) {
        val repository = ReadRepository { listOf(report("latest")) }
        val model = model(repository)
        var refreshed = false
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { state ->
                if (state == ReportListState.Loading && !refreshed) {
                    refreshed = true
                    model.load()
                }
            }
        }
        model.load()
        runCurrent()
        assertTrue(refreshed)
        assertEquals(1, repository.calls)
        assertEquals(ReportListState.Loaded(listOf(report("latest"))), model.state.value)
        collector.cancel()
    }

    @Test
    fun loadedListDoesNotRetainTheMutableListOwnedByRepository() = runTest(dispatcher) {
        val repositoryList = mutableListOf(report("first"))
        val model = model(ReadRepository { repositoryList })
        model.load()
        runCurrent()
        repositoryList.clear()
        assertEquals(ReportListState.Loaded(listOf(report("first"))), model.state.value)
    }

    @Test
    fun filtersCombineTypeAndStatusWithoutAlteringRecordsOrTheirOrder() {
        val a = report("a", type = IncidentType.RISK)
        val b = report("b", type = IncidentType.RISK, status = ReportStatus.CLOSED)
        val c = report("c", type = IncidentType.THEFT)
        val all = listOf(a, b, c)
        assertEquals(all, filterLocalReports(all))
        assertEquals(listOf(a, b), filterLocalReports(all, type = IncidentType.RISK))
        assertEquals(listOf(b), filterLocalReports(all, status = ReportStatus.CLOSED))
        assertEquals(listOf(b), filterLocalReports(all, IncidentType.RISK, ReportStatus.CLOSED))
        assertTrue(filterLocalReports(all, IncidentType.THEFT, ReportStatus.CLOSED).isEmpty())
        assertEquals(listOf(a, b, c), all)
    }

    private fun model(repository: ReportRepository, store: ViewModelStore = store()) =
        ViewModelProvider(store, ReportListViewModel.factory(repository))[ReportListViewModel::class.java]

    private fun store() = ViewModelStore().also(stores::add)

    private fun report(
        id: String,
        type: IncidentType = IncidentType.RISK,
        status: ReportStatus = ReportStatus.REPORTED,
    ) = Report(
        id = id,
        type = type,
        priority = Priority.MEDIUM,
        description = "Luminaria dañada frente al parque",
        occurredAt = Instant.parse("2026-10-09T18:00:00Z"),
        location = ReportLocation(reference = "Frente al parque"),
        createdAt = Instant.parse("2026-10-09T18:00:10Z"),
        status = status,
    )

    private class ReadRepository(private val read: suspend (Int) -> List<Report>) : ReportRepository {
        var calls = 0
        val jobs = mutableListOf<Job?>()
        override suspend fun list(): List<Report> {
            ++calls
            jobs += currentCoroutineContext()[Job]
            return read(calls)
        }
        override suspend fun create(draft: NewReport): Report = error("A list reading must never save a report")
        override suspend fun findById(id: String): Report? = error("A list reading must not query a folio")
    }
}
