package com.example.citizensecurity.maps

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.MapRegion
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportMapMarker
import com.example.citizensecurity.domain.ReportStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Operaciones y GeoJSON reales sin renderer; el dibujo se comprueba en el laboratorio Android. */
class MapLibreReportSceneHostTest {
    @Test
    fun geoJsonPreservaOrdenLongitudLatitudYEscapeConSoloPropiedadesMinimas() {
        val marker = marker("folio\"\\\nñ")
        val original = reportMarkerFeatures(listOf(marker))
        val originalPoint = original.features()!!.single().geometry() as Point
        assertEquals(marker.longitude.toRawBits(), originalPoint.longitude().toRawBits())
        assertEquals(marker.latitude.toRawBits(), originalPoint.latitude().toRawBits())

        val parsed = FeatureCollection.fromJson(original.toJson())
        val feature = parsed.features()!!.single()
        val point = feature.geometry() as Point
        // GeoJSON 6.0.1 escribe coordenadas mediante GeoJsonUtils.trim: siete decimales.
        // Medio paso (5e-8 grados), más dos ULP para representación binaria, acota ese redondeo.
        assertEquals(marker.longitude, point.longitude(), 5e-8 + 2 * Math.ulp(marker.longitude))
        assertEquals(marker.latitude, point.latitude(), 5e-8 + 2 * Math.ulp(marker.latitude))
        assertEquals(-99.1332078, point.longitude(), 0.0)
        assertEquals(19.4326081, point.latitude(), 0.0)
        assertEquals(setOf("reportId", "type", "priority", "status"), feature.properties()!!.keySet())
        assertEquals(marker.id, feature.getStringProperty("reportId"))
        assertEquals("THEFT", feature.getStringProperty("type"))
        assertEquals("HIGH", feature.getStringProperty("priority"))
        assertEquals("REPORTED", feature.getStringProperty("status"))
        assertNull(feature.id())
    }

    @Test
    fun regionLocalConservaTodosSusLimitesInclusivos() {
        val region = reportSceneRegion(19.40, 19.50, -99.20, -99.10)!!
        assertEquals(MapRegion(19.40, 19.50, -99.20, -99.10), region)
        assertTrue(region.contains(19.40, -99.20))
        assertTrue(region.contains(19.50, -99.10))
        assertFalse(region.contains(19.45, 0.0))
    }

    @Test
    fun regionCruzaAntimeridianoConLimitesContinuosOCanonicosSinVolverseGlobal() {
        val continuous = reportSceneRegion(-10.0, 10.0, 170.0, 190.0)!!
        assertEquals(MapRegion(-10.0, 10.0, 170.0, -170.0), continuous)
        assertEquals(continuous, reportSceneRegion(-10.0, 10.0, 170.0, -170.0))
        assertEquals(continuous, reportSceneRegion(-10.0, 10.0, -190.0, -170.0))
        assertTrue(continuous.contains(0.0, 179.9))
        assertTrue(continuous.contains(0.0, -179.9))
        assertFalse(continuous.contains(0.0, 0.0))
    }

    @Test
    fun ventanaDeUnaOVariasVueltasIncluyeElMundoEnVezDeColapsarLosExtremos() {
        val world = MapRegion(-85.0, 85.0, -180.0, 180.0)
        assertEquals(world, reportSceneRegion(-85.0, 85.0, -180.0, 180.0))
        assertEquals(world, reportSceneRegion(-85.0, 85.0, 20.0, 380.0))
        assertEquals(world, reportSceneRegion(-85.0, 85.0, -720.0, 720.0))
        assertTrue(world.contains(0.0, -180.0))
        assertTrue(world.contains(0.0, 180.0))
        assertTrue(world.contains(0.0, 0.0))
    }

    @Test
    fun copiasDelMundoYVentanasQueTerminanEn180ConservanElIntervalo() {
        assertEquals(MapRegion(-1.0, 1.0, 170.0, 180.0), reportSceneRegion(-1.0, 1.0, 530.0, 540.0))
        assertEquals(MapRegion(-1.0, 1.0, 170.0, 180.0), reportSceneRegion(-1.0, 1.0, -190.0, -180.0))
        assertEquals(MapRegion(-1.0, 1.0, -180.0, -170.0), reportSceneRegion(-1.0, 1.0, 180.0, 190.0))
        val point = reportSceneRegion(0.0, 0.0, 180.0, 180.0)!!
        assertTrue(point.contains(0.0, -180.0))
        assertTrue(point.contains(0.0, 180.0))
        assertFalse(point.contains(0.0, 179.0))
    }

