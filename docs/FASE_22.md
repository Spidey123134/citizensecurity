# Fase 22 — prioridad y búsqueda en segundo plano

## Incorporación a 0.6.0 — cierre validado

Esta fase está incluida en **0.6.0 / código 28**. Se ejecutaron las pruebas Android de filtros combinados, mapa/lista de zona y consulta dentro del cierre agrupado de **1164 casos distintos aprobados**, sin fallos, errores u omitidos. Se conserva el alcance de prioridad indicada por el autor. Los resultados de preparación no se duplican. [Entrega](ENTREGA_0_6_0.md) · [Validación](VALIDACION.md). Los bloques siguientes conservan el registro original.

## Registro de preparación

Estado: **Unreleased, preparación de 0.6.0**. Actualización de código en main; no genera APK, incrementa versión Android, crea tags ni publica Release. Última entrega binaria: 0.5.7 / código 27.

## Función visible

**Inicio → Reportes guardados** permite elegir Todas/Alta/Media/Baja junto con texto, fechas, tipo y estado. La prioridad corresponde a la indicada por el autor; no acredita una evaluación oficial. La selección se conserva al recrear la pantalla y **Limpiar búsqueda / Mostrar todos** restablecen también este filtro.

**Inicio → Mapa de reportes** incorpora el mismo selector. Cambiar prioridad retira la muestra anterior; **Consultar esta zona** aplica región, fechas, tipo, estado, prioridad y límite. SQL filtra prioridad antes de LIMIT + 1, de modo que otros niveles no consumen espacios ni inflan `hasMore`. Se conserva la prioridad al mover la cámara y recrear la pantalla. La lista de zona usa esa misma muestra limitada.

## Correcciones y respuesta de la interfaz

La búsqueda general anteriormente normalizaba descripción/referencia y recorría el historial durante composición. Ahora `ReportSearchViewModel` ejecuta ese cálculo en `Dispatchers.Default`, conserva los reportes originales y comprueba cancelación por fila. La pantalla muestra **Aplicando filtros…**, error y reintento; no presenta una búsqueda pendiente como cero coincidencias.

Cada resultado identifica las referencias exactas de fuente y filtros validados, además de las generaciones del motor. La UI comprueba esa identidad antes de iniciar el efecto de una búsqueda nueva. `ReportSearchInput` compara referencias para que una lectura nueva con contenido igual también sustituya la anterior, evitando comparar todo el historial en Main. La colección lifecycle de la lista usa un contenedor con igualdad referencial: así Compose recibe la fuente nueva aunque se omita una emisión intermedia Loading y su contenido sea igual al anterior. Volver del detalle relanza los mismos criterios; acceder al detalle cancela el cálculo pendiente.

También se corrigió el acceso a la lista de zona mientras la cámara está moviéndose: comprobar solo escena/modelo podía admitir la región anterior antes del evento camera-idle. Ahora el gesto compara la proyección actual con la consulta terminada; si difieren o falla esa lectura, retira la muestra y requiere consultar nuevamente. Abrir un folio desde la lista o el marcador seleccionado repite la comprobación.

## Comprobación y límites

**100 casos distintos aprobados, cero fallos/errores/omitidos en sus resultados finales; 21 nuevos.** Dominio: 16 (2 nuevos); SQL SQLite: 8 (2 nuevos); binding: 36 (2 nuevos); página vigente: 5 (1 nuevo); filtros: 23 (2 nuevos); motor: 12 (todos nuevos). Kotlin debug/release y tests Android compilados; estos últimos no se ejecutaron ni empaquetaron. Lint debug: **0 errores y 22 advertencias**, mismo conteo que el corte anterior. `git diff --check` aprobado.

La primera ejecución obtuvo 99 aprobados y un fallo en el test del hilo: las corutinas agregan un sufijo al nombre del thread al depurar. Se corrigió el test para comparar identidad exacta del hilo del executor y se repitieron sus 12 casos, todos aprobados; no se suman como casos adicionales ni se repitieron las otras suites sin cambios. La prueba demuestra que Main puede seguir trabajando durante el cálculo y se comprueba cancelación incluso frente a una respuesta anterior no cooperativa. Logs/XML permanecen en los directorios de construcción ignorados.

Las pruebas SQLite usan el WHERE de producción con `REPORT_SQLITE_TEST_PYTHON` y una base temporal en memoria; no equivalen a ejecutar el repositorio o renderizado Android. Pendiente para 0.6.0: controles de prioridad, teclado, fechas, carga/error/reintento, rotación y movimiento real de cámara en mapa nativo. No se repite la aceptación GPS anterior ni se atribuye una prueba física nueva.

Todavía se lee todo el historial del teléfono y se mantiene su lista en memoria; mover el filtrado fuera de Main reduce el riesgo de bloqueo, pero no sustituye una consulta paginada. No se modifican SQLite esquema 2, filas, fotografías, diarios, UUID, CAS, createOnce, GPS de 5 km o login/loading. No hay nuevas funciones ocultas.

## Plan

**15/28 = 53.6%**, mismos hitos y pesos del PDF: usuarios 1/4, reportes 4/4, GPS 4/4, consulta 2/4, mapas 4/4, administración 0/4, notificaciones 0/4. Prioridad y respuesta de búsqueda refuerzan consulta/mapas; no completan identidad del autor ni seguimiento administrativo. Los reportes siguen en el teléfono.
