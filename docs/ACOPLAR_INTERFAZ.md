# Acoplar la interfaz al reporte

## Revisión e identidad — 0.5.2

`ReportReviewContent` recibe la instantánea válida del borrador, muestra los campos y devuelve al formulario mediante `onEdit`. No abre SQLite ni GPS. La revisión se vuelve a validar al recrear Activity; no sirve como credencial de cercanía. La referencia sin coordenadas sigue siendo suficiente para revisar campos, no para pasar el guard de inserción.

La escritura futura debe usar `IdempotentReportRepository.createOnce(requestId, draft)`. Mantener UUID y contenido idénticos mientras el resultado sea incierto; una respuesta perdida tras commit se recupera con esa misma identidad, sin insertar otra fila. `ReportViewModel` mantiene la identidad durante su vida y conserva un éxito hasta `startNewReport`. **Retener identidad/contenido duraderamente ante muerte de proceso queda pendiente antes de activar la pantalla de guardado.** No presentar una nueva identidad como un reintento.

La consulta futura debe llamarse «Reportes en este teléfono»: el acceso académico no identifica al autor. Guardado/consulta reales siguen ocultos hasta la instrucción específica pendiente. [Fase 14](FASE_14.md) define alcance y pruebas.

## Antecedentes

## Entrega oficial 0.5.1 — formulario y mapa visibles

**9 de octubre de 2026 · código Android 21.** El usuario indicó «desde ahora puro released». Esta entrega incorpora las fases 12 y 13 a las variantes compartidas: Desarrollador → **Nuevo reporte · borrador** permite llenar el reporte, elegir un punto en MapLibre/OpenFreeMap, conservar el texto al volver y revisar los campos. El mapa muestra calles, nombres y pin. Los controles distinguen permiso denegado/aproximado/preciso, GPS desactivado y reintento, y funcionan al girar la pantalla. Publicación: [Release v0.5.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.1).

La confirmación mantiene GPS preciso, reciente, no simulado y radio de 5 km. El formulario **todavía no guarda ni envía reportes ni avisa a emergencias**; guardado y consulta reales siguen ocultos por la instrucción específica anterior. El login y el ejemplo ficticio del compañero se conservan. Se comprobaron **296 casos: 285 JVM y 11 nativos**, con cuatro nuevos casos de permisos/ubicación desactivada contra Android real, sin inyectar estados de permiso en el modelo ni ubicaciones falsas. Debug/release compilan, firma debug válida y lint sin errores, con 19 advertencias anteriores por variante.

**Avance: 11/28 = 39.3%.** Se acredita el hito existente de **4.3 permisos y estados GPS (3/4)** por las comprobaciones de los controles, respaldadas por las pruebas previas de fuente/estados. GPS físico y confirmación completa dentro de esta pantalla siguen pendientes: no se completa 4.3.4. Publicar una versión no suma otro hito. [Entrega 0.5.1](ENTREGA_0_5_1.md) explica los puntos y los pendientes; los cortes Unreleased anteriores permanecen como historia y su código de interfaz se incluye en esta entrega.

## Antecedentes conservados

## Fase 13 — permisos y recuperación del GPS — Unreleased

**Avance local del 9 de octubre de 2026**, autorizado por el usuario al pedir el siguiente paso. La vista de reporte ahora explica permiso aproximado, denegación, cancelación y fallos al comprobar o abrir el permiso. Un GPS sin respuesta indica cómo reintentar conservando el punto; ubicación desactivada pide volver a buscar por gesto. Los controles del mapa se pueden desplazar en orientación horizontal y usan el tamaño real de la ventana.

Se mantienen radio 5 km, GPS preciso/reciente/no simulado y confirmación validada; no hay GPS automático ni apertura automática de Ajustes. Debug se identifica como **0.5.0-fase13 / código 20**. La interfaz nueva sigue excluida de release, el login se conserva y guardado/consulta reales permanecen ocultos. **Sin commit, subida ni publicación; main y Release 0.5.0 intactos.** [Fase 13](FASE_13.md) registra comprobación y pendientes. Avance acreditado **10/28 = 35.7%**; la aceptación con GPS físico de esta pantalla sigue pendiente.

## Antecedente: fase 12

## Fase 12 — interfaz de nuevo reporte — Unreleased

**Trabajo local del 9 de octubre de 2026.** El usuario autorizó empezar la interfaz y reutilizar el primer borrador. Se recuperan su estilo azul/verde y la organización del formulario, conectados a los modelos actuales. Login y entrada del compañero se conservan. En debug, Desarrollador → Nuevo reporte · vista previa abre campos de incidente, revisión y selección de punto en MapLibre/OpenFreeMap con calles. No envía avisos ni guarda reportes reales; la consulta real permanece oculta.

La confirmación mantiene permiso preciso, GPS reciente y válido, rechazo de ubicación simulada y radio de 5 km. Abrir el mapa o volver no inicia GPS. El APK local se identifica como **0.5.0-fase12 / código 20**; la variante release excluye la nueva Activity y oculta su botón. **Main, tag y Release oficial 0.5.0 no cambian**. Esta instrucción autoriza este desarrollo local, no commit, subida, integración o nueva publicación. [Fase 12](FASE_12.md) detalla pruebas y pendientes. El avance acreditado sigue **10/28 = 35.7%** hasta aceptar el flujo visual con GPS físico.

## Antecedentes conservados

**Trabajo activo: fase 11 — puente de incidentes por zona a MapLibre — Unreleased, local.** Se prepara la conexión de lectura y marcadores para la futura pantalla del compañero mediante `ReportMapSceneBinding` y `MapLibreReportSceneHost`. El cierre técnico del 8 de octubre aprobó 325 casos distintos y comprobó mapa por zonas/marcadores y GPS físico por separado; no añade una pantalla, login ni acceso a datos reales. La oficial conserva **0.4.0a / código Android 19**. [Fase 11](FASE_11.md) registra alcance, comprobación y límites: 10/28 = 35.7%, con flujo visual productivo pendiente. El cierre de fase 10 y los demás bloques anteriores se conservan como antecedentes a continuación.

