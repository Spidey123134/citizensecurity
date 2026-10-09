# CITIZENSECURITY

## Entrega oficial 0.5.0

**Entrega 0.5.0 / código Android 20 — 9 de octubre de 2026.** El usuario autorizó integrar el avance del PR #8 a main y publicar esta Release oficial, al confirmar 0.5.0. Incluye las fases 8 a 11: coordinación de borrador y ubicación, consulta SQLite por zona, MapLibre/OpenFreeMap con calles y marcadores. La publicación se verifica en [Release v0.5.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.0). La interfaz y el login del compañero se conservan: la pantalla productiva del mapa todavía no está acoplada y los accesos al guardado y consulta reales siguen ocultos. Oficial identifica esta entrega del proyecto, no acredita aceptación productiva completa. Avance: **10/28 = 35.7%**, sin cambiar los hitos o sus pesos.

La evidencia funcional previa de fase 11 conserva **325 casos distintos aprobados y 57 definiciones nuevas**; el GPS físico se comprobó por separado. Para 0.5.0 se compilaron de nuevo debug/release y lint, sin errores; no se presenta la evidencia anterior como pruebas nuevas. Se conservan las versiones y APK anteriores.

Para actualizar el código del equipo, guarda o confirma primero tus cambios propios y ejecuta:

```bash
git fetch origin
git switch main
git pull --ff-only origin main
```

## Registro histórico anterior a 0.5.0

Los estados Unreleased y las autorizaciones siguientes describen sus cortes originales. El código de fases 8 a 11 se incluye en la entrega 0.5.0; los pendientes visuales se conservan.

