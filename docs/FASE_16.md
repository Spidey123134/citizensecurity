# Fase 16 — fotografía opcional del reporte · 0.5.4

Esta fase forma parte de la entrega 0.5.4, código Android 24. La fotografía se incorpora al recorrido visible de **Desarrollador → Nuevo reporte · guardado local**, junto con el formulario, la revisión y la consulta por folio. Se mantiene el acceso y splash del compañero. La publicación oficial y su APK corresponden a [v0.5.4](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.4).

## Comportamiento disponible

El formulario permite **Elegir fotografía**, ver la copia preparada, cambiarla o retirarla del borrador por gesto. Es opcional: los reportes anteriores y los nuevos reportes sin foto siguen funcionando. Cancelar el selector mantiene la selección anterior y los campos escritos. No se añade captura de cámara ni se pide acceso general a toda la galería.

La aplicación copia el contenido seleccionado a su almacenamiento privado. El reporte conserva una referencia a esa copia, no la URI de la galería. La revisión y el detalle del folio muestran la fotografía guardada; el archivo externo original puede dejar de estar disponible sin invalidar una copia privada ya completa. Cambiar la selección crea una identidad de foto nueva, sin sustituir el archivo de un reporte anterior.

La importación reconoce imágenes JPEG, PNG y WebP completas. Limita la entrada a 16 MiB y 64 megapíxeles antes de reservar el bitmap. Normaliza la orientación y prepara un JPEG de hasta 2048 píxeles por lado y 6 MiB; el fondo transparente se convierte en blanco. La nueva codificación elimina metadatos EXIF de la imagen original, incluidos GPS y cámara. El hash SHA-256, tamaño y dimensiones identifican los bytes preparados.

Una imagen incompleta, dañada, no reconocida o fuera de los límites produce un mensaje sin sustituir una foto anterior. Antes de insertar se verifica nuevamente la copia privada. Si falla esa comprobación, se conserva el formulario y el usuario puede seleccionar otra foto o retirar expresamente la selección del borrador. Si una foto de un reporte ya guardado deja de poder leerse, el detalle mantiene el folio y los demás campos y explica el problema; no inventa una imagen ni elimina el reporte.

## Continuidad de los datos

SQLite pasa del esquema 1 al 2 mediante una migración que añade columnas opcionales para la foto. Conserva filas, folios, campos e índice de la versión anterior; no sustituye ni vacía la tabla. Una versión futura o una migración no soportada se rechazan conservando la base. Las restricciones exigen metadatos completos y válidos, o una foto ausente por completo.

Los diarios de envío de 0.5.3 que no contenían el campo de foto siguen recuperando su identidad original. En los intentos nuevos, los metadatos de la foto forman parte del contenido inmutable del envío. Reintentar con el mismo UUID recupera el mismo folio; modificar o quitar la foto de un resultado incierto no transforma ese intento en otro reporte.

Los archivos de foto se guardan por escritura atómica privada. Esta entrega no añade una función de borrado de fotos históricas, limpieza automática de reportes ni sincronización con un servidor.

## Evidencia y límites de comprobación

Las regresiones verificables están en estas clases:

| Clase | Casos que comprueba |
| --- | --- |
| `ReportPhotoStoreTest` | Normalización, todas las orientaciones EXIF, eliminación de metadatos, transparencias, límites, corrupción, archivos truncados y conservación de copias anteriores |
| `SqliteReportPhotoTest` | Migración real desde esquema 1, conservación de campos e índice, reintentos idempotentes, conflictos de foto y rechazo de migraciones parciales/futuras |
| `ReportSubmissionPhotoTest` | Compatibilidad del diario anterior, metadatos y nanosegundos exactos, foto inmutable en un resultado incierto |
| `ReportPhotoUiTest` | Selector Android real, giro/cancelación, guardado y consulta con foto, desaparición del origen externo, fallo de copia y detalle conservado |
| `ReportPhotoImportViewModelTest` / `ReportDraftPhotoViewModelTest` | Cancelación, respuestas antiguas, coordinación con la selección y conservación de campos |

El cierre de resultados se registra en la validación de la entrega; esta fase no usa cifras provisionales como evidencia de aprobación. El decodificador alternativo para Android 26 se comprueba forzando esa ruta en Android 37. Esa comprobación no equivale a haber ejecutado físicamente un dispositivo Android 26.

Las pruebas de interfaz emplean permisos Android, SQLite, archivos privados y selector reales, con una fuente de ubicación controlada exclusivamente en `androidTest`. El recorrido final con GPS físico del teléfono continúa pendiente de aceptación. Las imágenes y coordenadas de las pruebas no son pruebas de un incidente real ni de autenticación.

## Lugar en el plan original

La fotografía completa el cuarto criterio de **4.2 Reportes**. La construcción acreditada del mismo plan queda en **14/28 = 50%**, sin modificar pesos ni añadir puntos por publicación. [Fase 17](FASE_17.md) refuerza la continuidad del borrador; no suma un criterio adicional del PDF. Cuentas verificadas, aislamiento por autor, administración y avisos siguen pendientes. Los reportes continúan locales, sin envío a autoridades ni atención de emergencias.

[Pasos para probar la APK 0.5.4](PROBAR_0_5_4.md).

Cierre de esta entrega: **752 casos distintos aprobados, 158 definiciones nuevas; compilación debug/release y lint sin errores**. Los detalles y límites de la ejecución están en [Validación 0.5.4](VALIDACION.md).
