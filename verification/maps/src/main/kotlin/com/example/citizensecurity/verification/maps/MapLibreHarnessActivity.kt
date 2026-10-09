package com.example.citizensecurity.verification.maps

import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.maps.IncidentMapSession
import com.example.citizensecurity.maps.MapLibreIncidentBinding
import com.example.citizensecurity.maps.MapsConfiguration
import org.maplibre.android.RenderingEngine
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import java.util.concurrent.CountDownLatch

/**
 * Activity exclusiva del APK testOnly: vista nativa real, sin pantalla productiva ni fuente GPS.
 * La cámara inicial es un punto de prueba explícito, nunca una ubicación del teléfono.
 * La selección utiliza el adaptador y la sesión originales; confirmar exige evidencia real ausente.
 */
class MapLibreHarnessActivity : ComponentActivity() {
    lateinit var mapView: MapView
        private set
    var map: MapLibreMap? = null
        private set
    var incidentBinding: MapLibreIncidentBinding? = null
        private set
    val session = IncidentMapSession(
        ReportLocation("Selección técnica sin reporte"),
        deviceLocation = { null },
        elapsedRealtimeNanos = SystemClock::elapsedRealtimeNanos,
    )
    val monitor = NativeMapLoadMonitor()
    val terminalLoad = CountDownLatch(1)
    val proposalReceived = CountDownLatch(1)
    val mapIdleAfterReady = CountDownLatch(1)

    @Volatile
    var mapIdle = false
        private set

    @Volatile
    var latestProposal: LocationProposal = LocationProposal.None
        private set
    @Volatile
    var proposalCount = 0
        private set
    @Volatile
    var mapDestroyed = false
        private set
    var registeredNativeListenerCount = 0
        private set

