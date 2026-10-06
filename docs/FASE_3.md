# Fase 3: guardar un reporte local

El incremento activo **0.3.2 — Unreleased** reutiliza la base 0.3.1 descrita aquí y mantiene el mismo alcance visible. No activa el guardado de datos reales ni la consulta; su lógica nueva de ubicación se documenta aparte en [Fase 6](FASE_6.md).

La **fase 3 y `0.3.0` permanecen en Unreleased**, con entrega separada y aceptación funcional pendientes. Su lógica ya estaba incluida en versiones publicadas anteriores: ese marcador no la mantenía fuera del APK ni vuelve privada la fuente. La indicación actual es dejar oculto el guardado de datos reales y mostrar únicamente un ejemplo ficticio.

La copia local se prepara como **`0.3.1`**, código Android **8**, para una futura versión oficial, sin commit, subida, integración, etiqueta ni Release nueva. El `main` remoto mantiene la publicación histórica 0.4.0 / código 7. El código 8 permite actualizar ese APK conservando datos; el antiguo identificador provisional 0.3.1 / código 4 es otro estado de pruebas.

Se aprovechan `NewReport`, `ReportLocation`, `ReportRepository` y `SqliteReportRepository`, que ya existen. La tarea adelantada `:core:data:guardarReporte` hace observable el guardado de un reporte válido y el rechazo de uno inválido, reutilizando la instrumentación Android. El código y los resultados anteriores se mantienen como preparación para revisar esta fase. El esquema de la base sigue siendo el mismo.

## Único ejemplo visible en la preparación 0.3.1

El botón **Ejemplo de guardado**, dentro de las herramientas de desarrollador, muestra un guardado ficticio con folio, categoría, prioridad, fecha, descripción, referencia y coordenadas. Tipo, prioridad, descripción, referencia y coordenadas son fijos; el repositorio genera el folio y las fechas se obtienen del reloj del ejemplo. El estado inicial `REPORTED` forma parte del contrato y se comprueba en las pruebas instrumentadas. Usa una instancia independiente de SQLite en memoria (`name = null`); no crea archivos de base de datos, no toma los campos del formulario y no utiliza el repositorio productivo de `CitizenSecurityApplication`.

Mientras se ejecuta, bloquea otra solicitud. `SaveExampleViewModel.runExample()` ofrece estados `Idle`, `Running`, `Saved(report)` y `Failed`; llama a `TemporaryReportSaveExample.run()` sin recibir un borrador. La base en memoria se cierra antes de devolver el reporte; lo que permanece en el modelo es ese resultado. Lo conserva al rotar y permite volver a verlo sin insertar un segundo ejemplo hasta salir de las herramientas. Al salir se descarta el resultado de esa sesión; no se elimina ni altera la base de reportes reales.

La recepción, validación y los tres casos de coordenadas continúan disponibles. El botón de desarrollador y `admin / admin` conservan su acceso de demostración local. No aparece un botón para guardar datos del formulario ni para consultar folios; la consulta 0.4 queda oculta. Los pasos están en [Funciones visibles](FUNCIONES_PUBLICADAS.md), y la comprobación de este ejemplo se registra aparte del historial en [Validación](VALIDACION.md).

El compañero puede preparar el formulario según el contrato existente, pero debe mantener sin conectar los eventos de guardado real hasta una instrucción precisa. El ejemplo no debe cambiarse para leer el formulario ni sustituirse por el repositorio productivo.

La comprobación local actual aprobó las 6 JVM del ejemplo y sus 4 instrumentadas, incluidas en 30 JVM de app y 15 instrumentadas API 37 ejecutadas y aprobadas. Las 32 de dominio conservaron su resultado mediante `UP-TO-DATE`. La comprobación visual real aprobó independencia del formulario, conservación al rotar, mismo folio al repetir y reinicio del ejemplo al salir y volver, sin cambios en las bases productivas. [Validación](VALIDACION.md) registra aparte la evidencia completa.

## Pulido actual del guardado conservado

Se reprodujo otra cancelación indebida: después de adquirir el monitor, cancelar durante la obtención de la fecha todavía permitía insertar un registro. La regresión falló en API 37 con 1 registro cuando se esperaban 0. El repositorio comprueba la cancelación antes de iniciar la transacción y antes de confirmar su éxito; después del arreglo la regresión aprobó dentro de las 15 instrumentadas actuales. Esto no revierte una transacción que ya haya sido confirmada. El guardado real continúa oculto y el esquema se conserva.

## Resultado que se revisará

