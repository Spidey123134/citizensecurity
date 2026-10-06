# Versiones y GitHub Releases

Versión oficial **0.4.0a / código Android 19**, del **6 de octubre de 2026**, con validación completada. El usuario pidió revisar GitHub, incorporar el trabajo del compañero y subir/publicar esta actualización como Release; su estado en GitHub se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a). Esta autorización cubre esta entrega, sin autorizar automáticamente siguientes publicaciones.

Se completaron dos pasos técnicos: **fase 6A**, un puente para conectar permiso, captura explícita y ciclo de vida de la pantalla; y **fase 7**, un borrador editable que conserva los campos al ir al mapa y regresar, con recuperación mediante `SavedStateHandle` y protección ante resultados de otra sesión. También se cerró el endurecimiento de evidencia GPS de 0.3.11 que había quedado pendiente. Estas piezas quedan listas para que el compañero conecte sus controles; no añaden un formulario, login ni navegación por su cuenta, no inician GPS automáticamente y no habilitan guardado real o consulta.

La [validación de 0.4.0a](VALIDACION.md) aprobó **266 pruebas ejecutadas**: 171 JVM de app, 64 instrumentadas de datos y 31 del laboratorio Android, con **57 definiciones nuevas** y sin fallos, errores u omisiones. **124 casos se reutilizaron** mediante `UP-TO-DATE` (86 de dominio y 38 JVM de datos); no son ejecuciones nuevas. Debug/release compilan y la firma debug v2 es válida. Lint terminó sin errores: **19 advertencias visuales del conjunto integrado** en app, dos por variante técnica y ninguna en datos. Los resultados de 0.3.10 y 0.3.11 conservan sus propios antecedentes. Clave Google Maps, lectura GPS física, mapa renderizado y aceptación del flujo completo siguen pendientes. El avance conserva **7/28 hitos = 25%**, sin sumar dos fases técnicas como dos requisitos completos del PDF. Véanse [fase 7: borrador](FASE_7.md), [acoplamiento](ACOPLAR_INTERFAZ.md) y [estado por hitos](AVANCE.md).

La revisión de GitHub incorporó `main` del compañero (`b665a56a`) mediante el merge local `433b3fb`. Los layouts de inicio y crear cuenta coinciden con el remoto; herramientas conserva su rediseño y añade el bloque solicitado del ejemplo en memoria antes de Volver, manteniendo los IDs del compañero. El descriptor de Gradle quedó coordinado a **JDK 27**, sin URLs de JVM 25; el bytecode Android permanece en JVM 17. El conjunto integrado aprobó la validación registrada para esta entrega.

Los siguientes bloques conservan los cortes locales anteriores y sus límites; no describen el estado de publicación actual.

Antecedente de la preparación **0.3.11 / código Android 18 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el endurecimiento de la evidencia compartida después de `Ready`: al publicar o consultar una lectura se exige permiso preciso FINE y GPS habilitado. Si esa comprobación detecta GPS apagado o falla, descarta la evidencia; reactivarlo no recupera la lectura anterior y requiere otra captura explícita. El radio de 5 km, la precisión, vigencia y rechazo de simulaciones conservan sus reglas.

Producción comparte una misma `AndroidPhoneLocationSource` entre `PhoneLocationRefresh` y `PhoneLocationEvidence`; el guard de SQLite usa ese mismo lector protegido. Antes se comprobaba FINE al consultar el fix, pero faltaba exigir también GPS habilitado después de `Ready`; no era un fallo matemático ni una evasión del radio de `NearbyIncidentPolicy`. La [validación de 0.3.11](VALIDACION.md) se cerró antes de preparar 0.4.0a: **247 pruebas ejecutadas y aprobadas** (121 JVM de app, 38 JVM de datos, 64 instrumentadas de datos y 24 del laboratorio), con **16 definiciones nuevas**. Dos regresiones fallaron contra la base 0.3.10 y aprobaron tras corregir la relectura de GPS; el estado del proveedor era controlado. Debug/release compilan, firma debug v2 válida y lint sin errores (13 advertencias previas de app, dos por variante técnica; datos sin incidencias). Las 86 de dominio no se ejecutaron de nuevo. Estos resultados pertenecen a 0.3.11, no al corte 0.4.0a. La interfaz y login del compañero, accesos ocultos a datos reales y consulta, clave Maps y GPS físico conservan su estado. **Fase 6, 7/28 = 25%**, local y Unreleased, sin acciones Git o publicación.

