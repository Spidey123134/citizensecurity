package com.example.citizensecurity.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportHistoryPage
import com.example.citizensecurity.domain.ReportListPageQuery
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportStatusEventKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Read-only consultation. Every gesture is bound to the state that rendered its control. */
@Composable
internal fun AdministrationConsultationScreen(
    model: AdministrationConsultationViewModel,
    onClose: () -> Unit,
    onChangeStatus: ((String?) -> Unit)? = null,
) {
    val state = model.state.collectAsStateWithLifecycle().value
    val list = rememberLazyListState()
    val zone = remember { ZoneId.systemDefault() }
    val focus = LocalFocusManager.current
    // A typed folio is neither persisted in saved instance state nor placed in an Intent.
    var folio by remember(model) { mutableStateOf("") }
    val request = when (state) {
        is AdministrationConsultationState.Loading -> state.request
        is AdministrationConsultationState.ReadyPage -> state.request
        is AdministrationConsultationState.ReadyReport -> state.request
        is AdministrationConsultationState.Error -> state.request
        is AdministrationConsultationState.Missing -> state.request
        else -> null
    }
    val pageQuery = (request as? AdministrationPageRequest)?.query
    val positionedRequest = remember(model) { arrayOf<Any?>(null) }
    fun action(block: () -> Unit) {
        if (model.state.value !== state) return
        block()
    }
    fun find() = action {
        focus.clearFocus()
        model.loadFolio(folio, state)
    }
    LaunchedEffect(model) { model.activate() }
    LaunchedEffect(state) {
        if (state === AdministrationConsultationState.Denied) {
            folio = ""
            focus.clearFocus()
        }
        // Compare request identity even if StateFlow conflates a fast Loading/Ready pair.
        if (positionedRequest[0] !== request) {
            positionedRequest[0] = request
            list.scrollToItem(0)
        }
    }
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp)
                .testTag("admin_consult_screen"),
            state = list,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Administración local", style = MaterialTheme.typography.headlineSmall)
                    Text("Consulta de reportes del espacio de la cuenta actual en este teléfono. No es una central policial ni confirma atención institucional.")
                }
            }
            if (state === AdministrationConsultationState.Denied) {
                item {
                    Text("No hay permiso vigente para consultar este espacio. Vuelve a Inicio para revisar tu sesión.",
                        modifier = Modifier.testTag("admin_consult_denied").semantics { liveRegion = LiveRegionMode.Polite })
                }
            } else {
                if (onChangeStatus != null) item {
                    OutlinedButton(onClick = { action { onChangeStatus(null) } },
                        modifier = Modifier.fillMaxWidth().testTag("admin_consult_recover_change")) {
                        Text("Comprobar cambio pendiente")
                    }
                }
                val browsing = state !is AdministrationConsultationState.ReadyReport &&
                    request !is AdministrationReportRequest
                if (browsing && state !is AdministrationConsultationState.Idle) {
                    consultationControls(
                        query = pageQuery ?: ReportListPageQuery(limit = 50),
                        enabled = state !is AdministrationConsultationState.Loading,
                        folio = folio,
                        onFolio = { value -> action { folio = value } },
                        onFind = ::find,
                        onFilters = { type, status, priority -> action { model.setFilters(type, status, priority, state) } },
                        onRefresh = { action { model.refresh(state) } },
                    )
                }
                when (state) {
                    AdministrationConsultationState.Idle -> item {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Comprobando el acceso…", Modifier.testTag("admin_consult_loading"))
                    }
                    is AdministrationConsultationState.Loading -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Consultando registros locales…", Modifier.testTag("admin_consult_loading"))
                            if (state.request is AdministrationReportRequest) OutlinedButton(
                                onClick = { action { model.backToList(state) } },
                                modifier = Modifier.fillMaxWidth().testTag("admin_consult_back_list"),
                            ) { Text("Volver al listado") }
                        }
                    }
                    AdministrationConsultationState.Invalid -> item {
                        Text("El folio debe tener el formato completo que recibiste al guardar el reporte.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("admin_consult_invalid").semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    is AdministrationConsultationState.Missing -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("El folio no se encontró en este espacio. No se buscan reportes de otras cuentas.",
                                modifier = Modifier.testTag("admin_consult_missing").semantics { liveRegion = LiveRegionMode.Polite })
                            OutlinedButton(onClick = { action { model.backToList(state) } }, modifier = Modifier.fillMaxWidth()
                                .testTag("admin_consult_back_list")) { Text("Volver al listado") }
                        }
                    }
                    is AdministrationConsultationState.Error -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No se pudo completar la consulta. Los reportes guardados se conservan.",
                                modifier = Modifier.testTag("admin_consult_error").semantics { liveRegion = LiveRegionMode.Polite })
                            Button(onClick = { action { model.retry(state) } }, modifier = Modifier.fillMaxWidth()
                                .testTag("admin_consult_retry")) { Text("Reintentar esta consulta") }
                            if (state.request is AdministrationReportRequest) OutlinedButton(
                                onClick = { action { model.backToList(state) } },
                                modifier = Modifier.fillMaxWidth().testTag("admin_consult_back_list"),
                            ) { Text("Volver al listado") }
                        }
                    }
                    is AdministrationConsultationState.ReadyPage -> {
                        item {
                            Text("${state.page.reports.size} reportes en esta página · guardados más recientes primero.",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("admin_consult_page_summary"))
                        }
                        if (state.page.reports.isEmpty()) item {
                            Text("No hay reportes que coincidan con los filtros en esta página.", Modifier.testTag("admin_consult_empty"))
                        }
                        items(state.page.reports, key = { it.id }) { report ->
                            ConsultationReportCard(report, zone, onOpen = { action { model.openReport(state, report.id) } })
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.page.hasMore) OutlinedButton(onClick = { action { model.nextPage(state) } },
                                    modifier = Modifier.fillMaxWidth().testTag("admin_consult_next")) { Text("Página siguiente") }
                                if (state.canPrevious) OutlinedButton(onClick = { action { model.previousPage(state) } },
                                    modifier = Modifier.fillMaxWidth().testTag("admin_consult_previous")) { Text("Página anterior") }
                                if (state.canPrevious) OutlinedButton(onClick = { action { model.refresh(state) } },
                                    modifier = Modifier.fillMaxWidth().testTag("admin_consult_first")) { Text("Volver a la primera página") }
                            }
                        }
                    }
                    is AdministrationConsultationState.ReadyReport -> {
                        item { ConsultationDetail(state.report, zone) }
                        if (onChangeStatus != null && state.report.status != ReportStatus.CLOSED) item {
                            Button(onClick = { action { onChangeStatus(state.report.id) } },
                                modifier = Modifier.fillMaxWidth().testTag("admin_consult_change_status")) {
                                Text("Preparar cambio de estado")
                            }
                        }
                        consultationHistory(state.history, state.report, zone)
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (state.history.hasMore) OutlinedButton(onClick = { action { model.nextHistory(state) } },
                                    modifier = Modifier.fillMaxWidth().testTag("admin_consult_history_next")) { Text("Eventos anteriores") }
                                if (state.canPreviousHistory) OutlinedButton(onClick = { action { model.previousHistory(state) } },
                                    modifier = Modifier.fillMaxWidth().testTag("admin_consult_history_previous")) { Text("Página de eventos anterior") }
                                OutlinedButton(onClick = { action { model.refresh(state) } },
                                    modifier = Modifier.fillMaxWidth().testTag("admin_consult_history_first")) { Text("Actualizar historial desde el inicio") }
                                OutlinedButton(onClick = { action { model.backToList(state) } },
                                    modifier = Modifier.fillMaxWidth().testTag("admin_consult_back_list")) { Text("Volver al listado") }
                            }
                        }
                    }
                    AdministrationConsultationState.Denied -> Unit
                }
            }
            item {
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                    .testTag("admin_consult_close")) { Text("Volver a Inicio") }
            }
        }
    }
}

