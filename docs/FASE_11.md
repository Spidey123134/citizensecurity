# Fase 11: incidentes por zona y marcadores en MapLibre

Preparación **Unreleased, local**, iniciada el **7 de octubre de 2026** y cerrada técnicamente el **8 de octubre**, posterior al cambio de proveedor de [fase 10](FASE_10.md). La oficial conserva **0.4.0a / código Android 19**. El metadato de las compilaciones técnicas no las convierte en una Release ni autoriza subir, integrar, etiquetar o publicar este trabajo. **325 casos distintos aprobados y 57 definiciones nuevas**, compilación debug/release, firma debug y lint sin errores. [Validación](VALIDACION.md) registra el cierre y [Avance](AVANCE.md) conserva los 28 hitos/pesos: **10/28 = 35.7%**, con Mapas 3/4 y Geolocalización 2/4.

Corresponde al apartado **4.5 Mapas** del plan y comprueba por separado captura física de **4.3 Geolocalización**. Une la lectura geográfica de [fase 9](FASE_9.md) con el motor MapLibre/OpenFreeMap ya configurado, para representar los marcadores mínimos de la región visible. La pantalla, diseño de pines y controles, navegación y login siguen a cargo del compañero. No se conecta una nueva pantalla productiva ni se activa guardado real o consulta: sus accesos permanecen ocultos. La cámara del laboratorio usa coordenadas fijas de prueba, sin convertirlas en evidencia del teléfono.

## Circuito de lectura

El circuito conserva una única composición de datos y separa sus responsabilidades:

| Paso | Responsabilidad |
| --- | --- |
| `SqliteReportRepository.queryMarkers(query)` | Filtrar directamente la base por región, tipo y estado; devolver una salida acotada con `hasMore`, sin cargar todos los reportes ni cambiar su estado. |
| `ReportMapViewModel` | Ejecutar la lectura explícita, cancelar la petición sustituida y publicar estados que conservan su consulta. Mantener una identidad pura del dueño de la escena. |
| `ReportMapSceneBinding` | Convertir un gesto visible y su proyección en consulta; seguir movimientos posteriores; retirar resultados anteriores y validar la identidad/región antes de entregar marcadores. |
| `MapLibreReportSceneHost` | Obtener la región nativa y sustituir solo sus recursos GeoJSON/capa/listeners, sin consultar, guardar ni capturar GPS. |
| `MapLibreMap` y estilo Liberty | Representar la capa propia sobre el mapa base real, conservando atribuciones y recursos ajenos. |

La fábrica `CitizenSecurityApplication.reportMapViewModelFactory` comparte el repositorio productivo existente. La pantalla no abre otra base ni lo cierra al destruir el mapa. El laboratorio utiliza explícitamente SQLite en memoria con fixtures propias y una fábrica separada; esa composición no se entrega al producto.

## API y activación

El constructor público es `ReportMapSceneBinding(owner, model, host, onState = {})`. El dueño pertenece a la vista y las acciones se serializan en el hilo principal. `ReportMapSceneHost` expone `visibleRegion`, `setCameraIdleListener`, `renderMarkers`, `clearMarkers` y `close`; no abre consultas por su cuenta. [Acoplar interfaz](ACOPLAR_INTERFAZ.md) muestra ejemplos Kotlin para la futura pantalla.

`refreshVisible(types = emptySet(), statuses = emptySet(), limit = 200)` solo recibe el gesto cuando el dueño está **RESUMED** y es la escena vigente. Devuelve `false` si está inactivo; `true` indica entrega del gesto, no datos cargados. Los filtros se copian antes de consultar el SDK. Los sets vacíos aceptan cualquier tipo o estado; el límite admite **1 a 500** y un valor inválido se rechaza antes de modificar la consulta.

Construir, observar, recrear la vista, cargar su estilo o volver a `RESUMED` no inicia una lectura. Después del gesto explícito, los eventos camera idle pueden consultar regiones nuevas con los mismos filtros; repetir la misma región no abre otra consulta. Otro gesto renueva los datos o sustituye los filtros. Si todavía no existe una proyección utilizable, no se lee la base: un idle posterior solo puede seguir el gesto que ya se recibió.

Antes de pedir otra región/filtros se retiran los marcadores anteriores. Un resultado exige la operación vigente, la consulta esperada y que la región nativa actual todavía coincida. No se pinta `previous` como resultado de otra zona. Una respuesta vacía sustituye los marcadores por una lista vacía; `hasMore` indica truncamiento, sin token de continuación ni promesa de lista completa.

