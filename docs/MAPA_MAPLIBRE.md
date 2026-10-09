# Mapa con MapLibre Native y OpenFreeMap

**Preparación activa: fase 11 — incidentes por zona con marcadores propios — Unreleased, local.** El SDK y el estilo de fase 10 se conservan. `ReportMapSceneBinding` conecta una lectura explícita a `MapLibreReportSceneHost`, sin crear la pantalla productiva ni activar consulta, guardado real o login. El cierre técnico del 8 de octubre aprobó 325 casos distintos y comprobó marcadores por zonas y GPS físico por separado. [Fase 11](FASE_11.md) registra sus límites y 10/28 = 35.7%, sin acreditar la pantalla productiva. La oficial sigue siendo **0.4.0a / código Android 19**, sin otra publicación. El siguiente cierre de fase 10 se conserva como antecedente.

Antecedente de **fase 10 — Unreleased, local**, por la elección del usuario del **6 de octubre de 2026**, cerrada técnicamente el **7 de octubre**. La oficial sigue siendo **0.4.0a / código Android 19**. **268 casos distintos aprobados y mapa real con calles y nombres legibles comprobado**. [Fase 10](FASE_10.md) y [Validación](VALIDACION.md) distinguen comprobación nativa y aceptación del flujo productivo. [Google Maps](GOOGLE_MAPS.md) y [configuración de clave Google](CONFIGURAR_CLAVE_MAPS.md) conservan los antecedentes del proveedor anterior.

## Proveedor y configuración

