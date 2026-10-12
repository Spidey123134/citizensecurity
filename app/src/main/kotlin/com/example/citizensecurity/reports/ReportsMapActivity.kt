package com.example.citizensecurity.reports

import android.content.Intent
import android.graphics.RectF
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.R
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.ReportMapQuery
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.maps.MapLibreReportSceneHost
import com.example.citizensecurity.maps.MapsConfiguration
import com.example.citizensecurity.maps.ReportMapSceneBinding
import com.example.citizensecurity.maps.ReportMapSceneState
import com.example.citizensecurity.maps.ReportMapViewModel
import com.example.citizensecurity.maps.currentVisibleMapPage
import com.example.citizensecurity.maps.currentVisibleMapPageInRegion
import com.example.citizensecurity.report.parseReportSearchFilters
import com.example.citizensecurity.report.ReportSearchFilterValidation
import java.time.ZoneId
import com.example.citizensecurity.preview.PreviewTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.SymbolLayer

/** Consulta solo los reportes de este teléfono. Abrir el mapa no inicia una lectura de zona ni GPS. */
class ReportsMapActivity : ComponentActivity() {
    private lateinit var queryModel: ReportMapViewModel
    private var sceneState by mutableStateOf<ReportMapSceneState>(ReportMapSceneState.Idle)
    private var selectedType by mutableStateOf<IncidentType?>(null)
    private var selectedStatus by mutableStateOf<ReportStatus?>(null)
    private var selectedPriority by mutableStateOf<Priority?>(null)
    private var selectedLimit by mutableIntStateOf(ReportMapQuery.DEFAULT_LIMIT)
    private var selectedFolio by mutableStateOf<String?>(null)
    private var dateFrom by mutableStateOf("")
    private var dateTo by mutableStateOf("")
    private var appliedFrom by mutableStateOf("")
    private var appliedTo by mutableStateOf("")
    private var dateError by mutableStateOf<String?>(null)
    private var dateZone by mutableStateOf(ZoneId.systemDefault().id)
    private var navigationMessage by mutableStateOf("Centro inicial de navegación: Ciudad de México. Puedes mover el mapa; no representa tu ubicación.")
    private var entryReadFailed by mutableStateOf(false)
    private var entryReadInFlight by mutableStateOf(false)
    private var mapInitialized by mutableStateOf(false)
    private var mapReady by mutableStateOf(false)
    private var mapMessage by mutableStateOf("Cargando calles…")
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var host: MapLibreReportSceneHost? = null
    private var scene: ReportMapSceneBinding? = null
    private var mapObserver: LifecycleEventObserver? = null
    private var mapStarted = false
    private var mapResumed = false
    private var styleAttempt: Any? = null
    private var loadedStyle: Style? = null
    private var styleTimeout: Job? = null
    private var styleReady = false
    private var tilesReady = false
    private var frameReady = false
    private var savedMapState: Bundle? = null
    private var entryRead: Job? = null
    private var entryCenter: LatLng? = null
    private var clickListener: MapLibreMap.OnMapClickListener? = null

    internal val reportsMapReady: Boolean get() = mapReady
    internal val currentSceneState: ReportMapSceneState get() = sceneState
    internal fun cameraTarget(): LatLng? = map?.cameraPosition?.target

    /** Evidencia nativa: no cuenta datos del modelo que todavía no se hayan dibujado. */
    internal fun renderedMarkerCount(): Int {
        val current = map ?: return 0
        val view = mapView ?: return 0
        val layer = host?.layerId ?: return 0
        if (!mapReady || current.style?.getLayer(layer) == null) return 0
        return current.queryRenderedFeatures(viewRectangle(view), layer)
            .mapNotNull { it.getStringProperty("reportId") }.distinct().size
    }

