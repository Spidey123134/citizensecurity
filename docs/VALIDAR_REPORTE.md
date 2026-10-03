# Paso 2: validar el reporte y sus coordenadas

El usuario autorizó trabajar este paso el 2 de octubre de 2026. Se reutiliza `ReportValidator`, que ya existe en `core/domain`, para comprobar los datos antes de guardar. La aprobación de comenzar esta fase y la aceptación de sus resultados son estados distintos: la aceptación funcional sigue pendiente.

El ejemplo recibe los mismos datos del [paso 1](RECIBIR_REPORTE.md) y permite añadir un par de coordenadas. El contrato no depende de una pantalla ni de Google Maps; `ReportLocation` y SQLite ya admiten latitud y longitud.

## Reglas que se revisan

| Campo | Regla | Error que recibe la interfaz |
| --- | --- | --- |
| Descripción | Entre 10 y 1000 puntos Unicode, después de quitar espacios de los extremos; sin `U+0000` | `DESCRIPTION` |
| Referencia escrita | Entre 5 y 200 puntos Unicode cuando se proporciona; sin `U+0000` | `LOCATION_REFERENCE` |
| Ubicación | Referencia escrita o par de coordenadas completo | `LOCATION_REFERENCE` si no hay ninguna ubicación |
| Coordenadas | Latitud y longitud juntas, números finitos; latitud entre −90 y 90 y longitud entre −180 y 180, incluidos los límites | `LOCATION_COORDINATES` |
| Fecha del incidente | Desde `1970-01-01T00:00:00Z`; como máximo un minuto después del reloj de validación | `OCCURRED_AT` |

Una referencia válida no permite pasar coordenadas incompletas o fuera de rango. Un par válido permite dejar la referencia vacía; si se escribe una referencia, también debe cumplir sus reglas. Validar no modifica ni guarda el borrador. La normalización y una nueva validación ocurren dentro de `ReportRepository.create` al guardar.

