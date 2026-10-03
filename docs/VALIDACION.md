# Validación de la primera entrega

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


## Cierre de la entrega 0.4.0

El 3 de octubre de 2026 el usuario solicitó terminar la versión 0.4.0 que estaba en desarrollo. Se conserva ese número, código Android **7**, y se prepara la integración mediante [PR #7](https://github.com/Spidey123134/citizensecurity/pull/7) y la [Release v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0). **0.3.0 permanece Unreleased**, sin etiqueta ni release propia; las etiquetas previas conservan su historial.

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
