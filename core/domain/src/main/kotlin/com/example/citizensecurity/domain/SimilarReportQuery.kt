package com.example.citizensecurity.domain

import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.UUID
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Búsqueda orientativa local: mismo tipo, hora del incidente dentro de ±[timeWindow] y punto
 * a [radiusMeters] o menos. No determina que dos reportes sean el mismo hecho ni bloquea guardar.
 * No obtiene GPS, modifica datos o compara texto/fotografías. El radio no sustituye al guard de 5 km.
 */
data class SimilarReportQuery(
    val type: IncidentType,
    val occurredAt: Instant,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double = DEFAULT_RADIUS_METERS,
    val timeWindow: Duration = DEFAULT_TIME_WINDOW,
    val excludedId: String? = null,
) {
    init {
        require(coordinateError(latitude, longitude) == null) { "La búsqueda requiere coordenadas válidas." }
        require(radiusMeters.isFinite() && radiusMeters > 0.0 && radiusMeters <= MAX_RADIUS_METERS) {
            "El radio de búsqueda debe ser positivo y no superar $MAX_RADIUS_METERS metros."
        }
        require(!timeWindow.isNegative && !timeWindow.isZero && timeWindow <= MAX_TIME_WINDOW) {
            "La ventana de búsqueda debe ser positiva y no superar 24 horas por lado."
        }
        require(excludedId == null || runCatching { UUID.fromString(excludedId).toString() == excludedId }.getOrDefault(false)) {
            "El folio excluido debe ser un UUID completo y canónico."
        }
    }

    /** Extremos inclusivos; una fecha extrema se limita a Instant.MIN/MAX sin desbordar. */
    val earliest: Instant = if (occurredAt < Instant.MIN.plus(timeWindow)) Instant.MIN else occurredAt.minus(timeWindow)
    val latest: Instant = if (occurredAt > Instant.MAX.minus(timeWindow)) Instant.MAX else occurredAt.plus(timeWindow)

    /** Caja conservadora para acotar SQLite; la distancia esférica decide después la coincidencia. */
    val region: MapRegion = run {
        val angularRadius = radiusMeters / EARTH_RADIUS_METERS
        val latitudeRadians = Math.toRadians(latitude)
        val latitudeDelta = Math.toDegrees(angularRadius)
        val south = (latitude - latitudeDelta - BOUNDING_EPSILON_DEGREES).coerceAtLeast(-90.0)
        val north = (latitude + latitudeDelta + BOUNDING_EPSILON_DEGREES).coerceAtMost(90.0)
        if (south <= -90.0 || north >= 90.0) {
            MapRegion(south, north, -180.0, 180.0)
        } else {
            val longitudeDelta = Math.toDegrees(asin((sin(angularRadius) / cos(latitudeRadians)).coerceIn(-1.0, 1.0))) +
                BOUNDING_EPSILON_DEGREES
            MapRegion(south, north, normalizeLongitude(longitude - longitudeDelta), normalizeLongitude(longitude + longitudeDelta))
        }
    }

    /** Radio terrestre medio IUGG, como NearbyIncidentPolicy; aproximación esférica en metros. */
    fun distanceTo(candidateLatitude: Double?, candidateLongitude: Double?): Double? {
        if (candidateLatitude == null || candidateLongitude == null || coordinateError(candidateLatitude, candidateLongitude) != null) {
            return null
        }
        val latitudeSin = sin(Math.toRadians(candidateLatitude - latitude) / 2.0)
        val longitudeSin = sin(Math.toRadians(candidateLongitude - longitude) / 2.0)
        val haversine = (latitudeSin * latitudeSin + cos(Math.toRadians(latitude)) *
            cos(Math.toRadians(candidateLatitude)) * longitudeSin * longitudeSin).coerceIn(0.0, 1.0)
        return EARTH_RADIUS_METERS * 2.0 * atan2(sqrt(haversine), sqrt(1.0 - haversine))
    }

    /** Un ULP absorbe el redondeo aritmético justo en el borde, sin ampliar el radio en metros. */
    fun includesDistance(distanceMeters: Double): Boolean = distanceMeters.isFinite() &&
        distanceMeters >= 0.0 && distanceMeters <= Math.nextUp(radiusMeters)

    companion object {
        const val DEFAULT_RADIUS_METERS = 200.0
        val DEFAULT_TIME_WINDOW: Duration = Duration.ofHours(2)
        const val MAX_RADIUS_METERS = 5_000.0
        val MAX_TIME_WINDOW: Duration = Duration.ofHours(24)
        const val MAX_CANDIDATES = 200
        const val MAX_MATCHES = 5
        private const val EARTH_RADIUS_METERS = 6_371_008.8
        private const val BOUNDING_EPSILON_DEGREES = 1e-9

        fun fromDraft(draft: NewReport, excludedId: String? = null): SimilarReportQuery? {
            val latitude = draft.location.latitude ?: return null
            val longitude = draft.location.longitude ?: return null
            if (coordinateError(latitude, longitude) != null) return null
            return SimilarReportQuery(draft.type, draft.occurredAt, latitude, longitude, excludedId = excludedId)
        }

        private fun normalizeLongitude(value: Double): Double = ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    }
}

data class SimilarReportMatch(val report: Report, val distanceMeters: Double) {
    init {
        require(distanceMeters.isFinite() && distanceMeters >= 0.0) { "La distancia de coincidencia debe ser válida." }
    }
}

/**
 * Hasta cinco coincidencias. searchIncomplete avisa que hubo más coincidencias o se alcanzó el
 * límite de 200 candidatos; incluso una página vacía no demuestra que no exista otro incidente.
 */
class SimilarReportPage(matches: List<SimilarReportMatch>, val searchIncomplete: Boolean) {
    val matches: List<SimilarReportMatch> = Collections.unmodifiableList(matches.toList())

    init { require(matches.size <= SimilarReportQuery.MAX_MATCHES) { "La búsqueda admite hasta cinco coincidencias." } }

    override fun equals(other: Any?): Boolean = other is SimilarReportPage && matches == other.matches && searchIncomplete == other.searchIncomplete
    override fun hashCode(): Int = 31 * matches.hashCode() + searchIncomplete.hashCode()
    override fun toString(): String = "SimilarReportPage(matches=$matches, searchIncomplete=$searchIncomplete)"
}

/** Lectura local limitada, sin guardar reportes ni iniciar ubicación. */
fun interface SimilarReportRepository {
    suspend fun findSimilar(query: SimilarReportQuery): SimilarReportPage
}
