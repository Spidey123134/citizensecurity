package com.example.citizensecurity.verification.maps

import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.domain.ReportMapPage
import com.example.citizensecurity.domain.ReportMapQuery
import com.example.citizensecurity.domain.ReportMapRepository
import com.example.citizensecurity.maps.MapLibreReportSceneHost
import com.example.citizensecurity.maps.MapsConfiguration
import com.example.citizensecurity.maps.ReportMapSceneBinding
import com.example.citizensecurity.maps.ReportMapSceneState
import com.example.citizensecurity.maps.ReportMapViewModel
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.geojson.Feature

/** Pantalla exclusiva de testOnly. Nunca abre el repositorio productivo ni solicita GPS. */
class MapZoneHarnessActivity : ComponentActivity() {
    lateinit var mapView: MapView
        private set
    lateinit var queryButton: Button
        private set
    lateinit var store: ZoneLabStoreViewModel
        private set
    lateinit var reportModel: ReportMapViewModel
        private set
    var map: MapLibreMap? = null
        private set
    var host: MapLibreReportSceneHost? = null
        private set
    var binding: ReportMapSceneBinding? = null
        private set
    var monitor = NativeMapLoadMonitor()
        private set
    var terminalLoad = CountDownLatch(1)
        private set
    var mapIdleAfterReady = CountDownLatch(1)
        private set
    @Volatile var mapIdle = false
        private set
    @Volatile var mapDestroyed = false
        private set
    var registeredNativeListenerCount = 0
        private set
    var latestState: ReportMapSceneState = ReportMapSceneState.Idle
        private set
    var stateDeliveries = 0
        private set
    private lateinit var status: TextView
    private var layers = NativeMapLayers()
    private val handler = Handler(Looper.getMainLooper())
    private val stateObservers = mutableListOf<(ReportMapSceneState) -> Unit>()
    private val frameObservers = mutableListOf<() -> Unit>()
    private val timeout = Runnable {
        if (!mapDestroyed) {
            monitor.onTimeout()
            publishMapLoad()
        }
    }
    private val styleListener = MapView.OnDidFinishLoadingStyleListener {
        if (!mapDestroyed) map?.getStyle { style ->
            if (!mapDestroyed) {
                layers = inspectLayers(style)
                monitor.onStyleLoaded(layers)
                publishMapLoad()
            }
        }
    }
    private val mapListener = MapView.OnDidFinishLoadingMapListener {
        if (!mapDestroyed) {
            monitor.onMapLoaded()
            publishMapLoad()
        }
    }
    private val failedListener = MapView.OnDidFailLoadingMapListener {
        if (!mapDestroyed) {
            monitor.onSdkFailure()
            publishMapLoad()
        }
    }
    private val frameListener = MapView.OnDidFinishRenderingFrameListener { fully, _, _ ->
        if (!mapDestroyed && fully) {
            if (layers.isComplete && monitor.state == NativeMapLoadState.Loading) {
                monitor.onFrameRendered(true, renderedBaseFeatures())
                publishMapLoad()
            }
            notifyFrameObservers()
        }
    }
    private val idleListener = MapView.OnDidBecomeIdleListener {
        if (!mapDestroyed) {
            if (monitor.state is NativeMapLoadState.Ready) {
                mapIdle = true
                mapIdleAfterReady.countDown()
            }
            notifyFrameObservers()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapsConfiguration.initialize(applicationContext)
        store = ViewModelProvider(this, viewModelFactory {
            initializer { ZoneLabStoreViewModel(applicationContext) }
        })[ZoneLabStoreViewModel::class.java]
        reportModel = ViewModelProvider(this, ReportMapViewModel.factory(store.recording))[ReportMapViewModel::class.java]
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = text(R.string.zones_idle, 14f)
        queryButton = Button(this).apply {
            id = QUERY_BUTTON_ID
            setText(R.string.zones_query)
            setOnClickListener { binding?.refreshVisible() }
        }
        mapView = MapView(this).apply { contentDescription = getString(R.string.zones_map_description) }
        layout.addView(text(R.string.zones_title, 18f))
        layout.addView(status)
        layout.addView(queryButton)
        layout.addView(mapView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        layout.addView(text(R.string.zones_no_gps, 12f))
        layout.addView(text(R.string.lab_attribution, 12f).apply {
            this.text = Html.fromHtml(getString(R.string.lab_attribution), Html.FROM_HTML_MODE_LEGACY)
            movementMethod = LinkMovementMethod.getInstance()
        })
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(layout)
        mapView.onCreate(savedInstanceState)
        mapView.addOnDidFinishLoadingStyleListener(styleListener)
        mapView.addOnDidFinishLoadingMapListener(mapListener)
        mapView.addOnDidFailLoadingMapListener(failedListener)
        mapView.addOnDidFinishRenderingFrameListener(frameListener)
        mapView.addOnDidBecomeIdleListener(idleListener)
        registeredNativeListenerCount = 5
        handler.postDelayed(timeout, MAP_TIMEOUT_MILLIS)
        mapView.getMapAsync { nativeMap ->
            if (mapDestroyed) return@getMapAsync
            map = nativeMap
            host = MapLibreReportSceneHost(nativeMap, mapView)
            binding = ReportMapSceneBinding(this, reportModel, requireNotNull(host), ::publishScene)
            if (savedInstanceState == null) nativeMap.cameraPosition = CameraPosition.Builder()
                .target(LatLng(TEST_LATITUDE, TEST_LONGITUDE)).zoom(TEST_ZOOM).build()
            nativeMap.setStyle(Style.Builder().fromUri(MapsConfiguration.STYLE_URL))
        }
    }

    /** Solo escucha una señal observada; no inicia consultas ni fabrica un resultado. Hilo main. */
    fun observeState(observer: (ReportMapSceneState) -> Unit): Closeable {
        stateObservers.add(observer)
        observer(latestState)
        return Closeable { stateObservers.remove(observer) }
    }

    fun observeFrames(observer: () -> Unit): Closeable {
        frameObservers.add(observer)
        observer()
        return Closeable { frameObservers.remove(observer) }
    }

    fun renderedMarkerFeatures(): List<Feature> {
        val nativeMap = map ?: return emptyList()
        val layer = host?.layerId ?: return emptyList()
        if (mapDestroyed || nativeMap.style?.getLayer(layer) == null) return emptyList()
        return nativeMap.queryRenderedFeatures(viewport(), layer)
    }

    fun renderedMarkerIds(): Set<String> = renderedMarkerFeatures()
        .map { it.getStringProperty("reportId") }.toSet()

    fun renderedBaseFeatures(): NativeRenderedFeatures {
        val nativeMap = map ?: return NativeRenderedFeatures(0, 0, 0)
        if (mapDestroyed || !layers.isComplete) return NativeRenderedFeatures(0, 0, 0)
        return NativeRenderedFeatures(
            nativeMap.queryRenderedFeatures(viewport(), *layers.roads.toTypedArray()).size,
            nativeMap.queryRenderedFeatures(viewport(), *layers.labels.toTypedArray()).size,
            nativeMap.queryRenderedFeatures(viewport(), *layers.buildings.toTypedArray()).size,
        )
    }

    fun reloadRealStyle() {
        check(!mapDestroyed)
        handler.removeCallbacks(timeout)
        monitor = NativeMapLoadMonitor()
        terminalLoad = CountDownLatch(1)
        mapIdleAfterReady = CountDownLatch(1)
        mapIdle = false
        layers = NativeMapLayers()
        handler.postDelayed(timeout, MAP_TIMEOUT_MILLIS)
        requireNotNull(map).setStyle(Style.Builder().fromUri(MapsConfiguration.STYLE_URL))
    }

    fun closeScene() {
        binding?.close()
    }

    private fun publishScene(next: ReportMapSceneState) {
        latestState = next
        stateDeliveries++
        when (next) {
            ReportMapSceneState.Idle -> status.setText(R.string.zones_idle)
            is ReportMapSceneState.Loading -> status.setText(R.string.zones_loading)
            is ReportMapSceneState.Ready -> status.text = resources.getQuantityString(
                if (next.hasMore) R.plurals.zones_truncated else R.plurals.zones_ready,
                next.count,
                next.count,
            )
            is ReportMapSceneState.Error -> status.setText(R.string.zones_failed)
            ReportMapSceneState.ViewportUnavailable -> status.setText(R.string.zones_viewport_unavailable)
            ReportMapSceneState.Closed -> status.setText(R.string.zones_closed)
        }
        stateObservers.toList().forEach { it(next) }
    }

    private fun publishMapLoad() {
        if (monitor.state != NativeMapLoadState.Loading) {
            handler.removeCallbacks(timeout)
            terminalLoad.countDown()
        }
    }

    private fun notifyFrameObservers() = frameObservers.toList().forEach { it() }
    private fun viewport() = RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat())
    private fun text(resource: Int, size: Float) = TextView(this).apply {
        setText(resource)
        textSize = size
        val inset = (8 * resources.displayMetrics.density).toInt()
        setPadding(inset, inset, inset, inset)
    }
    private fun inspectLayers(style: Style) = NativeMapLayers(
        roads = style.layers.filterIsInstance<LineLayer>()
            .filter { it.sourceLayer == "transportation" }.map { it.id },
        labels = style.layers.filterIsInstance<SymbolLayer>()
            .filter { it.sourceLayer in setOf("transportation_name", "place", "poi") }.map { it.id },
        buildings = style.layers.mapNotNull { layer -> when (layer) {
            is FillLayer -> layer.takeIf { it.sourceLayer == "building" }?.id
            is FillExtrusionLayer -> layer.takeIf { it.sourceLayer == "building" }?.id
            else -> null
        } },
    )

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { mapView.onSaveInstanceState(outState); super.onSaveInstanceState(outState) }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }

    override fun onDestroy() {
        mapDestroyed = true
        handler.removeCallbacks(timeout)
        try { closeScene() } finally {
            mapView.removeOnDidFinishLoadingStyleListener(styleListener)
            mapView.removeOnDidFinishLoadingMapListener(mapListener)
            mapView.removeOnDidFailLoadingMapListener(failedListener)
            mapView.removeOnDidFinishRenderingFrameListener(frameListener)
            mapView.removeOnDidBecomeIdleListener(idleListener)
            registeredNativeListenerCount = 0
            monitor.close()
            terminalLoad.countDown()
            mapIdleAfterReady.countDown()
            stateObservers.clear()
            frameObservers.clear()
            mapView.onDestroy()
            map = null
            super.onDestroy()
        }
    }

    companion object {
        const val TEST_LATITUDE = 19.4326077
        const val TEST_LONGITUDE = -99.1332088
        const val TEST_ZOOM = 15.5
        const val MAP_TIMEOUT_MILLIS = 30_000L
        const val QUERY_BUTTON_ID = 0x437A11
    }
}

/** Base en memoria retenida al recrear Activity; se cierra al liberar su ViewModelStore. */
class ZoneLabStoreViewModel(context: Context) : ViewModel() {
    val sqlite = SqliteReportRepository(context, databaseName = null)
    val recording = RecordingMapRepository(sqlite)
    @Volatile var closed = false
        private set
    override fun onCleared() {
        closed = true
        sqlite.close()
    }
}

/** Cuenta lecturas terminadas de SQLite, no respuestas prefabricadas. Solo pertenece a testOnly. */
class RecordingMapRepository(private val sqlite: SqliteReportRepository) : ReportMapRepository {
    val queries = CopyOnWriteArrayList<ReportMapQuery>()
    val completedReads = AtomicInteger()
    private val nextGate = AtomicReference<QueryGate?>()

    fun holdNextQuery(): QueryGate = QueryGate().also { check(nextGate.compareAndSet(null, it)) }

    override suspend fun queryMarkers(query: ReportMapQuery): ReportMapPage {
        val gate = nextGate.getAndSet(null)
        queries.add(query)
        val page = sqlite.queryMarkers(query)
        completedReads.incrementAndGet()
        if (gate != null) {
            gate.readCompleted.countDown()
            try { gate.release.await() } catch (cancelled: CancellationException) {
                gate.cancelled.countDown()
                throw cancelled
            }
        }
        return page
    }
}

class QueryGate {
    val readCompleted = CountDownLatch(1)
    val cancelled = CountDownLatch(1)
    val release = CompletableDeferred<Unit>()
}