**Antecedente: fase 10 — MapLibre Native + OpenFreeMap — Unreleased, local; cerrada técnicamente el 7 de octubre de 2026.** El usuario eligió el motor actual; los bindings de esta guía reciben **`MapLibreMap`** y el estilo es Liberty. SDK Google, metadato/lectura de clave y Google Play Services dejan de ser prerequisitos del runtime del mapa. [Mapa con MapLibre](MAPA_MAPLIBRE.md) es la guía activa. **268 casos distintos aprobados**: 222 JVM app, 34 Android flow, ocho JVM monitor y cuatro Android mapas; 25 definiciones añadidas y siete de readiness que reemplazan nueve de Google. Debug/release, firma debug y lint sin errores comprobados; [Validación](VALIDACION.md) detalla las advertencias. Carga/renderizado nativos y nombres legibles del PNG con GPU host acreditan solo **SDK configurado: 8/28 = 28.6%, Mapas 2/4**. La oficial sigue siendo **0.4.0a / código Android 19**, sin otro número de versión o publicación.

La consulta geográfica de **fase 9 quedó completada técnicamente**: `ReportMapViewModel`, filtros y salida limitada, con **457 casos distintos aprobados y 43 definiciones nuevas**; debug/release, firma y lint aprobados, sin fallos, errores, omisiones o reutilizados. [Fase 9](FASE_9.md) y [Validación](VALIDACION.md) conservan ese antecedente. **Aceptación visual pendiente**; interfaz/login y accesos reales ocultos permanecen.

El contrato de selección de **fase 8 quedó implementado y comprobado técnicamente**: `ReportLocationFlowBinding` coordina borrador, ubicación y vista; **226 ejecuciones aprobadas y 27 definiciones nuevas**, compilación y lint sin errores con advertencias anteriores. [Fase 8](FASE_8.md) conserva ese cierre. Esta guía prepara controles del compañero; las pantallas/login, guardado real y consultas mantienen su estado visible actual.

Versión oficial **0.4.0a / código Android 19**, del **6 de octubre de 2026**, con validación completada. El usuario pidió revisar GitHub, incorporar el trabajo del compañero y subir/publicar esta actualización como Release; su estado en GitHub se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a). Esta autorización cubre esta entrega, sin autorizar automáticamente siguientes publicaciones.

Se completaron dos pasos técnicos: **fase 6A**, un puente para conectar permiso, captura explícita y ciclo de vida de la pantalla; y **fase 7**, un borrador editable que conserva los campos al ir al mapa y regresar, con recuperación mediante `SavedStateHandle` y protección ante resultados de otra sesión. También se cerró el endurecimiento de evidencia GPS de 0.3.11 que había quedado pendiente. Estas piezas quedan listas para que el compañero conecte sus controles; no añaden un formulario, login ni navegación por su cuenta, no inician GPS automáticamente y no habilitan guardado real o consulta.

La [validación de 0.4.0a](VALIDACION.md) aprobó **266 pruebas ejecutadas**: 171 JVM de app, 64 instrumentadas de datos y 31 del laboratorio Android, con **57 definiciones nuevas** y sin fallos, errores u omisiones. **124 casos se reutilizaron** mediante `UP-TO-DATE` (86 de dominio y 38 JVM de datos); no son ejecuciones nuevas. Debug/release compilan y la firma debug v2 es válida. Lint terminó sin errores: **19 advertencias visuales del conjunto integrado** en app, dos por variante técnica y ninguna en datos. Los resultados de 0.3.10 y 0.3.11 conservan sus propios antecedentes. Clave Google Maps, lectura GPS física, mapa renderizado y aceptación del flujo completo siguen pendientes. El avance conserva **7/28 hitos = 25%**, sin sumar dos fases técnicas como dos requisitos completos del PDF. Véanse [fase 7: borrador](FASE_7.md), [acoplamiento](ACOPLAR_INTERFAZ.md) y [estado por hitos](AVANCE.md).

La revisión de GitHub incorporó `main` del compañero (`b665a56a`) mediante el merge local `433b3fb`. Los layouts de inicio y crear cuenta coinciden con el remoto; herramientas conserva su rediseño y añade el bloque solicitado del ejemplo en memoria antes de Volver, manteniendo los IDs del compañero. El descriptor de Gradle quedó coordinado a **JDK 27**, sin URLs de JVM 25; el bytecode Android permanece en JVM 17. El conjunto integrado aprobó la validación registrada para esta entrega.

Los siguientes bloques conservan los cortes locales anteriores y sus límites; no describen el estado de publicación actual.

Antecedente de la preparación **0.3.11 / código Android 18 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el endurecimiento de la evidencia compartida después de `Ready`: al publicar o consultar una lectura se exige permiso preciso FINE y GPS habilitado. Si esa comprobación detecta GPS apagado o falla, descarta la evidencia; reactivarlo no recupera la lectura anterior y requiere otra captura explícita. El radio de 5 km, la precisión, vigencia y rechazo de simulaciones conservan sus reglas.

Producción comparte una misma `AndroidPhoneLocationSource` entre `PhoneLocationRefresh` y `PhoneLocationEvidence`; el guard de SQLite usa ese mismo lector protegido. Antes se comprobaba FINE al consultar el fix, pero faltaba exigir también GPS habilitado después de `Ready`; no era un fallo matemático ni una evasión del radio de `NearbyIncidentPolicy`. La [validación de 0.3.11](VALIDACION.md) se cerró antes de preparar 0.4.0a: **247 pruebas ejecutadas y aprobadas** (121 JVM de app, 38 JVM de datos, 64 instrumentadas de datos y 24 del laboratorio), con **16 definiciones nuevas**. Dos regresiones fallaron contra la base 0.3.10 y aprobaron tras corregir la relectura de GPS; el estado del proveedor era controlado. Debug/release compilan, firma debug v2 válida y lint sin errores (13 advertencias previas de app, dos por variante técnica; datos sin incidencias). Las 86 de dominio no se ejecutaron de nuevo. Estos resultados pertenecen a 0.3.11, no al corte 0.4.0a. La interfaz y login del compañero, accesos ocultos a datos reales y consulta, clave Maps y GPS físico conservan su estado. **Fase 6, 7/28 = 25%**, local y Unreleased, sin acciones Git o publicación.

