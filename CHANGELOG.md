# Changelog

Registro de cambios del proyecto. Las fechas usan la hora de México. Los cambios publicados en una rama quedan disponibles para revisión; la integración en `main` se registra cuando ocurre.

## 0.4.0 — consulta local, Unreleased

Incremento autorizado el 3 de octubre de 2026, conservado desde el avance `474d07c` y actualizado con la base oficial `0.2.1`. Usa versión Android `0.4.0`, código **7**, en una rama separada. No tiene etiqueta ni release; la entrega oficial continúa siendo `v0.2.1` y fase 3 / `0.3.0` sigue en **Unreleased**.

### Añadido

- Consulta local por folio mediante `ReportQueryViewModel` y estados de solo lectura: `Idle`, `Loading`, `Found`, `NotFound`, `InvalidFolio` y `Error`. Reutiliza `ReportRepository.findById`, valida el formato UUID y evita consultas simultáneas.
- Fábrica con el repositorio compartido y propiedad `MainActivity.reportQueryViewModel`, para conectar después los controles del compañero sin acceder a SQLite desde la pantalla.
- Demostración `:core:data:consultarReporte`: comprueba una base vacía, recupera dos reportes completos después de reabrir SQLite, conserva el estado inicial y el orden del listado, y devuelve resultado inexistente para un folio ausente sin modificar registros. Exporta JSON de una base temporal que se elimina al terminar.
- [Plan del proyecto](docs/PLAN_DEL_PROYECTO.md) basado en las secciones reales del PDF y [guía de fase 4](docs/FASE_4.md), con el alcance local y los requisitos pendientes. El contrato y el esquema productivo de SQLite se conservan.

### Corregido

- El guardado podía permanecer en `Saving` al liberar el modelo antes de iniciar su coroutine. La finalización cancelada restaura `Idle` sin llamar al repositorio; la regresión se conserva.
- El modelo de guardado podía publicar `Saved`, errores por campo o un error de almacenamiento después de cancelarse si el repositorio devolvía un resultado o lanzaba una excepción tardía. Comprueba la cancelación antes de publicar esos estados.
- El modelo de consulta podía presentar un error de lectura después de cancelarse. Comprueba la cancelación antes de publicar `Error` y conserva el retorno a `Idle` de la operación cancelada.

### Comprobado

- Antes de corregir, 2 de las 24 pruebas JVM de app fallaron con las regresiones de cancelación.
- Después de corregir: 24 JVM de app aprobadas (8 de guardado, 8 de consulta y 8 de herramientas), 32 unitarias de dominio ejecutadas de nuevo y 10 instrumentadas SQLite aprobadas en Android 17 / API 37, sin fallos ni errores.
- Demostraciones `consultarReporte` y `guardarReporte` con JSON correctos; lint debug y release sin errores, datos sin incidencias y las 13 advertencias visuales previas del compañero.
- APK debug y APK release sin firma compilados. [Validación](docs/VALIDACION.md) registra la evidencia de esta rama; no se instaló su APK sobre la aplicación oficial del usuario.

No se añade una interfaz de consulta. El botón de desarrollador y `admin / admin` conservan exclusivamente las funciones oficiales de fases 1 y 2. La consulta local no identifica usuarios ni completa el requisito de reportes propios o seguimiento administrativo del PDF. La integración en `main`, la publicación, el flujo visual, la rotación real y la aceptación funcional del usuario siguen pendientes.

## 0.2.1 — Herramientas de funciones publicadas — 2026-10-03

Mantenimiento oficial de la línea `0.2`, sobre `main` actualizado. [Release v0.2.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.1). Se conserva `v0.2.0`; fase 3 / `0.3.0` y el avance `0.4.0` siguen en **Unreleased**.

### Añadido

- Botón **Desarrollador · funciones 0.2** junto al login del compañero, disponible en debug y release.
- Herramientas de recepción de datos, validación del reporte y tres ejemplos de coordenadas: punto válido, par incompleto y fuera de rango. Reutilizan `NewReport` y `ReportValidator`; no guardan ni consultan reportes.
- Acceso local de demostración **admin / admin** a las mismas herramientas. Se borra la contraseña del campo al entrar; no crea cuentas, sesiones ni privilegios administrativos.
- Guía [Funciones publicadas](docs/FUNCIONES_PUBLICADAS.md). Versión Android `0.2.1`, código **6**, para actualizar las compilaciones locales recientes conservando sus datos.

### Corregido y conservado

