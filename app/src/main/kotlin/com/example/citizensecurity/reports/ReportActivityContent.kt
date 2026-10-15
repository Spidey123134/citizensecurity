package com.example.citizensecurity.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citizensecurity.account.LocalReportWorkspace
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.ReportActivityEntry
import com.example.citizensecurity.domain.ReportActivityFilter
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportStatusEventKind
import com.example.citizensecurity.report.ReportActivityState
import com.example.citizensecurity.report.ReportActivityViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ReportActivityScreen(model: ReportActivityViewModel,
    onOpen: (ReportActivityEntry) -> Unit, onClose: () -> Unit, onAlerts: () -> Unit = {}) {
    val state = model.state.collectAsStateWithLifecycle().value
    val workspace = LocalReportWorkspace.current
    val zone = remember { ZoneId.systemDefault() }
    val list = rememberLazyListState()
    val query = when (state) {
        is ReportActivityState.Loading -> state.query
        is ReportActivityState.Ready -> state.query
        is ReportActivityState.Error -> state.query
        ReportActivityState.Idle -> null
    }
    val filter = query?.filter ?: ReportActivityFilter.ALL
    val positionedQuery = remember { arrayOf(query) }
    fun action(block: () -> Unit) {
        if (model.state.value !== state) return
        if (workspace != null && runCatching { workspace.checkActive() }.isFailure) return
        block()
    }
    LaunchedEffect(model) { model.activate(filter) }
    LaunchedEffect(state) {
        // StateFlow can conflate a quick Loading/Ready pair. Compare the actual request
        // identity on either state, including a refresh with equal filter/cursor values.
        if (positionedQuery[0] !== query) {
            positionedQuery[0] = query
            list.scrollToItem(0)
        }
    }
    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp)
            .testTag("report_activity"), state = list, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Actividad de reportes", style = MaterialTheme.typography.headlineSmall)
                Text("Guardados y cambios registrados en este teléfono. No confirma recepción o atención por una autoridad.")
                OutlinedButton(onClick = { action(onAlerts) }, modifier = Modifier.fillMaxWidth()
                    .testTag("activity_alerts")) { Text("Avisos de cambios de estado") }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = filter == ReportActivityFilter.ALL,
                        onClick = { action { if (filter != ReportActivityFilter.ALL) model.refresh(ReportActivityFilter.ALL) } },
                        label = { Text("Toda la actividad") }, modifier = Modifier.testTag("activity_all"))
                    FilterChip(selected = filter == ReportActivityFilter.STATUS_CHANGES,
                        onClick = { action { if (filter != ReportActivityFilter.STATUS_CHANGES) model.refresh(ReportActivityFilter.STATUS_CHANGES) } },
                        label = { Text("Cambios de estado") }, modifier = Modifier.testTag("activity_changes"))
                }
            }
            when (state) {
                ReportActivityState.Idle -> Unit
                is ReportActivityState.Loading -> item {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Consultando actividad…", Modifier.testTag("activity_loading"))
                }
                is ReportActivityState.Error -> item {
                    Text("No se pudo consultar la actividad. Tus reportes se conservan.", Modifier.testTag("activity_error"))
                    Button(onClick = { action { model.retry(state.query) } }, modifier = Modifier.testTag("activity_retry")) { Text("Reintentar") }
                }
                is ReportActivityState.Ready -> {
                    if (state.page.entries.isEmpty()) item {
                        Text(if (filter == ReportActivityFilter.STATUS_CHANGES)
                            "No hay cambios de estado registrados en este espacio. Guardar un reporte es un evento distinto."
                        else "No hay actividad registrada en este espacio. Los reportes antiguos pueden no tener un historial de creación.",
                            Modifier.testTag("activity_empty"))
                    }
                    items(state.page.entries, key = { it.event.operationId }) { entry ->
                        Card(Modifier.fillMaxWidth().testTag("activity_event_${entry.event.operationId}")) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (entry.event.kind == ReportStatusEventKind.CREATED) "Guardado en este teléfono" else "Cambio de estado",
                                    style = MaterialTheme.typography.titleMedium)
                                Text(activityType(entry.incidentType))
                                Text("Folio: ${entry.reportId}", style = MaterialTheme.typography.bodySmall)
                                Text(if (entry.event.kind == ReportStatusEventKind.CREATED) "Estado al guardar: ${activityStatus(entry.event.toStatus)}"
                                    else "${activityStatus(checkNotNull(entry.event.fromStatus))} → ${activityStatus(entry.event.toStatus)}")
                                Text("Fecha registrada: ${activityTime(entry.event.recordedAt, zone)} · ${zone.id}", style = MaterialTheme.typography.bodySmall)
                                if (entry.event.reason.isNotEmpty()) Text(entry.event.reason)
                                OutlinedButton(onClick = { action { onOpen(entry) } }, modifier = Modifier.fillMaxWidth()
                                    .testTag("activity_open_${entry.event.operationId}")) { Text("Abrir reporte") }
                            }
                        }
                    }
                    item {
                        Text("Hasta 50 eventos por página, por fecha registrada. Actualiza desde el inicio para comprobar cambios nuevos.", style = MaterialTheme.typography.bodySmall)
                        if (state.page.hasMore) OutlinedButton(onClick = { action { model.next(state.query) } },
                            modifier = Modifier.fillMaxWidth().testTag("activity_next")) { Text("Eventos anteriores") }
                        if (state.canPrevious) OutlinedButton(onClick = { action { model.previous(state.query) } },
                            modifier = Modifier.fillMaxWidth().testTag("activity_previous")) { Text("Página anterior") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { action { model.refresh(filter) } }, enabled = state !is ReportActivityState.Loading,
                    modifier = Modifier.fillMaxWidth().testTag("activity_refresh")) { Text("Actualizar desde el inicio") }
                OutlinedButton(onClick = { action(onClose) }, modifier = Modifier.fillMaxWidth().testTag("activity_close")) { Text("Volver a Inicio") }
            }
        }
    }
}

private fun activityStatus(status: ReportStatus): String = when (status) {
    ReportStatus.REPORTED -> "Reportado"
    ReportStatus.IN_REVIEW -> "En revisión"
    ReportStatus.ATTENDED -> "Atendido"
    ReportStatus.CLOSED -> "Cerrado"
}
private fun activityType(type: IncidentType): String = when (type) {
    IncidentType.EMERGENCY -> "Emergencia"
    IncidentType.THEFT -> "Robo"
    IncidentType.ACCIDENT -> "Accidente"
    IncidentType.FIRE -> "Incendio"
    IncidentType.RISK -> "Riesgo"
    IncidentType.OTHER -> "Otro"
}
private fun activityTime(value: Instant, zone: ZoneId): String = try {
    DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss").withZone(zone).format(value)
} catch (_: RuntimeException) { "Fecha fuera del intervalo representable" }