private fun LazyListScope.consultationControls(
    query: ReportListPageQuery,
    enabled: Boolean,
    folio: String,
    onFolio: (String) -> Unit,
    onFind: () -> Unit,
    onFilters: (IncidentType?, ReportStatus?, Priority?) -> Unit,
    onRefresh: () -> Unit,
) {
    item {
        Text("Filtrar el listado", style = MaterialTheme.typography.titleMedium)
        Text("Los filtros consultan solo este espacio. Cada cambio vuelve a la primera página.", style = MaterialTheme.typography.bodySmall)
    }
    item {
        ConsultationFilter("Tipo", query.type, IncidentType.entries, "Todos los tipos", "type", enabled,
            ::consultationType) { onFilters(it, query.status, query.priority) }
    }
    item {
        ConsultationFilter("Estado", query.status, ReportStatus.entries, "Todos los estados", "status", enabled,
            ::consultationStatus) { onFilters(query.type, it, query.priority) }
    }
    item {
        ConsultationFilter("Prioridad", query.priority, Priority.entries, "Todas las prioridades", "priority", enabled,
            ::consultationPriority) { onFilters(query.type, query.status, it) }
    }
    item {
        OutlinedButton(onClick = onRefresh, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("admin_consult_refresh")) { Text("Actualizar listado desde el inicio") }
    }
    item {
        Text("Consultar un folio", style = MaterialTheme.typography.titleMedium)
        Text("El folio se busca directamente en este espacio, independientemente de los filtros del listado.", style = MaterialTheme.typography.bodySmall)
    }
    item {
        OutlinedTextField(value = folio, onValueChange = onFolio,
            enabled = enabled, label = { Text("Folio completo del reporte") },
            supportingText = { Text("Copia el identificador que recibiste al guardarlo.") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (enabled) onFind() }),
            modifier = Modifier.fillMaxWidth().testTag("admin_consult_folio"))
    }
    item {
        Button(onClick = onFind, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("admin_consult_find")) { Text("Consultar folio") }
    }
    item {
        OutlinedButton(onClick = { onFolio("") }, enabled = enabled && folio.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().testTag("admin_consult_clear_folio")) { Text("Limpiar folio escrito") }
    }
}

