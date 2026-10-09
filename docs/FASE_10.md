# Fase 10: MapLibre Native con OpenFreeMap

Trabajo **Unreleased, local**, iniciado el **6 de octubre de 2026** y cerrado técnicamente el **7 de octubre** tras reanudar el corte interrumpido por el límite de uso, posterior a fase 9 y a la oficial **0.4.0a / código Android 19**. El usuario eligió cambiar el proveedor del mapa a **MapLibre Native + OpenFreeMap**. Se continúa sin otro número de versión, cambios a publicaciones ni acciones Git. **268 casos distintos aprobados y mapa real con nombres legibles comprobado**. [Validación](VALIDACION.md) registra las ejecuciones efectivas. Las **457 pruebas y 43 definiciones nuevas de fase 9**, y las **226 pruebas y 27 definiciones nuevas de fase 8**, conservan sus propios cortes.

Corresponde a **4.5 Mapas** del PDF. La elección del proveedor elimina la necesidad de una clave Google para el mapa actual. El criterio **SDK configurado** se acredita con carga/renderizado nativos y revisión de la captura real con nombres legibles. [Avance](AVANCE.md) pasa de **7/28 = 25% a 8/28 = 28.6%**, con **4.5 Mapas en 2/4**, conservando pesos y criterios. El laboratorio no completa mapa de incidentes por zonas y marcadores ni integración visual productiva.

## Cambio y resultado

