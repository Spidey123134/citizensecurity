# Fase 41 — cambio de estado e historial administrativo local

**15 de octubre de 2026 · desarrollo local Unreleased, validado.** Continúa **4.4 Consulta** y **4.6 Administración** del PDF. Utiliza la cuenta registrada `admin_local` y su asignación por UUID de fase 40; no crea cuentas o contraseñas automáticamente ni cambia el login/loading del compañero.

## Recorrido construido

Desde **Administración local → Consultar reportes**, abre un folio y pulsa **Preparar cambio de estado**. También existe **Comprobar cambio pendiente** para resolver una solicitud anterior sin necesitar otro folio. Entrar a la pantalla no consulta el diario ni modifica reportes automáticamente: primero se pulsa **Comprobar solicitudes pendientes**.

La recuperación lee el diario y busca el recibo de esa operación; no vuelve a aplicar el cambio. Una solicitud sin resolver bloquea la preparación de otra. Una lectura vacía comprobada permite consultar el folio actual. El folio del Intent es solo una sugerencia de búsqueda, sin motivo, revisión, actor o permiso de escritura.

1. **Consultar estado actual** lee el reporte y su última revisión real. Muestra descripción, estado, revisión y único avance consecutivo disponible.
2. Escribe un motivo de **5 a 500 caracteres válidos**, sin controles ni espacios exteriores. **Revisar cambio antes de confirmar** relee reporte y revisión y muestra el motivo completo; aún no crea una solicitud durable ni escribe un cambio.
3. **Confirmar y guardar cambio local** vuelve a comprobar revisión, sesión, permisos y primer plano. Solo este segundo gesto entrega al coordinador durable el cuerpo revisado.
4. El recibo confirmado muestra folio, transición, motivo, **UUID de la cuenta que registró el cambio**, fecha con zona, secuencia y UUID de operación. **Entendido: cerrar esta solicitud** relee solicitud y recibo exactos antes de cerrar el diario; el evento permanece en el historial.

| Estado actual local | Único avance permitido |
|---|---|
| Reportado (`REPORTED`) | En revisión (`IN_REVIEW`) |
| En revisión (`IN_REVIEW`) | Atendido (`ATTENDED`) |
| Atendido (`ATTENDED`) | Cerrado (`CLOSED`) |
| Cerrado (`CLOSED`) | Ninguno |

Son etiquetas de organización de esta cuenta y este teléfono. **Atendido o Cerrado no confirman que una policía o institución haya recibido, revisado o atendido el incidente.** La aplicación sigue siendo un proyecto académico local; no transmite el reporte a una central.

## Revisión, permisos y recuperación

La preparación lee páginas de historial de un evento y vuelve a comprobar reporte/secuencia para evitar presentar lecturas concurrentes como una revisión coherente. Si cambió el estado o la ficha, muestra los datos nuevos y exige otra revisión. Un reporte antiguo sin eventos conserva su revisión cero; no se inventa un evento inicial. Cancelar o volver a editar invalida la revisión anterior y sus gestos pendientes. Una lectura no cooperativa que termina después de cancelar no autoriza nuevas lecturas ni una publicación tardía.

`READ_REPORTS` y `CHANGE_STATUS` se comprueban en IO sobre el binding de dueño, actor, fuentes y sesión capturados. El escritor también revalida autorización y pantalla vigente dentro de la transacción antes del commit. El permiso no se obtiene del folio, nombre de usuario o diario guardado. Su retirada, una asignación ilegible o una sesión terminada cierran la vista y eliminan los datos privados, también si se detectan durante una escritura. No se muestra la solicitud privada como Pendiente a una cuenta que perdió acceso. Este cierre conserva el diario y la reserva de un trabajo cuyo resultado aún no se conoce; no borra la solicitud durable ni demuestra rollback. `admin/admin` mantiene su acceso académico sin estos permisos.

La ruta conserva una referencia a la ventana activa durante el giro. Una pausa o gesto de la Activity anterior no autoriza la ventana nueva; el último gesto y la escritura exigen primer plano y dispositivo desbloqueado. Girar conserva el ViewModel, sin reenviar una confirmación o recuperación. Esa retención no sustituye el diario tras terminar el proceso.

Cada solicitud durable conserva **UUID de operación, folio, dueño/actor, revisión, estados, motivo y fecha solicitada**. Un resultado incierto exige recuperar su recibo. **Reintentar la misma solicitud** reutiliza ese cuerpo completo y UUID; recuperar, reintentar y reconocer son gestos distintos. No se acepta otra solicitud para resolver la incertidumbre de la anterior.