**Avance compartido para el equipo — Unreleased.** La rama [development/fase-11-mapa-zonas](https://github.com/Spidey123134/citizensecurity/tree/development/fase-11-mapa-zonas) reúne las fases 8 a 11, necesarias para el mapa por zonas validado. El usuario autorizó subir este código el 8 de octubre de 2026. Se conserva main y la Release oficial 0.4.0a; no se crea otra Release ni se integra automáticamente. El siguiente paso sigue siendo acoplar la pantalla del compañero. Los resultados de 325 pruebas y 35.7% del plan pertenecen al cierre técnico previo a esta subida.

Para obtener esta preparación, conserva primero cualquier cambio propio y ejecuta `git fetch origin`; desde una copia que todavía no tenga la rama local, usa `git switch --track origin/development/fase-11-mapa-zonas`. La pantalla productiva y los accesos reales siguen pendientes de acuerdo.

**Trabajo activo: fase 11 — incidentes por zona y marcadores — Unreleased, local; cerrada técnicamente el 8 de octubre de 2026, iniciada el 7.** El circuito SQLite → `ReportMapViewModel` → `ReportMapSceneBinding` → `MapLibreReportSceneHost` → MapLibre representa marcadores mínimos sobre Liberty después de un gesto explícito. Se comprobó el mapa por zonas con cámara, filtros, límite, lista vacía, recarga y retirada/recreación de la vista. [Fase 11](docs/FASE_11.md) y [acoplamiento](docs/ACOPLAR_INTERFAZ.md) documentan el contrato. La oficial conserva **0.4.0a / código Android 19**, sin otra publicación.

El puente consulta solo en **RESUMED**, conserva filtros y sigue movimientos de cámara posteriores al gesto. Construir, recrear o volver a la pantalla no consulta automáticamente. Cambiar región retira marcadores anteriores; una respuesta tardía no pinta otra zona. Cada escena reserva un token puro en el modelo y usa un host propio: cerrar/pausar la vista anterior no cancela ni pinta la sucesora, incluso si ambas usan la misma consulta. El cierre retira solo sus recursos y mantiene abierto el repositorio compartido.

La [validación de fase 11](docs/VALIDACION.md) aprobó **325 casos distintos: 273 JVM app, 34 Android flow, ocho JVM monitor y diez Android mapas**. Se añaden **57 definiciones: 31 coordinador, 20 host y seis nativas**. App y nativos se repitieron tras ajustar dos aserciones a la precisión del SDK, sin cambios de producción ni reglas GPS y sin contar repeticiones. Flow34/monitor8 se ejecutaron en el primer pase y se reutilizaron en el final. Debug/release compilan, firma debug v2 válida y lint sin errores; advertencias y comprobación de conservación final se registran por separado.

También se obtuvo **GPS físico válido en el Motorola/API 36** con la fuente productiva real y captura por gesto: precisión aproximada **6.668 m**, antigüedad **46 ms**, cercanía permitida, punto distante rechazado y lectura no simulada. El primer intento terminó en TIMEOUT; el siguiente, cerca de una ventana, fue VALIDATED, conservando el límite de captura de 15 s. El laboratorio physical no guardó coordenadas ni creó reportes. Se acreditan dos hitos existentes: **4.3 Geolocalización en 2/4 y 4.5 Mapas en 3/4; total 10/28 = 35.7%**, manteniendo los mismos 28 hitos y pesos. El flujo visual productivo todavía no está acreditado.

El siguiente trabajo es coordinar la pantalla del compañero, que aún no existe en el repositorio. Conserva interfaces/login, navegación y accesos reales ocultos; no se activa guardado o consulta productivos. Los **18 APK anteriores**, la app del teléfono **0.4.0/código 7** y la del emulador **0.3.1/código 8** se preservan; el recibo cierre-fase11.json comprueba HEAD/índice, 63 archivos de base y hashes/fechas de ambos productos. Este cierre no incluye Git, publicación, Cloud, borrado ni apagado. Los bloques siguientes conservan sus resultados y pendientes históricos.

**Antecedente: fase 10 — MapLibre Native + OpenFreeMap — Unreleased, local; cerrada técnicamente el 7 de octubre de 2026.** El usuario eligió este proveedor para el mapa. Se integra **MapLibre Native Android 13.6.1** con el estilo **Liberty** y se adaptan los bindings a `MapLibreMap`; el runtime del mapa deja de usar SDK Google, metadato/lectura de clave y comprobación de Google Play Services. [Fase 10](docs/FASE_10.md) y [Mapa con MapLibre](docs/MAPA_MAPLIBRE.md) describen la configuración vigente. El laboratorio aislado comprobó **mapa real con calles y nombres legibles**, sin clave, tarjeta ni cuenta. La oficial sigue siendo **0.4.0a / código Android 19**, sin otro número ni publicación nueva.

La dependencia activa es `org.maplibre.gl:android-sdk-opengl:13.6.1`, con backend OpenGL ES estable. La inicialización local distingue `Ready`, `RendererUnavailable` y `VerificationFailed`; `Ready` todavía no prueba red, estilo cargado ni mapa renderizado. Esa carga y el PNG se comprobaron por separado: con GPU host aparecen Calle Tacuba, Calle Moneda y Calle Venustiano Carranza; el límite de etiquetas invisibles con renderizado software queda documentado. Se conservan GPS explícito, permisos, radio de 5 km, borrador, SQLite y consulta por zona. El compañero mantiene interfaz/login, pantalla del mapa, pines y controles; guardado real y consultas siguen ocultos. **Avance: 8/28 = 28.6%; 4.5 Mapas en 2/4**, al acreditar únicamente el hito existente de SDK configurado. La configuración Google siguiente pertenece a los antecedentes anteriores al cambio de proveedor.

La [validación de fase 10](docs/VALIDACION.md) aprobó **268 casos distintos: 222 JVM de app, 34 Android del flujo, ocho JVM del monitor y cuatro Android del mapa**. Se añaden **25 definiciones**; siete casos de readiness reemplazan nueve de Google, sin inflar el total con sustituciones o repeticiones. Debug/release compilan, firma debug v2 válida y lint sin errores: 19 advertencias anteriores app, dos flow y ocho del laboratorio de mapas. Los 16 APK anteriores, HEAD/índice, fuentes visuales y producto instalado 0.3.1/código 8 permanecen conservados. Los APK nuevos son artefactos locales separados; aceptación del flujo productivo pendiente.

**Antecedente: fase 9 — consulta de incidentes por zona — Unreleased, local; completada técnicamente.** Continúa la base oficial 0.4.0a sin asignar otra versión ni modificar su publicación. La nueva función recibe una región geográfica, filtros de tipo/estado y un límite; devuelve los datos mínimos de los marcadores guardados en SQLite. `ReportMapViewModel` carga solo mediante `load(query)`, conserva la consulta de cada resultado y descarta respuestas de una petición sustituida. [Fase 9](docs/FASE_9.md) y [acoplamiento](docs/ACOPLAR_INTERFAZ.md) documentan el contrato para el futuro mapa del compañero. **Aceptación visual pendiente.**

La [validación de fase 9](docs/VALIDACION.md) aprobó **457 casos distintos ejecutados, con 43 definiciones nuevas**, sin fallos, errores, omisiones ni resultados reutilizados: 96 de dominio, 38 JVM de datos, 78 SQLite Android, 211 JVM de app y 34 del laboratorio de flujo. Debug/release compilan, firma debug v2 válida y lint sin errores: 19 advertencias anteriores de app, ninguna de datos y dos del laboratorio. Dos regresiones comprueban que limpiar o pedir otra zona al recibir `Loading` no inicia la lectura sustituida; el job se reserva antes de emitir ese estado. La repetición final de app, compilación y lint no aumenta el recuento. Los resultados permanecen separados de la Release y de fases anteriores.

La petición actual autoriza migración y comprobación local, sin commit, subida, integración, etiqueta o Release nuevos. Para completar los hitos de 4.5 faltan el mapa por zonas con marcadores y su integración visual en la pantalla del compañero. GPS físico y aceptación siguen pendientes. La [guía MapLibre](docs/MAPA_MAPLIBRE.md) es la activa; la [guía de clave Google](docs/CONFIGURAR_CLAVE_MAPS.md) conserva el antecedente del proveedor anterior.

La **fase 8 quedó completada técnicamente**: coordinación del borrador y ubicación mediante `ReportLocationFlowBinding`, con **226 pruebas ejecutadas aprobadas y 27 definiciones nuevas**, compilación debug/release y lint sin errores con advertencias anteriores. Su cierre documental también terminó; la aceptación visual sigue pendiente. [Fase 8](docs/FASE_8.md) conserva ese corte como antecedente, sin trasladar sus pruebas a fase 9.

Versión oficial **0.4.0a / código Android 19**, del **6 de octubre de 2026**, con validación completada. El usuario pidió revisar GitHub, incorporar el trabajo del compañero y subir/publicar esta actualización como Release; su estado en GitHub se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a). Esta autorización cubre esta entrega, sin autorizar automáticamente siguientes publicaciones.

