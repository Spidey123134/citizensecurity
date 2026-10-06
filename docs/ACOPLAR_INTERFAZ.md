# Acoplar la interfaz al reporte

Versión oficial **0.4.0a / código Android 19**, del **6 de octubre de 2026**, con validación completada. El usuario pidió revisar GitHub, incorporar el trabajo del compañero y subir/publicar esta actualización como Release; su estado en GitHub se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a). Esta autorización cubre esta entrega, sin autorizar automáticamente siguientes publicaciones.

Se completaron dos pasos técnicos: **fase 6A**, un puente para conectar permiso, captura explícita y ciclo de vida de la pantalla; y **fase 7**, un borrador editable que conserva los campos al ir al mapa y regresar, con recuperación mediante `SavedStateHandle` y protección ante resultados de otra sesión. También se cerró el endurecimiento de evidencia GPS de 0.3.11 que había quedado pendiente. Estas piezas quedan listas para que el compañero conecte sus controles; no añaden un formulario, login ni navegación por su cuenta, no inician GPS automáticamente y no habilitan guardado real o consulta.

La [validación de 0.4.0a](VALIDACION.md) aprobó **266 pruebas ejecutadas**: 171 JVM de app, 64 instrumentadas de datos y 31 del laboratorio Android, con **57 definiciones nuevas** y sin fallos, errores u omisiones. **124 casos se reutilizaron** mediante `UP-TO-DATE` (86 de dominio y 38 JVM de datos); no son ejecuciones nuevas. Debug/release compilan y la firma debug v2 es válida. Lint terminó sin errores: **19 advertencias visuales del conjunto integrado** en app, dos por variante técnica y ninguna en datos. Los resultados de 0.3.10 y 0.3.11 conservan sus propios antecedentes. Clave Google Maps, lectura GPS física, mapa renderizado y aceptación del flujo completo siguen pendientes. El avance conserva **7/28 hitos = 25%**, sin sumar dos fases técnicas como dos requisitos completos del PDF. Véanse [fase 7: borrador](FASE_7.md), [acoplamiento](ACOPLAR_INTERFAZ.md) y [estado por hitos](AVANCE.md).

La revisión de GitHub incorporó `main` del compañero (`b665a56a`) mediante el merge local `433b3fb`. Los layouts de inicio y crear cuenta coinciden con el remoto; herramientas conserva su rediseño y añade el bloque solicitado del ejemplo en memoria antes de Volver, manteniendo los IDs del compañero. El descriptor de Gradle quedó coordinado a **JDK 27**, sin URLs de JVM 25; el bytecode Android permanece en JVM 17. El conjunto integrado aprobó la validación registrada para esta entrega.

Los siguientes bloques conservan los cortes locales anteriores y sus límites; no describen el estado de publicación actual.

Antecedente de la preparación **0.3.11 / código Android 18 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el endurecimiento de la evidencia compartida después de `Ready`: al publicar o consultar una lectura se exige permiso preciso FINE y GPS habilitado. Si esa comprobación detecta GPS apagado o falla, descarta la evidencia; reactivarlo no recupera la lectura anterior y requiere otra captura explícita. El radio de 5 km, la precisión, vigencia y rechazo de simulaciones conservan sus reglas.

Producción comparte una misma `AndroidPhoneLocationSource` entre `PhoneLocationRefresh` y `PhoneLocationEvidence`; el guard de SQLite usa ese mismo lector protegido. Antes se comprobaba FINE al consultar el fix, pero faltaba exigir también GPS habilitado después de `Ready`; no era un fallo matemático ni una evasión del radio de `NearbyIncidentPolicy`. La [validación de 0.3.11](VALIDACION.md) se cerró antes de preparar 0.4.0a: **247 pruebas ejecutadas y aprobadas** (121 JVM de app, 38 JVM de datos, 64 instrumentadas de datos y 24 del laboratorio), con **16 definiciones nuevas**. Dos regresiones fallaron contra la base 0.3.10 y aprobaron tras corregir la relectura de GPS; el estado del proveedor era controlado. Debug/release compilan, firma debug v2 válida y lint sin errores (13 advertencias previas de app, dos por variante técnica; datos sin incidencias). Las 86 de dominio no se ejecutaron de nuevo. Estos resultados pertenecen a 0.3.11, no al corte 0.4.0a. La interfaz y login del compañero, accesos ocultos a datos reales y consulta, clave Maps y GPS físico conservan su estado. **Fase 6, 7/28 = 25%**, local y Unreleased, sin acciones Git o publicación.

