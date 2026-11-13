# Cambios

## 0.1.0-rev.2-beta · 13 de noviembre de 2026

Revisión de la base histórica `b4f80a8`, organizada para presentar el desarrollo inicial según el pizarrón de clase.

[Prerelease publicada y descargas verificadas](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.1.0-rev.2-beta). Tag sobre `ef6c60177a3aebd801331b61ea2a00e5410cdf45`, con APK, ZIP de código y checksum. La oficial 0.7 sigue como latest y main conserva su contenido.

- Reúne el código en `app`, con `MainActivity`, `navigation`, `screen` y `data`.
- Añade `Navigation`, `Home` y `ReportDetails` como estructura de interfaz. Las dos pantallas permanecen vacías.
- Mueve los modelos, el contrato, el validador y SQLite al paquete `data`; renombra `SqliteReportRepository` a `AdminReportes`, que implementa `ReportRepository`.
- Conserva el contrato de crear, buscar y listar, las reglas de validación y el esquema SQLite versión 1. No añade operaciones de edición o borrado.
- Traslada las pruebas existentes a `app` y añade dos pruebas instrumentadas de inicio y navegación entre los contenedores vacíos.
- Identifica la instalación como `CitizenSecurity Base beta`, con `applicationId` `com.example.citizensecurity.basebeta`, versión `0.1.0-rev.2-beta` y código `2`.
- Incluye [la estructura y el diagrama actuales](docs/ESTRUCTURA_BETA.md), junto con [la explicación histórica de 0.1.0](docs/ESTRUCTURA_INICIAL.md).
- Valida 35 casos distintos: 28 JVM, cinco de SQLite y dos de navegación en Android API 26; cero fallos finales. Compilación/ensamblados debug y release correctos, lint sin errores y firma v2 comprobada.

Los resultados de compilación, lint y pruebas de esta revisión se registran en [Validación](docs/VALIDACION.md). La rama y la prerelease son independientes de la versión oficial 0.7.
