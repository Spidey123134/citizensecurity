# Laboratorio nativo de mapas

## Preparación de fase 11: incidentes por zona

`MapZoneHarnessActivity` añade una segunda Activity al mismo APK técnico. Reutiliza `ReportMapViewModel`, `ReportMapSceneBinding` y `MapLibreReportSceneHost` originales para conectar **SQLite real en memoria → consulta de la ventana visible → fuente GeoJSON y capa CircleLayer nativas** sobre Liberty. Su botón `Consultar zona` activa la primera lectura; construir, cargar estilo, mover la cámara antes del gesto, reanudar o recrear la vista no consultan. Después de ese gesto, un evento de cámara aplica los mismos filtros a la zona nueva. Salir de RESUMED cancela la lectura, retira marcadores y exige otro gesto al volver.

La Activity no crea reportes. Solo las seis pruebas nuevas insertan explícitamente tres fixtures ficticias en `SqliteReportRepository(databaseName = null)`: dos cerca de la cámara fija de Ciudad de México y una dos centésimas de grado al norte y este. No usan referencias del usuario, GPS, repositorio productivo ni cambios administrativos de estado. El dueño conserva base/modelo al recrear y cierra SQLite cuando se libera definitivamente su ViewModelStore.

La suite explícita `NativeMapsSuite` incluye los **cuatro casos fase 10 sin modificarlos y seis casos nuevos**:

1. Cero lecturas antes del gesto, botón nativo real, dos folios de SQLite dibujados, coordenadas longitud/latitud correctas y solo propiedades mínimas.
2. Cambio de cámara a otra zona, una lectura adicional y retiro de los folios anteriores.
3. Límite uno con `hasMore`, filtro por categoría y respuesta vacía por estado sin editar reportes; la capa/fuente quedan retiradas.
4. Recarga del estilo que reconecta los marcadores sin otra lectura; cerrar retira solo fuente/capa/listeners propios y conserva recursos ajenos.
5. Cancelación de una entrega suspendida después de una lectura SQLite al detener la Activity; reanudar no consulta hasta otro gesto.
6. Recreación que conserva base/modelo y cámara, cierra la vista anterior, bloquea sus entregas y requiere una consulta nueva; destrucción final cierra la base.

Las esperas usan callbacks de estado, frame y cámara SDK con límites. La carga base exige los mismos eventos reales y objetos de calles, etiquetas y edificios del monitor original. No reemplaza un fallo live por un mapa falso. Al aprobar el primer caso, se guardan `files/phase11-evidence/zones-native.png`, `zones-native-sdk.png` y `zones-native.json`: Activity y snapshot SDK con los dos marcadores, estilo, cámara fija, región consultada, propiedades mínimas, cantidades renderizadas y lecturas SQLite. Son evidencias de esta ejecución técnica; no acreditan la pantalla productiva ni GPS físico. Root ejecuta Gradle/adb centralmente y documenta los resultados; esta sección describe los casos implementados, no afirma todavía que se ejecutaron.

Las ocho JVM del monitor fase 10 y sus cuatro instrumentadas se conservan. El runner de `connectedDebugAndroidTest` selecciona ahora esta suite completa. No ejecutar `:app:connectedAndroidTest` ni instalar este trabajo sobre la app productiva. Todo permanece local y Unreleased, con los mismos metadatos técnicos.

## Antecedente de fase 10

APK técnico `testOnly`, paquete **com.example.citizensecurity.verification.maps**, Android mínimo 26, objetivo 37 y metadatos **0.4.0a / código 19**. Es independiente del APK instalado del producto. La Activity se puede abrir desde el launcher del emulador y mantiene una vista fija de Ciudad de México para revisar el renderizado.

Reutiliza `MapsConfiguration`, `MapLibreIncidentBinding`, `MapLibreMapClickHost` e `IncidentMapSession` desde las fuentes originales de `app/.../maps`, sin copiar esos contratos ni depender del APK productivo. Usa MapLibre Native **13.6.1** y el estilo público `https://tiles.openfreemap.org/styles/liberty`, sin clave. El AAR declara permisos de ubicación; el manifiesto del laboratorio los retira expresamente y las pruebas comprueban el manifiesto final. No crea una fuente GPS, no proporciona una lectura sintética del teléfono y no abre ni guarda reportes.

La carga requiere los callbacks reales de estilo, mapa y fotograma completo, las capas de calles/etiquetas/edificios y objetos renderizados de las tres familias mediante `queryRenderedFeatures`. La presencia de un estilo por sí sola no acredita un mapa visible. La espera termina al fallar el SDK o después de 30 segundos. Antes de capturar, la prueba espera la señal nativa `idle` posterior a esa carga y obtiene un snapshot del SDK fuera del callback; así incluye el final de las transiciones de símbolos. El cierre cancela el plazo, retira los cinco listeners nativos, cierra el adaptador original y destruye `MapView`; la Activity reenvía todo su ciclo de vida al SDK.

Las ocho pruebas JVM comprueban la política conservadora de carga, callbacks tardíos, estilos incompletos, timeout y cierre. Las cuatro pruebas Android comprueban:

1. Renderizado real de Liberty, cámara/zoom y toque nativo al contrato original. Confirmar sin evidencia GPS devuelve `DEVICE_LOCATION_REQUIRED`; cerrar el adaptador conserva un listener ajeno y bloquea nuevas propuestas.
2. Recreación de `MapView`, cámara conservada y retiro de la vista anterior, sin selección o GPS automático.
3. Fallo nativo al solicitar un recurso de estilo inexistente. Comprueba la ruta de error del SDK sin depender de una caída del servidor público.
4. Destrucción de la Activity durante la carga, que termina sesión y espera.

Comprobación central desde la raíz del repositorio:

```powershell
.\gradlew.bat :verification:maps:testDebugUnitTest :verification:maps:assembleDebug :verification:maps:assembleDebugAndroidTest :verification:maps:lintDebug
.\gradlew.bat :verification:maps:connectedDebugAndroidTest
```

Solo ejecutar la suite instrumentada contra este APK técnico. Las dos pruebas live requieren red y OpenFreeMap disponible; un timeout o ausencia de objetos es un fallo verificable, no una aceptación. El recurso inexistente comprueba error, no disponibilidad offline. No se descarga una región offline y una caché previa puede intervenir en otra ejecución.

Después de aprobar la carga live e idle, la prueba guarda `files/maplibre-evidence/liberty-native.png`, `liberty-native-sdk.png` y `liberty-native.json` dentro del paquete técnico. El snapshot contiene el mapa real; la captura de pantalla posterior muestra la Activity técnica y sus atribuciones. El JSON distingue la cámara fija de una ubicación GPS y registra estilo, eventos, idle, capas, cantidades de objetos y nombres aportados por sus teselas. Las esperas de idle y snapshot usan señales con un límite de cinco segundos cada una. Estos archivos son evidencia de esta ejecución, no criterios de GPS físico, mapa productivo ni aceptación del compañero.

Referencia de API: [ciclo de MapView](https://github.com/maplibre/maplibre-native/blob/android-v13.6.1/platform/android/docs/getting-started.md), [carga de estilo](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-style/-builder/from-uri.html) y [consulta de objetos renderizados](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map/query-rendered-features.html).
