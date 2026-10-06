package com.example.citizensecurity.data

import android.location.Location
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Usa metadatos nativos de Location; no solicita permisos ni obtiene posiciones reales. */
@RunWith(AndroidJUnit4::class)
class PhoneLocationEvidenceTest {

    @Test
    fun modificarLaLecturaOriginalNoCambiaLaEvidenciaYaRecibida() {
        val evidence = PhoneLocationEvidence({ true }, { true })
        val original = phoneLocation()
        evidence.update(original)
        val received = requireNotNull(evidence.currentFix())

        original.latitude = 48.8584
        original.longitude = 2.2945
        original.accuracy = 800.0f
        original.elapsedRealtimeNanos = FIX_TIME_NANOS + 1_000_000_000L
        original.time = Long.MAX_VALUE
        original.removeAccuracy()

        assertEquals(LATITUDE, received.latitude, 0.0)
        assertEquals(LONGITUDE, received.longitude, 0.0)
        assertEquals(12.5, received.accuracyMeters, 0.0)
        assertEquals(FIX_TIME_NANOS, received.elapsedRealtimeNanos)
        assertEquals(received, evidence.currentFix())
        assertFalse(original.hasAccuracy())
    }

    @Test
    fun laAusenciaDePrecisionNativaNoSeConfundeConUnaPrecisionCeroDeclarada() {
        val location = phoneLocation()
        location.removeAccuracy()
        assertFalse(location.hasAccuracy())
        assertTrue(location.toDeviceLocationFix().accuracyMeters.isNaN())

        location.accuracy = 0.0f
        assertTrue(location.hasAccuracy())
        assertEquals(0.0, location.toDeviceLocationFix().accuracyMeters, 0.0)
        // Cero explícito es válido; la ausencia se mantiene separada y será rechazada.
    }

    @Test
    fun conservaLosNanosegundosMonotonicosAunqueCambieLaHoraDeCalendario() {
        val location = phoneLocation()
        location.elapsedRealtimeNanos = FIX_TIME_NANOS
        location.time = 0L
        val before = location.toDeviceLocationFix()

        location.time = Long.MAX_VALUE
        val after = location.toDeviceLocationFix()

        assertEquals(FIX_TIME_NANOS, before.elapsedRealtimeNanos)
        assertEquals(before, after)
    }

    @Test
    fun sinPermisoNoAceptaUnaLecturaNiLaRecuperaAlConcederlo() {
        var permitted = false
        val evidence = PhoneLocationEvidence({ permitted }, { true })

        evidence.update(phoneLocation())
        assertNull(evidence.currentFix())
        permitted = true
        assertNull(evidence.currentFix())

        val fresh = phoneLocation().apply { elapsedRealtimeNanos = FIX_TIME_NANOS + 1L }
        evidence.update(fresh)
        assertEquals(fresh.toDeviceLocationFix(), evidence.currentFix())
        evidence.clear()
        assertNull(evidence.currentFix())
    }

    @Test
    fun revocarElPermisoAlConsultarDescartaLaLecturaAnterior() {
        var permitted = true
        val evidence = PhoneLocationEvidence({ permitted }, { true })
        evidence.update(phoneLocation())
        assertTrue(evidence.currentFix() != null)

        permitted = false
        assertNull(evidence.currentFix())
        permitted = true
        assertNull(evidence.currentFix())

        evidence.update(phoneLocation())
        assertTrue(evidence.currentFix() != null)
    }

    @Test
    fun unaActualizacionSinPermisoDescartaTambienLaLecturaQueYaExistia() {
        var permitted = true
        val evidence = PhoneLocationEvidence({ permitted }, { true })
        evidence.update(phoneLocation())
        permitted = false

        evidence.update(phoneLocation().apply { latitude = 48.8584 })
        permitted = true
        assertNull(evidence.currentFix())
    }

    @Test
    fun unaExcepcionDePermisoAlConsultarImpideUsarLaLecturaGuardada() {
        var permissionCheckFails = false
        val evidence = PhoneLocationEvidence(
            {
                if (permissionCheckFails) throw SecurityException("Permiso revocado durante la consulta.")
                true
            },
            { true },
        )
        evidence.update(phoneLocation())

        permissionCheckFails = true
        assertNull(evidence.currentFix())
        permissionCheckFails = false
        assertNull(evidence.currentFix())

        evidence.update(phoneLocation())
        assertTrue(evidence.currentFix() != null)
    }

    @Test
    fun unaExcepcionDePermisoAlActualizarDescartaLaLecturaAnterior() {
        var permissionCheckFails = false
        val evidence = PhoneLocationEvidence(
            {
                if (permissionCheckFails) throw SecurityException("Permiso revocado durante la actualización.")
                true
            },
            { true },
        )
        evidence.update(phoneLocation())

        permissionCheckFails = true
        evidence.update(phoneLocation().apply { longitude = 2.2945 })
        permissionCheckFails = false
        assertNull(evidence.currentFix())
    }

    @Test
    fun unFalloDelServicioAlConsultarPermisoNoEscapaNiConservaLaLectura() {
        var failed = false
        val evidence = PhoneLocationEvidence(
            {
                if (failed) throw RuntimeException("No respondió el servicio de permisos.")
                true
            },
            { true },
        )
        evidence.update(phoneLocation())
        assertTrue(evidence.currentFix() != null)

        failed = true
        assertNull(evidence.currentFix())
        failed = false
        assertNull(evidence.currentFix())
    }

    @Test
    fun unFalloDelServicioAlActualizarPermisoDescartaLaLecturaAnterior() {
        var failed = false
        val evidence = PhoneLocationEvidence(
            {
                if (failed) throw RuntimeException("No respondió el servicio de permisos.")
                true
            },
            { true },
        )
        evidence.update(phoneLocation())
        failed = true

        evidence.update(phoneLocation().apply { latitude = 48.8584 })
        failed = false
        assertNull(evidence.currentFix())
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    fun laMarcaDeSimulacionNativaSeConservaAunqueCambieElObjetoOriginal() {
        val evidence = PhoneLocationEvidence({ true }, { true })
        val location = phoneLocation().apply { isMock = true }
        assertTrue(location.isMock)
        evidence.update(location)

        location.isMock = false
        assertFalse(location.isMock)
        assertTrue(requireNotNull(evidence.currentFix()).isMock)
        evidence.update(location)
        assertFalse(requireNotNull(evidence.currentFix()).isMock)
    }

    private fun phoneLocation() = Location("gps").apply {
        latitude = LATITUDE
        longitude = LONGITUDE
        accuracy = 12.5f
        elapsedRealtimeNanos = FIX_TIME_NANOS
        time = 1_791_014_400_000L
    }

    private companion object {
        const val LATITUDE = 19.4326077
        const val LONGITUDE = -99.1332088
        const val FIX_TIME_NANOS = 1_234_567_890_123L
    }
}