## Estados de la conexión

| Estado | Significado |
| --- | --- |
| `Idle` | No existe consulta activa; observarlo no renueva la lectura. |
| `Loading(query)` | Se solicitó esa región y se retiraron los marcadores anteriores. |
| `Ready(query, count, hasMore)` | El host recibió la lista de esa consulta; `count = 0` es un resultado válido. El renderizado visual se verifica además en el laboratorio nativo. |
| `Error(query, message)` | La lectura o el SDK falló. El SDK usa un mensaje genérico y desactiva el seguimiento; `query` puede ser null si no se obtuvo la región. |
| `ViewportUnavailable` | Falta proyección utilizable o ya cambió al recibir el resultado; no se pintan datos de la región retirada. |
| `Closed` | La conexión terminó y sus callbacks posteriores son inertes. |

Un error permite otro gesto de reintento. La salida no expone descripción, referencias de ubicación ni identidad del usuario; los datos del marcador son únicamente folio, tipo, prioridad, estado y coordenadas.

## Propiedad entre vistas y limpieza

Cada binding reclama un token interno puro en `ReportMapViewModel` **antes** de conectar callbacks. Reservar el sucesor retira la propiedad anterior; no se guardan Activity, MapView, host, binding o callbacks en ese token. Una consulta con región/filtros iguales tampoco recupera la identidad anterior. `claimScene`, `isSceneOwner` y `releaseScene` son operaciones internas: la pantalla utiliza el binding, sin administrar esos tokens por su cuenta.

Si una vista anterior se pausa/cierra o recibe un callback/resultado tardío después de ser sustituida, no limpia, cancela ni representa la consulta nueva. Su cierre retira únicamente su host. Usa **un host nuevo por vista/conexión**, sin compartir la misma instancia entre dos bindings. El dueño comprueba la identidad de sus solicitudes `getMapAsync` antes de aceptar una entrega: un callback de una vista retirada no debe crear una escena que reclame el modelo vigente.

El host asigna IDs distintos a su fuente y capa, conserva recursos/listeners ajenos y copia los datos a GeoJSON. `MapLibreReportSceneHost(map, mapView)` observa las recargas de estilo de esa vista. Si solo se usa `MapLibreReportSceneHost(map)`, su dueño llama `host.onStyleLoaded(style)` desde el callback del estilo vigente. Recargar estilo puede reconectar datos ya leídos, sin abrir otra consulta; limpiar o cerrar retira también esos datos deseados para impedir que reaparezcan después.

Al salir de **RESUMED** se cancela la lectura vigente y se limpian los marcadores; volver exige un gesto nuevo. El dueño llama `close()` antes de `MapView.onDestroy()`, también al retirar solo el mapa. El cierre cancela su colección, libera el modelo únicamente si conserva el token y retira sus callbacks/recursos. Intenta todas las retiradas incluso si el SDK falla; puede propagar el primer fallo al llamador, con la conexión ya cerrada. Usa `finally` para garantizar la destrucción de MapView y conserva abiertos los datos compartidos. [Mapa con MapLibre](MAPA_MAPLIBRE.md) detalla ciclo de vida y atribución.

## Pruebas y cierre técnico

Se añadieron **57 definiciones de prueba**, separadas del cierre de fase 10:

| Área | Definiciones nuevas | Qué comprueban |
| --- | ---: | --- |
| `ReportMapSceneBindingTest` | 31 JVM | Gesto y visibilidad, ausencia de consulta automática, filtros/límite/lista vacía/hasMore, errores, región cambiada, cancelación ignorada, reentradas y sucesores sobre el mismo modelo. |
| `MapLibreReportSceneHostTest` | 20 JVM | GeoJSON mínimo, proyección y antimeridiano, recursos propios/ajenos, sustitución, recarga, cierre, fallos y reentrada del adaptador. |
| `ReportMapSceneNativeTest` | 6 Android | SQLite en memoria → modelo → binding → GeoJSON/capa del SDK sobre Liberty; gesto real, cambio de cámara, filtros/límite/vacío, recarga, retirada y recreación de la escena. |

El cierre aprobó **273 JVM app, 34 Android del flujo, ocho JVM del monitor y diez Android del mapa: 325 casos distintos**, sin fallos, errores u omisiones en los XML finales. Las 34 y ocho se ejecutaron en el primer pase; app y mapa se repitieron tras corregir dos comprobaciones de precisión, sin sumar repeticiones. Dominio/datos y su instrumentación conservan los resultados anteriores, sin atribuirlos a este corte. GPS manual no se suma como test automatizado.