    @Test
    fun limitesInvalidosNoSeReordenanNiSeUsanParaUnaConsultaGlobal() {
        assertNull(reportSceneRegion(10.0, -10.0, 0.0, 1.0))
        assertNull(reportSceneRegion(-91.0, 1.0, 0.0, 1.0))
        assertNull(reportSceneRegion(0.0, Double.NaN, 0.0, 1.0))
        assertNull(reportSceneRegion(0.0, 1.0, Double.POSITIVE_INFINITY, 1.0))
        assertNull(reportSceneRegion(0.0, 1.0, 540.0, -10.0))
    }

    @Test
    fun estiloPendienteDibujaSoloLaUltimaListaYCopiaElContenidoRecibido() {
        val access = Access().apply { style = null }
        val host = MapLibreReportSceneHost(access)
        val markers = mutableListOf(marker("retirado"))
        host.renderMarkers(markers)
        markers.clear()
        host.renderMarkers(listOf(marker("vigente")))
        assertTrue(access.resources.isEmpty())
        access.style = Any()
        access.loaded()
        assertEquals(listOf("vigente"), access.resources.single().ids())
        access.loaded()
        assertEquals(1, access.resources.size)
        assertEquals(1, access.resources.single().updates)
    }

    @Test
    fun vaciarAntesDeCargarElEstiloNoResucitaMarcadoresPendientes() {
        val access = Access().apply { style = null }
        val host = MapLibreReportSceneHost(access)
        host.renderMarkers(listOf(marker("viejo")))
        host.clearMarkers()
        access.style = Any()
        access.loaded()
        assertTrue(access.resources.isEmpty())
    }

    @Test
    fun nuevaListaSustituyeSinDuplicarCapaYUnaListaVaciaRetiraElContenido() {
        val access = Access()
        val host = MapLibreReportSceneHost(access)
        val markers = mutableListOf(marker("a"), marker("b"))
        host.renderMarkers(markers)
        markers.clear()
        assertEquals(listOf("a", "b"), access.resources.single().ids())
        host.renderMarkers(listOf(marker("c")))
        assertEquals(listOf("c"), access.resources.single().ids())
        assertEquals(1, access.resources.size)
        host.renderMarkers(emptyList())
        assertTrue(access.resources.single().removed)
        access.loaded()
        assertEquals(1, access.resources.size)
    }

    @Test
    fun dosHostsTienenIdsPropiosYConservanRecursosYListenersDelOtro() {
        val access = Access()
        var foreignCalls = 0
        val foreignIdle = MapLibreMap.OnCameraIdleListener { foreignCalls++ }
        val foreignStyle = MapView.OnDidFinishLoadingStyleListener { foreignCalls++ }
        access.idleListeners += foreignIdle
        access.styleListeners += foreignStyle
        val a = MapLibreReportSceneHost(access)
        val b = MapLibreReportSceneHost(access)
        assertNotEquals(a.sourceId, b.sourceId)
        assertNotEquals(a.layerId, b.layerId)
        a.renderMarkers(listOf(marker("a")))
        b.renderMarkers(listOf(marker("b")))
        b.setCameraIdleListener { }
        a.close()
        assertTrue(access.resources[0].removed)
        assertFalse(access.resources[1].removed)
        assertEquals(listOf("b"), access.resources[1].ids())
        assertEquals(2, access.idleListeners.size)
        assertEquals(2, access.styleListeners.size)
        access.idle()
        access.loaded()
        assertEquals(2, foreignCalls)
        b.close()
        assertEquals(listOf(foreignIdle), access.idleListeners)
        assertEquals(listOf(foreignStyle), access.styleListeners)
    }

    @Test
    fun recargaDeEstiloConectaSoloLosDatosVigentesYRetiraSuRecursoAnterior() {
        val access = Access()
        val host = MapLibreReportSceneHost(access)
        host.renderMarkers(listOf(marker("primero")))
        val old = access.resources.single()
        access.style = null
        host.renderMarkers(listOf(marker("nuevo")))
        assertEquals(listOf("primero"), old.ids())
        access.style = Any()
        access.loaded()
        assertTrue(old.removed)
        assertEquals(listOf("nuevo"), access.resources.last().ids())
        assertEquals(2, access.resources.size)
    }

