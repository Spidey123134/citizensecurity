# Obtener y renovar la ubicación del teléfono

Versión oficial **0.4.0a / código Android 19**, del **6 de octubre de 2026**, con validación completada. El usuario pidió revisar GitHub, incorporar el trabajo del compañero y subir/publicar esta actualización como Release; su estado en GitHub se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a). Esta autorización cubre esta entrega, sin autorizar automáticamente siguientes publicaciones.

Se completaron dos pasos técnicos: **fase 6A**, un puente para conectar permiso, captura explícita y ciclo de vida de la pantalla; y **fase 7**, un borrador editable que conserva los campos al ir al mapa y regresar, con recuperación mediante `SavedStateHandle` y protección ante resultados de otra sesión. También se cerró el endurecimiento de evidencia GPS de 0.3.11 que había quedado pendiente. Estas piezas quedan listas para que el compañero conecte sus controles; no añaden un formulario, login ni navegación por su cuenta, no inician GPS automáticamente y no habilitan guardado real o consulta.

La [validación de 0.4.0a](VALIDACION.md) aprobó **266 pruebas ejecutadas**: 171 JVM de app, 64 instrumentadas de datos y 31 del laboratorio Android, con **57 definiciones nuevas** y sin fallos, errores u omisiones. **124 casos se reutilizaron** mediante `UP-TO-DATE` (86 de dominio y 38 JVM de datos); no son ejecuciones nuevas. Debug/release compilan y la firma debug v2 es válida. Lint terminó sin errores: **19 advertencias visuales del conjunto integrado** en app, dos por variante técnica y ninguna en datos. Los resultados de 0.3.10 y 0.3.11 conservan sus propios antecedentes. Clave Google Maps, lectura GPS física, mapa renderizado y aceptación del flujo completo siguen pendientes. El avance conserva **7/28 hitos = 25%**, sin sumar dos fases técnicas como dos requisitos completos del PDF. Véanse [fase 7: borrador](FASE_7.md), [acoplamiento](ACOPLAR_INTERFAZ.md) y [estado por hitos](AVANCE.md).

La revisión de GitHub incorporó `main` del compañero (`b665a56a`) mediante el merge local `433b3fb`. Los layouts de inicio y crear cuenta coinciden con el remoto; herramientas conserva su rediseño y añade el bloque solicitado del ejemplo en memoria antes de Volver, manteniendo los IDs del compañero. El descriptor de Gradle quedó coordinado a **JDK 27**, sin URLs de JVM 25; el bytecode Android permanece en JVM 17. El conjunto integrado aprobó la validación registrada para esta entrega.

Los siguientes bloques conservan los cortes locales anteriores y sus límites; no describen el estado de publicación actual.

Antecedente de la preparación **0.3.11 / código Android 18 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el endurecimiento de la evidencia compartida después de `Ready`: al publicar o consultar una lectura se exige permiso preciso FINE y GPS habilitado. Si esa comprobación detecta GPS apagado o falla, descarta la evidencia; reactivarlo no recupera la lectura anterior y requiere otra captura explícita. El radio de 5 km, la precisión, vigencia y rechazo de simulaciones conservan sus reglas.

Producción comparte una misma `AndroidPhoneLocationSource` entre `PhoneLocationRefresh` y `PhoneLocationEvidence`; el guard de SQLite usa ese mismo lector protegido. Antes se comprobaba FINE al consultar el fix, pero faltaba exigir también GPS habilitado después de `Ready`; no era un fallo matemático ni una evasión del radio de `NearbyIncidentPolicy`. La [validación de 0.3.11](VALIDACION.md) se cerró antes de preparar 0.4.0a: **247 pruebas ejecutadas y aprobadas** (121 JVM de app, 38 JVM de datos, 64 instrumentadas de datos y 24 del laboratorio), con **16 definiciones nuevas**. Dos regresiones fallaron contra la base 0.3.10 y aprobaron tras corregir la relectura de GPS; el estado del proveedor era controlado. Debug/release compilan, firma debug v2 válida y lint sin errores (13 advertencias previas de app, dos por variante técnica; datos sin incidencias). Las 86 de dominio no se ejecutaron de nuevo. Estos resultados pertenecen a 0.3.11, no al corte 0.4.0a. La interfaz y login del compañero, accesos ocultos a datos reales y consulta, clave Maps y GPS físico conservan su estado. **Fase 6, 7/28 = 25%**, local y Unreleased, sin acciones Git o publicación.