- Cancelación comprobada dentro del monitor antes de ejecutar SQLite, aplicación compartida y exclusión de respaldos restauradas, integradas mediante [PR #4](https://github.com/Spidey123134/citizensecurity/pull/4).
- Puente `ReportViewModel`, fábrica compartida y `MainActivity.reportViewModel`, integrados mediante [PR #5](https://github.com/Spidey123134/citizensecurity/pull/5). El futuro formulario puede observar progreso, errores por campo y folio sin acceder a SQLite.
- Splash y XML del compañero conservados. La autenticación real, el formulario de guardado y el mapa siguen pendientes.

### Comprobado

- 14 pruebas JVM de app aprobadas: 6 del puente y 8 de recepción y acceso. Las 32 de dominio conservaron su resultado aprobado mediante `UP-TO-DATE`.
- 9 pruebas instrumentadas SQLite aprobadas en Android 17 / API 37 y JSON de la demostración existente generado en bases temporales.
- Lint debug y release sin errores; 13 advertencias visuales previas del compañero. APK debug y APK release sin firma compilados. Firma v2 del APK debug verificada.
- Prueba visual del botón, recepción, validación, tres ubicaciones y acceso de demostración. Evidencia y límites en [Validación](docs/VALIDACION.md).

La publicación no sustituye la revisión funcional del usuario. Las herramientas oficiales no exponen funciones de las fases Unreleased.

## 0.3.1 — identificador provisional de pruebas, sin publicar

Durante la revisión del 3 de octubre de 2026 se usó `0.3.1` para identificar el parche y sus pruebas. No tuvo etiqueta ni release. La numeración se corrigió después a `0.2.1`, manteniendo el código y la evidencia. Este bloque registra aquella comprobación previa sobre `7e90109`, cuando aún faltaba el recurso de splash; el compañero lo añadió después en `e18e636`.

### Corregido

- Una llamada a `create` cancelada mientras esperaba el monitor podía insertar el reporte al obtener el turno. El repositorio comprueba de nuevo la cancelación dentro del monitor, antes de ejecutar la operación SQLite.
- Restauración en el manifiesto de `CitizenSecurityApplication` y de la exclusión de respaldos de la base local, conservando el tema, el icono, el launcher y los cambios visuales del compañero.

### Añadido

- Regresión de cancelación durante la espera, que reprodujo la inserción indebida antes de la corrección.
- Cobertura de diez casos de texto Unicode que se conservaron exactamente en Android; ese candidato se descartó como fallo y no se cambiaron las reglas del validador.

### Actualizado

- Identificador temporal de pruebas Android `0.3.1`, `versionCode = 4`; después se corrigió a `0.2.1`.
- Seguimiento del parche, conservando el historial sin publicar de `0.3.0` y el esquema de la base.
- Base sincronizada con la nueva `SplashActivity` y el layout visual de login del compañero. La autenticación sigue pendiente; no se modificaron sus pantallas.

### Verificado

- Antes de corregir, la regresión de cancelación falló: quedaron 2 registros cuando se esperaba conservar solo el primero. El segundo guardado se había cancelado antes de liberar el monitor.
- La comprobación Unicode pasó sus diez casos en API 37.
- Después de corregir, las 9 pruebas instrumentadas aprobaron en API 37 antes de incorporar el commit visual. Las 32 unitarias conservaron su resultado anterior; Gradle marcó la tarea como `UP-TO-DATE`. Los ejemplos y JSON de guardado y rechazo también aprobaron en esa comprobación.
- En aquel estado integrado, lint de `core/data` y `:app:processDebugMainManifest` aprobaron. `:app:assembleDebug` falló porque faltaba `activity_splash.xml` y `:app:lintDebug` se bloqueó por el mismo recurso. No se generó un APK de aquel estado ni se verificó la interfaz; el bloqueo quedó resuelto al recibir `e18e636`. [Validación](docs/VALIDACION.md) conserva el historial.

## 0.3.0 — fase 3 adelantada, Unreleased

Trabajo adelantado de guardado local, con pruebas técnicas realizadas el 2 de octubre de 2026. El código y la demostración existen, pero la entrega de fase 3 permanece pendiente: `0.3.0` sigue en `Unreleased`, sin etiqueta ni release. Su publicación sigue pendiente.

### Añadido

- Demostración `:core:data:guardarReporte`: ejecuta la instrumentación Android existente, muestra un folio UUID generado por el repositorio y comprueba la recuperación del mismo reporte y sus coordenadas después de reabrir SQLite.
- Evidencia de rechazo de un reporte inválido sin insertar datos ni alterar los registros previos. Los resultados reales se exportan a JSON bajo `core/data/build/reports/fase3/`, fuera de Git.
- Guía [Fase 3](docs/FASE_3.md) y seguimiento de la entrega `0.3.0 — Unreleased`.
- Cuatro regresiones unitarias y dos instrumentadas para los fallos encontrados.

### Corregido

- La validación aceptaba texto UTF-16 mal formado que SQLite cambiaba al guardar. Ahora lo rechaza con el error del campo correspondiente, conservando emojis y caracteres Unicode válidos.
- Las marcas `U+FEFF` y `U+FFFE` al inicio del texto podían desaparecer o alterar los caracteres al guardarlos. Ahora se rechazan después de quitar los espacios de los extremos, antes de insertar.
- Coordenadas `-0.0` producían un reporte devuelto diferente del recuperado. El guardado las normaliza a `0.0`, como SQLite, manteniendo el resto de los valores.

### Actualizado

- Versión de desarrollo Android `0.3.0`, `versionCode = 3`.
- Contrato de textos y normalización, fases y proceso de Releases. La revisión funcional de este incremento sigue pendiente.

### Verificado

- Fallos reproducidos antes del arreglo: 3 de las 32 pruebas unitarias y 2 de las 7 instrumentadas fallaron con las regresiones añadidas.
- Después del arreglo: 32 unitarias y 7 instrumentadas aprobadas, sin fallos, errores ni omisiones; instrumentación en API 37.
- Demostración de guardado, ejemplos de fase 2, lint y APK debug correctos. Lint de datos sin incidencias; `app` conserva sus dos advertencias conocidas.
- Firma del APK comprobada. [Validación](docs/VALIDACION.md) registra los comandos, el SHA-256 y los límites.

Las bases usadas por la demostración son temporales y se eliminan al terminar las pruebas. El contrato y el esquema productivo se conservan; las interfaces, el mapa y el login continúan con el compañero.

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
