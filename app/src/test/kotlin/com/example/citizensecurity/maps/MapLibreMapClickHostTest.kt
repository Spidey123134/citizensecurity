package com.example.citizensecurity.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/** Comprueba registros reales del adaptador; no necesita ni acredita un mapa renderizado. */
class MapLibreMapClickHostTest {
    private val point = LatLng(19.4326081234567, -99.1332077654321)

    @Test
    fun dosHostsConservanListenersAjenosYRetiranSoloSuPropioRegistro() {
        val registry = Registry()
        var foreignClicks = 0
        val foreign = MapLibreMap.OnMapClickListener { foreignClicks++; false }
        registry.add(foreign)
        val a = host(registry)
        val b = host(registry)
        var bClicks = 0
        a.setListener { _, _ -> true }
        val oldA = registry.listeners.last()
        b.setListener { latitude, longitude ->
            assertEquals(point.latitude.toRawBits(), latitude.toRawBits())
            assertEquals(point.longitude.toRawBits(), longitude.toRawBits())
            bClicks++
            true
        }
        val currentB = registry.listeners.last()

        a.setListener(null)
        assertEquals(listOf(foreign, currentB), registry.listeners)
        assertFalse(oldA.onMapClick(point))
        assertTrue(currentB.onMapClick(point))
        assertFalse(foreign.onMapClick(point))
        assertEquals(1, bClicks)
        assertEquals(1, foreignClicks)
        a.setListener(null)
        assertEquals(1, registry.removals.size)
        b.setListener(null)
        assertEquals(listOf(foreign), registry.listeners)
    }

    @Test
    fun reemplazarInvalidaElCallbackAnteriorYPropagaConsumoDelVigente() {
        val registry = Registry()
        val host = host(registry)
        var oldClicks = 0
        host.setListener { _, _ -> oldClicks++; true }
        val old = registry.listeners.single()
        var currentClicks = 0
        var mayConsume = false
        host.setListener { _, _ -> currentClicks++; mayConsume }
        val current = registry.listeners.single()

        assertFalse(old.onMapClick(point))
        assertEquals(0, oldClicks)
        assertFalse(current.onMapClick(point))
        mayConsume = true
        assertTrue(current.onMapClick(point))
        assertEquals(2, currentClicks)
        host.setListener(null)
        assertFalse(current.onMapClick(point))
        assertEquals(2, currentClicks)
    }

    @Test
    fun retiradaFallidaDejaCallbackInerteYNoImpideUnaConexionNueva() {
        val registry = Registry()
        val host = host(registry)
        var oldClicks = 0
        host.setListener { _, _ -> oldClicks++; true }
        val old = registry.listeners.single()
        registry.onRemove = { error("No se retiró el registro.") }
        expectFailure { host.setListener(null) }
        assertTrue(registry.listeners.contains(old))
        assertFalse(old.onMapClick(point))
        assertEquals(0, oldClicks)

        registry.onRemove = null
        host.setListener { _, _ -> true }
        val current = registry.listeners.last()
        assertTrue(current.onMapClick(point))
        host.setListener(null)
        assertEquals(listOf(old), registry.listeners)
        assertFalse(old.onMapClick(point))
    }

    @Test
    fun instalacionParcialFallidaRetiraSoloSuRegistroEInvalidaEntregasTardias() {
        val registry = Registry()
        val foreign = MapLibreMap.OnMapClickListener { true }
        registry.add(foreign)
        val host = host(registry)
        var partial: MapLibreMap.OnMapClickListener? = null
        var clicks = 0
        registry.onAdd = { partial = it; error("Registro parcial.") }
        expectFailure { host.setListener { _, _ -> clicks++; true } }

        assertEquals(listOf(foreign), registry.listeners)
        assertSame(partial, registry.removals.single())
        assertFalse(partial!!.onMapClick(point))
        assertEquals(0, clicks)
        assertTrue(foreign.onMapClick(point))
    }

    @Test
    fun falloDeLimpiezaConservaErrorOriginalYCallbackParcialInerte() {
        val registry = Registry()
        val host = host(registry)
        val installFailure = IllegalStateException("Fallo de instalación.")
        val removeFailure = IllegalArgumentException("Fallo de retirada.")
        registry.onAdd = { throw installFailure }
        registry.onRemove = { throw removeFailure }

        val failure = expectFailure { host.setListener { _, _ -> true } }
        assertSame(installFailure, failure)
        assertEquals(listOf(removeFailure), failure.suppressed.toList())
        assertFalse(registry.listeners.single().onMapClick(point))
    }

    @Test
    fun reemplazoDuranteRetiradaNoInstalaElIntentoQuePerdioSuIdentidad() {
        val registry = Registry()
        val host = host(registry)
        host.setListener { _, _ -> true }
        val old = registry.listeners.single()
        var retiredAttemptClicks = 0
        var currentClicks = 0
        registry.onRemove = {
            if (it === old) {
                registry.onRemove = null
                host.setListener { _, _ -> currentClicks++; true }
            }
        }

        host.setListener { _, _ -> retiredAttemptClicks++; true }
        assertEquals(2, registry.additions.size)
        assertFalse(old.onMapClick(point))
        assertTrue(registry.listeners.single().onMapClick(point))
        assertEquals(0, retiredAttemptClicks)
        assertEquals(1, currentClicks)
    }

    @Test
    fun falloDeInstalacionAnteriorNoRetiraReemplazoCreadoPorReentrada() {
        val registry = Registry()
        val foreign = MapLibreMap.OnMapClickListener { false }
        registry.add(foreign)
        val host = host(registry)
        var old: MapLibreMap.OnMapClickListener? = null
        var currentClicks = 0
        registry.onAdd = {
            old = it
            registry.onAdd = null
            host.setListener { _, _ -> currentClicks++; true }
            error("La instalación anterior falla después del reemplazo.")
        }

        expectFailure { host.setListener { _, _ -> true } }
        val current = registry.listeners.last()
        assertEquals(listOf(foreign, current), registry.listeners)
        assertFalse(old!!.onMapClick(point))
        assertTrue(current.onMapClick(point))
        assertEquals(1, currentClicks)
        host.setListener(null)
        assertEquals(listOf(foreign), registry.listeners)
    }

    private fun host(registry: Registry) = MapLibreMapClickHost(registry::add, registry::remove)

    private fun expectFailure(action: () -> Unit): Exception {
        try {
            action()
            fail("El adaptador debe comunicar el fallo del SDK.")
        } catch (failure: Exception) {
            return failure
        }
        error("No se produjo el fallo esperado.")
    }

    private class Registry {
        val listeners = mutableListOf<MapLibreMap.OnMapClickListener>()
        val additions = mutableListOf<MapLibreMap.OnMapClickListener>()
        val removals = mutableListOf<MapLibreMap.OnMapClickListener>()
        var onAdd: ((MapLibreMap.OnMapClickListener) -> Unit)? = null
        var onRemove: ((MapLibreMap.OnMapClickListener) -> Unit)? = null

        fun add(listener: MapLibreMap.OnMapClickListener) {
            additions += listener
            listeners += listener
            onAdd?.invoke(listener)
        }

        fun remove(listener: MapLibreMap.OnMapClickListener) {
            removals += listener
            onRemove?.invoke(listener)
            listeners.remove(listener)
        }
    }
}
