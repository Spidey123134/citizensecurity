# CITIZENSECURITY · Base beta

**0.1.0-rev.2-beta · publicada el 13 de noviembre de 2026.** Revisión de la base inicial para organizar el código según el pizarrón de clase. Parte del commit `b4f80a8`; es una prerelease independiente de la versión oficial 0.7.

La primera función valida, guarda y consulta reportes en SQLite local. Esta revisión conserva sus reglas y organiza la aplicación en `MainActivity`, `navigation`, `screen` y `data`. `Home` y `ReportDetails` son contenedores vacíos para continuar el trabajo de interfaz; el inicio muestra `Home`, sin formulario ni registros de ejemplo.

## Estructura real

Todo el código está en el módulo `app`, bajo `app/src/main/kotlin/com/example/citizensecurity/`:

```text
├── MainActivity.kt
├── CitizenSecurityApplication.kt
├── navigation/Navigation.kt
├── screen/
│   ├── Home.kt
│   └── ReportDetails.kt
└── data/
    ├── Report.kt
    ├── ReportRepository.kt
    ├── ReportValidator.kt
    ├── AdminReportes.kt
    └── ReportDatabase.kt
```

`AdminReportes` implementa `ReportRepository`: valida y crea un reporte, busca por folio y lista los registros. El nombre corresponde al gestor de datos del pizarrón; no añade un rol de administrador. La interfaz usa Kotlin y el contenedor XML `activity_main.xml`.

- [Estructura de esta beta, diagrama y guion de presentación](docs/ESTRUCTURA_BETA.md).
- [Documento histórico del primer corte 0.1.0](docs/ESTRUCTURA_INICIAL.md).
- [Contrato para guardar y consultar](docs/GUARDAR_REPORTE.md).
- [Trabajo del equipo](docs/TRABAJO_EQUIPO.md), [validación](docs/VALIDACION.md) y [cambios](CHANGELOG.md).

## Abrir y verificar

Abre esta carpeta completa en Android Studio. La configuración conserva JDK 27 para ejecutar Gradle, bytecode Java/Kotlin 17, Android SDK Platform 37.0 y Build Tools 37.0.0. Android mínimo: API 26. Las versiones están fijadas en `gradle/libs.versions.toml`; el wrapper verifica su distribución con SHA-256. `local.properties` y la configuración local del JDK quedan fuera de Git.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

El segundo comando requiere un emulador o teléfono. Consulta los resultados y límites de cada revisión en [Validación](docs/VALIDACION.md); las pruebas históricas del 2 de octubre no acreditan por sí solas esta reorganización.

## Instalar la beta

El APK de esta revisión usa el nombre **CitizenSecurity Base beta**, `versionName` **0.1.0-rev.2-beta**, `versionCode` **2** y el identificador `com.example.citizensecurity.basebeta`. Puede instalarse junto a la aplicación oficial y guarda sus datos en su propio espacio privado.

La rama `beta/0.1.0-rev2` y la etiqueta `v0.1.0-rev.2-beta` identifican esta entrega. [Abrir la beta publicada](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.1.0-rev.2-beta) · [APK](https://github.com/Spidey123134/citizensecurity/releases/download/v0.1.0-rev.2-beta/citizensecurity-0.1.0-rev.2-beta.apk) · [Código fuente ZIP](https://github.com/Spidey123134/citizensecurity/releases/download/v0.1.0-rev.2-beta/citizensecurity-0.1.0-rev.2-beta-fuentes.zip) · [Checksum](https://github.com/Spidey123134/citizensecurity/releases/download/v0.1.0-rev.2-beta/SHA256SUMS-0.1.0-rev.2-beta.txt). Los tres archivos se descargaron de nuevo y coinciden con los originales. La publicación está marcada como prerelease y la versión oficial 0.7 sigue como la más reciente.

El tag conserva el código validado de `ef6c60177a3aebd801331b61ea2a00e5410cdf45`; los commits posteriores de esta rama solo cierran la documentación de publicación.

Esta base permite crear, buscar y listar desde su contrato de datos. Editar, borrar, formularios completos, mapas, fotografías, cuentas, sincronización y notificaciones quedan para otros incrementos. Guardar localmente no envía información a autoridades ni solicita atención de emergencias.
