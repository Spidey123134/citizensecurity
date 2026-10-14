# Fase 31 — recuperación durable de un cambio de estado

**Implementación validada del 14 de octubre de 2026 · Unreleased.** Continúa la base de [fase 28](FASE_28.md) y los apartados **4.4 seguimiento / 4.6 Administración** del PDF. La solicitud que antes solo vivía en el modelo ahora se conserva en un diario privado para sobrevivir a una interrupción y poder comprobar su resultado. La asignación administrativa sigue pendiente por elección del usuario: esta fase no compone controles administrativos, concede roles o convierte `admin/admin` en un administrador real.

## Resultado construido

Una solicitud conserva dueño, actor, folio, operación, estado/secuencia esperados, destino, motivo y fecha exactamente como se prepararon. Si el proceso termina después de una posible escritura, recuperar consulta ese mismo intento. Recuperar no cambia otro estado, genera otro UUID o elimina el pendiente. Solo un recibo comprobado permite reconocer explícitamente el resultado.

## Contrato y almacenamiento

El dominio separa `ReportStatusChangeScope`, `ReportStatusChangeRequest`, `ReportStatusChangeReceipt`, `ReportStatusChangeJournal` y `ReportStatusChangeAccess`. El dueño puede ser nulo para el espacio académico histórico; el actor es un UUID canónico. Ese identificador vincula el cuerpo de la solicitud, sin conceder permiso administrativo. El recibo incluye folio y evento real, y debe coincidir con operación, actor, secuencia, estados y motivo completos.

El diario privado está limitado a **8 KiB** y conserva una solicitud inmutable. La lectura valida toda su representación; una escritura retiene solo sobre vacío o sobre el mismo cuerpo completo. La revisión CAS impide sustituir o limpiar otro pendiente. El almacenamiento atómico se combina con una exclusión compartida del proceso que abarca diario y operación del backend; no basta con bloquear solamente la escritura de archivo. Un archivo inválido o resultado incierto conserva el pendiente y bloquea un intento distinto.

El acceso a SQLite utiliza contexto capturado de dueño/actor y comprueba autorización en cada entrada. La consulta de recibo es de **solo lectura**: busca el evento de la operación y verifica folio/actor/cuerpo, sin llamar al escritor como forma de comprobar. El escritor protegido de fase 27 conserva CAS y evento en una transacción. La autorización, incluido el actor, se vuelve a comprobar antes del commit; la configuración predeterminada continúa denegando escritura.

## Coordinación y recuperación

El modelo durable reserva la operación y conserva la solicitud antes de pedir un cambio al backend. Cancelación, una respuesta perdida o un fallo no autorizan reemplazarla. La recuperación es explícita y lee diario/recibo; si falta recibo, el intento queda pendiente. Un reintento explícito usa la misma solicitud e identidad, sujeto nuevamente a autorización.

Un recibo guardado en memoria no autoriza por sí solo limpiar el diario. El reconocimiento explícito vuelve a consultar y verificar el recibo SQLite antes de limpiar exactamente esa solicitud. Si falla la comprobación, cambia la sesión/actor o existe otro cuerpo en el diario, se conserva el pendiente. Generaciones y estado vigente impiden que una respuesta anterior confirme o borre una operación nueva.

## Alcance y aceptación

Las comprobaciones ejecutadas cubren representación/canonización y límite; lectura inválida sin sobrescritura; retención idéntica frente a otra solicitud; CAS y exclusión entre operaciones; cambio con autorización revocada antes del commit; recepción de otra operación/folio/actor rechazada; recuperación de solo lectura; cancelación después de una posible escritura; mismo UUID al reintentar; reconocimiento explícito con reverificación y limpieza del cuerpo exacto.

La lógica JVM y las pruebas de almacenamiento/acceso SQLite en Android **API 37** aprobaron; el desglose ejecutado y recibos están en [Validación](VALIDACION.md). Las pruebas nativas nuevas se ejecutaron únicamente en ese emulador aislado. No se atribuye ejecución API 26, aceptación administrativa o una prueba física nueva; la base queda validada sin activarse como panel productivo.

El cierre compartido de fases 31/32 aprobó **1062 casos distintos, 112 nuevos**, cero fallos/errores/omitidos finales: dominio JVM 169, datos JVM 111, app JVM 717, datos Android 53 y app Android 12. Es el total del corte, no solo de esta fase; no suma cierres históricos o repeticiones. Lint app sin errores/22 avisos por variante y datos sin incidencias.

**19/28 = 67.9% permanece.** Faltan el recorrido administrativo visible con identidad/permiso acordados y su aceptación, los cuatro criterios administrativos y los cuatro de notificaciones. Android conserva **0.6.0 / código 28**; código Unreleased, sin nueva APK publicada, tag o Release. Datos, historial, esquemas y login/loading del compañero se conservan.
