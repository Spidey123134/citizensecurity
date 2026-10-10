package com.example.citizensecurity.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NearbyIncidentDecision
import com.example.citizensecurity.domain.NearbyIncidentPolicy
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDraftCorrectionTest {
    private val now = Instant.parse("2026-10-09T03:00:00.123456789Z")

    @Test fun recoveredRequestRestoresAllFieldsForCorrectionIncludingItsPoint() {
        val model = ReportDraftViewModel(SavedStateHandle())
        val request = requestedDraft()
        assertTrue(model.restoreForCorrection(request))
        assertEquals(request, (model.review(now) as ReportDraftReview.Valid).draft)
        assertFalse(model.state.value.locationSelectionPending)
        assertTrue(model.state.value.inputErrors.isEmpty())
        assertEquals(request.location, model.beginLocationSelection()!!.originalLocation)
    }

    @Test fun recoveredFieldsCanBeRestoredByAnotherModelFromOnlySavedPrimitives() {
        val handle = SavedStateHandle()
        val model = ReportDraftViewModel(handle)
        assertTrue(model.restoreForCorrection(requestedDraft()))
        val entries = handle.keys().associateWith { handle.get<Any?>(it) }
        assertTrue(entries.values.all { value ->
            value == null || value is String || value is Number || value is Boolean
        })
        assertFalse(entries.keys.any { it.contains("gps", ignoreCase = true) || it.contains("permission", ignoreCase = true) })
        val reopened = ReportDraftViewModel(SavedStateHandle(entries))
        assertEquals(requestedDraft(), (reopened.review(now) as ReportDraftReview.Valid).draft)
    }

    @Test fun restoringCorrectionRetiresOldSelectionAndClearsRejectedInputProblems() {
        val model = ReportDraftViewModel(SavedStateHandle())
        model.setDescription("x".repeat(1_001))
        val retired = model.beginLocationSelection()!!
        assertTrue(model.state.value.inputErrors.isNotEmpty())
        assertTrue(model.restoreForCorrection(requestedDraft()))
        val expected = model.state.value
        assertFalse(model.isCurrentLocationSelection(retired.token))
        assertFalse(model.applyLocationSelection(retired.token, NearbyLocationConfirmation.Confirmed(
            ReportLocation("Otro lugar", 19.434, -99.135), 12.0,
        )))
        assertEquals(expected, model.state.value)
        assertTrue(expected.inputErrors.isEmpty())
    }

    @Test fun unusableRecoveredFieldsCannotPartiallyOverwriteExistingDraftOrSelection() {
        val model = ReportDraftViewModel(SavedStateHandle())
        assertTrue(model.restoreForCorrection(requestedDraft()))
        val pending = model.beginLocationSelection()!!
        val expected = model.state.value
        val invalid = listOf(
            requestedDraft().copy(description = "x".repeat(1_001)),
            requestedDraft().copy(location = ReportLocation("x".repeat(201), 19.4326, -99.1332)),
            requestedDraft().copy(location = ReportLocation("Referencia", 19.4326, null)),
            requestedDraft().copy(location = ReportLocation("Referencia", Double.NaN, -99.1332)),
            requestedDraft().copy(location = ReportLocation("Referencia", 91.0, -99.1332)),
        )
        invalid.forEach { candidate ->
            assertFalse(model.restoreForCorrection(candidate))
            assertEquals(expected, model.state.value)
            assertTrue(model.isCurrentLocationSelection(pending.token))
        }
    }

    @Test fun aClosedModelCannotRestoreFieldsOrChangeTheSavedSnapshot() {
        val handle = SavedStateHandle()
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ReportDraftViewModel(handle) as T
        }
        val model = ViewModelProvider(store, factory)[ReportDraftViewModel::class.java]
        assertTrue(model.restoreForCorrection(requestedDraft()))
        store.clear()
        val expected = model.state.value
        val snapshot = handle.keys().associateWith { handle.get<Any?>(it) }
        assertFalse(model.restoreForCorrection(requestedDraft().copy(priority = Priority.HIGH)))
        assertEquals(expected, model.state.value)
        assertEquals(snapshot, handle.keys().associateWith { handle.get<Any?>(it) })
    }

    @Test fun restoredPointNeverSubstitutesFreshGpsEvidenceForTheNearbyPolicy() {
        val model = ReportDraftViewModel(SavedStateHandle())
        assertTrue(model.restoreForCorrection(requestedDraft()))
        assertTrue(model.review(now) is ReportDraftReview.Valid)
        assertTrue(NearbyIncidentPolicy().evaluate(
            model.state.value.location, null, 1_000_000_000L,
        ) is NearbyIncidentDecision.Rejected)
    }

    private fun requestedDraft() = NewReport(
        IncidentType.RISK, Priority.MEDIUM, "Hay un árbol caído 🌳 frente al mercado.",
        now.minusSeconds(60), ReportLocation("Frente al mercado, junto al café", 19.4326, -99.1332),
    )
}
