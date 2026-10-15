package com.example.citizensecurity.message

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.*
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
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
class ReportMessageViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun main() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { stores.forEach { it.clear() }; Dispatchers.resetMain() }

    @Test fun constructorDoesNothingAndActivationOnlyReadsWithNoSelectionOrLocation() = runTest(dispatcher) {
        val reports = Reports()
        val contacts = Contacts()
        val model = model(reports, contacts)
        assertSame(ReportMessageState.Idle, model.state.value)
        assertEquals(0, reports.reads + contacts.reads)
        model.activate(FOLIO); model.activate(FOLIO); runCurrent()
        val choice = choice(model)
        assertNull(choice.selectedContactId)
        assertFalse(choice.includeLocation)
        model.activate(FOLIO); runCurrent()
        assertEquals(1, reports.reads)
        assertEquals(1, contacts.reads)
        assertEquals(0, reports.writes + contacts.writes)
    }

    @Test fun malformedFolioAndMissingOrMismatchedReportNeverExposeAComposition() = runTest(dispatcher) {
        val reports = Reports()
        val contacts = Contacts()
        val model = model(reports, contacts)
        model.load("sms:private-data"); runCurrent()
        assertTrue(model.state.value is ReportMessageState.Error)
        assertEquals(0, reports.reads)
        reports.value = null
        model.load(FOLIO); runCurrent()
        assertTrue(model.state.value is ReportMessageState.Error)
        assertEquals(0, contacts.reads)
        reports.value = report().copy(id = OTHER)
        model.load(FOLIO); runCurrent()
        assertTrue(model.state.value is ReportMessageState.Error)
        assertEquals(0, contacts.reads)
    }

    @Test fun selectionsRequireTheExactReadingAndAContactFromThatSnapshot() = runTest(dispatcher) {
        val model = model()
        model.load(FOLIO); runCurrent()
        val original = choice(model)
        model.selectContact(original, UUID.randomUUID().toString())
        assertSame(original, model.state.value)
        model.selectContact(original, CONTACT.id)
        val selected = choice(model)
        model.setIncludeLocation(original, true)
        assertSame(selected, model.state.value)
        model.setIncludeLocation(selected, true)
        assertTrue(choice(model).includeLocation)
        assertEquals(CONTACT.id, choice(model).selectedContactId)
    }

    @Test fun prepareRequiresSelectionAndRereadsTheActualReportWithoutWriting() = runTest(dispatcher) {
        val reports = Reports()
        val contacts = Contacts()
        val model = model(reports, contacts)
        model.load(FOLIO); runCurrent()
        model.prepare(choice(model)); runCurrent()
        assertTrue(model.state.value is ReportMessageState.Choose)
        assertEquals(1, reports.reads)
        model.selectContact(choice(model), CONTACT.id)
        reports.value = report().copy(description = "Descripción actualizada del incidente")
        model.prepare(choice(model)); runCurrent()
        val review = review(model)
        assertEquals(reports.value, review.report)
        assertTrue(review.draft.body.contains("Descripción actualizada"))
        assertFalse(review.includeLocation)
        assertFalse(review.draft.body.contains("19.4326"))
        assertEquals(2, reports.reads)
        assertEquals(2, contacts.reads)
        assertEquals(0, reports.writes + contacts.writes)
    }

    @Test fun changedOrDeletedContactRequiresSelectionAgainBeforePreparing() = runTest(dispatcher) {
        for (next in listOf(listOf(CONTACT.copy(phoneNumber = "5523456789")), emptyList())) {
            val contacts = Contacts()
            val model = model(contacts = contacts)
            chooseContact(model)
            contacts.value = snapshot(next)
            model.prepare(choice(model)); runCurrent()
            assertNull(choice(model).selectedContactId)
            assertNotNull(choice(model).message)
            assertEquals(next, choice(model).contacts.contacts)
        }
    }

    @Test fun duplicatePrepareGestureReadsOnlyOnceAndNeverOpensAnything() = runTest(dispatcher) {
        val contacts = Contacts()
        val model = model(contacts = contacts)
        chooseContact(model)
        val gate = CompletableDeferred<Unit>()
        contacts.beforeRead = { gate.await() }
        val expected = choice(model)
        model.prepare(expected); model.prepare(expected); runCurrent()
        assertTrue(model.state.value is ReportMessageState.Preparing)
        assertEquals(2, contacts.reads)
        gate.complete(Unit); runCurrent()
        assertTrue(model.state.value is ReportMessageState.Review)
    }

    @Test fun openingRequiresAnotherGestureAndLaunchesTheExactReviewedBodyOnlyOnce() = runTest(dispatcher) {
        val reports = Reports()
        val contacts = Contacts()
        val model = model(reports, contacts)
        val opened = mutableListOf<ReportMessageDraft>()
        prepare(model)
        val review = review(model)
        assertTrue(opened.isEmpty())
        model.open(review, { true }, opened::add)
        model.open(review, { true }, opened::add)
        runCurrent()
        assertSame(review.draft, opened.single())
        assertSame(review, (model.state.value as ReportMessageState.Opened).review)
        model.activate(FOLIO); model.open(review, { true }, opened::add); runCurrent()
        assertEquals(1, opened.size)
        assertEquals(3, reports.reads)
        assertEquals(3, contacts.reads)
        assertEquals(0, reports.writes + contacts.writes)
    }

    @Test fun reportChangingAfterReviewInvalidatesTheOldBodyAndRequiresAnotherPreparation() = runTest(dispatcher) {
        val reports = Reports()
        val model = model(reports)
        prepare(model)
        val old = review(model)
        reports.value = report().copy(status = ReportStatus.IN_REVIEW)
        var opens = 0
        model.open(old, { true }, { opens++ }); runCurrent()
        assertEquals(0, opens)
        assertEquals(ReportStatus.IN_REVIEW, choice(model).report.status)
        assertEquals(CONTACT.id, choice(model).selectedContactId)
        model.open(old, { true }, { opens++ }); runCurrent()
        assertEquals(0, opens)
        model.prepare(choice(model)); runCurrent()
        assertEquals(ReportStatus.IN_REVIEW, review(model).report.status)
    }

    @Test fun aContactChangeAfterReviewCannotReuseItsOldPhoneNumber() = runTest(dispatcher) {
        val contacts = Contacts()
        val model = model(contacts = contacts)
        prepare(model)
        val old = review(model)
        contacts.value = snapshot(listOf(CONTACT.copy(phoneNumber = "5523456789")))
        var opens = 0
        model.open(old, { true }, { opens++ }); runCurrent()
        assertEquals(0, opens)
        assertNull(choice(model).selectedContactId)
        assertEquals("5523456789", choice(model).contacts.contacts.single().phoneNumber)
    }

    @Test fun evenAnUnrelatedContactsRevisionRequiresFreshPreparationInsteadOfOpeningAnOldReview() = runTest(dispatcher) {
        val contacts = Contacts()
        val model = model(contacts = contacts)
        prepare(model)
        val old = review(model)
        contacts.value = snapshot(listOf(CONTACT, TrustedContact.create("Otro contacto", "5523456789")))
        var opens = 0
        model.open(old, { true }, { opens++ }); runCurrent()
        assertEquals(0, opens)
        assertEquals(CONTACT.id, choice(model).selectedContactId)
        assertNotNull(choice(model).message)
    }

    @Test fun preRotationActivityCannotLaunchAndNewGestureCanUseTheFreshReview() = runTest(dispatcher) {
        val model = model()
        prepare(model)
        var opens = 0
        model.open(review(model), { false }, { opens++ }); runCurrent()
        assertEquals(0, opens)
        assertNotNull(review(model).message)
        model.open(review(model), { true }, { opens++ }); runCurrent()
        assertEquals(1, opens)
    }

    @Test fun missingMessagingAppKeepsAReviewWithGenericErrorAndAllowsAnExplicitRetry() = runTest(dispatcher) {
        val model = model()
        prepare(model)
        val expected = review(model)
        model.open(expected, { true }, { throw IOException("C:/private_account/contact_phone") })
        runCurrent()
        assertSame(expected.draft, review(model).draft)
        assertFalse(review(model).message.orEmpty().contains("private_account"))
        var opens = 0
        model.open(review(model), { true }, { opens++ }); runCurrent()
        assertEquals(1, opens)
    }

    @Test fun revokedSessionAfterTheLastExternalGateCannotLaunchOrKeepPrivateReadyState() = runTest(dispatcher) {
        var active = true
        val model = model { check(active) }
        prepare(model)
        var opens = 0
        model.open(review(model), { active = false; true }, { opens++ }); runCurrent()
        assertEquals(0, opens)
        assertTrue(model.state.value is ReportMessageState.Error)
    }

    @Test fun lateNonCooperativeReadCannotReplaceAChangedFolioAfterCancellation() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val reports = Reports()
        val model = model(reports)
        reports.beforeRead = { withContext(NonCancellable) { gate.await() } }
        model.load(FOLIO); runCurrent()
        model.cancel()
        reports.beforeRead = {}
        reports.value = report().copy(id = OTHER)
        model.load(OTHER); runCurrent()
        assertEquals(OTHER, choice(model).report.id)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(OTHER, choice(model).report.id)
    }

    @Test fun cancellationDuringTheOpenRereadSuppressesTheExternalCallback() = runTest(dispatcher) {
        val contacts = Contacts()
        val model = model(contacts = contacts)
        prepare(model)
        val gate = CompletableDeferred<Unit>()
        contacts.beforeRead = { withContext(NonCancellable) { gate.await() } }
        var opens = 0
        model.open(review(model), { true }, { opens++ }); runCurrent()
        model.clear()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(0, opens)
        assertSame(ReportMessageState.Idle, model.state.value)
    }

    @Test fun busyObserverCanCancelBeforeTheReadOrExternalOpeningStarts() = runTest(dispatcher) {
        val reports = Reports()
        val model = model(reports)
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it is ReportMessageState.Loading) model.clear() }
        }
        model.load(FOLIO); runCurrent()
        assertEquals(0, reports.reads)
        assertSame(ReportMessageState.Idle, model.state.value)
        observer.cancel()
        prepare(model)
        var opens = 0
        val openingObserver = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it is ReportMessageState.Opening) model.clear() }
        }
        model.open(review(model), { true }, { opens++ }); runCurrent()
        assertEquals(0, opens)
        assertSame(ReportMessageState.Idle, model.state.value)
        openingObserver.cancel()
    }

    @Test fun clearingTheViewModelPreventsLateResultsAndAnyFurtherActivation() = runTest(dispatcher) {
        val contacts = Contacts()
        val model = model(contacts = contacts)
        prepare(model)
        val gate = CompletableDeferred<Unit>()
        contacts.beforeRead = { withContext(NonCancellable) { gate.await() } }
        var opens = 0
        model.open(review(model), { true }, { opens++ }); runCurrent()
        stores.single().clear()
        gate.complete(Unit); advanceUntilIdle()
        model.activate(FOLIO); runCurrent()
        assertEquals(0, opens)
        assertSame(ReportMessageState.Idle, model.state.value)
    }

    @Test fun staleOrForgedReviewCannotAuthorizeAnExternalLaunchAndEditingRequiresAnotherPreparation() = runTest(dispatcher) {
        val model = model()
        prepare(model)
        val first = review(model)
        val forged = ReportMessageState.Review(first.report, first.contacts, first.draft, first.includeLocation)
        var opens = 0
        model.open(forged, { true }, { opens++ }); runCurrent()
        assertSame(first, model.state.value)
        model.edit(first)
        assertTrue(model.state.value is ReportMessageState.Choose)
        model.open(first, { true }, { opens++ }); runCurrent()
        assertEquals(0, opens)
        model.prepare(choice(model)); runCurrent()
        model.open(review(model), { true }, { opens++ }); runCurrent()
        val opened = model.state.value as ReportMessageState.Opened
        model.edit(opened.review)
        assertTrue(model.state.value is ReportMessageState.Choose)
        assertEquals(1, opens)
    }

    @Test fun storageFailureDoesNotBecomeAnEmptyContactListOrExposeItsException() = runTest(dispatcher) {
        val contacts = Contacts()
        contacts.beforeRead = { throw IOException("C:/private_account/contact_phone") }
        val model = model(contacts = contacts)
        model.load(FOLIO); runCurrent()
        val error = model.state.value as ReportMessageState.Error
        assertFalse(error.message.contains("private_account"))
        assertEquals(0, contacts.writes)
        contacts.beforeRead = {}
        model.load(FOLIO); runCurrent()
        assertEquals(listOf(CONTACT), choice(model).contacts.contacts)
    }

    private fun model(
        reports: Reports = Reports(),
        contacts: Contacts = Contacts(),
        checkCurrent: () -> Unit = {},
    ): ReportMessageViewModel {
        val store = ViewModelStore().also(stores::add)
        return ViewModelProvider(store, ReportMessageViewModel.factory(reports, contacts,
            checkCurrent, dispatcher, ZoneId.of("America/Mexico_City")))[ReportMessageViewModel::class.java]
    }

    private suspend fun chooseContact(model: ReportMessageViewModel) {
        model.activate(FOLIO)
        // All jobs use this test's scheduler; no clock, GPS or Android calls are involved.
        dispatcher.scheduler.runCurrent()
        model.selectContact(choice(model), CONTACT.id)
    }

    private suspend fun prepare(model: ReportMessageViewModel) {
        chooseContact(model)
        model.prepare(choice(model))
        dispatcher.scheduler.runCurrent()
    }

    private fun choice(model: ReportMessageViewModel) = model.state.value as ReportMessageState.Choose
    private fun review(model: ReportMessageViewModel) = model.state.value as ReportMessageState.Review

    private class Reports : ReportRepository {
        var value: Report? = report()
        var reads = 0
        var writes = 0
        var beforeRead: suspend () -> Unit = {}
        override suspend fun findById(id: String): Report? {
            reads++
            val captured = value
            beforeRead()
            return captured
        }
        override suspend fun create(draft: NewReport): Report { writes++; error("No report writes are allowed") }
        override suspend fun list(): List<Report> = error("Composition never lists all reports")
    }

    private class Contacts : TrustedContactsRepository {
        var value = snapshot(listOf(CONTACT))
        var reads = 0
        var writes = 0
        var beforeRead: suspend () -> Unit = {}
        override suspend fun read(): TrustedContactsSnapshot { reads++; beforeRead(); return value }
        override suspend fun replace(expectedRevision: String?, contacts: List<TrustedContact>): TrustedContactsSnapshot {
            writes++
            error("No contact writes are allowed")
        }
    }

    private companion object {
        const val FOLIO = "00000000-0000-0000-0000-000000000037"
        const val OTHER = "00000000-0000-0000-0000-000000000038"
        val CONTACT = TrustedContact("00000000-0000-0000-0000-000000000039", "Persona de confianza", "+525512345678")
        fun snapshot(contacts: List<TrustedContact>) = TrustedContactsSnapshot(UUID.randomUUID().toString(), contacts)
        fun report() = Report(FOLIO, IncidentType.RISK, Priority.MEDIUM, "Incidente observado desde la calle.",
            Instant.parse("2026-10-15T12:00:00Z"), ReportLocation("Referencia privada del incidente", 19.4326, -99.1332),
            Instant.parse("2026-10-15T12:01:00Z"))
    }
}