Se completaron dos pasos técnicos: **fase 6A**, un puente para conectar permiso, captura explícita y ciclo de vida de la pantalla; y **fase 7**, un borrador editable que conserva los campos al ir al mapa y regresar, con recuperación mediante `SavedStateHandle` y protección ante resultados de otra sesión. También se cerró el endurecimiento de evidencia GPS de 0.3.11 que había quedado pendiente. Estas piezas quedan listas para que el compañero conecte sus controles; no añaden un formulario, login ni navegación por su cuenta, no inician GPS automáticamente y no habilitan guardado real o consulta.

La [validación de 0.4.0a](docs/VALIDACION.md) aprobó **266 pruebas ejecutadas**: 171 JVM de app, 64 instrumentadas de datos y 31 del laboratorio Android, con **57 definiciones nuevas** y sin fallos, errores u omisiones. **124 casos se reutilizaron** mediante `UP-TO-DATE` (86 de dominio y 38 JVM de datos); no son ejecuciones nuevas. Debug/release compilan y la firma debug v2 es válida. Lint terminó sin errores: **19 advertencias visuales del conjunto integrado** en app, dos por variante técnica y ninguna en datos. Los resultados de 0.3.10 y 0.3.11 conservan sus propios antecedentes. Clave Google Maps, lectura GPS física, mapa renderizado y aceptación del flujo completo siguen pendientes. El avance conserva **7/28 hitos = 25%**, sin sumar dos fases técnicas como dos requisitos completos del PDF. Véanse [fase 7: borrador](docs/FASE_7.md), [acoplamiento](docs/ACOPLAR_INTERFAZ.md) y [estado por hitos](docs/AVANCE.md).