    @Volatile
    var nativeStyleEvents = 0
        private set
    @Volatile
    var nativeMapEvents = 0
        private set
    @Volatile
    var nativeFrameEvents = 0
        private set
    @Volatile
    var nativeFullFrames = 0
        private set
    @Volatile
    var nativeIdleEvents = 0
        private set
    @Volatile
    private var lastQueriedFeatures = NativeRenderedFeatures(0, 0, 0)

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private var observedLayers = NativeMapLayers()
    private val timeout = Runnable {
        if (!mapDestroyed) {
            monitor.onTimeout()
            Log.d(DIAGNOSTIC_TAG, "timeout: ${loadDiagnostics()}")
            publishLoadState()
        }
    }
    private val styleListener = MapView.OnDidFinishLoadingStyleListener {
        if (!mapDestroyed) nativeStyleEvents++
        if (!mapDestroyed) map?.getStyle { loadedStyle ->
            if (!mapDestroyed) {
                observedLayers = inspectLayers(loadedStyle)
                monitor.onStyleLoaded(observedLayers)
                Log.d(DIAGNOSTIC_TAG, "style: ${loadDiagnostics()}")
                publishLoadState()
            }
        }
    }
    private val mapListener = MapView.OnDidFinishLoadingMapListener {
        if (!mapDestroyed) {
            nativeMapEvents++
            monitor.onMapLoaded()
            Log.d(DIAGNOSTIC_TAG, "map: ${loadDiagnostics()}")
            publishLoadState()
        }
    }
    private val failureListener = MapView.OnDidFailLoadingMapListener { _ ->
        if (!mapDestroyed) {
            monitor.onSdkFailure()
            publishLoadState()
        }
    }
    private val frameListener = MapView.OnDidFinishRenderingFrameListener { fully, _, _ ->
        if (!mapDestroyed) {
            nativeFrameEvents++
            if (fully) nativeFullFrames++
        }
        if (!mapDestroyed && fully && observedLayers.isComplete &&
            monitor.state == NativeMapLoadState.Loading
        ) {
            val features = renderedFeatures()
            if (features != lastQueriedFeatures || nativeFullFrames == 1) {
                lastQueriedFeatures = features
                Log.d(DIAGNOSTIC_TAG, "frame: ${loadDiagnostics()}")
            }
            monitor.onFrameRendered(true, features)
            publishLoadState()
        }
    }
    private val idleListener = MapView.OnDidBecomeIdleListener {
        if (!mapDestroyed) {
            nativeIdleEvents++
            if (monitor.state is NativeMapLoadState.Ready) {
                // Fully rendered puede preceder la transición de opacidad de los símbolos.
                mapIdle = true
                mapIdleAfterReady.countDown()
            }
            Log.d(DIAGNOSTIC_TAG, "idle: ${loadDiagnostics()}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapsConfiguration.initialize(applicationContext)
        Log.d(DIAGNOSTIC_TAG, "backend=${RenderingEngine.getCurrentType()}")
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = textView(R.string.lab_title, 18f)
        status = textView(R.string.lab_loading, 14f)
        mapView = MapView(this).apply { contentDescription = getString(R.string.lab_map_description) }
        layout.addView(title)
        layout.addView(status)
        layout.addView(mapView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        layout.addView(textView(R.string.lab_no_gps, 12f))
        layout.addView(textView(R.string.lab_attribution, 12f).apply {
            text = Html.fromHtml(getString(R.string.lab_attribution), Html.FROM_HTML_MODE_LEGACY)
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
        mapView.addOnDidFailLoadingMapListener(failureListener)
        mapView.addOnDidFinishRenderingFrameListener(frameListener)
        mapView.addOnDidBecomeIdleListener(idleListener)
        registeredNativeListenerCount = 5
        handler.postDelayed(
            timeout,
            intent.getLongExtra(EXTRA_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS).coerceIn(500L, DEFAULT_TIMEOUT_MILLIS),
        )
        mapView.getMapAsync { nativeMap ->
            if (mapDestroyed) return@getMapAsync
            map = nativeMap
            incidentBinding = MapLibreIncidentBinding(nativeMap, session) { proposal ->
                latestProposal = proposal
                proposalCount++
                proposalReceived.countDown()
            }
            if (savedInstanceState == null) {
                nativeMap.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(TEST_LATITUDE, TEST_LONGITUDE))
                    .zoom(TEST_ZOOM)
                    .build()
            }
            // El único otro estilo permitido es un recurso inexistente para provocar un fallo nativo.
            val styleUri = if (intent.getBooleanExtra(EXTRA_UNAVAILABLE_STYLE, false)) {
                UNAVAILABLE_STYLE_URI
            } else OPENFREEMAP_STYLE_URI
            nativeMap.setStyle(Style.Builder().fromUri(styleUri))
        }
    }

    /** Consulta el fotograma real, sin añadir geometría o marcadores ficticios. Hilo principal. */
    fun renderedFeatures(): NativeRenderedFeatures {
        val nativeMap = map ?: return NativeRenderedFeatures(0, 0, 0)
        if (mapDestroyed || !observedLayers.isComplete) return NativeRenderedFeatures(0, 0, 0)
        val viewport = RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat())
        return NativeRenderedFeatures(
            nativeMap.queryRenderedFeatures(viewport, *observedLayers.roads.toTypedArray()).size,
            nativeMap.queryRenderedFeatures(viewport, *observedLayers.labels.toTypedArray()).size,
            nativeMap.queryRenderedFeatures(viewport, *observedLayers.buildings.toTypedArray()).size,
        )
    }

    /** Nombres del mosaico real devueltos por el SDK, solo para la evidencia de esta vista técnica. */
    fun renderedLabelNames(): List<String> {
        val nativeMap = map ?: return emptyList()
        if (mapDestroyed || observedLayers.labels.isEmpty()) return emptyList()
        val viewport = RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat())
        return nativeMap.queryRenderedFeatures(viewport, *observedLayers.labels.toTypedArray())
            .mapNotNull { feature ->
                val properties = feature.properties()
                listOf("name", "name:latin", "name:nonlatin", "ref").firstNotNullOfOrNull { field ->
                    properties?.get(field)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?.asString?.takeIf { it.isNotBlank() }
                }
            }.distinct().take(30)
    }

    /** Solo señales y cantidades técnicas; no registra URLs de red ni datos del teléfono. */
    fun loadDiagnostics(): String =
        "backend=${RenderingEngine.getCurrentType()}, " +
            "style=${monitor.styleLoaded}($nativeStyleEvents), map=${monitor.mapLoaded}($nativeMapEvents), " +
            "full=${monitor.fullyRendered}, frames=$nativeFrameEvents/$nativeFullFrames, idle=$mapIdle($nativeIdleEvents), " +
            "layers=${observedLayers.roads.size}/${observedLayers.labels.size}/${observedLayers.buildings.size}, " +
            "features=${lastQueriedFeatures.roads}/${lastQueriedFeatures.labels}/${lastQueriedFeatures.buildings}, " +
            "viewport=${mapView.width}x${mapView.height}"

    /** Desconecta solo el adaptador original; permite comprobar un segundo toque nativo ignorado. */
    fun closeIncidentBinding() {
        try {
            incidentBinding?.close()
        } finally {
            incidentBinding = null
            session.cancel()
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        mapDestroyed = true
        handler.removeCallbacks(timeout)
        try {
            closeIncidentBinding()
        } finally {
            mapView.removeOnDidFinishLoadingStyleListener(styleListener)
            mapView.removeOnDidFinishLoadingMapListener(mapListener)
            mapView.removeOnDidFailLoadingMapListener(failureListener)
            mapView.removeOnDidFinishRenderingFrameListener(frameListener)
            mapView.removeOnDidBecomeIdleListener(idleListener)
            registeredNativeListenerCount = 0
            monitor.close()
            terminalLoad.countDown()
            mapIdleAfterReady.countDown()
            mapView.onDestroy()
            map = null
            super.onDestroy()
        }
    }

    private fun textView(resource: Int, size: Float) = TextView(this).apply {
        setText(resource)
        textSize = size
        val padding = (8 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
    }

    private fun publishLoadState() {
        val state = monitor.state
        status.setText(when (state) {
            NativeMapLoadState.Loading -> R.string.lab_loading
            is NativeMapLoadState.Ready -> R.string.lab_ready
            is NativeMapLoadState.Failed -> when (state.reason) {
                NativeMapFailure.SdkFailure -> R.string.lab_failed_sdk
                NativeMapFailure.TimedOut -> R.string.lab_failed_timeout
                NativeMapFailure.RequiredLayersMissing -> R.string.lab_failed_layers
            }
            NativeMapLoadState.Closed -> R.string.lab_closed
        })
        if (state != NativeMapLoadState.Loading) {
            handler.removeCallbacks(timeout)
            if (state is NativeMapLoadState.Failed) closeIncidentBinding()
            terminalLoad.countDown()
        }
    }

    private fun inspectLayers(style: Style) = NativeMapLayers(
        roads = style.layers.filterIsInstance<LineLayer>()
            .filter { it.sourceLayer == "transportation" }.map { it.id },
        labels = style.layers.filterIsInstance<SymbolLayer>()
            .filter { it.sourceLayer == "transportation_name" || it.sourceLayer == "poi" || it.sourceLayer == "place" }
            .map { it.id },
        // Liberty cambia la capa plana (13–14) por extrusión 3D desde zoom 14.
        buildings = style.layers.mapNotNull { layer ->
            when (layer) {
                is FillLayer -> layer.takeIf { it.sourceLayer == "building" }?.id
                is FillExtrusionLayer -> layer.takeIf { it.sourceLayer == "building" }?.id
                else -> null
            }
        },
    )

    companion object {
        const val EXTRA_UNAVAILABLE_STYLE = "lab_unavailable_style"
        const val EXTRA_TIMEOUT_MILLIS = "lab_map_timeout_millis"
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
        const val OPENFREEMAP_STYLE_URI = MapsConfiguration.STYLE_URL
        const val UNAVAILABLE_STYLE_URI = "asset://verification-unavailable-style.json"
        const val TEST_LATITUDE = 19.4326077
        const val TEST_LONGITUDE = -99.1332088
        const val TEST_ZOOM = 15.5
        private const val DIAGNOSTIC_TAG = "MapLibreLab"
    }
}
