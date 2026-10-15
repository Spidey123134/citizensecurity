# Fase 37 — mensaje revisado para un contacto

**15 de octubre de 2026 · código Unreleased.** Concreta E06 de [Mejoras útiles](MEJORAS_UTILES.md): preparar información de un reporte guardado para una persona elegida. Android conserva **0.6.1 / código 29**; este avance no publica otra APK, tag o Release.

## Recorrido funcional

1. Abrir un folio en Reportes guardados y pulsar **Preparar mensaje para un contacto**.
2. Elegir un contacto del mismo acceso. Si falta, **Administrar contactos de confianza** abre Ayuda; al volver se lee la lista actualizada, sin elegir un destinatario automáticamente.
3. Decidir si se incluye **referencia y punto guardados**. La opción empieza desmarcada; no se obtiene un GPS nuevo. La descripción se comparte completa y puede contener lugares o datos escritos por el autor, por lo que también debe revisarse.
4. Pulsar **Revisar mensaje** y comprobar destinatario, número y texto. Incluye folio local, tipo, prioridad indicada, estado local, fecha con zona y descripción. La ubicación elegida incluye referencia y coordenadas válidas, y un enlace de OpenStreetMap cuando hay punto.
5. **Abrir Mensajes SMS** vuelve a comprobar reporte/contactos y abre la composición. El usuario revisa o edita en la aplicación destinataria y decide enviar. Volver o girar no abre otra composición automáticamente.

Abrir la aplicación no demuestra envío, recepción o atención. La revisión avisa que algunas aplicaciones pueden ignorar el texto y que el operador puede cobrar el envío, especialmente con un mensaje largo. No se promete mensajería gratuita ni compatibilidad idéntica entre aplicaciones.

La ficha PDF conserva el recorrido separado del detalle, con inclusión voluntaria de fotografía; este mensaje no adjunta archivos. No hay envío automático, mensajes en segundo plano, selección de una cuenta nueva o recepción policial.

## Conservación y vigencia

La pantalla privada recibe únicamente el folio y el sello del espacio. Captura reporte/repositorio, contactos y sesión; nunca busca el dueño dinámicamente. Ayuda y composición reutilizan la misma fábrica de contactos, manteniendo los archivos por cuenta y el espacio académico histórico. No cambia el esquema de reportes **4**, cuentas **1** o contactos, ni migra/borra datos.

Preparar relee ambos repositorios. Un contacto eliminado o modificado exige elegir otra vez; no se adopta silenciosamente otro número. Antes de abrir se comparan reporte, destinatario y revisión/lista de contactos con lo mostrado: si cambiaron, hace falta preparar y revisar de nuevo. Errores o corrupción exigen recargar, sin sustituirlos por una lista vacía.

La acción reserva una sola apertura del estado exacto. La última comprobación de sesión y Activity RESUMED ocurre después de la lectura y antes de abrir. Cancelar, salir durante esa comprobación, cambiar de cuenta o recibir una respuesta anterior no autoriza compartir. El modelo conserva elección/revisión al girar; un proceso nuevo relee desde el folio, sin restaurar cuerpo, teléfono o consentimiento de apertura desde Bundle/SavedState.

El formateador valida UUID, Unicode, límites de descripción/referencia, fechas representables y pares de coordenadas finitos/en rango, incluso si la ubicación se omite. No trunca una descripción para ocultar un error. Conserva fecha, nanosegundos, offset y zona; no inventa otra fecha o cambia el reporte. El cuerpo completo está acotado a 3500 unidades UTF-16.

El Intent externo usa `ACTION_SENDTO`, `Uri.fromParts("smsto", teléfonoNormalizado, null)` y únicamente `sms_body`. No introduce texto libre en la URI, destinatarios adicionales, extras del workspace, fotografías, permisos de archivos o una aplicación forzada. Sigue el [contrato de composición de Android](https://developer.android.com/guide/components/intents-common#Messaging); el enlace conserva las coordenadas legibles junto al [formato URL documentado por OpenStreetMap](https://wiki.openstreetmap.org/wiki/Browsing#Other_URL_tricks).

## Relación con el plan y validación

**20/28 = 71.4% permanece.** E06 amplía utilidad y no redefine los siete requisitos originales del PDF. Asignación administrativa, seguimiento autorizado y los demás criterios de avisos continúan pendientes; `admin/admin` mantiene acceso académico.

**1231 casos distintos aprobados, 37 nuevos:** 1209 JVM y 22 Android (8 nuevos y 14 regresiones). Cero fallos, errores u omitidos; lint app cero errores/22 avisos por variante, datos sin incidencias. Dominio/modelo cubren texto completo y exclusiones, fechas/Unicode, selección y revisión vigentes, doble gesto, cambios durante la lectura, giro, aplicación ausente, revocación, cancelación, corrupción y respuestas tardías. Android usa SQLite y contactos privados aislados, con aperturas SMS interceptadas antes de otra aplicación. [Validación](VALIDACION.md) registra los pases y sus límites; no se atribuye un SMS enviado, prueba física o aceptación de un operador.

[Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Fases](FASES.md) · [Contactos](FASE_30.md) · [Entrega oficial 0.6.1](ENTREGA_0_6_1.md)
