package com.example.citizensecurity.verification.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.example.citizensecurity.data.PhoneLocationSource
import com.example.citizensecurity.domain.DeviceLocationFix
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException

/** Fuente SOLO del laboratorio: permiso Android real y lectura GPS controlada por instrumentación. */
class ControllablePhoneLocationSource(context: Context) : PhoneLocationSource {
    private val applicationContext = context.applicationContext

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var availabilityFailure: Exception? = null

    @Volatile
    var nextReading: suspend () -> DeviceLocationFix? = { null }

    val requests = AtomicInteger()
    val completions = AtomicInteger()
    val cancellations = AtomicInteger()

    override fun hasPrecisePermission(): Boolean =
        applicationContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun isLocationEnabled(): Boolean {
        availabilityFailure?.let { throw it }
        return enabled
    }

    override suspend fun awaitFix(): DeviceLocationFix? {
        requests.incrementAndGet()
        val reading = nextReading
        try {
            return reading()
        } catch (cancelled: CancellationException) {
            cancellations.incrementAndGet()
            throw cancelled
        } finally {
            completions.incrementAndGet()
        }
    }
}