Antecedente **0.3.10 / código 17 — Unreleased**, local, del **5 de octubre de 2026**. Comprobó el [flujo de ubicación en Android](FLUJO_UBICACION_ANDROID.md): permiso, renovación explícita, evidencia y confirmación dentro de 5 km. `verification/location`, `testOnly`, reutiliza las fuentes productivas de `app/.../maps` sin copiarlas y mantiene paquetes separados del producto. Conserva sus tres variantes de diálogo nativo y añade `flow` con fuentes controladas. Esas lecturas sintéticas solo existen en el laboratorio; no demuestran captura GPS física ni habilitan guardado productivo. Las pruebas de `AndroidPhoneLocationSource` comprueban por separado registro nativo y cancelación de la solicitud antes de recibir una lectura.

El corte 0.3.10 ejecutó y aprobó **21 casos Android distintos en API 37: tres diálogos de permiso extendidos, 16 de flujo y dos de fuente GPS**, con **18 definiciones nuevas**. Los dos de GPS comprueban registro nativo con cancelación antes del callback y rechazo de una `Location` marcada como simulada a través de una fuente controlada; no obtuvieron un fix físico. La espera agotada, GPS controlado apagado, lectura inválida, salida y recreación de Activity se comprueban con la fuente del laboratorio. Debug/release compilan; firma debug v2 válida. Lint terminó sin errores: app conserva 13 advertencias anteriores y cada variante del laboratorio dos (`DataExtractionRules` y `MissingApplicationIcon`). Las **121 JVM de app quedaron `UP-TO-DATE`** como evidencia previa, sin nueva ejecución. [Validación](VALIDACION.md) distingue comandos, repeticiones y límites.

Como antecedente, **0.3.9 aprobó 121 JVM de app y tres flujos nativos de permiso**, compilaciones debug/release y lint sin errores con 13 advertencias anteriores; las 47 JVM nuevas pertenecen a 0.3.8. La [guía de permisos Android](PRUEBAS_PERMISOS_ANDROID.md) conserva sus límites. Preciso, aproximado y ausente son estados distintos; conceder permiso no aporta una lectura GPS ni inicia su renovación automáticamente. Continúan el límite productivo de 15 segundos y la política de 5 km. GPS físico, mapa renderizado, clave Maps y conexión de los controles siguen pendientes; la interfaz y login del compañero permanecen bajo su responsabilidad.

El contrato de captura conservado de **0.3.7 / código 14 — Unreleased** se mantiene. Continúa fase 6 y la regla de 5 km. `PhoneLocationRefresh` conserva su contrato público y captura explícita; fallos ordinarios del proveedor, permiso o reloj se convierten en estados controlados y limpian evidencia. La cancelación se propaga. Después de publicar se revalidan permiso, GPS y vigencia para no reutilizar una autorización que dejó de ser válida.

Si `removeUpdates` falla con una excepción ordinaria, la fuente realiza un único reintento inmediato; si ambos fallan, la lectura no se acepta y los callbacks tardíos se descartan. Una pérdida de permiso no se reintenta. No se puede garantizar que Android haya retirado el listener si rechaza ambas llamadas: no se declara limpieza nativa exitosa en ese caso. Un error al retirar tampoco oculta el error original del registro.