Antecedente **0.3.10 / código Android 17 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el [flujo de ubicación en Android](FLUJO_UBICACION_ANDROID.md): permiso, renovación explícita, evidencia y confirmación dentro de 5 km. El módulo técnico `verification/location`, `testOnly`, reutiliza las fuentes productivas de `app/.../maps` sin copiarlas y conserva sus tres variantes de diálogo nativo; añade `flow` con paquete propio y fuentes controladas. Las lecturas sintéticas solo pertenecen al laboratorio; la pantalla productiva obtiene la evidencia desde la fábrica de `CitizenSecurityApplication`. El compañero conserva el diseño y los eventos de sus controles: esta comprobación no los conecta ni modifica el login.

El corte 0.3.10 ejecutó y aprobó **21 casos Android distintos en API 37: tres diálogos de permiso extendidos, 16 de flujo y dos de fuente GPS**, con **18 definiciones nuevas**. Los dos de GPS comprueban registro nativo con cancelación antes del callback y rechazo de una `Location` marcada como simulada mediante una fuente controlada; no obtuvieron un fix físico. Debug/release compilan; firma debug v2 válida. Lint terminó sin errores: app conserva 13 advertencias anteriores y cada variante del laboratorio dos (`DataExtractionRules` y `MissingApplicationIcon`). Las **121 JVM de app quedaron `UP-TO-DATE`** como evidencia previa, sin nueva ejecución. [Validación](VALIDACION.md) separa comandos, repeticiones y límites.

Como antecedente, **0.3.9 aprobó 121 JVM de app y tres flujos nativos de permiso**, compilaciones debug/release y lint sin errores con 13 advertencias anteriores. Las 47 JVM nuevas pertenecen a 0.3.8. Conceder permiso no inicia GPS, confirma un punto ni guarda reportes. GPS físico, mapa renderizado, conexión de los controles y clave Google Cloud siguen pendientes. Guardado real y consulta conservan sus accesos ocultos. En aquel corte no había acciones de publicación autorizadas. Avance: **7/28 = 25%**.

## Borrador editable de 0.4.0a

El formulario y el mapa comparten `ReportDraftViewModel` con **el mismo `ViewModelStoreOwner` y clave**. En una `ComponentActivity`, su fábrica utiliza los `CreationExtras` de Android para obtener `SavedStateHandle`:

```kotlin
val borrador = ViewModelProvider(this, ReportDraftViewModel.factory())[
    ReportDraftViewModel::class.java
]
```

Si el mapa está en un fragmento de esa actividad, obtiene el mismo modelo desde el propietario compartido; no crea uno con un propietario o clave distintos. Una actividad diferente no comparte el `ViewModelStore` automáticamente. No se colocan modelos, puentes ni tokens en `Application`, `Bundle` o una `MapView` para simular esa relación; el compañero define su navegación conservando un propietario común para este flujo.

La pantalla conecta cambios a `setType`, `setPriority`, `setDescription`, `setOccurredAt` y `setLocationReference`. Los primeros, la fecha y `reset()` devuelven `Boolean` para indicar que la edición se aceptó; texto devuelve `ReportDraftEditResult.Accepted`, `Closed` o `Rejected(field, message)`. Descripción admite **1.000 puntos Unicode** y referencia **200**, contando espacios exteriores; el modelo rechaza sin truncar. Ante `Rejected`, el control conserva el texto escrito y representa `state.inputErrors`, mientras el modelo conserva el último valor aceptado. No debe sobrescribir automáticamente la entrada rechazada con ese valor anterior. Una edición válida del campo elimina su error.

