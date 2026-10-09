# Fase 8: coordinar el borrador y la ubicación

Trabajo **Unreleased, local**, solicitado como siguiente fase después de la oficial **0.4.0a / código Android 19**, el **6 de octubre de 2026**. No se asigna otro número de versión ni se cambia lo publicado. Esta petición permite implementar y verificar el paso, sin autorizar commit, subida, integración, etiqueta, Release o borrado. Implementación y comprobación técnica completadas; se cerró la documentación interrumpida por el límite de uso. [Validación](VALIDACION.md) registra resultados efectivos, separados de los antecedentes de 0.4.0a. Aceptación del flujo visual pendiente.

Corresponde a **4.2 Reportes, 4.3 Geolocalización y 4.5 Mapas** del PDF. La fase técnica 8 organiza una mejora de sus contratos; no añade una octava función al documento ni completa otro hito por sí sola. [Avance](AVANCE.md) conserva **7/28 = 25%**. El compañero mantiene formulario, mapa, diseño de controles, navegación y login.

## Problema y resultado

La composición de 0.4.0a protege la aplicación de coordenadas al borrador mediante un token. La pantalla tenía que comprobarlo también **antes** de confirmar o cancelar el flujo de ubicación: si un control viejo confirmaba primero, podía cerrar la selección nueva aunque después el borrador rechazara ese resultado. Una vista anterior tampoco debe retirar el listener o detener la captura de su reemplazo.

`ReportLocationFlowBinding` reúne el borrador y el flujo de ubicación en un contrato que hace esas comprobaciones antes de los efectos. `ReportLocationMapBinding` representa una vista concreta dentro de la apertura. `prepareMap(token)` reserva la conexión antes de solicitar la instancia asíncrona; su handle valida la entrega posterior. El coordinador mantiene un solo attachment y cierra el anterior antes de sustituirlo; el attachment anterior queda inactivo incluso si se conecta el mismo mapa con el mismo token.

Al recrear la Activity, `currentSelection` permite recuperar la apertura del borrador retenido sin fabricar otra. Se conservan campos y pin, mientras el paso por `ON_STOP` cancela la captura y exige otra renovación explícita. Un modelo nuevo restaurado mediante `SavedStateHandle` recupera datos, pero no token, captura, permiso ni evidencia autorizada.

## Contrato para el compañero

Crea `ReportLocationFlowBinding(owner, flow, draft)` en el hilo principal y **antes de `STARTED`**, después del único `IncidentLocationFlowBinding` de esa Activity. Ambos usan el mismo `LifecycleOwner`; el borrador y el modelo de ubicación proceden del mismo propietario retenido y sus claves habituales. Al recrear crea bindings nuevos con esos mismos modelos. Los bindings pertenecen a la Activity o vista durante su vida; no conserves Activity, MapView o bindings en ViewModels, `SavedStateHandle`, `Bundle` o `Application`. Todas sus operaciones, incluidas confirmación, attach y cierre, se ejecutan en el hilo principal. [Acoplar interfaz](ACOPLAR_INTERFAZ.md) muestra la composición.

| Operación | Comportamiento |
| --- | --- |
| `currentSelection` | Leer la apertura vigente para representar la pantalla recreada; no inicia otra selección ni captura. |
| `openSelection()` | Abrir por gesto visible y devolver la petición con token y ubicación original; invalida la apertura anterior y exige renovar. |
| `select(token, latitude, longitude)` | Entregar el punto solo para la apertura vigente y dueño visible. |
| `requestPermission(token, explanationAccepted = false)` | Solicitar COARSE/FINE por gesto; ningún resultado inicia GPS. |
| `refreshLocation(token)` | Renovar por gesto separado; conserva los límites y estados del flujo actual. |
| `confirm(token)` | Confirmar mediante la evidencia actual y aplicar coordenadas al borrador si acepta; no guarda. |
| `cancelSelection(token)` | Cancelar la apertura vigente conservando coordenadas originales y ediciones. |
| `prepareMap(token)` | Reservar y devolver el attachment antes de solicitar el mapa asíncrono; cierra el anterior y rechaza una apertura inactiva. |
| `ReportLocationMapBinding.attachMap(googleMap)` | Conectar la entrega asíncrona solo si esa reserva de vista sigue vigente; devuelve `Boolean`. |
| `attachMap(token, googleMap)` | Conectar directamente un mapa ya obtenido; no sustituye la reserva previa requerida para callbacks asíncronos. |
| `ReportLocationMapBinding.close()` | Retirar su listener y detener su captura; un handle anterior no afecta al vigente. |
| `close()` | Cerrar el coordinador de esa Activity de forma idempotente; destruir su dueño lo cierra y conserva la apertura del borrador retenido. |

