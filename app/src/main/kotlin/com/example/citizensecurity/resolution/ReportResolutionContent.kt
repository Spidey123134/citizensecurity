package com.example.citizensecurity.resolution

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.citizensecurity.domain.ReportResolution
import com.example.citizensecurity.domain.ReportResolutionActor
import com.example.citizensecurity.domain.ReportResolutionActorKind
import com.example.citizensecurity.domain.ReportResolutionEvent
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReportResolutionContent(model: ReportResolutionViewModel, reportId: String, isCurrent: () -> Boolean = { true }) {
    val state by model.state.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    LaunchedEffect(model, reportId) { if (isCurrent()) model.activate(reportId) }
    fun action(block: () -> Unit) { if (isCurrent() && model.state.value === state) block() }
    val owner = when (val current = state) {
        is ReportResolutionState.Ready -> current.page.reportId
        is ReportResolutionState.Reviewing -> current.command.reportId
        is ReportResolutionState.Working -> current.command.reportId
        is ReportResolutionState.Uncertain -> current.command.reportId
        is ReportResolutionState.Confirmed -> current.command.reportId
        is ReportResolutionState.Loading -> current.reportId
        is ReportResolutionState.Missing -> current.reportId
        is ReportResolutionState.Error -> current.reportId
        else -> null
    }
    Column(Modifier.fillMaxWidth().testTag("resolution_section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Resultado del incidente", style = MaterialTheme.typography.titleMedium)
        Text("Declaración local de quien reportó o de la administración local. Es independiente del estado de seguimiento.",
            style = MaterialTheme.typography.bodySmall)
        if (state === ReportResolutionState.Denied) {
            Notice("El acceso ya no está vigente. Vuelve a Inicio para revisar tu sesión.", "resolution_denied")
        } else if (owner != reportId) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else when (val current = state) {
            is ReportResolutionState.Ready -> {
                Text("Resultado: ${resolutionLabel(current.page.resolution)}", Modifier.testTag("resolution_current"))
                current.message?.let { Notice(it, "resolution_message") }
                if (current.blockedByPending) {
                    Notice("Hay una declaración pendiente en otro folio. Vuelve a ese detalle para comprobar su recibo antes de preparar otra.",
                        "resolution_pending_elsewhere")
                } else if (current.page.actor != null) {
                    Text("Declararás como ${actorLabel(requireNotNull(current.page.actor))}", Modifier.testTag("resolution_actor"),
                        style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReportResolution.entries.forEach { target ->
                            FilterChip(selected = current.target == target, enabled = target != current.page.resolution,
                                onClick = { action { model.select(current, target) } },
                                label = { Text(resolutionLabel(target)) },
                                modifier = Modifier.testTag("resolution_choice_${target.name}"))
                        }
                    }
                    OutlinedTextField(value = current.reason,
                        onValueChange = { value -> action { model.setReason(current, value) } },
                        label = { Text("Motivo de esta declaración") },
                        supportingText = { Text("Entre 5 y 500 caracteres. Evita datos personales innecesarios.") },
                        modifier = Modifier.fillMaxWidth().testTag("resolution_reason"))
                    Button(onClick = { action { model.review(current) } }, enabled = current.canReview,
                        modifier = Modifier.fillMaxWidth().testTag("resolution_review")) { Text("Revisar declaración") }
                } else Text("La cuenta actual puede consultar el resultado, pero no declarar uno para este reporte.")
                TextButton(onClick = { action { model.refresh(current) } },
                    modifier = Modifier.testTag("resolution_refresh")) { Text("Actualizar resultado e historial") }
                Column(Modifier.fillMaxWidth().testTag("resolution_history"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Historial del resultado", style = MaterialTheme.typography.titleSmall)
                    if (current.page.events.isEmpty()) Text("Todavía no hay declaraciones de resultado. No se deduce que un incidente esté solucionado por estar cerrado.")
                    current.page.events.forEach { event -> ResolutionEvent(event, zone) }
                    if (current.page.hasMore || current.previous.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { action { model.previous(current) } }, enabled = current.previous.isNotEmpty(),
                                modifier = Modifier.weight(1f).testTag("resolution_previous")) { Text("Página anterior") }
                            OutlinedButton(onClick = { action { model.next(current) } },
                                enabled = current.page.hasMore && current.previous.size < 50,
                                modifier = Modifier.weight(1f).testTag("resolution_next")) { Text("Eventos anteriores") }
                        }
                    }
                }
            }
            is ReportResolutionState.Reviewing -> {
                Text("Revisa antes de guardar", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("resolution_reviewing"))
                Text("${resolutionLabel(current.command.expectedResolution)} → ${resolutionLabel(current.command.targetResolution)}")
                Text("Declarado por ${actorLabel(current.command.actor)}")
                Text("Motivo: ${current.command.reason}")
                Text("Se guardará en este teléfono con fecha e historial. No confirma atención de autoridades.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { action { model.confirm(current) } }, modifier = Modifier.fillMaxWidth().testTag("resolution_confirm")) {
                    Text("Confirmar y guardar resultado")
                }
                OutlinedButton(onClick = { action { model.edit(current) } }, modifier = Modifier.fillMaxWidth().testTag("resolution_edit")) {
                    Text("Volver a editar")
                }
            }
            is ReportResolutionState.Working -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Notice("Comprobando y guardando la declaración…", "resolution_working")
            }
            is ReportResolutionState.Uncertain -> {
                Notice(current.message, "resolution_uncertain")
                Button(onClick = { action { model.recover(current) } }, modifier = Modifier.fillMaxWidth().testTag("resolution_recover")) {
                    Text("Comprobar recibo · solo lectura")
                }
                if (current.canRetry) OutlinedButton(onClick = { action { model.retry(current) } },
                    modifier = Modifier.fillMaxWidth().testTag("resolution_retry")) { Text("Reintentar la misma declaración") }
                if (current.canDiscard) OutlinedButton(onClick = { action { model.discard(current) } },
                    modifier = Modifier.fillMaxWidth().testTag("resolution_discard")) { Text("Cerrar solicitud y consultar resultado") }
                Text("Esta recuperación vive en memoria. Si cierras la app, al volver se consultará lo guardado sin repetir escrituras.",
                    style = MaterialTheme.typography.bodySmall)
            }
            is ReportResolutionState.Confirmed -> {
                Notice("Resultado guardado: ${resolutionLabel(current.event.toResolution)}", "resolution_confirmed")
                ResolutionEvent(current.event, zone)
                Button(onClick = { action { model.acknowledge(current) } }, modifier = Modifier.fillMaxWidth().testTag("resolution_acknowledge")) {
                    Text("Ver resultado e historial actualizado")
                }
            }
            is ReportResolutionState.Loading -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Notice("Consultando el resultado local…", "resolution_loading")
            }
            is ReportResolutionState.Missing -> Notice("El reporte ya no está disponible en este espacio.", "resolution_missing")
            is ReportResolutionState.Error -> {
                Notice("No se pudo consultar el resultado. Los datos guardados se conservan.", "resolution_error")
                OutlinedButton(onClick = { action { model.refresh(current) } }, modifier = Modifier.testTag("resolution_refresh")) {
                    Text("Volver a consultar")
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ResolutionEvent(event: ReportResolutionEvent, zone: ZoneId) {
    Column(Modifier.fillMaxWidth().testTag("resolution_event_${event.sequence}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${resolutionLabel(event.fromResolution)} → ${resolutionLabel(event.toResolution)}", style = MaterialTheme.typography.titleSmall)
        Text("${DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss z").withZone(zone).format(event.recordedAt)} · declaración ${event.sequence}",
            style = MaterialTheme.typography.bodySmall)
        Text("${actorLabel(event.actor)}", style = MaterialTheme.typography.bodySmall)
        Text(event.reason)
    }
}

@Composable
private fun Notice(message: String, tag: String) = Text(message,
    Modifier.testTag(tag).semantics { liveRegion = LiveRegionMode.Polite })

internal fun resolutionLabel(value: ReportResolution): String = when (value) {
    ReportResolution.UNCONFIRMED -> "Sin confirmar"
    ReportResolution.RESOLVED -> "Solucionado"
    ReportResolution.UNRESOLVED -> "Sigue sin solucionarse"
}

private fun actorLabel(actor: ReportResolutionActor): String =
    "${if (actor.kind == ReportResolutionActorKind.AUTHOR) "Autor" else "Administrador local"} · cuenta ${actor.accountId}"
