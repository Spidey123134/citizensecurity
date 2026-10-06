package com.example.citizensecurity.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.example.citizensecurity.domain.DeviceLocationFix
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/** Una lectura solicitada explícitamente; no conserva Activity, posición anterior ni servicio. */
class AndroidPhoneLocationSource internal constructor(
    private val backend: GpsLocationBackend,
    private val precisePermission: () -> Boolean,
) : PhoneLocationSource {

    constructor(context: Context) : this(
        AndroidGpsLocationBackend(context.applicationContext.getSystemService(LocationManager::class.java)),
        permissionCheck(context.applicationContext),
    )

    override fun hasPrecisePermission(): Boolean = precisePermission()

    override fun isLocationEnabled(): Boolean = backend.isEnabled()

    override suspend fun awaitFix(): DeviceLocationFix? {
        if (!hasPrecisePermission()) {
            throw SecurityException("Se necesita permiso de ubicación precisa para obtener la posición.")
        }
        if (!isLocationEnabled()) return null
        return suspendCancellableCoroutine { continuation ->
            OneFixRequest(backend, continuation).start()
        }
    }

    private companion object {
        fun permissionCheck(applicationContext: Context): () -> Boolean = {
            applicationContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}

/** Punto interno para comprobar carreras sin cambiar permisos ni proveedores del dispositivo. */
internal interface GpsLocationBackend {
    fun isEnabled(): Boolean
    fun requestUpdates(listener: LocationListener)
    fun removeUpdates(listener: LocationListener)
}

private class AndroidGpsLocationBackend(private val manager: LocationManager?) : GpsLocationBackend {
    override fun isEnabled(): Boolean = try {
        manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
    } catch (_: IllegalArgumentException) {
        false
    }

    // awaitFix comprueba FINE; el controlador recibe SecurityException si se revoca al registrar.
    @SuppressLint("MissingPermission")
    override fun requestUpdates(listener: LocationListener) {
        val availableManager = manager ?: throw IllegalArgumentException("El proveedor GPS no está disponible.")
        availableManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            0L,
            0.0f,
            listener,
            Looper.getMainLooper(),
        )
    }

    // La limpieza también se intenta si la revocación coincidió con el registro.
    @SuppressLint("MissingPermission")
    override fun removeUpdates(listener: LocationListener) {
        manager?.removeUpdates(listener)
    }
}

private class OneFixRequest(
    private val backend: GpsLocationBackend,
    private val continuation: CancellableContinuation<DeviceLocationFix?>,
) {
    private val monitor = Any()
    private var registrationAttempted = false
    private var registering = false
    private var finished = false
    private var completion: Result<DeviceLocationFix?>? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val fix = try {
                location.toDeviceLocationFix()
            } catch (error: Exception) {
                finish(Result.failure(error))
                return
            }
            finish(Result.success(fix))
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) finish(Result.success(null))
        }

        override fun onProviderEnabled(provider: String) = Unit

        // Estos tres callbacks no tienen implementación por defecto antes de API 30.
        @Deprecated("Compatibilidad con LocationListener en Android 26–29.")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    fun start() {
        continuation.invokeOnCancellation { finish(null) }
        synchronized(monitor) {
            if (finished || !continuation.isActive) return
            registrationAttempted = true
            registering = true
            try {
                backend.requestUpdates(listener)
            } catch (_: IllegalArgumentException) {
                // El proveedor puede desaparecer entre la comprobación y el registro.
                finish(Result.success(null))
            } catch (error: Exception) {
                finish(Result.failure(error))
            } finally {
                registering = false
                if (finished) releaseAndComplete()
            }
        }
    }

    private fun finish(result: Result<DeviceLocationFix?>?) {
        synchronized(monitor) {
            if (finished) return
            finished = true
            completion = result
            // Un callback síncrono o una cancelación durante register esperan a que termine.
            if (!registering) releaseAndComplete()
        }
    }

    private fun releaseAndComplete() {
        var cleanupFailure: Exception? = null
        if (registrationAttempted) {
            registrationAttempted = false
            try {
                backend.removeUpdates(listener)
            } catch (error: SecurityException) {
                cleanupFailure = error
            } catch (_: Exception) {
                // Retirar es idempotente. Un fallo transitorio del servicio tiene un
                // segundo intento, sin mantener esperas ni reintentos en segundo plano.
                try {
                    backend.removeUpdates(listener)
                } catch (error: Exception) {
                    cleanupFailure = error
                }
            }
        }
        val result = completion
        completion = null
        // La limpieza no lanza desde callbacks/cancelación. Conserva el fallo original
        // y no entrega una lectura utilizable si no pudo retirar la suscripción.
        if (result != null) {
            val completionResult = if (cleanupFailure != null && result.isSuccess) {
                Result.failure(cleanupFailure)
            } else {
                result
            }
            continuation.resumeWith(completionResult)
        }
    }
}
