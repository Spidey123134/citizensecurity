# CITIZENSECURITY

Proyecto Android académico en Kotlin para registrar y consultar incidentes. El desarrollo se hace una función por vez. En esta primera base los reportes se guardan en el dispositivo; no hay envío a autoridades ni atención de emergencias.

## Primera función

Validar y guardar un reporte local con categoría, prioridad, descripción, fecha y ubicación escrita o coordenadas. La consulta por folio permite comprobar que el registro permanece después de reabrir la base.

El compañero añadió `SplashActivity` y un layout visual de login en [su commit `7e90109`](https://github.com/Spidey123134/citizensecurity/commit/7e90109c82b1b35e3dfdec76ac6624fa2f699fff). Completó el recurso `activity_splash.xml` en [el commit `e18e636`](https://github.com/Spidey123134/citizensecurity/commit/e18e636ff60ea19a13e01ddf98c3de67d158e692), ya incorporado desde `main` actualizado. La autenticación todavía está pendiente; las interfaces continúan bajo su responsabilidad.

## Estructura

| Módulo | Responsabilidad |
| --- | --- |
| `app` | Entrada Android, interfaces y composición de dependencias |
| `core/domain` | Modelos, contrato del repositorio y validación independiente de Android |
| `core/data` | SQLite y persistencia del reporte |

Lee [Trabajo del equipo](docs/TRABAJO_EQUIPO.md) y [Contrato de la primera función](docs/GUARDAR_REPORTE.md).

Para revisar la función por pasos, empieza con [Recibir los datos del reporte](docs/RECIBIR_REPORTE.md). Incluye un ejemplo de consola que reutiliza `NewReport` y permite cambiar los cinco datos sin una pantalla.

El incremento **0.2.0** permite [validar el reporte y su ubicación](docs/VALIDAR_REPORTE.md). Se puede comprobar un punto válido, un par de coordenadas incompleto y un punto fuera de rango. Esta es nuestra parte inicial para conectar después la selección de un punto en Google Maps.

El mantenimiento actual es **0.2.1**, con número interno Android `4`, preparado y comprobado sobre la base actualizada del compañero. [PR #4](https://github.com/Spidey123134/citizensecurity/pull/4) registra su integración con `main`. Corrige el guardado de una operación cancelada mientras esperaba entrar al repositorio y conserva los cambios visuales del compañero. Compilación, lint y las 9 pruebas instrumentadas aprobaron. Todavía no se publicó una release `0.2.1`; la última publicada continúa siendo `v0.2.0`.

La [fase 3 de guardado local](docs/FASE_3.md) y la versión **0.3.0 permanecen en Unreleased**: existe código adelantado, pero su entrega sigue pendiente. El comando `:core:data:guardarReporte` ya permite comprobar el folio, la reapertura y el rechazo en bases temporales; esa demostración se conserva como preparación de la fase. `0.3.1` fue un identificador provisional de pruebas, sin etiqueta ni release. La evidencia por versión está en [Validación](docs/VALIDACION.md).

## Seguimiento del proyecto

- [Inicio del equipo](docs/INICIO_EQUIPO.md): clonar la base, configurar la PC y subir una función desde una rama propia.
- [Changelog](CHANGELOG.md): cambios realizados y comprobaciones ejecutadas.
- [Fases de trabajo](docs/FASES.md): estado de cada paso, responsable y resultado que se revisará.
- [Releases](https://github.com/Spidey123134/citizensecurity/releases): entregas por versión con sus cambios y comprobaciones. El proceso se describe en [Publicar una versión](docs/RELEASES.md).

El compañero `vazdavr-sudo` desarrolla las interfaces y el login. Nuestra parte se centra en la lógica y los datos del reporte. Se revisa una función por vez antes de elegir la siguiente.

## Herramientas

Java 27 para ejecutar Gradle; Java/Kotlin JVM 17 para el código Android. El segundo ajuste determina el bytecode de la aplicación, no la versión del JDK instalado. Android mínimo 8.0 / API 26.

Las versiones de las dependencias están fijadas en `gradle/libs.versions.toml`; el wrapper verifica su descarga con SHA-256.

## Abrir y verificar

Abre esta carpeta completa en Android Studio. Usa JDK 27 para Gradle y Android SDK Platform 37.0 / Build Tools 37.0.0. La ruta del SDK se guarda en `local.properties`, fuera de Git. El JDK local del IDE se configura con `GRADLE_LOCAL_JAVA_HOME` en `.gradle/config.properties`.

```powershell
.\gradlew.bat :core:domain:test :core:data:lintDebug :app:lintDebug :app:assembleDebug
.\gradlew.bat :core:data:connectedDebugAndroidTest
```

El segundo comando requiere un emulador o teléfono. El estado completo de `0.2.1`, con el splash del compañero, aprobó la comprobación. `app` conserva 13 advertencias visuales de lint y la autenticación sigue pendiente. Los resultados y los límites se registran en [Validación](docs/VALIDACION.md).

## Siguientes incrementos

La siguiente línea acordada es seleccionar dónde ocurrió el incidente en Google Maps. Nuestra parte recibe, valida y conserva las coordenadas; el compañero desarrolla la vista del mapa y sus controles. El mapa y la configuración de Google Cloud todavía están pendientes. Se revisa este incremento antes de conectar el servicio. Cuentas, sincronización, fotografías, GPS, administración y notificaciones siguen pendientes.