Antecedente **0.3.10 / código Android 17 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el [flujo de ubicación en Android](FLUJO_UBICACION_ANDROID.md): permiso, renovación explícita, evidencia y confirmación dentro de 5 km. El módulo técnico `verification/location`, `testOnly`, reutiliza las fuentes productivas de `app/.../maps` sin copiarlas y conserva sus tres variantes de diálogo nativo; añade `flow` con paquete propio y fuentes controladas. Las lecturas sintéticas solo pertenecen al laboratorio; la pantalla productiva obtiene la evidencia desde la fábrica de `CitizenSecurityApplication`. El compañero conserva el diseño y los eventos de sus controles: esta comprobación no los conecta ni modifica el login.

El corte 0.3.10 ejecutó y aprobó **21 casos Android distintos en API 37: tres diálogos de permiso extendidos, 16 de flujo y dos de fuente GPS**, con **18 definiciones nuevas**. Los dos de GPS comprueban registro nativo con cancelación antes del callback y rechazo de una `Location` marcada como simulada mediante una fuente controlada; no obtuvieron un fix físico. Debug/release compilan; firma debug v2 válida. Lint terminó sin errores: app conserva 13 advertencias anteriores y cada variante del laboratorio dos (`DataExtractionRules` y `MissingApplicationIcon`). Las **121 JVM de app quedaron `UP-TO-DATE`** como evidencia previa, sin nueva ejecución. [Validación](VALIDACION.md) separa comandos, repeticiones y límites.

Como antecedente, **0.3.9 aprobó 121 JVM de app y tres flujos nativos de permiso**, compilaciones debug/release y lint sin errores con 13 advertencias anteriores. Las 47 JVM nuevas pertenecen a 0.3.8. Conceder permiso no inicia GPS, confirma un punto ni guarda reportes. GPS físico, mapa renderizado, conexión de los controles y clave Google Cloud siguen pendientes. Guardado real y consulta conservan sus accesos ocultos. En aquel corte no había acciones de publicación autorizadas. Avance: **7/28 = 25%**.

## Motor activo del mapa

`libs.maplibre` fija `org.maplibre.gl:android-sdk-opengl:13.6.1`, con backend OpenGL ES estable. La dependencia y las diferencias con el artefacto genérico se documentan en [Mapa con MapLibre](MAPA_MAPLIBRE.md).

Antes de crear o inflar `MapView`, llama en el hilo principal a `MapsConfiguration.initialize(context)`, que inicializa MapLibre con el contexto de aplicación. `CitizenSecurityApplication.mapsReadinessChecker.check()` devuelve `Ready`, `RendererUnavailable` o `VerificationFailed`; `Ready` solo acredita inicialización local. La pantalla debe comprobar por separado carga de estilo/mapa, renderizado y fallos de red, conservando sus atribuciones visibles.

En `getMapAsync` se recibe `org.maplibre.android.maps.MapLibreMap`; el estilo se solicita con `mapa.setStyle(Style.Builder().fromUri(MapsConfiguration.STYLE_URL))`, importando `org.maplibre.android.maps.Style`. El compañero mantiene el ciclo de `MapView` y retira sus listeners antes de destruirla. [Mapa con MapLibre](MAPA_MAPLIBRE.md) muestra la composición para su pantalla sin añadirla al producto.

El coordinador de borrador conserva `prepareMap(token)` y el handle de esa vista. `attachMap(map)` y el puente de ubicación aceptan ahora `MapLibreMap`; la reserva sigue ocurriendo antes de pedir el mapa. El host añade/retira su listener por identidad, conserva listeners ajenos y devuelve `false` para un callback retirado. `MapLibreIncidentBinding` conserva el puente simple; `GoogleMapIncidentBinding` es un wrapper deprecated sobre MapLibre y su nombre no incorpora SDK Google. No se mezclan el puente simple y el coordinador de borrador sobre una misma selección.

La migración conserva GPS explícito, permiso preciso, regla de 5 km, borrador, SQLite y consulta por zona. En fase 10, `verification/maps` comprobó solo el motor en un paquete `testOnly` sin GPS y acreditó SDK configurado. El cierre posterior de **fase 11** comprobó el circuito de marcadores por zona sobre Liberty y obtuvo GPS físico válido en el laboratorio physical independiente: **10/28 = 35.7%, 4.3 en 2/4 y 4.5 en 3/4**, conservando hitos/pesos. La pantalla productiva y aceptación del flujo completo siguen pendientes; no se activan consultas o guardado reales. La [guía activa](MAPA_MAPLIBRE.md) conserva la limitación de nombres invisibles con renderizado software y el pase con `-gpu host`, sin modificar configuración/datos del AVD ni incluir un parche sin publicar. Las referencias históricas a claves Google pertenecen al proveedor anterior.

## Conectar incidentes por zona — fase 11

El nuevo puente conecta el `ReportMapViewModel` retenido a la vista concreta del mapa. Su host obtiene la región desde la proyección nativa y sustituye únicamente sus capas/fuente GeoJSON, sin modificar el pin de selección ni los recursos de otra conexión. La pantalla productiva y sus controles siguen pendientes del compañero; este contrato no activa la consulta oculta ni permite usar una base de prueba como repositorio productivo.

Obtén el modelo con `ViewModelProvider` y `CitizenSecurityApplication.reportMapViewModelFactory`, como en el antecedente de fase 9 siguiente. En el hilo principal, después de recibir el mapa de la vista vigente, la futura pantalla puede componer:

```kotlin
import com.example.citizensecurity.maps.MapLibreReportSceneHost
import com.example.citizensecurity.maps.ReportMapSceneBinding
import com.example.citizensecurity.maps.ReportMapSceneState
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

// Campos de la Activity o del dueño de la vista; nunca de un ViewModel o Application.
private var consultaMapa: ReportMapSceneBinding? = null

fun conectarConsultaZona(mapa: MapLibreMap, vista: MapView) {
    val anterior = consultaMapa
    val host = MapLibreReportSceneHost(mapa, vista)
    val nueva = ReportMapSceneBinding(
        owner = this,
        model = incidentes,
        host = host,
        onState = { estado: ReportMapSceneState -> representarEstadoDelMapa(estado) },
    )
    consultaMapa = nueva
    anterior?.close()
}

// Evento explícito de su futuro control de consulta, únicamente cuando esté autorizado.
fun consultarZonaVisible() {
    consultaMapa?.refreshVisible(
        types = tiposElegidos,
        statuses = estadosElegidos,
        limit = 200,
    )
}
```

