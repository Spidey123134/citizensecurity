# Fase 21 — fechas y lista de la zona

Estado: **Unreleased, preparación de 0.6.0**. Se actualiza el código de main sin generar APK, incrementar versión Android, crear tags ni publicar una Release. La última APK continúa siendo 0.5.7 / código 27.

## Comportamiento

En **Inicio → Mapa de reportes**, los campos Desde/Hasta aceptan fechas opcionales `dd/MM/aaaa` y calendario. **Aplicar fechas** valida ambos campos y retira una consulta anterior; **Consultar esta zona** realiza la lectura con el intervalo aplicado. Escribir sin aplicar no cambia los filtros. Una fecha imposible o invertida muestra el error y conserva el filtro válido anterior. **Quitar fechas** retira el intervalo y requiere volver a consultar.

El último día se incluye completo mediante el inicio del día siguiente en la zona horaria conservada por la pantalla; no se suman 24 horas fijas. La fecha corresponde al incidente, no a cuándo se guardó. Entradas, fechas aplicadas y zona se conservan por separado al recrear la pantalla; la consulta de zona requiere un nuevo gesto al volver.

**Ver lista de esta zona** muestra los mismos folios de la muestra terminada del mapa, con tipo, estado y prioridad. Abrir detalle navega al folio real. Si hay más coincidencias que el límite, se indica que la muestra está incompleta. Esta lista no realiza otra lectura ni representa todos los reportes del teléfono. La lista general mantiene sus filtros de texto; no se declara paridad de búsqueda de texto con el mapa.

## Integridad de la consulta

- `ReportMapQuery` incorpora extremos opcionales `[startInclusive, endExclusive)`. La igualdad e identidad de consulta incluyen ambos extremos. Un intervalo instantáneo vacío es válido y devuelve cero coincidencias; uno invertido se rechaza.
- SQLite combina fecha, región, tipo y estado en WHERE **antes de LIMIT + 1**. Compara segundos y nanosegundos, conserva el orden de guardado/folio y los casos del antimeridiano. `hasMore` cuenta solo coincidencias del filtro completo. No modifica filas o esquema.
- El binding conserva las fechas al mover la cámara. Cambiar intervalo invalida la respuesta anterior incluso en la misma región; conserva la cancelación y control de generaciones existentes.
- La lista solo se ofrece cuando escena y modelo están Ready y concuerdan en consulta, conteo y `hasMore`. Carga, error, cierre o muestra incoherente la deshabilitan. Abrir un folio vuelve a comprobar que pertenece a la muestra vigente.

## Comprobación

**79 pruebas aprobadas, 0 fallos/errores/omitidas; 17 casos nuevos.** Dominio `ReportMapQueryTest`: 14 (4 nuevos); SQLite `ReportMapSqlSelectionTest`: 6 (todos nuevos); binding: 34 (3 nuevos); página visible: 4 (todos nuevos); validador de búsqueda/fechas existente: 21. Se compiló Kotlin debug/release y se ejecutó lint debug: **0 errores, 22 advertencias**, el mismo conteo del corte 0.5.7. No se repitió la suite completa de las fases anteriores.

Las seis pruebas SQLite ejecutaron la selección SQL de producción sobre SQLite del equipo, en memoria, con `REPORT_SQLITE_TEST_PYTHON` apuntando a Python 3; no ejecutan el repositorio Android completo ni acreditan render nativo. Se comprobó además `git diff --check`. Logs y XML de ejecución permanecen en directorios de construcción ignorados.

No se ejecuta instrumentación o aceptación visual Android en este bloque y no se genera APK. Queda pendiente para la entrega agrupada comprobar controles/teclado/calendario, rotación, fechas y lista sobre el mapa nativo con una muestra temporal, antes de publicar 0.6.0. La prueba GPS física ya informada por el usuario pertenece al avance anterior; no se atribuye a esta fase.

## Plan y alcance

**15/28 = 53.6%**, mismos criterios y pesos del PDF: usuarios 1/4, reportes 4/4, GPS 4/4, consulta 2/4, mapas 4/4, administración 0/4 y notificaciones 0/4. Este bloque refuerza consulta/mapas sin inventar otro hito. Se conservan login/loading, datos, fotografías, folios duraderos, borradores y comprobaciones GPS de 5 km.

Los reportes permanecen locales; cuentas reales, aislamiento por autor, seguimiento administrativo y notificaciones siguen pendientes. La publicación futura se agrupa en 0.6.0 y requiere la instrucción de entrega correspondiente.
