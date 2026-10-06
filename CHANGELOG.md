# Changelog

Registro de cambios del proyecto. Las fechas usan la hora de México. Distingue preparación local, integración, publicación y revisión funcional; terminar un trabajo no autoriza subirlo.

## 0.4.0a — Puente de ubicación y borrador — 2026-10-06

Versión oficial **0.4.0a / código Android 19**: integración, subida a `main` y Release `v0.4.0a` autorizadas expresamente por el usuario. Validación completada; el estado de publicación se consulta en la Release enlazada.

- **Fase 6A:** `IncidentLocationFlowBinding` conecta controles y ciclo de vida sin crear pantallas. Solo acepta gestos en `RESUMED`, cancela captura al salir y cierra permisos al destruir. Los clicks de Google Maps pasan por el mismo puente; cerrar MapView detiene GPS sin cerrar el registro de la Activity.
- **Fase 7:** `ReportDraftViewModel` conserva categoría, prioridad, descripción, fecha, referencia y coordenadas. Cada selección usa un token propio: cancelar o recibir una respuesta anterior no sobrescribe el borrador. La restauración recupera campos, sin evidencia GPS ni autorización de guardado.
- Límites de 1000/200 puntos Unicode, sin truncar entradas rechazadas. `review(now)` entrega datos válidos o errores; no inserta reportes ni envía avisos.
- Integra el cambio visual del compañero `b665a56`: login y creación de cuenta visual conservados; el nuevo diseño de herramientas mantiene sus controles y recupera el ejemplo ficticio en memoria. El guardado de datos reales y toda consulta siguen ocultos.
- Descriptor Gradle regenerado para **JDK 27**, sin URLs de JDK 25; bytecode Android permanece JVM 17. Se conservan historia, versiones y APK anteriores.
- Corregida la segunda apertura tras confirmar/cancelar: reinicia sesión en el mismo modelo, exige otra captura e ignora lecturas viejas. Factory de borrador usa el estado guardado proporcionado por Android.
- **266 pruebas ejecutadas y aprobadas, 57 nuevas; 124 reutilizadas.** Debug/release, firma debug v2 y lint sin errores: 19 advertencias visuales app, dos por variante técnica, cero datos. [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a) agrupa APK académico, suma SHA-256 y notas.
- Clave Google, renderizado real del mapa, GPS físico y controles de estas funciones siguen pendientes. [Fases](docs/FASES.md), [borrador](docs/FASE_7.md) y [validación](docs/VALIDACION.md) distinguen código probado y funciones visibles.

## 0.3.11 — Evidencia GPS vigente — Unreleased — 2026-10-05

Cierre del trabajo pendiente anterior: **0.3.11 / código Android 18**, comprobado localmente antes de preparar 0.4.0a.

- `PhoneLocationEvidence` comprueba permiso preciso y disponibilidad GPS al publicar y consultar. Un fallo o GPS deshabilitado descarta la lectura; reactivarlo no resucita evidencia anterior. La comprobación sucede al acceder, sin observación continua de cada cambio del sistema.
- La composición productiva comparte la misma fuente Android entre renovación/evidencia y el guard SQLite. Se comprueba otra vez disponibilidad después de publicar; no se devuelve `Ready` sin evidencia vigente.
- Dos regresiones reprodujeron el fallo anterior con proveedor controlado y aprobaron corregidas. **247 pruebas ejecutadas y aprobadas**, **16 definiciones nuevas**: 121 JVM app, 38 JVM datos, 64 SQLite/datos Android y 24 laboratorio. Las 86 de dominio no se repitieron.
- Debug/release compilaron; firma debug v2 y lint sin errores. App mantuvo 13 advertencias anteriores, laboratorio dos por variante, datos cero. Artefactos previos conservados. No se obtuvo GPS físico ni se instaló el producto. [Validación](docs/VALIDACION.md) conserva la evidencia histórica propia de este corte.

## 0.3.10 — Integración Android de ubicación — Unreleased — 2026-10-05

Preparación **local / código Android 17**, conservando 0.3.9 y sus artefactos. Continúa fase 6; este incremento comprueba juntos los componentes existentes de permiso, captura, evidencia y confirmación. No añade controles a las pantallas del compañero.

- Nueva variante técnica `.flow` y Activity sin launcher que compilan las fuentes productivas originales. Permiso nativo y ciclo de Activity reales; lectura, reloj y estado del proveedor controlados para reproducir aceptación y rechazo sin alterar GPS o coordenadas del producto.
- 16 casos cubren captura explícita, selección cerca/lejos dentro de la regla de 5 km, calidad/vigencia, timeout, errores, concurrencia, recreación, cancelación al salir y respuesta tardía. El permiso no captura; recibir una lectura lista no confirma ni guarda.
- Tres diálogos anteriores se amplían para comprobar que el pin se conserva y no hay captura automática. Aproximado/rechazo impiden llegar a la fuente; preciso permite el gesto posterior y rechaza una lectura del fixture marcada como simulada.
- Dos casos adicionales verifican registro en `LocationManager` y cancelación antes del callback, y rechazo de la marca simulada de `Location` a través del adaptador y flujo originales. No acreditan una lectura GPS física.
- Corregida la selección de pruebas del laboratorio mediante `LocationFlowAndroidSuite`: el primer comando omitía dos casos del proveedor. Los XML finales confirman **21 casos Android distintos aprobados, 18 definiciones nuevas**; no se suman las 16 repeticiones. Las **121 JVM de app permanecieron `UP-TO-DATE`**, sin ejecución nueva en este corte.
- Debug/release compilados y firma debug v2 válida. Lint sin errores: 13 advertencias anteriores de app y dos por cada una de las cuatro variantes técnicas. App instalada 0.3.1, fuentes visuales, índice e historial conservados; fixtures ausentes del DEX productivo y APK previos preservados por hash.
- [Flujo Android](docs/FLUJO_UBICACION_ANDROID.md), [permisos nativos](docs/PRUEBAS_PERMISOS_ANDROID.md) y [validación](docs/VALIDACION.md) documentan la conexión y evidencia. Controles del compañero, teléfono físico y mapa con clave siguen pendientes. Fase 6 abierta, **7/28 = 25%**, local y Unreleased, sin acciones Git ni publicación.

