package com.example.citizensecurity.verification.maps

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.maps.MapsConfiguration
import com.example.citizensecurity.maps.ReportMapSceneState
import com.example.citizensecurity.maps.ReportMapState
import java.io.Closeable
import java.io.File
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * End to end nativo: SQLite real en memoria → modelo productivo → puente → GeoJSON/capa SDK.
 * Las fixtures se guardan solo por llamadas explícitas de estas pruebas dentro de testOnly.
 * Se requiere OpenFreeMap live. No obtiene evidencia GPS ni abre datos del producto.
 */
@RunWith(AndroidJUnit4::class)
class ReportMapSceneNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun consultarPorGestoDibujaDosReportesSQLiteConDatosMinimosSobreLiberty() = withHarness { scenario, activity, fixtures ->
        assertTechnicalIsolation()
        assertEquals(0, activity.store.recording.completedReads.get())
        assertTrue(activity.store.recording.queries.isEmpty())
        // Incluso un evento nativo de cámara antes del botón debe dejar la base sin consultas.
        moveCamera(scenario, MapZoneHarnessActivity.TEST_LATITUDE, MapZoneHarnessActivity.TEST_LONGITUDE, 15.6)
        assertEquals(0, activity.store.recording.completedReads.get())
        scenario.onActivity { assertSame(ReportMapSceneState.Idle, it.latestState) }