La revisión de GitHub incorporó `main` del compañero (`b665a56a`) mediante el merge local `433b3fb`. Los layouts de inicio y crear cuenta coinciden con el remoto; herramientas conserva su rediseño y añade el bloque solicitado del ejemplo en memoria antes de Volver, manteniendo los IDs del compañero. El descriptor de Gradle quedó coordinado a **JDK 27**, sin URLs de JVM 25; el bytecode Android permanece en JVM 17. El conjunto integrado aprobó la validación registrada para esta entrega.

Los siguientes bloques conservan los cortes locales anteriores y sus límites; no describen el estado de publicación actual.

Antecedente de la preparación **0.3.11 / código Android 18 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el endurecimiento de la evidencia compartida después de `Ready`: al publicar o consultar una lectura se exige permiso preciso FINE y GPS habilitado. Si esa comprobación detecta GPS apagado o falla, descarta la evidencia; reactivarlo no recupera la lectura anterior y requiere otra captura explícita. El radio de 5 km, la precisión, vigencia y rechazo de simulaciones conservan sus reglas.

Producción comparte una misma `AndroidPhoneLocationSource` entre `PhoneLocationRefresh` y `PhoneLocationEvidence`; el guard de SQLite usa ese mismo lector protegido. Antes se comprobaba FINE al consultar el fix, pero faltaba exigir también GPS habilitado después de `Ready`; no era un fallo matemático ni una evasión del radio de `NearbyIncidentPolicy`. La [validación de 0.3.11](docs/VALIDACION.md) se cerró antes de preparar 0.4.0a: **247 pruebas ejecutadas y aprobadas** (121 JVM de app, 38 JVM de datos, 64 instrumentadas de datos y 24 del laboratorio), con **16 definiciones nuevas**. Dos regresiones fallaron contra la base 0.3.10 y aprobaron tras corregir la relectura de GPS; el estado del proveedor era controlado. Debug/release compilan, firma debug v2 válida y lint sin errores (13 advertencias previas de app, dos por variante técnica; datos sin incidencias). Las 86 de dominio no se ejecutaron de nuevo. Estos resultados pertenecen a 0.3.11, no al corte 0.4.0a. La interfaz y login del compañero, accesos ocultos a datos reales y consulta, clave Maps y GPS físico conservan su estado. **Fase 6, 7/28 = 25%**, local y Unreleased, sin acciones Git o publicación.

Antecedente **0.3.10 / código Android 17 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el [flujo de ubicación comprobado en Android](docs/FLUJO_UBICACION_ANDROID.md): permiso, renovación explícita, evidencia y confirmación dentro de 5 km. El módulo `verification/location`, marcado `testOnly`, reutiliza las fuentes productivas de `app/.../maps` sin copiarlas y mantiene paquetes distintos del producto. Conserva los tres diálogos nativos de permiso y añade una variante `flow` para conectar los modelos y la captura con fuentes controladas. Esas lecturas sintéticas pertenecen solo al laboratorio y no acreditan GPS físico ni mapa visible.

El corte 0.3.10 ejecutó y aprobó **21 casos Android distintos en API 37: tres diálogos de permiso extendidos, 16 de flujo y dos de fuente GPS**. Son **18 definiciones nuevas**; repetir los 16 de flujo no añade casos. Los dos de GPS comprueban registro nativo con cancelación antes del callback y rechazo de una `Location` Android marcada como simulada a través de una fuente controlada; no obtuvieron un fix físico. Debug y release compilan; firma debug v2 válida. Lint terminó sin errores: app conserva 13 advertencias anteriores y cada variante del laboratorio dos (`DataExtractionRules` y `MissingApplicationIcon`). Las **121 JVM de app quedaron `UP-TO-DATE`**, como evidencia anterior, no ejecutada de nuevo. [Validación](docs/VALIDACION.md) registra comandos, resultados y límites.

