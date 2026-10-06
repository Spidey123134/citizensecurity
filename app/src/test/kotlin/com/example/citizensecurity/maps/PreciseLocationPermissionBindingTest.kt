package com.example.citizensecurity.maps

import android.Manifest
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.citizensecurity.data.PhoneLocationRefreshResult
import com.example.citizensecurity.domain.ReportLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Contrato AndroidX real y registro controlado; no representa un diálogo Android ejecutado. */
class PreciseLocationPermissionBindingTest {
    private val denied = LocationPermissionSnapshot(false, false, false)
    private val approximate = LocationPermissionSnapshot(false, true, false)
    private val precise = LocationPermissionSnapshot(true, true, false)

    @Test
    fun registrarNoLeePermisosNiSolicitaNada() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        var reads = 0
        PreciseLocationPermissionBinding(caller, model, { reads++; denied }, { true })
        assertTrue(caller.permissionContract is ActivityResultContracts.RequestMultiplePermissions)
        assertEquals(1, caller.registrations)
        assertEquals(0, reads)
        assertEquals(0, caller.launches)
        assertEquals(LocationPermissionAccess.Unknown, model.state.value.access)
    }

    @Test
    fun solicitudExplicitaPideAmbosPermisosUnaVez() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        val binding = binding(caller, model)
        assertEquals(LocationPermissionAction.LaunchRequest, binding.request())
        assertArrayEquals(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), caller.lastInput)
        assertEquals(LocationPermissionAction.AlreadyRunning, binding.request())
        binding.check()
        assertEquals(LocationPermissionAction.AlreadyRunning, binding.request())
        assertEquals(1, caller.launches)
        assertTrue(model.state.value.requestInFlight)
    }

    @Test
    fun explicarAntesDeSolicitarDeNuevoNoLanzaSinAceptacion() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        val binding = binding(caller, model, { approximate.copy(shouldExplain = true) })
        assertEquals(LocationPermissionAction.ExplanationRequired, binding.request())
        assertEquals(0, caller.launches)
        assertEquals(LocationPermissionAccess.Approximate, model.state.value.access)
        assertEquals(LocationPermissionAction.LaunchRequest, binding.request(explanationAccepted = true))
        assertEquals(1, caller.launches)
    }

    @Test
    fun permisoPrecisoActualNoVuelveAPedirDialogo() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        assertEquals(LocationPermissionAction.AlreadyGranted, binding(caller, model, { precise }).request())
        assertEquals(0, caller.launches)
        assertEquals(LocationPermissionAccess.Precise, model.state.value.access)
    }

    @Test
    fun resultadoPositivoNoSustituyeElPermisoActualRevocado() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        val binding = binding(caller, model)
        binding.request()
        caller.deliver(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true))
        assertEquals(LocationPermissionAccess.Denied, model.state.value.access)
        assertFalse(model.state.value.requestInFlight)
    }

    @Test
    fun resultadoAproximadoConservaElGradoActualSinSuponerPrecision() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        var current = denied
        val binding = binding(caller, model, { current })
        binding.request()
        current = approximate
        caller.deliver(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true))
        assertEquals(LocationPermissionAccess.Approximate, model.state.value.access)
        current = precise
        binding.check()
        assertEquals(LocationPermissionAccess.Precise, model.state.value.access)
        current = denied
        binding.check()
        assertEquals(LocationPermissionAccess.Denied, model.state.value.access)
        assertEquals(1, caller.launches)
    }

    @Test
    fun resultadoVacioMarcaCancelacionYPermiteReintentar() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        val binding = binding(caller, model)
        binding.request()
        caller.deliver(emptyMap())
        assertEquals(LocationPermissionIssue.Cancelled, model.state.value.issue)
        assertFalse(model.state.value.requestInFlight)
        assertEquals(LocationPermissionAction.LaunchRequest, binding.request())
        assertEquals(2, caller.launches)
    }

    @Test
    fun noSolicitaDesdePantallaQueNoEstaResumed() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        var reads = 0
        val binding = PreciseLocationPermissionBinding(caller, model, { reads++; denied }, { false })
        assertEquals(LocationPermissionAction.NotVisible, binding.request())
        assertEquals(0, reads)
        assertEquals(0, caller.launches)
    }

    @Test
    fun falloAlLeerNoConcedePermisoNiLanzaDialogo() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        model.inspect(precise)
        val binding = binding(caller, model, { throw SecurityException("Servicio de permisos no disponible.") })
        assertEquals(LocationPermissionAction.Unavailable, binding.request())
        assertEquals(LocationPermissionAccess.Unknown, model.state.value.access)
        assertEquals(LocationPermissionIssue.PermissionCheckFailed, model.state.value.issue)
        assertEquals(0, caller.launches)
    }

    @Test
    fun falloAlReleerCallbackTerminadoLiberaSolicitudParaReintentar() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        var broken = false
        val binding = binding(caller, model, { if (broken) error("No responde.") else denied })
        binding.request()
        broken = true
        caller.deliver(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true))
        assertFalse(model.state.value.requestInFlight)
        assertEquals(LocationPermissionAccess.Unknown, model.state.value.access)
        assertEquals(LocationPermissionIssue.PermissionCheckFailed, model.state.value.issue)
        broken = false
        assertEquals(LocationPermissionAction.LaunchRequest, binding.request())
    }

    @Test
    fun falloAlLanzarNoDejaSolicitudBloqueada() {
        val caller = FakeCaller().apply { failLaunch = true }
        val model = LocationPermissionViewModel()
        val binding = binding(caller, model)
        assertEquals(LocationPermissionAction.Unavailable, binding.request())
        assertFalse(model.state.value.requestInFlight)
        assertEquals(LocationPermissionIssue.RequestLaunchFailed, model.state.value.issue)
        caller.failLaunch = false
        assertEquals(LocationPermissionAction.LaunchRequest, binding.request())
    }

    @Test
    fun recrearPuenteConMismoModeloNoDuplicaDialogoYRecibeResultado() {
        val model = LocationPermissionViewModel()
        val old = FakeCaller()
        val first = binding(old, model)
        first.request()
        first.close()
        val new = FakeCaller()
        val second = binding(new, model, { precise })
        assertEquals(LocationPermissionAction.AlreadyRunning, second.request())
        assertEquals(0, new.launches)
        old.deliver(emptyMap())
        assertTrue(model.state.value.requestInFlight)
        new.deliver(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true))
        assertEquals(LocationPermissionAccess.Precise, model.state.value.access)
        assertFalse(model.state.value.requestInFlight)
        assertEquals(LocationPermissionAction.AlreadyGranted, second.request())
    }

    @Test
    fun callbackRestauradoSinSolicitudLocalSoloReleeHechos() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        binding(caller, model, { precise })
        caller.deliver(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to false))
        assertEquals(LocationPermissionAccess.Precise, model.state.value.access)
        assertEquals(0, caller.launches)
        assertFalse(model.state.value.requestInFlight)
    }

    @Test
    fun cerrarIgnoraCallbacksTardiosYDesregistraSoloUnaVez() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        var reads = 0
        val binding = binding(caller, model, { reads++; denied })
        binding.request()
        val before = model.state.value
        binding.close()
        binding.close()
        caller.deliver(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true))
        binding.check()
        assertEquals(LocationPermissionAction.Inactive, binding.request())
        assertEquals(before, model.state.value)
        assertEquals(1, reads)
        assertEquals(1, caller.unregisters)
    }

    @Test
    fun limpiarModeloIgnoraResultadoSinConsultarActivityAntigua() {
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        val model = ViewModelProvider(owner, LocationPermissionViewModel.factory())[LocationPermissionViewModel::class.java]
        val caller = FakeCaller()
        var reads = 0
        val binding = binding(caller, model, { reads++; precise })
        store.clear()
        caller.deliver(emptyMap())
        binding.check()
        assertEquals(LocationPermissionAction.Inactive, binding.request())
        assertEquals(0, reads)
    }

    @Test
    fun cerrarMientrasSeLeeEvitaLanzarDespuesDeSalir() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        lateinit var binding: PreciseLocationPermissionBinding
        binding = binding(caller, model, { binding.close(); denied })
        assertEquals(LocationPermissionAction.Inactive, binding.request())
        assertEquals(0, caller.launches)
        assertFalse(model.state.value.requestInFlight)
    }

    @Test
    fun cancelacionDeLaLecturaSePropagaSinSolicitarPermiso() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        val failure = CancellationException("Se abandono la operacion.")
        val binding = binding(caller, model, { throw failure })
        try {
            binding.request()
            fail("Debe propagar cancelacion.")
        } catch (received: CancellationException) {
            assertSame(failure, received)
        }
        assertEquals(0, caller.launches)
        assertFalse(model.state.value.requestInFlight)
    }

    @Test
    fun falloDeLecturaDespuesDeCerrarNoBorraLaComprobacionDelNuevoDueno() {
        val old = FakeCaller()
        val model = LocationPermissionViewModel()
        val next = binding(FakeCaller(), model, { precise })
        lateinit var previous: PreciseLocationPermissionBinding
        previous = binding(old, model, {
            previous.close()
            next.check()
            throw SecurityException("La pantalla anterior ya termino.")
        })
        previous.check()
        assertEquals(LocationPermissionAccess.Precise, model.state.value.access)
        assertEquals(null, model.state.value.issue)
    }

    @Test
    fun callbackFallidoDelPuenteCerradoNoDesbloqueaOtraSolicitud() {
        val old = FakeCaller()
        val model = LocationPermissionViewModel()
        var completing = false
        lateinit var previous: PreciseLocationPermissionBinding
        previous = binding(old, model, {
            if (completing) {
                previous.close()
                model.onPermissionResult(denied, cancelled = false)
                model.prepareRequest(denied)
                throw SecurityException("Ya pertenece a otro dueno.")
            }
            denied
        })
        previous.request()
        completing = true
        old.deliver(emptyMap())
        assertTrue(model.state.value.requestInFlight)
        assertEquals(LocationPermissionAccess.Denied, model.state.value.access)
        assertEquals(null, model.state.value.issue)
    }

    @Test
    fun solicitudQueFallaDespuesDeCerrarDevuelveInactivoSinBorrarHechosNuevos() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        lateinit var binding: PreciseLocationPermissionBinding
        binding = binding(caller, model, {
            binding.close()
            model.inspect(precise)
            error("Se destruyo el dueno.")
        })
        assertEquals(LocationPermissionAction.Inactive, binding.request())
        assertEquals(LocationPermissionAccess.Precise, model.state.value.access)
        assertEquals(null, model.state.value.issue)
        assertEquals(0, caller.launches)
    }

    @Test
    fun falloDelLanzadorCerradoNoAlteraElModeloRetenido() {
        val caller = FakeCaller()
        val model = LocationPermissionViewModel()
        val binding = binding(caller, model)
        caller.onLaunch = {
            binding.close()
            model.onPermissionResult(precise, cancelled = false)
            error("El lanzador anterior ya no es utilizable.")
        }
        assertEquals(LocationPermissionAction.Inactive, binding.request())
        assertEquals(LocationPermissionAccess.Precise, model.state.value.access)
        assertEquals(null, model.state.value.issue)
        assertFalse(model.state.value.requestInFlight)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun concederPermisoNoIniciaGpsNiConfirmaElPin() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store = ViewModelStore()
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        var captures = 0
        try {
            val locationModel = ViewModelProvider(owner, IncidentLocationViewModel.factory(
                ReportLocation("Frente al parque", 19.43, -99.13),
                { captures++; PhoneLocationRefreshResult.Unavailable },
                { null },
                { 300_000_000_000L },
            )).get(IncidentLocationViewModel::class.java)
            locationModel.select(19.432, -99.135)
            val before = locationModel.state.value
            val permissions = LocationPermissionViewModel()
            val caller = FakeCaller()
            var current = denied
            val binding = binding(caller, permissions, { current })
            binding.request()
            current = precise
            caller.deliver(mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true))
            assertEquals(LocationPermissionAccess.Precise, permissions.state.value.access)
            assertEquals(before, locationModel.state.value)
            assertEquals(0, captures)
            locationModel.refreshLocation()
            assertEquals(1, captures)
            assertEquals(IncidentLocationIssue.Unavailable, (locationModel.state.value as IncidentLocationState.Error).issue)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    private fun binding(
        caller: FakeCaller,
        model: LocationPermissionViewModel,
        read: () -> LocationPermissionSnapshot = { denied },
    ) = PreciseLocationPermissionBinding(caller, model, read, { true })

    private class FakeCaller : ActivityResultCaller {
        var registrations = 0
        var launches = 0
        var unregisters = 0
        var failLaunch = false
        var onLaunch: (() -> Unit)? = null
        var lastInput: Array<String>? = null
        var permissionContract: ActivityResultContract<*, *>? = null
        private lateinit var callback: (Map<String, Boolean>) -> Unit

        override fun <I, O> registerForActivityResult(
            contract: ActivityResultContract<I, O>, callback: ActivityResultCallback<O>,
        ): ActivityResultLauncher<I> {
            registrations++
            permissionContract = contract
            @Suppress("UNCHECKED_CAST")
            this.callback = { callback.onActivityResult(it as O) }
            return object : ActivityResultLauncher<I>() {
                override val contract: ActivityResultContract<I, *> = contract
                override fun launch(input: I, options: ActivityOptionsCompat?) {
                    onLaunch?.invoke()
                    if (failLaunch) error("El registro no puede lanzar la solicitud.")
                    @Suppress("UNCHECKED_CAST")
                    lastInput = (input as Array<String>).copyOf()
                    launches++
                }
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