El proyecto utiliza **MapLibre Native Android 13.6.1** y el estilo **Liberty de OpenFreeMap**. La versión consta en la [Release oficial de MapLibre](https://github.com/maplibre/maplibre-native/releases/tag/android-v13.6.1); OpenFreeMap publica el estilo y admite su uso con MapLibre Native en su [guía de inicio](https://openfreemap.org/quick_start/).

La dependencia fijada por `libs.maplibre` es **`org.maplibre.gl:android-sdk-opengl:13.6.1`**. OpenGL ES es el backend estable de mayor compatibilidad; el artefacto genérico `android-sdk` apunta actualmente a Vulkan, según la [documentación de MapLibre](https://maplibre.org/maplibre-native/docs/book/platforms/android/android-rendering-backends.html). Para la composición del proyecto se usa el alias existente:

```kotlin
implementation(libs.maplibre) // org.maplibre.gl:android-sdk-opengl:13.6.1
```

```text
https://tiles.openfreemap.org/styles/liberty
```

La instancia pública de OpenFreeMap es gratuita, sin registro, tarjeta ni API key. Exige atribución y actualmente no ofrece garantías SLA; la carga requiere conectividad y disponibilidad del servicio. [Condiciones y atribución de OpenFreeMap](https://openfreemap.org/).

El runtime del mapa ya no lee `MAPS_API_KEY`, usa el metadato Google ni requiere Google Play Services. No hace falta abrir Google Cloud o configurar facturación para este proveedor. Las coordenadas y el almacenamiento local son independientes del motor elegido.

## Preparar la vista del compañero

En el hilo principal, inicializa **antes de crear o inflar `MapView`**:

```kotlin
MapsConfiguration.initialize(this)
// Después crear o inflar la MapView de la pantalla del compañero.
```

`MapsConfiguration.initialize` usa `MapLibre.getInstance(applicationContext)`. `CitizenSecurityApplication.mapsReadinessChecker.check()` también comprueba esa inicialización y devuelve `MapsReadiness.Ready`, `RendererUnavailable` o `VerificationFailed`. `Ready` no comprueba red, carga del estilo o renderizado; la vista debe representar esos resultados por separado.

Una vez disponible `MapLibreMap`, solicita el estilo compartido:

```kotlin
import org.maplibre.android.maps.Style

mapView.getMapAsync { mapa ->
    mapa.setStyle(Style.Builder().fromUri(MapsConfiguration.STYLE_URL))
}
```

Este ejemplo es un contrato para su futura pantalla; el incremento no añade una vista productiva. La pantalla mantiene el ciclo de `MapView` (`onCreate`, inicio/reanudación, pausa/parada, estado guardado, memoria baja y destrucción). Retira sus listeners y cierra el binding antes de destruirla. No debe conservar Activity, MapView o bindings en un ViewModel o `Application`.

Conserva los controles de atribución visibles y comprueba que muestran OpenMapTiles y OpenStreetMap; OpenFreeMap puede aparecer también. No ocultes la atribución al colocar los controles del formulario. La obligación y las fuentes figuran en [OpenFreeMap](https://openfreemap.org/).

## Selección y consulta por zona

Para la selección del borrador se conserva la reserva del coordinador de fase 8, ahora con `MapLibreMap`:

```kotlin
val enlace = coordinador.prepareMap(apertura.token) ?: return
mapView.getMapAsync { mapa ->
    if (enlace.attachMap(mapa)) {
        mapa.setStyle(Style.Builder().fromUri(MapsConfiguration.STYLE_URL))
    }
}
```

Una nueva vista reserva otro handle. La entrega anterior no sustituye sus listeners; cerrar un handle retirado tampoco afecta a su reemplazo. Los clicks se registran/retiran por identidad y devuelven `false` cuando el callback ya no corresponde. El flujo mantiene permiso preciso, captura explícita y evidencia actual dentro de 5 km; un tap selecciona un punto, sin confirmar, capturar GPS ni guardar.

`MapLibreIncidentBinding` sirve al contrato simple con `IncidentMapSession`. El archivo `GoogleMapIncidentBinding.kt` conserva un wrapper deprecated sobre MapLibre; las conexiones nuevas usan el nombre actual. No mezcles ese puente simple con `ReportLocationFlowBinding` sobre la misma selección. [Acoplar interfaz](ACOPLAR_INTERFAZ.md) conserva la conexión completa del borrador y sus controles.

Como antecedente, [fase 9](FASE_9.md) preparó el contrato directo `ReportMapViewModel.load(query)` y el resultado mínimo con `hasMore`; `previous` conserva su propia zona. La conexión vigente utiliza **`ReportMapSceneBinding.refreshVisible(...)` por un gesto explícito en RESUMED**, descrita a continuación. El binding administra la proyección, las llamadas al modelo y los camera idle posteriores; un movimiento de cámara sin gesto previo no consulta. No añadas otro listener que llame directamente a `load` o `clear` sobre ese mismo modelo. Los marcadores por zona ya se comprobaron técnicamente en fase 11; la pantalla productiva y sus controles siguen pendientes del compañero.

## Puente de marcadores — fase 11

La futura pantalla obtiene `ReportMapViewModel` mediante la fábrica compartida y crea `MapLibreReportSceneHost` y `ReportMapSceneBinding` para su propia vista, como muestra [Acoplar interfaz](ACOPLAR_INTERFAZ.md). Un host nuevo posee sus IDs de fuente/capas y listeners; sustituye solo sus marcadores y deja los pines o capas ajenos intactos. No compartas la misma instancia entre dos bindings. El host usa los campos mínimos del marcador en GeoJSON; no copia descripción, referencias ni identidad del usuario.

El constructor con MapView observa las recargas de estilo nativas de esa vista:

```kotlin
val host = MapLibreReportSceneHost(mapa, mapView)
val consultaZona = ReportMapSceneBinding(
    owner = this,
    model = incidentes,
    host = host,
    onState = { estado -> representarEstadoDelMapa(estado) },
)
```

`incidentes` es el modelo retenido de la pantalla y `representarEstadoDelMapa` pertenece a la interfaz del compañero. El ejemplo no añade controles al producto. Si solo se dispone del mapa, conserva el host y entrega cada estilo nuevo desde su callback vigente:

```kotlin
val host = MapLibreReportSceneHost(mapa)
val consultaZona = ReportMapSceneBinding(this, incidentes, host)
mapa.setStyle(Style.Builder().fromUri(MapsConfiguration.STYLE_URL)) { estilo ->
    host.onStyleLoaded(estilo)
}
```

Para recargas posteriores se entrega también su estilo correspondiente. Si la vista o solicitud ya fue retirada, ese callback no debe conectar otra escena ni recuperar una retirada. La pantalla mantiene el ciclo de MapView y la identidad de sus solicitudes asíncronas; nunca guarda MapView/host/binding en el modelo o `Application`. Si reemplaza un binding ya conocido, crea primero su sucesor para reservar la propiedad y después cierra el anterior. Un callback tardío de `getMapAsync` se comprueba antes de crear cualquier sucesor.

La construcción, observación, carga del estilo, recreación y `onResume` no leen SQLite. La consulta necesita un gesto explícito y dueño `RESUMED`:

```kotlin
consultaZona.refreshVisible(
    types = tiposElegidos,
    statuses = estadosElegidos,
    limit = 200,
)
```

Los sets vacíos aceptan todos los tipos/estados y el puente conserva sus copias; el límite admite 1 a 500. `true` solo confirma entrega del gesto. La región se obtiene de la proyección del SDK; sin estilo cargado o dimensiones utilizables de la MapView se publica `ViewportUnavailable` y no se consulta. Un camera idle posterior a ese gesto puede iniciar la lectura cuando exista región; antes del gesto no la inicia. Durante el seguimiento, camera idle pide otra zona con los mismos filtros y un evento repetido de la misma región no duplica consultas. Otro gesto permite renovar o cambiar filtros.

Antes de cargar una región nueva se retiran los marcadores anteriores. Un resultado solo se pinta si siguen vigentes el propietario puro del modelo, su petición y la región actual; se ignoran resultados tardíos o `previous` de otra zona. `Ready(query, count, hasMore)` indica que se representaron los marcadores propios: un resultado vacío los sustituye por cero y `hasMore` obliga a mostrar que la salida está limitada. Un fallo de SDK retira la operación y usa un error genérico; reintentar requiere otro gesto.

Al salir de `RESUMED` se cancela la consulta y se limpian sus overlays. Volver no renueva la lectura. Cada escena reserva en el modelo un token de identidad sin Activity, MapView o callbacks; el cierre/pause de su antecesora no limpia la consulta de una sucesora, aunque la región sea la misma. Cierra el binding antes de `MapView.onDestroy()` y garantiza esta destrucción con `finally` si el SDK falla al retirar recursos. El cierre no cierra el repositorio compartido. Los controles de atribución continúan visibles.

Esta conexión sigue **local y Unreleased**, con cierre técnico del **8 de octubre de 2026**. [Fase 11](FASE_11.md) y [Validación](VALIDACION.md) registran **325 casos distintos aprobados**: 273 JVM app, 34 Android flow, ocho JVM monitor y diez Android mapas; **57 definiciones nuevas**, 31 coordinador, 20 host y seis nativas. App/nativos se repitieron tras corregir dos aserciones de precisión del SDK, sin cambios de producción o reglas GPS; flow/monitor se ejecutaron en el primer pase y se reutilizaron al final, sin doble recuento. Debug/release, firma debug v2 y lint sin errores aprobados, con 19 advertencias app, dos flow, dos physical y ocho mapas.

El circuito SQLite en memoria → modelo → binding → host → SDK mostró **dos círculos de reportes de prueba** sobre Liberty, comprobados mediante objetos renderizados y captura revisada. La comprobación física independiente obtuvo GPS real **VALIDATED** en Motorola/API36: precisión aproximada **6.668 m**, antigüedad **46 ms**, cercanía permitida, punto distante rechazado y lectura no simulada. La prueba manual usó el APK técnico inicial con captura de **15 s**: hubo un primer TIMEOUT y el siguiente gesto cerca de una ventana funcionó con el mismo límite; no se persistieron coordenadas ni se crearon reportes. Se acreditan dos hitos existentes, captura del dispositivo y mapa por zonas/marcadores: **4.3 en 2/4, 4.5 en 3/4; 10/28 = 35.7%**, con los mismos 28 hitos y pesos.

El recibo final aprobó la conservación de **63 archivos base, 18 APK anteriores, HEAD/índice y productos instalados**, incluidos sus hashes y fechas. La pantalla, navegación, login, activación del acceso a datos reales y aceptación del flujo productivo siguen pendientes. La oficial conserva 0.4.0a/código 19; no se publica este corte local.

## Comprobar la carga real

`verification/maps` es un laboratorio `testOnly` aislado. Sus pruebas nativas requieren el estilo y los datos públicos reales: comprueban carga de estilo/mapa, un fotograma completo y objetos consultables de calles, etiquetas y edificios, además de taps/cámara del SDK. La consulta de objetos no sustituye la revisión visual del PNG para asegurar nombres legibles. La prueba de fallo usa un estilo inexistente; no añade geometría ficticia para aparentar un mapa cargado.

El **corte de fase 10** aprobó **222 JVM de app, 34 Android del flujo, ocho JVM del monitor y cuatro nativas del mapa: 268 casos distintos**. Son 25 definiciones añadidas y siete de readiness que reemplazan nueve de Google; no se inflan los recuentos con sustituciones o repeticiones. Debug/release, firma debug y lint sin errores se comprobaron en ese corte, con las advertencias detalladas en [Validación](VALIDACION.md). Las 457 pruebas de fase 9 y las 226 de fase 8 conservan sus cortes. El cierre posterior de fase 11, con 325 casos y 57 definiciones nuevas, está registrado en la sección anterior; no convierte los resultados de fases previas en ejecuciones nuevas.

## Emulador y nombres invisibles

La captura del primer pase OpenGL con renderizado software mostró geometría, pero los nombres de calles no eran visibles, aunque la consulta de objetos devolvía etiquetas. Existe un [reporte abierto de MapLibre sobre texto de OpenFreeMap en emuladores](https://github.com/maplibre/maplibre-native/issues/3648); una [propuesta abierta para SwiftShader](https://github.com/maplibre/maplibre-native/pull/4625) también describe símbolos transparentes. Son antecedentes compatibles con el síntoma observado; no se afirma que el servicio omita calles o que todo teléfono tenga el problema.

Para comprobarlo, cierra el emulador de prueba y abre **el mismo AVD** con aceleración de la GPU del equipo. Desde el ejecutable `emulator` del SDK, el argumento para una ejecución es:

```text
emulator -avd Medium_Phone_API_37.0 -gpu host
```

No hace falta borrar el AVD, limpiar sus datos ni modificar el código de la interfaz. Revisa una captura del mapa real con nombres legibles y atribución visible; las señales automáticas de carga y `queryRenderedFeatures` se conservan como evidencia complementaria. Si GPU host no está disponible, la comprobación queda pendiente para otro emulador compatible o un teléfono, sin declarar aprobada la parte visual. No se incorpora el parche sin publicar de MapLibre.

En esta PC se comprobaron los cuatro casos nativos con GPU host NVIDIA RTX 3070/OpenGL y el mismo SDK, estilo y código. La captura revisada muestra **Calle Tacuba, Calle Moneda y Calle Venustiano Carranza**, nombres de lugares y atribución visible. El argumento se utilizó solo en la ejecución de prueba, sin editar la configuración del AVD. El recibo local ignorado `.gradle/validacion/cierre-fase10.json` conserva los resultados; repetir los cuatro casos no aumenta las 268 pruebas distintas.

**En fase 10**, la carga/renderizado reales y nombres visibles acreditaron únicamente **SDK configurado**, con **8/28 = 28.6%** y **4.5 Mapas en 2/4**. Aquel laboratorio no acreditó GPS físico, marcadores por zona ni pantalla productiva. **Fase 11** comprobó después los marcadores y GPS físico por separado, dejando **10/28 = 35.7%, 4.3 en 2/4 y 4.5 en 3/4**, según [Avance](AVANCE.md); la integración de la pantalla y aceptación completas siguen pendientes. La limitación del renderizador software y los reportes de MapLibre quedan documentados; no se afirma que estén corregidos en el SDK incluido.
