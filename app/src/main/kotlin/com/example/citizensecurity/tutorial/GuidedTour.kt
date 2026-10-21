package com.example.citizensecurity.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.citizensecurity.R

/** Guidance only: opening or moving through a tour never invokes a product action. */
enum class TourTopic(val key: String) {
    HOME("home"), REPORT("report"), REVIEW("review"), REPORTS("reports"), DETAIL("detail"), MAP("map"),
    RESOLUTION("resolution"),
}

private data class TourStep(val title: String, val body: String)
private data class TourState(val topic: String, val open: Boolean = false, val step: Int = 0)

/** Optional, contextual help. Only the topic and UI position enter saved instance state. */
@Composable
fun GuidedTourButton(topic: TourTopic, modifier: Modifier = Modifier) {
    key(topic.key) {
        val steps = stepsFor(topic)
        val saver = remember(topic) {
            listSaver<TourState, Any>(
                save = { listOf(it.topic, it.open, it.step) },
                restore = { saved ->
                    val index = saved.getOrNull(2) as? Int
                    if (saved.getOrNull(0) == topic.key && saved.getOrNull(1) is Boolean &&
                        index != null && index in steps.indices
                    ) TourState(topic.key, saved[1] as Boolean, index)
                    else TourState(topic.key)
                },
            )
        }
        var state by rememberSaveable(topic.key, stateSaver = saver) { mutableStateOf(TourState(topic.key)) }
        val focus = LocalFocusManager.current
        fun close() { state = TourState(topic.key) }

        IconButton(
            onClick = {
                focus.clearFocus()
                state = TourState(topic.key, open = true)
            },
            modifier = modifier.size(48.dp).testTag("tour_open_${topic.key}"),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_tour_help),
                contentDescription = "Abrir recorrido de ${topicLabel(topic)}",
                modifier = Modifier.size(24.dp),
            )
        }
        if (state.topic == topic.key && state.open) {
            val availableHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
            Dialog(onDismissRequest = ::close) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                    // Actions participate in the scroll so large text and a short landscape window
                    // cannot cut them off. A new step starts at the top, including its heading.
                    key(state.step) {
                        Column(
                            Modifier.fillMaxWidth().heightIn(max = maxOf(48.dp, availableHeight * 0.85f))
                                .verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val progress = "Paso ${state.step + 1} de ${steps.size}"
                            Text(
                                progress,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.testTag("tour_progress").semantics {
                                    stateDescription = progress
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                            Text(
                                steps[state.step].title,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.testTag("tour_title").semantics { heading() },
                            )
                            Text(steps[state.step].body, modifier = Modifier.testTag("tour_body"))
                            Button(
                                onClick = {
                                    if (state.step == steps.lastIndex) close()
                                    else state = state.copy(step = state.step + 1)
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tour_next"),
                            ) { Text(if (state.step == steps.lastIndex) "Listo" else "Siguiente") }
                            OutlinedButton(
                                onClick = { if (state.step > 0) state = state.copy(step = state.step - 1) },
                                enabled = state.step > 0,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tour_previous"),
                            ) { Text("Anterior") }
                            TextButton(
                                onClick = ::close,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("tour_close"),
                            ) { Text("Cerrar") }
                        }
                    }
                }
            }
        }
    }
}

private fun topicLabel(topic: TourTopic): String = when (topic) {
    TourTopic.HOME -> "Inicio"
    TourTopic.REPORT -> "Nuevo reporte"
    TourTopic.REVIEW -> "Revisar reporte"
    TourTopic.REPORTS -> "Reportes guardados"
    TourTopic.DETAIL -> "Detalle del reporte"
    TourTopic.MAP -> "Reportes en el mapa"
    TourTopic.RESOLUTION -> "Resultado del incidente"
}

