package com.example.citizensecurity.administration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportStatusChangeRequest
import com.example.citizensecurity.report.ReportStatusRecoveryState
import com.example.citizensecurity.report.ReportStatusRecoveryViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Opening, rotation and returning from another activity never recover, prepare or submit a change. */
@Composable
internal fun AdministrationStatusChangeScreen(
    preparation: AdministrationStatusChangeViewModel,
    recovery: ReportStatusRecoveryViewModel,
    initialReportId: String?,
    canApply: () -> Boolean,
    onClose: () -> Unit,
) {
    val prepared = preparation.state.collectAsStateWithLifecycle().value
    val recovered = recovery.state.collectAsStateWithLifecycle().value
    val focus = LocalFocusManager.current
    val zone = remember { ZoneId.systemDefault() }
    val list = rememberLazyListState()
    var folio by remember(preparation) { mutableStateOf(initialReportId.orEmpty()) }
    fun action(block: () -> Unit) {
        if (preparation.state.value !== prepared || recovery.state.value !== recovered || !canApply()) return
        focus.clearFocus()
        block()
    }
    fun close() {
        focus.clearFocus()
        preparation.cancel()
        recovery.close()
        onClose()
    }
    LaunchedEffect(recovered) {
        if (recovered !== ReportStatusRecoveryState.Ready) preparation.cancel()
        if (recovered === ReportStatusRecoveryState.Closed) { folio = ""; focus.clearFocus() }
        list.scrollToItem(0)
    }
    LaunchedEffect(prepared) {
        if (prepared === AdministrationStatusChangeState.Denied) {
            folio = ""
            focus.clearFocus()
            recovery.close()
        }
    }
    // New preparation stages start at their heading; edits within the same field keep their place.
    LaunchedEffect(prepared.javaClass) { list.scrollToItem(0) }
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp)
                .testTag("admin_status_screen"),
            state = list,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seguimiento administrativo local", style = MaterialTheme.typography.headlineSmall)
                    Text("Estos estados organizan el reporte en esta cuenta y este teléfono. Marcar Atendido o Cerrado no confirma intervención policial ni atención de una institución.")
                }
            }
            if (prepared === AdministrationStatusChangeState.Denied || recovered === ReportStatusRecoveryState.Closed) {
                item { StatusMessage("No hay permiso o sesión vigente. Vuelve a Inicio para revisar el acceso.", "admin_status_denied") }
            } else when (recovered) {
                ReportStatusRecoveryState.AwaitingRecovery -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Antes de preparar otro cambio, comprueba si quedó una solicitud pendiente en este espacio. Esta comprobación no cambia reportes.")
                        Button(onClick = { action { preparation.cancel(); recovery.recover(recovered) } },
                            modifier = Modifier.fillMaxWidth().testTag("admin_status_recover")) { Text("Comprobar solicitudes pendientes") }
                    }
                }
                is ReportStatusRecoveryState.Recovering -> item {
                    StatusLoading("Buscando la solicitud y su recibo…", "admin_status_recovering")
                }
                is ReportStatusRecoveryState.Working -> item {
                    StatusLoading(if (recovered.acknowledging) "Comprobando y cerrando la solicitud…" else "Guardando el cambio local…", "admin_status_working")
                    Text("Si sales o se interrumpe, recupera su recibo al volver. La solicitud conserva su identidad.")
                }
                is ReportStatusRecoveryState.Pending -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusMessage(recovered.message, "admin_status_pending")
                        StatusRequest(recovered.request)
                        if (recovered.requiresRecovery) Button(
                            onClick = { action { recovery.recover(recovered) } },
                            modifier = Modifier.fillMaxWidth().testTag("admin_status_recover"),
                        ) { Text("Recuperar resultado de esta solicitud") }
                        else Button(onClick = { action { recovery.retry(recovered) } },
                            modifier = Modifier.fillMaxWidth().testTag("admin_status_retry")) { Text("Reintentar la misma solicitud") }
                        Text("No se puede preparar otro reporte mientras esta solicitud esté pendiente.")
                    }
                }
                is ReportStatusRecoveryState.Confirmed -> item {
                    val event = recovered.receipt.event
                    Card(Modifier.fillMaxWidth().testTag("admin_status_confirmed")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Cambio local confirmado", style = MaterialTheme.typography.titleLarge)
                            Text("Folio: ${recovered.receipt.reportId}")
                            Text("${statusLabel(checkNotNull(event.fromStatus))} → ${statusLabel(event.toStatus)}")
                            Text("Secuencia del historial: ${event.sequence}")
                            Text("Motivo: ${event.reason}")
                            Text("Cuenta local que registró el cambio: ${event.actorAccountId}")
                            Text("Registro: ${statusTime(event.recordedAt, zone)} · ${zone.id}")
                            Text("Operación: ${event.operationId}", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { action { recovery.acknowledgeConfirmed(recovered) } },
                                modifier = Modifier.fillMaxWidth().testTag("admin_status_acknowledge")) { Text("Entendido: cerrar esta solicitud") }
                            Text("Cerrar el recibo no borra el evento del historial. Después podrás consultar de nuevo el estado para preparar otro cambio.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                is ReportStatusRecoveryState.Rejected -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusMessage(recovered.message, "admin_status_rejected")
                        StatusRequest(recovered.request)
                        Button(onClick = { action { recovery.acknowledgeRejected(recovered) } },
                            modifier = Modifier.fillMaxWidth().testTag("admin_status_acknowledge_rejected")) { Text("Cerrar solicitud rechazada") }
                        Text("Después consulta otra vez el folio y revisa una nueva solicitud; este botón no reintenta el cambio.")
                    }
                }
                is ReportStatusRecoveryState.Error -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusMessage(recovered.message, "admin_status_recovery_error")
                        Button(onClick = { action { recovery.recover(recovered) } },
                            modifier = Modifier.fillMaxWidth().testTag("admin_status_recover")) { Text("Volver a comprobar el resultado") }
                    }
                }
                is ReportStatusRecoveryState.Invalid -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusMessage(recovered.message, "admin_status_recovery_invalid")
                        Button(onClick = { action { recovery.recover(recovered) } },
                            modifier = Modifier.fillMaxWidth().testTag("admin_status_recover")) { Text("Comprobar solicitudes antes de continuar") }
                    }
                }
                ReportStatusRecoveryState.Ready -> {
                    when (prepared) {
                        AdministrationStatusChangeState.Idle, AdministrationStatusChangeState.Invalid,
                        is AdministrationStatusChangeState.Missing, is AdministrationStatusChangeState.Error -> {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Consultar el estado antes de preparar un cambio", style = MaterialTheme.typography.titleMedium)
                                    OutlinedTextField(value = folio, onValueChange = { value ->
                                        if (preparation.state.value === prepared && recovery.state.value === recovered && canApply()) folio = value
                                    },
                                        label = { Text("Folio completo") }, modifier = Modifier.fillMaxWidth().testTag("admin_status_folio"))
                                    Button(onClick = { action { preparation.loadFolio(folio, prepared) } },
                                        modifier = Modifier.fillMaxWidth().testTag("admin_status_load")) { Text("Consultar estado actual") }
                                    when (prepared) {
                                        AdministrationStatusChangeState.Invalid -> StatusMessage("Escribe el folio UUID completo que recibiste al guardar el reporte.", "admin_status_invalid")
                                        is AdministrationStatusChangeState.Missing -> StatusMessage("Este folio no está guardado en el espacio de la cuenta actual.", "admin_status_missing")
                                        is AdministrationStatusChangeState.Error -> {
                                            StatusMessage(prepared.message, "admin_status_read_error")
                                            OutlinedButton(onClick = { action { preparation.loadFolio(prepared.reportId, prepared) } },
                                                modifier = Modifier.fillMaxWidth().testTag("admin_status_reload")) { Text("Volver a consultar ese folio") }
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                        is AdministrationStatusChangeState.Loading -> item {
                            StatusLoading("Comprobando estado, revisión y permisos…", "admin_status_loading")
                            OutlinedButton(onClick = { action { preparation.cancel() } },
                                modifier = Modifier.fillMaxWidth().testTag("admin_status_cancel_preparation")) { Text("Cancelar preparación") }
                        }
                        is AdministrationStatusChangeState.Editing -> item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatusSnapshot(prepared.snapshot)
                                prepared.message?.let { StatusMessage(it, "admin_status_changed") }
                                OutlinedTextField(value = prepared.reason, onValueChange = { value ->
                                    // Keep keyboard focus while typing. The model still checks the exact rendered state.
                                    if (preparation.state.value === prepared && recovery.state.value === recovered && canApply()) {
                                        preparation.setReason(prepared, value)
                                    }
                                }, label = { Text("Motivo del cambio") }, minLines = 3,
                                    supportingText = { Text("De 5 a 500 caracteres válidos, sin controles ni espacios al inicio o final.") },
                                    isError = prepared.reason.isNotEmpty() && !prepared.validReason,
                                    modifier = Modifier.fillMaxWidth().testTag("admin_status_reason"))
                                Button(onClick = { action { preparation.review(prepared) } }, enabled = prepared.validReason,
                                    modifier = Modifier.fillMaxWidth().testTag("admin_status_review")) { Text("Revisar cambio antes de confirmar") }
                                OutlinedButton(onClick = { action { preparation.cancel() } },
                                    modifier = Modifier.fillMaxWidth().testTag("admin_status_cancel_preparation")) { Text("Cancelar preparación") }
                            }
                        }
                        is AdministrationStatusChangeState.Reviewing -> item {
                            Card(Modifier.fillMaxWidth().testTag("admin_status_review_summary")) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Revisa el cambio local", style = MaterialTheme.typography.titleLarge)
                                    StatusSnapshot(prepared.snapshot)
                                    Text("Motivo que quedará en el historial: ${prepared.reason}")
                                    Text("Al confirmar se registra este único avance consecutivo con tu cuenta local. Ninguna autoridad recibe el reporte desde esta acción.")
                                    Button(onClick = { action { preparation.confirmReviewed(prepared, recovery, canApply) } },
                                        modifier = Modifier.fillMaxWidth().testTag("admin_status_confirm")) { Text("Confirmar y guardar cambio local") }
                                    OutlinedButton(onClick = { action { preparation.editReview(prepared) } },
                                        modifier = Modifier.fillMaxWidth().testTag("admin_status_edit_reason")) { Text("Volver a editar el motivo") }
                                    OutlinedButton(onClick = { action { preparation.cancel() } },
                                        modifier = Modifier.fillMaxWidth().testTag("admin_status_cancel_preparation")) { Text("Cancelar sin enviar esta preparación") }
                                }
                            }
                        }
                        is AdministrationStatusChangeState.Closed -> item {
                            StatusSnapshot(prepared.snapshot)
                            StatusMessage("Este reporte ya está Cerrado. No hay otro estado consecutivo disponible.", "admin_status_terminal")
                            OutlinedButton(onClick = { action { preparation.cancel() } }, modifier = Modifier.fillMaxWidth()
                                .testTag("admin_status_cancel_preparation")) { Text("Consultar otro folio") }
                        }
                        AdministrationStatusChangeState.Denied -> Unit
                    }
                }
                ReportStatusRecoveryState.Closed -> Unit
            }
            item {
                OutlinedButton(onClick = ::close, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                    .testTag("admin_status_close")) { Text("Volver a la consulta") }
            }
        }
    }
}

