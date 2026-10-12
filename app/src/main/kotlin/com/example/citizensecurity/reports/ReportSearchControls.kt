package com.example.citizensecurity.reports

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** Separate list items keep each input/button reachable in landscape and with larger text. */
internal fun LazyListScope.reportSearchControls(
    text: String,
    from: String,
    to: String,
    onText: (String) -> Unit,
    onFrom: (String) -> Unit,
    onTo: (String) -> Unit,
    error: String?,
    onApply: () -> Unit,
    onClear: () -> Unit,
    appliedText: String,
    appliedFrom: String,
    appliedTo: String,
    zoneName: String,
) {
    if (error != null) item(key = "report_search_rejected") {
        Text(
            "$error Se conservan los resultados de la búsqueda aplicada.",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("report_search_error").semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    item {
        Text("Encontrar un incidente", style = MaterialTheme.typography.titleMedium)
        Text("Busca en la descripción o referencia. Las fechas corresponden al incidente; puedes dejar los límites vacíos.")
    }
    item {
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            label = { Text("Descripción o referencia") },
            supportingText = { Text("Hasta 512 caracteres; escribe una palabra o frase.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onApply() }),
            modifier = Modifier.fillMaxWidth().testTag("report_search_text"),
        )
    }
    item {
        SearchDateField(from, onFrom, "Desde", "report_date_from", zoneName, onApply)
    }
    item {
        SearchDateField(to, onTo, "Hasta", "report_date_to", zoneName, onApply)
    }
    item {
        Button(onClick = onApply, modifier = Modifier.fillMaxWidth().testTag("report_apply_search")) {
            Text("Buscar reportes")
        }
    }
    item {
        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth().testTag("report_clear_search")) {
            Text("Limpiar búsqueda")
        }
    }
    item {
        val summary = buildList {
            if (appliedText.isNotEmpty()) add("Texto: $appliedText")
            if (appliedFrom.isNotEmpty()) add("Desde: $appliedFrom")
            if (appliedTo.isNotEmpty()) add("Hasta: $appliedTo")
        }
        Text(
            if (summary.isEmpty()) "Búsqueda aplicada: sin texto ni fechas."
            else "Búsqueda aplicada: ${summary.joinToString(" · ")}. Fechas en $zoneName.",
            modifier = Modifier.testTag("reports_search_summary"),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SearchDateField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    tag: String,
    zoneName: String,
    onApply: () -> Unit,
) {
    val context = LocalContext.current
    var picker by remember { mutableStateOf<DatePickerDialog?>(null) }
    DisposableEffect(context) {
        onDispose { picker?.dismiss(); picker = null }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("$label · dd/MM/yyyy") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onApply() }),
        trailingIcon = {
            TextButton(onClick = {
                val formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT)
                val initial = runCatching {
                    LocalDate.parse(value.trim(), formatter).also { require(it.year >= 1) }
                }
                    .getOrElse { LocalDate.now(ZoneId.of(zoneName)) }
                picker?.dismiss()
                picker = DatePickerDialog(context, { _, year, month, day ->
                    onChange(LocalDate.of(year, month + 1, day).format(formatter))
                }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
                    setTitle("Fecha del incidente · $label")
                    setOnDismissListener { picker = null }
                    show()
                }
            }, modifier = Modifier.testTag("${tag}_calendar").semantics {
                contentDescription = "Elegir fecha ${if (label == "Desde") "inicial" else "final"}"
            }) { Text("Elegir") }
        },
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}
