package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.IdempotentReportRepository
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportSubmissionJournal
import com.example.citizensecurity.domain.ReportSubmissionRequest
import com.example.citizensecurity.domain.ValidationErrors
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportSubmissionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val now = Instant.parse("2026-10-09T03:00:00.123456789Z")
    private val requestId = "1e345678-1234-4567-89ab-0123456789ab"

    @Before fun setMain() = Dispatchers.setMain(dispatcher)

    @After fun finish() {
        try { stores.forEach { it.clear() } } finally { Dispatchers.resetMain() }
    }

    @Test fun initialRecoveryReadsEmptyJournalWithoutCreatingOrQueryingAReport() = runTest(dispatcher) {
        val journal = FakeJournal()
        val repository = FakeRepository()
        val model = model(repository, journal)
        assertEquals(ReportSubmissionState.Restoring, model.state.value)
        model.save(draft())
        runCurrent()
        assertEquals(ReportSubmissionState.Idle, model.state.value)
        assertEquals(1, journal.reads)
        assertEquals(0, journal.writes.size)
        assertTrue(repository.calls.isEmpty())
        assertTrue(repository.finds.isEmpty())
    }

    @Test fun processRecoveryOfPendingRequestDoesNotInsertUntilExplicitRetry() = runTest(dispatcher) {
        val pending = request()
        val journal = FakeJournal(pending)
        val repository = FakeRepository()
        val model = model(repository, journal)
        runCurrent()
        assertEquals(ReportSubmissionState.Pending(pending), model.state.value)
        assertEquals(listOf(requestId), repository.finds)
        assertTrue(repository.calls.isEmpty())
        assertTrue(journal.writes.isEmpty())
        model.retryPending()
        runCurrent()
        assertEquals(listOf(pending.requestId to pending.draft), repository.calls)
        assertEquals(requestId, (model.state.value as ReportSubmissionState.Saved).report.id)
        assertEquals(pending, journal.value)
    }

    @Test fun recoveryFindsTheCommittedFolioWithoutAnotherInsertOrGps() = runTest(dispatcher) {
        val raw = draft().copy(description = "  ${draft().description}  ",
            location = ReportLocation("  Frente al mercado  ", -0.0, -0.0))
        val pending = request(raw)
        val saved = report(requestId, raw.copy(description = raw.description.trim(),
            location = ReportLocation("Frente al mercado", 0.0, 0.0)))
        val repository = FakeRepository().apply { rows[requestId] = saved }
        val journal = FakeJournal(pending)
        val model = model(repository, journal)
        runCurrent()
        assertEquals(ReportSubmissionState.Saved(saved), model.state.value)
        assertTrue(repository.calls.isEmpty())
        assertTrue(journal.writes.isEmpty())
        assertEquals(pending, journal.value)
    }

    @Test fun mismatchedCommittedFolioFailsClosedAndCannotSaveAnotherDraft() = runTest(dispatcher) {
        val pending = request()
        val repository = FakeRepository().apply {
            rows[requestId] = report(requestId, draft().copy(description = "Este reporte corresponde a otro incidente."))
        }
        val journal = FakeJournal(pending)
        val model = model(repository, journal)
        runCurrent()
        assertTrue(model.state.value is ReportSubmissionState.Error)
        model.save(draft())
        model.retryPending()
        runCurrent()
        assertTrue(repository.calls.isEmpty())
        assertEquals(pending, journal.value)
        assertFalse(model.startNewReport())
    }

    @Test fun unreadableJournalBlocksFreshIdentityUntilRecoverySucceeds() = runTest(dispatcher) {
        val journal = FakeJournal().apply { readFailure = IOException("corrupt") }
        val repository = FakeRepository()
        val model = model(repository, journal)
        runCurrent()
        assertTrue(model.state.value is ReportSubmissionState.Error)
        model.save(draft())
        model.retryPending()
        runCurrent()
        assertTrue(journal.writes.isEmpty())
        assertTrue(repository.calls.isEmpty())
        journal.readFailure = null
        model.recover()
        runCurrent()
        assertEquals(ReportSubmissionState.Idle, model.state.value)
        model.save(draft())
        runCurrent()
        assertTrue(model.state.value is ReportSubmissionState.Saved)
    }

    @Test fun identityAndNormalizedInputAreDurableBeforeRepositoryIsCalled() = runTest(dispatcher) {
        val journal = FakeJournal()
        val repository = FakeRepository().apply {
            onCreate = { id, draft ->
                assertEquals(id, journal.value!!.requestId)
                assertEquals(draft, journal.value!!.draft)
                assertEquals(now, journal.value!!.requestedAt)
                report(id, draft)
            }
        }
        val model = model(repository, journal)
        runCurrent()
        model.save(draft().copy(description = "  ${draft().description}  "))
        runCurrent()
        assertTrue(model.state.value is ReportSubmissionState.Saved)
        assertEquals(draft(), journal.value!!.draft)
        assertTrue(journal.clears.isEmpty())
    }

    @Test fun failureToPersistIdentityNeverCallsRepositoryAndRetryUsesSameId() = runTest(dispatcher) {
        val journal = FakeJournal().apply { writeFailure = IOException("full") }
        val repository = FakeRepository()
        val model = model(repository, journal)
        runCurrent()
        model.save(draft())
        runCurrent()
        val pending = (model.state.value as ReportSubmissionState.Error).request!!
        assertTrue(repository.calls.isEmpty())
        journal.writeFailure = null
        model.retryPending()
        runCurrent()
        assertEquals(listOf(pending.requestId to pending.draft), repository.calls)
    }

    @Test fun lostResponseAfterCommitIsRecoveredInNewModelWithoutDuplicate() = runTest(dispatcher) {
        val journal = FakeJournal()
        val repository = FakeRepository().apply {
            onCreate = { id, draft ->
                rows[id] = report(id, draft)
                throw IOException("La respuesta se perdió después del commit.")
            }
        }
        val first = model(repository, journal)
        runCurrent()
        first.save(draft())
        runCurrent()
        assertTrue(first.state.value is ReportSubmissionState.Error)
        val pending = journal.value!!
        stores.last().clear()
        val reopened = model(repository, journal)
        runCurrent()
        assertEquals(ReportSubmissionState.Saved(repository.rows.getValue(pending.requestId)), reopened.state.value)
        assertEquals(1, repository.calls.size)
        assertEquals(1, repository.rows.size)
        assertEquals(pending, journal.value)
    }

    @Test fun cancellationDuringSaveKeepsIdentityAndARecreatedModelOnlyReads() = runTest(dispatcher) {
        val journal = FakeJournal()
        val repository = FakeRepository().apply { onCreate = { _, _ -> awaitCancellation() } }
        val first = model(repository, journal)
        runCurrent()
        first.save(draft())
        runCurrent()
        val pending = journal.value!!
        stores.last().clear()
        runCurrent()
        assertEquals(ReportSubmissionState.Pending(pending), first.state.value)
        val reopened = model(repository, journal)
        runCurrent()
        assertEquals(ReportSubmissionState.Pending(pending), reopened.state.value)
        assertEquals(1, repository.calls.size)
        repository.onCreate = { id, draft -> report(id, draft).also { repository.rows[id] = it } }
        reopened.retryPending()
        runCurrent()
        assertEquals(listOf(pending.requestId, pending.requestId), repository.calls.map { it.first })
        assertEquals(1, repository.rows.size)
    }

    @Test fun cancellationAfterJournalCommitDoesNotReachRepository() = runTest(dispatcher) {
        val journal = FakeJournal().apply { cancelAfterWrite = true }
        val repository = FakeRepository()
        val first = model(repository, journal)
        runCurrent()
        first.save(draft())
        runCurrent()
        val pending = journal.value!!
        assertEquals(ReportSubmissionState.Pending(pending), first.state.value)
        assertTrue(repository.calls.isEmpty())
        journal.cancelAfterWrite = false
        first.retryPending()
        runCurrent()
        assertEquals(listOf(pending.requestId to pending.draft), repository.calls)
    }

    @Test fun pendingRequestCannotBeDiscardedOrReplacedByChangedInput() = runTest(dispatcher) {
        val pending = request()
        val journal = FakeJournal(pending)
        val repository = FakeRepository()
        val model = model(repository, journal)
        runCurrent()
        assertFalse(model.startNewReport())
        model.save(draft().copy(description = "Otro incidente que no corresponde al intento anterior."))
        runCurrent()
        assertEquals(pending, (model.state.value as ReportSubmissionState.Error).request)
        assertTrue(repository.calls.isEmpty())
        assertTrue(journal.clears.isEmpty())
        assertEquals(pending, journal.value)
    }

    @Test fun rapidRepeatedSaveClicksStartOnlyOneRepositoryOperation() = runTest(dispatcher) {
        val completion = CompletableDeferred<Report>()
        val repository = FakeRepository().apply { onCreate = { _, _ -> completion.await() } }
        val journal = FakeJournal()
        val model = model(repository, journal)
        runCurrent()
        repeat(10) { model.save(draft()) }
        runCurrent()
        assertEquals(1, repository.calls.size)
        val pending = journal.value!!
        repeat(10) { model.retryPending() }
        completion.complete(report(pending.requestId, pending.draft))
        runCurrent()
        assertEquals(1, repository.calls.size)
        assertTrue(model.state.value is ReportSubmissionState.Saved)
    }

    @Test fun aReentrantSaveWhenSavingIsEmittedCannotStartAnotherIdentity() = runTest(dispatcher) {
        val repository = FakeRepository()
        val journal = FakeJournal()
        val model = model(repository, journal)
        runCurrent()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it is ReportSubmissionState.Saving) model.save(draft()) }
        }
        model.save(draft())
        runCurrent()
        assertEquals(1, repository.calls.size)
        assertEquals(1, journal.writes.size)
    }

    @Test fun savedReceiptBlocksEveryNewSaveUntilExplicitNewReport() = runTest(dispatcher) {
        val repository = FakeRepository()
        val journal = FakeJournal()
        val model = model(repository, journal)
        runCurrent()
        model.save(draft())
        runCurrent()
        val firstId = journal.value!!.requestId
        model.save(draft())
        model.save(draft().copy(priority = Priority.HIGH))
        model.retryPending()
        model.recover()
        runCurrent()
        assertEquals(1, repository.calls.size)
        assertTrue(model.startNewReport())
        assertEquals(ReportSubmissionState.Restoring, model.state.value)
        runCurrent()
        assertEquals(ReportSubmissionState.Idle, model.state.value)
        assertNull(journal.value)
        assertEquals(listOf(firstId), journal.clears)
        model.save(draft())
        runCurrent()
        assertNotEquals(firstId, journal.value!!.requestId)
        assertEquals(2, repository.rows.size)
    }

    @Test fun failureToClearFinishedJournalKeepsSavedReceiptAndAllowsRetry() = runTest(dispatcher) {
        val repository = FakeRepository()
        val journal = FakeJournal()
        val model = model(repository, journal)
        runCurrent()
        model.save(draft())
        runCurrent()
        val saved = model.state.value
        val pending = journal.value
        journal.clearFailure = IOException("read only")
        assertTrue(model.startNewReport())
        runCurrent()
        assertEquals(saved, model.state.value)
        assertEquals(pending, journal.value)
        model.save(draft())
        runCurrent()
        assertEquals(1, repository.calls.size)
        journal.clearFailure = null
        assertTrue(model.startNewReport())
        runCurrent()
        assertEquals(ReportSubmissionState.Idle, model.state.value)
    }

    @Test fun invalidFieldsDoNotPersistIdentityOrReachRepository() = runTest(dispatcher) {
        val journal = FakeJournal()
        val repository = FakeRepository()
        val model = model(repository, journal)
        runCurrent()
        model.save(draft().copy(description = "Corta"))
        assertTrue(model.state.value is ReportSubmissionState.Invalid)
        runCurrent()
        assertTrue(journal.writes.isEmpty())
        assertTrue(repository.calls.isEmpty())
        model.save(draft())
        runCurrent()
        assertTrue(model.state.value is ReportSubmissionState.Saved)
    }

    @Test fun rejectedGpsGuardClearsUncommittedIdentityAndAllowsCorrectedPoint() = runTest(dispatcher) {
        val journal = FakeJournal()
        val errors = mapOf(ReportField.LOCATION_COORDINATES to "La ubicación GPS expiró. Vuelve a capturarla.")
        var rejectedId: String? = null
        val repository = FakeRepository().apply {
            onCreate = { id, _ ->
                rejectedId = id
                throw InvalidReportException(ValidationErrors(errors))
            }
        }
        val model = model(repository, journal)
        runCurrent()
        model.save(draft())
        runCurrent()
        assertEquals(ReportSubmissionState.Invalid(errors, draft()), model.state.value)
        assertNull(journal.value)
        assertTrue(repository.rows.isEmpty())
        repository.onCreate = { id, draft -> report(id, draft).also { repository.rows[id] = it } }
        model.save(draft().copy(location = ReportLocation("Frente al mercado", 19.433, -99.134)))
        runCurrent()
        assertTrue(model.state.value is ReportSubmissionState.Saved)
        assertNotEquals(rejectedId, journal.value!!.requestId)
        assertEquals(1, repository.rows.size)
    }

    @Test fun rejectedGpsGuardWithFailedCleanupRetainsIdentityUntilSafeRetry() = runTest(dispatcher) {
        val journal = FakeJournal().apply { clearFailure = IOException("full") }
        val repository = FakeRepository().apply {
            onCreate = { _, _ -> throw InvalidReportException(ValidationErrors(
                mapOf(ReportField.LOCATION_COORDINATES to "GPS no válido."),
            )) }
        }
        val model = model(repository, journal)
        runCurrent()
        model.save(draft())
        runCurrent()
        val pending = journal.value!!
        assertEquals(pending, (model.state.value as ReportSubmissionState.Error).request)
        model.save(draft().copy(priority = Priority.HIGH))
        runCurrent()
        assertEquals(1, repository.calls.size)
        journal.clearFailure = null
        repository.onCreate = { id, draft -> report(id, draft).also { repository.rows[id] = it } }
        model.retryPending()
        runCurrent()
        assertEquals(listOf(pending.requestId, pending.requestId), repository.calls.map { it.first })
        assertEquals(1, repository.rows.size)
        assertTrue(model.state.value is ReportSubmissionState.Saved)
    }

    @Test fun aFastRejectedRetryCarriesRecoveredFieldsAfterTheJournalWasCleared() = runTest(dispatcher) {
        val pending = request()
        val journal = FakeJournal(pending)
        val repository = FakeRepository().apply {
            onCreate = { _, _ -> throw InvalidReportException(ValidationErrors(
                mapOf(ReportField.LOCATION_COORDINATES to "Actualiza la ubicación GPS."),
            )) }
        }
        val model = model(repository, journal)
        runCurrent()
        model.retryPending()
        runCurrent()
        val rejected = model.state.value as ReportSubmissionState.Invalid
        assertEquals(pending.draft, rejected.draft)
        assertNull(journal.value)
        val editable = ReportDraftViewModel(androidx.lifecycle.SavedStateHandle())
        assertTrue(editable.restoreForCorrection(rejected.draft!!))
        assertEquals(pending.draft, (editable.review(now) as ReportDraftReview.Valid).draft)
        assertTrue(repository.rows.isEmpty())
    }

    @Test fun acknowledgedCorrectionKeepsErrorsWithoutReplacingEditsOnRecreation() = runTest(dispatcher) {
        val journal = FakeJournal(request())
        val errors = mapOf(ReportField.LOCATION_COORDINATES to "Renueva la ubicación GPS.")
        val repository = FakeRepository().apply {
            onCreate = { _, _ -> throw InvalidReportException(ValidationErrors(errors)) }
        }
        val model = model(repository, journal)
        runCurrent()
        model.retryPending()
        runCurrent()
        val rejected = model.state.value as ReportSubmissionState.Invalid
        val handle = androidx.lifecycle.SavedStateHandle()
        val editable = ReportDraftViewModel(handle)
        assertTrue(editable.restoreForCorrection(rejected.draft!!))
        assertTrue(model.acknowledgeCorrectionDraft())
        assertEquals(ReportSubmissionState.Invalid(errors, null), model.state.value)
        assertFalse(model.acknowledgeCorrectionDraft())
        val correction = "El incidente está junto a la segunda entrada del mercado."
        editable.setDescription(correction)
        editable.setPriority(Priority.HIGH)
        val recreated = ReportDraftViewModel(androidx.lifecycle.SavedStateHandle(
            handle.keys().associateWith { handle.get<Any?>(it) },
        ))
        (model.state.value as ReportSubmissionState.Invalid).draft?.let { recreated.restoreForCorrection(it) }
        assertEquals(correction, recreated.state.value.description)
        assertEquals(Priority.HIGH, recreated.state.value.priority)
        assertEquals(errors, (model.state.value as ReportSubmissionState.Invalid).errors)
        assertNull(journal.value)
        assertTrue(repository.rows.isEmpty())
    }

    @Test fun acknowledgingCorrectionCannotDiscardPendingIdentityOrReceipt() = runTest(dispatcher) {
        val pending = request()
        val journal = FakeJournal(pending)
        val repository = FakeRepository()
        val model = model(repository, journal)
        runCurrent()
        assertFalse(model.acknowledgeCorrectionDraft())
        assertEquals(ReportSubmissionState.Pending(pending), model.state.value)
        assertEquals(pending, journal.value)
        model.retryPending()
        runCurrent()
        val saved = model.state.value
        assertFalse(model.acknowledgeCorrectionDraft())
        assertEquals(saved, model.state.value)
        assertEquals(pending, journal.value)
    }

    @Test fun recoveryLookupFailureCannotLaunchRetryWriteUntilAReadSucceeds() = runTest(dispatcher) {
        val journal = FakeJournal(request())
        val repository = FakeRepository().apply { lookupFailure = IOException("locked") }
        val model = model(repository, journal)
        runCurrent()
        assertTrue(model.state.value is ReportSubmissionState.Error)
        model.retryPending()
        model.save(draft())
        runCurrent()
        assertTrue(repository.calls.isEmpty())
        repository.lookupFailure = null
        model.recover()
        runCurrent()
        assertTrue(model.state.value is ReportSubmissionState.Pending)
        model.retryPending()
        runCurrent()
        assertTrue(model.state.value is ReportSubmissionState.Saved)
    }

    @Test fun repositoryReturningAnotherFolioDoesNotUnlockNewReport() = runTest(dispatcher) {
        val journal = FakeJournal()
        val repository = FakeRepository().apply { onCreate = { _, draft -> report(requestId, draft) } }
        val model = model(repository, journal)
        runCurrent()
        model.save(draft())
        runCurrent()
        assertTrue(model.state.value is ReportSubmissionState.Error)
        assertEquals(journal.value, (model.state.value as ReportSubmissionState.Error).request)
        assertFalse(model.startNewReport())
    }

    @Test fun clearingModelBeforeSaveJobStartsNeverWritesTheJournal() = runTest(dispatcher) {
        val journal = FakeJournal()
        val repository = FakeRepository()
        val model = model(repository, journal)
        runCurrent()
        model.save(draft())
        stores.last().clear()
        runCurrent()
        assertTrue(journal.writes.isEmpty())
        assertTrue(repository.calls.isEmpty())
        model.save(draft())
        model.recover()
        runCurrent()
        assertTrue(journal.writes.isEmpty())
    }

    @Test fun recoveryAfterCancelledReadDoesNotCreateAReplacementIdentity() = runTest(dispatcher) {
        val pending = request()
        val journal = FakeJournal(pending)
        val repository = FakeRepository().apply { onLookup = { awaitCancellation() } }
        val first = model(repository, journal)
        runCurrent()
        stores.last().clear()
        runCurrent()
        assertEquals(ReportSubmissionState.Pending(pending), first.state.value)
        repository.onLookup = null
        val second = model(repository, journal)
        runCurrent()
        assertEquals(ReportSubmissionState.Pending(pending), second.state.value)
        assertTrue(repository.calls.isEmpty())
        assertTrue(journal.writes.isEmpty())
    }

    private fun model(repository: FakeRepository, journal: FakeJournal): ReportSubmissionViewModel {
        val store = ViewModelStore().also(stores::add)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReportSubmissionViewModel(repository, journal, Clock.fixed(now, ZoneOffset.UTC)) as T
        }
        return ViewModelProvider(store, factory)[ReportSubmissionViewModel::class.java]
    }

    private fun draft() = NewReport(
        IncidentType.RISK, Priority.MEDIUM, "Hay una luminaria dañada frente al mercado.",
        now.minusSeconds(60), ReportLocation("Frente al mercado", 19.4326, -99.1332),
    )
    private fun request(draft: NewReport = draft()) = ReportSubmissionRequest(requestId, draft, now)
    private fun report(id: String, draft: NewReport) = Report(
        id, draft.type, draft.priority, draft.description, draft.occurredAt, draft.location, now,
    )

    private class FakeJournal(var value: ReportSubmissionRequest? = null) : ReportSubmissionJournal {
        var reads = 0
        val writes = mutableListOf<ReportSubmissionRequest>()
        val clears = mutableListOf<String>()
        var readFailure: Exception? = null
        var writeFailure: Exception? = null
        var clearFailure: Exception? = null
        var cancelAfterWrite = false
        override suspend fun read(): ReportSubmissionRequest? {
            reads++
            readFailure?.let { throw it }
            return value
        }
        override suspend fun write(request: ReportSubmissionRequest) {
            writeFailure?.let { throw it }
            check(value == null || value == request)
            writes += request
            value = request
            if (cancelAfterWrite) throw CancellationException("cancelled after atomic write")
        }
        override suspend fun clear(requestId: String) {
            clearFailure?.let { throw it }
            check(value == null || value!!.requestId == requestId)
            clears += requestId
            value = null
        }
    }

    private inner class FakeRepository : IdempotentReportRepository {
        val rows = linkedMapOf<String, Report>()
        val calls = mutableListOf<Pair<String, NewReport>>()
        val finds = mutableListOf<String>()
        var lookupFailure: Exception? = null
        var onLookup: (suspend (String) -> Report?)? = null
        var onCreate: suspend (String, NewReport) -> Report = { id, draft ->
            rows[id] ?: report(id, draft).also { rows[id] = it }
        }
        override suspend fun create(draft: NewReport): Report = error("Use createOnce.")
        override suspend fun createOnce(requestId: String, draft: NewReport): Report {
            calls += requestId to draft
            return onCreate(requestId, draft)
        }
        override suspend fun findById(id: String): Report? {
            finds += id
            lookupFailure?.let { throw it }
            return onLookup?.invoke(id) ?: rows[id]
        }
        override suspend fun list(): List<Report> = rows.values.toList()
    }
}
