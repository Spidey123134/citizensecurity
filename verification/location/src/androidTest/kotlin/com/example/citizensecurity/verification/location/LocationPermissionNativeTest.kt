package com.example.citizensecurity.verification.location

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.example.citizensecurity.maps.LocationPermissionAccess
import com.example.citizensecurity.maps.LocationPermissionAction
import com.example.citizensecurity.maps.LocationPermissionIssue
import com.example.citizensecurity.maps.LocationPermissionViewModel
import com.example.citizensecurity.maps.PreciseLocationPermissionBinding
import com.example.citizensecurity.maps.IncidentLocationIssue
import com.example.citizensecurity.maps.IncidentLocationState
import com.example.citizensecurity.maps.IncidentLocationViewModel
import com.example.citizensecurity.data.PhoneLocationEvidence
import com.example.citizensecurity.data.PhoneLocationRefresh
import com.example.citizensecurity.data.PhoneLocationSource
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.NearbyIncidentRejection
import com.example.citizensecurity.domain.ReportLocation
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Cada variante tiene permisos propios; preparar únicamente su paquete antes de instrumentar. */
@RunWith(AndroidJUnit4::class)
class LocationPermissionNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun dialogoNativoRetienePeticionAlRecrearYReleePermisosSinCapturaAutomatica() {
        assertTrue("Precisión aproximada requiere Android 12 o posterior.", Build.VERSION.SDK_INT >= 31)
        val packageName = instrumentation.targetContext.packageName
        assertTrue(packageName.startsWith("com.example.citizensecurity.verification.location."))
        val variant = packageName.substringAfterLast('.')
        val expectedAccess = when (variant) {
            "precise" -> LocationPermissionAccess.Precise
            "approximate" -> LocationPermissionAccess.Approximate
            "denied" -> LocationPermissionAccess.Denied
            else -> throw AssertionError("Variante de verificación desconocida: $variant")
        }
        assertEquals(PackageManager.PERMISSION_DENIED, permission(Manifest.permission.ACCESS_FINE_LOCATION))
        assertEquals(PackageManager.PERMISSION_DENIED, permission(Manifest.permission.ACCESS_COARSE_LOCATION))

