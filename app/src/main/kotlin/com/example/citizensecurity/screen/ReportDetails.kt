package com.example.citizensecurity.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun ReportDetails(example: LocationExample, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val validation = remember(example) { example.validate() }
    val colors = MaterialTheme.colorScheme
    val cardColors = if (validation.isValid) {
        CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = colors.errorContainer, contentColor = colors.onErrorContainer)
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("Volver a inicio") }
        Text(
            example.title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Latitud: ${example.location.latitude ?: "Sin proporcionar"}")
            Text("Longitud: ${example.location.longitude ?: "Sin proporcionar"}")
        }
        Card(modifier = Modifier.fillMaxWidth(), colors = cardColors) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (validation.isValid) "Reporte válido" else "Reporte con errores",
                    style = MaterialTheme.typography.titleMedium,
                )
                validation.errors.values.forEach { message -> Text(message) }
            }
        }
    }
}
