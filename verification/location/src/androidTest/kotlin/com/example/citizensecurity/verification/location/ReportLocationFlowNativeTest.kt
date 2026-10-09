package com.example.citizensecurity.verification.location

import android.Manifest
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NearbyIncidentRejection
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.maps.IncidentLocationState
import com.example.citizensecurity.maps.IncidentLocationViewModel
import com.example.citizensecurity.maps.LocationPermissionAction
import com.example.citizensecurity.report.ReportDraftReview
import com.example.citizensecurity.report.ReportDraftViewModel
import com.example.citizensecurity.report.ReportLocationFlowBinding
import com.example.citizensecurity.report.ReportLocationSelectionRequest
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Activity y permisos reales con lecturas controladas, sin formulario, mapa visible ni SQLite. */
@RunWith(AndroidJUnit4::class)
class ReportLocationFlowNativeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun concederUbicacionSoloAlPaqueteTecnico() {
        val packageName = instrumentation.targetContext.packageName
        assertEquals(LAB_PACKAGE, packageName)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Test
    fun controlesDeAperturaAnteriorNoCierranNiAlteranUnaSeleccionNuevaListaParaConfirmar() = withHarness { h ->
        val first = onMain {
            h.binding.openSelection()!!.also { assertTrue(h.binding.select(it.token, LATITUDE, LONGITUDE)) }
        }
        val current = onMain {
            h.binding.openSelection()!!.also {
                assertNotSame(first.token, it.token)
                assertEquals(IncidentLocationState.Idle(), h.location.state.value)
                assertTrue(h.binding.select(it.token, LATITUDE, LONGITUDE))
            }
        }
        h.capture(current)
        val selected = h.location.state.value
        val draft = h.draft.state.value
        onMain {
            assertNull(h.binding.confirm(first.token))
            assertFalse(h.binding.select(first.token, 48.8566, 2.3522))
            assertFalse(h.binding.refreshLocation(first.token))
            assertFalse(h.binding.cancelSelection(first.token))
            assertEquals(LocationPermissionAction.Inactive, h.binding.requestPermission(first.token, true))
            assertSame(current, h.binding.currentSelection)
            assertEquals(selected, h.location.state.value)
            assertEquals(draft, h.draft.state.value)
            assertTrue(h.binding.confirm(current.token) is NearbyLocationConfirmation.Confirmed)
            assertFalse(h.draft.state.value.locationSelectionPending)
            assertNull(h.binding.currentSelection)
        }
        assertEquals(1, h.fixture.source.requests.get())
    }

    @Test
    fun confirmacionCercanaActualizaSoloCoordenadasYConservaEdicionesHechasMientrasEstaAbierto() = withHarness { h ->
        val request = onMain {
            populate(h.draft)
            h.binding.openSelection()!!.also {
                assertEquals(OLD_LOCATION, it.originalLocation)
                assertTrue(h.binding.select(it.token, NEW_LATITUDE, NEW_LONGITUDE))
                h.draft.setDescription("Se corrigió la descripción mientras el mapa estaba abierto 🌳")
                h.draft.setLocationReference("Junto a la entrada que sí usa la gente")
            }
        }
        assertEquals(0, h.fixture.source.requests.get())
        h.capture(request)
        onMain {
            val accepted = h.binding.confirm(request.token) as NearbyLocationConfirmation.Confirmed
            assertEquals(OLD_LOCATION.reference, accepted.location.reference)
            val state = h.draft.state.value
            assertEquals("Se corrigió la descripción mientras el mapa estaba abierto 🌳", state.description)
            assertEquals("Junto a la entrada que sí usa la gente", state.location.reference)
            assertEquals(NEW_LATITUDE, state.location.latitude!!, 0.0)
            assertEquals(NEW_LONGITUDE, state.location.longitude!!, 0.0)
            assertEquals(IncidentType.RISK, state.type)
            assertEquals(Priority.MEDIUM, state.priority)
            assertEquals(NOW.minusSeconds(60), state.occurredAt)
            assertFalse(state.locationSelectionPending)
            assertTrue(h.draft.review(NOW) is ReportDraftReview.Valid)
            assertNull(h.binding.confirm(request.token))
        }
        assertEquals(1, h.fixture.source.requests.get())
    }