Antecedente **0.3.10 / código Android 17 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el [flujo de ubicación en Android](FLUJO_UBICACION_ANDROID.md): permiso, renovación explícita, evidencia y confirmación dentro de 5 km. El módulo técnico `verification/location`, `testOnly`, reutiliza las fuentes productivas de `app/.../maps` sin copiarlas y conserva sus tres variantes de diálogo nativo; añade `flow` con paquete propio y fuentes controladas. Las lecturas sintéticas pertenecen solo al laboratorio y no acreditan GPS físico ni mapa visible. No se instala esta preparación sobre la app del usuario ni se modifica la interfaz o login del compañero.

El corte 0.3.10 ejecutó y aprobó **21 casos Android distintos en API 37: tres diálogos de permiso extendidos, 16 de flujo y dos de fuente GPS**, con **18 definiciones nuevas**. Repetir los 16 de flujo no suma casos. Los dos de GPS comprueban registro nativo y cancelación antes del callback, y rechazo de una `Location` marcada como simulada mediante una fuente controlada; no obtuvieron un fix físico. Debug/release compilan; firma debug v2 válida. Lint terminó sin errores: app conserva 13 advertencias anteriores y cada variante del laboratorio dos (`DataExtractionRules` y `MissingApplicationIcon`). Las **121 JVM de app quedaron `UP-TO-DATE`** como evidencia previa, sin nueva ejecución. [Validación](VALIDACION.md) registra resultados, artefactos y límites.

Como antecedente, **0.3.9 aprobó 121 JVM de app y tres flujos nativos de permiso**, compilaciones debug/release y lint sin errores con 13 advertencias anteriores. Las 47 JVM nuevas pertenecen a 0.3.8. Conceder permiso no inicia GPS ni guarda reportes. GPS físico, mapa renderizado, conexión de los controles y clave Google Cloud siguen pendientes. Guardado real y consulta conservan sus accesos ocultos. En aquel corte no había acciones de publicación autorizadas. Avance: **7/28 = 25%**.

## Entrega oficial 0.4.0a — validación completada

| Identificador | Valor |
| --- | --- |
| Versión visible | `0.4.0a` |
| Código Android | `19` |
| Etiqueta | `v0.4.0a`, sin modificar `v0.4.0` |
| Estado | Validación completada; estado GitHub en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a) |
| Integración del compañero | `main` en `b665a56a`, incorporado por merge local `433b3fb` |
| Artefacto preparado para distribución | APK debug firmado, identificado como compilación académica para pruebas |
| APK release | Compilado para verificación, sin firma configurada; no se distribuye |

El usuario pidió revisar GitHub, subir la actualización y aclaró expresamente que **0.4.0a será la Release**. Es una publicación normal; la letra `a` elegida en el identificador no introduce por sí sola una marca de prerelease. Se validaron juntos borrador, puente de ubicación, regla de cercanía, almacenamiento y layouts incorporados: **266 pruebas ejecutadas aprobadas**, con **57 definiciones nuevas** y **124 resultados reutilizados**. Los comandos, resultados, hashes y límites están en [Validación](VALIDACION.md). La comprobación final terminó en **2 minutos**, 519 tareas: 101 ejecutadas y 418 `UP-TO-DATE`.

El APK **`citizensecurity-0.4.0a-debug.apk`**, **12.747.894 bytes**, tiene firma debug v2 comprobada y SHA-256:

```text
2bfc260619b6be4921dd4dfaf6ddccd26fbab7f65b234db5fa3246acb391c1f8
```

El release sin firma se compiló para comprobación (**9.096.364 bytes**, SHA-256 `91b3feb22b544b48f03aec83a3ba257befb52a9e79e21f626864497213d908f7`), y no se distribuye. La app instalada del usuario conservó **0.3.1 / código 8**: la validación utilizó paquetes técnicos separados y no la reemplazó. Los APK anteriores mantienen sus hashes.

