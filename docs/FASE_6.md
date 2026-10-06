# Fase 6: selección y confirmación de ubicación

**Validación 0.4.0a:** 266 pruebas distintas ejecutadas y aprobadas, 57 definiciones nuevas; 124 JVM de dominio/datos reutilizadas. Debug/release compilan, firma debug v2 válida, lint sin errores: app19 advertencias visuales, laboratorio2 por variante y datos0. [Validación](VALIDACION.md) documenta el pase y sus límites. La reapertura del mapa usa el mismo modelo/puente, exige renovación explícita e ignora capturas anteriores; el borrador no restaura autorización GPS ni guarda por sí solo.

Versión actual: **0.4.0a / código Android 19**, del **6 de octubre de 2026**, oficial por instrucción humana; integración local y validación completadas; el estado de publicación se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a).

Se completan dos partes técnicas: **fase 6A**, puente de ubicación por ciclo de Activity y clicks Maps con gestos visibles; **fase 7**, borrador editable que conserva los campos al volver del mapa y restaura estado guardado por Android. No solicitan GPS ni guardan automáticamente. La interfaz de `main` del compañero se incorpora conservando sus cambios; el ejemplo temporal visible mantiene sus controles. Guardado real y consulta siguen ocultos.

El pendiente **0.3.11** quedó cerrado: 247 pruebas ejecutadas, 16 definiciones nuevas, firma debug correcta y lint sin errores. Son antecedentes, no resultados nuevos de 0.4.0a. [Validación](VALIDACION.md) separa los cortes.

**Avance del PDF: 7/28 = 25%.** Las dos partes técnicas no acreditan el formulario completo, GPS físico, mapa con clave ni autenticación. Fase 6 continúa abierta; [fase 7](FASE_7.md) documenta el borrador y sus límites.

## Antecedente 0.3.10 — Unreleased

La preparación activa es **0.3.10 / código Android 17**, del **5 de octubre de 2026**, local y Unreleased. Continúa la **fase técnica 6** sobre geolocalización y mapas (4.3 y 4.5 del PDF). Se comprueban juntos los componentes existentes, sin cambios de lógica productiva, interfaz o login.

**Validación 0.3.10:** **21 casos Android distintos aprobados**, cero fallos, errores u omitidos: tres diálogos extendidos, 16 de composición del flujo y dos del proveedor/adaptador Android. Son **18 definiciones nuevas**; las **121 JVM de app quedaron `UP-TO-DATE`** y conservan la evidencia anterior. Debug/release compilan, firma debug v2 verificada y lint sin errores, con 13 advertencias previas de app y dos por variante técnica. [Validación](VALIDACION.md) separa las ejecuciones y los límites. La matriz usa lecturas y reloj controlados con permiso/ciclo Android reales; el registro GPS nativo se canceló antes de un callback, sin obtener una posición física.

El laboratorio comparte las fuentes productivas sin copiarlas. Sus tres paquetes de permisos conservan el diálogo real y añaden la comprobación de que su callback no captura. El cuarto paquete `.flow` comprueba gesto de renovación, calidad de lectura, cercanía, error/timeout, concurrencia, `onStop`, recreación y respuestas tardías. Los fixtures y su tiempo de espera corto existen solo en `verification/location`; el producto conserva 15 segundos, precisión máxima 100 m y vigencia máxima dos minutos.

[Flujo Android](FLUJO_UBICACION_ANDROID.md) explica los eventos para los controles del compañero. [Pruebas Android de permisos](PRUEBAS_PERMISOS_ANDROID.md) documenta aislamiento y repetición. Registro/cancelación nativos no equivalen a obtener GPS físico, ni recreación de Activity a muerte del proceso.

Selección, cercanía de **5 km** y protección SQLite se conservan. Guardado real y consulta permanecen ocultos; la app instalada conserva 0.3.1/código 8, fecha y hash. Clave Google, conexión de controles, lectura GPS válida de un teléfono y mapa renderizado siguen pendientes. No se instala `:app` sobre el producto ni se publica este incremento.

**Avance: 7/28 = 25%**. La composición técnica aprobada no completa el hito de permisos/estados GPS en la pantalla ni el flujo GPS físico. La fase 6 continúa abierta y fase 5 mantiene su conexión visual pendiente.

## Antecedente 0.3.9 — Unreleased

