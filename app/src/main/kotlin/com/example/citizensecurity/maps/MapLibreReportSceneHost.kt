package com.example.citizensecurity.maps

import androidx.annotation.MainThread
import com.example.citizensecurity.domain.MapRegion
import com.example.citizensecurity.domain.ReportMapMarker
import java.util.UUID
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Dibuja únicamente los marcadores mínimos recibidos; no consulta, guarda ni obtiene GPS.
 * Cada instancia conserva sus propias fuente, capa y callbacks. Debe cerrarse antes de
 * destruir MapView. El constructor con MapView vuelve a conectar los datos tras recargar
 * el estilo; con solo MapLibreMap, la pantalla llama [onStyleLoaded] desde su callback.
 */
@MainThread
class MapLibreReportSceneHost internal constructor(
    private val access: ReportSceneMapAccess,
) : ReportMapSceneHost {
    constructor(map: MapLibreMap) : this(NativeReportSceneMapAccess(map, null))
    constructor(map: MapLibreMap, mapView: MapView) : this(NativeReportSceneMapAccess(map, mapView))

    private val identity = UUID.randomUUID().toString()
    val sourceId: String = "citizensecurity-reports-source-$identity"
    val layerId: String = "citizensecurity-reports-layer-$identity"

    private var closed = false
    private var revision = 0L
    private var desired: FeatureCollection? = null
    private var attached: ReportSceneResources? = null
    private var synchronizing = false
    private var synchronizeAgain = false
    private var idleRegistration: IdleRegistration? = null
    private val styleListener = MapView.OnDidFinishLoadingStyleListener {
        if (!closed) synchronize()
    }

    init {
        try {
            access.addStyleListener(styleListener)
        } catch (failure: Exception) {
            closed = true
            try { access.removeStyleListener(styleListener) }
            catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    override fun visibleRegion(): MapRegion? = if (closed) null else access.visibleRegion()

    override fun setCameraIdleListener(listener: (() -> Unit)?) {
        if (closed) return
        val previous = idleRegistration
        val replacement = listener?.let { IdleRegistration(it) }
        idleRegistration = replacement
        previous?.receive = null
        try {
            previous?.let { access.removeIdleListener(it.native) }
        } catch (failure: Exception) {
            if (idleRegistration === replacement) idleRegistration = null
            replacement?.receive = null
            throw failure
        }
        if (replacement == null || closed || idleRegistration !== replacement) return
        try {
            access.addIdleListener(replacement.native)
            // Un callback síncrono de otro consumidor puede cerrar o reemplazar el registro.
            if (closed || idleRegistration !== replacement) {
                replacement.receive = null
                access.removeIdleListener(replacement.native)
            }
        } catch (failure: Exception) {
            if (idleRegistration === replacement) idleRegistration = null
            replacement.receive = null
            try { access.removeIdleListener(replacement.native) }
            catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    override fun renderMarkers(markers: List<ReportMapMarker>) {
        if (closed) return
        desired = markers.takeIf { it.isNotEmpty() }?.let(::reportMarkerFeatures)
        revision++
        synchronize()
    }

    override fun clearMarkers() {
        if (closed) return
        desired = null
        revision++
        detach()
    }

    /** Ignora callbacks de un estilo anterior, pendiente o entregados después de close. */
    fun onStyleLoaded(style: Style) {
        if (!closed && access.currentStyleIdentity() === style) synchronize()
    }

    private fun synchronize() {
        if (closed) return
        if (synchronizing) { synchronizeAgain = true; return }
        synchronizing = true
        try {
            do {
                synchronizeAgain = false
                synchronizeOnce()
            } while (!closed && synchronizeAgain)
        } finally {
            synchronizing = false
        }
    }

    private fun synchronizeOnce() {
        if (closed) return
        val features = desired ?: run { detach(); return }
        val style = access.currentStyleIdentity() ?: return
        val previous = attached
        if (previous != null && previous.styleIdentity !== style) detach()
        if (closed || desired !== features || access.currentStyleIdentity() !== style) return
        val existing = attached
        if (existing != null) {
            existing.update(features)
            return
        }
        val expectedRevision = revision
        val resources = access.createResources(style, sourceId, layerId)
        if (closed || revision != expectedRevision || access.currentStyleIdentity() !== style) return
        // Reservar antes de añadir permite que clear/close retiren una instalación parcial.
        attached = resources
        try {
            resources.install(features) {
                !closed && attached === resources && revision == expectedRevision &&
                    access.currentStyleIdentity() === style
            }
        } catch (failure: Exception) {
            if (attached === resources) attached = null
            try { resources.remove() }
            catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            throw failure
        }
        if (closed || attached !== resources || revision != expectedRevision ||
            access.currentStyleIdentity() !== style
        ) {
            if (attached === resources) attached = null
            resources.remove()
            // Una sustitución durante instalación aplica únicamente la lista más reciente.
            if (!closed && desired != null && attached == null) synchronizeAgain = true
        }
    }

    private fun detach() {
        val previous = attached
        attached = null
        previous?.remove()
    }

    override fun close() {
        if (closed) return
        closed = true
        desired = null
        revision++
        val idle = idleRegistration
        idleRegistration = null
        idle?.receive = null
        var failure: Exception? = null
        fun cleanup(action: () -> Unit) {
            try { action() }
            catch (error: Exception) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        cleanup { idle?.let { access.removeIdleListener(it.native) } }
        cleanup { access.removeStyleListener(styleListener) }
        cleanup { detach() }
        failure?.let { throw it }
    }

    private inner class IdleRegistration(var receive: (() -> Unit)?) {
        val native = MapLibreMap.OnCameraIdleListener {
            if (!closed && idleRegistration === this) receive?.invoke()
        }
    }
}

/** GeoJSON tiene longitud primero; sus propiedades no incluyen detalles del reporte o GPS. */
internal fun reportMarkerFeatures(markers: List<ReportMapMarker>): FeatureCollection =
    FeatureCollection.fromFeatures(markers.map { marker ->
        Feature.fromGeometry(Point.fromLngLat(marker.longitude, marker.latitude)).apply {
            addStringProperty("reportId", marker.id)
            addStringProperty("type", marker.type.name)
            addStringProperty("priority", marker.priority.name)
            addStringProperty("status", marker.status.name)
        }
    })

/** Acepta límites continuos del SDK y regiones canónicas que cruzan ±180°, sin invertirlos. */
internal fun reportSceneRegion(south: Double, north: Double, west: Double, east: Double): MapRegion? {
    if (!south.isFinite() || !north.isFinite() || !west.isFinite() || !east.isFinite() ||
        south !in -90.0..90.0 || north !in -90.0..90.0 || south > north
    ) return null
    val span = when {
        east >= west -> east - west
        west in -180.0..180.0 && east in -180.0..180.0 -> east + 360.0 - west
        else -> return null
    }
    if (span >= 360.0) return MapRegion(south, north, -180.0, 180.0)
    fun normalize(value: Double): Double {
        val remainder = value % 360.0
        return when {
            remainder < -180.0 -> remainder + 360.0
            remainder >= 180.0 -> remainder - 360.0
            else -> remainder
        }
    }
    val normalizedWest = normalize(west)
    val normalizedEast = normalize(east).let {
        if (it == -180.0 && normalizedWest != -180.0 && span > 0.0) 180.0 else it
    }
    return MapRegion(south, north, normalizedWest, normalizedEast)
}

/** Seams de operaciones nativas; las pruebas JVM no simulan que el mapa se renderizó. */
internal interface ReportSceneMapAccess {
    fun visibleRegion(): MapRegion?
    fun currentStyleIdentity(): Any?
    fun createResources(style: Any, sourceId: String, layerId: String): ReportSceneResources
    fun addIdleListener(listener: MapLibreMap.OnCameraIdleListener)
    fun removeIdleListener(listener: MapLibreMap.OnCameraIdleListener)
    fun addStyleListener(listener: MapView.OnDidFinishLoadingStyleListener)
    fun removeStyleListener(listener: MapView.OnDidFinishLoadingStyleListener)
}

internal interface ReportSceneResources {
    val styleIdentity: Any
    fun install(features: FeatureCollection, stillCurrent: () -> Boolean)
    fun update(features: FeatureCollection)
    fun remove()
}

private class NativeReportSceneMapAccess(
    private val map: MapLibreMap,
    private val mapView: MapView?,
) : ReportSceneMapAccess {
    override fun visibleRegion(): MapRegion? {
        if (currentStyleIdentity() == null || map.width <= 0f || map.height <= 0f) return null
        val bounds = map.projection.visibleRegion.latLngBounds
        return reportSceneRegion(
            bounds.latitudeSouth, bounds.latitudeNorth, bounds.longitudeWest, bounds.longitudeEast,
        )
    }

    override fun currentStyleIdentity(): Style? = map.style?.takeIf { it.isFullyLoaded }
    override fun createResources(style: Any, sourceId: String, layerId: String): ReportSceneResources =
        NativeReportSceneResources(style as Style, sourceId, layerId)

    override fun addIdleListener(listener: MapLibreMap.OnCameraIdleListener) = map.addOnCameraIdleListener(listener)
    override fun removeIdleListener(listener: MapLibreMap.OnCameraIdleListener) = map.removeOnCameraIdleListener(listener)
    override fun addStyleListener(listener: MapView.OnDidFinishLoadingStyleListener) {
        mapView?.addOnDidFinishLoadingStyleListener(listener)
    }
    override fun removeStyleListener(listener: MapView.OnDidFinishLoadingStyleListener) {
        mapView?.removeOnDidFinishLoadingStyleListener(listener)
    }
}

private class NativeReportSceneResources(
    private val style: Style,
    private val sourceId: String,
    private val layerId: String,
) : ReportSceneResources {
    override val styleIdentity: Any get() = style
    private val source = GeoJsonSource(sourceId, FeatureCollection.fromFeatures(emptyList<Feature>()))
    private val layer = CircleLayer(layerId, sourceId).withProperties(
        PropertyFactory.circleRadius(7f),
        PropertyFactory.circleColor(Expression.match(
            Expression.get("priority"),
            Expression.literal("HIGH"), Expression.literal("#C62828"),
            Expression.literal("MEDIUM"), Expression.literal("#EF6C00"),
            Expression.literal("#1565C0"),
        )),
        PropertyFactory.circleStrokeColor("#FFFFFF"),
        PropertyFactory.circleStrokeWidth(1.5f),
    )
    private var sourceAdded = false
    private var layerAdded = false

    override fun install(features: FeatureCollection, stillCurrent: () -> Boolean) {
        if (!stillCurrent()) return
        check(style.getSource(sourceId) == null && style.getLayer(layerId) == null) {
            "La fuente o capa del mapa ya está ocupada."
        }
        source.setGeoJson(features)
        if (!stillCurrent()) return
        // Reservar también el objeto antes del SDK cubre callbacks durante addSource/addLayer.
        sourceAdded = true
        style.addSource(source)
        sourceAdded = true
        if (!stillCurrent()) { remove(); return }
        layerAdded = true
        style.addLayer(layer)
        layerAdded = true
        if (!stillCurrent()) remove()
    }

    override fun update(features: FeatureCollection) {
        check(style.isFullyLoaded && style.getSource(sourceId) === source && style.getLayer(layerId) === layer) {
            "Los marcadores ya no pertenecen al estilo vigente."
        }
        source.setGeoJson(features)
    }

    override fun remove() {
        // setStyle limpia y desactiva los objetos anteriores: no tocar ese proxy nativo.
        if (!style.isFullyLoaded) { layerAdded = false; sourceAdded = false; return }
        var failure: Exception? = null
        if (layerAdded) {
            layerAdded = false
            try {
                if (style.getLayer(layerId) === layer) check(style.removeLayer(layer)) {
                    "No se pudo retirar la capa de marcadores."
                }
            } catch (error: Exception) { layerAdded = true; failure = error }
        }
        if (sourceAdded) {
            sourceAdded = false
            try {
                if (style.getSource(sourceId) === source) check(style.removeSource(source)) {
                    "No se pudo retirar la fuente de marcadores."
                }
            }
            catch (error: Exception) {
                sourceAdded = true
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}