## 0.3.9 — Verificación Android de permisos — Unreleased — 2026-10-05

Preparación **local / código Android 16**, conservando 0.3.8 y su evidencia. Sigue fase 6; pantallas/login del compañero y accesos visibles básicos 0.3.1 se mantienen.

- Laboratorio `verification/location` con `testOnly`, Activity sin launcher y paquetes distintos para permiso preciso, aproximado y rechazo. Comparte las fuentes de `app/.../maps` sin copiar o mover código y no instala la app productiva.
- Prueba del contrato real con `PermissionController`, recreación nativa con solicitud pendiente, lectura actual de permisos y cierre del dueño. Ningún permiso inicia GPS ni convierte el pin en evidencia.
- **3 flujos Android nuevos y 121 JVM de app ejecutados**, 124 aprobados. Recreación con diálogo pendiente, permisos reales y nuevo dueño comprobados en Android 17/API 37. Debug/release compilan; firma debug v2 correcta. Lint sin errores: app conserva 13 advertencias anteriores y laboratorio 2 por variante. App instalada, pantallas y APK anteriores conservados por hash.
- Corregida la configuración de fuentes Kotlin compartidas para AGP 9: `kotlin.directories`, en lugar del conjunto Java que no las compilaba.
- [Guía del laboratorio](docs/PRUEBAS_PERMISOS_ANDROID.md) y [validación](docs/VALIDACION.md) registran ejecución, aislamiento y límites. No se acredita GPS físico, muerte de proceso ni mapa renderizado.
- La conexión de los controles corresponde al compañero. La clave Google y el flujo completo siguen pendientes; **7/28 = 25%**. Todo local y Unreleased, sin acciones Git o publicación.

## 0.3.8 — Permiso de ubicación en primer plano — Unreleased — 2026-10-05

Preparación **local / código Android 15**, conservando 0.3.7. El usuario confirmó que aún no tiene clave Google y pidió avanzar con ubicación y permisos. Continúa fase 6; interfaz/login del compañero, mapa visible, guardado real y consulta conservan su estado anterior.

- `LocationPermissionViewModel` distingue acceso preciso/aproximado/ausente, explicación previa, cancelación y fallos. Conserva una sola solicitud al rotar; los resultados restaurados no abren otra ni conceden evidencia GPS. No infiere rechazo permanente por ausencia de rationale.
- `PreciseLocationPermissionBinding` registra el contrato AndroidX real y solicita COARSE/FINE juntos mediante un gesto con la Activity en RESUMED. Relee los permisos actuales al recibir resultado o comprobar al volver; no inicia captura, confirma puntos o guarda. Su vida coincide con el ActivityResultCaller, no con MapView.
- Corregidos los caminos de error que podían alterar el modelo desde un puente cerrado. Un fallo en la comprobación del callback final libera la solicitud; un fallo durante la espera la conserva para evitar duplicarla. Los estados permiten reintentar por una nueva acción.
- Corregida la dependencia Fragment 1.1.0 heredada de Google Maps mediante restricción a 1.9.1 compatible con Activity Result, sin ocultar el error de lint.
- **121 JVM de app aprobadas, 47 nuevas** (25 modelo y 22 puente/integración). Debug y release sin firma compilan; firma debug v2 correcta. Lint app sin errores, con las 13 advertencias anteriores. No se repitieron las suites de dominio/datos ni instrumentación; el registro controlado no acredita diálogo o rotación Android reales.
- [Guía del compañero](docs/PERMISOS_UBICACION.md) y [validación](docs/VALIDACION.md) actualizadas. Avance **7/28 = 25%**; GPS real, permiso interactivo en la pantalla y mapa con clave siguen pendientes. Sin instalación, commit, subida, integración, etiqueta ni Release.

## 0.3.7 — Flujo del mapa y conservación de datos — Unreleased — 2026-10-03

Preparación local **0.3.7 / código Android 14**. Incluye tres incrementos lógicos: **0.3.5** robustez del GPS, **0.3.6** coordinación de selección/captura/confirmación y **0.3.7** integridad SQLite y prerequisitos del mapa. No hubo APK o entregas independientes 0.3.5/0.3.6, commits, subida, integración, etiqueta o Release nueva.

- Renovaciones fallidas invalidan la evidencia; se comprueban otra vez permiso, GPS y vigencia después de publicarla. La fuente reintenta una sola vez una retirada fallida del listener, descarta lecturas si no logra retirar y conserva la cancelación o fallo original.
- `IncidentLocationViewModel` coordina una sola sesión, conserva el último pin, bloquea confirmación durante captura, exige renovar después de salir o fallar e ignora respuestas viejas. La fábrica comparte las dependencias productivas. `IncidentLocationMapBinding` conecta el SDK al modelo sin guardar ni duplicar sesiones.
- `MapsReadinessChecker` distingue clave ausente, servicios Google no disponibles y fallo de comprobación antes de crear la vista. `Ready` solo acredita prerequisitos locales; no autorización Cloud ni carga real.
- SQLite conserva la base corrupta y devuelve el fallo en lugar de eliminarla/recrearla. Se comprueba cancelación después de cada guard, incluido el anterior al commit, para evitar insertar tras cancelación; el esquema sigue en versión 1. No revierte escrituras ya confirmadas.
- **52 casos nuevos** respecto del pulido 0.3.4. **163 diferentes ejecutados** sin fallos y **86 de dominio reutilizados**: 249 registrados. Debug y release sin firma compilan; firma debug v2 verificada. Lint: datos sin incidencias y app con las mismas 13 advertencias visuales, sin errores. Pruebas de datos usan bases aisladas y GPS con backend controlado.
- Pantallas/login y herramientas básicas 0.3.1 conservadas; guardado real y consulta siguen ocultos. Clave Cloud, mapa visible, permisos interactivos y GPS real pendientes. Avance **7/28 = 25%**; las mejoras refuerzan hitos existentes sin dar por aceptada la integración.

