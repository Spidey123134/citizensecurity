package com.example.citizensecurity.reports

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.maps.ReportMapSceneState
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
 * SQLite de prueba aislado → modelos/binding productivos → MapLibre/OpenFreeMap nativo.
 * La evidencia de ubicación de la fixture permite sembrar exclusivamente esta base técnica;
 * no se presenta como GPS físico ni se escribe un reporte en la base del usuario.
 */
@RunWith(AndroidJUnit4::class)
class ReportsMapUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private var scenario: ActivityScenario<ReportsMapActivity>? = null
    private lateinit var risk: Report
    private lateinit var theft: Report

    @Before fun seedOnlyTheTechnicalStore() = runBlocking {
        val fixture = fixtureRule.fixture
        fixture.publishFreshFix()
        risk = fixture.repository.create(draft(IncidentType.RISK, LATITUDE, LONGITUDE,
            "Una luminaria de prueba está dañada frente al parque."))
        theft = fixture.repository.create(draft(IncidentType.THEFT, LATITUDE + 0.0030, LONGITUDE + 0.0030,
            "Reporte ficticio de robo utilizado solamente por esta prueba."))
    }

    @After fun closeTheActivityBeforeItsFixture() {
        scenario?.close()
        scenario = null
    }

    @Test fun explicitQueryDrawsSqliteMarkersOnRealStreetsWithoutStartingGps() {
        val before = storedRows()
        launch()
        awaitMap()
        onActivity {
            assertSame(ReportMapSceneState.Idle, it.currentSceneState)
            assertEquals(0, it.renderedMarkerCount())
        }
        assertEquals(0, fixtureRule.fixture.refreshCalls)
        query()
        awaitScene { it.count == 2 && !it.hasMore }
        awaitMarkers(2)
        awaitNative { it.renderedStreetNames().isNotEmpty() }
        ui.onNodeWithTag("map_marker_count").assertTextEquals("2 marcadores en esta zona")
        capture("release053-mapa-consulta.png")
        assertEquals(before, storedRows())
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun changingFiltersClearsOldPinsAndShowsAnHonestEmptyResult() {
        val before = storedRows()
        launch()
        awaitMap()
        query()
        awaitScene { it.count == 2 }
        awaitMarkers(2)

        ui.onNodeWithTag("map_type_filter").performScrollTo().performClick()
        // null/Todos ocupa 0; THEFT es la tercera entrada de este menú.
        ui.onNodeWithTag("map_type_filter_2").performClick()
        awaitNative { it.currentSceneState == ReportMapSceneState.Idle && it.renderedMarkerCount() == 0 }
        query()
        awaitScene { it.count == 1 && it.query.types == setOf(IncidentType.THEFT) }
        awaitMarkers(1)

        ui.onNodeWithTag("map_status_filter").performScrollTo().performClick()
        // Todos, Reportado, En revisión: las fixtures todavía están en REPORTED.
        ui.onNodeWithTag("map_status_filter_2").performClick()
        awaitNative { it.currentSceneState == ReportMapSceneState.Idle && it.renderedMarkerCount() == 0 }
        query()
        awaitScene { it.count == 0 && !it.hasMore }
        awaitMarkers(0)
        ui.onNodeWithTag("map_reports_message").assertTextContains("Esto no indica que la zona sea segura.", substring = true)
        assertEquals(before, storedRows())
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun openingStoredFolioCentersItsPointAndMarkerOpensTheSameDetail() {
        val before = storedRows()
        launch(risk.id)
        awaitMap()
        awaitNative { activity ->
            val target = activity.cameraTarget()
            target != null && kotlin.math.abs(target.latitude - risk.location.latitude!!) < 0.000001 &&
                kotlin.math.abs(target.longitude - risk.location.longitude!!) < 0.000001
        }
        ui.onNodeWithTag("map_navigation_message").assertTextContains(risk.id, substring = true)
        onActivity {
            assertSame(ReportMapSceneState.Idle, it.currentSceneState)
            assertEquals(0, it.renderedMarkerCount())
        }
        query()
        awaitScene { it.count == 2 }
        awaitMarkers(2)
        ui.onNodeWithTag("reports_map_canvas").performTouchInput { click(center) }
        ui.waitUntil(10_000) {
            ui.onAllNodesWithTag("map_selected_folio").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("map_selected_folio").assertTextContains(risk.id, substring = true)
        ui.onNodeWithTag("open_map_report").performScrollTo().performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithTag("report_folio").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("report_folio").assertTextEquals(risk.id)
        ui.onNodeWithTag("report_description").performScrollTo().assertTextEquals(risk.description)
        // Primero retorna a su lista y después cierra la Activity de detalle, sin dejarla viva.
        ui.onNodeWithTag("report_back_list").performScrollTo().performClick()
        ui.onNodeWithTag("report_back_list").performScrollTo().performClick()
        ui.onNodeWithTag("reports_map_canvas").assertExists()
        awaitNative { it.currentSceneState == ReportMapSceneState.Idle && it.renderedMarkerCount() == 0 }
        assertEquals(before, storedRows())
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun markerLimitReportsTruncationAndRecreationRequiresANewQuery() {
        runBlocking {
            val fixture = fixtureRule.fixture
            fixture.publishFreshFix()
            // Más de 50 puntos junto al centro: el hasMore no depende de que el punto
            // distante del caso de detalle siga dentro de la ventana más baja en horizontal.
            repeat(50) { index ->
                fixture.repository.create(draft(IncidentType.RISK,
                    LATITUDE + (index / 10) * 0.00004, LONGITUDE + (index % 10) * 0.00004,
                    "Marcador ficticio número $index para comprobar el límite de la consulta."))
            }
        }
        val before = storedRows()
        launch()
        awaitMap()
        ui.onNodeWithTag("map_limit_filter").performScrollTo().performClick()
        ui.onNodeWithTag("map_limit_filter_0").performClick()
        val viewportBeforeQuery = ui.onNodeWithTag("reports_map_canvas").fetchSemanticsNode().boundsInRoot
        query()
        awaitScene { it.count == 50 && it.hasMore && it.query.limit == 50 }
        awaitMarkers(50)
        assertEquals("El aviso de truncamiento no debe redimensionar la zona consultada.",
            viewportBeforeQuery, ui.onNodeWithTag("reports_map_canvas").fetchSemanticsNode().boundsInRoot)
        ui.onNodeWithTag("map_reports_message").assertTextContains("Esta muestra no es la lista completa.", substring = true)
        scenario!!.recreate()
        awaitMap()
        onActivity { assertSame(ReportMapSceneState.Idle, it.currentSceneState) }
        awaitMarkers(0)
        ui.onNodeWithTag("map_limit_filter").assertTextContains("50 marcadores", substring = true)
        query()
        awaitScene { it.count == 50 && it.hasMore && it.query.limit == 50 }
        try {
            onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            awaitNative { it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            awaitMap()
            val landscapeViewport = ui.onNodeWithTag("reports_map_canvas").fetchSemanticsNode().boundsInRoot
            assertTrue("El mapa debe conservar altura visible en horizontal.", landscapeViewport.height > 0f)
            ui.onNodeWithTag("query_reports_zone").performScrollTo().assertIsDisplayed().assertIsEnabled()
            onActivity { assertSame(ReportMapSceneState.Idle, it.currentSceneState) }
            query()
            awaitScene { it.count == 50 && it.hasMore && it.query.limit == 50 }
            assertEquals("El resultado horizontal no debe redimensionar el mapa.", landscapeViewport,
                ui.onNodeWithTag("reports_map_canvas").fetchSemanticsNode().boundsInRoot)
        } finally {
            onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            awaitNative { it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        }
        assertEquals(before, storedRows())
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    private fun launch(folio: String? = null) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, ReportsMapActivity::class.java)
        if (folio != null) intent.putExtra(ReportsMapActivity.EXTRA_FOLIO, folio)
        scenario = ActivityScenario.launch(intent)
    }

    private fun query() = ui.onNodeWithTag("query_reports_zone").performScrollTo().performClick()
    private fun awaitMap() = awaitNative { it.reportsMapReady }
    private fun awaitMarkers(count: Int) = awaitNative { it.renderedMarkerCount() == count }
    private fun awaitScene(predicate: (ReportMapSceneState.Ready) -> Boolean) = awaitNative {
        (it.currentSceneState as? ReportMapSceneState.Ready)?.let(predicate) == true
    }

    private fun awaitNative(predicate: (ReportsMapActivity) -> Boolean) {
        try {
            ui.waitUntil(timeoutMillis = 90_000) {
                var ready = false
                scenario!!.onActivity { ready = predicate(it) }
                ready
            }
        } catch (failure: Exception) {
            var details = "No se pudo leer el estado de la Activity."
            runCatching { onActivity { activity ->
                details = "lifecycle=${activity.lifecycle.currentState}; mapReady=${activity.reportsMapReady}; " +
                    "scene=${activity.currentSceneState}; camera=${activity.cameraTarget()}; " +
                    "renderedMarkers=${activity.renderedMarkerCount()}"
            } }
            runCatching { capture("release053-mapa-fallo.png") }
            throw AssertionError("El mapa no alcanzó el estado esperado: $details", failure)
        }
    }

    private fun onActivity(action: (ReportsMapActivity) -> Unit) { scenario!!.onActivity(action) }
    private fun storedRows(): List<Report> = runBlocking { fixtureRule.fixture.repository.list() }

    private fun capture(name: String) {
        ui.waitForIdle()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        var destination: File? = null
        onActivity { destination = File(it.getExternalFilesDir(null), name) }
        requireNotNull(destination).outputStream().use {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
    }

    private fun draft(type: IncidentType, latitude: Double, longitude: Double, description: String) = NewReport(
        type = type, priority = Priority.MEDIUM, description = description,
        occurredAt = Instant.now().minusSeconds(60),
        location = ReportLocation("Referencia ficticia de la prueba nativa", latitude, longitude),
    )

    private companion object {
        const val LATITUDE = 19.4326
        const val LONGITUDE = -99.1332
    }
}
