package com.example.citizensecurity.maps

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.MapRegion
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportMapMarker
import com.example.citizensecurity.domain.ReportMapPage
import com.example.citizensecurity.domain.ReportMapQuery
import com.example.citizensecurity.domain.ReportStatus
import java.time.Instant
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReportMapVisiblePageTest {
    private val region = MapRegion(19.0, 20.0, -100.0, -99.0)
    private val start = Instant.ofEpochSecond(1_000, 123)
    private val end = Instant.ofEpochSecond(2_000, 456)
    private val query = ReportMapQuery(region, startInclusive = start, endExclusive = end)
    private val marker = ReportMapMarker("folio", IncidentType.RISK, Priority.MEDIUM,
        ReportStatus.REPORTED, 19.5, -99.5)

    @Test
    fun readyCoherenteExponeLaMismaPaginaIncluidoResultadoVacio() {
        for (page in listOf(ReportMapPage(listOf(marker), true), ReportMapPage(emptyList(), false))) {
            assertSame(page, currentVisibleMapPage(
                ReportMapSceneState.Ready(query, page.markers.size, page.hasMore),
                ReportMapState.Ready(query, page)))
        }
    }

    @Test
    fun cargaErrorOEscenaRetiradaNoExponePaginaAnterior() {
        val page = ReportMapPage(listOf(marker), false)
        val reading = ReportMapState.Ready(query, page)
        val sceneReady = ReportMapSceneState.Ready(query, 1, false)
        val unavailableScenes = listOf(
            ReportMapSceneState.Idle, ReportMapSceneState.Loading(query),
            ReportMapSceneState.Error(query, "Fallo"), ReportMapSceneState.ViewportUnavailable,
            ReportMapSceneState.Closed,
        )
        unavailableScenes.forEach { assertNull(currentVisibleMapPage(it, reading)) }
        val unavailableReadings = listOf(
            ReportMapState.Idle, ReportMapState.Loading(query, reading),
            ReportMapState.Error(query, "Fallo", reading),
        )
        unavailableReadings.forEach { assertNull(currentVisibleMapPage(sceneReady, it)) }
    }

    @Test
    fun distintaRegionOFechaInclusoPorUnNanosegundoNoExponeLaPagina() {
        val page = ReportMapPage(listOf(marker), false)
        val scene = ReportMapSceneState.Ready(query, 1, false)
        val otherQueries = listOf(
            ReportMapQuery(MapRegion(20.0, 21.0, -100.0, -99.0), startInclusive = start, endExclusive = end),
            ReportMapQuery(region, startInclusive = start.plusNanos(1), endExclusive = end),
            ReportMapQuery(region, startInclusive = start, endExclusive = end.plusNanos(1)),
            ReportMapQuery(region),
            ReportMapQuery(region, startInclusive = start, endExclusive = end, priorities = setOf(Priority.HIGH)),
        )
        otherQueries.forEach { assertNull(currentVisibleMapPage(scene, ReportMapState.Ready(it, page))) }
    }

    @Test
    fun conteoOExcesoDistintoImpidePresentarLaListaComoLaLecturaDelMapa() {
        val reading = ReportMapState.Ready(query, ReportMapPage(listOf(marker), true))
        assertNull(currentVisibleMapPage(ReportMapSceneState.Ready(query, 0, true), reading))
        assertNull(currentVisibleMapPage(ReportMapSceneState.Ready(query, 2, true), reading))
        assertNull(currentVisibleMapPage(ReportMapSceneState.Ready(query, 1, false), reading))
    }

    @Test
    fun gestoDuranteMovimientoDeCamaraNoAbreMuestraDeOtraRegion() {
        val page = ReportMapPage(listOf(marker), false)
        val scene = ReportMapSceneState.Ready(query, 1, false)
        val reading = ReportMapState.Ready(query, page)
        assertSame(page, currentVisibleMapPageInRegion(scene, reading, region))
        assertNull(currentVisibleMapPageInRegion(scene, reading, MapRegion(20.0, 21.0, -100.0, -99.0)))
        assertNull(currentVisibleMapPageInRegion(scene, reading, null))
        assertNull(currentVisibleMapPageInRegion(ReportMapSceneState.Loading(query), reading, region))
        assertNull(currentVisibleMapPageInRegion(scene, ReportMapState.Loading(query, reading), region))
    }
}