        var buttonX = 0
        var buttonY = 0
        scenario.onActivity {
            val screen = IntArray(2)
            it.queryButton.getLocationOnScreen(screen)
            buttonX = screen[0] + it.queryButton.width / 2
            buttonY = screen[1] + it.queryButton.height / 2
        }
        assertTrue("El botón nativo debe recibir un gesto real.", device.click(buttonX, buttonY))
        awaitSceneReady(scenario) { it.count == 2 && !it.hasMore }
        awaitRenderedIds(scenario, fixtures.idsA)
        scenario.onActivity {
            val ready = it.latestState as ReportMapSceneState.Ready
            assertTrue(ready.query.region.contains(fixtures.a1.location.latitude!!, fixtures.a1.location.longitude!!))
            assertTrue(ready.query.region.contains(fixtures.a2.location.latitude!!, fixtures.a2.location.longitude!!))
            assertFalse(ready.query.region.contains(fixtures.b.location.latitude!!, fixtures.b.location.longitude!!))
            assertEquals(1, it.store.recording.completedReads.get())
            assertEquals(1, it.store.recording.queries.size)
            val features = it.renderedMarkerFeatures()
            assertEquals(2, features.size)
            features.forEach { feature ->
                assertEquals(setOf("reportId", "type", "priority", "status"), requireNotNull(feature.properties()).keySet())
                val original = fixtures.inA.single { report -> report.id == feature.getStringProperty("reportId") }
                val point = feature.geometry() as Point
                // El SDK reconvierte posiciones int16 de tesela, no el double original de SQLite.
                // Zoom 15: un paso 360/(8192*2^15) + redondeo JSON de 7 decimales < 1.4e-6°.
                // Fuente 13.6.1: src/mln/tile/geometry_tile_data.cpp y include/mln/util/constants.hpp.
                assertEquals(original.location.longitude!!, point.longitude(), 0.0000014)
                assertEquals(original.location.latitude!!, point.latitude(), 0.0000014)
                assertEquals(original.type.name, feature.getStringProperty("type"))
                assertEquals(original.priority.name, feature.getStringProperty("priority"))
                assertEquals(ReportStatus.REPORTED.name, feature.getStringProperty("status"))
            }
            assertTrue(it.renderedBaseFeatures().isComplete)
        }
        saveNativeZoneEvidence(scenario, fixtures)
    }

    @Test
    fun cambiarCamaraConsultaOtraZonaUnaVezYRetiraLosIdsAnteriores() = withHarness { scenario, activity, fixtures ->
        refresh(scenario)
        awaitSceneReady(scenario) { it.count == 2 }
        awaitRenderedIds(scenario, fixtures.idsA)
        assertEquals(1, activity.store.recording.completedReads.get())

        moveCamera(scenario, fixtures.b.location.latitude!!, fixtures.b.location.longitude!!, MapZoneHarnessActivity.TEST_ZOOM)
        awaitSceneReady(scenario) {
            it.count == 1 && it.query.region.contains(fixtures.b.location.latitude!!, fixtures.b.location.longitude!!)
        }
        awaitRenderedIds(scenario, setOf(fixtures.b.id))
        scenario.onActivity {
            val query = (it.latestState as ReportMapSceneState.Ready).query
            fixtures.inA.forEach { old -> assertFalse(query.region.contains(old.location.latitude!!, old.location.longitude!!)) }
            assertEquals(2, it.store.recording.completedReads.get())
            assertEquals(2, it.store.recording.queries.size)
            assertEquals(setOf(fixtures.b.id), it.renderedMarkerIds())
            assertTrue(it.renderedMarkerIds().intersect(fixtures.idsA).isEmpty())
        }
    }

    @Test
    fun limiteFiltrosYRespuestaVaciaSustituyenLaCapaSinModificarEstados() = withHarness { scenario, activity, fixtures ->
        scenario.onActivity { assertTrue(requireNotNull(it.binding).refreshVisible(limit = 1)) }
        awaitSceneReady(scenario) { it.count == 1 && it.hasMore }
        awaitRendered(scenario) { it.size == 1 && fixtures.idsA.containsAll(it) }
        scenario.onActivity {
            assertEquals(1, (it.latestState as ReportMapSceneState.Ready).query.limit)
            assertTrue((it.latestState as ReportMapSceneState.Ready).hasMore)
            assertTrue(requireNotNull(it.binding).refreshVisible(types = setOf(IncidentType.THEFT)))
        }
        awaitSceneReady(scenario) { it.count == 1 && !it.hasMore && it.query.types == setOf(IncidentType.THEFT) }
        awaitRenderedIds(scenario, setOf(fixtures.a1.id))
        scenario.onActivity {
            assertTrue(requireNotNull(it.binding).refreshVisible(statuses = setOf(ReportStatus.CLOSED)))
        }
        awaitSceneReady(scenario) { it.count == 0 && !it.hasMore && it.query.statuses == setOf(ReportStatus.CLOSED) }
        awaitRenderedIds(scenario, emptySet())
        scenario.onActivity {
            val host = requireNotNull(it.host)
            val style = requireNotNull(requireNotNull(it.map).style)
            assertNull(style.getLayer(host.layerId))
            assertNull(style.getSource(host.sourceId))
            assertEquals(3, it.store.recording.completedReads.get())
        }
        // Se utilizó un filtro, no una edición administrativa de las fixtures.
        runBlocking {
            assertTrue(activity.store.sqlite.list().all { it.status == ReportStatus.REPORTED })
            assertEquals(3, activity.store.sqlite.list().size)
        }
    }

    @Test
    fun recargarEstiloReatachaMarcadoresSinConsultarYCerrarConservaRecursosAjenos() = withHarness { scenario, activity, fixtures ->
        refresh(scenario)
        awaitSceneReady(scenario) { it.count == 2 }
        awaitRenderedIds(scenario, fixtures.idsA)
        lateinit var ownLayer: String
        lateinit var ownSource: String
        scenario.onActivity {
            ownLayer = requireNotNull(it.host).layerId
            ownSource = requireNotNull(it.host).sourceId
            it.reloadRealStyle()
        }
        awaitRealMap(activity)
        awaitRenderedIds(scenario, fixtures.idsA)
        assertEquals("Recargar solo el estilo conserva los datos ya leídos.", 1, activity.store.recording.completedReads.get())

        val siblingIdle = CountDownLatch(1)
        val siblingListener = MapLibreMap.OnCameraIdleListener { siblingIdle.countDown() }
        val foreignSourceId = "lab-foreign-source"
        val foreignLayerId = "lab-foreign-layer"
        scenario.onActivity {
            val map = requireNotNull(it.map)
            val style = requireNotNull(map.style)
            val foreignSource = GeoJsonSource(foreignSourceId, FeatureCollection.fromFeatures(emptyList<Feature>()))
            val foreignLayer = CircleLayer(foreignLayerId, foreignSourceId)
                .withProperties(PropertyFactory.circleRadius(4f))
            assertNotNull(style.getLayer(ownLayer))
            assertNotNull(style.getSource(ownSource))
            style.addSource(foreignSource)
            style.addLayer(foreignLayer)
            map.addOnCameraIdleListener(siblingListener)
            it.closeScene()
            assertSame(ReportMapSceneState.Closed, it.latestState)
            assertNull(style.getLayer(ownLayer))
            assertNull(style.getSource(ownSource))
            assertSame(foreignLayer, style.getLayer(foreignLayerId))
            assertSame(foreignSource, style.getSource(foreignSourceId))
            map.moveCamera(CameraUpdateFactory.zoomTo(MapZoneHarnessActivity.TEST_ZOOM + 0.2))
        }
        assertTrue("El listener ajeno sigue recibiendo camera idle.", siblingIdle.await(5, TimeUnit.SECONDS))
        scenario.onActivity {
            requireNotNull(it.map).removeOnCameraIdleListener(siblingListener)
            assertEquals(1, it.store.recording.completedReads.get())
            assertTrue(it.renderedMarkerIds().isEmpty())
            assertSame(ReportMapSceneState.Closed, requireNotNull(it.binding).state)
        }
    }

    @Test
    fun detenerVistaCancelaLecturaPendienteYLuegoRequiereOtroGesto() = withHarness { scenario, activity, fixtures ->
        refresh(scenario)
        awaitSceneReady(scenario) { it.count == 2 }
        awaitRenderedIds(scenario, fixtures.idsA)
        val gate = activity.store.recording.holdNextQuery()
        refresh(scenario)
        assertTrue("La segunda lectura debe llegar a SQLite y quedar pendiente de entrega.", gate.readCompleted.await(5, TimeUnit.SECONDS))
        scenario.onActivity { assertTrue(it.latestState is ReportMapSceneState.Loading) }
        scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue("ON_PAUSE/STOP debe cancelar la entrega de la lectura retirada.", gate.cancelled.await(5, TimeUnit.SECONDS))
        scenario.onActivity {
            assertSame(ReportMapSceneState.Idle, it.latestState)
            assertSame(ReportMapState.Idle, it.reportModel.state.value)
            val host = requireNotNull(it.host)
            val style = requireNotNull(requireNotNull(it.map).style)
            assertNull(style.getLayer(host.layerId))
            assertNull(style.getSource(host.sourceId))
            assertFalse(requireNotNull(it.binding).refreshVisible())
        }
        gate.release.complete(Unit)
        scenario.moveToState(Lifecycle.State.RESUMED)
        moveCamera(scenario, MapZoneHarnessActivity.TEST_LATITUDE + 0.0001, MapZoneHarnessActivity.TEST_LONGITUDE, MapZoneHarnessActivity.TEST_ZOOM)
        scenario.onActivity {
            assertSame(ReportMapSceneState.Idle, it.latestState)
            assertEquals(2, it.store.recording.completedReads.get())
        }
        refresh(scenario)
        awaitSceneReady(scenario) { it.count == 2 }
        awaitRenderedIds(scenario, fixtures.idsA)
        assertEquals(3, activity.store.recording.completedReads.get())
    }

    @Test
    fun recrearRetieneBaseEnMemoriaYModeloPeroNoConsultaNiEntregaEnLaVistaAnterior() = withHarness { scenario, previous, fixtures ->
        refresh(scenario)
        awaitSceneReady(scenario) { it.count == 2 }
        awaitRenderedIds(scenario, fixtures.idsA)
        val oldBinding = requireNotNull(previous.binding)
        val oldHost = requireNotNull(previous.host)
        val retainedStore = previous.store
        val retainedModel = previous.reportModel
        moveCamera(scenario, MapZoneHarnessActivity.TEST_LATITUDE + 0.0002, MapZoneHarnessActivity.TEST_LONGITUDE, MapZoneHarnessActivity.TEST_ZOOM)
        awaitSceneReady(scenario) { it.count == 2 }
        assertEquals(2, retainedStore.recording.completedReads.get())

        scenario.recreate()
        lateinit var current: MapZoneHarnessActivity
        scenario.onActivity { current = it }
        awaitRealMap(current)
        assertTrue(previous.mapDestroyed)
        assertEquals(0, previous.registeredNativeListenerCount)
        assertSame(ReportMapSceneState.Closed, oldBinding.state)
        assertSame(NativeMapLoadState.Closed, previous.monitor.state)
        assertSame(retainedStore, current.store)
        assertSame(retainedModel, current.reportModel)
        assertFalse(retainedStore.closed)
        val oldDeliveryCount = previous.stateDeliveries
        scenario.onActivity {
            assertNotSame(oldHost, it.host)
            assertNotSame(oldBinding, it.binding)
            assertSame(ReportMapSceneState.Idle, it.latestState)
            assertSame(ReportMapState.Idle, it.reportModel.state.value)
            assertEquals(2, it.store.recording.completedReads.get())
            val camera = requireNotNull(it.map).cameraPosition
            assertEquals(MapZoneHarnessActivity.TEST_LATITUDE + 0.0002, requireNotNull(camera.target).latitude, 0.00001)
            assertEquals(MapZoneHarnessActivity.TEST_ZOOM, camera.zoom, 0.001)
        }
        moveCamera(scenario, MapZoneHarnessActivity.TEST_LATITUDE + 0.0003, MapZoneHarnessActivity.TEST_LONGITUDE, MapZoneHarnessActivity.TEST_ZOOM)
        assertEquals(2, retainedStore.recording.completedReads.get())
        refresh(scenario)
        awaitSceneReady(scenario) { it.count == 2 }
        awaitRenderedIds(scenario, fixtures.idsA)
        assertEquals(3, retainedStore.recording.completedReads.get())
        assertEquals(oldDeliveryCount, previous.stateDeliveries)
        scenario.moveToState(Lifecycle.State.DESTROYED)
        assertTrue("El dueño cierra SQLite al liberar el ViewModelStore definitivo.", retainedStore.closed)
    }

    private fun withHarness(
        action: (ActivityScenario<MapZoneHarnessActivity>, MapZoneHarnessActivity, Fixtures) -> Unit,
    ) {
        assertEquals("com.example.citizensecurity.verification.maps", context.packageName)
        val scenario = ActivityScenario.launch<MapZoneHarnessActivity>(Intent(context, MapZoneHarnessActivity::class.java))
        try {
            lateinit var activity: MapZoneHarnessActivity
            scenario.onActivity { activity = it }
            // Si no se llama esto, la Activity abre una base vacía y el botón no añade datos.
            val fixtures = seedOnlyTechnicalDatabase(activity.store)
            awaitRealMap(activity)
            action(scenario, activity, fixtures)
        } finally { scenario.close() }
    }

    private fun seedOnlyTechnicalDatabase(store: ZoneLabStoreViewModel): Fixtures = runBlocking {
        val occurredAt = Instant.now().minusSeconds(60)
        fun draft(type: IncidentType, priority: Priority, lat: Double, lon: Double) = NewReport(
            type, priority, "Incidente ficticio fijo del laboratorio; no representa un hecho real.", occurredAt,
            ReportLocation("Punto técnico sin datos personales", lat, lon),
        )
        val a1 = store.sqlite.create(draft(IncidentType.THEFT, Priority.HIGH,
            MapZoneHarnessActivity.TEST_LATITUDE - 0.0005, MapZoneHarnessActivity.TEST_LONGITUDE - 0.0006))
        val a2 = store.sqlite.create(draft(IncidentType.FIRE, Priority.MEDIUM,
            MapZoneHarnessActivity.TEST_LATITUDE + 0.0005, MapZoneHarnessActivity.TEST_LONGITUDE + 0.0006))
        val b = store.sqlite.create(draft(IncidentType.OTHER, Priority.LOW,
            MapZoneHarnessActivity.TEST_LATITUDE + 0.02, MapZoneHarnessActivity.TEST_LONGITUDE + 0.02))
        Fixtures(a1, a2, b)
    }

    private fun refresh(scenario: ActivityScenario<MapZoneHarnessActivity>) {
        scenario.onActivity { assertTrue(requireNotNull(it.binding).refreshVisible()) }
    }

    private fun awaitRealMap(activity: MapZoneHarnessActivity) {
        assertTrue("La carga SDK debe terminar antes de 35 segundos.", activity.terminalLoad.await(35, TimeUnit.SECONDS))
        assertTrue("Se requiere estilo, mapa y objetos base renderizados reales: ${activity.monitor.state}",
            activity.monitor.state is NativeMapLoadState.Ready)
        assertTrue("El mapa real debe quedar idle tras cargar.", activity.mapIdleAfterReady.await(5, TimeUnit.SECONDS))
        assertTrue(activity.mapIdle)
    }

    private fun awaitSceneReady(scenario: ActivityScenario<MapZoneHarnessActivity>, matches: (ReportMapSceneState.Ready) -> Boolean) {
        val ready = CountDownLatch(1)
        var registration: Closeable? = null
        scenario.onActivity {
            registration = it.observeState { state -> if (state is ReportMapSceneState.Ready && matches(state)) ready.countDown() }
        }
        try {
            assertTrue("El modelo y el puente deben entregar la consulta esperada antes de 10 segundos.", ready.await(10, TimeUnit.SECONDS))
        } finally { scenario.onActivity { registration?.close() } }
    }

    private fun awaitRenderedIds(scenario: ActivityScenario<MapZoneHarnessActivity>, ids: Set<String>) =
        awaitRendered(scenario) { it == ids }

    private fun awaitRendered(scenario: ActivityScenario<MapZoneHarnessActivity>, matches: (Set<String>) -> Boolean) {
        val rendered = CountDownLatch(1)
        var registration: Closeable? = null
        scenario.onActivity { activity ->
            registration = activity.observeFrames { if (matches(activity.renderedMarkerIds())) rendered.countDown() }
        }
        try {
            assertTrue("El SDK debe renderizar únicamente los folios esperados antes de 10 segundos.", rendered.await(10, TimeUnit.SECONDS))
        } finally { scenario.onActivity { registration?.close() } }
    }

    private fun moveCamera(scenario: ActivityScenario<MapZoneHarnessActivity>, lat: Double, lon: Double, zoom: Double) {
        val idle = CountDownLatch(1)
        val listener = MapLibreMap.OnCameraIdleListener { idle.countDown() }
        scenario.onActivity {
            val map = requireNotNull(it.map)
            map.addOnCameraIdleListener(listener)
            map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(lat, lon)).zoom(zoom).build()))
        }
        try { assertTrue("La cámara SDK debe completar el movimiento.", idle.await(5, TimeUnit.SECONDS)) }
        finally { scenario.onActivity { requireNotNull(it.map).removeOnCameraIdleListener(listener) } }
    }

    @Suppress("DEPRECATION")
    private fun assertTechnicalIsolation() {
        val application = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertTrue(application.flags and ApplicationInfo.FLAG_TEST_ONLY != 0)
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in permissions)
        assertFalse(Manifest.permission.ACCESS_COARSE_LOCATION in permissions)
    }

    private fun saveNativeZoneEvidence(scenario: ActivityScenario<MapZoneHarnessActivity>, fixtures: Fixtures) {
        val directory = File(context.filesDir, "phase11-evidence").apply { check(isDirectory || mkdirs()) }
        val idle = CountDownLatch(1)
        val snapshotReady = CountDownLatch(1)
        val snapshot = AtomicReference<Bitmap>()
        var frameRegistration: Closeable? = null
        val idleListener = MapView.OnDidBecomeIdleListener { idle.countDown() }
        scenario.onActivity {
            it.mapView.addOnDidBecomeIdleListener(idleListener)
            // El estilo puede estar idle antes de que los nuevos datos GeoJSON terminen de dibujarse.
            frameRegistration = it.observeFrames {
                if (it.renderedMarkerIds() == fixtures.idsA) idle.countDown()
            }
        }
        try {
            assertTrue("Los marcadores deben alcanzar un fotograma nativo antes de capturar.", idle.await(5, TimeUnit.SECONDS))
            scenario.onActivity {
                it.mapView.removeOnDidBecomeIdleListener(idleListener)
                frameRegistration?.close()
                requireNotNull(it.map).snapshot { bitmap -> snapshot.set(bitmap); snapshotReady.countDown() }
            }
            assertTrue("El SDK debe entregar snapshot con ambos marcadores.", snapshotReady.await(5, TimeUnit.SECONDS))
            requireNotNull(snapshot.get()).useBitmap { bitmap ->
                File(directory, "zones-native-sdk.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
            lateinit var evidence: JSONObject
            scenario.onActivity {
                val ready = it.latestState as ReportMapSceneState.Ready
                val native = it.monitor.state as NativeMapLoadState.Ready
                val camera = requireNotNull(it.map).cameraPosition
                evidence = JSONObject().apply {
                    put("package", context.packageName)
                    put("style", MapsConfiguration.STYLE_URL)
                    put("cameraSource", "fixed technical viewport; no device GPS")
                    put("dataSource", "SqliteReportRepository(databaseName=null), explicit fictional test fixtures")
                    put("styleLoaded", it.monitor.styleLoaded)
                    put("mapLoaded", it.monitor.mapLoaded)
                    put("fullyRendered", it.monitor.fullyRendered)
                    put("mapIdle", it.mapIdle)
                    put("renderedRoads", native.features.roads)
                    put("renderedLabels", native.features.labels)
                    put("renderedBuildings", native.features.buildings)
                    put("renderedReportIds", JSONArray(it.renderedMarkerIds().sorted()))
                    put("expectedReportIds", JSONArray(fixtures.idsA.sorted()))
                    put("renderedReports", it.renderedMarkerFeatures().size)
                    put("sourceId", requireNotNull(it.host).sourceId)
                    put("layerId", requireNotNull(it.host).layerId)
                    put("sqliteCompletedReads", it.store.recording.completedReads.get())
                    put("queryCount", ready.count)
                    put("hasMore", ready.hasMore)
                    put("latitude", requireNotNull(camera.target).latitude)
                    put("longitude", requireNotNull(camera.target).longitude)
                    put("zoom", camera.zoom)
                    put("region", JSONObject().apply {
                        put("south", ready.query.region.south); put("north", ready.query.region.north)
                        put("west", ready.query.region.west); put("east", ready.query.region.east)
                    })
                    put("sdkSnapshot", "zones-native-sdk.png")
                }
            }
            instrumentation.waitForIdleSync()
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull(screenshot)
            requireNotNull(screenshot).useBitmap { bitmap ->
                File(directory, "zones-native.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
            File(directory, "zones-native.json").writeText(evidence.toString(2))
        } finally {
            scenario.onActivity { it.mapView.removeOnDidBecomeIdleListener(idleListener); frameRegistration?.close() }
        }
    }

    private fun Bitmap.useBitmap(action: (Bitmap) -> Unit) { try { action(this) } finally { recycle() } }
    private data class Fixtures(val a1: Report, val a2: Report, val b: Report) {
        val inA get() = listOf(a1, a2)
        val idsA get() = inA.map { it.id }.toSet()
    }
}