El flujo completo usa [IncidentLocationViewModel](../app/src/main/kotlin/com/example/citizensecurity/maps/IncidentLocationViewModel.kt): conserva el pin, cancela al salir, ignora resultados viejos y exige renovar después de `onStop` o de un intento fallido. En 0.4.0a, [IncidentLocationFlowBinding](../app/src/main/kotlin/com/example/citizensecurity/maps/IncidentLocationFlowBinding.kt) conecta ese contrato al ciclo de vida y separa los gestos de permiso, selección, captura y confirmación. El modelo no inicia GPS ni guarda automáticamente. [Validación](VALIDACION.md) distingue pruebas del laboratorio y GPS físico, todavía pendiente. Interfaz y presentación de permisos siguen con el compañero.

## Conectar los eventos sin iniciar GPS automáticamente

Obtén `LocationPermissionViewModel` e `IncidentLocationViewModel` mediante `ViewModelProvider` y crea `IncidentLocationFlowBinding.forActivity(activity, permissionModel, locationModel)` una vez en `onCreate`, antes de `STARTED`, de forma incondicional y en orden estable. El puente pertenece a la actividad; no se conserva en un ViewModel o `MapView`. Cada recreación obtiene otro puente para el nuevo dueño. Mantén una sola solicitud/selección compartida, sin registrar además el puente anterior de permisos para ese flujo.

| Evento | Operación del puente |
| --- | --- |
| Abrir una selección del mapa por gesto | `beginSelection(originalLocation)`; reinicia la sesión en el mismo modelo, invalida/cancela la captura anterior y exige renovar. |
| Pulsar el control de permiso | `requestPermission(explanationAccepted)`; devuelve `LocationPermissionAction` sin capturar. |
| Tocar un punto | `select(latitude, longitude)`; entrega el gesto al modelo si el dueño está `RESUMED`. |
| Pulsar obtener/renovar ubicación | `refreshLocation()`; entrega el gesto explícito, y `model.state` expresa el resultado. |
| Pulsar confirmar | `confirm()`; devuelve el resultado del modelo o `null` con dueño inactivo. No guarda. |
| `ON_RESUME` | Consulta permisos actuales; no abre diálogo ni inicia captura. |
| `ON_STOP` | Detiene captura y exige otra renovación al volver, conservando el pin. |
| `ON_DESTROY` | Cierra el puente y su registro. |

`select` y `refreshLocation` devuelven `Boolean` para indicar que se entregó el gesto, sin acreditar validez del punto ni una lectura GPS lista. `close()` es idempotente y detiene la captura sin cancelar la selección retenida al recrear. El registro rechaza dueño destruido, alta tardía o puente duplicado sobre el mismo `Lifecycle`. Usa eventos en el hilo principal y observa el estado para representarlo, sin solicitar ubicación desde la colección.

El formulario puede conservar sus campos al ir y volver del mapa mediante [el borrador de fase 7](FASE_7.md). Sus coordenadas y texto recuperados no son evidencia del teléfono ni sustituyen las comprobaciones descritas abajo. Esta conexión técnica no añade controles a las pantallas del compañero.

## Contrato y composición

- `PhoneLocationSource` consulta `hasPrecisePermission()` e `isLocationEnabled()` y ofrece `suspend fun awaitFix(): DeviceLocationFix?`.
- `AndroidPhoneLocationSource` usa el contexto de aplicación y el proveedor `LocationManager.GPS_PROVIDER`. Espera una lectura mediante un listener y retira su suscripción al terminar o cancelarse. No conserva una `Activity`, no recurre a la última ubicación guardada y no se inicia como servicio en segundo plano.
- `PhoneLocationRefresh.refresh()` controla una solicitud a la vez mediante `Mutex`. Una segunda llamada devuelve `AlreadyRunning` sin reemplazar o cancelar la primera.
- `CitizenSecurityApplication` comparte una misma `AndroidPhoneLocationSource` entre `phoneLocationRefresh` y `phoneLocationEvidence`; también comparte el lector de evidencia con el guard del repositorio productivo. La composición es diferida: crear la aplicación u obtener esas propiedades no llama a `refresh()` automáticamente.
- Desde 0.3.11, `PhoneLocationEvidence` recibe dos lectores: permiso preciso y GPS habilitado, ambos obligatorios. Los comprueba al publicar y consultar; si el permiso es insuficiente no consulta el proveedor. Si la comprobación devuelve falso o falla, descarta la lectura en memoria; volver a habilitar GPS no la recupera. Solo una captura nueva puede publicar otra evidencia. El lector no inicia una solicitud GPS ni cambia ajustes del teléfono.