`incidentes` es el modelo retenido obtenido con la fábrica compartida. `tiposElegidos` y `estadosElegidos` son sets de los enums de dominio, y `representarEstadoDelMapa` corresponde a los controles y presentación del compañero. El ejemplo describe el contrato futuro; no se añade a una Activity existente en este incremento. Antes de aceptar un `getMapAsync` tardío, la pantalla comprueba la identidad de la vista y de su solicitud; un callback de una MapView retirada no debe crear otro puente ni reclamar el modelo. Invalidar la solicitud y cerrar el puente anterior forma parte de retirar esa vista.

Crear el host/binding, observar el modelo, recrear la vista, cargar su estilo o volver a `RESUMED` **no consulta SQLite**. `refreshVisible` acepta el gesto solo si el dueño está `RESUMED` y es la conexión vigente; `false` significa que no entregó la solicitud. `true` significa gesto aceptado, no carga terminada. Si falta una proyección utilizable publica `ViewportUnavailable`; cuando el estilo/vista estén listos, un camera idle posterior puede seguir ese gesto previo. Sin gesto previo, los eventos del SDK permanecen sin lectura.

Después del primer gesto, camera idle consulta una región nueva conservando la instantánea de filtros y límite; un evento repetido de la misma región no abre otra lectura. Los filtros vacíos aceptan todos los tipos o estados. El límite predeterminado es 200, admite 1 a 500 y un valor inválido se rechaza antes de tocar la consulta. Otro `refreshVisible` renueva explícitamente los datos y sustituye los filtros; cambiar los sets originales no modifica una consulta ya recibida.

| Estado del puente | Contrato de representación |
| --- | --- |
| `Idle` | No hay consulta activa; observarlo no inicia una. |
| `Loading(query)` | Se retiraron los marcadores anteriores antes de solicitar esta zona. |
| `Ready(query, count, hasMore)` | El host representó los marcadores de esa consulta y región; una lista vacía deja `count = 0`. `hasMore` exige indicar que el resultado está limitado. |
| `Error(query, message)` | Falló la lectura o el SDK; permitir otro gesto de reintento. Un fallo del SDK invalida el seguimiento y muestra un mensaje genérico. `query` puede ser null si no se obtuvo región. |
| `ViewportUnavailable` | No existe una región utilizable o cambió antes de pintar; no se muestran marcadores de la consulta retirada. |
| `Closed` | La conexión terminó; callbacks y solicitudes posteriores son inertes. |

Antes de pintar `Ready`, el puente exige la identidad de su operación, la consulta esperada y que la región actual todavía coincida. No usa `previous` de otra zona. `hasMore` solo indica truncamiento; no es un token de continuación ni acredita que se cargaron todos los incidentes. Los marcadores conservan únicamente `id`, tipo, prioridad, estado y coordenadas, sin descripción, referencia de ubicación ni identidad personal.

Cada binding reclama un **token puro de propiedad en el modelo** antes de registrar callbacks. Al crear su sucesor sobre el mismo modelo, queda retirada la propiedad del anterior, incluso si ambas consultas tienen filtros y región iguales. La pausa, el cierre, un resultado o un callback de esa vista anterior no limpia ni pinta la consulta nueva. El modelo no retiene Activity, MapView, host, binding ni callbacks. Usa **un host nuevo por vista/conexión**, sin compartir una misma instancia entre dos bindings; sus capas y listeners pertenecen solo a ese host.

Al salir de `RESUMED`, el puente cancela la lectura vigente y limpia sus marcadores; regresar exige un `refreshVisible` nuevo y explícito. Conserva el registro de cámara hasta el cierre, pero ignora sus eventos inactivos. `close()` retira los callbacks, cancela su colección y lectura, limpia/cierra su host y mantiene abierto el repositorio compartido. El dueño llama a `close` **antes de `MapView.onDestroy()`**, también al retirar solo esa vista. Un fallo de limpieza del SDK puede propagarse después de intentar todas las retiradas; usa `finally` para garantizar la destrucción de MapView. No llames directamente a `model.clear()` desde el cierre de una vista anterior: la liberación del token se realiza dentro del puente.

`MapLibreReportSceneHost(mapa, vista)` sigue las recargas de estilo por los callbacks de esa MapView. Si se usa el constructor con solo `mapa`, la pantalla debe llamar `host.onStyleLoaded(estilo)` en el callback correspondiente de cada estilo **vigente** y retirar su propio callback al destruir la vista. Ambos contratos conservan los controles de atribución visibles. La [guía MapLibre](MAPA_MAPLIBRE.md) completa la composición; [fase 11](FASE_11.md) registra su cierre del 8 de octubre por separado de los antecedentes, sin nueva publicación.

El cierre aprobó **325 casos distintos**: 273 JVM app, 34 Android flow, ocho JVM monitor y diez Android mapas; **57 definiciones nuevas**, 31 coordinador, 20 host y seis nativas. App/nativos se repitieron tras corregir dos aserciones de precisión del SDK, sin modificar producción ni las reglas GPS; flow/monitor se ejecutaron primero y se reutilizaron al final, sin doble recuento. Debug/release, firma debug v2 y lint sin errores aprobados: 19 advertencias app, dos flow, dos physical y ocho mapas. Se revisaron la captura y los objetos nativos de **dos círculos de reportes de prueba** sobre Liberty, sin abrir los datos del producto.

