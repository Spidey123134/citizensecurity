package com.example.citizensecurity.verification.maps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.NearbyIncidentRejection
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Ejecutar solo en el paquete técnico. La prueba live requiere red y datos reales de OpenFreeMap;
 * fallar o no renderizar no se considera éxito ni se sustituye por un estilo o mapa falso.
 * Las capturas contienen solo este mapa de prueba, sin GPS, formularios ni reportes del usuario.
 */
@RunWith(AndroidJUnit4::class)
class MapLibreNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun libertyRenderizaObjetosYClicSdkSeleccionaSinAutorizarConfirmacionGps() = withHarness { scenario, activity ->
        assertTechnicalIsolation()
        awaitRealMap(activity)
        scenario.onActivity {
            assertTrue(it.monitor.styleLoaded)
            assertTrue(it.monitor.mapLoaded)
            assertTrue(it.monitor.fullyRendered)
            assertTrue(it.renderedFeatures().isComplete)
            assertEquals(MapLibreHarnessActivity.TEST_LATITUDE, requireNotNull(it.map?.cameraPosition?.target).latitude, 0.0001)
            assertEquals(MapLibreHarnessActivity.TEST_LONGITUDE, requireNotNull(it.map?.cameraPosition?.target).longitude, 0.0001)
            assertEquals(MapLibreHarnessActivity.TEST_ZOOM, requireNotNull(it.map).cameraPosition.zoom, 0.001)
        }
        saveRealMapEvidence(scenario)

        val cameraIdle = CountDownLatch(1)
        val cameraListener = MapLibreMap.OnCameraIdleListener { cameraIdle.countDown() }
        scenario.onActivity {
            val map = requireNotNull(it.map)
            map.addOnCameraIdleListener(cameraListener)
            map.moveCamera(CameraUpdateFactory.zoomTo(MapLibreHarnessActivity.TEST_ZOOM + 0.5))
        }
        assertTrue("El SDK debe finalizar el movimiento de cámara.", cameraIdle.await(5, TimeUnit.SECONDS))
        scenario.onActivity {
            val map = requireNotNull(it.map)
            map.removeOnCameraIdleListener(cameraListener)
            assertEquals(MapLibreHarnessActivity.TEST_ZOOM + 0.5, map.cameraPosition.zoom, 0.001)
        }

        lateinit var expectedPoint: LatLng
        var clickX = 0
        var clickY = 0
        scenario.onActivity {
            val point = PointF(it.mapView.width / 2f, it.mapView.height / 2f)
            expectedPoint = requireNotNull(it.map).projection.fromScreenLocation(point)
            val screen = IntArray(2)
            it.mapView.getLocationOnScreen(screen)
            clickX = screen[0] + point.x.toInt()
            clickY = screen[1] + point.y.toInt()
        }
        assertTrue(device.click(clickX, clickY))
        assertTrue("El toque debe llegar al listener original del SDK.", activity.proposalReceived.await(5, TimeUnit.SECONDS))
        scenario.onActivity {
            val proposal = it.latestProposal as LocationProposal.ValidPoint
            assertEquals(expectedPoint.latitude, proposal.latitude, 0.0001)
            assertEquals(expectedPoint.longitude, proposal.longitude, 0.0001)
            assertEquals(1, it.proposalCount)
            val blocked = requireNotNull(it.incidentBinding).confirm() as NearbyLocationConfirmation.Blocked
            assertEquals(NearbyIncidentRejection.DEVICE_LOCATION_REQUIRED, blocked.rejection.reason)
        }

