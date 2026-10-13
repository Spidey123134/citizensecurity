package com.example.citizensecurity.reports

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real isolated report/event stores. Compiled now; grouped Android acceptance is pending. */
class ReportHistoryUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private var scenario: ActivityScenario<ReportsActivity>? = null
    private val fixture get() = fixtureRule.fixture
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun closeOwnedActivity() { scenario?.close() }

    @Test fun explicitHistoryShowsTheRecordedCreationWithoutChangingReportOrStartingGps() {
        val report = seed()
        scenario = ActivityScenario.launch(Intent(context, ReportsActivity::class.java)
            .putExtra(ReportsActivity.EXTRA_FOLIO, report.id))
        awaitTag("history_open")
        assertTrue(ui.onAllNodesWithTag("history_event_1").fetchSemanticsNodes().isEmpty())
        ui.onNodeWithTag("history_open").performScrollTo().performClick()
        awaitTag("history_event_1")
        ui.onNodeWithText("Reporte guardado · Reportado").assertIsDisplayed()
        ui.onNodeWithTag("history_status").assertTextContains("Reportado")
        ui.onNodeWithTag("history_refresh").performScrollTo().performClick()
        awaitTag("history_event_1")
        runBlocking {
            assertEquals(report, fixture.repository.findById(report.id))
            val page = checkNotNull(fixture.repository.readHistory(ReportHistoryQuery(report.id)))
            assertEquals(1, page.events.size)
            assertTrue(page.completeFromCreation)
            assertNull(fixture.journal.read())
        }
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun damagedHistoryShowsItsOwnErrorAndKeepsTheReportDetails() {
        val report = seed()
        SQLiteDatabase.openDatabase(context.getDatabasePath(fixture.databaseName).path, null,
            SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("PRAGMA ignore_check_constraints = ON")
            db.execSQL("UPDATE report_status_events SET recorded_at_nanos = 1000000000 WHERE report_id = ?", arrayOf<Any>(report.id))
        }
        scenario = ActivityScenario.launch(Intent(context, ReportsActivity::class.java)
            .putExtra(ReportsActivity.EXTRA_FOLIO, report.id))
        awaitTag("history_open")
        ui.onNodeWithTag("history_open").performScrollTo().performClick()
        awaitTag("history_error")
        ui.onNodeWithTag("report_description").performScrollTo().assertTextContains(report.description)
        ui.onNodeWithTag("history_retry").performScrollTo().assertIsDisplayed()
        assertEquals(report, runBlocking { fixture.repository.findById(report.id) })
        assertEquals(0, fixture.refreshCalls)
    }

    private fun seed(): Report = runBlocking {
        fixture.publishFreshFix()
        fixture.repository.createOnce(UUID.randomUUID().toString(), NewReport(IncidentType.RISK,
            Priority.MEDIUM, "Hay una luminaria dañada frente al parque.", Instant.now().minusSeconds(30),
            ReportLocation("Frente al parque de prueba", 19.4326, -99.1332)))
    }

    private fun awaitTag(tag: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }
}
