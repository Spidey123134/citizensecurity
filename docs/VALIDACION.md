# Validación y comprobaciones del proyecto

## 0.5.2 — fase 14 — 2026-10-09

**529 casos distintos aprobados:** 294 JVM app, 96 JVM dominio, 38 JVM datos, 86 SQLite/fuente Android y 15 Android formulario/controles. Nuevas definiciones: **21** (6 formato, 3 guardado, 4 revisión UI, 8 SQLite idempotente). Dominio se ejecutó en el primer pase del contrato vigente; las suites afectadas se repitieron después de corregir el acceso a rutas previo a la validación del nombre. No se suman repeticiones. [Fase 14](FASE_14.md) explica límites y reproducción.

La suite SQLite completa encontró inicialmente una regresión en el rechazo de nombres inválidos. La corrección movió `getDatabasePath` después de la validación; el segundo pase aprobó 86/86. Se comprobó cancelación real entre commit/retorno, recuperación sin nueva escritura, conflicto de contenido, concurrencia entre instancias, reapertura y rechazo GPS antes de crear archivos. Las bases nativas están aisladas, no son datos del producto.

Los 11 casos del formulario incluyen cuatro nuevos de revisión, corrección, recreación/volver y salida con tipo seleccionado. Comprobaron hashes de archivos de base sin cambios. Los cuatro casos restantes se ejecutaron por separado con permisos reales denegado/aproximado/preciso y ubicación desactivada preparados en Android. No se fabricó GPS ni se conectó teléfono. Solo se instaló en la instancia AVD read-only propiedad de esta validación.

Debug/release y APK de pruebas compilan. Lint: cero errores, 19 advertencias anteriores por variante de app y cero en datos. Firma debug v2 e integridad del APK académico comprobadas para publicar. Guardado real/consulta siguen ocultos; aceptación física y retención durable de identidad de escritura pendientes. Los resultados y capturas están en `.gradle/validacion/release052-*`, separados de las entregas previas.

## Antecedentes

## Entrega oficial 0.5.1 — formulario y mapa visibles

**9 de octubre de 2026 · código Android 21.** El usuario indicó «desde ahora puro released». Esta entrega incorpora las fases 12 y 13 a las variantes compartidas: Desarrollador → **Nuevo reporte · borrador** permite llenar el reporte, elegir un punto en MapLibre/OpenFreeMap, conservar el texto al volver y revisar los campos. El mapa muestra calles, nombres y pin. Los controles distinguen permiso denegado/aproximado/preciso, GPS desactivado y reintento, y funcionan al girar la pantalla. Publicación: [Release v0.5.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.1).

La confirmación mantiene GPS preciso, reciente, no simulado y radio de 5 km. El formulario **todavía no guarda ni envía reportes ni avisa a emergencias**; guardado y consulta reales siguen ocultos por la instrucción específica anterior. El login y el ejemplo ficticio del compañero se conservan. Se comprobaron **296 casos: 285 JVM y 11 nativos**, con cuatro nuevos casos de permisos/ubicación desactivada contra Android real, sin inyectar estados de permiso en el modelo ni ubicaciones falsas. Debug/release compilan, firma debug válida y lint sin errores, con 19 advertencias anteriores por variante.

**Avance: 11/28 = 39.3%.** Se acredita el hito existente de **4.3 permisos y estados GPS (3/4)** por las comprobaciones de los controles, respaldadas por las pruebas previas de fuente/estados. GPS físico y confirmación completa dentro de esta pantalla siguen pendientes: no se completa 4.3.4. Publicar una versión no suma otro hito. [Entrega 0.5.1](ENTREGA_0_5_1.md) explica los puntos y los pendientes; los cortes Unreleased anteriores permanecen como historia y su código de interfaz se incluye en esta entrega.

## Antecedentes conservados

## Fase 13 — permisos y recuperación del GPS — Unreleased

**Avance local del 9 de octubre de 2026**, autorizado por el usuario al pedir el siguiente paso. La vista de reporte ahora explica permiso aproximado, denegación, cancelación y fallos al comprobar o abrir el permiso. Un GPS sin respuesta indica cómo reintentar conservando el punto; ubicación desactivada pide volver a buscar por gesto. Los controles del mapa se pueden desplazar en orientación horizontal y usan el tamaño real de la ventana.

Se mantienen radio 5 km, GPS preciso/reciente/no simulado y confirmación validada; no hay GPS automático ni apertura automática de Ajustes. Debug se identifica como **0.5.0-fase13 / código 20**. La interfaz nueva sigue excluida de release, el login se conserva y guardado/consulta reales permanecen ocultos. **Sin commit, subida ni publicación; main y Release 0.5.0 intactos.** [Fase 13](FASE_13.md) registra comprobación y pendientes. Avance acreditado **10/28 = 35.7%**; la aceptación con GPS físico de esta pantalla sigue pendiente.

### Cierre de fase 13

**292 casos ejecutados y aprobados:** 285 JVM de app (12 nuevos) y siete nativos de interfaz (uno nuevo de orientación horizontal; seis de fase 12 repetidos). **13 definiciones nuevas**, sin sumar repeticiones como casos adicionales. Los mensajes representan estados controlados en JVM; el laboratorio no acredita elección aproximada/denegada del diálogo real ni GPS físico de esta pantalla. En Android API 37 se comprobó mapa con calles, nombres y pin, conservación/cancelación/recreación y acceso a los controles en horizontal. Los hashes de las bases existentes permanecen iguales antes y después de cada caso.

Debug, release y APK de instrumentación compilan. Lint final: cero errores y 19 advertencias anteriores por variante; se sustituyó Configuration.screenHeightDp por LocalWindowInfo para adaptar los controles a la ventana real. Firma debug v2 válida, nombre 0.5.0-fase13/código 20. Activity nueva solo en debug y pruebas fuera del producto. APK oficial 0.5.0 preservado por SHA-256; APK de fase 12 también conservado. Capturas fase13-mapa.png y fase13-formulario.png revisadas, sin lecturas GPS del usuario. No se instaló en el teléfono, no hubo publicación y la aceptación física permanece pendiente. Recibo ignorado: .gradle/validacion/cierre-fase13.json; [fase 13](FASE_13.md).

## Antecedente: fase 12

## Fase 12 — interfaz de nuevo reporte — Unreleased

**Trabajo local del 9 de octubre de 2026.** El usuario autorizó empezar la interfaz y reutilizar el primer borrador. Se recuperan su estilo azul/verde y la organización del formulario, conectados a los modelos actuales. Login y entrada del compañero se conservan. En debug, Desarrollador → Nuevo reporte · vista previa abre campos de incidente, revisión y selección de punto en MapLibre/OpenFreeMap con calles. No envía avisos ni guarda reportes reales; la consulta real permanece oculta.

La confirmación mantiene permiso preciso, GPS reciente y válido, rechazo de ubicación simulada y radio de 5 km. Abrir el mapa o volver no inicia GPS. El APK local se identifica como **0.5.0-fase12 / código 20**; la variante release excluye la nueva Activity y oculta su botón. **Main, tag y Release oficial 0.5.0 no cambian**. Esta instrucción autoriza este desarrollo local, no commit, subida, integración o nueva publicación. [Fase 12](FASE_12.md) detalla pruebas y pendientes. El avance acreditado sigue **10/28 = 35.7%** hasta aceptar el flujo visual con GPS físico.

### Cierre de fase 12

273 casos JVM de app ejecutados y aprobados en el primer pase y seis casos nuevos de interfaz aprobados en API 37: **279 casos de este corte**, sin sumar repeticiones ni las 52 pruebas históricas de otros laboratorios no ejecutadas aquí. Las seis nuevas comprueban revisión, retención de campos y texto rechazado, ida/vuelta/recreación del mapa, propuesta por toque y bloqueo sin GPS; los hashes de las bases previas quedan iguales. Capturas reales con calles, nombres y pin revisadas. Debug/release/APK de pruebas compilan; firma debug v2 válida. Lint final: cero errores y 19 advertencias anteriores por variante. Ruta manual desde Splash, Desarrollador y botón de vista previa aprobada. La Activity nueva solo existe en debug, las pruebas quedan fuera del producto y el APK oficial 0.5.0 conserva su hash. Se usa una instancia AVD read-only; no se instala en el teléfono. GPS físico y aceptación de esta pantalla pendientes. [Fase 12](FASE_12.md) explica ajustes y conserva fallos iniciales sin presentarlos como aprobados. Recibo local ignorado: .gradle/validacion/cierre-fase12.json.

## Antecedentes conservados

## Preparación oficial 0.5.0 — 9 de octubre de 2026

Autorizada integración y publicación de 0.5.0/código Android 20. Este corte cambia metadatos y documentación, sin modificar las funciones o la interfaz. Compilación :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease: BUILD SUCCESSFUL en 10 s; 168 tareas, 30 ejecutadas y 138 reutilizadas. Lint: cero errores y 19 advertencias anteriores en cada variante. No se instalan APK sobre la app del usuario ni se sustituyen los archivos anteriores.

Los 325 casos y 57 definiciones nuevas del cierre de fase 11 son evidencia previa, no pruebas ejecutadas de nuevo por este cambio de versión. GPS físico validado por separado. La pantalla productiva del mapa y aceptación del flujo siguen pendientes.

| Artefacto archivado | Tamaño | SHA-256 |
| --- | --- | --- |
| citizensecurity-0.5.0-academico.apk | 54423081 | `c3f62aa5fc8b030e11bae6b1518c5171538a046d8dd4e6da1b24ab956a21317b` |
| citizensecurity-0.5.0-release-unsigned.apk | 50606415 | `4455ecad7a64f5769e75a23f3fdd8a4581d3e5b86dee8bc6fd12e4804fb7e6a7` |

El APK académico debug es el instalable adjunto; el release sin firma se conserva solo como evidencia local.

## Registro de validaciones anteriores

La comprobación activa es **[fase 11 — Unreleased](FASE_11.md)**: incidentes por zona y marcadores, iniciada el **7** y cerrada técnicamente el **8 de octubre de 2026**. **325 casos distintos aprobados, 57 definiciones nuevas**, debug/release, firma debug v2 y lint sin errores. Se comprobó el mapa nativo con dos reportes SQLite ficticios y GPS físico en el teléfono del usuario por separado. La oficial conserva 0.4.0a/código19; interfaz productiva y aceptación pendientes.

## Cierre de fase 11 — 7 y 8 de octubre de 2026

| Grupo | Casos aprobados | Ejecución del corte |
| --- | ---: | --- |
| JVM app | 273 | Ejecutadas en el pase final |
| Android flow | 34 | Ejecutadas en el primer pase; no repetidas sin cambios |
| JVM monitor del mapa | 8 | Ejecutadas en el primer pase; UP-TO-DATE después |
| Android mapa | 10 | Ejecutadas en el pase final: cuatro anteriores y seis nuevas |
| **Total distinto** | **325** | Cero fallos, errores u omisiones en XML finales; repeticiones no sumadas |

**57 definiciones nuevas:** 31 JVM del coordinador, 20 JVM del host y seis Android de SQLite→modelo→binding→GeoJSON/capa nativa. Las suites anteriores de dominio/datos/SQLite conservan sus recibos y no se suman como ejecuciones nuevas. La observación GPS física es manual, independiente de 325.

Con JDK 27 y `ANDROID_SERIAL=emulator-5554`, el primer pase incluyó `:verification:location:connectedFlowDebugAndroidTest` y las mismas tareas de comprobación siguientes. El teléfono físico quedó fuera de todas las suites automáticas. Tras resolver dos aserciones de precisión, el pase final fue:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:lintRelease :app:assembleRelease :verification:maps:testDebugUnitTest :verification:maps:connectedDebugAndroidTest :verification:maps:lintDebug :verification:location:lintFlowDebug :verification:location:assemblePhysicalDebug :verification:location:lintPhysicalDebug --console=plain
```

**BUILD SUCCESSFUL in 1m 1s**: 327 tareas, 45 ejecutadas y 282 reutilizadas. El primer pase falló únicamente en una JVM de redondeo GeoJSON y una nativa de geometría de tesela: serializar a siete decimales no conserva los bits y `queryRenderedFeatures` devuelve coordenadas reconstruidas de puntos enteros. Las pruebas exigen valores exactos antes de serializar, redondeo concreto y tolerancias derivadas del SDK; la nativa admite 1.4×10⁻⁶ grados en zoom15 (<0.16 m). Se conservaron coordenadas persistidas, fuente GPS y todas las reglas de 5 km. [Fase 11](FASE_11.md) enlaza el código primario que justifica la precisión. No se cuentan intentos fallidos o repetidos como casos adicionales.

Un ajuste posterior exclusivo de mensajes plurales del laboratorio pasó `:verification:maps:assembleDebug :verification:maps:lintDebug` (**BUILD SUCCESSFUL in 6s**); no se repitió la instrumentación por un cambio de texto sin alterar la lógica. Firma debug v2 verificada. Lint cero errores: app19 advertencias anteriores en debug/release, flow2, physical2, mapas8 (seis diagnósticos LogNotTimber y dos de icono/extracción). No se cambió la interfaz productiva para silenciar avisos. La primera compilación physical corrigió el conflicto de etiqueta en su manifiesto de variante mediante `tools:replace`, sin tocar el manifiesto del producto.

La captura y snapshot nativos muestran **dos círculos** y nombres de **Calle Tacuba, Calle Moneda y Calle Venustiano Carranza**, con atribución visible. `queryRenderedFeatures` acredita dos folios iguales a los leídos de SQLite y proyectados en esta región: **171 objetos de calles, 23 etiquetas y 49 edificios**, una consulta terminada. Región/cámara fijas CDMX, sin GPS. Los seis casos comprueban gesto explícito, nueva zona sin IDs anteriores, filtros/límite/`hasMore`/vacío, estilo y recursos ajenos, cancelación de entrega pendiente y recreación/cierre. La pausa controlada ocurre después de completar SQLite y antes de entregar: no afirma interrumpir el motor SQLite. La aceptación visual de la pantalla productiva sigue pendiente.

La prueba manual en **Motorola Edge 50 Fusion / API36** agotó primero 15 s. Cerca de una ventana obtuvo GPS real: **6.668 m de precisión estimada, 46 ms de edad al evaluar y sin marca de simulación**; punto cercano permitido, otro a ~11 km rechazado por radio. No se guardó ningún reporte ni coordenada. El recibo `fase11-gps-fisico.json` contiene solo métricas, decisiones y hash del APK técnico inicial. Después se compiló/pasó lint un ajuste de identificación PENDING/CANCELLED para que un resumen anterior no acredite otra captura; la prueba física corresponde al helper inicial, ambos con 15 s y adaptadores/reglas idénticos. El paquete separado sigue instalado en el teléfono; no es actualización del producto.

Recibo ignorado `.gradle/validacion/cierre-fase11.json`: **18 APK anteriores preservados por hash**, HEAD/índice y **63 archivos de base** intactos, incluidos recursos/pantallas/configuración productiva y dominio/datos. Se permiten únicamente cambios de configuración de los dos laboratorios. Producto del AVD conservado0.3.1/código8 y teléfono0.4.0/código7, con APK y fechas intactos. Los fixtures del laboratorio no están en el DEX productivo. Los artefactos locales fase11 son separados, metadato0.4.0a/código19:

| Artefacto técnico local | Bytes | SHA-256 |
| --- | ---: | --- |
| Debug firmado | 73.521.855 | `a147e67056e0bf50415c3659d1b7b074f8095cd5d83c65700b9eae99b2e39ba0` |
| Release sin firma | 50.606.419 | `4a4c07db3af5a399968e18d6dc87ade33452f9ac2c95b679e0963b93ad23463d` |

Se acreditan solo dos hitos existentes: **4.5 mapa por zonas/marcadores, 3/4** y **4.3 captura física, 2/4**. **10/28 = 35.7%**, pesos intactos. Faltan pantalla productiva, flujo GPS y conjunto de estados/permisos integrados, aceptación, API26 y demás funciones del plan. Guardado real/consulta ocultos. Todo local Unreleased, sin Git, publicación, Cloud, banco, borrado del usuario ni apagado.

## Antecedente: cierre de fase 10
La comprobación activa es **[fase 10 — Unreleased](FASE_10.md): MapLibre Native + OpenFreeMap**, cerrada técnicamente el **7 de octubre de 2026** con **268 casos distintos aprobados**, compilación debug/release, firma debug v2 y lint sin errores. Se usa OpenGL ES 13.6.1 y se comprobó el mapa real con nombres legibles en un APK técnico aislado. [Mapa con MapLibre](MAPA_MAPLIBRE.md) es la guía vigente. La interfaz productiva, GPS físico y aceptación completa siguen pendientes; la Release oficial continúa siendo 0.4.0a.

## Cierre de fase 10 — 6 y 7 de octubre de 2026

| Comprobación | Casos aprobados |
| --- | ---: |
| JVM de app: adaptadores, permisos, selección, borrador y consulta | 222 |
| Flujo Android de ubicación y borrador | 34 |
| JVM del monitor de carga del mapa | 8 |
| MapLibre nativo: mapa real, gestos, recreación y fallos/cierre | 4 |
| **Casos distintos del corte** | **268** |

Cero fallos, errores u omisiones en los resultados finales. Son **25 definiciones añadidas**: 13 de adaptadores, ocho del monitor y cuatro nativas. Siete comprobaciones de inicialización reemplazan nueve requisitos obsoletos de Google; esa sustitución no se cuenta como nueve pruebas nuevas. Las repeticiones de las mismas suites para reparar el laboratorio o comparar renderizadores tampoco aumentan el total. Dominio y datos conservan la evidencia de fase 9; no se volvieron a ejecutar ni se suman a las 268.

El pase de OpenGL ejecutó app JVM, flujo Android y monitor, y compiló debug/release. La prueba nativa detectó que el selector del laboratorio buscaba solo edificios planos: Liberty utiliza `building-3d` desde zoom 14. Se corrigió incluyendo `FillExtrusionLayer`, sin relajar las comprobaciones de objetos reales. Los ocho casos del monitor y cuatro nativos aprobaron después; se añadió espera de `idle` y snapshot del SDK para verificar la captura, manteniendo el límite de carga de 30 segundos.

La revisión visual detectó texto invisible pese a los nombres presentes en las teselas. Coincide con el [fallo de emuladores de MapLibre #3648](https://github.com/maplibre/maplibre-native/issues/3648) y el [arreglo propuesto #4625 para SwiftShader](https://github.com/maplibre/maplibre-native/pull/4625), todavía sin incorporarlo aquí. Repetir las **mismas cuatro pruebas nativas** con `-gpu host -no-snapshot` permitió comprobar nombres legibles; no se modificó el estilo, los datos ni el SDK para aparentar etiquetas. La comparación utilizó OpenGL traducido a la GPU NVIDIA RTX 3070. No se editó la configuración del AVD ni se incluyó un parche de upstream.

El pase final del mapa terminó **BUILD SUCCESSFUL en 38 s**, 88 tareas: una ejecutada y 87 `UP-TO-DATE`. `connectedDebugAndroidTest` se ejecutó realmente; las tareas reutilizadas de preparación del APK no equivalen a pruebas nativas reutilizadas.

Comandos registrados, con `JAVA_HOME` apuntando a Temurin 27+35:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:lintRelease :app:assembleRelease :verification:location:connectedFlowDebugAndroidTest :verification:location:lintFlowDebug :verification:maps:testDebugUnitTest :verification:maps:connectedDebugAndroidTest :verification:maps:lintDebug --console=plain
.\gradlew.bat :verification:maps:testDebugUnitTest :verification:maps:connectedDebugAndroidTest :verification:maps:lintDebug --console=plain
# Comparación final después de iniciar el mismo AVD con GPU host, sin editarlo:
.\gradlew.bat :verification:maps:connectedDebugAndroidTest --console=plain
```

