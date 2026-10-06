package com.example.citizensecurity.domain

sealed interface LocationProposal {
    data object None : LocationProposal

    data class ValidPoint(val latitude: Double, val longitude: Double) : LocationProposal

    data class InvalidCoordinates(val message: String) : LocationProposal
}

sealed interface LocationConfirmation {
    data object NoSelection : LocationConfirmation

    data class Confirmed(val location: ReportLocation) : LocationConfirmation

    data class InvalidCoordinates(val message: String) : LocationConfirmation
}

sealed interface NearbyLocationConfirmation {
    data object NoSelection : NearbyLocationConfirmation
    data class InvalidCoordinates(val message: String) : NearbyLocationConfirmation
    data class Confirmed(val location: ReportLocation, val distanceMeters: Double) : NearbyLocationConfirmation
    data class Blocked(val rejection: NearbyIncidentDecision.Rejected) : NearbyLocationConfirmation
}

/**
 * Selección independiente del mapa: proponer un punto no modifica el borrador del reporte.
 * Cada propuesta devuelve otra sesión y sustituye la anterior, incluso si el punto es inválido.
 * La pantalla aplica [LocationConfirmation.Confirmed.location] solo al confirmar; al cancelar
 * conserva [originalLocation] y descarta la sesión. Este contrato no guarda ni consulta reportes.
 */
class IncidentLocationSelection private constructor(
    val originalLocation: ReportLocation,
    val proposal: LocationProposal,
) {
    constructor(originalLocation: ReportLocation) : this(originalLocation, LocationProposal.None)

    fun propose(latitude: Double, longitude: Double): IncidentLocationSelection {
        val error = coordinateError(latitude, longitude)
        val nextProposal = if (error == null) {
            LocationProposal.ValidPoint(latitude, longitude)
        } else {
            LocationProposal.InvalidCoordinates(error)
        }
        return IncidentLocationSelection(originalLocation, nextProposal)
    }

    /** Confirmación geométrica para ejemplos; no acredita cercanía. Reportes usan confirmNearby. */
    fun confirm(): LocationConfirmation = when (val point = proposal) {
        LocationProposal.None -> LocationConfirmation.NoSelection
        is LocationProposal.InvalidCoordinates -> LocationConfirmation.InvalidCoordinates(point.message)
        is LocationProposal.ValidPoint -> LocationConfirmation.Confirmed(
            originalLocation.copy(latitude = point.latitude, longitude = point.longitude),
        )
    }

    /** Comprueba el punto propuesto con evidencia del teléfono, distinta del pin seleccionado. */
    fun confirmNearby(
        fix: DeviceLocationFix?,
        nowElapsedRealtimeNanos: Long,
        policy: NearbyIncidentPolicy = NearbyIncidentPolicy(),
    ): NearbyLocationConfirmation = when (val geometric = confirm()) {
        LocationConfirmation.NoSelection -> NearbyLocationConfirmation.NoSelection
        is LocationConfirmation.InvalidCoordinates -> NearbyLocationConfirmation.InvalidCoordinates(geometric.message)
        is LocationConfirmation.Confirmed -> when (
            val decision = policy.evaluate(geometric.location, fix, nowElapsedRealtimeNanos)
        ) {
            is NearbyIncidentDecision.Allowed -> NearbyLocationConfirmation.Confirmed(
                geometric.location, decision.distanceMeters,
            )
            is NearbyIncidentDecision.Rejected -> NearbyLocationConfirmation.Blocked(decision)
        }
    }

    /** La ubicación original se conserva exactamente, incluso si tenía coordenadas incompletas. */
    fun cancel(): ReportLocation = originalLocation
}