Un conflicto de revisión o folio ausente puede producir un rechazo definitivo. **Cerrar solicitud rechazada** comprueba la misma solicitud y vuelve a buscar su recibo: si apareció uno coincidente, muestra **confirmado** en lugar de descartarlo. Una ausencia de recibo tras reconstruir el proceso no acredita por sí sola un rechazo definitivo. Corrupción, cuerpos distintos o recibos incompatibles conservan el error y los archivos; no se convierten en un diario vacío ni autorizan un reinicio.

El almacén del diario rechaza `.new` o `.bak` pendientes antes de permitir que AtomicFile restaure o reemplace archivos. Conserva esos residuos como evidencia de un estado incierto; leer no restaura una copia de respaldo, limpia parciales ni crea un diario vacío. Las pruebas anteriores que aceptaban restaurar respaldos se adaptaron a esta política de conservación.

## Relación con el PDF

**25/28 = 89.3% de construcción**, con los mismos 28 criterios originales. El recorrido integrado acredita **seguimiento de estados de 4.4 (4/4)** y los dos criterios restantes de **4.6 Administración (4/4): cambio de estado e historial**. Los cinco requisitos restantes conservan sus cifras: 4.1, 4.2, 4.3 y 4.5 en 4/4; 4.7 en 1/4. Las pruebas, líneas de código y cantidad de fases no añaden criterios al denominador.

El siguiente alcance es **4.7 Notificaciones**: cerrar los tres criterios pendientes de eventos, entrega y recorrido completo utilizando cambios reales autorizados. La preferencia, el permiso y el consumidor manual de fase 36 se conservan; no se atribuyen una notificación nueva, trabajo en segundo plano o recepción institucional a esta pantalla.

## Validación y límites

**1488 casos distintos aprobados, 103 definiciones nuevas**, sin fallos, errores u omitidos finales. Son **1379 JVM** —205 recibos intactos de dominio de fase 40 reutilizados, 148 datos y 1026 app ejecutados sobre la fuente final— y **109 Android API 37** —72 datos y 37 app—. Los 75 JVM nuevos corresponden a recuperación (25), preparación (33) y binding (17); los 28 Android nuevos a diario (11), binding escritor (9) y recorrido visible (8). Los 37 casos distintos de app son la unión de un primer pase de 35 y el pase final de 17 tras el ajuste de privacidad: 15 coinciden y no se suman dos veces. Se conservan 20 regresiones de rutas no alteradas por ese ajuste. Repetir o ampliar la ejecución de un caso existente no lo convierte en una novedad.

Los 72 Android de datos se desglosan en diario (30), acceso SQL de cambios (21), historial SQL (13) y acceso administrativo (8). Los 37 de app incluyen recorrido nuevo (8), binding escritor (9) y regresiones de alta/consulta con PIN nativo real (5), Inicio (8), aislamiento (4) y binding lector (3). La confirmación del propietario vuelve a comprobarse como regresión, sin contarla como una novedad de fase 41. Lint app termina con **cero errores y 22 avisos existentes por variante**; datos sin incidencias.

El recorrido nativo comprobó los tres avances consecutivos y el bloqueo de otro avance desde Cerrado, recuperación de una solicitud con el mismo UUID, lectura de un cambio ya confirmado sin crear otra transición, ventana nueva y giro. Utilizó rol local, SQLite y diario reales en fixtures aislados. Las pruebas del diario comprobaron también la conservación de `.new` y `.bak`; los dos casos antiguos de restauración se ajustaron al rechazo sin restaurar. El PIN de los casos de alta se retiró, se restauró el ajuste previo de bloqueo y el emulador se cerró. No se modificó un teléfono físico.

La APK interna de QA final tiene SHA-256 `54ef3afd3a2faa7dc22e6e44f094800ead219ef52e0705701e77a02a6d512523`; es evidencia interna, no una nueva descarga oficial.

Los recibos del emulador no acreditan instalación o activación en el teléfono físico, ejecución API 26, muerte completa del proceso o colaboración institucional. El detalle final se registra en [Validación](VALIDACION.md).

## Publicación

**Este corte es local:** no actualiza GitHub, main, el [PR borrador #30 de fase 40](https://github.com/Spidey123134/citizensecurity/pull/30), una APK, tag o Release. La descarga oficial **0.6.1** conserva sus funciones y archivo. El candidato 0.6.2 archivado es anterior a las fases 38–41 y no contiene este trabajo; necesitará prepararse otra vez cuando se autorice la próxima entrega.

[Guía para el equipo](ADMINISTRADOR_LOCAL.md) · [Fase 40](FASE_40.md) · [Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Fases](FASES.md)
