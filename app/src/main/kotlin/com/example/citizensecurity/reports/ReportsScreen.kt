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
import com.example.citizensecurity.report.ReportListState
import com.example.citizensecurity.report.ReportListViewModel
import com.example.citizensecurity.report.ReportQueryState
import com.example.citizensecurity.report.ReportQueryViewModel
import com.example.citizensecurity.report.filterLocalReports
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReportsScreen(
    list: ReportListViewModel,
    query: ReportQueryViewModel,
    initialFolio: String?,
    onClose: () -> Unit,
    onMap: (String) -> Unit,
) {
    val listState by list.state.collectAsStateWithLifecycle()
    val queryState by query.state.collectAsStateWithLifecycle()
    var folio by rememberSaveable { mutableStateOf(initialFolio.orEmpty()) }
    var detailFolio by rememberSaveable {
        mutableStateOf(initialFolio?.trim()?.lowercase()?.takeIf { it.isNotEmpty() })
    }
    var typeName by rememberSaveable { mutableStateOf<String?>(null) }
    var statusName by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val queryBusy = queryState is ReportQueryState.Loading

    LaunchedEffect(detailFolio) {
        detailFolio?.let { query.findByFolio(it) }
    }
    BackHandler(enabled = detailFolio != null) { detailFolio = null }

    Scaffold { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets).imePadding()
                .testTag(if (detailFolio == null) "report_list" else "report_detail"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        ) {
            item {
                TextButton(
                    onClick = { if (detailFolio != null) detailFolio = null else onClose() },
                    modifier = Modifier.testTag("report_back_list"),
                ) { Text(if (detailFolio != null) "Volver a los reportes" else "Volver") }
                Text(
                    if (detailFolio == null) "Reportes en este teléfono" else "Detalle del reporte",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                LocalOnlyNotice()
            }
            if (detailFolio != null) {
                when (val state = queryState) {
                    is ReportQueryState.Found -> {
                        if (state.report.id == detailFolio) {
                            item { ReportDetail(state.report, onMap) }
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
                        onValueChange = { folio = it },
                        label = { Text("Buscar por folio completo") },
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
                    OutlinedButton(
                        onClick = list::load,
                        modifier = Modifier.fillMaxWidth().testTag("report_refresh"),
                    ) { Text(if (listState == ReportListState.Loading) "Reiniciar lectura" else "Actualizar lista") }
                }
                when (val state = listState) {
                    ReportListState.Idle, ReportListState.Loading -> item { LoadingNotice() }
                    is ReportListState.Error -> item {
                        Text(state.message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        Button(onClick = list::load) { Text("Reintentar lectura") }
                    }
                    is ReportListState.Loaded -> {
                        val selectedType = IncidentType.entries.firstOrNull { it.name == typeName }
                        val selectedStatus = ReportStatus.entries.firstOrNull { it.name == statusName }
                        val visible = filterLocalReports(state.reports, selectedType, selectedStatus)
                        item { Text("${visible.size} de ${state.reports.size} reportes locales") }
                        if (state.reports.isEmpty()) {
                            item {
                                Text(
                                    "Todavía no hay reportes guardados en este teléfono.",
                                    modifier = Modifier.testTag("report_no_data"),
                                )
                            }
                        } else if (visible.isEmpty()) {
                            item {
                                Text("No hay reportes con estos filtros.", Modifier.testTag("report_empty_filtered"))
                                TextButton(onClick = { typeName = null; statusName = null }) {
                                    Text("Mostrar todos")
                                }
                            }
                        }
                        items(visible, key = { it.id }) { report ->
                            ReportCard(report, enabled = !queryBusy) {
                                focus.clearFocus()
                                detailFolio = report.id
                            }
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
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
private fun ReportCard(report: Report, enabled: Boolean, onSelect: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("list_card_${report.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(typeLabel(report.type), style = MaterialTheme.typography.titleMedium)
            Text("Prioridad: ${priorityLabel(report.priority)}")
            Text("Estado local: ${statusLabel(report.status)}")
            Text("Incidente: ${formatReportTime(report.occurredAt)}")
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
private fun ReportDetail(report: Report, onMap: (String) -> Unit) {
    val context = LocalContext.current
    var copied by remember(report.id) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailField("Folio", report.id, "report_folio")
            OutlinedButton(
                onClick = {
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
            DetailField("Descripción", report.description, "report_description")
            DetailField("Fecha del incidente", formatReportTime(report.occurredAt))
            DetailField("Guardado en este teléfono", formatReportTime(report.createdAt))
            DetailField("Referencia", report.location.reference.ifBlank { "Sin referencia escrita" })
            val latitude = report.location.latitude
            val longitude = report.location.longitude
            val hasPoint = latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
            if (hasPoint) {
                DetailField("Punto guardado", "$latitude, $longitude")
                Button(onClick = { onMap(report.id) }, modifier = Modifier.testTag("report_detail_map")) {
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

private fun formatReportTime(instant: Instant): String = DateTimeFormatter
    .ofPattern("dd/MM/yyyy HH:mm:ss z").withZone(ZoneId.systemDefault()).format(instant)

private val FULL_FOLIO = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
