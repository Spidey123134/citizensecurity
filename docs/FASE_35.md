# Fase 35 — centro de actividad de reportes

**Implementación validada del 14 de octubre de 2026 · Unreleased.** Se retoman Consulta y Notificaciones del PDF después de publicar 0.6.1. Esta fase permite consultar actividad real desde el espacio de reportes vigente. Compilación, JVM, almacenamiento y recorrido Android API 37 aprobados; la aceptación física del usuario se registra por separado.

Android conserva **0.6.1 / código 29** durante la preparación. La APK oficial anterior contiene las fases 29–34. Publicar otra APK, versión, tag o Release requiere su instrucción correspondiente; actualizar el código validado en GitHub mantiene la autorización existente.

## Relación con el PDF y el porcentaje

El apartado **4.4 Consulta** pide visualizar los reportes y sus estados. El **4.7 Notificaciones** se refiere a notificaciones relacionadas con cambios de estado. El centro prepara un consumidor visible de los eventos reales, conservando esa definición.

Un evento `CREATED` acredita que el reporte se guardó en este teléfono con estado inicial Reportado; no representa una transición posterior, recepción institucional o atención. Un evento `STATUS_CHANGED` conserva estado anterior, siguiente, secuencia, actor y motivo registrados por el servicio autorizado.

La matriz mantiene **19/28 = 67.9%** y los nueve criterios pendientes. El consumidor de actividad no completa por sí solo la entrega de notificaciones, permisos/preferencias o el recorrido administrativo. La asignación administrativa sigue pendiente por decisión del usuario; `admin/admin` conserva acceso académico y la escritura de estados continúa denegada por defecto. No se suman hitos por añadir otra pantalla, una migración o más pruebas.

## Recorrido definido

Desde el inicio del espacio local se abre **Actividad de reportes**. Ofrece dos filtros:

- **Toda la actividad:** creaciones y cambios que estén efectivamente registrados.
- **Cambios de estado:** únicamente eventos `STATUS_CHANGED`. No transforma creaciones en cambios para llenar la lista.

Cada entrada identifica el folio, la fecha registrada y lo que ocurrió. La creación se presenta como guardado local. Un cambio distingue el estado anterior del estado después de ese evento; no debe confundirlo con un estado posterior del reporte. Abrir el folio usa el mismo espacio capturado y permite consultar su ficha e historial.

Un espacio sin eventos presenta un resultado vacío real. Los reportes antiguos sin historial registrado se conservan sin inventar su creación, autoría o transiciones. Un fallo de consulta muestra una recuperación explícita y no se convierte en ausencia de actividad.

La lectura se pagina en SQLite; no carga todos los reportes ni consulta cada historial individual para fabricar una lista global. Cambiar el filtro o actualizar reinicia desde la primera página. Continuar solicita la página siguiente del mismo filtro y cursor; los resultados anteriores no se acumulan sin límite.

## Datos y límites de la consulta

La fuente es `report_status_events`, vinculada al folio de `reports`. `createOnce` ya confirma la fila y su evento `CREATED` dentro de una transacción; recuperar o reintentar el mismo folio no crea otro evento. El servicio de cambios confirma su CAS y `STATUS_CHANGED` juntos, con autorización revisada antes del commit.

El alcance incluye un índice aditivo y una migración de reportes a **esquema 4**, preservando filas, fotos y eventos anteriores. La base de cuentas mantiene esquema 1. La recuperación por recibo debe aceptar bases de reportes 3 y 4 por lectura, sin reconstruir o degradar datos; comprobarlo forma parte del cierre.

El cursor global usa fecha registrada con segundos/nanosegundos y `operationId` para resolver empates de forma determinista. Ese orden describe las fechas guardadas; el reloj del teléfono puede retroceder. La **secuencia por folio** sigue describiendo el orden confirmado de sus cambios. No se presenta el orden global de fechas como una autoridad que sustituya esa secuencia.

Las páginas y el recorrido en memoria están acotados. Una página pertenece al filtro y solicitud que la produjeron. Cancelación, otra consulta, salir del centro o una sesión revocada deben impedir que su resultado habilite accesos antiguos. La navegación se comprueba contra el folio presente en la página vigente.

## Sesión y acciones

La pantalla retiene un `ReportWorkspace` fijo al girar. El lector revisa la sesión y cancelación antes y después de cada consulta; no busca dinámicamente otra cuenta para continuar una pantalla anterior. Cambiar de cuenta retira sus datos y accesos.

Consultar actividad no solicita GPS, permisos, captura de cámara ni cambios de estado. Esta fase no asigna un administrador, activa un escritor, conecta un servidor o envía avisos de Android. Tampoco incorpora estados de leído/no leído: esa persistencia y las preferencias de entrega necesitan su propio alcance.

## Comprobaciones ejecutadas

- Creación real y su evento juntos; reintento del mismo folio sin duplicar actividad.
- Filtro exclusivo de cambios, orden de empates/nanosegundos y cursor sin omitir ni repetir filas.
- Migraciones desde los esquemas admitidos, conservación de reportes/fotos/eventos y compatibilidad de los recibos de fase 31.
- Páginas acotadas, cambio de filtros, actualización, recuperación de errores y respuestas anteriores descartadas.
- Giro, salida, cuenta revocada y navegación únicamente desde la página y espacio vigentes.
- Recorrido Android con datos aislados y eventos confirmados por el repositorio. Los cambios creados mediante autorización controlada de prueba no se presentan como una asignación administrativa productiva.

**1224 casos distintos aprobados, 66 definiciones nuevas**: 1123 JVM (179 dominio, 119 datos y 825 app) y 101 Android (88 datos y 13 app). Cero fallos/errores/omitidos finales. Kotlin debug/release y ensamblado interno para pruebas aprobados; lint app cero errores/22 avisos por variante, datos sin incidencias. Desglose, recibos y límites en [Validación](VALIDACION.md); no se suman repeticiones o cortes anteriores.

La prueba nativa de actualización encontró que una lectura rápida podía saltarse el estado Loading y conservar el desplazamiento al pie de la lista. La pantalla compara la identidad de consulta al recibir Loading o Ready; actualizar una consulta con valores iguales muestra ahora el evento nuevo arriba. La recreación conserva la página/filtro y el nuevo caso confirma el comportamiento. Los doce nativos de actividad comprueban migración, rollback, fotos/eventos, recibos 3/4 sin escrituras, cursores y aislamiento. Los cinco de UI y ocho de Inicio pasaron sobre el código corregido.

Emulador propio de solo lectura API 37. La creación de cambios en pruebas utiliza el escritor SQLite real con un actor autorizado únicamente por el harness; no concede ese permiso al producto ni acredita recepción institucional, aviso Android o un guardado GPS físico nuevo. No se instaló/borró nada en el teléfono del usuario. Próximo alcance: entrega de avisos Android con permisos y preferencias, conservando esta fuente real de cambios.

[Plan](PLAN_DEL_PROYECTO.md) · [Avance y criterios](AVANCE.md) · [Historial de fase 27](FASE_27.md) · [Recuperación de fase 31](FASE_31.md) · [Mejoras útiles](MEJORAS_UTILES.md)
