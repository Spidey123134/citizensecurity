package com.example.citizensecurity.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lectura del dispositivo, separada del punto elegido para el incidente.
 * El adaptador Android debe aportar estos datos desde Location y consultar el mismo reloj
 * monotónico para evaluar la lectura. No deben obtenerse del formulario ni del marcador.
 * [accuracyMeters] es una estimación de precisión, no una garantía de la posición real.
 */
data class DeviceLocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val elapsedRealtimeNanos: Long,
    val isMock: Boolean,
)

enum class NearbyIncidentRejection {
    INCIDENT_COORDINATES_REQUIRED,
    INVALID_INCIDENT_COORDINATES,
    DEVICE_LOCATION_REQUIRED,
    INVALID_DEVICE_COORDINATES,
    INVALID_ACCURACY,
    INACCURATE_DEVICE_LOCATION,
    MOCK_DEVICE_LOCATION,
    INVALID_MONOTONIC_TIME,
    FUTURE_DEVICE_LOCATION,
    STALE_DEVICE_LOCATION,
    OUTSIDE_ALLOWED_RADIUS,
}

sealed interface NearbyIncidentDecision {
    data class Allowed(
        val distanceMeters: Double,
        val upperEstimateDistanceMeters: Double,
    ) : NearbyIncidentDecision

    data class Rejected(
        val reason: NearbyIncidentRejection,
        val message: String,
        val distanceMeters: Double? = null,
    ) : NearbyIncidentDecision
}

/**
 * Comprueba cercanía a una lectura reciente del teléfono, sin acceder al GPS ni guardar reportes.
 * Se permite el punto únicamente cuando distancia + precisión <= [maxDistanceMeters]: una lectura
 * imprecisa no amplía el radio elegido. No demuestra que el incidente ocurrió ni impide toda
 * falsificación del dispositivo; isMock solo representa lo que detectó el adaptador.
 *
 * Haversine usa el radio terrestre medio IUGG de 6 371 008,8 m; es una aproximación esférica,
 * no una distancia geodésica exacta sobre el elipsoide. La precisión del GPS también es estimada.
 */
class NearbyIncidentPolicy(
    val maxDistanceMeters: Double = 5_000.0,
    val maxAccuracyMeters: Double = 100.0,
    val maxFixAgeNanos: Long = 120_000_000_000L,
) {
    init {
        require(maxDistanceMeters.isFinite() && maxDistanceMeters > 0.0) {
            "El radio máximo debe ser positivo y finito."
        }
        require(maxAccuracyMeters.isFinite() && maxAccuracyMeters > 0.0) {
            "La precisión máxima debe ser positiva y finita."
        }
        require(maxFixAgeNanos > 0L) {
            "La antigüedad máxima de la ubicación debe ser positiva."
        }
    }

    fun evaluate(
        incident: ReportLocation,
        fix: DeviceLocationFix?,
        nowElapsedRealtimeNanos: Long,
    ): NearbyIncidentDecision {
        val latitude = incident.latitude
        val longitude = incident.longitude
        if (latitude == null || longitude == null) {
            return reject(
                NearbyIncidentRejection.INCIDENT_COORDINATES_REQUIRED,
                "Selecciona un punto completo para el incidente.",
            )
        }
        coordinateError(latitude, longitude)?.let { error ->
            return reject(NearbyIncidentRejection.INVALID_INCIDENT_COORDINATES, error)
        }
        checkDeviceLocation(fix, nowElapsedRealtimeNanos)?.let { return it }
        val validFix = requireNotNull(fix)
        val distance = haversineMeters(validFix.latitude, validFix.longitude, latitude, longitude)
        val upperEstimate = distance + validFix.accuracyMeters
        if (upperEstimate > maxDistanceMeters) {
            return NearbyIncidentDecision.Rejected(
                NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS,
                "El punto queda fuera del área permitida alrededor de tu ubicación.",
                distance,
            )
        }
        return NearbyIncidentDecision.Allowed(distance, upperEstimate)
    }

    /** Comprueba solo la evidencia del teléfono, sin exigir que ya se haya elegido un incidente. */
    fun checkDeviceLocation(
        fix: DeviceLocationFix?,
        nowElapsedRealtimeNanos: Long,
    ): NearbyIncidentDecision.Rejected? {
        if (fix == null) {
            return reject(
                NearbyIncidentRejection.DEVICE_LOCATION_REQUIRED,
                "Obtén la ubicación actual del teléfono antes de confirmar el punto.",
            )
        }
        if (fix.isMock) {
            return reject(
                NearbyIncidentRejection.MOCK_DEVICE_LOCATION,
                "La ubicación del teléfono está marcada como simulada. Usa una lectura real.",
            )
        }
        coordinateError(fix.latitude, fix.longitude)?.let {
            return reject(
                NearbyIncidentRejection.INVALID_DEVICE_COORDINATES,
                "La lectura de ubicación del teléfono contiene coordenadas inválidas.",
            )
        }
        if (!fix.accuracyMeters.isFinite() || fix.accuracyMeters < 0.0) {
            return reject(
                NearbyIncidentRejection.INVALID_ACCURACY,
                "La lectura del teléfono debe incluir una precisión válida.",
            )
        }
        if (fix.accuracyMeters > maxAccuracyMeters) {
            return reject(
                NearbyIncidentRejection.INACCURATE_DEVICE_LOCATION,
                "La ubicación del teléfono es poco precisa. Obtén una lectura más precisa.",
            )
        }
        if (nowElapsedRealtimeNanos < 0L || fix.elapsedRealtimeNanos < 0L) {
            return reject(
                NearbyIncidentRejection.INVALID_MONOTONIC_TIME,
                "No se pudo comprobar la antigüedad de la ubicación del teléfono.",
            )
        }
        if (fix.elapsedRealtimeNanos > nowElapsedRealtimeNanos) {
            return reject(
                NearbyIncidentRejection.FUTURE_DEVICE_LOCATION,
                "La hora de la lectura de ubicación no es válida. Obtén otra lectura.",
            )
        }
        // Al ser tiempos no negativos y fix <= now, la resta no puede desbordar Long.
        if (nowElapsedRealtimeNanos - fix.elapsedRealtimeNanos > maxFixAgeNanos) {
            return reject(
                NearbyIncidentRejection.STALE_DEVICE_LOCATION,
                "La ubicación del teléfono es antigua. Actualízala antes de confirmar el punto.",
            )
        }

        return null
    }

    private fun reject(reason: NearbyIncidentRejection, message: String) =
        NearbyIncidentDecision.Rejected(reason, message)

    private fun haversineMeters(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Double {
        val fromLatitudeRadians = Math.toRadians(fromLatitude)
        val toLatitudeRadians = Math.toRadians(toLatitude)
        val halfLatitudeDifference = Math.toRadians(toLatitude - fromLatitude) / 2.0
        val halfLongitudeDifference = Math.toRadians(toLongitude - fromLongitude) / 2.0
        val latitudeSin = sin(halfLatitudeDifference)
        val longitudeSin = sin(halfLongitudeDifference)
        val haversine = (
            latitudeSin * latitudeSin +
                cos(fromLatitudeRadians) * cos(toLatitudeRadians) * longitudeSin * longitudeSin
            ).coerceIn(0.0, 1.0)
        val centralAngle = 2.0 * atan2(sqrt(haversine), sqrt(1.0 - haversine))
        return 6_371_008.8 * centralAngle
    }
}
