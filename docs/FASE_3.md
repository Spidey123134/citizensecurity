# Fase 3: guardar un reporte local

Esta fase está autorizada, implementada y probada en **`0.3.0 — Unreleased`**. El resultado está listo para revisión; la aceptación funcional del usuario sigue pendiente. No se crea una etiqueta `v0.3.0` ni se publica una release de esa versión.

Se aprovechan `NewReport`, `ReportLocation`, `ReportRepository` y `SqliteReportRepository`, que ya existen. La nueva tarea `:core:data:guardarReporte` hace observable el guardado de un reporte válido y el rechazo de uno inválido, reutilizando la instrumentación Android. También se corrigieron dos fallos de conservación de los datos. El esquema de la base sigue siendo el mismo.

## Resultado que se revisará

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

## Evidencia y alcance

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

El APK local está en `app/build/outputs/apk/debug/app-debug.apk`, tiene `11729718` bytes y SHA-256 `5132185a350c8c17f7291c3d741442d03e5e7d335c9d64f0c77e5aa3e5a42ee9`. No se publica como adjunto en este incremento. La verificación en teléfono físico y API 26 continúa pendiente. El contexto de las comprobaciones está en [Validación](VALIDACION.md), el estado en [Fases](FASES.md) y el trabajo de la versión en [Releases](RELEASES.md).

La fase no incorpora interfaces, login, SDK de Google Maps, GPS, servidor ni una nueva migración. Las pantallas y el mapa corresponden al compañero (`vazdavr-sudo`). Nuestra parte recibe, valida y conserva el par de coordenadas. El guardado sigue siendo local.
