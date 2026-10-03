# Acoplar la interfaz al reporte

El puente técnico conecta la actividad con la lógica y los datos existentes mediante `ReportViewModel`, en `com.example.citizensecurity.report`. `MainActivity` usa `ComponentActivity` y expone `reportViewModel`; conserva el XML y el login visual del compañero. La conexión no añade un formulario, autenticación ni guardado automático. El compañero puede utilizar este contrato al construir su pantalla de reportes.

## Obtener la instancia

En `MainActivity`, usa la propiedad ya preparada:

```kotlin
val modelo = reportViewModel
```

`CitizenSecurityApplication.reportViewModelFactory` es una fábrica compartida que recibe `reportRepository`. Otra actividad que vaya a mostrar reportes puede obtener su propia instancia del ViewModel con la misma fábrica:

```kotlin
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.report.ReportViewModel

val app = application as CitizenSecurityApplication
val modelo = ViewModelProvider(this, app.reportViewModelFactory)[ReportViewModel::class.java]
```

La fábrica se construye con `ReportViewModel.factory(repository)`. Android utiliza `ViewModelProvider` para asociar el ViewModel a la actividad y proporcionar sus dependencias mediante una fábrica. [Fábricas de ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories).

El repositorio pertenece a `CitizenSecurityApplication` y se comparte durante el proceso. Una pantalla no debe crear otra instancia por cada actualización ni cerrar la compartida. La fábrica está disponible sin abrir SQLite ni guardar un reporte al iniciar la actividad.

## Construir la entrada del formulario

La pantalla convierte su selección visible a los valores del contrato, sin usar el texto traducido como nombre de un enum:

| Selección visible | Valor de `IncidentType` |
| --- | --- |
| Emergencia | `EMERGENCY` |
| Robo | `THEFT` |
| Accidente | `ACCIDENT` |
| Incendio | `FIRE` |
| Riesgo | `RISK` |
| Otro | `OTHER` |

La prioridad se conecta de la misma forma: Baja → `Priority.LOW`, Media → `Priority.MEDIUM`, Alta → `Priority.HIGH`. Las etiquetas son una propuesta para la pantalla; los valores del contrato son los existentes.

Cuando el usuario pulse el futuro botón de guardar, la pantalla construirá:

```kotlin
val borrador = NewReport(
    type = tipoSeleccionado,
    priority = prioridadSeleccionada,
    description = descripcionEscrita,
    occurredAt = fechaDelIncidente,
    location = ReportLocation(
        reference = referenciaEscrita,
        latitude = latitudSeleccionada,
        longitude = longitudSeleccionada,
    ),
)
modelo.save(borrador)
```

`tipoSeleccionado` y `prioridadSeleccionada` son los enums elegidos; `fechaDelIncidente` es un `Instant` convertido desde la fecha y zona de la pantalla. Las coordenadas son `Double?`: ambas ausentes para usar solo una referencia, o ambas presentes para un punto seleccionado. Las reglas completas están en [Contrato de guardado](GUARDAR_REPORTE.md).

Este fragmento describe la conexión futura del botón. El login actual no llama a `save` ni tiene listeners nuevos para guardar reportes. Google Maps y la captura del punto siguen pendientes del compañero; el puente ya recibe el par de coordenadas del contrato compartido sin depender del SDK.

## Observar el resultado

`save(draft: NewReport)` inicia la operación en el ViewModel. La pantalla lee `state: StateFlow<ReportSaveState>`; el flujo es de solo lectura y se obtiene con estos estados:

| Estado | Acción de la futura pantalla |
| --- | --- |
| `Idle` | Dejar disponible el botón de guardar. |
| `Saving` | Deshabilitar el botón y mostrar que se está guardando. |
| `Saved(report)` | Mostrar el folio `report.id` y confirmar que el repositorio devolvió el reporte. |
| `Invalid(errors)` | Habilitar la corrección y mostrar cada mensaje junto a su campo. |
| `Error(message)` | Habilitar el reintento y mostrar el mensaje de almacenamiento. |

Los errores de validación se identifican con `ReportField.DESCRIPTION`, `LOCATION_REFERENCE`, `LOCATION_COORDINATES` y `OCCURRED_AT`. `Invalid.errors` es un `Map<ReportField, String>`; no se necesita interpretar excepciones ni detalles de SQLite desde la pantalla.

En la futura actividad de reportes, la observación puede seguir este patrón:

```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.citizensecurity.report.ReportSaveState
import kotlinx.coroutines.launch

lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        modelo.state.collect { estado ->
            botonGuardar.isEnabled = estado != ReportSaveState.Saving
            when (estado) {
                ReportSaveState.Idle -> mostrarFormularioDisponible()
                ReportSaveState.Saving -> mostrarGuardadoEnCurso()
                is ReportSaveState.Saved -> mostrarFolio(estado.report.id)
                is ReportSaveState.Invalid -> mostrarErrores(estado.errors)
                is ReportSaveState.Error -> mostrarError(estado.message)
            }
        }
    }
}
```

El botón y las funciones `mostrar…` representan componentes que el compañero implementará en la pantalla; este ejemplo no se ha añadido al login. `repeatOnLifecycle` observa mientras la actividad está al menos en `STARTED` y detiene la colección cuando deja ese estado. Para un fragmento, usa el `lifecycleScope` y `repeatOnLifecycle` de `viewLifecycleOwner`. [Corrutinas y ciclo de vida](https://developer.android.com/topic/libraries/architecture/coroutines), [patrón para Views](https://developer.android.com/topic/libraries/architecture/views/coroutines-views).

La colección solo representa el estado: no debe llamar a `save`. `StateFlow` vuelve a entregar su estado actual al observar, por lo que mostrar `Saved` debe ser una actualización de la vista y no una nueva inserción automática. El ViewModel bloquea llamadas simultáneas mientras está en `Saving`; una nueva llamada después de terminar constituye otro guardado.

El guardado usa el ámbito del ViewModel y propaga la cancelación. Al limpiar el ViewModel se cancela su operación, se ignoran nuevas llamadas desde esa instancia y el repositorio compartido permanece utilizable. La pantalla no cierra el repositorio. La permanencia tras reabrir SQLite se demuestra con la instrumentación existente; no se ha comprobado todavía una rotación real con un formulario ni un flujo completo de guardar desde la interfaz.

## Estado de verificación

El 3 de octubre de 2026 se comprobaron el puente y su integración técnica: 6 pruebas JVM del ViewModel aprobadas, 9 instrumentadas de SQLite ejecutadas y aprobadas en API 37, lint sin errores y APK `0.2.1` generado. Lint de datos no tuvo incidencias; `app` conserva 13 advertencias visuales. Las 32 pruebas de dominio mantuvieron su resultado anterior mediante `UP-TO-DATE`, sin una nueva ejecución de esos casos. La evidencia completa y el artefacto están en [Validación](VALIDACION.md).

Los XML registran las 6 pruebas del puente a `2026-10-03T16:48:36.500Z` y las 9 instrumentadas a `2026-10-03T16:47:13Z`. El resultado reutilizado del dominio conserva `2026-10-03T05:39:56.971Z`.

La comprobación es técnica; el formulario, los listeners de sus controles, el mapa y la autenticación siguen pendientes del compañero. No se probó un flujo visual completo ni una rotación real de esa futura pantalla. `0.2.1` continúa sin etiqueta ni release publicada; fase 3 y `0.3.0` permanecen en `Unreleased`, con entrega y aceptación funcional pendientes.
