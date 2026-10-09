package com.example.citizensecurity.maps

import android.content.Context
import org.maplibre.android.MapLibre

/** MapLibre y estilo público de OpenFreeMap; no usa clave, cuenta ni Google Play Services. */
object MapsConfiguration {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    /** Invocar antes de crear MapView, en el hilo principal; no inicia GPS ni carga una vista. */
    fun initialize(context: Context) {
        MapLibre.getInstance(context.applicationContext)
    }
}
