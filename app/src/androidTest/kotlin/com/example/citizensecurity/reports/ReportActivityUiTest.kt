package com.example.citizensecurity.reports

import android.app.Activity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.citizensecurity.R
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.release.ReleasedToolsActivity
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real private SQLite activity. Only fixture permissions/phone readings and actors are controlled. */
@RunWith(AndroidJUnit4::class)
class ReportActivityUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<ReportTestApplication>()
    private val fixture get() = fixtureRule.fixture
    private var scenario: ActivityScenario<ReportActivityActivity>? = null
    private var home: ActivityScenario<ReleasedToolsActivity>? = null

    @Before fun beginAcademicLease() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.accountSession.signOut() }
    }
    @After fun closeOnlyOwnedTestActivities() {
        scenario?.close(); home?.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filter { it.packageName == app.packageName }.forEach(Activity::finish)
            app.accountSession.signOut()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test fun homeRouteShowsHonestEmptyActivityWithoutWritingOrStartingGps() {
        assertTrue(runBlocking { fixture.repository.readActivity(ReportActivityQuery()).entries.isEmpty() })
        home = ActivityScenario.launch(ReleasedToolsActivity::class.java)
        onView(withId(R.id.home_activity)).perform(scrollTo(), click())
        awaitTag("activity_empty")
        ui.onNodeWithTag("activity_empty").assertTextContains("No hay actividad registrada", substring = true)
        ui.onNodeWithTag("activity_changes").performClick()
        awaitTag("activity_empty")
        ui.onNodeWithTag("activity_empty").assertTextContains("No hay cambios de estado", substring = true)
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun realCommittedTransitionIsDistinctFromSavingAndOpensItsFolio() {
        val report = seed(1).single()
        val operation = UUID.randomUUID().toString()
        val actor = UUID.randomUUID().toString()
        runBlocking {
            SqliteReportRepository(app, fixture.databaseName, statusAuthority = { actor }).use {
                it.changeStatus(ReportStatusChangeCommand(operation, report.id, 1, ReportStatus.REPORTED,
                    ReportStatus.IN_REVIEW, "Revisión registrada en prueba aislada"))
            }
        }
        launchActivity()
        awaitTag("activity_event_$operation")
        ui.onNodeWithText("Reportado → En revisión").assertExists()
        scrollTo("activity_event_${report.id}")
        ui.onNodeWithText("Estado al guardar: Reportado").assertExists()
        scrollTo("activity_changes"); ui.onNodeWithTag("activity_changes").performClick()
        awaitTag("activity_event_$operation")
        ui.onNodeWithTag("activity_event_${report.id}").assertDoesNotExist()
        scenario!!.recreate()
        awaitTag("activity_event_$operation")
        ui.onNodeWithTag("activity_changes").assertIsSelected()
        ui.onNodeWithTag("activity_open_$operation").performClick()
        ui.waitUntil(15_000) { resumed<ReportsActivity>() }
        ui.waitUntil(15_000) { ui.onAllNodesWithText(report.id, substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun boundedPagesAndRotationKeepTheCorrectCursorWithoutDuplicatingEvents() {
        seed(52)
        val first = runBlocking { fixture.repository.readActivity(ReportActivityQuery()) }
        val second = runBlocking { fixture.repository.readActivity(ReportActivityQuery(before = first.nextBefore)) }
        assertEquals(50, first.entries.size); assertEquals(2, second.entries.size)
        assertTrue(first.hasMore); assertFalse(second.hasMore)
        val initial = first.entries.first().event.operationId
        val next = second.entries.first().event.operationId
        launchActivity(); awaitTag("activity_event_$initial")
        scrollTo("activity_next"); ui.onNodeWithTag("activity_next").performClick()
        awaitTag("activity_event_$next")
        scenario!!.recreate(); awaitTag("activity_event_$next")
        ui.onNodeWithTag("activity_event_$initial").assertDoesNotExist()
        scrollTo("activity_previous"); ui.onNodeWithTag("activity_previous").performClick()
        awaitTag("activity_event_$initial")
        assertEquals(52, runBlocking { fixture.repository.readActivity(ReportActivityQuery(limit = 100)).entries.size })
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun changingAccountsRetiresTheOldScreenAndNeverShowsItsEventsInAnotherAccount() {
        val firstAccount = LocalAccount(UUID.randomUUID().toString(), "actividad_uno")
        val secondAccount = LocalAccount(UUID.randomUUID().toString(), "actividad_dos")
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.accountSession.signIn(firstAccount) }
        fixture.publishFreshFix()
        val owned = runBlocking {
            val workspace = app.openReportWorkspace(app.accountSession.capture())
            try { workspace.repository.createOnce(UUID.randomUUID().toString(), draft()) } finally { workspace.close() }
        }
        launchActivity(); awaitTag("activity_event_${owned.id}")
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.accountSession.signIn(secondAccount) }
        ui.waitUntil(15_000) { scenario!!.state == Lifecycle.State.DESTROYED }
        launchActivity(); awaitTag("activity_empty")
        ui.onNodeWithTag("activity_event_${owned.id}").assertDoesNotExist()
        scenario!!.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.accountSession.signIn(firstAccount) }
        launchActivity(); awaitTag("activity_event_${owned.id}")
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun refreshAtAnEquivalentFirstPageReturnsToTheNewEventAtTheTop() {
        seed(5)
        val first = runBlocking { fixture.repository.readActivity(ReportActivityQuery()).entries.first() }
        launchActivity(); awaitTag("activity_event_${first.event.operationId}")
        scrollTo("activity_refresh")
        val added = seed(1).single()
        ui.onNodeWithTag("activity_refresh").performClick()
        awaitTag("activity_event_${added.id}")
        ui.onNodeWithTag("activity_event_${added.id}").assertIsDisplayed()
        assertEquals(6, runBlocking { fixture.repository.readActivity(ReportActivityQuery()).entries.size })
        assertEquals(0, fixture.refreshCalls)
    }

    private fun seed(count: Int): List<Report> = runBlocking {
        fixture.publishFreshFix()
        (0 until count).map { fixture.repository.createOnce(UUID.randomUUID().toString(), draft()) }
    }
    private fun draft() = NewReport(IncidentType.THEFT, Priority.MEDIUM,
        "Descripción real de la prueba de actividad local", Instant.now().minusSeconds(300),
        ReportLocation(reference = "Referencia controlada de la prueba", latitude = 19.4326, longitude = -99.1332))
    private fun launchActivity() { scenario = ActivityScenario.launch(ReportActivityActivity::class.java) }
    private fun awaitTag(tag: String) = ui.waitUntil(15_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun scrollTo(tag: String) = ui.onNodeWithTag("report_activity").performScrollToNode(hasTestTag(tag))
    private inline fun <reified T : Activity> resumed(): Boolean {
        var found = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is T }
        }
        return found
    }
}