        lateinit var initialModel: LocationPermissionViewModel
        lateinit var initialBinding: PreciseLocationPermissionBinding
        lateinit var locationModel: IncidentLocationViewModel
        var captureCalls = 0
        val selectedPoint = LocationProposal.ValidPoint(19.433, -99.134)
        val source = object : PhoneLocationSource {
            override fun hasPrecisePermission() =
                permission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            override fun isLocationEnabled() = true // Estado controlado del laboratorio, no del GPS nativo.
            override suspend fun awaitFix(): DeviceLocationFix {
                captureCalls++
                // El fixture es simulado y debe rechazarse incluso tras conceder FINE.
                return DeviceLocationFix(19.4326077, -99.1332088, 20.0, SystemClock.elapsedRealtimeNanos(), true)
            }
        }
        val evidence = PhoneLocationEvidence(source::hasPrecisePermission, source::isLocationEnabled)
        val refresh = PhoneLocationRefresh(source, evidence, SystemClock::elapsedRealtimeNanos)
        val locationFactory = IncidentLocationViewModel.factory(
            ReportLocation("Referencia del laboratorio", 19.4326077, -99.1332088),
            refresh::refresh, evidence::currentFix, SystemClock::elapsedRealtimeNanos,
        )
        var initialInstance = 0
        val scenario = ActivityScenario.launch(LocationPermissionHarnessActivity::class.java)
        try {
            scenario.onActivity { activity ->
                initialModel = activity.permissionModel
                initialBinding = activity.permissionBinding
                initialInstance = activity.instanceId
                assertEquals(LocationPermissionAccess.Denied, initialModel.state.value.access)
                assertFalse(initialModel.state.value.requestInFlight)
                assertFalse(initialModel.state.value.shouldExplain)
                locationModel = ViewModelProvider(activity, locationFactory).get(IncidentLocationViewModel::class.java)
                locationModel.select(selectedPoint.latitude, selectedPoint.longitude)
                assertEquals(selectedPoint, locationModel.state.value.proposal)
                assertEquals(0, captureCalls)
            }
            assertFalse("Registrar y comprobar no deben mostrar diálogo.", device.hasObject(controllerId(DENY)))
            scenario.onActivity { activity ->
                assertEquals(LocationPermissionAction.LaunchRequest, activity.permissionBinding.request())
                assertEquals(LocationPermissionAction.AlreadyRunning, activity.permissionBinding.request())
                assertTrue(initialModel.state.value.requestInFlight)
            }
            requireOwnDialog()

            // ActivityScenario.recreate fuerza RESUMED y no sirve con PermissionController encima.
            // Esta llamada usa la recreación Android real sin alterar la visibilidad del dueño.
            onMain { currentActivity().recreate() }
            await("La Activity debe recrearse mientras la petición permanece abierta.") {
                onMain {
                    val current = LocationPermissionHarnessActivity.current?.get()
                    current != null && current.instanceId != initialInstance &&
                        current.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)
                }
            }
            onMain {
                val activity = currentActivity()
                assertSame(initialModel, activity.permissionModel)
                assertTrue(activity.permissionModel.state.value.requestInFlight)
                assertEquals(LocationPermissionAction.AlreadyRunning, activity.permissionBinding.request())
                assertEquals(LocationPermissionAction.Inactive, initialBinding.request())
                assertSame(locationModel, ViewModelProvider(activity, locationFactory).get(IncidentLocationViewModel::class.java))
                assertEquals(selectedPoint, locationModel.state.value.proposal)
                assertEquals(0, captureCalls)
            }
            requireOwnDialog()
            when (variant) {
                "precise" -> {
                    requireControllerObject(FINE).click()
                    requireControllerObject(ALLOW_FOREGROUND).click()
                }
                "approximate" -> {
                    requireControllerObject(COARSE).click()
                    requireControllerObject(ALLOW_FOREGROUND).click()
                }
                "denied" -> requireControllerObject(DENY).click()
            }
            await("Debe entregarse el resultado del sistema al nuevo dueño.") {
                onMain {
                    val activity = LocationPermissionHarnessActivity.current?.get()
                    activity != null && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        !activity.permissionModel.state.value.requestInFlight &&
                        activity.permissionModel.state.value.access == expectedAccess
                }
            }
            assertEquals(
                if (variant == "precise") PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED,
                permission(Manifest.permission.ACCESS_FINE_LOCATION),
            )
            assertEquals(
                if (variant == "denied") PackageManager.PERMISSION_DENIED else PackageManager.PERMISSION_GRANTED,
                permission(Manifest.permission.ACCESS_COARSE_LOCATION),
            )
            scenario.onActivity {
                assertEquals("El resultado del permiso no inicia GPS.", 0, captureCalls)
                assertEquals(selectedPoint, locationModel.state.value.proposal)
                assertNull(evidence.currentFix())
                // El gesto posterior se prueba contra permiso real + renovación productiva.
                locationModel.refreshLocation()
            }
            await("La renovación explícita debe terminar con el estado correcto.") {
                onMain { locationModel.state.value is IncidentLocationState.Error }
            }
            onMain {
                val issue = (locationModel.state.value as IncidentLocationState.Error).issue
                if (variant == "precise") {
                    assertEquals(1, captureCalls)
                    assertEquals(NearbyIncidentRejection.MOCK_DEVICE_LOCATION,
                        (issue as IncidentLocationIssue.Blocked).rejection.reason)
                } else {
                    assertEquals(0, captureCalls)
                    assertSame(IncidentLocationIssue.PermissionRequired, issue)
                }
                assertEquals(selectedPoint, locationModel.state.value.proposal)
                assertNull(evidence.currentFix())
                assertNull(locationModel.confirm())
            }