    @Test
    fun rechazoDePuntoLejanoMantieneAperturaYCancelarConservaCoordenadasYReferenciaEditada() = withHarness { h ->
        val request = onMain {
            populate(h.draft)
            h.binding.openSelection()!!.also { assertTrue(h.binding.select(it.token, 48.8566, 2.3522)) }
        }
        h.capture(request)
        onMain {
            val blocked = h.binding.confirm(request.token) as NearbyLocationConfirmation.Blocked
            assertEquals(NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS, blocked.rejection.reason)
            assertSame(request, h.binding.currentSelection)
            assertTrue(h.draft.state.value.locationSelectionPending)
            assertEquals(OLD_LOCATION, h.draft.state.value.location)
            h.draft.setLocationReference("Referencia corregida antes de cancelar")
            assertTrue(h.binding.cancelSelection(request.token))
            assertFalse(h.binding.cancelSelection(request.token))
            assertNull(h.binding.currentSelection)
            assertFalse(h.draft.state.value.locationSelectionPending)
            assertEquals(OLD_LOCATION.copy(reference = "Referencia corregida antes de cancelar"), h.draft.state.value.location)
            assertEquals(IncidentLocationState.Cancelled(OLD_LOCATION), h.location.state.value)
        }
        assertEquals(1, h.fixture.source.requests.get())
    }

    @Test
    fun recreacionRetieneBorradorYTokenSinCapturaAutomaticaYDesconectaDuenoAnterior() = withHarness { h ->
        val request = onMain {
            populate(h.draft)
            h.binding.openSelection()!!.also { assertTrue(h.binding.select(it.token, NEW_LATITUDE, NEW_LONGITUDE)) }
        }
        h.capture(request)
        val previous = h.binding
        val draft = h.draft.state.value
        val pin = h.location.state.value.proposal
        h.scenario.recreate()
        h.scenario.onActivity { activity ->
            assertSame(h.draft, activity.draftModel)
            assertSame(h.location, activity.locationModel)
            assertSame(h.fixture, activity.fixture)
            h.binding = activity.reportLocationBinding
            assertNotSame(previous, h.binding)
            assertSame(request, h.binding.currentSelection)
            assertEquals(draft, h.draft.state.value)
            assertEquals(pin, h.location.state.value.proposal)
            assertNull(previous.currentSelection)
            assertNull(previous.openSelection())
            assertFalse(previous.select(request.token, 48.8566, 2.3522))
            assertFalse(previous.refreshLocation(request.token))
            assertFalse(previous.cancelSelection(request.token))
            assertNull(previous.confirm(request.token))
            assertEquals(LocationPermissionAction.Inactive, previous.requestPermission(request.token))
            assertNull(h.binding.confirm(request.token))
        }
        assertEquals(1, h.fixture.source.requests.get())
        h.capture(request)
        onMain {
            previous.close()
            assertTrue(h.binding.confirm(request.token) is NearbyLocationConfirmation.Confirmed)
            assertEquals(NEW_LATITUDE, h.draft.state.value.location.latitude!!, 0.0)
        }
        assertEquals(2, h.fixture.source.requests.get())
    }

    @Test
    fun pantallaEnCreatedNoAbreNiInvalidaLaAperturaYExigeRenovarAlVolver() = withHarness { h ->
        h.fixture.source.nextReading = { awaitCancellation() }
        val request = onMain {
            h.binding.openSelection()!!.also {
                assertTrue(h.binding.select(it.token, LATITUDE, LONGITUDE))
                assertTrue(h.binding.refreshLocation(it.token))
            }
        }
        await("La captura debe empezar antes de salir.") { h.fixture.source.requests.get() == 1 }
        h.scenario.moveToState(Lifecycle.State.CREATED)
        await("onStop debe cancelar la lectura.") { h.fixture.source.cancellations.get() == 1 }
        val selected = h.location.state.value
        val draft = h.draft.state.value
        onMain {
            assertNull(h.binding.openSelection())
            assertSame(request, h.binding.currentSelection)
            assertFalse(h.binding.select(request.token, 48.8566, 2.3522))
            assertFalse(h.binding.refreshLocation(request.token))
            assertFalse(h.binding.cancelSelection(request.token))
            assertNull(h.binding.confirm(request.token))
            assertEquals(LocationPermissionAction.NotVisible, h.binding.requestPermission(request.token))
            assertEquals(selected, h.location.state.value)
            assertEquals(draft, h.draft.state.value)
        }
        h.scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals(1, h.fixture.source.requests.get())
        assertNull(onMain { h.binding.confirm(request.token) })
        h.capture(request)
        assertTrue(onMain { h.binding.confirm(request.token) } is NearbyLocationConfirmation.Confirmed)
        assertEquals(2, h.fixture.source.requests.get())
    }