@Composable
private fun stepsFor(topic: TourTopic): List<TourStep> = when (topic) {
    TourTopic.HOME -> listOf(
        TourStep("Guarda lo que ocurrió", stringResource(R.string.home_report_description)),
        TourStep("Encuentra tus reportes", stringResource(R.string.home_list_description)),
        TourStep("Consulta el mapa", stringResource(R.string.home_map_description)),
        TourStep("Ayuda y seguimiento", "Ayuda y contactos abre el marcador para que tú decidas llamar. Actividad muestra cambios locales y permite revisar avisos optativos. Guardar un reporte no avisa a emergencias ni a autoridades."),
    )
    TourTopic.REPORT -> listOf(
        TourStep("Cuenta lo ocurrido", "Elige el tipo y la prioridad, escribe lo ocurrido y revisa la fecha y hora del incidente. La prioridad es la que tú indicas."),
        TourStep("Marca el lugar", "En el mapa, toca el punto del incidente. Busca tu ubicación precisa y confirma un punto dentro de 5 km. La captura de GPS necesita tu gesto; el recorrido no la inicia."),
        TourStep("Adjunta una fotografía", "Puedes tomar una foto o elegirla de la galería. Es opcional. Revisa la vista previa y espera a que termine de prepararse antes de continuar."),
        TourStep("Revisa antes de guardar", "Revisar borrador muestra los datos completos antes de crear el reporte. Guardar borrador para continuar después conserva los campos sin crear todavía un reporte."),
    )
    TourTopic.REVIEW -> listOf(
        TourStep("Comprueba el borrador", "Revisa los datos, el punto y la fotografía. Vuelve a editar si algo está mal. El aviso de reportes parecidos es una advertencia para que decidas con cuidado."),
        TourStep("Actualiza tu ubicación", "Antes de guardar, usa el control de ubicación para renovar la comprobación de los 5 km. Abrir esta ayuda no obtiene GPS ni concede permisos."),
        TourStep("Guarda y conserva el folio", "Guardar reporte en este teléfono crea el registro local y entrega un folio. Si el resultado queda pendiente, compruébalo o reintenta el mismo intento; no crees otro para sustituirlo. No se envía a autoridades."),
    )
    TourTopic.REPORTS -> listOf(
        TourStep("Busca tus reportes", "Puedes consultar un folio completo o recorrer los registros de este espacio en el teléfono. Una cuenta distinta tiene su propio espacio."),
        TourStep("Usa los filtros", "Combina texto, fechas, tipo, estado y prioridad. Buscar reportes aplica el texto y las fechas; si hay más resultados, continúa con los controles de página."),
        TourStep("Abre un detalle", "Pulsa Ver detalle para revisar los datos de un registro, su fotografía e historial. El estado describe el seguimiento local; no confirma atención de autoridades."),
    )
    TourTopic.DETAIL -> listOf(
        TourStep("Identifica el reporte", "El folio identifica este registro. Puedes copiarlo y revisar la descripción, fechas y fotografía. Ver en el mapa aparece cuando hay un punto guardado."),
        TourStep("Consulta el resultado y su historial", "Resultado del incidente indica si se declaró solucionado o sigue sin solucionarse, con su propia ayuda. Cerrado es un estado de seguimiento y no confirma solución. Los historiales conservan los cambios locales; un error al consultarlos no borra el reporte."),
        TourStep("Prepara lo que vas a compartir", "La ficha PDF y el mensaje para un contacto tienen su propia revisión. Tú eliges qué incluir y abres otra aplicación con un gesto separado; ver el detalle o este recorrido no comparte datos."),
    )
    TourTopic.MAP -> listOf(
        TourStep("Explora las calles", "Mueve y acerca el mapa hasta la zona que quieres revisar. El centro inicial es solo un punto de navegación; no representa tu ubicación."),
        TourStep("Consulta esta zona", "Elige los filtros y consulta la zona visible. Solo aparecen reportes con coordenadas guardadas en tu espacio de este teléfono. El límite puede dejar coincidencias fuera de la muestra."),
        TourStep("Abre un reporte de la muestra", "Selecciona un marcador y pulsa Abrir reporte, o usa Ver lista de esta zona. El color representa la prioridad indicada por el autor, no una verificación del incidente."),
    )
    TourTopic.RESOLUTION -> listOf(
        TourStep("¿Quedó solucionado?", "Sin confirmar, Solucionado y Sigue sin solucionarse describen el resultado declarado. El estado de seguimiento va por separado: Cerrado no significa Solucionado. Es información local, sin confirmar atención de autoridades."),
        TourStep("Quién puede declararlo", "El autor del reporte y un administrador local autorizado pueden declarar en su propio espacio. El acceso académico permite consultar. Cada declaración conserva la cuenta, el rol, la fecha y el motivo de quien la guardó."),
        TourStep("Revisa antes de confirmar", "Elige un resultado, escribe el motivo y pulsa Revisar declaración. Guardar requiere después Confirmar y guardar resultado. Si el problema cambia, otra declaración corrige el resultado y conserva las anteriores en el historial. Esta ayuda no guarda cambios."),
        TourStep("Si no sabes si se guardó", "Usa Comprobar recibo para consultar el intento pendiente. Reintentar requiere otro gesto y usa la misma declaración. Si cierras la app, al volver se consulta lo guardado; la solicitud pendiente no se conserva ni se repite automáticamente."),
    )
}
