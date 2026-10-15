package com.example.citizensecurity.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ReportMessageScreen(model: ReportMessageViewModel,
    onSelect: (ReportMessageState.Choose, String?) -> Unit,
    onLocation: (ReportMessageState.Choose, Boolean) -> Unit,
    onPrepare: (ReportMessageState.Choose) -> Unit, onEdit: (ReportMessageState.Review) -> Unit,
    onOpen: (ReportMessageState.Review) -> Unit, onReload: () -> Unit,
    onContacts: () -> Unit, onClose: () -> Unit) {
    val state = model.state.collectAsStateWithLifecycle().value
    fun action(block: () -> Unit) { if (model.state.value === state) block() }
    val busy = state is ReportMessageState.Loading || state is ReportMessageState.Preparing || state is ReportMessageState.Opening
    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp).testTag("message_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Mensaje para un contacto", style = MaterialTheme.typography.headlineSmall)
                Text("Elige a quién y revisa el texto antes de abrir Mensajes SMS. El envío lo decides en esa aplicación.")
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Comprobando reporte y contacto…") }
            when (state) {
                ReportMessageState.Idle, is ReportMessageState.Loading, is ReportMessageState.Preparing,
                is ReportMessageState.Opening -> Unit
                is ReportMessageState.Error -> item { Text(state.message, Modifier.testTag("message_error")) }
                is ReportMessageState.Choose -> {
                    item {
                        Text("Folio: ${state.report.id}")
                        Text("Elige un contacto guardado", style = MaterialTheme.typography.titleMedium)
                        state.message?.let { Text(it, Modifier.testTag("message_notice")) }
                        if (state.contacts.contacts.isEmpty()) Text("Todavía no tienes contactos guardados para este acceso.",
                            Modifier.testTag("message_empty_contacts"))
                    }
                    items(state.contacts.contacts, key = { it.id }) { contact ->
                        Row(Modifier.fillMaxWidth()) {
                            RadioButton(selected = state.selectedContactId == contact.id,
                                onClick = { action { onSelect(state, contact.id) } },
                                modifier = Modifier.testTag("message_contact_${contact.id}"))
                            Column { Text(contact.name); Text(contact.phoneNumber) }
                        }
                    }
                    item {
                        Row {
                            Checkbox(checked = state.includeLocation,
                                onCheckedChange = { action { onLocation(state, it) } },
                                modifier = Modifier.testTag("message_location"))
                            Text("Incluir referencia y punto guardados")
                        }
                        Text("Revisa también la descripción: puede contener datos personales o un lugar escrito por ti.",
                            style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { action { onPrepare(state) } }, enabled = state.selectedContactId != null,
                            modifier = Modifier.fillMaxWidth().testTag("message_prepare")) { Text("Revisar mensaje") }
                    }
                }
                is ReportMessageState.Review -> {
                    item { MessageReview(state) }
                    item {
                        Button(onClick = { action { onOpen(state) } }, modifier = Modifier.fillMaxWidth().testTag("message_open")) {
                            Text("Abrir Mensajes SMS")
                        }
                        OutlinedButton(onClick = { action { onEdit(state) } }, modifier = Modifier.fillMaxWidth()
                            .testTag("message_edit")) { Text("Cambiar contacto u opciones") }
                    }
                }
                is ReportMessageState.Opened -> {
                    item {
                        Text("Se abrió la composición. Esto no confirma que el mensaje se haya enviado o recibido.",
                            Modifier.testTag("message_opened"))
                        MessageReview(state.review)
                    }
                    item {
                        OutlinedButton(onClick = { action { onEdit(state.review) } }, modifier = Modifier.fillMaxWidth()
                            .testTag("message_edit")) { Text("Preparar otro mensaje") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { action(onContacts) }, enabled = !busy, modifier = Modifier.fillMaxWidth()
                    .testTag("message_contacts")) { Text("Administrar contactos de confianza") }
                OutlinedButton(onClick = { action(onReload) }, enabled = !busy, modifier = Modifier.fillMaxWidth()
                    .testTag("message_reload")) { Text("Actualizar reporte y contactos") }
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("message_close")) { Text("Volver") }
            }
        }
    }
}

@Composable
private fun MessageReview(review: ReportMessageState.Review) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Para: ${review.draft.contact.name}")
        Text(review.draft.contact.phoneNumber, Modifier.testTag("message_target"))
        Text(if (review.includeLocation) "Referencia y punto: incluidos si están guardados." else "Referencia y punto: omitidos.")
        review.message?.let { Text(it, Modifier.testTag("message_notice")) }
        SelectionContainer { Text(review.draft.body, Modifier.testTag("message_body")) }
        Text("Revisa número y texto en la app de mensajes; algunas apps pueden no conservar el texto. " +
            "Tu operador puede cobrar el envío, especialmente si el mensaje es largo.", style = MaterialTheme.typography.bodySmall)
        Text("Para compartir la fotografía, prepara la ficha PDF desde el detalle y elige incluirla.", style = MaterialTheme.typography.bodySmall)
    }
}