@Composable
private fun <T : Enum<T>> ConsultationFilter(
    title: String,
    selected: T?,
    choices: List<T>,
    all: String,
    tag: String,
    enabled: Boolean,
    label: (T) -> String,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) expanded = false }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().testTag("admin_consult_$tag")) {
            Text("$title: ${selected?.let(label) ?: all}")
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp)) {
            DropdownMenuItem(text = { Text(all) }, onClick = { expanded = false; onSelect(null) },
                modifier = Modifier.testTag("admin_consult_${tag}_ALL"))
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(label(choice)) },
                    onClick = { expanded = false; onSelect(choice) },
                    modifier = Modifier.testTag("admin_consult_${tag}_${choice.name}"))
            }
        }
    }
}

@Composable
private fun ConsultationReportCard(report: Report, zone: ZoneId, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("admin_consult_report_${report.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(consultationType(report.type), style = MaterialTheme.typography.titleMedium)
            Text("Estado: ${consultationStatus(report.status)} · Prioridad: ${consultationPriority(report.priority)}")
            Text("Folio: ${report.id}", style = MaterialTheme.typography.bodySmall)
            Text("Incidente: ${consultationTime(report.occurredAt, zone)} · ${zone.id}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()
                .testTag("admin_consult_open_${report.id}")) { Text("Consultar datos e historial") }
        }
    }
}

