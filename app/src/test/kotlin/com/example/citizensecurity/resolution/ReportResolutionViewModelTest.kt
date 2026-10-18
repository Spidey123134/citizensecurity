package com.example.citizensecurity.resolution

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.*
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportResolutionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun dispose() { stores.forEach { it.clear() }; Dispatchers.resetMain() }

    @Test fun constructionDoesNotReadOrWriteAnything() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); runCurrent()
        assertSame(ReportResolutionState.Idle, m.state.value)
        assertEquals(0, f.reads); assertEquals(0, f.receipts); assertTrue(f.writes.isEmpty())
    }

    @Test fun activateReadsOnlyOnePageAndCannotWrite() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); assertTrue(m.activate(FOLIO)); runCurrent()
        assertEquals(ReportResolution.UNCONFIRMED, ready(m).page.resolution)
        assertEquals(50, f.queries.single().limit); assertTrue(f.writes.isEmpty())
        assertFalse(m.activate(FOLIO)); runCurrent(); assertEquals(1, f.reads)
    }

    @Test fun anInvalidIdNeverReachesTheRepository() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model()
        listOf("", "123", FOLIO.uppercase(), "$FOLIO ", "' OR 1=1 --").forEach { assertFalse(m.activate(it)) }
        runCurrent(); assertEquals(0, f.reads); assertSame(ReportResolutionState.Idle, m.state.value)
    }

    @Test fun readOnlyActorCanReadButCannotPrepareAnyDeclaration() = runTest(dispatcher) {
        val f = Fixture(); f.actor = null; val m = f.model(); m.activate(FOLIO); runCurrent()
        assertFalse(m.select(ready(m), ReportResolution.RESOLVED))
        assertFalse(m.setReason(ready(m), REASON)); assertFalse(m.review(ready(m)))
        assertEquals(0, f.receipts); assertTrue(f.writes.isEmpty())
    }

    @Test fun allThreeValuesAreIndependentAndCurrentValueCannotBeSelected() = runTest(dispatcher) {
        val f = Fixture(); f.append(ReportResolution.RESOLVED)
        val m = f.model(); m.activate(FOLIO); runCurrent()
        assertFalse(m.select(ready(m), ReportResolution.RESOLVED))
        assertTrue(m.select(ready(m), ReportResolution.UNCONFIRMED))
        assertEquals(ReportResolution.UNCONFIRMED, ready(m).target)
        assertTrue(m.select(ready(m), ReportResolution.UNRESOLVED))
        assertEquals(ReportResolution.UNRESOLVED, ready(m).target)
    }

    @Test fun staleTextAndChoiceGesturesCannotReplaceCurrentPreparation() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); m.activate(FOLIO); runCurrent()
        val old = ready(m); assertTrue(m.setReason(old, REASON))
        assertFalse(m.setReason(old, "Otro motivo válido")); assertFalse(m.select(old, ReportResolution.RESOLVED))
        assertEquals(REASON, ready(m).reason)
    }

    @Test fun reasonValidationMatchesUnicodeDomainBoundaries() = runTest(dispatcher) {
        listOf("", "1234", " 12345", "12345 ", "12345\n", "12345\u0000", "12345\t", "x".repeat(501),
            "12345\uD800", "12345\uDC00").forEach { assertFalse(resolutionReasonValid(it)) }
        listOf("12345", "x".repeat(500), "😀".repeat(500), REASON).forEach { assertTrue(resolutionReasonValid(it)) }
    }

    @Test fun aTargetWithoutAValidReasonCannotStartAReview() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); m.activate(FOLIO); runCurrent()
        m.select(ready(m), ReportResolution.RESOLVED)
        assertFalse(m.review(ready(m))); assertEquals(1, f.reads); assertTrue(f.writes.isEmpty())
    }

    @Test fun reviewingRereadsActorAndRevisionButNeverWrites() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m); val count = f.reads
        assertTrue(m.review(ready(m))); runCurrent()
        assertEquals(count + 1, f.reads)
        val reviewed = review(m); assertEquals(f.actor, reviewed.command.actor)
        assertEquals(REASON, reviewed.command.reason)
        assertEquals(0, f.receipts); assertTrue(f.writes.isEmpty())
    }

    @Test fun aChangedRevisionRequiresAnotherReviewInsteadOfUsingOldReason() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m); f.append(ReportResolution.UNRESOLVED)
        m.review(ready(m)); runCurrent()
        assertEquals(ReportResolution.UNRESOLVED, ready(m).page.resolution)
        assertNull(ready(m).target); assertNotNull(ready(m).message); assertTrue(f.writes.isEmpty())
    }

    @Test fun aChangedActorRequiresFreshPreparationAndCannotBeForgedByUi() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m)
        f.actor = ReportResolutionActor(ACCOUNT, ReportResolutionActorKind.LOCAL_ADMINISTRATOR)
        m.review(ready(m)); runCurrent()
        assertEquals(ReportResolutionActorKind.LOCAL_ADMINISTRATOR, ready(m).page.actor!!.kind)
        assertNull(ready(m).target); assertTrue(f.writes.isEmpty())
    }

    @Test fun editInvalidatesTheOldConfirmAndNextReviewGetsAnotherUuid() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m); m.review(ready(m)); runCurrent()
        val first = review(m); assertTrue(m.edit(first)); assertFalse(m.confirm(first))
        m.review(ready(m)); runCurrent()
        assertNotEquals(first.command.operationId, review(m).command.operationId); assertTrue(f.writes.isEmpty())
    }

    @Test fun explicitSecondGestureWritesTheExactReviewedCommandOnce() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m); m.review(ready(m)); runCurrent()
        val expected = review(m); val command = expected.command
        assertTrue(m.confirm(expected)); assertFalse(m.confirm(expected)); runCurrent()
        assertSame(command, f.writes.single())
        assertTrue((m.state.value as ReportResolutionState.Confirmed).event.matches(command))
    }

    @Test fun duplicateConfirmCannotStartASecondNoncooperativeWriter() = runTest(dispatcher) {
        val f = Fixture(); val gate = CompletableDeferred<Unit>(); f.onRecord = { gate.await() }
        val m = f.model(); prepare(m); m.review(ready(m)); runCurrent()
        val expected = review(m); m.confirm(expected); runCurrent()
        assertTrue(m.state.value is ReportResolutionState.Working)
        assertFalse(m.confirm(expected)); assertEquals(1, f.writes.size)
        gate.complete(Unit); runCurrent(); assertTrue(m.state.value is ReportResolutionState.Confirmed)
    }

    @Test fun revisionChangedAfterReviewCannotReachRecord() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m); m.review(ready(m)); runCurrent()
        f.append(ReportResolution.UNRESOLVED); m.confirm(review(m)); runCurrent()
        assertTrue(f.writes.isEmpty()); assertTrue(m.state.value is ReportResolutionState.Uncertain)
        m.recover(uncertain(m)); runCurrent(); assertFalse(uncertain(m).canRetry); assertTrue(uncertain(m).canDiscard)
    }

    @Test fun authorityChangedAfterReviewCannotReachRecord() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m); m.review(ready(m)); runCurrent()
        f.actor = null; m.confirm(review(m)); runCurrent()
        assertTrue(f.writes.isEmpty()); m.recover(uncertain(m)); runCurrent()
        assertFalse(uncertain(m).canRetry)
    }

    @Test fun anErrorAfterCommitRecoversTheReceiptWithoutRepeatingRecord() = runTest(dispatcher) {
        val f = Fixture(); f.afterRecord = { throw IOException("lost response") }
        val m = f.model(); prepare(m); m.review(ready(m)); runCurrent()
        val command = review(m).command; m.confirm(review(m)); runCurrent()
        assertTrue(m.state.value is ReportResolutionState.Uncertain)
        assertSame(command, uncertain(m).command)
        m.recover(uncertain(m)); runCurrent()
        assertTrue((m.state.value as ReportResolutionState.Confirmed).event.matches(command))
        assertEquals(1, f.writes.size)
    }

    @Test fun anErrorBeforeCommitNeedsExplicitRetryWithTheSameUuidAndBody() = runTest(dispatcher) {
        val f = Fixture(); f.onRecord = { throw IOException("before SQL") }
        val m = f.model(); prepare(m); m.review(ready(m)); runCurrent()
        val command = review(m).command; m.confirm(review(m)); runCurrent()
        assertFalse(m.retry(uncertain(m))); m.recover(uncertain(m)); runCurrent()
        assertTrue(uncertain(m).canRetry); assertEquals(1, f.writes.size)
        f.onRecord = {}; m.retry(uncertain(m)); runCurrent()
        assertEquals(2, f.writes.size); assertTrue(f.writes.all { it === command })
        assertEquals(1, f.events.size); assertTrue(m.state.value is ReportResolutionState.Confirmed)
    }

    @Test fun aMalformedReceiptCannotBecomeAConfirmedResultOrTriggerAWrite() = runTest(dispatcher) {
        val f = Fixture(); f.onRecord = { throw IOException() }
        val m = f.model(); prepare(m); m.review(ready(m)); runCurrent(); m.confirm(review(m)); runCurrent()
        f.receiptOverride = { event(it).copy(reason = "Cuerpo distinto") }
        m.recover(uncertain(m)); runCurrent()
        assertTrue(m.state.value is ReportResolutionState.Uncertain); assertFalse(uncertain(m).canRetry)
        assertEquals(1, f.writes.size)
    }

    @Test fun confirmedReceiptRequiresAnotherReadBeforeReturningToTheEditor() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m); m.review(ready(m)); runCurrent(); m.confirm(review(m)); runCurrent()
        val confirmed = m.state.value as ReportResolutionState.Confirmed
        val count = f.receipts; m.acknowledge(confirmed); runCurrent()
        assertEquals(count + 1, f.receipts); assertEquals(ReportResolution.RESOLVED, ready(m).page.resolution)
        assertFalse(m.acknowledge(confirmed)); assertEquals(1, f.writes.size)
    }

    @Test fun aLateReceiptWinsOverDiscardingAStaleRequest() = runTest(dispatcher) {
        val f = Fixture(); f.onRecord = { throw IOException() }
        val m = f.model(); prepare(m); m.review(ready(m)); runCurrent()
        val command = review(m).command; m.confirm(review(m)); runCurrent()
        f.append(ReportResolution.UNRESOLVED); m.recover(uncertain(m)); runCurrent()
        assertTrue(uncertain(m).canDiscard)
        f.receiptOverride = { event(it) }
        m.discard(uncertain(m)); runCurrent()
        assertEquals(command.operationId, (m.state.value as ReportResolutionState.Confirmed).event.operationId)
        assertEquals(1, f.writes.size)
    }

    @Test fun staleRequestCanBeClosedOnlyBySeparateReadAndGesture() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m); m.review(ready(m)); runCurrent()
        f.append(ReportResolution.UNRESOLVED); m.confirm(review(m)); runCurrent()
        assertFalse(m.discard(uncertain(m))); m.recover(uncertain(m)); runCurrent()
        assertTrue(m.discard(uncertain(m))); runCurrent()
        assertEquals(ReportResolution.UNRESOLVED, ready(m).page.resolution)
        assertNull(ready(m).target); assertTrue(f.writes.isEmpty())
    }

    @Test fun clearingDuringAReadHidesItsLateNoncooperativeResult() = runTest(dispatcher) {
        val f = Fixture(); val gate = CompletableDeferred<Unit>()
        f.onRead = { withContext(NonCancellable) { gate.await() } }
        val m = f.model(); m.activate(FOLIO); runCurrent(); m.clear()
        gate.complete(Unit); runCurrent()
        assertSame(ReportResolutionState.Idle, m.state.value); assertTrue(f.writes.isEmpty())
    }

    @Test fun switchingReportsIgnoresTheOldReadEvenIfItCompletesLast() = runTest(dispatcher) {
        val f = Fixture(); val gate = CompletableDeferred<Unit>()
        f.onRead = { query -> if (query.reportId == FOLIO) withContext(NonCancellable) { gate.await() } }
        val m = f.model(); m.activate(FOLIO); runCurrent(); m.activate(OTHER); runCurrent()
        assertEquals(OTHER, ready(m).page.reportId)
        gate.complete(Unit); runCurrent(); assertEquals(OTHER, ready(m).page.reportId)
    }

    @Test fun clearingAnUncertainCommandAllowsOnlyRecoveryWhenReturningToItsFolio() = runTest(dispatcher) {
        val f = Fixture(); f.onRecord = { throw IOException() }
        val m = f.model(); prepare(m); m.review(ready(m)); runCurrent()
        val command = review(m).command; m.confirm(review(m)); runCurrent()
        m.clear(); m.activate(FOLIO); runCurrent()
        assertSame(command, uncertain(m).command); assertEquals(1, f.writes.size)
        assertFalse(uncertain(m).canRetry)
    }

    @Test fun pendingCommandBlocksAnotherFolioWithoutShowingItsPrivateBody() = runTest(dispatcher) {
        val f = Fixture(); f.onRecord = { throw IOException() }
        val m = f.model(); prepare(m); m.review(ready(m)); runCurrent(); m.confirm(review(m)); runCurrent()
        m.clear(); m.activate(OTHER); runCurrent()
        assertEquals(OTHER, ready(m).page.reportId); assertTrue(ready(m).blockedByPending)
        assertEquals("", ready(m).reason); assertNull(ready(m).target)
        assertFalse(m.select(ready(m), ReportResolution.RESOLVED)); assertEquals(1, f.writes.size)
    }

    @Test fun navigationDoesNotReleaseTheExclusionOfAnActiveWriter() = runTest(dispatcher) {
        val f = Fixture(); val gate = CompletableDeferred<Unit>()
        f.onRecord = { withContext(NonCancellable) { gate.await() } }
        val m = f.model(); prepare(m); m.review(ready(m)); runCurrent(); m.confirm(review(m)); runCurrent()
        m.clear(); m.activate(OTHER); runCurrent(); assertTrue(ready(m).blockedByPending)
        m.clear(); m.activate(FOLIO); runCurrent(); assertTrue(m.state.value is ReportResolutionState.Working)
        assertEquals(1, f.writes.size); gate.complete(Unit); runCurrent()
        assertTrue(m.state.value is ReportResolutionState.Confirmed)
    }

    @Test fun aNewProcessModelReadsExistingSqlButDoesNotReplayTheOldCommand() = runTest(dispatcher) {
        val f = Fixture(); f.afterRecord = { throw IOException() }
        val m = f.model(); prepare(m); m.review(ready(m)); runCurrent(); m.confirm(review(m)); runCurrent()
        stores.first().clear(); val reopened = f.model(); reopened.activate(FOLIO); runCurrent()
        assertEquals(ReportResolution.RESOLVED, ready(reopened).page.resolution)
        assertEquals(1, f.writes.size); assertNull(ready(reopened).target)
    }

    @Test fun pagingUsesExclusiveCursorsAndFiftyEventsWithoutInventingHistory() = runTest(dispatcher) {
        val f = Fixture(); repeat(121) { index -> f.append(if (index % 2 == 0) ReportResolution.RESOLVED else ReportResolution.UNRESOLVED) }
        val m = f.model(); m.activate(FOLIO); runCurrent()
        assertEquals(50, ready(m).page.events.size); assertEquals(121L, ready(m).page.events.first().sequence)
        m.next(ready(m)); runCurrent()
        assertEquals(71L, ready(m).page.events.first().sequence); assertEquals(1, ready(m).previous.size)
        assertEquals(72L, f.queries.last().beforeSequence)
        m.next(ready(m)); runCurrent(); assertEquals(21, ready(m).page.events.size); assertFalse(ready(m).page.hasMore)
        m.previous(ready(m)); runCurrent(); assertEquals(71L, ready(m).page.events.first().sequence)
        m.refresh(ready(m)); runCurrent(); assertEquals(121L, ready(m).page.events.first().sequence)
        assertTrue(ready(m).previous.isEmpty()); assertTrue(f.writes.isEmpty())
    }

    @Test fun backendPermissionDenialRetiresAllVisiblePrivatePreparation() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m); m.review(ready(m)); runCurrent()
        f.onRecord = { throw ReportResolutionPermissionDeniedException() }
        m.confirm(review(m)); runCurrent(); assertSame(ReportResolutionState.Denied, m.state.value)
        assertFalse(m.activate(FOLIO)); assertFalse(m.refresh())
    }

    @Test fun sessionRevokedDuringANoncooperativeReadCannotPublishPrivateData() = runTest(dispatcher) {
        val f = Fixture(); val gate = CompletableDeferred<Unit>()
        f.onRead = { withContext(NonCancellable) { gate.await() } }
        val m = f.model(); m.activate(FOLIO); runCurrent(); f.active = false
        gate.complete(Unit); runCurrent(); assertSame(ReportResolutionState.Denied, m.state.value)
    }

    @Test fun revocationOnResumeDoesNotWriteRecoverOrAcknowledgeAnything() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m); f.active = false
        assertFalse(m.checkAccess()); runCurrent()
        assertSame(ReportResolutionState.Denied, m.state.value); assertEquals(0, f.receipts); assertTrue(f.writes.isEmpty())
    }

    @Test fun resumeRemovesAnOldReviewWhenAuthorityBecomesReadOnly() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m); m.review(ready(m)); runCurrent()
        val old = review(m); f.actor = null; m.checkAccess(); runCurrent()
        assertNull(ready(m).page.actor); assertEquals("", ready(m).reason)
        assertFalse(m.confirm(old)); assertTrue(f.writes.isEmpty()); assertEquals(0, f.receipts)
    }

    @Test fun resumeWithdrawsAnOldFormWhenAuthorityCanNoLongerBeRead() = runTest(dispatcher) {
        val f = Fixture(); val m = f.model(); prepare(m)
        f.onRead = { throw IOException("private authority could not be read") }
        m.checkAccess(); runCurrent()
        assertTrue(m.state.value is ReportResolutionState.Error); assertTrue(f.writes.isEmpty())
    }

    @Test fun resumeWithAPendingCommandNeverRetriesOrAcknowledgesIt() = runTest(dispatcher) {
        val f = Fixture(); f.onRecord = { throw IOException() }
        val m = f.model(); prepare(m); m.review(ready(m)); runCurrent(); m.confirm(review(m)); runCurrent()
        val expected = uncertain(m); val receipts = f.receipts
        m.checkAccess(); runCurrent()
        assertSame(expected, m.state.value); assertEquals(1, f.writes.size); assertEquals(receipts, f.receipts)
    }

    @Test fun resumeDisablesAnOldRetryIfTheActorOrRevisionChangedWithoutReadingAReceipt() = runTest(dispatcher) {
        val f = Fixture(); f.onRecord = { throw IOException() }
        val m = f.model(); prepare(m); m.review(ready(m)); runCurrent(); m.confirm(review(m)); runCurrent()
        m.recover(uncertain(m)); runCurrent(); assertTrue(uncertain(m).canRetry)
        val command = uncertain(m).command; val receipts = f.receipts
        f.actor = null; m.checkAccess(); runCurrent()
        assertSame(command, uncertain(m).command); assertFalse(uncertain(m).canRetry)
        assertFalse(uncertain(m).canDiscard); assertEquals(receipts, f.receipts); assertEquals(1, f.writes.size)
    }

    @Test fun missingReportDoesNotCreateAnUnconfirmedRowOrAnyEvent() = runTest(dispatcher) {
        val f = Fixture(); f.missing = true
        val m = f.model(); m.activate(FOLIO); runCurrent()
        assertTrue(m.state.value is ReportResolutionState.Missing); assertTrue(f.events.isEmpty()); assertTrue(f.writes.isEmpty())
    }

    @Test fun readErrorIsGenericAndDoesNotReplaceDataWithAnEmptyResult() = runTest(dispatcher) {
        val f = Fixture(); f.onRead = { throw IOException("private body") }
        val m = f.model(); m.activate(FOLIO); runCurrent()
        assertTrue(m.state.value is ReportResolutionState.Error); assertTrue(f.writes.isEmpty())
        f.onRead = {}; m.refresh(); runCurrent(); assertTrue(m.state.value is ReportResolutionState.Ready)
    }

    @Test fun wrongReportFromReaderCannotAppearInTheCurrentDetail() = runTest(dispatcher) {
        val f = Fixture(); f.pageOverride = { ReportResolutionPage(OTHER, ReportResolution.UNCONFIRMED, 0, emptyList(), false, f.actor) }
        val m = f.model(); m.activate(FOLIO); runCurrent()
        assertTrue(m.state.value is ReportResolutionState.Error); assertTrue(f.writes.isEmpty())
    }

    private suspend fun kotlinx.coroutines.test.TestScope.prepare(m: ReportResolutionViewModel) {
        m.activate(FOLIO); runCurrent()
        assertTrue(m.select(ready(m), ReportResolution.RESOLVED)); assertTrue(m.setReason(ready(m), REASON))
    }
    private fun ready(m: ReportResolutionViewModel) = m.state.value as ReportResolutionState.Ready
    private fun review(m: ReportResolutionViewModel) = m.state.value as ReportResolutionState.Reviewing
    private fun uncertain(m: ReportResolutionViewModel) = m.state.value as ReportResolutionState.Uncertain

    private inner class Fixture : ReportResolutionRepository {
        var active = true
        var actor: ReportResolutionActor? = ReportResolutionActor(ACCOUNT, ReportResolutionActorKind.AUTHOR)
        var reads = 0; var receipts = 0; var missing = false
        val queries = mutableListOf<ReportResolutionQuery>()
        val events = mutableListOf<ReportResolutionEvent>()
        val writes = mutableListOf<ReportResolutionCommand>()
        var onRead: suspend (ReportResolutionQuery) -> Unit = {}
        var onRecord: suspend (ReportResolutionCommand) -> Unit = {}
        var afterRecord: suspend () -> Unit = {}
        var receiptOverride: ((ReportResolutionCommand) -> ReportResolutionEvent?)? = null
        var pageOverride: ((ReportResolutionQuery) -> ReportResolutionPage?)? = null

        fun model(): ReportResolutionViewModel {
            val store = ViewModelStore().also(stores::add)
            return ViewModelProvider.create(store, ReportResolutionViewModel.factory(this,
                { check(active) }, dispatcher))[ReportResolutionViewModel::class.java]
        }
        fun append(target: ReportResolution) {
            val last = events.lastOrNull()
            events += ReportResolutionEvent(UUID.randomUUID().toString(), FOLIO, events.size.toLong() + 1,
                last?.toResolution ?: ReportResolution.UNCONFIRMED, target, Instant.parse("2026-10-17T13:00:00Z"),
                requireNotNull(actor), REASON)
        }
        override suspend fun read(query: ReportResolutionQuery): ReportResolutionPage? {
            ++reads; queries += query; onRead(query)
            pageOverride?.let { return it(query) }
            if (missing) return null
            val all = events.filter { it.reportId == query.reportId }
            val before = query.beforeSequence
            val selected = all.asReversed().filter { before == null || it.sequence < before }.take(query.limit + 1)
            return ReportResolutionPage(query.reportId, all.lastOrNull()?.toResolution ?: ReportResolution.UNCONFIRMED,
                all.lastOrNull()?.sequence ?: 0, selected.take(query.limit), selected.size > query.limit, actor)
        }
        override suspend fun record(command: ReportResolutionCommand): ReportResolutionEvent {
            writes += command; onRecord(command)
            val existing = events.firstOrNull { it.operationId == command.operationId }
            val result = existing ?: event(command).also(events::add)
            afterRecord(); return result
        }
        override suspend fun receipt(command: ReportResolutionCommand): ReportResolutionEvent? {
            ++receipts
            receiptOverride?.let { return it(command) }
            return events.firstOrNull { it.operationId == command.operationId }
        }
    }

    companion object {
        private const val FOLIO = "a0000000-0000-4000-8000-000000000001"
        private const val OTHER = "b0000000-0000-4000-8000-000000000002"
        private const val ACCOUNT = "c0000000-0000-4000-8000-000000000003"
        private const val REASON = "Se revisó el resultado en el lugar."
        private fun event(command: ReportResolutionCommand) = ReportResolutionEvent(command.operationId,
            command.reportId, command.expectedSequence + 1L, command.expectedResolution, command.targetResolution,
            Instant.parse("2026-10-17T14:00:00Z"), command.actor, command.reason)
    }
}
