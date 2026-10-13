# Fase 23 — historial por páginas

Estado: **Unreleased, preparación de 0.6.0**. Código en main sin generar APK, incrementar versión Android, crear tags ni publicar Release. Última entrega binaria: 0.5.7 / código 27.

## Comportamiento visible

**Inicio → Reportes guardados** muestra hasta **50 coincidencias por página**. **Más reportes** continúa y reemplaza las filas de la página anterior; **Anterior** vuelve a un inicio ya visitado y relee esa página. **Actualizar lista · volver al inicio** reinicia con los filtros actuales. Se conservan texto, fechas, tipo, estado y prioridad, junto con calendario y búsqueda por folio.

Una búsqueda revisa hasta 500 candidatos por gesto. Si no encontró coincidencias pero quedan candidatos, muestra **No hay coincidencias en el tramo revisado. Continúa buscando** y habilita **Continuar búsqueda**. El contador describe solo la página; no inventa un total global ni presenta una lectura incompleta como ausencia definitiva. Si el recorrido terminó, el aviso corresponde al tramo restante. La ausencia de reportes en el teléfono se muestra únicamente al terminar una consulta inicial sin filtros ni texto que examinó cero filas.

Girar o volver del detalle conserva el inicio/página y realiza una lectura fresca con esos criterios. Modificar criterios efectivos reinicia el recorrido; una fecha inválida conserva el filtro aplicado. Un error permite repetir la misma página fallida sin alterar el historial exitoso. Los gestos de navegación/detalle verifican que la página Ready mostrada siga vigente.

## Contrato y memoria

`ReportListPageRepository.listPage` devuelve hasta 100 candidatos por lote, con copia inmutable y `hasMore`. SQLite aplica fecha del incidente `[inicio, fin)`, tipo, estado y prioridad antes de LIMIT + 1. La continuación exclusiva compara fecha de guardado, nanosegundos y folio en orden descendente, sin OFFSET. No modifica esquema 2, índices o filas.

El texto se normaliza y compara con el mismo código Kotlin anterior, en segundo plano y con cancelación por fila. Mantiene acentos, mayúsculas, espacios y caracteres como `%`/`_` con la misma semántica anterior; no se sustituye por LIKE o FTS. El cursor de continuación es el **último candidato examinado**: si se completaron 50 coincidencias dentro de un lote, las filas cargadas pero no examinadas se recuperan al continuar. El límite de 500 se refiere a candidatos filtrados por los criterios SQL y revisados en Kotlin, no a todas las filas que el motor SQLite pueda recorrer internamente.

La UI conserva una página de hasta 50 coincidencias; cada lote contiene como máximo 100 y el repositorio lee una fila adicional para decidir continuación. Se mantienen hasta 50 inicios de navegación, sin guardar páginas enteras. Al alcanzar la ventana de navegación, **Anterior** llega al inicio más antiguo retenido; el botón de actualización permite volver al inicio completo.

Cada página es una consulta actual, no una instantánea congelada de toda la base. Los reportes nuevos pueden afectar lecturas posteriores, especialmente si cambia el reloj del teléfono. Volver al inicio permite revisar los más recientes. No se cambia login/loading, GPS de 5 km, fotos, diarios, UUID, CAS, createOnce, guardado o mapa. Los modelos anteriores de lista/búsqueda y sus pruebas se conservan como antecedentes, pero la lista productiva deja de llamar `repository.list()`.

## Comprobación

**60 casos distintos aprobados, cero fallos/errores/omitidos en sus resultados finales; 37 nuevos.** Dominio `ReportListPageTest`: 10 nuevos; SQL host `ReportListSqlSelectionTest`: 9 nuevos; motor paginado: 18 nuevos; normalización/filtros anteriores: 23. Se comprobaron empates por segundos/nanosegundos/folio, criterios antes del límite, continuación sin omisiones, página llena a mitad de lote, 500 candidatos sin coincidencias, siguiente/anterior/reintento, conservación al recrearse, cancelación, respuestas tardías, reentrada, páginas malformadas y trabajo fuera de Main.

La primera ejecución obtuvo un fallo en una expectativa nueva sobre «año»/«ano»: el normalizador existente elimina las marcas Unicode y los considera coincidentes. Se corrigió solo esa expectativa para mantener la semántica previa, sin modificar el normalizador; sus 18 casos se repitieron y aprobaron. No se suman repeticiones como casos adicionales. Los XML finales de las cuatro suites están conservados en `.gradle/validacion/fase23-resultados`, ignorado por Git.

Kotlin debug/release y tests Android compilados; lint debug: **0 errores, 22 advertencias**, mismo conteo del corte anterior. `git diff --check` aprobado. SQL host ejecuta selección/orden de producción sobre SQLite en memoria con `REPORT_SQLITE_TEST_PYTHON`, en modo query_only, comprobando que filas, esquema y user_version no cambian; no acredita ejecución del repositorio Android completo.

Se añadió **un caso Android para 70 reportes de una fixture aislada**, primera página de 50, segunda de 20, anterior y retorno del detalle, con comprobación de datos/GPS/diarios. Se adaptaron los contadores de las pruebas anteriores. **Estos tests solo se compilaron, no se ejecutaron**, sin APK ni aceptación visual en este bloque. Quedan para la entrega agrupada 0.6.0, junto con campos, teclado, calendario, rotación y mapa. No se atribuye una nueva aceptación GPS física.

## Plan

**15/28 = 53.6%**, mismos hitos y pesos del PDF: usuarios 1/4, reportes 4/4, GPS 4/4, consulta 2/4, mapas 4/4, administración 0/4 y notificaciones 0/4. Este bloque mejora la consulta local a escala; no completa autor autenticado, aislamiento de datos, seguimiento administrativo o notificaciones. Los reportes permanecen locales.
