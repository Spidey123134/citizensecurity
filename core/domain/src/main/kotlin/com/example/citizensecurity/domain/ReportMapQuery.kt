package com.example.citizensecurity.domain

import java.util.Collections

/** Ventana geográfica inclusiva; west > east cruza el meridiano ±180°. */
data class MapRegion(
    val south: Double,
    val north: Double,
    val west: Double,
    val east: Double,
) {
    init {
        require(south.isFinite() && north.isFinite() && south in -90.0..90.0 && north in -90.0..90.0) {
            "Los límites de latitud deben estar entre -90 y 90 grados."
        }
        require(south <= north) { "El límite sur no puede superar al norte." }
        require(west.isFinite() && east.isFinite() && west in -180.0..180.0 && east in -180.0..180.0) {
            "Los límites de longitud deben estar entre -180 y 180 grados."
        }
    }

    val crossesAntimeridian: Boolean get() = west > east

    /** Los extremos +180 y -180 representan el mismo meridiano. */
    val includesAntimeridian: Boolean get() = includesLongitude(-180.0) || includesLongitude(180.0)

    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in south..north &&
            longitude in -180.0..180.0 &&
            (includesLongitude(longitude) || (kotlin.math.abs(longitude) == 180.0 && includesAntimeridian))

    private fun includesLongitude(longitude: Double): Boolean = if (crossesAntimeridian) {
        longitude >= west || longitude <= east
    } else {
        longitude in west..east
    }
}

/** Filtros vacíos aceptan cualquier categoría/estado. Conserva una copia inmutable de cada filtro. */
class ReportMapQuery(
    val region: MapRegion,
    types: Set<IncidentType> = emptySet(),
    statuses: Set<ReportStatus> = emptySet(),
    val limit: Int = DEFAULT_LIMIT,
) {
    val types: Set<IncidentType> = Collections.unmodifiableSet(types.toSet())
    val statuses: Set<ReportStatus> = Collections.unmodifiableSet(statuses.toSet())

    init {
        require(limit in 1..MAX_LIMIT) { "La consulta del mapa admite entre 1 y $MAX_LIMIT marcadores." }
    }

    override fun equals(other: Any?): Boolean = other is ReportMapQuery &&
        region == other.region && types == other.types && statuses == other.statuses && limit == other.limit

    override fun hashCode(): Int {
        var result = region.hashCode()
        result = 31 * result + types.hashCode()
        result = 31 * result + statuses.hashCode()
        return 31 * result + limit
    }

    override fun toString(): String = "ReportMapQuery(region=$region, types=$types, statuses=$statuses, limit=$limit)"

    companion object {
        const val DEFAULT_LIMIT = 200
        const val MAX_LIMIT = 500
    }
}

/** Datos mínimos para un marcador; no expone descripción, referencia, identidad ni evidencia GPS. */
data class ReportMapMarker(
    val id: String,
    val type: IncidentType,
    val priority: Priority,
    val status: ReportStatus,
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0 &&
            longitude.isFinite() && longitude in -180.0..180.0) {
            "El marcador requiere un par de coordenadas válido."
        }
    }
}

/** hasMore indica truncamiento; esta respuesta no debe presentarse como todos los incidentes. */
class ReportMapPage(markers: List<ReportMapMarker>, val hasMore: Boolean) {
    val markers: List<ReportMapMarker> = Collections.unmodifiableList(markers.toList())

    override fun equals(other: Any?): Boolean = other is ReportMapPage && markers == other.markers && hasMore == other.hasMore
    override fun hashCode(): Int = 31 * markers.hashCode() + hasMore.hashCode()
    override fun toString(): String = "ReportMapPage(markers=$markers, hasMore=$hasMore)"
}

/** Lectura local de incidentes por ventana; no cambia un reporte ni inicia GPS. */
fun interface ReportMapRepository {
    suspend fun queryMarkers(query: ReportMapQuery): ReportMapPage
}