El primer pase registró una aserción JVM que exigía igualdad de bits después de serializar y una nativa que exigía mayor precisión que la geometría renderizada. El serializador GeoJSON redondea a siete decimales; el test exige exactitud antes de serializar y el redondeo concreto después. `queryRenderedFeatures` reconstruye coordenadas de teselas enteras de extensión 8192: el test usa **1.4×10⁻⁶ grados** en zoom 15, menos de 0.16 m, conservando IDs, orden longitud/latitud y propiedades exactas. Esta [conversión del SDK](https://github.com/maplibre/maplibre-native/blob/android-v13.6.1/src/mln/tile/geometry_tile_data.cpp#L122) y su [extensión de tesela](https://github.com/maplibre/maplibre-native/blob/android-v13.6.1/include/mln/util/constants.hpp#L24) justifican el margen. Solo cambiaron las aserciones; GPS, SQLite, coordenadas guardadas y radio de 5 km conservan sus reglas. El laboratorio también usa plurales correctos y pasó compilación/lint tras ese ajuste de texto.

Debug/release compilan y firma debug v2 válida. Lint sin errores: app19 advertencias anteriores por variante; flow2, physical2 y mapas8 anteriores. El recibo ignorado `.gradle/validacion/cierre-fase11.json` conserva **18 APK anteriores**, HEAD/índice y 63 archivos de base. La app instalada conserva 0.3.1/código8 en el AVD y 0.4.0/código7 en el teléfono, con hashes y fechas intactos. Los artefactos nuevos son locales y separados de la oficial.

`verification/maps` continúa siendo un paquete **testOnly** separado del producto. Su Activity técnica añade controles solo al laboratorio y requiere el mapa público real; las fixtures se insertan mediante pasos explícitos de las pruebas en SQLite en memoria. No solicita permiso GPS, abre la base productiva, identifica usuarios ni representa un reporte ficticio como incidente real. La comprobación del mapa requiere carga/renderizado nativos y revisión visual de la captura, conservando el límite de GPU software documentado en fase 10.

## GPS físico: comprobación independiente

La variante técnica **physical** de `verification/location`, con paquete `com.example.citizensecurity.verification.location.physical`, permite probar en el teléfono del usuario la fuente Android real. Mantiene permiso preciso y captura **por gesto**, reutilizando `AndroidPhoneLocationSource`, `PhoneLocationRefresh`, `PhoneLocationEvidence` y `NearbyIncidentPolicy`. No incluye mapa, guarda reportes ni usa una lectura simulada como evidencia. Esta prueba separada no activa los accesos reales de la app productiva.

El primer intento en el Motorola Edge 50 Fusion, **API 36**, terminó en **TIMEOUT** al agotar 15 segundos, sin ubicación inventada ni autorización de reporte. El usuario se acercó a una ventana y pulsó de nuevo: **VALIDATED**, precisión estimada **6.668 m** y edad **46 ms al evaluar**, sin marca de simulación. El punto del propio fix fue permitido y un punto ficticio a unos 11 km se rechazó como fuera del radio. No se alteró el tiempo de espera ni ninguna regla para obtener ese resultado. La prueba observa una captura física y acredita únicamente ese hito de 4.3; no completa sus estados/permisos ni el flujo productivo.

La evidencia manual inicial se conserva en `.gradle/validacion/fase11-gps-fisico.json` con hash del APK técnico que la obtuvo. Después se preparó un ajuste del resumen para identificar cada intento y marcarlo PENDING/CANCELLED antes de otra captura: así un resultado viejo no acredita una solicitud nueva. Ese ajuste exclusivo del registro técnico compiló y pasó lint; la prueba GPS física corresponde al helper inicial, ambos con 15 s y los mismos adaptadores/reglas. No se reinstaló el producto. El paquete técnico queda en el teléfono para repetir la comprobación por gesto.

El resumen de este laboratorio guarda únicamente métricas y decisiones, **sin latitud/longitud**, y declara que no creó reportes. Al detenerse cancela la captura y descarta la evidencia. El cierre distingue el resultado físico manual de las pruebas controladas y del mapa nativo; no acredita aceptación del flujo completo.

La interfaz productiva, activación de datos reales y aceptación del usuario siguen pendientes. Esta fase permanece **local y Unreleased**, sin acciones de publicación ni cambios a versiones históricas.
