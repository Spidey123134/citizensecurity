# CitizenSecurity 0.7.1 rev. 2

**27 de noviembre de 2026 · versión preliminar · Android 0.7.1-rev.2 / código 34.** Se toma la 0.7.1 de Compose publicada en el commit `76d6ad5`. El código 34 distingue esta revisión de la 0.7.1 preliminar y la 0.7.0 rev. 2 local, ambas con código 33. [Descargas](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.7.1-rev.2).

- [CitizenSecurityNavigation](../app/src/main/kotlin/com/example/citizensecurity/CitizenSecurityNavigation.kt) organiza el acceso a Inicio y las rutas de las pantallas Compose.
- [HomeScreen](../app/src/main/kotlin/com/example/citizensecurity/release/HomeScreen.kt) presenta el Inicio de 0.7.1.
- [ReportsScreen](../app/src/main/kotlin/com/example/citizensecurity/reports/ReportsScreen.kt) coordina selección, búsqueda y consulta. Separa la presentación en [FilterRow](../app/src/main/kotlin/com/example/citizensecurity/screen/reports/FilterRow.kt), [ReportDetails](../app/src/main/kotlin/com/example/citizensecurity/screen/reports/ReportDetails.kt) y [ReportLabels](../app/src/main/kotlin/com/example/citizensecurity/screen/reports/ReportLabels.kt).
- [core:data](../core/data/build.gradle.kts) ejecuta el almacenamiento local y [core:domain](../core/domain/build.gradle.kts) contiene las reglas.

## Instalar la revisión

El paquete `com.example.citizensecurity` y el certificado académico permiten instalar esta APK encima de la [0.7.0 oficial](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.7.0) para conservar el almacenamiento. La [0.7.1 preliminar de GitHub](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.7.1) lleva otro certificado. Si tienes esa APK de demostración instalada, conserva la instalación y sus datos ante el rechazo de Android.

## Validación

En emulador API 37 se instaló la 0.7.0 oficial, se guardaron datos de prueba y se aplicó la actualización a rev. 2. En el proceso nuevo se comprobaron bytes, esquema SQLite 5 y lecturas del reporte, historial, resultado, foto, contacto, borrador y solicitud de guardado. La solicitud no generó otro reporte. El lanzador abrió el acceso de Compose. La prueba se limita a los datos preparados en el emulador.

Pasaron 1,461 pruebas JVM (216 de dominio, 158 de datos y 1,087 de app). Compilación y ensamblados debug/release correctos, lint con cero errores y 16 avisos por variante. La APK pesa 56,010,416 bytes y tiene SHA-256 `94deca992e837a906c77fec88e5071c6c040dc5caf9714e3eb876c7c40e04adf`.

Pasaron 26 pruebas Android API 37. Se probaron 11 de Inicio y Acceso, 12 de Reportes, ayuda e aislamiento de cuenta, una de mapa a detalle y regreso, y las dos etapas de actualización. El primer intento de regreso al mapa consultó la interfaz durante el cambio de ventana. Se corrigió la espera de esa prueba y pasó al repetirla, conservando las comprobaciones y la misma APK. Los registros del corte quedan en `.gradle/validacion/rev071r2/` en la copia de trabajo.