En el desarrollo `0.3.0 — Unreleased`, descripción y referencia también rechazan UTF-16 mal formado y `U+FEFF`/`U+FFFE` al inicio del texto después de quitar espacios. Devuelven el mismo error por campo que los caracteres no permitidos. Los emojis bien codificados y `U+FEFF` en el interior siguen permitidos. La auditoría reprodujo en Android que esas entradas iniciales cambiaban al guardarse, según el comportamiento de [enlace de textos en SQLite](https://www.sqlite.org/c3ref/bind_blob.html).

Las cuatro regresiones nuevas del dominio están aprobadas, junto con las 28 anteriores: 32 pruebas. La instrumentación añade regresiones para texto y cero negativo: 7 pruebas aprobadas. La evidencia antes y después del arreglo está en [Validación](VALIDACION.md). La release `v0.2.0` conserva su código original; estas correcciones pertenecen al incremento sin publicar.

La revisión de mantenimiento `0.2.1` mantiene estas reglas. Una comprobación adicional en Android confirmó que diez casos de texto aceptado, incluidos `U+FFFE` interno y `U+FFFF`, se conservan exactamente al consultar y reabrir; el candidato a otro fallo Unicode quedó descartado. La suite de persistencia pasa a 9 pruebas, incluyendo la corrección de cancelación durante la espera descrita en [Contrato de guardado](GUARDAR_REPORTE.md). Los resultados y la integración con la pantalla de carga del compañero se registran en [Validación](VALIDACION.md). El identificador provisional `0.3.1` no llegó a publicarse.

## Ejemplo de consola

Con `JAVA_HOME` configurado para JDK 27:

```powershell
.\gradlew.bat :core:domain:validarReporte --console=plain
```

Las propiedades `tipo`, `descripcion`, `prioridad`, `fecha` y `ubicacion` conservan el formato del paso anterior. `latitud` y `longitud` son opcionales; una propiedad omitida o vacía representa una coordenada ausente. Los números usan punto decimal. Tipo y prioridad admiten minúsculas; la fecha se convierte con `Instant.parse`. Si la fecha se omite o queda en blanco, el ejemplo captura una sola vez el momento actual en UTC con `Clock` y usa ese mismo instante para validar.

Un reporte con coordenadas y sin referencia escrita:

```powershell
.\gradlew.bat :core:domain:validarReporte --console=plain `
    '-Ptipo=RISK' `
    '-Pdescripcion=Hay una luminaria dañada frente al parque.' `
    '-Pprioridad=MEDIUM' `
    '-Pfecha=2026-10-01T18:00:00Z' `
    '-Pubicacion=' `
    '-Platitud=19.4326' `
    '-Plongitud=-99.1332'
```

El resultado esperado es `Reporte válido.`. Para revisar un par incompleto, ejecuta el mismo comando quitando `-Plongitud`; para revisar un valor fuera de rango, cambia la latitud a `91`. Ambos deben mostrar un error de coordenadas.

Si un tipo, prioridad, fecha o número no puede convertirse, el ejemplo muestra `Datos de entrada con errores:` con un mensaje en español junto al campo y no construye un borrador incompleto. Si puede construirlo, ejecuta `ReportValidator` y muestra `Reporte válido.` o `Reporte con errores:` con los mensajes correspondientes.

El comando termina normalmente aunque muestre errores de entrada o validación: es un ejemplo para revisar resultados, no una prueba que falle Gradle. `BUILD SUCCESSFUL` confirma que se ejecutó el ejemplo; no implica que el reporte sea válido. Hay que comprobar el mensaje mostrado. El ejemplo queda en `src/examples`, separado del código del APK, y no crea folios ni guarda reportes.

## Tres casos de ubicación

```powershell
.\gradlew.bat :core:domain:validarUbicaciones --console=plain
```

Este ejemplo utiliza un reloj fijo en `2026-10-02T23:30:00Z` y la misma fecha de incidente para que el paso del tiempo no altere el resultado de los tres casos:

| Caso | Entrada | Resultado esperado |
| --- | --- | --- |
| Ubicación válida | Par de coordenadas válido, referencia vacía | Reporte válido |
| Par incompleto | Latitud presente, longitud ausente | Error de coordenadas |
| Fuera de rango | Par completo con latitud `91` | Error de coordenadas |

Ningún caso guarda datos. La permanencia de las coordenadas al cerrar y reabrir SQLite se comprueba con las pruebas instrumentadas de `core/data`, según [Validación](VALIDACION.md).

## Conexión futura con Google Maps

La siguiente línea de trabajo acordada es elegir la ubicación con Google Maps. Nuestra parte recibe, valida y conserva el par de coordenadas; el compañero (`vazdavr-sudo`) desarrolla la vista del mapa, el pin, la confirmación de ubicación y el login. El contrato que conectará ambas áreas ya está preparado:

```kotlin
val ubicacion = ReportLocation(
    reference = referenciaEscrita,
    latitude = latitudSeleccionada,
    longitude = longitudSeleccionada,
)
val borrador = NewReport(
    type = tipo,
    description = descripcion,
    priority = prioridad,
    occurredAt = fechaDelIncidente,
    location = ubicacion,
)
val resultado = ReportValidator().validate(borrador, Instant.now())
val errorDeCoordenadas = resultado.errors[ReportField.LOCATION_COORDINATES]
```

Los nombres de las variables representan valores obtenidos por la interfaz. La interfaz muestra los errores junto a sus campos y llama al repositorio compartido después de que el usuario confirme. El repositorio vuelve a validar y solo confirma el guardado al devolver un `Report`.

Google Maps entrega un par `LatLng` al tocar un punto, según los [eventos del SDK Android](https://developers.google.com/maps/documentation/android-sdk/events). La conexión del SDK, el proyecto de Google, la clave y la configuración de facturación siguen pendientes; los requisitos están en [uso y facturación de Google Maps](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing). Esta entrega prepara los datos del reporte; todavía no implementa un mapa real ni captura GPS.

## Evidencia de esta entrega

El 2 de octubre de 2026 se ejecutaron estas comprobaciones de la fase 2:

- `recibirReporte` conservó su salida de recepción.
- `validarReporte` aceptó el reporte predeterminado y un reporte personalizado con tipo y prioridad en minúsculas, referencia vacía y coordenadas válidas.
- `validarUbicaciones` mostró los tres resultados previstos: válido, par incompleto y fuera de rango.
- Entradas con tipo y prioridad inexistentes, fecha no convertible, latitud con coma y longitud no numérica mostraron cinco errores de entrada en español, sin traza de excepción.
- Un borrador con descripción y referencia cortas, fecha fuera del límite y latitud `NaN` mostró errores en los cuatro campos del validador.
- Las 28 pruebas unitarias del dominio se ejecutaron de nuevo con `--rerun-tasks` y aprobaron.
- La inspección de `domain.jar` confirmó que las clases de los ejemplos no se incluyen en el artefacto productivo.

También se ejecutó la comprobación Android de la versión `0.2.0`:

```powershell
.\gradlew.bat :core:data:lintDebug :app:lintDebug :app:assembleDebug :core:data:connectedDebugAndroidTest --console=plain
```

| Comprobación | Resultado de esta entrega |
| --- | --- |
| Pruebas unitarias del dominio | 28 aprobadas; 0 fallos, errores u omitidas |
| Pruebas instrumentadas de SQLite | 5 aprobadas; 0 fallos, errores u omitidas |
| Lint de `core/data` | 0 incidencias |
| Lint de `app` | 0 errores; 2 advertencias existentes: `MergeRootFrame` y `MissingApplicationIcon` |
| APK debug | Generado correctamente, versión `0.2.0`, `versionCode = 2` |
| Firma del APK | Verificada con `apksigner`, esquema v2 |

Las advertencias corresponden al contenedor vacío y al icono pendiente de la interfaz. Se mantienen visibles. El contexto de las pruebas instrumentadas y los límites de dispositivo de la base se documentan en [Validación](VALIDACION.md); esta entrega no registra una nueva comprobación en teléfono físico o API 26.

El APK local está en `app/build/outputs/apk/debug/app-debug.apk`, tiene `11729718` bytes y SHA-256 `c7c56d5b8b7234af95c2424c313f774cff12eb4b257c8c89db8523d22b53a1a0`. Su comprobación local no significa que se publique como adjunto; la release prevista de esta versión incluye código fuente y notas, según [Releases](RELEASES.md).

La fase está implementada y probada. La aceptación funcional del usuario sigue pendiente.