[Detalle y contrato del equipo](docs/INCREMENTOS_035_037.md), [Google Maps](docs/GOOGLE_MAPS.md) y [validación](docs/VALIDACION.md) conservan las pruebas y limitaciones. Los APK anteriores permanecen intactos.

## 0.3.4 — Pulido de selección en Google Maps — Unreleased — 2026-10-03

- Corregida una cancelación tardía que, después de confirmar un punto, devolvía la ubicación original. La sesión conserva ahora el resultado terminado; cancelar antes de confirmar sigue devolviendo exactamente el original. Los eventos tardíos no reabren la selección.
- Añadida una regresión para confirmación seguida de cancelación repetida. Aprobadas **42 JVM de app**, compilación debug, firma v2 y lint sin errores, con las 13 advertencias anteriores.
- Corregida la guía de ubicación que todavía afirmaba en presente que el manifiesto no declaraba permisos ni SDK. Se distingue el paso inicial de captura de la incorporación posterior de Google Maps.
- Permanece **0.3.4 / código 11, local y Unreleased**, avance 25%. Sin cambios a interfaz/login, instalación, commit, subida o publicación. [Validación](docs/VALIDACION.md) conserva la evidencia propia del pulido.

## 0.3.4 — Incorporación de Google Maps — Unreleased — 2026-10-03

Complemento de la misma preparación local 0.3.4 / código 11: el usuario reiteró que la API Google Maps ya estaba solicitada. Se incorporó el SDK oficial 20.0.0, configuración de clave desde secrets.properties ignorado o entorno, metadato y permisos de internet/ubicación declarados. No hay permisos concedidos automáticamente ni clave en Git. El puente real de `OnMapClickListener` selecciona coordenadas; IncidentMapSession confirma por proximidad con evidencia actual, permite corregir rechazos y termina al confirmar/cancelar. No guarda automáticamente ni modifica login, menús o layouts del compañero.

Aprobadas **41 JVM de app**, incluidas **11 nuevas de sesión del mapa**, compilación debug, firma v2 y lint sin errores (13 advertencias previas). SDK, sesión y puente están en el DEX. La prueba no acredita mapa visible: faltan clave Google Cloud y pantalla integrada. Avance: **7/28 = 25%**. El APK previo de captura se conserva sin sobrescribir; el complemento tiene su propio artefacto local ignorado. No se crearon commits, subidas, integraciones, etiquetas o Releases. [Google Maps](docs/GOOGLE_MAPS.md) contiene configuración y SHA-1 debug; [Validación](docs/VALIDACION.md) distingue esta ejecución de las 185 pruebas anteriores.

## 0.3.4 — Solicitud y renovación de ubicación — Unreleased — 2026-10-03

Preparación local **0.3.4 / código Android 11**, conservando 0.3.3 y las bases anteriores. Continúa la fase 6 y la regla de 5 km; no hay commit, subida, integración, etiqueta ni publicación.

### Cambios

- Fuente Android para solicitar una primera lectura del proveedor GPS desde el contexto de aplicación, sin usar el marcador o la última posición guardada. Retira el listener al recibirla, cancelar, deshabilitarse el proveedor o fallar el registro.
- `PhoneLocationRefresh` comprueba permiso preciso y GPS habilitado, limita la espera a 15 segundos configurables y evita solicitudes simultáneas. Descarta la evidencia anterior al renovar; solo publica una lectura que cumple coordenadas, precisión, vigencia y ausencia de marca de simulación.
- Estados separados para lectura lista, permiso pendiente, GPS apagado, lectura no disponible, tiempo agotado, solicitud simultánea y calidad rechazada. La cancelación externa se propaga y libera la suscripción.
- Corrección de una carrera entre timeout y publicación: agotar la espera durante la actualización no deja evidencia aceptada. Una regresión determinista comprueba la limpieza.
- Composición diferida en `CitizenSecurityApplication`: no solicita GPS al iniciar la app. Sin cambios a interfaz, login o permisos del manifiesto; guardado real y consulta siguen ocultos. La primera lectura rechazada exige un reintento explícito, sin esperar automáticamente otra más precisa.
- [Contrato de ubicación](docs/OBTENER_UBICACION.md) y [mejoras posteriores](docs/MEJORAS_FUTURAS.md). Las propuestas no se implementan ahora ni tienen versión oficial comprometida. [Avance](docs/AVANCE.md) conserva **7/28 hitos, 25%** hasta comprobar captura real e integración.

### Comprobado localmente

- **185 pruebas diferentes aprobadas:** 86 de dominio, 30 JVM de app, 20 JVM de renovación y 49 instrumentadas en API 37. Las 37 nuevas cubren estados, timeout, concurrencia, cancelación y limpieza; las repeticiones no se suman.
- Compilación debug, firma v2 y lint debug correctos: datos sin incidencias, app con las 13 advertencias visuales previas. Los 43 recursos del APK coinciden con 0.3.3.
- Las instrumentadas usan `Location` nativo y un backend aislado; el gestor real se consulta con permiso denegado. No se concedieron permisos, capturó GPS real ni instaló la preparación sobre la app del usuario, que conserva 0.3.1 / código 8. API 26–29 y mapa integrado siguen sin comprobar.

