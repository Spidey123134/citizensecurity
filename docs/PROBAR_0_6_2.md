# Probar CitizenSecurity 0.6.2

**16 de octubre de 2026 · Android 0.6.2 / código 30 · Release oficial publicada.** Esta guía recorre **todas las fases 35–42**, incluidas las que quedaron Unreleased. Descarga la [APK final citizensecurity-0.6.2-academico.apk](https://github.com/Spidey123134/citizensecurity/releases/download/v0.6.2/citizensecurity-0.6.2-academico.apk) y el [checksum SHA-256](https://github.com/Spidey123134/citizensecurity/releases/download/v0.6.2/SHA256SUMS-0.6.2.txt) de la [Release 0.6.2](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.2), ya publicados y verificados. El candidato interno antiguo de solo 35–37 conserva otro hash y no es esta entrega.

APK final: **56,200,431 bytes**, SHA-256 `114323c0a97e9adc07fa3f2f7996d6054df65b982f1d0bf19699dacd11d836a4`. [Cambios, identidad y límites](ENTREGA_0_6_2.md).

## Actualizar conservando lo anterior

1. Anota un folio que ya tengas y recuerda con qué cuenta o acceso académico lo guardaste.
2. Abre la APK y acepta **Actualizar** encima de la instalación actual. **No desinstales ni borres datos.** Si Android rechaza firma o actualización, conserva la app anterior y revisa el archivo.
3. Abre la app, ingresa si lo solicita y consulta el folio anterior desde el mismo acceso. Comprueba campos, fotografía e historial previos. Otra cuenta mantiene su propio espacio; admin/admin y Desarrollador usan el académico sin permisos administrativos.

Esta guía permite registrar una aceptación física nueva; no afirma que ya se haya ejecutado en tu teléfono. La actualización de la oficial 0.6.1 al binario final 0.6.2 aprobó sus dos etapas en emulador propio API 37, conservando datos/foto/contactos antes de migrar y verificándolos después. Esa evidencia se registra por separado en [Validación](VALIDACION.md).

## Revisar el borrador y su fotografía

1. Desde Inicio abre **Reportar** y prepara un reporte con información de prueba que no exponga a otras personas.
2. Adjunta una foto vertical desde galería o cámara y abre **Revisar borrador**.
3. Comprueba datos, lugar y fotografía separados, imagen completa ajustada a su marco y texto/acciones sin solapamientos. Desplázate hasta el final; puedes probar también un tamaño de letra mayor.
4. Volver a editar conserva campos y foto. Revisar por sí solo no guarda; el guardado requiere su acción y los controles vigentes de ubicación/posibles repetidos.

Este paso comprueba legibilidad. Para foto, PDF, contactos y accesos de ayuda conservados puedes seguir también la [guía 0.6.1](PROBAR_0_6_1.md).

## Consultar la actividad real

1. En Inicio abre **Actividad de reportes**. **Toda la actividad** muestra guardados y cambios registrados en este acceso.
2. Pulsa **Abrir reporte** y comprueba su folio. **Guardado en este teléfono** identifica una creación local, sin confirmar envío institucional.
3. Selecciona **Cambios de estado**. Si aún no hiciste cambios autorizados, el resultado vacío es correcto; no debe inventarlos.
4. Con más de 50 eventos usa **Eventos anteriores** y **Página anterior**. **Actualizar desde el inicio** vuelve al primer tramo. Los reportes antiguos sin eventos conservan sus datos aunque no aparezca una creación inventada.

## Activar la administración de tu cuenta local

La asignación administra **solo el espacio de admin_local en ese teléfono**, no otras cuentas o instalaciones. Las contraseñas no se sincronizan. [Guía completa](ADMINISTRADOR_LOCAL.md).

1. En el login abre **Crear cuenta**, registra **admin_local** con contraseña propia de **15–128 caracteres** e ingresa. Si ya existe, usa su contraseña actual. No hay contraseña administrativa predeterminada; admin/admin conserva su acceso académico.
2. En Inicio abre **Administración local**. En la primera asignación pulsa **Confirmar propietario con Android** y completa PIN, patrón o contraseña en la pantalla del sistema. Cancelar no activa permisos.
3. Al regresar pulsa **Activar administración local** dentro de los dos minutos desde que iniciaste la confirmación. Volver de Android no basta. Una asignación comprobada no requiere repetir el alta para cada cambio.
4. Abre **Consultar reportes** y comprueba filtros tipo/estado/prioridad, actualización, páginas y folio completo. Solo verás reportes de esta cuenta. Para probar un cambio necesitas un reporte guardado en ese mismo espacio.

Si falta bloqueo seguro, configúralo tú desde los ajustes antes de pedir la confirmación. No publiques contraseña ni PIN. Ante un archivo incierto/corrupto, conserva el mensaje para revisión; no borres datos ni reinstales para superar el error.

## Cambiar un estado y comprobar su historial

1. En la consulta administrativa abre un folio y pulsa **Preparar cambio de estado**.
2. Pulsa **Comprobar solicitudes pendientes**. Es una lectura; si existe una solicitud previa, resuélvela antes de preparar otra.
3. Con diario vacío comprobado pulsa **Consultar estado actual**. Comprueba folio, descripción, estado y revisión.
4. Escribe un motivo de **5–500 caracteres válidos**, sin controles ni espacios exteriores, y pulsa **Revisar cambio antes de confirmar**. Lee motivo y transición: todavía no se guarda.
5. Pulsa **Confirmar y guardar cambio local**. Comprueba recibo y evento del historial con actor, fecha, motivo, secuencia y operación. Solo avanza un paso: **Reportado → En revisión → Atendido → Cerrado**. Cerrado no ofrece otro avance.
6. Abre **Ver actividad de esta cuenta** desde el recibo y comprueba su cambio. Volver o navegar no reconoce la solicitud. Usa **Entendido: cerrar esta solicitud** para reconocerla y poder preparar otra.

Ante interrupción, **Recuperar resultado de esta solicitud** busca el mismo recibo sin repetir el cambio. Si ofrece **Reintentar la misma solicitud**, conserva su UUID/cuerpo. No prepares otra solicitud para resolver la incertidumbre anterior. Una revisión cambiada exige consultar y revisar de nuevo. Atendido/Cerrado organizan datos locales y no confirman intervención oficial.

## Activar y comprobar los avisos del cambio

1. Desde Actividad, o desde el recibo con **Revisar avisos de cambios**, abre Avisos. Entrar no debe pedir permiso, reconocer el recibo o publicar automáticamente.
2. Pulsa **Activar avisos**. Android 13 o posterior puede pedir permiso; denegarlo mantiene la preferencia desactivada. **Ajustes de avisos de Android** permite revisar un bloqueo; al volver se comprueba su estado actual.
3. Con preferencia y Android permitidos pulsa **Revisar cambios**. Solo las transiciones confirmadas producen el resumen general. Una creación nueva no es un cambio para avisar; sin transiciones, vacío es correcto.
4. Si ofrece **Seguir revisando cambios**, continúa explícitamente. Cada gesto recorre hasta 500 eventos en páginas de 50; no significa que ya examinó todo el historial.
5. Comprueba un aviso general, sin cuenta, folio, descripción, contacto, motivo, foto o ubicación. Tocarlo abre la entrada normal. Otra revisión no debe avisar de operaciones registradas como aceptadas.
6. **Desactivar avisos** retira el resumen y conserva reportes/IDs. Cambia de cuenta: no deben aparecer datos o preferencias privadas de la anterior.

La revisión es manual desde esta pantalla; no hay push ni revisión en segundo plano. Android puede bloquear u ocultar un aviso y aceptar su solicitud no prueba lectura. Reconocer el recibo administrativo y revisar avisos son decisiones separadas.

## Preparar un mensaje para una persona

1. Abre un folio en **Reportes guardados** y pulsa **Preparar mensaje para un contacto**.
2. Elige un contacto del mismo acceso. Si falta, abre **Administrar contactos de confianza**, guarda uno propio y vuelve: debe releer la lista sin elegir destinatario automáticamente.
3. Incluir **referencia y punto guardados** comienza desmarcado y no obtiene GPS nuevo. Revisa también descripción: puede contener datos que escribiste aunque no incluyas ubicación.
4. Pulsa **Revisar mensaje** y comprueba nombre, teléfono, folio y texto completo. Un contacto/reporte cambiado exige otra preparación.
5. Pulsa **Abrir Mensajes SMS** y revisa contenido/destinatario en la aplicación que se abra. **Para probar solo la composición, vuelve sin pulsar Enviar.** El operador puede cobrar y algunas aplicaciones pueden omitir el texto.
6. Regresa o gira: no debe abrir otra composición automáticamente. Sin aplicación compatible, debe explicar el error y permitir otra decisión.

CitizenSecurity no envía automáticamente ni acredita recepción. Este mensaje no adjunta foto/PDF; **Preparar ficha PDF** conserva su recorrido independiente.

## Registrar el resultado

Anota modelo, Android, folio anterior conservado, lectura de foto, cuenta, activación administrativa, transición/recibo/historial, actividad, permiso de avisos y apertura/cancelación SMS. No expongas datos personales. Distingue abrir de enviar/leer y aceptar el punto GPS de guardar un reporte con folio.

**28/28 = 100% de construcción funcional local** del plan original. La guía registra aceptación física adicional, separada de seguridad de producción, integración institucional y pruebas de todos los teléfonos.

[Entrega y checksum](ENTREGA_0_6_2.md) · [Administración local](ADMINISTRADOR_LOCAL.md) · [Validación](VALIDACION.md) · [Avance](AVANCE.md)
