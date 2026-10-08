package com.example.citizensecurity.maps

import androidx.annotation.MainThread
import org.maplibre.android.maps.MapLibreMap

/**
 * Cada host administra solo su registro de MapLibre; otros listeners del mapa se conservan.
 * Retirar o sustituir invalida primero el callback, incluso si el SDK falla al retirarlo.
 * La identidad del registro bloquea entregas tardías y una instalación retirada por reentrada.
 */
@MainThread
internal class MapLibreMapClickHost internal constructor(
    private val add: (MapLibreMap.OnMapClickListener) -> Unit,
    private val remove: (MapLibreMap.OnMapClickListener) -> Unit,
) : IncidentMapClickHost {
    constructor(map: MapLibreMap) : this(map::addOnMapClickListener, map::removeOnMapClickListener)

    private var current: Registration? = null

    override fun setListener(listener: ((Double, Double) -> Boolean)?) {
        val previous = current
        val replacement = listener?.let { Registration(it) }
        current = replacement
        previous?.receive = null
        try {
            previous?.let { remove(it.native) }
        } catch (failure: Exception) {
            if (current === replacement) current = null
            replacement?.receive = null
            throw failure
        }
        // Retirar el registro anterior puede cerrar o reemplazar este intento.
        if (replacement == null || current !== replacement) return
        try {
            add(replacement.native)
        } catch (failure: Exception) {
            if (current === replacement) current = null
            replacement.receive = null
            try { remove(replacement.native) }
            catch (removeFailure: Exception) { failure.addSuppressed(removeFailure) }
            throw failure
        }
    }

    private inner class Registration(var receive: ((Double, Double) -> Boolean)?) {
        val native = MapLibreMap.OnMapClickListener { point ->
            current === this && receive?.invoke(point.latitude, point.longitude) == true
        }
    }
}