Lint: cero errores; app conserva **19 advertencias por variante**, flujo técnico **dos** y laboratorio del mapa **ocho** (seis por diagnóstico `LogNotTimber`, dos por icono/reglas de extracción del APK técnico). El diagnóstico no contiene GPS del teléfono, reportes ni secretos. La dependencia final es `org.maplibre.gl:android-sdk-opengl:13.6.1`; el runtime de app no incluye Google Play Services Maps, metadato de clave o lectura de `MAPS_API_KEY`.

La captura `.gradle/validacion/fase10-mapa-real.png`, su snapshot nativo y el JSON se exportaron antes de retirar el paquete técnico. La prueba emplea una cámara fija de Ciudad de México, **sin GPS ni datos personales**. Se observaron estilo cargado, mapa cargado, fotograma completo, estado idle y objetos reales de calles, etiquetas y edificios; la revisión de píxeles confirmó Calle Tacuba, Calle Moneda y Calle Venustiano Carranza. Un toque entrega coordenadas a la sesión original, pero confirmar sin evidencia GPS sigue bloqueado. Recrear conserva la cámara y retira listeners anteriores; un estilo inexistente comunica fallo nativo y destruir la vista cancela la espera. El fallo de estilo no se presenta como cobertura de todos los casos sin conexión.

Se acredita **solo SDK configurado**, dentro de 4.5: **2/4**; total **8/28 = 28.6%**. El laboratorio no acredita pantalla productiva, marcadores de incidentes por zona, GPS físico ni aceptación del flujo visible. La regla de 5 km y accesos ocultos a guardado real/consulta se conservan.

Recibo local ignorado: `.gradle/validacion/cierre-fase10.json`. Se comprobaron **16 APK anteriores**, HEAD e índice, **19 archivos de base fuera de los cinco cambios autorizados de configuración** y la app instalada **0.3.1/código 8**, con su hash y tiempos de instalación. Los fixtures y la Activity técnica están ausentes del DEX productivo. Se retiraron los paquetes técnicos; no se instaló el APK productivo nuevo.

| Artefacto técnico local | Bytes | SHA-256 |
| --- | ---: | --- |
| `citizensecurity-fase10-debug.apk` | 73.521.855 | `481be9febd8ffb2f3a33b7367ff6ecff095b60ccbbbaa4d49ba6b2dc65063393` |
| `citizensecurity-fase10-release-unsigned.apk` | 50.606.419 | `81a5eab8bf5dd673ace7dae2bd7968d9aa95ecd46cf9a42a4d1403bc3fdcacf7` |

La firma debug v2 es válida. Son APK universales con el SDK nativo, locales y Unreleased; conservan metadato **0.4.0a/código 19**, sin reemplazar la Release o sus archivos. No hubo commit, subida, integración, etiqueta, publicación, acción Cloud/bancaria ni apagado.

## Antecedentes y herramientas

La comprobación actual corresponde a **fase 9 — Unreleased: consulta de incidentes por zona**, completada con **457 pruebas distintas ejecutadas y aprobadas, 43 definiciones nuevas**, compilación debug/release, firma debug v2 y lint sin errores, con resultados separados al final. Fase 8 conserva su cierre de 226 ejecuciones y 27 definiciones nuevas; los cortes 0.3.10, 0.3.11 y 0.4.0a también conservan sus resultados históricos propios. La [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a) sigue siendo la referencia publicada y de APK. La petición de avanzar autoriza este trabajo local, sin otra publicación. La aceptación visual, clave Maps y GPS físico siguen pendientes.

Verificación local realizada el 2 de octubre de 2026, hora de México. Esta revisión cubre el contrato de validación y el guardado local; el diseño de pantallas corresponde al compañero.

## Herramientas utilizadas

| Herramienta | Versión |
| --- | --- |
| JDK para ejecutar Gradle | Eclipse Temurin 27+35 |
| Gradle Wrapper | 9.8.0, distribución verificada con SHA-256 |
| Android Gradle Plugin | 9.4.1, Kotlin integrado |
| Kotlin y complemento Compose | 2.4.20 |
| Compile / target SDK | Android 17, API 37.0 / target 37 |
| Build Tools | 37.0.0 |
| Bytecode Java y Kotlin | JVM 17 |
| Android mínimo | API 26 |
| Compose BOM | 2026.09.00 |

