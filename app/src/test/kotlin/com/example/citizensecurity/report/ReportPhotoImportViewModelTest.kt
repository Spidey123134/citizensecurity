package com.example.citizensecurity.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import java.io.IOException
import java.time.Instant
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
class ReportPhotoImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val draft = ReportDraftViewModel(SavedStateHandle())

    @Before fun configureMain() = Dispatchers.setMain(dispatcher)
    @After fun closeModels() {
        try { stores.forEach { it.clear() } } finally { Dispatchers.resetMain() }
    }

    @Test fun explicitImportAppliesMetadataOnceAndReleasesPendingSelection() = runTest(dispatcher) {
        val sources = mutableListOf<String>()
        val model = model { sources += it; photo() }
        val token = draft.beginPhotoSelection()!!
        assertTrue(model.importPhoto(token, "content://selected/photo"))
        assertEquals(ReportPhotoImportState.Importing(token), model.state.value)
        runCurrent()
        assertEquals(listOf("content://selected/photo"), sources)
        assertEquals(ReportPhotoImportState.Imported(photo()), model.state.value)
        assertEquals(photo(), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertFalse(model.importPhoto(token, "content://selected/photo"))
    }

    @Test fun duplicateDeliveryForTheSamePendingTokenDoesNotStartAnotherImporter() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportPhoto>()
        var calls = 0
        val model = model { ++calls; completion.await() }
        val token = draft.beginPhotoSelection()!!
        model.importPhoto(token, "first")
        assertFalse(model.importPhoto(token, "duplicate"))
        runCurrent()
        assertEquals(1, calls)
        completion.complete(photo())
        advanceUntilIdle()
        assertEquals(photo(), draft.state.value.photo)
    }

    @Test fun failedReplacementKeepsPriorPhotoAndDoesNotExposeExceptionDetails() = runTest(dispatcher) {
        draft.applyPhotoSelection(draft.beginPhotoSelection()!!, photo())
        val model = model { throw IOException("Private path, source URI, or personal filename") }
        val token = draft.beginPhotoSelection()!!
        model.importPhoto(token, "replacement")
        runCurrent()
        assertEquals(photo(), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(
            ReportPhotoImportState.Error("No se pudo preparar la fotografía. Vuelve a elegir una imagen válida."),
            model.state.value,
        )
    }

    @Test fun pickerCancellationPreservesPriorPhotoAndNeverStartsImport() = runTest(dispatcher) {
        draft.applyPhotoSelection(draft.beginPhotoSelection()!!, photo())
        var calls = 0
        val model = model { ++calls; photo(2) }
        val token = draft.beginPhotoSelection()!!
        assertTrue(model.cancelSelection(token))
        assertFalse(model.importPhoto(token, "late picker delivery"))
        runCurrent()
        assertEquals(0, calls)
        assertEquals(photo(), draft.state.value.photo)
        assertEquals(ReportPhotoImportState.Idle, model.state.value)
    }

    @Test fun newerSelectionOwnsThePhotoEvenIfPreviousImporterIgnoresCancellation() = runTest(dispatcher) {
        val first = CompletableDeferred<ReportPhoto>()
        val model = model { source ->
            if (source == "old") withContext(NonCancellable) { first.await() } else photo(2)
        }
        val older = draft.beginPhotoSelection()!!
        model.importPhoto(older, "old")
        runCurrent()
        val newer = draft.beginPhotoSelection()!!
        model.importPhoto(newer, "new")
        runCurrent()
        assertEquals(photo(2), draft.state.value.photo)
        first.complete(photo())
        advanceUntilIdle()
        assertEquals(photo(2), draft.state.value.photo)
        assertEquals(ReportPhotoImportState.Imported(photo(2)), model.state.value)
    }

    @Test fun resetInvalidatesAResultThatFinishesAfterStartingAnotherReport() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportPhoto>()
        val model = model { completion.await() }
        model.importPhoto(draft.beginPhotoSelection()!!, "selected")
        runCurrent()
        draft.reset()
        completion.complete(photo())
        advanceUntilIdle()
        assertNull(draft.state.value.photo)
        assertEquals(ReportPhotoImportState.Idle, model.state.value)
    }

    @Test fun correctionRestoresItsOwnPhotoAndDiscardsTheEarlierPendingImport() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportPhoto>()
        val model = model { completion.await() }
        model.importPhoto(draft.beginPhotoSelection()!!, "old")
        runCurrent()
        val correction = NewReport(
            IncidentType.RISK, Priority.MEDIUM, "Luminaria dañada frente al parque",
            Instant.parse("2026-10-09T18:00:00Z"), ReportLocation("Entrada norte"), photo(2),
        )
        assertTrue(draft.restoreForCorrection(correction))
        completion.complete(photo())
        advanceUntilIdle()
        assertEquals(photo(2), draft.state.value.photo)
        assertEquals(ReportPhotoImportState.Idle, model.state.value)
    }

    @Test fun detachInvalidatesPendingReplacementWithoutWaitingForItsProducer() = runTest(dispatcher) {
        draft.applyPhotoSelection(draft.beginPhotoSelection()!!, photo())
        val completion = CompletableDeferred<ReportPhoto>()
        val model = model { withContext(NonCancellable) { completion.await() } }
        model.importPhoto(draft.beginPhotoSelection()!!, "replacement")
        runCurrent()
        draft.removePhoto()
        model.clearStatus()
        completion.complete(photo(2))
        advanceUntilIdle()
        assertNull(draft.state.value.photo)
        assertEquals(ReportPhotoImportState.Idle, model.state.value)
    }

    @Test fun clearingBeforeImportBeginsPreventsFileReadsAndReleasesSelection() = runTest(dispatcher) {
        var calls = 0
        val store = ViewModelStore()
        val model = model(store) { ++calls; photo() }
        val token = draft.beginPhotoSelection()!!
        model.importPhoto(token, "selected")
        store.clear()
        runCurrent()
        assertEquals(0, calls)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(ReportPhotoImportState.Idle, model.state.value)
        assertFalse(model.importPhoto(draft.beginPhotoSelection()!!, "after close"))
    }

    @Test fun clearingCancelsAPendingJobAndCannotPublishItsLatePhoto() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportPhoto>()
        val store = ViewModelStore()
        val model = model(store) { completion.await() }
        model.importPhoto(draft.beginPhotoSelection()!!, "selected")
        runCurrent()
        store.clear()
        completion.complete(photo())
        advanceUntilIdle()
        assertNull(draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
    }

    @Test fun reentrantSelectionDuringImportingCannotStartTheSupersededLazyJob() = runTest(dispatcher) {
        val sources = mutableListOf<String>()
        val model = model { sources += it; photo(2) }
        var refreshed = false
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { state ->
                if (state is ReportPhotoImportState.Importing && !refreshed) {
                    refreshed = true
                    model.importPhoto(draft.beginPhotoSelection()!!, "new")
                }
            }
        }
        model.importPhoto(draft.beginPhotoSelection()!!, "old")
        runCurrent()
        assertEquals(listOf("new"), sources)
        assertEquals(photo(2), draft.state.value.photo)
        collector.cancel()
    }

    @Test fun missingOrStaleSourceCannotReplaceThePreviousPhoto() = runTest(dispatcher) {
        draft.applyPhotoSelection(draft.beginPhotoSelection()!!, photo())
        var calls = 0
        val model = model { ++calls; photo(2) }
        val old = draft.beginPhotoSelection()!!
        val current = draft.beginPhotoSelection()!!
        assertFalse(model.importPhoto(old, "stale"))
        model.importPhoto(current, "  ")
        runCurrent()
        assertEquals(0, calls)
        assertEquals(photo(), draft.state.value.photo)
        assertTrue(model.state.value is ReportPhotoImportState.Error)
    }

    private fun model(
        store: ViewModelStore = ViewModelStore(),
        importer: suspend (String) -> ReportPhoto,
    ): ReportPhotoImportViewModel {
        stores += store
        return ViewModelProvider(store, ReportPhotoImportViewModel.factory(draft, importer))[ReportPhotoImportViewModel::class.java]
    }

    private fun photo(number: Int = 1) = ReportPhoto(
        "1e345678-1234-4567-89ab-0123456789a$number", number.toString().repeat(64), 1234L, 640, 480,
    )
}
