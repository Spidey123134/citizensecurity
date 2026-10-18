package com.example.citizensecurity.tutorial

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.maps.ReportMapSceneState
import com.example.citizensecurity.preview.ReportDraftActivity
import com.example.citizensecurity.release.ReleasedToolsActivity
import com.example.citizensecurity.report.ReportDraftState
import com.example.citizensecurity.reports.ReportsActivity
import com.example.citizensecurity.reports.ReportsMapActivity
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Optional product help over the real screens and nonce-owned SQLite/journals. A tour must not
 * perform the report, GPS, consultation or sharing actions it explains. Map rendering/network
 * success and a physical GPS reading are deliberately not prerequisites for this UI acceptance.
 */
@RunWith(AndroidJUnit4::class)
class GuidedTourUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = ApplicationProvider.getApplicationContext<ReportTestApplication>()
    private val fixture get() = fixtureRule.fixture
    private var scenario: ActivityScenario<out Activity>? = null
    private var previousFontScale: String? = null

    @Before fun academicAccessWithoutLoginSideEffects() {
        instrumentation.runOnMainSync { app.accountSession.signOut() }
    }

    @After fun closeOnlyOwnedActivitiesAndRestoreFontScale() {
        scenario?.close()
        scenario = null
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filter { it.packageName == app.packageName }.forEach(Activity::finish)
            app.accountSession.signOut()
        }
        instrumentation.waitForIdleSync()
        previousFontScale?.let { original ->
            if (original == "null") shell("settings delete system font_scale")
            else shell("settings put system font_scale $original")
        }
    }

    @Test fun homeTourIsOptInNavigableAndFinishingAllowsAReplayFromTheFirstStep() {
        launch<ReleasedToolsActivity>()
        awaitTag("tour_open_home")
        ui.onNodeWithTag("tour_progress").assertDoesNotExist()
        val before = durableState()
        ui.onNodeWithTag("tour_open_home").assert(SemanticsMatcher("Has a spoken help label") {
            it.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.any { label -> label.startsWith("Abrir recorrido de ") } == true
        })
        open("home")
        progress(1, 4)
        ui.onNodeWithTag("tour_title").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        ui.onNodeWithTag("tour_previous").assertIsNotEnabled()
        next(); progress(2, 4)
        next(); progress(3, 4)
        previous(); progress(2, 4)
        next(); next(); progress(4, 4)
        ui.onNodeWithTag("tour_next").assertTextEquals("Listo")
        next()
        ui.onNodeWithTag("tour_progress").assertDoesNotExist()
        open("home"); progress(1, 4)
        closeTour()
        assertEquals(before, durableState())
        assertEquals(0, fixture.refreshCalls)
        assertNull(fixture.currentFix())
    }

    @Test fun androidBackDismissesHelpAndLeavesTheUnderlyingHomeOpen() {
        val home = launch<ReleasedToolsActivity>()
        open("home"); next(); progress(2, 4)
        pressBack()
        ui.onNodeWithTag("tour_progress").assertDoesNotExist()
        assertEquals(Lifecycle.State.RESUMED, home.state)
        ui.onNodeWithTag("tour_open_home").assertExists()
        open("home"); progress(1, 4)
        closeTour()
        assertEquals(0, fixture.refreshCalls)
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
    }

    @Test fun rotatingTheScreenKeepsAnOpenTourOnItsCurrentStep() {
        val home = launch<ReleasedToolsActivity>()
        open("home"); next(); progress(2, 4)
        val target = otherOrientation(home)
        home.onActivity { it.requestedOrientation = target.requested }
        ui.waitUntil(15_000) {
            var rotated = false
            home.onActivity { rotated = it.resources.configuration.orientation == target.configuration }
            rotated
        }
        progress(2, 4)
        previous(); progress(1, 4)
        closeTour()
        open("home"); progress(1, 4)
        closeTour()
        assertEquals(0, fixture.refreshCalls)
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
    }

    @Test fun reportHelpLeavesTypedFieldsJournalsAndGpsUntouched() {
        val draft = launch<ReportDraftActivity>()
        awaitTag("draft_description")
        ui.onNodeWithTag("tour_progress").assertDoesNotExist()
        completeReferenceFields()
        var before: ReportDraftState? = null
        draft.onActivity { before = it.draftModel.state.value }
        val durable = durableState()
        open("report")
        finishTour(4)
        draft.onActivity { assertEquals(before, it.draftModel.state.value) }
        ui.onNodeWithTag("draft_description").performScrollTo().assertTextContains(DESCRIPTION)
        ui.onNodeWithTag("draft_reference").performScrollTo().assertTextContains(REFERENCE)
        assertEquals(durable, durableState())
        assertEquals(0, fixture.refreshCalls)
        assertNull(fixture.currentFix())
    }

    @Test fun reviewHasItsOwnTourWithoutSavingOrChangingTheReviewedDraft() {
        val draft = launch<ReportDraftActivity>()
        awaitTag("draft_description")
        completeReferenceFields()
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        awaitTag("review_description")
        val durable = durableState()
        var before: ReportDraftState? = null
        draft.onActivity { before = it.draftModel.state.value }
        open("review"); progress(1, 3)
        finishTour(3)
        ui.onNodeWithTag("review_description").performScrollTo().assertTextContains(DESCRIPTION)
        draft.onActivity { assertEquals(before, it.draftModel.state.value) }
        ui.onNodeWithTag("tour_open_report").assertDoesNotExist()
        assertEquals(durable, durableState())
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun listAndDetailHelpDoNotChangeSavedReportsOrCarryThePreviousTopicsStep() {
        // Technical fixture insert only: no production save, status or export button is exercised.
        fixture.publishFreshFix()
        val report = runBlocking { fixture.repository.create(NewReport(IncidentType.RISK, Priority.MEDIUM,
            DESCRIPTION, Instant.now().minusSeconds(60), ReportLocation(REFERENCE, 19.4326, -99.1332))) }
        fixture.clearFix()
        launch<ReportsActivity>()
        awaitTag("report_list")
        awaitListItem("report_list", "report_open_${report.id}")
        val durable = durableState()
        open("reports"); next(); progress(2, 3)
        closeTour()
        ui.onNodeWithTag("report_list").performScrollToNode(hasTestTag("report_open_${report.id}"))
        ui.onNodeWithTag("report_open_${report.id}").performClick()
        awaitTag("report_copy_folio")
        open("detail"); progress(1, 3)
        finishTour(3)
        ui.onNodeWithTag("tour_open_reports").assertDoesNotExist()
        assertEquals(durable, durableState())
        assertEquals(0, fixture.refreshCalls)
        assertNull(fixture.currentFix())
    }

    @Test fun mapTourDoesNotQueryTheZoneCreateMarkersOrRequestGps() {
        val map = launch<ReportsMapActivity>()
        awaitTag("tour_open_map")
        val durable = durableState()
        ui.onNodeWithTag("tour_progress").assertDoesNotExist()
        open("map"); finishTour(3)
        map.onActivity {
            // Loading streets may fail offline; neither Idle nor an unavailable style is a query.
            assertTrue(it.currentSceneState is ReportMapSceneState.Idle ||
                it.currentSceneState is ReportMapSceneState.Error)
            assertEquals(0, it.renderedMarkerCount())
        }
        ui.onNodeWithTag("map_zone_list").assertDoesNotExist()
        assertEquals(durable, durableState())
        assertEquals(0, fixture.refreshCalls)
        assertNull(fixture.currentFix())
    }

    @Test fun largerTextAndLandscapeKeepTheTourReadableAndItsControlsReachable() {
        previousFontScale = shell("settings get system font_scale").trim()
        shell("settings put system font_scale 1.3")
        val home = launch<ReleasedToolsActivity>()
        home.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        ui.waitUntil(15_000) {
            var ready = false
            home.onActivity {
                ready = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    kotlin.math.abs(it.resources.configuration.fontScale - 1.3f) < 0.05f
            }
            ready
        }
        open("home")
        ui.onNodeWithTag("tour_body").performScrollTo().assertIsDisplayed()
        next(); progress(2, 4)
        previous(); progress(1, 4)
        // Capture after interaction rather than during Android's native dialog-entry fade.
        ui.onNodeWithTag("tour_body").performScrollTo().assertIsDisplayed()
        capture("tour43-large-text-landscape.png")
        closeTour()
        assertEquals(Lifecycle.State.RESUMED, home.state)
        assertEquals(0, fixture.refreshCalls)
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
    }

    private inline fun <reified T : Activity> launch(): ActivityScenario<T> =
        ActivityScenario.launch(T::class.java).also { scenario = it }

    private fun open(topic: String) {
        if (topic == "reports" || topic == "detail") {
            awaitListItem(if (topic == "reports") "report_list" else "report_detail", "tour_open_$topic")
        }
        awaitTag("tour_open_$topic")
        ui.onNodeWithTag("tour_open_$topic").performClick()
        awaitTag("tour_progress")
    }
    private fun next() = ui.onNodeWithTag("tour_next").performScrollTo().performClick()
    private fun previous() = ui.onNodeWithTag("tour_previous").performScrollTo().performClick()
    private fun closeTour() {
        ui.onNodeWithTag("tour_close").performScrollTo().performClick()
        ui.onNodeWithTag("tour_progress").assertDoesNotExist()
    }
    private fun progress(step: Int, total: Int) {
        val text = "Paso $step de $total"
        ui.onNodeWithTag("tour_progress").assertTextEquals(text)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text))
    }
    private fun finishTour(total: Int) {
        for (step in 1..total) {
            progress(step, total)
            next()
        }
        ui.onNodeWithTag("tour_progress").assertDoesNotExist()
    }
    private fun completeReferenceFields() {
        ui.onNodeWithTag("type_RISK").performScrollTo().performClick()
        ui.onNodeWithTag("priority_MEDIUM").performScrollTo().performClick()
        ui.onNodeWithTag("draft_description").performScrollTo().performTextInput(DESCRIPTION)
        ui.onNodeWithTag("draft_reference").performScrollTo().performTextInput(REFERENCE)
        closeSoftKeyboard()
    }
    private fun awaitTag(tag: String) = ui.waitUntil(15_000) {
        ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun awaitListItem(list: String, tag: String) = ui.waitUntil(15_000) {
        // Lazy items can be outside the composition, and the initial bounded SQL read is async.
        runCatching { ui.onNodeWithTag(list).performScrollToNode(hasTestTag(tag)) }.isSuccess
    }
    private fun durableState(): DurableState = runBlocking {
        DurableState(fixture.repository.list(), fixture.journal.read(), fixture.checkpoint.read(),
            app.filesDir.listFiles().orEmpty()
                .filter { it.isFile && (it.name.startsWith("submission_ui_") || it.name.startsWith("draft_ui_")) }
                .associate { it.name to it.readBytes().toList() })
    }
    private fun otherOrientation(owner: ActivityScenario<ReleasedToolsActivity>): Orientation {
        var portrait = false
        owner.onActivity { portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        return if (portrait) Orientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        else Orientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
    }
    private fun capture(name: String) {
        ui.waitForIdle()
        val image = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(image)
        File(app.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        image.recycle()
    }
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8) }

    private data class Orientation(val requested: Int, val configuration: Int)
    private data class DurableState(
        val reports: List<com.example.citizensecurity.domain.Report>,
        val submission: com.example.citizensecurity.domain.ReportSubmissionRequest?,
        val checkpoint: com.example.citizensecurity.domain.ReportDraftCheckpoint?,
        val journalBytes: Map<String, List<Byte>>,
    )
    companion object {
        private const val DESCRIPTION = "Una luminaria dejó de funcionar frente a la entrada del parque."
        private const val REFERENCE = "Entrada norte del parque, junto al cruce peatonal"
    }
}
