package com.example.citizensecurity.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.ReportPhoto
import java.io.IOException
import java.util.Collections
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
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Camera acquisition and retained request ownership, without Android camera/file mocks. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportCameraViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val draft = ReportDraftViewModel(SavedStateHandle())
    private val files = Files()
    private val launches = mutableListOf<String>()
    private val imports = mutableListOf<String>()

    @Before fun configureMain() = Dispatchers.setMain(dispatcher)
    @After fun closeModels() {
        try { stores.forEach { it.clear() } } finally { Dispatchers.resetMain() }
    }

    @Test fun constructingAndReceivingAnUnmatchedResultNeverCreatesOrImports() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        assertEquals(ReportCameraState.Idle, model.state.value)
        assertFalse(model.complete(true))
        assertFalse(model.complete(false))
        assertTrue(files.created.isEmpty())
        assertTrue(imports.isEmpty())
        assertFalse(draft.state.value.photoSelectionPending)
    }

    @Test fun successfulCaptureImportsOnlyItsReservedSourceAndReleasesIt() = runTest(dispatcher) {
        val model = model()
        assertTrue(prepare(model))
        assertTrue(draft.state.value.photoSelectionPending)
        runCurrent()
        assertEquals(ReportCameraState.Waiting, model.state.value)
        assertEquals(listOf(files.created.single().source), launches)
        assertTrue(imports.isEmpty())
        assertTrue(model.complete(true))
        runCurrent()
        assertEquals(listOf(files.created.single().source), imports)
        assertEquals(photo(), draft.state.value.photo)
        assertEquals(ReportCameraState.Ready, model.state.value)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun repeatedPrepareBeforeTheLazyJobStartsAcquiresAndLaunchesOnce() = runTest(dispatcher) {
        val model = model()
        assertTrue(prepare(model))
        assertFalse(prepare(model))
        runCurrent()
        assertEquals(1, files.created.size)
        assertEquals(1, launches.size)
        assertFalse(prepare(model))
        assertTrue(model.complete(false))
        runCurrent()
        assertEquals(1, files.released.size)
    }

    @Test fun aPendingGalleryTokenBlocksCameraWithoutAllocatingAFile() = runTest(dispatcher) {
        val gallery = draft.beginPhotoSelection()!!
        val model = model()
        assertFalse(prepare(model))
        runCurrent()
        assertSame(gallery, draft.currentPhotoSelection())
        assertTrue(files.created.isEmpty())
        assertTrue(launches.isEmpty())
    }

    @Test fun cameraCancellationPreservesThePreviousPhotoAndNeverImports() = runTest(dispatcher) {
        seedPrevious()
        val model = model()
        prepare(model)
        runCurrent()
        assertEquals(photo(2), draft.state.value.photo)
        assertTrue(model.complete(false))
        runCurrent()
        assertEquals(photo(2), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertTrue(imports.isEmpty())
        assertEquals(files.created, files.released)
    }

    @Test fun duplicateDeliveryDuringImportDoesNotStartOrCancelAnotherImport() = runTest(dispatcher) {
        val finish = CompletableDeferred<ReportPhoto>()
        var calls = 0
        val model = model { source -> imports += source; calls++; finish.await() }
        prepare(model)
        runCurrent()
        assertTrue(model.complete(true))
        assertFalse(model.complete(false))
        assertFalse(model.complete(true))
        runCurrent()
        assertEquals(1, calls)
        assertEquals(ReportCameraState.Importing, model.state.value)
        assertTrue(files.released.isEmpty())
        finish.complete(photo())
        runCurrent()
        assertEquals(photo(), draft.state.value.photo)
        assertEquals(1, files.released.size)
        assertFalse(model.complete(true))
    }

    @Test fun missingCameraApplicationPreservesPreviousPhotoAndReleasesTheOutput() = runTest(dispatcher) {
        seedPrevious()
        val model = model()
        assertTrue(model.prepare({ true }) { throw IOException("No camera; private source path") })
        runCurrent()
        assertEquals(photo(2), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertTrue(model.state.value is ReportCameraState.Error)
        assertFalse((model.state.value as ReportCameraState.Error).message.contains("private source"))
        assertTrue(imports.isEmpty())
        assertEquals(files.created, files.released)
    }

    @Test fun failedFileCreationDoesNotLaunchOrDiscardPreviousPhoto() = runTest(dispatcher) {
        seedPrevious()
        files.createAction = { throw IOException("private cache filename") }
        val model = model()
        prepare(model)
        runCurrent()
        assertTrue(model.state.value is ReportCameraState.Error)
        assertFalse((model.state.value as ReportCameraState.Error).message.contains("private cache"))
        assertEquals(photo(2), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertTrue(launches.isEmpty())
        assertTrue(imports.isEmpty())
        assertTrue(files.released.isEmpty())
    }

    @Test fun leavingForegroundDuringFileCreationPreventsLaunchAndReleasesIt() = runTest(dispatcher) {
        seedPrevious()
        val finish = CompletableDeferred<Unit>()
        files.createAction = { finish.await(); files.capture() }
        var canLaunch = true
        val model = model()
        assertTrue(model.prepare({ canLaunch }) { launches += it })
        runCurrent()
        canLaunch = false
        finish.complete(Unit)
        runCurrent()
        assertTrue(launches.isEmpty())
        assertEquals(photo(2), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun sessionRevocationWhileCreatingPreventsCameraLaunch() = runTest(dispatcher) {
        val finish = CompletableDeferred<Unit>()
        files.createAction = { finish.await(); files.capture() }
        var current = true
        val model = model(checkCurrent = { check(current) })
        prepare(model)
        runCurrent()
        current = false
        finish.complete(Unit)
        runCurrent()
        assertTrue(launches.isEmpty())
        assertTrue(imports.isEmpty())
        assertNull(draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun sessionRevocationBeforeTheResultPreventsItsImport() = runTest(dispatcher) {
        seedPrevious()
        var current = true
        val model = model(checkCurrent = { check(current) })
        prepare(model)
        runCurrent()
        current = false
        model.complete(true)
        runCurrent()
        assertTrue(imports.isEmpty())
        assertEquals(photo(2), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun sessionRevocationDuringImportCannotAttachItsReturnedMetadata() = runTest(dispatcher) {
        seedPrevious()
        val finish = CompletableDeferred<ReportPhoto>()
        var current = true
        val model = model(checkCurrent = { check(current) }) { source -> imports += source; finish.await() }
        prepare(model)
        runCurrent()
        model.complete(true)
        runCurrent()
        current = false
        finish.complete(photo())
        runCurrent()
        assertEquals(photo(2), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun unusableCaptureKeepsPreviousPhotoAndReleasesOnlyItsTemporarySource() = runTest(dispatcher) {
        seedPrevious()
        val model = model { throw IOException("content://sensitive/source: empty or corrupt JPEG") }
        prepare(model)
        runCurrent()
        model.complete(true)
        runCurrent()
        assertTrue(model.state.value is ReportCameraState.Error)
        assertFalse((model.state.value as ReportCameraState.Error).message.contains("sensitive"))
        assertEquals(photo(2), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun resettingDraftBeforeTheCallbackRejectsTheCapture() = runTest(dispatcher) {
        val model = model()
        prepare(model)
        runCurrent()
        draft.reset()
        model.complete(true)
        runCurrent()
        assertNull(draft.state.value.photo)
        assertTrue(imports.isEmpty())
        assertEquals(files.created, files.released)
    }

    @Test fun aNewerSelectionTokenCannotBeReplacedOrCancelledByCameraResult() = runTest(dispatcher) {
        val model = model()
        prepare(model)
        runCurrent()
        val newer = draft.beginPhotoSelection()!!
        model.complete(true)
        runCurrent()
        assertTrue(imports.isEmpty())
        assertSame(newer, draft.currentPhotoSelection())
        assertTrue(draft.state.value.photoSelectionPending)
        assertNull(draft.state.value.photo)
        assertEquals(files.created, files.released)
    }

    @Test fun resettingDuringNonCooperativeImportDiscardsItsLatePhoto() = runTest(dispatcher) {
        val finish = CompletableDeferred<ReportPhoto>()
        val model = model { withContext(NonCancellable) { finish.await() } }
        prepare(model)
        runCurrent()
        model.complete(true)
        runCurrent()
        draft.reset()
        finish.complete(photo())
        runCurrent()
        assertNull(draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun newerSelectionDuringNonCooperativeImportKeepsItsOwnPhoto() = runTest(dispatcher) {
        val finish = CompletableDeferred<ReportPhoto>()
        val model = model { withContext(NonCancellable) { finish.await() } }
        prepare(model)
        runCurrent()
        model.complete(true)
        runCurrent()
        val newer = draft.beginPhotoSelection()!!
        assertTrue(draft.applyPhotoSelection(newer, photo(2)))
        finish.complete(photo())
        runCurrent()
        assertEquals(photo(2), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun recreationWithTheSameStoreRetainsTheWaitingCaptureWithoutRecreatingIt() = runTest(dispatcher) {
        val store = ViewModelStore()
        val original = model(store)
        prepare(original)
        runCurrent()
        val recreated = model(store)
        assertSame(original, recreated)
        assertEquals(ReportCameraState.Waiting, recreated.state.value)
        assertEquals(1, files.created.size)
        assertEquals(1, launches.size)
        assertTrue(recreated.complete(true))
        runCurrent()
        assertEquals(listOf(files.created.single().source), imports)
        assertEquals(photo(), draft.state.value.photo)
    }

    @Test fun aFreshModelCannotAdoptAResultFromThePreviousProcess() = runTest(dispatcher) {
        val old = model()
        prepare(old)
        runCurrent()
        val freshDraft = ReportDraftViewModel(SavedStateHandle())
        val freshStore = ViewModelStore().also(stores::add)
        val fresh = ViewModelProvider(freshStore, ReportCameraViewModel.factory(
            freshDraft, files, { source -> imports += source; photo() }, {},
        ))[ReportCameraViewModel::class.java]
        assertFalse(fresh.complete(true))
        runCurrent()
        assertNull(freshDraft.state.value.photo)
        assertFalse(freshDraft.state.value.photoSelectionPending)
        assertTrue(imports.isEmpty())
        assertEquals(1, files.created.size)
        old.complete(false)
        runCurrent()
    }

    @Test fun clearingWaitingModelReleasesTheCaptureAndRejectsItsLateResult() = runTest(dispatcher) {
        seedPrevious()
        val store = ViewModelStore()
        val model = model(store)
        prepare(model)
        runCurrent()
        store.clear()
        runCurrent()
        awaitRelease()
        assertFalse(model.complete(true))
        assertFalse(prepare(model))
        assertTrue(imports.isEmpty())
        assertEquals(photo(2), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun clearingDuringNonCancellableCreationStillReleasesTheObtainedHandle() = runTest(dispatcher) {
        val finish = CompletableDeferred<Unit>()
        files.createAction = { withContext(NonCancellable) { finish.await(); files.capture() } }
        val store = ViewModelStore()
        val model = model(store)
        prepare(model)
        runCurrent()
        store.clear()
        finish.complete(Unit)
        advanceUntilIdle()
        awaitRelease()
        assertTrue(launches.isEmpty())
        assertTrue(imports.isEmpty())
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun clearingDuringNonCooperativeImportWaitsToReleaseUntilItStopsReading() = runTest(dispatcher) {
        seedPrevious()
        val finish = CompletableDeferred<ReportPhoto>()
        val store = ViewModelStore()
        val model = model(store) { withContext(NonCancellable) { finish.await() } }
        prepare(model)
        runCurrent()
        model.complete(true)
        runCurrent()
        store.clear()
        runCurrent()
        assertTrue(files.released.isEmpty())
        finish.complete(photo())
        advanceUntilIdle()
        awaitRelease()
        assertEquals(photo(2), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun clearingAfterResultBeforeTheImportBodyStartsStillReleasesItsCapture() = runTest(dispatcher) {
        seedPrevious()
        val store = ViewModelStore()
        val model = model(store)
        prepare(model)
        runCurrent()
        assertTrue(model.complete(true))
        store.clear()
        advanceUntilIdle()
        awaitRelease()
        assertTrue(imports.isEmpty())
        assertEquals(photo(2), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun reentrantClearWhenImportingIsPublishedReleasesWithoutStartingImport() = runTest(dispatcher) {
        val store = ViewModelStore()
        val model = model(store)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it == ReportCameraState.Importing) store.clear() }
        }
        prepare(model)
        runCurrent()
        model.complete(true)
        advanceUntilIdle()
        awaitRelease()
        assertTrue(imports.isEmpty())
        assertNull(draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
        collector.cancel()
    }

    @Test fun reentrantClearInsideTheExternalLaunchReleasesItsAlreadyGrantedOutput() = runTest(dispatcher) {
        val store = ViewModelStore()
        val model = model(store)
        assertTrue(model.prepare({ true }) { source -> launches += source; store.clear() })
        advanceUntilIdle()
        awaitRelease()
        assertEquals(1, launches.size)
        assertTrue(imports.isEmpty())
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun clearStatusDuringCreationKeepsTheOperationReservedUntilItsCallback() = runTest(dispatcher) {
        seedPrevious()
        val finish = CompletableDeferred<Unit>()
        files.createAction = { finish.await(); files.capture() }
        val model = model()
        prepare(model)
        runCurrent()
        model.clearStatus()
        assertFalse(prepare(model))
        assertTrue(draft.state.value.photoSelectionPending)
        finish.complete(Unit)
        runCurrent()
        assertEquals(1, launches.size)
        assertEquals(ReportCameraState.Waiting, model.state.value)
        assertTrue(model.complete(false))
        runCurrent()
        assertEquals(photo(2), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun clearStatusDuringNonCooperativeImportKeepsTheRequestAndSourceUntilItFinishes() = runTest(dispatcher) {
        seedPrevious()
        val finish = CompletableDeferred<ReportPhoto>()
        val model = model { withContext(NonCancellable) { finish.await() } }
        prepare(model)
        runCurrent()
        model.complete(true)
        runCurrent()
        model.clearStatus()
        runCurrent()
        assertFalse(prepare(model))
        assertTrue(files.released.isEmpty())
        finish.complete(photo())
        advanceUntilIdle()
        assertEquals(photo(), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(ReportCameraState.Ready, model.state.value)
        assertEquals(files.created, files.released)
    }

    @Test fun clearingStatusWhileCameraIsOpenCannotReplaceItsAwaitedResult() = runTest(dispatcher) {
        val model = model()
        prepare(model)
        runCurrent()
        model.clearStatus()
        assertEquals(ReportCameraState.Waiting, model.state.value)
        assertFalse(prepare(model))
        assertEquals(1, files.created.size)
        assertTrue(files.released.isEmpty())
        assertTrue(model.complete(true))
        runCurrent()
        assertEquals(listOf(files.created.single().source), imports)
        assertEquals(photo(), draft.state.value.photo)
    }

    @Test fun temporaryCleanupFailureDoesNotDetachAnAlreadyImportedPhoto() = runTest(dispatcher) {
        files.releaseFailure = IOException("private temporary path")
        val model = model()
        prepare(model)
        runCurrent()
        model.complete(true)
        runCurrent()
        assertEquals(photo(), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertTrue(model.state.value is ReportCameraState.Error)
        assertFalse((model.state.value as ReportCameraState.Error).message.contains("private"))
        assertEquals(files.created, files.released)
        assertFalse(model.complete(true))
    }

    @Test fun synchronousCameraResultCannotBeLostBetweenWaitingAndLaunch() = runTest(dispatcher) {
        val model = model()
        assertTrue(model.prepare({ true }) { source ->
            launches += source
            assertEquals(ReportCameraState.Waiting, model.state.value)
            assertTrue(model.complete(true))
        })
        runCurrent()
        assertEquals(1, files.created.size)
        assertEquals(listOf(files.created.single().source), imports)
        assertEquals(photo(), draft.state.value.photo)
        assertEquals(files.created, files.released)
    }

    @Test fun reentrantSecondGestureCannotReplaceTheReservedPreparation() = runTest(dispatcher) {
        val model = model()
        var intercepted = false
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { state ->
                if (state == ReportCameraState.Preparing && !intercepted) {
                    intercepted = true
                    assertFalse(prepare(model))
                }
            }
        }
        assertTrue(prepare(model))
        runCurrent()
        assertTrue(intercepted)
        assertEquals(1, files.created.size)
        assertEquals(1, launches.size)
        model.complete(false)
        runCurrent()
        collector.cancel()
    }

    @Test fun revocationInsideTheLaunchPredicateCannotOpenTheCamera() = runTest(dispatcher) {
        var current = true
        val model = model(checkCurrent = { check(current) })
        assertTrue(model.prepare({ current = false; true }) { launches += it })
        runCurrent()
        assertTrue(launches.isEmpty())
        assertNull(draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
    }

    @Test fun reentrantDraftResetWhenWaitingIsPublishedPreventsTheExternalLaunch() = runTest(dispatcher) {
        val model = model()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it == ReportCameraState.Waiting) draft.reset() }
        }
        prepare(model)
        runCurrent()
        assertTrue(launches.isEmpty())
        assertNull(draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(files.created, files.released)
        collector.cancel()
    }

    @Test fun anotherExplicitCaptureAfterCancellationUsesANewFileAndReplacesOnlyOnSuccess() = runTest(dispatcher) {
        seedPrevious()
        val model = model()
        prepare(model)
        runCurrent()
        model.complete(false)
        runCurrent()
        assertEquals(photo(2), draft.state.value.photo)
        assertTrue(prepare(model))
        runCurrent()
        assertEquals(2, files.created.size)
        assertNotEquals(files.created[0], files.created[1])
        assertEquals(photo(2), draft.state.value.photo)
        model.complete(true)
        runCurrent()
        assertEquals(listOf(files.created[1].source), imports)
        assertEquals(photo(), draft.state.value.photo)
        assertEquals(files.created, files.released)
    }

    @Test fun busyRemainsReservedAfterApplyingPhotoUntilTemporaryCleanupFinishes() = runTest(dispatcher) {
        val cleanupFinished = CompletableDeferred<Unit>()
        files.releaseAction = { cleanupFinished.await() }
        val model = model()
        assertFalse(model.busy.value)
        assertTrue(prepare(model))
        assertTrue(model.busy.value)
        runCurrent()
        assertTrue(model.complete(true))
        runCurrent()
        assertEquals(photo(), draft.state.value.photo)
        assertFalse(draft.state.value.photoSelectionPending)
        assertEquals(ReportCameraState.Ready, model.state.value)
        assertTrue(model.busy.value)
        assertFalse(prepare(model))
        assertEquals(1, files.created.size)
        cleanupFinished.complete(Unit)
        runCurrent()
        assertFalse(model.busy.value)
        assertTrue(prepare(model))
        runCurrent()
        assertEquals(2, files.created.size)
        assertTrue(model.complete(false))
        runCurrent()
        assertFalse(model.busy.value)
        assertEquals(photo(), draft.state.value.photo)
        assertEquals(files.created, files.released)
    }

    private fun prepare(model: ReportCameraViewModel): Boolean = model.prepare({ true }) { launches += it }

    private fun model(
        store: ViewModelStore = ViewModelStore(),
        checkCurrent: () -> Unit = {},
        importer: suspend (String) -> ReportPhoto = { source -> imports += source; photo() },
    ): ReportCameraViewModel {
        if (store !in stores) stores += store
        return ViewModelProvider(store, ReportCameraViewModel.factory(draft, files, importer, checkCurrent))[
            ReportCameraViewModel::class.java,
        ]
    }

    private fun seedPrevious() { assertTrue(draft.applyPhotoSelection(draft.beginPhotoSelection()!!, photo(2))) }

    private suspend fun awaitRelease() = withContext(Dispatchers.Default) {
        withTimeout(5_000) { files.firstRelease.await() }
    }

    private class Files : ReportCameraCaptureFiles {
        val created: MutableList<ReportCameraCapture> = Collections.synchronizedList(mutableListOf())
        val released: MutableList<ReportCameraCapture> = Collections.synchronizedList(mutableListOf())
        val firstRelease = CompletableDeferred<Unit>()
        var createAction: suspend () -> ReportCameraCapture = { capture() }
        var releaseAction: suspend () -> Unit = {}
        var releaseFailure: Exception? = null
        private var next = 0
        fun capture(): ReportCameraCapture = ReportCameraCapture("capture-${++next}", "content://own.camera/capture-$next")
            .also { created += it }
        override suspend fun create(): ReportCameraCapture = createAction()
        override suspend fun release(capture: ReportCameraCapture) {
            released += capture
            firstRelease.complete(Unit)
            releaseAction()
            releaseFailure?.let { throw it }
        }
    }

    private fun photo(number: Int = 1) = ReportPhoto(
        "1e345678-1234-4567-89ab-0123456789a$number", number.toString().repeat(64), 1234L, 640, 480,
    )
}
