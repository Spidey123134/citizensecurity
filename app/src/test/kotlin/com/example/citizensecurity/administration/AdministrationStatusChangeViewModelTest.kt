package com.example.citizensecurity.administration

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.report.ReportStatusRecoveryState
import com.example.citizensecurity.report.ReportStatusRecoveryViewModel
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
class AdministrationStatusChangeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun configure() = Dispatchers.setMain(dispatcher)
    @After fun dispose() { stores.forEach { it.clear() }; Dispatchers.resetMain() }

    @Test fun constructingThePreparationNeitherReadsNorCreatesACommand() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); runCurrent()
        assertSame(AdministrationStatusChangeState.Idle, model.state.value)
        assertEquals(0, f.reads)
        assertTrue(f.permissions.isEmpty())
        assertTrue(f.writes.isEmpty())
        assertNull(f.journal.saved)
    }

    @Test fun invalidFolioCannotReachTheReader() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model()
        listOf("", "short", "1-1-1-1-1", "$FOLIO trailing", "' OR 1=1 --").forEach {
            assertFalse(model.loadFolio(it))
            assertSame(AdministrationStatusChangeState.Invalid, model.state.value)
        }
        assertEquals(0, f.reads)
    }

    @Test fun folioTrimsAndNormalizesCaseAndReadsABoundedCoherentRevision() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model()
        assertTrue(model.loadFolio(" \n${FOLIO.uppercase()}\t")); runCurrent()
        assertEquals(FOLIO, editing(model).snapshot.report.id)
        assertEquals(1L, editing(model).snapshot.sequence)
        assertEquals(4, f.reads)
        assertEquals(listOf(1, 1), f.historyLimits)
        assertEquals(setOf(ReportAdministrationPermission.READ_REPORTS, ReportAdministrationPermission.CHANGE_STATUS),
            f.permissions.toSet())
    }

    @Test fun duplicateLoadWhileInFlightDoesNotStartAnotherRead() = runTest(dispatcher) {
        val f = Fixture(); val gate = CompletableDeferred<Unit>()
        f.onFind = { gate.await(); f.row }
        val model = f.model()
        assertTrue(model.loadFolio(FOLIO)); assertFalse(model.loadFolio(FOLIO)); runCurrent()
        assertEquals(1, f.reads)
        gate.complete(Unit); runCurrent()
        assertTrue(model.state.value is AdministrationStatusChangeState.Editing)
    }

    @Test fun aMissingFolioDoesNotInventAReportOrHistory() = runTest(dispatcher) {
        val f = Fixture(); f.row = null
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        assertEquals(FOLIO, (model.state.value as AdministrationStatusChangeState.Missing).reportId)
        assertTrue(f.historyLimits.isEmpty())
    }

    @Test fun closedReportHasNoFurtherTransition() = runTest(dispatcher) {
        val f = Fixture(); f.row = report(ReportStatus.CLOSED)
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        val closed = model.state.value as AdministrationStatusChangeState.Closed
        assertNull(closed.snapshot.nextStatus)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun eachNonterminalStateOffersOnlyItsImmediateSuccessor() = runTest(dispatcher) {
        assertEquals(ReportStatus.IN_REVIEW, AdministrationStatusSnapshot(report(), 1).nextStatus)
        assertEquals(ReportStatus.ATTENDED, AdministrationStatusSnapshot(report(ReportStatus.IN_REVIEW), 2).nextStatus)
        assertEquals(ReportStatus.CLOSED, AdministrationStatusSnapshot(report(ReportStatus.ATTENDED), 3).nextStatus)
    }

    @Test fun reasonMatchesDomainLimitsIncludingUnicodeAndRejectsControlsAndUnpairedSurrogates() = runTest(dispatcher) {
        listOf("", "1234", " 12345", "12345 ", "12345\n", "12345\u0000", "12345\t", "x".repeat(501), "12345\uD800", "12345\uDC00").forEach {
            assertFalse("Should reject this reason", administrationReasonValid(it))
        }
        listOf("12345", "x".repeat(500), "😀".repeat(500), "Revisión local de los datos.").forEach {
            assertTrue("Should accept this reason", administrationReasonValid(it))
        }
    }

    @Test fun anObsoleteTextGestureCannotReplaceTheLatestReason() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        val old = editing(model)
        assertTrue(model.setReason(old, REASON))
        assertFalse(model.setReason(old, "Gesture obsoleto"))
        assertEquals(REASON, editing(model).reason)
    }

    @Test fun invalidReasonCannotBeginReviewOrAnyStorageOperation() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        val count = f.reads
        assertFalse(model.review(editing(model)))
        assertEquals(count, f.reads)
        assertNull(f.journal.saved)
    }

    @Test fun reviewRereadsTheRowAndRevisionButDoesNotWriteOrRetainACommand() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); prepare(model)
        val count = f.reads
        assertTrue(model.review(editing(model))); runCurrent()
        assertEquals(count + 4, f.reads)
        assertEquals(REASON, reviewing(model).reason)
        assertTrue(f.writes.isEmpty())
        assertNull(f.journal.saved)
    }

    @Test fun editingAReviewInvalidatesItsOldConfirmGesture() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); prepare(model); model.review(editing(model)); runCurrent()
        val old = reviewing(model)
        assertTrue(model.editReview(old))
        val recovery = f.recovery(); recovery.recover(); runCurrent()
        assertFalse(model.confirmReviewed(old, recovery) { true })
        assertEquals(REASON, editing(model).reason)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun aChangedReportDuringReviewRequiresAnotherReviewOfTheNewState() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); prepare(model)
        f.row = report(ReportStatus.IN_REVIEW); f.sequence = 2
        model.review(editing(model)); runCurrent()
        assertEquals(ReportStatus.IN_REVIEW, editing(model).snapshot.report.status)
        assertNotNull(editing(model).message)
        assertEquals(REASON, editing(model).reason)
        assertNull(f.journal.saved)
    }

    @Test fun confirmationRereadsAndHandsOffOnlyTheCompleteReviewedBody() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); prepare(model); model.review(editing(model)); runCurrent()
        val review = reviewing(model); val recovery = f.recovery(); recovery.recover(); runCurrent()
        assertTrue(model.confirmReviewed(review, recovery) { true })
        assertFalse(model.confirmReviewed(review, recovery) { true })
        runCurrent()
        val request = f.writes.single()
        assertEquals(FOLIO, request.command.reportId)
        assertEquals(1L, request.command.expectedSequence)
        assertEquals(ReportStatus.REPORTED, request.command.expectedStatus)
        assertEquals(ReportStatus.IN_REVIEW, request.command.targetStatus)
        assertEquals(REASON, request.command.reason)
        assertEquals(SCOPE, request.scope)
        assertTrue(recovery.state.value is ReportStatusRecoveryState.Confirmed)
        assertSame(AdministrationStatusChangeState.Idle, model.state.value)
        assertEquals(request, f.journal.saved)
    }

    @Test fun aRevisionChangedAfterReviewDoesNotSubmitTheOldRequest() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); prepare(model); model.review(editing(model)); runCurrent()
        val review = reviewing(model); val recovery = f.recovery(); recovery.recover(); runCurrent()
        f.row = report(ReportStatus.IN_REVIEW); f.sequence = 2
        model.confirmReviewed(review, recovery) { true }; runCurrent()
        assertTrue(f.writes.isEmpty())
        assertNotNull(editing(model).message)
        assertNull(f.journal.saved)
    }

    @Test fun recoveryMustHaveExplicitlyVerifiedAnEmptyJournalBeforeConfirmation() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); prepare(model); model.review(editing(model)); runCurrent()
        val count = f.reads
        assertFalse(model.confirmReviewed(reviewing(model), f.recovery()) { true })
        assertEquals(count, f.reads)
        assertNull(f.journal.saved)
    }

    @Test fun aBackgroundConfirmGestureDoesNotReadOrWrite() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); prepare(model); model.review(editing(model)); runCurrent()
        val recovery = f.recovery(); recovery.recover(); runCurrent(); val count = f.reads
        assertFalse(model.confirmReviewed(reviewing(model), recovery) { false })
        assertEquals(count, f.reads)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun losingForegroundDuringRevisionChecksPreventsHandoffAndRequiresANewGesture() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); prepare(model); model.review(editing(model)); runCurrent()
        val recovery = f.recovery(); recovery.recover(); runCurrent()
        var foreground = true
        f.onFind = { foreground = false; f.row }
        model.confirmReviewed(reviewing(model), recovery) { foreground }; runCurrent()
        assertTrue(model.state.value is AdministrationStatusChangeState.Reviewing)
        assertNull(f.journal.saved)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun cancellingThePreparationInvalidatesQueuedReviewAndConfirmation() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); prepare(model)
        val editing = editing(model); model.review(editing); runCurrent()
        val review = reviewing(model); model.cancel()
        val recovery = f.recovery(); recovery.recover(); runCurrent()
        assertFalse(model.review(editing))
        assertFalse(model.confirmReviewed(review, recovery) { true })
        assertSame(AdministrationStatusChangeState.Idle, model.state.value)
        assertNull(f.journal.saved)
    }

    @Test fun readPermissionDenialPreventsTheFirstDatabaseReadAndCannotBeRevived() = runTest(dispatcher) {
        val f = Fixture(); f.readAllowed = false
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        assertSame(AdministrationStatusChangeState.Denied, model.state.value)
        assertEquals(0, f.reads)
        f.readAllowed = true
        assertFalse(model.loadFolio(FOLIO))
    }

    @Test fun readPermissionAloneDoesNotPermitStatusPreparation() = runTest(dispatcher) {
        val f = Fixture(); f.writeAllowed = false
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        assertSame(AdministrationStatusChangeState.Denied, model.state.value)
        assertEquals(0, f.reads)
    }

    @Test fun lossOfSessionDuringAuthorityLookupIsCheckedBeforeSql() = runTest(dispatcher) {
        val f = Fixture(); f.onPolicy = { f.current = false }
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        assertSame(AdministrationStatusChangeState.Denied, model.state.value)
        assertEquals(0, f.reads)
    }

    @Test fun sessionLossAfterTheFirstReadRetiresPrivatePreparationBeforeReadingHistory() = runTest(dispatcher) {
        val f = Fixture(); f.onFind = { f.current = false; f.row }
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        assertSame(AdministrationStatusChangeState.Denied, model.state.value)
        assertTrue(f.historyLimits.isEmpty())
    }

    @Test fun revokedWritePermissionAfterDisplayClearsTheReasonAndReportOnResume() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); prepare(model)
        f.writeAllowed = false; model.checkAccess(); runCurrent()
        assertSame(AdministrationStatusChangeState.Denied, model.state.value)
        assertFalse(model.loadFolio(FOLIO))
        assertNull(f.journal.saved)
    }

    @Test fun mismatchedHistoryAndReportCannotProduceAReviewableSnapshot() = runTest(dispatcher) {
        val f = Fixture(); f.onHistory = { history(ReportStatus.IN_REVIEW, 2) }
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        assertTrue(model.state.value is AdministrationStatusChangeState.Error)
    }

    @Test fun historyForAnotherFolioIsRejected() = runTest(dispatcher) {
        val f = Fixture(); f.onHistory = { ReportHistoryPage(OTHER, ReportStatus.REPORTED, 0, emptyList(), false, false) }
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        assertTrue(model.state.value is AdministrationStatusChangeState.Error)
    }

    @Test fun missingLatestHistoryEventCannotMasqueradeAsAValidRevision() = runTest(dispatcher) {
        val f = Fixture(); f.onHistory = { ReportHistoryPage(FOLIO, ReportStatus.REPORTED, 1, emptyList(), false, false) }
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        assertTrue(model.state.value is AdministrationStatusChangeState.Error)
    }

    @Test fun reportChangedBetweenBoundedReadsCannotBePresentedAsCoherent() = runTest(dispatcher) {
        val f = Fixture(); var count = 0
        f.onFind = { if (++count == 1) report() else report().copy(description = "Descripción concurrente") }
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        assertTrue(model.state.value is AdministrationStatusChangeState.Error)
    }

    @Test fun sequenceChangedDuringTheSecondHistoryReadCannotBePresentedAsCoherent() = runTest(dispatcher) {
        val f = Fixture(); var count = 0
        f.onHistory = { if (++count == 1) history() else history(ReportStatus.IN_REVIEW, 2) }
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        assertTrue(model.state.value is AdministrationStatusChangeState.Error)
    }

    @Test fun aLegacyReportWithoutEventsCanUseItsRealZeroRevisionWithoutInventedCreation() = runTest(dispatcher) {
        val f = Fixture(); f.sequence = 0
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        assertEquals(0L, editing(model).snapshot.sequence)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun readerFailureProvidesAnExplicitRereadInsteadOfAnEmptyReplacement() = runTest(dispatcher) {
        val f = Fixture(); f.onFind = { throw IOException() }
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        val error = model.state.value as AdministrationStatusChangeState.Error
        assertEquals(FOLIO, error.reportId)
        f.onFind = { f.row }; model.loadFolio(error.reportId, error); runCurrent()
        assertTrue(model.state.value is AdministrationStatusChangeState.Editing)
        assertNull(f.journal.saved)
    }

    @Test fun aNonCooperativeReadCannotRestorePrivateDataAfterCancellation() = runTest(dispatcher) {
        val f = Fixture(); val gate = CompletableDeferred<Unit>()
        f.onFind = { withContext(NonCancellable) { gate.await(); f.row } }
        val model = f.model(); model.loadFolio(FOLIO); runCurrent()
        model.cancel(); gate.complete(Unit); runCurrent()
        assertSame(AdministrationStatusChangeState.Idle, model.state.value)
        assertTrue(f.historyLimits.isEmpty())
        assertNull(f.journal.saved)
    }

    @Test fun clearingTheViewModelPermanentlyRetiresAnyRenderedGesture() = runTest(dispatcher) {
        val f = Fixture(); val model = f.model(); prepare(model); val old = editing(model)
        stores.first().clear()
        assertSame(AdministrationStatusChangeState.Denied, model.state.value)
        assertFalse(model.setReason(old, "Otra intención"))
        assertFalse(model.loadFolio(FOLIO))
    }

    private suspend fun kotlinx.coroutines.test.TestScope.prepare(model: AdministrationStatusChangeViewModel) {
        model.loadFolio(FOLIO); runCurrent()
        model.setReason(editing(model), REASON)
    }

    private inner class Fixture {
        var current = true
        var readAllowed = true
        var writeAllowed = true
        var row: Report? = report()
        var sequence = 1L
        var reads = 0
        var onPolicy: () -> Unit = {}
        val permissions = mutableListOf<ReportAdministrationPermission>()
        val historyLimits = mutableListOf<Int>()
        val writes = mutableListOf<ReportStatusChangeRequest>()
        val journal = Journal()
        var onFind: suspend () -> Report? = { row }
        var onHistory: suspend () -> ReportHistoryPage? = { row?.let { history(it.status, sequence) } }
        private val reader = object : ReportAdministrationReader {
            override val scope = SCOPE
            override suspend fun findById(id: String): Report? { assertEquals(FOLIO, id); reads++; return onFind() }
            override suspend fun listPage(query: ReportListPageQuery): ReportListPage = error("No list scan")
            override suspend fun readHistory(query: ReportHistoryQuery): ReportHistoryPage? {
                reads++; historyLimits.add(query.limit); return onHistory()
            }
        }
        private val policy = ReportAdministrationPolicy { scope, permission ->
            assertEquals(SCOPE, scope); permissions.add(permission); onPolicy()
            if ((permission == ReportAdministrationPermission.READ_REPORTS && !readAllowed) ||
                (permission == ReportAdministrationPermission.CHANGE_STATUS && !writeAllowed)) throw ReportAdministrationPermissionDeniedException()
        }
        fun model(): AdministrationStatusChangeViewModel {
            val store = ViewModelStore().also { stores.add(it) }
            return ViewModelProvider(store, viewModelFactory { initializer {
                AdministrationStatusChangeViewModel(reader, policy, { check(current) }, dispatcher)
            } })[AdministrationStatusChangeViewModel::class.java]
        }
        fun recovery(): ReportStatusRecoveryViewModel {
            val store = ViewModelStore().also { stores.add(it) }
            val receipts = mutableMapOf<String, ReportStatusChangeReceipt>()
            val access = object : ReportStatusChangeAccess {
                override val scope = SCOPE
                override fun checkCurrent() { check(current && readAllowed && writeAllowed) }
                override suspend fun change(request: ReportStatusChangeRequest): ReportStatusEvent {
                    checkCurrent(); writes.add(request)
                    val c = request.command
                    return ReportStatusEvent(c.operationId, c.expectedSequence + 1, ReportStatusEventKind.STATUS_CHANGED,
                        c.expectedStatus, c.targetStatus, TIME, ACTOR, c.reason).also {
                        receipts[c.operationId] = ReportStatusChangeReceipt(c.reportId, it)
                    }
                }
                override suspend fun findReceipt(request: ReportStatusChangeRequest): ReportStatusChangeReceipt? {
                    checkCurrent(); return receipts[request.command.operationId]
                }
            }
            return ViewModelProvider(store, viewModelFactory { initializer {
                ReportStatusRecoveryViewModel(journal, access, dispatcher, Clock.fixed(TIME, ZoneOffset.UTC), { OPERATION })
            } })[ReportStatusRecoveryViewModel::class.java]
        }
    }

    private class Journal : ReportStatusChangeJournal {
        var saved: ReportStatusChangeRequest? = null
        override suspend fun read() = saved
        override suspend fun retain(request: ReportStatusChangeRequest) { check(saved == null || saved == request); saved = request }
        override suspend fun clear(expected: ReportStatusChangeRequest) { check(saved == expected); saved = null }
        override suspend fun <T> exclusive(block: suspend () -> T): T = block()
    }
    private fun editing(model: AdministrationStatusChangeViewModel) = model.state.value as AdministrationStatusChangeState.Editing
    private fun reviewing(model: AdministrationStatusChangeViewModel) = model.state.value as AdministrationStatusChangeState.Reviewing

    private companion object {
        const val FOLIO = "00000000-0000-4000-8000-000000000001"
        const val OTHER = "00000000-0000-4000-8000-000000000002"
        const val ACTOR = "00000000-0000-4000-8000-000000999999"
        const val OPERATION = "00000000-0000-4000-8000-000000000099"
        const val REASON = "Comprobación local de la información del reporte."
        val TIME: Instant = Instant.parse("2026-10-15T18:00:00Z")
        val SCOPE = ReportStatusChangeScope(ACTOR, ACTOR)
        fun report(status: ReportStatus = ReportStatus.REPORTED) = Report(FOLIO, IncidentType.RISK, Priority.MEDIUM,
            "Descripción completa del reporte local.", TIME, ReportLocation("Referencia local"), TIME, status)
        fun history(status: ReportStatus = ReportStatus.REPORTED, sequence: Long = 1): ReportHistoryPage {
            val event = if (sequence == 0L) emptyList() else listOf(if (status == ReportStatus.REPORTED) {
                ReportStatusEvent(OPERATION, sequence, ReportStatusEventKind.CREATED, null, status, TIME, ACTOR)
            } else {
                val previous = when (status) {
                    ReportStatus.IN_REVIEW -> ReportStatus.REPORTED
                    ReportStatus.ATTENDED -> ReportStatus.IN_REVIEW
                    ReportStatus.CLOSED -> ReportStatus.ATTENDED
                    else -> error("Unexpected state")
                }
                ReportStatusEvent(OPERATION, sequence, ReportStatusEventKind.STATUS_CHANGED, previous, status, TIME, ACTOR, REASON)
            })
            return ReportHistoryPage(FOLIO, status, sequence, event, sequence > 1, sequence == 1L && status == ReportStatus.REPORTED)
        }
    }
}