            scenario.recreate()
            scenario.onActivity { activity ->
                assertSame(initialModel, activity.permissionModel)
                activity.permissionBinding.check()
                assertEquals(expectedAccess, activity.permissionModel.state.value.access)
                assertFalse(activity.permissionModel.state.value.requestInFlight)
                assertEquals(
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION),
                    activity.permissionModel.state.value.shouldExplain,
                )
                if (variant == "precise") {
                    assertEquals(LocationPermissionAction.AlreadyGranted, activity.permissionBinding.request())
                } else if (activity.permissionModel.state.value.shouldExplain) {
                    assertEquals(LocationPermissionAction.ExplanationRequired, activity.permissionBinding.request())
                    assertEquals(LocationPermissionIssue.ExplanationRequired, activity.permissionModel.state.value.issue)
                }
                assertFalse(activity.permissionModel.state.value.requestInFlight)
            }
            assertFalse("Comprobar o pedir explicación no abre otra petición.", device.hasObject(controllerId(DENY)))
        } finally {
            dismissOnlyOwnDialogIfPresent()
            scenario.close()
        }
        onMain {
            assertEquals(LocationPermissionAction.Inactive, initialBinding.request())
            assertFalse(initialModel.isActive)
        }

        ActivityScenario.launch(LocationPermissionHarnessActivity::class.java).use { freshScenario ->
            freshScenario.onActivity { activity ->
                assertNotSame(initialModel, activity.permissionModel)
                assertEquals(expectedAccess, activity.permissionModel.state.value.access)
                assertFalse(activity.permissionModel.state.value.requestInFlight)
            }
            assertFalse("Un dueño nuevo solo relee los permisos existentes.", device.hasObject(controllerId(DENY)))
        }
        Log.i(
            "LocationVerification",
            "LOCATION_PERMISSION_NATIVE=" + JSONObject()
                .put("variant", variant)
                .put("package", packageName)
                .put("api", Build.VERSION.SDK_INT)
                .put("nativeDialog", true)
                .put("duplicateBlocked", true)
                .put("recreatedWhilePending", true)
                .put("viewModelRetained", true)
                .put("callbackAccess", expectedAccess.name)
                .put("newOwnerReadsCurrentPermissions", true)
                .put("automaticCaptureCalls", 0)
                .put("explicitCaptureCalls", captureCalls)
                .put("fixtureMarkedMock", true)
                .put("phoneEvidencePublished", false)
                .put("productionApplicationTargeted", false)
                .toString(),
        )
    }

    private fun permission(name: String): Int = ContextCompat.checkSelfPermission(instrumentation.targetContext, name)

    private fun controllerId(id: String): BySelector = By.res(
        Pattern.compile("(?:com\\.android\\.permissioncontroller|com\\.google\\.android\\.permissioncontroller):id/" + Pattern.quote(id)),
    )

    private fun requireControllerObject(id: String): UiObject2 =
        device.wait(Until.findObject(controllerId(id)), TIMEOUT_MS)
            ?: throw AssertionError("Falta el control nativo de permiso: $id")

    private fun requireOwnDialog() {
        val message = requireControllerObject(MESSAGE)
        val expectedLabel = instrumentation.targetContext.packageManager
            .getApplicationLabel(instrumentation.targetContext.applicationInfo).toString()
        assertTrue("El diálogo debe pertenecer exclusivamente a la aplicación de verificación.", message.text.contains(expectedLabel))
        requireControllerObject(DENY)
    }

    private fun dismissOnlyOwnDialogIfPresent() {
        val message = device.findObject(controllerId(MESSAGE)) ?: return
        val expectedLabel = instrumentation.targetContext.packageManager
            .getApplicationLabel(instrumentation.targetContext.applicationInfo).toString()
        if (message.text.contains(expectedLabel)) device.findObject(controllerId(DENY))?.click()
    }

    private fun currentActivity(): LocationPermissionHarnessActivity =
        LocationPermissionHarnessActivity.current?.get()
            ?: throw AssertionError("No hay un dueño Android activo en la verificación.")

    private fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable>()
        instrumentation.runOnMainSync {
            try {
                result.set(action())
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        failure.get()?.let { throw it }
        return result.get()
    }

    private fun await(description: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS
        do {
            if (predicate()) return
            SystemClock.sleep(30)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail(description)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val MESSAGE = "permission_message"
        const val FINE = "permission_location_accuracy_radio_fine"
        const val COARSE = "permission_location_accuracy_radio_coarse"
        const val ALLOW_FOREGROUND = "permission_allow_foreground_only_button"
        const val DENY = "permission_deny_button"
    }
}