    internal fun renderedStreetNames(): List<String> {
        val current = map ?: return emptyList()
        val view = mapView ?: return emptyList()
        val layers = current.style?.layers?.filterIsInstance<SymbolLayer>()?.map { it.id }.orEmpty()
        if (!mapReady || layers.isEmpty()) return emptyList()
        return current.queryRenderedFeatures(viewRectangle(view), *layers.toTypedArray())
            .mapNotNull { it.properties()?.get("name")?.takeIf { value -> value.isJsonPrimitive }?.asString }
            .filter { it.isNotBlank() }.distinct()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dependencies = application as CitizenSecurityApplication
        queryModel = ViewModelProvider(this, dependencies.reportMapViewModelFactory)[ReportMapViewModel::class.java]
        selectedType = savedInstanceState?.getString(TYPE)?.let { value -> IncidentType.entries.find { it.name == value } }
        selectedStatus = savedInstanceState?.getString(STATUS)?.let { value -> ReportStatus.entries.find { it.name == value } }
        selectedPriority = savedInstanceState?.getString(PRIORITY)?.let { value -> Priority.entries.find { it.name == value } }
        selectedLimit = savedInstanceState?.getInt(LIMIT, ReportMapQuery.DEFAULT_LIMIT)
            ?.takeIf { it in LIMITS } ?: ReportMapQuery.DEFAULT_LIMIT
        dateZone = savedInstanceState?.getString(DATE_ZONE)?.takeIf {
            it.length <= 100 && runCatching { ZoneId.of(it) }.isSuccess
        }
            ?: ZoneId.systemDefault().id
        dateFrom = savedInstanceState?.getString(DATE_FROM)?.takeIf { it.length <= 32 }.orEmpty()
        dateTo = savedInstanceState?.getString(DATE_TO)?.takeIf { it.length <= 32 }.orEmpty()
        appliedFrom = savedInstanceState?.getString(APPLIED_FROM)?.takeIf { it.length <= 32 }.orEmpty()
        appliedTo = savedInstanceState?.getString(APPLIED_TO)?.takeIf { it.length <= 32 }.orEmpty()
        dateError = savedInstanceState?.getString(DATE_ERROR)?.takeIf { it.length <= 256 }
        savedMapState = savedInstanceState?.getBundle(MAP_STATE)
        mapInitialized = runCatching { MapsConfiguration.initialize(this) }.isSuccess
        setContent { PreviewTheme { Content() } }
        if (intent.hasExtra(EXTRA_FOLIO)) readEntryReport()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Content() {
        val reading by queryModel.state.collectAsStateWithLifecycle()
        val page = currentVisibleMapPage(sceneState, reading)
        var showZoneList by remember { mutableStateOf(false) }
        val focus = LocalFocusManager.current
        LaunchedEffect(page) { if (page == null) showZoneList = false }
        if (showZoneList && page != null) AlertDialog(
            onDismissRequest = { showZoneList = false },
            title = { Text("Reportes de esta zona") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (page.hasMore) "Misma muestra limitada del mapa; hay más coincidencias. Acerca la zona o cambia el límite."
                        else "Mismos reportes de la consulta vigente del mapa.")
                    if (page.markers.isEmpty()) Text("No hay coincidencias en esta consulta.")
                    LazyColumn(Modifier.heightIn(max = 320.dp).testTag("map_zone_list")) {
                        items(page.markers, key = { it.id }) { marker ->
                            TextButton(onClick = {
                                showZoneList = false
                                openVisibleReport(marker.id)
                            },
                                modifier = Modifier.fillMaxWidth().testTag("map_zone_report_${marker.id}")) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(typeLabel(marker.type) + " · " + statusLabel(marker.status))
                                    Text("Prioridad: " + when (marker.priority) {
                                        Priority.HIGH -> "Alta"; Priority.MEDIUM -> "Media"; Priority.LOW -> "Baja"
                                    })
                                    Text("Folio: ${marker.id}", style = MaterialTheme.typography.bodySmall)
                                    Text("Abrir detalle")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showZoneList = false }) { Text("Volver al mapa") } },
        )
        Scaffold(
            topBar = { TopAppBar(title = { Text("Reportes en el mapa") },
                navigationIcon = { TextButton(onClick = ::finish) { Text("Volver") } }) },
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                // Se calcula después de barra e insets; en horizontal queda espacio real para el mapa.
                val controlsMaxHeight = maxHeight * 0.52f
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reportes locales de este teléfono. No se envían a autoridades ni muestran incidentes de otros usuarios.",
                        style = MaterialTheme.typography.bodySmall)
                    if (mapInitialized) AndroidView(
                        factory = { context -> FrameLayout(context).apply {
                            try { addView(createMap(), FrameLayout.LayoutParams(-1, -1)) }
                            catch (_: Exception) {
                                closeMap()
                                mapMessage = "No se pudo preparar el mapa. Vuelve e inténtalo nuevamente."
                            }
                        } },
                        onRelease = { container -> if (mapView?.parent === container) closeMap() },
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("reports_map_canvas"),
                    ) else Text("No se pudo iniciar el mapa en este dispositivo.", Modifier.weight(1f))
                    // El resultado no debe cambiar la proyección que originó su propia consulta:
                    // Loading/hasMore/selección del folio conservan una altura estable para el mapa.
                    Column(Modifier.fillMaxWidth().height(controlsMaxHeight)
                        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(navigationMessage, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("map_navigation_message"))
                        if (entryReadInFlight) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (entryReadFailed) TextButton(onClick = ::readEntryReport,
                            modifier = Modifier.testTag("retry_map_folio")) { Text("Reintentar consulta del folio") }
                        if (!mapReady) {
                            Text(mapMessage, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("map_loading_message"))
                            if (mapInitialized && map != null && mapMessage != "Cargando calles…") {
                                TextButton(onClick = { mapView?.let(::loadStyle) },
                                    modifier = Modifier.testTag("retry_reports_map")) { Text("Reintentar mapa") }
                            }
                        } else if (scene == null) {
                            TextButton(onClick = { resetQuery() },
                                modifier = Modifier.testTag("retry_map_connection")) { Text("Reintentar conexión de la consulta") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChoiceMenu("Tipo: " + (selectedType?.let(::typeLabel) ?: "Todos"),
                                listOf(null) + IncidentType.entries, selectedType,
                                { value -> value?.let(::typeLabel) ?: "Todos" }, "map_type_filter", Modifier.weight(1f)) {
                                selectedType = it; resetQuery()
                            }
                            ChoiceMenu("Estado: " + (selectedStatus?.let(::statusLabel) ?: "Todos"),
                                listOf(null) + ReportStatus.entries, selectedStatus,
                                { value -> value?.let(::statusLabel) ?: "Todos" }, "map_status_filter", Modifier.weight(1f)) {
                                selectedStatus = it; resetQuery()
                            }
                        }
                        ChoiceMenu("Prioridad: " + (selectedPriority?.let(::priorityLabel) ?: "Todas"),
                            listOf(null) + Priority.entries, selectedPriority,
                            { value -> value?.let(::priorityLabel) ?: "Todas" }, "map_priority_filter", Modifier.fillMaxWidth()) {
                            selectedPriority = it; resetQuery()
                        }
                        ChoiceMenu("Límite: $selectedLimit marcadores", LIMITS, selectedLimit,
                            { "$it marcadores" }, "map_limit_filter", Modifier.fillMaxWidth()) {
                            selectedLimit = it; resetQuery()
                        }
                        Text("Fechas del incidente (opcionales)", style = MaterialTheme.typography.titleSmall)
                        SearchDateField(dateFrom, { acceptDate(it, true) }, "Desde", "map_date_from", dateZone) {
                            focus.clearFocus(); applyDates()
                        }
                        SearchDateField(dateTo, { acceptDate(it, false) }, "Hasta", "map_date_to", dateZone) {
                            focus.clearFocus(); applyDates()
                        }
                        dateError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("map_date_error").semantics { liveRegion = LiveRegionMode.Polite }) }
                        Text("Fechas aplicadas: ${appliedFrom.ifEmpty { "sin inicio" }} → ${appliedTo.ifEmpty { "sin final" }} ($dateZone).",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("map_applied_dates"))
                        OutlinedButton(onClick = { focus.clearFocus(); applyDates() },
                            modifier = Modifier.fillMaxWidth().testTag("apply_map_dates")) { Text("Aplicar fechas") }
                        TextButton(onClick = {
                            focus.clearFocus(); dateFrom = ""; dateTo = ""; appliedFrom = ""; appliedTo = ""
                            dateError = null; resetQuery()
                        }, modifier = Modifier.testTag("clear_map_dates")) { Text("Quitar fechas") }
                        Button(onClick = ::refreshVisible, enabled = mapReady && scene != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("query_reports_zone")) {
                            Text(if (sceneState is ReportMapSceneState.Error) "Reintentar esta zona" else "Consultar esta zona")
                        }
                        Text(sceneMessage(), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("map_reports_message")
                                .semantics { liveRegion = LiveRegionMode.Polite })
                        val ready = sceneState as? ReportMapSceneState.Ready
                        Text(ready?.let { "${it.count} marcadores en esta zona" } ?: "Sin una consulta vigente",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("map_marker_count"))
                        if (ready != null) {
                            Text("Prioridad indicada por el autor: rojo alta, naranja media, azul baja. El marcador no acredita que el incidente haya sido verificado.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = {
                            if (visibleZonePage() != null) showZoneList = true else resetQuery()
                        }, enabled = page != null,
                            modifier = Modifier.fillMaxWidth().testTag("open_map_zone_list")) {
                            Text("Ver lista de esta zona")
                        }
                        selectedFolio?.let { folio ->
                            Text("Folio seleccionado: $folio", style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("map_selected_folio"))
                            OutlinedButton(onClick = {
                                openVisibleReport(folio)
                            }, modifier = Modifier.fillMaxWidth().testTag("open_map_report")) { Text("Abrir reporte") }
                        }
                        Text("Solo se muestran reportes con coordenadas guardadas. Los que tienen únicamente una referencia aparecen en la lista.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            startActivity(Intent(this@ReportsMapActivity, ReportsActivity::class.java))
                        }, modifier = Modifier.fillMaxWidth().testTag("open_map_report_list")) { Text("Ver lista de reportes") }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    @Composable
    private fun <T> ChoiceMenu(title: String, values: List<T>, selected: T, label: (T) -> String,
        tag: String, modifier: Modifier, onSelect: (T) -> Unit) {
        var expanded by remember { mutableStateOf(false) }
        Box(modifier) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().testTag(tag)) { Text(title) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                values.forEachIndexed { index, value -> DropdownMenuItem(
                    text = { Text(label(value) + if (selected == value) " ✓" else "") },
                    onClick = { expanded = false; if (selected != value) onSelect(value) },
                    modifier = Modifier.testTag("${tag}_$index"),
                ) }
            }
        }
    }

    private fun sceneMessage(): String = when (val current = sceneState) {
        ReportMapSceneState.Idle, ReportMapSceneState.Closed -> "Mueve el mapa, elige los filtros y pulsa Consultar esta zona. Al volver a esta pantalla debes consultar de nuevo."
        is ReportMapSceneState.Loading -> "Consultando los reportes guardados de esta zona…"
        is ReportMapSceneState.Error -> current.message
        ReportMapSceneState.ViewportUnavailable -> "No se pudo obtener la zona visible. Espera a que el mapa termine de cargar y consulta otra vez."
        is ReportMapSceneState.Ready -> when {
            current.hasMore -> "Hay más reportes de los que permite el límite de ${current.query.limit}. Acerca el mapa o filtra para ver otros. Esta muestra no es la lista completa."
            current.count == 0 -> "No hay reportes guardados con coordenadas que coincidan con esta zona y estos filtros. Esto no indica que la zona sea segura."
            else -> "Toca un marcador para elegir su folio. Mover el mapa actualiza la consulta con los filtros aplicados."
        }
    }

    private fun typeLabel(value: IncidentType) = resources.getStringArray(R.array.tools_types)[value.ordinal]
    private fun statusLabel(value: ReportStatus) = when (value) {
        ReportStatus.REPORTED -> "Reportado"
        ReportStatus.IN_REVIEW -> "En revisión"
        ReportStatus.ATTENDED -> "Atendido"
        ReportStatus.CLOSED -> "Cerrado"
    }

    private fun priorityLabel(value: Priority) = when (value) {
        Priority.HIGH -> "Alta"; Priority.MEDIUM -> "Media"; Priority.LOW -> "Baja"
    }

    private fun visibleZonePage() = currentVisibleMapPageInRegion(
        sceneState, queryModel.state.value, runCatching { host?.visibleRegion() }.getOrNull(),
    )

    private fun refreshVisible() {
        if (!mapReady) return
        val filters = parseReportSearchFilters(startDate = appliedFrom, endDate = appliedTo, zoneId = ZoneId.of(dateZone))
        if (filters is ReportSearchFilterValidation.Invalid) { dateError = filters.message; return }
        val dates = (filters as ReportSearchFilterValidation.Valid).filters
        selectedFolio = null
        scene?.refreshVisible(selectedType?.let { setOf(it) }.orEmpty(), selectedStatus?.let { setOf(it) }.orEmpty(), selectedLimit,
            dates.startInclusive, dates.endExclusive, selectedPriority?.let { setOf(it) }.orEmpty())
    }

    private fun acceptDate(value: String, from: Boolean) {
        val current = if (from) dateFrom else dateTo
        if (value.length > 32) { dateError = "La fecha admite hasta 32 caracteres. Se conserva el texto anterior."; return }
        if (value != current) {
            if (from) dateFrom = value else dateTo = value
            dateError = null
        }
    }

    private fun applyDates() {
        when (val checked = parseReportSearchFilters(startDate = dateFrom, endDate = dateTo, zoneId = ZoneId.of(dateZone))) {
            is ReportSearchFilterValidation.Invalid -> dateError = checked.message + " Se conserva la consulta anterior."
            is ReportSearchFilterValidation.Valid -> {
                appliedFrom = dateFrom.trim(); appliedTo = dateTo.trim(); dateError = null
                resetQuery()
            }
        }
    }

    private fun openReport(folio: String) {
        startActivity(Intent(this, ReportsActivity::class.java).putExtra(EXTRA_FOLIO, folio))
    }

    private fun openVisibleReport(folio: String) {
        if (visibleZonePage()?.markers?.any { it.id == folio } == true) openReport(folio)
        else resetQuery()
    }

    /** Cambiar filtros retira el resultado anterior; el siguiente gesto aplica la selección nueva. */
    private fun resetQuery() {
        closeScene()
        sceneState = ReportMapSceneState.Idle
        if (mapReady) createScene()
    }

    private fun createScene() {
        if (!mapReady || scene != null || isDestroyed) return
        val current = map ?: return
        val view = mapView ?: return
        val nextHost = MapLibreReportSceneHost(current, view)
        host = nextHost
        try {
            scene = ReportMapSceneBinding(this, queryModel, nextHost) { state ->
                if (host === nextHost && mapView === view && !isDestroyed) {
                    sceneState = state
                    if (state !is ReportMapSceneState.Ready) selectedFolio = null
                }
            }
        } catch (_: Exception) {
            runCatching { nextHost.close() }
            host = null
            sceneState = ReportMapSceneState.Error(null, "No se pudo preparar la consulta. Reintenta cargar el mapa.")
        }
    }

    private fun createMap(): MapView {
        closeMap()
        val view = MapView(this)
        mapView = view
        val restored = savedMapState
        view.onCreate(restored)
        savedMapState = null
        val observer = LifecycleEventObserver { _, event ->
            if (mapView === view) when (event) {
                Lifecycle.Event.ON_START -> { mapStarted = true; view.onStart() }
                Lifecycle.Event.ON_RESUME -> { mapResumed = true; view.onResume() }
                Lifecycle.Event.ON_PAUSE -> { mapResumed = false; view.onPause() }
                Lifecycle.Event.ON_STOP -> { mapStarted = false; view.onStop() }
                else -> Unit
            }
        }
        mapObserver = observer
        lifecycle.addObserver(observer)
        view.addOnDidFinishLoadingMapListener {
            if (mapView === view && styleReady && map?.style === loadedStyle && !isDestroyed) {
                tilesReady = true; publishReadiness()
            }
        }
        view.addOnDidFinishRenderingFrameListener { fully, _, _ ->
            if (mapView === view && styleReady && map?.style === loadedStyle && !isDestroyed && fully) {
                frameReady = true; publishReadiness()
            }
        }
        view.addOnDidFailLoadingMapListener {
            if (mapView === view && !isDestroyed) {
                closeScene()
                mapReady = false
                sceneState = ReportMapSceneState.Error(null, "La consulta se retiró porque el mapa no pudo cargar. Reintenta el mapa y consulta nuevamente.")
                mapMessage = "No se pudieron cargar las calles. Revisa tu conexión y reintenta."
            }
        }
        view.getMapAsync { current ->
            if (mapView !== view || isDestroyed) return@getMapAsync
            map = current
            if (restored == null || entryCenter != null) {
                current.moveCamera(CameraUpdateFactory.newLatLngZoom(entryCenter ?: LatLng(19.4326, -99.1332), 14.0))
            }
            val listener = MapLibreMap.OnMapClickListener { location ->
                val layer = host?.layerId
                if (mapView !== view || !mapReady || sceneState !is ReportMapSceneState.Ready || layer == null) false
                else {
                    val point = current.projection.toScreenLocation(location)
                    val radius = 18f * resources.displayMetrics.density
                    val hits = current.queryRenderedFeatures(RectF(point.x - radius, point.y - radius, point.x + radius, point.y + radius), layer)
                    selectedFolio = hits.firstOrNull()?.getStringProperty("reportId")
                    selectedFolio != null
                }
            }
            clickListener = listener
            current.addOnMapClickListener(listener)
            loadStyle(view)
        }
        return view
    }

    private fun publishReadiness() {
        if (!mapReady && styleReady && tilesReady && frameReady) {
            mapReady = true
            styleTimeout?.cancel()
            createScene()
        }
    }

    private fun loadStyle(view: MapView) {
        val current = map ?: return
        if (mapView !== view || isDestroyed) return
        closeScene()
        sceneState = ReportMapSceneState.Idle
        val attempt = Any()
        styleAttempt = attempt
        styleTimeout?.cancel()
        mapReady = false
        styleReady = false
        loadedStyle = null
        tilesReady = false
        frameReady = false
        mapMessage = "Cargando calles…"
        try {
            current.setStyle(Style.Builder().fromUri(MapsConfiguration.STYLE_URL)) { style ->
                if (mapView !== view || styleAttempt !== attempt || isDestroyed) return@setStyle
                loadedStyle = style
                styleReady = style.isFullyLoaded
                publishReadiness()
            }
        } catch (_: Exception) {
            mapMessage = "No se pudo cargar el mapa. Revisa tu conexión y reintenta."
        }
        styleTimeout = lifecycleScope.launch {
            delay(20_000)
            if (mapView === view && styleAttempt === attempt && !mapReady) {
                mapMessage = "La carga del mapa está tardando. Revisa tu conexión y reintenta."
            }
        }
    }

    /** El folio viene de la navegación; se leen sus coordenadas guardadas, nunca extras de GPS. */
    private fun readEntryReport() {
        if (entryReadInFlight) return
        val folio = intent.getStringExtra(EXTRA_FOLIO)?.trim()?.lowercase().orEmpty()
        if (!FOLIO_PATTERN.matches(folio)) {
            entryReadFailed = false
            navigationMessage = "No se puede centrar un folio incompleto. Puedes consultar una zona del mapa."
            return
        }
        entryReadFailed = false
        entryReadInFlight = true
        navigationMessage = "Consultando el lugar guardado del folio…"
        entryRead = lifecycleScope.launch {
            try {
                val report = (application as CitizenSecurityApplication).reportRepository.findById(folio)
                ensureActive()
                val latitude = report?.location?.latitude
                val longitude = report?.location?.longitude
                when {
                    report == null -> navigationMessage = "Este folio no existe en este teléfono. Puedes consultar otra zona."
                    latitude == null || longitude == null -> navigationMessage = "Este reporte tiene solo una referencia escrita. Consulta su detalle desde la lista."
                    !latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0 -> {
                        navigationMessage = "El lugar guardado de este reporte no contiene coordenadas válidas. Consulta su detalle desde la lista."
                    }
                    else -> {
                        entryCenter = LatLng(latitude, longitude)
                        // La lectura de este folio no activa el seguimiento de zona.
                        resetQuery()
                        map?.moveCamera(CameraUpdateFactory.newLatLngZoom(entryCenter!!, 15.0))
                        navigationMessage = "Mapa centrado en el lugar guardado del folio $folio. Pulsa Consultar esta zona para ver sus marcadores."
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                entryReadFailed = true
                navigationMessage = "No se pudo consultar el lugar del folio. Reintenta; el mapa sigue disponible."
            } finally {
                entryReadInFlight = false
            }
        }
    }

    private fun closeScene() {
        val previous = scene
        val previousHost = host
        scene = null
        host = null
        selectedFolio = null
        // Cerrar antes de destruir MapView; nunca se cierra el repositorio compartido.
        if (previous != null) runCatching { previous.close() }
        else runCatching { previousHost?.close() }
    }

    private fun closeMap() {
        val view = mapView
        val current = map
        closeScene()
        clickListener?.let { listener -> runCatching { current?.removeOnMapClickListener(listener) } }
        clickListener = null
        mapObserver?.let(lifecycle::removeObserver)
        mapObserver = null
        mapView = null
        map = null
        mapReady = false
        styleReady = false
        loadedStyle = null
        tilesReady = false
        frameReady = false
        styleAttempt = null
        styleTimeout?.cancel()
        styleTimeout = null
        if (mapResumed) runCatching { view?.onPause() }
        if (mapStarted) runCatching { view?.onStop() }
        mapResumed = false
        mapStarted = false
        runCatching { view?.onDestroy() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(TYPE, selectedType?.name)
        outState.putString(STATUS, selectedStatus?.name)
        outState.putString(PRIORITY, selectedPriority?.name)
        outState.putInt(LIMIT, selectedLimit)
        outState.putString(DATE_ZONE, dateZone)
        outState.putString(DATE_FROM, dateFrom); outState.putString(DATE_TO, dateTo)
        outState.putString(APPLIED_FROM, appliedFrom); outState.putString(APPLIED_TO, appliedTo)
        outState.putString(DATE_ERROR, dateError)
        mapView?.let { view -> outState.putBundle(MAP_STATE, Bundle().also(view::onSaveInstanceState)) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        entryRead?.cancel()
        closeMap()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    private fun viewRectangle(view: MapView) = RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())

    companion object {
        const val EXTRA_FOLIO = "report_folio"
        private const val TYPE = "map_reports_type"
        private const val STATUS = "map_reports_status"
        private const val PRIORITY = "map_reports_priority"
        private const val LIMIT = "map_reports_limit"
        private const val MAP_STATE = "map_reports_native_state"
        private const val DATE_ZONE = "map_date_zone"
        private const val DATE_FROM = "map_date_from"
        private const val DATE_TO = "map_date_to"
        private const val APPLIED_FROM = "map_applied_from"
        private const val APPLIED_TO = "map_applied_to"
        private const val DATE_ERROR = "map_date_error"
        private val LIMITS = listOf(50, 100, 200, 500)
        private val FOLIO_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
