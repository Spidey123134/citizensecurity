# CITIZENSECURITY

Proyecto Android académico en Kotlin para registrar y consultar incidentes. El desarrollo se hace una función por vez. En esta primera base los reportes se guardan en el dispositivo; no hay envío a autoridades ni atención de emergencias.

## Primera función

Validar y guardar un reporte local con categoría, prioridad, descripción, fecha y ubicación escrita o coordenadas. La consulta por folio permite comprobar que el registro permanece después de reabrir la base.

El compañero añadió `SplashActivity` y un layout visual de login en [su commit `7e90109`](https://github.com/Spidey123134/citizensecurity/commit/7e90109c82b1b35e3dfdec76ac6624fa2f699fff). La autenticación aún no está implementada. Falta `app/src/main/res/layout/activity_splash.xml`, por lo que la aplicación integrada todavía no compila; las interfaces continúan bajo su responsabilidad.

## Estructura

| Módulo | Responsabilidad |
| --- | --- |
| `app` | Entrada Android, interfaces y composición de dependencias |
| `core/domain` | Modelos, contrato del repositorio y validación independiente de Android |
| `core/data` | SQLite y persistencia del reporte |

Lee [Trabajo del equipo](docs/TRABAJO_EQUIPO.md) y [Contrato de la primera función](docs/GUARDAR_REPORTE.md).

Para revisar la función por pasos, empieza con [Recibir los datos del reporte](docs/RECIBIR_REPORTE.md). Incluye un ejemplo de consola que reutiliza `NewReport` y permite cambiar los cinco datos sin una pantalla.

El incremento **0.2.0** permite [validar el reporte y su ubicación](docs/VALIDAR_REPORTE.md). Se puede comprobar un punto válido, un par de coordenadas incompleto y un punto fuera de rango. Esta es nuestra parte inicial para conectar después la selección de un punto en Google Maps.

El desarrollo actual es **0.3.1 — Unreleased**, un parche sobre el trabajo de `0.3.0`, que tampoco se publicó. Conserva la [fase 3 de guardado local](docs/FASE_3.md) y corrige una operación cancelada mientras esperaba entrar al repositorio. Las 9 pruebas instrumentadas del parche aprobaron antes de incorporar la nueva interfaz; ese commit no cambió el dominio ni los datos. La revisión del parche se prepara mediante un PR borrador, sin integrarlo en `main` hasta resolver el recurso faltante. La última release publicada continúa siendo `v0.2.0`.

El comando `:core:data:guardarReporte` ejecuta las pruebas Android, muestra un folio real y exporta evidencia del guardado, reapertura y rechazo en bases temporales. Requiere un emulador o teléfono. El estado de compilación de la aplicación y la evidencia por versión están en [Validación](docs/VALIDACION.md).

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

El segundo comando requiere un emulador o teléfono. En el estado integrado actual, `:app:assembleDebug` y `:app:lintDebug` se bloquean por `activity_splash` ausente. Lint de `core/data` y el procesamiento del manifiesto aprobaron. Los resultados completos se registran en [Validación](docs/VALIDACION.md).

## Siguientes incrementos

La siguiente línea acordada es seleccionar dónde ocurrió el incidente en Google Maps. Nuestra parte recibe, valida y conserva las coordenadas; el compañero desarrolla la vista del mapa y sus controles. El mapa y la configuración de Google Cloud todavía están pendientes. Se revisa este incremento antes de conectar el servicio. Cuentas, sincronización, fotografías, GPS, administración y notificaciones siguen pendientes.