@Composable
private fun ConsultationDetail(report: Report, zone: ZoneId) {
    Card(Modifier.fillMaxWidth().testTag("admin_consult_detail")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Datos del reporte", style = MaterialTheme.typography.titleLarge)
            Text("Folio: ${report.id}")
            Text("Tipo: ${consultationType(report.type)}")
            Text("Prioridad: ${consultationPriority(report.priority)}")
            Text("Estado: ${consultationStatus(report.status)}", Modifier.testTag("admin_consult_detail_status"))
            Text("Descripción", style = MaterialTheme.typography.titleMedium)
            Text(report.description)
            Text("Fecha y lugar", style = MaterialTheme.typography.titleMedium)
            Text("Incidente: ${consultationTime(report.occurredAt, zone)}")
            Text("Guardado: ${consultationTime(report.createdAt, zone)} · ${zone.id}")
            Text(if (report.location.reference.isBlank()) "Sin referencia escrita." else "Referencia: ${report.location.reference}")
            val latitude = report.location.latitude
            val longitude = report.location.longitude
            Text(if (latitude != null && longitude != null) "Punto registrado: $latitude, $longitude" else "Sin punto registrado.")
            Text(if (report.photo == null) "Sin fotografía adjunta." else "Con fotografía adjunta; esta consulta muestra únicamente sus datos e historial.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun LazyListScope.consultationHistory(history: ReportHistoryPage, report: Report, zone: ZoneId) {
    item {
        Column(Modifier.testTag("admin_consult_history"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Historial de estados", style = MaterialTheme.typography.titleLarge)
            Text("Eventos registrados en este teléfono, más recientes primero. No confirma recepción ni atención de una autoridad.")
            Text("Estado al consultar: ${consultationStatus(history.currentStatus)}")
            if (history.currentStatus != report.status) Text("El estado de la ficha y del historial no coinciden. Actualiza la consulta para comprobarlo.",
                color = MaterialTheme.colorScheme.error)
            if (!history.completeFromCreation) Text("El historial no está completo desde la creación. No se conocen los cambios anteriores.",
                Modifier.testTag("admin_consult_history_incomplete"))
            if (history.events.isEmpty()) Text("No hay eventos registrados en esta página.", Modifier.testTag("admin_consult_history_empty"))
        }
    }
    items(history.events, key = { it.operationId }) { event ->
        Card(Modifier.fillMaxWidth().testTag("admin_consult_history_event_${event.sequence}")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (event.kind == ReportStatusEventKind.CREATED) "Guardado local · ${consultationStatus(event.toStatus)}"
                    else "${consultationStatus(checkNotNull(event.fromStatus))} → ${consultationStatus(event.toStatus)}",
                    style = MaterialTheme.typography.titleMedium)
                Text("Registro: ${consultationTime(event.recordedAt, zone)} · ${zone.id}")
                event.actorAccountId?.let { Text("Cuenta local que registró el evento: $it", style = MaterialTheme.typography.bodySmall) }
                if (event.reason.isNotEmpty()) Text(event.reason)
            }
        }
    }
}

private fun consultationType(type: IncidentType): String = when (type) {
    IncidentType.EMERGENCY -> "Emergencia"
    IncidentType.THEFT -> "Robo"
    IncidentType.ACCIDENT -> "Accidente"
    IncidentType.FIRE -> "Incendio"
    IncidentType.RISK -> "Riesgo"
    IncidentType.OTHER -> "Otro"
}

private fun consultationPriority(priority: Priority): String = when (priority) {
    Priority.LOW -> "Baja"
    Priority.MEDIUM -> "Media"
    Priority.HIGH -> "Alta"
}

private fun consultationStatus(status: ReportStatus): String = when (status) {
    ReportStatus.REPORTED -> "Reportado"
    ReportStatus.IN_REVIEW -> "En revisión"
    ReportStatus.ATTENDED -> "Atendido"
    ReportStatus.CLOSED -> "Cerrado"
}

private fun consultationTime(time: Instant, zone: ZoneId): String = try {
    DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss").withZone(zone).format(time)
} catch (_: RuntimeException) { "Fecha fuera del intervalo representable" }
