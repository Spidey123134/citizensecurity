package com.example.citizensecurity.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ReportAlertsScreen(model: ReportAlertsViewModel, permissionChoice: ReportAlertsPermissionChoice,
    permissionMessage: String?, onEnable: () -> Unit, onDisable: () -> Unit, onReview: () -> Unit,
    onContinue: () -> Unit, onReload: () -> Unit, onSettings: () -> Unit, onActivity: () -> Unit, onClose: () -> Unit) {
    val state = model.state.collectAsStateWithLifecycle().value
    val busy = model.busy.collectAsStateWithLifecycle().value
    val requestingPermission = permissionChoice.state.collectAsStateWithLifecycle().value != null
    val enabled = !busy && !requestingPermission
    fun action(block: () -> Unit) {
        if (model.state.value === state && !model.busy.value && permissionChoice.state.value == null) block()
    }
    Scaffold { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(20.dp).testTag("report_alerts"),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Avisos de cambios de estado", style = MaterialTheme.typography.headlineSmall)
                Text("Activa los avisos para este acceso y pulsa Revisar cambios. Android recibirá un resumen cuando encuentre cambios registrados que aún no se hayan avisado en este teléfono.")
                Text("La revisión funciona desde esta pantalla. La app conserva los datos en este teléfono; el aviso no confirma atención o recepción por una autoridad.")
            }
            item { Text("El aviso usa texto general, sin folios, nombres, fotografías o ubicación.", style = MaterialTheme.typography.bodySmall) }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Comprobando avisos…", Modifier.testTag("alerts_loading")) }
            if (requestingPermission) item { Text("Esperando tu decisión en Android…", Modifier.testTag("alerts_permission_pending")) }
            when (state) {
                ReportAlertsState.Idle -> Unit
                is ReportAlertsState.Loading -> Unit
                is ReportAlertsState.Error -> item {
                    Text(state.message, Modifier.testTag("alerts_error"))
                    Button(onClick = { action(onReload) }, enabled = enabled, modifier = Modifier.fillMaxWidth()
                        .testTag("alerts_reload")) { Text("Volver a comprobar") }
                }
                is ReportAlertsState.Ready -> item {
                    Text(if (state.snapshot.enabled) "Preferencia activada para este acceso" else "Avisos desactivados para este acceso",
                        Modifier.testTag("alerts_preference"))
                    Text(when (state.availability) {
                        ReportAlertsAvailability.ALLOWED -> "Android permite mostrar avisos de este canal."
                        ReportAlertsAvailability.RUNTIME_PERMISSION_DENIED -> "Falta el permiso de avisos de Android."
                        ReportAlertsAvailability.APP_BLOCKED -> "Android bloquea las notificaciones de esta app."
                        ReportAlertsAvailability.CHANNEL_BLOCKED -> "El canal de cambios de estado está bloqueado en Android."
                    }, Modifier.testTag("alerts_android_status"))
                    if (state.snapshot.enabled) {
                        OutlinedButton(onClick = { action(onDisable) }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().testTag("alerts_disable")) { Text("Desactivar avisos") }
                    } else {
                        Button(onClick = { action(onEnable) }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().testTag("alerts_enable")) { Text("Activar avisos") }
                    }
                    Button(onClick = { action(onReview) }, enabled = enabled && state.snapshot.enabled &&
                        state.availability == ReportAlertsAvailability.ALLOWED,
                        modifier = Modifier.fillMaxWidth().testTag("alerts_review")) { Text("Revisar cambios") }
                    if (state.canContinue) OutlinedButton(onClick = { action(onContinue) }, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("alerts_continue")) { Text("Seguir revisando cambios") }
                    state.message?.let { Text(it, Modifier.testTag("alerts_message")) }
                    OutlinedButton(onClick = { action(onReload) }, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("alerts_reload")) { Text("Actualizar estado de los avisos") }
                }
            }
            permissionMessage?.let { message -> item { Text(message, Modifier.testTag("alerts_permission_message")) } }
            item {
                OutlinedButton(onClick = { action(onSettings) }, enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag("alerts_settings")) { Text("Ajustes de avisos de Android") }
                OutlinedButton(onClick = onActivity, modifier = Modifier.fillMaxWidth().testTag("alerts_activity")) { Text("Ver actividad de reportes") }
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().testTag("alerts_close")) { Text("Volver") }
            }
        }
    }
}
