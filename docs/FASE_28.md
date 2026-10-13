# Fase 28 — base de coordinación de cambios de estado

Estado: **base técnica incluida en 0.6.0 / código 28**. La asignación administrativa sigue pendiente y la app no compone una pantalla ni un escritor administrativo. Se conserva el historial de lectura de fase 27.

## Alcance pequeño

ReportStatusChangeViewModel coordina una solicitud revisada mediante ReportStatusRepository. Conserva folio, estado/secuencia esperados, destino, motivo y operationId como un comando completo. Reserva la tarea antes de publicar Writing, evita solicitudes simultáneas y no permite reemplazar un comando cuyo resultado todavía deba comprobarse.

Una respuesta coincidente lleva a Confirmed: verifica UUID, secuencia siguiente, clase de evento, estados, motivo y actor presente. Un recibo incompatible, excepción no concluyente o cancelación mantiene Pending y la misma identidad. **Cancelar no prueba que SQLite haya deshecho la escritura**; el escritor anterior conserva la reserva hasta terminar. Reintentar requiere gesto explícito y repite el mismo comando, sin cambiar folio, revisión o motivo.

Un rechazo inicial definitivo por permisos, revisión o folio puede descartarse explícitamente para preparar una nueva solicitud. Si hubo incertidumbre antes, un rechazo posterior no permite olvidar la identidad. Un conflicto de operationId se trata como resultado incierto. Un recibo confirmado se consume explícitamente antes de otro cambio; respuestas tardías no sustituyen el estado vigente.

## Límites antes de activarlo

El modelo retiene el comando durante su vida y rotación, **sin diario durable ni recuperación tras muerte del proceso**. No identifica por sí mismo al administrador: el servicio necesita una autorización externa que suministre y compruebe el actor. Falta una identidad administrativa durable, permiso acordado y diario de solicitud/recibo antes de conectar una pantalla de escritura.

La decisión del usuario sigue siendo **dejar pendiente la asignación**. La composición productiva no entrega este escritor, ni administra permisos o roles. admin/admin y Desarrollador son accesos académicos. No se atribuye un cambio o atención real a esta preparación interna.

## Comprobación y avance

Se añaden **12 casos JVM ejecutados y aprobados** de validación, solicitud/recibo, reintento exacto, cancelación/incertidumbre, single flight y generación, incluidos una sola vez en los **1164 del cierre agrupado** de [VALIDACION](VALIDACION.md). No hay casos nativos de un formulario administrativo ni aceptación de ese recorrido, porque aún no está conectado.

**19/28 = 67.9% permanece**, mismos criterios del PDF. Esta base refuerza el servicio de 4.4/4.6 sin acreditar seguimiento o administración. El siguiente alcance es un diario durable vinculado al actor autorizado, seguido de la composición del recorrido cuando se acuerde el permiso.
