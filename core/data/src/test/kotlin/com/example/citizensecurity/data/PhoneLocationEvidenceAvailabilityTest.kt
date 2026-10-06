package com.example.citizensecurity.data

import com.example.citizensecurity.domain.DeviceLocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Comprueba disponibilidad mediante datos de dominio, sin usar Location o un proveedor Android. */
class PhoneLocationEvidenceAvailabilityTest {
    @Test
    fun apagarGpsAlConsultarDescartaLaLecturaYReactivarloNoLaRecupera() {
        val f = Fixture()
        f.publishInitial()

        f.enabled = false
        assertNull(f.evidence.currentFix())
        f.enabled = true
        f.verifyExplicitPublicationRequired()
    }

    @Test
    fun publicarConGpsApagadoDescartaTambienLaLecturaAnterior() {
        val f = Fixture()
        f.publishInitial()

        f.enabled = false
        assertFalse(f.evidence.updateFix(NEXT_FIX))
        f.enabled = true
        f.verifyExplicitPublicationRequired()
    }

    @Test
    fun falloOrdinarioDeGpsAlConsultarNoEscapaNiResucitaLaLectura() {
        val f = Fixture()
        f.publishInitial()

        f.providerError = IllegalStateException("El proveedor no respondió.")
        assertNull(f.evidence.currentFix())
        f.providerError = null
        f.verifyExplicitPublicationRequired()
    }

    @Test
    fun falloDeSeguridadDeGpsAlConsultarDescartaLaLecturaHastaUnaNuevaPublicacion() {
        val f = Fixture()
        f.publishInitial()

        f.providerError = SecurityException("No se pudo consultar el proveedor.")
        assertNull(f.evidence.currentFix())
        f.providerError = null
        f.verifyExplicitPublicationRequired()
    }

    @Test
    fun falloOrdinarioDeGpsAlPublicarNoConservaLaLecturaAnteriorNiLaNueva() {
        val f = Fixture()
        f.publishInitial()

        f.providerError = IllegalStateException("El proveedor no respondió.")
        assertFalse(f.evidence.updateFix(NEXT_FIX))
        f.providerError = null
        f.verifyExplicitPublicationRequired()
    }

    @Test
    fun falloDeSeguridadDeGpsAlPublicarNoConservaLaLecturaAnteriorNiLaNueva() {
        val f = Fixture()
        f.publishInitial()

        f.providerError = SecurityException("No se pudo consultar el proveedor.")
        assertFalse(f.evidence.updateFix(NEXT_FIX))
        f.providerError = null
        f.verifyExplicitPublicationRequired()
    }

    @Test
    fun permisoAusenteAlConsultarDescartaEvidenciaSinConsultarGps() {
        val f = Fixture()
        f.publishInitial()
        f.providerReads = 0

        f.permitted = false
        assertNull(f.evidence.currentFix())
        assertEquals(0, f.providerReads)
        f.permitted = true
        f.verifyExplicitPublicationRequired()
    }

    @Test
    fun permisoAusenteAlPublicarDescartaEvidenciaSinConsultarGps() {
        val f = Fixture()
        f.publishInitial()
        f.providerReads = 0

        f.permitted = false
        assertFalse(f.evidence.updateFix(NEXT_FIX))
        assertEquals(0, f.providerReads)
        f.permitted = true
        f.verifyExplicitPublicationRequired()
    }

    @Test
    fun falloDePermisoNoConsultaGpsNiDejaRecuperarLaLecturaGuardada() {
        val f = Fixture()
        f.publishInitial()
        f.providerReads = 0

        f.permissionError = SecurityException("El permiso cambió durante la consulta.")
        assertNull(f.evidence.currentFix())
        assertEquals(0, f.providerReads)
        f.permissionError = null
        f.verifyExplicitPublicationRequired()
    }

    @Test
    fun apagarGpsEntreLasComprobacionesDePublicacionImpideAceptarLaLectura() {
        var providerReads = 0
        val evidence = PhoneLocationEvidence(
            { true },
            { ++providerReads != 2 },
        )

        assertFalse(evidence.updateFix(FIX))
        assertNull(evidence.currentFix())
        assertTrue(evidence.updateFix(NEXT_FIX))
        assertEquals(NEXT_FIX, evidence.currentFix())
    }

    private class Fixture {
        var permitted = true
        var enabled = true
        var permissionError: Exception? = null
        var providerError: Exception? = null
        var providerReads = 0
        val evidence = PhoneLocationEvidence(
            {
                permissionError?.let { throw it }
                permitted
            },
            {
                providerReads++
                providerError?.let { throw it }
                enabled
            },
        )

        fun publishInitial() {
            assertTrue(evidence.updateFix(FIX))
            assertEquals(FIX, evidence.currentFix())
        }

        fun verifyExplicitPublicationRequired() {
            assertNull(evidence.currentFix())
            assertTrue(evidence.updateFix(NEXT_FIX))
            assertEquals(NEXT_FIX, evidence.currentFix())
        }
    }

    private companion object {
        val FIX = DeviceLocationFix(19.4326077, -99.1332088, 20.0, 300_000_000_000L, false)
        val NEXT_FIX = FIX.copy(latitude = 19.4327, elapsedRealtimeNanos = FIX.elapsedRealtimeNanos + 1L)
    }
}
