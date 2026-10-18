package com.example.citizensecurity.reports

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.evidence.ReportEvidenceContent
import com.example.citizensecurity.evidence.ReportEvidenceState
import com.example.citizensecurity.evidence.ReportEvidenceViewModel
import com.example.citizensecurity.evidence.evidenceRequest
import com.example.citizensecurity.report.ReportQueryState
import com.example.citizensecurity.report.ReportQueryViewModel
import com.example.citizensecurity.report.ReportPagedSearchViewModel
import com.example.citizensecurity.report.ReportPagedSearchState
import com.example.citizensecurity.report.ReportHistoryViewModel
import com.example.citizensecurity.report.ReportHistoryState
import com.example.citizensecurity.report.currentPagedResult
import com.example.citizensecurity.report.parseReportSearchFilters
import com.example.citizensecurity.report.ReportSearchFilterValidation
import com.example.citizensecurity.tutorial.GuidedTourButton
import com.example.citizensecurity.tutorial.TourTopic
import com.example.citizensecurity.resolution.ReportResolutionContent
import com.example.citizensecurity.resolution.ReportResolutionViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReportsScreen(
    query: ReportQueryViewModel,
    pages: ReportPagedSearchViewModel,
    initialFolio: String?,
    onClose: () -> Unit,
    onMap: (String) -> Unit,
    history: ReportHistoryViewModel? = null,
    evidence: ReportEvidenceViewModel? = null,
    onShareEvidence: (ReportEvidenceState.Ready, () -> Boolean) -> Unit = { _, _ -> },
    onMessage: ((String) -> Unit)? = null,
    resolution: ReportResolutionViewModel? = null,
) {
    val queryState by query.state.collectAsStateWithLifecycle()
    val pageState by pages.state.collectAsStateWithLifecycle()
    var folio by rememberSaveable { mutableStateOf(initialFolio.orEmpty()) }
    var detailFolio by rememberSaveable {
        mutableStateOf(initialFolio?.trim()?.lowercase()?.takeIf { it.isNotEmpty() })
    }
    var typeName by rememberSaveable { mutableStateOf<String?>(null) }
    var statusName by rememberSaveable { mutableStateOf<String?>(null) }
    var priorityName by rememberSaveable { mutableStateOf<String?>(null) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var dateFrom by rememberSaveable { mutableStateOf("") }
    var dateTo by rememberSaveable { mutableStateOf("") }
    var appliedText by rememberSaveable { mutableStateOf("") }
    var appliedFrom by rememberSaveable { mutableStateOf("") }
    var appliedTo by rememberSaveable { mutableStateOf("") }
    var searchError by rememberSaveable { mutableStateOf<String?>(null) }
    var searchRevision by rememberSaveable { mutableIntStateOf(0) }
    var restartSearchPending by rememberSaveable { mutableStateOf(false) }
    // Keep the calendar interpretation stable when the screen is recreated.
    val searchZoneName by rememberSaveable { mutableStateOf(ZoneId.systemDefault().id) }
    val searchZone = remember(searchZoneName) { ZoneId.of(searchZoneName) }
    val selectedType = IncidentType.entries.firstOrNull { it.name == typeName }
    val selectedStatus = ReportStatus.entries.firstOrNull { it.name == statusName }
    val selectedPriority = Priority.entries.firstOrNull { it.name == priorityName }
    val appliedFilters = remember(appliedText, appliedFrom, appliedTo, searchZone, selectedType, selectedStatus, selectedPriority, searchRevision) {
        parseReportSearchFilters(appliedText, appliedFrom, appliedTo, searchZone, selectedType, selectedStatus, selectedPriority)
    }
    val validatedFilters = (appliedFilters as? ReportSearchFilterValidation.Valid)?.filters
    // Check ownership during composition; an effect for new filters may not have run yet.
    val currentPage = if (validatedFilters != null && detailFolio == null)
        currentPagedResult(validatedFilters, pageState) else null
    val currentPageError = (pageState as? ReportPagedSearchState.Error)?.takeIf {
        it.filters === validatedFilters
    }
    val hasNoCriteria = validatedFilters?.let {
        it.type == null && it.status == null && it.priority == null &&
            it.startInclusive == null && it.endExclusive == null && it.normalizedText.isEmpty()
    } == true
    val emptyDatabase = hasNoCriteria && currentPage?.let {
        it.pageNumber == 1 && it.examined == 0 && it.reports.isEmpty() && it.nextCursor == null
    } == true
    val focus = LocalFocusManager.current
    val scroll = rememberLazyListState()
    val queryBusy = queryState is ReportQueryState.Loading
    val leaveDetail = { query.cancelQuery(); history?.clear(); evidence?.clear(); resolution?.clear(); detailFolio = null }
    fun requestSearchRestart() {
        restartSearchPending = true
        searchRevision++
    }
    fun acceptBounded(value: String, current: String, maximum: Int, label: String, accept: (String) -> Unit) {
        if (value.length <= maximum) {
            if (value != current) {
                accept(value)
                searchError = null
            }
        } else {
            focus.clearFocus()
            searchError = "$label admite hasta $maximum caracteres. Se conserva el texto anterior."
        }
    }
    val applySearch = {
        focus.clearFocus()
        when (val checked = parseReportSearchFilters(searchText, dateFrom, dateTo, searchZone)) {
            is ReportSearchFilterValidation.Invalid -> searchError = checked.message
            is ReportSearchFilterValidation.Valid -> {
                appliedText = searchText.trim()
                appliedFrom = dateFrom.trim()
                appliedTo = dateTo.trim()
                searchError = null
                requestSearchRestart()
            }
        }
    }

    LaunchedEffect(detailFolio) {
        val historyFolio = when (val state = history?.state?.value) {
            is ReportHistoryState.Loading -> state.query.reportId
            is ReportHistoryState.Ready -> state.query.reportId
            is ReportHistoryState.NotFound -> state.query.reportId
            is ReportHistoryState.Error -> state.query.reportId
            else -> null
        }
        if (detailFolio == null || historyFolio != detailFolio) history?.clear()
        if (detailFolio == null || evidence?.state?.value?.evidenceRequest()?.reportId != detailFolio) evidence?.clear()
        if (detailFolio == null) query.cancelQuery() else query.findByFolio(detailFolio.orEmpty())
    }
    LaunchedEffect(detailFolio, queryState, resolution) {
        // Recreating this screen rereads the same folio. Its transient Loading hides the
        // section without discarding a retained review or generating a different UUID.
        val sameDetail = when (val current = queryState) {
            is ReportQueryState.Found -> current.report.id == detailFolio
            is ReportQueryState.Loading -> current.folio == detailFolio
            else -> false
        }
        if (detailFolio == null || !sameDetail) resolution?.clear()
    }
    LaunchedEffect(validatedFilters, detailFolio != null) {
        if (validatedFilters == null || detailFolio != null) pages.cancelSearch()
        else if (restartSearchPending) {
            restartSearchPending = false
            pages.search(validatedFilters)
        } else pages.activate(validatedFilters)
    }
    LaunchedEffect(searchError, detailFolio) {
        // Header and folio are the first two items; the rejected-search notice comes next.
        if (searchError != null && detailFolio == null) scroll.animateScrollToItem(2)
    }
    BackHandler(enabled = detailFolio != null, onBack = leaveDetail)

    Scaffold { insets ->
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize().padding(insets).imePadding()
                .testTag(if (detailFolio == null) "report_list" else "report_detail"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            item {
                TextButton(
                    onClick = { if (detailFolio != null) leaveDetail() else onClose() },
                    modifier = Modifier.testTag("report_back_list"),
                ) { Text(if (detailFolio != null) "Volver a los reportes" else "Volver") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        if (detailFolio == null) "Reportes en este teléfono" else "Detalle del reporte",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    GuidedTourButton(if (detailFolio == null) TourTopic.REPORTS else TourTopic.DETAIL)
                }
                Spacer(Modifier.height(8.dp))
                LocalOnlyNotice()
                if (detailFolio == null && emptyDatabase) {
                    Text("Todavía no hay reportes guardados en este teléfono.", Modifier.testTag("report_no_data"))
                }
            }
            if (detailFolio != null) {
                when (val state = queryState) {
                    is ReportQueryState.Found -> {
                        if (state.report.id == detailFolio) {
                            item { ReportDetail(state.report, searchZone, onMap, history, evidence, onShareEvidence, onMessage, resolution) {
                                detailFolio == state.report.id && query.state.value === state
                            } }
                        } else {
                            item { LoadingNotice() }
                        }
                    }
                    is ReportQueryState.Loading, ReportQueryState.Idle -> item { LoadingNotice() }
                    else -> item {
                        QueryNotice(state)
                        Button(onClick = { query.findByFolio(detailFolio.orEmpty()) }) {
                            Text("Consultar nuevamente")
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = folio,
                        onValueChange = { acceptBounded(it, folio, 64, "El campo de folio") { folio = it } },
                        label = { Text("Buscar por folio completo") },
                        supportingText = { Text("Folio completo de 36 caracteres, con guiones.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("report_search"),
                    )
                    Button(
                        onClick = {
                            focus.clearFocus()
                            val normalized = folio.trim().lowercase()
                            if (FULL_FOLIO.matches(normalized)) {
                                detailFolio = normalized
                            } else {
                                query.findByFolio(folio)
                            }
                        },
                        enabled = !queryBusy,
                        modifier = Modifier.fillMaxWidth().testTag("report_query"),
                    ) { Text("Consultar folio") }
                    if (queryState is ReportQueryState.InvalidFolio) QueryNotice(queryState)
                }
                reportSearchControls(
                        text = searchText,
                        from = dateFrom,
                        to = dateTo,
                        onText = { acceptBounded(it, searchText, 512, "La búsqueda") { searchText = it } },
                        onFrom = { acceptBounded(it, dateFrom, 32, "La fecha inicial") { dateFrom = it } },
                        onTo = { acceptBounded(it, dateTo, 32, "La fecha final") { dateTo = it } },
                        error = searchError,
                        onApply = applySearch,
                        onClear = {
                            focus.clearFocus()
                            searchText = ""; dateFrom = ""; dateTo = ""
                            appliedText = ""; appliedFrom = ""; appliedTo = ""
                            typeName = null; statusName = null; priorityName = null; searchError = null
                            requestSearchRestart()
                        },
                        appliedText = appliedText,
                        appliedFrom = appliedFrom,
                        appliedTo = appliedTo,
                        zoneName = searchZoneName,
                    )
                item {
                    Text("Tipo de incidente", style = MaterialTheme.typography.titleSmall)
                    FilterRow(
                        items = IncidentType.entries.map { it.name to typeLabel(it) },
                        selected = typeName,
                        prefix = "report_type",
                        onSelect = { typeName = it },
                    )
                    Text("Estado local", style = MaterialTheme.typography.titleSmall)
                    FilterRow(
                        items = ReportStatus.entries.map { it.name to statusLabel(it) },
                        selected = statusName,
                        prefix = "report_status",
                        onSelect = { statusName = it },
                    )
                    Text("Prioridad indicada en el reporte", style = MaterialTheme.typography.titleSmall)
                    FilterRow(
                        items = Priority.entries.map { it.name to priorityLabel(it) },
                        selected = priorityName,
                        prefix = "report_priority",
                        onSelect = { priorityName = it },
                    )
                    OutlinedButton(
                        onClick = { validatedFilters?.let(pages::search) },
                        modifier = Modifier.fillMaxWidth().testTag("report_refresh"),
                    ) { Text(if (pageState is ReportPagedSearchState.Loading) "Reiniciar búsqueda" else "Actualizar lista · volver al inicio") }
                }
                item {
                    val checked = appliedFilters
                    if (checked is ReportSearchFilterValidation.Invalid) {
                        Text(checked.message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    } else if (currentPageError != null) {
                        Text(currentPageError.message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        Button(onClick = pages::retry, modifier = Modifier.testTag("report_retry_search")) { Text("Reintentar búsqueda") }
                    } else if (currentPage == null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text("Buscando reportes…", Modifier.testTag("report_search_loading")
                                .semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    } else {
                        Text("Página ${currentPage.pageNumber}: ${currentPage.reports.size} coincidencias", Modifier.testTag("report_result_count"))
                        if (currentPage.nextCursor != null) Text("Quedan reportes por revisar. Esta página no representa toda la búsqueda.")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                if (validatedFilters != null && currentPagedResult(validatedFilters, pages.state.value) === currentPage)
                                    pages.previousPage()
                            }, enabled = currentPage.hasPrevious,
                                modifier = Modifier.weight(1f).testTag("report_previous_page")) { Text("Anterior") }
                            Button(onClick = {
                                if (validatedFilters != null && currentPagedResult(validatedFilters, pages.state.value) === currentPage)
                                    pages.nextPage()
                            }, enabled = currentPage.nextCursor != null,
                                modifier = Modifier.weight(1f).testTag("report_next_page")) {
                                Text(if (currentPage.reports.isEmpty()) "Continuar búsqueda" else "Más reportes")
                            }
                        }
                    }
                }
                if (currentPage != null && currentPage.reports.isEmpty() && !emptyDatabase) {
                    item {
                        if (currentPage.nextCursor != null) {
                            Text("No hay coincidencias en el tramo revisado. Continúa buscando.", Modifier.testTag("report_search_incomplete"))
                        } else Text("No hay reportes con estos filtros en el tramo restante.", Modifier.testTag("report_empty_filtered"))
                        TextButton(onClick = {
                            typeName = null; statusName = null; priorityName = null
                            requestSearchRestart()
                            searchText = ""; dateFrom = ""; dateTo = ""
                            appliedText = ""; appliedFrom = ""; appliedTo = ""; searchError = null
                        }) { Text("Mostrar todos") }
                    }
                }
                items(currentPage?.reports.orEmpty(), key = { it.id }) { report ->
                    ReportCard(report, zone = searchZone, enabled = !queryBusy) {
                        if (validatedFilters != null && currentPagedResult(validatedFilters, pages.state.value) === currentPage) {
                            focus.clearFocus()
                            detailFolio = report.id
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalOnlyNotice() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text(
            "Guardados en este teléfono. No se envían a autoridades ni avisan a emergencias.",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun FilterRow(
    items: List<Pair<String, String>>,
    selected: String?,
    prefix: String,
    onSelect: (String?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("${prefix}_filters"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("Todos") },
            modifier = Modifier.testTag("${prefix}_ALL"),
        )
        items.forEach { (key, label) ->
            FilterChip(
                selected = selected == key,
                onClick = { onSelect(key) },
                label = { Text(label) },
                modifier = Modifier.testTag("${prefix}_$key"),
            )
        }
    }
}

@Composable
private fun ReportCard(report: Report, zone: ZoneId, enabled: Boolean, onSelect: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("list_card_${report.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(typeLabel(report.type), style = MaterialTheme.typography.titleMedium)
            Text("Prioridad: ${priorityLabel(report.priority)}")
            Text("Estado local: ${statusLabel(report.status)}")
            Text("Incidente: ${formatReportTime(report.occurredAt, zone)}")
            Text(report.description, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("Folio: ${report.id}", style = MaterialTheme.typography.bodySmall)
            TextButton(
                onClick = onSelect,
                enabled = enabled,
                modifier = Modifier.testTag("report_open_${report.id}"),
            ) { Text("Ver detalle") }
        }
    }
}

@Composable
private fun ReportDetail(report: Report, zone: ZoneId, onMap: (String) -> Unit, history: ReportHistoryViewModel?,
    evidence: ReportEvidenceViewModel?, onShareEvidence: (ReportEvidenceState.Ready, () -> Boolean) -> Unit,
    onMessage: ((String) -> Unit)?,
    resolution: ReportResolutionViewModel?,
    isCurrent: () -> Boolean) {
    val context = LocalContext.current
    var copied by remember(report.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailField("Folio", report.id, "report_folio")
            OutlinedButton(
                onClick = {
                    if (!isCurrent()) return@OutlinedButton
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Folio del reporte", report.id))
                    copied = true
                },
                modifier = Modifier.testTag("report_copy_folio"),
            ) { Text("Copiar folio") }
            if (copied) Text("Folio copiado.", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            DetailField("Tipo de incidente", typeLabel(report.type))
            DetailField("Prioridad", priorityLabel(report.priority))
            DetailField("Estado local", statusLabel(report.status))
            if (history != null) ReportHistoryContent(report, zone, history, isCurrent)
            if (resolution != null) ReportResolutionContent(resolution, report.id, isCurrent)
            DetailField("Descripción", report.description, "report_description")
            if (onMessage != null) OutlinedButton(onClick = { if (isCurrent()) onMessage(report.id) },
                modifier = Modifier.fillMaxWidth().testTag("report_detail_message")) {
                Text("Preparar mensaje para un contacto")
            }
            if (evidence != null) ReportEvidenceContent(report, zone, evidence, isCurrent, onShareEvidence)
            com.example.citizensecurity.report.ReportPhotoContent(report.photo, tagPrefix = "report_photo")
            DetailField("Fecha del incidente", formatReportTime(report.occurredAt, zone))
            DetailField("Guardado en este teléfono", formatReportTime(report.createdAt, zone))
            DetailField("Referencia", report.location.reference.ifBlank { "Sin referencia escrita" })
            val latitude = report.location.latitude
            val longitude = report.location.longitude
            val hasPoint = latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
            if (hasPoint) {
                DetailField("Punto guardado", "$latitude, $longitude")
                Button(onClick = { if (isCurrent()) onMap(report.id) }, modifier = Modifier.testTag("report_detail_map")) {
                    Text("Ver en el mapa")
                }
            } else {
                Text("Este reporte no tiene un punto guardado en el mapa.")
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String, tag: String? = null) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, modifier = if (tag == null) Modifier else Modifier.testTag(tag))
    }
}

@Composable
private fun LoadingNotice() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text("Leyendo los datos de este teléfono…", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun QueryNotice(state: ReportQueryState) {
    val message = when (state) {
        is ReportQueryState.NotFound -> "Ese folio no está guardado en este teléfono."
        is ReportQueryState.InvalidFolio -> state.message
        is ReportQueryState.Error -> state.message
        else -> return
    }
    Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

private fun typeLabel(type: IncidentType): String = when (type) {
    IncidentType.EMERGENCY -> "Emergencia"
    IncidentType.THEFT -> "Robo"
    IncidentType.ACCIDENT -> "Accidente"
    IncidentType.FIRE -> "Incendio"
    IncidentType.RISK -> "Riesgo"
    IncidentType.OTHER -> "Otro"
}

private fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.LOW -> "Baja"
    Priority.MEDIUM -> "Media"
    Priority.HIGH -> "Alta"
}

private fun statusLabel(status: ReportStatus): String = when (status) {
    ReportStatus.REPORTED -> "Guardado localmente"
    ReportStatus.IN_REVIEW -> "En revisión"
    ReportStatus.ATTENDED -> "Atendido"
    ReportStatus.CLOSED -> "Cerrado"
}

private fun formatReportTime(instant: Instant, zone: ZoneId): String = DateTimeFormatter
    .ofPattern("dd/MM/yyyy HH:mm:ss z").withZone(zone).format(instant)

private val FULL_FOLIO = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
