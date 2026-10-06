# Fase 7: borrador editable y regreso del mapa

La **0.4.0a / código Android 19**, del **6 de octubre de 2026**, completa este segundo paso técnico junto con el puente de ubicación de fase 6. El usuario solicitó terminar lo pendiente, avanzar dos fases y publicar esta actualización como Release oficial. La implementación y sus comprobaciones técnicas están completadas; [Validación](VALIDACION.md) registra resultados efectivos, y [Versiones](RELEASES.md) enlaza el estado de la Release.

Esta fase corresponde al apartado **4.2 Reporte de emergencia o robo** del PDF y conecta la entrada del reporte con **4.3 Geolocalización** y **4.5 Mapa de incidentes**. La fase técnica 7 no equivale al apartado 4.7 Notificaciones: el PDF enumera funciones del producto, mientras estas fases organizan nuestro trabajo. El avance por requisitos conserva **7/28 hitos = 25%**, según [Avance](AVANCE.md).

## Resultado del paso

`ReportDraftViewModel` administra un borrador con tipo, prioridad, descripción, fecha y ubicación. El compañero puede conectar esos campos a su formulario sin que la pantalla escriba SQLite. La selección del mapa es una operación aparte: abrirla conserva los campos del borrador y su ubicación original; confirmar devuelve una ubicación; cancelar conserva la ubicación anterior. No se crea un `Report` ni un folio en este paso.

Cada apertura sigue `draft.beginLocationSelection()` → cerrar el binding del mapa anterior → `flow.beginSelection(request.originalLocation)` → crear `IncidentLocationMapBinding(googleMap, flow)`. Se mantienen los mismos modelos y puente de permisos de la Activity. `beginSelection` solo se acepta con dueño `RESUMED`, invalida/cancela la captura previa y exige renovar por otro gesto explícito; no inicia GPS ni registra de nuevo el permiso. Si el flujo no puede abrir, se cancela el token del borrador para no dejar una selección pendiente. Al regresar se cierra el binding de la vista, conservando los campos y el registro de la Activity.

`SavedStateHandle` permite conservar valores simples del borrador cuando Android proporciona estado guardado para recrear su propietario. Esto no es una copia de seguridad ni garantiza recuperación después de cierre forzado, eliminación de la tarea o borrado de datos, según el [contrato de estado guardado de Android](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate). Nuestro modelo no restaura tokens de selección, permisos, captura pendiente ni evidencia GPS como autorización.

Un token pertenece al modelo y a una selección vigente. Un resultado tardío de otra selección, de otro modelo o anterior a la recuperación no puede modificar la ubicación. Los campos recuperados siguen siendo entrada editable, sujeta a validación antes de avanzar; unas coordenadas conservadas no acreditan permiso ni cercanía actual.

## Contrato de conexión

La [fuente del modelo](../app/src/main/kotlin/com/example/citizensecurity/report/ReportDraftViewModel.kt) ofrece estas operaciones; el cierre de pruebas del nuevo corte está registrado en [Validación](VALIDACION.md):

| Operación | Propósito |
| --- | --- |
| `state` | Leer el estado del borrador, sin volver a enviarlo automáticamente al observar. |
| `setType`, `setPriority`, `setOccurredAt` | Editar un campo; devuelve `Boolean` sin reconstruir los demás. |
| `setDescription`, `setLocationReference` | Editar texto; devuelve `Accepted`, `Rejected(field, message)` o `Closed`. |
| `beginLocationSelection()` | Entregar `ReportLocationSelectionRequest?` con token y ubicación original; invalida una apertura anterior. |
| `applyLocationSelection(token, Confirmed)` | Aplicar coordenadas de la selección vigente, preservando la referencia y otros campos; devuelve `Boolean`. |
| `cancelLocationSelection(token)` | Cerrar esa selección conservando coordenadas y ediciones; devuelve `Boolean`. |
| `review(now)` | Devolver `ReportDraftReview.Valid(NewReport)` o `Invalid(Map<ReportDraftField, String>)`, sin guardar. |
| `reset()` | Empezar otro borrador e invalidar resultados pendientes; devuelve `Boolean`. |

La fábrica `ReportDraftViewModel.factory()` usa `createSavedStateHandle()` y necesita los `CreationExtras` del propietario Android. Formulario y mapa comparten **el mismo propietario y clave** del ViewModel; dos actividades no comparten una instancia automáticamente. No se serializan tokens ni se conserva una actividad o puente en el modelo. La guía de [acoplamiento](ACOPLAR_INTERFAZ.md) ofrece ejemplos para el compañero.

Descripción admite 1.000 puntos Unicode y referencia 200, incluidos espacios exteriores, con rechazo sin truncamiento. Ante `Rejected`, el control debe conservar la entrada rechazada y representar el error; el modelo conserva el último texto aceptado. Una edición válida elimina su error. La recuperación verifica versión y tipos del estado guardado, fecha, texto y pares de coordenadas; valores incompatibles producen errores de entrada que bloquean la revisión hasta corregirlos o reiniciar el borrador.

La pantalla y sus controles corresponden al compañero. Crear la fábrica, observar el estado, restaurarlo o abrir el mapa no inicia captura GPS ni guardado. Un `Valid` acredita campos válidos en ese momento, sin otorgar permiso, cercanía actual ni autorización para insertar.

## Confirmación y guardado separados

El flujo de ubicación mantiene evidencia del dispositivo independiente del marcador y aplica **distancia aproximada + precisión <= 5.000 m**, permiso preciso, GPS habilitado, vigencia y rechazo de simulaciones. El borrador recibe el resultado técnico de ese flujo; no considera que su tipo o token autorice una escritura por sí solo.

La futura activación del guardado utiliza el repositorio protegido de `CitizenSecurityApplication`, que vuelve a comprobar evidencia y cercanía antes de escribir y confirmar. Permanecen ocultos los accesos a guardado de datos reales y consulta; el ejemplo fijo en memoria sigue separado. [Proximidad](PROXIMIDAD_REPORTES.md), [obtener ubicación](OBTENER_UBICACION.md) y [Google Maps](GOOGLE_MAPS.md) conservan esos contratos.

## Comprobación y entrega

El borrador aprobó **29 pruebas JVM y dos Android nuevas**: edición sin pérdida de campos, cancelación y confirmación del mapa, rechazo de tokens vencidos, recuperación de valores sin autorización GPS, límites de texto sin truncamiento y errores de validación. Una prueba nativa recrea la Activity y conserva el modelo; la otra utiliza SavedStateRegistry y Parcel con dueño y ViewModelStore nuevos. Esta recuperación controlada no prueba muerte real del proceso ni persistencia después de cierre forzado.

La entrega completa aprobó **266 pruebas ejecutadas**, incluidas las del borrador, con **57 definiciones nuevas**; 124 resultados de dominio/datos fueron reutilizados. Debug/release, firma debug v2 y lint sin errores aprobaron. App conserva 19 advertencias visuales del conjunto integrado y el laboratorio dos por variante. No se suma otra vez esta fase al total ni se usa el número de pruebas como aceptación funcional.

Para aceptar el flujo completo falta conectar el formulario y los controles del compañero, configurar la clave Maps, renderizar el mapa y comprobar GPS físico. Las lecturas controladas del laboratorio solo validan el comportamiento técnico; el proyecto sigue siendo académico y no envía incidentes a autoridades.
