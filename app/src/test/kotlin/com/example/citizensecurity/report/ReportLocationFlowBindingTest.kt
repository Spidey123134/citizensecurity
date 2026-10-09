package com.example.citizensecurity.report

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.data.PhoneLocationRefreshResult
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.maps.IncidentLocationFlowBinding
import com.example.citizensecurity.maps.IncidentLocationState
import com.example.citizensecurity.maps.IncidentLocationViewModel
import com.example.citizensecurity.maps.IncidentMapClickHost
import com.example.citizensecurity.maps.LocationPermissionAction
import com.example.citizensecurity.maps.LocationPermissionSnapshot
import com.example.citizensecurity.maps.LocationPermissionViewModel
import com.example.citizensecurity.maps.PreciseLocationPermissionBinding
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Reproduce callbacks y controles tardíos; no representa mapa renderizado ni GPS físico. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportLocationFlowBindingTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val flows = mutableListOf<IncidentLocationFlowBinding>()
    private val bindings = mutableListOf<ReportLocationFlowBinding>()
    private val elapsed = 300_000_000_000L
    private val fix = DeviceLocationFix(19.4326077, -99.1332088, 20.0, elapsed, false)
    private val original = ReportLocation("Frente al parque", 19.431, -99.131)

    @Before
    fun prepareMain() { Dispatchers.setMain(dispatcher) }

    @After
    fun closeOwners() {
        bindings.forEach { it.close() }
        flows.forEach { it.close() }
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test
    fun controlesDeAperturaAnteriorNoModificanNiCierranLaSeleccionActual() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        val a = h.binding.openSelection()!!
        val b = h.binding.openSelection()!!
        assertNotSame(a.token, b.token)
        h.binding.select(b.token, fix.latitude, fix.longitude)
        h.binding.refreshLocation(b.token)
        runCurrent()
        val ready = h.model.state.value
        val draft = h.draft.state.value
        assertFalse(h.binding.select(a.token, 48.8566, 2.3522))
        assertFalse(h.binding.refreshLocation(a.token))
        assertNull(h.binding.confirm(a.token))
        assertFalse(h.binding.cancelSelection(a.token))
        assertEquals(LocationPermissionAction.Inactive, h.binding.requestPermission(a.token))
        assertNull(h.binding.attachMap(a.token, Host()))
        assertEquals(ready, h.model.state.value)
        assertEquals(draft, h.draft.state.value)
        assertSame(b, h.binding.currentSelection)
        assertEquals(1, h.captures)
        assertEquals(0, h.caller.launches)
        assertTrue(h.binding.confirm(b.token) is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun mapaReemplazadoConMismoTokenNoRetiraListenerNiInvalidaLecturaNueva() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        val request = h.binding.openSelection()!!
        val host = Host()
        val previous = h.binding.attachMap(request.token, host)!!
        val previousClick = host.currentListener!!
        val current = h.binding.attachMap(request.token, host)!!
        val currentClick = host.currentListener!!
        host.click(fix.latitude, fix.longitude)
        h.binding.refreshLocation(request.token)
        runCurrent()
        val ready = h.model.state.value
        assertFalse(previousClick(48.8566, 2.3522))
        previous.close()
        assertSame(currentClick, host.currentListener)
        assertEquals(1, host.removals)
        assertEquals(ready, h.model.state.value)
        assertTrue(h.binding.confirm(request.token) is NearbyLocationConfirmation.Confirmed)
        assertFalse(host.click(48.8566, 2.3522))
        current.close()
    }

    @Test
    fun mapReadyAnteriorDeLaMismaAperturaNoReemplazaListenerNiDetieneCaptura() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        val request = h.binding.openSelection()!!
        val previous = h.binding.prepareMap(request.token)!!
        val current = h.binding.prepareMap(request.token)!!
        val host = Host()
        assertTrue(current.attachMap(host))
        val listener = host.currentListener
        assertTrue(host.click(fix.latitude, fix.longitude))
        h.binding.refreshLocation(request.token)
        runCurrent()
        val ready = h.model.state.value
        assertFalse(previous.attachMap(host))
        previous.close()
        assertSame(listener, host.currentListener)
        assertEquals(0, host.removals)
        assertEquals(ready, h.model.state.value)
        assertTrue(h.binding.confirm(request.token) is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun mapReadyDespuesDeCerrarLaVistaNoInstalaListenerNiIniciaCaptura() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        val request = h.binding.openSelection()!!
        val map = h.binding.prepareMap(request.token)!!
        map.close()
        val host = Host()
        assertFalse(map.attachMap(host))
        runCurrent()
        assertNull(host.currentListener)
        assertEquals(0, host.removals)
        assertEquals(0, h.captures)
        assertSame(request, h.binding.currentSelection)
    }

    @Test
    fun cerrarMapaActualCancelaGpsConservaTokenYPinYExigeRenovar() = runTest(dispatcher) {
        var cancelled = false
        val h = harness(refresh = { try { awaitCancellation() } finally { cancelled = true } })
        h.owner.resume()
        val request = h.binding.openSelection()!!
        val host = Host()
        val map = h.binding.attachMap(request.token, host)!!
        host.click(fix.latitude, fix.longitude)
        val pin = h.model.state.value.proposal
        h.binding.refreshLocation(request.token)
        runCurrent()
        map.close()
        runCurrent()
        assertTrue(cancelled)
        assertSame(request, h.binding.currentSelection)
        assertEquals(pin, h.model.state.value.proposal)
        assertEquals(original, h.draft.state.value.location)
        assertNull(h.binding.confirm(request.token))
        assertFalse(host.deliverLate(48.8566, 2.3522))
        assertEquals(pin, h.model.state.value.proposal)
        assertEquals(0, h.caller.unregisters)
    }

    @Test
    fun adjuntarMapaEnCreatedNoSeleccionaNiIniciaGpsHastaGestoVisible() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        val request = h.binding.openSelection()!!
        h.owner.stop()
        val host = Host()
        assertTrue(h.binding.attachMap(request.token, host) != null)
        assertFalse(host.click(fix.latitude, fix.longitude))
        assertTrue(h.model.state.value is IncidentLocationState.Idle)
        h.owner.resume()
        runCurrent()
        assertEquals(0, h.captures)
        assertTrue(host.click(fix.latitude, fix.longitude))
        assertTrue(h.model.state.value is IncidentLocationState.PointSelected)
        assertNull(h.binding.confirm(request.token))
    }

    @Test
    fun recrearRecuperaTokenRetenidoYPinSinAbrirOtraSesionNiCapturar() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        val request = h.binding.openSelection()!!
        h.binding.select(request.token, fix.latitude, fix.longitude)
        h.binding.refreshLocation(request.token)
        runCurrent()
        val pin = h.model.state.value.proposal
        h.owner.destroy()
        assertNull(h.binding.currentSelection)
        val nextOwner = Owner()
        val next = bind(nextOwner, h.model, h.draft)
        assertSame(request, next.currentSelection)
        nextOwner.resume()
        runCurrent()
        assertEquals(1, h.captures)
        assertEquals(pin, h.model.state.value.proposal)
        assertNull(next.confirm(request.token))
        next.refreshLocation(request.token)
        runCurrent()
        h.binding.close()
        assertFalse(h.binding.cancelSelection(request.token))
        assertTrue(next.confirm(request.token) is NearbyLocationConfirmation.Confirmed)
        nextOwner.destroy()
    }

    @Test
    fun restaurarCamposEnOtroModeloNoRecuperaTokenNiAutorizacionGps() {
        val h = harness()
        h.owner.resume()
        val request = h.binding.openSelection()!!
        val snapshot = h.handle.keys().associateWith { h.handle.get<Any?>(it) }
        val restored = ReportDraftViewModel(SavedStateHandle(snapshot))
        assertEquals(h.draft.state.value.copy(locationSelectionPending = false), restored.state.value)
        assertNull(restored.currentLocationSelection())
        assertFalse(restored.isCurrentLocationSelection(request.token))
        assertFalse(restored.cancelLocationSelection(request.token))
    }

    @Test
    fun confirmarConservaReferenciaYCamposEditadosMientrasElMapaEstaAbierto() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        val request = h.binding.openSelection()!!
        h.binding.select(request.token, fix.latitude, fix.longitude)
        h.draft.setDescription("Se observa humo desde el mercado.")
        h.draft.setLocationReference("Referencia corregida")
        h.draft.setType(IncidentType.FIRE)
        h.draft.setPriority(Priority.HIGH)
        h.draft.setOccurredAt(Instant.parse("2026-10-06T18:00:00Z"))
        val edited = h.draft.state.value
        h.binding.refreshLocation(request.token)
        runCurrent()
        assertTrue(h.binding.confirm(request.token) is NearbyLocationConfirmation.Confirmed)
        assertEquals(edited.copy(
            location = edited.location.copy(latitude = fix.latitude, longitude = fix.longitude),
            locationSelectionPending = false,
        ), h.draft.state.value)
        assertNull(h.binding.currentSelection)
        assertNull(h.binding.confirm(request.token))
        assertFalse(h.binding.cancelSelection(request.token))
    }

    @Test
    fun confirmarFranciaDesdeMexicoBloqueaYCancelarConservaUbicacionDelBorrador() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        val request = h.binding.openSelection()!!
        h.binding.select(request.token, 48.8566, 2.3522)
        h.binding.refreshLocation(request.token)
        runCurrent()
        assertTrue(h.binding.confirm(request.token) is NearbyLocationConfirmation.Blocked)
        assertSame(request, h.binding.currentSelection)
        assertTrue(h.draft.state.value.locationSelectionPending)
        assertEquals(original, h.draft.state.value.location)
        h.draft.setLocationReference("Referencia modificada")
        assertTrue(h.binding.cancelSelection(request.token))
        assertEquals(original.copy(reference = "Referencia modificada"), h.draft.state.value.location)
        assertFalse(h.draft.state.value.locationSelectionPending)
        assertTrue(h.model.state.value is IncidentLocationState.Cancelled)
    }

    @Test
    fun abrirOtraSeleccionFueraDeResumedConservaAperturaYMapaActuales() {
        val h = harness()
        assertNull(h.binding.openSelection())
        assertNull(h.draft.currentLocationSelection())
        h.owner.resume()
        val request = h.binding.openSelection()!!
        val host = Host()
        h.binding.attachMap(request.token, host)
        val listener = host.currentListener
        h.owner.pause()
        assertNull(h.binding.openSelection())
        assertSame(request, h.binding.currentSelection)
        assertSame(listener, host.currentListener)
        assertEquals(0, host.removals)
    }

    @Test
    fun controlesInvisiblesNoEditanPinCierranTokenOPidenPermiso() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        val request = h.binding.openSelection()!!
        h.binding.select(request.token, fix.latitude, fix.longitude)
        h.owner.stop()
        val pin = h.model.state.value.proposal
        assertFalse(h.binding.select(request.token, 48.8566, 2.3522))
        assertFalse(h.binding.refreshLocation(request.token))
        assertNull(h.binding.confirm(request.token))
        assertFalse(h.binding.cancelSelection(request.token))
        assertEquals(LocationPermissionAction.NotVisible, h.binding.requestPermission(request.token))
        assertSame(request, h.binding.currentSelection)
        assertEquals(pin, h.model.state.value.proposal)
        assertEquals(0, h.captures)
        assertEquals(0, h.caller.launches)
    }

    @Test
    fun cerrarCoordinadorEsIdempotenteConservaBorradorYCierraSoloSusCallbacks() = runTest(dispatcher) {
        var cancelled = false
        val h = harness(refresh = { try { awaitCancellation() } finally { cancelled = true } })
        h.owner.resume()
        val request = h.binding.openSelection()!!
        val host = Host()
        h.binding.attachMap(request.token, host)
        host.click(fix.latitude, fix.longitude)
        h.binding.refreshLocation(request.token)
        runCurrent()
        h.binding.close()
        runCurrent()
        val stopped = h.model.state.value
        h.binding.close()
        host.deliverLate(48.8566, 2.3522)
        assertTrue(cancelled)
        assertEquals(stopped, h.model.state.value)
        assertSame(request, h.draft.currentLocationSelection())
        assertNull(h.binding.currentSelection)
        assertNull(h.binding.openSelection())
        assertEquals(LocationPermissionAction.Inactive, h.binding.requestPermission(request.token))
        assertEquals(1, host.removals)
        assertEquals(0, h.caller.unregisters)
        assertEquals(1, h.owner.registry.observerCount)
    }

    @Test
    fun abrirBDetieneCapturaDeAYNoRenuevaAutomaticamente() = runTest(dispatcher) {
        var cancelled = false
        val h = harness(refresh = { try { awaitCancellation() } finally { cancelled = true } })
        h.owner.resume()
        val a = h.binding.openSelection()!!
        val host = Host()
        h.binding.attachMap(a.token, host)
        h.binding.select(a.token, fix.latitude, fix.longitude)
        h.binding.refreshLocation(a.token)
        runCurrent()
        val b = h.binding.openSelection()!!
        runCurrent()
        assertTrue(cancelled)
        assertEquals(1, h.captures)
        assertEquals(1, host.removals)
        assertTrue(h.model.state.value is IncidentLocationState.Idle)
        assertSame(b, h.binding.currentSelection)
        assertNull(h.binding.confirm(b.token))
        assertEquals(original, h.draft.state.value.location)
    }

    @Test
    fun flujoDeOtroDuenoNoSeAcoplaNiAgregaObserver() {
        val h = harness()
        val otherOwner = Owner()
        expectFailure { ReportLocationFlowBinding(otherOwner, h.flow, h.draft) }
        assertEquals(0, otherOwner.registry.observerCount)
        assertEquals(2, h.owner.registry.observerCount)
    }

    @Test
    fun registroDuplicadoOTardioFallaYElPuenteOriginalSigueActivo() {
        val h = harness()
        expectFailure { ReportLocationFlowBinding(h.owner, h.flow, h.draft) }
        assertEquals(2, h.owner.registry.observerCount)
        h.owner.resume()
        val request = h.binding.openSelection()!!
        expectFailure { ReportLocationFlowBinding(h.owner, h.flow, h.draft) }
        assertSame(request, h.binding.currentSelection)
        h.owner.destroy()
        expectFailure { ReportLocationFlowBinding(h.owner, h.flow, h.draft) }
        assertEquals(0, h.owner.registry.observerCount)
    }

    @Test
    fun falloAlRetirarListenerDetieneGpsYElCallbackViejoQuedaInerte() = runTest(dispatcher) {
        var cancelled = false
        val h = harness(refresh = { try { awaitCancellation() } finally { cancelled = true } })
        h.owner.resume()
        val request = h.binding.openSelection()!!
        val host = Host()
        val map = h.binding.attachMap(request.token, host)!!
        host.click(fix.latitude, fix.longitude)
        val pin = h.model.state.value.proposal
        h.binding.refreshLocation(request.token)
        runCurrent()
        host.failRemoval = true
        expectFailure { map.close() }
        runCurrent()
        assertTrue(cancelled)
        host.deliverLate(48.8566, 2.3522)
        assertEquals(pin, h.model.state.value.proposal)
        assertSame(request, h.binding.currentSelection)
        assertNull(h.binding.confirm(request.token))
        host.failRemoval = false
        map.close()
        assertEquals(1, host.removals)
    }

    @Test
    fun falloAlAdjuntarMapaRetiraListenerParcialYDetieneCaptura() = runTest(dispatcher) {
        var cancelled = false
        val h = harness(refresh = { try { awaitCancellation() } finally { cancelled = true } })
        h.owner.resume()
        val request = h.binding.openSelection()!!
        h.binding.select(request.token, fix.latitude, fix.longitude)
        val pin = h.model.state.value.proposal
        h.binding.refreshLocation(request.token)
        runCurrent()
        val host = Host().apply { failInstall = true }
        expectFailure { h.binding.attachMap(request.token, host) }
        runCurrent()
        assertTrue(cancelled)
        assertNull(host.currentListener)
        host.deliverLate(48.8566, 2.3522)
        assertEquals(pin, h.model.state.value.proposal)
        assertSame(request, h.binding.currentSelection)
    }

    @Test
    fun mapaConTokenAntiguoNoReemplazaListenerNiDetieneCapturaActual() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        val a = h.binding.openSelection()!!
        val b = h.binding.openSelection()!!
        val host = Host()
        h.binding.attachMap(b.token, host)
        val listener = host.currentListener
        host.click(fix.latitude, fix.longitude)
        h.binding.refreshLocation(b.token)
        runCurrent()
        val ready = h.model.state.value
        assertNull(h.binding.attachMap(a.token, host))
        assertSame(listener, host.currentListener)
        assertEquals(0, host.removals)
        assertEquals(ready, h.model.state.value)
        assertTrue(h.binding.confirm(b.token) is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun resetDelBorradorInvalidaControlesYCallbacksSinAplicarElPuntoAnterior() {
        val h = harness()
        h.owner.resume()
        val request = h.binding.openSelection()!!
        val host = Host()
        h.binding.attachMap(request.token, host)
        h.draft.reset()
        val before = h.model.state.value
        assertFalse(host.click(fix.latitude, fix.longitude))
        assertNull(h.binding.currentSelection)
        assertFalse(h.binding.select(request.token, fix.latitude, fix.longitude))
        assertFalse(h.binding.refreshLocation(request.token))
        assertFalse(h.binding.cancelSelection(request.token))
        assertNull(h.binding.confirm(request.token))
        assertEquals(before, h.model.state.value)
        assertEquals(ReportDraftState(), h.draft.state.value)
    }

    @Test
    fun cerrarMapaTrasResetDirectoDetieneLaCapturaAunqueElTokenYaNoExista() = runTest(dispatcher) {
        var cancelled = false
        val h = harness(refresh = { try { awaitCancellation() } finally { cancelled = true } })
        h.owner.resume()
        val request = h.binding.openSelection()!!
        val host = Host()
        val map = h.binding.attachMap(request.token, host)!!
        host.click(fix.latitude, fix.longitude)
        h.binding.refreshLocation(request.token)
        runCurrent()
        h.draft.reset()
        map.close()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(ReportDraftState(), h.draft.state.value)
        val stopped = h.model.state.value
        host.deliverLate(48.8566, 2.3522)
        assertEquals(stopped, h.model.state.value)
        assertFalse(h.binding.refreshLocation(request.token))
        assertNull(h.binding.confirm(request.token))
    }

    private fun harness(
        refresh: suspend () -> PhoneLocationRefreshResult = { PhoneLocationRefreshResult.Ready(fix) },
    ): Harness {
        val store = ViewModelStore().also(stores::add)
        val storeOwner = object : ViewModelStoreOwner { override val viewModelStore = store }
        var captures = 0
        val model = ViewModelProvider(storeOwner, IncidentLocationViewModel.factory(
            original, { captures++; refresh() }, { fix }, { elapsed },
        )).get(IncidentLocationViewModel::class.java)
        val handle = SavedStateHandle()
        val draft = ViewModelProvider(storeOwner, viewModelFactory {
            initializer { ReportDraftViewModel(handle) }
        }).get(ReportDraftViewModel::class.java)
        draft.setLocationReference(original.reference)
        val initial = draft.beginLocationSelection()!!
        draft.applyLocationSelection(initial.token, NearbyLocationConfirmation.Confirmed(original, 100.0))
        val owner = Owner()
        val caller = Caller()
        val flow = flow(owner, model, caller)
        val binding = ReportLocationFlowBinding(owner, flow, draft).also(bindings::add)
        return Harness(owner, caller, handle, model, draft, flow, binding) { captures }
    }

    private fun flow(owner: Owner, model: IncidentLocationViewModel, caller: Caller = Caller()): IncidentLocationFlowBinding {
        val permissions = PreciseLocationPermissionBinding(
            caller, LocationPermissionViewModel(), { LocationPermissionSnapshot(true, true, false) },
            { owner.lifecycle.currentState == Lifecycle.State.RESUMED },
        )
        return IncidentLocationFlowBinding(owner, permissions, model).also(flows::add)
    }

    private fun bind(owner: Owner, model: IncidentLocationViewModel, draft: ReportDraftViewModel): ReportLocationFlowBinding =
        ReportLocationFlowBinding(owner, flow(owner, model), draft).also(bindings::add)

    private fun expectFailure(action: () -> Unit) {
        try { action(); fail("La operación debe rechazar el dueño o adjunto inválido.") }
        catch (_: IllegalStateException) { /* Rechazo esperado. */ }
    }

    private class Harness(
        val owner: Owner,
        val caller: Caller,
        val handle: SavedStateHandle,
        val model: IncidentLocationViewModel,
        val draft: ReportDraftViewModel,
        val flow: IncidentLocationFlowBinding,
        val binding: ReportLocationFlowBinding,
        private val captureCount: () -> Int,
    ) { val captures: Int get() = captureCount() }

    private class Host : IncidentMapClickHost {
        var removals = 0
        var failRemoval = false
        var failInstall = false
        var currentListener: ((Double, Double) -> Boolean)? = null
        private var previous: ((Double, Double) -> Boolean)? = null
        override fun setListener(listener: ((Double, Double) -> Boolean)?) {
            if (listener == null) {
                removals++
                if (failRemoval) error("El SDK no retiró el listener.")
            } else previous = listener
            currentListener = listener
            if (listener != null && failInstall) error("El SDK falló al instalar el listener.")
        }
        fun click(latitude: Double, longitude: Double): Boolean = currentListener?.invoke(latitude, longitude) == true
        fun deliverLate(latitude: Double, longitude: Double): Boolean = previous?.invoke(latitude, longitude) == true
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun pause() { registry.currentState = Lifecycle.State.STARTED }
        fun stop() { registry.currentState = Lifecycle.State.CREATED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }

    private class Caller : ActivityResultCaller {
        var launches = 0
        var unregisters = 0
        override fun <I, O> registerForActivityResult(
            contract: ActivityResultContract<I, O>, callback: ActivityResultCallback<O>,
        ): ActivityResultLauncher<I> = object : ActivityResultLauncher<I>() {
            override val contract: ActivityResultContract<I, *> = contract
            override fun launch(input: I, options: ActivityOptionsCompat?) { launches++ }
            override fun unregister() { unregisters++ }
        }
        override fun <I, O> registerForActivityResult(
            contract: ActivityResultContract<I, O>, registry: ActivityResultRegistry,
            callback: ActivityResultCallback<O>,
        ): ActivityResultLauncher<I> = registerForActivityResult(contract, callback)
    }
}