La fecha es un `Instant` que la pantalla convierte desde su fecha y zona; el modelo no inventa una hora para completar el formulario. `review(now)` devuelve `ReportDraftReview.Valid(NewReport)` o `Invalid(Map<ReportDraftField, String>)`. Revisa los campos y bloquea una selección del mapa todavía pendiente; `Valid` no acredita evidencia GPS actual ni inserta en SQLite. Observar `state` representa el borrador, sin llamar a guardar ni a obtener ubicación.

## Ir al mapa y volver sin perder los campos

Por un gesto explícito, `beginLocationSelection()` entrega `ReportLocationSelectionRequest?` con token de identidad y `originalLocation`. Una segunda apertura invalida el token anterior. El flujo mantiene los mismos modelos y registro de permisos de la Activity: cierra el binding del mapa anterior y llama a **`flow.beginSelection(originalLocation)`** para iniciar una sesión nueva en el modelo retenido, únicamente con el dueño `RESUMED`. Esta operación invalida/cancela la captura anterior y exige otra renovación explícita; no registra de nuevo permisos ni inicia GPS. Después crea otro `IncidentLocationMapBinding(googleMap, flow)` para la vista de esa apertura.

```kotlin
val apertura = borrador.beginLocationSelection() ?: return
mapBinding?.close()
mapBinding = null
if (!flow.beginSelection(apertura.originalLocation)) {
    borrador.cancelLocationSelection(apertura.token)
    return
}
// Conservar apertura en memoria como la apertura vigente de esta vista.
// Al tener GoogleMap, crear IncidentLocationMapBinding(googleMap, flow).

fun recibirConfirmacion(resultado: NearbyLocationConfirmation.Confirmed) {
    val aplicada = borrador.applyLocationSelection(apertura.token, resultado)
    if (aplicada) volverAlFormulario()
}

fun cancelarSeleccion() {
    if (borrador.cancelLocationSelection(apertura.token)) {
        modeloUbicacion.cancel()
        mapBinding?.close()
        volverAlFormulario()
    }
}
```

El fragmento muestra los eventos que el compañero conectará; no añade controles ni navegación. La pantalla conserva su apertura vigente y descarta callbacks de controles/vistas anteriores **antes** de llamar a `flow.confirm()` o modificar el modelo de ubicación. El token del borrador protege la aplicación del resultado; no autentica el evento que lo generó. Al regresar, cierra el binding de esa vista y conserva el puente de permisos de la Activity. `applyLocationSelection` aplica únicamente coordenadas y conserva referencia y otros campos editados entretanto. Exige token vigente, coordenadas finitas completas y distancia válida; el DTO `Confirmed` no es una credencial. Un resultado de otra apertura, modelo o anterior a restaurar el propietario se ignora. Cancelar conserva las coordenadas anteriores y las ediciones. `reset()` invalida la selección y empieza un borrador vacío.

`SavedStateHandle` conserva primitivos del borrador y errores de entrada cuando Android guarda estado para recrear su propietario; ese estado depende de la tarea y no es almacenamiento permanente, según [Android Developers](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate). Nuestro modelo no guarda el token, captura pendiente, permiso ni evidencia GPS autorizada. Después de recuperar el modelo no hay selección pendiente ni captura automática. Las coordenadas restauradas siguen siendo datos del borrador y deberán pasar por las comprobaciones actuales de ubicación antes de guardar. Valores restaurados incompatibles o inválidos producen errores de campo; no se convierten en datos aceptados silenciosamente.

Véase [Fase 7](FASE_7.md) para el alcance y [Proximidad](PROXIMIDAD_REPORTES.md) para la comprobación antes de escribir. El botón de guardar real y la consulta continúan ocultos; el ejemplo ficticio usa su propio modelo y no recibe este borrador.

