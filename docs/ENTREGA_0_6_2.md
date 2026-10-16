# Borrador local de entrega 0.6.2

**Borrador local, sin publicar · 15 de octubre de 2026.** La publicación se pospuso por instrucción del usuario para continuar trabajando en la app. **0.6.2 / código Android 30 es una numeración tentativa** del candidato preparado; se revisará al autorizar la siguiente Release. Este documento conserva las notas y comprobaciones disponibles, sin anunciar una entrega oficial ni una descarga pública.

El candidato agrupa las fases **35–37**, añadidas después de la entrega 0.6.1: actividad de reportes, preferencias y avisos locales de cambios, y preparación de un mensaje para un contacto. Conserva el paquete, la firma académica, los datos y el login/loading existentes. [Guía local para una futura prueba](PROBAR_0_6_2.md).

## Cambios respecto a 0.6.1

- **Actividad de reportes:** desde Inicio se consultan guardados y cambios efectivamente registrados, con filtros, páginas de 50 eventos y acceso al folio. Actualizar comienza desde la primera página; no se inventa historial para los reportes antiguos.
- **Avisos voluntarios por acceso:** desde Actividad se activa la preferencia y se solicita el permiso de Android cuando corresponda. **Revisar cambios** busca cambios de estado y presenta un resumen general, sin información privada del incidente. La revisión es manual dentro de esa pantalla; no hay avisos push ni revisión en segundo plano. Guardar un reporte nuevo no se presenta como cambio posterior y no genera este aviso.
- **Mensaje para un contacto:** desde un folio guardado se elige un contacto, se decide si incluir referencia y punto —opción inicialmente desactivada— y se revisan destinatario y texto. **Abrir Mensajes SMS** abre la composición en otra aplicación; el usuario decide enviar allí. El operador puede cobrar y algunas aplicaciones pueden omitir el texto. La ficha PDF conserva su acceso separado; este mensaje no adjunta archivos.

La base de reportes pasa de **esquema 3 a 4** mediante una migración aditiva que conserva reportes, fotografías e historial y añade un índice de actividad. Las cuentas mantienen esquema 1. Las preferencias de avisos se guardan por cuenta; el acceso académico conserva su propio espacio local. Desactivar avisos no borra los identificadores de cambios ya registrados ni los reportes.

Los avisos consumen únicamente cambios de estado confirmados. **La asignación administrativa continúa pendiente y la escritura sigue denegada por defecto**; `admin/admin` conserva acceso académico. Por ello, un teléfono que solo tenga eventos de guardado puede mostrar actividad y ninguna notificación de cambios. La app no añade cambios ficticios para llenar esa lista.

Los datos siguen locales. Abrir Mensajes SMS o recibir la aceptación de una notificación por Android no demuestra envío, lectura, recepción policial ni atención. El radio GPS de **5 km** y el aviso de posibles repetidos de **200 m/±2 h** se conservan. La cámara, ficha PDF, contactos y accesos de ayuda de 0.6.1 siguen incluidos.

## Candidato interno archivado

- Archivo local: `.gradle/validacion/citizensecurity-0.6.2-academico.apk`.
- Tamaño: **55,302,267 bytes**.
- SHA-256: `ee75a653fc468153d062747bfeca5727104a236d29dcf876bacd6494162d3b2f`.
- La APK permanece como candidato interno; no hay un archivo de esta versión publicado en una Release. El checksum público se preparará para el binario definitivo cuando corresponda publicar.
- Paquete: `com.example.citizensecurity`; minSdk 26 / targetSdk 37.
- Firma v2; certificado SHA-256: `0a4006a1d2ef2ad02a0dad7c51b55c0ba5ffcd096a226eed6456326b2fed3ebd`.

Cuando se autorice probar una actualización, debe instalarse encima de la app anterior, **sin desinstalar ni borrar datos**. Si Android la rechaza, conservar la instalación y revisar el archivo; no sustituir la conservación de datos por una reinstalación. Los archivos publicados de 0.6.1 mantienen su identidad histórica. Los datos anteriores de tamaño y hash identifican únicamente este candidato; cualquier compilación posterior deberá volver a comprobarse.

## Validación y límites

La compilación y lint completados durante la preparación resultaron correctos. Se conservan recibos anteriores intactos de **1,209 pruebas JVM**: 197 de dominio, 127 de datos y 885 de app. Son resultados previos reutilizados, no una nueva ejecución completa atribuida a este borrador.

La aceptación de actualización se ejecutó en **dos etapas satisfactorias en emulador**: `seed061` preparó datos bajo la APK 0.6.1 oficial y `verify062` comprobó su conservación después de instalar este candidato encima. Se contrastaron los bytes de base de datos, contactos y fotografía antes de abrir el repositorio actualizado; después se comprobó la migración del esquema 3 al 4 y la recuperación de esos datos.

**La validación final del candidato no está cerrada.** Las 139 pruebas nativas planificadas para el binario 0.6.2 siguen pendientes de ejecución final; no se incluyen como pruebas aprobadas ni se declara un total final de la futura entrega.

El historial de validaciones de las fases está en [Validación](VALIDACION.md). Los recibos locales de las dos etapas de actualización se conservan en `.gradle/validacion/release062-upgrade-seed.log` y `.gradle/validacion/release062-upgrade-verify.log`. Las pruebas de composición SMS están diseñadas para interceptar la apertura antes de otra aplicación: **no se envían mensajes de prueba**. Los cambios de estado de los recorridos automatizados se limitan al entorno de pruebas; no conceden un rol al producto.

La prueba física anterior del punto GPS se conserva como antecedente; este borrador no atribuye otra comprobación física de GPS, cámara, SMS o recepción de avisos. Las ejecuciones en emulador no acreditan funcionamiento en todos los fabricantes, muerte completa del proceso ni entrega exactamente una vez. La aceptación física de una futura actualización se registrará por separado con la [guía de prueba](PROBAR_0_6_2.md).

**Avance del PDF: 20/28 = 71.4%.** Frente a 0.6.1 se acredita el criterio de permisos y preferencias de avisos. Quedan ocho criterios: seguimiento autorizado, administración y el recorrido productivo restante de notificaciones. El mensaje a contactos amplía utilidad sin crear criterios nuevos.

[Actividad](FASE_35.md) · [Avisos](FASE_36.md) · [Mensaje a contactos](FASE_37.md) · [Avance](AVANCE.md) · [Entrega anterior](ENTREGA_0_6_1.md)