    @Test
    fun cierreInvalidaCallbacksTardiosYNoConsultaNiDibujaDespues() {
        val access = Access()
        val host = MapLibreReportSceneHost(access)
        var idleCalls = 0
        host.setCameraIdleListener { idleCalls++ }
        host.renderMarkers(listOf(marker("a")))
        val staleIdle = access.idleListeners.single()
        val staleStyle = access.styleListeners.single()
        host.close()
        host.close()
        host.renderMarkers(listOf(marker("b")))
        host.clearMarkers()
        host.setCameraIdleListener { idleCalls++ }
        staleIdle.onCameraIdle()
        staleStyle.onDidFinishLoadingStyle()
        assertNull(host.visibleRegion())
        assertEquals(0, access.regionReads)
        assertEquals(0, idleCalls)
        assertEquals(1, access.resources.size)
        assertTrue(access.resources.single().removed)
        assertTrue(access.idleListeners.isEmpty())
        assertTrue(access.styleListeners.isEmpty())
    }

    @Test
    fun reemplazarCallbackRetiraSoloElAnteriorYLoDejaInerte() {
        val access = Access()
        val host = MapLibreReportSceneHost(access)
        var oldCalls = 0
        var newCalls = 0
        host.setCameraIdleListener { oldCalls++ }
        val old = access.idleListeners.single()
        host.setCameraIdleListener { newCalls++ }
        old.onCameraIdle()
        access.idle()
        assertEquals(0, oldCalls)
        assertEquals(1, newCalls)
        host.setCameraIdleListener(null)
        assertTrue(access.idleListeners.isEmpty())
    }

    @Test
    fun registroIdleParcialFallidoQuedaInerteYSeRetiraSinPerderOtro() {
        val access = Access()
        val foreign = MapLibreMap.OnCameraIdleListener { }
        access.idleListeners += foreign
        val host = MapLibreReportSceneHost(access)
        var partial: MapLibreMap.OnCameraIdleListener? = null
        var calls = 0
        val failure = IllegalStateException("falló el SDK")
        access.onAddIdle = { partial = it; throw failure }
        assertSame(failure, expectFailure { host.setCameraIdleListener { calls++ } })
        partial!!.onCameraIdle()
        assertEquals(0, calls)
        assertEquals(listOf(foreign), access.idleListeners)
    }

    @Test
    fun cerrarDuranteRegistroIdleNoDejaCallbackInstalado() {
        val access = Access()
        val host = MapLibreReportSceneHost(access)
        access.onAddIdle = { host.close() }
        host.setCameraIdleListener { fail("No debe entregarse tras close") }
        assertTrue(access.idleListeners.isEmpty())
        assertTrue(access.styleListeners.isEmpty())
    }

    @Test
    fun falloDeInstalacionLimpiaRecursoParcialYPermiteReintentarDatosPendientes() {
        val access = Access()
        val host = MapLibreReportSceneHost(access)
        val failure = IllegalArgumentException("capa no añadida")
        access.onInstall = { throw failure }
        assertSame(failure, expectFailure { host.renderMarkers(listOf(marker("a"))) })
        assertTrue(access.resources.single().removed)
        access.onInstall = null
        access.loaded()
        assertEquals(listOf("a"), access.resources.last().ids())
        assertFalse(access.resources.last().removed)
    }

    @Test
    fun cerrarDuranteInstalacionImpideCompletarCapaYNoResucitaLaLista() {
        val access = Access()
        val host = MapLibreReportSceneHost(access)
        access.onInstall = { host.close() }
        host.renderMarkers(listOf(marker("a")))
        assertTrue(access.resources.single().removed)
        assertFalse(access.resources.single().layerAdded)
        access.loaded()
        assertEquals(1, access.resources.size)
    }

    @Test
    fun sustitucionDuranteInstalacionAplicaLaListaNuevaSinActualizarUnaCapaIncompleta() {
        val access = Access()
        val host = MapLibreReportSceneHost(access)
        access.onInstall = {
            access.onInstall = null
            host.renderMarkers(listOf(marker("nuevo")))
        }
        host.renderMarkers(listOf(marker("viejo")))
        assertTrue(access.resources.first().removed)
        assertFalse(access.resources.first().layerAdded)
        assertEquals(0, access.resources.first().updates)
        assertEquals(listOf("nuevo"), access.resources.last().ids())
        assertTrue(access.resources.last().layerAdded)
    }

