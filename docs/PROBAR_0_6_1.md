# Probar 0.6.1 en Android

**Guía de la entrega 0.6.1 / código Android 29.** Descarga **citizensecurity-0.6.1-academico.apk** y su checksum desde [Release v0.6.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.1). Usa el APK del producto **CitizenSecurity**.

## Actualizar conservando datos

1. Antes de actualizar, anota un folio que ya tengas guardado y recuerda con qué cuenta o acceso académico lo creaste.
2. Abre la APK descargada y acepta **Actualizar**, instalándola encima de la app actual. Esta entrega conserva el paquete y la firma académica; **no desinstales ni borres datos** para actualizar.
3. Si Android informa que la firma no coincide o no permite actualizar, conserva la instalación anterior y sus archivos; revisa que descargaste el APK del producto antes de intentar otra cosa.
4. Abre la app, vuelve a ingresar si lo solicita y consulta el folio anterior con el mismo acceso. Deben conservarse sus campos, fotografía e historial existente. Una cuenta distinta mantiene su propio espacio; `admin/admin` y **Desarrollador** usan el académico, sin permisos administrativos.

La comprobación del paquete, versión creciente y firma se registra en [Validación](VALIDACION.md); no se ha ejecutado una actualización en tu teléfono. Estos pasos sirven para registrar además su aceptación física; leer la guía no acredita haberlos ejecutado.

## Tomar una foto para el reporte

1. En **Inicio → Reportar**, agrega los datos del incidente y baja a **Fotografía del incidente**. La fotografía es opcional.
2. Pulsa **Tomar foto con la cámara**. Debe abrirse la aplicación de cámara del teléfono. Toma una foto y confirma su uso allí.
3. Al volver, espera a que termine la preparación y comprueba la vista previa. La app guarda una copia JPEG en el teléfono sin metadatos de cámara. La foto debe corresponder a la que acabas de tomar; capturarla todavía no guarda el reporte.
4. Completa la ubicación y la validación GPS existente cuando vayas a guardar: lectura precisa/reciente y punto dentro de 5 km. Pulsa **Revisar borrador** y luego **Guardar reporte**. Abre el folio en **Reportes guardados** y comprueba que contiene la fotografía.
5. Para comprobar cancelación, vuelve a un borrador con una fotografía y pulsa **Tomar otra foto**, después cancela en la cámara. La imagen anterior y los campos deben permanecer. **Quitar del borrador** retira únicamente esa referencia, sin borrar reportes anteriores.

Si la cámara no abre, no devuelve archivo o la imagen está dañada/demasiado grande, debe mostrarse un error conservando el borrador y la foto anterior. La fuente admite hasta **16 MiB/64 megapíxeles** y se prepara como JPEG de hasta **2048 píxeles de lado/6 MiB**. Puedes reintentar o usar la galería. No hace falta cambiar permisos generales de archivos; CitizenSecurity delega la captura a la aplicación de cámara.

Girar mientras hay una captura viva no debe abrir otra cámara ni duplicar la fotografía. Si Android mata por completo el proceso o retiras la tarea, puede pedir que vuelvas a tomar/elegir la imagen: una captura temporal no se promete recuperable después de esa interrupción. Conserva el mensaje recibido para poder revisar ese caso.

## Comprobar que la galería sigue funcionando

1. En un borrador pulsa **Elegir de la galería** o **Cambiar desde la galería** y selecciona una imagen. Debe aparecer la copia preparada en la vista previa.
2. Vuelve a abrir el selector y cancela. Debe conservarse la fotografía anterior y el texto del borrador.
3. Mientras una selección o importación está pendiente, la app debe impedir otra captura/selección y la revisión del reporte. Al terminar debe volver a permitir continuar.

## Añadir Ayuda inmediata a Ajustes rápidos

1. Abre **Ayuda inmediata** desde Inicio o desde el atajo del icono de la app. Localiza su opción de **Ajustes rápidos**.
2. En Android 13/API 33 o posterior, solicita añadir el mosaico y decide en el diálogo del sistema. Cancelar o rechazar debe conservar Ayuda y explicar el resultado; solicitarlo no debe anunciarlo como añadido antes de recibir la respuesta.
3. Si se indica alta manual, desliza desde la parte superior para expandir Ajustes rápidos, pulsa **Editar** (a veces un lápiz), busca **Ayuda** de CITIZENSECURITY y arrástralo a los mosaicos activos. La ruta exacta depende del fabricante. Es la forma manual para Android 8–12/API 26–32 y una alternativa en versiones recientes.
4. Cierra la app y toca el mosaico. Debe abrir la pantalla pública de Ayuda, sin pedir login o GPS y sin mostrar cuentas, contactos privados o reportes.
5. Con el teléfono bloqueado, si tu sistema permite acceder a los mosaicos, toca Ayuda. Debe solicitar desbloqueo antes de abrir la pantalla; cancelarlo no debe abrir el marcador. Registra por separado si tu modelo oculta los mosaicos mientras está bloqueado.
6. Retira el mosaico desde **Editar** y comprueba que los accesos dentro de la app y desde el icono siguen funcionando. Volver a añadirlo requiere tu decisión.

**Para probar los accesos 911/089 no pulses el botón de llamar del teléfono.** Al tocar uno en Ayuda solo debe abrirse el marcador con ese número; puedes cerrarlo sin realizar ninguna llamada. Añadir/tocar el mosaico y abrir Ayuda nunca llaman automáticamente. El servicio real de emergencia no se usa como prueba.

## Registrar la prueba física

Anota modelo del teléfono, versión de Android y resultado de cámara confirmada/cancelada, galería y mosaico añadido/retirado/desbloqueo. Un mensaje o imagen inesperados deben registrarse sin exponer datos personales. Las pruebas automatizadas de captura usan un protocolo controlado con archivos reales; no sustituyen la comprobación de la cámara física o del panel de tu fabricante.

Esta entrega conserva **19/28 = 67.9%** del plan original. Aún no hay asignación administrativa, recepción oficial por policías o notificaciones de cambios de estado. Los reportes y fotografías permanecen locales; una ficha solo se comparte si tú la preparas y eliges compartirla.

[Cámara](FASE_33.md) · [Ajustes rápidos](FASE_34.md) · [Mejoras útiles](MEJORAS_UTILES.md) · [Validación](VALIDACION.md) · [Avance](AVANCE.md).