La dependencia activa pasa a **MapLibre Native Android 13.6.1**, publicada en su [Release oficial](https://github.com/maplibre/maplibre-native/releases/tag/android-v13.6.1). El estilo base es **Liberty**, servido por OpenFreeMap y compatible con MapLibre Native, según su [guía oficial](https://openfreemap.org/quick_start/). SDK de Google Maps, metadato/lectura de clave y comprobación de Google Play Services dejan de formar parte del runtime del mapa. Las guías Google se conservan como antecedentes; la configuración vigente está en [Mapa con MapLibre](MAPA_MAPLIBRE.md).

El alias `libs.maplibre` fija **`org.maplibre.gl:android-sdk-opengl:13.6.1`**. Se utiliza OpenGL ES, backend estable con mayor compatibilidad de dispositivos según la [documentación oficial de renderizadores](https://maplibre.org/maplibre-native/docs/book/platforms/android/android-rendering-backends.html). El artefacto genérico `android-sdk` corresponde actualmente a Vulkan; recibir HTTP 200 del estilo no prueba que ese backend llegue a renderizar el mapa. La comprobación nativa se ejecutó con OpenGL; la revisión de nombres visibles se registra por separado.

OpenFreeMap declara su instancia pública gratuita, sin registro, tarjeta ni clave API; exige atribución y no ofrece garantía SLA. Estas condiciones pertenecen al servicio actual, sin prometer disponibilidad permanente ni que futuras condiciones sean iguales. [OpenFreeMap](https://openfreemap.org/).

La migración conserva coordenadas, GPS explícito, permisos, radio de 5 km, borrador, SQLite, proyección por zona y sus límites. El compañero mantiene pantalla, pines, controles, navegación y login. Esta fase prepara el motor y sus adaptadores y comprueba un mapa en un paquete técnico; no activa una pantalla productiva ni accesos a datos reales.

## Configuración y estados

`MapsConfiguration.STYLE_URL` contiene `https://tiles.openfreemap.org/styles/liberty`. `MapsConfiguration.initialize(context)` llama a `MapLibre.getInstance(context.applicationContext)`; se invoca en el hilo principal **antes de crear o inflar `MapView`**. Inicializar no crea una vista, carga el estilo ni obtiene GPS.

`MapsReadinessChecker` inicializa el SDK y conserva estos resultados:

| Estado | Significado |
| --- | --- |
| `Ready` | La inicialización local terminó; todavía no acredita red, estilo ni mapa renderizado. |
| `RendererUnavailable` | El motor nativo no pudo enlazarse. |
| `VerificationFailed` | La comprobación local falló por otra excepción. |

La cancelación se propaga. La vista del compañero deberá observar carga/fallo del estilo y renderizado por separado, retirar listeners al destruirse y conservar las atribuciones visibles. [Mapa con MapLibre](MAPA_MAPLIBRE.md) explica la composición sin claves.

## Bindings del mapa

Los contratos Android que reciben el mapa utilizan ahora **`org.maplibre.android.maps.MapLibreMap`**. `ReportLocationFlowBinding.prepareMap(token)` conserva la reserva previa a `getMapAsync`; `ReportLocationMapBinding.attachMap(map)` continúa rechazando entregas de vistas retiradas. Selección y confirmación mantienen su token, ciclo de vida y evidencia GPS independiente del pin.

`MapLibreMapClickHost` registra y retira **su propio listener por identidad** mediante `addOnMapClickListener` y `removeOnMapClickListener`. Conserva los listeners ajenos del mapa. El callback devuelve `Boolean`: un registro retirado o sustituido devuelve `false`, incluso si recibe una entrega tardía. Se invalida antes de intentar la retirada, incluyendo los caminos de error o reentrada.

`MapLibreIncidentBinding` es el puente simple hacia `IncidentMapSession`. `GoogleMapIncidentBinding.kt` se conserva como wrapper **deprecated** que delega al puente nuevo y también recibe `MapLibreMap`; su nombre no incorpora el SDK Google ni permite pasarle un `GoogleMap`. Las conexiones nuevas usan MapLibre. El flujo completo del borrador sigue utilizando el coordinador de fase 8; no se mezclan ambas sesiones sobre una misma selección.

## Laboratorio y comprobación

El nuevo módulo **`verification/maps`** es `testOnly`, con paquete aislado `com.example.citizensecurity.verification.maps`. Reutiliza las fuentes productivas originales y no instala el producto sobre la app del usuario. Su cámara parte de coordenadas explícitas de prueba; no obtiene GPS, inserta reportes ni convierte el punto del mapa en evidencia del teléfono.

Las pruebas automáticas aprobaron **268 casos distintos: 222 JVM de app, 34 Android del flujo, ocho JVM del monitor y cuatro Android del mapa**. No hubo fallos, errores u omisiones en el pase aprobado. Se añaden **25 definiciones**: 13 de los adaptadores, ocho del monitor de carga y cuatro nativas del mapa. Además, siete casos de inicialización MapLibre **reemplazan nueve de prerrequisitos Google**; no se suman como nueve casos nuevos ni como otra suite. Los resultados de dominio/datos de fase 9 no se atribuyen a una nueva ejecución de fase 10. Las repeticiones tampoco aumentan el total.

Debug/release compilan y la firma debug v2 es válida. Lint no presenta errores: app conserva 19 advertencias anteriores, el laboratorio flow dos y el de mapas ocho; estas últimas son seis `LogNotTimber` de diagnóstico y dos advertencias del contenedor técnico. Los 16 APK anteriores, HEAD/índice, fuentes visuales y producto instalado **0.3.1 / código 8** permanecen conservados. Los artefactos locales de fase 10 están separados; su metadato 0.4.0a/código 19 no los convierte en una Release. [Validación](VALIDACION.md) contiene comandos, recibo y límites.

Los cuatro casos nativos comprueban carga de estilo/mapa, fotograma completo con objetos consultables de calles/etiquetas/edificios, toque entregado por el SDK, cámara/recreación, retirada de listeners propios conservando los ajenos, fallo y cancelación. Sin GPS, una selección no autoriza confirmar. La prueba de estilo inaccesible es determinista; no acredita funcionamiento sin conexión. La carga real necesita red y no se sustituye por geometría ficticia.

**Una consulta de objetos renderizados no demuestra por sí sola que el texto sea visible en el PNG.** La captura con renderizado software del emulador detectó etiquetas presentes en los datos y texto invisible. MapLibre mantiene un [reporte abierto sobre textos de OpenFreeMap en emuladores](https://github.com/maplibre/maplibre-native/issues/3648) y una [propuesta abierta para SwiftShader](https://github.com/maplibre/maplibre-native/pull/4625); se toma como limitación compatible con lo observado, sin afirmar una causa definitiva ni incorporar código sin publicar.

Se repitieron los **cuatro casos nativos del mapa** con el mismo SDK, estilo y código, usando la GPU host NVIDIA RTX 3070/OpenGL. Aprobaron; el PNG revisado muestra **Calle Tacuba, Calle Moneda y Calle Venustiano Carranza**, además de nombres de lugares y las atribuciones. El pase terminó `BUILD SUCCESSFUL in 38s`, con 88 tareas (una ejecutada y 87 `UP-TO-DATE`); los cuatro casos repetidos no aumentan el total de 268. `-gpu host` se usó solo al iniciar el emulador de prueba, sin editar su configuración ni limpiar datos. Recibo local ignorado: `.gradle/validacion/cierre-fase10.json`. [Mapa con MapLibre](MAPA_MAPLIBRE.md) conserva esta alternativa para las pruebas del equipo; un teléfono físico sigue pendiente.

El hito **SDK configurado** queda acreditado con esa evidencia real. Se suma un hito de los 28 existentes: **8/28 = 28.6%**, con **4.5 Mapas en 2/4**. No se acreditan los otros dos hitos de mapa por zonas con marcadores y flujo visual integrado; tampoco GPS físico ni aceptación funcional del usuario.

## Alcance siguiente

Tras validar el motor, el compañero podrá conectar su vista y representar los resultados de `ReportMapViewModel` de la consulta vigente. `hasMore` seguirá indicando truncamiento y `previous` conservará su propia zona. Faltan el mapa productivo con marcadores por zona, controles integrados, GPS físico y aceptación visual del usuario.

Las atribuciones de OpenMapTiles/OpenStreetMap permanecen visibles. No se crea Cloud, facturación, cuenta, tarjeta, servicio de mapas propio ni publicación nueva. Guardado real y consultas siguen ocultos, y el ejemplo ficticio conserva su base en memoria. No se cambia autenticación, administración, notificaciones ni se autoriza borrado o apagado.