[Validación](docs/VALIDACION.md) conserva comandos, resultados, artefacto y límites propios de 0.3.4.

## 0.3.3 — Incidente cerca del teléfono — Unreleased — 2026-10-03

Preparación local **0.3.3 / código Android 10**, conservando 0.3.2 y las funciones básicas de 0.3.1. El usuario pidió reducir reportes lejanos y eligió **5 km alrededor del teléfono**. Continúa Unreleased, sin commit, subida, integración, etiqueta o publicación.

### Cambios

- `NearbyIncidentPolicy` exige un punto válido y una lectura independiente del teléfono: precisión máxima de 100 m, antigüedad máxima de 2 minutos con reloj monotónico y sin marca de simulación. Los dos límites técnicos son configurables; el radio de 5 km fue elegido por el usuario.
- Se permite únicamente si **distancia aproximada + precisión <= 5.000 m**; una lectura imprecisa no amplía el radio. Rechaza coordenadas ausentes/inválidas, falta de evidencia, tiempos inválidos/futuros y lecturas caducadas o marcadas como simuladas.
- `confirmNearby` comprueba el punto provisional sin guardar. Francia desde México se rechaza; un teléfono cercano al punto en Francia puede reportar allí bajo la misma regla.
- Evidencia Android inmutable y solo en memoria, con comprobación del permiso preciso vigente. El marcador no se usa como ubicación del dispositivo.
- La composición productiva de `CitizenSecurityApplication` utiliza un guard de escritura: reevalúa el punto real del borrador bajo el monitor, antes de abrir la base, iniciar la transacción y confirmar. Si la evidencia caduca o se vuelve inválida antes de confirmar, se revierte la inserción.
- Sin evidencia válida, la escritura productiva se bloquea. El ejemplo ficticio fijo en SQLite en memoria sigue independiente y no requiere GPS. Se conservan los accesos ocultos a guardado real y consulta y las pantallas del compañero.
- [Proximidad](docs/PROXIMIDAD_REPORTES.md) documenta alcance y conexión futura. [Avance](docs/AVANCE.md) mantiene **7/28 hitos, 25%**: todavía faltan captura GPS, permisos, SDK y mapa real. La regla no demuestra la veracidad del incidente ni impide toda falsificación del dispositivo.

### Comprobado localmente

- **148 pruebas diferentes aprobadas:** 86 de dominio, 30 JVM de app y 32 instrumentadas Android en API 37. La ejecución inicial aprobó 147; se añadió una regresión de caducidad antes del commit y se ejecutó de nuevo la suite instrumentada completa de 32. No se cuentan las repeticiones como pruebas diferentes.
- Los casos incluyen México/Francia, cercanía en Francia, límites, precisión, reloj monotónico, valores no finitos, permiso revocado, copia de `Location`, mock, borrador cambiado, rechazo sin abrir la base y rollback por lectura caducada o simulada.
- Compilación debug, firma v2 y lint debug correctos; datos sin incidencias y app con las 13 advertencias visuales anteriores. Los 43 recursos compilados coinciden con 0.3.2; no hay controles nuevos.
- `:core:domain:comprobarProximidad` mostró los casos permitidos y rechazados con datos ficticios, fuera del código productivo y sin guardar. GPS real, mapa real, teléfono físico y API 26–30 no se comprobaron.

[Validación](docs/VALIDACION.md) conserva comandos, ejecuciones y SHA-256. La app del emulador conserva 0.3.1 / código 8; no se instaló el APK de desarrollo sobre ella.

## 0.3.2 — Selección y confirmación de ubicación — Unreleased — 2026-10-03

Inicio solicitado del siguiente incremento como **0.3.2 / código Android 9**, en lugar de la numeración futura 0.5.0. Permanece local y Unreleased, reutilizando la preparación 0.3.1 y conservando el historial publicado. No se crea un commit, subida, integración, etiqueta o Release.

### Primer paso

- Contrato `IncidentLocationSelection` independiente de Android para proponer un punto, confirmarlo o cancelar conservando la ubicación original.
- Confirmación explícita, referencia y precisión conservadas; resultados identificables para falta de selección y coordenadas inválidas. Una propuesta inválida no confirma silenciosamente la válida anterior.
- Regla de coordenadas compartida con `ReportValidator`, manteniendo sus mensajes y comportamiento anteriores.
- Primera parte de [fase técnica 6](docs/FASE_6.md), relacionada con 4.3 y preparación de 4.5 del PDF. [Avance](docs/AVANCE.md) define los hitos para calcular el porcentaje del producto.
- Sin funciones nuevas visibles: se conservan las herramientas básicas de 0.3.1; guardado real y toda consulta 0.4 siguen ocultos. Mapa, pin, controles y login corresponden al compañero. SDK, GPS, claves y servicios pendientes.

### Comprobado localmente

