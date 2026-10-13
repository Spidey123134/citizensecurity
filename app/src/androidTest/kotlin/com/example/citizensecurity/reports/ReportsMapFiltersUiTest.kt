package com.example.citizensecurity.reports

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.maps.ReportMapSceneState
import com.example.citizensecurity.testing.ReportTestFixture
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real date/priority controls and native map; only the UUID-owned fixture is seeded. */
@RunWith(AndroidJUnit4::class)
class ReportsMapFiltersUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private var scenario: ActivityScenario<ReportsMapActivity>? = null
    private val fixture get() = fixtureRule.fixture
    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.now(zone).minusDays(3)
    private val start = day.atStartOfDay(zone).toInstant()
    private val end = day.plusDays(1).atStartOfDay(zone).toInstant()
    private lateinit var first: Report
    private lateinit var last: Report
    private lateinit var rowsBefore: List<Report>

    @Before fun seedOnlyOwnedReports() = runBlocking {
        fixture.publishFreshFix()
        first = create(Priority.HIGH, start, 0.0, "Árbol caído al inicio del día de la prueba.")
        last = create(Priority.LOW, end.minusNanos(1), 0.0004, "Luminaria rota al final del día de la prueba.")
        create(Priority.MEDIUM, start.plusSeconds(3600), 0.0007, "Alcantarilla dañada dentro del día de la prueba.")
        create(Priority.HIGH, start.minusNanos(1), -0.0004, "Incidente anterior que debe quedar fuera de las fechas.")
        rowsBefore = fixture.repository.list()
        fixture.clearFix()
    }

    @After fun closeOwnedActivity() { scenario?.close(); scenario = null }

    @Test fun inclusiveIncidentDatesAndPriorityInvalidateTheSampleUntilExplicitQuery() {
        launch()
        awaitIdle()
        query()
        awaitReady(4)
        setField("map_date_from", format(day))
        setField("map_date_to", format(day))
        // Editing is separate from applying: the previously queried four rows still belong here.
        awaitReady(4)
        click("apply_map_dates")
        awaitIdle()
        ui.onNodeWithTag("open_map_zone_list").performScrollTo().assertIsNotEnabled()
        query()
        awaitReady(3) { it.query.startInclusive == start && it.query.endExclusive == end }

        choosePriority(Priority.HIGH)
        awaitIdle()
        ui.onNodeWithTag("open_map_zone_list").performScrollTo().assertIsNotEnabled()
        query()
        awaitReady(1) { it.query.priorities == setOf(Priority.HIGH) && it.query.startInclusive == start }
        click("open_map_zone_list")
        ui.onNodeWithTag("map_zone_report_${first.id}").assertIsDisplayed()
        ui.onNodeWithTag("map_zone_report_${last.id}").assertDoesNotExist()
        ui.onNodeWithTag("map_zone_report_${first.id}").assertTextContains("Prioridad: Alta", substring = true)
        ui.onNodeWithText("Volver al mapa").performClick()

        choosePriority(Priority.LOW)
        awaitIdle()
        query()
        awaitReady(1) { it.query.priorities == setOf(Priority.LOW) }
        click("open_map_zone_list")
        ui.onNodeWithTag("map_zone_report_${last.id}").assertIsDisplayed()
        ui.onNodeWithTag("map_zone_report_${first.id}").assertDoesNotExist()
        ui.onNodeWithTag("map_zone_report_${last.id}").assertTextContains("Prioridad: Baja", substring = true)
        ui.onNodeWithText("Volver al mapa").performClick()
        assertReadingOnly()
    }

    @Test fun invalidDateKeepsTheAppliedSampleAndRotationRequiresAnotherExplicitQuery() {
        launch()
        setField("map_date_from", format(day))
        setField("map_date_to", format(day))
        click("apply_map_dates")
        choosePriority(Priority.HIGH)
        query()
        awaitReady(1) { it.query.startInclusive == start && it.query.endExclusive == end }
        val appliedBefore = text("map_applied_dates")
        var queryBefore: ReportMapQuery? = null
        scenario!!.onActivity { queryBefore = (it.currentSceneState as ReportMapSceneState.Ready).query }

        setField("map_date_from", "31/02/2026")
        click("apply_map_dates")
        ui.onNodeWithTag("map_date_error").performScrollTo()
            .assertTextContains("Se conserva la consulta anterior.", substring = true)
        assertEquals(appliedBefore, text("map_applied_dates"))
        awaitReady(1) { it.query == queryBefore }
        click("open_map_zone_list")
        ui.onNodeWithTag("map_zone_report_${first.id}").assertIsDisplayed()
        ui.onNodeWithTag("map_zone_report_${last.id}").assertDoesNotExist()
        ui.onNodeWithText("Volver al mapa").performClick()

        scenario!!.recreate()
        awaitIdle()
        ui.onNodeWithTag("map_date_from").performScrollTo().assertTextContains("31/02/2026", substring = true)
        assertEquals(appliedBefore, text("map_applied_dates"))
        ui.onNodeWithTag("map_priority_filter").performScrollTo().assertTextContains("Alta", substring = true)
        ui.onNodeWithTag("open_map_zone_list").performScrollTo().assertIsNotEnabled()
        query()
        awaitReady(1) { it.query.startInclusive == start && it.query.endExclusive == end && it.query.priorities == setOf(Priority.HIGH) }
        assertReadingOnly()
    }

    private suspend fun create(priority: Priority, occurredAt: Instant, offset: Double, description: String) =
        fixture.repository.create(NewReport(IncidentType.RISK, priority, description, occurredAt,
            ReportLocation("Referencia exclusiva del mapa de prueba", ReportTestFixture.LATITUDE + offset,
                ReportTestFixture.LONGITUDE + offset)))

    private fun launch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(Intent(context, ReportsMapActivity::class.java))
        awaitNative { it.reportsMapReady }
    }
    private fun format(value: LocalDate) = value.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
    private fun query() = click("query_reports_zone")
    private fun choosePriority(priority: Priority) {
        click("map_priority_filter")
        ui.onNodeWithTag("map_priority_filter_${priority.ordinal + 1}").performClick()
    }
    private fun click(tag: String) {
        settleInputSurface()
        ui.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
    }
    private fun setField(tag: String, value: String) {
        settleInputSurface()
        ui.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
        settleInputSurface()
    }
    private fun text(tag: String): String = ui.onNodeWithTag(tag).performScrollTo()
        .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
    private fun awaitIdle() = awaitNative {
        it.reportsMapReady && it.currentSceneState === ReportMapSceneState.Idle && it.renderedMarkerCount() == 0
    }
    private fun awaitReady(count: Int, additional: (ReportMapSceneState.Ready) -> Boolean = { true }) = awaitNative {
        val state = it.currentSceneState as? ReportMapSceneState.Ready
        state != null && state.count == count && !state.hasMore && it.renderedMarkerCount() == count && additional(state)
    }
    private fun awaitNative(predicate: (ReportsMapActivity) -> Boolean) {
        ui.waitUntil(90_000) {
            var ready = false
            scenario!!.onActivity { ready = predicate(it) }
            ready
        }
    }
    private fun settleInputSurface() {
        ui.waitForIdle()
        scenario!!.onActivity { activity ->
            WindowCompat.getInsetsController(activity.window, activity.window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        ui.waitUntil(15_000) {
            var ready = false
            scenario!!.onActivity { activity ->
                ready = activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED &&
                    activity.window.decorView.hasWindowFocus() && activity.window.decorView.isLaidOut &&
                    ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) != true
            }
            ready
        }
        ui.waitForIdle()
    }
    private fun assertReadingOnly() {
        assertEquals(rowsBefore, runBlocking { fixture.repository.list() })
        assertEquals(0, fixture.refreshCalls)
        assertNull(fixture.currentFix())
        assertNull(runBlocking { fixture.journal.read() })
        assertNull(runBlocking { fixture.checkpoint.read() })
    }
}
