package com.example.citizensecurity.alerts

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.*
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class ReportAlertsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val preferences = Preferences()
    private val source = Activity()
    private val publisher = Publisher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() {
        try { stores.forEach { it.clear() } } finally { Dispatchers.resetMain() }
    }

    @Test fun constructionAndUnloadedGesturesNeverReadWritePrepareOrPublish() = runTest(dispatcher) {
        val model = model()
        assertFalse(model.setEnabled(true))
        assertFalse(model.review())
        assertFalse(model.continueReview())
        runCurrent()
        assertEquals(ReportAlertsState.Idle, model.state.value)
        assertEquals(0, preferences.reads)
        assertTrue(source.queries.isEmpty())
        assertEquals(0, publisher.prepared)
        assertEquals(0, publisher.published)
    }

    @Test fun explicitActivationLoadsOnceAndRetainsTheReadingAcrossReactivation() = runTest(dispatcher) {
        val model = model()
        assertTrue(model.activate())
        assertFalse(model.activate())
        runCurrent()
        assertEquals(ReportAlertsSnapshot(null), ready(model).snapshot)
        assertFalse(model.activate())
        assertEquals(1, preferences.reads)
        assertEquals(0, publisher.prepared)
        assertEquals(0, publisher.published)
    }

    @Test fun deniedAndroidAvailabilityCannotEnableOrWritePreferences() = runTest(dispatcher) {
        publisher.available = ReportAlertsAvailability.RUNTIME_PERMISSION_DENIED
        val model = loaded()
        assertTrue(model.setEnabled(true))
        runCurrent()
        assertFalse(ready(model).snapshot.enabled)
        assertEquals(ReportAlertsAvailability.RUNTIME_PERMISSION_DENIED, ready(model).availability)
        assertEquals(0, preferences.sets)
        assertEquals(0, publisher.prepared)
    }

    @Test fun enablingPreparesTheChannelAndPersistsWithoutReviewingOrPosting() = runTest(dispatcher) {
        source.rows = listOf(entry(1))
        val model = loaded()
        assertTrue(model.setEnabled(true))
        assertFalse(model.setEnabled(true))
        runCurrent()
        assertTrue(ready(model).snapshot.enabled)
        assertEquals(1, preferences.sets)
        assertEquals(1, publisher.prepared)
        assertEquals(0, publisher.published)
        assertTrue(source.queries.isEmpty())
    }

    @Test fun disablingPreservesPresentationIdsBeforeCancellingTheOwnedSummary() = runTest(dispatcher) {
        preferences.current = snapshot(true, setOf(id(1)))
        val model = loaded()
        publisher.cancelAction = { assertFalse(preferences.current.enabled) }
        model.setEnabled(false)
        runCurrent()
        assertFalse(ready(model).snapshot.enabled)
        assertEquals(setOf(id(1)), ready(model).snapshot.presented)
        assertEquals(1, publisher.cancelled)
        assertTrue(source.queries.isEmpty())
    }

    @Test fun reviewingDisabledPreferencesDoesNotQueryEventsOrPublish() = runTest(dispatcher) {
        source.rows = listOf(entry(1))
        val model = loaded()
        model.review()
        runCurrent()
        assertTrue(source.queries.isEmpty())
        assertEquals(0, publisher.published)
        assertEquals(0, preferences.records)
    }

    @Test fun reviewingWithBlockedChannelDoesNotQueryEventsOrAcknowledgeAnything() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        publisher.available = ReportAlertsAvailability.CHANNEL_BLOCKED
        val model = loaded()
        model.review()
        runCurrent()
        assertEquals(ReportAlertsAvailability.CHANNEL_BLOCKED, ready(model).availability)
        assertTrue(source.queries.isEmpty())
        assertEquals(0, publisher.published)
        assertEquals(0, preferences.records)
    }

    @Test fun historicalRealChangesProduceOneSummaryAndAreAcknowledgedAfterPostingOnly() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = listOf(entry(1), entry(2), entry(3))
        publisher.publishAction = { check -> check(); assertTrue(preferences.current.presented.isEmpty()); ReportAlertsPublishResult.ACCEPTED }
        val model = loaded()
        model.review()
        runCurrent()
        assertEquals(1, publisher.published)
        assertEquals(1, preferences.records)
        assertEquals(setOf(id(1), id(2), id(3)), ready(model).snapshot.presented)
        assertEquals(3, ready(model).examined)
        assertFalse(ready(model).canContinue)
        assertTrue(source.queries.all { it.filter == ReportActivityFilter.STATUS_CHANGES })
    }

    @Test fun acceptedPresentationIdsDeduplicateRepeatedExplicitReviews() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = listOf(entry(1))
        val model = loaded()
        model.review(); runCurrent()
        model.review(); runCurrent()
        assertEquals(1, publisher.published)
        assertEquals(1, preferences.records)
        assertEquals(setOf(id(1)), ready(model).snapshot.presented)
    }

    @Test fun aNewReviewStartsAtTheHeadAndIncludesAnUnseenBackdatedChange() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = listOf(entry(1))
        val model = loaded()
        model.review(); runCurrent()
        source.rows = source.rows + entry(2, BASE.minusSeconds(1_000))
        model.review(); runCurrent()
        assertEquals(2, publisher.published)
        assertEquals(setOf(id(1), id(2)), ready(model).snapshot.presented)
        assertTrue(source.queries.all { it.before == null })
    }

    @Test fun oneGestureExaminesAtMostFiveHundredAndContinuationUsesTheExactCursor() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = (1..501).map(::entry)
        val model = loaded()
        model.review(); runCurrent()
        assertEquals(10, source.queries.size)
        assertTrue(source.queries.all { it.limit == 50 })
        assertEquals(500, ready(model).examined)
        assertEquals(500, ready(model).snapshot.presented.size)
        assertTrue(ready(model).canContinue)
        assertEquals(1, publisher.published)
        assertTrue(model.continueReview()); runCurrent()
        assertNotNull(source.queries.last().before)
        assertEquals(1, ready(model).examined)
        assertEquals(501, ready(model).snapshot.presented.size)
        assertFalse(ready(model).canContinue)
        assertEquals(2, publisher.published)
    }

    @Test fun alreadyPresentedPagesCanBeContinuedWithoutPostingAnEmptySummary() = runTest(dispatcher) {
        source.rows = (1..501).map(::entry)
        preferences.current = snapshot(true, (2..501).map(::id).toSet())
        val model = loaded()
        model.review(); runCurrent()
        assertTrue(ready(model).canContinue)
        assertEquals(0, publisher.published)
        assertEquals(0, preferences.records)
        model.continueReview(); runCurrent()
        assertEquals(1, publisher.published)
        assertEquals(501, ready(model).snapshot.presented.size)
    }

    @Test fun creationsAreFilteredBeforeSourceLimitAndNeverBecomePresentationReceipts() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = (1..500).map { entry(it, kind = ReportStatusEventKind.CREATED) } + entry(900, BASE.minusSeconds(20))
        val model = loaded()
        model.review(); runCurrent()
        assertEquals(setOf(id(900)), ready(model).snapshot.presented)
        assertEquals(1, ready(model).examined)
        assertTrue(source.queries.all { it.filter == ReportActivityFilter.STATUS_CHANGES })
    }

    @Test fun aSourceThatReturnsCreationDespiteTheFilterIsRejectedWithoutPublishing() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.readAction = { ReportActivityPage(listOf(entry(1, kind = ReportStatusEventKind.CREATED)), false) }
        val model = loaded()
        model.review(); runCurrent()
        assertTrue(model.state.value is ReportAlertsState.Error)
        assertEquals(0, publisher.published)
        assertEquals(0, preferences.records)
    }

    @Test fun malformedOrderFromTheSourceCannotReachThePublisher() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.readAction = { ReportActivityPage(listOf(entry(1), entry(2)), false) }
        val model = loaded()
        model.review(); runCurrent()
        assertTrue(model.state.value is ReportAlertsState.Error)
        assertEquals(0, publisher.published)
        assertEquals(0, preferences.records)
    }

    @Test fun aShortPageClaimingMoreDataIsRejectedWithoutSkippingItsUnknownSuffix() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.readAction = { ReportActivityPage(listOf(entry(1)), true) }
        val model = loaded()
        model.review(); runCurrent()
        assertTrue(model.state.value is ReportAlertsState.Error)
        assertEquals(0, publisher.published)
        assertEquals(0, preferences.records)
    }

    @Test fun aContinuationResponseOutsideItsExclusiveCursorCannotBeAcknowledged() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = (1..501).map(::entry)
        val model = loaded()
        model.review(); runCurrent()
        source.readAction = { ReportActivityPage(listOf(entry(900)), false) }
        model.continueReview(); runCurrent()
        assertTrue(model.state.value is ReportAlertsState.Error)
        assertEquals(1, publisher.published)
        assertEquals(1, preferences.records)
    }

    @Test fun ledgerCapacityFailsBeforePostingAndNeverTrimsOrResetsExistingIds() = runTest(dispatcher) {
        preferences.current = snapshot(true, (10_000 until 14_096).map(::id).toSet())
        source.rows = listOf(entry(1))
        val before = preferences.current
        val model = loaded()
        model.review(); runCurrent()
        assertTrue(model.state.value is ReportAlertsState.Error)
        assertEquals(before, preferences.current)
        assertEquals(4096, preferences.current.presented.size)
        assertEquals(0, publisher.published)
        assertEquals(0, preferences.records)
    }

    @Test fun permissionLostDuringScanPreventsPresentationAndLeavesIdsUnacknowledged() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.readAction = { publisher.available = ReportAlertsAvailability.APP_BLOCKED; ReportActivityPage(listOf(entry(1)), false) }
        val model = loaded()
        model.review(); runCurrent()
        assertEquals(ReportAlertsAvailability.APP_BLOCKED, ready(model).availability)
        assertEquals(0, publisher.published)
        assertEquals(0, preferences.records)
    }

    @Test fun aBlockedPublisherResultDoesNotWriteReceiptsAndCanBeReviewedAgain() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = listOf(entry(1))
        publisher.publishAction = { it(); ReportAlertsPublishResult.BLOCKED }
        val model = loaded()
        model.review(); runCurrent()
        assertTrue(ready(model).snapshot.presented.isEmpty())
        assertEquals(0, preferences.records)
        publisher.publishAction = { it(); ReportAlertsPublishResult.ACCEPTED }
        model.review(); runCurrent()
        assertEquals(setOf(id(1)), ready(model).snapshot.presented)
    }

    @Test fun publishFailureRequiresReloadBeforeAnotherAttemptAndNeverAcknowledges() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = listOf(entry(1))
        publisher.publishAction = { throw IOException("private URI and account details") }
        val model = loaded()
        model.review(); runCurrent()
        val error = model.state.value as ReportAlertsState.Error
        assertTrue(error.needsReload)
        assertFalse(error.message.contains("private"))
        assertFalse(model.review())
        assertFalse(model.setEnabled(false))
        assertEquals(0, preferences.records)
        publisher.publishAction = { it(); ReportAlertsPublishResult.ACCEPTED }
        model.load(); runCurrent()
        model.review(); runCurrent()
        assertEquals(setOf(id(1)), ready(model).snapshot.presented)
    }

    @Test fun failedAttemptCleanupPreservesTheOriginalFailureAndRequiresReload() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = listOf(entry(1))
        // A diagnostic field prevents coroutine stack recovery from copying the exception;
        // this assertion observes the exact failure to which cleanup attaches its detail.
        val original = AttemptFailure("publication outcome uncertain")
        val cleanup = AttemptFailure("cleanup outcome uncertain")
        publisher.publishAction = { throw original }
        publisher.cancelAttemptAction = { throw cleanup }
        val model = loaded()
        model.review(); runCurrent()
        assertEquals(listOf(cleanup), original.suppressed.toList())
        assertTrue((model.state.value as ReportAlertsState.Error).needsReload)
        assertEquals(1, publisher.attemptCancellations)
        assertTrue(preferences.current.presented.isEmpty())
        assertFalse(model.review())
    }

    @Test fun anUncertainReceiptWriteIsRecoveredByReadingBeforeConsideringAnotherSummary() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = listOf(entry(1))
        preferences.recordAction = { ids, revision -> preferences.recordDefault(ids, revision); throw IOException("completion lost") }
        val model = loaded()
        model.review(); runCurrent()
        assertTrue((model.state.value as ReportAlertsState.Error).needsReload)
        assertEquals(1, publisher.published)
        assertEquals(setOf(id(1)), preferences.current.presented)
        assertFalse(model.review())
        preferences.recordAction = preferences::recordDefault
        model.load(); runCurrent()
        model.review(); runCurrent()
        assertEquals(1, publisher.published)
        assertEquals(setOf(id(1)), ready(model).snapshot.presented)
    }

    @Test fun aPreferenceWriteWithLostCompletionNeverAssumesDefaultDisabledOrOverwrites() = runTest(dispatcher) {
        preferences.setAction = { enabled, revision -> preferences.setDefault(enabled, revision); throw IOException("completion lost") }
        val model = loaded()
        model.setEnabled(true); runCurrent()
        assertTrue((model.state.value as ReportAlertsState.Error).needsReload)
        assertTrue(preferences.current.enabled)
        assertFalse(model.setEnabled(false))
        model.load(); runCurrent()
        assertTrue(ready(model).snapshot.enabled)
    }

    @Test fun concurrentPreferenceRevisionChangeDuringScanPreventsPostingFromAnOldSnapshot() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.readAction = { preferences.current = snapshot(false); ReportActivityPage(listOf(entry(1)), false) }
        val model = loaded()
        model.review(); runCurrent()
        assertTrue((model.state.value as ReportAlertsState.Error).needsReload)
        assertFalse(preferences.current.enabled)
        assertEquals(0, publisher.published)
        assertEquals(0, preferences.records)
    }

    @Test fun concurrentDisableDuringPublicationRetiresTheLateSummaryAfterReceiptCasFails() = runTest(dispatcher) {
        preferences.current = snapshot(true, setOf(id(77)))
        source.rows = listOf(entry(1))
        publisher.publishAction = { check ->
            // Another screen completes its disable/cancel before this in-flight notify returns.
            preferences.setDefault(false, preferences.current.revision)
            publisher.cancel()
            check()
            ReportAlertsPublishResult.ACCEPTED
        }
        val model = loaded()
        model.review(); runCurrent()
        assertTrue((model.state.value as ReportAlertsState.Error).needsReload)
        assertFalse(preferences.current.enabled)
        assertEquals(setOf(id(77)), preferences.current.presented)
        assertFalse(publisher.hasSummary)
        assertEquals(1, publisher.attemptCancellations)
        assertFalse(model.review())
    }

    @Test fun receiptConflictCleanupCannotCancelANewerSummaryFromAnotherReviewer() = runTest(dispatcher) {
        preferences.current = snapshot(true, setOf(id(77)))
        source.rows = listOf(entry(1))
        val newer = Publisher(publisher.slot)
        preferences.recordAction = { _, _ ->
            preferences.setDefault(false, preferences.current.revision)
            publisher.cancel()
            preferences.setDefault(true, preferences.current.revision)
            assertEquals(ReportAlertsPublishResult.ACCEPTED, newer.publish {})
            throw ReportAlertsConflictException()
        }
        val model = loaded()
        model.review(); runCurrent()
        assertTrue(model.state.value is ReportAlertsState.Error)
        assertEquals(1, publisher.attemptCancellations)
        assertTrue(newer.hasSummary)
        assertTrue(newer.ownsSummary)
        assertEquals(setOf(id(77)), preferences.current.presented)
    }

    @Test fun cancellingAfterAcceptanceRetiresTheSummaryWithoutAcknowledgingThePendingReceipt() = runTest(dispatcher) {
        preferences.current = snapshot(true, setOf(id(77)))
        source.rows = listOf(entry(1))
        val finishWrite = CompletableDeferred<Unit>()
        preferences.recordAction = { ids, revision ->
            finishWrite.await()
            preferences.recordDefault(ids, revision)
        }
        val store = ViewModelStore()
        val model = loaded(store)
        model.review(); runCurrent()
        assertTrue(publisher.hasSummary)
        store.clear(); runCurrent()
        assertEquals(ReportAlertsState.Idle, model.state.value)
        assertEquals(1, publisher.attemptCancellations)
        assertFalse(publisher.hasSummary)
        assertEquals(setOf(id(77)), preferences.current.presented)
    }

    @Test fun aWrongReceiptBodyCannotBePublishedAsSuccessfulLocalAcknowledgement() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = listOf(entry(1))
        preferences.recordAction = { _, _ -> snapshot(true, setOf(id(99))) }
        val model = loaded()
        model.review(); runCurrent()
        assertTrue((model.state.value as ReportAlertsState.Error).needsReload)
        assertEquals(1, publisher.published)
        assertTrue(preferences.current.presented.isEmpty())
    }

    @Test fun repeatedAndReentrantReviewGesturesKeepOnePendingScanAndOneSummary() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        val finish = CompletableDeferred<Unit>()
        source.readAction = { finish.await(); ReportActivityPage(listOf(entry(1)), false) }
        val model = loaded()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it is ReportAlertsState.Loading && it.action == ReportAlertsAction.REVIEW) assertFalse(model.review()) }
        }
        assertTrue(model.review())
        assertFalse(model.review())
        runCurrent()
        assertTrue(model.busy.value)
        assertFalse(model.load())
        finish.complete(Unit)
        runCurrent()
        assertFalse(model.busy.value)
        assertEquals(1, publisher.published)
        assertEquals(1, preferences.records)
        collector.cancel()
    }

    @Test fun revocationDuringAnEventReadClosesTheModelWithoutPublishingOrRebinding() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        var current = true
        source.readAction = { current = false; ReportActivityPage(listOf(entry(1)), false) }
        val model = loaded(check = { check(current) })
        model.review(); runCurrent()
        assertEquals(ReportAlertsState.Idle, model.state.value)
        current = true
        assertFalse(model.load())
        assertFalse(model.review())
        assertEquals(0, publisher.published)
        assertEquals(0, preferences.records)
    }

    @Test fun revocationInsidePublishingCannotWriteThePresentationLedger() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = listOf(entry(1))
        var current = true
        publisher.publishAction = { check -> current = false; check(); ReportAlertsPublishResult.ACCEPTED }
        val model = loaded(check = { check(current) })
        model.review(); runCurrent()
        assertEquals(ReportAlertsState.Idle, model.state.value)
        assertEquals(0, preferences.records)
        assertTrue(preferences.current.presented.isEmpty())
        assertEquals(1, publisher.attemptCancellations)
    }

    @Test fun clearingDuringNonCooperativePublishCannotAcknowledgeItsLateCompletion() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.rows = listOf(entry(1))
        val finish = CompletableDeferred<Unit>()
        publisher.publishAction = { withContext(NonCancellable) { finish.await() }; ReportAlertsPublishResult.ACCEPTED }
        val store = ViewModelStore()
        val model = loaded(store)
        model.review(); runCurrent()
        store.clear()
        finish.complete(Unit)
        advanceUntilIdle()
        assertEquals(ReportAlertsState.Idle, model.state.value)
        assertEquals(0, preferences.records)
        assertFalse(model.load())
        assertEquals(1, publisher.attemptCancellations)
        assertFalse(publisher.hasSummary)
    }

    @Test fun clearingDuringNonCooperativeLoadCannotExposeItsLateSnapshot() = runTest(dispatcher) {
        val finish = CompletableDeferred<Unit>()
        preferences.readAction = { withContext(NonCancellable) { finish.await() }; snapshot(true, setOf(id(1))) }
        val store = ViewModelStore()
        val model = model(store)
        model.load(); runCurrent()
        store.clear()
        finish.complete(Unit)
        advanceUntilIdle()
        assertEquals(ReportAlertsState.Idle, model.state.value)
        assertEquals(0, publisher.published)
    }

    @Test fun failedPreferenceReadNeedsReloadAndCannotBeReplacedByAnEmptySnapshot() = runTest(dispatcher) {
        preferences.current = snapshot(true, setOf(id(1)))
        preferences.readAction = { throw IOException("private ledger filename") }
        val model = model()
        model.load(); runCurrent()
        assertTrue((model.state.value as ReportAlertsState.Error).needsReload)
        assertFalse(model.setEnabled(true))
        assertFalse(model.review())
        assertEquals(setOf(id(1)), preferences.current.presented)
        assertEquals(0, preferences.sets)
        assertEquals(0, preferences.records)
    }

    @Test fun cancelledScanNeedsReloadAndCannotAcknowledgeItsPartialRead() = runTest(dispatcher) {
        preferences.current = snapshot(true)
        source.readAction = { throw CancellationException("interrupted") }
        val model = loaded()
        model.review(); runCurrent()
        assertTrue((model.state.value as ReportAlertsState.Error).needsReload)
        assertFalse(model.review())
        assertEquals(0, publisher.published)
        assertEquals(0, preferences.records)
    }

    @Test fun permissionRevokedAfterEnableWriteIsNotReportedAsSuccessfulPermission() = runTest(dispatcher) {
        val model = loaded()
        preferences.setAction = { enabled, revision ->
            preferences.setDefault(enabled, revision).also { publisher.available = ReportAlertsAvailability.APP_BLOCKED }
        }
        model.setEnabled(true); runCurrent()
        assertTrue((model.state.value as ReportAlertsState.Error).needsReload)
        model.load(); runCurrent()
        assertTrue(ready(model).snapshot.enabled)
        assertEquals(ReportAlertsAvailability.APP_BLOCKED, ready(model).availability)
        assertEquals(0, publisher.published)
    }

    private suspend fun TestScope.loaded(store: ViewModelStore = ViewModelStore(), check: () -> Unit = {}): ReportAlertsViewModel {
        val model = model(store, check)
        assertTrue(model.load())
        runCurrent()
        assertTrue(model.state.value is ReportAlertsState.Ready)
        return model
    }

    private fun model(store: ViewModelStore = ViewModelStore(), check: () -> Unit = {}): ReportAlertsViewModel {
        if (store !in stores) stores += store
        return ViewModelProvider(store, ReportAlertsViewModel.factory(source, preferences, publisher, check, dispatcher))[
            ReportAlertsViewModel::class.java]
    }

    private fun ready(model: ReportAlertsViewModel) = model.state.value as ReportAlertsState.Ready

    private class Preferences : ReportAlertsRepository {
        var current = ReportAlertsSnapshot(null)
        var reads = 0
        var sets = 0
        var records = 0
        private var revision = 1_000_000
        var readAction: suspend () -> ReportAlertsSnapshot = { current }
        var setAction: suspend (Boolean, String?) -> ReportAlertsSnapshot = ::setDefault
        var recordAction: suspend (Set<String>, String?) -> ReportAlertsSnapshot = ::recordDefault
        override suspend fun read(): ReportAlertsSnapshot { reads++; return readAction() }
        override suspend fun setEnabled(enabled: Boolean, expectedRevision: String?): ReportAlertsSnapshot {
            sets++; return setAction(enabled, expectedRevision)
        }
        override suspend fun recordPresented(operationIds: Set<String>, expectedRevision: String?): ReportAlertsSnapshot {
            records++; return recordAction(operationIds, expectedRevision)
        }
        fun setDefault(enabled: Boolean, expected: String?): ReportAlertsSnapshot {
            check(expected == current.revision)
            current = ReportAlertsSnapshot(id(++revision), enabled, current.presented)
            return current
        }
        fun recordDefault(ids: Set<String>, expected: String?): ReportAlertsSnapshot {
            check(expected == current.revision)
            current = ReportAlertsSnapshot(id(++revision), current.enabled, current.presented + ids)
            return current
        }
    }

    private class Activity : ReportActivityRepository {
        var rows: List<ReportActivityEntry> = emptyList()
        val queries = mutableListOf<ReportActivityQuery>()
        var readAction: suspend (ReportActivityQuery) -> ReportActivityPage = { query ->
            val matches = rows.filter { it.event.kind == ReportStatusEventKind.STATUS_CHANGED }
                .sortedWith(compareByDescending<ReportActivityEntry> { it.event.recordedAt }.thenByDescending { it.event.operationId })
                .filter { query.before == null || it.event.recordedAt < query.before!!.recordedAt ||
                    it.event.recordedAt == query.before!!.recordedAt && it.event.operationId < query.before!!.operationId }
            ReportActivityPage(matches.take(query.limit), matches.size > query.limit)
        }
        override suspend fun readActivity(query: ReportActivityQuery): ReportActivityPage { queries += query; return readAction(query) }
    }

    private class AttemptFailure(message: String) : IOException(message) { val marker = Any() }
    private class SummarySlot { var sequence = 0L; var current: Long? = null }

    private class Publisher(val slot: SummarySlot = SummarySlot()) : ReportAlertsPublisher {
        var available = ReportAlertsAvailability.ALLOWED
        var prepared = 0
        var published = 0
        var cancelled = 0
        var attemptCancellations = 0
        private var attempt: Long? = null
        val hasSummary get() = slot.current != null
        val ownsSummary get() = attempt != null && attempt == slot.current
        var cancelAction: () -> Unit = {}
        var cancelAttemptAction: suspend () -> Unit = {}
        var publishAction: suspend (() -> Unit) -> ReportAlertsPublishResult = { it(); ReportAlertsPublishResult.ACCEPTED }
        override fun availability() = available
        override fun prepare() { prepared++ }
        override suspend fun publish(checkCurrent: () -> Unit): ReportAlertsPublishResult {
            published++
            return publishAction(checkCurrent).also { result ->
                if (result == ReportAlertsPublishResult.ACCEPTED) {
                    attempt = ++slot.sequence
                    slot.current = attempt
                }
            }
        }
        override suspend fun cancelAttempt() {
            attemptCancellations++
            cancelAttemptAction()
            if (ownsSummary) slot.current = null
            attempt = null
        }
        override fun cancel() { cancelled++; slot.current = null; attempt = null; cancelAction() }
    }

    companion object {
        private val BASE = Instant.parse("2026-10-14T10:00:00Z")
        private fun id(number: Int): String = UUID(0L, number.toLong()).toString()
        private var revisions = 2_000_000
        private fun snapshot(enabled: Boolean, ids: Set<String> = emptySet()) = ReportAlertsSnapshot(id(++revisions), enabled, ids)
        private fun entry(number: Int, at: Instant = BASE.plusSeconds(number.toLong()),
            kind: ReportStatusEventKind = ReportStatusEventKind.STATUS_CHANGED): ReportActivityEntry {
            val changed = kind == ReportStatusEventKind.STATUS_CHANGED
            return ReportActivityEntry(id(100_000 + number), IncidentType.RISK,
                ReportStatusEvent(id(number), if (changed) 2L else 1L, kind,
                    if (changed) ReportStatus.REPORTED else null,
                    if (changed) ReportStatus.IN_REVIEW else ReportStatus.REPORTED,
                    at, id(999_999), if (changed) "Revisión local registrada" else ""))
        }
    }
}
