# Changelog

Registro de cambios del proyecto. Las fechas usan la hora de México. Los cambios publicados en una rama quedan disponibles para revisión; la integración en `main` se registra cuando ocurre.

## 0.2.0 — 2026-10-02 — Validación de reportes y ubicación

### Añadido

- Ejemplo `:core:domain:validarReporte` para revisar descripción, fecha y ubicación con el validador existente. Recibe una referencia escrita o latitud y longitud opcionales.
- Mensajes por campo y errores de entrada legibles para categorías, prioridades, fechas y coordenadas que no puedan convertirse.
- Ejemplo `:core:domain:validarUbicaciones` con tres casos reproducibles: punto completo sin referencia escrita, par incompleto y punto fuera de rango.
- Guía [Validar reporte](docs/VALIDAR_REPORTE.md) y proceso [Publicar una versión](docs/RELEASES.md). Las entregas tendrán una etiqueta y notas de cambios en GitHub Releases.

### Actualizado

- Versión Android `0.2.0`, con `versionCode = 2`.
- Plan y reparto de la nueva línea de Google Maps: nuestra parte recibe, valida y conserva coordenadas; el compañero desarrolla la vista del mapa, el marcador y sus controles.
- La fase actual pasa a validar el reporte y la ubicación. El usuario aprobó comenzar este incremento; la revisión de sus resultados sigue pendiente.

La selección de un punto en un mapa real y la configuración de Google Cloud se conectarán en un incremento posterior. Los ejemplos de esta versión reutilizan las reglas existentes y permanecen fuera del APK. La persistencia de coordenadas ya disponible conserva su contrato y su esquema.

### Verificado

- Recepción anterior, validación predeterminada y personalizada, tres casos de coordenadas, errores de conversión y errores por campo ejecutados correctamente.
- 28 pruebas unitarias de dominio y 5 pruebas instrumentadas de persistencia aprobadas en esta entrega.
- Lint sin errores: `core/data` sin incidencias y `app` con las dos advertencias existentes de su contenedor e icono.
- APK debug `0.2.0` / código `2` construido y firma comprobada; los ejemplos siguen fuera del JAR productivo.

El detalle de los comandos, resultados y artefacto local está en [Validación](docs/VALIDACION.md). La entrega de esta versión incluye código fuente y notas; la interfaz y el mapa siguen pendientes.

Integrado en `main` mediante [PR #2](https://github.com/Spidey123134/citizensecurity/pull/2), commit `aa29990`. [Release v0.2.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.0) publicada el 2 de octubre de 2026, hora de México; la etiqueta corresponde a ese commit y el código de la versión se puede descargar desde la release. La aceptación funcional de este incremento sigue pendiente.

## 2026-10-02 — Recepción de datos y seguimiento del trabajo

### Añadido

- Ejemplo ejecutable `:core:domain:recibirReporte` para recibir tipo, descripción, prioridad, fecha y ubicación escrita usando el modelo `NewReport` existente.
- Parámetros de consola para cambiar los cinco datos; el ejemplo queda separado del código incluido en el APK.
- Guía [Recibir reporte](docs/RECIBIR_REPORTE.md), con la conversión de fecha en milisegundos y referencia de ubicación del proyecto anterior.
- Plan [Fases de trabajo](docs/FASES.md), con responsables, estados y criterios de revisión.
- Guía [Inicio del equipo](docs/INICIO_EQUIPO.md), con clonación, configuración local, áreas de trabajo y publicación de ramas.
- Este changelog y enlaces de seguimiento desde el README.

### Aclarado

- El compañero `vazdavr-sudo` se encarga de las interfaces, navegación y login. Nuestra parte continúa en la lógica y los datos del reporte.
- Se comprobó que `vazdavr-sudo` tiene acceso de escritura al repositorio compartido.
- La recepción se revisa como primer paso. Validación, folio, guardado y consulta ya tienen una base técnica, y se revisarán uno por uno con el usuario.

### Verificado

- Ejemplo ejecutado con valores predeterminados y personalizados.
- 28 pruebas unitarias existentes del dominio aprobadas.
- Lint y generación del APK correctos; las dos advertencias existentes de la interfaz inicial están documentadas en [Validación](docs/VALIDACION.md).
- El artefacto `domain.jar` contiene solo el código productivo del dominio; el ejemplo permanece fuera del APK.

Entrega integrada en `main` el 2 de octubre de 2026 mediante [PR #1](https://github.com/Spidey123134/citizensecurity/pull/1), con el ejemplo de recepción, changelog, fases y guía del equipo. El commit de integración es `328977d`. La revisión funcional del usuario sobre este paso sigue pendiente.

## 2026-10-02 — Base inicial 0.1.0

### Añadido

- Estructura Kotlin separada en `app`, `core/domain` y `core/data`.
- Actividad inicial con contenedor vacío para el desarrollo de las interfaces.
- Modelos de incidente, prioridad, ubicación y reporte; contrato independiente de Android.
- Validador de textos, ubicación y fecha, con errores por campo.
- Persistencia local SQLite: guardar en transacción, generar folio UUID, buscar por folio y listar por fecha de creación.
- Base nueva de reportes y migraciones que fallan conservando los datos cuando falta un paso explícito.
- Configuración con JDK 27, Gradle 9.8.0, AGP 9.4.1, Kotlin 2.4.20 y SDK 37.0. Los detalles y límites de compatibilidad están en [Validación](docs/VALIDACION.md).
- Contrato de guardado, instrucciones del equipo y documentación de la verificación local.

### Verificado

- 28 pruebas unitarias del validador y 5 pruebas instrumentadas de persistencia aprobadas.
- Compilación del APK y lint sin errores.
- Reapertura de la base, conservación de coordenadas y fechas, rechazo sin inserción, orden y cierre idempotente.

La base inicial está integrada en `main` mediante el commit `b4f80a8`. La versión `0.1.0` identifica la configuración inicial de la aplicación; todavía no se ha publicado una release con etiqueta.

El proyecto anterior y su respaldo se conservaron en la PC como referencia, fuera de este repositorio. El progreso visual y el login continúan pendientes del compañero.

## Cómo registrar el siguiente cambio

Añade una entrada con fecha, comportamiento concreto y comprobaciones realmente ejecutadas. Distingue entre código implementado, pruebas técnicas e incremento revisado por el usuario. Actualiza la fase correspondiente al entregar o revisar una función.