0.3.9 / código 16 aprobó tres decisiones de permiso nativas y recreación con el diálogo abierto. Las 121 JVM de app se ejecutaron de nuevo entonces, para un total de 124 pruebas. Debug/release y lint aprobaron; los APK y resultados de ese corte se preservan. Sus límites incluyen GPS físico, muerte del proceso y pantalla integrada.

## Antecedente 0.3.8 — Unreleased

La preparación anterior **0.3.8 / código Android 15**, del **5 de octubre de 2026**, permaneció local y Unreleased. El usuario indicó que todavía no tiene clave de Google y eligió avanzar con ubicación y permisos. La fase técnica **6** siguió abierta; no se abrió otra fase ni se dio por aceptada la conexión visual de la fase 5.

`LocationPermissionViewModel` conserva el estado de permiso preciso, aproximado o ausente y una solicitud pendiente durante la rotación. `PreciseLocationPermissionBinding` registra el contrato AndroidX para solicitar `COARSE` y `FINE` juntos, únicamente mediante un gesto con el dueño en `RESUMED`. El resultado relee permisos actuales y nunca inicia GPS, confirma el punto ni guarda un reporte. Se distinguen explicación previa, cancelación y fallos de lectura o lanzamiento, sin inferir denegación permanente a partir de `shouldExplain = false`.

El compañero obtiene el modelo con `ViewModelProvider` y su fábrica, registra el puente siempre en `onCreate` antes de `STARTED` y mantiene el mismo orden de contratos al recrear la Activity. El puente vive durante todo el `ActivityResultCaller`, no durante cada `MapView`; para `forActivity`, se cierra en `onDestroy` de la Activity. `check()` al volver relee hechos sin abrir diálogos. [Permisos de ubicación](PERMISOS_UBICACION.md) contiene el contrato y un ejemplo de composición para sus controles.

La preparación conserva selección, regla de **5 km**, fuente GPS y modelo de mapa de los incrementos anteriores. `IncidentLocationViewModel.onStop()` mantiene su obligación independiente de cancelar la captura y exigir una nueva lectura. No se cambian dominio o persistencia en este paso, no se modifica interfaz/login y el guardado real y la consulta siguen ocultos. La clave, el diálogo integrado, la captura GPS real y el mapa visible permanecen pendientes; no se instaló el incremento sobre la aplicación actual del usuario.

Las pruebas del nuevo contrato usan un dueño controlado y no acreditan el diálogo o rotación Android reales. [Validación](VALIDACION.md) registra por separado los resultados finalmente ejecutados, el mantenimiento de compatibilidad y los artefactos. **Avance: 7/28 = 25%**, sin sumar otro hito por preparar el puente de permisos. No hay commit, subida, integración, etiqueta ni Release nueva.

## Antecedente 0.3.7 — Unreleased

La preparación local **0.3.7 / código Android 14** se conserva como antecedente. Los incrementos 0.3.5 a 0.3.7 agrupan refuerzo del proveedor GPS, un modelo compartido cancelable para captura y selección, y conexión al SDK con verificación local de prerrequisitos. También se protege la base frente a recreación por corrupción y se revisa la cancelación después de los guards de escritura. No hay tres APK intermedios entregados ni publicaciones nuevas. [Incrementos 0.3.5–0.3.7](INCREMENTOS_035_037.md) describe cambios, estados y el contrato con el compañero.

El compañero obtiene `IncidentLocationViewModel` desde `CitizenSecurityApplication.incidentLocationViewModelFactory(originalLocation)`, observa `state` y conecta su `GoogleMap` mediante `IncidentLocationMapBinding`. `onStop` cancela captura y exige renovar evidencia antes de confirmar, conservando el pin. El modelo bloquea solicitudes simultáneas y confirmación mientras captura; respuestas tardías no reabren un flujo cancelado. Solo una confirmación aceptada entrega coordenadas, sin guardar ni consultar reportes.

Antes de crear la vista, `application.mapsReadinessChecker.check()` distingue clave ausente y servicios locales no disponibles. `Ready` no valida Google Cloud ni acredita carga de mapa. La clave, el mapa visible, permisos interactivos y GPS real siguen pendientes. Interfaz y login del compañero permanecen intactos; guardado real y consulta continúan ocultos.

La verificación integral aprobó pruebas JVM e instrumentadas API 37, compilaciones debug/release y lint; [Validación](VALIDACION.md) conserva el recuento final, resultados reutilizados y límites. No prueba teléfono físico, GPS real ni mapa renderizado. **Avance: 7/28 = 25%**, sin sumar hitos por reforzar contratos ya existentes. La fase 5 y la aceptación funcional de la fase 6 continúan pendientes.

