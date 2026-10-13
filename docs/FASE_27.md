# Fase 27 — historial real de estados

## Incorporación a 0.6.0 — cierre validado

Esta fase está incluida en **0.6.0 / código 28**. Se ejecutaron sus **15 casos Android que antes solo estaban compilados**: 13 de datos y dos de interfaz. SQLiteOpenHelper comprobó migración/rollback, creación y reintento atómicos, permiso denegado sin abrir base, CAS/conflictos, revocación/cancelación y corrupción. La interfaz comprobó consulta explícita del evento real, actualización sin duplicarlo y error independiente que conserva la ficha del reporte.

Esos 15 forman parte, una sola vez, de los **1164 casos distintos aprobados** del cierre agrupado, con cero fallos, errores u omitidos. La asignación administrativa sigue pendiente y el escritor productivo no está conectado; la lectura probada no concede permisos o acredita atención por una autoridad. **19/28 = 67.9% permanece**. [Entrega](ENTREGA_0_6_0.md) · [Validación](VALIDACION.md) · [Base de fase 28](FASE_28.md). Los bloques siguientes conservan el registro original de preparación.

## Registro de preparación

Estado: **Unreleased para 0.6.0**, actualización de código. Sin APK, cambio de versión, tag o Release; última entrega binaria 0.5.7 / código 27.

## Recorrido visible

En **Inicio → Reportes guardados**, abre un folio y pulsa **Consultar historial**. La ficha muestra los eventos registrados, su estado al consultar, fecha y cuenta local cuando se conoce. **Actualizar historial** vuelve al inicio; **Eventos anteriores** recorre otra página y **Volver al inicio** consulta los eventos más recientes. La lectura comienza por gesto, sin solicitar GPS ni modificar el reporte.

El historial conserva una página de hasta 50 eventos; el contrato admite como máximo 100. Un cursor exclusivo por secuencia consulta eventos anteriores en orden descendente, sin OFFSET ni cargar toda la tabla. Cada página es una lectura actual de estado y eventos en una transacción. La secuencia confirmada fija el orden; un ajuste del reloj no reordena los hechos. Las fechas muestran el instante realmente registrado en el teléfono.

Una consulta fallida ofrece **Reintentar historial**, conserva el inicio de esa página y deja disponible la ficha del reporte. Se puede cancelar una lectura; salir o consultar otro folio invalida su respuesta anterior. Girar conserva la consulta del mismo folio. Los botones de historial, copiar y abrir mapa verifican que la ficha mostrada siga vigente. Si el estado leído por el historial difiere del de la ficha, se pide volver a consultar el folio; no se sobrescribe una lectura por otra que podría ser anterior.

El texto de la pantalla aclara que es un registro local y no confirma atención por una autoridad. Un reporte anterior sin evento de creación conserva su estado y avisa que el historial no es completo. La ausencia de eventos no se interpreta como ausencia de cambios anteriores.

## Persistencia y cambios protegidos

ReportDatabase pasa de esquema 2 a **3** mediante una migración aditiva: añade report_status_events, sin recrear reportes, fotografías o estados. La actualización desde esquema 1 conserva además la migración fotográfica anterior. No se generan eventos ni autores para filas antiguas. La base de cuentas permanece en esquema 1 y los espacios por UUID conservan sus nombres.

createOnce inserta el reporte nuevo y su evento CREATED en una misma transacción. El evento conserva el instante real de creación y la cuenta capturada cuando existe; el modo académico no inventa una identidad. Un reintento que encuentra el mismo reporte devuelve su folio sin duplicar eventos ni completar artificialmente un historial antiguo.

El servicio de escritura preparado permite únicamente **Reportado → En revisión → Atendido → Cerrado**. Cada operación lleva UUID, estado/secuencia esperados y motivo de 5 a 500 caracteres Unicode válidos; la autorización suministra el actor. Actualizar el estado por CAS y añadir el evento forman una sola transacción. Conflictos, cancelación o revocación antes de confirmar impiden un cambio parcial. Repetir la misma operación devuelve su evento original si actor y contenido coinciden; reutilizarla para otro cambio se rechaza.

La composición productiva **deniega escritura por defecto antes de abrir la base**. No ofrece un control administrativo ni expone el escritor en ReportWorkspace. El usuario eligió **dejar pendiente la asignación administrativa**. admin/admin y Desarrollador conservan el acceso académico; no conceden permisos administrativos. Esta fase prepara el servicio y la lectura, sin declarar atención, cierre administrativo o autorización real.

La consulta utiliza el repositorio capturado de la cuenta y comprueba sesión/cancelación antes y después. El modelo reserva la tarea antes de publicar carga, descarta generaciones anteriores y valida folio, cursor, secuencias, continuidad, operaciones únicas y coherencia del estado. Una respuesta corrupta produce error completo en vez de mezclar datos de consultas distintas. La separación de cuentas, fotografías, diarios, GPS 5 km y login/loading del compañero se conserva.

## Comprobación

**100 casos distintos aprobados; cero fallos, errores u omitidos. 37 nuevos:**

| Grupo nuevo | Casos |
| --- | ---: |
| Dominio: eventos, UUID, motivos y transiciones | 9 |
| Datos: SQL productivo y SQLite del host | 10 |
| Modelo: carga, páginas, cancelación, respuesta tardía y validación | 14 |
| Consulta del contexto: sesión, revocación y conservación de la ficha | 4 |

Los **63 de regresión** cubren aislamiento SQLite (4), selección de lista (9), selección de mapa (8), entradas de acceso (26), acceso (6) y sellos de contexto (10). No se suman repeticiones como casos nuevos. El host ejecuta el DDL y las consultas productivas, con migración desde versiones 1/2, rollback, conservación de datos, creación atómica, reintento, CAS, paginación y validación de nanosegundos.

Kotlin release y pruebas Android de app/datos compilados; debug se compila como dependencia. Lint: **app 0 errores/22 advertencias anteriores; datos 0 errores/0 advertencias**. Evidencia local ignorada: .gradle/validacion/fase27-validacion.log, fase27-cierre.log y fase27-resultados.

**15 nuevos casos Android preparados y compilados, no ejecutados:** 13 de datos y 2 de interfaz. Cubren SQLiteOpenHelper/migración, creación/reintento, permiso denegado sin abrir base, conflictos, revocación/cancelación, corrupción y lectura explícita del historial con conservación de la ficha. Usan archivos y datos aislados de pruebas; la fuente GPS controlada no acredita una nueva prueba física. La ejecución Android y aceptación visual quedan pendientes para la entrega agrupada; compilar no las sustituye.

## Avance y siguiente alcance

**19/28 = 67.9% de construcción**, mismos criterios del PDF: Usuarios 4/4, Reportes 4/4, GPS 4/4, Consulta 3/4, Mapas 4/4, Administración 0/4 y Notificaciones 0/4. El historial visible y el servicio protegido refuerzan 4.4/4.6 sin sumar todavía un hito de seguimiento o administración. Faltan actor/permiso administrativo acordados, recorrido integrado y comprobación de cambios autorizados; asignación pendiente por decisión del usuario.

La siguiente etapa administrativa debe conservar esta denegación hasta acordar el permiso. Consulta administrativa, notificaciones, servidor/sincronización y aceptación Android siguen pendientes. La actualización de código no cambia la APK publicada ni equivale a una entrega 0.6.0.
