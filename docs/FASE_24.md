# Fase 24 — recuperar consulta y reiniciar búsquedas

## Incorporación a 0.6.0 — cierre validado

Esta fase está incluida en **0.6.0 / código 28**. Se ejecutaron las pruebas Android de recuperación de criterios/posición y reinicio de búsquedas en el cierre agrupado de **1164 casos distintos aprobados**, sin fallos, errores u omitidos. La recuperación sigue dependiendo del estado conservado por Android; no promete reanudación después de forzar cierre. Los resultados de preparación no se duplican. [Entrega](ENTREGA_0_6_0.md) · [Validación](VALIDACION.md). Los bloques siguientes conservan el registro original.

## Registro de preparación

Estado: **Unreleased, preparación de 0.6.0**. Código en main; sin APK, incremento de versión, tag o Release. Última entrega binaria: 0.5.7 / código 27.

## Comportamiento

La consulta paginada conserva criterios efectivos, inicio solicitado, número de página e historial pequeño cuando Android restaura el estado de la actividad tras finalizar el proceso. Al activar la pantalla vuelve a leer esa página; no conserva una copia de los resultados. Si los criterios cambiaron, empieza un recorrido nuevo. Una solicitud pendiente o fallida mantiene su inicio para continuar o reintentar sin saltar páginas.

**Buscar reportes** reinicia desde la primera página aunque el texto y las fechas aplicadas sean idénticos a los anteriores. El mismo reinicio se aplica a **Limpiar búsqueda** y **Mostrar todos**. Antes, reasignar valores iguales no cambiaba las claves del efecto y dejaba la página avanzada activa. Una fecha inválida sigue mostrando el error y conserva la consulta aplicada.

La UI conserva una revisión y una solicitud de reinicio para que el efecto publique la búsqueda con el mismo objeto de filtros que usa la pantalla. No llama al motor con otro objeto equivalente que dejaría los resultados inaccesibles por su comprobación de identidad. Girar o volver del detalle, sin un nuevo gesto de búsqueda, conserva el inicio previo.

## Recuperación acotada

El motor recibe `SavedStateHandle` en su fábrica y guarda un checkpoint versionado de `ArrayList<String>`. Contiene criterios, cursor con segundos/nanosegundos/folio, solicitud y hasta 50 inicios de navegación. Límite: 217 campos y 15000 caracteres; el texto normalizado admite hasta 4096 porque NFD puede ampliar una entrada Unicode válida de 512 caracteres. No se modifica el normalizador anterior.

Se comprueban versión, tipos, tamaños, nombres de enums, límites de fechas/nanosegundos, representación canónica, orden de cursores y coherencia entre página, navegación e índices. Un estado inválido se descarta completo; el siguiente gesto/activación usa los criterios válidos de la pantalla desde el inicio. El constructor restaura únicamente metadatos y no consulta SQLite.

Se conserva la solicitud antes de publicar Loading y el inicio canónico después de completar una lectura. Cancelar conserva los metadatos de posición, sin conservar trabajos o generaciones. Los checkpoints no incluyen objetos Report, descripciones de resultados, fotografías, permisos, ubicaciones del teléfono o evidencia GPS. No alteran SQLite esquema 2, filas, diarios, CAS, UUID, createOnce o comprobación de 5 km.

La recuperación depende del estado que Android haya guardado. Saved state está pensado para destrucción iniciada por el sistema; cerrar forzosamente, quitar la tarea o finalizar la pantalla no proporciona la misma garantía. Las escrituras realizadas estando detenida la actividad requieren otro ciclo de inicio/parada para ser guardadas. Se mantienen metadatos pequeños y se reconstruyen resultados mediante lectura, según [la documentación de Android sobre guardar estado](https://developer.android.com/topic/libraries/architecture/saving-states) y [SavedStateHandle](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate).

## Comprobación

**34 casos JVM aprobados, cero fallos/errores/omitidos; 16 nuevos.** Motor paginado anterior: 18; restauración: 9 nuevos; codec: 7 nuevos. Cubren página avanzada fresca, solicitudes NEXT/PREVIOUS pendientes, error/reintento, historial truncado a 50, criterios distintos, reinicio explícito, rechazo completo de estados corruptos/tipos/versiones, sustitución reentrante y ausencia de consulta en el constructor. Incluyen una entrada Hangul de 512 caracteres que se normaliza a 1536, sin perder recuperación.

Kotlin debug/release y tests Android compilados; lint debug: **0 errores, 22 advertencias**, mismo conteo anterior. `git diff --check` aprobado. No se repiten las suites SQL de fase 23 porque su contrato e implementación no cambiaron.

Se añadió un caso Android con 70 filas aisladas que avanza a la página 2 y repite Buscar/Limpiar para volver a la primera. **Solo se compiló, no se ejecutó.** La reconstrucción JVM usa los metadatos de SavedStateHandle; no se presenta como prueba de muerte física del proceso o serialización real de Bundle en un dispositivo. Estas comprobaciones, junto con los controles y restauración de tarea, quedan para la entrega agrupada 0.6.0. No se solicita o acredita otra prueba física GPS.

## Plan y equipo

**15/28 = 53.6%**, mismos hitos y pesos: usuarios 1/4, reportes 4/4, GPS 4/4, consulta 2/4, mapas 4/4, administración 0/4, notificaciones 0/4. Este refuerzo no completa cuentas, autoría, seguimiento o notificaciones.

El usuario confirmó que su compañero construye solo la interfaz del login. La lógica de cuentas se coordina desde nuestro trabajo como siguiente bloque, conservando esa interfaz. Todavía no hay autenticación/aislamiento reales acreditados; admin/admin sigue siendo acceso académico, sin permisos administrativos reales. La fase 24 no modifica login/loading.
