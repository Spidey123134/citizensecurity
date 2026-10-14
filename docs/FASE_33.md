# Fase 33 — Tomar una fotografía para el reporte

**Implementación validada del 14 de octubre de 2026 · Unreleased.** El usuario pidió usar la cámara al adjuntar una fotografía, además de elegirla desde la galería. Corresponde a **4.2 Reportes** del PDF y a **E10** de [Mejoras útiles](MEJORAS_UTILES.md): amplía las opciones de entrada de la fotografía que ya estaba implementada. El criterio original de fotografía ya estaba completo; **19/28 = 67.9% permanece**. Asignación administrativa y los nueve criterios originales pendientes conservan su alcance en [Avance](AVANCE.md).

## Recorrido funcional

En **Reportar → Fotografía del incidente**, el usuario puede elegir **Elegir de la galería** o **Tomar foto con la cámara**. Cuando el borrador ya tiene fotografía, los accesos permiten cambiarla desde la galería o tomar otra foto. Se abre la aplicación de cámara del teléfono únicamente por ese gesto. Tomar una fotografía no guarda el reporte: al volver se muestra la copia preparada en el borrador, antes de **Revisar borrador** y del guardado existente.

Cancelar la cámara, no disponer de una aplicación receptora o recibir un archivo vacío/dañado conserva la fotografía anterior y los campos del borrador. El error permite reintentar o elegir la galería. **Quitar del borrador** solo retira su referencia actual; no elimina fotografías privadas o reportes guardados. Galería y cámara comparten una selección en curso: durante la captura/importación no se inicia otro origen ni se revisa o guarda un borrador con fotografía pendiente.

Se utiliza [ActivityResultContracts.TakePicture](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.TakePicture), que entrega a la cámara una URI de destino para la imagen completa. El resultado indica si se guardó allí; la app comprueba e importa ese archivo mediante su flujo de fotografías. No usa la miniatura o una URI alternativa devuelta en los extras. Android permite delegar esta captura básica a otra aplicación mediante una intención. [Guía oficial de camera intents](https://developer.android.com/media/camera/camera-intents).

## Foto preparada y conservación

El importador pertenece al **ReportWorkspace capturado**, con comprobación de sesión antes y después de la lectura. Reutiliza `ReportPhotoStore`, sin duplicar otro almacén de fotografías ni cambiar esquemas o datos anteriores. Admite una fuente íntegra de hasta **16 MiB y 64 megapíxeles**, normaliza orientación y crea un JPEG privado de hasta **2048 píxeles de lado y 6 MiB**, sin conservar EXIF u otros metadatos del archivo externo. Una captura que exceda esos límites se rechaza; el límite temporal de caché no aumenta el límite de importación.

La nueva foto recibe una identidad propia y solo sustituye la referencia del borrador cuando la importación terminó correctamente y la selección sigue vigente. Una lectura tardía, cancelada o de una sesión revocada no puede aplicar la fotografía a otro borrador/cuenta. La copia preparada mantiene la comprobación de tamaño, dimensiones y huella del flujo existente. No se presenta como conservación del archivo original de cámara.

## Temporal y acceso de la cámara

`ReportCameraCaptureStore` crea un archivo vacío **UUID.jpg** bajo `cache/report_camera/`, fuera del hilo de interfaz. Los nombres y URI no incluyen cuenta, propietario o folio. `ReportCameraFileProvider`, con autoridad del paquete terminada en `.camera_capture`, está sin exportar y declara exclusivamente esa carpeta de caché. No expone fotos privadas, bases de datos, fichas PDF o la caché general. La intención de cámara lleva la URI de salida, `ClipData` y permisos temporales de lectura/escritura para ese archivo.

La limpieza valida el UUID y la URI exactos, revoca **READ/WRITE** y elimina únicamente el temporal de esa captura. Rechaza escapes y symlinks; no recorre fotos/reportes históricos ni barre residuos desconocidos. Las instancias del almacén comparten exclusión y comprueban antes de crear un máximo de **cuatro archivos y 64 MiB existentes**. La cámara externa puede escribir después de esa comprobación; la cuota no promete limitar sus bytes durante la captura. Android puede retirar archivos de caché y una captura demasiado grande o ausente falla al importar.

No se añaden permisos **CAMERA**, de almacenamiento o de lectura general de imágenes; la aplicación de cámara realiza la captura. Tampoco se declara una cámara física obligatoria. Si la cámara no puede abrirse, la alternativa de galería sigue disponible. No se obtiene GPS nuevo, realiza llamadas o comparte la imagen automáticamente por este recorrido.

## Selección, giro e interrupción

La selección tiene un token del borrador y una única captura vigente. El modelo conserva **Waiting** durante una recreación de la Activity dentro del mismo proceso: girar no vuelve a abrir la cámara ni crea otro temporal. El resultado se consume una sola vez y se verifica contra ese origen/token, con exclusión entre cámara y galería. Preparar, lanzar y aplicar vuelven a comprobar que el gesto y el ReportWorkspace continúen vigentes.

La URI y el token de cámara **no se guardan en SavedStateHandle, Bundle o checkpoint**. Si el proceso desaparece, un callback sin la captura viva correspondiente se rechaza y se pide volver a tomar/elegir la fotografía. Esto no promete recuperar una captura después de la muerte completa del proceso, cierre forzado o retirada de la tarea. Los temporales residuales se conservan hasta que el sistema gestione su caché; no se borra un archivo desconocido para liberar cuota.

Al revocarse la sesión o cerrarse el modelo se descarta el resultado y se intenta retirar únicamente su temporal. Una falla de limpieza no elimina la foto preparada, anterior o el reporte; se informa cuando corresponde. Las pruebas separan esa reconstrucción del modelo de una aceptación con la cámara física.

## Comprobación y límites del corte

**794 casos distintos aprobados en este corte, 71 nuevos**, cero fallos/errores/omitidos finales: 766 JVM de app y 28 Android (15 almacén/provider, 7 cámara y 6 regresiones de galería). Los nuevos son 35 del modelo de cámara, 14 del vínculo de galería y 22 nativos. Kotlin debug/release, ensamblado interno y lint aprobados: cero errores y 22 avisos por variante de app. Desglose y recibos en [Validación](VALIDACION.md); dominio/datos anteriores no se presentan como ejecución nueva.

Las pruebas nativas del recorrido interceptan el protocolo externo de cámara y escriben imágenes controladas en la URI real para comprobar importación, cancelación, archivo vacío/dañado, giro y ausencia de receptor. No prueban la óptica, enfoque, aplicación de cámara o hardware de un teléfono físico. La aceptación física queda separada de esas comprobaciones. No se atribuye a recrear una Activity la muerte completa del proceso Android.

Código **Unreleased**, Android **0.6.0 / código 28**. Actualizar código en GitHub continúa autorizado; esta fase no publica otra APK, tag o Release y conserva login/loading del compañero.
