# Fase 9: consultar incidentes por zona

Trabajo **Unreleased, local**, del **6 de octubre de 2026**, posterior al cierre técnico de fase 8 y a la oficial **0.4.0a / código Android 19**. Se continúa sin asignar otra versión ni modificar la publicación. **Implementación y validación técnica completadas: 457 casos distintos ejecutados y aprobados, con 43 definiciones nuevas; aceptación visual pendiente.** [Validación](VALIDACION.md) conserva las ejecuciones efectivas de este corte sin sumar repeticiones. Las **226 pruebas aprobadas y 27 definiciones nuevas de fase 8** pertenecen a su antecedente, conservado en [Fase 8](FASE_8.md).

El entregable nuevo corresponde a **4.5 Mapa de incidentes** del PDF: recuperar los incidentes locales de una región y entregar los datos que necesita un marcador. Esta fase añade una consulta geográfica a las funciones anteriores de selección; los hitos de [Avance](AVANCE.md) conservan su criterio y **7/28 = 25%** hasta comprobar clave, renderizado, marcadores y flujo real. El compañero conserva mapa, controles, formulario, navegación y login.

## Resultado concreto

La selección de un punto y la consulta de incidentes de una zona resuelven necesidades distintas. Las fases anteriores conservan un punto para el borrador; este paso obtiene marcadores de los reportes ya almacenados dentro de una región. La consulta recibe sus límites, tipos y estados elegidos, y un máximo de resultados. La salida contiene folio, tipo, prioridad, estado y coordenadas, junto con una señal de que existen más coincidencias.

La lectura se hace directamente en SQLite con filtros y una proyección de esas columnas. Se excluyen los reportes sin coordenadas. No se carga la lista completa para filtrarla en memoria, no se devuelven descripción ni referencia y no se modifica ningún registro. El esquema y versión de la base se conservan.

## Contrato de dominio

| Tipo u operación | Contrato |
| --- | --- |
| `MapRegion(south, north, west, east)` | Valores finitos; latitudes entre −90 y 90, longitudes entre −180 y 180 y `south <= north`. Los bordes se incluyen. `west > east` describe una región que cruza el antimeridiano; −180 y 180 representan el mismo meridiano al consultar. |
| `ReportMapQuery(region, types, statuses, limit)` | `types: Set<IncidentType>` y `statuses: Set<ReportStatus>` vacíos aceptan todos los valores de ese filtro. `limit` vale 200 por defecto y admite 1 a 500. Conserva una instantánea inmutable de los filtros. |
| `ReportMapMarker(id, type, priority, status, latitude, longitude)` | Datos mínimos de un marcador; no contiene descripción, referencia ni una identidad de usuario. |
| `ReportMapPage(markers, hasMore)` | Hasta `limit` marcadores; `hasMore` señala coincidencias adicionales en esa misma consulta. |
| `ReportMapRepository.queryMarkers(query)` | Operación suspendida de lectura por zona. Es un contrato separado; las firmas anteriores de `ReportRepository` se conservan. |

Una región admite `south == north` o `west == east`: consulta sus bordes sin inventar un área adicional. Una región como `west = 170.0, east = -170.0` incluye ambos lados del meridiano 180. `west = 180.0, east = -180.0` consulta únicamente ese meridiano y sus dos representaciones; `west = -180.0, east = 180.0` incluye todas las longitudes. Los límites geográficos son los de la zona a consultar, independientes de la evidencia GPS que exige confirmar o guardar un reporte dentro de 5 km.

`SqliteReportRepository` implementa el contrato nuevo y el anterior. La consulta usa parámetros para coordenadas y enums y ordena por **segundos de creación, nanosegundos de creación y folio, en orden descendente**. Consulta `limit + 1` filas: devuelve como máximo `limit` y usa la adicional para determinar `hasMore`. El desempate por folio mantiene un orden estable para esa lectura. No hay paginación, token de continuación ni garantía de cargar todos los incidentes en esta fase.

## Modelo y composición Android

`ReportMapViewModel`, en `com.example.citizensecurity.maps`, expone `state: StateFlow<ReportMapState>`. Sus acciones se entregan en el hilo principal. Crear el modelo o su fábrica y observar el flujo no consulta la base. Solo **`load(query)`** inicia la lectura; `clear()` cancela el trabajo vigente, descarta el resultado anterior y vuelve a `Idle`, permitiendo consultas nuevas después.

| Estado | Información y consumo futuro |
| --- | --- |
| `Idle` | No se ha solicitado una región o se limpió el modelo. |
| `Loading(query, previous)` | Región/filtros que están cargando; `previous` puede conservar el último `Ready`. |
| `Ready(query, page)` | Resultado de esa consulta exacta. Una lista vacía es una lectura terminada sin coincidencias. |
| `Error(query, message, previous)` | Consulta fallida, mensaje representable y último `Ready` cuando existe. |

