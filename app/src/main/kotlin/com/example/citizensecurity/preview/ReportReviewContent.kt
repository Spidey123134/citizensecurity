package com.example.citizensecurity.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.citizensecurity.R
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.report.ReportReviewSnapshot
import java.time.ZoneId

/** Resumen de lectura del borrador revisado; sus acciones no abren GPS, permisos ni SQLite. */
@Composable
internal fun ReportReviewContent(
    draft: NewReport,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    actions: @Composable () -> Unit = {},
    canEdit: Boolean = true,
) {
    val locale = LocalConfiguration.current.locales[0]
    val timeZone = ZoneId.systemDefault()
    val review = remember(draft, locale, timeZone) { ReportReviewSnapshot.from(draft, locale, timeZone) }
    val types = stringArrayResource(R.array.tools_types)
    val priorities = stringArrayResource(R.array.tools_priorities)
    Column(
        modifier = modifier.fillMaxSize().testTag("review_summary")
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Revisa tu borrador", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Text("Comprueba los datos antes de guardar. El reporte se conserva solo en este teléfono; no se envía a autoridades ni avisa a emergencias.",
                modifier = Modifier.padding(16.dp))
        }
        ReviewField("Tipo de incidente", types[review.type.ordinal], "review_type")
        ReviewField("Prioridad indicada por ti", priorities[review.priority.ordinal], "review_priority")
        ReviewField("Descripción de lo ocurrido", review.description, "review_description")
        ReviewField("Fecha y hora del incidente", "${review.occurredAtText}\nZona horaria: ${review.timeZoneText}", "review_occurred_at")
        HorizontalDivider()
        Text("Lugar del incidente", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() })
        ReviewField("Dirección o referencia", review.locationReference.ifBlank { "Sin referencia escrita." }, "review_reference")
        if (review.latitudeText != null && review.longitudeText != null) {
            ReviewField("Latitud del punto", review.latitudeText, "review_latitude")
            ReviewField("Longitud del punto", review.longitudeText, "review_longitude")
            Text("Estas coordenadas describen el incidente. Revisar los campos no renueva el GPS ni acredita cercanía actual.",
                style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Sin punto seleccionado en el mapa. El lugar se describe con la referencia escrita.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("review_no_coordinates"))
        }
        HorizontalDivider()
        Button(onClick = onEdit, enabled = canEdit,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("edit_review")) {
            Text("Corregir datos")
        }
        actions()
    }
}

@Composable
private fun ReviewField(label: String, value: String, tag: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(tag))
    }
}
