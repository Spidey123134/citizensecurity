# Validación de la base y sus revisiones

## Revisión 0.1.0-rev.2-beta · 13 de noviembre de 2026

Esta revisión reúne el código en `app`, renombra la implementación SQLite a `AdminReportes` y añade los contenedores vacíos `Home` y `ReportDetails` conectados por `Navigation`. Las reglas del validador y el esquema SQLite se conservan.

Comandos de la estructura actual:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

| Verificación de esta revisión | Estado |
| --- | --- |
| 28 pruebas JVM trasladadas a `app` | Ejecutadas y aprobadas, sin fallos, errores u omitidos |
| 5 pruebas instrumentadas SQLite trasladadas a `app` | Ejecutadas y aprobadas en emulador propio API 26 |
| 2 pruebas instrumentadas de inicio y navegación | Ejecutadas y aprobadas en el mismo emulador API 26 |
| Compilación y ensamblados debug/release, APK de instrumentación | Correctos |
| Lint debug/release | Cero errores; siete avisos por variante: cinco actualizaciones disponibles y dos del contenedor/icono originales |

**35 casos distintos aprobados en esta revisión**, sin sumar el corte histórico. Se comprobó además la equivalencia de siete archivos trasladados respecto a `b4f80a8`: solo cambian paquetes/imports y el nombre del gestor; las reglas, SQLite y los casos de prueba permanecen iguales. Recibos locales ignorados: `.gradle/validacion/beta010/relocation-proof.json` y `test-proof.json`.

APK preparado: **1,502,794 bytes**, SHA-256 `4263c3bfa9c312083ba2503c5749195493b6f00df4b3164cfe80f39b434fa508`, versión `0.1.0-rev.2-beta`/código 2, firma v2 académica comprobada y paquete `com.example.citizensecurity.basebeta`. El launcher se abrió correctamente en el emulador. No se probó un teléfono físico ni una actualización desde 0.7: esta beta tiene identidad y almacenamiento independientes.

Las dos pruebas de navegación comprueban que inicio y detalle permanecen vacíos, que el contenedor se sustituye correctamente y que la lista de archivos de base de datos no cambia durante el recorrido. No prueban un formulario ni un flujo visual de registro: esos controles todavía no existen.

Rutas actuales de resultados:

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Pruebas JVM: `app/build/test-results/testDebugUnitTest/`.
- Instrumentación ejecutada por runner Android: `.gradle/validacion/beta010/android-api26.log`; `connectedDebugAndroidTest` genera sus resultados bajo `app/build/outputs/androidTest-results/connected/debug/` cuando se usa ese comando.
- Lint: `app/build/reports/`.

El emulador propio se cerró después de verificar instalación, pruebas y launcher, conservando su carpeta. No se cambiaron permisos ni ajustes del dispositivo. La instalación simultánea con la oficial no fue una prueba ejecutada. Los resultados históricos siguientes corresponden al corte original y no acreditan esta revisión.

## Registro histórico · primera entrega 0.1.0

Verificación local realizada el 2 de octubre de 2026, hora de México. Esta revisión cubre el contrato de validación y el guardado local; el diseño de pantallas corresponde al compañero.

### Herramientas utilizadas

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

### Comando y resultados

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

### Artefactos locales

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256: `4c98ef7d1b4559ad26034f0e17de446cbd85a36afb17e5ae95db9d168dbf0713`.
- Resultados JVM: `core/domain/build/test-results/test/`.
- Resultados instrumentados: `core/data/build/outputs/androidTest-results/connected/debug/`.
- Informes de lint: `app/build/reports/` y `core/data/build/reports/`.

Los artefactos y las rutas locales del SDK/JDK quedan fuera de Git. Esta entrega todavía no incluye un flujo visual para guardar reportes ni una prueba en un teléfono físico o en API 26; el compañero conectará su interfaz al contrato documentado.
