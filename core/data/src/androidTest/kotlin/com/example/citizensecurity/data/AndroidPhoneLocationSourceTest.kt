package com.example.citizensecurity.data

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Callbacks nativos con backend aislado; no concede permisos ni obtiene GPS real. */
@RunWith(AndroidJUnit4::class)
class AndroidPhoneLocationSourceTest {

    @Test
    fun losServiciosYPermisosSeObtienenDelContextoDeAplicacion() {
        val application = context()
        val screenContext = object : ContextWrapper(application) {
            override fun getApplicationContext(): Context = application

            override fun getSystemService(name: String): Any? =
                error("No debe consultar el contexto de la pantalla.")

            override fun checkSelfPermission(permission: String): Int =
                error("No debe conservar el contexto de la pantalla.")
        }
        val source = AndroidPhoneLocationSource(screenContext)

        assertEquals(
            application.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
            source.hasPrecisePermission(),
        )
        assertEquals(
            application.getSystemService(LocationManager::class.java)
                .isProviderEnabled(LocationManager.GPS_PROVIDER),
            source.isLocationEnabled(),
        )
    }

    @Test
    fun elPermisoDenegadoDelSistemaNoIniciaUnaLectura() = runBlocking {
        val source = AndroidPhoneLocationSource(context())
        // Esta suite no declara ni concede FINE; comprobarlo evita una captura accidental.
        assertFalse(source.hasPrecisePermission())

        try {
            source.awaitFix()
            fail("Sin permiso debe rechazar antes de registrar GPS.")
        } catch (_: SecurityException) {
            // No se modificó el permiso del emulador.
        }
    }

    @Test
    fun sinPermisoNoConsultaElProveedorNiRegistraUnListener() = runBlocking {
        val backend = FakeGpsBackend()
        val source = AndroidPhoneLocationSource(backend) { false }

        try {
            source.awaitFix()
            fail("Falta el rechazo por permiso.")
        } catch (_: SecurityException) {
            assertEquals(0, backend.enabledChecks)
            assertEquals(0, backend.registrations)
            assertEquals(0, backend.removals)
        }
    }

    @Test
    fun conGpsApagadoTerminaSinRegistrarNiRecuperarUnaPosicionAnterior() = runBlocking {
        val backend = FakeGpsBackend().apply { enabled = false }
        val source = source(backend)

        assertFalse(source.isLocationEnabled())
        assertNull(source.awaitFix())
        assertEquals(0, backend.registrations)
        assertEquals(0, backend.removals)
    }

    @Test
    fun laPrimeraLecturaSeCopiaYRetiraLaSuscripcionAntesDeEntregarla() = runBlocking {
        val backend = FakeGpsBackend()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { source(backend).awaitFix() }
        assertEquals(1, backend.active.size)
        val listener = backend.lastListener()
        val location = location()

        listener.onLocationChanged(location)
        val received = requireNotNull(pending.await())
        assertEquals(1, backend.removals)
        assertTrue(backend.active.isEmpty())
        assertEquals(location.toDeviceLocationFix(), received)

        location.latitude = 48.8584
        location.accuracy = 900.0f
        listener.onLocationChanged(location)
        assertEquals(19.4326077, received.latitude, 0.0)
        assertEquals(12.5, received.accuracyMeters, 0.0)
        assertEquals(1, backend.removals)
    }

    @Test
    fun unCallbackSincronoDuranteElRegistroSeLimpiaDespuesDeTerminarElRegistro() = runBlocking {
        val backend = FakeGpsBackend().apply {
            onRegister = { listener ->
                listener.onLocationChanged(location())
                assertEquals(0, removals)
                assertTrue(active.contains(listener))
            }
        }

        assertEquals(location().toDeviceLocationFix(), source(backend).awaitFix())
        assertEquals(1, backend.registrations)
        assertEquals(1, backend.removals)
        assertTrue(backend.active.isEmpty())
    }

    @Test
    fun cancelarUnaLecturaPendienteRetiraElListenerYDescartaCallbacksPosteriores() = runBlocking {
        val backend = FakeGpsBackend()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { source(backend).awaitFix() }
        val listener = backend.lastListener()

        pending.cancelAndJoin()
        listener.onLocationChanged(location())
        listener.onProviderDisabled(LocationManager.GPS_PROVIDER)

        assertTrue(pending.isCancelled)
        assertTrue(backend.active.isEmpty())
        assertEquals(1, backend.removals)
    }

    @Test
    fun unaCorrutinaYaCanceladaNoLlegaARegistrarGps() = runBlocking {
        val backend = FakeGpsBackend()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().job.cancel()
            source(backend).awaitFix()
        }