@Composable
private fun StatusSnapshot(snapshot: AdministrationStatusSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("admin_status_snapshot")) {
        Text("Folio: ${snapshot.report.id}")
        Text(snapshot.report.description)
        Text("Estado actual local: ${statusLabel(snapshot.report.status)}", Modifier.testTag("admin_status_current"))
        Text("Revisión del historial: ${snapshot.sequence}")
        snapshot.nextStatus?.let { Text("Único avance disponible: ${statusLabel(it)}", Modifier.testTag("admin_status_target")) }
    }
}

@Composable
private fun StatusRequest(request: ReportStatusChangeRequest) {
    Card(Modifier.fillMaxWidth().testTag("admin_status_pending_request")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Folio de la solicitud: ${request.command.reportId}")
            Text("${statusLabel(request.command.expectedStatus)} → ${statusLabel(request.command.targetStatus)}")
            Text("Revisión esperada: ${request.command.expectedSequence}")
            Text("Motivo: ${request.command.reason}")
            Text("Operación: ${request.command.operationId}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusMessage(message: String, tag: String) {
    Text(message, modifier = Modifier.testTag(tag).semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun StatusLoading(message: String, tag: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        StatusMessage(message, tag)
    }
}

private fun statusLabel(status: ReportStatus): String = when (status) {
    ReportStatus.REPORTED -> "Reportado"
    ReportStatus.IN_REVIEW -> "En revisión"
    ReportStatus.ATTENDED -> "Atendido"
    ReportStatus.CLOSED -> "Cerrado"
}

private fun statusTime(time: Instant, zone: ZoneId): String = try {
    DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss").withZone(zone).format(time)
} catch (_: RuntimeException) { "Fecha fuera del intervalo representable" }
