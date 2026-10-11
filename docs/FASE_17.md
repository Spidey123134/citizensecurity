# Fase 17 — guardar y recuperar un borrador local · 0.5.4

Segunda fase de la entrega 0.5.4, código Android 24. Permite conservar un formulario completo para continuar después, incluida la foto opcional de [fase 16](FASE_16.md). Sigue visible en el recorrido compartido y conserva el login/splash del compañero. La copia pertenece a este teléfono; el acceso académico no identifica cuentas ni autores reales.

## Recorrido del usuario

En **Nuevo reporte → Revisar borrador**, **Guardar borrador para continuar después** conserva los campos validados y la referencia de la fotografía. El formulario debe estar completo para llegar a esta revisión. Puede guardarse una referencia de ubicación válida sin haber confirmado un punto GPS: conservar un borrador no inserta un reporte ni solicita ubicación.

Al volver a Nuevo reporte se muestra que hay una copia disponible y su fecha. Abrir la pantalla solo lee esa copia. **Recuperar borrador guardado** pide confirmar que se sustituirán los campos que se están editando; cancelar conserva esos campos. Después de confirmar, la aplicación vuelve a leer la revisión actual. Si otra pantalla la cambió o ya le vinculó un folio, informa del resultado actualizado sin restaurar a ciegas una copia antigua.

La restauración entrega los campos una vez. Girar después de corregir no vuelve a aplicar el borrador guardado sobre las correcciones. Sus coordenadas recuperadas siguen siendo datos del formulario: guardar el reporte vuelve a exigir permiso preciso, GPS actual válido y cercanía dentro del radio de 5 km.

Una copia que ya se convirtió en reporte muestra su folio y ofrece abrirlo. Se conserva la copia con su foto y fecha, pero no se ofrece restaurarla como si todavía no se hubiera guardado el reporte.

## Identidades y respuestas inciertas

`ReportDraftCheckpointStore` conserva una copia completa en `report_draft_v1.json`, separado del diario de envío y de SQLite. Usa AtomicFile, UTF-8 estricto, tipos JSON comprobados y un límite de 16 KiB. Conserva los nanosegundos y metadatos de foto; no contiene bitmaps, URI externas, permisos ni credenciales GPS. Una lectura corrupta, incompleta o de un esquema desconocido falla conservando el archivo y no equivale a una copia vacía.

Cada guardado de borrador con cambios crea una revisión UUID nueva. Antes de reemplazar compara tanto el UUID leído como su marca de folio. Una pantalla antigua que no haya observado un folio recién confirmado no puede retirar esa marca sustituyendo el borrador. Las instancias del mismo archivo comparten bloqueo; repetir la copia exacta con los valores actuales no reescribe sus bytes.

Cuando los campos coinciden con la copia conservada, su identidad se reutiliza para `createOnce`. El diario de envío mantiene esa misma identidad antes de insertar. Si se pierde una respuesta después del commit, la recuperación lee el resultado y reintentar recupera la fila existente; no crea un folio nuevo para ese contenido pendiente. Una escritura de borrador incierta conserva también la identidad candidata y obliga a recargar antes de sustituir una copia.

Después de confirmar el reporte, la copia coincidente se marca con el folio sin modificar sus campos, foto ni fecha. Repetir la misma marca es idempotente y asignar otro folio se rechaza. Si la marca no puede confirmarse, el recibo mantiene el reporte ya guardado y solicita comprobar la copia; no declara un fracaso de la inserción ni facilita un duplicado. No se añade borrado de borradores históricos ni cambios administrativos de estado.

## Evidencia y límites de comprobación

| Clase / caso verificable | Comprobación |
| --- | --- |
| `ReportDraftCheckpointStoreTest` | Reapertura con Unicode/foto/nanosegundos; CAS, concurrencia, backup atómico, corrupción y límites sin sobrescribir; diario de envío independiente |
| `aScreenThatHasNotObservedTheFolioCannotReplaceTheMarkedCopy` | La marca de folio impide el reemplazo desde una lectura antigua; tras recargar se permite un reemplazo explícito |
| `ReportDraftCheckpointViewModelTest` | Lectura sin restauración ni escritura, resultado incierto, cancelación, confirmación con lectura nueva, respuesta antigua y evento de restauración consumido |
| `preferredIdentityUsesOnlyMatchingNormalizedCompleteFieldsAndConfirmedFolio` | La identidad reutilizada corresponde al contenido completo, incluidos fecha, ubicación y fotografía |
| `ReportPhotoUiTest.savedCheckpointRecoversPhotoWithoutGpsOrInsertAndLinksConfirmedFolio` | Recorrido visible de conservar/recuperar la copia, sin GPS ni inserción automáticos, y enlace con el folio confirmado |
| `ReportPhotoUiTest.cancelCheckpointRecoveryRetainsTheFieldsBeingEdited` | Cancelar la confirmación conserva las modificaciones del formulario |
| `ReportPhotoUiTest.staleCheckpointWriteCannotCreateAnotherFolioBeforeReloadingTheConfirmedCopy` | Una escritura antigua no facilita otro folio después de que se confirmó la misma copia |

El cierre agregado de pruebas, compilación y lint se registra en la validación de la entrega, sin contar repeticiones ni adelantar resultados pendientes. Las fixtures se limitan a `androidTest`; la aceptación física del recorrido completo con GPS sigue pendiente.

## Avance del plan

Esta fase mejora la fiabilidad del punto **4.2 Reportes** ya construido. El avance permanece en **14/28 = 50%**, alcanzado por la foto de fase 16. No se inventa un criterio del PDF para guardar borradores, publicar la versión o repetir pruebas. Siguen pendientes cuentas reales, administración, avisos y aceptación física final de geolocalización.

[Pasos para probar la APK 0.5.4](PROBAR_0_5_4.md).

Cierre de esta entrega: **752 casos distintos aprobados, 158 definiciones nuevas; compilación debug/release y lint sin errores**. Los detalles y límites de la ejecución están en [Validación 0.5.4](VALIDACION.md).