        try {
            pending.await()
            fail("La solicitud debe conservar la cancelación.")
        } catch (_: CancellationException) {
            assertEquals(0, backend.registrations)
            assertEquals(0, backend.removals)
        }
    }

    @Test
    fun cancelarDuranteElRegistroEsperaLaLimpiezaDelRegistroParcial() = runBlocking {
        val backend = FakeGpsBackend()
        var requestJob: Job? = null
        backend.onRegister = {
            requireNotNull(requestJob).cancel()
            assertEquals(0, backend.removals)
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            requestJob = currentCoroutineContext().job
            source(backend).awaitFix()
        }

        try {
            pending.await()
            fail("Debe conservar la cancelación durante el registro.")
        } catch (_: CancellationException) {
            assertTrue(backend.active.isEmpty())
            assertEquals(1, backend.removals)
        }
    }

    @Test
    fun cancelarDesdeOtroHiloMientrasRegistraNoDejaUnListenerFueraDelControl() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancellationStarted = CountDownLatch(1)
        val backend = FakeGpsBackend().apply {
            onRegister = {
                entered.countDown()
                check(release.await(5L, TimeUnit.SECONDS)) { "El registro no se liberó." }
            }
        }
        val pending = async(Dispatchers.Default) { source(backend).awaitFix() }
        assertTrue(entered.await(2L, TimeUnit.SECONDS))
        val cancelling = Thread {
            cancellationStarted.countDown()
            pending.cancel()
        }
        cancelling.start()
        try {
            assertTrue(cancellationStarted.await(2L, TimeUnit.SECONDS))
            withTimeout(2_000L) {
                while (!pending.isCancelled) delay(1L)
            }
        } finally {
            release.countDown()
        }
        pending.join()
        cancelling.join(2_000L)

        assertFalse(cancelling.isAlive)
        assertTrue(pending.isCancelled)
        assertTrue(backend.active.isEmpty())
        assertEquals(1, backend.registrations)
        assertEquals(1, backend.removals)
    }

    @Test
    fun unTiempoDeEsperaAgotadoCancelaLaSuscripcion() = runBlocking {
        val backend = FakeGpsBackend()

        try {
            withTimeout(200L) { source(backend).awaitFix() }
            fail("No se recibió ninguna posición.")
        } catch (_: CancellationException) {
            assertEquals(1, backend.registrations)
            assertEquals(1, backend.removals)
            assertTrue(backend.active.isEmpty())
        }
    }

    @Test
    fun apagarGpsDuranteLaEsperaTerminaSinLecturaYRetiraElListener() = runBlocking {
        val backend = FakeGpsBackend()
        val pending = async(start = CoroutineStart.UNDISPATCHED) { source(backend).awaitFix() }

        backend.lastListener().onProviderDisabled(LocationManager.NETWORK_PROVIDER)
        assertFalse(pending.isCompleted)
        backend.lastListener().onProviderDisabled(LocationManager.GPS_PROVIDER)

        assertNull(pending.await())
        assertEquals(1, backend.removals)
        assertTrue(backend.active.isEmpty())
    }

    @Test
    fun apagarGpsSincronicamenteMientrasRegistraTambienLimpiaAlSalir() = runBlocking {
        val backend = FakeGpsBackend().apply {
            onRegister = { it.onProviderDisabled(LocationManager.GPS_PROVIDER) }
        }

        assertNull(source(backend).awaitFix())
        assertEquals(1, backend.removals)
        assertTrue(backend.active.isEmpty())
    }

    @Test
    fun unPermisoRevocadoTrasElRegistroParcialSePropagaYRetiraElListener() = runBlocking {
        val failure = SecurityException("Permiso revocado durante el registro.")
        val backend = FakeGpsBackend().apply { onRegister = { throw failure } }

        try {
            source(backend).awaitFix()
            fail("Debe comunicar la revocación al controlador.")
        } catch (received: SecurityException) {
            assertSame(failure, received)
            assertEquals(1, backend.removals)
            assertTrue(backend.active.isEmpty())
        }
    }

    @Test
    fun siElProveedorDesapareceAlRegistrarDevuelveAusenciaYRetiraElRegistroParcial() = runBlocking {
        val backend = FakeGpsBackend().apply {
            onRegister = { throw IllegalArgumentException("El proveedor dejó de existir.") }
        }

        assertNull(source(backend).awaitFix())
        assertEquals(1, backend.removals)
        assertTrue(backend.active.isEmpty())
    }

    @Test
    fun unFalloInesperadoDeRegistroNoDejaLaSuscripcionActiva() = runBlocking {
        val failure = IllegalStateException("No se pudo registrar.")
        val backend = FakeGpsBackend().apply { onRegister = { throw failure } }

        try {
            source(backend).awaitFix()
            fail("Debe propagar el fallo de la fuente.")
        } catch (received: IllegalStateException) {
            assertSame(failure, received)
            assertEquals(1, backend.removals)
            assertTrue(backend.active.isEmpty())
        }
    }

    @Test
    fun unaRevocacionDuranteLaLimpiezaNoOcultaElFalloOriginal() = runBlocking {
        val failure = SecurityException("El registro perdió el permiso.")
        val backend = FakeGpsBackend().apply {
            onRegister = { throw failure }
            onRemove = { throw SecurityException("El permiso ya no existe al retirar.") }
        }

        try {
            source(backend).awaitFix()
            fail("Debe mantener el motivo original.")
        } catch (received: SecurityException) {
            assertSame(failure, received)
            assertEquals(1, backend.removals)
            assertTrue(backend.active.isEmpty())
        }
    }

    @Test
    fun falloTransitorioAlRetirarReintentaAntesDeEntregarLaLectura() = runBlocking {
        val backend = FakeGpsBackend().apply {
            beforeRemove = {
                if (removals == 1) throw RuntimeException("El servicio no respondió al retirar.")
            }
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { source(backend).awaitFix() }

        backend.lastListener().onLocationChanged(location())

        assertEquals(location().toDeviceLocationFix(), pending.await())
        assertEquals(2, backend.removals)
        assertTrue(backend.active.isEmpty())
    }

    @Test
    fun falloPersistenteAlRetirarNoEscapaDelCallbackNiEntregaUnaLectura() = runBlocking {
        supervisorScope {
            val failure = RuntimeException("El servicio sigue sin retirar el listener.")
            val backend = FakeGpsBackend().apply { beforeRemove = { throw failure } }
            // El supervisor permite observar el fallo del hijo y mantiene su ciclo de vida ligado a la prueba.
            val pending = async(start = CoroutineStart.UNDISPATCHED) { source(backend).awaitFix() }
            val listener = backend.lastListener()

            listener.onLocationChanged(location())
            try {
                pending.await()
                fail("Una limpieza fallida no debe conceder una ubicación válida.")
            } catch (received: RuntimeException) {
                assertSame(failure, received)
                assertEquals(2, backend.removals)
            }
            // El proveedor rechazó ambas retiradas: solo podemos garantizar que este
            // request ya terminó y descarta callbacks, no que el sistema lo retiró.
            listener.onLocationChanged(location().apply { latitude = 48.8584 })
            listener.onProviderDisabled(LocationManager.GPS_PROVIDER)
            assertEquals(2, backend.removals)
        }
    }

    @Test
    fun cancelarConLimpiezaFallidaConservaCancelacionSinExcepcionDelHandler() = runBlocking {
        val backend = FakeGpsBackend().apply {
            beforeRemove = { throw RuntimeException("No respondió al cancelar.") }
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { source(backend).awaitFix() }
        val listener = backend.lastListener()

        pending.cancelAndJoin()
        listener.onLocationChanged(location())

        assertTrue(pending.isCancelled)
        assertEquals(2, backend.removals)
    }

    @Test
    fun unFalloDeLimpiezaNoOcultaUnFalloOriginalDelRegistro() = runBlocking {
        val original = IllegalStateException("Falló el registro de GPS.")
        val backend = FakeGpsBackend().apply {
            onRegister = { throw original }
            beforeRemove = { throw RuntimeException("Falló también retirar.") }
        }

        try {
            source(backend).awaitFix()
            fail("Debe conservar el fallo original.")
        } catch (received: IllegalStateException) {
            assertSame(original, received)
            assertEquals(2, backend.removals)
        }
    }

    @Test
    fun revocarPermisoAlRetirarUnaLecturaNoLaEntregaComoValida() = runBlocking {
        supervisorScope {
            val failure = SecurityException("Se revocó el permiso al retirar.")
            val backend = FakeGpsBackend().apply { onRemove = { throw failure } }
            val pending = async(start = CoroutineStart.UNDISPATCHED) { source(backend).awaitFix() }

            backend.lastListener().onLocationChanged(location())
            try {
                pending.await()
                fail("Debe informar que se perdió el permiso.")
            } catch (received: SecurityException) {
                assertSame(failure, received)
                assertEquals(1, backend.removals)
                assertTrue(backend.active.isEmpty())
            }
        }
    }

    private fun source(backend: FakeGpsBackend) = AndroidPhoneLocationSource(backend) { true }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun location() = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = 19.4326077
        longitude = -99.1332088
        accuracy = 12.5f
        elapsedRealtimeNanos = 1_234_567_890_123L
    }

    private class FakeGpsBackend : GpsLocationBackend {
        var enabled = true
        var enabledChecks = 0
        var registrations = 0
        var removals = 0
        var onRegister: ((LocationListener) -> Unit)? = null
        var onRemove: (() -> Unit)? = null
        var beforeRemove: (() -> Unit)? = null
        val active = mutableSetOf<LocationListener>()
        private var latestListener: LocationListener? = null

        override fun isEnabled(): Boolean {
            enabledChecks++
            return enabled
        }

        override fun requestUpdates(listener: LocationListener) {
            registrations++
            latestListener = listener
            active.add(listener)
            onRegister?.invoke(listener)
        }

        override fun removeUpdates(listener: LocationListener) {
            removals++
            beforeRemove?.invoke()
            active.remove(listener)
            onRemove?.invoke()
        }

        fun lastListener(): LocationListener = requireNotNull(latestListener)
    }
}