Los siguientes casos corresponden al contrato técnico conservado de guardado real; no son nuevos controles habilitados de la aplicación.

| Caso | Entrada | Resultado esperado |
| --- | --- | --- |
| Guardado válido | Datos válidos con referencia escrita | `create` devuelve un `Report` con folio UUID, fecha de guardado y estado `REPORTED`; el texto queda sin espacios de los extremos. |
| Ubicación por coordenadas | Latitud y longitud válidas, referencia vacía | El reporte conserva ambos números y se recupera por su folio después de cerrar y reabrir la base. |
| Rechazo sin inserción | Borrador con campos inválidos, incluidas coordenadas inválidas | `create` devuelve `InvalidReportException` con errores por campo; los registros anteriores permanecen y no aparece uno nuevo. |
| Folios distintos | Dos guardados válidos con los mismos datos | Cada guardado recibe un folio distinto; la interfaz confirma una sola solicitud a la vez para evitar duplicados involuntarios. |

El folio y `createdAt` los genera el repositorio. La fecha del incidente viene del borrador. La validación ocurre dentro de `create`, incluso cuando se validó antes en la fase 2. La confirmación del guardado se produce después de recibir el `Report`; un error de almacenamiento se propaga y no se presenta como éxito.

El contrato completo y la forma de obtener la instancia compartida están en [Guardar reporte](GUARDAR_REPORTE.md). La validación previa y los mensajes por campo están en [Validar reporte](VALIDAR_REPORTE.md).

## Ejecutar la demostración

Con `JAVA_HOME` configurado para JDK 27 y un emulador o teléfono disponible:

```powershell
.\gradlew.bat :core:data:guardarReporte --console=plain
```

La tarea depende de `connectedDebugAndroidTest`, por lo que aprovecha la instrumentación del mismo proceso de Gradle. Después de que las pruebas terminan correctamente, lee la evidencia de guardado y rechazo capturada en sus registros locales, muestra el resultado y exporta los JSON. No ejecuta otro Gradle dentro de Gradle.

La clase `SqliteReportRepositoryTest`, en `core/data/src/androidTest/kotlin/com/example/citizensecurity/data/`, reutiliza estos casos para revisar la fase:

| Prueba existente | Qué permite comprobar |
| --- | --- |
| `guardarYReabrirConservaReporteYNormalizaTexto` | Folio UUID, fecha de guardado, estado inicial, texto normalizado y recuperación después de reabrir. |
| `coordenadasSinReferenciaYFechaPrecisaSeConservanAlReabrir` | Conservación del par de coordenadas sin referencia y de la fecha precisa. |
| `reporteInvalidoNoModificaLosDatosGuardados` | Rechazo de un borrador inválido con errores por campo y conservación de los registros previos. |
| `listarOrdenaPorFechaDeGuardadoYConservaFoliosDistintos` | Folios distintos y orden de consulta por fecha de guardado. |

Las pruebas utilizan una base temporal independiente por caso y eliminan su archivo al terminar. No escriben reportes de demostración en la base de la aplicación. Los ejemplos de `core/domain` continúan dedicados a recepción y validación; el guardado se comprueba en Android.

Los resultados XML y los registros por prueba están en `core/data/build/outputs/androidTest-results/connected/debug/`. La tarea exporta `reporte.json` y `rechazo.json` en `core/data/build/reports/fase3/<dispositivo>/`.

`reporte.json` incluye los objetos `saved` y `restored`, `reopened = true` y el folio generado. `rechazo.json` incluye `rejected = true`, `inserted = false`, los errores por campo y el número de registros antes y después. Guardado y rechazo son casos separados: cada uno usa su propia base temporal y su propio folio; el conteo del rechazo pertenece únicamente a su caso.

Ambos JSON declaran que la base es temporal. El guardado usa un reloj fijo de prueba: las fechas del incidente y guardado son datos del caso, no la hora real de uso de la aplicación. `generatedAt` identifica la hora real de generación de la evidencia.

## Auditoría de la fase 2

La revisión reprodujo dos fallos y comprobó sus correcciones:

| Fallo reproducido | Comportamiento anterior | Corrección comprobada |
| --- | --- | --- |
| Texto Unicode no representable | Se aceptaban sustitutos UTF-16 aislados y marcas iniciales `U+FEFF`/`U+FFFE`; el texto recuperado podía diferir del que devolvió `create`. | El validador rechaza esas entradas en descripción o referencia con el mensaje de carácter no permitido. Los emojis con pares Unicode válidos continúan permitidos; `U+0000` mantiene su rechazo previo. |
| Cero negativo en coordenadas | `create` devolvía `-0.0`, pero SQLite recuperaba `0.0`, causando diferencias entre el reporte devuelto y el consultado. | El repositorio convierte solo los ceros a `0.0` antes de guardar y devolver el reporte. Las demás coordenadas conservan su valor. |