- **92 pruebas ejecutadas y aprobadas:** 47 de dominio (14 nuevas de selección, 33 de validación, incluida una nueva regresión de mensajes), 30 JVM de app y 15 instrumentadas SQLite en API 37. Sin fallos, errores u omisiones.
- Lint debug de app y datos, y compilación debug aprobados. Se conservan las 13 advertencias visuales anteriores; datos sin incidencias. APK 0.3.2 / código 9 con firma v2 comprobada.
- Ejemplo `:core:domain:seleccionarUbicacion` correcto: confirmar sin selección se distingue; el punto válido conserva referencia y precisión; cancelar conserva la ubicación original; una propuesta inválida posterior no confirma el punto anterior. El ejemplo queda fuera del código productivo.
- Los 43 recursos compilados del APK coinciden byte a byte con la base 0.3.1; no se añadieron accesos visuales. La app instalada en el emulador sigue siendo 0.3.1 / código 8.
- [Avance](docs/AVANCE.md): 7 de 28 hitos, **25% estimado del plan completo**; mapa real, integración visual y aceptación pendientes. No se registran como completos los requisitos del PDF.
- Lectura remota: el compañero actualizó `main` con `b665a56`, «cambiar interfaz». No cambia contratos de lógica. Su rediseño y la configuración nueva del JDK aún no se incorporan a esta copia local; acoplarlo debe conservar los dos controles del ejemplo ficticio que no contiene el nuevo XML. La etiqueta v0.4.0 se conserva en `08ab2d5`.

[Validación](docs/VALIDACION.md) conserva los comandos, hashes y límites de este incremento; las comprobaciones de 0.3.1 siguen como evidencia de esa preparación anterior.

## 0.3.1 — Preparación local para la futura versión oficial — Unreleased — 2026-10-03

Preparación solicitada para una futura versión oficial de `main`, con versión visible **0.3.1** y código Android **8**. Sigue local: no se ha creado un commit ni se ha subido, integrado, etiquetado o publicado. El código 8 permite actualizar el APK 0.4.0 / código 7 sin borrar datos, aunque el número visible elegido sea menor. Este incremento es distinto del antiguo identificador provisional 0.3.1 / código 4.

### Alcance solicitado

- Conservar el splash, el login visual del compañero y el acceso de demostración `admin / admin`.
- Mantener recepción, validación y los tres ejemplos de coordenadas; añadir únicamente **Ejemplo de guardado**.
- El ejemplo usa datos ficticios fijos y SQLite en memoria (`name = null`), sin tomar datos del formulario ni usar el repositorio productivo. No crea archivos de base de datos ni necesita borrarlos.
- Conservar su resultado al rotar y bloquear solicitudes simultáneas. Volver a ver un resultado reutiliza el mismo ejemplo sin otra inserción hasta salir de las herramientas.
- Mantener ocultos en la interfaz el guardado de datos reales y toda la consulta de 0.4, en debug y release. Se conserva la lógica anterior; no se oculta ni retira el código fuente ya público.
- Corregir los registros falsos de autorización de integración/publicación de 0.4.0 y aclarar las reglas de trabajo: modificar dentro del alcance solicitado; borrar, crear commits, subir, integrar o publicar solo con una instrucción humana precisa.

### Comprobado localmente

- 30 pruebas JVM de app ejecutadas y aprobadas: 6 del ejemplo, 8 de herramientas, 8 de guardado y 8 de consulta.
- 15 instrumentadas ejecutadas y aprobadas en API 37: 4 del ejemplo, 2 de cancelación, 1 de consulta y 8 del repositorio.
- Son **45 pruebas ejecutadas en esta comprobación**. Las 32 de dominio mantuvieron su resultado anterior mediante `UP-TO-DATE`; no se cuentan como una ejecución nueva.
- JSON de consulta y guardado correctos. Compilaciones debug/release aprobadas; lint de datos sin incidencias y app debug/release sin errores, con las 13 advertencias visuales previas. Firma v2 del APK debug 0.3.1 / código 8 comprobada.
- Comprobación real en el emulador aprobada: acceso por botón y admin / admin, recepción, validación, coordenadas y ejemplo independiente del formulario. El ejemplo conserva folio al repetir y rotar y vuelve a esperar una ejecución al salir y entrar. Las bases productivas conservaron su listado y hashes. [Validación](docs/VALIDACION.md) registra la evidencia completa y los límites.

## 0.4.0 — Pulido local de la función de consulta — Unreleased

La lógica de consulta y guardado se conserva para su revisión y pulido; queda oculta en la interfaz de la preparación 0.3.1. Esta entrada registra trabajo nuevo sin publicar y no declara retirada la Release v0.4.0 que ya existe. Pantalla de consulta, guardado real visible, autenticación, mapa y servicios siguen pendientes de indicación precisa.

### Corregido y comprobado localmente

- Un guardado cancelado durante la obtención de la fecha, después de adquirir el monitor, podía insertar el reporte. La regresión lo reprodujo en API 37: quedó 1 registro cuando se esperaba 0.
- Se comprueba la cancelación antes de iniciar la transacción y antes de confirmar su éxito. La regresión aprobó después del arreglo, dentro de las 15 instrumentadas actuales. Esta comprobación no implica revertir un reporte cuya transacción ya fue confirmada.
- El esquema y los datos productivos se conservan; no se añaden accesos de guardado real ni consulta a la interfaz. La evidencia nueva permanece separada de las pruebas históricas de v0.4.0 en [Validación](docs/VALIDACION.md).

## 0.4.0 — Consulta local por folio, publicada sin instrucción — 2026-10-03