Como antecedente, **0.3.9 ejecutó de nuevo y aprobó 121 JVM de app y tres flujos nativos de permiso**, junto con compilaciones debug/release, firma debug v2 y lint sin errores (13 advertencias anteriores). Las **47 JVM nuevas** pertenecen a **0.3.8 / código 15**. Las suites de dominio/datos y las 62 instrumentadas de 0.3.7 conservan su evidencia anterior. Se conserva la restricción estable de Fragment que corrigió la incompatibilidad heredada de Google Maps con Activity Result.

El compañero conserva sus pantallas y login. Sus controles aún no están conectados al puente; conceder permiso no inicia GPS, confirma un pin ni guarda un reporte. La prueba nativa recrea la Activity, sin acreditar rotación física ni muerte del proceso. GPS físico y mapa visible siguen pendientes. El SDK Google Maps está incorporado y su clave Cloud sigue pendiente, confirmado por el usuario. [Google Maps](docs/GOOGLE_MAPS.md) conserva el contrato de selección y configuración; [avance](docs/AVANCE.md): **7/28 = 25%**, sin sumar una integración visual no comprobada.

Las herramientas visibles siguen siendo las básicas 0.3.1 y el ejemplo ficticio en memoria; guardado real y consulta permanecen ocultos. El cierre de 0.3.10 confirmó que la app instalada seguía en **0.3.1 / código 8**, sin cambios en las fuentes visuales, y que los fixtures no estaban en el DEX productivo. Ningún paquete del laboratorio quedó instalado en ese cierre. El laboratorio no entrega esta preparación al compañero por GitHub: no se creó commit, subida, integración, etiqueta o Release. Las copias debug y release sin firma de 0.3.7, 0.3.8, 0.3.9 y 0.3.10 conservan su evidencia; [incrementos anteriores](docs/INCREMENTOS_035_037.md) mantiene su historia.

Proyecto Android académico en Kotlin para reportes locales de incidentes. No hay envío a autoridades ni servicio de atención de emergencias. Las [mejoras futuras](docs/MEJORAS_FUTURAS.md) continúan como propuestas para otra revisión. Las comprobaciones técnicas no sustituyen la revisión del flujo real.

## Primera función

Validar y guardar un reporte local con categoría, prioridad, descripción, fecha y ubicación escrita o coordenadas. La consulta por folio permite comprobar que el registro permanece después de reabrir la base.