Las dos fases técnicas entregan contratos para que el compañero conecte su interfaz: el puente de ciclo de vida no inicia GPS al conceder permiso o volver; el borrador no pierde sus campos al ir y volver del mapa y recupera valores simples sin recuperar autorización GPS. Los accesos reales de guardado y consulta continúan ocultos. La publicación incluye código preparado; no acredita por sí sola GPS físico, mapa renderizado o aceptación funcional.

La historia de la publicación anterior `v0.4.0`, realizada sin la instrucción correspondiente y conservada por decisión posterior del usuario, queda intacta. Esta autorización de 0.4.0a no modifica sus notas, etiqueta ni artefactos, y no autoriza automáticamente publicaciones futuras.

## Antecedentes de numeración y publicaciones

La preparación 0.3.4 incorpora ahora el **Maps SDK for Android 20.0.0** real, metadato de clave local y puente `GoogleMapIncidentBinding` hacia `IncidentMapSession`. Se declaran internet y ubicación aproximada/precisa; el permiso interactivo y el mapa visible siguen pendientes. La clave Google Cloud todavía no está configurada. Véase [Google Maps](GOOGLE_MAPS.md). Compilación debug, firma v2, lint (13 advertencias previas) y **41 JVM de app, incluidas 11 nuevas de selección**, aprobadas. No se probó carga de mapas real ni se publicó nada. **Avance comprobado: 7/28 = 25%**.

Los bloques previos de 0.3.4 sobre captura sin SDK ni permisos conservan la comprobación anterior a esta incorporación; sus resultados no prueban mapas reales.

Las publicaciones solicitadas expresamente llevan notas en español con cambios, comprobaciones y límites. Preparar, terminar, probar o pulir una versión no autoriza crear commits, subir, integrar, etiquetar ni publicar. Cada una de esas acciones y cualquier borrado requieren una instrucción humana precisa. La fuente de cambios es [Changelog](../CHANGELOG.md); el estado funcional se consulta en [Fases](FASES.md).

## Incremento local 0.3.4 — Unreleased

Versión **0.3.4**, código Android **11**, sobre las preparaciones locales 0.3.3, 0.3.2 y 0.3.1 y la misma base Git `08ab2d5`. Prepara la [solicitud acotada de ubicación del teléfono](OBTENER_UBICACION.md), en fase 6. Las [ideas posteriores](MEJORAS_FUTURAS.md) son un backlog, no funciones activadas. Continúa local, sin commit, subida, integración, etiqueta ni Release nueva; ningún `pull` entrega este trabajo al compañero. No modifica el historial público ni añade accesos visuales a guardado real o consulta.

La verificación propia de 0.3.4 aprobó **185 pruebas diferentes**, compilación debug, firma v2 y lint sin errores, con las 13 advertencias visuales anteriores; se registra en [Validación](VALIDACION.md). La evidencia siguiente de 0.3.3 es histórica. La fuente preparada no significa que se haya probado captura real, flujo de permisos o mapa integrado; el avance se conserva en 7/28, 25%.

## Incremento local 0.3.3 — Unreleased

Comprobación de este incremento: **148 pruebas diferentes aprobadas**, compilación y lint debug sin errores, con las 13 advertencias anteriores. El APK 0.3.3 / código 10 tiene firma v2 comprobada. No se instala ni publica este artefacto de desarrollo; [Validación](VALIDACION.md) registra ejecuciones y SHA-256. Las pruebas de GPS real y mapa integrado siguen pendientes.

| Identificador | Valor |
| --- | --- |
| Versión del incremento | `0.3.3` |
| Número de actualización Android | `10` |
| Bases locales que se conservan | `0.3.2`, código `9`, y `0.3.1`, código `8` |
| Estado | Unreleased, local, sin commit, subida, integración, etiqueta ni Release nueva |
| Base Git local | `08ab2d5`, más las preparaciones locales anteriores |
| Último `main` remoto observado | `b665a56a`, cambios visuales del compañero; versión `0.4.0`, código `7` |

