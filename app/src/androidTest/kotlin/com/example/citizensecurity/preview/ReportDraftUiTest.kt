package com.example.citizensecurity.preview

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import java.security.MessageDigest
import org.junit.rules.ExternalResource
import com.example.citizensecurity.maps.IncidentLocationState
import com.example.citizensecurity.report.ReportDraftField
import com.example.citizensecurity.report.ReportDraftReview
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Corre solo en el AVD aislado de fase12; no instala sobre el producto del teléfono del usuario. */
@RunWith(AndroidJUnit4::class)
class ReportDraftUiTest {
    private var originalDatabases = emptyMap<String, String>()
    @get:Rule(order = 0) val preservedData = object : ExternalResource() {
        override fun before() { originalDatabases = databaseSnapshot() }
        override fun after() { assertDatabaseUnchanged() }
    }
    @get:Rule(order = 1) val ui = createAndroidComposeRule<ReportDraftActivity>()

    private fun databaseSnapshot(): Map<String, String> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.getDatabasePath("reports.db").parentFile!!
        return directory.listFiles().orEmpty().filter { it.isFile }.associate {
            it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { b -> "%02x".format(b) }
        }
    }

    private fun assertDatabaseUnchanged() = assertEquals(originalDatabases, databaseSnapshot())

    private fun capture(name: String) {
        ui.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        File(ui.activity.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
    }

    @Test fun reviewShowsMissingFieldsAndNeverCreatesDatabase() {
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithText("Selecciona el tipo de incidente.").assertExists()
        ui.onNodeWithText("Selecciona la prioridad.").assertExists()
        ui.runOnIdle {
            assertTrue(ui.activity.draftModel.review(Instant.now()) is ReportDraftReview.Invalid)
            assertDatabaseUnchanged()
        }
    }

    private fun completeReferenceDraft() {
        ui.onNodeWithTag("type_RISK").performScrollTo().performClick()
        ui.onNodeWithTag("priority_MEDIUM").performScrollTo().performClick()
        ui.onNodeWithTag("draft_description").performScrollTo().performTextInput("Una luminaria dejó de funcionar frente al parque.")
        ui.onNodeWithTag("draft_reference").performScrollTo().performTextInput("Frente al parque, entrada norte")
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
    }

    @Test fun reviewDisplaysEnteredFieldsWithoutSavingOrCapturingGps() {
        completeReferenceDraft()
        ui.onNodeWithTag("review_type").performScrollTo().assertTextContains("Riesgo", substring = true)
        ui.onNodeWithTag("review_priority").performScrollTo().assertTextContains("Media", substring = true)
        ui.onNodeWithTag("review_description").performScrollTo().assertTextContains("Una luminaria dejó de funcionar frente al parque.", substring = true)
        ui.onNodeWithTag("review_occurred_at").performScrollTo().assertExists()
        ui.onNodeWithTag("review_reference").performScrollTo().assertTextContains("Frente al parque, entrada norte", substring = true)
        capture("release052-revision.png")
        ui.runOnIdle {
            assertNull(ui.activity.draftModel.state.value.location.latitude)
            assertFalse(ui.activity.locationModel.state.value is IncidentLocationState.Capturing)
            assertDatabaseUnchanged()
        }
    }

    @Test fun reviewCanBeCorrectedAndShowsTheUpdatedSnapshot() {
        completeReferenceDraft()
        ui.onNodeWithTag("edit_review").performScrollTo().performClick()
        ui.onNodeWithTag("draft_description").performScrollTo().assertTextContains("Una luminaria dejó de funcionar frente al parque.")
        ui.onNodeWithTag("draft_description").performTextReplacement("Un cable caído impide pasar por la entrada norte.")
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithTag("review_description").performScrollTo().assertTextContains("Un cable caído impide pasar por la entrada norte.", substring = true)
        ui.runOnIdle { assertDatabaseUnchanged() }
    }

    @Test fun reviewSurvivesRecreationAndBackReturnsToTheDraft() {
        completeReferenceDraft()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("review_description").performScrollTo().assertTextContains("Una luminaria dejó de funcionar frente al parque.", substring = true)
        ui.onNodeWithText("Volver").performClick()
        ui.onNodeWithTag("draft_description").performScrollTo().assertTextContains("Una luminaria dejó de funcionar frente al parque.")
        ui.onNodeWithText("¿Salir del borrador?").assertDoesNotExist()
        ui.runOnIdle { assertDatabaseUnchanged() }
    }

    @Test fun selectingOnlyATypeAlsoRequiresConfirmationBeforeExit() {
        ui.onNodeWithTag("type_RISK").performScrollTo().performClick()
        ui.onNodeWithText("Volver").performClick()
        ui.onNodeWithText("¿Salir del borrador?").assertExists()
        ui.onNodeWithText("Seguir editando").performClick()
        ui.onNodeWithTag("type_RISK").performScrollTo().assertIsSelected()
        ui.runOnIdle { assertDatabaseUnchanged() }
    }

    @Test fun formEditsSurviveActivityRecreationWithoutGps() {
        ui.onNodeWithTag("type_RISK").performScrollTo().performClick()
        ui.onNodeWithTag("priority_MEDIUM").performScrollTo().performClick()
        ui.onNodeWithTag("draft_description").performScrollTo().performTextInput("Una luminaria dejó de funcionar frente al parque.")
        ui.onNodeWithTag("draft_reference").performScrollTo().performTextInput("Frente al parque, entrada norte")
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        ui.onNodeWithTag("type_RISK").performScrollTo()
        capture("fase12-formulario.png")
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("draft_description").performScrollTo().assertTextContains("Una luminaria dejó de funcionar frente al parque.")
        ui.onNodeWithTag("draft_reference").performScrollTo().assertTextContains("Frente al parque, entrada norte")
        ui.runOnIdle {
            assertNull(ui.activity.draftModel.state.value.location.latitude)
            assertFalse(ui.activity.locationModel.state.value is IncidentLocationState.Capturing)
            assertDatabaseUnchanged()
        }
    }

    @Test fun openingCancellingAndReopeningMapPreservesDraftAndRequiresGps() {
        ui.onNodeWithTag("draft_description").performScrollTo().performTextInput("Una luminaria dejó de funcionar frente al parque.")
        ui.onNodeWithTag("draft_reference").performScrollTo().performTextInput("Entrada norte del parque")
        repeat(2) {
            ui.onNodeWithTag("open_incident_map").performScrollTo().performClick()
            ui.onNodeWithTag("confirm_point").assertIsNotEnabled()
            ui.runOnIdle {
                assertFalse(ui.activity.locationModel.state.value is IncidentLocationState.Capturing)
                assertTrue(ui.activity.draftModel.state.value.locationSelectionPending)
            }
            ui.onNodeWithTag("cancel_map").performScrollTo().performClick()
            ui.onNodeWithTag("draft_description").performScrollTo().assertTextContains("Una luminaria dejó de funcionar frente al parque.")
            ui.onNodeWithTag("draft_reference").performScrollTo().assertTextContains("Entrada norte del parque")
            ui.runOnIdle {
                assertNull(ui.activity.draftModel.state.value.location.latitude)
                assertFalse(ui.activity.draftModel.state.value.locationSelectionPending)
            }
        }
    }

    @Test fun rejectedTextSurvivesMapRoundTripAndStillBlocksReview() {
        val raw = "a".repeat(1001)
        ui.onNodeWithTag("draft_description").performScrollTo().performTextInput(raw)
        ui.runOnIdle { assertTrue(ReportDraftField.DESCRIPTION in ui.activity.draftModel.state.value.inputErrors) }
        ui.onNodeWithTag("open_incident_map").performScrollTo().performClick()
        ui.onNodeWithTag("cancel_map").performScrollTo().performClick()
        ui.onNodeWithTag("draft_description").performScrollTo().assertTextContains(raw)
        ui.onNodeWithTag("draft_description").performTextClearance()
        ui.onNodeWithTag("draft_description").performTextInput("Una luminaria dejó de funcionar frente al parque.")
        ui.runOnIdle { assertFalse(ReportDraftField.DESCRIPTION in ui.activity.draftModel.state.value.inputErrors) }
    }

    @Test fun mapRecreationKeepsSelectionWithoutAutomaticCapture() {
        ui.onNodeWithTag("open_incident_map").performScrollTo().performClick()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("confirm_point").assertIsNotEnabled()
        ui.runOnIdle {
            assertTrue(ui.activity.draftModel.state.value.locationSelectionPending)
            assertFalse(ui.activity.locationModel.state.value is IncidentLocationState.Capturing)
        }
        ui.onNodeWithTag("cancel_map").performScrollTo().performClick()
        ui.onNodeWithTag("open_incident_map").performScrollTo().assertExists()
    }

    @Test fun realMapTapCreatesOnlyAProposalAndCannotConfirmWithoutGps() {
        ui.onNodeWithTag("open_incident_map").performScrollTo().performClick()
        ui.waitUntil(timeoutMillis = 60_000) { ui.activity.previewMapReady }
        ui.waitUntil(timeoutMillis = 30_000) {
            var labels = emptyList<String>()
            ui.runOnUiThread { labels = ui.activity.previewRenderedStreetNames() }
            labels.isNotEmpty()
        }
        ui.onNodeWithTag("incident_map").performTouchInput { click(center) }
        ui.waitUntil(timeoutMillis = 10_000) {
            ui.activity.locationModel.state.value.proposal is com.example.citizensecurity.domain.LocationProposal.ValidPoint
        }
        ui.onNodeWithTag("confirm_point").assertIsNotEnabled()
        ui.waitUntil(timeoutMillis = 10_000) {
            var rendered = false
            ui.runOnUiThread { rendered = ui.activity.previewPointRendered() }
            rendered
        }
        capture("fase12-mapa.png")
        ui.runOnIdle {
            assertNull(ui.activity.draftModel.state.value.location.latitude)
            assertDatabaseUnchanged()
        }
    }

    @Test fun landscapeKeepsMapAndRecoveryControlsAccessible() {
        ui.onNodeWithTag("draft_description").performScrollTo().performTextInput("Una luminaria dejó de funcionar frente al parque.")
        ui.onNodeWithTag("open_incident_map").performScrollTo().performClick()
        try {
            ui.runOnUiThread { ui.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            ui.waitUntil(timeoutMillis = 15_000) {
                ui.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            }
            ui.onNodeWithTag("incident_map").assertIsDisplayed()
            ui.onNodeWithTag("permission_guidance").performScrollTo().assertIsDisplayed()
            ui.onNodeWithTag("confirm_point").performScrollTo().assertIsNotEnabled()
            ui.onNodeWithTag("cancel_map").performScrollTo().performClick()
            ui.onNodeWithTag("draft_description").performScrollTo().assertTextContains("Una luminaria dejó de funcionar frente al parque.")
            ui.runOnIdle { assertFalse(ui.activity.locationModel.state.value is IncidentLocationState.Capturing) }
        } finally {
            ui.runOnUiThread { ui.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }
}