Fuentes: [controlador](../core/data/src/main/kotlin/com/example/citizensecurity/data/PhoneLocationRefresh.kt), [fuente Android](../core/data/src/main/kotlin/com/example/citizensecurity/data/AndroidPhoneLocationSource.kt), [evidencia](../core/data/src/main/kotlin/com/example/citizensecurity/data/PhoneLocationEvidence.kt) y [composición](../app/src/main/kotlin/com/example/citizensecurity/CitizenSecurityApplication.kt).

## Secuencia de una renovación

1. Descartar la evidencia anterior al iniciar una solicitud nueva. Una renovación fallida no mantiene una lectura anterior como autorización.
2. Comprobar permiso preciso y GPS habilitado antes de solicitar una posición.
3. Esperar una lectura durante **15 segundos** por defecto. `timeoutMillis` permite configurar un límite positivo; este valor es técnico y no fue elegido por el usuario.
4. Volver a comprobar permiso y GPS después de la espera.
5. Validar la lectura mediante `NearbyIncidentPolicy.checkDeviceLocation`: coordenadas válidas, precisión finita entre **0 y 100 m**, tiempo monotónico no futuro con antigüedad de hasta **2 minutos**, y ausencia de marca de simulación.
6. Conservar únicamente una lectura aceptada en `PhoneLocationEvidence`. La evidencia vive en memoria; no crea archivos ni se recupera después de reiniciar el proceso.

La fuente devuelve **la primera lectura recibida**. Si es imprecisa, antigua o está marcada como simulada, el controlador devuelve `Rejected`; hace falta otro intento explícito. No espera dentro de los 15 segundos hasta alcanzar mejor precisión. La cancelación externa se propaga como cancelación, limpia la evidencia y libera la solicitud para un intento posterior. El tiempo agotado cancela la espera y limpia cualquier evidencia, incluso si coincidió con su publicación en memoria.

## Resultados para la futura pantalla

| Resultado | Significado y conexión prevista |
| --- | --- |
| `Ready(fix)` | La lectura fue aceptada y publicada como evidencia en ese momento. Para confirmar se relee evidencia actual con permiso preciso y GPS habilitado; no se reutiliza el objeto `Ready` como autorización. |
| `PermissionRequired` | Falta permiso preciso o se revocó durante la solicitud. El controlador no muestra un diálogo ni solicita el permiso. |
| `LocationDisabled` | El proveedor GPS está deshabilitado. El controlador no modifica ajustes del teléfono. |
| `Unavailable` | La fuente no entregó una lectura o no pudo completar la solicitud. Se puede ofrecer otro intento conservando el borrador. |
| `TimedOut` | La espera terminó sin resultado dentro del límite configurado. No se reutiliza la evidencia anterior. |
| `AlreadyRunning` | Hay otra solicitud activa. No se registra otra captura ni se interrumpe la existente. |
| `Rejected(rejection)` | La lectura no cumple la política; el motivo y mensaje permiten explicar precisión, antigüedad, simulación u otros datos inválidos. |

El flujo con `IncidentLocationViewModel` conserva el trabajo de la captura; `IncidentLocationFlowBinding` entrega `onStop` automáticamente al perder visibilidad y cierra el registro al destruirse el dueño. No se crea otra captura o sesión desde la pantalla. Si se usa directamente `PhoneLocationRefresh` para otro consumidor, ese consumidor conserva y cancela su `Job` al salir. La cancelación por abandono no debe mostrarse como error de ubicación. El compañero mantiene el diseño, los controles y la presentación de estos estados.