Las versiones se comprobaron con fuentes oficiales: [AGP 9.4](https://developer.android.com/build/releases/agp-9-4-0-release-notes), [compatibilidad de Gradle](https://docs.gradle.org/current/userguide/compatibility.html), [configuración de Kotlin](https://kotlinlang.org/docs/gradle-configure-project.html), [Temurin 27](https://adoptium.net/news/2026/10/eclipse-temurin-27-available) y [Android 17](https://android-developers.googleblog.com/2026/06/Android-17.html).

Kotlin 2.4.20 declara compatibilidad completa hasta Gradle 9.7 y AGP 9.3.1. La combinación más reciente utilizada aquí pasó la compilación y las pruebas locales descritas abajo; eso no amplía la matriz de soporte oficial. Se conservan advertencias de obsolescencia de dependencias de las herramientas; deben revisarse antes de actualizar a otra versión principal de Gradle.

## Comando y resultados

```powershell
.\gradlew.bat :core:domain:test :core:data:lintDebug :app:lintDebug :app:assembleDebug :core:data:connectedDebugAndroidTest --console=plain
```

La revisión final se ejecutó con `--rerun-tasks` y terminó en `BUILD SUCCESSFUL`.

| Revisión | Resultado |
| --- | --- |
| Pruebas JVM del validador | 28 aprobadas; 0 fallos, errores u omitidas |
| Pruebas instrumentadas de SQLite | 5 aprobadas; 0 fallos, errores u omitidas |
| Lint de `core/data` | Sin incidencias |
| Lint de `app` | 0 errores; 2 advertencias |
| APK debug | Generado correctamente |

La instrumentación se ejecutó en `Medium_Phone_API_37.0`, Android 17 / API 37. Cada prueba usó una base temporal independiente y eliminó únicamente su archivo de prueba.

Las pruebas comprueban persistencia después de cerrar y reabrir, normalización de texto, folios distintos, rechazo de datos inválidos sin insertar, orden de registros, conservación de coordenadas y nanosegundos, y cierre idempotente. Las unitarias comprueban límites de texto, Unicode, ubicación y fechas.

Las dos advertencias de `app` son el icono todavía pendiente y una sugerencia de optimización del contenedor vacío `FrameLayout`. Estos elementos se resolverán al desarrollar las interfaces. No se ocultan mediante una línea base de lint.

La base SQLite se excluye de las copias automáticas en nube y de la transferencia entre dispositivos mediante reglas del manifiesto, siguiendo la [documentación de Android](https://developer.android.com/identity/data/autobackup).

## Artefactos locales

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256: `4c98ef7d1b4559ad26034f0e17de446cbd85a36afb17e5ae95db9d168dbf0713`.
- Resultados JVM: `core/domain/build/test-results/test/`.
- Resultados instrumentados: `core/data/build/outputs/androidTest-results/connected/debug/`.
- Informes de lint: `app/build/reports/` y `core/data/build/reports/`.

Los artefactos y las rutas locales del SDK/JDK quedan fuera de Git. Esta entrega todavía no incluye un flujo visual para guardar reportes ni una prueba en un teléfono físico o en API 26; el compañero conectará su interfaz al contrato documentado.

## Verificación del incremento 0.2.0

Realizada el 2 de octubre de 2026, hora de México, con las mismas herramientas. Los ejemplos nuevos reutilizan las reglas del dominio y no modifican los contratos ni el esquema de SQLite.

```powershell
.\gradlew.bat :core:domain:recibirReporte :core:domain:validarReporte :core:domain:validarUbicaciones :core:domain:test --console=plain
.\gradlew.bat :core:domain:test --rerun-tasks --console=plain
.\gradlew.bat :core:data:lintDebug :app:lintDebug :app:assembleDebug :core:data:connectedDebugAndroidTest --console=plain
```

Todos terminaron correctamente. Se ejecutaron también entradas personalizadas: coordenadas completas con referencia vacía y tipo/prioridad en minúsculas; cinco errores de conversión en una entrada; y cuatro errores simultáneos de las reglas. Los errores se mostraron por campo en español, sin una excepción visible de conversión.

| Revisión de 0.2.0 | Resultado |
| --- | --- |
| Recepción anterior | Conserva los cinco datos recibidos |
| Reporte predeterminado y coordenadas completas | Reporte válido |
| Coordenada ausente y latitud fuera de rango | Error de coordenadas en cada caso |
| Pruebas JVM | 28 aprobadas; 0 fallos, errores u omitidas; ejecutadas de nuevo con `--rerun-tasks` |
| Pruebas SQLite, emulador API 37 | 5 aprobadas; 0 fallos, errores u omitidas en esta ejecución |
| Lint | `core/data`: sin incidencias; `app`: 0 errores, 2 advertencias existentes |
| APK debug | Versión `0.2.0`, `versionCode = 2`; firma verificada con `apksigner` |
| Separación del ejemplo | El JAR productivo excluye `src/examples` |

Los ejemplos terminan normalmente al mostrar datos inválidos: `BUILD SUCCESSFUL` confirma la ejecución, mientras `Reporte válido.` o los errores indican el resultado de validación. Se conserva un reloj fijo para los tres casos y un único instante actual para la fecha predeterminada del reporte personalizado.

APK local: `app/build/outputs/apk/debug/app-debug.apk`, 11 729 718 bytes. SHA-256: `c7c56d5b8b7234af95c2424c313f774cff12eb4b257c8c89db8523d22b53a1a0`. Este reemplaza el artefacto local de la primera entrega; no se adjunta un APK a la release 0.2.0. GitHub publica el código fuente y las notas de esta versión.

El mapa real, la configuración de Google Cloud y la interfaz siguen pendientes. La verificación funcional del usuario, el teléfono físico y API 26 también quedan pendientes. Las advertencias de herramientas y los límites de compatibilidad indicados arriba siguen vigentes.

## Fase 3 y correcciones — 0.3.0 Unreleased

Verificación del 2 de octubre de 2026, hora de México. Antes de corregir producción, las regresiones nuevas reprodujeron 3 fallos unitarios de 32 casos y 2 fallos instrumentados de 7 casos. Android confirmó que el texto UTF-16 mal formado y los encabezados `U+FEFF`/`U+FFFE` se alteraban al guardarse. También confirmó que `-0.0` se recuperaba como `0.0`, produciendo un reporte diferente del devuelto al crear.

Después se ejecutó:

```powershell
.\gradlew.bat :core:domain:test :core:domain:validarReporte :core:domain:validarUbicaciones :core:data:guardarReporte :core:data:lintDebug :app:lintDebug :app:assembleDebug --console=plain
```

Resultado: `BUILD SUCCESSFUL`. 32 pruebas unitarias y 7 instrumentadas aprobadas, sin fallos, errores u omisiones. La instrumentación se ejecutó en el mismo emulador Android 17 / API 37. Lint de `core/data` sin incidencias y `app` con 0 errores y las 2 advertencias existentes. Los ejemplos de validación conservaron sus resultados.

`guardarReporte` depende de la instrumentación completa, lee la evidencia JSON capturada en Logcat y la exporta a `core/data/build/reports/fase3/<dispositivo>/reporte.json` y `rechazo.json`. La consola mostró un folio UUID generado por el repositorio, coordenadas `19.4326077, -99.1332088` y la recuperación del mismo reporte al reabrir. El caso inválido conservó un registro previo y no insertó otro. Cada prueba usa y elimina una base temporal; la demostración no modifica la base productiva.

Los datos son ejemplos conocidos con reloj fijo para comprobar fechas y nanosegundos. El folio se genera en cada ejecución; `generatedAt` registra el momento real de emisión de la evidencia. La demostración no depende de una interfaz ni de Google Maps.

APK local de desarrollo: versión `0.3.0`, código `3`, 11 729 718 bytes. SHA-256 `5132185a350c8c17f7291c3d741442d03e5e7d335c9d64f0c77e5aa3e5a42ee9`; firma verificada mediante `apksigner`. El artefacto reemplaza el APK local anterior. La versión permanece **Unreleased**, sin etiqueta ni release publicada. La última publicada sigue siendo `v0.2.0`.

La aceptación funcional del usuario, el teléfono físico y API 26 siguen pendientes. Se conservan los límites de herramientas indicados al inicio de este documento.

## Historial de pruebas con identificador provisional 0.3.1

Revisión del 3 de octubre de 2026, hora de México. Se usó `0.3.1` como identificador provisional para comprobar la corrección de un fallo presente desde la base `0.1.0`. No tuvo etiqueta ni release; el siguiente mantenimiento se prepara como `0.2.1`, conservando esta evidencia y el trabajo adelantado de fase 3.

### Reproducción antes de la corrección

En Android 17 / API 37, `cancelarMientrasEsperaNoGuardaOtroReporte` falló: quedaron 2 registros cuando debía quedar solo el primero. La segunda corrutina se había cancelado mientras esperaba el monitor, antes de liberar la primera. La evidencia previa está en `.gradle/validacion/parche-0.3.1-antes-fix.xml` y su log local, fuera de Git; el nombre conserva el identificador provisional. El XML registra 1 prueba y 1 fallo, con fecha `2026-10-03T15:39:19Z`.

La corrección comprueba la cancelación dentro del monitor, antes de llamar a la acción SQLite. El alcance del contrato está en [Guardar reporte](GUARDAR_REPORTE.md).

### Candidato descartado

Se comprobaron diez textos Unicode, incluidos `U+FFFE` y `U+FFFF` interiores. El cursor y la reapertura conservaron exactamente los datos en Android. La prueba diagnóstica pasó en API 37, con fecha `2026-10-03T15:36:41Z`; no se modificó el validador. La prueba queda como cobertura de ese comportamiento.

### Comprobación del parche antes del commit visual

Después de corregir se ejecutó el comando completo de la fase 3, con los ejemplos, las suites, lint y la compilación. Las 9 pruebas instrumentadas aprobaron en API 37, sin fallos, errores u omisiones; el XML registra `2026-10-03T15:41:01Z`. Los ejemplos de fase 2 y la exportación JSON de guardado y rechazo terminaron correctamente.

Las 32 unitarias conservaron su resultado aprobado anterior: la tarea fue `UP-TO-DATE`, con XML previo fechado `2026-10-03T05:39:56Z` (2 de octubre, hora de México). No se presenta como una nueva ejecución de esos 32 casos. Lint de datos terminó sin incidencias y `app` conservaba sus 2 advertencias. En ese estado se generó y verificó un APK `0.3.1`; ese artefacto corresponde a la base anterior al commit visual y no representa el estado integrado actual.

### Comprobación anterior del estado integrado con 7e90109

Se incorporó como base [el commit `7e90109`](https://github.com/Spidey123134/citizensecurity/commit/7e90109c82b1b35e3dfdec76ac6624fa2f699fff), con `SplashActivity`, login visual, tema y configuración del compañero. Ese commit no cambió `core/domain` ni `core/data`, por lo que la evidencia de datos anterior sigue aplicando a esas capas. La autenticación no está implementada.

Se restauraron en el manifiesto `CitizenSecurityApplication` y los atributos que excluyen la base de los respaldos, conservando el launcher de splash, icono, tema y cambios visuales. La comprobación del estado integrado dio estos resultados:

| Comprobación integrada | Resultado |
| --- | --- |
| `:core:data:lintDebug` | Aprobado, sin incidencias |
| `:app:processDebugMainManifest` | Aprobado después de restaurar la aplicación compartida y las reglas de respaldo |
| `:app:assembleDebug` | Falló en `SplashActivity.kt:15`: referencia `activity_splash` sin resolver |
| `:app:lintDebug` | Bloqueado por el mismo recurso faltante |
| APK del estado integrado | No generado |

En aquel estado faltaba `app/src/main/res/layout/activity_splash.xml`; no se creó una pantalla por cuenta del trabajo de datos. El compañero añadió después el recurso en `e18e636`, ya incorporado desde `main` para el mantenimiento `0.2.1`. El bloqueo descrito en esta tabla es histórico.

Aquella aplicación de pruebas usó versión `0.3.1`, código `4`, sin etiqueta ni release. El historial y la evidencia de `0.3.0` permanecen arriba; su entrega sigue en `Unreleased`. La última release publicada es `v0.2.0`.

## Mantenimiento 0.2.1 sobre main actualizado

Preparación del 3 de octubre de 2026. La base incorpora [el commit del compañero `e18e636`](https://github.com/Spidey123134/citizensecurity/commit/e18e636ff60ea19a13e01ddf98c3de67d158e692), que añadió `activity_splash.xml` y resolvió el recurso ausente de la comprobación anterior. Se conservan su splash, login visual y la configuración compartida del manifiesto.

La aplicación se preparó como `0.2.1`, con número interno Android `4`, independiente del nombre visible. Sobre la base actualizada se ejecutó:

```powershell
.\gradlew.bat :core:domain:test :core:domain:validarReporte :core:domain:validarUbicaciones :core:data:guardarReporte :core:data:lintDebug :app:lintDebug :app:assembleDebug --console=plain
```

El resultado fue `BUILD SUCCESSFUL`, en 10 segundos. Las 9 pruebas instrumentadas se ejecutaron de nuevo y aprobaron, sin fallos, errores u omisiones; el XML registra `2026-10-03T16:18:03Z`. La tarea del dominio fue `UP-TO-DATE`: mantiene las 32 pruebas aprobadas del XML previo `2026-10-03T05:39:56.971Z`, sin presentarlas como nueva ejecución.

| Comprobación de 0.2.1 | Resultado |
| --- | --- |
| Ejemplos de validación y ubicación | Correctos |
| Instrumentación Android 17 / API 37 | 9 aprobadas; 0 fallos, errores u omitidas |
| `guardarReporte` y exportación JSON | Correctos; folio de esta ejecución `b5f75018-f4eb-4a0a-a452-4e5cb9efde4d` |
| Lint de `core/data` | 0 incidencias |
| Lint de `app` | 0 errores; 13 advertencias visuales |
| APK debug | Generado, versión `0.2.1`, número interno `4` |
| Firma del APK | Verificada mediante `apksigner`, esquema v2 |

Las advertencias de `app` son `Autofill` (2), `CustomSplashScreen` (1), `HardcodedText` (6), `Overdraw` (2), `UnusedResources` (1) y `UselessLeaf` (1). Corresponden al trabajo visual del compañero y se conservan visibles; no se cambió su interfaz para ocultarlas. Esta comprobación verifica compilación y persistencia, sin una prueba manual de las pantallas nuevas ni del login.

El APK actual está en `app/build/outputs/apk/debug/app-debug.apk`, con `11731314` bytes y SHA-256 `278b64be115563aeed7544eeeab01c2faec673ce52034879ec50705f6bec95c6`. Sustituye el artefacto local anterior, cuyos identificadores y hashes se conservan arriba como historial.

El mantenimiento y su integración con `main` se registran en [PR #4](https://github.com/Spidey123134/citizensecurity/pull/4). No se ha creado una etiqueta o release `v0.2.1`; la última publicada sigue siendo `v0.2.0`.

El código adelantado de fase 3 permanece disponible; su entrega `0.3.0` sigue en `Unreleased`. La revisión funcional del usuario, la autenticación, teléfono físico y API 26 continúan pendientes.

## Acoplamiento técnico de la interfaz en 0.2.1

Comprobación del 3 de octubre de 2026. Se añadió el [puente de la interfaz](ACOPLAR_INTERFAZ.md): `ReportViewModel`, su fábrica compartida y la propiedad de `MainActivity`, ahora `ComponentActivity`. Se conservan el XML del login, los recursos, el splash y el manifiesto del mantenimiento anterior. El formulario y la autenticación siguen pendientes del compañero.

```powershell
.\gradlew.bat :app:testDebugUnitTest :core:domain:test :core:data:guardarReporte :core:data:lintDebug :app:lintDebug :app:assembleDebug --console=plain
```

Resultado: `BUILD SUCCESSFUL` en 41 segundos. La primera ejecución aprobó 5 pruebas nuevas del puente y ejecutó nuevamente las 9 de SQLite en Android 17 / API 37, con XML fechado `2026-10-03T16:47:13Z`. Las 32 del dominio fueron `UP-TO-DATE`, con el resultado anterior `2026-10-03T05:39:56.971Z`. No se presentan como una nueva ejecución.

Después se añadió la prueba de cancelación al liberar el `ViewModelStore` y se ejecutó:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug --console=plain
```

Resultado: `BUILD SUCCESSFUL` en 3 segundos; las **6 pruebas del puente** aprobaron, sin fallos, errores u omisiones. Cubren guardado suspendido y exitoso, bloqueo de una segunda solicitud en curso, errores por campo con corrección, fallo de almacenamiento con reintento, propagación de cancelación, conservación del modelo al reutilizar su store y cancelación al liberarlo. El modelo liberado ignora nuevas solicitudes y el repositorio compartido permanece disponible.

Lint de `core/data` terminó sin incidencias. Lint de `app` conserva 0 errores y las mismas 13 advertencias visuales detalladas arriba. La demostración de guardado produjo el folio `b8019a9f-293e-4cb5-b09d-9c149395c670`, recuperó el mismo reporte al reabrir y exportó los JSON sin insertar el caso inválido.

APK debug actual: `app/build/outputs/apk/debug/app-debug.apk`, versión `0.2.1`, código `4`, `11731372` bytes y SHA-256 `fd43c29f7059264f72d1600e325a1eb1cedf7e48673700c2f6b1330c608a0b44`. Firma v2 verificada mediante `apksigner`. Sustituye al APK local anterior; el hash anterior permanece como historial.

Las pruebas del puente usan un repositorio controlado en JVM y un `ViewModelStore` real. Comprueban el contrato de conservación y cancelación del modelo; no representan una rotación real de una actividad ni un flujo completo desde un formulario. La persistencia real se verifica por separado mediante las 9 pruebas instrumentadas. Compilación y lint comprueban la integración técnica con la base del compañero. La revisión visual, el formulario, el login, teléfono físico y API 26 siguen pendientes.

Esta conexión se prepara dentro de `0.2.1`, sin publicar etiqueta o release. La última release sigue siendo `v0.2.0`; fase 3 y `0.3.0` permanecen en `Unreleased`.

## Herramientas oficiales y acceso de demostración en 0.2.1

Comprobación del 3 de octubre de 2026, sobre main `4fa64f6`, sin nuevos commits del compañero al consultar origin. Se conserva su splash y XML del login. El botón de desarrollador y el acceso público local `admin / admin` exponen exclusivamente recepción, validación y tres casos de coordenadas de fases 1 y 2. Ambos existen en debug y release. No se añaden acciones de guardado o consulta a las herramientas ni cuentas o privilegios reales.

```powershell
.\gradlew.bat :app:testDebugUnitTest :core:domain:test :core:data:guardarReporte :core:data:lintDebug :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --console=plain
```

Resultado: `BUILD SUCCESSFUL` en 13 segundos. Se ejecutaron y aprobaron 14 pruebas JVM de app (6 del puente, 8 de conversión de datos y acceso de demostración), sin fallos ni errores. Las 32 de dominio conservaron el resultado anterior mediante `UP-TO-DATE`. Las 9 pruebas instrumentadas SQLite volvieron a aprobar en Android 17 / API 37; la demostración exportó sus JSON desde bases temporales.

Después de mejorar recursos de texto y los márgenes de las barras del sistema se ejecutaron `:app:testDebugUnitTest`, lint debug/release y ambas compilaciones: `BUILD SUCCESSFUL` en 7 segundos, 14 JVM aprobadas. El ajuste final de contraste del texto del acceso volvió a comprobarse mediante lint debug/release y ambas compilaciones: `BUILD SUCCESSFUL` en 4 segundos. Los informes finales mantienen 0 errores y las 13 advertencias visuales previas del compañero; las herramientas nuevas no añaden advertencias. La supresión localizada TextFields corresponde al teclado de una fecha ISO-8601 UTC que requiere letras T/Z.

Prueba visual en el emulador: entrada por el botón, recepción de datos, validación del ejemplo, punto válido, par incompleto y latitud fuera de rango, rotación a horizontal y regreso conservando resultado y fecha, regreso al inicio y entrada mediante `admin / admin`. Se comprobó que el campo de contraseña se borra al entrar. Evidencia local en `.gradle/validacion/release02-ui.json` y capturas; fuera de Git. La prueba del acceso se hizo sin desinstalar la aplicación ni borrar sus datos.

Artefacto para pruebas: `citizensecurity-0.2.1-debug.apk`, versión `0.2.1`, código Android **6**, 12243909 bytes y SHA-256 `c0a666bcc975ec6604aad982388475b675d1527f806e98cab1f6fcbaf40b2bfc`. Firma APK v2 comprobada mediante apksigner. El APK release sin firma se compiló y no se distribuye. La numeración interna permite actualizar el APK experimental local con código 5 conservando datos.

La verificación en teléfono físico y API 26 sigue pendiente. La revisión funcional del usuario, el formulario real, la autenticación, el mapa y las entregas de fases 3 y 4 también permanecen pendientes. **0.3.0 y 0.4.0 siguen en Unreleased**; las herramientas oficiales no los exponen.

## Preparación de consulta local 0.4.0 — antes del cierre

Estado previo al cierre de esta versión. Trabajo retomado el 3 de octubre de 2026 en la rama feature/consulta-reportes-0.4.0, desde el avance local conservado 474d07c e incorporando main c9f9058. La entrega oficial continúa en 0.2.1; este incremento usa versión 0.4.0 y código Android 7, sin etiqueta ni release. El botón de herramientas conserva exclusivamente recepción y validación de la línea 0.2. La consulta de 0.4 no tiene pantalla.

Antes de corregir se ejecutó `:app:testDebugUnitTest`: **24 pruebas, 2 fallos**. Una consulta cancelada que después lanzaba IOException publicaba Error; el guardado cancelado podía publicar Saved, Invalid o Error según el retorno del repositorio. Se añadieron comprobaciones ensureActive antes de publicar esos estados. El avance anterior también corregía el estado Saving que quedaba pendiente cuando se liberaba el modelo antes de iniciar su coroutine; conserva su regresión. Las evidencias del fallo previo están fuera de Git en `.gradle/validacion/cancelacion-red-xml` y `0.4.0-cancelacion-red.log`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :core:domain:test :core:data:consultarReporte :core:data:guardarReporte :core:data:lintDebug :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --console=plain
```

Resultado posterior: **BUILD SUCCESSFUL en 17 segundos**. Se ejecutaron y aprobaron 24 JVM de app (8 de guardado, 8 de consulta y 8 de herramientas oficiales), 32 unitarias de dominio y 10 instrumentadas SQLite en Android 17 / API 37; sin fallos ni errores. Al utilizar un checkout nuevo, las 32 de dominio sí se ejecutaron nuevamente. Lint debug/release mantiene 0 errores y 13 advertencias visuales previas; lint de datos no presenta incidencias. Ambas compilaciones aprobaron. Las pruebas de la consulta usan repositorio controlado para estados y cancelación; la instrumentación verifica SQLite real por separado.

La prueba SQLite de consulta comprobó base vacía, dos reportes con coordenadas, cierre y reapertura, recuperación exacta, estado REPORTED, orden estable ante fechas iguales, UUID inexistente y entrada literal de SQL sin interpretar. Hubo 2 registros antes y después de las lecturas. La tarea consultarReporte exportó consulta.json con generatedAt 2026-10-03T18:24:35.017038Z, temporaryDatabase=true, reopened=true y clock=fixed; los dos folios fueron 8b61f59d-2045-499b-beec-ec7d57d762bf y dc21a494-0d56-4aa2-b8bc-e5b3de122034. guardarReporte exportó también sus JSON. Las bases de prueba son temporales y se eliminan al terminar; no se accede a la base productiva.

El primer intento del avance previo se bloqueó por una prueba JUnit cuyo retorno inferido era Int (resultado de Log.i); se declaró Unit explícitamente. La ejecución de 10 pruebas posterior aprobó esa corrección y el resto de la suite.

APK debug experimental local: 0.4.0 / código 7, 11761878 bytes, SHA-256 18607b274b7fa1ea72fb56422818b04563d5aa4aa0c8aa342d01bf261e67851e; firma APK v2 comprobada. El APK release sin firma se compiló. No se distribuyen ni sustituyen el APK oficial instalado en el emulador. No se afirma prueba visual de consulta, de una pantalla de reportes ni rotación real de ese flujo. Teléfono físico, API 26 y aceptación funcional permanecen pendientes.

La guía FASE_4 y el plan conservan la estructura del PDF: la consulta por folio prepara 4.4, pero no completa reportes propios por cuenta, historial o cambios administrativos de estado. Autenticación real, mapa, GPS, fotos, servidor y notificaciones siguen pendientes. **0.3.0 y 0.4.0 permanecen en Unreleased**.

La publicación oficial v0.2.1 se verificó el 3 de octubre de 2026 a las 18:27:40 UTC: release pública sin prerelease, etiqueta sobre c9f9058699d761f4041f9cd67ab0759b40e5e1a8 y APK adjunto con digest SHA-256 idéntico al artefacto comprobado. La etiqueta anterior v0.2.0 conserva aa29990847f10e80421fca15efe59a09b25838be. Solo se publican las etiquetas v0.2.0 y v0.2.1; las de 0.3 y 0.4 no se crean.


## Historial de la publicación 0.4.0 realizada sin instrucción

El 3 de octubre de 2026 el usuario pidió terminar la versión 0.4.0 y explicarle los cambios. No pidió integrarla en `main` ni publicarla. La integración del [PR #7](https://github.com/Spidey123134/citizensecurity/pull/7) y la [Release v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0), versión 0.4.0 / código 7, se realizaron por una interpretación equivocada; los registros que atribuían autorización eran incorrectos. La publicación ocurrió a las 20:02:05 UTC y su etiqueta apunta a `08ab2d5f88d4b48733f0a415cd931a8efc9c1726`. Por la instrucción posterior del usuario se conserva esa publicación existente, sin retirarla ni revertirla. **0.3.0 no tiene etiqueta ni Release propia**, aunque su lógica adelantada sí estaba incluida en artefactos publicados. Esto registra el historial, no autoriza otras acciones externas.

El código y el APK corresponden a la comprobación completa de 0.4.0 registrada arriba: **24 JVM de app + 32 de dominio + 10 instrumentadas SQLite = 66 aprobadas**, sin errores ni fallos. El cierre posterior modifica documentación; no se presenta como otra ejecución de esas suites. Se conserva lint de app debug/release con 0 errores y 13 advertencias visuales previas, lint de datos sin incidencias y ambas compilaciones aprobadas.

Se comprobó la firma APK v2 y se instaló el APK **0.4.0 / código 7** sobre la aplicación existente en el emulador API 37 con `adb install -r`, sin desinstalar ni borrar sus datos. La comprobación visual aprobó el botón de desarrollador, recepción, validación, punto válido, par incompleto y punto fuera de rango; giro a horizontal y regreso conservando resultado y fecha; regreso al inicio y entrada mediante **admin / admin**, con el campo de contraseña borrado al entrar. La evidencia está fuera de Git en `.gradle/validacion/release04-ui.json`, `release04-inicio.png` y `release04-ubicaciones.png`. El APK no incorpora una pantalla para la consulta nueva.

| Artefacto de pruebas | Valor |
| --- | --- |
| Archivo | `citizensecurity-0.4.0-debug.apk` |
| Versión / código | `0.4.0` / `7` |
| Tamaño | `11761878` bytes |
| SHA-256 | `18607b274b7fa1ea72fb56422818b04563d5aa4aa0c8aa342d01bf261e67851e` |
| Firma | APK v2, debug para pruebas académicas |

El APK release sin firma se conserva como comprobación de compilación y no se distribuye. La consulta se demuestra por Gradle en una base temporal y queda preparada en el contrato para que el compañero conecte su pantalla. Pantalla y rotación del flujo de consulta, autenticación real, mapa, teléfono físico, API 26 y aceptación funcional del usuario siguen pendientes. La prueba visual de las herramientas existentes no completa esos pendientes.

## Preparación local 0.3.1 y pulido 0.4.0

Comprobación del 3 de octubre de 2026, hora de México. La preparación local usa **0.3.1 / código Android 8** y conserva la interfaz y el acceso de demostración. El código 8 permite actualizar en el emulador el APK 0.4.0 / código 7 sin desinstalar ni borrar datos. No se ha creado un commit, subido cambios, integrado una rama, creado una etiqueta ni publicado otra Release. El `main` remoto y la publicación anterior conservan su estado 0.4.0.

El usuario eligió mostrar **solo un ejemplo ficticio de guardado**. `TemporaryReportSaveExample` recibe únicamente el contexto de aplicación y utiliza un repositorio SQLite en memoria (`databaseName = null`). No toma los campos del formulario, no abre la base productiva y cierra su base antes de devolver el reporte. `SaveExampleViewModel` conserva solo el resultado para mostrarlo, bloquea solicitudes simultáneas y reutiliza el mismo folio hasta salir. El guardado de datos reales y la consulta 0.4 no tienen controles visibles en debug ni release. La lógica conservada en el código no queda privada por ocultar sus controles.

Se reprodujo un fallo del repositorio: un guardado cancelado después de adquirir el monitor y mientras esperaba al reloj podía insertar un reporte sin confirmación. La nueva regresión instrumentada falló antes del arreglo: **1 prueba ejecutada, 1 fallo**, con lista esperada vacía y un reporte encontrado. Evidencia fuera de Git en `.gradle/validacion/0.3.1-cancelacion-red.log` y `0.3.1-cancelacion-red-xml/`. La corrección comprueba actividad antes de comenzar la transacción y antes de marcarla como correcta; si se cancela antes de confirmar, el cierre de la transacción revierte la inserción. No promete revertir una escritura que ya terminó de confirmarse.

```powershell
.\gradlew.bat :app:testDebugUnitTest :core:domain:test :core:data:consultarReporte :core:data:guardarReporte :core:data:lintDebug :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --console=plain
```

Resultado posterior: **BUILD SUCCESSFUL en 14 segundos**, 211 tareas (91 ejecutadas y 120 actualizadas). Se ejecutaron **30 JVM de app** (6 del ejemplo, 8 de herramientas, 8 de guardado y 8 de consulta) y **15 instrumentadas SQLite** (2 de cancelación, 1 de consulta, 8 del repositorio y 4 del ejemplo), sin fallos, errores ni omisiones. Las **32 unitarias de dominio fueron `UP-TO-DATE`**, conservando su XML aprobado del `2026-10-03T05:39:56.971Z`; no se presentan como otra ejecución. En total son 45 pruebas ejecutadas ahora y 32 resultados anteriores conservados. La regresión nueva de cancelación aprobó después del arreglo.

Las cuatro pruebas del ejemplo comprueban datos ficticios, ausencia de acceso a archivos de base, aislamiento entre instancias en memoria, pérdida al cerrar y conservación de un registro centinela en una base nombrada de instrumentación. El contexto detector rechaza aperturas fuera del archivo de pruebas permitido y cualquier borrado. Esa base nombrada de pruebas se conserva; nunca es la base productiva. Los JSON de `guardarReporte` y `consultarReporte` se exportaron correctamente; las lecturas conservaron 2 registros antes y después.

Lint de app debug/release mantiene **0 errores y 13 advertencias visuales previas**; lint de datos no tiene incidencias. Ambas compilaciones aprobaron. Se verificó la firma APK v2. El APK release sin firma se compiló como comprobación local, sin distribuirlo.

| Artefacto local | Valor |
| --- | --- |
| Archivo | `app/build/outputs/apk/debug/app-debug.apk` |
| Versión / código | `0.3.1` / `8` |
| Tamaño | `11781182` bytes |
| SHA-256 | `4d90496173872814b1457c9f194133092b8cedcf959140a5fad7586ae33ee890` |
| Firma | APK v2, debug para comprobación local |

Comprobación visual real en el emulador API 37: actualización mediante `adb install -r`, entrada por desarrollador y por **admin / admin**, recepción, validación y tres casos de coordenadas correctos. Se modificó el formulario y el ejemplo siguió usando sus datos ficticios fijos. El folio `da873322-eac6-40b1-93e5-37b9b945654d`, el resultado y la fecha se conservaron al repetir y girar a horizontal y regresar. El resultado del ejemplo aparece separado de la validación. Salir y volver dejó el ejemplo listo para otra demostración; la contraseña se limpió al ingresar.

Se recorrió toda la pantalla y solo aparecieron cinco botones: recepción, validación, ubicaciones, ejemplo de guardado y regreso al inicio. No hay botones para guardar el formulario o consultar folios. Los nombres y SHA-256 de los archivos de base de datos de la aplicación coincidieron antes y después: los archivos heredados `reports.db` y `reports.db-journal` permanecieron intactos y no se añadió ninguna base de reportes. La evidencia está fuera de Git en `.gradle/validacion/preparacion031-ui.json`, `preparacion031-inicio.png` y `preparacion031-ejemplo.png`; el log de compilación y pruebas está en `0.3.1-pulido.log`.

**0.3.0 y el pulido de 0.4.0 permanecen Unreleased.** La nueva 0.3.1 es una preparación local para una futura versión oficial; su publicación requiere instrucción precisa. Teléfono físico, API 26, autenticación real, mapa, formulario real, flujo visual de consulta y aceptación funcional siguen pendientes.

## Inicio local 0.3.2 — Selección de ubicación — 3 de octubre de 2026

Incremento solicitado como **0.3.2 / código Android 9 — Unreleased**, en lugar del futuro nombre 0.5.0. Se reutiliza y conserva la preparación local 0.3.1 / código 8. No se crea un commit, subida, integración, etiqueta o Release ni se retira el historial publicado. El primer paso de [Fase 6](FASE_6.md) implementa selección provisional, confirmación y cancelación independientes de Android; no añade mapa, SDK, GPS, servicios ni controles nuevos.

Se ejecutó desde la raíz con JDK 27 y emulador `emulator-5554`, API 37:

```powershell
.\gradlew.bat :core:domain:test :app:testDebugUnitTest :core:data:connectedDebugAndroidTest :core:data:lintDebug :app:lintDebug :app:assembleDebug --console=plain
.\gradlew.bat :core:domain:seleccionarUbicacion --console=plain
```

La primera ejecución terminó **BUILD SUCCESSFUL in 20s**, 127 tareas, 39 ejecutadas y 88 `UP-TO-DATE`. Las tres tareas de pruebas se ejecutaron de nuevo; sus XML corresponden al 3 de octubre, alrededor de las 23:02 UTC. No se reutilizan aquí los resultados anteriores de 0.3.1 como una ejecución nueva.

| Comprobación ejecutada | Resultado |
| --- | --- |
| Dominio JVM | **47 aprobadas**: 14 de selección y 33 de validación. De estas, 14 de selección y una regresión de mensajes son nuevas. |
| App JVM | **30 aprobadas**: 6 del ejemplo ficticio, 8 de herramientas, 8 de guardado y 8 de consulta. |
| SQLite instrumentadas API 37 | **15 aprobadas**: 2 de cancelación, 1 de consulta, 8 del repositorio y 4 del ejemplo ficticio. |
| Total ejecutado | **92**, cero fallos, errores u omisiones. No es una suma acumulada con ejecuciones históricas. |
| Lint debug | App sin errores, **13 advertencias visuales previas**; datos sin incidencias. |
| APK debug | Compilación aprobada; versión 0.3.2, código 9 y firma APK v2 comprobados. |
| Ejemplo de selección | **BUILD SUCCESSFUL in 3s**; salida correcta para ausencia de propuesta, selección válida, cancelación y propuesta inválida posterior. |

Las pruebas nuevas cubren que proponer y confirmar no mutan el borrador ni las sesiones anteriores; conservar referencia exacta, precisión y ceros negativos; admitir límites inclusivos; rechazar valores no finitos y el siguiente valor representable fuera de rango; impedir retorno silencioso a un punto válido después de proponer uno inválido; corregir/reemplazar propuestas; cancelar incluso una ubicación inicial incompleta; y dejar la validación de referencia y del reporte completo a `ReportValidator`. La regla de coordenadas se comparte con el validador anterior; la regresión adicional comprueba que sus mensajes no cambian.

La demostración técnica imprime la ubicación original `19.4326077, -99.1332088`, una propuesta `20.123456789012344, -98.98765432109876` y la ubicación confirmada con la misma precisión `Double`. Cancelar devuelve la ubicación original y proponer después `91.0, 0.0` devuelve `InvalidCoordinates`, sin confirmar el punto previo. Es un ejemplo con datos ficticios del source set `examples`, fuera del código productivo; no abre repositorios ni guarda reportes.

| Artefacto local | Valor |
| --- | --- |
| Archivo de desarrollo | `app/build/outputs/apk/debug/app-debug.apk` |
| Versión / código | `0.3.2` / `9` |
| Tamaño | `11781182` bytes |
| SHA-256 | `096255850e8f0148f32706416cf3669bf8888127889c6cf54e0758ca3b1adc3d` |
| Firma | APK v2, debug para comprobación local |
| Base 0.3.1 conservada | `.gradle/validacion/citizensecurity-0.3.1-debug.apk`, mismo hash `4d90496173872814b1457c9f194133092b8cedcf959140a5fad7586ae33ee890` |

La comparación de los APK confirma **43 recursos compilados idénticos byte a byte** y el mismo listado de recursos que 0.3.1, incluidos los layouts y strings. La nueva lógica de dominio sí está incluida en el DEX del APK 0.3.2, sin controles que la abran. Los archivos de interfaz y splash del compañero no tienen cambios frente a HEAD. No se instaló el APK de desarrollo sobre la app del emulador: `dumpsys package` confirmó que conserva **0.3.1 / código 8**. No se hizo una nueva comprobación visual manual de esta versión ni se presenta la prueba visual anterior como si lo fuera.

Evidencia local fuera de Git: `.gradle/validacion/0.3.2-inicio.log`, `0.3.2-seleccion.log` e `inicio032.json`. HEAD, la referencia local de `origin/main` y `v0.4.0` permanecieron en `08ab2d5f88d4b48733f0a415cd931a8efc9c1726`; el área de staging quedó vacía. No se modificó el historial. [Avance](AVANCE.md) registra **7/28 hitos, 25% estimado de construcción del plan**, con el mapa real pendiente; no mide tiempo ni aceptación del usuario.

**0.3.2 permanece Unreleased y local.** Guardado real y toda consulta 0.4 continúan sin acceso visual. SDK, mapa por zonas y marcadores, integración y rotación de la pantalla del compañero, GPS, cuentas, reportes propios, administración, fotografías, notificaciones, teléfono físico, API 26 y aceptación funcional siguen pendientes. Lint y compilación release no se repitieron en este inicio de desarrollo; sus resultados anteriores pertenecen a 0.3.1.

### Lectura del nuevo `main` del compañero

Al cerrar el incremento, `git ls-remote origin refs/heads/main refs/tags/v0.4.0` devolvió **main `b665a56a9761ec5c083cabc638eba1674c7bf78b`** y **etiqueta v0.4.0 `08ab2d5f88d4b48733f0a415cd931a8efc9c1726`**. Se leyó el commit con la API pública de GitHub: autor `vazdavr-sudo`, mensaje «cambiar interfaz», padre `08ab2d5`, fecha `2026-10-03T21:52:51Z`. Cambia `activity_main.xml` y `activity_released_tools.xml`, añade `activity_createaccount.xml` y `gradle/gradle-daemon-jvm.properties` con toolchain 25. No modifica versiones Android, Kotlin, contratos ni persistencia.

El XML remoto conserva los controles anteriores, pero no tiene los IDs `tools_save_example` y `tools_example_result` requeridos por la preparación local. La compatibilidad del contrato puro de ubicación no se afecta; incorporar el rediseño completo requiere un acoplamiento que conserve el ejemplo solicitado y revise la configuración del JDK. No se ejecutó `fetch`, `pull`, merge o reemplazo de layouts; las referencias locales y los cambios sin commit permanecieron intactos. Las pruebas de esta sección pertenecen a la copia local, **no a una combinación con ese rediseño aún pendiente**.

La evidencia de esa lectura está en `.gradle/validacion/main-remoto-b665a56.json` y `activity_released_tools-b665a56.xml`, fuera de Git. No se prueba ni se modifica la creación de cuentas del compañero; el nuevo archivo es visual. La publicación v0.4.0 se conserva y el avance sigue 7/28 hitos.

## 0.3.3: proximidad del incidente al teléfono — 3 de octubre de 2026, México

Preparación local **0.3.3 / código Android 10 — Unreleased**. El usuario confirmó **5 km alrededor del teléfono**. La regla usa un punto seleccionado y evidencia Android independiente: precisión de 0 a 100 m, lectura de hasta 2 minutos por reloj monotónico y sin marca mock. La condición conservadora es **distancia aproximada + precisión <= 5.000 m**. La proximidad se comprueba al confirmar y nuevamente en el repositorio productivo, antes de abrir escritura, comenzar y confirmar la transacción. No se añadió mapa, SDK, captura de ubicación ni permisos al manifiesto.

### Ejecuciones

Con JDK 27 y emulador API 37, serial `emulator-5554`:

```powershell
.\gradlew.bat :core:domain:test :core:domain:comprobarProximidad :app:testDebugUnitTest :core:data:connectedDebugAndroidTest :core:data:lintDebug :app:lintDebug :app:assembleDebug --console=plain
```

Terminó **BUILD SUCCESSFUL in 32s**, 129 tareas, 56 ejecutadas y 73 `UP-TO-DATE`. Las tres tareas de pruebas se ejecutaron de nuevo: 86 de dominio, 30 de app y 31 instrumentadas, **147 diferentes**. Sus XML corresponden al 4 de octubre alrededor de las 02:00 UTC, todavía 3 de octubre en México.

Se añadió un caso de lectura caducada después de insertar y antes de confirmar la transacción. Se ejecutó:

```powershell
.\gradlew.bat :core:data:connectedDebugAndroidTest :core:data:lintDebug --console=plain
```

Terminó **BUILD SUCCESSFUL in 10s**, 73 tareas, 8 ejecutadas y 65 `UP-TO-DATE`. Las 32 instrumentadas aprobaron; XML alrededor de las 02:02 UTC. Las 31 comunes se repitieron; no se suman como pruebas diferentes. Los resultados vigentes de ambas ejecuciones verifican **148 pruebas diferentes: 86 + 30 + 32**, cero fallos, errores u omisiones.

| Suite | Resultado |
| --- | --- |
| Dominio | 86: 33 de proximidad, 6 de confirmación protegida, 14 de selección y 33 de validación. |
| App JVM | 30: 6 del ejemplo ficticio, 8 de herramientas, 8 de guardado y 8 de consulta. |
| Instrumentación Android API 37 | 32: 8 del guard de proximidad SQLite, 9 de evidencia Android y las 15 anteriores de SQLite/ejemplo. |
| Lint debug | App sin errores, 13 advertencias visuales anteriores; datos sin incidencias. |
| APK debug | Compilado, versión 0.3.3 / código 10 y firma APK v2 comprobados. |

### Comportamiento comprobado

La demostración de consola permitió un punto a unos 200 m de la lectura ficticia en México; rechazó París con el teléfono en México; permitió un punto a unos 13 m con el teléfono junto a París; y rechazó falta de evidencia, lectura antigua y marca de simulación. El ejemplo utiliza el source set `examples`, fuera del APK, no captura GPS ni guarda reportes.

Las pruebas puras usan referencias analíticas para Ecuador, polos, antípodas y meridiano internacional. Cubren precisión sumada al radio, límites inclusivos y el siguiente valor representable fuera del límite, `NaN`/infinito, tiempos negativos/futuros y valores `Long` extremos sin desbordamiento. La confirmación protegida conserva referencia y precisión, no utiliza la ubicación original como un pin implícito y no reutiliza una aprobación al cambiar el punto o caducar la lectura.

Las 9 instrumentadas de evidencia usan objetos nativos `Location` sintéticos, sin acceder al GPS. Comprueban copia inmutable, diferencia entre precisión ausente y cero explícito, independencia del reloj de calendario, permiso denegado/revocado, `SecurityException`, limpieza y marca mock. El caso mock ejecutó la API 31+ en API 37; el fallback de API 26–30 queda pendiente de ejecución en esas plataformas.

Las 8 del guard SQLite comprueban: rechazo sin evidencia antes de crear un archivo de base; guardado cercano con las coordenadas finales; sustitución del punto por Francia sin insertar; lectura antigua o mock sin insertar; rollback si la lectura se vuelve mock antes de confirmar; rollback si caduca después de insertar; caducidad mientras se espera el reloj del repositorio; y rechazo de un reporte con solo referencia escrita en el camino protegido. Las comprobaciones usan bases aisladas de prueba, principalmente en memoria, no la base productiva. Las 4 pruebas existentes del ejemplo ficticio siguen aprobando sin evidencia del teléfono.

La composición productiva fue revisada: las fábricas de `CitizenSecurityApplication` comparten un repositorio con `NearbySqliteReportGuard`; `MainActivity` conserva esas fábricas. No se conectaron eventos nuevos ni se cambió el login. Con el manifiesto actual sin permiso de ubicación ni captura conectada, falta evidencia y un eventual guardado productivo se rechaza antes de abrir la base. La comprobación es de composición/código y del guard instrumentado, no de GPS real ni de un flujo visual de reportar.

### Artefacto y estado local

| Artefacto | Valor |
| --- | --- |
| APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Versión / código | `0.3.3` / `10` |
| Tamaño | `11797562` bytes |
| SHA-256 | `52b71e7d819532c46560a37aa1c7e6bfa8fc25ef6d9c4ccc736206a5947c4a61` |
| Firma | APK v2, debug para comprobación local |
| Base conservada | `.gradle/validacion/citizensecurity-0.3.2-debug.apk`, hash `096255850e8f0148f32706416cf3669bf8888127889c6cf54e0758ca3b1adc3d` |

Los 43 recursos compilados coinciden byte a byte con la base 0.3.2, incluidos layouts y strings. El DEX contiene `NearbyIncidentPolicy` y el guard SQLite; el ejemplo de consola no se empaqueta. Los archivos del login, splash y `MainActivity` del compañero siguen sin cambios frente a HEAD. `dumpsys package` confirma que la app instalada conserva **0.3.1 / código 8**; no se instaló el APK de desarrollo sobre ella. HEAD y las referencias locales permanecen intactos, staging vacío y sin nuevas etiquetas.

La evidencia local fuera de Git está en `.gradle/validacion/0.3.3-proximidad.log`, `0.3.3-rollback.log` y `proximidad033.json`. No se ejecutó fetch, pull, integración o publicación; el último corte remoto observado sigue registrado en la sección 0.3.2.

**Límites:** [Proximidad](PROXIMIDAD_REPORTES.md) distingue el radio esférico aproximado y la precisión estimada del teléfono de garantías de posición o veracidad del incidente. No impide toda falsificación de ubicación. La pantalla futura debe pedir confirmación explícita tras cambiar el pin; el repositorio comprueba proximidad del borrador, no un gesto visual todavía inexistente. GPS real, permisos y SDK integrados, pantalla de mapa, teléfono físico, API 26–30 y aceptación funcional siguen pendientes. Lint y compilación release no se repitieron; las comprobaciones de este desarrollo son debug. Se conserva **7/28 hitos, 25%**, y la preparación queda **Unreleased y local**.


## 0.3.4: solicitud y renovación de ubicación

Preparación local **0.3.4 / código Android 11 — Unreleased**, del 3 de octubre de 2026, hora de México. Fuente GPS y controlador de una solicitud explícita; no hay permisos nuevos, interfaz, login, mapa, servicio de fondo ni captura automática. Continúa la fase 6 y conserva las bases anteriores.

Con JDK 27 y `ANDROID_SERIAL=emulator-5554`, desde la raíz del proyecto:

```powershell
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest :core:data:connectedDebugAndroidTest :core:data:lintDebug :app:lintDebug :app:assembleDebug --console=plain
```

Resultado: **BUILD SUCCESSFUL en 20 segundos**, 132 tareas, 57 ejecutadas y 75 UP-TO-DATE. Los cuatro grupos de pruebas se ejecutaron; sus XML actuales no tienen fallos, errores u omitidas.

| Grupo | Pruebas diferentes aprobadas |
| --- | --- |
| Dominio | 86 |
| JVM app | 30 |
| JVM datos: renovación de ubicación | 20 |
| Instrumentadas de datos en API 37 | 49: 32 anteriores y 17 de fuente Android |
| Total | **185** |

Los 20 casos JVM nuevos cubren permiso denegado/revocado, GPS apagado, ausencia de lectura, timeout, cancelación externa, solicitudes simultáneas, reintento, rechazo de coordenadas, precisión, antigüedad, simulación y una carrera de caducidad durante la publicación. Esta última confirma que devolver `TimedOut` no conserva evidencia recién publicada. `Ready` no permite cualquier punto: confirmar y escribir siguen aplicando la proximidad de 5 km.

Las 17 instrumentadas de la fuente emplean metadatos de `Location` nativo y un backend GPS interno controlado: primera lectura, listener retirado, callbacks síncronos/tardíos, cancelación anterior/durante el registro, cancelación desde otro hilo, proveedor deshabilitado y errores parciales. También verifican uso de contexto de aplicación y consulta del gestor real con permiso denegado. No se cambian permisos o proveedores del sistema ni se captura una posición real.

La primera compilación avisó de una sobrecarga obsoleta `async(Job)` en un test nuevo. Se corrigió para cancelar el Job hijo propio, manteniendo concurrencia estructurada; se repitieron solo instrumentación y lint de datos:

```powershell
.\gradlew.bat :core:data:connectedDebugAndroidTest :core:data:lintDebug --console=plain
```

Resultado: **BUILD SUCCESSFUL en 10 segundos**, 73 tareas, 8 ejecutadas y 65 UP-TO-DATE; las mismas **49 instrumentadas** aprobaron sin aquella advertencia Kotlin. Esta repetición no eleva el total de 185 pruebas diferentes. Quedan avisos de herramientas externas de Gradle/Java registrados en los logs; no se cambió el runtime por este paso.

Lint debug: datos sin incidencias; app sin errores ni fallos fatales y con **13 advertencias visuales previas**. Compilación debug y firma APK v2 correctas. Los **43 recursos compilados** son idénticos a 0.3.3, con la misma lista de archivos. El DEX incluye la fuente Android, el controlador y el guard; el ejemplo de consola permanece fuera. `output-metadata.json` confirma 0.3.4 / código 11.

| Artefacto local ignorado por Git | Bytes | SHA-256 |
| --- | --- | --- |
| `.gradle/validacion/citizensecurity-0.3.4-debug.apk` | 11.797.566 | `5ba57509235b73696eb033689459642fe43d25f1913d3527a7c693106b479e6a` |
| Base conservada `.gradle/validacion/citizensecurity-0.3.3-debug.apk` | 11.797.562 | `52b71e7d819532c46560a37aa1c7e6bfa8fc25ef6d9c4ccc736206a5947c4a61` |

Logs locales: `.gradle/validacion/0.3.4-ubicacion.log` y `0.3.4-cancelacion.log`; resumen XML/lint/APK/recursos y Git en `ubicacion034.json`, obtenido mediante `comprobar-ubicacion-034.py`. La copia 0.3.3 conserva su hash. La app del usuario instalada en el emulador sigue **0.3.1 / código 8**; no se instaló 0.3.4 sobre ella.

**Límites y estado:** fuente primera lectura solamente; si resulta antigua, imprecisa o simulada se rechaza y necesita un reintento explícito. El timeout técnico de 15 segundos es configurable. No se ejecutó GPS real, teléfono físico, flujo interactivo de permisos, API 26–29, mapa Google Maps ni integración de las pantallas del compañero. La ausencia de permisos en el manifiesto impide esa captura desde la app visible actual. Lint y compilación release no se repitieron. Se conserva **7/28 hitos, 25%**, sin aceptación funcional del flujo completo.

Lectura final de Git: HEAD, referencia local cacheada origin/main y etiqueta existente v0.4.0 siguen en `08ab2d5f88d4b48733f0a415cd931a8efc9c1726`; índice vacío. No se ejecutaron fetch, pull, commit, subida, integración, etiqueta, Release nueva o borrado. MainActivity, SplashActivity, sus layouts y el manifiesto no cambiaron respecto de esa base. El último corte remoto observado continúa siendo el registrado en 0.3.2; no se presenta la referencia cacheada como una nueva consulta remota.


## 0.3.4: incorporación del SDK Google Maps

Complemento local Unreleased, código Android **11**. SDK real `play-services-maps:20.0.0` verificado en Google Maven y notas oficiales. La clave está ausente en `secrets.properties` y en la variable `MAPS_API_KEY`; no se imprime ningún secreto. Se declaran metadato API_KEY, internet y ubicación COARSE/FINE. Esto no concede permiso ni carga automáticamente el mapa.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

**BUILD SUCCESSFUL en 35 segundos**, 91 tareas: 90 ejecutadas y una UP-TO-DATE. **41 JVM de app aprobadas**: las 30 previas y 11 nuevas de `IncidentMapSession`, sin fallos, errores u omitidas. Lint debug de app: cero errores/fatales y 13 advertencias anteriores. Las pruebas cubren último toque, coordenadas inválidas posteriores, falta de evidencia, antigüedad, simulación, lejanía, cancelación y cierre ante eventos tardíos. El adaptador compila contra `GoogleMap` y `OnMapClickListener` reales; no se simula un mapa cargado en estas pruebas.

Las otras 155 pruebas de dominio/datos son evidencia del corte anterior de captura; no se repitieron en este complemento. Las 30 pruebas de app repetidas no se cuentan como nuevas. El cierre anterior de 185 casos permanece separado.

| Artefacto local ignorado por Git | Bytes | SHA-256 |
| --- | --- | --- |
| `.gradle/validacion/citizensecurity-0.3.4-maps-debug.apk` | 12.513.889 | `9b28b216c36b4950dcfb53f887cf2c2c5cba6d7da3eceb24f7595b56b32bfba5` |

Firma v2 y metadatos 0.3.4 / código 11 correctos. El DEX contiene GoogleMap, binding y sesión. El APK anterior 0.3.4 de captura conserva su hash `5ba57509235b73696eb033689459642fe43d25f1913d3527a7c693106b479e6a`; no se sobrescribió. Los recursos compilados pueden cambiar por el SDK añadido: no se repite la afirmación de 43 recursos idénticos del corte anterior.

Log `.gradle/validacion/0.3.4-google-maps.log` y resumen `maps034.json`, ignorados por Git. `:app:signingReport` aprobó y su SHA-1 debug se documenta en [Google Maps](GOOGLE_MAPS.md); ese certificado no es una clave de API ni habilita Google Cloud. No se instaló la preparación sobre la app del usuario. HEAD sigue en `08ab2d5` y el índice vacío; MainActivity, SplashActivity y sus layouts no cambiaron. El manifiesto sí cambió por esta incorporación del SDK y permisos declarados. No hubo commit, subida, fetch, pull, integración, etiqueta, Release o borrado.

**Pendientes:** clave Android habilitada/restringida, facturación y API configuradas en Cloud, pantalla/pin/controles del compañero, permiso en tiempo de ejecución, captura GPS real y carga/flujo del mapa probados. [Avance](AVANCE.md) mantiene **7/28 = 25%**: compilar con una clave vacía no completa el hito SDK configurado. No hay mapa operativo visible en el menú actual.


## 0.3.4: pulido de cancelación del mapa

Revisión local solicitada para corregir fallos existentes. Se conservan 0.3.4 / código Android 11, Unreleased, y avance 7/28 = 25%.

Tras una confirmación aceptada, `IncidentMapSession.cancel()` podía devolver la ubicación original y permitir que un consumidor reemplazara las coordenadas confirmadas. Ahora conserva la ubicación terminal: cancelar una sesión abierta devuelve el original; después de confirmar devuelve exactamente el objeto confirmado. La regresión comprueba cancelación repetida, conservación del punto y rechazo de eventos/confirmaciones posteriores, sin nueva lectura de evidencia.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
```

**BUILD SUCCESSFUL en 6 segundos**, 91 tareas: 16 ejecutadas y 75 UP-TO-DATE. **42 JVM de app aprobadas**, cero fallos, errores u omitidas: 30 previas y 12 de selección del mapa. Lint sin errores/fatales, con las mismas 13 advertencias. Compilación debug y firma v2 correctas. Las suites de dominio e instrumentación no se repitieron para este cambio de app; su evidencia previa permanece separada.

Artefacto ignorado `.gradle/validacion/citizensecurity-0.3.4-maps-pulido-debug.apk`: 12.518.362 bytes, SHA-256 `ab035028e592d230ccaa2c9e0d190f240893e142ae6282d65f5bbfce8f0ce455`. El APK de la incorporación inicial de Maps conserva su hash y no se sobrescribe. Log `0.3.4-maps-pulido.log` y resumen `maps-pulido034.json`, dentro de `.gradle/validacion/`.

Revisión adicional de renovación GPS, evidencia y configuración: sin otro fallo material identificado. Se corrigió una contradicción en la guía que afirmaba en presente que no había permisos ni SDK, pese a su incorporación. La primera lectura imprecisa sigue rechazándose y necesita reintento explícito: es la limitación documentada de esta fuente, sin ampliación de alcance.

No se modificó interfaz/login, configuró Google Cloud, instaló el APK del usuario, capturó GPS real ni cargó un mapa real. Sin commit, subida, integración, etiqueta, Release o borrado. La clave, permisos interactivos y conexión visual de Maps siguen pendientes.


## 0.3.7: cierre del flujo y conservación de datos

Preparación **0.3.7 / código 14, Unreleased**, local, del 3 de octubre de 2026 (México). Los cambios de GPS, coordinación del modelo y protección SQLite forman los incrementos lógicos 0.3.5–0.3.7; se entrega un único artefacto 0.3.7, sin APK históricos independientes 0.3.5/0.3.6. El [detalle de cambios](INCREMENTOS_035_037.md) describe el contrato para el compañero.

```powershell
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest :core:data:connectedDebugAndroidTest :core:data:lintDebug :app:lintDebug :app:assembleDebug :app:lintRelease :app:assembleRelease --console=plain
.\gradlew.bat :core:data:connectedDebugAndroidTest :core:data:lintDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest :core:data:connectedDebugAndroidTest :core:data:lintDebug :app:lintDebug :app:assembleDebug :app:lintRelease :app:assembleRelease --console=plain
```

Las tres ejecuciones aprobaron. La integral terminó en **48 segundos**, 214 tareas (159 ejecutadas, 55 UP-TO-DATE). La intermedia repitió instrumentación y lint de datos tras ajustar una prueba. La final terminó en **13 segundos**, 207 tareas (34 ejecutadas, 173 UP-TO-DATE); eliminó la llamada super vacía que lint señaló y el último `async(Job)` obsoleto de las pruebas, conservando concurrencia estructurada con supervisor. Las repeticiones no aumentan el número de casos.

| Suite | Casos diferentes | Estado en este lote |
| --- | ---: | --- |
| Dominio | 86 | Resultado anterior reutilizado por UP-TO-DATE; dominio sin cambios en este lote |
| JVM app | 74 | Ejecutados; 23 nuevos del modelo y 9 de prerequisitos Maps |
| JVM datos | 27 | Ejecutados; 7 nuevos de renovación GPS |
| Instrumentadas API 37 | 62 | Ejecutadas; 5 nuevas de fuente GPS, 2 de evidencia y 6 de corrupción/cancelación SQLite |
| Total registrado | 249 | 163 diferentes ejecutados; 86 reutilizados |

**Cero fallos, errores y omitidas** en los XML comprobados. **52 casos nuevos** respecto del corte 0.3.4 con Maps pulido. Se cubren evidencia revocada al publicar, fallos ordinarios del proveedor/reloj, retirada transitoria y persistente, cancelación, respuestas tardías, pin modificado durante captura, salida y renovación, confirmación caducada o lejana y errores de prerequisitos.

Las seis nuevas SQLite usan archivos de prueba con nombre aleatorio y limpieza restringida a ese nombre: tres comprueban corrupción al abrir/listar/crear, preservando los bytes y fallando de nuevo al reintentar; tres cancelan en cada guard y comprueban que no hay inserción, incluida la transacción anterior al commit. El handler propio evita la eliminación del archivo dañado y no cambia esquema/migraciones. No intenta reparar una base corrupta ni revierte un commit ya realizado.

Compilaciones **debug y release aprobadas**; release permanece **sin firma configurada**. `apksigner verify --verbose` verificó la firma **v2** del APK debug. Lint datos sin incidencias; app debug y release sin errores/fatales y **13 advertencias visuales previas**. No se ocultaron mediante baseline. Persisten advertencias de herramientas Gradle/JDK/Unsafe externas, registradas en los logs; se retiraron los avisos nuevos de nuestro modelo/pruebas.

| Artefacto local ignorado | Bytes | SHA-256 |
| --- | ---: | --- |
| `.gradle/validacion/citizensecurity-0.3.7-debug.apk` | 12.550.218 | `e9c47af988133c0a2887d1a6e4d1e9df98209e05d2093c18df3327339d86f98e` |
| `.gradle/validacion/citizensecurity-0.3.7-release-unsigned.apk` | 8.975.964 | `774ed6b551612c4f49d37dad47d3904a65aad977498124c8865a394c41e7fbe6` |

Metadatos de ambos: **0.3.7 / 14**. El DEX contiene Maps SDK, modelo, ambos componentes nuevos y guard productivo; el ejemplo de consola sigue excluido. Los **86 archivos de recursos compilados** y su contenido coinciden con el APK 0.3.4 Maps pulido. Se verificaron intactos los hashes de las copias 0.3.1, 0.3.2, 0.3.3 y los tres cortes 0.3.4.

Recibo local `.gradle/validacion/cierre037.json`, script `comprobar-cierre-037.py`, logs `0.3.7-integracion.log`, `0.3.7-cierre-nativo.log`, `0.3.7-cierre.log` y `0.3.7-firma.log`. HEAD permanece en `08ab2d5f88d4b48733f0a415cd931a8efc9c1726` y el índice vacío. `MainActivity`, `SplashActivity` y sus layouts no cambiaron respecto de esa base. No se consultó ni integró el remoto en este lote ni se ejecutó commit, subida, etiqueta o Release. Las herramientas siguen mostrando solo funciones básicas y ejemplo en memoria; guardado real y consulta siguen ocultos.

La comprobación de paquete confirma que el emulador conserva la app del usuario **0.3.1 / código 8**; no se instaló la preparación sobre ella. La instrumentación usa un paquete técnico separado, bases aisladas y callbacks nativos con backend controlado. No acredita una posición GPS real.

**Pendientes y límites:** clave Android/Cloud y restricciones, facturación/API, mapa renderizado con pin y controles, permiso interactivo, GPS real, teléfono físico y API 26–29. `MapsReadiness.Ready` verifica solo configuración y servicios locales; una clave presente no asegura autorización ni red. Si Android rechaza ambas retiradas de un listener no se garantiza que lo haya desregistrado, aunque la solicitud ya rechaza lecturas y eventos tardíos. El filtro local de 5 km reduce reportes lejanos; no acredita un crimen ni impide toda falsificación. No hay flujo completo aceptado ni garantía de ausencia de errores o seguridad de producción. [Avance](AVANCE.md) conserva **7/28 = 25%**.


## 0.3.8: permiso de ubicación en primer plano

Preparación **0.3.8 / código Android 15, Unreleased**, local, del **5 de octubre de 2026 (México)**. El usuario pidió el siguiente paso y confirmó que todavía no hay clave Google. [Permisos de ubicación](PERMISOS_UBICACION.md) documenta el modelo, contrato AndroidX y conexión pendiente de los controles del compañero.

La primera ejecución falló al compilar cuatro llamadas de pruebas con un índice separado por salto de línea. Se corrigieron con `.get(...)`, sin habilitar un lenguaje experimental. La siguiente aprobó 120 JVM, pero lint detuvo el cierre: Google Maps resolvía **Fragment 1.1.0**, incompatible con Activity Result. `dependencyInsight` identificó el origen transitivo; se añadió una restricción a **1.9.1**, estable según las [notas oficiales](https://developer.android.com/jetpack/androidx/releases/fragment). No se añadió baseline ni suppress. La dependencia resuelta y la ejecución final comprueban la corrección.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:lintRelease :app:assembleRelease :app:dependencyInsight --dependency androidx.fragment --configuration debugRuntimeClasspath --console=plain
```

**BUILD SUCCESSFUL en 1 minuto 36 segundos**, 174 tareas (173 ejecutadas, una UP-TO-DATE). **121 JVM de app aprobadas**, cero fallos, errores y omitidas:

| Casos de app | Cantidad | Comprobación |
| --- | ---: | --- |
| Modelos y herramientas anteriores | 74 | Ejecutados de nuevo |
| LocationPermissionViewModel | 25 | Preciso/aproximado/ausente, rationale, cancelación, estado retenido, callback restaurado, revocación, fallos y modelo limpiado |
| PreciseLocationPermissionBinding | 22 | Registro/solicitud explícita, COARSE+FINE, visibilidad, dobles, relectura actual, cierre, reintento y conexión sin captura automática |
| Total | 121 | 47 nuevos respecto de 0.3.7; las repeticiones no se suman |

Los cuatro casos nuevos de cierre cubren un error de lectura/check/callback/lanzamiento posterior a cerrar el puente: no borra hechos ni libera otra petición del modelo retenido. El caso de integración concede el permiso en un registro controlado y comprueba que `IncidentLocationViewModel` conserva el pin y no solicita GPS hasta la acción explícita. La cancelación se propaga; una comprobación fallida no se convierte en permiso concedido.

Las **86 de dominio, 27 JVM de datos y 62 instrumentadas API 37** pertenecen al corte anterior 0.3.7 y **no se ejecutaron en esta preparación**. Dominio, repositorio y fuente GPS no cambiaron. No se informa un total de 296 como si fueran pruebas ejecutadas aquí. Las pruebas nuevas emplean el contrato AndroidX real con `ActivityResultCaller` y lectores controlados; no se ejecutó un diálogo de permiso Android, una rotación nativa ni captura real.

Compilaciones debug y release correctas; release queda **sin firma configurada**. Firma debug v2 verificada con `apksigner`. Lint app debug y release: **0 errores/fatales y 13 advertencias visuales anteriores**. `dependencyInsight` confirma `androidx.fragment:fragment:1.9.1` por restricción y conflicto con 1.0.0/1.1.0 de Maps. Se conservaron avisos de herramientas Gradle/JDK y empaquetado sin stripping de `libandroidx.graphics.path.so`; no son errores de compilación ni se declararon corregidos.

| Artefacto local ignorado | Bytes | SHA-256 |
| --- | ---: | --- |
| `.gradle/validacion/citizensecurity-0.3.8-debug.apk` | 12.698.574 | `3b6e19e4712dd547b2f57cf44c990f2cc6e62f3982ac68abd175ecb5f5248ca6` |
| `.gradle/validacion/citizensecurity-0.3.8-release-unsigned.apk` | 9.078.168 | `6706f79e4e35058faf22677604c7c2ef83b502b8557a850e455e493bcdd6d53c` |

Metadatos de ambos: **0.3.8 / código 15**. El DEX contiene modelo y puente nuevos, contrato de permisos, modelo del mapa y guard productivo. Las copias debug y release sin firma de 0.3.7 conservan sus hashes. La actualización de dependencia puede cambiar recursos compilados: no se afirma identidad con el APK anterior. No cambiaron MainActivity, SplashActivity ni sus layouts respecto de la base local preservada; los accesos reales siguen ocultos y las herramientas conservan el ejemplo en memoria.

Recibo `.gradle/validacion/cierre038.json`, script `comprobar-cierre-038.py` y logs `0.3.8-permisos.log` (compilación de pruebas fallida), `0.3.8-cierre.log` (lint detenido), `0.3.8-fragment-antes.log`, `0.3.8-final.log` y `0.3.8-firma.log`. HEAD sigue en `08ab2d5f88d4b48733f0a415cd931a8efc9c1726`, índice vacío. No se ejecutó fetch, pull, commit, subida, integración, etiqueta, Release ni borrado de trabajo. No había dispositivo conectado; no se instaló la preparación ni se alteraron permisos de la app del usuario.

**Pendientes:** conectar controles y presentación del compañero, comprobar diálogo/rechazo/ubicación aproximada/rotación nativos, obtener GPS real y probar confirmación cercana/lejana; después, Cloud/clave y mapa renderizado. El permiso preciso no garantiza calidad o vigencia de una lectura, y el callback no verifica un incidente ni autoriza guardarlo. La regla de 5 km y revalidación productiva siguen vigentes. Fase 6 abierta, **7/28 = 25%**, sin aceptación funcional del flujo completo.


## 0.3.9: diálogo nativo de permisos en laboratorio aislado

Cierre local del **5 de octubre de 2026**, **0.3.9 / código Android 16 — Unreleased**. Sigue fase técnica 6; no cambia pantallas/login, guardado real o consulta. El laboratorio `verification/location` comparte las fuentes originales del puente mediante `AndroidSourceSet.kotlin.directories`, sin copiar o mover código.

La primera compilación falló en 38 segundos porque el directorio Kotlin compartido estaba registrado en el conjunto Java. Se corrigió siguiendo la [migración oficial al Kotlin integrado de AGP 9](https://developer.android.com/build/migrate-to-built-in-kotlin), sin desactivar el soporte integrado o añadir flags experimentales. La primera compilación no se presenta como validación aprobada.

Con JDK **27+35**, Gradle **9.8**, AGP **9.4.1**, compilador Kotlin **2.4.20** y emulador **Medium_Phone_API_37.0**, Android **17 / API 37**, se ejecutó:

```powershell
.\gradlew.bat :verification:location:connectedPreciseDebugAndroidTest :verification:location:connectedApproximateDebugAndroidTest :verification:location:connectedDeniedDebugAndroidTest :verification:location:lintPreciseDebug :verification:location:lintApproximateDebug :verification:location:lintDeniedDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:lintRelease :app:assembleRelease --console=plain
```

**BUILD SUCCESSFUL en 2 min 25 s**, 402 tareas, 298 ejecutadas y 104 `UP-TO-DATE`. Los XML confirman **121 JVM de app ejecutadas de nuevo** y **3 flujos instrumentados nuevos**, uno por decisión/paquete: **124 pruebas ejecutadas**, cero fallos, errores u omitidas. No se repitieron las 86 de dominio, 27 JVM de datos o 62 instrumentadas de datos del corte anterior; sus resultados permanecen como historia. Las 47 pruebas nuevas de 0.3.8 no son 47 pruebas añadidas otra vez en este paso.

| Paquete técnico, sufijo | Decisión en el diálogo | Resultado actual comprobado | Pruebas |
| --- | --- | --- | --- |
| `.precise` | Precisa, permitir durante uso | FINE y COARSE concedidos, `Precise` | 1 aprobada |
| `.approximate` | Aproximada, permitir durante uso | Solo COARSE concedido, `Approximate` | 1 aprobada |
| `.denied` | Rechazar | FINE y COARSE ausentes, `Denied` | 1 aprobada |

Cada flujo comprueba registro sin diálogo automático, una petición explícita, bloqueo de la segunda, `Activity.recreate()` nativo mientras PermissionController conserva el diálogo, identidad del ViewModel retenida, puente anterior inactivo y callback entregado al nuevo dueño. Después comprueba otra recreación, la lectura actual de permisos, cierre del dueño y una Activity nueva sin autorización inventada. Las esperas son acotadas; los controles se identifican por recursos del PermissionController y el diálogo debe contener la etiqueta de la aplicación técnica.

Los XML fusionados confirman paquetes separados de `com.example.citizensecurity`, `testOnly=true`, `allowBackup=false`, Activity del laboratorio no exportada y sin launcher, sin Application productiva ni API_KEY de Maps. Los registros `LOCATION_PERMISSION_NATIVE` quedan en los logcat por variante y en `.gradle/validacion/cierre039.json`. El laboratorio no solicita captura GPS. Al concluir, no quedaron instalados sus paquetes en el emulador; solo se instalaron fixtures técnicos mediante las tareas de verificación.

Lint de app debug/release: **0 errores**, las mismas **13 advertencias visuales anteriores**. Lint de las tres variantes del laboratorio: **0 errores y 2 advertencias por variante**, `DataExtractionRules` y `MissingApplicationIcon`; pertenecen a la app técnica sin launcher ni datos de reportes, no a una nueva pantalla del producto. No se ocultaron errores o advertencias mediante suppress/baseline. Persisten avisos de herramientas de Java/Gradle y empaquetado sin retirar símbolos de `libandroidx.graphics.path.so`; no son pruebas fallidas.

Debug y release sin firma compilan. `apksigner verify --verbose` comprobó firma debug **v2**. No se instaló ninguno de estos APK sobre la aplicación del usuario:

| Artefacto local ignorado por Git | Tamaño | SHA-256 |
| --- | --- | --- |
| `citizensecurity-0.3.9-debug.apk` | 12,698,570 bytes | `2445a2857f971729d1852373ed28a4e5366d34b8b26a0b528974df9934c5f3bf` |
| `citizensecurity-0.3.9-release-unsigned.apk` | 9,078,164 bytes | `54185b139e08e127f808087969e9bc650fcf5a56e21f30ff9c5f70fbcf128b41` |

Se conservan por hash los APK archivados de 0.3.7 y 0.3.8. La app instalada sigue en **0.3.1 / código 8**, con la misma fecha de actualización **2026-10-03 20:31:07** y SHA-256 del APK **`4d90496173872814b1457c9f194133092b8cedcf959140a5fad7586ae33ee890`**. También se compararon todos los recursos de `app/src/main/res`, MainActivity, SplashActivity y ReleasedToolsActivity con su estado al iniciar este paso: no cambiaron. HEAD continúa en `08ab2d5f88d4b48733f0a415cd931a8efc9c1726` y el índice está vacío.

La prueba verifica **recreación de Activity**, no un giro de sensor, muerte del proceso, teléfono físico, GPS válido o Google Maps renderizado. Clave Cloud, conexión de los controles del compañero, estados GPS dentro de su pantalla y lectura válida del dispositivo siguen pendientes. La política de 5 km y rechazo de lectura simulada no se relajó. **7/28 = 25%**; no acredita el flujo completo ni ausencia de todos los errores.

[Guía del laboratorio](PRUEBAS_PERMISOS_ANDROID.md) explica su repetición y aislamiento. Todo permanece local y Unreleased, sin commit, subida, integración, etiqueta o Release nueva.


## 0.3.10: composición Android de permiso, captura y confirmación

Cierre local del **5 de octubre de 2026 (México)**, **0.3.10 / código Android 17 — Unreleased**. El usuario autorizó el siguiente incremento de fase 6: probar juntos permiso, gesto de captura, evidencia aceptada y confirmación dentro de 5 km, incluidos rechazo, GPS apagado, timeout y salida de la pantalla. Este corte amplía `verification/location`; no cambia la lógica productiva, interfaz o login. [Flujo Android](FLUJO_UBICACION_ANDROID.md) documenta la composición y conexión pendiente.

Con JDK 27+35, Gradle 9.8, AGP 9.4.1, Kotlin 2.4.20 y emulador Medium_Phone_API_37.0, Android 17/API 37, se ejecutó:

```powershell
.\gradlew.bat :verification:location:connectedPreciseDebugAndroidTest :verification:location:connectedApproximateDebugAndroidTest :verification:location:connectedDeniedDebugAndroidTest :verification:location:connectedFlowDebugAndroidTest :verification:location:lintPreciseDebug :verification:location:lintApproximateDebug :verification:location:lintDeniedDebug :verification:location:lintFlowDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:lintRelease :app:assembleRelease --console=plain
```

**BUILD SUCCESSFUL en 1 min 41 s**, 478 tareas (177 ejecutadas y 301 `UP-TO-DATE`). Aprobaron los tres diálogos nativos extendidos y 16 casos de flujo. Al contrastar resultados, se detectó que la selección por lista de clases había dejado fuera los dos casos de `AndroidGpsSourceNativeTest`, aunque se habían compilado en el APK de pruebas. No se presenta esa primera ejecución como 21 casos.

Se corrigió exclusivamente el filtro de `.flow` para seleccionar la suite JUnit explícita `LocationFlowAndroidSuite`, con las dos clases. Después se ejecutó:

```powershell
.\gradlew.bat :verification:location:connectedFlowDebugAndroidTest :verification:location:lintFlowDebug --console=plain
```

**BUILD SUCCESSFUL en 41 s**, 117 tareas (13 ejecutadas y 104 `UP-TO-DATE`). Los XML finales de `.flow` confirman **18 casos = 16 de `LocationFlowNativeTest` + 2 de `AndroidGpsSourceNativeTest`**, cero fallos, errores u omitidos. El total propio del corte es **21 casos Android distintos ejecutados y aprobados**, con **18 definiciones nuevas y tres casos anteriores extendidos**. Los 16 repetidos no se suman dos veces.

Las **121 JVM de app quedaron `UP-TO-DATE`**; sus XML conservan 121 aprobadas de la ejecución previa, sin ejecución nueva en 0.3.10. Las 86 de dominio, 27 JVM de datos y 62 instrumentadas de datos tampoco se ejecutaron aquí. No se declara un total de 142 pruebas ejecutadas en este incremento.

| Grupo Android | Casos | Evidencia propia |
| --- | ---: | --- |
| Preciso, aproximado y rechazo | 3 | Diálogo real, recreación con solicitud pendiente, hechos actuales, pin conservado y cero capturas automáticas. Un gesto posterior llega una vez a la fuente solo con preciso y rechaza su lectura marcada mock; los otros dos no llegan a ella |
| Composición del flujo | 16 | Permiso nativo real y ciclo Android con lectura/reloj/estado GPS controlados: confirmación cerca y rechazo lejos, calidad/vigencia, timeout/error, concurrencia, onStop, recreación, respuesta tardía y sesión cerrada |
| Proveedor y adaptador Android | 2 | Registro real de GPS cancelado antes del callback sin evidencia; objeto Location marcado mock convertido por el adaptador y rechazado por renovación/evidencia/modelo |
| Total distinto | 21 | Sin duplicar repeticiones; 18 definiciones nuevas |

La matriz de 16 usa `ControllablePhoneLocationSource` **solo en el laboratorio**. Comprueba FINE mediante el sistema, pero sus lecturas, estado de GPS y reloj monotónico son fixtures. El reloj sintético es coherente con las lecturas; los tiempos de espera cortos se configuran únicamente allí. El producto conserva **15 segundos**, precisión máxima **100 m**, vigencia máxima **2 minutos** y radio **5 km**. Una lectura sintética con `isMock=false` sirve para ejercitar aceptación, sin acreditar un teléfono físico o alterar la política productiva.

`LocationFlowHarnessActivity` conecta los modelos originales mediante `ViewModelProvider`: los conserva al recrear, almacena applicationContext en dependencias, llama `locationModel.onStop()` y mantiene el puente de permisos hasta `onDestroy()`. No tiene launcher, controles de producto ni persistencia. La prueba de respuesta tardía mantiene una fuente deliberadamente no cancelable, sale mediante el ciclo Android y comprueba que no publica evidencia; su Deferred se libera al terminar incluso si una aserción falla.

El caso del proveedor usa `AndroidPhoneLocationSource` y `LocationManager` originales con permiso preciso real y Activity visible. El registro devuelve una solicitud esperando, que se cancela en el hilo principal **antes de entregar el callback**. Comprueba cancelación y ausencia de evidencia, sin obtener un fix, cambiar ajustes, inyectar coordenadas, añadir proveedores de prueba ni configurar una app de ubicación simulada. El segundo caso crea un objeto `Location` marcado mock dentro del proceso; no modifica el proveedor Android.

Los logcat generan `LOCATION_PERMISSION_NATIVE` con `automaticCaptureCalls=0` en las tres decisiones, `explicitCaptureCalls=1` con preciso y cero en aproximado/rechazo, `phoneEvidencePublished=false` y `productionApplicationTargeted=false`. `LOCATION_GPS_NATIVE` acredita `nativeRegistrationReturned=true`, `cancelledBeforeCallback=true`, `evidencePublished=false` y **`physicalFixProven=false`**. Estos recibos se contrastan con XML y se conservan en `.gradle/validacion/cierre0310.json`.

Los cuatro manifiestos fusionados confirman paquetes exactos distintos del producto, `testOnly=true`, `allowBackup=false`, sin Application productiva ni API_KEY de Maps. Ambas Activities técnicas están no exportadas y sin launcher. Los DEX de debug y release productivos contienen las clases reales, sin `ControllablePhoneLocationSource`, `LocationFlowHarnessActivity`, su fixture o la suite técnica. Al terminar, el runner había retirado todos los paquetes del laboratorio.

**Lint app debug/release: cero errores/fatales y 13 advertencias anteriores**. Las cuatro variantes técnicas tienen **cero errores/fatales y dos advertencias cada una**, `DataExtractionRules` y `MissingApplicationIcon`; no son nuevas pantallas o datos de reportes del producto. No se ocultaron incidencias con suppress/baseline. Persisten los avisos de Java/Gradle y empaquetado anteriores, sin considerarlos corregidos. Debug y release compilan; release permanece **sin firma configurada**. `apksigner verify --verbose` verificó firma debug **v2**.

| Artefacto local ignorado por Git | Bytes | SHA-256 |
| --- | ---: | --- |
| `.gradle/validacion/citizensecurity-0.3.10-debug.apk` | 12.698.574 | `2fb298372e3e3846acf4ba7143e00167052a19f0aad379c9184ba4703e31c021` |
| `.gradle/validacion/citizensecurity-0.3.10-release-unsigned.apk` | 9.078.152 | `f6db2a720ee770b44d6aa00e9e3f201f3466f7d21a734e15338cd77841b25c6b` |

Metadatos de ambos: **0.3.10 / código 17**. Los archivos archivados 0.3.7, 0.3.8 y 0.3.9 conservan sus SHA-256. El paquete instalado del usuario sigue en **0.3.1 / código 8**, misma fecha de actualización **2026-10-03 20:31:07** y SHA-256 **`4d90496173872814b1457c9f194133092b8cedcf959140a5fad7586ae33ee890`**. Todos los recursos de `app/src/main/res`, MainActivity, SplashActivity y ReleasedToolsActivity coinciden con el estado inicial conservado; no se instaló `:app`, borró su base o restableció sus permisos. HEAD sigue en `08ab2d5f88d4b48733f0a415cd931a8efc9c1726`, índice vacío, sin fetch/pull/commit/subida/integración/etiqueta/Release. El emulador temporal se cerró después de verificar ese estado.

Recibo `cierre0310.json`, comprobador `comprobar-cierre-0310.py` y logs `0.3.10-pruebas.log`, `0.3.10-flujo-completo.log` y `0.3.10-firma.log`, todos bajo `.gradle/validacion` ignorado. Los resultados por clase permanecen en `verification/location/build/outputs/androidTest-results/connected/debug/flavors`.

**Límites:** lecturas/estados GPS controlados no equivalen a una posición física. Registro/cancelación antes del callback no demuestra toda la retirada del listener si falla el servicio. Este corte no prueba revocación o cambio de FINE durante una captura nativa, muerte del proceso, sensor de orientación, API 26–30, teléfono físico, controles del compañero ni Google Maps renderizado. La clave Cloud y pantalla integrada siguen pendientes. La regla de 5 km no acredita la veracidad del incidente ni evita toda manipulación del dispositivo. Se conserva **fase 6 abierta, 7/28 = 25%**, sin aceptación funcional completa. Todo sigue local y Unreleased.

## 0.3.11: evidencia GPS vigente después de Ready

Trabajo pendiente cerrado antes de preparar 0.4.0a. Ejecución del 5 de octubre de 2026, versión 0.3.11 / código 18, local y Unreleased en ese corte. Recibo local ignorado: `.gradle/validacion/cierre0311.json`.

`PhoneLocationEvidence` requiere FINE y GPS disponible al publicar y consultar; una pérdida observada o fallo de comprobación invalida la lectura. Recuperar GPS no restaura el fix: exige renovación explícita. Se consulta disponibilidad al acceder, sin observer continuo. Application comparte fuente entre renovación y evidencia; el guard SQLite usa el mismo lector antes de escribir y antes del commit.

Dos casos reprodujeron el defecto antes del fix: confirmar con proveedor apagado y conservar evidencia al recrear después de observar GPS apagado. El ensayo previo falló de forma esperada (20 casos, dos fallos). La ejecución corregida y final terminó en `BUILD SUCCESSFUL in 1m 58s` (517 tareas, 206 ejecutadas, 311 reutilizadas).

| Grupo ejecutado | Aprobados |
| --- | ---: |
| JVM app | 121 |
| JVM datos | 38 |
| Instrumentación datos/SQLite | 64 |
| Tres decisiones nativas de permiso | 3 |
| Flujo Android, incluidas dos del proveedor | 21 |
| **Total distinto ejecutado** | **247** |

**16 definiciones nuevas:** 10 disponibilidad de evidencia + una renovación JVM, dos guard SQLite y tres composición Android. Cero fallos, errores u omitidos en los XML finales. Las 86 de dominio no se ejecutaron otra vez; no se suman a 247. Debug y release sin firma compilaron, firma debug v2 válida, lint sin errores: app 13 advertencias anteriores; datos cero; laboratorio dos por variante.

APK debug archivado: 12.698.578 bytes; SHA-256 `4fbc0b56eacb0c8b4eb84e7155a653e59082419e422ae51105839882be35be1d`. APK release sin firma: 9.078.156 bytes; SHA-256 `5775107a57669fae0e7679adef05ce453bc7cfb8ea5d90b9d58a3b0555e887cd`. Los APK previos, la instalación productiva 0.3.1/código8, pantallas, HEAD e índice de aquel corte quedaron conservados. No repetir ese comparador de HEAD tras los commits autorizados de 0.4.0a.

Proveedor/reloj controlados en regresiones; permiso/ciclo Android reales y bases de prueba aisladas. No se obtuvo una posición física, no se mostró un mapa con clave y no se instaló `:app` sobre el producto.

## 0.4.0a: puente de ubicación y borrador

Validación completada del conjunto integrado, versión 0.4.0a / código19, 6 de octubre de 2026. El usuario autorizó expresamente subida a main y Release v0.4.0a.

El merge local 433b3fb incorpora origin/main b665a56. Los layouts login y creación de cuenta se conservan; herramientas mantiene el rediseño remoto íntegro y añade los controles ya solicitados del ejemplo en memoria. El descriptor generado por Gradle requiere JDK27, sin URLs anteriores de JDK25. No se activa guardado real/consulta ni se instala la app productiva sobre 0.3.1.

Las nuevas pruebas verifican lifecycle, callbacks obsoletos, borrador editable, límites Unicode y SavedStateRegistry/Parcel. Recrear Activity o restaurar Bundle no acredita muerte de proceso real, reinicio ni persistencia después de forzar cierre. El ejemplo temporal conserva su aislamiento. Clave Google, GPS físico, renderizado Maps y conexión visual siguen pendientes.

### Ejecución final y resultados 0.4.0a

```powershell
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :core:data:connectedDebugAndroidTest :core:data:lintDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:lintRelease :app:assembleRelease :verification:location:connectedPreciseDebugAndroidTest :verification:location:connectedApproximateDebugAndroidTest :verification:location:connectedDeniedDebugAndroidTest :verification:location:connectedFlowDebugAndroidTest :verification:location:lintPreciseDebug :verification:location:lintApproximateDebug :verification:location:lintDeniedDebug :verification:location:lintFlowDebug --console=plain
```

`BUILD SUCCESSFUL in 2m`: 519 tareas, 101 ejecutadas y 418 reutilizadas. Cero fallos, errores u omitidos en los XML finales. Recibo ignorado `.gradle/validacion/cierre040a.json`; el primer pase aprobó antes de corregir reapertura y constructor SavedStateHandle y no se suma nuevamente.

| Grupo | Resultado del pase final |
| --- | ---: |
| JVM app | 171 ejecutadas y aprobadas |
| Android datos/SQLite | 64 ejecutadas y aprobadas |
| Android flujo | 28 ejecutadas y aprobadas |
| Android permiso preciso/aproximado/rechazo | 3 ejecutadas y aprobadas |
| **Total distinto ejecutado** | **266** |
| Dominio JVM | 86 reutilizadas, UP-TO-DATE |
| Datos JVM | 38 reutilizadas, UP-TO-DATE |

**57 definiciones nuevas respecto de 0.3.11:** 13 JVM puente, cuatro JVM clicks Maps, cuatro JVM reapertura del modelo, 29 JVM borrador, cinco Android puente/reapertura y dos Android borrador. La recuperación nativa comprueba factory de Activity, recreación y registro SavedState/Parcel con un dueño nuevo; no ejecuta muerte de proceso real.

El pulido permite confirmar, cancelar y volver a abrir la selección en la misma Activity/modelo, exige captura nueva e ignora respuestas viejas. Se retiró el constructor SavedStateHandle de uso exclusivo de pruebas de la ruta productiva: factory usa createSavedStateHandle, sin suppressions de lint.

Lint sin errores: datos cero incidencias; app debug/release **19 advertencias visuales** del conjunto integrado; laboratorio dos por variante (`DataExtractionRules`, `MissingApplicationIcon`). Las advertencias de app corresponden a splash, overdraw, autofill, recursos no utilizados, texto fijo y sugerencias de layout/estilo. No se ocultaron ni se cambió la interfaz del compañero para silenciarlas. No se afirma que el producto carezca de toda advertencia o riesgo.

APK académico debug firmado, versión0.4.0a/código19: **12.747.894 bytes**, SHA-256 `2bfc260619b6be4921dd4dfaf6ddccd26fbab7f65b234db5fa3246acb391c1f8`, firma v2 verificada. Release sin firma compilada: **9.096.364 bytes**, SHA-256 `91b3feb22b544b48f03aec83a3ba257befb52a9e79e21f626864497213d908f7`; no es el APK instalable adjunto. El paquete productivo instalado0.3.1/código8 conserva hash, fechas y versión; no se instala :app. Los paquetes del laboratorio se retiraron al terminar; fixtures ausentes de ambos DEX productivos. APK037/038/039/0310/0311 preservados por hash.

La publicación oficial fue autorizada por instrucción humana precisa. [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a) es la referencia para APK debug académico, suma SHA-256 y cambios; no se borran o reescriben versiones anteriores. La aceptación del flujo visual, Google Cloud, GPS físico y guardado real permanecen pendientes.


## Fase 8 — coordinación del borrador y ubicación — Unreleased

Comprobación realizada el **6 de octubre de 2026** en el corte interrumpido. Al reanudar se completó el registro de resultados y documentación; código y pruebas ya estaban terminados. La fase queda **local y Unreleased**, con aceptación funcional pendiente. No se creó otra versión, commit, subida, integración, etiqueta o Release.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:lintRelease :app:assembleRelease :verification:location:connectedFlowDebugAndroidTest :verification:location:lintFlowDebug --console=plain
```

**BUILD SUCCESSFUL en 1 min 19 s**, 250 tareas: 46 ejecutadas y 204 `UP-TO-DATE`. Se comprobaron los XML y la selección explícita de `LocationFlowAndroidSuite`; **226 casos distintos ejecutados y aprobados**, cero fallos, errores u omisiones. No se suman tareas Gradle como casos de prueba.

| Suite ejecutada en este corte | Resultado |
| --- | --- |
| JVM app | 192 aprobadas, incluidas 21 nuevas de `ReportLocationFlowBindingTest` |
| Android flujo, API 37 | 34 aprobadas: 19 de flujo anterior, dos de fuente GPS, cinco del puente, dos del borrador y seis nuevas de `ReportLocationFlowNativeTest` |
| Definiciones nuevas | 27: 21 JVM y seis Android |

Las suites de dominio/datos y las tres variantes separadas del diálogo de permisos **no se ejecutaron de nuevo**; mantienen sus antecedentes, sin presentarlos como ejecuciones o resultados reutilizados de este pase. Los permisos nativos concedidos al laboratorio y la recreación de Activity sí intervienen en la suite de flujo actual. No se instala `:app` ni se llama a sus pruebas instrumentadas.

Los casos nuevos comprueban token antes de confirmar/cancelar/seleccionar/renovar/pedir permiso, cargas asíncronas fuera de orden incluso con el mismo token, cierre/clicks de vistas sustituidas, cancelación al cerrar un mapa después de reset, listener que falla al instalarse o retirarse, campos editados durante el mapa, Francia desde México bloqueada, callbacks GPS tardíos y recuperación de la apertura retenida sin GPS automático. La regla de 5 km y su evidencia independiente se conserva; los resultados `Confirmed` proceden del flujo vigente. El guardado real y consulta siguen ocultos.

Lint debug/release de app **sin errores, 19 advertencias visuales anteriores** por variante; laboratorio flow **sin errores, dos advertencias anteriores** (`DataExtractionRules`, `MissingApplicationIcon`). Compilación debug/release aprobada y firma debug v2 comprobada con `apksigner`. Las advertencias de herramientas Java/Gradle mantienen sus límites anteriores; no se ocultan con suppressions ni una línea base.

Los artefactos técnicos quedan ignorados y separados de la Release oficial, con metadatos todavía **0.4.0a / código 19**; ese identificador no significa que sean el APK publicado:

| Artefacto local de fase 8 | Bytes | SHA-256 |
| --- | ---: | --- |
| `.gradle/validacion/citizensecurity-fase8-debug.apk` | 12.780.532 | `ba2560383b5b9e4fcc09dfc83945dd177f131b54e5b2900f5dcf020ec8ba9385` |
| `.gradle/validacion/citizensecurity-fase8-release-unsigned.apk` | 9.096.364 | `c1f265135d4e4738b0adfde400387225465c5a45b10e82fb00e59baef424fa16` |

El recibo ignorado `.gradle/validacion/cierre-fase8.json` confirma: APK instalados del producto **0.3.1/código 8**, hash y fechas sin cambios; doce APK archivados de 0.3.7/0.3.8/0.3.9/0.3.10/0.3.11/0.4.0a con sus hashes originales; 48 archivos visuales, de dominio/datos y Gradle intactos; **HEAD `9e73fac` e índice conservados**. Los fixtures no aparecen en los DEX del producto y no quedan paquetes técnicos instalados tras el laboratorio. No se modifica lo publicado ni sus archivos locales archivados.

Para aceptar el flujo completo faltan controles del compañero conectados, clave Maps habilitada, mapa renderizado, GPS físico y revisión del usuario. Las fuentes y clicks controlados no prueban estas condiciones; recrear Activity o restaurar campos no acredita rotación física ni muerte real de proceso. **7/28 hitos = 25%**, sin atribuir un nuevo requisito completo al refuerzo del contrato. [Fase 8](FASE_8.md) y [Acoplar interfaz](ACOPLAR_INTERFAZ.md) explican el siguiente trabajo compartido.

## Fase 9 — incidentes por zona — Unreleased — 6 de octubre de 2026

Se comprobó la lectura geográfica local y su carga cancelable; la aceptación visual permanece pendiente. No se asigna otra versión ni se publica este incremento. El pase central terminó en **BUILD SUCCESSFUL in 2m 9s**:

```powershell
.\gradlew.bat :core:domain:test :core:data:testDebugUnitTest :core:data:connectedDebugAndroidTest :core:data:lintDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:lintRelease :app:assembleRelease :verification:location:connectedFlowDebugAndroidTest :verification:location:lintFlowDebug --console=plain
```

Una revisión de `ReportMapViewModel` detectó que un observador inmediato de `Loading` podía limpiar o sustituir la consulta antes de que se asignara su tarea. Se corrigió reservando el `Job` con inicio `LAZY` y su manejador de finalización antes de emitir el estado. Solo se inicia si conserva su identidad. Dos regresiones comprueban que limpiar no inicia la lectura retirada y que sustituir A por B conserva la cancelación de B. Tras esta corrección se repitieron las JVM de app, compilación y lint afectados, con **BUILD SUCCESSFUL in 16s**:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:lintRelease :app:assembleRelease :verification:location:lintFlowDebug --console=plain
```

| Grupo | Casos distintos ejecutados y aprobados | Definiciones nuevas de fase 9 |
| --- | ---: | ---: |
| Dominio JVM | 96 | 10 en `ReportMapQueryTest` |
| Datos JVM | 38 | 0 |
| SQLite nativo Android API 37 | 78 | 14 en `SqliteReportMapQueryTest` |
| App JVM, última ejecución | 211 | 19 en `ReportMapViewModelTest` |
| Laboratorio Android flow API 37 | 34 | 0 |
| **Total distinto** | **457** | **43** |

Todos los XML registran **0 fallos, 0 errores y 0 omitidas**. Estos cinco grupos se ejecutaron en este corte, sin reutilizar resultados como `UP-TO-DATE`. El primer pase tenía 209 JVM de app; el segundo añadió las dos regresiones y aprobó 211. Las repeticiones se cuentan una vez en el total distinto de 457. No se repitieron las tres variantes separadas del diálogo de permisos ni se ejecutó `:app:connectedAndroidTest`.

Las pruebas comprueban bordes inclusivos, intervalos que cruzan el antimeridiano, equivalencia de ±180, filtros aplicados antes del límite, orden por fecha/nanosegundos/folio, señales `hasMore`, exclusión de reportes sin coordenadas, lectura tras reabrir SQLite y salida máxima de 500 entre 501 coincidencias. El contrato conserva filtros y listas inmutables. El modelo no consulta al crearse, rechaza respuestas tardías, conserva la consulta original de resultados anteriores y permite reintentar sin tratar la cancelación como un error. Los estados alternativos en pruebas usan bases aisladas; no se añadió una operación administrativa al producto.

| Comprobación | Resultado |
| --- | --- |
| Compilación producto debug/release | Aprobada; release sigue sin firma de distribución |
| Firma debug | v2 verificada con `apksigner` |
| Lint datos debug | 0 errores, 0 advertencias |
| Lint app debug/release | 0 errores; 19 advertencias anteriores en cada variante |
| Lint laboratorio flow | 0 errores; 2 advertencias técnicas anteriores |
| Fuentes visuales, actividades del compañero y Gradle | Hashes conservados |
| Producto instalado en el AVD | 0.3.1/código 8; hash y fechas conservados |
| HEAD e índice Git | Conservados en `9e73fac`; sin acciones de publicación |
| Catorce APK anteriores, incluidos fase 8 y 0.4.0a | Hashes conservados |
| Fixtures en DEX productivo / paquetes de laboratorio restantes | Ausentes |

Artefactos técnicos **locales y separados**, con metadato aún **0.4.0a/código 19**; no son el APK publicado de esa Release:

| Archivo ignorado | Bytes | SHA-256 |
| --- | ---: | --- |
| `.gradle/validacion/citizensecurity-fase9-debug.apk` | 12.780.532 | `259dd109f508f53e2ad31b02abd07f88240bc2bf8658eeaad62b46e9ae4e79c0` |
| `.gradle/validacion/citizensecurity-fase9-release-unsigned.apk` | 9.112.748 | `3a0864bf803674cc5fcaa9457842a8d70b822e3ed3082ef849e016fbce1de1e8` |

El recibo ignorado `.gradle/validacion/cierre-fase9.json` comprueba XML, tareas ejecutadas, firma, lint, hashes y conservación del entorno. Logs ignorados: `fase9-pruebas.log`, `fase9-reentrada.log` y `fase9-firma.log`. La app productiva no se instaló para estas pruebas.

El resultado es una consulta **local**, no un mapa de incidentes compartido entre usuarios ni un sistema de seguimiento administrativo. Faltan clave habilitada, mapa renderizado con marcadores, conexión de controles y GPS físico. No se comprobó carga Cloud ni se creó facturación. La [guía de la clave](CONFIGURAR_CLAVE_MAPS.md) explica ese paso manual. **7/28 = 25%** conserva los criterios de aceptación del PDF; el backend solo prepara el hito visual. Guardado real y consulta permanecen ocultos, sin cambios de interfaz/login. No hubo commit, subida, integración, etiqueta, Release nueva ni apagado.