        // Otra entrega SDK prueba que quitar nuestro puente conserva los listeners ajenos.
        val siblingClick = CountDownLatch(1)
        val sibling = MapLibreMap.OnMapClickListener { siblingClick.countDown(); false }
        scenario.onActivity {
            requireNotNull(it.map).addOnMapClickListener(sibling)
            it.closeIncidentBinding()
            assertTrue(it.session.isClosed)
        }
        assertTrue(device.click(clickX + 15, clickY + 15))
        assertTrue("El segundo toque debe seguir llegando al SDK tras cerrar el puente.", siblingClick.await(5, TimeUnit.SECONDS))
        scenario.onActivity {
            assertEquals("El adaptador retirado no debe recibir otro toque.", 1, it.proposalCount)
            requireNotNull(it.map).removeOnMapClickListener(sibling)
        }
    }

    @Test
    fun recrearMapViewConservaCamaraYRetiraListenersDeLaVistaAnterior() = withHarness { scenario, previous ->
        awaitRealMap(previous)
        val cameraIdle = CountDownLatch(1)
        val listener = MapLibreMap.OnCameraIdleListener { cameraIdle.countDown() }
        val target = LatLng(MapLibreHarnessActivity.TEST_LATITUDE + 0.001, MapLibreHarnessActivity.TEST_LONGITUDE + 0.001)
        scenario.onActivity {
            val map = requireNotNull(it.map)
            map.addOnCameraIdleListener(listener)
            map.moveCamera(CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(target).zoom(16.0).build(),
            ))
        }
        assertTrue(cameraIdle.await(5, TimeUnit.SECONDS))
        scenario.onActivity { requireNotNull(it.map).removeOnCameraIdleListener(listener) }
        scenario.recreate()
        lateinit var current: MapLibreHarnessActivity
        scenario.onActivity { current = it }
        awaitRealMap(current)
        assertTrue(previous.mapDestroyed)
        assertEquals(0, previous.registeredNativeListenerCount)
        assertTrue(previous.session.isClosed)
        assertSame(NativeMapLoadState.Closed, previous.monitor.state)
        scenario.onActivity {
            val camera = requireNotNull(it.map).cameraPosition
            assertEquals(target.latitude, requireNotNull(camera.target).latitude, 0.0001)
            assertEquals(target.longitude, requireNotNull(camera.target).longitude, 0.0001)
            assertEquals(16.0, camera.zoom, 0.001)
            assertEquals(0, it.proposalCount)
            assertEquals(LocationProposal.None, it.latestProposal)
        }
    }

    @Test
    fun estiloInaccesibleTerminaConFalloNativoSinEsperarIndefinidamente() = withHarness(unavailableStyle = true) { scenario, activity ->
        assertTrue("El SDK debe comunicar fallo del recurso inexistente.", activity.terminalLoad.await(5, TimeUnit.SECONDS))
        scenario.onActivity {
            assertEquals(NativeMapLoadState.Failed(NativeMapFailure.SdkFailure), it.monitor.state)
            assertFalse(it.monitor.styleLoaded)
            assertFalse(it.monitor.mapLoaded)
        }
    }

    @Test
    fun destruirVistaCancelaEsperaYSesionInclusoAntesDeCargarEstilo() = withHarness(unavailableStyle = true) { scenario, activity ->
        scenario.moveToState(Lifecycle.State.DESTROYED)
        assertTrue(activity.mapDestroyed)
        assertEquals(0, activity.registeredNativeListenerCount)
        assertSame(NativeMapLoadState.Closed, activity.monitor.state)
        assertTrue(activity.session.isClosed)
        assertEquals(0L, activity.terminalLoad.count)
        assertEquals(0, activity.proposalCount)
    }

    private fun withHarness(
        unavailableStyle: Boolean = false,
        action: (ActivityScenario<MapLibreHarnessActivity>, MapLibreHarnessActivity) -> Unit,
    ) {
        assertEquals("com.example.citizensecurity.verification.maps", context.packageName)
        val intent = Intent(context, MapLibreHarnessActivity::class.java)
            .putExtra(MapLibreHarnessActivity.EXTRA_UNAVAILABLE_STYLE, unavailableStyle)
        val scenario = ActivityScenario.launch<MapLibreHarnessActivity>(intent)
        try {
            lateinit var activity: MapLibreHarnessActivity
            scenario.onActivity { activity = it }
            action(scenario, activity)
        } finally {
            scenario.close()
        }
    }

    private fun awaitRealMap(activity: MapLibreHarnessActivity) {
        assertTrue("La carga nativa debe terminar antes de 35 s.", activity.terminalLoad.await(35, TimeUnit.SECONDS))
        assertTrue("OpenFreeMap debe renderizar calles, etiquetas y edificios; estado=${activity.monitor.state}; ${activity.loadDiagnostics()}",
            activity.monitor.state is NativeMapLoadState.Ready)
        assertTrue("El mapa debe quedar idle después de Ready, con sus transiciones terminadas; ${activity.loadDiagnostics()}",
            activity.mapIdleAfterReady.await(5, TimeUnit.SECONDS))
        assertTrue("Cerrar la vista no equivale a observar el mapa idle.", activity.mapIdle)
    }

    @Suppress("DEPRECATION")
    private fun assertTechnicalIsolation() {
        val application = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertTrue(application.flags and android.content.pm.ApplicationInfo.FLAG_TEST_ONLY != 0)
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in permissions)
        assertFalse(Manifest.permission.ACCESS_COARSE_LOCATION in permissions)
    }

    private fun saveRealMapEvidence(scenario: ActivityScenario<MapLibreHarnessActivity>) {
        val directory = File(context.filesDir, "maplibre-evidence").apply { check(isDirectory || mkdirs()) }
        val snapshotReady = CountDownLatch(1)
        val snapshot = AtomicReference<Bitmap>()
        // Fuera del callback idle: solicitar snapshot desde ese callback puede causar recursión SDK.
        scenario.onActivity {
            assertTrue(it.mapIdle)
            requireNotNull(it.map).snapshot { bitmap ->
                snapshot.set(bitmap)
                snapshotReady.countDown()
            }
        }
        assertTrue("El SDK debe entregar su fotograma final después de idle.", snapshotReady.await(5, TimeUnit.SECONDS))
        requireNotNull(snapshot.get()).useBitmap { bitmap ->
            File(directory, "liberty-native-sdk.png").outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
        lateinit var evidence: JSONObject
        scenario.onActivity {
            val ready = it.monitor.state as NativeMapLoadState.Ready
            val camera = requireNotNull(it.map).cameraPosition
            val names = it.renderedLabelNames()
            assertTrue("El mosaico debe aportar nombres reales para sus etiquetas.", names.isNotEmpty())
            evidence = JSONObject().apply {
                put("package", context.packageName)
                put("style", MapLibreHarnessActivity.OPENFREEMAP_STYLE_URI)
                put("cameraSource", "fixed technical viewport; no device GPS")
                put("styleLoaded", it.monitor.styleLoaded)
                put("mapLoaded", it.monitor.mapLoaded)
                put("fullyRendered", it.monitor.fullyRendered)
                put("mapIdle", it.mapIdle)
                put("sdkSnapshot", "liberty-native-sdk.png")
                put("diagnostics", it.loadDiagnostics())
                put("latitude", requireNotNull(camera.target).latitude)
                put("longitude", requireNotNull(camera.target).longitude)
                put("zoom", camera.zoom)
                put("roadLayers", JSONArray(ready.layers.roads))
                put("labelLayers", JSONArray(ready.layers.labels))
                put("renderedLabelNames", JSONArray(names))
                put("buildingLayers", JSONArray(ready.layers.buildings))
                put("renderedRoads", ready.features.roads)
                put("renderedLabels", ready.features.labels)
                put("renderedBuildings", ready.features.buildings)
            }
        }
        instrumentation.waitForIdleSync()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("La captura debe mostrar la Activity y el mapa nativo.", screenshot)
        requireNotNull(screenshot).useBitmap { bitmap ->
            File(directory, "liberty-native.png").outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
        File(directory, "liberty-native.json").writeText(evidence.toString(2))
    }

    private fun Bitmap.useBitmap(action: (Bitmap) -> Unit) {
        try { action(this) } finally { recycle() }
    }
}
