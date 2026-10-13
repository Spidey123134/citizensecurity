package com.example.citizensecurity.reports

import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.widget.DatePicker
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.testing.ReportTestFixture
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gestos reales de búsqueda sobre SQLite exclusivo de androidTest. La ubicación controlada
 * solo permite sembrar esa base; consultar no renueva GPS, cambia estados ni escribe diarios.
 */
@RunWith(AndroidJUnit4::class)
class ReportsSearchUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private var scenario: ActivityScenario<ReportsActivity>? = null
    private val fixture get() = fixtureRule.fixture
    private val zone = ZoneId.systemDefault()
    private val firstDay = LocalDate.now(zone).minusDays(5)
    private val lastDay = firstDay.plusDays(2)
    private lateinit var first: Report
    private lateinit var middle: Report
    private lateinit var last: Report
    private lateinit var before: List<Report>

    @After fun closeOwnedActivity() {
        scenario?.close()
        scenario = null
    }

    @Test fun descriptionAndReferenceSearchIgnoresAccentsAndCombinesTypeAndLocalStatus() {
        seed()
        launch()
        search("  ARBOL  ")
        awaitCount(4)
        assertCard(first)
        assertCard(middle) // La coincidencia de este robo está solo en la referencia.
        assertCard(last)
        select("report_type_THEFT")
        awaitCount(1)
        select("report_status_IN_REVIEW")
        awaitCount(1)
        assertCard(middle)
        select("report_status_REPORTED")
        awaitCount(0)
        field("report_empty_filtered").assertExists()
        ui.onNodeWithTag("report_no_data").assertDoesNotExist()
        assertReadingOnly()
    }

    @Test fun dateRangeIncludesBothLocalDaysAndUsesIncidentTimeInsteadOfCreationTime() {
        seed()
        launch()
        setField("report_date_from", format(firstDay))
        setField("report_date_to", format(lastDay))
        apply()
        awaitCount(3)
        assertCard(first) // Exactamente a las 00:00 del primer día.
        assertCard(middle)
        assertCard(last) // Último nanosegundo del último día.
        assertTrue("Las filas se guardaron fuera del rango de fecha del incidente.",
            before.all { it.createdAt.atZone(zone).toLocalDate().isAfter(lastDay) })
        setField("report_date_from", format(lastDay))
        apply()
        awaitCount(1)
        assertCard(last)
        assertReadingOnly()
    }

    @Test fun impossibleDateAndReversedRangeShowErrorsWithoutReplacingTheLastValidResults() {
        seed()
        launch()
        search("ARBOL")
        awaitCount(4)
        val validSummary = text("reports_search_summary")
        setField("report_date_from", "31/02/2026")
        apply()
        awaitError()
        awaitCount(4)
        assertEquals(validSummary, text("reports_search_summary"))
        assertCard(middle)
        setField("report_date_from", format(lastDay))
        setField("report_date_to", format(firstDay))
        apply()
        awaitError()
        awaitCount(4)
        assertEquals(validSummary, text("reports_search_summary"))
        scenario!!.recreate()
        awaitCount(4)
        field("report_date_from").assertTextContains(format(lastDay), substring = true)
        field("report_date_to").assertTextContains(format(firstDay), substring = true)
        field("report_search_error").assertExists()
        assertEquals(validSummary, text("reports_search_summary"))
        setField("report_date_to", format(lastDay))
        apply()
        awaitCount(1)
        ui.onNodeWithTag("report_search_error").assertDoesNotExist()
        assertCard(last)
        assertReadingOnly()
    }

    @Test fun rotationAndDetailReturnKeepPendingInputsSeparateFromAppliedSearchAndClearRestoresAll() {
        seed()
        launch()
        setField("report_search_text", "ARBOL")
        setField("report_date_from", format(firstDay))
        setField("report_date_to", format(lastDay))
        apply()
        select("report_type_RISK")
        select("report_status_REPORTED")
        awaitCount(2)
        val applied = text("reports_search_summary")
        // Estos nuevos valores aún no se aplican. No deben sustituir la búsqueda ya aceptada.
        setField("report_search_text", "texto pendiente sin coincidencias")
        setField("report_date_to", "31/02/2026")
        try {
            scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            awaitOrientation(Configuration.ORIENTATION_LANDSCAPE)
            awaitCount(2)
            field("report_search_text").assertTextContains("texto pendiente sin coincidencias", substring = true)
            field("report_date_to").assertTextContains("31/02/2026", substring = true)
            assertEquals(applied, text("reports_search_summary"))
            select("report_open_${first.id}")
            ui.waitUntil(15_000) { ui.onAllNodesWithTag("report_folio").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("report_folio").assertTextEquals(first.id)
            ui.onNodeWithTag("report_back_list").performScrollTo().performClick()
            awaitCount(2)
            field("report_search_text").assertTextContains("texto pendiente sin coincidencias", substring = true)
            field("report_date_to").assertTextContains("31/02/2026", substring = true)
            assertEquals(applied, text("reports_search_summary"))
            select("report_clear_search")
            awaitCount(5)
            assertEmptyField("report_search_text")
            assertEmptyField("report_date_from")
            assertEmptyField("report_date_to")
            field("report_type_ALL").assertIsSelected()
            field("report_status_ALL").assertIsSelected()
            ui.onNodeWithTag("report_search_error").assertDoesNotExist()
            search("texto que no está en ninguno de los reportes")
            awaitCount(0)
            field("report_empty_filtered").assertExists()
            ui.onNodeWithTag("report_no_data").assertDoesNotExist()
            assertReadingOnly()
        } finally {
            scenario?.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            if (scenario != null) awaitOrientation(Configuration.ORIENTATION_PORTRAIT)
        }
    }

    @Test fun emptyUnfilteredPageAndFilteredNoMatchesAreDistinctWithoutGpsOrWrites() {
        before = storedRows()
        launch()
        awaitCount(0)
        field("report_no_data").assertExists()
        search("arból")
        awaitCount(0)
        field("report_empty_filtered").assertExists()
        ui.onNodeWithTag("report_list").performScrollToIndex(0)
        ui.onNodeWithTag("report_no_data").assertDoesNotExist()
        select("report_clear_search")
        awaitCount(0)
        field("report_no_data").assertExists()
        ui.onNodeWithTag("report_empty_filtered").assertDoesNotExist()
        assertReadingOnly()
    }

    @Test fun imeSearchRevealsDateErrorAndKeepsThePreviouslyAppliedResults() {
        seed()
        launch()
        search("ARBOL")
        awaitCount(4)
        val applied = text("reports_search_summary")
        setField("report_date_from", "31/02/2026")
        ui.onNodeWithTag("report_date_from").performImeAction()
        settleInputSurface()
        ui.waitUntil(15_000) {
            ui.onAllNodesWithTag("report_search_error").fetchSemanticsNodes().isNotEmpty()
        }
        // No desplazamiento de prueba: el rechazo debe hacerse visible desde la acción del IME.
        ui.onNodeWithTag("report_search_error").assertIsDisplayed()
            .assertTextContains("Se conservan los resultados", substring = true)
        awaitCount(4)
        assertEquals(applied, text("reports_search_summary"))
        assertReadingOnly()
    }

    @Test fun cancellingTheAndroidCalendarKeepsInputsAndAppliedQueryWithoutWrites() {
        seed()
        launch()
        setField("report_search_text", "ARBOL")
        setField("report_date_from", format(firstDay))
        setField("report_date_to", format(lastDay))
        apply()
        awaitCount(3)
        val applied = text("reports_search_summary")
        select("report_date_to_calendar")
        onView(isAssignableFrom(DatePicker::class.java)).check(matches(isDisplayed()))
        pressBack()
        settleInputSurface()
        field("report_date_to").assertTextContains(format(lastDay), substring = true)
        field("report_date_from").assertTextContains(format(firstDay), substring = true)
        field("report_search_text").assertTextContains("ARBOL", substring = true)
        awaitCount(3)
        assertEquals(applied, text("reports_search_summary"))
        assertReadingOnly()
    }

    @Test fun oversizedPastesKeepPreviousInputsAndResultsAcrossRecreationWhileExactLimitsAreAccepted() {
        seed()
        launch()
        setField("report_search_text", "ARBOL")
        setField("report_date_from", format(firstDay))
        setField("report_date_to", format(lastDay))
        setField("report_search", first.id)
        apply()
        awaitCount(3)
        val applied = text("reports_search_summary")

        setField("report_search_text", "x".repeat(513))
        field("report_search_error").assertTextContains("512", substring = true)
        assertEditableField("report_search_text", "ARBOL")
        awaitCount(3)
        assertEquals(applied, text("reports_search_summary"))

        setField("report_date_from", "x".repeat(33))
        field("report_search_error").assertTextContains("32", substring = true)
        assertEditableField("report_date_from", format(firstDay))
        awaitCount(3)
        assertEquals(applied, text("reports_search_summary"))

        setField("report_search", "x".repeat(65))
        field("report_search_error").assertTextContains("64", substring = true)
        assertEditableField("report_search", first.id)
        awaitCount(3)
        assertEquals(applied, text("reports_search_summary"))

        settleInputSurface()
        scenario!!.recreate()
        awaitCount(3)
        assertEditableField("report_search_text", "ARBOL")
        assertEditableField("report_date_from", format(firstDay))
        assertEditableField("report_date_to", format(lastDay))
        assertEditableField("report_search", first.id)
        field("report_search_error").assertTextContains("64", substring = true)
        assertEquals(applied, text("reports_search_summary"))

        val exactText = "ARBOL" + " ".repeat(507)
        val exactDate = format(firstDay) + " ".repeat(22)
        val exactFolio = first.id + " ".repeat(28)
        setField("report_search_text", exactText)
        setField("report_date_from", exactDate)
        setField("report_search", exactFolio)
        assertEditableField("report_search_text", exactText)
        assertEditableField("report_date_from", exactDate)
        assertEditableField("report_search", exactFolio)
        apply()
        awaitCount(3)
        assertEquals(applied, text("reports_search_summary"))
        ui.onNodeWithTag("report_search_error").assertDoesNotExist()
        assertReadingOnly()
    }

    @Test fun pagedHistoryReplacesResultsAndDetailReturnKeepsTheCurrentPage() {
        seedPagedHistory(70)
        launch()
        awaitCount(50)
        field("report_previous_page").assertIsNotEnabled()
        assertCard(before.first())
        assertCard(before[49])
        field("report_next_page").assertIsEnabled()

        select("report_next_page")
        awaitCount(20, page = 2) // La segunda página reemplaza las 50 filas anteriores.
        assertCard(before[50])
        assertCard(before.last())
        field("report_next_page").assertIsNotEnabled()
        field("report_previous_page").assertIsEnabled()

        val selected = before[50]
        select("report_open_${selected.id}")
        ui.waitUntil(15_000) { ui.onAllNodesWithTag("report_folio").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("report_folio").assertTextEquals(selected.id)
        ui.onNodeWithTag("report_back_list").performScrollTo().performClick()
        awaitCount(20, page = 2)
        assertCard(selected)
        field("report_next_page").assertIsNotEnabled()

        select("report_previous_page")
        awaitCount(50)
        assertCard(before.first())
        assertCard(before[49])
        field("report_previous_page").assertIsNotEnabled()
        field("report_next_page").assertIsEnabled()
        assertReadingOnly()
    }

    @Test fun repeatedExplicitSearchAndClearRestartAtTheFirstPage() {
        seedPagedHistory(70)
        launch()
        awaitCount(50)
        select("report_next_page")
        awaitCount(20, page = 2)
        apply() // Los campos aplicados no cambian; el gesto sí debe reiniciar.
        awaitCount(50)
        assertCard(before.first())
        field("report_previous_page").assertIsNotEnabled()
        select("report_next_page")
        awaitCount(20, page = 2)
        select("report_clear_search")
        awaitCount(50)
        assertCard(before.first())
        field("report_previous_page").assertIsNotEnabled()
        assertReadingOnly()
    }

    private fun seedPagedHistory(count: Int) = runBlocking {
        fixture.publishFreshFix()
        val occurredAt = firstDay.atTime(12, 0).atZone(zone).toInstant()
        repeat(count) { index ->
            fixture.repository.create(draft(IncidentType.RISK, occurredAt,
                "Un árbol obstruye la entrada del parque, reporte ${index + 1}.",
                "Entrada del parque, referencia ${index + 1}"))
        }
        before = storedRows() // Orden real de SQLite, incluidos los empates por nanosegundos/folio.
        assertEquals(count, before.size)
        fixture.clearFix()
    }

    private fun seed() = runBlocking {
        fixture.publishFreshFix()
        val start = firstDay.atStartOfDay(zone).toInstant()
        val endExclusive = lastDay.plusDays(1).atStartOfDay(zone).toInstant()
        first = fixture.repository.create(draft(IncidentType.RISK, start,
            "Un Árbol obstruye la entrada de la escuela.", "Entrada norte de la escuela"))
        middle = fixture.repository.create(draft(IncidentType.THEFT,
            firstDay.plusDays(1).atTime(12, 0).atZone(zone).toInstant(),
            "Robo observado junto a la parada del transporte.", "Plaza del Árbol"))
        last = fixture.repository.create(draft(IncidentType.RISK, endExclusive.minusNanos(1),
            "Un cable quedó caído frente a la entrada del parque.", "Cruce del Árbol"))
        val preceding = fixture.repository.create(draft(IncidentType.RISK, start.minusNanos(1),
            "Un árbol bloqueó el acceso antes del intervalo solicitado.", "Límite anterior del intervalo"))
        val following = fixture.repository.create(draft(IncidentType.THEFT, endExclusive,
            "Robo observado después del intervalo solicitado.", "Límite posterior del intervalo"))
        // Estados exclusivos de estas filas técnicas; ninguna ruta del producto los cambia.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SQLiteDatabase.openDatabase(context.getDatabasePath(fixture.databaseName).absolutePath,
            null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            listOf(middle to ReportStatus.IN_REVIEW, preceding to ReportStatus.IN_REVIEW,
                following to ReportStatus.CLOSED).forEach { (report, status) ->
                assertEquals(1, database.update("reports", ContentValues().apply {
                    put("status", status.name)
                }, "id = ?", arrayOf(report.id)))
            }
        }
        before = storedRows()
        fixture.clearFix() // Una consulta no puede depender de la evidencia utilizada al sembrar.
    }

    private fun draft(type: IncidentType, occurredAt: Instant, description: String, reference: String) = NewReport(
        type = type, priority = Priority.MEDIUM, description = description, occurredAt = occurredAt,
        location = ReportLocation(reference, ReportTestFixture.LATITUDE, ReportTestFixture.LONGITUDE),
    )

    private fun launch() { scenario = ActivityScenario.launch(ReportsActivity::class.java) }
    private fun storedRows(): List<Report> = runBlocking { fixture.repository.list() }
    private fun format(day: LocalDate): String = day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    private fun field(tag: String): SemanticsNodeInteraction {
        val row = when {
            tag.startsWith("report_type_") -> "report_type_filters"
            tag.startsWith("report_status_") -> "report_status_filters"
            else -> null
        }
        if (row == null) ui.onNodeWithTag("report_list").performScrollToNode(hasTestTag(tag))
        else {
            ui.onNodeWithTag("report_list").performScrollToNode(hasTestTag(row))
            ui.onNodeWithTag(row).performScrollToNode(hasTestTag(tag))
        }
        ui.waitForIdle()
        return ui.onNodeWithTag(tag)
    }
    private fun setField(tag: String, value: String) {
        settleInputSurface()
        field(tag).performTextReplacement(value)
        ui.waitForIdle()
    }
    private fun select(tag: String) {
        settleInputSurface()
        field(tag)
        ui.waitForIdle()
        // Releer el nodo después del desplazamiento evita tocar la composición anterior.
        try {
            ui.onNodeWithTag(tag).assertIsDisplayed().performClick()
        } catch (failure: AssertionError) {
            ui.onNodeWithTag("report_list").printToLog("Search057")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if (bitmap != null) {
                java.io.File(context.getExternalFilesDir(null), "release057-search-control-failure.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            throw failure
        }
    }
    private fun apply() = select("report_apply_search")
    private fun search(value: String) { setField("report_search_text", value); apply() }
    private fun assertCard(report: Report) { field("list_card_${report.id}").assertExists() }
    private fun text(tag: String): String = field(tag).fetchSemanticsNode().config[SemanticsProperties.Text]
        .joinToString("") { it.text }
    private fun assertEmptyField(tag: String) = assertEditableField(tag, "")
    private fun assertEditableField(tag: String, expected: String) = field(tag).assert(
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString(expected)),
    )
    private fun awaitCount(visible: Int, page: Int = 1) {
        ui.waitUntil(15_000) {
            // Una lectura inicial aún puede estar pendiente y no tener el item de contador.
            runCatching {
                field("report_result_count")
                ui.onAllNodes(hasTestTag("report_result_count") and hasText("Página $page: $visible coincidencias"))
                    .fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        ui.onNodeWithTag("report_result_count").assertTextEquals("Página $page: $visible coincidencias")
    }
    private fun awaitError() {
        field("report_search_error")
        assertTrue("El error de fechas debe explicar el rechazo.", text("report_search_error").isNotBlank())
    }
    private fun awaitOrientation(expected: Int) {
        ui.waitUntil(15_000) {
            var ready = false
            scenario!!.onActivity { activity ->
                ready = activity.resources.configuration.orientation == expected &&
                    activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED &&
                    activity.window.decorView.hasWindowFocus() && activity.window.decorView.isLaidOut
            }
            ready
        }
        ui.waitForIdle()
    }
    private fun settleInputSurface() {
        ui.waitForIdle()
        // API 37 may omit Espresso's result callback when IME is already hidden.
        // Use Android's public insets controller, then check the actual window state.
        scenario!!.onActivity { activity ->
            androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        ui.waitUntil(15_000) {
            var ready = false
            scenario!!.onActivity { activity ->
                val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ready = activity.lifecycle.currentState == androidx.lifecycle.Lifecycle.State.RESUMED &&
                    activity.window.decorView.hasWindowFocus() && activity.window.decorView.isLaidOut &&
                    insets?.isVisible(WindowInsetsCompat.Type.ime()) != true
            }
            ready
        }
        ui.waitForIdle()
    }
    private fun assertReadingOnly() {
        assertEquals(before, storedRows())
        assertEquals(0, fixture.refreshCalls)
        assertNull(fixture.currentFix())
        assertNull(runBlocking { fixture.journal.read() })
        assertNull(runBlocking { fixture.checkpoint.read() })
    }
}
