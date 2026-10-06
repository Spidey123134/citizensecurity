package com.example.citizensecurity.verification.location

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.NearbyIncidentRejection
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.maps.IncidentLocationFlowBinding
import com.example.citizensecurity.maps.IncidentLocationState
import com.example.citizensecurity.maps.IncidentLocationViewModel
import com.example.citizensecurity.maps.LocationPermissionAction
import com.example.citizensecurity.maps.LocationPermissionViewModel
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Activity y permisos nativos, lectura controlada; no añade pantallas ni prueba GPS físico. */
@RunWith(AndroidJUnit4::class)
class IncidentLocationFlowBindingNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun grantOnlyToTechnicalPackage() {
        val packageName = instrumentation.targetContext.packageName
        assertEquals("com.example.citizensecurity.verification.location.flow", packageName)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun puenteVisibleReutilizaLaSesionYRechazaPuntoLejanoSinCapturaAutomatica() = withHarness { h ->
        onMain {
            assertEquals(LocationPermissionAction.AlreadyGranted, h.binding.requestPermission())
            assertTrue(h.binding.select(48.8566, 2.3522))
        }
        assertEquals(0, h.fixture.source.requests.get())
        h.capture()
        val distant = onMain { h.binding.confirm() } as NearbyLocationConfirmation.Blocked
        assertEquals(NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS, distant.rejection.reason)
        onMain { assertTrue(h.binding.select(LATITUDE, LONGITUDE)) }
        val accepted = onMain { h.binding.confirm() } as NearbyLocationConfirmation.Confirmed
        assertEquals(h.fixture.original.reference, accepted.location.reference)
        assertEquals(1, h.fixture.source.requests.get())
        assertNull(onMain { h.binding.confirm() })
    }

