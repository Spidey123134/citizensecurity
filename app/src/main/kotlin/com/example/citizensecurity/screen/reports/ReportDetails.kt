package com.example.citizensecurity.screen.reports

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.evidence.ReportEvidenceContent
import com.example.citizensecurity.evidence.ReportEvidenceState
import com.example.citizensecurity.evidence.ReportEvidenceViewModel
import com.example.citizensecurity.report.ReportHistoryViewModel
import com.example.citizensecurity.reports.ReportHistoryContent
import com.example.citizensecurity.resolution.ReportResolutionContent
import com.example.citizensecurity.resolution.ReportResolutionViewModel
import java.time.ZoneId

@Composable
internal fun ReportDetail(report: Report, zone: ZoneId, onMap: (String) -> Unit, history: ReportHistoryViewModel?,
    evidence: ReportEvidenceViewModel?, onShareEvidence: (ReportEvidenceState.Ready, () -> Boolean) -> Unit,
    onMessage: ((String) -> Unit)?,
    resolution: ReportResolutionViewModel?,
    isCurrent: () -> Boolean) {
    val context = LocalContext.current
    var copied by remember(report.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