    @Test
    fun abrirOtraSeleccionCancelaLaCapturaYUnCallbackTardioNoAutorizaElBorradorNuevo() = withHarness { h ->
        val lateReading = CompletableDeferred<DeviceLocationFix?>()
        h.fixture.source.nextReading = { withContext(NonCancellable) { lateReading.await() } }
        try {
            val first = onMain {
                h.binding.openSelection()!!.also {
                    assertTrue(h.binding.select(it.token, LATITUDE, LONGITUDE))
                    assertTrue(h.binding.refreshLocation(it.token))
                }
            }
            await("La lectura antigua debe estar esperando su callback.") { h.fixture.source.requests.get() == 1 }
            val current = onMain {
                h.binding.openSelection()!!.also {
                    assertNotSame(first.token, it.token)
                    assertTrue(h.binding.select(it.token, NEW_LATITUDE, NEW_LONGITUDE))
                }
            }
            val pin = h.location.state.value.proposal
            lateReading.complete(h.fixture.syntheticFix())
            await("El callback antiguo debe terminar sin publicar evidencia.") { h.fixture.source.completions.get() == 1 }
            onMain {
                assertNull(h.fixture.evidence.currentFix())
                assertEquals(pin, h.location.state.value.proposal)
                assertSame(current, h.binding.currentSelection)
                assertNull(h.binding.confirm(first.token))
                assertNull(h.binding.confirm(current.token))
                assertTrue(h.draft.state.value.locationSelectionPending)
                assertNull(h.draft.state.value.location.latitude)
                assertNull(h.draft.state.value.location.longitude)
            }
            assertEquals(1, h.fixture.source.requests.get())
            h.capture(current)
            assertTrue(onMain { h.binding.confirm(current.token) } is NearbyLocationConfirmation.Confirmed)
            assertEquals(2, h.fixture.source.requests.get())
        } finally {
            lateReading.complete(null)
        }
    }

    private fun populate(model: ReportDraftViewModel) {
        model.setType(IncidentType.RISK)
        model.setPriority(Priority.MEDIUM)
        model.setDescription("Hay una rama caída que tapa la entrada del parque 🌳")
        model.setOccurredAt(NOW.minusSeconds(60))
        model.setLocationReference(OLD_LOCATION.reference)
        val request = model.beginLocationSelection()!!
        assertTrue(model.applyLocationSelection(request.token, NearbyLocationConfirmation.Confirmed(OLD_LOCATION, 0.0)))
    }

    private fun withHarness(action: (Harness) -> Unit) {
        val intent = Intent(instrumentation.targetContext, LocationFlowHarnessActivity::class.java)
        val scenario = ActivityScenario.launch<LocationFlowHarnessActivity>(intent)
        try {
            lateinit var harness: Harness
            scenario.onActivity { activity ->
                assertEquals(LAB_PACKAGE, activity.packageName)
                harness = Harness(scenario, activity.fixture, activity.locationModel, activity.draftModel, activity.reportLocationBinding)
            }
            action(harness)
        } finally {
            scenario.close()
        }
    }

    private inner class Harness(
        val scenario: ActivityScenario<LocationFlowHarnessActivity>,
        val fixture: LocationFlowFixtureViewModel,
        val location: IncidentLocationViewModel,
        val draft: ReportDraftViewModel,
        var binding: ReportLocationFlowBinding,
    ) {
        fun capture(request: ReportLocationSelectionRequest) {
            fixture.source.nextReading = { fixture.syntheticFix() }
            onMain { assertTrue(binding.refreshLocation(request.token)) }
            await("El gesto explícito debe publicar la lectura controlada para la selección vigente.") {
                when (val state = location.state.value) {
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
        const val LAB_PACKAGE = "com.example.citizensecurity.verification.location.flow"
        const val LATITUDE = 19.4326077
        const val LONGITUDE = -99.1332088
        const val NEW_LATITUDE = 19.4350
        const val NEW_LONGITUDE = -99.1320
        val NOW = Instant.parse("2026-10-06T17:00:00Z")
        val OLD_LOCATION = ReportLocation("Frente al parque", 19.4326, -99.1332)
    }
}
