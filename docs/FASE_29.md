# Fase 29 — ficha compartible del incidente

Estado: **desarrollo Unreleased posterior a 0.6.0**. Proyecto académico en México. Código Android permanece **0.6.0 / 28**; la APK oficial de esa entrega no contiene esta mejora. No se publica otra APK, tag o Release por esta fase.

## Utilidad y recorrido

La ficha reúne la información de un reporte guardado en un PDF legible para conservarla o entregarla voluntariamente a un contacto o autoridad. Evita reconstruir la descripción, fecha y lugar de memoria. La ficha parte del folio real en el espacio local de la sesión actual.

1. Abre **Inicio → Reportes guardados** y elige el folio.
2. En **Ficha del incidente**, pulsa **Preparar ficha PDF** y revisa qué incluir. La ubicación empieza marcada; la foto empieza desmarcada y solo se ofrece cuando el reporte tiene una.
3. Confirma la preparación. Se relee el reporte guardado y se genera la copia; todavía no se comparte.
4. Pulsa **Compartir PDF** para abrir el selector de Android. Elige el destinatario y completa la acción en la aplicación elegida.

El PDF incluye folio, tipo, prioridad declarada por el autor, estado local, descripción, fecha del incidente, guardado y exportación, con zona horaria. Si eliges ubicación incluye la referencia y coordenadas del incidente que ya estaban guardadas. Si eliges foto incluye la copia normalizada validada de CitizenSecurity y sus datos de integridad. Omitir ubicación o foto queda indicado en la ficha y no modifica el reporte.

## Composición y protección

La preparación captura el ReportWorkspace de la pantalla y relee el folio, sin resolver otra cuenta durante el trabajo ni obtener GPS nuevo. Comprueba sesión, generación y cancelación durante lectura, dibujo y escritura. La ficha se pagina y mantiene límites de 12 páginas y 12 MiB; una foto solicitada ausente, corrupta o incompatible causa un error. No se omite silenciosamente: puedes decidir preparar otra ficha sin foto.

La copia PDF se confirma atómicamente en **cache/evidence_shared**, con nombre aleatorio y comprobación de tamaño/hash antes de compartir. La caché de fichas admite hasta 32 archivos y 64 MiB; alcanzar el límite impide otra preparación y muestra el error, sin borrar reportes, fotos o fichas anteriores automáticamente. Android puede retirar archivos de caché, por lo que una copia ausente requiere prepararse otra vez. El modelo conserva metadatos acotados durante su vida, sin bytes, bitmaps o URI en estado restaurable. Preparar varias veces o cambiar de folio invalida el trabajo anterior. Un resultado tardío no habilita compartir otra ficha.

Compartir requiere un gesto vigente, pantalla RESUMED y folio/sesión actuales. FileProvider expone exclusivamente ese subdirectorio de caché y concede lectura de la copia elegida mediante el selector Android. No expone SQLite ni el archivo privado de la foto. Cancelar el selector no confirma envío; abrirlo tampoco demuestra que el destinatario recibió el documento. Un error conserva la ficha del reporte y permite otra decisión explícita.

## Límites del documento

Los datos son los declarados y guardados en el teléfono. La prioridad no es una clasificación verificada; el estado local no prueba atención policial. La fecha del teléfono y el hash de una copia normalizada no acreditan cuándo ocurrió el incidente, quién capturó el original o una cadena de custodia. La app no atribuye valor legal, autenticidad forense ni recepción oficial a la ficha.

Al compartir pueden salir descripción, ubicación precisa y fotografía, según lo que revisaste. La aplicación destinataria gestiona el envío y puede conservar una copia; cerrar sesión no recupera un documento que otra persona ya copió. La ficha ofrece una forma de entrega voluntaria, sin integrar policías o emergencias ni asignar permisos administrativos.

## Comprobación y avance

La comprobación técnica cubre paginación/texto Unicode, fotografía e integridad, límites, cancelación, revocación de sesión, escritura incompleta y gestos separados de preparación/selector. El PDF de prueba debe renderizarse y revisarse visualmente. Los resultados ejecutados y la evidencia se registran en [VALIDACION](VALIDACION.md), separados del cierre 0.6.0; este documento no atribuye una aceptación física o una entrega real a una autoridad.

**19/28 = 67.9% permanece**, mismos criterios del PDF original. Refuerza 4.2 Reportes y 4.4 Consulta con una extensión útil; no completa seguimiento administrativo o notificaciones. [Fase 30](FASE_30.md) añade ayuda/contactos. La futura colaboración institucional requiere institución receptora, actor/permiso y recepción verificable acordados.
