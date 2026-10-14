package com.example.citizensecurity.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.ReportPhoto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** The gallery callback owns its opening, independently of a camera or a later draft. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportPhotoPickerBindingTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val draft = ReportDraftViewModel(SavedStateHandle())
    private val sources = mutableListOf<String>()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun cleanup() {
        try { stores.forEach { it.clear() } } finally { Dispatchers.resetMain() }
    }

    @Test fun reservingAnOpeningDoesNotImportAndKeepsItsOwnToken() = runTest(dispatcher) {
        val model = model()
        assertFalse(model.hasPendingPickerSelection())
        val token = model.beginPickerSelection()!!
        assertTrue(model.hasPendingPickerSelection())
        assertSame(token, draft.currentPhotoSelection())
        assertTrue(draft.state.value.photoSelectionPending)
        runCurrent()
        assertTrue(sources.isEmpty())
        assertEquals(ReportPhotoImportState.Idle, model.state.value)
    }

    @Test fun duplicateGestureCannotReplaceTheOpeningBeforeTheUiRecomposes() = runTest(dispatcher) {
        val model = model()
        val first = model.beginPickerSelection()!!
        assertNull(model.beginPickerSelection())
        assertSame(first, draft.currentPhotoSelection())
        assertTrue(model.hasPendingPickerSelection())
        assertTrue(sources.isEmpty())
    }

    @Test fun cameraOrOtherSourcePendingBlocksGalleryWithoutStealingItsToken() = runTest(dispatcher) {
        val camera = draft.beginPhotoSelection()!!
        val model = model()
        assertNull(model.beginPickerSelection())
        assertFalse(model.hasPendingPickerSelection())
        assertSame(camera, draft.currentPhotoSelection())
        assertFalse(model.completePickerSelection("content://unmatched/result"))
        runCurrent()
        assertTrue(sources.isEmpty())
        assertSame(camera, draft.currentPhotoSelection())
    }

    @Test fun successfulCallbackConsumesItsOpeningAndImportsItsSelectedSource() = runTest(dispatcher) {
        val model = model()
        model.beginPickerSelection()!!
        assertTrue(model.completePickerSelection("content://gallery/selected"))
        assertFalse(model.hasPendingPickerSelection())
        runCurrent()
        assertEquals(listOf("content://gallery/selected"), sources)
        assertEquals(photo(), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
    }

    @Test fun cancelledPickerPreservesThePriorPhotoWithoutReadingAnySource() = runTest(dispatcher) {
        draft.applyPhotoSelection(draft.beginPhotoSelection()!!, photo(2))
        val model = model()
        model.beginPickerSelection()!!
        assertTrue(model.completePickerSelection(null))
        runCurrent()
        assertFalse(model.hasPendingPickerSelection())
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(photo(2), draft.state.value.photo)
        assertTrue(sources.isEmpty())
    }

    @Test fun duplicateCallbackCannotStartAnotherImportOrCancelThePendingOne() = runTest(dispatcher) {
        val finish = CompletableDeferred<ReportPhoto>()
        val model = model { source -> sources += source; finish.await() }
        model.beginPickerSelection()!!
        assertTrue(model.completePickerSelection("content://gallery/first"))
        assertFalse(model.completePickerSelection("content://gallery/duplicate"))
        assertFalse(model.completePickerSelection(null))
        assertNull(model.beginPickerSelection())
        runCurrent()
        assertEquals(listOf("content://gallery/first"), sources)
        finish.complete(photo())
        runCurrent()
        assertEquals(photo(), draft.state.value.photo)
    }

    @Test fun abortingAHandlerFailureReleasesOnlyTheExpectedPickerOpening() = runTest(dispatcher) {
        val model = model()
        val token = model.beginPickerSelection()!!
        model.abortPickerLaunch(ReportPhotoSelectionToken())
        assertTrue(model.hasPendingPickerSelection())
        assertSame(token, draft.currentPhotoSelection())
        model.abortPickerLaunch(token)
        assertFalse(model.hasPendingPickerSelection())
        assertFalse(draft.state.value.photoSelectionPending)
        assertFalse(model.completePickerSelection("content://late/result"))
        assertNotNull(model.beginPickerSelection())
        assertTrue(sources.isEmpty())
    }

    @Test fun staleCallbackCannotAdoptOrCancelANewerCameraToken() = runTest(dispatcher) {
        val model = model()
        model.beginPickerSelection()!!
        val camera = draft.beginPhotoSelection()!!
        assertFalse(model.completePickerSelection("content://old/gallery"))
        runCurrent()
        assertSame(camera, draft.currentPhotoSelection())
        assertTrue(draft.state.value.photoSelectionPending)
        assertFalse(model.hasPendingPickerSelection())
        assertTrue(sources.isEmpty())
    }

    @Test fun staleCancellationDoesNotCancelAnotherSourcesOpening() = runTest(dispatcher) {
        val model = model()
        model.beginPickerSelection()!!
        val newer = draft.beginPhotoSelection()!!
        assertFalse(model.completePickerSelection(null))
        assertSame(newer, draft.currentPhotoSelection())
        assertTrue(draft.state.value.photoSelectionPending)
        assertTrue(sources.isEmpty())
    }

    @Test fun aFreshModelDoesNotAdoptACallbackEvenWhenItsDraftHasAPendingToken() = runTest(dispatcher) {
        val old = model()
        old.beginPickerSelection()!!
        val newDraft = ReportDraftViewModel(SavedStateHandle())
        val newToken = newDraft.beginPhotoSelection()!!
        val fresh = model(draftModel = newDraft)
        assertFalse(fresh.completePickerSelection("content://restored/old-result"))
        runCurrent()
        assertSame(newToken, newDraft.currentPhotoSelection())
        assertNull(newDraft.state.value.photo)
        assertTrue(sources.isEmpty())
    }

    @Test fun sameViewModelStoreRetainsItsOpeningAcrossActivityRecreation() = runTest(dispatcher) {
        val store = ViewModelStore()
        val first = model(store)
        val token = first.beginPickerSelection()!!
        val recreated = model(store)
        assertSame(first, recreated)
        assertSame(token, draft.currentPhotoSelection())
        assertTrue(recreated.hasPendingPickerSelection())
        assertTrue(recreated.completePickerSelection("content://selected/after-rotation"))
        runCurrent()
        assertEquals(listOf("content://selected/after-rotation"), sources)
        assertEquals(photo(), draft.state.value.photo)
    }

    @Test fun clearingStatusCannotReleaseTheOutstandingExternalCallbackToANewPicker() = runTest(dispatcher) {
        val model = model()
        model.beginPickerSelection()!!
        model.clearStatus()
        assertTrue(model.hasPendingPickerSelection())
        assertNull(model.beginPickerSelection())
        model.completePickerSelection(null)
        assertFalse(model.hasPendingPickerSelection())
        assertNotNull(model.beginPickerSelection())
        assertTrue(sources.isEmpty())
    }

    @Test fun clearingGalleryStatusCannotCancelACameraOrAnotherSourcesToken() = runTest(dispatcher) {
        val model = model()
        val camera = draft.beginPhotoSelection()!!
        model.clearStatus()
        assertSame(camera, draft.currentPhotoSelection())
        assertTrue(draft.state.value.photoSelectionPending)
        assertFalse(model.hasPendingPickerSelection())
        assertTrue(sources.isEmpty())
    }

    @Test fun callbackToAClearedModelNeverImportsOrAdoptsANewToken() = runTest(dispatcher) {
        val store = ViewModelStore()
        val model = model(store)
        model.beginPickerSelection()!!
        store.clear()
        val newer = draft.beginPhotoSelection()!!
        assertFalse(model.completePickerSelection("content://late/result"))
        runCurrent()
        assertSame(newer, draft.currentPhotoSelection())
        assertTrue(sources.isEmpty())
        assertNull(model.beginPickerSelection())
    }

    private fun model(
        store: ViewModelStore = ViewModelStore(),
        draftModel: ReportDraftViewModel = draft,
        importer: suspend (String) -> ReportPhoto = { source -> sources += source; photo() },
    ): ReportPhotoImportViewModel {
        if (store !in stores) stores += store
        return ViewModelProvider(store, ReportPhotoImportViewModel.factory(draftModel, importer))[
            ReportPhotoImportViewModel::class.java,
        ]
    }

    private fun photo(number: Int = 1) = ReportPhoto(
        "1e345678-1234-4567-89ab-0123456789a$number", number.toString().repeat(64), 1234L, 640, 480,
    )
}