Solicitar otra consulta cancela la anterior y solo la petición vigente puede publicar un resultado. Repetir la misma consulta mientras está pendiente no abre otra operación. Después de terminar, `load(query)` permite repetir explícitamente la lectura. Las respuestas tardías de una operación sustituida o limpiada no vuelven a colocar sus marcadores en el estado actual. La cancelación no se presenta como un error de almacenamiento; cancelar la lectura vigente por otra causa recupera el último `Ready` o `Idle`. Liberar el ViewModel cancela e ignora nuevas cargas de esa instancia.

El job lazy queda reservado **antes de publicar `Loading`**. Si un consumidor inmediato de ese estado llama a `clear()` o `load(B)`, puede cancelar o sustituir la petición A antes de que su lectura comience. Se corrigió esta reentrada y dos regresiones comprobadas cubren ambas acciones; están incluidas en las 19 definiciones nuevas del modelo.

`CitizenSecurityApplication.reportMapViewModelFactory` se construye de forma lazy y utiliza **el mismo `SqliteReportRepository` productivo compartido y protegido** que los contratos existentes. Una pantalla no abre otro repositorio ni cierra la instancia compartida. El guard del guardado conserva sus reglas y esta lectura no concede evidencia para escribir.

## Conexión futura del mapa

El compañero obtiene el modelo con la fábrica compartida y solicita una región después de un gesto o cuando termina el movimiento de cámara. La región procede del área que su mapa muestra; no se fabrica a partir del punto del borrador. [Acoplar interfaz](ACOPLAR_INTERFAZ.md) muestra la composición y el consumo de estados.

La vista conserva la consulta que desea representar y compara la `query` de `Loading`, `Ready` o `Error` antes de colocar marcadores. Si `Loading` o `Error` mantiene `previous`, sus marcadores pertenecen a **`previous.query`**, que puede describir otra zona o filtros. Conservarlos en el modelo permite decidir cómo representar un reintento; no justifica mostrarlos como resultados de la nueva región. Un `Ready` vacío elimina los resultados anteriores de esa región. Un `Ready` con `hasMore = true` debe indicar que la salida está limitada y no afirmar que contiene todos los incidentes.

Este paso entrega datos y estado; no instala listeners de cámara, renderiza un `GoogleMap`, coloca pines, añade controles o conecta accesos visibles. Se conservan los bindings de selección de fase 8 para su propósito. Obtener marcadores no confirma un punto del borrador, inicia GPS, guarda reportes, cambia estados ni establece permisos administrativos.

## Comprobación y siguiente entrega

La validación técnica de fase 9 aprobó **457 casos distintos ejecutados**, sin fallos, errores u omitidos y **sin resultados reutilizados**. Se añadieron **43 definiciones**:

| Suite | Casos ejecutados y aprobados | Definiciones nuevas |
| --- | --- | --- |
| Dominio | 96 | 10 |
| Datos JVM | 38 | 0 |
| SQLite Android nativo | 78 | 14 |
| App JVM | 211 | 19 de `ReportMapViewModelTest` |
| Laboratorio Android flow | 34 | 0 |
| Total | **457** | **43** |

Los casos comprueban límites, antimeridiano y alias ±180, filtros, límite/truncamiento, exclusión de reportes sin coordenadas, orden, ausencia de escrituras y permanencia. El modelo se comprueba frente a consultas sustituidas, respuestas tardías, duplicados pendientes, errores, limpieza y la reentrada inmediata desde `Loading`. La lectura del laboratorio es controlada y no acredita GPS físico ni mapa renderizado.

El pase amplio terminó en **2 min 9 s**. Tras corregir la reentrada se repitieron app, compilación y lint en **16 s**; esas repeticiones no se suman a los 457 casos distintos. Debug/release compilan y la firma debug v2 es válida. Lint terminó sin errores: app conserva **19 advertencias anteriores en debug/release**, datos ninguna y flow dos anteriores.

El recibo ignorado `.gradle/validacion/cierre-fase9.json` confirma conservación de los **14 APK previos**, HEAD/índice, fuentes visuales, Gradle y producto instalado **0.3.1 / código 8**. Los fixtures permanecen fuera del DEX productivo. [Validación](VALIDACION.md) registra la evidencia de este corte por separado de fase 8 y de la Release.

El siguiente paso para nuevos hitos es configurar una clave de Maps habilitada, conectar el mapa y sus marcadores con el compañero y comprobar el flujo visible y GPS físico. La [guía para configurar la clave Maps](CONFIGURAR_CLAVE_MAPS.md) reúne los pasos manuales; esta fase no crea un proyecto Cloud, configura facturación ni activa un servicio externo. La aceptación del usuario permanece pendiente.

Guardado real y consultas siguen sin acceso visible; el ejemplo ficticio conserva sus datos fijos en memoria. No se cambia interfaz/login, autenticación, roles, administración, fotografías, servidor o notificaciones. El trabajo local solicitado no autoriza nuevos commits, subida, integración, etiquetas, Releases, borrado o apagado de la PC.
