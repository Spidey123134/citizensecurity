package com.example.citizensecurity.maps

import android.content.Context
import android.content.pm.PackageManager

/** Comprueba presencia local; no verifica autorización, facturación ni conectividad de Google. */
object MapsConfiguration {
    private const val API_KEY_METADATA = "com.google.android.geo.API_KEY"

    @Suppress("DEPRECATION")
    fun isConfigured(context: Context): Boolean {
        val application = context.packageManager.getApplicationInfo(
            context.packageName, PackageManager.GET_META_DATA,
        )
        return !application.metaData?.getString(API_KEY_METADATA).isNullOrBlank()
    }
}
