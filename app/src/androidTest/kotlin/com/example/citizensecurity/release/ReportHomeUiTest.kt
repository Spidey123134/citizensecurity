package com.example.citizensecurity.release

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.citizensecurity.R
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportDraftCheckpoint
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportSubmissionRequest
import com.example.citizensecurity.preview.ReportDraftActivity
import com.example.citizensecurity.report.ReportSubmissionState
import com.example.citizensecurity.reports.ReportsActivity
import com.example.citizensecurity.reports.ReportsMapActivity
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

/** Product navigation with real isolated journals/SQLite; opening it never refreshes GPS. */
@RunWith(AndroidJUnit4::class)
class ReportHomeUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private var scenario: ActivityScenario<ReleasedToolsActivity>? = null
    private var previousFontScale: String? = null
    private val fixture get() = fixtureRule.fixture
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun closeOwnedActivitiesAndRestoreFontScale() {
        scenario?.close()
        scenario = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filter { it.packageName == context.packageName }.forEach(Activity::finish)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        previousFontScale?.let { value ->
            if (value == "null") shell("settings delete system font_scale")
            else shell("settings put system font_scale $value")
        }
    }

    @Test fun realReportListAndMapRoutesReturnHomeWithoutSavingOrStartingGps() {
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        val before = journalBytes()
        launchHome()
        assertNoHistoricalDemonstrationControls()
        capture("release056-inicio.png")

        clickHome(R.id.tools_preview_interface)
        awaitActivity<ReportDraftActivity>()
        awaitTag("draft_description")
        withResumed<ReportDraftActivity> {
            assertEquals("", it.draftModel.state.value.description)
            assertNull(it.draftModel.state.value.type)
            assertNull(it.draftModel.state.value.location.latitude)
        }
        pressBack()
        awaitHome()

        clickHome(R.id.tools_reports)
        awaitActivity<ReportsActivity>()
        awaitTag("report_no_data")
        ui.onNodeWithTag("report_no_data").assertExists()
        pressBack()
        awaitHome()

        clickHome(R.id.tools_reports_map)
        // The route works even if street tiles cannot load; this is not a network acceptance test.
        awaitActivity<ReportsMapActivity>()
        pressBack()
        awaitHome()
        scenario!!.recreate()
        awaitHome()

        assertEquals(0, fixture.refreshCalls)
        assertNull(fixture.currentFix())
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertEquals(before, journalBytes())
    }

    @Test fun helpRouteOpensEmergencyAccessWithoutPreparingAReportOrGps() {
        val before = journalBytes()
        launchHome()
        clickHome(R.id.home_help)
        awaitActivity<com.example.citizensecurity.help.HelpContactsActivity>()
        awaitTag("help_dial_911")
        ui.onNodeWithTag("help_dial_911").assertExists()
        ui.onNodeWithTag("help_dial_089").assertExists()
        assertEquals(0, fixture.refreshCalls)
        assertNull(fixture.currentFix())
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertEquals(before, journalBytes())
        pressBack()
        awaitHome()
    }

    @Test fun quickHelpRouteReturnsWithoutLoggingOutOrPreparingAReport() {
        val app = context.applicationContext as com.example.citizensecurity.CitizenSecurityApplication
        launchHome()
        val before = app.accountSession.capture()
        val journals = journalBytes()
        clickHome(R.id.home_quick_help)
        awaitActivity<com.example.citizensecurity.help.EmergencyAccessActivity>()
        awaitTag("emergency_title")
        assertSame(before, app.accountSession.capture())
        assertEquals(0, fixture.refreshCalls)
        assertEquals(journals, journalBytes())
        pressBack()
        awaitHome()
    }

    @Test fun pendingDurableAttemptCanBeOpenedWithItsOriginalFieldsAndIdentity() {
        val request = ReportSubmissionRequest(UUID.randomUUID().toString(), draft(), Instant.now())
        runBlocking { fixture.journal.write(request) }
        val before = journalBytes()
        launchHome()
        awaitHomeText("intento")
        clickHome(R.id.tools_preview_interface)
        awaitActivity<ReportDraftActivity>()
        ui.waitUntil(15_000) {
            var pending = false
            withResumed<ReportDraftActivity> { pending = it.submissionModel.state.value is ReportSubmissionState.Pending }
            pending
        }
        ui.onNodeWithTag("review_description").performScrollTo().assertTextEquals(DESCRIPTION)
        withResumed<ReportDraftActivity> {
            val pending = it.submissionModel.state.value as ReportSubmissionState.Pending
            assertEquals(request, pending.request)
        }
        pressBack()
        awaitHome()
        assertEquals(request, runBlocking { fixture.journal.read() })
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertEquals(before, journalBytes())
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun savedCompleteDraftIsRestoredOnlyAfterItsExplicitConfirmation() {
        val saved = ReportDraftCheckpoint(UUID.randomUUID().toString(), draft(), Instant.now())
        runBlocking { fixture.checkpoint.write(saved, expectedId = null) }
        val before = journalBytes()
        launchHome()
        awaitHomeText("borrador")
        scenario!!.recreate()
        awaitHomeText("borrador")
        clickHome(R.id.tools_preview_interface)
        awaitActivity<ReportDraftActivity>()
        awaitTag("restore_checkpoint")
        withResumed<ReportDraftActivity> { assertEquals("", it.draftModel.state.value.description) }
        ui.onNodeWithTag("restore_checkpoint").performScrollTo().performClick()
        ui.onNodeWithTag("confirm_restore_checkpoint").performClick()
        ui.waitUntil(15_000) {
            var restored = false
            withResumed<ReportDraftActivity> { restored = it.draftModel.state.value.description == DESCRIPTION }
            restored
        }
        withResumed<ReportDraftActivity> {
            assertEquals(saved.draft.location, it.draftModel.state.value.location)
            assertEquals(IncidentType.RISK, it.draftModel.state.value.type)
        }
        assertEquals(saved, runBlocking { fixture.checkpoint.read() })
        assertNull(runBlocking { fixture.journal.read() })
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertEquals(before, journalBytes())
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun returningFromSavedReportsReadsTheDraftCreatedWhileHomeWasStopped() {
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        returnProgressStep("01 launch empty home") { launchHome() }
        assertNull(runBlocking { fixture.checkpoint.read() })
        returnProgressStep("02 click saved reports") { clickHome(R.id.tools_reports) }
        returnProgressStep("03 await first ReportsActivity resumed") { awaitActivity<ReportsActivity>() }
        returnProgressStep("04 await first empty list") { awaitTag("report_no_data") }
        returnProgressStep("05 verify home stopped before seeding") {
            ui.waitUntil(15_000) {
                var stopped = false
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    stopped = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STOPPED)
                        .any { it is ReleasedToolsActivity }
                }
                stopped
            }
        }
        // Seed the test-owned AtomicFile while its home is stopped. No production UI writes,
        // report insertion, or GPS reading occurs in this refresh acceptance scenario.
        val saved = ReportDraftCheckpoint(UUID.randomUUID().toString(), draft(), Instant.now())
        returnProgressStep("06 seed complete draft in owned store") {
            runBlocking { fixture.checkpoint.write(saved, expectedId = null) }
        }
        val beforeReturn = journalBytes()
        returnProgressStep("07 first Back") { pressBack() }
        returnProgressStep("08 await changed home draft indicator") { awaitHomeText("borrador") }
        assertEquals(beforeReturn, journalBytes())

        returnProgressStep("09 request landscape") {
            scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        }
        returnProgressStep("10 await landscape configuration") {
            ui.waitUntil(15_000) {
                var landscape = false
                scenario!!.onActivity {
                    landscape = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                }
                landscape
            }
        }
        returnProgressStep("11 await draft indicator after rotation") { awaitHomeText("borrador") }
        returnProgressStep("11b await settled native window after rotation") {
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
            ui.waitUntil(15_000) {
                var ready = false
                scenario!!.onActivity {
                    val scroll = it.findViewById<ScrollView>(R.id.tools_container)
                    ready = it.window.decorView.hasWindowFocus() && !it.window.decorView.isLayoutRequested &&
                        scroll.width > 0 && scroll.height > 0 && !scroll.isLayoutRequested &&
                        homeRouteIds.all { id -> it.findViewById<Button>(id).height > 0 }
                }
                ready
            }
        }
        returnProgressStep("12 click saved reports again") { clickHome(R.id.tools_reports) }
        returnProgressStep("13 await second ReportsActivity resumed") { awaitActivity<ReportsActivity>() }
        returnProgressStep("14 await second empty list") { awaitTag("report_no_data") }
        returnProgressStep("15 second Back") { pressBack() }
        returnProgressStep("16 await draft indicator after second return") { awaitHomeText("borrador") }

        assertEquals(saved, runBlocking { fixture.checkpoint.read() })
        assertNull(runBlocking { fixture.journal.read() })
        assertEquals(beforeReturn, journalBytes())
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertEquals(0, fixture.refreshCalls)
        assertNull(fixture.currentFix())
        recordReturnProgress("17 complete: exact journals, no report rows or GPS")
    }

    @Test fun malformedAttemptIsReportedAndRetryPreservesItsBytesInsteadOfPretendingItIsEmpty() {
        val file = File(context.filesDir, fixture.journalName)
        val invalidBytes = "{\"schema\":999,\"unknown\":true}".toByteArray(Charsets.UTF_8)
        file.writeBytes(invalidBytes)
        val before = journalBytes()
        launchHome()
        awaitHomeText("comprobar")
        clickHome(R.id.home_retry)
        awaitHomeText("comprobar")
        assertArrayEquals(invalidBytes, file.readBytes())
        clickHome(R.id.tools_preview_interface)
        awaitActivity<ReportDraftActivity>()
        awaitTag("recover_submission")
        ui.onNodeWithText("No se pudo comprobar el último intento").assertExists()
        ui.onNodeWithTag("recover_submission").performClick()
        ui.waitUntil(15_000) {
            var error = false
            withResumed<ReportDraftActivity> {
                error = it.submissionModel.state.value is ReportSubmissionState.Error
            }
            error
        }
        assertArrayEquals(invalidBytes, file.readBytes())
        assertEquals(before, journalBytes())
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun landscapeAndLargeFontKeepEveryFunctionalRouteReadableAndReachable() {
        launchHome()
        val before = journalBytes()
        previousFontScale = shell("settings get system font_scale").trim()
        shell("settings put system font_scale 2.0")
        scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        ui.waitUntil(15_000) {
            var configured = false
            scenario!!.onActivity {
                configured = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    it.resources.configuration.fontScale >= 1.9f
            }
            configured
        }
        scenario!!.recreate()
        awaitHome()
        // Font scale, orientation and recreation each enqueue a new Android window/layout.
        // A completed journal read alone is not evidence that the visible window is settled.
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5_000)
        ui.waitUntil(15_000) {
            var laidOut = false
            scenario!!.onActivity {
                val scroll = it.findViewById<ScrollView>(R.id.tools_container)
                laidOut = scroll.width > 0 && scroll.height > 0 && !scroll.isLayoutRequested &&
                    homeRouteIds.all { id -> it.findViewById<Button>(id).height > 0 }
            }
            laidOut
        }
        homeRouteIds.forEach { id ->
            try {
                onView(withId(id)).perform(scrollTo()).check(matches(isCompletelyDisplayed()))
                    .check(matches(isEnabled())).check { view, noView ->
                        if (noView != null) throw noView
                        val button = view as Button
                        assertTrue("Button text is clipped: ${button.text}",
                            button.height - button.compoundPaddingTop - button.compoundPaddingBottom >= button.layout.height)
                    }
            } catch (failure: Throwable) {
                // Preserve the strict display assertion and produce measurements before teardown.
                runCatching { recordLandscapeFailure(id) }
                throw failure
            }
        }
        assertNoHistoricalDemonstrationControls()
        assertEquals(before, journalBytes())
        assertNull(runBlocking { fixture.journal.read() })
        assertNull(runBlocking { fixture.checkpoint.read() })
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertEquals(0, fixture.refreshCalls)
    }

    private fun launchHome() {
        scenario = ActivityScenario.launch(ReleasedToolsActivity::class.java)
        awaitHome()
    }

    private fun awaitHome() {
        awaitActivity<ReleasedToolsActivity>()
        ui.waitUntil(15_000) {
            var ready = false
            scenario!!.onActivity { ready = it.findViewById<Button>(R.id.home_retry).isEnabled }
            ready
        }
    }

    private fun awaitHomeText(fragment: String) {
        awaitHome()
        ui.waitUntil(15_000) {
            var found = false
            scenario!!.onActivity {
                found = it.findViewById<TextView>(R.id.home_status).text.toString().contains(fragment, ignoreCase = true)
            }
            found
        }
    }

    private fun clickHome(id: Int) = onView(withId(id)).perform(scrollTo(), click())

    private inline fun <reified T : Activity> awaitActivity() {
        ui.waitUntil(15_000) { var found = false; withResumed<T> { found = true }; found }
    }

    private inline fun <reified T : Activity> withResumed(crossinline action: (T) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<T>().singleOrNull()?.let(action)
        }
    }

    private fun awaitTag(tag: String) = ui.waitUntil(15_000) {
        ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun journalBytes(): Map<String, List<Byte>> = context.filesDir.listFiles().orEmpty()
        .filter { it.isFile && (it.name.startsWith("submission_ui_") || it.name.startsWith("draft_ui_")) }
        .associate { it.name to it.readBytes().toList() }

    private fun assertNoHistoricalDemonstrationControls() {
        scenario!!.onActivity { activity ->
            listOf("tools_receive", "tools_validate", "tools_locations", "tools_save_example", "tools_description")
                .forEach { name ->
                    val id = context.resources.getIdentifier(name, "id", context.packageName)
                    if (id != 0) assertNull("Historical demonstration is still in the menu: $name", activity.findViewById<android.view.View>(id))
                }
        }
    }

    private fun draft() = NewReport(IncidentType.RISK, Priority.MEDIUM, DESCRIPTION,
        Instant.now().minusSeconds(60), ReportLocation("Entrada norte del parque", 19.4326, -99.1332))

    private fun capture(name: String) {
        ui.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(500, 5_000)
        val image = automation.takeScreenshot()!!
        File(context.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        image.recycle()
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun recordLandscapeFailure(id: Int) {
        var measurements = ""
        scenario!!.onActivity { activity ->
            val scroll = activity.findViewById<ScrollView>(R.id.tools_container)
            val button = activity.findViewById<Button>(id)
            fun visible(view: android.view.View): String {
                val rectangle = Rect()
                return "${view.getGlobalVisibleRect(rectangle)} $rectangle"
            }
            measurements = """
                configuration=${activity.resources.configuration}
                decor=${activity.window.decorView.width}x${activity.window.decorView.height} visible=${visible(activity.window.decorView)}
                scroll=${scroll.width}x${scroll.height} y=${scroll.scrollY} childHeight=${scroll.getChildAt(0).height}
                scrollPadding=${scroll.paddingLeft},${scroll.paddingTop},${scroll.paddingRight},${scroll.paddingBottom} clipToPadding=${scroll.clipToPadding}
                scrollVisible=${visible(scroll)} insets=${scroll.rootWindowInsets}
                button=${context.resources.getResourceEntryName(id)} text=${button.text}
                buttonSize=${button.width}x${button.height} top=${button.top} bottom=${button.bottom} visible=${visible(button)}
                textHeight=${button.layout?.height} lines=${button.layout?.lineCount} compoundPadding=${button.compoundPaddingTop},${button.compoundPaddingBottom}
            """.trimIndent()
        }
        File(context.getExternalFilesDir(null), "release056-inicio-landscape-failure.txt").writeText(measurements)
        capture("release056-inicio-landscape-failure.png")
    }

    private fun returnProgressStep(step: String, action: () -> Unit) {
        recordReturnProgress("BEFORE $step")
        try {
            action()
            recordReturnProgress("AFTER $step")
        } catch (failure: Throwable) {
            runCatching { recordReturnProgress("FAILED $step: ${failure.javaClass.simpleName}: ${failure.message}") }
            runCatching { capture("release056-inicio-return-failure.png") }
            throw failure
        }
    }

    private fun recordReturnProgress(step: String) {
        var activities = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            activities = Stage.values().flatMap { stage ->
                monitor.getActivitiesInStage(stage).filter { it.packageName == context.packageName }.map { activity ->
                    val status = if (activity is ReleasedToolsActivity)
                        activity.findViewById<TextView>(R.id.home_status)?.text?.toString()?.replace('\n', '|') else null
                    "${activity.javaClass.simpleName}#${System.identityHashCode(activity)}=$stage" +
                        " orientation=${activity.resources.configuration.orientation}" +
                        " fontScale=${activity.resources.configuration.fontScale} status=$status"
                }
            }.joinToString("; ")
        }
        val line = "${SystemClock.elapsedRealtime()} $step :: $activities\n"
        File(context.getExternalFilesDir(null), "release056-inicio-return-progress.txt").appendText(line)
        Log.i("ReportHomeReturnTest", line)
    }

    private val homeRouteIds get() = listOf(R.id.home_help, R.id.home_quick_help, R.id.tools_preview_interface, R.id.tools_reports, R.id.tools_reports_map, R.id.tools_back)

    private companion object { const val DESCRIPTION = "Un cable caído impide pasar por la entrada norte del parque." }
}
