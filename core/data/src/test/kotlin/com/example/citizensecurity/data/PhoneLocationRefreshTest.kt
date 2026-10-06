package com.example.citizensecurity.data

import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.NearbyIncidentRejection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhoneLocationRefreshTest {
    @Test
    fun construirNoSolicitaUbicacionYRenovarPublicaSoloLaLecturaRecibida() = runTest {
        val f = Fixture()
        assertEquals(0, f.source.requests)
        assertNull(f.evidence.currentFix())
        assertEquals(PhoneLocationRefreshResult.Ready(FIX), f.refresh.refresh())
        assertEquals(FIX, f.evidence.currentFix())
        assertEquals(1, f.source.requests)
    }

    @Test
    fun permisoDenegadoNoInvocaLaFuenteNiConservaLaEvidenciaAnterior() = runTest {
        val f = Fixture()
        f.evidence.updateFix(FIX)
        f.source.permission = false
        assertEquals(PhoneLocationRefreshResult.PermissionRequired, f.refresh.refresh())
        assertEquals(0, f.source.requests)
        f.source.permission = true
        assertNull(f.evidence.currentFix())
    }

    @Test
    fun gpsApagadoNoSolicitaNiConservaLaLecturaAnterior() = runTest {
        val f = Fixture()
        f.evidence.updateFix(FIX)
        f.source.enabled = false
        assertEquals(PhoneLocationRefreshResult.LocationDisabled, f.refresh.refresh())
        assertEquals(0, f.source.requests)
        assertNull(f.evidence.currentFix())
    }

    @Test
    fun ausenciaDeLecturaNoReutilizaUnaUbicacionAnterior() = runTest {
        val f = Fixture()
        f.evidence.updateFix(FIX)
        f.source.next = { null }
        assertEquals(PhoneLocationRefreshResult.Unavailable, f.refresh.refresh())
        assertNull(f.evidence.currentFix())
    }

    @Test
    fun limiteDeEsperaCancelaLaFuenteYPermiteIntentarDeNuevo() = runTest {
        val f = Fixture()
        f.source.next = { awaitCancellation() }
        var result: PhoneLocationRefreshResult? = null
        val job = launch { result = f.refresh.refresh() }
        runCurrent()
        advanceTimeBy(14_999)
        runCurrent()
        assertTrue(job.isActive)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(PhoneLocationRefreshResult.TimedOut, result)
        assertEquals(1, f.source.closed)
        assertNull(f.evidence.currentFix())
        f.source.next = { FIX }
        assertEquals(PhoneLocationRefreshResult.Ready(FIX), f.refresh.refresh())
    }

    @Test
    fun cancelarLaPantallaPropagaCancelacionYLiberaLaSolicitud() = runTest {
        val f = Fixture()
        f.evidence.updateFix(FIX)
        f.source.next = { awaitCancellation() }
        var returned = false
        val job = launch { f.refresh.refresh(); returned = true }
        runCurrent()
        job.cancel()
        job.join()
        assertFalse(returned)
        assertEquals(1, f.source.closed)
        assertNull(f.evidence.currentFix())
        f.source.next = { FIX }
        assertEquals(PhoneLocationRefreshResult.Ready(FIX), f.refresh.refresh())
    }

    @Test
    fun caducarDurantePublicacionNoDejaEvidenciaTrasDevolverTimeout() = runTest {
        var duringPublish = false
        val source = FakeSource()
        val evidence = PhoneLocationEvidence(
            {
                if (duringPublish) {
                    duringPublish = false
                    advanceTimeBy(15_000)
                    runCurrent()
                }
                true
            },
            source::isLocationEnabled,
        )
        source.next = { duringPublish = true; FIX }
        val refresh = PhoneLocationRefresh(source, evidence, { NOW })
        assertEquals(PhoneLocationRefreshResult.TimedOut, refresh.refresh())
        assertNull(evidence.currentFix())
        assertEquals(1, source.closed)
    }

    @Test
    fun dosSolicitudesSimultaneasCompartenUnSoloIntentoSinCancelarElPrimero() = runTest {
        val f = Fixture()
        val reading = CompletableDeferred<DeviceLocationFix>()
        f.source.next = { reading.await() }
        var first: PhoneLocationRefreshResult? = null
        val job = launch { first = f.refresh.refresh() }
        runCurrent()
        assertEquals(PhoneLocationRefreshResult.AlreadyRunning, f.refresh.refresh())
        assertEquals(1, f.source.requests)
        assertTrue(job.isActive)
        reading.complete(FIX)
        job.join()
        assertEquals(PhoneLocationRefreshResult.Ready(FIX), first)
        assertEquals(FIX, f.evidence.currentFix())
    }

    @Test
    fun revocarPermisoDuranteLaCapturaDescartaLaLectura() = runTest {
        val f = Fixture()
        f.source.next = { f.source.permission = false; FIX }
        assertEquals(PhoneLocationRefreshResult.PermissionRequired, f.refresh.refresh())
        f.source.permission = true
        assertNull(f.evidence.currentFix())
    }

    @Test
    fun apagarGpsDuranteLaCapturaDescartaLaLectura() = runTest {
        val f = Fixture()
        f.source.next = { f.source.enabled = false; FIX }
        assertEquals(PhoneLocationRefreshResult.LocationDisabled, f.refresh.refresh())
        assertNull(f.evidence.currentFix())
    }

    @Test
    fun lectoresDiscrepantesAlPublicarNoInventanUnaDenegacionActualNiDevuelvenReady() = runTest {
        val f = Fixture(storePermission = { false })
        assertEquals(PhoneLocationRefreshResult.Unavailable, f.refresh.refresh())
        assertNull(f.evidence.currentFix())
    }

    @Test
    fun perdidaTransitoriaDeGpsDurantePublicacionExigeUnNuevoIntentoExplicito() = runTest {
        val source = FakeSource()
        var evidenceProviderReads = 0
        val evidence = PhoneLocationEvidence(
            source::hasPrecisePermission,
            {
                if (++evidenceProviderReads == 2) {
                    source.enabled = false
                    val observedEnabled = source.isLocationEnabled()
                    source.enabled = true
                    observedEnabled
                } else {
                    source.isLocationEnabled()
                }
            },
        )
        val refresh = PhoneLocationRefresh(source, evidence, { NOW })

        assertEquals(PhoneLocationRefreshResult.Unavailable, refresh.refresh())
        assertTrue(source.enabled)
        assertNull(evidence.currentFix())
        assertEquals(1, source.requests)

        assertEquals(PhoneLocationRefreshResult.Ready(FIX), refresh.refresh())
        assertEquals(FIX, evidence.currentFix())
        assertEquals(2, source.requests)
    }

    @Test
    fun securityExceptionDuranteRegistroSeTraduceEnPermisoRequerido() = runTest {
        val f = Fixture()
        f.source.next = { throw SecurityException("revocado") }
        assertEquals(PhoneLocationRefreshResult.PermissionRequired, f.refresh.refresh())
        assertNull(f.evidence.currentFix())
        f.source.next = { FIX }
        assertEquals(PhoneLocationRefreshResult.Ready(FIX), f.refresh.refresh())
    }

    @Test
    fun proveedorQueDesapareceNoDejaUnaLecturaAnteriorNiBloqueaReintento() = runTest {
        val f = Fixture()
        f.evidence.updateFix(FIX)
        f.source.next = { throw IllegalArgumentException("proveedor ausente") }
        assertEquals(PhoneLocationRefreshResult.Unavailable, f.refresh.refresh())
        assertNull(f.evidence.currentFix())
        f.source.next = { FIX }
        assertEquals(PhoneLocationRefreshResult.Ready(FIX), f.refresh.refresh())
    }

    @Test
    fun falloInesperadoDelProveedorNoEscapaNiBloqueaUnIntentoPosterior() = runTest {
        val f = Fixture()
        f.evidence.updateFix(FIX)
        f.source.next = { throw RuntimeException("El servicio de ubicación falló.") }

        assertEquals(PhoneLocationRefreshResult.Unavailable, f.refresh.refresh())
        assertNull(f.evidence.currentFix())
        assertEquals(1, f.source.closed)
        f.source.next = { FIX }
        assertEquals(PhoneLocationRefreshResult.Ready(FIX), f.refresh.refresh())
    }

    @Test
    fun falloInesperadoAlConsultarPermisoDescartaLaEvidenciaSinCapturar() = runTest {
        val f = Fixture()
        f.evidence.updateFix(FIX)
        f.source.permissionError = RuntimeException("No respondió el servicio de permisos.")

        assertEquals(PhoneLocationRefreshResult.Unavailable, f.refresh.refresh())
        assertNull(f.evidence.currentFix())
        assertEquals(0, f.source.requests)
    }

    @Test
    fun falloInesperadoAlConsultarGpsDescartaLaEvidenciaSinCapturar() = runTest {
        val f = Fixture()
        f.evidence.updateFix(FIX)
        f.source.enabledError = RuntimeException("No respondió el proveedor.")

        assertEquals(PhoneLocationRefreshResult.Unavailable, f.refresh.refresh())
        assertNull(f.evidence.currentFix())
        assertEquals(0, f.source.requests)
    }

    @Test
    fun apagarGpsMientrasPublicaNoDevuelveUnaUbicacionPreparada() = runTest {
        lateinit var f: Fixture
        f = Fixture(storePermission = { f.source.enabled = false; true })

        assertEquals(PhoneLocationRefreshResult.LocationDisabled, f.refresh.refresh())
        assertNull(f.evidence.currentFix())
    }

    @Test
    fun revocarPermisoDeLaFuenteMientrasPublicaDescartaLaLectura() = runTest {
        lateinit var f: Fixture
        f = Fixture(storePermission = { f.source.permission = false; true })

        assertEquals(PhoneLocationRefreshResult.PermissionRequired, f.refresh.refresh())
        assertNull(f.evidence.currentFix())
    }

    @Test
    fun laLecturaQueCaducaDurantePublicacionNoSeEntregaComoPreparada() = runTest {
        var now = NOW
        val source = FakeSource()
        val evidence = PhoneLocationEvidence(
            { now = NOW + 120_000_000_000L; true }, source::isLocationEnabled,
        )
        val refresh = PhoneLocationRefresh(source, evidence, { now })

        val result = refresh.refresh() as PhoneLocationRefreshResult.Rejected
        assertEquals(NearbyIncidentRejection.STALE_DEVICE_LOCATION, result.rejection.reason)
        assertNull(evidence.currentFix())
    }

    @Test
    fun falloDelRelojTrasPublicarDescartaLaEvidenciaYLiberaLaSolicitud() = runTest {
        var clockFailed = false
        val source = FakeSource()
        val evidence = PhoneLocationEvidence({ clockFailed = true; true }, source::isLocationEnabled)
        val refresh = PhoneLocationRefresh(source, evidence, {
            if (clockFailed) throw RuntimeException("No se pudo consultar el reloj.")
            NOW
        })

        assertEquals(PhoneLocationRefreshResult.Unavailable, refresh.refresh())
        assertNull(evidence.currentFix())
        source.enabled = false
        assertEquals(PhoneLocationRefreshResult.LocationDisabled, refresh.refresh())
    }

    @Test
    fun lecturaSimuladaSeRechazaSinPublicar() = rejected(
        FIX.copy(isMock = true), NearbyIncidentRejection.MOCK_DEVICE_LOCATION,
    )

    @Test
    fun lecturaAntiguaSeRechazaSinPublicar() = rejected(
        FIX.copy(elapsedRealtimeNanos = NOW - 120_000_000_001L),
        NearbyIncidentRejection.STALE_DEVICE_LOCATION,
    )

    @Test
    fun lecturaImprecisaSeRechazaSinPublicar() = rejected(
        FIX.copy(accuracyMeters = 101.0), NearbyIncidentRejection.INACCURATE_DEVICE_LOCATION,
    )

    @Test
    fun lecturaSinPrecisionSeRechazaSinPublicar() = rejected(
        FIX.copy(accuracyMeters = Double.NaN), NearbyIncidentRejection.INVALID_ACCURACY,
    )

    @Test
    fun lecturaFuturaSeRechazaSinPublicar() = rejected(
        FIX.copy(elapsedRealtimeNanos = NOW + 1), NearbyIncidentRejection.FUTURE_DEVICE_LOCATION,
    )

    @Test
    fun coordenadasInvalidasDelTelefonoNoSeConfundenConUnPinInvalido() = rejected(
        FIX.copy(latitude = Double.NaN), NearbyIncidentRejection.INVALID_DEVICE_COORDINATES,
    )

    @Test(expected = IllegalArgumentException::class)
    fun tiempoDeEsperaCeroNoEsUnaConfiguracionValida() {
        val source = FakeSource()
        PhoneLocationRefresh(
            source,
            PhoneLocationEvidence(source::hasPrecisePermission, source::isLocationEnabled),
            { NOW },
            timeoutMillis = 0L,
        )
    }

    private fun rejected(fix: DeviceLocationFix, reason: NearbyIncidentRejection) = runTest {
        val f = Fixture()
        f.evidence.updateFix(FIX)
        f.source.next = { fix }
        val result = f.refresh.refresh() as PhoneLocationRefreshResult.Rejected
        assertEquals(reason, result.rejection.reason)
        assertNull(f.evidence.currentFix())
    }

    private class Fixture(storePermission: (() -> Boolean)? = null) {
        val source = FakeSource()
        val evidence = PhoneLocationEvidence(
            storePermission ?: source::hasPrecisePermission,
            source::isLocationEnabled,
        )
        val refresh = PhoneLocationRefresh(source, evidence, { NOW })
    }

    private class FakeSource : PhoneLocationSource {
        var permission = true
        var enabled = true
        var requests = 0
        var closed = 0
        var permissionError: Exception? = null
        var enabledError: Exception? = null
        var next: suspend () -> DeviceLocationFix? = { FIX }
        override fun hasPrecisePermission(): Boolean {
            permissionError?.let { throw it }
            return permission
        }
        override fun isLocationEnabled(): Boolean {
            enabledError?.let { throw it }
            return enabled
        }
        override suspend fun awaitFix(): DeviceLocationFix? {
            requests++
            try { return next() } finally { closed++ }
        }
    }

    private companion object {
        const val NOW = 200_000_000_000L
        val FIX = DeviceLocationFix(19.4326, -99.1332, 12.0, NOW - 1_000_000_000L, false)
    }
}