La prueba física manual por gesto, con la fuente productiva real y el APK técnico inicial de captura de **15 s**, pasó de un primer TIMEOUT a **VALIDATED** cerca de una ventana: precisión aproximada **6.668 m**, antigüedad **46 ms**, cercanía permitida, distancia rechazada y lectura no simulada; sin coordenadas persistidas o reportes creados. El recibo final comprobó **63 archivos base, 18 APK anteriores, HEAD/índice y productos instalados**, incluidos hashes y fechas. Estos resultados acreditan únicamente captura del dispositivo y mapa por zonas/marcadores, **10/28 = 35.7%**; la futura pantalla del compañero, sus controles e integración visual productiva siguen pendientes y los accesos reales permanecen ocultos.

## Antecedente de fase 9: consultar la zona del futuro mapa

El mapa de incidentes utiliza un modelo de consulta independiente del borrador y de la selección de su punto. Obtén `ReportMapViewModel` del propietario de esa pantalla, con la fábrica de `CitizenSecurityApplication`; no abras otro SQLite ni cierres el repositorio compartido:

```kotlin
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.domain.MapRegion
import com.example.citizensecurity.domain.ReportMapQuery
import com.example.citizensecurity.maps.ReportMapState
import com.example.citizensecurity.maps.ReportMapViewModel

val app = application as CitizenSecurityApplication
val incidentes = ViewModelProvider(this, app.reportMapViewModelFactory)[
    ReportMapViewModel::class.java
]
```

La fábrica es lazy y comparte el mismo repositorio productivo protegido que `reportRepository`. El contrato anterior continúa expuesto como `ReportRepository`; la proyección geográfica usa `ReportMapRepository`. Crear la fábrica/modelo y observar `state` no lee la base. Las acciones del modelo se entregan en el hilo principal.

En fase 9, antes de existir el binding de escena, el contrato directo del modelo recibía **`load(query)`** con una región construida por su consumidor. El ejemplo siguiente conserva ese contrato inferior para referencia; **la integración vigente del mapa utiliza `ReportMapSceneBinding.refreshVisible(...)` por un gesto RESUMED**, y el binding decide las llamadas al modelo y los camera idle posteriores. No conectes este fragmento a otro listener de cámara ni lo ejecutes automáticamente por construcción, recreación o resume:

```kotlin
val consulta = ReportMapQuery(
    region = MapRegion(
        south = surVisible,
        north = norteVisible,
        west = oesteVisible,
        east = esteVisible,
    ),
    types = tiposElegidos,
    statuses = estadosElegidos,
    limit = 200,
)
consultaVisible = consulta
incidentes.load(consulta)
```

`surVisible`, `norteVisible`, `oesteVisible` y `esteVisible` son límites finitos de su mapa, con `south <= north`, latitudes en −90..90 y longitudes en −180..180. Un `west > east` permite cruzar el antimeridiano; los extremos −180 y 180 representan el mismo meridiano. `tiposElegidos` y `estadosElegidos` son sets de los enums existentes; vacíos aceptan cualquier valor de su filtro. La consulta toma una instantánea de esos sets. El límite predeterminado es 200 y admite 1 a 500. `consultaVisible` representa la consulta que la vista desea mostrar y se conserva con el estado de su mapa.

Este fragmento era la composición manual de fase 9 y no añadió un listener de cámara, controles o lectura automática. En el protocolo de **fase 11**, construir el binding sucesor reclama la escena e invalida la consulta anterior; una vista recreada no repinta un `Ready` retenido ni consulta hasta recibir su propio gesto `refreshVisible` en RESUMED. Observar el modelo o un evento del SDK sin gesto previo no sustituye esa acción.

Observa `state: StateFlow<ReportMapState>` con el ciclo de vida de la vista. La colección representa resultados y **no llama a `load`**:

| Estado | Comportamiento de la futura vista |
| --- | --- |
| `Idle` | Mostrar que no hay consulta vigente; no iniciar una al observar. |
| `Loading(query, previous)` | Mostrar carga solo si `query == consultaVisible`. `previous` mantiene su propia `query`; no atribuir sus marcadores a la región nueva. |
| `Ready(query, page)` | Representar `page.markers` solo si `query == consultaVisible`. Una salida vacía limpia los resultados anteriores. |
| `Error(query, message, previous)` | Representar el fallo de esa consulta y permitir otro gesto de reintento. No mostrar `previous` como respuesta de otra zona o filtros. |

Los marcadores contienen `id`, `type`, `priority`, `status`, `latitude` y `longitude`; no contienen descripción, referencia de ubicación ni identidad de usuario. `page.hasMore == true` indica que hay coincidencias adicionales fuera del máximo devuelto. Representa que la salida está limitada; no anuncies que todos los incidentes están cargados. Este contrato no ofrece tokens de continuación ni otra página.

En el contrato inferior del modelo, una consulta nueva cancela la pendiente y descarta sus resultados tardíos; repetir la misma mientras carga no abre otra. Después de finalizar, una llamada explícita permite actualizarla. `clear()` cancela, descarta el último resultado y vuelve a `Idle` sin cerrar el repositorio; permite nuevas consultas después. Si la lectura vigente se cancela por otra causa, recupera el último `Ready` o `Idle`, conservando la consulta de ese resultado. Liberar el ViewModel cancela e ignora nuevas cargas de esa instancia. Con la escena de fase 11, deja carga/limpieza/liberación al binding: el cierre de una vista anterior no debe llamar directamente a `model.clear()` y limpiar su sucesora.

El modelo reserva el job lazy **antes de emitir `Loading`**: si un consumidor inmediato limpia el modelo o pide otra zona desde ese estado, la lectura sustituida no se inicia. Dos regresiones cubren ambas acciones y forman parte de las 19 pruebas nuevas del modelo. La colección de la futura pantalla sigue siendo solo representación; el evento que controla su mapa decide cuándo pedir otra región.

La lectura filtra directamente SQLite y excluye los reportes sin coordenadas, sin cargar todos los reportes para filtrarlos en la pantalla. No inserta, cambia estados, confirma un punto ni obtiene GPS. [Fase 9](FASE_9.md) conserva ese contrato; fase 10 comprobó el motor y **fase 11** completó técnicamente el circuito de marcadores por zona, con GPS físico comprobado por separado. El avance vigente es **10/28 = 35.7%**, con **4.3 en 2/4 y 4.5 en 3/4**. La pantalla productiva y sus controles corresponden al compañero y siguen pendientes, junto con su aceptación; los accesos de consulta permanecen ocultos hasta acordar su activación. La [guía MapLibre](MAPA_MAPLIBRE.md) documenta la conexión vigente sin clave Google y mediante gesto explícito.