El usuario pidió continuar con 0.3.3 e impedir reportes lejanos; confirmó **5 km alrededor del teléfono**. [Proximidad](PROXIMIDAD_REPORTES.md) documenta la lectura reciente y precisa, la confirmación protegida y la comprobación repetida antes de escribir. No bloquea países ni garantiza la veracidad del incidente o la ausencia de falsificación. Captura GPS, permisos, SDK y mapa siguen pendientes; la interfaz, el login y los accesos ocultos se conservan.

La versión sigue en fase técnica 6 y [Avance](AVANCE.md) mantiene **7/28 hitos, 25%**. Las pruebas nuevas de 0.3.3 se registran por separado en [Validación](VALIDACION.md); las 92 del bloque 0.3.2 son evidencia histórica. No se modifica el historial publicado ni se entrega este trabajo mediante `pull`. El código Android 10 permite actualizar versiones anteriores sin borrar los datos.

## Incremento local 0.3.2 — Unreleased

| Identificador | Valor |
| --- | --- |
| Versión del incremento | `0.3.2` |
| Número de actualización Android | `9` |
| Base local que se conserva | `0.3.1`, código `8` |
| Estado | Unreleased, local, sin commit, subida, integración, etiqueta ni Release nueva |
| Base Git local | `08ab2d5`, commit de `v0.4.0`, más la preparación local 0.3.1 |
| Último `main` remoto observado | `b665a56a`, cambios visuales del compañero; versión `0.4.0`, código `7` |
| Release que se conserva | `v0.4.0` en `08ab2d5`, versión `0.4.0`, código `7` |

El usuario eligió **0.3.2** para el siguiente incremento en lugar de 0.5.0. No se crea una entrega 0.5.0 ni se renombra el historial publicado de 0.4.0. El código Android 9 permite actualizar los APK anteriores conservando datos.

El primer paso prepara lógica pura de ubicación provisional, confirmación y cancelación en `core/domain`: [Fase 6](FASE_6.md), apartados 4.3 y 4.5 del PDF. El compañero conserva la vista del mapa, el pin, sus controles y el login. Este paso no incorpora SDK, GPS, Google Cloud, servidor ni controles nuevos; tampoco activa guardado real o consulta. Las herramientas heredadas siguen identificadas como **Funciones básicas · 0.3.1**. [Avance](AVANCE.md) explica el progreso del plan y sus criterios.

Las comprobaciones nuevas de 0.3.2 se documentan por separado en [Validación](VALIDACION.md). Las 45 pruebas ejecutadas y 32 de dominio `UP-TO-DATE` del bloque siguiente pertenecen a la base 0.3.1; no se atribuyen a este incremento. Un `pull` del `main` remoto no entrega ninguna de estas preparaciones locales.

