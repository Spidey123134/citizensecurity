package com.example.citizensecurity.report

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.*
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportActivityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun configureMain() = Dispatchers.setMain(dispatcher)
    @After fun closeModels() {
        try { stores.forEach { it.clear() } } finally { Dispatchers.resetMain() }
    }

    @Test fun constructorDoesNotReadOrInventActivityAndNavigationNeedsAPage() = runTest(dispatcher) {
        val repository = Repository { page() }
        val model = model(repository)
        runCurrent()
        assertSame(ReportActivityState.Idle, model.state.value)
        assertFalse(model.next())
        assertFalse(model.previous())
        assertFalse(model.retry())
        assertFalse(model.canOpen(entry(1)))
        assertTrue(repository.queries.isEmpty())
    }

    @Test fun explicitActivationReadsFiftyAndRetainsTheExactQueryIdentity() = runTest(dispatcher) {
        val repository = Repository { page() }
        val model = model(repository)
        assertTrue(model.activate())
        val loading = model.state.value as ReportActivityState.Loading
        assertEquals(ReportActivityFilter.ALL, loading.query.filter)
        assertNull(loading.query.before)
        assertEquals(50, loading.query.limit)
        runCurrent()
        val ready = ready(model)
        assertSame(loading.query, ready.query)
        assertSame(loading.query, repository.queries.single())
        assertEquals(1, ready.page.entries.size)
        assertFalse(ready.canPrevious)
    }

    @Test fun genuineEmptyPageIsSuccessfulWithoutManufacturedNotifications() = runTest(dispatcher) {
        val repository = Repository { ReportActivityPage(emptyList(), false) }
        val model = model(repository)
        model.activate(ReportActivityFilter.STATUS_CHANGES)
        runCurrent()
        assertTrue(ready(model).page.entries.isEmpty())
        assertFalse(ready(model).page.hasMore)
        assertFalse(model.next())
        assertFalse(model.previous())
        assertFalse(model.canOpen(entry(1)))
        assertEquals(1, repository.queries.size)
    }

    @Test fun failedReadDoesNotBecomeEmptyAndRetriesItsExactQueryWithoutPrivateErrorText() = runTest(dispatcher) {
        var reads = 0
        val repository = Repository { if (++reads == 1) throw IOException("Private file C:/secrets/report.db") else page() }
        val model = model(repository)
        model.activate()
        runCurrent()
        val failed = model.state.value as ReportActivityState.Error
        assertFalse(failed.message.contains("secrets"))
        assertFalse(model.next())
        assertFalse(model.previous())
        assertFalse(model.canOpen(entry(1)))
        assertTrue(model.retry(failed.query))
        runCurrent()
        assertSame(failed.query, ready(model).query)
        assertSame(repository.queries[0], repository.queries[1])
        assertFalse(ready(model).canPrevious)
    }

    @Test fun cancelledRepositoryReadCanRetryTheExactPageInsteadOfShowingAnEmptySuccess() = runTest(dispatcher) {
        var reads = 0
        val repository = Repository { if (++reads == 1) throw CancellationException("Interruption") else page() }
        val model = model(repository)
        model.activate()
        runCurrent()
        val failed = model.state.value as ReportActivityState.Error
        assertTrue(model.retry())
        runCurrent()
        assertSame(failed.query, ready(model).query)
        assertSame(repository.queries[0], repository.queries[1])
    }

    @Test fun repeatedActivationRetainsLoadingReadyAndErrorWithoutAutomaticReads() = runTest(dispatcher) {
        val gate = CompletableDeferred<ReportActivityPage>()
        val repository = Repository { gate.await() }
        val model = model(repository)
        model.activate()
        val loading = model.state.value
        assertFalse(model.activate())
        assertFalse(model.refresh())
        assertSame(loading, model.state.value)
        runCurrent()
        gate.complete(page())
        runCurrent()
        val loaded = model.state.value
        assertFalse(model.activate())
        assertSame(loaded, model.state.value)
        assertEquals(1, repository.queries.size)

        val failedRepository = Repository { throw IOException("Fail") }
        val failedModel = model(failedRepository)
        failedModel.activate()
        runCurrent()
        val error = failedModel.state.value
        assertFalse(failedModel.activate())
        runCurrent()
        assertSame(error, failedModel.state.value)
        assertEquals(1, failedRepository.queries.size)
    }

    @Test fun explicitRefreshOfReadyReadsAgainWithANewFirstPageIdentity() = runTest(dispatcher) {
        val repository = Repository { page() }
        val model = model(repository)
        model.activate()
        runCurrent()
        val original = ready(model)
        assertTrue(model.refresh())
        runCurrent()
        assertNotSame(original.query, ready(model).query)
        assertEquals(original.query, ready(model).query)
        assertEquals(2, repository.queries.size)
    }

    @Test fun newFilterCancelsAndDiscardsALateNonCooperativeOldPage() = runTest(dispatcher) {
        val old = CompletableDeferred<ReportActivityPage>()
        val repository = Repository { query ->
            if (query.filter == ReportActivityFilter.ALL) withContext(NonCancellable) { old.await() }
            else page(changed = true)
        }
        val model = model(repository)
        model.activate()
        runCurrent()
        assertTrue(model.activate(ReportActivityFilter.STATUS_CHANGES))
        runCurrent()
        val current = ready(model)
        old.complete(page())
        advanceUntilIdle()
        assertSame(current, model.state.value)
        assertEquals(ReportActivityFilter.STATUS_CHANGES, current.query.filter)
        assertTrue(repository.jobs.first()!!.isCancelled)
    }

    @Test fun oldReadFailureCannotReplaceAFilteredSuccessfulReading() = runTest(dispatcher) {
        val old = CompletableDeferred<Unit>()
        val repository = Repository { query ->
            if (query.filter == ReportActivityFilter.ALL) {
                withContext(NonCancellable) { old.await() }
                throw IOException("Late failure")
            }
            page(changed = true)
        }
        val model = model(repository)
        model.activate()
        runCurrent()
        model.refresh(ReportActivityFilter.STATUS_CHANGES)
        runCurrent()
        val current = ready(model)
        old.complete(Unit)
        advanceUntilIdle()
        assertSame(current, model.state.value)
    }

    @Test fun nextReplacesThePageAndPreviousPerformsAFreshReadOfItsPosition() = runTest(dispatcher) {
        val repository = Repository { query ->
            if (query.before == null) fullPage(100) else fullPage(50, more = false)
        }
        val model = model(repository)
        model.activate()
        runCurrent()
        val first = ready(model)
        assertTrue(model.next(first.query))
        val continuation = model.state.value as ReportActivityState.Loading
        assertEquals(first.page.nextBefore, continuation.query.before)
        runCurrent()
        val second = ready(model)
        assertEquals(50, second.page.entries.size)
        assertEquals(operation(50), second.page.entries.first().event.operationId)
        assertTrue(second.canPrevious)
        assertFalse(model.next())
        assertTrue(model.previous(second.query))
        runCurrent()
        val returned = ready(model)
        assertEquals(first.query, returned.query)
        assertNotSame(first.query, returned.query)
        assertFalse(returned.canPrevious)
        assertEquals(listOf(null, first.page.nextBefore, null), repository.queries.map { it.before })
    }

    @Test fun nextFailureRetainsItsCursorAndRetryAddsOnlyOnePreviousPosition() = runTest(dispatcher) {
        var olderReads = 0
        val repository = Repository { query ->
            if (query.before == null) fullPage(100)
            else if (++olderReads == 1) throw IOException("Continuation failed") else fullPage(50, more = false)
        }
        val model = model(repository)
        model.activate()
        runCurrent()
        val first = ready(model)
        model.next()
        runCurrent()
        val failed = model.state.value as ReportActivityState.Error
        assertEquals(first.page.nextBefore, failed.query.before)
        assertTrue(model.retry())
        runCurrent()
        assertSame(failed.query, ready(model).query)
        assertTrue(model.previous())
        runCurrent()
        assertFalse(ready(model).canPrevious)
        assertFalse(model.previous())
        assertEquals(4, repository.queries.size)
        assertSame(repository.queries[1], repository.queries[2])
    }

    @Test fun previousFailureRetriesTheSamePositionAndDoesNotPopItsHistoryTwice() = runTest(dispatcher) {
        var firstReads = 0
        val repository = Repository { query ->
            if (query.before == null) {
                if (++firstReads == 2) throw IOException("Previous failed")
                fullPage(100)
            } else fullPage(50, more = false)
        }
        val model = model(repository)
        model.activate()
        runCurrent()
        model.next()
        runCurrent()
        model.previous()
        runCurrent()
        val failed = model.state.value as ReportActivityState.Error
        assertNull(failed.query.before)
        model.retry(failed.query)
        runCurrent()
        assertSame(failed.query, ready(model).query)
        assertFalse(ready(model).canPrevious)
        assertFalse(model.previous())
        assertSame(repository.queries[2], repository.queries[3])
    }

    @Test fun queuedGesturesNeedTheCurrentQueryIdentityAndCurrentEntryObject() = runTest(dispatcher) {
        val currentPage = fullPage(100)
        val repository = Repository { query -> if (query.before == null) currentPage else fullPage(50, more = false) }
        val model = model(repository)
        model.activate()
        runCurrent()
        val first = ready(model)
        val entry = first.page.entries.first()
        assertTrue(model.canOpen(entry, first.query))
        assertFalse(model.canOpen(entry.copy(), first.query))
        assertFalse(model.canOpen(entry, first.query.copy()))
        assertFalse(model.next(first.query.copy()))
        assertEquals(1, repository.queries.size)
        model.next()
        runCurrent()
        val second = ready(model)
        assertFalse(model.canOpen(entry, first.query))
        assertFalse(model.next(first.query))
        assertFalse(model.previous(first.query))
        assertFalse(model.retry(first.query))
        model.previous(second.query)
        runCurrent()
        // Same entry objects from a caching repository still need the new reading's query.
        assertFalse(model.canOpen(entry, first.query))
        assertTrue(model.canOpen(entry, ready(model).query))
    }

    @Test fun reactivationOfAnOlderPageRetainsItsFilterCursorPageAndPreviousNavigation() = runTest(dispatcher) {
        val repository = Repository { query -> if (query.before == null) fullPage(100) else fullPage(50, more = false) }
        val model = model(repository)
        model.activate()
        runCurrent()
        model.next()
        runCurrent()
        val older = ready(model)
        assertFalse(model.activate())
        runCurrent()
        assertSame(older, model.state.value)
        assertTrue(older.canPrevious)
        assertEquals(2, repository.queries.size)
    }

    @Test fun refreshOrFilterChangeResetsPreviousNavigationToTheNewestPage() = runTest(dispatcher) {
        val repository = Repository { query ->
            if (query.filter == ReportActivityFilter.STATUS_CHANGES) page(changed = true)
            else if (query.before == null) fullPage(100) else fullPage(50, more = false)
        }
        val model = model(repository)
        model.activate()
        runCurrent()
        model.next()
        runCurrent()
        assertTrue(model.refresh())
        runCurrent()
        assertNull(ready(model).query.before)
        assertFalse(ready(model).canPrevious)
        model.next()
        runCurrent()
        assertTrue(model.activate(ReportActivityFilter.STATUS_CHANGES))
        runCurrent()
        assertNull(ready(model).query.before)
        assertFalse(ready(model).canPrevious)
        assertFalse(model.previous())
    }

    @Test fun changesFilterRejectsCreatedEventsRatherThanShowingThemAsStateUpdates() = runTest(dispatcher) {
        val repository = Repository { page() }
        val model = model(repository)
        model.activate(ReportActivityFilter.STATUS_CHANGES)
        runCurrent()
        assertTrue(model.state.value is ReportActivityState.Error)
        assertFalse(model.canOpen(entry(1)))
    }

    @Test fun oversizedPageOrPrematureMoreFlagIsAnErrorWithoutExposingEntries() = runTest(dispatcher) {
        listOf(
            ReportActivityPage((100 downTo 50).map { entry(it) }, false),
            ReportActivityPage(listOf(entry(100)), true),
        ).forEach { malformed ->
            val model = model(Repository { malformed })
            model.activate()
            runCurrent()
            assertTrue(model.state.value is ReportActivityState.Error)
            assertFalse(model.canOpen(malformed.entries.first()))
        }
    }

    @Test fun continuationMustBeStrictlyOlderThanItsExclusiveTimestampAndOperationCursor() = runTest(dispatcher) {
        val first = fullPage(100)
        val repository = Repository { query ->
            if (query.before == null) first else ReportActivityPage(listOf(first.entries.last()), false)
        }
        val model = model(repository)
        model.activate()
        runCurrent()
        model.next()
        runCurrent()
        assertTrue(model.state.value is ReportActivityState.Error)
        assertEquals(first.nextBefore, (model.state.value as ReportActivityState.Error).query.before)
    }

    @Test fun cursorPreservesNanosecondsAndUsesOperationTieBreakOnlyAtTheSameInstant() = runTest(dispatcher) {
        val precise = TIME.plusNanos(123_456_789)
        val first = ReportActivityPage((100 downTo 51).map { entry(it, precise) }, true)
        val older = ReportActivityPage(listOf(entry(50, precise), entry(200, precise.minusNanos(1))), false)
        val repository = Repository { query -> if (query.before == null) first else older }
        val model = model(repository)
        model.activate()
        runCurrent()
        model.next()
        runCurrent()
        val query = repository.queries.last()
        val before = checkNotNull(query.before)
        assertEquals(precise, before.recordedAt)
        assertEquals(123_456_789, before.recordedAt.nano)
        assertEquals(operation(51), before.operationId)
        assertEquals(older.entries, ready(model).page.entries)
    }

    @Test fun revocationBeforeTheLazyReadPreventsRepositoryAccessAndRetiresTheModel() = runTest(dispatcher) {
        var allowed = true
        val repository = Repository { page() }
        val model = model(repository) { check(allowed) }
        model.activate()
        allowed = false
        runCurrent()
        assertSame(ReportActivityState.Idle, model.state.value)
        assertTrue(repository.queries.isEmpty())
        allowed = true
        assertFalse(model.activate())
        assertFalse(model.refresh())
        assertFalse(model.retry())
    }

    @Test fun revocationAfterReadingCannotPublishItsPageOrAllowOpeningAnEntry() = runTest(dispatcher) {
        var allowed = true
        val repository = Repository { allowed = false; page() }
        val model = model(repository) { check(allowed) }
        model.activate()
        runCurrent()
        assertSame(ReportActivityState.Idle, model.state.value)
        assertFalse(model.canOpen(entry(1)))
        assertEquals(1, repository.queries.size)
    }

    @Test fun revokedPendingScopeCancelsANonCooperativeReadAndCannotReviveAfterALateResult() = runTest(dispatcher) {
        var allowed = true
        val gate = CompletableDeferred<ReportActivityPage>()
        val repository = Repository { withContext(NonCancellable) { gate.await() } }
        val model = model(repository) { check(allowed) }
        model.activate()
        runCurrent()
        allowed = false
        assertFalse(model.refresh())
        allowed = true
        gate.complete(page())
        advanceUntilIdle()
        assertSame(ReportActivityState.Idle, model.state.value)
        assertTrue(repository.jobs.single()!!.isCancelled)
        assertFalse(model.activate())
    }

    @Test fun revocationOnTheCancelledReadCompletionRetiresInsteadOfExposingItsCursorAsAnError() = runTest(dispatcher) {
        var allowed = true
        val repository = Repository { allowed = false; throw CancellationException("Revoked during cancellation") }
        val model = model(repository) { check(allowed) }
        model.activate()
        runCurrent()
        assertSame(ReportActivityState.Idle, model.state.value)
        assertFalse(model.retry())
    }

    @Test fun loadingCollectorCanReplaceTheQueryBeforeItsLazyRepositoryJobStarts() = runTest(dispatcher) {
        val repository = Repository { page(changed = true) }
        val model = model(repository)
        var changed = false
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { state ->
                if (state is ReportActivityState.Loading && !changed) {
                    changed = true
                    model.refresh(ReportActivityFilter.STATUS_CHANGES)
                }
            }
        }
        model.activate()
        runCurrent()
        assertEquals(listOf(ReportActivityFilter.STATUS_CHANGES), repository.queries.map { it.filter })
        assertEquals(ReportActivityFilter.STATUS_CHANGES, ready(model).query.filter)
        collector.cancel()
    }

    @Test fun clearingTheStoreBeforeOrDuringAReadDiscardsItsResultAndDisablesFurtherActions() = runTest(dispatcher) {
        listOf(false, true).forEach { started ->
            val gate = CompletableDeferred<ReportActivityPage>()
            val repository = Repository { withContext(NonCancellable) { gate.await() } }
            val store = store()
            val model = model(repository, store = store)
            model.activate()
            if (started) runCurrent()
            store.clear()
            gate.complete(page())
            advanceUntilIdle()
            assertSame(ReportActivityState.Idle, model.state.value)
            assertFalse(model.activate())
            assertFalse(model.canOpen(entry(1)))
            assertEquals(if (started) 1 else 0, repository.queries.size)
        }
    }

    @Test fun repositoryReadAndLeaseRechecksRunOnTheInjectedReadDispatcher() = runTest(dispatcher) {
        val reads = StandardTestDispatcher(testScheduler, "activity-read")
        var readContext: ContinuationInterceptor? = null
        var guardCalls = 0
        val repository = Repository { readContext = currentCoroutineContext()[ContinuationInterceptor]; page() }
        val model = model(repository, readDispatcher = reads) { guardCalls++ }
        model.activate()
        runCurrent()
        assertSame(reads, readContext)
        assertTrue(guardCalls >= 4)
        assertTrue(model.state.value is ReportActivityState.Ready)
    }

    @Test fun boundedPreviousPositionsNeverKeepMoreThanFiftyEvenAcrossManyPages() = runTest(dispatcher) {
        val repository = Repository { query ->
            val start = query.before?.operationId?.takeLast(12)?.toInt()?.minus(1) ?: 100_000
            fullPage(start)
        }
        val model = model(repository)
        model.activate()
        runCurrent()
        repeat(60) { assertTrue(model.next()); runCurrent() }
        assertTrue(ready(model).canPrevious)
        repeat(50) { assertTrue(model.previous()); runCurrent() }
        assertFalse(ready(model).canPrevious)
        assertFalse(model.previous())
        assertNotNull(ready(model).query.before)
        assertTrue(model.refresh())
        runCurrent()
        assertNull(ready(model).query.before)
        assertFalse(ready(model).canPrevious)
    }

    private fun model(repository: ReportActivityRepository, store: ViewModelStore = store(),
        readDispatcher: CoroutineDispatcher = dispatcher, check: () -> Unit = {}): ReportActivityViewModel =
        ViewModelProvider(store, ReportActivityViewModel.factory(repository, readDispatcher, check))[ReportActivityViewModel::class.java]

    private fun store() = ViewModelStore().also { stores.add(it) }
    private fun ready(model: ReportActivityViewModel) = model.state.value as ReportActivityState.Ready

    private class Repository(private val answer: suspend (ReportActivityQuery) -> ReportActivityPage) : ReportActivityRepository {
        val queries = mutableListOf<ReportActivityQuery>()
        val jobs = mutableListOf<Job?>()
        override suspend fun readActivity(query: ReportActivityQuery): ReportActivityPage {
            queries.add(query)
            jobs.add(currentCoroutineContext()[Job])
            return answer(query)
        }
    }

    private fun page(changed: Boolean = false) = ReportActivityPage(listOf(entry(1, changed = changed)), false)
    private fun fullPage(start: Int, more: Boolean = true) =
        ReportActivityPage((start downTo start - 49).map { entry(it) }, more)
    private fun entry(number: Int, recordedAt: Instant = TIME, changed: Boolean = false): ReportActivityEntry =
        ReportActivityEntry(FOLIO, IncidentType.RISK, if (changed) ReportStatusEvent(
            operation(number), 2L, ReportStatusEventKind.STATUS_CHANGED, ReportStatus.REPORTED,
            ReportStatus.IN_REVIEW, recordedAt, ACTOR, "Se revisó el reporte.",
        ) else ReportStatusEvent(operation(number), 1L, ReportStatusEventKind.CREATED, null,
            ReportStatus.REPORTED, recordedAt, null))
    private fun operation(number: Int) = "00000000-0000-4000-8000-${number.toString().padStart(12, '0')}"

    private companion object {
        const val FOLIO = "4b2c9510-0ca0-46fb-94f1-1c1f7ee79f0d"
        const val ACTOR = "76c1aa89-770e-4a7f-9e6b-3437db4369fd"
        val TIME: Instant = Instant.parse("2026-10-14T17:00:00Z")
    }
}