## Borrador editable de 0.4.0a

El formulario y el mapa comparten `ReportDraftViewModel` con **el mismo `ViewModelStoreOwner` y clave**. En una `ComponentActivity`, su fábrica utiliza los `CreationExtras` de Android para obtener `SavedStateHandle`:

```kotlin
val borrador = ViewModelProvider(this, ReportDraftViewModel.factory())[
    ReportDraftViewModel::class.java
]
```

Si el mapa está en un fragmento de esa actividad, obtiene el mismo modelo desde el propietario compartido; no crea uno con un propietario o clave distintos. Una actividad diferente no comparte el `ViewModelStore` automáticamente. No se colocan modelos, puentes ni tokens en `Application`, `Bundle` o una `MapView` para simular esa relación; el compañero define su navegación conservando un propietario común para este flujo.

La pantalla conecta cambios a `setType`, `setPriority`, `setDescription`, `setOccurredAt` y `setLocationReference`. Los primeros, la fecha y `reset()` devuelven `Boolean` para indicar que la edición se aceptó; texto devuelve `ReportDraftEditResult.Accepted`, `Closed` o `Rejected(field, message)`. Descripción admite **1.000 puntos Unicode** y referencia **200**, contando espacios exteriores; el modelo rechaza sin truncar. Ante `Rejected`, el control conserva el texto escrito y representa `state.inputErrors`, mientras el modelo conserva el último valor aceptado. No debe sobrescribir automáticamente la entrada rechazada con ese valor anterior. Una edición válida del campo elimina su error.

La fecha es un `Instant` que la pantalla convierte desde su fecha y zona; el modelo no inventa una hora para completar el formulario. `review(now)` devuelve `ReportDraftReview.Valid(NewReport)` o `Invalid(Map<ReportDraftField, String>)`. Revisa los campos y bloquea una selección del mapa todavía pendiente; `Valid` no acredita evidencia GPS actual ni inserta en SQLite. Observar `state` representa el borrador, sin llamar a guardar ni a obtener ubicación.

## Coordinar el borrador con la ubicación

La fase 8 reúne la composición manual de 0.4.0a en un contrato. Obtén **los mismos modelos retenidos y claves** del propietario compartido, y crea un solo flujo y coordinador en `onCreate`, incondicionalmente, **antes de `STARTED` y en orden estable**:

```kotlin
val app = application as CitizenSecurityApplication
val borrador = ViewModelProvider(this, ReportDraftViewModel.factory())[
    ReportDraftViewModel::class.java
]
val permisos = ViewModelProvider(this, LocationPermissionViewModel.factory())[
    LocationPermissionViewModel::class.java
]
val ubicacion = ViewModelProvider(
    this,
    app.incidentLocationViewModelFactory(borrador.state.value.location),
)[IncidentLocationViewModel::class.java]
val flow = IncidentLocationFlowBinding.forActivity(this, permisos, ubicacion)
val coordinador = ReportLocationFlowBinding(this, flow, borrador)
```

Este fragmento pertenece a una `ComponentActivity` y muestra la composición para la futura pantalla. No añade controles o navegación al producto. El puente y coordinador pertenecen a esa instancia de Activity y se crean de nuevo al recrearla, mientras sus modelos permanecen con `ViewModelProvider`. No se conservan en un ViewModel, `Application`, `Bundle` o `MapView`. No se registra un segundo puente de permisos para la misma selección.

## Abrir el mapa y recuperar una apertura retenida

Por un gesto visible, `coordinador.openSelection()` entrega `ReportLocationSelectionRequest?`. Abre una sesión en los mismos modelos, invalida la apertura y captura anteriores y exige renovar; no inicia GPS ni confirma un punto. Un dueño inactivo rechaza la apertura sin dejar una selección pendiente.

Después de recrear la Activity, usa **`coordinador.currentSelection`** para representar una selección que ya estaba abierta. No llames a `openSelection()` por recreación, `ON_RESUME`, observación de estado o concesión de permiso: eso sería una apertura diferente. La apertura y el pin se conservan cuando Android retiene los modelos; el paso por `ON_STOP` exige otra captura explícita.

```kotlin
// Al pulsar el control de abrir mapa:
val apertura = coordinador.openSelection() ?: return
// El compañero representa su mapa para esta apertura.

// Al recrear su pantalla:
val aperturaRetenida = coordinador.currentSelection
// Representar aperturaRetenida si existe; no iniciar otra selección.
```

Retener el ViewModel por recreación y recuperar campos de `SavedStateHandle` son caminos distintos. Android puede proporcionar primitivos del borrador a **un modelo nuevo**; ese estado depende de la tarea y no es almacenamiento permanente, según [Android Developers](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-savedstate). Nuestro modelo no serializa token, selección pendiente, permiso ni evidencia GPS. Un modelo nuevo recupera campos sin una apertura vigente; las coordenadas restauradas siguen siendo entrada que debe comprobarse con evidencia actual antes de un futuro guardado. Valores incompatibles producen errores de campo.

## Conectar una vista y sus acciones

Antes de pedir el mapa asíncronamente, reserva una conexión para **el coordinador de esa Activity y el token de esa apertura**. Esa reserva distingue dos vistas aunque compartan token:

```kotlin
val coordinadorDeEstaVista = coordinador
val tokenDeEstaApertura = apertura.token
val enlace = coordinadorDeEstaVista.prepareMap(tokenDeEstaApertura) ?: return
// Conservar enlace y cerrarlo al retirar esta vista.
mapView.getMapAsync { mapa ->
    enlace.attachMap(mapa)
}
```

`prepareMap(token)` devuelve `ReportLocationMapBinding?` y cierra la conexión anterior **antes de solicitar el nuevo mapa**; rechaza con `null` una apertura o dueño que ya no corresponden. `enlace.attachMap(mapa)` devuelve `Boolean` y solo conecta si esa reserva sigue vigente. Una entrega tardía del mapa anterior no sustituye el listener de la vista nueva, aunque comparta token. `coordinador.attachMap(token, mapa)` es una operación directa para un mapa ya disponible; no se usa dentro de un callback asíncrono de una vista antigua.