## Antecedente 0.3.4: incorporación del SDK

La preparación 0.3.4 incorpora ahora el **Maps SDK for Android 20.0.0** real, metadato de clave local y puente `GoogleMapIncidentBinding` hacia `IncidentMapSession`. Se declaran internet y ubicación aproximada/precisa; el permiso interactivo y el mapa visible siguen pendientes. La clave Google Cloud todavía no está configurada. Véase [Google Maps](GOOGLE_MAPS.md). Compilación debug, firma v2, lint (13 advertencias previas) y **41 JVM de app, incluidas 11 nuevas de selección**, aprobadas. No se probó carga de mapas real ni se publicó nada. **Avance comprobado: 7/28 = 25%**.

Los bloques previos de 0.3.4 sobre captura sin SDK ni permisos conservan la comprobación anterior a esta incorporación; sus resultados no prueban mapas reales.

## 0.3.4 — Unreleased: obtener y renovar la lectura del teléfono

La preparación anterior **0.3.4 / código Android 11** se mantuvo local y Unreleased, sobre 0.3.3 y las bases anteriores. Continuó la **fase técnica 6**, relacionada con **4.3 Geolocalización** y **4.5 Mapa de incidentes** del PDF. Su paso inicial preparó una solicitud explícita de ubicación del teléfono; la pantalla del mapa, el login y sus controles siguieron a cargo del compañero.

`PhoneLocationRefresh.refresh()` necesita permiso preciso y GPS habilitado, descarta la evidencia anterior al iniciar una renovación y espera una lectura durante **15 segundos** como valor técnico configurable. Comprueba coordenadas, precisión máxima de **100 m**, vigencia monotónica de hasta **2 minutos** y ausencia de marca de simulación mediante la misma política de 0.3.3. Solo conserva una lectura válida. Devuelve estados identificables para lectura lista, permiso pendiente, GPS deshabilitado, lectura no disponible, tiempo agotado, solicitud simultánea o rechazo de calidad. La cancelación externa se propaga y limpia la evidencia.

La composición compartida ofrece `CitizenSecurityApplication.phoneLocationRefresh` de forma diferida, sin invocarlo automáticamente. Esta preparación no añade permisos al manifiesto, solicitudes visuales de permiso, mapa, SDK, login ni controles nuevos. El flujo futuro debe solicitar la lectura desde una pantalla visible y cancelar su trabajo al salir o en `onStop`; el ejemplo fijo en memoria sigue separado y no necesita GPS.

**`Ready` acredita una lectura aceptada en ese momento; no autoriza cualquier punto del mapa.** `confirmNearby` y el guard de escritura vuelven a comprobar la vigencia y la distancia al punto actual, con **distancia aproximada + precisión <= 5.000 m**. El radio de 5 km fue elegido por el usuario; los 15 segundos de espera son una configuración técnica, no otra decisión atribuida al usuario.

[Obtener ubicación](OBTENER_UBICACION.md) documenta el contrato y la conexión futura. La fuente Android usa el proveedor GPS y devuelve la primera lectura, limpiando la suscripción al terminar o cancelarse. Una primera lectura imprecisa, antigua o marcada como simulada produce `Rejected` y requiere otro intento explícito; no espera hasta mejorar precisión dentro de los 15 segundos.

El cierre local aprobó **185 pruebas diferentes**: **86 de dominio, 30 de app, 20 JVM de datos y 49 instrumentadas API 37**, con cero fallos, errores u omisiones. La primera ejecución completa aprobó en 20 segundos; tras corregir un uso obsoleto en una prueba, se repitieron las mismas 49 instrumentadas y lint de datos en 10 segundos. No se suman otra vez las repeticiones. Se corrigió y comprobó una carrera de tiempo agotado durante publicación para impedir que conserve evidencia. Lint debug quedó sin errores, con las 13 advertencias previas de app; compilación debug, firma v2 y metadatos **0.3.4 / código 11** aprobaron. Los 43 recursos del APK coinciden con 0.3.3.

Las fuentes y backend controlados, junto con el camino nativo de permiso denegado, **no demuestran captura GPS real**. No se probó API 26–29, teléfono físico ni Google Maps; no se añadieron permisos ni cambios visuales y no se instaló el APK sobre la app 0.3.1 del usuario. [Validación](VALIDACION.md) registra el cierre 0.3.4. **Avance: 7/28 hitos, 25%**, hasta verificar captura real, permisos y flujo integrado. La fase 5 y el mapa real continúan pendientes.