SQLite documenta que al enlazar texto UTF-16 puede retirar la marca de orden de bytes inicial o sustituir caracteres Unicode inválidos; eso explica por qué estas entradas requieren validación para conservar el texto. [Enlace de valores de SQLite](https://www.sqlite.org/c3ref/bind_blob.html).

Las regresiones fallaron antes de corregir y aprobaron después. La reproducción y los cambios se registran en [Changelog](../CHANGELOG.md) bajo `Unreleased`.

## Historial del mantenimiento identificado provisionalmente como 0.3.1

Se reprodujo un fallo del repositorio original: un segundo guardado cancelado mientras esperaba el monitor terminaba insertándose después de liberar el primer guardado. La corrección comprueba la cancelación al adquirir el monitor, antes de iniciar la acción SQLite. La suite posterior de 9 pruebas instrumentadas aprobó en API 37, junto con los ejemplos y los JSON de guardado y rechazo; la tarea de 32 unitarias reutilizó su resultado anterior mediante `UP-TO-DATE`.

La comprobación adicional de diez casos Unicode conservó los textos exactamente en Android. Se conserva como cobertura de regresión sin cambiar las reglas del validador. Este mantenimiento continúa la misma fase de guardado; no añade otra función.

Esas pruebas se ejecutaron antes de incorporar `7e90109`, que no modificó el dominio ni los datos. Su splash y login visual inicialmente quedaron sin `activity_splash.xml`, bloqueando la compilación. El compañero añadió el recurso en `e18e636`. El estado previo de mantenimiento aprobó compilación, lint y 9 instrumentadas con código Android `4`; después se integró y publicó como `0.2.1` / código 6. La autenticación permanece pendiente. [Validación](VALIDACION.md) conserva esos estados históricos, que no equivalen a activar el guardado real ni a publicar una entrega separada 0.3.0.

## Evidencia de 0.3.0 y alcance

El 2 de octubre de 2026, hora de México, se ejecutó la comprobación completa:

```powershell
.\gradlew.bat :core:domain:test :core:domain:validarReporte :core:domain:validarUbicaciones :core:data:guardarReporte :core:data:lintDebug :app:lintDebug :app:assembleDebug --console=plain
```

| Comprobación | Resultado |
| --- | --- |
| Pruebas unitarias | 32 aprobadas; 0 fallos, errores u omitidas |
| Pruebas instrumentadas en API 37 | 7 aprobadas; 0 fallos, errores u omitidas |
| Ejemplos de validación y ubicación | Ejecutados correctamente |
| `guardarReporte` | Guardado, reapertura y rechazo sin inserción comprobados; JSON exportados |
| Lint de `core/data` | 0 incidencias |
| Lint de `app` | 0 errores; 2 advertencias existentes de contenedor e icono |
| APK debug | Construido, versión `0.3.0`, `versionCode = 3` |

La ejecución produjo el folio `876baa82-27fc-445b-8277-5e56ca8ecab3` con estado `REPORTED`, latitud `19.4326077` y longitud `-99.1332088`. `saved` y `restored` contienen el mismo reporte. En el caso de rechazo quedó 1 registro antes y después, con `inserted = false`. Estos folios son evidencia de esa ejecución; cada nuevo guardado genera otro UUID.

El APK de esa ejecución `0.3.0` se generó en `app/build/outputs/apk/debug/app-debug.apk`, con `11729718` bytes y SHA-256 `5132185a350c8c17f7291c3d741442d03e5e7d335c9d64f0c77e5aa3e5a42ee9`. La misma ruta se reutiliza al construir una versión posterior; este hash identifica el artefacto anterior. No se publica como adjunto. La verificación en teléfono físico y API 26 continúa pendiente. El contexto de las comprobaciones está en [Validación](VALIDACION.md), el estado en [Fases](FASES.md) y el trabajo de la versión en [Releases](RELEASES.md).

La fase de datos conserva su alcance de recepción, validación y persistencia local del par de coordenadas, sin una nueva migración. Las pantallas, el mapa y la autenticación corresponden al compañero (`vazdavr-sudo`); sus cambios visuales de splash y login se conservan. El SDK de Google Maps, GPS y servidor siguen pendientes.
