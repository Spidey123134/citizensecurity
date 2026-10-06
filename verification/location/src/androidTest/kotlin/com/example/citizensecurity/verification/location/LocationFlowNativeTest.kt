package com.example.citizensecurity.verification.location

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.NearbyIncidentRejection
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.maps.IncidentLocationIssue
import com.example.citizensecurity.maps.IncidentLocationState
import com.example.citizensecurity.maps.IncidentLocationViewModel
import com.example.citizensecurity.maps.LocationPermissionAccess
import com.example.citizensecurity.maps.LocationPermissionAction
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Ciclo Android real y GPS controlado: estas pruebas no acreditan una lectura GPS física. */
@RunWith(AndroidJUnit4::class)
class LocationFlowNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun prepararPermisoRealSoloParaElPaqueteDeFlujo() {
        val packageName = instrumentation.targetContext.packageName
        assertEquals("com.example.citizensecurity.verification.location.flow", packageName)
        // Los diálogos se prueban aparte. Aquí se concede únicamente al paquete técnico exacto.
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        assertEquals(PackageManager.PERMISSION_GRANTED, permission(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertEquals(PackageManager.PERMISSION_GRANTED, permission(Manifest.permission.ACCESS_FINE_LOCATION))
    }

    @Test
    fun permisoYSeleccionNoCapturanYElGestoExplicitoConfirmaSinAlterarLaReferencia() = withHarness { h ->
        h.scenario.onActivity { activity ->
            assertEquals(LocationPermissionAccess.Precise, activity.permissionModel.state.value.access)
            assertEquals(LocationPermissionAction.AlreadyGranted, activity.permissionBinding.request())
            activity.permissionBinding.check()
            activity.locationModel.select(LATITUDE, LONGITUDE)
        }
        assertEquals(0, h.fixture.source.requests.get())
        assertNull(h.fixture.evidence.currentFix())
        assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Blocked)
        h.succeed()
        val result = onMain { h.model.confirm() } as NearbyLocationConfirmation.Confirmed
        assertEquals(h.fixture.original.reference, result.location.reference)
        assertEquals(LATITUDE, result.location.latitude!!, 0.0)
        assertEquals(LONGITUDE, result.location.longitude!!, 0.0)
        assertEquals(1, h.fixture.source.requests.get())
        assertNull(onMain { h.model.confirm() })
    }