Las [mejoras futuras](MEJORAS_FUTURAS.md) son ideas para revisión posterior, sin implementación, fecha ni versión oficial comprometida. Los bloques siguientes conservan la evidencia histórica de 0.3.3 y 0.3.2.

## 0.3.3 — Unreleased: incidente cerca del teléfono

El incremento **0.3.3 / código Android 10** se preparó sobre las bases locales 0.3.2 y 0.3.1. El usuario pidió impedir reportes lejanos y confirmó **5 km alrededor del teléfono**. La versión permanece local y Unreleased, sin commit, subida, integración, etiqueta ni publicación. No modifica pantallas ni activa guardado real o consulta.

`NearbyIncidentPolicy` exige un punto completo y válido y una `DeviceLocationFix` independiente del marcador. La lectura debe incluir precisión finita entre 0 y **100 m**, una marca monotónica no futura con antigüedad máxima de **2 minutos** y no estar marcada como simulada. Los límites de precisión y vigencia son valores técnicos configurables; el radio de **5.000 m** fue elegido por el usuario. Solo se permite si **distancia aproximada + precisión <= 5.000 m**, para evitar ampliar el radio por una lectura imprecisa.

`confirmNearby` comprueba esa regla antes de devolver una ubicación confirmada. La composición productiva comprueba nuevamente la evidencia y el punto actual antes de iniciar y confirmar una escritura: confirmar una vez no deja un permiso indefinido. Sin lectura o con datos inválidos, antiguos, demasiado imprecisos o marcados como simulados, el punto se rechaza. El ejemplo ficticio en memoria permanece separado y no solicita ubicación.

La restricción es de proximidad, no de país: rechaza un punto en Francia desde México; si la persona está cerca del incidente en Francia, el país no lo invalida. La regla es local, no prueba que ocurrió el incidente ni impide toda falsificación del dispositivo. La distancia es esférica aproximada y la precisión de Android es una estimación, no una garantía de posición.

[Proximidad de reportes](PROXIMIDAD_REPORTES.md) documenta el contrato, los motivos de rechazo y la conexión futura. La obtención y renovación real de la ubicación, permisos, SDK y pantalla del mapa siguen pendientes. La verificación nueva de 0.3.3 se registra en [Validación](VALIDACION.md), separada de las 92 pruebas de 0.3.2. Se conserva **7/28 hitos, 25%**: este incremento fortalece el contrato de mapa sin completar otro hito de GPS o SDK.

La comprobación nueva de 0.3.3 aprobó **148 pruebas diferentes**, lint debug y compilación debug, manteniendo las 13 advertencias previas. Corresponde al contrato y guard de proximidad, no a GPS o mapa reales; [Validación](VALIDACION.md) registra ambas ejecuciones. El bloque siguiente conserva el historial del paso 0.3.2.

## 0.3.2 — Unreleased

El usuario pidió iniciar el siguiente incremento como **0.3.2**, en lugar de la numeración futura 0.5.0. El código Android es **9**. La preparación permanece local y Unreleased; no se crea un commit, subida, integración, etiqueta o Release. Las publicaciones históricas se conservan.

La base es la preparación local **0.3.1 / código 8**, con recepción, validación, coordenadas y el ejemplo ficticio de guardado en memoria. Ese trabajo se conserva. El guardado de datos reales y toda consulta 0.4 siguen sin acceso desde la interfaz. El APK 0.3.1 comprobado se conserva fuera de Git en `.gradle/validacion/citizensecurity-0.3.1-debug.apk`.

La lectura remota detectó el nuevo rediseño del compañero en `main`, commit `b665a56`. La preparación local todavía parte de `08ab2d5` y no incorpora ese XML: su acoplamiento debe conservar los controles del ejemplo ficticio y revisar la configuración del JDK. El contrato nuevo de ubicación no tiene conflicto directo con ese cambio visual. [Validación](VALIDACION.md) distingue ambas bases y registra el pendiente.

## Primer paso de nuestra parte en 0.3.2

Preparar una selección provisional de ubicación independiente de Android y Google Maps:

