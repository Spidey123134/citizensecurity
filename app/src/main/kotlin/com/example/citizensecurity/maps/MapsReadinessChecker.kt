package com.example.citizensecurity.maps

import com.google.android.gms.common.ConnectionResult
import kotlinx.coroutines.CancellationException

sealed interface MapsReadiness {
    data object Ready : MapsReadiness
    data object MissingApiKey : MapsReadiness
    data class ServicesUnavailable(val errorCode: Int) : MapsReadiness
    data object VerificationFailed : MapsReadiness
}

/** Antes de crear la vista: presencia de clave y servicios locales; no valida Google Cloud. */
class MapsReadinessChecker(
    private val hasConfiguredKey: () -> Boolean,
    private val googleServicesStatus: () -> Int,
) {
    fun check(): MapsReadiness = try {
        if (!hasConfiguredKey()) MapsReadiness.MissingApiKey
        else {
            val status = googleServicesStatus()
            if (status == ConnectionResult.SUCCESS) MapsReadiness.Ready
            else MapsReadiness.ServicesUnavailable(status)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        MapsReadiness.VerificationFailed
    }
}