    @Test
    fun puntoLejanoEsRechazadoYSePuedeCorregirConLaMismaEvidencia() = withHarness { h ->
        onMain { h.model.select(48.8566, 2.3522) }
        h.succeed()
        val distant = onMain { h.model.confirm() } as NearbyLocationConfirmation.Blocked
        assertEquals(NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS, distant.rejection.reason)
        onMain { h.model.select(LATITUDE, LONGITUDE) }
        assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Confirmed)
        assertEquals(1, h.fixture.source.requests.get())
    }

    @Test
    fun gpsControladoImprecisoNoPublicaEvidencia() = rejectReading(
        NearbyIncidentRejection.INACCURATE_DEVICE_LOCATION,
    ) { it.syntheticFix(accuracyMeters = 100.01) }

    @Test
    fun gpsControladoAntiguoNoPublicaEvidencia() = rejectReading(
        NearbyIncidentRejection.STALE_DEVICE_LOCATION,
    ) { it.syntheticFix(elapsedRealtimeNanos = it.nowNanos - 120_000_000_001L) }

    @Test
    fun gpsControladoFuturoNoPublicaEvidencia() = rejectReading(
        NearbyIncidentRejection.FUTURE_DEVICE_LOCATION,
    ) { it.syntheticFix(elapsedRealtimeNanos = it.nowNanos + 1L) }

    @Test
    fun gpsMarcadoSimuladoNoPublicaEvidencia() = rejectReading(
        NearbyIncidentRejection.MOCK_DEVICE_LOCATION,
    ) { it.syntheticFix(isMock = true) }

    @Test
    fun gpsControladoSinPrecisionNoPublicaEvidencia() = rejectReading(
        NearbyIncidentRejection.INVALID_ACCURACY,
    ) { it.syntheticFix(accuracyMeters = Double.NaN) }

    @Test
    fun gpsControladoApagadoNoSolicitaYDescartaLaEvidenciaAnterior() = withHarness { h ->
        onMain { h.model.select(LATITUDE, LONGITUDE) }
        h.succeed()
        assertTrue(h.fixture.evidence.currentFix() != null)
        h.fixture.source.enabled = false
        onMain { h.model.refreshLocation() }
        h.awaitIssue(IncidentLocationIssue.LocationDisabled)
        assertEquals(1, h.fixture.source.requests.get())
        assertNull(h.fixture.evidence.currentFix())
        assertNull(onMain { h.model.confirm() })
    }

    @Test
    fun apagarGpsDespuesDeReadyBloqueaConfirmacionYReactivarExigeRenovar() = withHarness { h ->
        onMain { h.model.select(LATITUDE, LONGITUDE) }
        h.succeed()
        val pin = h.model.state.value.proposal
        h.fixture.source.enabled = false
        val result = onMain { h.model.confirm() }
        assertTrue("GPS apagado después de Ready debe bloquear la confirmación.", result is NearbyLocationConfirmation.Blocked)
        assertEquals(NearbyIncidentRejection.DEVICE_LOCATION_REQUIRED,
            (result as NearbyLocationConfirmation.Blocked).rejection.reason)
        assertNull(h.fixture.evidence.currentFix())
        h.fixture.source.enabled = true
        assertNull(h.fixture.evidence.currentFix())
        assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Blocked)
        assertEquals(pin, h.model.state.value.proposal)
        assertEquals(1, h.fixture.source.requests.get())
        h.succeed()
        assertEquals(2, h.fixture.source.requests.get())
        assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun consultarEvidenciaConGpsApagadoNoLaRecuperaAlRecrearLaActivity() = withHarness { h ->
        onMain { h.model.select(LATITUDE, LONGITUDE) }
        h.succeed()
        h.fixture.source.enabled = false
        assertNull("La lectura compartida se descarta al observar GPS apagado.", h.fixture.evidence.currentFix())
        h.fixture.source.enabled = true
        h.scenario.recreate()
        h.scenario.onActivity { recreated ->
            assertSame(h.fixture, recreated.fixture)
            assertSame(h.model, recreated.locationModel)
            assertNull(recreated.fixture.evidence.currentFix())
            assertNull(recreated.locationModel.confirm())
        }
        assertEquals(1, h.fixture.source.requests.get())
        h.succeed()
        assertEquals(2, h.fixture.source.requests.get())
        assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun falloDeConsultaDelProveedorDespuesDeReadyBloqueaSinPerderElPin() {
        for (failure in listOf(IllegalStateException("Proveedor no disponible."), SecurityException("Consulta no autorizada."))) {
            withHarness { h ->
                onMain { h.model.select(LATITUDE, LONGITUDE) }
                h.succeed()
                val pin = h.model.state.value.proposal
                h.fixture.source.availabilityFailure = failure
                val result = onMain { h.model.confirm() }
                assertTrue(result is NearbyLocationConfirmation.Blocked)
                assertNull(h.fixture.evidence.currentFix())
                assertEquals(pin, h.model.state.value.proposal)
                h.fixture.source.availabilityFailure = null
                assertNull(h.fixture.evidence.currentFix())
                assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Blocked)
                assertEquals(1, h.fixture.source.requests.get())
                h.succeed()
                assertEquals(2, h.fixture.source.requests.get())
                assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Confirmed)
            }
        }
    }

    @Test
    fun esperaAgotadaCancelaLaFuenteYUnNuevoGestoPermiteReintentar() = withHarness(timeoutMillis = 250L) { h ->
        onMain { h.model.select(LATITUDE, LONGITUDE) }
        val pin = h.model.state.value.proposal
        h.fixture.source.nextReading = { awaitCancellation() }
        onMain { h.model.refreshLocation() }
        h.awaitIssue(IncidentLocationIssue.TimedOut)
        assertEquals(1, h.fixture.source.requests.get())
        assertEquals(1, h.fixture.source.completions.get())
        assertEquals(1, h.fixture.source.cancellations.get())
        assertEquals(pin, h.model.state.value.proposal)
        assertNull(h.fixture.evidence.currentFix())
        assertNull(onMain { h.model.confirm() })
        h.succeed()
        assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Confirmed)
        assertEquals(2, h.fixture.source.requests.get())
    }

    @Test
    fun gestosDuplicadosNoAbrenCapturasSimultaneasNiPermitenConfirmarEnEspera() = withHarness { h ->
        val reading = CompletableDeferred<DeviceLocationFix>()
        h.fixture.source.nextReading = { reading.await() }
        onMain {
            h.model.select(LATITUDE, LONGITUDE)
            h.model.refreshLocation()
            repeat(5) { h.model.refreshLocation() }
            assertNull(h.model.confirm())
        }
        await("Debe iniciar una única lectura controlada.") { h.fixture.source.requests.get() == 1 }
        assertTrue(h.model.state.value is IncidentLocationState.Capturing)
        reading.complete(h.fixture.syntheticFix())
        h.awaitReady()
        assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Confirmed)
        assertEquals(1, h.fixture.source.requests.get())
    }

    @Test
    fun onStopAndroidCancelaConservaElPinYExigeRenovacionAlVolver() = withHarness { h ->
        h.fixture.source.nextReading = { awaitCancellation() }
        onMain {
            h.model.select(LATITUDE, LONGITUDE)
            h.model.refreshLocation()
        }
        await("La captura debe estar suscrita antes de dejar la pantalla.") { h.fixture.source.requests.get() == 1 }
        val pin = h.model.state.value.proposal
        h.scenario.moveToState(Lifecycle.State.CREATED)
        await("onStop debe cancelar la lectura.") { h.fixture.source.cancellations.get() == 1 }
        assertEquals(pin, h.model.state.value.proposal)
        assertNull(h.fixture.evidence.currentFix())
        h.scenario.moveToState(Lifecycle.State.RESUMED)
        assertNull(onMain { h.model.confirm() })
        h.awaitIssue(IncidentLocationIssue.RefreshRequired)
        assertEquals(1, h.fixture.source.requests.get())
        h.succeed()
        assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun recrearAndroidRetieneModelosYDependenciasPeroExigeRenovarAntesDeConfirmar() = withHarness { h ->
        onMain { h.model.select(LATITUDE, LONGITUDE) }
        h.succeed()
        val pin = h.model.state.value.proposal
        h.scenario.recreate()
        h.scenario.onActivity { activity ->
            assertSame(h.fixture, activity.fixture)
            assertSame(h.model, activity.locationModel)
            assertEquals(LocationPermissionAccess.Precise, activity.permissionModel.state.value.access)
            assertEquals(pin, activity.locationModel.state.value.proposal)
            assertNull(activity.locationModel.confirm())
        }
        assertEquals(1, h.fixture.source.requests.get())
        h.awaitIssue(IncidentLocationIssue.RefreshRequired)
        h.succeed()
        assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun evidenciaQueCaducaDespuesDeReadySeRechazaAlConfirmar() = withHarness { h ->
        onMain { h.model.select(LATITUDE, LONGITUDE) }
        h.succeed()
        h.fixture.nowNanos += 120_000_000_001L
        val result = onMain { h.model.confirm() } as NearbyLocationConfirmation.Blocked
        assertEquals(NearbyIncidentRejection.STALE_DEVICE_LOCATION, result.rejection.reason)
        assertEquals(1, h.fixture.source.requests.get())
    }

    @Test
    fun respuestaTardiaTrasOnStopNoPublicaEvidenciaNiCambiaElPin() = withHarness { h ->
        val reading = CompletableDeferred<DeviceLocationFix>()
        h.fixture.source.nextReading = { withContext(NonCancellable) { reading.await() } }
        try {
            onMain {
                h.model.select(LATITUDE, LONGITUDE)
                h.model.refreshLocation()
            }
            await("La captura controlada debe comenzar.") { h.fixture.source.requests.get() == 1 }
            h.scenario.moveToState(Lifecycle.State.CREATED)
            val stopped = h.model.state.value
            reading.complete(h.fixture.syntheticFix())
            await("La respuesta tardía debe terminar sin publicar.") { h.fixture.source.completions.get() == 1 }
            assertEquals(stopped, h.model.state.value)
            assertNull(h.fixture.evidence.currentFix())
            h.scenario.moveToState(Lifecycle.State.RESUMED)
            assertNull(onMain { h.model.confirm() })
            h.succeed()
            assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Confirmed)
        } finally {
            // Incluso una aserción fallida libera esta fuente que ignora cancelación.
            reading.complete(h.fixture.syntheticFix())
        }
    }

    @Test
    fun falloDeFuenteControladaNoEscapaYPuedeReintentarse() = withHarness { h ->
        onMain { h.model.select(LATITUDE, LONGITUDE) }
        h.fixture.source.nextReading = { throw IllegalStateException("Fallo controlado del laboratorio.") }
        onMain { h.model.refreshLocation() }
        h.awaitIssue(IncidentLocationIssue.Unavailable)
        assertNull(h.fixture.evidence.currentFix())
        assertNull(onMain { h.model.confirm() })
        h.succeed()
        assertTrue(onMain { h.model.confirm() } is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun cancelarRestauraLaReferenciaOriginalYCierraTodosLosGestosPosteriores() = withHarness { h ->
        onMain { h.model.select(48.8566, 2.3522) }
        assertSame(h.fixture.original, onMain { h.model.cancel() })
        onMain {
            h.model.select(LATITUDE, LONGITUDE)
            h.model.refreshLocation()
            assertNull(h.model.confirm())
        }
        assertEquals(IncidentLocationState.Cancelled(h.fixture.original), h.model.state.value)
        assertEquals(0, h.fixture.source.requests.get())
        assertNull(h.fixture.evidence.currentFix())
    }

    private fun rejectReading(
        reason: NearbyIncidentRejection,
        reading: (LocationFlowFixtureViewModel) -> DeviceLocationFix,
    ) = withHarness { h ->
        onMain { h.model.select(LATITUDE, LONGITUDE) }
        val pin = h.model.state.value.proposal
        h.fixture.source.nextReading = { reading(h.fixture) }
        onMain { h.model.refreshLocation() }
        await("La lectura no válida debe devolver rechazo.") {
            (h.model.state.value as? IncidentLocationState.Error)?.issue is IncidentLocationIssue.Blocked
        }
        val issue = (h.model.state.value as IncidentLocationState.Error).issue as IncidentLocationIssue.Blocked
        assertEquals(reason, issue.rejection.reason)
        assertEquals(pin, h.model.state.value.proposal)
        assertEquals(1, h.fixture.source.requests.get())
        assertNull(h.fixture.evidence.currentFix())
        assertNull(onMain { h.model.confirm() })
    }

    private fun withHarness(
        timeoutMillis: Long = LocationFlowHarnessActivity.DEFAULT_LAB_TIMEOUT_MILLIS,
        action: (RunningHarness) -> Unit,
    ) {
        val intent = Intent(instrumentation.targetContext, LocationFlowHarnessActivity::class.java)
            .putExtra(LocationFlowHarnessActivity.EXTRA_TIMEOUT_MILLIS, timeoutMillis)
        val scenario = ActivityScenario.launch<LocationFlowHarnessActivity>(intent)
        try {
            lateinit var harness: RunningHarness
            scenario.onActivity { activity ->
                harness = RunningHarness(scenario, activity.fixture, activity.locationModel)
                assertEquals(LocationPermissionAccess.Precise, activity.permissionModel.state.value.access)
                assertEquals(0, activity.fixture.source.requests.get())
                assertNull(activity.fixture.evidence.currentFix())
            }
            action(harness)
        } finally {
            scenario.close()
        }
    }

    private inner class RunningHarness(
        val scenario: ActivityScenario<LocationFlowHarnessActivity>,
        val fixture: LocationFlowFixtureViewModel,
        val model: IncidentLocationViewModel,
    ) {
        fun succeed() {
            val fix = fixture.syntheticFix()
            fixture.source.nextReading = { fix }
            onMain { model.refreshLocation() }
            awaitReady()
            assertEquals(fix, fixture.evidence.currentFix())
        }

        fun awaitReady() = await("Debe completarse la captura sin confirmar ni mover el pin.") {
            when (val state = model.state.value) {
                is IncidentLocationState.Idle -> state.deviceFix != null
                is IncidentLocationState.PointSelected -> state.deviceFix != null
                else -> false
            }
        }

        fun awaitIssue(issue: IncidentLocationIssue) = await("Falta el estado esperado: $issue") {
            (model.state.value as? IncidentLocationState.Error)?.issue == issue
        }
    }

    private fun permission(name: String): Int = instrumentation.targetContext.checkSelfPermission(name)

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
            SystemClock.sleep(20L)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail(description)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val LATITUDE = 19.4326077
        const val LONGITUDE = -99.1332088
    }
}