    @Test
    fun observerAndroidCancelaAlSalirYBloqueaGestosHastaRenovarEnResumed() = withHarness { h ->
        h.fixture.source.nextReading = { awaitCancellation() }
        onMain {
            h.binding.select(LATITUDE, LONGITUDE)
            assertTrue(h.binding.refreshLocation())
        }
        await("La fuente debe comenzar antes de dejar la pantalla.") { h.fixture.source.requests.get() == 1 }
        val pin = h.model.state.value.proposal
        h.scenario.moveToState(Lifecycle.State.CREATED)
        await("El observer debe cancelar la captura en onStop.") { h.fixture.source.cancellations.get() == 1 }
        onMain {
            assertFalse(h.binding.select(48.8566, 2.3522))
            assertFalse(h.binding.refreshLocation())
            assertNull(h.binding.confirm())
            assertEquals(LocationPermissionAction.NotVisible, h.binding.requestPermission())
        }
        assertEquals(pin, h.model.state.value.proposal)
        assertNull(h.fixture.evidence.currentFix())
        h.scenario.moveToState(Lifecycle.State.RESUMED)
        assertNull(onMain { h.binding.confirm() })
        assertEquals(1, h.fixture.source.requests.get())
        h.capture()
        assertEquals(2, h.fixture.source.requests.get())
        assertTrue(onMain { h.binding.confirm() } is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun recrearAndroidDesconectaPuenteAnteriorYConservaModelosSinNuevaCaptura() = withHarness { h ->
        onMain { h.binding.select(LATITUDE, LONGITUDE) }
        h.capture()
        val previousBinding = h.binding
        val pin = h.model.state.value.proposal
        h.scenario.recreate()
        h.scenario.onActivity { next ->
            assertSame(h.model, next.locationModel)
            assertSame(h.permissions, next.permissionModel)
            assertSame(h.fixture, next.fixture)
            h.binding = next.flowBinding
            assertFalse(previousBinding.select(48.8566, 2.3522))
            assertFalse(previousBinding.refreshLocation())
            assertNull(previousBinding.confirm())
            assertEquals(LocationPermissionAction.Inactive, previousBinding.requestPermission())
            assertNull(h.binding.confirm())
            assertEquals(pin, next.locationModel.state.value.proposal)
        }
        assertEquals(1, h.fixture.source.requests.get())
        h.capture()
        onMain { previousBinding.close() }
        assertEquals(2, h.fixture.source.requests.get())
        assertTrue(onMain { h.binding.confirm() } is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun registroTardioODuplicadoSeRechazaYElPuenteOriginalSigueFuncionando() = withHarness { h ->
        h.scenario.onActivity { activity ->
            try {
                IncidentLocationFlowBinding.forActivity(activity, activity.permissionModel, activity.locationModel)
                fail("Registrar después de STARTED debe fallar.")
            } catch (_: IllegalStateException) {
                assertSame(h.binding, activity.flowBinding)
            }
        }
        h.scenario.moveToState(Lifecycle.State.CREATED)
        h.scenario.onActivity { activity ->
            try {
                IncidentLocationFlowBinding.forActivity(activity, activity.permissionModel, activity.locationModel)
                fail("Un dueño no puede registrar otro puente activo.")
            } catch (_: IllegalStateException) {
                assertSame(h.binding, activity.flowBinding)
            }
        }
        h.scenario.moveToState(Lifecycle.State.RESUMED)
        onMain {
            assertEquals(LocationPermissionAction.AlreadyGranted, h.binding.requestPermission())
            assertTrue(h.binding.select(LATITUDE, LONGITUDE))
        }
        assertEquals(0, h.fixture.source.requests.get())
        h.capture()
        assertTrue(onMain { h.binding.confirm() } is NearbyLocationConfirmation.Confirmed)
        assertEquals(1, h.fixture.source.requests.get())
    }

    @Test
    fun reabrirTrasConfirmarYCancelarUsaMismoDuenoYExigeCapturaExplicitaNueva() = withHarness { h ->
        onMain { h.binding.select(LATITUDE, LONGITUDE) }
        h.capture()
        assertTrue(onMain { h.binding.confirm() } is NearbyLocationConfirmation.Confirmed)
        val cancelledOriginal = ReportLocation("  Segunda apertura cancelada  ", 19.434, -99.134)
        onMain {
            assertTrue(h.binding.beginSelection(cancelledOriginal))
            assertEquals(IncidentLocationState.Idle(), h.model.state.value)
            h.binding.select(LATITUDE, LONGITUDE)
            assertNull(h.binding.confirm())
            assertSame(cancelledOriginal, h.model.cancel())
        }
        val acceptedOriginal = ReportLocation("  Tercera apertura aceptada  ", 19.435, -99.135)
        onMain {
            assertTrue(h.binding.beginSelection(acceptedOriginal))
            assertEquals(LocationPermissionAction.AlreadyGranted, h.binding.requestPermission())
            h.binding.select(LATITUDE, LONGITUDE)
            assertNull(h.binding.confirm())
        }
        assertEquals(1, h.fixture.source.requests.get())
        h.capture()
        val accepted = onMain { h.binding.confirm() } as NearbyLocationConfirmation.Confirmed
        assertEquals(acceptedOriginal.reference, accepted.location.reference)
        assertEquals(2, h.fixture.source.requests.get())
        h.scenario.onActivity { activity ->
            assertSame(h.model, activity.locationModel)
            assertSame(h.permissions, activity.permissionModel)
            assertSame(h.binding, activity.flowBinding)
        }
    }

    private fun withHarness(action: (Harness) -> Unit) {
        val intent = Intent(instrumentation.targetContext, LocationFlowHarnessActivity::class.java)
        val scenario = ActivityScenario.launch<LocationFlowHarnessActivity>(intent)
        try {
            lateinit var harness: Harness
            scenario.onActivity { activity ->
                harness = Harness(scenario, activity.fixture, activity.locationModel, activity.permissionModel, activity.flowBinding)
            }
            action(harness)
        } finally {
            scenario.close()
        }
    }

    private inner class Harness(
        val scenario: ActivityScenario<LocationFlowHarnessActivity>,
        val fixture: LocationFlowFixtureViewModel,
        val model: IncidentLocationViewModel,
        val permissions: LocationPermissionViewModel,
        var binding: IncidentLocationFlowBinding,
    ) {
        fun capture() {
            fixture.source.nextReading = { fixture.syntheticFix() }
            onMain { assertTrue(binding.refreshLocation()) }
            await("La captura explícita debe publicar una lectura controlada.") {
                when (val state = model.state.value) {
                    is IncidentLocationState.PointSelected -> state.deviceFix != null
                    is IncidentLocationState.Idle -> state.deviceFix != null
                    else -> false
                }
            }
        }
    }

    private fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable>()
        instrumentation.runOnMainSync {
            try { result.set(action()) } catch (error: Throwable) { failure.set(error) }
        }
        failure.get()?.let { throw it }
        return result.get()
    }

    private fun await(description: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        do {
            if (predicate()) return
            SystemClock.sleep(20L)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail(description)
    }

    private companion object {
        const val LATITUDE = 19.4326077
        const val LONGITUDE = -99.1332088
    }
}
