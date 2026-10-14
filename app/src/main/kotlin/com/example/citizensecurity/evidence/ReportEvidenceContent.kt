package com.example.citizensecurity.evidence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import com.example.citizensecurity.account.LocalReportWorkspace
import com.example.citizensecurity.domain.Report
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ReportEvidenceContent(
    report: Report,
    zone: ZoneId,
    model: ReportEvidenceViewModel,
    isCurrentDetail: () -> Boolean,
    onShare: (ReportEvidenceState.Ready, () -> Boolean) -> Unit,
) {
    val workspace = LocalReportWorkspace.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by model.state.collectAsStateWithLifecycle()
    var optionsVisible by remember(report.id) { mutableStateOf(false) }
    var includeLocation by remember(report.id) { mutableStateOf(true) }
    var includePhoto by remember(report.id) { mutableStateOf(false) }
    fun current(): Boolean = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && isCurrentDetail() &&
        runCatching { workspace?.checkActive(); true }.getOrDefault(false)
    val request = state.evidenceRequest()
    val shown = if (request == null || request.reportId == report.id) state else ReportEvidenceState.Idle
    val busy = shown is ReportEvidenceState.Loading || shown is ReportEvidenceState.Sharing
    fun openOptions() {
        if (!current() || model.state.value !== state || busy) return
        includeLocation = request?.options?.includeLocation ?: true
        includePhoto = report.photo != null && (request?.options?.includePhoto ?: false)
        optionsVisible = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ficha del incidente", style = MaterialTheme.typography.titleLarge)
        Text("Conserva una copia PDF con folio, descripción y fechas para compartirla con quien elijas. Son datos registrados por el autor; no acredita que una autoridad haya recibido el reporte.")
        when (val value = shown) {
            ReportEvidenceState.Idle -> Unit
            is ReportEvidenceState.Loading -> {
                CircularProgressIndicator()
                Text("Preparando ficha…", Modifier.testTag("evidence_loading"))
                TextButton(onClick = { if (current() && model.state.value === value) model.cancel() }) {
                    Text("Cancelar preparación")
                }
            }
            is ReportEvidenceState.Error -> Text(value.message, Modifier.testTag("evidence_error")
                .semantics { liveRegion = LiveRegionMode.Polite })
            is ReportEvidenceState.Ready -> {
                Text("PDF preparado · ${value.artifact.pageCount} página(s) · ${DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss z").withZone(zone).format(value.artifact.createdAt)}",
                    Modifier.testTag("evidence_ready"))
                Text("Punto y referencia: ${if (value.request.options.includeLocation) "incluidos" else "omitidos"}. Fotografía: ${if (value.request.options.includePhoto) "incluida" else "omitida"}.")
                value.message?.let { Text(it, Modifier.testTag("evidence_share_error")) }
                Button(onClick = {
                    if (current() && model.state.value === value) onShare(value, isCurrentDetail)
                }, modifier = Modifier.testTag("evidence_share")) { Text("Compartir PDF") }
            }
            is ReportEvidenceState.Sharing -> {
                CircularProgressIndicator()
                Text("Comprobando la ficha antes de abrir el selector…")
            }
            is ReportEvidenceState.ChooserOpened -> Text("Se abrió el selector para compartir. La app no puede confirmar que el destinatario recibió el archivo.",
                Modifier.testTag("evidence_chooser_opened"))
        }
        if (!busy) OutlinedButton(onClick = ::openOptions, modifier = Modifier.testTag("evidence_prepare")) {
            Text(if (shown is ReportEvidenceState.Ready || shown is ReportEvidenceState.ChooserOpened) "Preparar otra ficha" else "Preparar ficha PDF")
        }
    }
    if (optionsVisible && current() && !busy) AlertDialog(
        onDismissRequest = { optionsVisible = false },
        title = { Text("Contenido de la ficha") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Incluye folio, tipo, prioridad indicada, estado local, descripción y fechas. Revisa la descripción si contiene datos que prefieres no compartir.")
                Row {
                    Checkbox(includeLocation, { if (current()) includeLocation = it }, Modifier.testTag("evidence_location"))
                    Text("Incluir referencia y punto guardado")
                }
                Row {
                    Checkbox(includePhoto, { if (current()) includePhoto = it }, Modifier.testTag("evidence_photo"), enabled = report.photo != null)
                    Text(if (report.photo == null) "Sin fotografía guardada" else "Incluir fotografía guardada")
                }
                Text("Omitir el punto no oculta lugares mencionados en la descripción o visibles en la foto. La foto es una copia normalizada por la app, no el archivo original. Al compartir, la otra aplicación puede conservar su copia.")
                Text("Preparar el PDF no lo envía. Después elegirás con quién compartirlo.")
            }
        },
        confirmButton = { TextButton(onClick = {
            if (current() && model.state.value === state) {
                model.prepare(ReportEvidenceRequest(report.id, ReportEvidenceOptions(includeLocation, includePhoto), zone))
                optionsVisible = false
            }
        }, modifier = Modifier.testTag("evidence_confirm")) { Text("Preparar PDF") } },
        dismissButton = { TextButton(onClick = { optionsVisible = false }) { Text("Cancelar") } },
    )
}

internal fun ReportEvidenceState.evidenceRequest(): ReportEvidenceRequest? = when (this) {
    ReportEvidenceState.Idle -> null
    is ReportEvidenceState.Loading -> request
    is ReportEvidenceState.Ready -> request
    is ReportEvidenceState.Error -> request
    is ReportEvidenceState.Sharing -> request
    is ReportEvidenceState.ChooserOpened -> request
}
