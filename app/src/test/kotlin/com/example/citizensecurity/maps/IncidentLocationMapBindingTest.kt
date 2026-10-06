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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Eventos del adaptador controlados: no representa Google Maps renderizado ni GPS físico. */
@OptIn(ExperimentalCoroutinesApi::class)
class IncidentLocationMapBindingTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val flows = mutableListOf<IncidentLocationFlowBinding>()
    private val maps = mutableListOf<IncidentLocationMapBinding>()
    private val now = 300_000_000_000L
    private val fix = DeviceLocationFix(19.4326077, -99.1332088, 20.0, now, false)

    @Before
    fun prepareMain() { Dispatchers.setMain(dispatcher) }

    @After
    fun closeOwners() {
        maps.forEach { it.close() }
        flows.forEach { it.close() }
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test
    fun clicksAntesDeResumedYDespuesDeStopNoCambianLaSeleccion() = runTest(dispatcher) {
        val h = harness()
        val host = Host()
        connect(host, h.flow)
        host.click(48.8566, 2.3522)
        assertTrue(h.model.state.value is IncidentLocationState.Idle)
        h.owner.resume()
        host.click(fix.latitude, fix.longitude)
        val selected = h.model.state.value.proposal
        assertTrue(h.model.state.value is IncidentLocationState.PointSelected)
        h.owner.pause()
        host.click(48.8566, 2.3522)
        assertEquals(selected, h.model.state.value.proposal)
        h.owner.stop()
        host.click(48.8566, 2.3522)
        assertEquals(selected, h.model.state.value.proposal)
        h.owner.resume()
        runCurrent()
        assertEquals(0, h.captures)
        assertEquals(0, h.caller.launches)
        assertEquals(1, host.registrations)
    }

    @Test
    fun cerrarMapaCancelaCapturaEIgnoraClicksTardiosSinCerrarPermisos() = runTest(dispatcher) {
        var cancelled = false
        val h = harness(refresh = {
            try { awaitCancellation() } finally { cancelled = true }
        })
        val host = Host()
        val map = connect(host, h.flow)
        h.owner.resume()
        host.click(fix.latitude, fix.longitude)
        val pin = h.model.state.value.proposal
        h.flow.refreshLocation()
        runCurrent()
        assertTrue(h.model.state.value is IncidentLocationState.Capturing)
        map.close()
        runCurrent()
        assertTrue(cancelled)
        assertEquals(1, host.removals)
        assertNull(host.currentListener)
        host.deliverLate(48.8566, 2.3522)
        assertEquals(pin, h.model.state.value.proposal)
        assertNull(h.flow.confirm())
        assertEquals(LocationPermissionAction.AlreadyGranted, h.flow.requestPermission())
        assertEquals(0, h.caller.unregisters)
        map.close()
        assertEquals(1, host.removals)
        h.owner.destroy()
        assertEquals(1, h.caller.unregisters)
    }

    @Test
    fun recrearMapaReusaFlujoYCloseAnteriorNoInvalidaNuevaRenovacion() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        val oldHost = Host()
        val previous = connect(oldHost, h.flow)
        oldHost.click(fix.latitude, fix.longitude)
        h.flow.refreshLocation()
        runCurrent()
        assertEquals(1, h.captures)
        previous.close()
        assertNull(h.flow.confirm())
        val host = Host()
        connect(host, h.flow)
        assertEquals(1, h.caller.registrations)
        assertEquals(1, h.captures)
        host.click(fix.latitude, fix.longitude)
        h.flow.refreshLocation()
        runCurrent()
        oldHost.deliverLate(48.8566, 2.3522)
        previous.close()
        assertTrue(h.flow.confirm() is NearbyLocationConfirmation.Confirmed)
        assertEquals(2, h.captures)
        assertEquals(0, h.caller.unregisters)
    }

    @Test
    fun falloAlRetirarListenerAunCancelaCapturaYBloqueaCallbackAnterior() = runTest(dispatcher) {
        var cancelled = false
        val h = harness(refresh = {
            try { awaitCancellation() } finally { cancelled = true }
        })
        val host = Host()
        val map = connect(host, h.flow)
        h.owner.resume()
        host.click(fix.latitude, fix.longitude)
        val pin = h.model.state.value.proposal
        h.flow.refreshLocation()
        runCurrent()
        host.failRemoval = true
        try {
            map.close()
            fail("El adaptador comunica su fallo al retirar el listener.")
        } catch (_: IllegalStateException) {
            // La operación de retirada falló; el callback ya es inerte por nuestro cierre.
        }
        runCurrent()
        assertTrue(cancelled)
        host.deliverLate(48.8566, 2.3522)
        assertEquals(pin, h.model.state.value.proposal)
        assertNull(h.flow.confirm())
        assertEquals(0, h.caller.unregisters)
        host.failRemoval = false
    }

    private fun connect(host: Host, flow: IncidentLocationFlowBinding) =
        IncidentLocationMapBinding(host, flow).also(maps::add)

    private fun harness(
        refresh: suspend () -> PhoneLocationRefreshResult = { PhoneLocationRefreshResult.Ready(fix) },
    ): Harness {
        val owner = Owner()
        val caller = Caller()
        val permissions = LocationPermissionViewModel()
        val store = ViewModelStore().also(stores::add)
        val storeOwner = object : ViewModelStoreOwner { override val viewModelStore = store }
        var captures = 0
        val model = ViewModelProvider(storeOwner, IncidentLocationViewModel.factory(
            ReportLocation("Frente al parque", fix.latitude, fix.longitude),
            { captures++; refresh() }, { fix }, { now },
        )).get(IncidentLocationViewModel::class.java)
        val permissionBinding = PreciseLocationPermissionBinding(
            caller, permissions, { LocationPermissionSnapshot(true, true, false) },
            { owner.lifecycle.currentState == Lifecycle.State.RESUMED },
        )
        val flow = IncidentLocationFlowBinding(owner, permissionBinding, model).also(flows::add)
        return Harness(owner, caller, model, flow) { captures }
    }

    private class Harness(
        val owner: Owner,
        val caller: Caller,
        val model: IncidentLocationViewModel,
        val flow: IncidentLocationFlowBinding,
        private val captureCount: () -> Int,
    ) { val captures: Int get() = captureCount() }

    private class Host : IncidentMapClickHost {
        var registrations = 0
        var removals = 0
        var failRemoval = false
        var currentListener: ((Double, Double) -> Unit)? = null
        private var previous: ((Double, Double) -> Unit)? = null

        override fun setListener(listener: ((Double, Double) -> Unit)?) {
            if (listener == null) {
                removals++
                if (failRemoval) error("El SDK no retiró el listener.")
            } else {
                registrations++
                previous = listener
            }
            currentListener = listener
        }

        fun click(latitude: Double, longitude: Double) { currentListener?.invoke(latitude, longitude) }
        fun deliverLate(latitude: Double, longitude: Double) { previous?.invoke(latitude, longitude) }
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
        var registrations = 0
        var launches = 0
        var unregisters = 0

        override fun <I, O> registerForActivityResult(
            contract: ActivityResultContract<I, O>, callback: ActivityResultCallback<O>,
        ): ActivityResultLauncher<I> {
            registrations++
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
    }
}
