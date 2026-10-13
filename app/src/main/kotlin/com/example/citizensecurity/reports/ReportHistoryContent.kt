package com.example.citizensecurity.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citizensecurity.account.LocalReportWorkspace
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportStatusEventKind
import com.example.citizensecurity.report.ReportHistoryState
import com.example.citizensecurity.report.ReportHistoryViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Only records that actually exist; unknown legacy history never becomes invented events. */
@Composable
internal fun ReportHistoryContent(report: Report, zone: ZoneId, history: ReportHistoryViewModel, isCurrentDetail: () -> Boolean) {
    val state = history.state.collectAsStateWithLifecycle().value
    val workspace = LocalReportWorkspace.current
    fun action(perform: () -> Unit) {
        if (!isCurrentDetail() || history.state.value !== state) return
        if (workspace != null && runCatching { workspace.checkActive() }.isFailure) return
        perform()
    }
    val ready = (state as? ReportHistoryState.Ready)?.takeIf { it.query.reportId == report.id }
    val loading = (state as? ReportHistoryState.Loading)?.takeIf { it.query.reportId == report.id }
    val failure = (state as? ReportHistoryState.Error)?.takeIf { it.query.reportId == report.id }
    val missing = (state as? ReportHistoryState.NotFound)?.takeIf { it.query.reportId == report.id }

    Column(Modifier.fillMaxWidth().testTag("report_history"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Historial de estados", style = MaterialTheme.typography.titleMedium)
        Text("Registro local; no confirma atención por una autoridad.", style = MaterialTheme.typography.bodySmall)
        when {
            loading != null -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Consultando historial…", Modifier.testTag("history_loading"))
                OutlinedButton(onClick = { action(history::cancel) }, modifier = Modifier.testTag("history_cancel")) { Text("Cancelar consulta") }
            }
            failure != null -> {
                Text("No se pudo consultar el historial. Se conserva la ficha del reporte.", Modifier.testTag("history_error"))
                Button(onClick = { action(history::reload) }, modifier = Modifier.testTag("history_retry")) { Text("Reintentar historial") }
            }
            missing != null -> {
                Text("Este folio no se encontró al consultar su historial. Consulta nuevamente el reporte.", Modifier.testTag("history_missing"))
                OutlinedButton(onClick = { action { history.load(report.id) } }) { Text("Consultar historial nuevamente") }
            }
            ready != null -> {
                Text("Estado al consultar este historial: ${historyStatus(ready.page.currentStatus)}", Modifier.testTag("history_status"))
                if (ready.page.currentStatus != report.status) Text(
                    "El estado de la ficha y del historial no coinciden. Consulta nuevamente el folio para comprobarlo.",
                    color = MaterialTheme.colorScheme.error,
                )
                if (!ready.page.completeFromCreation) Text(
                    "No hay un registro completo desde su creación. No conocemos todos los cambios anteriores.",
                    Modifier.testTag("history_incomplete"),
                )
                if (ready.page.events.isEmpty()) Text("No hay eventos registrados en esta página.", Modifier.testTag("history_empty"))
                ready.page.events.forEach { event ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("history_event_${event.sequence}")) {
                        Text(if (event.kind == ReportStatusEventKind.CREATED) "Reporte guardado · ${historyStatus(event.toStatus)}"
                            else "${historyStatus(checkNotNull(event.fromStatus))} → ${historyStatus(event.toStatus)}")
                        Text(historyTime(event.recordedAt, zone), style = MaterialTheme.typography.bodySmall)
                        event.actorAccountId?.let { actor ->
                            Text("Cuenta local: ${workspace?.session?.account?.takeIf { it.id == actor }?.username ?: actor}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (event.reason.isNotEmpty()) Text(event.reason)
                    }
                }
                Text("Orden de registro: eventos más recientes primero.", style = MaterialTheme.typography.bodySmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ready.page.hasMore) OutlinedButton(onClick = { action(history::nextPage) }, modifier = Modifier.testTag("history_more")) { Text("Eventos anteriores") }
                    if (ready.query.beforeSequence != null) OutlinedButton(onClick = { action(history::firstPage) }, modifier = Modifier.testTag("history_first")) { Text("Volver al inicio") }
                }
                OutlinedButton(onClick = { action(history::firstPage) }, modifier = Modifier.testTag("history_refresh")) { Text("Actualizar historial") }
            }
            else -> Button(onClick = { action { history.load(report.id) } }, modifier = Modifier.testTag("history_open")) { Text("Consultar historial") }
        }
    }
}

private fun historyStatus(status: ReportStatus): String = when (status) {
    ReportStatus.REPORTED -> "Reportado"
    ReportStatus.IN_REVIEW -> "En revisión"
    ReportStatus.ATTENDED -> "Atendido"
    ReportStatus.CLOSED -> "Cerrado"
}

private fun historyTime(value: java.time.Instant, zone: ZoneId): String = try {
    DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss").withZone(zone).format(value)
} catch (_: RuntimeException) { "Fecha fuera del intervalo representable" }
