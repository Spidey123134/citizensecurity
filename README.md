# CITIZENSECURITY

Proyecto Android académico en Kotlin para registrar y consultar incidentes. El desarrollo se hace una función por vez. En esta primera base los reportes se guardan en el dispositivo; no hay envío a autoridades ni atención de emergencias.

## Primera función

Validar y guardar un reporte local con categoría, prioridad, descripción, fecha y ubicación escrita o coordenadas. La consulta por folio permite comprobar que el registro permanece después de reabrir la base.

El módulo `app` contiene una actividad y un contenedor vacío: las interfaces, el menú y la navegación corresponden al compañero. No se incluye una interfaz provisional de formulario ni datos de demostración al iniciar.

## Estructura

| Módulo | Responsabilidad |
| --- | --- |
| `app` | Entrada Android, interfaces y composición de dependencias |
| `core/domain` | Modelos, contrato del repositorio y validación independiente de Android |
| `core/data` | SQLite y persistencia del reporte |

Lee [Trabajo del equipo](docs/TRABAJO_EQUIPO.md) y [Contrato de la primera función](docs/GUARDAR_REPORTE.md).

Para revisar la función por pasos, empieza con [Recibir los datos del reporte](docs/RECIBIR_REPORTE.md). Incluye un ejemplo de consola que reutiliza `NewReport` y permite cambiar los cinco datos sin una pantalla.

## Herramientas

Java 27 para ejecutar Gradle; Java/Kotlin JVM 17 para el código Android. El segundo ajuste determina el bytecode de la aplicación, no la versión del JDK instalado. Android mínimo 8.0 / API 26.

Las versiones de las dependencias están fijadas en `gradle/libs.versions.toml`; el wrapper verifica su descarga con SHA-256.

## Abrir y verificar

Abre esta carpeta completa en Android Studio. Usa JDK 27 para Gradle y Android SDK Platform 37.0 / Build Tools 37.0.0. La ruta del SDK se guarda en `local.properties`, fuera de Git. El JDK local del IDE se configura con `GRADLE_LOCAL_JAVA_HOME` en `.gradle/config.properties`.

```powershell
.\gradlew.bat :core:domain:test :core:data:lintDebug :app:lintDebug :app:assembleDebug
.\gradlew.bat :core:data:connectedDebugAndroidTest
```

El segundo comando requiere un emulador o teléfono. Los resultados reales de la primera revisión se registran en [Validación](docs/VALIDACION.md).

## Siguientes incrementos

Se elige el siguiente incremento después de revisar esta función. Cuentas, sincronización, fotografías, GPS, administración y notificaciones quedan pendientes. La configuración de un servicio externo deberá acordarse antes de añadirlo.