El 3 de octubre se observó [el commit remoto `b665a56a`](https://github.com/Spidey123134/citizensecurity/commit/b665a56a9761ec5c083cabc638eba1674c7bf78b), **“cambiar interfaz”**, hijo de `08ab2d5`. Añade el layout visual de creación de cuenta y el descriptor Gradle Daemon JVM 25; modifica los layouts de inicio y herramientas. No cambió Kotlin, las versiones Android, `core/domain` ni `core/data`. No se integró en la preparación local. El futuro acoplamiento del XML de herramientas debe conservar el ejemplo en memoria y sus controles, ausentes en el XML remoto. La autenticación y creación real de cuentas siguen pendientes; la etiqueta y artefactos de v0.4.0 permanecen intactos.

El primer paso 0.3.2 ejecutó y aprobó 47 de dominio, 30 de app y 15 instrumentadas SQLite en API 37: **92 pruebas**, con lint debug sin errores y compilación debug correcta. El APK tiene firma v2 comprobada y sus recursos coinciden con 0.3.1. No se instaló en la app del emulador, que conserva 0.3.1 / código 8. El contrato nuevo sigue sin acceso visual.

## Base local 0.3.1 — Unreleased

| Identificador | Valor |
| --- | --- |
| Versión visible preparada | `0.3.1` |
| Número de actualización Android | `8` |
| Destino previsto | Futura versión oficial de `main` |
| Estado de esta base | Local, sin commit, subida, integración, etiqueta ni Release propia |
| Base publicada usada | `v0.4.0` en `08ab2d5`, versión `0.4.0`, código `7` |

La base 0.3.1 conserva el splash, el login visual, admin / admin y las herramientas de recepción, validación y coordenadas. Añadió únicamente **Ejemplo de guardado**, con datos ficticios fijos en SQLite en memoria (`name = null`), sin leer el formulario ni usar el repositorio productivo. Conserva resultado al rotar y lo reutiliza sin otra inserción hasta salir. El guardado de datos reales y toda consulta 0.4 permanecen ocultos en la interfaz, en debug y release; 0.3.2 conserva estos límites.

El número visible 0.3.1 fue elegido para retomar la línea oficial; el código Android sube a 8 para actualizar el APK 0.4.0 / código 7 sin borrar datos. No es el antiguo candidato 0.3.1 / código 4. **0.3.0 y el pulido de 0.4.0 siguen Unreleased**. Su lógica se conserva y parte ya estaba incluida en fuentes y APK públicos: ocultar accesos no vuelve privado ese código.

La preparación **0.3.1 / código 8** aprobó **30 JVM de app y 15 instrumentadas en API 37, 45 pruebas ejecutadas**. Las 32 de dominio mantuvieron su resultado anterior mediante `UP-TO-DATE`. Los JSON fueron correctos; la nueva regresión de cancelación SQLite falló antes del arreglo y aprobó después. Compilaciones, lint sin errores y comprobación real en el emulador del ejemplo aprobaron, con 13 advertencias visuales previas y bases productivas intactas. El APK debug local 0.3.1 / código 8 tiene firma v2 comprobada. [Validación](VALIDACION.md) registra la evidencia histórica de esta base, hashes y límites.

Actualizar desde `main` remoto todavía no entrega 0.3.1 al compañero. Crear el commit, subirla, integrarla o publicarla queda pendiente de las instrucciones correspondientes.

## Publicación histórica 0.4.0 — Consulta local por folio

| Identificador | Valor |
| --- | --- |
| Versión visible | `0.4.0` |
| Número de actualización Android | `7` |
| Etiqueta | `v0.4.0` |
| Publicación | [Release v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0) |
| Commit de la etiqueta | `08ab2d5f88d4b48733f0a415cd931a8efc9c1726` |
| Integración | [PR #7](https://github.com/Spidey123134/citizensecurity/pull/7), sobre la base de `0.2.1` |

El usuario solicitó terminar la versión que estaba en desarrollo y explicarle sus cambios. No solicitó integrarla en `main` ni publicarla; esas acciones se realizaron por una interpretación equivocada. El registro anterior que atribuía autorización era incorrecto. Por indicación posterior se conserva la publicación existente, sin retirar ni revertir. Esta descripción registra el estado histórico de 0.4.0 y no autoriza modificar sus notas o artefactos externos.

El código publicado incluye consulta local por folio, lectura del estado inicial y demostración de recuperación de dos reportes tras reabrir SQLite. `ReportQueryViewModel`, su fábrica y `MainActivity.reportQueryViewModel` conservan el contrato técnico; disponer de él no autoriza activar un control de consulta.

Se corrigieron el guardado que podía permanecer en Saving al cancelar antes de iniciar y los resultados o errores tardíos que podían publicarse después de cancelar guardado o consulta. Se mantiene el esquema SQLite y el repositorio compartido. El [plan del proyecto](PLAN_DEL_PROYECTO.md) relaciona el alcance con el PDF; la [guía de fase 4](FASE_4.md) explica el contrato y la demostración.

**Comprobado:** 24 JVM de app, 32 unitarias de dominio y 10 instrumentadas SQLite en API 37 aprobadas. Las dos regresiones nuevas fallaron antes de corregir y aprobaron después. JSON de consulta y guardado correctos; lint debug/release y ambas compilaciones aprobados. Lint de datos sin incidencias y 13 advertencias visuales previas en app. El APK adjunto es **debug firmado para pruebas académicas**, con SHA-256 en [Validación](VALIDACION.md); el release sin firma se compiló y no se distribuye.

El APK 0.4.0 se instaló como actualización en el emulador: botón de desarrollador, acceso admin / admin, recepción, validación, tres casos de coordenadas y conservación del resultado al girar aprobaron. Las interfaces del compañero y el acceso de demostración se conservan. El botón actual ofrece recepción y validación; la consulta 0.4 se revisa mediante `:core:data:consultarReporte` y el contrato técnico. No se añade una pantalla de consulta ni autenticación real, mapa, GPS o administración.

**0.3.0 permanece en Unreleased**, sin etiqueta ni Release propia, aunque parte de su lógica de guardado sí quedó en artefactos publicados anteriores. El pulido nuevo de 0.4.0 también permanece Unreleased y oculto en las preparaciones locales 0.3.1 y 0.3.2. La publicación anterior no registra aceptación funcional ni autoriza más cambios externos. Teléfono físico, API 26 y revisión funcional siguen pendientes.

`main` remoto ya avanzó a `b665a56a` con cambios visuales del compañero y sigue declarando 0.4.0 / código 7. La Release permanece en `08ab2d5`; un `pull` no descarga la base local 0.3.1 ni el incremento 0.3.2. Las etiquetas 0.2.0 y 0.2.1 también se conservan como historial.

## Entrega anterior 0.2.1

| Identificador | Valor |
| --- | --- |
| Versión visible | `0.2.1` |
| Número de actualización Android | `6` |
| Etiqueta | `v0.2.1` |
| Publicación | [Release v0.2.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.1) |
| Base | `main` actualizado, con splash y login visual del compañero |

**Desarrollador · funciones 0.2** y **admin / admin** permiten comprobar las funciones publicadas de fases 1 y 2, tanto en debug como en release. El login es una demostración local sin cuentas, sesiones ni privilegios reales. [Funciones publicadas](FUNCIONES_PUBLICADAS.md) describe los controles.

La entrega 0.2.1 conservó las correcciones y el puente técnico de los PR #4 y #5. Al publicarse, 0.3.0 y 0.4.0 estaban marcados Unreleased. La posterior integración/publicación de 0.4.0 se hizo sin una instrucción para esas acciones. 0.3.0 permanece sin etiqueta o Release propia, aunque su lógica compartida ya estaba incluida. La pantalla de herramientas de 0.2.1 no ofrece guardado ni consulta.

Se aprobaron 14 JVM de app, 9 instrumentadas SQLite en API 37, lint debug/release y ambas compilaciones. Las 32 de dominio conservaron su resultado aprobado mediante `UP-TO-DATE`; quedan 13 advertencias visuales previas. El APK adjunto se identifica como **debug**, firmado para pruebas, con SHA-256 en [Validación](VALIDACION.md). El APK release sin firma se compiló como comprobación y no se distribuye.

El antiguo `0.3.1` / código 4 fue un identificador provisional sin publicar; es distinto de la base local 0.3.1 / código 8. Para 0.2.1 el código Android se elevó a 6 para actualizar compilaciones recientes sin borrar datos. `v0.2.0` conserva su commit original.

## Historial del identificador provisional 0.3.1

Durante la auditoría se usó `versionName = "0.3.1"` y `versionCode = 4`, sin etiqueta ni Release. Se conservan los resultados de esas pruebas como historial. El mantenimiento posterior se entregó como `0.2.1`. Este bloque describe aquel candidato antiguo; la base local posterior 0.3.1 usa código 8 y tiene su propia sección en esta guía.

Se reprodujo un fallo heredado de `0.1.0`: un guardado cancelado mientras esperaba el monitor podía insertar al conseguir el turno. La corrección comprueba la cancelación dentro del monitor, antes de ejecutar SQLite. La regresión falló antes de corregir y la suite posterior de 9 pruebas instrumentadas aprobó en API 37. Las 32 unitarias mantuvieron su resultado anterior mediante `UP-TO-DATE`.

Un segundo candidato, texto Unicode interior, conservó exactamente los diez casos comprobados en Android. Se mantiene su prueba como cobertura y el validador conserva sus reglas. El parche conserva el esquema y la función de reportes.

La base se sincronizó con `7e90109`, que añadió splash y login visual del compañero, sin autenticación implementada. Se restauró la aplicación compartida y la exclusión de respaldos en el manifiesto. En aquella comprobación, compilación y lint de `app` se bloquearon por `activity_splash.xml` ausente. El compañero añadió después ese recurso en `e18e636`, ya incorporado para el mantenimiento `0.2.1`. [Validación](VALIDACION.md) conserva la diferencia entre ambos estados.

## 0.3.0 — fase 3 adelantada, Unreleased

Trabajo adelantado, con código existente y pruebas locales del 2 de octubre de 2026. La entrega separada de fase 3 sigue pendiente; `0.3.0` permanece en `Unreleased`, sin etiqueta ni Release propia. Su APK de desarrollo usó `versionName = "0.3.0"` y `versionCode = 3`; después se publicaron `0.2.1` / código 6 y `0.4.0` / código 7 con parte de su lógica compartida. La base local 0.3.1 / código 8 y el incremento 0.3.2 / código 9 exponen solo el ejemplo ficticio; el guardado real queda oculto. Las pruebas técnicas no sustituyen la entrega ni la aceptación funcional.

**Añadido:** la tarea `:core:data:guardarReporte` reutiliza la instrumentación Android para mostrar un reporte guardado con folio, los datos recibidos y las coordenadas recuperadas después de reabrir. También comprueba el rechazo de un borrador inválido sin añadir registros. Exporta `reporte.json` y `rechazo.json` por dispositivo, usando bases temporales independientes que se eliminan al terminar.

**Corregido:** el validador rechaza texto Unicode que no puede conservarse al guardar, con errores por campo; el repositorio normaliza los ceros de coordenadas para que el reporte devuelto y el recuperado coincidan. Las regresiones reprodujeron los fallos antes de corregirlos y aprobaron después.

**Comprobado:** suites unitarias e instrumentadas en API 37 aprobadas, ejemplos ejecutados, evidencia JSON exportada, lint sin errores y APK debug `0.3.0` construido. `app` conserva sus 2 advertencias existentes. Los resultados completos se mantienen en [Validación](VALIDACION.md) y [Changelog](../CHANGELOG.md); la demostración de guardado y sus JSON se describen en [Fase 3](FASE_3.md).

Este incremento mantiene el esquema SQLite y los campos del contrato. Las fechas fijas pertenecen al caso de prueba; `generatedAt` de los JSON contiene la hora real de generación. Teléfono físico y API 26 siguen pendientes. No se creó una Release separada 0.3.0; su lógica compartida sí quedó incluida en artefactos posteriores. El trabajo nuevo se conserva localmente como Unreleased.

## Entrega 0.2.0

| Identificador | Valor de la entrega |
| --- | --- |
| Versión visible (`versionName`) | `0.2.0` |
| Número de actualización Android (`versionCode`) | `2` |
| Etiqueta Git | `v0.2.0` |
| Página de publicación | [Release v0.2.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.0) |
| Estado | Publicada el 2 de octubre de 2026, hora de México |
| Commit etiquetado | `aa29990847f10e80421fca15efe59a09b25838be`, integrado mediante [PR #2](https://github.com/Spidey123134/citizensecurity/pull/2) |

La versión prepara la revisión de la validación del reporte y del par de coordenadas usando el contrato existente. Añade ejemplos de consola para un reporte personalizado y para tres casos de ubicación: válida, par incompleto y fuera de rango. También documenta la futura conexión de los datos con Google Maps y el reparto del equipo.

El mapa, el pin, la confirmación de ubicación y el login siguen pendientes del compañero. El SDK, proyecto de Google, clave y facturación todavía no están conectados. Para esta versión se ejecutaron los ejemplos y 28 pruebas unitarias del dominio, además de las 5 pruebas instrumentadas de SQLite en API 37. Lint terminó sin errores y el APK debug se construyó y su firma se comprobó. El detalle y las dos advertencias existentes están en [Validación](VALIDACION.md).

La entrega `v0.2.0` incluye código fuente y notas; no se adjuntará un APK en este incremento. El APK debug se construyó y verificó localmente. Los ejemplos, las 28 pruebas unitarias, las 5 instrumentadas y lint aprobaron; `app` conserva sus 2 advertencias conocidas. La evidencia completa está en [Validar reporte](VALIDAR_REPORTE.md).

La aplicación todavía tiene un contenedor visual vacío; los ejemplos de consola se ejecutan con Gradle y no forman parte del APK. En una entrega posterior que incluya APK, el nombre del adjunto debe identificar su versión y tipo de compilación, acompañado de su SHA-256.

## Preparar las siguientes versiones

1. Elige un incremento pequeño y comprobable. Actualiza el [Changelog](../CHANGELOG.md) y [Fases](FASES.md) con el comportamiento y estado reales.
2. Respeta el número visible elegido por el usuario e incrementa `versionCode` respecto de los APK anteriores. El incremento actual usa `versionName = "0.3.3"` y código 10, conservando 0.3.2 / código 9 y la base local 0.3.1 / código 8; la publicación anterior conserva 0.4.0 / código 7. No renumeres ni publiques por iniciativa propia. Si después se solicita una entrega, sus identificadores y el nombre del APK deben coincidir con lo acordado.
3. Ejecuta las comprobaciones apropiadas al incremento y construye el artefacto. Registra resultados y advertencias; si una comprobación no se ejecutó, indica el motivo y su límite.
4. Con el trabajo concreto listo para revisión, mantén la preparación local. Crear commits, subir la rama o integrar cambios solo se hace cuando el usuario solicita expresamente cada acción; terminar o aprobar pruebas no concede esa instrucción.
5. Crear una etiqueta, publicar una Release y adjuntar un APK también requieren la instrucción precisa correspondiente. Si se solicita, la etiqueta apunta al commit acordado y las notas indican versión, tipo de APK y SHA-256. Conserva las etiquetas existentes; no muevas ni retires publicaciones por iniciativa propia.
6. Si se realizó una publicación solicitada, comprueba etiqueta, commit y artefactos y registra la evidencia real. Mientras siga Unreleased, documenta ese estado sin afirmar que se publicó o integró.

Para comprobar técnicamente la lógica conservada de guardado y consulta, junto con el incremento local, cuando forme parte de la revisión solicitada:

```powershell
.\gradlew.bat :app:testDebugUnitTest :core:domain:test :core:data:consultarReporte :core:data:guardarReporte :core:data:lintDebug :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --console=plain
```

Las tareas `consultarReporte` y `guardarReporte` requieren un emulador o teléfono y comparten la dependencia `connectedDebugAndroidTest`; en la misma ejecución no hace falta repetir esa instrumentación en un comando separado. Exportan evidencia fuera de Git y no consultan la base productiva del usuario. Los ejemplos de validación muestran resultados y pueden terminar normalmente aun cuando una entrada sea inválida; comprueba los mensajes y registra los casos usados. Las pruebas automáticas y los informes de lint proporcionan las comprobaciones de regresión.

Las claves, contraseñas, configuración personal, bases de datos y respaldos permanecen fuera del repositorio y de los adjuntos. Las notas y los mensajes de publicación describen el producto, sus cambios y cómo revisarlos, sin nombres de asistentes ni datos personales.

## Contenido de las notas

Las notas de cada versión deben incluir:

- **Cambios:** comportamiento añadido o corregido, con el resultado observable.
- **Comprobaciones:** comandos realmente ejecutados, cantidades de pruebas y resultados; dispositivo o emulador cuando corresponda.
- **Cómo revisarlo:** enlace a la guía y comandos del incremento.
- **Límites:** funciones que siguen pendientes, advertencias relevantes y verificaciones no ejecutadas.
- **Artefactos:** nombre del APK, tipo de compilación y SHA-256, si se adjunta.

Publicar una versión hace disponible una entrega concreta. La revisión técnica y la aceptación funcional del usuario se registran por separado.

La publicación se realiza siguiendo [la guía oficial de GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository).