El coordinador mantiene un solo attachment. Reservar/conectar el mapa puede ocurrir en `CREATED` o `STARTED`: representa la vista sin entregar gestos ni iniciar GPS. El registro del listener pertenece al attachment; no instales además `IncidentLocationMapBinding`, `MapLibreIncidentBinding` o el wrapper deprecated `GoogleMapIncidentBinding` para seleccionar sobre ese mismo flujo. El host conserva los listeners ajenos y un callback retirado devuelve `false`. Un click o `close()` tardío del attachment anterior no afecta al nuevo, incluso si ambos utilizaron el mismo token o `MapLibreMap`.

Los controles del compañero usan el token capturado al construir su vista:

| Evento | Operación |
| --- | --- |
| Elegir un punto | `coordinador.select(apertura.token, latitud, longitud)`; los clicks del attachment ya pasan por este contrato. |
| Solicitar permiso preciso | `coordinador.requestPermission(apertura.token, explanationAccepted)`; representa `LocationPermissionAction`. |
| Renovar ubicación del teléfono | `coordinador.refreshLocation(apertura.token)`, por un gesto separado. |
| Confirmar | `coordinador.confirm(apertura.token)`; representa el resultado o la ausencia de confirmación. |
| Cancelar | `coordinador.cancelSelection(apertura.token)`; volver solo si acepta esa cancelación. |
| Retirar la vista del mapa | `enlace.close()`; no cerrar el registro de permisos al retirar solo esa vista. |

La comprobación del token ocurre **antes de llamar al flujo**. Un evento de otra apertura no confirma/cancela una selección nueva ni solicita permiso o GPS. Los eventos requieren el dueño visible en `RESUMED`; un `Boolean` de selección o renovación indica que se aceptó entregar el gesto, sin asegurar validez del punto o éxito de captura. `state` de ubicación y permiso proporciona el resultado que la pantalla representa.

`confirm(token)` evalúa el punto mediante el flujo actual: puede devolver `NoSelection`, `InvalidCoordinates`, `Blocked`, `Confirmed` o `null`. Un `Confirmed` aplica las coordenadas al borrador y conserva **sus campos y referencia actuales**, incluidas ediciones hechas mientras el mapa estaba abierto. Una confirmación bloqueada no vuelve al formulario como éxito. No construyas un DTO `Confirmed` en la pantalla ni llames por separado a `flow.confirm()` o `borrador.applyLocationSelection()` en esta conexión: el coordinador realiza esa secuencia.

Cancelar conserva las coordenadas originales y las ediciones del borrador; no deshace un guardado. Cerrar el attachment detiene su captura sin cerrar permisos de la Activity. Destruir el dueño cierra su coordinador y bindings; la apertura del borrador retenido se conserva para recreación. Cualquier cierre es idempotente. Conceder permiso, recuperar estado y volver a la pantalla nunca capturan automáticamente.

Para un gesto de limpiar con selección pendiente, primero cancela **esa apertura** y luego reinicia el borrador:

```kotlin
if (coordinador.cancelSelection(apertura.token)) {
    borrador.reset()
}
```

No llames a `reset()` desde un control anterior antes de comprobar su token: reiniciar el borrador directamente invalidaría la apertura vigente y no constituye una cancelación del flujo GPS. Sin selección pendiente, el control actual puede reiniciar solo el borrador.

La evidencia del dispositivo sigue siendo independiente del pin: permiso preciso, GPS habilitado, precisión máxima de 100 m, lectura monotónica no futura de hasta dos minutos, sin marca de simulación y **distancia aproximada + precisión <= 5.000 m**. Confirmar aplica un punto; no crea reporte, folio ni inserción. Guardado real y consulta permanecen ocultos; el ejemplo fijo en memoria no recibe este borrador. [Fase 8](FASE_8.md), [fase 7](FASE_7.md) y [Proximidad](PROXIMIDAD_REPORTES.md) conservan alcance y límites.

La composición manual publicada en 0.4.0a y sus APIs inferiores se conservan como antecedentes en [Fase 7](FASE_7.md). El contrato recomendado para conectar la selección al borrador es el coordinador; **el corte de fase 8** no acreditó entonces mapa visible, GPS físico ni aceptación funcional. Fase 11 comprobó después los marcadores por zona y captura física por separado, sin acreditar aún la pantalla productiva o su aceptación. Consulta de zona y selección del punto mantienen sus propios contratos y gestos.

## Contrato conservado para el futuro guardado

Los ejemplos siguientes documentan el modelo de guardado ya existente. No activan su acceso en la aplicación ni sustituyen la revisión de ubicación actual por una confirmación anterior.

## Obtener la instancia

En `MainActivity`, usa la propiedad ya preparada:

```kotlin
val modelo = reportViewModel
```

`CitizenSecurityApplication.reportViewModelFactory` es una fábrica compartida que recibe `reportRepository`. Otra actividad que vaya a mostrar reportes puede obtener su propia instancia del ViewModel con la misma fábrica:

```kotlin
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.report.ReportViewModel

val app = application as CitizenSecurityApplication
val modelo = ViewModelProvider(this, app.reportViewModelFactory)[ReportViewModel::class.java]
```

La fábrica se construye con `ReportViewModel.factory(repository)`. Android utiliza `ViewModelProvider` para asociar el ViewModel a la actividad y proporcionar sus dependencias mediante una fábrica. [Fábricas de ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-factories).

El repositorio pertenece a `CitizenSecurityApplication` y se comparte durante el proceso. Una pantalla no debe crear otra instancia por cada actualización ni cerrar la compartida. La fábrica está disponible sin abrir SQLite ni guardar un reporte al iniciar la actividad.

## Construir la entrada del formulario

La pantalla convierte su selección visible a los valores del contrato, sin usar el texto traducido como nombre de un enum:

| Selección visible | Valor de `IncidentType` |
| --- | --- |
| Emergencia | `EMERGENCY` |
| Robo | `THEFT` |
| Accidente | `ACCIDENT` |
| Incendio | `FIRE` |
| Riesgo | `RISK` |
| Otro | `OTHER` |

La prioridad se conecta de la misma forma: Baja → `Priority.LOW`, Media → `Priority.MEDIUM`, Alta → `Priority.HIGH`. Las etiquetas son una propuesta para la pantalla; los valores del contrato son los existentes.

Cuando el usuario pulse el futuro botón de guardar, la pantalla construirá:

```kotlin
val borrador = NewReport(
    type = tipoSeleccionado,
    priority = prioridadSeleccionada,
    description = descripcionEscrita,
    occurredAt = fechaDelIncidente,
    location = ReportLocation(
        reference = referenciaEscrita,
        latitude = latitudSeleccionada,
        longitude = longitudSeleccionada,
    ),
)
modelo.save(borrador)
```

`tipoSeleccionado` y `prioridadSeleccionada` son los enums elegidos; `fechaDelIncidente` es un `Instant` convertido desde la fecha y zona de la pantalla. Las coordenadas son `Double?`: ambas ausentes para usar solo una referencia, o ambas presentes para un punto seleccionado. Las reglas completas están en [Contrato de guardado](GUARDAR_REPORTE.md).

Este fragmento conserva el contrato `NewReport` de la base anterior: recibe el par de coordenadas como datos y ese contrato de dominio no depende del SDK. La preparación actual usa MapLibre Native/OpenFreeMap sin clave Google y tiene el circuito técnico de marcadores por zona comprobado; conectarlo a la pantalla productiva, sus controles y el formulario sigue pendiente. El ejemplo describe el futuro botón de guardado, todavía oculto: el login no llama a `save` ni tiene listeners nuevos para insertar reportes.

## Observar el resultado

`save(draft: NewReport)` inicia la operación en el ViewModel. La pantalla lee `state: StateFlow<ReportSaveState>`; el flujo es de solo lectura y se obtiene con estos estados:

| Estado | Acción de la futura pantalla |
| --- | --- |
| `Idle` | Dejar disponible el botón de guardar. |
| `Saving` | Deshabilitar el botón y mostrar que se está guardando. |
| `Saved(report)` | Mostrar el folio `report.id` y confirmar que el repositorio devolvió el reporte. |
| `Invalid(errors)` | Habilitar la corrección y mostrar cada mensaje junto a su campo. |
| `Error(message)` | Habilitar el reintento y mostrar el mensaje de almacenamiento. |

Los errores de validación se identifican con `ReportField.DESCRIPTION`, `LOCATION_REFERENCE`, `LOCATION_COORDINATES` y `OCCURRED_AT`. `Invalid.errors` es un `Map<ReportField, String>`; no se necesita interpretar excepciones ni detalles de SQLite desde la pantalla.

En la futura actividad de reportes, la observación puede seguir este patrón:

```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.citizensecurity.report.ReportSaveState
import kotlinx.coroutines.launch

lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        modelo.state.collect { estado ->
            botonGuardar.isEnabled = estado != ReportSaveState.Saving
            when (estado) {
                ReportSaveState.Idle -> mostrarFormularioDisponible()
                ReportSaveState.Saving -> mostrarGuardadoEnCurso()
                is ReportSaveState.Saved -> mostrarFolio(estado.report.id)
                is ReportSaveState.Invalid -> mostrarErrores(estado.errors)
                is ReportSaveState.Error -> mostrarError(estado.message)
            }
        }
    }
}
```

El botón y las funciones `mostrar…` representan componentes que el compañero implementará en la pantalla; este ejemplo no se ha añadido al login. `repeatOnLifecycle` observa mientras la actividad está al menos en `STARTED` y detiene la colección cuando deja ese estado. Para un fragmento, usa el `lifecycleScope` y `repeatOnLifecycle` de `viewLifecycleOwner`. [Corrutinas y ciclo de vida](https://developer.android.com/topic/libraries/architecture/coroutines), [patrón para Views](https://developer.android.com/topic/libraries/architecture/views/coroutines-views).

La colección solo representa el estado: no debe llamar a `save`. `StateFlow` vuelve a entregar su estado actual al observar, por lo que mostrar `Saved` debe ser una actualización de la vista y no una nueva inserción automática. El ViewModel bloquea llamadas simultáneas mientras está en `Saving`; una nueva llamada después de terminar constituye otro guardado.

El guardado usa el ámbito del ViewModel y propaga la cancelación. Al limpiar el ViewModel se cancela su operación, se ignoran nuevas llamadas desde esa instancia y el repositorio compartido permanece utilizable. La pantalla no cierra el repositorio. La permanencia tras reabrir SQLite se demuestra con la instrumentación existente; no se ha comprobado todavía una rotación real con un formulario ni un flujo completo de guardar desde la interfaz.

## Antecedente de verificación del puente

El 3 de octubre de 2026 se comprobaron el puente y su integración técnica: 6 pruebas JVM del ViewModel aprobadas, 9 instrumentadas de SQLite ejecutadas y aprobadas en API 37, lint sin errores y APK `0.2.1` generado. Lint de datos no tuvo incidencias; `app` conserva 13 advertencias visuales. Las 32 pruebas de dominio mantuvieron su resultado anterior mediante `UP-TO-DATE`, sin una nueva ejecución de esos casos. La evidencia completa y el artefacto están en [Validación](VALIDACION.md).

Los XML registran las 6 pruebas del puente a `2026-10-03T16:48:36.500Z` y las 9 instrumentadas a `2026-10-03T16:47:13Z`. El resultado reutilizado del dominio conserva `2026-10-03T05:39:56.971Z`.

Aquella comprobación fue técnica; no probó un flujo visual completo ni una rotación real de la futura pantalla. En ese corte `0.2.1` aún no tenía etiqueta ni Release; su publicación posterior se conserva en [Versiones](RELEASES.md). El formulario, sus listeners, el mapa y la autenticación continúan pendientes del compañero. Fase 3 y `0.3.0` permanecen en `Unreleased`, con entrega y aceptación funcional pendientes.
