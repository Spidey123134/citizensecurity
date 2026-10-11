package com.example.citizensecurity.release

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportDraftCheckpoint
import com.example.citizensecurity.domain.ReportDraftCheckpointJournal
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportSubmissionJournal
import com.example.citizensecurity.domain.ReportSubmissionRequest
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
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
class ReportHomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val submission = SubmissionJournal()
    private val checkpoint = CheckpointJournal()

    @Before fun configureMain() { Dispatchers.setMain(dispatcher) }
    @After fun releaseModels() {
        try { stores.forEach(ViewModelStore::clear) } finally { Dispatchers.resetMain() }
    }

    @Test fun initialRefreshReadsBothActualJournalsWithoutWriting() = runTest(dispatcher) {
        val model = model()
        assertEquals(ReportHomeState(), model.state.value)
        assertEquals(0, submission.reads)
        assertEquals(0, checkpoint.reads)
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false), model.state.value)
        assertEquals(1, submission.reads)
        assertEquals(1, checkpoint.reads)
        assertEquals(0, submission.writes + checkpoint.writes)
    }

    @Test fun durableAttemptMeansItNeedsCheckingEvenIfItsRequestMayAlreadyHaveCommitted() = runTest(dispatcher) {
        submission.action = { request() }
        val model = model()
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false, pendingAttempt = true), model.state.value)
        assertEquals(0, submission.writes)
    }

    @Test fun savedDraftIsVisibleWithoutInventingAnAttemptOrFolio() = runTest(dispatcher) {
        checkpoint.action = { savedDraft() }
        val model = model()
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false, savedDraft = true), model.state.value)
    }

    @Test fun markedDraftReportsAnExistingFolioWithoutCreatingAnotherAttempt() = runTest(dispatcher) {
        checkpoint.action = { savedDraft().copy(submittedFolio = FOLIO) }
        val model = model()
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false, savedDraft = true, draftHasFolio = true), model.state.value)
        assertEquals(0, submission.writes + checkpoint.writes)
    }

    @Test fun bothDurableIndicatorsRemainIndependent() = runTest(dispatcher) {
        submission.action = { request() }
        checkpoint.action = { savedDraft().copy(submittedFolio = FOLIO) }
        val model = model()
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false, pendingAttempt = true, savedDraft = true, draftHasFolio = true), model.state.value)
    }

    @Test fun failedSubmissionReadDoesNotSuppressAValidSavedDraft() = runTest(dispatcher) {
        submission.action = { throw IOException("Cannot read attempt") }
        checkpoint.action = { savedDraft() }
        val model = model()
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false, savedDraft = true, submissionReadFailed = true), model.state.value)
        assertEquals(1, checkpoint.reads)
    }

    @Test fun failedDraftReadDoesNotSuppressAnAttemptToCheck() = runTest(dispatcher) {
        submission.action = { request() }
        checkpoint.action = { throw IOException("Cannot read draft") }
        val model = model()
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false, pendingAttempt = true, draftReadFailed = true), model.state.value)
        assertEquals(1, submission.reads)
    }

    @Test fun bothFailuresAreUnknownAndNeverTreatedAsConfirmedEmpty() = runTest(dispatcher) {
        submission.action = { throw IOException("Corrupt attempt") }
        checkpoint.action = { throw IllegalArgumentException("Unsupported draft") }
        val model = model()
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false, submissionReadFailed = true, draftReadFailed = true), model.state.value)
        assertEquals(0, submission.writes + checkpoint.writes)
    }

    @Test fun returningHomeReloadsChangedJournalsRatherThanRetainingOldHints() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        submission.action = { request() }
        checkpoint.action = { savedDraft() }
        assertTrue(model.refresh())
        assertEquals(ReportHomeState(), model.state.value)
        advanceUntilIdle()
        assertTrue(model.state.value.pendingAttempt)
        assertTrue(model.state.value.savedDraft)
        submission.action = { null }
        checkpoint.action = { null }
        model.refresh()
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false), model.state.value)
        assertEquals(3, submission.reads)
        assertEquals(3, checkpoint.reads)
        assertEquals(0, submission.writes + checkpoint.writes)
    }

    @Test fun aLaterErrorMarksAnEarlierConfirmedValueUnknownAndRetryRecovers() = runTest(dispatcher) {
        submission.action = { request() }
        checkpoint.action = { savedDraft().copy(submittedFolio = FOLIO) }
        val model = model()
        advanceUntilIdle()
        checkpoint.action = { throw IOException("Read failed after return") }
        model.refresh()
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false, pendingAttempt = true, draftReadFailed = true), model.state.value)
        checkpoint.action = { savedDraft() }
        model.refresh()
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false, pendingAttempt = true, savedDraft = true), model.state.value)
    }

    @Test fun independentReadsStartWhileTheOtherJournalIsStillSuspended() = runTest(dispatcher) {
        val gate = CompletableDeferred<ReportSubmissionRequest?>()
        submission.action = { gate.await() }
        checkpoint.action = { savedDraft() }
        val model = model()
        runCurrent()
        assertEquals(1, submission.reads)
        assertEquals(1, checkpoint.reads)
        assertTrue(model.state.value.loading)
        gate.complete(null)
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false, savedDraft = true), model.state.value)
    }

    @Test fun repeatedRefreshBeforeDispatchOnlyReadsTheLatestGeneration() = runTest(dispatcher) {
        val model = model()
        repeat(5) { assertTrue(model.refresh()) }
        advanceUntilIdle()
        assertEquals(1, submission.reads)
        assertEquals(1, checkpoint.reads)
        assertEquals(ReportHomeState(loading = false), model.state.value)
    }

    @Test fun newRefreshCancelsSuspendedReadsAndIgnoresTheirLateSuccess() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var oldJob: Job? = null
        submission.action = {
            oldJob = currentCoroutineContext()[Job]
            withContext(NonCancellable) { gate.await() }
            request()
        }
        val model = model()
        runCurrent()
        submission.action = { null }
        checkpoint.action = { savedDraft() }
        model.refresh()
        runCurrent()
        assertTrue(oldJob!!.isCancelled)
        val latest = ReportHomeState(loading = false, savedDraft = true)
        assertEquals(latest, model.state.value)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(latest, model.state.value)
    }

    @Test fun anObsoleteLateFailureCannotOverwriteTheNewGeneration() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        checkpoint.action = {
            withContext(NonCancellable) { gate.await() }
            throw IOException("Old failure")
        }
        val model = model()
        runCurrent()
        checkpoint.action = { savedDraft().copy(submittedFolio = FOLIO) }
        model.refresh()
        runCurrent()
        val latest = ReportHomeState(loading = false, savedDraft = true, draftHasFolio = true)
        assertEquals(latest, model.state.value)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(latest, model.state.value)
    }

    @Test fun releasingTheStoreBeforeDispatchDoesNotReadOrAllowRefresh() = runTest(dispatcher) {
        val store = store()
        val model = model(store)
        store.clear()
        advanceUntilIdle()
        assertFalse(model.refresh())
        assertEquals(0, submission.reads)
        assertEquals(0, checkpoint.reads)
        assertFalse(model.state.value.loading)
        assertTrue(model.state.value.submissionReadFailed)
        assertTrue(model.state.value.draftReadFailed)
    }

    @Test fun releasingTheStoreCancelsActiveReadsAndRejectsLateResults() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var readingJob: Job? = null
        submission.action = {
            readingJob = currentCoroutineContext()[Job]
            withContext(NonCancellable) { gate.await() }
            request()
        }
        val store = store()
        val model = model(store)
        runCurrent()
        store.clear()
        assertTrue(readingJob!!.isCancelled)
        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.state.value.pendingAttempt)
        assertFalse(model.state.value.loading)
        assertFalse(model.refresh())
        assertEquals(0, submission.writes + checkpoint.writes)
    }

    @Test fun explicitCancellationIsNotConvertedIntoAnEmptySuccessfulRead() = runTest(dispatcher) {
        submission.action = { throw CancellationException("Read cancelled") }
        val model = model()
        advanceUntilIdle()
        assertEquals(ReportHomeState(loading = false, submissionReadFailed = true, draftReadFailed = true), model.state.value)
        assertEquals(0, submission.writes + checkpoint.writes)
    }

    @Test fun aSharedViewModelStorePreservesTheModelWithoutAnotherInitialRead() = runTest(dispatcher) {
        val store = store()
        val first = model(store)
        advanceUntilIdle()
        val restored = model(store)
        assertSame(first, restored)
        advanceUntilIdle()
        assertEquals(1, submission.reads)
        assertEquals(1, checkpoint.reads)
        restored.refresh()
        advanceUntilIdle()
        assertEquals(2, submission.reads)
        assertEquals(2, checkpoint.reads)
    }

    @Test fun stateCollectorCanReenterRefreshWithoutAnObsoleteReadOrHungLoading() = runTest(dispatcher) {
        val model = model()
        advanceUntilIdle()
        var reentered = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect {
                if (it.loading && !reentered) {
                    reentered = true
                    model.refresh()
                }
            }
        }
        model.refresh()
        advanceUntilIdle()
        assertTrue(reentered)
        assertEquals(ReportHomeState(loading = false), model.state.value)
        assertEquals(2, submission.reads)
        assertEquals(2, checkpoint.reads)
    }

    private fun model(store: ViewModelStore = store()) =
        ViewModelProvider(store, ReportHomeViewModel.factory(submission, checkpoint))[ReportHomeViewModel::class.java]

    private fun store() = ViewModelStore().also(stores::add)

    private class SubmissionJournal : ReportSubmissionJournal {
        var reads = 0
        var writes = 0
        var action: suspend () -> ReportSubmissionRequest? = { null }
        override suspend fun read(): ReportSubmissionRequest? { reads++; return action() }
        override suspend fun write(request: ReportSubmissionRequest) { writes++; error("Home must not write") }
        override suspend fun clear(requestId: String) { writes++; error("Home must not clear") }
    }

    private class CheckpointJournal : ReportDraftCheckpointJournal {
        var reads = 0
        var writes = 0
        var action: suspend () -> ReportDraftCheckpoint? = { null }
        override suspend fun read(): ReportDraftCheckpoint? { reads++; return action() }
        override suspend fun write(checkpoint: ReportDraftCheckpoint, expectedId: String?, expectedSubmittedFolio: String?) {
            writes++; error("Home must not write")
        }
        override suspend fun markSubmitted(id: String, folio: String) { writes++; error("Home must not mark") }
    }

    private fun request() = ReportSubmissionRequest(REQUEST_ID, draft(), TIME)
    private fun savedDraft() = ReportDraftCheckpoint(DRAFT_ID, draft(), TIME)
    private fun draft() = NewReport(IncidentType.RISK, Priority.MEDIUM,
        "Una luminaria está dañada frente al parque.", TIME, ReportLocation("Parque central"))

    companion object {
        private const val REQUEST_ID = "41919817-c3e6-4d99-9601-36cebf2240b1"
        private const val DRAFT_ID = "347d0089-65d0-44a0-9e2d-e10bc51983ce"
        private const val FOLIO = "611d4098-30d6-449e-b98a-90dfe83f7b94"
        private val TIME = Instant.parse("2026-10-11T12:00:00Z")
    }
}
