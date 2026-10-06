package com.example.citizensecurity.maps

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.citizensecurity.data.PhoneLocationRefreshResult
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.ReportLocation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Lifecycle AndroidX real, registro y GPS controlados; no acredita un diálogo o GPS físico. */
@OptIn(ExperimentalCoroutinesApi::class)
class IncidentLocationFlowBindingTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val bindings = mutableListOf<IncidentLocationFlowBinding>()
    private val precise = LocationPermissionSnapshot(true, true, false)
    private val denied = LocationPermissionSnapshot(false, false, false)
    private val original = ReportLocation("  Frente al parque  ", 19.4326077, -99.1332088)
    private val now = 300_000_000_000L
    private val fix = DeviceLocationFix(19.4326077, -99.1332088, 20.0, now, false)

    @Before
    fun prepareMain() { Dispatchers.setMain(dispatcher) }

    @After
    fun clearOwners() {
        bindings.forEach { it.close() }
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test
    fun registrarYVolverSoloConsultanPermisosSinCapturaNiConfirmacion() = runTest(dispatcher) {
        var captures = 0
        val model = model(refresh = { captures++; PhoneLocationRefreshResult.Ready(fix) })
        val owner = Owner()
        val caller = Caller()
        val permissions = LocationPermissionViewModel()
        var reads = 0
        bind(owner, model, caller, permissions) { reads++; precise }
        assertEquals(1, caller.registrations)
        assertEquals(0, reads)
        owner.resume()
        assertEquals(1, reads)
        assertEquals(LocationPermissionAccess.Precise, permissions.state.value.access)
        owner.stop()
        owner.resume()
        assertEquals(2, reads)
        runCurrent()
        assertEquals(0, captures)
        assertEquals(0, caller.launches)
        assertTrue(model.state.value is IncidentLocationState.Idle)
    }

    @Test
    fun accionesAntesDeResumedNoCambianPinNiLanzanPermisoOCaptura() = runTest(dispatcher) {
        var captures = 0
        val model = model(refresh = { captures++; PhoneLocationRefreshResult.Ready(fix) })
        val owner = Owner()
        val caller = Caller()
        val bridge = bind(owner, model, caller)
        owner.create()
        val before = model.state.value
        assertFalse(bridge.select(48.8566, 2.3522))
        assertFalse(bridge.refreshLocation())
        assertNull(bridge.confirm())
        assertEquals(LocationPermissionAction.NotVisible, bridge.requestPermission())
        owner.start()
        assertFalse(bridge.select(fix.latitude, fix.longitude))
        assertFalse(bridge.refreshLocation())
        assertNull(bridge.confirm())
        runCurrent()
        assertEquals(before, model.state.value)
        assertEquals(0, captures)
        assertEquals(0, caller.launches)
    }

    @Test
    fun gestosVisiblesUsanElMismoModeloYConfirmacionNoGuardaNiCambiaReferencia() = runTest(dispatcher) {
        var captures = 0
        val model = model(refresh = { captures++; PhoneLocationRefreshResult.Ready(fix) })
        val owner = Owner()
        val bridge = bind(owner, model)
        owner.resume()
        assertTrue(bridge.select(fix.latitude, fix.longitude))
        assertTrue(bridge.refreshLocation())
        runCurrent()
        val result = bridge.confirm() as NearbyLocationConfirmation.Confirmed
        assertEquals(original.reference, result.location.reference)
        assertEquals(1, captures)
        assertTrue(model.state.value is IncidentLocationState.Confirmed)
        assertNull(bridge.confirm())
    }

    @Test
    fun onPauseBloqueaGestosNuevosPeroConservaCapturaHastaOnStop() = runTest(dispatcher) {
        val completion = CompletableDeferred<PhoneLocationRefreshResult>()
        var captures = 0
        val model = model(refresh = { captures++; completion.await() })
        val owner = Owner()
        val bridge = bind(owner, model)
        owner.resume()
        bridge.select(fix.latitude, fix.longitude)
        bridge.refreshLocation()
        runCurrent()
        owner.pause()
        val waiting = model.state.value
        assertTrue(waiting is IncidentLocationState.Capturing)
        assertFalse(bridge.select(48.8566, 2.3522))
        assertFalse(bridge.refreshLocation())
        assertNull(bridge.confirm())
        assertEquals(LocationPermissionAction.NotVisible, bridge.requestPermission())
        assertEquals(waiting, model.state.value)
        assertEquals(1, captures)
        owner.stop()
        runCurrent()
        assertTrue(model.state.value is IncidentLocationState.PointSelected)
        completion.complete(PhoneLocationRefreshResult.Ready(fix))
        runCurrent()
        assertNull((model.state.value as IncidentLocationState.PointSelected).deviceFix)
    }

    @Test
    fun onStopCancelaCapturaConservaPinYVolverRequiereOtroGesto() = runTest(dispatcher) {
        val completion = CompletableDeferred<PhoneLocationRefreshResult>()
        var captures = 0
        val model = model(refresh = {
            if (++captures == 1) completion.await() else PhoneLocationRefreshResult.Ready(fix)
        })
        val owner = Owner()
        val bridge = bind(owner, model)
        owner.resume()
        bridge.select(fix.latitude, fix.longitude)
        bridge.refreshLocation()
        runCurrent()
        val pin = model.state.value.proposal
        owner.stop()
        runCurrent()
        assertEquals(pin, model.state.value.proposal)
        assertFalse(bridge.refreshLocation())
        owner.resume()
        assertNull(bridge.confirm())
        assertEquals(1, captures)
        assertSame(IncidentLocationIssue.RefreshRequired, (model.state.value as IncidentLocationState.Error).issue)
        bridge.refreshLocation()
        runCurrent()
        assertEquals(2, captures)
        assertTrue(bridge.confirm() is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun destroyCierraUnaVezIgnoraCallbacksYDesconectaObserver() = runTest(dispatcher) {
        val owner = Owner()
        val caller = Caller()
        val permissions = LocationPermissionViewModel()
        val model = model()
        var reads = 0
        val bridge = bind(owner, model, caller, permissions) { reads++; denied }
        owner.resume()
        assertEquals(LocationPermissionAction.LaunchRequest, bridge.requestPermission())
        owner.destroy()
        val before = model.state.value
        val permissionBefore = permissions.state.value
        val readsBefore = reads
        bridge.close()
        caller.deliver(mapOf("fine" to true))
        assertFalse(bridge.select(fix.latitude, fix.longitude))
        assertFalse(bridge.refreshLocation())
        assertNull(bridge.confirm())
        assertEquals(LocationPermissionAction.Inactive, bridge.requestPermission())
        assertEquals(before, model.state.value)
        assertEquals(permissionBefore, permissions.state.value)
        assertEquals(readsBefore, reads)
        assertEquals(1, caller.unregisters)
        assertEquals(0, owner.registry.observerCount)
    }

    @Test
    fun closeExplicitoCancelaCapturaEnEsperaSinCerrarSesionRetenida() = runTest(dispatcher) {
        var captures = 0
        val model = model(refresh = { captures++; PhoneLocationRefreshResult.Ready(fix) })
        val owner = Owner()
        val caller = Caller()
        var reads = 0
        val bridge = bind(owner, model, caller) { reads++; precise }
        owner.resume()
        bridge.select(fix.latitude, fix.longitude)
        val pin = model.state.value.proposal
        bridge.refreshLocation()
        bridge.close()
        bridge.close()
        owner.stop()
        owner.resume()
        runCurrent()
        assertEquals(0, captures)
        assertEquals(1, reads)
        assertEquals(1, caller.unregisters)
        assertEquals(pin, model.state.value.proposal)
        assertFalse(bridge.refreshLocation())
        // El modelo retenido conserva su sesión para un puente del nuevo dueño.
        val nextOwner = Owner()
        val next = bind(nextOwner, model)
        nextOwner.resume()
        next.refreshLocation()
        runCurrent()
        assertEquals(1, captures)
        assertTrue(next.confirm() is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun callbackDelDialogoMientrasEstaPausadaNoCierraPermisoNiIniciaGps() = runTest(dispatcher) {
        var captures = 0
        var current = denied
        val permissions = LocationPermissionViewModel()
        val owner = Owner()
        val caller = Caller()
        val model = model(refresh = { captures++; PhoneLocationRefreshResult.Ready(fix) })
        val bridge = bind(owner, model, caller, permissions) { current }
        owner.resume()
        bridge.select(fix.latitude, fix.longitude)
        val pin = model.state.value.proposal
        assertEquals(LocationPermissionAction.LaunchRequest, bridge.requestPermission())
        owner.pause()
        current = precise
        caller.deliver(mapOf("fine" to true))
        assertEquals(0, caller.unregisters)
        assertEquals(LocationPermissionAccess.Precise, permissions.state.value.access)
        assertFalse(permissions.state.value.requestInFlight)
        assertEquals(pin, model.state.value.proposal)
        assertNull(bridge.confirm())
        owner.resume()
        runCurrent()
        assertEquals(0, captures)
        bridge.refreshLocation()
        runCurrent()
        assertEquals(1, captures)
    }

    @Test
    fun recrearConservaSolicitudPendienteSinObserverNiDialogoDuplicado() = runTest(dispatcher) {
        val model = model()
        val permissions = LocationPermissionViewModel()
        val previousOwner = Owner()
        val previousCaller = Caller()
        val previous = bind(previousOwner, model, previousCaller, permissions) { denied }
        previousOwner.resume()
        previous.select(fix.latitude, fix.longitude)
        val pin = model.state.value.proposal
        assertEquals(LocationPermissionAction.LaunchRequest, previous.requestPermission())
        previousOwner.destroy()
        assertTrue(permissions.state.value.requestInFlight)
        val nextOwner = Owner()
        val nextCaller = Caller()
        val next = bind(nextOwner, model, nextCaller, permissions) { precise }
        nextOwner.resume()
        assertEquals(LocationPermissionAction.AlreadyRunning, next.requestPermission())
        assertEquals(0, nextCaller.launches)
        assertEquals(0, previousOwner.registry.observerCount)
        assertEquals(1, nextOwner.registry.observerCount)
        previousCaller.deliver(emptyMap())
        assertTrue(permissions.state.value.requestInFlight)
        nextCaller.deliver(mapOf("fine" to true))
        assertFalse(permissions.state.value.requestInFlight)
        assertEquals(LocationPermissionAccess.Precise, permissions.state.value.access)
        assertEquals(pin, model.state.value.proposal)
        assertNull(next.confirm())
    }

    @Test
    fun registroTardioODestruidoFallaSinAgregarObserver() {
        listOf(Lifecycle.State.STARTED, Lifecycle.State.RESUMED, Lifecycle.State.DESTROYED).forEach { state ->
            val owner = Owner()
            if (state == Lifecycle.State.DESTROYED) owner.create()
            owner.registry.currentState = state
            val caller = Caller()
            val permission = permissions(owner, caller, LocationPermissionViewModel()) { precise }
            try {
                try {
                    IncidentLocationFlowBinding(owner, permission, model())
                    fail("El registro tardío debe fallar antes de instalar el observer.")
                } catch (_: IllegalStateException) {
                    assertEquals(0, owner.registry.observerCount)
                }
            } finally {
                permission.close()
            }
        }
    }

    @Test
    fun unSegundoPuenteDelMismoDuenoFallaYElOriginalSigueSiendoElUnico() {
        val owner = Owner()
        val caller = Caller()
        val model = model()
        var reads = 0
        val bridge = bind(owner, model, caller) { reads++; precise }
        try {
            IncidentLocationFlowBinding(owner, bridge.permissionBinding, model)
            fail("Un dueño debe tener un único puente activo.")
        } catch (_: IllegalStateException) {
            assertEquals(1, owner.registry.observerCount)
        }
        owner.resume()
        assertEquals(1, reads)
        assertEquals(1, caller.registrations)
        assertEquals(LocationPermissionAction.AlreadyGranted, bridge.requestPermission())
        assertEquals(0, caller.unregisters)
    }

    @Test
    fun cerrarOtraVezElPuenteAnteriorNoInvalidaLaLecturaDelNuevoDueno() = runTest(dispatcher) {
        val model = model()
        val oldOwner = Owner()
        val previous = bind(oldOwner, model)
        oldOwner.resume()
        previous.select(fix.latitude, fix.longitude)
        oldOwner.destroy()
        val nextOwner = Owner()
        val next = bind(nextOwner, model)
        nextOwner.resume()
        next.refreshLocation()
        runCurrent()
        previous.close()
        assertTrue(next.confirm() is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun segundaAperturaVisibleReusaRegistroYLasAperturasInactivasNoCambianLaSesion() = runTest(dispatcher) {
        var captures = 0
        val model = model(refresh = { captures++; PhoneLocationRefreshResult.Ready(fix) })
        val owner = Owner()
        val caller = Caller()
        val bridge = bind(owner, model, caller)
        val nextOriginal = ReportLocation("  Nuevo reporte cerca del parque  ", 19.434, -99.134)
        assertFalse(bridge.beginSelection(nextOriginal))
        assertTrue(model.state.value is IncidentLocationState.Idle)
        owner.resume()
        bridge.select(fix.latitude, fix.longitude)
        bridge.refreshLocation()
        runCurrent()
        assertTrue(bridge.confirm() is NearbyLocationConfirmation.Confirmed)
        val confirmed = model.state.value
        owner.pause()
        assertFalse(bridge.beginSelection(nextOriginal))
        assertEquals(confirmed, model.state.value)
        owner.resume()
        assertTrue(bridge.beginSelection(nextOriginal))
        assertEquals(IncidentLocationState.Idle(), model.state.value)
        assertEquals(1, caller.registrations)
        assertEquals(1, captures)
        bridge.select(fix.latitude, fix.longitude)
        assertNull(bridge.confirm())
        bridge.refreshLocation()
        runCurrent()
        val accepted = bridge.confirm() as NearbyLocationConfirmation.Confirmed
        assertEquals(nextOriginal.reference, accepted.location.reference)
        assertEquals(2, captures)
        owner.destroy()
        val closed = model.state.value
        assertFalse(bridge.beginSelection(original))
        assertEquals(closed, model.state.value)
    }

    private fun bind(
        owner: Owner,
        model: IncidentLocationViewModel,
        caller: Caller = Caller(),
        permissionModel: LocationPermissionViewModel = LocationPermissionViewModel(),
        read: () -> LocationPermissionSnapshot = { precise },
    ) = IncidentLocationFlowBinding(owner, permissions(owner, caller, permissionModel, read), model)
        .also(bindings::add)

    private fun permissions(
        owner: Owner,
        caller: Caller,
        model: LocationPermissionViewModel,
        read: () -> LocationPermissionSnapshot,
    ) = PreciseLocationPermissionBinding(caller, model, read) {
        owner.lifecycle.currentState == Lifecycle.State.RESUMED
    }

    private fun model(
        refresh: suspend () -> PhoneLocationRefreshResult = { PhoneLocationRefreshResult.Ready(fix) },
    ): IncidentLocationViewModel {
        val store = ViewModelStore().also(stores::add)
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        return ViewModelProvider(owner, IncidentLocationViewModel.factory(original, refresh, { fix }, { now }))
            .get(IncidentLocationViewModel::class.java)
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
        fun create() { registry.currentState = Lifecycle.State.CREATED }
        fun start() { registry.currentState = Lifecycle.State.STARTED }
        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun pause() { registry.currentState = Lifecycle.State.STARTED }
        fun stop() { registry.currentState = Lifecycle.State.CREATED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }

    private class Caller : ActivityResultCaller {
        var registrations = 0
        var launches = 0
        var unregisters = 0
        private lateinit var callback: (Map<String, Boolean>) -> Unit

        override fun <I, O> registerForActivityResult(
            contract: ActivityResultContract<I, O>, callback: ActivityResultCallback<O>,
        ): ActivityResultLauncher<I> {
            registrations++
            @Suppress("UNCHECKED_CAST")
            this.callback = { callback.onActivityResult(it as O) }
            return object : ActivityResultLauncher<I>() {
                override val contract: ActivityResultContract<I, *> = contract
                override fun launch(input: I, options: ActivityOptionsCompat?) { launches++ }
                override fun unregister() { unregisters++ }
            }
        }

        override fun <I, O> registerForActivityResult(
            contract: ActivityResultContract<I, O>, registry: ActivityResultRegistry,
            callback: ActivityResultCallback<O>,
        ): ActivityResultLauncher<I> = registerForActivityResult(contract, callback)

        fun deliver(result: Map<String, Boolean>) { callback(result) }
    }
}
