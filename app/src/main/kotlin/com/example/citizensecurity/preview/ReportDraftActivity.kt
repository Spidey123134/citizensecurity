package com.example.citizensecurity.preview

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.graphics.RectF
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
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
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.maps.*
import com.example.citizensecurity.report.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Formulario de borrador. Usa los modelos productivos; no abre ni escribe una base de reportes. */
class ReportDraftActivity : ComponentActivity() {
    internal lateinit var draftModel: ReportDraftViewModel
        private set
    internal lateinit var locationModel: IncidentLocationViewModel
        private set
    private lateinit var permissionModel: LocationPermissionViewModel
    private lateinit var flow: IncidentLocationFlowBinding
    private lateinit var bridge: ReportLocationFlowBinding
    private var selection by mutableStateOf<ReportLocationSelectionRequest?>(null)
    private var mapReady by mutableStateOf(false)
    private var mapMessage by mutableStateOf("Cargando calles…")
    private var permissionExplanation by mutableStateOf(false)
    private var exitQuestion by mutableStateOf(false)
    private var reviewResult by mutableStateOf<ReportDraftReview?>(null)
    private var reviewErrors by mutableStateOf<Map<ReportDraftField, String>>(emptyMap())
    private var mapView: MapView? = null
    private var map: MapLibreMap? = null
    private var mapAttachment: ReportLocationMapBinding? = null
    private var mapObserver: LifecycleEventObserver? = null
    private var mapStarted = false
    private var mapResumed = false
    private var styleAttempt: Any? = null
    private var styleTimeout: Job? = null
    private var centeredFix: Long? = null
    private var savedMapState: Bundle? = null
    private var mapInitialized = false
    private var styleReady = false
    private var tilesReady = false
    private var frameReady = false
    internal val previewMapReady: Boolean get() = mapReady
    internal fun previewPointRendered(): Boolean {
        val current = map ?: return false
        val view = mapView ?: return false
        return mapReady && current.queryRenderedFeatures(RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()), PIN_LAYER).isNotEmpty()
    }

    internal fun previewRenderedStreetNames(): List<String> {
        val current = map ?: return emptyList()
        val view = mapView ?: return emptyList()
        val layers = current.style?.layers?.filterIsInstance<SymbolLayer>()?.map { it.id }.orEmpty()
        if (!mapReady || layers.isEmpty()) return emptyList()
        return current.queryRenderedFeatures(RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()), *layers.toTypedArray())
            .mapNotNull { it.properties()?.get("name")?.takeIf { value -> value.isJsonPrimitive }?.asString }
            .filter { it.isNotBlank() }.distinct()
    }

    private fun publishMapReadiness() {
        if (!mapReady && styleReady && tilesReady && frameReady) {
            mapReady = true
            styleTimeout?.cancel()
            updateMap(locationModel.state.value)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draftModel = ViewModelProvider(this, ReportDraftViewModel.factory())[ReportDraftViewModel::class.java]
        val dependencies = application as CitizenSecurityApplication
        locationModel = ViewModelProvider(this, dependencies.incidentLocationViewModelFactory(draftModel.state.value.location))[
            IncidentLocationViewModel::class.java,
        ]
        permissionModel = ViewModelProvider(this, LocationPermissionViewModel.factory())[LocationPermissionViewModel::class.java]
        flow = IncidentLocationFlowBinding.forActivity(this, permissionModel, locationModel)
        bridge = ReportLocationFlowBinding(this, flow, draftModel)
        selection = bridge.currentSelection
        savedMapState = savedInstanceState?.getBundle(MAP_STATE)
        if (savedInstanceState == null && draftModel.state.value.occurredAt == null) {
            draftModel.setOccurredAt(Instant.now().minusSeconds(30))
        }
        mapInitialized = runCatching { MapsConfiguration.initialize(this) }.isSuccess
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        setContent { PreviewTheme { Content() } }
    }

    private fun goBack() {
        val request = selection
        if (request != null) {
            if (bridge.cancelSelection(request.token)) {
                closeMap()
                selection = null
            }
        } else {
            val state = draftModel.state.value
            if (state.description.isNotBlank() || state.location.reference.isNotBlank() || state.location.latitude != null) {
                exitQuestion = true
            } else finish()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Content() {
        val draft by draftModel.state.collectAsStateWithLifecycle()
        val screens = rememberSaveableStateHolder()
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = { TopAppBar(
                title = { Text(if (selection == null) "Nuevo reporte" else "Lugar del incidente") },
                navigationIcon = { TextButton(onClick = ::goBack) { Text("Volver") } },
            ) },
        ) { padding ->
            val request = selection
            if (request == null) screens.SaveableStateProvider("report_form") {
                Form(draft, Modifier.padding(padding))
            }
            else MapSelection(request, Modifier.padding(padding))
        }
        if (permissionExplanation) AlertDialog(
            onDismissRequest = { permissionExplanation = false },
            title = { Text("Ubicación precisa") },
            text = { Text("La ubicación del teléfono permite comprobar que el punto está a un máximo de 5 km. Solo se obtiene cuando pulses Buscar mi ubicación.") },
            confirmButton = { TextButton(onClick = {
                permissionExplanation = false
                selection?.let { bridge.requestPermission(it.token, explanationAccepted = true) }
            }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { permissionExplanation = false }) { Text("Ahora no") } },
        )
        if (exitQuestion) AlertDialog(
            onDismissRequest = { exitQuestion = false }, title = { Text("¿Salir del borrador?") },
            text = { Text("Este borrador todavía no se guarda. Si sales, perderás sus cambios.") },
            confirmButton = { TextButton(onClick = { exitQuestion = false; finish() }) { Text("Salir") } },
            dismissButton = { TextButton(onClick = { exitQuestion = false }) { Text("Seguir editando") } },
        )
        if (reviewResult is ReportDraftReview.Valid) AlertDialog(
            onDismissRequest = { reviewResult = null }, title = { Text("Borrador válido") },
            text = { Text("Los campos están completos. No se guardó ni se envió ningún reporte; esta vista prepara el borrador para tu revisión.") },
            confirmButton = { TextButton(onClick = { reviewResult = null }) { Text("Continuar editando") } },
        )
    }

    @Composable
    private fun Form(draft: ReportDraftState, modifier: Modifier) {
        val locale = LocalConfiguration.current.locales[0]
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        // Conservar también el texto rechazado: el usuario puede corregirlo, sin truncarlo.
        var description by rememberSaveable { mutableStateOf(draft.description) }
        var reference by rememberSaveable { mutableStateOf(draft.location.reference) }
        val errors = reviewErrors + draft.inputErrors
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text("Prepara un borrador. Todavía no se guarda ni envía; no avisa a emergencias.", Modifier.padding(16.dp))
            }
            Text("1. ¿Qué ocurrió?", style = MaterialTheme.typography.titleLarge)
            IncidentType.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { type -> FilterChip(
                        selected = draft.type == type,
                        onClick = { draftModel.setType(type); reviewErrors = reviewErrors - ReportDraftField.TYPE },
                        label = { Text(resources.getStringArray(R.array.tools_types)[type.ordinal]) },
                        modifier = Modifier.weight(1f).testTag("type_${type.name}"),
                    ) }
                }
            }
            FieldError(errors[ReportDraftField.TYPE])
            Text("Prioridad indicada por ti", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Priority.entries.forEach { priority -> FilterChip(
                    selected = draft.priority == priority,
                    onClick = { draftModel.setPriority(priority); reviewErrors = reviewErrors - ReportDraftField.PRIORITY },
                    label = { Text(resources.getStringArray(R.array.tools_priorities)[priority.ordinal]) },
                    modifier = Modifier.weight(1f).testTag("priority_${priority.name}"),
                ) }
            }
            FieldError(errors[ReportDraftField.PRIORITY])
            OutlinedTextField(
                value = description, onValueChange = { description = it; draftModel.setDescription(it); reviewErrors = reviewErrors - ReportDraftField.DESCRIPTION },
                label = { Text("Describe lo ocurrido") }, minLines = 3, maxLines = 6,
                isError = errors[ReportDraftField.DESCRIPTION] != null,
                supportingText = { Text(errors[ReportDraftField.DESCRIPTION] ?: "${description.codePointCount(0, description.length)}/1000 · mínimo 10 caracteres") },
                modifier = Modifier.fillMaxWidth().testTag("draft_description"),
            )
            OutlinedButton(onClick = ::pickDate, modifier = Modifier.fillMaxWidth()) {
                Text("Fecha y hora: " + (draft.occurredAt?.let {
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale).withZone(ZoneId.systemDefault()).format(it)
                } ?: "Seleccionar"))
            }
            FieldError(errors[ReportDraftField.OCCURRED_AT])
            HorizontalDivider()
            Text("2. Lugar del incidente", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = reference, onValueChange = { reference = it; draftModel.setLocationReference(it); reviewErrors = reviewErrors - ReportDraftField.LOCATION_REFERENCE },
                label = { Text("Dirección o referencia") }, isError = errors[ReportDraftField.LOCATION_REFERENCE] != null,
                supportingText = { Text(errors[ReportDraftField.LOCATION_REFERENCE] ?: "Calle, colonia y una referencia visible. Máximo 200 caracteres.") },
                modifier = Modifier.fillMaxWidth().testTag("draft_reference"),
            )
            Button(onClick = {
                focus.clearFocus()
                keyboard?.hide()
                savedMapState = null
                selection = bridge.openSelection()
                reviewErrors = reviewErrors - ReportDraftField.LOCATION_COORDINATES
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("open_incident_map")) {
                Text(if (draft.location.latitude == null) "Seleccionar punto en el mapa" else "Cambiar punto en el mapa")
            }
            if (draft.location.latitude != null && draft.location.longitude != null) {
                Text("Punto elegido: %.5f, %.5f".format(locale, draft.location.latitude, draft.location.longitude))
            }
            FieldError(errors[ReportDraftField.LOCATION_COORDINATES])
            Text("Solo se confirma un punto dentro de 5 km de tu ubicación precisa. El GPS debe estar actualizado.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Button(onClick = {
                val result = draftModel.review(Instant.now())
                reviewResult = result
                reviewErrors = (result as? ReportDraftReview.Invalid)?.errors ?: emptyMap()
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("review_draft")) { Text("Revisar borrador") }
            errors[ReportDraftField.SNAPSHOT]?.let { FieldError(it) }
            Text("Las fotos y el guardado se conectarán después de revisar este flujo.", style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun FieldError(message: String?) {
        if (message != null) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    @Composable
    private fun MapSelection(request: ReportLocationSelectionRequest, modifier: Modifier) {
        val locale = LocalConfiguration.current.locales[0]
        val state by locationModel.state.collectAsStateWithLifecycle()
        val windowHeightPx = LocalWindowInfo.current.containerSize.height
        val controlsMaxHeight = with(LocalDensity.current) { (windowHeightPx * 0.52f).toDp() }
        val permissions by permissionModel.state.collectAsStateWithLifecycle()
        val capturing = state is IncidentLocationState.Capturing
        val fix = when (state) {
            is IncidentLocationState.Idle -> (state as IncidentLocationState.Idle).deviceFix
            is IncidentLocationState.PointSelected -> (state as IncidentLocationState.PointSelected).deviceFix
            else -> null
        }
        Column(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Toca el lugar exacto. Después busca tu ubicación y confirma el punto.", style = MaterialTheme.typography.bodyMedium)
            if (mapInitialized) AndroidView(
                factory = { context -> FrameLayout(context).apply {
                    try {
                        addView(createMap(request), FrameLayout.LayoutParams(-1, -1))
                    } catch (_: Exception) {
                        closeMap()
                        mapMessage = "No se pudo preparar el mapa. Vuelve al borrador e inténtalo de nuevo."
                    }
                } },
                update = { container -> if (mapView?.parent === container) updateMap(state) },
                onRelease = { container -> if (mapView?.parent === container) closeMap() },
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("incident_map"),
            ) else {
                Text("No se pudo iniciar el mapa en este dispositivo.", modifier = Modifier.weight(1f))
            }
            Column(Modifier.fillMaxWidth().heightIn(max = controlsMaxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!mapReady) {
                Text(mapMessage, style = MaterialTheme.typography.bodySmall)
                if (mapMessage != "Cargando calles…" && mapInitialized) {
                    TextButton(onClick = { mapView?.let(::loadStyle) }) { Text("Reintentar mapa") }
                }
            }
            val point = state.proposal as? LocationProposal.ValidPoint
            Text(if (point == null) "Todavía no has elegido un punto." else "Punto: %.5f, %.5f".format(locale, point.latitude, point.longitude),
                style = MaterialTheme.typography.bodySmall)
            Text(permissionGuidance(permissions),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("permission_guidance").semantics { liveRegion = LiveRegionMode.Polite })
            if (state is IncidentLocationState.Error) Text(
                gpsGuidance((state as IncidentLocationState.Error).issue),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("gps_guidance").semantics { liveRegion = LiveRegionMode.Polite })
            if (fix != null) Text("GPS obtenido. Precisión aproximada: ±%.0f m".format(locale, fix.accuracyMeters), style = MaterialTheme.typography.bodySmall)
            else if (!capturing) Text("Actualiza el GPS para comprobar los 5 km.", style = MaterialTheme.typography.bodySmall)
            if (capturing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(
                enabled = !capturing && !permissions.requestInFlight,
                onClick = {
                    if (permissions.access == LocationPermissionAccess.Precise) bridge.refreshLocation(request.token)
                    else if (bridge.requestPermission(request.token) == LocationPermissionAction.ExplanationRequired) permissionExplanation = true
                }, modifier = Modifier.fillMaxWidth().testTag("locate_phone"),
            ) { Text(when {
                capturing -> "Buscando ubicación…"
                permissions.requestInFlight -> "Esperando permiso…"
                permissions.access == LocationPermissionAccess.Precise -> "Buscar mi ubicación"
                else -> "Permitir ubicación precisa"
            }) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = ::goBack, modifier = Modifier.weight(1f).testTag("cancel_map")) { Text("Cancelar") }
                Button(
                    enabled = mapReady && point != null && fix != null && !capturing && permissions.access == LocationPermissionAccess.Precise,
                    onClick = {
                        if (bridge.confirm(request.token) is NearbyLocationConfirmation.Confirmed) {
                            closeMap()
                            selection = null
                        }
                    }, modifier = Modifier.weight(1f).testTag("confirm_point"),
                ) { Text("Confirmar punto") }
            }
            Spacer(Modifier.height(6.dp))
            }
        }
    }

    private fun createMap(request: ReportLocationSelectionRequest): MapView {
        closeMap()
        mapAttachment = bridge.prepareMap(request.token)
        val attachment = mapAttachment
        val view = MapView(this)
        mapView = view
        view.onCreate(savedMapState)
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
            if (mapView === view && !isDestroyed) { tilesReady = true; publishMapReadiness() }
        }
        view.addOnDidFinishRenderingFrameListener { fully, _, _ ->
            if (mapView === view && !isDestroyed && fully) { frameReady = true; publishMapReadiness() }
        }
        view.addOnDidFailLoadingMapListener {
            if (mapView === view && !mapReady) mapMessage = "No se pudieron cargar las calles. Revisa tu conexión y reintenta."
        }
        view.getMapAsync { current ->
            if (mapView !== view || isDestroyed || attachment?.attachMap(current) != true) return@getMapAsync
            map = current
            val original = request.originalLocation
            val latitude = original.latitude
            val longitude = original.longitude
            val center = if (latitude != null && longitude != null) LatLng(latitude, longitude)
                else LatLng(19.4326, -99.1332)
            current.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 14.0))
            loadStyle(view)
        }
        return view
    }

    private fun loadStyle(view: MapView) {
        val current = map ?: return
        if (mapView !== view || isDestroyed) return
        val attempt = Any()
        styleAttempt = attempt
        styleTimeout?.cancel()
        mapReady = false
        styleReady = false
        tilesReady = false
        frameReady = false
        mapMessage = "Cargando calles…"
        current.setStyle(Style.Builder().fromUri(MapsConfiguration.STYLE_URL)) { style ->
            if (mapView !== view || styleAttempt !== attempt || isDestroyed) return@setStyle
            try {
                style.addSource(GeoJsonSource(PIN_SOURCE, FeatureCollection.fromFeatures(emptyArray<Feature>())))
                style.addLayer(CircleLayer(PIN_LAYER, PIN_SOURCE).withProperties(
                    circleColor("#007F78"), circleRadius(9f), circleStrokeColor("#FFFFFF"), circleStrokeWidth(3f),
                ))
                styleReady = true
                publishMapReadiness()
            } catch (_: Exception) {
                mapReady = false
                mapMessage = "No se pudo mostrar el punto sobre el mapa. Intenta cargarlo de nuevo."
            }
        }
        styleTimeout = lifecycleScope.launch {
            delay(20_000)
            if (mapView === view && styleAttempt === attempt && !mapReady) {
                mapMessage = "La carga del mapa está tardando. Revisa tu conexión y reintenta."
            }
        }
    }

    private fun updateMap(state: IncidentLocationState) {
        if (!mapReady) return
        val current = map ?: return
        val style = current.style ?: return
        val point = state.proposal as? LocationProposal.ValidPoint
        val features = if (point == null) emptyArray<Feature>() else arrayOf(Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)))
        style.getSourceAs<GeoJsonSource>(PIN_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(features))
        val fix = when (state) {
            is IncidentLocationState.Idle -> state.deviceFix
            is IncidentLocationState.PointSelected -> state.deviceFix
            else -> null
        }
        if (fix != null && centeredFix != fix.elapsedRealtimeNanos) {
            centeredFix = fix.elapsedRealtimeNanos
            current.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(fix.latitude, fix.longitude), 15.0))
        }
    }

    private fun closeMap() {
        val view = mapView
        val attachment = mapAttachment
        mapAttachment = null
        mapObserver?.let(lifecycle::removeObserver)
        mapObserver = null
        mapView = null
        map = null
        mapReady = false
        styleReady = false
        tilesReady = false
        frameReady = false
        centeredFix = null
        styleAttempt = null
        styleTimeout?.cancel()
        styleTimeout = null
        // Al cambiar de pantalla puede seguir RESUMED: cerrar toda la vida de esta vista.
        runCatching { attachment?.close() }
        if (mapResumed) runCatching { view?.onPause() }
        if (mapStarted) runCatching { view?.onStop() }
        mapResumed = false
        mapStarted = false
        runCatching { view?.onDestroy() }
    }

    private fun pickDate() {
        val calendar = Calendar.getInstance().apply { timeInMillis = (draftModel.state.value.occurredAt ?: Instant.now()).toEpochMilli() }
        DatePickerDialog(this, { _, year, month, day ->
            calendar.set(year, month, day)
            TimePickerDialog(this, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                draftModel.setOccurredAt(Instant.ofEpochMilli(calendar.timeInMillis))
                reviewErrors = reviewErrors - ReportDraftField.OCCURRED_AT
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapView?.let { view -> outState.putBundle(MAP_STATE, Bundle().also(view::onSaveInstanceState)) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        closeMap()
        if (::bridge.isInitialized) bridge.close()
        if (::flow.isInitialized) flow.close()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    private companion object {
        const val MAP_STATE = "draft_preview_map_state"
        const val PIN_SOURCE = "draft-preview-point"
        const val PIN_LAYER = "draft-preview-point-layer"
    }
}