**`Ready` no confirma el incidente ni permite reportar cualquier punto.** Después se usa `confirmNearby` con la evidencia actual y el reloj monotónico. El repositorio productivo protegido vuelve a comprobar el borrador y la evidencia antes de escribir y de confirmar la transacción. Permanece la condición **distancia Haversine aproximada + precisión <= 5.000 m**, con el radio de **5 km elegido por el usuario**. Una lectura aceptada puede caducar antes del guardado o quedar descartada si la relectura detecta permiso revocado, GPS apagado o una comprobación fallida. Reactivar GPS exige renovar la captura; no restaura la evidencia descartada.

## Antecedente de comprobación 0.3.4 y límites de ese corte

El paso inicial de captura no declaraba permisos ni incorporaba el SDK. Después, la integración de Google Maps añadió el SDK real y los permisos de internet y ubicación COARSE/FINE al manifiesto. Todavía faltan la solicitud de permiso en tiempo de ejecución y el mapa visible conectado; declarar permisos no los concede ni activa GPS. La fuente productiva necesita permiso concedido antes de capturar. El ejemplo fijo en SQLite en memoria sigue funcionando sin ubicación.

La comprobación local aprobó **185 pruebas diferentes**, con cero fallos, errores u omisiones: **86 de dominio, 30 de app, 20 JVM de datos y 49 instrumentadas en API 37**. Las 49 instrumentadas incluyen las 32 anteriores y 17 de la fuente nueva. Se comprobaron estados, primera lectura, cancelación, tiempo de espera y limpieza de suscripciones mediante fuentes y backend controlados; el camino nativo real comprobó el rechazo con permiso denegado. **No se obtuvo una posición por GPS real.**

La primera ejecución completa terminó correctamente en **20 segundos**, con **132 tareas: 57 ejecutadas y 75 `UP-TO-DATE`**. Tras quitar un uso de `async(Job)` obsoleto en una prueba instrumentada, la segunda ejecución repitió las mismas **49 instrumentadas** y lint de datos: resultado correcto en **10 segundos**, **73 tareas: 8 ejecutadas y 65 `UP-TO-DATE`**. Esas repeticiones no se cuentan como pruebas adicionales. [Validación](VALIDACION.md) registra el cierre 0.3.4 y sus comandos.

Se corrigió una carrera en la que agotar el tiempo durante la publicación podía conservar evidencia en memoria. La regresión determinista que cubre esa coincidencia aprobó como parte de las **20 JVM de datos**. La renovación fallida limpia la evidencia; el resultado anterior no queda disponible como autorización.

Lint debug de datos quedó sin incidencias y el de app sin errores, con **13 advertencias visuales anteriores**. La compilación debug aprobó; se verificaron firma v2 y metadatos del APK **0.3.4 / código 11**, además de **43 recursos idénticos a 0.3.3**. No cambió la interfaz, login ni permisos del manifiesto. La evidencia y los logs están fuera de Git en `.gradle/validacion/ubicacion034.json` y sus archivos de ejecución.

La captura real, permisos visuales, GPS deshabilitado en el flujo integrado, Google Maps, Android API 26–29 y comprobación en teléfono físico siguen pendientes. No se instaló esta preparación sobre la app del usuario: el emulador conserva **0.3.1 / código 8**. No se creó commit, subida, integración, etiqueta ni publicación. [Avance](AVANCE.md) conserva **7/28 hitos = 25%**; preparar la fuente o comprobar un backend controlado no completa el hito de captura real.

Las [mejoras futuras](MEJORAS_FUTURAS.md) proponen cómo hacer más útil el producto después de revisar este paso. Son ideas sin implementación ni número de versión oficial comprometido. Terminar, probar o documentar no autoriza crear commits, subir, integrar, etiquetar, publicar o borrar; esas acciones requieren una instrucción humana precisa.