Cada control conserva el coordinador de su dueño y token de su apertura. Para `getMapAsync`, reserva antes mediante `prepareMap(token)` y captura **ese handle**; el callback llama a `handle.attachMap(map)`. Una nueva vista reserva otro handle y vuelve inerte la entrega anterior incluso con el mismo token. Reservar y adjuntar el mapa puede ocurrir en `CREATED` o `STARTED`; no entrega un gesto ni obtiene GPS. Los controles interactivos se aceptan con dueño `RESUMED`.

Los controles llaman al coordinador, sin confirmar/cancelar directamente los modelos ni duplicar la aplicación del resultado. `false`, `null` o una acción inactiva no son éxito ni motivo para navegar. El attachment tiene uso exclusivo del listener de clicks del mapa durante su vida. No se mezcla este coordinador con listeners/bindings anteriores del mismo mapa.

Si el usuario limpia el formulario mientras hay una selección pendiente, primero `cancelSelection(token)` y solo cuando devuelve `true`, `draft.reset()`. Un control de otra apertura no reinicia directamente el borrador. `reset()` por sí solo modifica los campos, pero no reemplaza la cancelación del flujo GPS.

## Confirmar y guardar siguen separados

La confirmación obtiene el resultado del flujo actual, sin recibir coordenadas autorizadas o un DTO `Confirmed` fabricado por la pantalla. Si acepta, aplica solo el par de coordenadas y preserva la referencia, categoría, prioridad, descripción y fecha **actuales** del borrador. Una respuesta bloqueada mantiene la selección para que el usuario pueda corregirla o renovar. Un evento de otra apertura no llega a modificar el flujo vigente.

El radio sigue siendo **5 km alrededor del teléfono**, con evidencia independiente del pin: FINE y GPS habilitado al usarla, precisión finita entre 0 y 100 m, vigencia monotónica no futura de hasta 120 s, lectura no marcada como simulada y distancia aproximada más precisión menor o igual a 5.000 m. La captura sigue siendo explícita, cancelable y limitada por el tiempo de espera existente.

El futuro guardado real deberá usar el repositorio protegido de `CitizenSecurityApplication` y volver a comprobar la evidencia antes de escribir. Esta fase no abre SQLite, crea un folio ni activa consulta. El único ejemplo visible sigue usando datos ficticios fijos y memoria, separado del borrador. Tampoco implementa nuevas pantallas, autenticación, Cloud, fotografías, servidor o notificaciones.

## Comprobación y revisión

La comprobación de este corte aprobó **226 pruebas ejecutadas: 192 JVM de app y 34 Android del flujo**, sin fallos, errores u omisiones. Se añadieron **27 definiciones** (21 JVM y seis Android): controles de otra apertura, reserva/carga fuera de orden con el mismo token, clicks/cierres anteriores, cierre después de reset durante captura, recreación con selección pendiente, cancelación, respuesta GPS tardía y confirmación que conserva ediciones. Lecturas controladas y recreación de Activity; no acreditan GPS físico, mapa renderizado ni muerte real de proceso.

Debug/release compilan, firma debug v2 válida y lint sin errores: 19 advertencias anteriores en app y dos del laboratorio. App instalada 0.3.1/código 8, doce APK archivados, fuentes visuales, dominio/datos, Gradle, HEAD e índice conservados. Dominio/datos y los tres diálogos separados no se ejecutaron de nuevo. Los artefactos técnicos de fase 8 están separados de la oficial 0.4.0a, aunque conservan por ahora el metadato Android 0.4.0a/código 19.

Los resultados anteriores de 0.4.0a permanecen con su propio corte. Para aceptar el flujo completo faltan controles del compañero conectados, clave Maps habilitada, renderizado, GPS físico y revisión del usuario. Mejorar el contrato no permite afirmar ausencia total de errores ni un requisito del PDF completo.
