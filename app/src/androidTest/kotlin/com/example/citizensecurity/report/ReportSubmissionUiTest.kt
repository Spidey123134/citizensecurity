package com.example.citizensecurity.report

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportSubmissionRequest
import com.example.citizensecurity.preview.ReportDraftActivity
import com.example.citizensecurity.testing.ReportTestFixture
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite/AtomicFile acceptance on an isolated emulator; its phone reading is a test fixture. */
@RunWith(AndroidJUnit4::class)
class ReportSubmissionUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private var scenario: ActivityScenario<ReportDraftActivity>? = null
    private val fixture get() = fixtureRule.fixture

    @After
    fun closeActivity() {
        scenario?.close()
        scenario = null
        // ReportsActivity may have been opened from the receipt. Finish that owned UI as well.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .filter { it.packageName == InstrumentationRegistry.getInstrumentation().targetContext.packageName }
                .forEach { it.finish() }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun userCanSaveOneRealRowCopyFolioAndReadItsActualDetailAndFilters() {
        launch()
        prepareReviewedPoint()
        saveAndAwaitReceipt()
        val folio = displayedFolio()
        val saved = runBlocking { fixture.repository.findById(folio) }!!
        assertEquals(1, runBlocking { fixture.repository.list().size })
        assertEquals(DESCRIPTION, saved.description)
        assertEquals(REFERENCE, saved.location.reference)
        assertEquals(IncidentType.RISK, saved.type)
        assertEquals(folio, runBlocking { fixture.journal.read() }!!.requestId)
        capture("release053-guardado.png")

        ui.onNodeWithTag("copy_saved_folio").performScrollTo().performClick()
        // Clipboard reading is permitted only while this activity is in the foreground.
        var copied = ""
        scenario!!.onActivity { activity ->
            copied = activity.getSystemService(android.content.ClipboardManager::class.java)
                .primaryClip!!.getItemAt(0).text.toString()
        }
        assertEquals(folio, copied)
        fixture.publishFreshFix()
        runBlocking { fixture.repository.create(anotherDraft()) }
        val refreshes = fixture.refreshCalls
        ui.onNodeWithTag("open_saved_report").performScrollTo().performClick()
        ui.waitUntil(15_000) { ui.onAllNodesWithTag("report_folio").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("report_folio").assertTextEquals(folio)
        ui.onNodeWithTag("report_description").performScrollTo().assertTextEquals(DESCRIPTION)
        capture("release053-consulta.png")
        ui.onNodeWithTag("report_back_list").performScrollTo().performClick()
        ui.onNodeWithTag("report_list").performScrollToNode(hasTestTag("report_type_RISK"))
        ui.onNodeWithTag("report_type_RISK").performScrollTo().performClick()
        ui.onNodeWithTag("report_list").performScrollToNode(hasTestTag("list_card_$folio"))
        ui.onNodeWithTag("list_card_$folio").assertExists()
        ui.onNodeWithText("1 de 2 reportes locales").assertExists()
        assertEquals(refreshes, fixture.refreshCalls)
        assertEquals(2, runBlocking { fixture.repository.list().size })
    }

    @Test
    fun staleGpsCannotSaveUntilUserExplicitlyRefreshesAndRetries() {
        launch()
        prepareReviewedPoint()
        fixture.expireFix()
        ui.onNodeWithTag("save_report").performScrollTo().performClick()
        ui.waitUntil(15_000) { submissionState() is ReportSubmissionState.Invalid }
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertNull(runBlocking { fixture.journal.read() })
        val correctedDescription = "El cable de la luminaria quedó caído frente a la entrada norte."
        val correctedReference = "Entrada norte del parque, junto a la banca azul"
        ui.onNodeWithTag("draft_description").performScrollTo().performTextReplacement(correctedDescription)
        ui.onNodeWithTag("draft_reference").performScrollTo().performTextReplacement(correctedReference)
        scenario!!.recreate()
        ui.onNodeWithTag("draft_description").performScrollTo().assertTextContains(correctedDescription)
        ui.onNodeWithTag("draft_reference").performScrollTo().assertTextContains(correctedReference)
        scenario!!.onActivity { activity ->
            assertEquals(correctedDescription, activity.draftModel.state.value.description)
            assertEquals(correctedReference, activity.draftModel.state.value.location.reference)
        }
        assertEquals("Recrear no debe renovar el GPS automáticamente", 1, fixture.refreshCalls)
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithTag("refresh_save_gps").performScrollTo().performClick()
        ui.onNodeWithTag("save_gps_message").assertTextContains("Ubicación actualizada", substring = true)
        saveAndAwaitReceipt()
        val saved = runBlocking { fixture.repository.list() }.single()
        assertEquals(correctedDescription, saved.description)
        assertEquals(correctedReference, saved.location.reference)
        assertEquals(2, fixture.refreshCalls)
    }

    @Test
    fun receiptSurvivesRecreationAndNewActivityWithoutAnotherGpsCaptureOrDuplicate() {
        launch()
        prepareReviewedPoint()
        saveAndAwaitReceipt()
        val folio = displayedFolio()
        val captures = fixture.refreshCalls
        scenario!!.recreate()
        awaitReceipt()
        ui.onNodeWithTag("saved_folio").assertTextEquals(folio)
        scenario!!.close()
        scenario = null
        fixture.clearFix()
        launch()
        awaitReceipt()
        ui.onNodeWithTag("saved_folio").assertTextEquals(folio)
        assertEquals(captures, fixture.refreshCalls)
        assertEquals(1, runBlocking { fixture.repository.list().size })
        assertEquals(folio, runBlocking { fixture.journal.read() }!!.requestId)
    }

    @Test
    fun recoveredPendingRequestRetainsItsFieldsWhenGpsRejectsTheRetry() {
        val draft = anotherDraft().copy(type = IncidentType.RISK, description = DESCRIPTION, location =
            ReportLocation(REFERENCE, ReportTestFixture.LATITUDE, ReportTestFixture.LONGITUDE))
        val request = ReportSubmissionRequest(UUID.randomUUID().toString(), draft, Instant.now())
        runBlocking { fixture.journal.write(request) }
        fixture.clearFix()
        launch()
        ui.waitUntil(15_000) { submissionState() is ReportSubmissionState.Pending }
        ui.onNodeWithTag("review_description").performScrollTo().assertTextEquals(DESCRIPTION)
        ui.onNodeWithTag("retry_submission").performScrollTo().performClick()
        ui.waitUntil(15_000) { submissionState() is ReportSubmissionState.Invalid }
        ui.waitForIdle()
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertNull(runBlocking { fixture.journal.read() })
        scenario!!.onActivity { activity ->
            val state = activity.draftModel.state.value
            assertEquals(IncidentType.RISK, state.type)
            assertEquals(Priority.MEDIUM, state.priority)
            assertEquals(DESCRIPTION, state.description)
            assertEquals(draft.location, state.location)
            assertEquals(draft.occurredAt, state.occurredAt)
        }
        ui.onNodeWithTag("draft_description").performScrollTo().assertTextContains(DESCRIPTION)
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithTag("refresh_save_gps").performScrollTo().performClick()
        saveAndAwaitReceipt()
        val saved = runBlocking { fixture.repository.list() }.single()
        assertEquals(draft.location, saved.location)
        assertEquals(DESCRIPTION, saved.description)
        assertEquals(1, fixture.refreshCalls)
    }

    @Test
    fun startingAnotherReportExplicitlyClearsOnlyTheReceiptAndKeepsTheSavedRow() {
        launch()
        prepareReviewedPoint()
        saveAndAwaitReceipt()
        val first = displayedFolio()
        val captures = fixture.refreshCalls
        ui.onNodeWithTag("new_report").performScrollTo().performClick()
        ui.waitUntil(15_000) { submissionState() == ReportSubmissionState.Idle }
        ui.onNodeWithTag("draft_description").performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")),
        )
        ui.onNodeWithTag("type_RISK").performScrollTo().assertIsNotSelected()
        assertNull(runBlocking { fixture.journal.read() })
        assertEquals(listOf(first), runBlocking { fixture.repository.list().map { it.id } })
        assertEquals(captures, fixture.refreshCalls)
        scenario!!.onActivity { assertNull(it.draftModel.state.value.location.latitude) }
    }

    private fun launch() {
        scenario = ActivityScenario.launch(ReportDraftActivity::class.java)
    }

    private fun prepareReviewedPoint() {
        ui.waitUntil(15_000) { submissionState() == ReportSubmissionState.Idle }
        ui.onNodeWithTag("type_RISK").performScrollTo().performClick()
        ui.onNodeWithTag("priority_MEDIUM").performScrollTo().performClick()
        ui.onNodeWithTag("draft_description").performScrollTo().performTextInput(DESCRIPTION)
        ui.onNodeWithTag("draft_reference").performScrollTo().performTextInput(REFERENCE)
        ui.onNodeWithTag("open_incident_map").performScrollTo().performClick()
        ui.waitUntil(60_000) {
            var ready = false
            scenario!!.onActivity { ready = it.previewMapReady }
            ready
        }
        ui.onNodeWithTag("incident_map").performTouchInput { click(center) }
        ui.waitUntil(10_000) {
            var selected = false
            scenario!!.onActivity { selected = it.locationModel.state.value.proposal is LocationProposal.ValidPoint }
            selected
        }
        ui.onNodeWithTag("confirm_point").assertIsNotEnabled()
        ui.onNodeWithTag("locate_phone").performScrollTo().performClick()
        ui.onNodeWithTag("confirm_point").performScrollTo().assertIsEnabled().performClick()
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithTag("review_summary").assertExists()
        ui.onNodeWithTag("review_description").performScrollTo().assertTextEquals(DESCRIPTION)
    }

    private fun saveAndAwaitReceipt() {
        ui.onNodeWithTag("save_report").performScrollTo().assertIsEnabled().performClick()
        awaitReceipt()
    }

    private fun awaitReceipt() {
        ui.waitUntil(15_000) { submissionState() is ReportSubmissionState.Saved }
        ui.onNodeWithTag("saved_folio").assertExists()
    }

    private fun submissionState(): ReportSubmissionState {
        var state: ReportSubmissionState = ReportSubmissionState.Restoring
        scenario!!.onActivity { state = it.submissionModel.state.value }
        return state
    }

    private fun displayedFolio(): String = ui.onNodeWithTag("saved_folio").fetchSemanticsNode()
        .config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun anotherDraft() = NewReport(
        type = IncidentType.THEFT,
        priority = Priority.MEDIUM,
        description = "Reporte de prueba aislada para comprobar los filtros de lectura.",
        occurredAt = Instant.now().minusSeconds(60),
        location = ReportLocation("Referencia de prueba", ReportTestFixture.LATITUDE, ReportTestFixture.LONGITUDE),
    )

    private fun capture(name: String) {
        ui.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
    }

    private companion object {
        const val DESCRIPTION = "Una luminaria dejó de funcionar frente al parque."
        const val REFERENCE = "Frente al parque, entrada norte"
    }
}
