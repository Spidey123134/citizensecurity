package com.example.citizensecurity.data

import android.location.Location
import android.os.Build
import com.example.citizensecurity.domain.DeviceLocationFix
import java.util.concurrent.atomic.AtomicReference

/** Copia únicamente evidencia de Location; no captura GPS, pide permisos ni conserva archivos. */
fun Location.toDeviceLocationFix(): DeviceLocationFix {
    val snapshot = Location(this)
    @Suppress("DEPRECATION")
    val mock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        snapshot.isMock
    } else {
        snapshot.isFromMockProvider
    }
    return DeviceLocationFix(
        latitude = snapshot.latitude,
        longitude = snapshot.longitude,
        accuracyMeters = if (snapshot.hasAccuracy()) snapshot.accuracy.toDouble() else Double.NaN,
        elapsedRealtimeNanos = snapshot.elapsedRealtimeNanos,
        isMock = mock,
    )
}

/**
 * Solo vive en memoria y se alimenta desde la captura, nunca desde el pin.
 * Permiso preciso y GPS habilitado son necesarios tanto para publicar como para consultar.
 * Observar una pérdida o fallo descarta la lectura; recuperar disponibilidad no la restaura.
 */
class PhoneLocationEvidence(
    private val hasPreciseLocationPermission: () -> Boolean,
    private val isLocationEnabled: () -> Boolean,
) {
    private val latest = AtomicReference<DeviceLocationFix?>(null)

    fun update(location: Location) {
        updateFix(location.toDeviceLocationFix())
    }

    /** Solo la fuente del teléfono publica una lectura; el controlador valida antes su calidad. */
    internal fun updateFix(fix: DeviceLocationFix): Boolean {
        if (!isAvailable()) {
            clear()
            return false
        }
        latest.set(fix)
        return currentFix() != null
    }

    fun currentFix(): DeviceLocationFix? {
        if (!isAvailable()) {
            clear()
            return null
        }
        return latest.get()
    }

    fun clear() {
        latest.set(null)
    }

    private fun isAvailable(): Boolean = try {
        // No consultar el proveedor sin permiso preciso.
        hasPreciseLocationPermission() && isLocationEnabled()
    } catch (_: Exception) {
        // Un fallo de permiso o proveedor no acredita disponibilidad. No dejar escapar
        // errores del servicio hacia el mapa o guardado ni conservar evidencia anterior.
        false
    }
}
