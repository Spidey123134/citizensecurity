package com.example.citizensecurity.maps

import android.app.ActivityManager
import android.content.Context
import org.maplibre.android.MapLibre

/** MapLibre y estilo público de OpenFreeMap; no usa clave, cuenta ni Google Play Services. */
object MapsConfiguration {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    const val UNSUPPORTED_GRAPHICS_MESSAGE = "El mapa requiere OpenGL ES 3.0, que no está disponible en este dispositivo. Puedes volver sin perder los datos o consultar tus reportes en la lista."

    class UnsupportedGraphicsException : IllegalStateException(UNSUPPORTED_GRAPHICS_MESSAGE)

    /** La instalación directa de una APK no comprueba el requisito gráfico del manifest. */
    fun requireSupportedGraphics(context: Context) {
        val version = context.applicationContext.getSystemService(ActivityManager::class.java)
            ?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        if (version < 0x00030000) throw UnsupportedGraphicsException()
    }

    /** Invocar antes de crear MapView, en el hilo principal; no inicia GPS ni carga una vista. */
    fun initialize(context: Context) {
        requireSupportedGraphics(context)
        MapLibre.getInstance(context.applicationContext)
    }
}