## Puente de los controles de ubicación

`IncidentLocationFlowBinding.forActivity(activity, permissionModel, locationModel)` se crea **una vez e incondicionalmente en `onCreate`, antes de `STARTED` y en orden estable**. Los modelos se obtienen mediante `ViewModelProvider`; el puente pertenece a esa instancia de actividad y se crea de nuevo al recrearla. No se conserva en un ViewModel ni en una `MapView` y no se registra junto a otro puente de permisos del mismo flujo.

El puente consulta permisos en `ON_RESUME`, detiene la captura en `ON_STOP` y se cierra en `ON_DESTROY`. Los eventos se usan en el hilo principal. `beginSelection(originalLocation)` inicia otra selección en el mismo modelo y puente, invalidando la captura anterior y exigiendo renovar; devuelve `false` con dueño inactivo. `requestPermission(explanationAccepted)` devuelve `LocationPermissionAction`; `select(lat, lng)` y `refreshLocation()` devuelven `Boolean` indicando únicamente que entregaron un gesto con el dueño en `RESUMED`, sin asegurar validez del punto o éxito de captura. `confirm()` devuelve el resultado del modelo o `null` si el dueño está inactivo. Conceder permiso, volver a la pantalla o recuperar estado no captura GPS automáticamente.

`close()` es idempotente, detiene la captura y cierra el registro de permisos sin cancelar la selección retenida al recrear. El cierre de la vista del mapa usa `mapBinding.close()` y `flow.onMapViewClosed()`, que detiene captura sin cerrar los permisos de la Activity. Al volver se necesita una captura nueva y explícita. El modelo conserva sus reglas de cercanía, vigencia, precisión y simulación. Un solo puente activo por `Lifecycle`; registros duplicados, tardíos o de dueño destruido se rechazan. [Google Maps](GOOGLE_MAPS.md) y [Obtener ubicación](OBTENER_UBICACION.md) explican la conexión visual pendiente.

## Contrato conservado para el futuro guardado

Los ejemplos siguientes documentan el modelo de guardado ya existente. No activan su acceso en la aplicación ni sustituyen la revisión de ubicación actual por una confirmación anterior.

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

Este fragmento conserva el contrato `NewReport` de la base anterior: recibe el par de coordenadas como datos y ese contrato de dominio no depende del SDK. En la actualización actual, Google Maps sí tiene su SDK real y bindings incorporados; su clave, vista y controles conectados siguen pendientes. El ejemplo describe el futuro botón de guardado, todavía oculto: el login no llama a `save` ni tiene listeners nuevos para insertar reportes.

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

## Antecedente de verificación del puente

El 3 de octubre de 2026 se comprobaron el puente y su integración técnica: 6 pruebas JVM del ViewModel aprobadas, 9 instrumentadas de SQLite ejecutadas y aprobadas en API 37, lint sin errores y APK `0.2.1` generado. Lint de datos no tuvo incidencias; `app` conserva 13 advertencias visuales. Las 32 pruebas de dominio mantuvieron su resultado anterior mediante `UP-TO-DATE`, sin una nueva ejecución de esos casos. La evidencia completa y el artefacto están en [Validación](VALIDACION.md).

Los XML registran las 6 pruebas del puente a `2026-10-03T16:48:36.500Z` y las 9 instrumentadas a `2026-10-03T16:47:13Z`. El resultado reutilizado del dominio conserva `2026-10-03T05:39:56.971Z`.

Aquella comprobación fue técnica; no probó un flujo visual completo ni una rotación real de la futura pantalla. En ese corte `0.2.1` aún no tenía etiqueta ni Release; su publicación posterior se conserva en [Versiones](RELEASES.md). El formulario, sus listeners, el mapa y la autenticación continúan pendientes del compañero. Fase 3 y `0.3.0` permanecen en `Unreleased`, con entrega y aceptación funcional pendientes.
