package com.example.citizensecurity.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.citizensecurity.domain.TrustedContact

@Composable
internal fun HelpContactsScreen(
    model: HelpContactsViewModel,
    dialMessage: String?,
    academic: Boolean,
    onEmergencyDial: (String) -> Unit,
    onContactDial: (TrustedContact, HelpContactsState.Ready) -> Unit,
    onClose: () -> Unit,
    checkGesture: () -> Boolean,
) {
    val state by model.state.collectAsState()
    val draft by model.draft.collectAsState()
    var deletion by remember { mutableStateOf<Pair<TrustedContact, HelpContactsState.Ready>?>(null) }
    LaunchedEffect(model) { if (model.state.value is HelpContactsState.Idle) model.load() }
    LaunchedEffect(state) { if (deletion?.second !== state) deletion = null }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ayuda y contactos", style = MaterialTheme.typography.headlineSmall)
        Text("Si hay peligro inmediato, llama al 911. Abrir el marcador no necesita GPS ni guardar un reporte.")
        Button(modifier = Modifier.fillMaxWidth().testTag("help_dial_911"), onClick = {
            if (checkGesture()) onEmergencyDial("911")
        }) { Text("Abrir marcador · 911 emergencias") }
        OutlinedButton(modifier = Modifier.fillMaxWidth().testTag("help_dial_089"), onClick = {
            if (checkGesture()) onEmergencyDial("089")
        }) { Text("Abrir marcador · 089 denuncia anónima") }
        Text("911: emergencias. 089: denuncia anónima. Fuente: Secretaría de Seguridad y Protección Ciudadana de México.",
            style = MaterialTheme.typography.bodySmall)
        Text("Proyecto académico: los reportes se guardan en este teléfono. La app no envía avisos a policías ni servicios de emergencia.",
            style = MaterialTheme.typography.bodySmall)
        dialMessage?.let { Text(it, Modifier.testTag("help_dial_message")) }
        HorizontalDivider()
        Text("Tus contactos de confianza", style = MaterialTheme.typography.titleLarge)
        Text("Agrega hasta 5 teléfonos manualmente. Se guardan solo en este espacio de la app; no se lee tu agenda.")
        if (academic) Text("Este es el espacio académico: sus contactos se comparten entre quienes usen ese acceso en este teléfono. Usa tu cuenta local para un espacio propio.",
            style = MaterialTheme.typography.bodySmall)
        when (val shown = state) {
            HelpContactsState.Idle -> OutlinedButton(onClick = { if (checkGesture()) model.load() }) { Text("Leer contactos") }
            is HelpContactsState.Reading -> {
                CircularProgressIndicator(Modifier.testTag("help_contacts_reading"))
                Text("Leyendo contactos…")
            }
            is HelpContactsState.Writing -> {
                CircularProgressIndicator()
                Text("Guardando contactos…")
            }
            is HelpContactsState.ReadFailure -> {
                Text("No se pudieron leer los contactos. Se conserva el archivo; no se reemplaza con una lista vacía.",
                    Modifier.testTag("help_contacts_error"))
                if (shown.requiresReload) Text("La escritura anterior pudo completarse. Vuelve a leer antes de agregar o eliminar.")
                OutlinedButton(onClick = { if (checkGesture() && model.state.value === shown) model.load() }) {
                    Text("Volver a leer contactos")
                }
            }
            is HelpContactsState.WriteUncertain -> {
                Text("El cambio pudo guardarse, pero no se pudo confirmar. Vuelve a leer los contactos antes de otro cambio.",
                    Modifier.testTag("help_contacts_uncertain"))
                OutlinedButton(onClick = { if (checkGesture() && model.state.value === shown) model.load() }) {
                    Text("Comprobar contactos guardados")
                }
            }
            is HelpContactsState.Ready -> {
                if (shown.snapshot.contacts.isEmpty()) Text("Todavía no hay contactos guardados.", Modifier.testTag("help_contacts_empty"))
                shown.snapshot.contacts.forEach { contact ->
                    Text(contact.name, style = MaterialTheme.typography.titleMedium)
                    Text(contact.phoneNumber)
                    OutlinedButton(modifier = Modifier.fillMaxWidth().testTag("help_contact_dial_${contact.id}"), onClick = {
                        if (checkGesture() && model.state.value === shown) onContactDial(contact, shown)
                    }) { Text("Abrir marcador") }
                    TextButton(onClick = {
                        if (checkGesture() && model.state.value === shown) deletion = contact to shown
                    }) { Text("Eliminar contacto") }
                    HorizontalDivider()
                }
                shown.validationMessage?.let { Text(it, Modifier.testTag("help_contacts_validation")) }
                if (shown.snapshot.contacts.size < TrustedContact.MAX_CONTACTS) {
                    OutlinedTextField(draft.name, { if (checkGesture()) model.editName(it) }, modifier = Modifier.fillMaxWidth().testTag("help_contact_name"),
                        singleLine = true, label = { Text("Nombre del contacto") })
                    OutlinedTextField(draft.phoneNumber, { if (checkGesture()) model.editPhoneNumber(it) }, modifier = Modifier.fillMaxWidth().testTag("help_contact_phone"),
                        singleLine = true, label = { Text("Teléfono · 10 a 15 dígitos") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    Button(modifier = Modifier.fillMaxWidth().testTag("help_contact_save"), onClick = {
                        if (checkGesture() && model.state.value === shown) model.add(draft.name, draft.phoneNumber)
                    }) { Text("Guardar contacto") }
                }
                OutlinedButton(onClick = { if (checkGesture() && model.state.value === shown) model.load() }) { Text("Actualizar contactos") }
            }
        }
        TextButton(onClick = { if (checkGesture()) onClose() }) { Text("Volver") }
    }
    deletion?.let { (contact, shown) ->
        if (state === shown) AlertDialog(
            onDismissRequest = { deletion = null },
            title = { Text("¿Eliminar contacto?") },
            text = { Text("Se quitará ${contact.name} de los contactos de este espacio. No se modifica la agenda del teléfono.") },
            confirmButton = { TextButton(onClick = {
                if (checkGesture() && model.state.value === shown) model.remove(contact.id, shown)
                deletion = null
            }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deletion = null }) { Text("Cancelar") } },
        )
    }
}
