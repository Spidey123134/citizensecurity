package com.example.citizensecurity.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import com.example.citizensecurity.R
import com.example.citizensecurity.report.ReportSimilarityState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** El aviso orienta la decisión; no confirma veracidad ni sustituye el guard de GPS. */
@Composable
internal fun SimilarReportWarning(
    state: ReportSimilarityState,
    canContinue: Boolean,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    onOpenReport: (String) -> Unit,
) {
    val ready = state as? ReportSimilarityState.Ready
    val failed = state as? ReportSimilarityState.Failed
    if (failed == null && (ready == null || (ready.page.matches.isEmpty() && !ready.page.searchIncomplete))) return
    val locale = LocalConfiguration.current.locales[0]
    val types = stringArrayResource(R.array.tools_types)
    AlertDialog(
        modifier = Modifier.testTag("similar_warning"),
        onDismissRequest = onDismiss,
        title = { Text(if (failed != null) "No pudimos revisar reportes parecidos" else "Revisa estos reportes parecidos") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Buscamos en este teléfono incidentes del mismo tipo, a 200 m del punto y hasta 2 horas antes o después de la hora indicada.")
                if (failed != null) {
                    Text(failed.message, Modifier.testTag("similar_failure"))
                    Text("Puedes volver a intentarlo o continuar. El guardado seguirá comprobando tu ubicación y el límite de 5 km.")
                } else {
                    Text("La cercanía no demuestra que sea el mismo incidente. Revisa el folio antes de repetirlo.")
                    ready!!.page.matches.forEach { match ->
                        val report = match.report
                        HorizontalDivider()
                        Text(types[report.type.ordinal], style = MaterialTheme.typography.titleSmall)
                        Text("Aproximadamente ${match.distanceMeters.roundToInt()} m del punto")
                        Text(runCatching { DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale)
                            .withZone(ZoneId.systemDefault()).format(report.occurredAt) }.getOrElse { report.occurredAt.toString() })
                        Text(report.location.reference.take(160))
                        Text(report.id, Modifier.testTag("similar_folio_${report.id}"), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { onOpenReport(report.id) }, modifier = Modifier.testTag("open_similar_${report.id}")) {
                            Text("Ver reporte guardado")
                        }
                    }
                    if (ready.page.searchIncomplete) Text("La búsqueda es limitada; podría haber más coincidencias.", Modifier.testTag("similar_incomplete"))
                }
            }
        },
        confirmButton = { TextButton(onClick = onContinue, enabled = canContinue,
            modifier = Modifier.testTag("confirm_distinct_report")) {
            Text(if (failed != null) "Continuar con el guardado" else "Es otro incidente, guardar")
        } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel_similar_save")) { Text("Volver a revisar") } },
    )
}
