package com.example.citizensecurity.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportPhoto
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ReportDraftPhotoViewModelTest {
    @Test fun photoIsOptionalAndReviewKeepsItsExactMetadata() {
        val model = ready()
        assertNull((model.review(NOW) as ReportDraftReview.Valid).draft.photo)
        val photo = photo()
        assertTrue(model.applyPhotoSelection(model.beginPhotoSelection()!!, photo))
        assertEquals(photo, (model.review(NOW) as ReportDraftReview.Valid).draft.photo)
    }

    @Test fun metadataRestoresFromPrimitivesWithoutUriBitmapOrSelectionToken() {
        val handle = SavedStateHandle()
        val model = ready(handle)
        val photo = photo()
        model.applyPhotoSelection(model.beginPhotoSelection()!!, photo)
        val restored = ReportDraftViewModel(copy(handle))
        assertEquals(photo, restored.state.value.photo)
        assertEquals(photo, (restored.review(NOW) as ReportDraftReview.Valid).draft.photo)
        assertTrue(handle.keys().map { handle.get<Any?>(it) }.all {
            it == null || it is String || it is Long || it is Int || it is Double
        })
        assertFalse(handle.keys().any { it.contains("uri", ignoreCase = true) || it.contains("token", ignoreCase = true) })
    }

    @Test fun oldSnapshotWithNoPhotoMetadataStillReviewsNormally() {
        val handle = SavedStateHandle()
        ready(handle)
        handle.keys().filter { it.startsWith("reportDraft.photo") }.forEach { handle.remove<Any?>(it) }
        val restored = ReportDraftViewModel(copy(handle))
        assertNull(restored.state.value.photo)
        assertTrue(restored.review(NOW) is ReportDraftReview.Valid)
    }

    @Test fun malformedOrPartialSavedMetadataBlocksReviewUntilPhotoIsExplicitlyRemoved() {
        val corruptions = listOf(
            "reportDraft.photoId" to "../../foreign.jpg",
            "reportDraft.photoId" to "",
            "reportDraft.photoHash" to "A".repeat(64),
            "reportDraft.photoHash" to "0".repeat(63),
            "reportDraft.photoBytes" to 0L,
            "reportDraft.photoBytes" to (ReportPhoto.MAX_BYTES + 1),
            "reportDraft.photoBytes" to "3456",
            "reportDraft.photoWidth" to 2049,
            "reportDraft.photoHeight" to null,
        )
        corruptions.forEach { (key, value) ->
            val handle = SavedStateHandle()
            val model = ready(handle)
            model.applyPhotoSelection(model.beginPhotoSelection()!!, photo())
            handle[key] = value
            val restored = ReportDraftViewModel(copy(handle))
            assertNull(restored.state.value.photo)
            assertTrue(restored.state.value.inputErrors.containsKey(ReportDraftField.PHOTO))
            assertTrue((restored.review(NOW) as ReportDraftReview.Invalid).errors.containsKey(ReportDraftField.PHOTO))
            restored.removePhoto()
            assertTrue(restored.review(NOW) is ReportDraftReview.Valid)
        }
    }

    @Test fun cancelledReplacementRetainsThePriorPhotoAndUnblocksReview() {
        val model = ready()
        model.applyPhotoSelection(model.beginPhotoSelection()!!, photo())
        val token = model.beginPhotoSelection()!!
        assertEquals(photo(), model.state.value.photo)
        assertTrue(model.state.value.photoSelectionPending)
        assertTrue((model.review(NOW) as ReportDraftReview.Invalid).errors.containsKey(ReportDraftField.PHOTO))
        assertTrue(model.cancelPhotoSelection(token))
        assertEquals(photo(), model.state.value.photo)
        assertTrue(model.review(NOW) is ReportDraftReview.Valid)
        assertFalse(model.applyPhotoSelection(token, photo(2)))
    }

    @Test fun newerSelectionInvalidatesEveryOlderCallback() {
        val model = ready()
        val older = model.beginPhotoSelection()!!
        val newer = model.beginPhotoSelection()!!
        assertFalse(model.applyPhotoSelection(older, photo()))
        assertFalse(model.cancelPhotoSelection(older))
        assertSame(newer, model.currentPhotoSelection())
        assertTrue(model.applyPhotoSelection(newer, photo(2)))
        assertEquals(photo(2), model.state.value.photo)
        assertFalse(model.applyPhotoSelection(newer, photo()))
    }

    @Test fun tokenFromAnotherDraftCannotReplaceOrCancelThisPhoto() {
        val first = ready()
        val second = ready()
        val foreign = first.beginPhotoSelection()!!
        val current = second.beginPhotoSelection()!!
        assertFalse(second.applyPhotoSelection(foreign, photo()))
        assertFalse(second.cancelPhotoSelection(foreign))
        assertTrue(second.applyPhotoSelection(current, photo(2)))
    }

    @Test fun resetAndExplicitRemovalInvalidatePendingCallbacksWithoutDeletingAnyFile() {
        val model = ready()
        model.applyPhotoSelection(model.beginPhotoSelection()!!, photo())
        val first = model.beginPhotoSelection()!!
        model.removePhoto()
        assertNull(model.state.value.photo)
        assertFalse(model.applyPhotoSelection(first, photo(2)))
        val second = model.beginPhotoSelection()!!
        model.reset()
        assertEquals(ReportDraftState(), model.state.value)
        assertFalse(model.applyPhotoSelection(second, photo(2)))
    }

    @Test fun correctionRestoresTheRejectedPhotoAndInvalidatesPendingImport() {
        val model = ready()
        model.applyPhotoSelection(model.beginPhotoSelection()!!, photo())
        val correction = (model.review(NOW) as ReportDraftReview.Valid).draft.copy(photo = photo(2))
        val token = model.beginPhotoSelection()!!
        assertTrue(model.restoreForCorrection(correction))
        assertEquals(photo(2), model.state.value.photo)
        assertFalse(model.state.value.photoSelectionPending)
        assertFalse(model.applyPhotoSelection(token, photo()))
        assertEquals(correction, (model.review(NOW) as ReportDraftReview.Valid).draft)
    }

    @Test fun processRestorationKeepsPriorMetadataAndRequiresANewSelectionAuthority() {
        val handle = SavedStateHandle()
        val old = ready(handle)
        old.applyPhotoSelection(old.beginPhotoSelection()!!, photo())
        val interrupted = old.beginPhotoSelection()!!
        val restored = ReportDraftViewModel(copy(handle))
        assertNull(restored.currentPhotoSelection())
        assertFalse(restored.state.value.photoSelectionPending)
        assertEquals(photo(), restored.state.value.photo)
        assertFalse(restored.applyPhotoSelection(interrupted, photo(2)))
        assertTrue(restored.review(NOW) is ReportDraftReview.Valid)
    }

    @Test fun clearingTheDraftInvalidatesPhotoCallbacksAndFutureSelections() {
        val store = ViewModelStore()
        val model = ViewModelProvider(store, viewModelFactory {
            initializer { ReportDraftViewModel(SavedStateHandle()) }
        })[ReportDraftViewModel::class.java]
        val token = model.beginPhotoSelection()!!
        store.clear()
        assertFalse(model.applyPhotoSelection(token, photo()))
        assertFalse(model.cancelPhotoSelection(token))
        assertNull(model.beginPhotoSelection())
        assertFalse(model.removePhoto())
        assertFalse(model.state.value.photoSelectionPending)
    }

    private fun ready(handle: SavedStateHandle = SavedStateHandle()) = ReportDraftViewModel(handle).apply {
        setType(IncidentType.RISK)
        setPriority(Priority.MEDIUM)
        setDescription("Una luminaria dejó de funcionar frente al parque.")
        setOccurredAt(NOW.minusSeconds(60))
        setLocationReference("Entrada norte del parque")
    }

    private fun copy(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })

    private fun photo(number: Int = 1) = ReportPhoto(
        "1e345678-1234-4567-89ab-0123456789a$number", number.toString().repeat(64), 1234L, 640, 480,
    )

    private companion object { val NOW = Instant.parse("2026-10-09T18:00:00Z") }
}
