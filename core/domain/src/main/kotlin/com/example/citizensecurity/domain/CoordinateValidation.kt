package com.example.citizensecurity.domain

/** Ambos valores ausentes son válidos para un reporte con referencia escrita. */
internal fun coordinateError(latitude: Double?, longitude: Double?): String? = when {
    (latitude != null) != (longitude != null) -> {
        "Agrega tanto la latitud como la longitud."
    }
    latitude != null && longitude != null &&
        (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0) -> {
        "Las coordenadas deben ser finitas: latitud entre -90 y 90, " +
            "y longitud entre -180 y 180."
    }
    else -> null
}
