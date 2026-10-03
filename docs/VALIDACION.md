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

`guardarReporte` depende de la instrumentación completa, lee la evidencia JSON capturada en Logcat y la exporta a `core/data/build/reports/fase3/<dispositivo>/reporte.json` y `rechazo.json`. La consola mostró un folio generado por SQLite, coordenadas `19.4326077, -99.1332088` y la recuperación del mismo reporte al reabrir. El caso inválido conservó un registro previo y no insertó otro. Cada prueba usa y elimina una base temporal; la demostración no modifica la base productiva.

Los datos son ejemplos conocidos con reloj fijo para comprobar fechas y nanosegundos. El folio se genera en cada ejecución; `generatedAt` registra el momento real de emisión de la evidencia. La demostración no depende de una interfaz ni de Google Maps.

APK local de desarrollo: versión `0.3.0`, código `3`, 11 729 718 bytes. SHA-256 `5132185a350c8c17f7291c3d741442d03e5e7d335c9d64f0c77e5aa3e5a42ee9`; firma verificada mediante `apksigner`. El artefacto reemplaza el APK local anterior. La versión permanece **Unreleased**, sin etiqueta ni release publicada. La última publicada sigue siendo `v0.2.0`.

La aceptación funcional del usuario, el teléfono físico y API 26 siguen pendientes. Se conservan los límites de herramientas indicados al inicio de este documento.