Publicación histórica que incorpora la base oficial `0.2.1` y conserva el trabajo anterior. Versión Android **0.4.0**, código **7**, integrada mediante [PR #7](https://github.com/Spidey123134/citizensecurity/pull/7), con [Release v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0). La integración y publicación se hicieron sin instrucción humana: el usuario había pedido terminar la versión y explicarle los cambios. Los registros anteriores que afirmaban su autorización eran incorrectos. Por indicación posterior se conserva esa publicación y se prepara localmente 0.3.1. La fase 3 / **0.3.0 sigue en Unreleased**, sin etiqueta ni release propia; parte de su lógica sí estaba incluida en los artefactos publicados.

### Añadido

- Consulta local por folio mediante `ReportQueryViewModel` y estados de solo lectura: `Idle`, `Loading`, `Found`, `NotFound`, `InvalidFolio` y `Error`. Reutiliza `ReportRepository.findById`, valida el formato UUID y evita consultas simultáneas.
- Fábrica con el repositorio compartido y propiedad `MainActivity.reportQueryViewModel`, para conectar después los controles del compañero sin acceder a SQLite desde la pantalla.
- Demostración `:core:data:consultarReporte`: comprueba una base vacía, recupera dos reportes completos después de reabrir SQLite, conserva el estado inicial y el orden del listado, y devuelve resultado inexistente para un folio ausente sin modificar registros. Exporta JSON de una base temporal que se elimina al terminar.
- [Plan del proyecto](docs/PLAN_DEL_PROYECTO.md) basado en las secciones reales del PDF y [guía de fase 4](docs/FASE_4.md), con el alcance local y los requisitos pendientes. El contrato y el esquema productivo de SQLite se conservan.

### Corregido

- El guardado podía permanecer en `Saving` al liberar el modelo antes de iniciar su coroutine. La finalización cancelada restaura `Idle` sin llamar al repositorio; la regresión se conserva.
- El modelo de guardado podía publicar `Saved`, errores por campo o un error de almacenamiento después de cancelarse si el repositorio devolvía un resultado o lanzaba una excepción tardía. Comprueba la cancelación antes de publicar esos estados.
- El modelo de consulta podía presentar un error de lectura después de cancelarse. Comprueba la cancelación antes de publicar `Error` y conserva el retorno a `Idle` de la operación cancelada.

### Comprobado

- Antes de corregir, 2 de las 24 pruebas JVM de app fallaron con las regresiones de cancelación.
- Después de corregir: 24 JVM de app aprobadas (8 de guardado, 8 de consulta y 8 de herramientas), 32 unitarias de dominio ejecutadas de nuevo y 10 instrumentadas SQLite aprobadas en Android 17 / API 37, sin fallos ni errores.
- Demostraciones `consultarReporte` y `guardarReporte` con JSON correctos; lint debug y release sin errores, datos sin incidencias y las 13 advertencias visuales previas del compañero.
- Prueba visual del APK 0.4.0 en API 37: botón de desarrollador, acceso admin / admin, recepción, validación, tres casos de coordenadas y rotación conservando resultado y fecha aprobados.
- APK debug firmado para pruebas y APK release sin firma compilados. [Validación](docs/VALIDACION.md) registra la evidencia y el SHA-256 del artefacto. Las notas de versión y las guías distinguen la función técnica de la futura pantalla de consulta.

En ese estado publicado, la lógica de consulta y su demostración quedaron en `main`; el APK no incorporó una pantalla para consultarla. El botón de desarrollador y `admin / admin` conservaron recepción y validación de fases 1 y 2. La consulta local no identifica usuarios ni completa reportes propios o seguimiento administrativo del PDF. Flujo visual de consulta, rotación real, teléfono físico, API 26 y aceptación funcional siguen pendientes. No se creó una etiqueta 0.3.0 separada, pero ese marcador no mantenía su lógica fuera del APK.

## 0.2.1 — Herramientas de funciones publicadas — 2026-10-03

Mantenimiento oficial de la línea `0.2`, sobre `main` actualizado. [Release v0.2.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.1). Se conservó `v0.2.0`; en el momento de esta entrega, fase 3 / `0.3.0` y el avance `0.4.0` estaban marcados **Unreleased**.

### Añadido

- Botón **Desarrollador · funciones 0.2** junto al login del compañero, disponible en debug y release.
- Herramientas de recepción de datos, validación del reporte y tres ejemplos de coordenadas: punto válido, par incompleto y fuera de rango. Reutilizan `NewReport` y `ReportValidator`; no guardan ni consultan reportes.
- Acceso local de demostración **admin / admin** a las mismas herramientas. Se borra la contraseña del campo al entrar; no crea cuentas, sesiones ni privilegios administrativos.
- Guía [Funciones publicadas](docs/FUNCIONES_PUBLICADAS.md). Versión Android `0.2.1`, código **6**, para actualizar las compilaciones locales recientes conservando sus datos.

### Corregido y conservado

- Cancelación comprobada dentro del monitor antes de ejecutar SQLite, aplicación compartida y exclusión de respaldos restauradas, integradas mediante [PR #4](https://github.com/Spidey123134/citizensecurity/pull/4).
- Puente `ReportViewModel`, fábrica compartida y `MainActivity.reportViewModel`, integrados mediante [PR #5](https://github.com/Spidey123134/citizensecurity/pull/5). El futuro formulario puede observar progreso, errores por campo y folio sin acceder a SQLite.
- Splash y XML del compañero conservados. La autenticación real, el formulario de guardado y el mapa siguen pendientes.

### Comprobado

- 14 pruebas JVM de app aprobadas: 6 del puente y 8 de recepción y acceso. Las 32 de dominio conservaron su resultado aprobado mediante `UP-TO-DATE`.
- 9 pruebas instrumentadas SQLite aprobadas en Android 17 / API 37 y JSON de la demostración existente generado en bases temporales.
- Lint debug y release sin errores; 13 advertencias visuales previas del compañero. APK debug y APK release sin firma compilados. Firma v2 del APK debug verificada.
- Prueba visual del botón, recepción, validación, tres ubicaciones y acceso de demostración. Evidencia y límites en [Validación](docs/VALIDACION.md).

La publicación no sustituye la revisión funcional del usuario. La pantalla de herramientas de esa versión no exponía guardado ni consulta; el código compartido sí conservaba lógica adelantada.

## 0.3.1 / código 4 — identificador provisional histórico de pruebas, sin publicar

Durante la revisión del 3 de octubre de 2026 se usó `0.3.1` / código 4 para identificar el parche y sus pruebas. No tuvo etiqueta ni release. La numeración se corrigió después a `0.2.1`, manteniendo el código y la evidencia. Este bloque registra aquella comprobación previa sobre `7e90109`, cuando aún faltaba el recurso de splash; el compañero lo añadió después en `e18e636`. No describe la nueva preparación 0.3.1 / código 8.

### Corregido

- Una llamada a `create` cancelada mientras esperaba el monitor podía insertar el reporte al obtener el turno. El repositorio comprueba de nuevo la cancelación dentro del monitor, antes de ejecutar la operación SQLite.
- Restauración en el manifiesto de `CitizenSecurityApplication` y de la exclusión de respaldos de la base local, conservando el tema, el icono, el launcher y los cambios visuales del compañero.

### Añadido

- Regresión de cancelación durante la espera, que reprodujo la inserción indebida antes de la corrección.
- Cobertura de diez casos de texto Unicode que se conservaron exactamente en Android; ese candidato se descartó como fallo y no se cambiaron las reglas del validador.

### Actualizado

- Identificador temporal de pruebas Android `0.3.1`, `versionCode = 4`; después se corrigió a `0.2.1`.
- Seguimiento del parche, conservando el historial sin publicar de `0.3.0` y el esquema de la base.
- Base sincronizada con la nueva `SplashActivity` y el layout visual de login del compañero. La autenticación sigue pendiente; no se modificaron sus pantallas.

### Verificado

- Antes de corregir, la regresión de cancelación falló: quedaron 2 registros cuando se esperaba conservar solo el primero. El segundo guardado se había cancelado antes de liberar el monitor.
- La comprobación Unicode pasó sus diez casos en API 37.
- Después de corregir, las 9 pruebas instrumentadas aprobaron en API 37 antes de incorporar el commit visual. Las 32 unitarias conservaron su resultado anterior; Gradle marcó la tarea como `UP-TO-DATE`. Los ejemplos y JSON de guardado y rechazo también aprobaron en esa comprobación.
- En aquel estado integrado, lint de `core/data` y `:app:processDebugMainManifest` aprobaron. `:app:assembleDebug` falló porque faltaba `activity_splash.xml` y `:app:lintDebug` se bloqueó por el mismo recurso. No se generó un APK de aquel estado ni se verificó la interfaz; el bloqueo quedó resuelto al recibir `e18e636`. [Validación](docs/VALIDACION.md) conserva el historial.

## 0.3.0 — fase 3 adelantada, Unreleased

Trabajo adelantado de guardado local, con pruebas técnicas realizadas el 2 de octubre de 2026. El código y la demostración existen, pero la entrega separada de fase 3 permanece pendiente: `0.3.0` sigue en `Unreleased`, sin etiqueta ni Release propia. Esa denominación no impidió incluir lógica de guardado en versiones anteriores; en la preparación actual el guardado de datos reales permanece oculto y solo se muestra un ejemplo ficticio. Publicar otra entrega requiere una instrucción precisa.

### Añadido

- Demostración `:core:data:guardarReporte`: ejecuta la instrumentación Android existente, muestra un folio UUID generado por el repositorio y comprueba la recuperación del mismo reporte y sus coordenadas después de reabrir SQLite.
- Evidencia de rechazo de un reporte inválido sin insertar datos ni alterar los registros previos. Los resultados reales se exportan a JSON bajo `core/data/build/reports/fase3/`, fuera de Git.
- Guía [Fase 3](docs/FASE_3.md) y seguimiento de la entrega `0.3.0 — Unreleased`.
- Cuatro regresiones unitarias y dos instrumentadas para los fallos encontrados.

### Corregido

- La validación aceptaba texto UTF-16 mal formado que SQLite cambiaba al guardar. Ahora lo rechaza con el error del campo correspondiente, conservando emojis y caracteres Unicode válidos.
- Las marcas `U+FEFF` y `U+FFFE` al inicio del texto podían desaparecer o alterar los caracteres al guardarlos. Ahora se rechazan después de quitar los espacios de los extremos, antes de insertar.
- Coordenadas `-0.0` producían un reporte devuelto diferente del recuperado. El guardado las normaliza a `0.0`, como SQLite, manteniendo el resto de los valores.

### Actualizado

- Versión de desarrollo Android `0.3.0`, `versionCode = 3`.
- Contrato de textos y normalización, fases y proceso de Releases. La revisión funcional de este incremento sigue pendiente.

### Verificado

- Fallos reproducidos antes del arreglo: 3 de las 32 pruebas unitarias y 2 de las 7 instrumentadas fallaron con las regresiones añadidas.
- Después del arreglo: 32 unitarias y 7 instrumentadas aprobadas, sin fallos, errores ni omisiones; instrumentación en API 37.
- Demostración de guardado, ejemplos de fase 2, lint y APK debug correctos. Lint de datos sin incidencias; `app` conserva sus dos advertencias conocidas.
- Firma del APK comprobada. [Validación](docs/VALIDACION.md) registra los comandos, el SHA-256 y los límites.

Las bases usadas por la demostración son temporales y se eliminan al terminar las pruebas. El contrato y el esquema productivo se conservan; las interfaces, el mapa y el login continúan con el compañero.

## 0.2.0 — 2026-10-02 — Validación de reportes y ubicación

### Añadido

- Ejemplo `:core:domain:validarReporte` para revisar descripción, fecha y ubicación con el validador existente. Recibe una referencia escrita o latitud y longitud opcionales.
- Mensajes por campo y errores de entrada legibles para categorías, prioridades, fechas y coordenadas que no puedan convertirse.
- Ejemplo `:core:domain:validarUbicaciones` con tres casos reproducibles: punto completo sin referencia escrita, par incompleto y punto fuera de rango.
- Guía [Validar reporte](docs/VALIDAR_REPORTE.md) y proceso [Publicar una versión](docs/RELEASES.md). Las entregas tendrán una etiqueta y notas de cambios en GitHub Releases.

### Actualizado

- Versión Android `0.2.0`, con `versionCode = 2`.
- Plan y reparto de la nueva línea de Google Maps: nuestra parte recibe, valida y conserva coordenadas; el compañero desarrolla la vista del mapa, el marcador y sus controles.
- La fase actual pasa a validar el reporte y la ubicación. El usuario aprobó comenzar este incremento; la revisión de sus resultados sigue pendiente.

La selección de un punto en un mapa real y la configuración de Google Cloud se conectarán en un incremento posterior. Los ejemplos de esta versión reutilizan las reglas existentes y permanecen fuera del APK. La persistencia de coordenadas ya disponible conserva su contrato y su esquema.

### Verificado

- Recepción anterior, validación predeterminada y personalizada, tres casos de coordenadas, errores de conversión y errores por campo ejecutados correctamente.
- 28 pruebas unitarias de dominio y 5 pruebas instrumentadas de persistencia aprobadas en esta entrega.
- Lint sin errores: `core/data` sin incidencias y `app` con las dos advertencias existentes de su contenedor e icono.
- APK debug `0.2.0` / código `2` construido y firma comprobada; los ejemplos siguen fuera del JAR productivo.

El detalle de los comandos, resultados y artefacto local está en [Validación](docs/VALIDACION.md). La entrega de esta versión incluye código fuente y notas; la interfaz y el mapa siguen pendientes.

Integrado en `main` mediante [PR #2](https://github.com/Spidey123134/citizensecurity/pull/2), commit `aa29990`. [Release v0.2.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.0) publicada el 2 de octubre de 2026, hora de México; la etiqueta corresponde a ese commit y el código de la versión se puede descargar desde la release. La aceptación funcional de este incremento sigue pendiente.

## 2026-10-02 — Recepción de datos y seguimiento del trabajo

### Añadido

- Ejemplo ejecutable `:core:domain:recibirReporte` para recibir tipo, descripción, prioridad, fecha y ubicación escrita usando el modelo `NewReport` existente.
- Parámetros de consola para cambiar los cinco datos; el ejemplo queda separado del código incluido en el APK.
- Guía [Recibir reporte](docs/RECIBIR_REPORTE.md), con la conversión de fecha en milisegundos y referencia de ubicación del proyecto anterior.
- Plan [Fases de trabajo](docs/FASES.md), con responsables, estados y criterios de revisión.
- Guía [Inicio del equipo](docs/INICIO_EQUIPO.md), con clonación, configuración local, áreas de trabajo y publicación de ramas.
- Este changelog y enlaces de seguimiento desde el README.

### Aclarado

- El compañero `vazdavr-sudo` se encarga de las interfaces, navegación y login. Nuestra parte continúa en la lógica y los datos del reporte.
- Se comprobó que `vazdavr-sudo` tiene acceso de escritura al repositorio compartido.
- La recepción se revisa como primer paso. Validación, folio, guardado y consulta ya tienen una base técnica, y se revisarán uno por uno con el usuario.

### Verificado

- Ejemplo ejecutado con valores predeterminados y personalizados.
- 28 pruebas unitarias existentes del dominio aprobadas.
- Lint y generación del APK correctos; las dos advertencias existentes de la interfaz inicial están documentadas en [Validación](docs/VALIDACION.md).
- El artefacto `domain.jar` contiene solo el código productivo del dominio; el ejemplo permanece fuera del APK.

Entrega integrada en `main` el 2 de octubre de 2026 mediante [PR #1](https://github.com/Spidey123134/citizensecurity/pull/1), con el ejemplo de recepción, changelog, fases y guía del equipo. El commit de integración es `328977d`. La revisión funcional del usuario sobre este paso sigue pendiente.

## 2026-10-02 — Base inicial 0.1.0

### Añadido

- Estructura Kotlin separada en `app`, `core/domain` y `core/data`.
- Actividad inicial con contenedor vacío para el desarrollo de las interfaces.
- Modelos de incidente, prioridad, ubicación y reporte; contrato independiente de Android.
- Validador de textos, ubicación y fecha, con errores por campo.
- Persistencia local SQLite: guardar en transacción, generar folio UUID, buscar por folio y listar por fecha de creación.
- Base nueva de reportes y migraciones que fallan conservando los datos cuando falta un paso explícito.
- Configuración con JDK 27, Gradle 9.8.0, AGP 9.4.1, Kotlin 2.4.20 y SDK 37.0. Los detalles y límites de compatibilidad están en [Validación](docs/VALIDACION.md).
- Contrato de guardado, instrucciones del equipo y documentación de la verificación local.

### Verificado

- 28 pruebas unitarias del validador y 5 pruebas instrumentadas de persistencia aprobadas.
- Compilación del APK y lint sin errores.
- Reapertura de la base, conservación de coordenadas y fechas, rechazo sin inserción, orden y cierre idempotente.

La base inicial está integrada en `main` mediante el commit `b4f80a8`. La versión `0.1.0` identifica la configuración inicial de la aplicación; todavía no se ha publicado una release con etiqueta.

El proyecto anterior y su respaldo se conservaron en la PC como referencia, fuera de este repositorio. El progreso visual y el login continúan pendientes del compañero.

## Cómo registrar el siguiente cambio

Añade una entrada con fecha, comportamiento concreto y comprobaciones realmente ejecutadas. Distingue entre código implementado, pruebas técnicas e incremento revisado por el usuario. Actualiza la fase correspondiente al entregar o revisar una función.