    @Test
    fun limpiarDuranteInstalacionImpideQueLaCapaSeAgregueDespues() {
        val access = Access()
        val host = MapLibreReportSceneHost(access)
        access.onInstall = { host.clearMarkers() }
        host.renderMarkers(listOf(marker("a")))
        assertTrue(access.resources.single().removed)
        assertFalse(access.resources.single().layerAdded)
        access.loaded()
        assertEquals(1, access.resources.size)
    }

    @Test
    fun cierreIntentaTodosLosRecursosAunqueFalleRetirarUnListener() {
        val access = Access()
        val host = MapLibreReportSceneHost(access)
        var idleCalls = 0
        host.setCameraIdleListener { idleCalls++ }
        host.renderMarkers(listOf(marker("a")))
        val staleIdle = access.idleListeners.single()
        val failure = IllegalArgumentException("listener no retirado")
        access.onRemoveIdle = { throw failure }
        assertSame(failure, expectFailure { host.close() })
        staleIdle.onCameraIdle()
        assertEquals(0, idleCalls)
        assertTrue(access.styleListeners.isEmpty())
        assertTrue(access.resources.single().removed)
        assertNull(host.visibleRegion())
    }

    private fun marker(id: String) = ReportMapMarker(
        id, IncidentType.THEFT, Priority.HIGH, ReportStatus.REPORTED, 19.4326081234567, -99.1332077654321,
    )

    private fun expectFailure(action: () -> Unit): Exception {
        try { action(); fail("Se esperaba el error original del SDK.") }
        catch (failure: Exception) { return failure }
        error("No hubo error.")
    }

    private class Access : ReportSceneMapAccess {
        var style: Any? = Any()
        var regionReads = 0
        val resources = mutableListOf<Resources>()
        val idleListeners = mutableListOf<MapLibreMap.OnCameraIdleListener>()
        val styleListeners = mutableListOf<MapView.OnDidFinishLoadingStyleListener>()
        var onAddIdle: ((MapLibreMap.OnCameraIdleListener) -> Unit)? = null
        var onRemoveIdle: ((MapLibreMap.OnCameraIdleListener) -> Unit)? = null
        var onInstall: (() -> Unit)? = null

        override fun visibleRegion(): MapRegion {
            regionReads++
            return MapRegion(19.4, 19.5, -99.2, -99.1)
        }
        override fun currentStyleIdentity(): Any? = style
        override fun createResources(style: Any, sourceId: String, layerId: String): Resources =
            Resources(style, this).also { resources += it }
        override fun addIdleListener(listener: MapLibreMap.OnCameraIdleListener) {
            idleListeners += listener
            onAddIdle?.invoke(listener)
        }
        override fun removeIdleListener(listener: MapLibreMap.OnCameraIdleListener) {
            onRemoveIdle?.invoke(listener)
            idleListeners.remove(listener)
        }
        override fun addStyleListener(listener: MapView.OnDidFinishLoadingStyleListener) { styleListeners += listener }
        override fun removeStyleListener(listener: MapView.OnDidFinishLoadingStyleListener) { styleListeners.remove(listener) }
        fun loaded() = styleListeners.toList().forEach { it.onDidFinishLoadingStyle() }
        fun idle() = idleListeners.toList().forEach { it.onCameraIdle() }
    }

    private class Resources(override val styleIdentity: Any, private val access: Access) : ReportSceneResources {
        var features: FeatureCollection? = null
        var layerAdded = false
        var removed = false
        var updates = 0
        override fun install(features: FeatureCollection, stillCurrent: () -> Boolean) {
            if (!stillCurrent()) return
            this.features = features
            access.onInstall?.invoke()
            if (stillCurrent()) layerAdded = true
        }
        override fun update(features: FeatureCollection) {
            check(layerAdded && !removed) { "No actualizar una capa incompleta." }
            updates++
            this.features = features
        }
        override fun remove() { removed = true; layerAdded = false }
        fun ids(): List<String> = features!!.features()!!.map { it.getStringProperty("reportId") }
    }
}