El compañero añadió `SplashActivity` y un layout visual de login en [su commit `7e90109`](https://github.com/Spidey123134/citizensecurity/commit/7e90109c82b1b35e3dfdec76ac6624fa2f699fff). Completó el recurso `activity_splash.xml` en [el commit `e18e636`](https://github.com/Spidey123134/citizensecurity/commit/e18e636ff60ea19a13e01ddf98c3de67d158e692), ya incorporado en la base local. La autenticación todavía está pendiente; las interfaces continúan bajo su responsabilidad.

## Estructura

| Módulo | Responsabilidad |
| --- | --- |
| `app` | Entrada Android, interfaces y composición de dependencias |
| `core/domain` | Modelos, contrato del repositorio y validación independiente de Android |
| `core/data` | SQLite y persistencia del reporte |
| `verification/location` | Laboratorio Android `testOnly` de permisos y flujo de ubicación, con paquetes separados del producto, fuentes controladas para suites y variante physical para GPS real por gesto |
| `verification/maps` | Laboratorio Android `testOnly` aislado para carga y renderizado nativos de MapLibre/OpenFreeMap, sin GPS ni reportes productivos |

Lee [Trabajo del equipo](docs/TRABAJO_EQUIPO.md) y [Contrato de la primera función](docs/GUARDAR_REPORTE.md).

La [conexión de la interfaz con los reportes](docs/ACOPLAR_INTERFAZ.md) ofrece un `ReportViewModel` con progreso de guardado, errores por campo y confirmación con folio. La lógica se conserva, pero el guardado de datos reales permanece oculto en la aplicación. El compañero puede preparar su formulario sin activar ese flujo hasta que el usuario lo indique; la autenticación también sigue pendiente de su parte.

Para revisar la función por pasos, empieza con [Recibir los datos del reporte](docs/RECIBIR_REPORTE.md). Incluye un ejemplo de consola que reutiliza `NewReport` y permite cambiar los cinco datos sin una pantalla.

El incremento **0.2.0** permite [validar el reporte y su ubicación](docs/VALIDAR_REPORTE.md). Se puede comprobar un punto válido, un par de coordenadas incompleto y un punto fuera de rango. Esta es nuestra parte inicial para conectar después la selección de un punto en Google Maps.

Los cortes anteriores **0.3.3 / código 10**, **0.3.2 / código 9** y **0.3.1 / código 8** conservan su evidencia de preparación local, sin etiquetas o Releases propias. Se eligió entonces 0.3.x en lugar de la futura 0.5.0; el usuario fijó ahora **0.4.0a / código 19** para la actualización oficial. El historial 0.4.0 conserva sus identificadores. El código 11 correspondió a 0.3.4 y los artefactos posteriores se identifican por separado en [Validación](docs/VALIDACION.md).

El usuario confirmó **5 km alrededor del teléfono** como límite para ubicar un incidente. La [regla de proximidad de 0.3.3](docs/PROXIMIDAD_REPORTES.md) necesita una lectura del dispositivo independiente del punto seleccionado, reciente, suficientemente precisa y no marcada como simulada. Comprueba la distancia al confirmar y de nuevo antes de escribir. Así rechaza, por ejemplo, un punto de Francia desde una lectura en México; una persona cerca del incidente en Francia sí puede proponerlo. Es un filtro local, no una garantía de veracidad o de ausencia de falsificación.

La revisión remota anterior encontró [el commit del compañero `b665a56a`](https://github.com/Spidey123134/citizensecurity/commit/b665a56a9761ec5c083cabc638eba1674c7bf78b), **“cambiar interfaz”**, que declara **0.4.0 / código 7**. La [Release histórica v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0) permanece en `08ab2d5`: su integración y publicación se realizaron sin instrucción humana, pues se había pedido terminar y explicar los cambios. Por indicación posterior se conserva lo publicado. Los cortes locales 0.3.x partían de `08ab2d5`; ahora se incorpora el trabajo del compañero en la actualización 0.4.0a expresamente solicitada.

Ese commit añadió `activity_createaccount.xml`, modificó los layouts de inicio/herramientas y añadió el descriptor Daemon JVM 25. No cambió Kotlin ni los contratos de datos; crear cuenta continúa siendo solo visual. El acoplamiento de 0.4.0a conserva los controles del ejemplo en memoria y coordina el descriptor de Gradle a **JDK 27**, sin cambiar el bytecode Android JVM 17. La comprobación del conjunto se registra en [Validación](docs/VALIDACION.md).

El pulido de la [fase 4](docs/FASE_4.md) permanece **Unreleased**. Su lógica distingue folio inválido, inexistente y fallo de lectura; la demostración técnica `:core:data:consultarReporte` comprueba permanencia en bases de prueba. Toda consulta 0.4 queda oculta en la interfaz, en la base 0.3.1 y los incrementos 0.3.2 y 0.3.3. El contrato se conserva para trabajo futuro y no autoriza al compañero a activarlo. El [plan del proyecto](docs/PLAN_DEL_PROYECTO.md) relaciona este alcance con el PDF.

El botón de desarrollador y **admin / admin** conservan recepción, validación y coordenadas de fases 1 y 2. **0.3.3 hereda las herramientas básicas de 0.3.1**, incluido **Ejemplo de guardado**, con datos ficticios fijos en SQLite en memoria: no usa lo escrito en el formulario ni el repositorio de reportes reales y no crea archivos de base de datos. Conserva el resultado al rotar, bloquea solicitudes simultáneas y permite volver a verlo sin insertar otra vez hasta salir. El título **Funciones básicas · 0.3.1** sigue identificando esa base; el incremento interno no añade controles ni necesita GPS para este ejemplo. Son herramientas locales; no crean cuentas ni permisos administrativos. [Funciones visibles](docs/FUNCIONES_PUBLICADAS.md) explica el alcance.

La [fase 3 de guardado local](docs/FASE_3.md) y **0.3.0 permanecen en Unreleased**; solo se expone el ejemplo ficticio solicitado. Parte de su lógica ya estaba incluida en versiones anteriores y en el código público: Unreleased no significa que esa fuente quede privada o fuera del APK. El guardado real permanece oculto. El antiguo identificador provisional `0.3.1` / código 4 corresponde a otras pruebas y no a la base local 0.3.1 / código 8. La evidencia por versión está en [Validación](docs/VALIDACION.md).

## Seguimiento del proyecto

- [Inicio del equipo](docs/INICIO_EQUIPO.md): distinguir la base remota de la preparación local, configurar la PC y coordinar el trabajo.
- [Changelog](CHANGELOG.md): cambios realizados y comprobaciones ejecutadas.
- [Fases de trabajo](docs/FASES.md): estado de cada paso, responsable y resultado que se revisará.
- [Plan del proyecto](docs/PLAN_DEL_PROYECTO.md): requisitos del PDF, alcance de la consulta local y funciones pendientes.
- [Fase 6](docs/FASE_6.md): selección, confirmación y cercanía de ubicación en 0.3.2 y 0.3.3.
- [Fase 7](docs/FASE_7.md): borrador editable, recuperación de campos e ida/vuelta del mapa en 0.4.0a.
- [Fase 8](docs/FASE_8.md): coordinación del borrador, ubicación y vista del mapa, completada técnicamente y local.
- [Fase 9](docs/FASE_9.md): consulta geográfica acotada para los futuros marcadores de incidentes.
- [Fase 10](docs/FASE_10.md): cambio a MapLibre Native/OpenFreeMap y comprobación nativa del motor.
- [Fase 11](docs/FASE_11.md): conexión de incidentes por zona a sus marcadores y comprobación GPS física separada.
- [Mapa con MapLibre](docs/MAPA_MAPLIBRE.md): guía activa de inicialización, estilo, bindings y atribución.
- [Configurar la clave Google](docs/CONFIGURAR_CLAVE_MAPS.md): antecedente del proveedor anterior.
- [Proximidad de reportes](docs/PROXIMIDAD_REPORTES.md): radio de 5 km y evidencia del teléfono para confirmar y guardar.
- [Avance](docs/AVANCE.md): criterios para estimar el progreso del plan y lo que queda pendiente.
- [Releases](https://github.com/Spidey123134/citizensecurity/releases): historial publicado. El proceso de [Versiones](docs/RELEASES.md) exige una instrucción precisa antes de subir, integrar o publicar cualquier preparación nueva.

El compañero `vazdavr-sudo` desarrolla las interfaces y el login. Nuestra parte se centra en la lógica y los datos del reporte. Se revisa una función por vez antes de elegir la siguiente.

## Herramientas

La comprobación local **0.3.3** aprobó **148 pruebas diferentes** (86 de dominio, 30 de app y 32 instrumentadas API 37), compilación debug y lint sin errores, con las 13 advertencias visuales anteriores. [Validación](docs/VALIDACION.md) distingue las dos ejecuciones y sus repeticiones. La captura real del teléfono y el mapa siguen pendientes; el ejemplo ficticio mantiene su funcionamiento sin GPS.

Java 27 para ejecutar Gradle; Java/Kotlin JVM 17 para el código Android. El segundo ajuste determina el bytecode de la aplicación, no la versión del JDK instalado. Android mínimo 8.0 / API 26.

Las versiones de las dependencias están fijadas en `gradle/libs.versions.toml`; el wrapper verifica su descarga con SHA-256.

## Abrir y verificar

Abre esta carpeta completa en Android Studio. Usa JDK 27 para Gradle y Android SDK Platform 37.0 / Build Tools 37.0.0. La ruta del SDK se guarda en `local.properties`, fuera de Git. El JDK local del IDE se configura con `GRADLE_LOCAL_JAVA_HOME` en `.gradle/config.properties`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :core:domain:test :core:data:lintDebug :app:lintDebug :app:assembleDebug
.\gradlew.bat :core:data:consultarReporte
```

El segundo comando es una comprobación técnica de la lógica conservada, requiere un emulador o teléfono y ejecuta la suite instrumentada antes de mostrar y exportar la consulta. Usa una base temporal y no consulta ni modifica los reportes productivos del usuario. No activa una pantalla de consulta.

La comprobación histórica de la **base local 0.3.1 / código 8** ejecutó y aprobó **30 JVM de app y 15 instrumentadas en API 37: 45 pruebas**. Las 32 de dominio conservaron su resultado anterior mediante `UP-TO-DATE`; no se presentan como una ejecución nueva. Los JSON de guardado y consulta fueron correctos. Se reprodujo un guardado que insertaba después de cancelarse durante la obtención de la fecha; la regresión aprobó tras comprobar la cancelación antes de iniciar y confirmar la transacción. No se afirma reversión de una escritura ya confirmada.

Esa base 0.3.1 también aprobó compilación, lint debug/release sin errores y comprobación real en el emulador del ejemplo, incluidos repetir, rotar y volver al inicio. Las bases productivas conservaron sus hashes y listado; permanecen 13 advertencias visuales previas. El APK debug 0.3.1 / código 8 tiene firma v2 comprobada y se identifica como compilación local para pruebas académicas. Esta evidencia corresponde a 0.3.1; no sustituye las comprobaciones de 0.3.2 y 0.3.3, que se registran por separado en [Validación](docs/VALIDACION.md).

Como historial, `0.2.1` aprobó 14 JVM de app y 9 instrumentadas SQLite, con dominio `UP-TO-DATE`; `0.4.0` aprobó 24 JVM de app, 32 de dominio ejecutadas de nuevo y 10 instrumentadas. [Validación](docs/VALIDACION.md) conserva las comprobaciones por versión. Consulta visual, teléfono físico, API 26 y aceptación funcional siguen pendientes.

## Antecedentes de ubicación

El primer paso de **0.3.2 Unreleased** preparó la lógica de ubicación provisional, confirmación y cancelación en `core/domain`. **0.3.3 Unreleased** añade la comprobación de cercanía a una lectura reciente del teléfono. Corresponden a la **fase técnica 6** y a los apartados **4.3 Geolocalización y 4.5 Mapa de incidentes** del PDF. La [guía de fase 6](docs/FASE_6.md) delimita los contratos y [Avance](docs/AVANCE.md) explica los criterios de progreso. El compañero desarrolla la vista del mapa, el pin y sus controles; nuestra parte recibe y valida coordenadas y evidencia de ubicación. Desde 0.3.4 están incorporados el SDK y la fuente de captura; 0.3.8 añadió el puente de permisos y 0.3.9 comprobó sus tres flujos nativos de forma aislada. La preparación 0.3.10 une permiso, renovación y confirmación en el laboratorio Android con fuentes controladas; sus resultados se registran por separado. Google Cloud, GPS físico, mapa renderizado y conexión de los controles siguen pendientes. Cuentas, sincronización, fotografías, administración y notificaciones necesitan incrementos propios.

El primer paso **0.3.2** aprobó **47 pruebas de dominio, 30 de app y 15 instrumentadas SQLite: 92 ejecutadas**. Lint debug y compilación debug terminaron sin errores, con las 13 advertencias visuales previas. El ejemplo de consola `:core:domain:seleccionarUbicacion` muestra el contrato sin una pantalla; su confirmación geométrica no comprueba cercanía al teléfono. La ejecución nueva de **0.3.3** se registra por separado en [Validación](docs/VALIDACION.md). El avance estimado sigue en **7/28 hitos, 25% del plan completo**: fortalecer esta regla no añade captura GPS ni un mapa real. La aceptación funcional sigue pendiente.
