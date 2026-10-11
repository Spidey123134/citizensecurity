package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportDraftCheckpoint
import com.example.citizensecurity.domain.ReportDraftCheckpointJournal
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.ReportStatus
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ReportDraftCheckpointViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val now = Instant.parse("2026-10-10T03:04:05.123456789Z")
    private val checkpointId = "1e345678-1234-4567-89ab-0123456789ab"
    private val reportId = "2e345678-1234-4567-89ab-0123456789ab"

    @Before fun setMain() = Dispatchers.setMain(dispatcher)

    @After fun finish() {
        try { stores.forEach { it.clear() } } finally { Dispatchers.resetMain() }
    }

    @Test fun initialReadDoesNotWriteRestoreOrMarkAndBlocksSavingUntilLoaded() = runTest(dispatcher) {
        val journal = FakeJournal(checkpoint())
        val model = model(journal)
        assertTrue(model.state.value.busy)
        assertFalse(model.state.value.loaded)
        assertFalse(model.save(draft()))
        assertFalse(model.markSubmittedIfMatching(report()))
        runCurrent()
        assertEquals(checkpoint(), model.state.value.checkpoint)
        assertTrue(model.state.value.loaded)
        assertFalse(model.state.value.busy)
        assertEquals(1, journal.reads)
        assertTrue(journal.writes.isEmpty())
        assertTrue(journal.marks.isEmpty())
    }

    @Test fun emptyJournalIsLoadedWithoutCreatingAnIdentity() = runTest(dispatcher) {
        val journal = FakeJournal()
        val model = model(journal)
        runCurrent()
        assertEquals(ReportDraftCheckpointState(loaded = true), model.state.value)
        assertTrue(journal.writes.isEmpty())
        assertTrue(journal.marks.isEmpty())
    }

    @Test fun successfulSavePreservesPhotoAndDatePrecisionWithCanonicalRevision() = runTest(dispatcher) {
        val journal = FakeJournal()
        val model = model(journal)
        runCurrent()
        val input = draft().copy(photo = photo())
        assertTrue(model.save(input))
        assertTrue(model.state.value.busy)
        assertNull(model.state.value.checkpoint)
        runCurrent()
        val saved = model.state.value.checkpoint!!
        assertEquals(UUID.fromString(saved.id).toString(), saved.id)
        assertEquals(input, saved.draft)
        assertEquals(now, saved.savedAt)
        assertNull(saved.submittedFolio)
        assertNull(journal.writes.single().second)
        assertEquals(saved, journal.value)
        assertTrue(model.state.value.loaded)
        assertFalse(model.state.value.failure)
        assertTrue(model.state.value.message!!.contains("Todavía no es un reporte"))
    }

    @Test fun replacementUsesTheLastReadRevisionAndKeepsItVisibleWhileBusy() = runTest(dispatcher) {
        val first = checkpoint()
        val journal = FakeJournal(first)
        val model = model(journal)
        runCurrent()
        val edited = draft().copy(priority = Priority.HIGH, photo = photo())
        assertTrue(model.save(edited))
        assertEquals(first, model.state.value.checkpoint)
        assertFalse(model.save(draft()))
        assertFalse(model.reload())
        runCurrent()
        val replacement = journal.value!!
        assertNotEquals(first.id, replacement.id)
        assertEquals(first.id, journal.writes.single().second)
        assertEquals(edited, replacement.draft)
        assertEquals(replacement, model.state.value.checkpoint)
    }

    @Test fun explicitSaveNormalizesTextAndZeroWithoutChangingPhotoMetadata() = runTest(dispatcher) {
        val journal = FakeJournal()
        val model = model(journal)
        runCurrent()
        val input = draft().copy(
            description = "  ${draft().description}  ",
            location = ReportLocation("  Frente al mercado  ", -0.0, -0.0),
            photo = photo(),
        )
        assertTrue(model.save(input))
        runCurrent()
        val saved = journal.value!!.draft
        assertEquals(draft().description, saved.description)
        assertEquals(ReportLocation("Frente al mercado", 0.0, 0.0), saved.location)
        assertEquals(photo(), saved.photo)
        assertEquals(input.occurredAt, saved.occurredAt)
    }

    @Test fun invalidDraftDoesNotWriteOrLoseThePreviousCheckpoint() = runTest(dispatcher) {
        val first = checkpoint()
        val journal = FakeJournal(first)
        val model = model(journal)
        runCurrent()
        assertFalse(model.save(draft().copy(description = "Corta")))
        assertEquals(first, model.state.value.checkpoint)
        assertTrue(model.state.value.loaded)
        assertTrue(model.state.value.failure)
        assertFalse(model.state.value.busy)
        assertTrue(journal.writes.isEmpty())
        assertTrue(model.save(draft()))
        runCurrent()
        assertFalse(model.state.value.failure)
        assertEquals(1, journal.writes.size)
    }

    @Test fun referenceOnlyDraftCanBeSavedWithoutAReportPointOrGps() = runTest(dispatcher) {
        val journal = FakeJournal()
        val model = model(journal)
        runCurrent()
        val input = draft().copy(location = ReportLocation("Frente al mercado"))
        assertTrue(model.save(input))
        runCurrent()
        assertEquals(input, journal.value!!.draft)
        assertTrue(journal.marks.isEmpty())
    }

    @Test fun failedReadBlocksEveryWriteUntilExplicitReloadSucceeds() = runTest(dispatcher) {
        val journal = FakeJournal(checkpoint()).apply { readFailure = IOException("corrupt") }
        val model = model(journal)
        runCurrent()
        assertFalse(model.state.value.loaded)
        assertTrue(model.state.value.failure)
        assertNull(model.state.value.checkpoint)
        assertFalse(model.save(draft()))
        assertFalse(model.markSubmittedIfMatching(report()))
        assertTrue(journal.writes.isEmpty())
        assertTrue(journal.marks.isEmpty())
        journal.readFailure = null
        assertTrue(model.reload())
        runCurrent()
        assertEquals(checkpoint(), model.state.value.checkpoint)
        assertTrue(model.state.value.loaded)
        assertFalse(model.state.value.failure)
    }

    @Test fun reloadFailureRetainsTheLastCheckpointButDisablesStaleReplacement() = runTest(dispatcher) {
        val first = checkpoint()
        val journal = FakeJournal(first)
        val model = model(journal)
        runCurrent()
        journal.readFailure = IOException("locked")
        assertTrue(model.reload())
        assertEquals(first, model.state.value.checkpoint)
        assertFalse(model.state.value.loaded)
        runCurrent()
        assertEquals(first, model.state.value.checkpoint)
        assertFalse(model.state.value.loaded)
        assertFalse(model.save(draft()))
        assertTrue(journal.writes.isEmpty())
    }

    @Test fun anotherWritersRevisionCannotBeOverwrittenFromStaleState() = runTest(dispatcher) {
        val first = checkpoint()
        val journal = FakeJournal(first)
        val model = model(journal)
        runCurrent()
        val external = checkpoint(id = reportId, input = draft().copy(priority = Priority.HIGH))
        journal.value = external
        assertTrue(model.save(draft().copy(type = IncidentType.THEFT)))
        runCurrent()
        assertEquals(external, journal.value)
        assertEquals(first, model.state.value.checkpoint)
        assertFalse(model.state.value.loaded)
        assertTrue(model.state.value.failure)
        assertFalse(model.save(draft()))
        assertTrue(model.reload())
        runCurrent()
        assertEquals(external, model.state.value.checkpoint)
        assertTrue(model.save(draft()))
        runCurrent()
        assertEquals(external.id, journal.writes.last().second)
    }

    @Test fun responseLostAfterSaveNeedsAReadInsteadOfAnAutomaticRewrite() = runTest(dispatcher) {
        val first = checkpoint()
        val journal = FakeJournal(first).apply { writeFailureAfterCommit = IOException("response lost") }
        val model = model(journal)
        runCurrent()
        assertTrue(model.save(draft().copy(photo = photo())))
        runCurrent()
        val actuallyCommitted = journal.value!!
        assertNotEquals(first.id, actuallyCommitted.id)
        assertEquals(first, model.state.value.checkpoint)
        assertFalse(model.state.value.loaded)
        assertFalse(model.save(draft()))
        assertEquals(1, journal.writes.size)
        journal.writeFailureAfterCommit = null
        assertTrue(model.reload())
        runCurrent()
        assertEquals(actuallyCommitted, model.state.value.checkpoint)
        assertEquals(1, journal.writes.size)
    }

    @Test fun matchingConfirmedReportMarksFolioAfterTheStoreAcknowledgesIt() = runTest(dispatcher) {
        val first = checkpoint(input = draft().copy(photo = photo()))
        val gate = CompletableDeferred<Unit>()
        val journal = FakeJournal(first).apply { beforeMark = { gate.await() } }
        val model = model(journal)
        runCurrent()
        assertNotEquals(first.id, reportId)
        assertTrue(model.markSubmittedIfMatching(report(input = first.draft)))
        runCurrent()
        assertEquals(first, model.state.value.checkpoint)
        assertTrue(model.state.value.busy)
        assertNull(journal.value!!.submittedFolio)
        assertFalse(model.markSubmittedIfMatching(report(input = first.draft)))
        gate.complete(Unit)
        runCurrent()
        assertEquals(reportId, model.state.value.checkpoint!!.submittedFolio)
        assertEquals(listOf(first.id to reportId), journal.marks)
        assertTrue(journal.writes.isEmpty())
        assertFalse(model.state.value.failure)
    }

    @Test fun matchingIgnoresCheckpointIdentityAndNormalizesLocationAndDescription() = runTest(dispatcher) {
        val raw = draft().copy(
            description = "  ${draft().description}  ",
            location = ReportLocation("  Frente al mercado  ", -0.0, -0.0),
        )
        val journal = FakeJournal(checkpoint(input = raw))
        val model = model(journal)
        runCurrent()
        val committed = raw.copy(description = raw.description.trim(),
            location = ReportLocation("Frente al mercado", 0.0, 0.0))
        assertTrue(model.markSubmittedIfMatching(report(input = committed)))
        runCurrent()
        assertEquals(reportId, journal.value!!.submittedFolio)
        assertEquals(raw, journal.value!!.draft)
    }

    @Test fun everyDifferentBodyFieldPreventsLinkingAConfirmedReport() = runTest(dispatcher) {
        val input = draft().copy(photo = photo())
        val journal = FakeJournal(checkpoint(input = input))
        val model = model(journal)
        runCurrent()
        val originalState = model.state.value
        val different = listOf(
            input.copy(type = IncidentType.FIRE),
            input.copy(priority = Priority.HIGH),
            input.copy(description = "El incidente ocurrió en otra entrada del mercado."),
            input.copy(occurredAt = input.occurredAt.plusNanos(1)),
            input.copy(location = input.location.copy(reference = "Segunda entrada del mercado")),
            input.copy(location = input.location.copy(latitude = 19.433)),
            input.copy(location = input.location.copy(longitude = -99.134)),
            input.copy(photo = photo(2)),
            input.copy(photo = null),
        )
        different.forEach { assertFalse(model.markSubmittedIfMatching(report(input = it))) }
        runCurrent()
        assertEquals(originalState, model.state.value)
        assertTrue(journal.marks.isEmpty())
        assertTrue(journal.writes.isEmpty())
    }

    @Test fun administrativeReportStatusDoesNotChangeTheImmutableBodyMatch() = runTest(dispatcher) {
        val journal = FakeJournal(checkpoint())
        val model = model(journal)
        runCurrent()
        assertTrue(model.markSubmittedIfMatching(report().copy(status = ReportStatus.ATTENDED)))
        runCurrent()
        assertEquals(reportId, journal.value!!.submittedFolio)
    }

    @Test fun alreadyLinkedCheckpointCannotBeLinkedAgainOrToAnotherFolio() = runTest(dispatcher) {
        val linked = checkpoint().copy(submittedFolio = reportId)
        val journal = FakeJournal(linked)
        val model = model(journal)
        runCurrent()
        assertFalse(model.markSubmittedIfMatching(report()))
        assertFalse(model.markSubmittedIfMatching(report(id = checkpointId)))
        runCurrent()
        assertEquals(linked, model.state.value.checkpoint)
        assertTrue(journal.marks.isEmpty())
        assertFalse(model.state.value.failure)
    }

    @Test fun failedMarkRetainsCheckpointAndExplainsTheAlreadySavedReport() = runTest(dispatcher) {
        val first = checkpoint()
        val journal = FakeJournal(first).apply { markFailure = IOException("read only") }
        val model = model(journal)
        runCurrent()
        assertTrue(model.markSubmittedIfMatching(report()))
        runCurrent()
        assertEquals(first, journal.value)
        assertEquals(first, model.state.value.checkpoint)
        assertFalse(model.state.value.loaded)
        assertTrue(model.state.value.failure)
        assertTrue(model.state.value.message!!.startsWith("El reporte está guardado"))
        assertFalse(model.markSubmittedIfMatching(report()))
        journal.markFailure = null
        assertTrue(model.reload())
        runCurrent()
        assertTrue(model.markSubmittedIfMatching(report()))
        runCurrent()
        assertEquals(reportId, model.state.value.checkpoint!!.submittedFolio)
    }

    @Test fun lostMarkAcknowledgementRecoversLinkedFolioByReadingOnly() = runTest(dispatcher) {
        val first = checkpoint()
        val journal = FakeJournal(first).apply { markFailureAfterCommit = IOException("response lost") }
        val model = model(journal)
        runCurrent()
        assertTrue(model.markSubmittedIfMatching(report()))
        runCurrent()
        assertEquals(reportId, journal.value!!.submittedFolio)
        assertNull(model.state.value.checkpoint!!.submittedFolio)
        assertFalse(model.state.value.loaded)
        assertFalse(model.markSubmittedIfMatching(report()))
        assertTrue(model.reload())
        runCurrent()
        assertEquals(reportId, model.state.value.checkpoint!!.submittedFolio)
        assertFalse(model.markSubmittedIfMatching(report()))
        assertEquals(1, journal.marks.size)
    }

    @Test fun staleCheckpointCannotMarkAnExternalWritersReplacement() = runTest(dispatcher) {
        val first = checkpoint()
        val journal = FakeJournal(first)
        val model = model(journal)
        runCurrent()
        val external = checkpoint(id = reportId, input = draft().copy(photo = photo()))
        journal.value = external
        assertTrue(model.markSubmittedIfMatching(report()))
        runCurrent()
        assertEquals(external, journal.value)
        assertEquals(first, model.state.value.checkpoint)
        assertTrue(model.state.value.failure)
        assertFalse(model.state.value.loaded)
    }

    @Test fun staleUnmarkedRevisionCannotReplaceTheSameRevisionLinkedByAnotherWriter() = runTest(dispatcher) {
        val first = checkpoint()
        val journal = FakeJournal(first)
        val model = model(journal)
        runCurrent()
        val externallyLinked = first.copy(submittedFolio = reportId)
        journal.value = externallyLinked
        assertTrue(model.save(draft().copy(photo = photo())))
        runCurrent()
        assertEquals(externallyLinked, journal.value)
        assertEquals(first, model.state.value.checkpoint)
        assertFalse(model.state.value.loaded)
        assertTrue(model.state.value.failure)
        assertEquals(listOf<String?>(null), journal.expectedSubmittedFolios)
        assertFalse(model.save(draft()))
    }

    @Test fun explicitReplacementAfterReadingALinkedRevisionIncludesItsConfirmedFolioInCas() = runTest(dispatcher) {
        val linked = checkpoint().copy(submittedFolio = reportId)
        val journal = FakeJournal(linked)
        val model = model(journal)
        runCurrent()
        assertTrue(model.save(draft().copy(priority = Priority.HIGH)))
        runCurrent()
        val replacement = journal.value!!
        assertNotEquals(linked.id, replacement.id)
        assertNull(replacement.submittedFolio)
        assertEquals(Priority.HIGH, replacement.draft.priority)
        assertEquals(listOf(reportId), journal.expectedSubmittedFolios)
        assertEquals(replacement, model.state.value.checkpoint)
        assertTrue(model.state.value.loaded)
        assertFalse(model.state.value.failure)
    }

    @Test fun cancelledSaveBeforeTheJobStartsDoesNotWriteAnything() = runTest(dispatcher) {
        val journal = FakeJournal(checkpoint())
        val model = model(journal)
        runCurrent()
        assertTrue(model.save(draft()))
        stores.last().clear()
        runCurrent()
        assertTrue(journal.writes.isEmpty())
        assertEquals(checkpoint(), model.state.value.checkpoint)
        assertFalse(model.state.value.loaded)
        assertFalse(model.state.value.busy)
        assertFalse(model.save(draft()))
        assertFalse(model.reload())
    }

    @Test fun cancelledSaveAfterCommitRemainsUnconfirmedUntilANewModelReadsIt() = runTest(dispatcher) {
        val journal = FakeJournal(checkpoint()).apply {
            writeFailureAfterCommit = CancellationException("cancelled after atomic write")
        }
        val first = model(journal)
        runCurrent()
        assertTrue(first.save(draft().copy(photo = photo())))
        runCurrent()
        val persisted = journal.value!!
        assertNotEquals(checkpointId, persisted.id)
        assertEquals(checkpoint(), first.state.value.checkpoint)
        assertFalse(first.state.value.loaded)
        val second = model(journal)
        runCurrent()
        assertEquals(persisted, second.state.value.checkpoint)
        assertEquals(1, journal.writes.size)
        assertTrue(journal.marks.isEmpty())
    }

    @Test fun aLateReadAfterOwnerCancellationCannotReplaceTheLastKnownCheckpoint() = runTest(dispatcher) {
        val first = checkpoint()
        val gate = CompletableDeferred<Unit>()
        val journal = FakeJournal(first)
        val model = model(journal)
        runCurrent()
        val replacement = checkpoint(id = reportId, input = draft().copy(photo = photo()))
        journal.beforeRead = { withContext(NonCancellable) { gate.await() } }
        journal.value = replacement
        assertTrue(model.reload())
        runCurrent()
        stores.last().clear()
        gate.complete(Unit)
        runCurrent()
        assertEquals(first, model.state.value.checkpoint)
        assertFalse(model.state.value.loaded)
        assertFalse(model.state.value.busy)
        assertTrue(model.state.value.failure)
    }

    @Test fun repeatedAndReentrantGesturesCannotReserveMultipleWrites() = runTest(dispatcher) {
        val journal = FakeJournal()
        val model = model(journal)
        runCurrent()
        val attempts = mutableListOf<Boolean>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect {
                if (it.busy) attempts += model.save(draft().copy(priority = Priority.HIGH))
            }
        }
        assertTrue(model.save(draft()))
        assertFalse(model.save(draft()))
        runCurrent()
        assertEquals(listOf(false), attempts)
        assertEquals(1, journal.writes.size)
        assertEquals(Priority.MEDIUM, journal.value!!.draft.priority)
        collector.cancel()
    }

    @Test fun cancelledMarkNeverFabricatesALinkInTheRetainedCheckpoint() = runTest(dispatcher) {
        val journal = FakeJournal(checkpoint()).apply {
            markFailureAfterCommit = CancellationException("cancelled after mark")
        }
        val model = model(journal)
        runCurrent()
        assertTrue(model.markSubmittedIfMatching(report()))
        runCurrent()
        assertNull(model.state.value.checkpoint!!.submittedFolio)
        assertEquals(reportId, journal.value!!.submittedFolio)
        assertFalse(model.state.value.loaded)
        assertFalse(model.state.value.busy)
        assertTrue(model.state.value.failure)
    }

    @Test fun lostSaveAcknowledgementRetainsTheAttemptedRevisionAndItsPreferredIdentity() = runTest(dispatcher) {
        val first = checkpoint()
        val journal = FakeJournal(first).apply { writeFailureAfterCommit = IOException("response lost") }
        val model = model(journal)
        runCurrent()
        val input = draft().copy(photo = photo())
        assertTrue(model.save(input))
        val reserved = model.state.value.uncertainWrite!!
        assertEquals(input, reserved.draft)
        assertEquals(reserved.id, model.preferredRequestId(input))
        runCurrent()
        assertEquals(reserved, journal.value)
        assertEquals(first, model.state.value.checkpoint)
        assertEquals(reserved, model.state.value.uncertainWrite)
        assertFalse(model.state.value.loaded)
        assertEquals(reserved.id, model.preferredRequestId(input))
        assertNull(model.preferredRequestId(draft()))
        assertTrue(model.reload())
        runCurrent()
        assertEquals(reserved, model.state.value.checkpoint)
        assertNull(model.state.value.uncertainWrite)
        assertEquals(reserved.id, model.preferredRequestId(input))
    }

    @Test fun cancelledCheckpointSaveKeepsTheCandidateUntilASuccessfulReadResolvesIt() = runTest(dispatcher) {
        val journal = FakeJournal().apply { writeFailureAfterCommit = CancellationException("cancelled") }
        val model = model(journal)
        runCurrent()
        assertTrue(model.save(draft()))
        val candidate = model.state.value.uncertainWrite!!
        runCurrent()
        assertEquals(candidate, model.state.value.uncertainWrite)
        assertFalse(model.state.value.loaded)
        assertEquals(candidate.id, model.preferredRequestId(draft()))
        assertTrue(model.reload())
        runCurrent()
        assertEquals(candidate, model.state.value.checkpoint)
        assertNull(model.state.value.uncertainWrite)
    }

    @Test fun aFailedReadCannotDiscardAnUncertainWriteOrClaimItWasAbsent() = runTest(dispatcher) {
        val journal = FakeJournal().apply { writeFailureAfterCommit = IOException("unknown") }
        val model = model(journal)
        runCurrent()
        assertTrue(model.save(draft()))
        runCurrent()
        val candidate = model.state.value.uncertainWrite!!
        journal.readFailure = IOException("unreadable")
        assertTrue(model.reload())
        runCurrent()
        assertEquals(candidate, model.state.value.uncertainWrite)
        assertFalse(model.state.value.loaded)
        assertEquals(candidate.id, model.preferredRequestId(draft()))
    }

    @Test fun preferredIdentityUsesOnlyMatchingNormalizedCompleteFieldsAndConfirmedFolio() = runTest(dispatcher) {
        val input = draft().copy(photo = photo())
        val journal = FakeJournal(checkpoint(input = input).copy(submittedFolio = reportId))
        val model = model(journal)
        assertNull(model.preferredRequestId(input))
        runCurrent()
        assertEquals(reportId, model.preferredRequestId(input.copy(
            description = "  ${input.description}  ",
            location = input.location.copy(reference = "  ${input.location.reference}  "),
        )))
        assertNull(model.preferredRequestId(input.copy(photo = photo(2))))
        assertNull(model.preferredRequestId(input.copy(occurredAt = input.occurredAt.plusNanos(1))))
        assertNull(model.preferredRequestId(input.copy(priority = Priority.HIGH)))
        assertNull(model.preferredRequestId(input.copy(location = input.location.copy(latitude = 19.433))))
    }

    @Test fun initialLoadingNeverProducesARestoreEventUntilTheExplicitFreshRead() = runTest(dispatcher) {
        val stored = checkpoint(input = draft().copy(photo = photo()))
        val journal = FakeJournal(stored)
        val model = model(journal)
        runCurrent()
        assertNull(model.state.value.restoreReady)
        assertTrue(model.requestRestore(stored.id))
        assertNull(model.state.value.restoreReady)
        runCurrent()
        assertEquals(stored, model.state.value.restoreReady)
        assertEquals(stored, model.state.value.checkpoint)
        assertEquals(2, journal.reads)
        assertTrue(journal.writes.isEmpty())
        assertTrue(journal.marks.isEmpty())
    }

    @Test fun restoringARevisionMarkedAfterTheDialogWasOpenedOnlyOffersItsFolio() = runTest(dispatcher) {
        val stored = checkpoint()
        val journal = FakeJournal(stored)
        val model = model(journal)
        runCurrent()
        journal.value = stored.copy(submittedFolio = reportId)
        assertTrue(model.requestRestore(stored.id))
        runCurrent()
        assertNull(model.state.value.restoreReady)
        assertEquals(reportId, model.state.value.checkpoint!!.submittedFolio)
        assertTrue(model.state.value.loaded)
        assertFalse(model.state.value.failure)
        assertTrue(model.state.value.message!!.contains("Abre su folio"))
        assertTrue(journal.writes.isEmpty())
    }

    @Test fun restoringAnOutdatedDialogRefreshesItsNewRevisionWithoutReplacingTheForm() = runTest(dispatcher) {
        val first = checkpoint()
        val replacement = checkpoint(id = reportId, input = draft().copy(photo = photo()))
        val journal = FakeJournal(first)
        val model = model(journal)
        runCurrent()
        journal.value = replacement
        assertTrue(model.requestRestore(first.id))
        runCurrent()
        assertEquals(replacement, model.state.value.checkpoint)
        assertNull(model.state.value.restoreReady)
        assertFalse(model.state.value.failure)
        assertTrue(model.state.value.message!!.contains("cambió"))
        assertTrue(model.requestRestore(replacement.id))
        runCurrent()
        assertEquals(replacement, model.state.value.restoreReady)
        assertTrue(journal.writes.isEmpty())
    }

    @Test fun missingOrUnreadableFreshRestoreNeverEmitsTheOldCopy() = runTest(dispatcher) {
        val first = checkpoint()
        val journal = FakeJournal(first)
        val model = model(journal)
        runCurrent()
        journal.readFailure = IOException("locked")
        assertTrue(model.requestRestore(first.id))
        runCurrent()
        assertEquals(first, model.state.value.checkpoint)
        assertNull(model.state.value.restoreReady)
        assertFalse(model.state.value.loaded)
        assertTrue(model.state.value.failure)
        journal.readFailure = null
        journal.value = null
        assertTrue(model.requestRestore(first.id))
        runCurrent()
        assertNull(model.state.value.checkpoint)
        assertNull(model.state.value.restoreReady)
        assertTrue(model.state.value.loaded)
        assertFalse(model.state.value.failure)
    }

    @Test fun consumedRestoreEventCannotReplaceCorrectionsAfterSavedStateRecreation() = runTest(dispatcher) {
        val stored = checkpoint(input = draft().copy(photo = photo()))
        val model = model(FakeJournal(stored))
        runCurrent()
        assertTrue(model.requestRestore(stored.id))
        runCurrent()
        val ready = model.state.value.restoreReady!!
        val handle = SavedStateHandle()
        val editable = ReportDraftViewModel(handle)
        assertTrue(editable.restoreForCorrection(ready.draft))
        assertFalse(model.acknowledgeRestore(reportId))
        assertEquals(ready, model.state.value.restoreReady)
        assertTrue(model.acknowledgeRestore(ready.id))
        assertFalse(model.acknowledgeRestore(ready.id))
        editable.setDescription("La luminaria dañada está frente a la segunda entrada del mercado.")
        editable.setPriority(Priority.HIGH)
        val recreated = ReportDraftViewModel(SavedStateHandle(
            handle.keys().associateWith { handle.get<Any?>(it) },
        ))
        model.state.value.restoreReady?.let { recreated.restoreForCorrection(it.draft) }
        assertEquals(editable.state.value.description, recreated.state.value.description)
        assertEquals(Priority.HIGH, recreated.state.value.priority)
        assertEquals(photo(), recreated.state.value.photo)
        assertNull(model.state.value.restoreReady)
    }

    @Test fun restoreGestureIsReservedAgainstReentryAndNoncanonicalIdsNeverReadTheJournal() = runTest(dispatcher) {
        val journal = FakeJournal(checkpoint())
        val model = model(journal)
        runCurrent()
        assertFalse(model.requestRestore("../../foreign"))
        assertEquals(1, journal.reads)
        val attempts = mutableListOf<Boolean>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it.busy) attempts += model.requestRestore(checkpointId) }
        }
        assertTrue(model.requestRestore(checkpointId))
        assertFalse(model.requestRestore(checkpointId))
        runCurrent()
        assertEquals(listOf(false), attempts)
        assertEquals(2, journal.reads)
        assertEquals(checkpoint(), model.state.value.restoreReady)
        collector.cancel()
    }

    @Test fun aCancelledLateRestoreCannotDeliverFieldsToADestroyedOwner() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val journal = FakeJournal(checkpoint())
        val model = model(journal)
        runCurrent()
        journal.beforeRead = { withContext(NonCancellable) { gate.await() } }
        assertTrue(model.requestRestore(checkpointId))
        runCurrent()
        stores.last().clear()
        gate.complete(Unit)
        runCurrent()
        assertNull(model.state.value.restoreReady)
        assertFalse(model.state.value.loaded)
        assertTrue(model.state.value.failure)
    }

    @Test fun savingTheSameNormalizedBodyPreservesTheRevisionDateAndOriginalSnapshot() = runTest(dispatcher) {
        val raw = draft().copy(
            description = "  ${draft().description}  ",
            location = ReportLocation("  Frente al mercado  ", -0.0, -0.0),
            photo = photo(),
        )
        val original = checkpoint(input = raw)
        val journal = FakeJournal(original)
        val model = model(journal)
        runCurrent()
        val normalized = raw.copy(description = raw.description.trim(),
            location = ReportLocation("Frente al mercado", 0.0, 0.0))
        assertTrue(model.save(normalized))
        assertEquals(original, model.state.value.uncertainWrite)
        runCurrent()
        assertEquals(original, journal.value)
        assertEquals(original, model.state.value.checkpoint)
        assertEquals(original to original.id, journal.writes.single())
        assertEquals(original.id, model.preferredRequestId(normalized))
        assertTrue(model.save(normalized.copy(description = "  ${normalized.description}  ")))
        runCurrent()
        assertEquals(original, journal.value)
        assertTrue(journal.writes.all { it.first == original && it.second == original.id })
    }

    @Test fun resavingTheSameLinkedBodyCannotRemoveItsSubmittedFolio() = runTest(dispatcher) {
        val linked = checkpoint(input = draft().copy(photo = photo())).copy(submittedFolio = reportId)
        val journal = FakeJournal(linked)
        val model = model(journal)
        runCurrent()
        assertTrue(model.save(linked.draft.copy(description = "  ${linked.draft.description}  ")))
        runCurrent()
        assertEquals(linked, journal.value)
        assertEquals(linked, model.state.value.checkpoint)
        assertEquals(listOf(reportId), journal.expectedSubmittedFolios)
        assertEquals(reportId, model.preferredRequestId(linked.draft))
        assertTrue(model.state.value.message!!.contains("abrir su folio"))
    }

    @Test fun unchangedSaveStillRejectsAnExternalMarkInsteadOfClaimingTheCopyIsUnreported() = runTest(dispatcher) {
        val original = checkpoint()
        val journal = FakeJournal(original)
        val model = model(journal)
        runCurrent()
        val externallyLinked = original.copy(submittedFolio = reportId)
        journal.value = externallyLinked
        assertTrue(model.save(original.draft))
        runCurrent()
        assertEquals(externallyLinked, journal.value)
        assertEquals(original, model.state.value.checkpoint)
        assertEquals(original, model.state.value.uncertainWrite)
        assertFalse(model.state.value.loaded)
        assertTrue(model.state.value.failure)
        assertTrue(model.reload())
        runCurrent()
        assertEquals(externallyLinked, model.state.value.checkpoint)
        assertNull(model.state.value.uncertainWrite)
        assertEquals(reportId, model.preferredRequestId(original.draft))
    }

    @Test fun twoRetainedScreensResavingTheSameBodyShareTheReportIdentity() = runTest(dispatcher) {
        val original = checkpoint(input = draft().copy(photo = photo()))
        val journal = FakeJournal(original)
        val first = model(journal)
        val second = model(journal)
        runCurrent()
        assertTrue(first.save(original.draft))
        runCurrent()
        assertEquals(original.id, journal.value!!.id)
        assertEquals(first.preferredRequestId(original.draft), second.preferredRequestId(original.draft))
        assertTrue(second.save(original.draft))
        runCurrent()
        assertEquals(original, journal.value)
        assertEquals(original.id, first.preferredRequestId(original.draft))
        assertEquals(original.id, second.preferredRequestId(original.draft))
        assertTrue(first.markSubmittedIfMatching(report(id = original.id, input = original.draft)))
        runCurrent()
        assertEquals(original.id, journal.value!!.submittedFolio)
        assertTrue(second.save(original.draft))
        runCurrent()
        assertTrue(second.state.value.failure)
        assertEquals(original.id, journal.value!!.id)
        assertEquals(original.id, journal.value!!.submittedFolio)
    }

    private fun model(journal: FakeJournal): ReportDraftCheckpointViewModel {
        val store = ViewModelStore().also(stores::add)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ReportDraftCheckpointViewModel(journal, Clock.fixed(now, ZoneOffset.UTC)) as T
        }
        return ViewModelProvider(store, factory)[ReportDraftCheckpointViewModel::class.java]
    }

    private fun draft() = NewReport(
        IncidentType.RISK,
        Priority.MEDIUM,
        "Hay una luminaria dañada frente al mercado.",
        now.minusSeconds(60),
        ReportLocation("Frente al mercado", 19.4326, -99.1332),
    )

    private fun checkpoint(id: String = checkpointId, input: NewReport = draft()) =
        ReportDraftCheckpoint(id, input, now.minusSeconds(30))

    private fun report(id: String = reportId, input: NewReport = draft()) = Report(
        id, input.type, input.priority, input.description, input.occurredAt,
        input.location, now, photo = input.photo,
    )

    private fun photo(number: Int = 1) = ReportPhoto(
        "3e345678-1234-4567-89ab-0123456789a$number", number.toString().repeat(64), 1234L, 640, 480,
    )

    private class FakeJournal(var value: ReportDraftCheckpoint? = null) : ReportDraftCheckpointJournal {
        var reads = 0
        val writes = mutableListOf<Pair<ReportDraftCheckpoint, String?>>()
        val expectedSubmittedFolios = mutableListOf<String?>()
        val marks = mutableListOf<Pair<String, String>>()
        var readFailure: Exception? = null
        var writeFailureAfterCommit: Exception? = null
        var markFailure: Exception? = null
        var markFailureAfterCommit: Exception? = null
        var beforeRead: (suspend () -> Unit)? = null
        var beforeMark: (suspend () -> Unit)? = null

        override suspend fun read(): ReportDraftCheckpoint? {
            reads++
            beforeRead?.invoke()
            readFailure?.let { throw it }
            return value
        }

        override suspend fun write(
            checkpoint: ReportDraftCheckpoint,
            expectedId: String?,
            expectedSubmittedFolio: String?,
        ) {
            writes += checkpoint to expectedId
            expectedSubmittedFolios += expectedSubmittedFolio
            check(value?.id == expectedId) { "Checkpoint replaced by another writer." }
            check(value?.submittedFolio == expectedSubmittedFolio) { "Checkpoint linked by another writer." }
            value = checkpoint
            writeFailureAfterCommit?.let { throw it }
        }

        override suspend fun markSubmitted(id: String, folio: String) {
            marks += id to folio
            beforeMark?.invoke()
            markFailure?.let { throw it }
            val previous = value ?: error("Missing checkpoint.")
            check(previous.id == id) { "Checkpoint replaced by another writer." }
            check(previous.submittedFolio == null || previous.submittedFolio == folio)
            value = previous.copy(submittedFolio = folio)
            markFailureAfterCommit?.let { throw it }
        }
    }
}
