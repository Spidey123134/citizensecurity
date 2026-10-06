package com.example.citizensecurity.verification.location

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.data.AndroidPhoneLocationSource
import com.example.citizensecurity.data.PhoneLocationEvidence
import com.example.citizensecurity.data.PhoneLocationRefresh
import com.example.citizensecurity.data.PhoneLocationSource
import com.example.citizensecurity.data.toDeviceLocationFix
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.NearbyIncidentRejection
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.maps.IncidentLocationIssue
import com.example.citizensecurity.maps.IncidentLocationState
import com.example.citizensecurity.maps.IncidentLocationViewModel
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Registro/cancelación nativos y conversión de Location, sin inyectar un proveedor GPS. */
@RunWith(AndroidJUnit4::class)
class AndroidGpsSourceNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Before
    fun grantOnlyFixturePermission() {
        assertEquals("com.example.citizensecurity.verification.location.flow", context.packageName)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(hasPrecisePermission())
    }

    @Test
    fun registrarGPSNativoYCancelarAntesDelCallbackNoPublicaEvidencia() {
        ActivityScenario.launch(LocationFlowHarnessActivity::class.java).use { scenario ->
            val source = AndroidPhoneLocationSource(context)
            val evidence = PhoneLocationEvidence(source::hasPrecisePermission, source::isLocationEnabled)
            lateinit var request: Deferred<*>
            scenario.onActivity { activity ->
                assertTrue(source.hasPrecisePermission())
                // El fixture no modifica ajustes. Si el proveedor está apagado, falla explícito.
                assertTrue("Esta comprobación requiere GPS habilitado en el emulador de prueba.", source.isLocationEnabled())
                val refresh = PhoneLocationRefresh(source, evidence, SystemClock::elapsedRealtimeNanos)
                request = activity.lifecycleScope.async(start = CoroutineStart.UNDISPATCHED) {
                    refresh.refresh()
                }
                // Registro síncrono terminado; los callbacks de GPS llegarían después al Looper.
                assertTrue("La fuente nativa debe quedar esperando una lectura.", request.isActive)
                assertNull(evidence.currentFix())
                request.cancel()
            }
            runBlocking { withTimeout(2_000) { request.join() } }
            instrumentation.waitForIdleSync()
            assertTrue(request.isCancelled)
            assertNull(evidence.currentFix())
            Log.i("LocationVerification", "LOCATION_GPS_NATIVE=" + JSONObject()
                .put("package", context.packageName)
                .put("api", Build.VERSION.SDK_INT)
                .put("gpsProviderEnabled", true)
                .put("nativeRegistrationReturned", true)
                .put("cancelledBeforeCallback", true)
                .put("evidencePublished", false)
                .put("physicalFixProven", false)
                .toString())
        }
    }

    @Test
    fun marcaSimuladaDeLocationAndroidSeRechazaEnElFlujoCompleto() {
        val androidLocation = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 19.4326077
            longitude = -99.1332088
            accuracy = 20f
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= 31) setMock(true)
            else throw AssertionError("Esta prueba de Location.isMock requiere API 31 o posterior.")
        }
        val fix = androidLocation.toDeviceLocationFix()
        assertTrue(fix.isMock)
        val evidence = PhoneLocationEvidence(::hasPrecisePermission, { true }) // Estado controlado del fixture.
        var awaitCalls = 0
        val source = object : PhoneLocationSource {
            override fun hasPrecisePermission() = this@AndroidGpsSourceNativeTest.hasPrecisePermission()
            override fun isLocationEnabled() = true // Solo el estado del fixture.
            override suspend fun awaitFix(): DeviceLocationFix { awaitCalls++; return fix }
        }
        val refresh = PhoneLocationRefresh(source, evidence, SystemClock::elapsedRealtimeNanos)
        val factory = IncidentLocationViewModel.factory(
            ReportLocation("Referencia del laboratorio", fix.latitude, fix.longitude),
            refresh::refresh, evidence::currentFix, SystemClock::elapsedRealtimeNanos,
        )
        lateinit var model: IncidentLocationViewModel
        ActivityScenario.launch(LocationFlowHarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity, factory).get("android-mock-adapter", IncidentLocationViewModel::class.java)
                model.select(fix.latitude, fix.longitude)
                assertEquals(0, awaitCalls)
                model.refreshLocation()
            }
            awaitError(model)
            scenario.onActivity {
                assertEquals(1, awaitCalls)
                val issue = (model.state.value as IncidentLocationState.Error).issue as IncidentLocationIssue.Blocked
                assertEquals(NearbyIncidentRejection.MOCK_DEVICE_LOCATION, issue.rejection.reason)
                assertNull(evidence.currentFix())
                assertNull(model.confirm())
                assertFalse(model.state.value is IncidentLocationState.Confirmed)
            }
        }
    }

    private fun hasPrecisePermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    private fun awaitError(model: IncidentLocationViewModel) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val ready = AtomicReference(false)
            instrumentation.runOnMainSync { ready.set(model.state.value is IncidentLocationState.Error) }
            if (ready.get()) return
            SystemClock.sleep(20)
        }
        throw AssertionError("La lectura simulada no produjo un rechazo explícito.")
    }
}
