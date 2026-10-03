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

La [conexión de la interfaz con los reportes](docs/ACOPLAR_INTERFAZ.md) ofrece un `ReportViewModel` con progreso de guardado, errores por campo y confirmación con folio. `MainActivity` conserva el layout del compañero y obtiene el modelo del repositorio compartido. El formulario de reportes y la autenticación siguen pendientes de su parte.

Para revisar la función por pasos, empieza con [Recibir los datos del reporte](docs/RECIBIR_REPORTE.md). Incluye un ejemplo de consola que reutiliza `NewReport` y permite cambiar los cinco datos sin una pantalla.

El incremento **0.2.0** permite [validar el reporte y su ubicación](docs/VALIDAR_REPORTE.md). Se puede comprobar un punto válido, un par de coordenadas incompleto y un punto fuera de rango. Esta es nuestra parte inicial para conectar después la selección de un punto en Google Maps.

La versión actual de `main` es **0.4.0**, código Android **7**. Incorpora la consulta local por folio, la lectura del estado inicial y las correcciones de cancelación, conservando el splash, el login visual y el acceso de demostración de **0.2.1**. [Release v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0). El compañero se actualiza desde `main`; las Releases conservan cada entrega y sus notas.

La [fase 4](docs/FASE_4.md) ofrece `ReportQueryViewModel`, su fábrica y `MainActivity.reportQueryViewModel` para conectar el futuro control del compañero. La demostración `:core:data:consultarReporte` recupera dos reportes después de reabrir SQLite y comprueba que las lecturas no modifican sus datos. Distingue folio inválido, inexistente y fallo de lectura. El [plan del proyecto](docs/PLAN_DEL_PROYECTO.md) relaciona este alcance con el PDF.

El botón **Desarrollador · funciones 0.2** y **admin / admin** siguen disponibles para recepción, validación y coordenadas de fases 1 y 2. Son herramientas locales; no crean cuentas ni permisos administrativos. La consulta de 0.4 se comprueba mediante Gradle y su contrato técnico; su pantalla sigue pendiente del compañero. [Funciones publicadas](docs/FUNCIONES_PUBLICADAS.md) explica ambas formas de revisión.

La [fase 3 de guardado local](docs/FASE_3.md) y la versión **0.3.0 permanecen en Unreleased**: existe código adelantado, pero su entrega sigue pendiente. El comando `:core:data:guardarReporte` ya permite comprobar el folio, la reapertura y el rechazo en bases temporales; esa demostración se conserva como preparación de la fase. `0.3.1` fue un identificador provisional de pruebas, sin etiqueta ni release. La evidencia por versión está en [Validación](docs/VALIDACION.md).

## Seguimiento del proyecto

- [Inicio del equipo](docs/INICIO_EQUIPO.md): clonar la base, configurar la PC y subir una función desde una rama propia.
- [Changelog](CHANGELOG.md): cambios realizados y comprobaciones ejecutadas.
- [Fases de trabajo](docs/FASES.md): estado de cada paso, responsable y resultado que se revisará.
- [Plan del proyecto](docs/PLAN_DEL_PROYECTO.md): requisitos del PDF, alcance de la consulta local y funciones pendientes.
- [Releases](https://github.com/Spidey123134/citizensecurity/releases): entregas por versión con sus cambios y comprobaciones. El proceso se describe en [Publicar una versión](docs/RELEASES.md).

El compañero `vazdavr-sudo` desarrolla las interfaces y el login. Nuestra parte se centra en la lógica y los datos del reporte. Se revisa una función por vez antes de elegir la siguiente.

## Herramientas

Java 27 para ejecutar Gradle; Java/Kotlin JVM 17 para el código Android. El segundo ajuste determina el bytecode de la aplicación, no la versión del JDK instalado. Android mínimo 8.0 / API 26.

Las versiones de las dependencias están fijadas en `gradle/libs.versions.toml`; el wrapper verifica su descarga con SHA-256.

## Abrir y verificar

Abre esta carpeta completa en Android Studio. Usa JDK 27 para Gradle y Android SDK Platform 37.0 / Build Tools 37.0.0. La ruta del SDK se guarda en `local.properties`, fuera de Git. El JDK local del IDE se configura con `GRADLE_LOCAL_JAVA_HOME` en `.gradle/config.properties`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :core:domain:test :core:data:lintDebug :app:lintDebug :app:assembleDebug
.\gradlew.bat :core:data:consultarReporte
```

El segundo comando está disponible en `main` de `0.4.0`, requiere un emulador o teléfono y ejecuta la suite instrumentada antes de mostrar y exportar la consulta. Usa una base temporal y no consulta ni modifica los reportes productivos del usuario.

La entrega oficial `0.2.1` aprobó 14 pruebas JVM de app y 9 instrumentadas SQLite; las 32 de dominio conservaron su resultado mediante `UP-TO-DATE`. La entrega `0.4.0` aprobó 24 JVM de app, las 32 de dominio ejecutadas de nuevo y 10 instrumentadas en API 37, además de los JSON de consulta y guardado. Ambas comprobaciones incluyeron lint y compilación debug/release sin errores; se mantienen 13 advertencias visuales previas y datos sin incidencias. La comprobación visual de las herramientas oficiales y los límites están en [Validación](docs/VALIDACION.md). La consulta visual, la rotación real de ese futuro flujo y la aceptación funcional de `0.4.0` siguen pendientes. El APK debug se identifica como compilación para pruebas académicas.

## Siguientes incrementos

La siguiente línea acordada es seleccionar dónde ocurrió el incidente en Google Maps. Nuestra parte recibe, valida y conserva las coordenadas; el compañero desarrolla la vista del mapa y sus controles. El mapa y la configuración de Google Cloud todavía están pendientes. Se revisa este incremento antes de conectar el servicio. Cuentas, sincronización, fotografías, GPS, administración y notificaciones siguen pendientes.