1. Partir de la ubicación que ya contiene el borrador.
2. Proponer un nuevo par de coordenadas sin modificar esa ubicación ni guardar un reporte.
3. Confirmar explícitamente un punto válido para obtener un `ReportLocation`, conservando la referencia escrita y la precisión del par.
4. Devolver un resultado identificable si falta seleccionar un punto o las coordenadas son inválidas.
5. Cancelar y recuperar exactamente la ubicación original. Una propuesta inválida posterior no permite confirmar silenciosamente otra anterior.

El contrato inicial valida únicamente las coordenadas. El borrador completo sigue pasando por `ReportValidator`, que verifica descripción, referencia y fecha antes de un eventual guardado autorizado. Se reutiliza la misma regla de coordenadas para ambos contratos. La proximidad adicional de 0.3.3 se comprueba por separado y no se deduce de esta validación geométrica.

## Contrato geométrico inicial de 0.3.2

El siguiente ejemplo histórico comprueba solo coordenadas; **`confirm()` no acredita cercanía al teléfono**. Para la futura confirmación de un incidente de 0.3.3, usa el camino protegido `confirmNearby` y la evidencia descrita en [Proximidad](PROXIMIDAD_REPORTES.md). Ninguno de los ejemplos activa un guardado real.

```kotlin
// Borrador recibido por la pantalla, todavía sin guardar.
var selection = IncidentLocationSelection(draft.location)
selection = selection.propose(latitude, longitude)

when (val result = selection.confirm()) {
    is LocationConfirmation.Confirmed -> {
        val updatedDraft = draft.copy(location = result.location)
        // Devolver este borrador al formulario; todavía no guardarlo.
    }
    LocationConfirmation.NoSelection -> { /* Pedir seleccionar un punto. */ }
    is LocationConfirmation.InvalidCoordinates -> { /* Mostrar result.message. */ }
}
```

`propose` devuelve una nueva sesión inmutable: la pantalla debe conservar ese resultado. `cancel()` devuelve la ubicación original y la pantalla debe descartar la sesión, sin aplicar ninguna propuesta. El contrato no conserva estado de pantalla entre recreaciones; esa conexión y sus pruebas de rotación se coordinan con el compañero.

Para observar el resultado sin una pantalla, desde la raíz del proyecto con JDK 27:

```powershell
.\gradlew.bat :core:domain:seleccionarUbicacion --console=plain
```

La demostración imprime ubicación original, propuesta, confirmación, cancelación y rechazo de un punto inválido posterior. Usa datos ficticios y el source set `examples`, fuera del código productivo de la app; no guarda ni consulta reportes.

## Relación con el plan y reparto del paso 0.3.2

Este paso pertenece a la **fase técnica 6**, relacionada con **4.3 Geolocalización** y la preparación de **4.5 Mapa de incidentes** del PDF. La fase 5, conexión de las pantallas del reporte, continúa pendiente; la numeración de la versión no indica que las fases previas estén aceptadas.

| Responsable | Paso |
| --- | --- |
| Nuestra parte | Contrato de selección provisional, validación, confirmación y cancelación, con pruebas de lógica. |
| Compañero (`vazdavr-sudo`) | Vista del mapa, pin, botones y conexión de sus eventos al contrato; interfaz y login. |
| Ambos, en un alcance posterior acordado | SDK, proyecto de Google, clave, facturación y comprobación del mapa integrado. |

Esta preparación inicial de 0.3.2 no añade SDK, captura GPS, permisos, claves, llamadas externas, pantallas, botones ni accesos a guardado o consulta. Tampoco implementa el mapa de zonas e incidentes completo del PDF.

## Revisión del primer paso 0.3.2

El primer resultado se comprueba mediante pruebas unitarias de confirmación, cancelación, reemplazo, falta de selección, límites geográficos y valores no finitos. Las compilaciones y lint comprueban la compatibilidad con la base. La evidencia ejecutada se registra en [Validación](VALIDACION.md).

El primer paso aprobó **47 pruebas de dominio, 30 de app y 15 instrumentadas SQLite en API 37**, además de lint debug y compilación debug. La demostración de consola produjo los resultados esperados. La primera parte de esta fase está implementada y probada; la fase completa sigue abierta, con SDK y mapa real pendientes.

Después de revisar este contrato se acuerda el siguiente paso con el compañero. Todavía no se ha comprobado un flujo con Google Maps real ni se registra aceptación funcional del usuario. [Avance](AVANCE.md) explica los porcentajes con hitos explícitos; [Fases](FASES.md) distingue implementación, pruebas y revisión.
