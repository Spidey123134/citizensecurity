# Borrador local para probar la próxima actualización

**Guía local, sin publicar · 15 de octubre de 2026.** La Release se pospuso para continuar trabajando en la app. La numeración **0.6.2 / código Android 30 es tentativa** y corresponde al candidato interno guardado en `.gradle/validacion/citizensecurity-0.6.2-academico.apk`; no hay descarga pública de esa versión. Esta guía conserva los pasos para una futura prueba cuando se autorice la entrega o instalación.

El candidato archivado mide **55,302,267 bytes** y su SHA-256 es `ee75a653fc468153d062747bfeca5727104a236d29dcf876bacd6494162d3b2f`. Ese hash solo identifica el archivo interno preparado; deberá revisarse si cambia el binario. Usa únicamente la APK del producto CitizenSecurity que se indique en la futura entrega.

## Actualizar conservando lo anterior

1. Antes de actualizar, anota un folio que ya tengas y recuerda con qué cuenta o acceso académico lo guardaste.
2. Abre la APK y acepta **Actualizar** encima de la instalación actual. **No desinstales ni borres datos.** Si Android rechaza la firma o la actualización, conserva la app anterior y revisa qué APK descargaste.
3. Abre la app, ingresa si lo solicita y consulta el folio anterior desde el mismo acceso. Comprueba campos, fotografía e historial que ya existían. Una cuenta distinta mantiene su propio espacio; `admin/admin` y Desarrollador usan el académico sin permisos administrativos.

La guía describe una aceptación física futura; no acredita una actualización en tu teléfono. Durante la preparación se aprobaron dos etapas de actualización en emulador, de 0.6.1 al candidato 0.6.2, y se conservaron los datos de prueba. La validación nativa final del candidato sigue pendiente. El estado y sus límites están en el [borrador de entrega](ENTREGA_0_6_2.md); [Validación](VALIDACION.md) conserva el historial de las fases.

## Consultar la actividad real

1. En Inicio, abre **Actividad de reportes**. **Toda la actividad** muestra guardados y cambios que estén registrados en este acceso.
2. Abre una entrada con **Abrir reporte** y comprueba que corresponde a su folio. Un guardado se identifica como **Guardado en este teléfono**: no confirma envío o recepción institucional.
3. Prueba **Cambios de estado**. Si no existen cambios, debe mostrar el resultado vacío correspondiente. La asignación administrativa sigue pendiente; no necesitas un cambio ficticio ni permisos especiales para comprobar esta pantalla.
4. Si hay más de 50 eventos, usa **Eventos anteriores** y **Página anterior**. **Actualizar desde el inicio** regresa a la primera página y comprueba cambios nuevos. Los reportes antiguos sin eventos conservan sus datos aunque no aparezca una creación inventada en esta lista.

## Activar y comprobar los avisos

1. Desde Actividad, pulsa **Avisos de cambios de estado**. Entrar debe consultar el estado sin pedir permiso o publicar un aviso automáticamente. La preferencia empieza desactivada para un acceso nuevo.
2. Pulsa **Activar avisos**. En Android 13 o posterior puede aparecer el permiso del sistema. Si lo deniegas, la preferencia debe permanecer desactivada. Puedes usar **Ajustes de avisos de Android** para revisar un bloqueo; al regresar se comprueba su estado actual.
3. Con la preferencia activada y Android permitido, pulsa **Revisar cambios**. Solo los cambios de estado confirmados pueden producir el resumen general. Un reporte recién guardado, por sí solo, no produce un aviso de cambio. Si no hay cambios, el resultado vacío es correcto.
4. Si aparece **Seguir revisando cambios**, úsalo para continuar. Cada gesto revisa un tramo limitado; el resultado de un tramo no significa que se recorrió todo el historial.
5. Si hay un aviso, comprueba que su texto es general y no contiene folio, descripción, contacto, foto o ubicación. Al tocarlo debe abrirse la entrada normal de la app. Repetir la revisión no debe acumular avisos para las mismas operaciones registradas.
6. Pulsa **Desactivar avisos**. Debe retirar el resumen propio conservando los reportes y el registro de operaciones ya comprobadas. La preferencia corresponde al acceso actual; cambiar de cuenta no debe mostrar sus datos en la siguiente.

La revisión se realiza **manualmente desde esta pantalla**, sin push o revisión en segundo plano. Android puede ocultar o bloquear un aviso. La aceptación de su solicitud no acredita que una persona lo vio, ni recepción de un reporte por una autoridad. En el producto sigue pendiente la asignación de quien pueda cambiar estados; no se habilita desde `admin/admin`.

## Preparar un mensaje para una persona

1. En **Reportes guardados**, abre un folio y pulsa **Preparar mensaje para un contacto**.
2. Elige un contacto de ese mismo acceso. Si no tienes uno, abre **Administrar contactos de confianza**, agrega un contacto propio y vuelve; la app debe leer la lista actualizada sin elegir destinatario automáticamente.
3. Revisa la opción de incluir **referencia y punto guardados**: comienza desmarcada y no obtiene un GPS nuevo. Aunque esté desmarcada, la descripción puede contener lugares o datos que tú escribiste; revisa todo el texto antes de compartirlo.
4. Pulsa **Revisar mensaje** y comprueba nombre, número, folio y texto completo. Si cambió el contacto o el reporte, debe pedir preparar de nuevo en lugar de usar una revisión antigua.
5. Pulsa **Abrir Mensajes SMS**. Comprueba destinatario y contenido en la aplicación que se abra. **Para comprobar solo la composición, vuelve sin pulsar Enviar.** El operador puede cobrar el envío; algunas aplicaciones pueden omitir el texto y tendrás que revisarlo allí.
6. Regresa o gira el teléfono: no debe abrirse otra composición automáticamente. Si falta una aplicación compatible, el mensaje de error debe permitir volver a decidir sin perder el reporte.

Abrir la composición no envía desde CitizenSecurity ni acredita recepción. Esta opción no adjunta fotografía o PDF; **Preparar ficha PDF** sigue disponible por separado en el detalle del reporte.

## Registrar el resultado

Anota modelo, versión de Android, conservación del folio anterior, actividad visible, decisión del permiso y resultado de abrir/cancelar la composición. Indica si solo comprobaste el texto o si decidiste enviar un mensaje por tu cuenta; no lo registres como envío o recepción verificados cuando solo se abrió Mensajes. Si aparece un error, conserva su texto sin exponer datos personales.

El avance original es **20/28 = 71.4%**. La asignación administrativa, el seguimiento autorizado y parte del recorrido productivo de avisos siguen pendientes. Para cámara, galería, PDF, contactos y accesos de ayuda conservados, también puedes seguir la [guía 0.6.1](PROBAR_0_6_1.md).

[Borrador de entrega y candidato interno](ENTREGA_0_6_2.md) · [Validación](VALIDACION.md) · [Avance](AVANCE.md)
