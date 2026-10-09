package com.example.citizensecurity.maps

import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.ReportLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MapLibreIncidentBindingTest {
    private val elapsed = 300_000_000_000L
    private val original = ReportLocation("Frente al parque", 19.431, -99.131)
    private val fix = DeviceLocationFix(19.4326077, -99.1332088, 20.0, elapsed, false)

    @Test
    fun proponerNoLeeGpsYUnaConfirmacionBloqueadaConservaElListener() {
        var evidenceReads = 0
        var evidence: DeviceLocationFix? = null
        val session = IncidentMapSession(original, { evidenceReads++; evidence }, { elapsed })
        val host = Host()
        val proposals = mutableListOf<LocationProposal>()
        val binding = MapLibreIncidentBinding(host, session) { proposals += it }
        val listener = host.currentListener

        assertTrue(host.click(fix.latitude, fix.longitude))
        assertEquals(0, evidenceReads)
        assertEquals(1, proposals.size)
        assertTrue(binding.confirm() is NearbyLocationConfirmation.Blocked)
        assertSame(listener, host.currentListener)
        assertFalse(session.isClosed)
        evidence = fix
        val confirmed = binding.confirm() as NearbyLocationConfirmation.Confirmed
        assertEquals(fix.latitude, confirmed.location.latitude!!, 0.0)
        assertEquals(original.reference, confirmed.location.reference)
        assertTrue(session.isClosed)
        assertNull(host.currentListener)
        assertEquals(1, host.removals)
        assertFalse(host.deliverLate(48.8566, 2.3522))
        assertSame(NearbyLocationConfirmation.NoSelection, binding.confirm())
        assertSame(confirmed.location, binding.cancel())
        assertEquals(2, evidenceReads)
        assertEquals(1, proposals.size)
        assertEquals(1, host.removals)
    }

    @Test
    fun cancelarConservaOriginalYNoEntregaCallbacksRetirados() {
        val session = session()
        val host = Host()
        var proposals = 0
        val binding = MapLibreIncidentBinding(host, session) { proposals++ }
        assertTrue(host.click(fix.latitude, fix.longitude))
        assertSame(original, binding.cancel())
        assertNull(host.currentListener)
        assertFalse(host.deliverLate(48.8566, 2.3522))
        binding.close()
        assertEquals(1, proposals)
        assertEquals(1, host.removals)
        assertTrue(session.isClosed)
    }

    @Test
    fun sesionCanceladaExternamenteRetiraListenerSinConsumirElClick() {
        val session = session()
        val host = Host()
        var proposals = 0
        MapLibreIncidentBinding(host, session) { proposals++ }
        session.cancel()

        assertFalse(host.click(fix.latitude, fix.longitude))
        assertNull(host.currentListener)
        assertEquals(1, host.removals)
        assertEquals(0, proposals)
        assertFalse(host.deliverLate(fix.latitude, fix.longitude))
    }

    @Test
    fun sesionTerminadaAntesDeCrearPuenteNoRegistraListener() {
        val session = session()
        session.cancel()
        val host = Host()
        val binding = MapLibreIncidentBinding(host, session) { fail("La sesión está terminada.") }

        assertEquals(0, host.registrations)
        assertFalse(host.click(fix.latitude, fix.longitude))
        binding.close()
        assertEquals(0, host.removals)
    }

    @Test
    fun instalacionFallidaCancelaSesionYBloqueaCallbackParcial() {
        val session = session()
        val host = Host().apply { failInstall = true }
        var proposals = 0
        expectFailure { MapLibreIncidentBinding(host, session) { proposals++ } }

        assertTrue(session.isClosed)
        assertNull(host.currentListener)
        assertFalse(host.deliverLate(fix.latitude, fix.longitude))
        assertEquals(0, proposals)
        assertEquals(1, host.removals)
    }

    @Test
    fun retiradaFallidaCancelaSesionYDejaCallbackRegistradoInerte() {
        val session = session()
        val host = Host()
        var proposals = 0
        val binding = MapLibreIncidentBinding(host, session) { proposals++ }
        host.failRemoval = true
        expectFailure { binding.close() }

        assertTrue(session.isClosed)
        assertFalse(host.click(fix.latitude, fix.longitude))
        assertFalse(host.deliverLate(fix.latitude, fix.longitude))
        assertEquals(0, proposals)
        binding.close()
        assertEquals(1, host.removals)
    }

    private fun session() = IncidentMapSession(original, { fix }, { elapsed })

    private fun expectFailure(action: () -> Unit) {
        try { action(); fail("El adaptador debe comunicar el fallo del SDK.") }
        catch (_: IllegalStateException) { /* Fallo esperado. */ }
    }

    private class Host : IncidentMapClickHost {
        var registrations = 0
        var removals = 0
        var failInstall = false
        var failRemoval = false
        var currentListener: ((Double, Double) -> Boolean)? = null
        private var previous: ((Double, Double) -> Boolean)? = null

        override fun setListener(listener: ((Double, Double) -> Boolean)?) {
            if (listener == null) {
                removals++
                if (failRemoval) error("La retirada falla.")
            } else {
                registrations++
                previous = listener
            }
            currentListener = listener
            if (listener != null && failInstall) error("La instalación falla.")
        }

        fun click(latitude: Double, longitude: Double): Boolean = currentListener?.invoke(latitude, longitude) == true
        fun deliverLate(latitude: Double, longitude: Double): Boolean = previous?.invoke(latitude, longitude) == true
    }
}
