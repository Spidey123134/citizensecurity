# Avance del plan del proyecto

## 0.5.2 — fase 14 — avance comprobado

**11/28 = 39.3%**, sin redefinir hitos o pesos. El punto 4.2 recibe revisión completa y una corrección técnica contra duplicados, pero todavía no completa el hito de formulario con guardado real autorizado. La pregunta para activar guardado/consulta sigue pendiente. Se comprobaron 529 casos distintos; [Fase 14](FASE_14.md) detalla construcción y límites. Para acreditar el siguiente hito falta guardar desde el formulario con autorización expresa, identidad durable y GPS válido. El flujo físico de esta pantalla tampoco se declara completo por pruebas con emulador.

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

## Entrega oficial 0.5.0

**Entrega 0.5.0 / código Android 20 — 9 de octubre de 2026.** El usuario autorizó integrar el avance del PR #8 a main y publicar esta Release oficial, al confirmar 0.5.0. Incluye las fases 8 a 11: coordinación de borrador y ubicación, consulta SQLite por zona, MapLibre/OpenFreeMap con calles y marcadores. La publicación se verifica en [Release v0.5.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.0). La interfaz y el login del compañero se conservan: la pantalla productiva del mapa todavía no está acoplada y los accesos al guardado y consulta reales siguen ocultos. Oficial identifica esta entrega del proyecto, no acredita aceptación productiva completa. Avance: **10/28 = 35.7%**, sin cambiar los hitos o sus pesos.

La evidencia funcional previa de fase 11 conserva **325 casos distintos aprobados y 57 definiciones nuevas**; el GPS físico se comprobó por separado. Para 0.5.0 se compilaron de nuevo debug/release y lint, sin errores; no se presenta la evidencia anterior como pruebas nuevas. Se conservan las versiones y APK anteriores.

## Registro histórico anterior a 0.5.0

Los estados Unreleased y las autorizaciones siguientes describen sus cortes originales. El código de fases 8 a 11 se incluye en la entrega 0.5.0; los pendientes visuales se conservan.

**Trabajo activo: fase 11 — incidentes por zona y marcadores — Unreleased, local; cierre técnico del 8 de octubre de 2026**, iniciado el día 7. [Fase 11](FASE_11.md) une SQLite, región visible, filtros y MapLibre real, sin añadir una pantalla productiva. El laboratorio comprobó dos marcadores y nombres de calles, sustitución al mover cámara, límite/`hasMore`, filtros/vacío, recarga de estilo, cancelación y recreación. **325 casos distintos aprobados, 57 definiciones nuevas**; debug/release y firma debug correctos, lint sin errores. [Validación](VALIDACION.md) separa repeticiones y resultados anteriores.

**Avance actual: 10/28 = 35.7%.** Se acreditan dos hitos existentes con los mismos pesos: **4.5 mapa por zonas y marcadores** deja Mapas en **3/4**, y **4.3 captura del dispositivo** deja Geolocalización en **2/4**. El Motorola Edge 50 Fusion, API 36, obtuvo una lectura GPS real con precisión estimada de **6.67 m** y antigüedad de **46 ms al evaluar**: punto cercano permitido y punto a unos 11 km rechazado. El primer intento agotó 15 segundos; cerca de una ventana respondió, sin cambiar radio, precisión, antigüedad, proveedor o límite de espera. Es una prueba física manual, separada del recuento automatizado y del mapa fijo del laboratorio, sin coordenadas persistidas ni reportes creados.

La pantalla de mapa del compañero todavía no está en esta copia: él conserva diseño, controles, login y navegación. Nuestra parte entrega conexión, estados y recursos de marcadores para acoplarla. Falta el flujo productivo integrado y su aceptación, estados/permisos GPS completos y funciones restantes del PDF. Guardado real y consulta siguen ocultos; la oficial conserva **0.4.0a/código 19**, sin otra publicación.

## Antecedente: cierre de fase 10
**Antecedente: fase 10 — MapLibre Native + OpenFreeMap — Unreleased, local; cierre técnico del 7 de octubre de 2026**, posterior a **0.4.0a / código Android 19**, sin otro número de versión. El usuario eligió este proveedor; el SDK activo es MapLibre Native Android 13.6.1, artefacto OpenGL ES, con estilo Liberty y bindings `MapLibreMap`. [Fase 10](FASE_10.md) describe la migración y [Mapa con MapLibre](MAPA_MAPLIBRE.md) es la guía vigente. **268 casos distintos aprobados y mapa real con nombres legibles comprobado**; `MapsReadiness.Ready` sigue comprobando solo inicialización local. La carga/fotograma y el PNG real se verifican por separado.

**El avance pasa de 7/28 = 25% a 8/28 = 28.6%.** Se conservan los 28 hitos, sus pesos y el criterio **SDK configurado**: la carga/renderizado nativos y el PNG con calles y nombres legibles acreditan únicamente ese hito; **4.5 Mapas queda en 2/4 = 50%**. El cambio de proveedor elimina la necesidad de una clave Google y no redefine el criterio. La consulta geográfica de fase 9 sigue sin acreditar el mapa por zonas **y marcadores**, y el laboratorio tampoco completa integración visual productiva o GPS físico. La oficial 0.4.0a permanece; esta petición no autoriza otra entrega en GitHub.

La validación de fase 10 aprobó **222 JVM app, 34 Android flow, ocho JVM monitor y cuatro Android mapas: 268 casos distintos**, con **25 definiciones añadidas**; siete casos de readiness reemplazan nueve de Google. Debug/release, firma debug y lint sin errores comprobados; las advertencias y el recibo local están en [Validación](VALIDACION.md). La consulta de objetos no bastó para acreditar texto visible con renderizado software. Se repitieron los cuatro casos nativos con GPU host y el mismo SDK, estilo y código; el PNG muestra Calle Tacuba, Calle Moneda y Calle Venustiano Carranza. La repetición no aumenta 268, no incorpora un parche sin publicar ni altera el AVD.

La **fase 9 quedó completada técnicamente**: consulta local por zona con filtros y proyección mínima, **457 casos distintos ejecutados y aprobados, 43 definiciones nuevas**, sin fallos, errores, omisiones ni reutilizados; debug/release, firma y lint aprobados. La repetición final no se suma. [Fase 9](FASE_9.md) conserva ese corte; aceptación visual pendiente.

La **fase 8 quedó completada técnicamente** y su registro interrumpido se cerró: **226 pruebas ejecutadas aprobadas, 27 definiciones nuevas** (192 JVM app y 34 Android del flujo), compilación y lint sin errores con advertencias anteriores. [Fase 8](FASE_8.md) conserva la coordinación de borrador, ubicación y vista; sus resultados son un antecedente, con aceptación visual pendiente.

El siguiente paso es conectar el mapa del compañero con la consulta y sus marcadores, además de GPS físico y flujo visible, cuando se acuerde ese alcance. La [guía activa MapLibre](MAPA_MAPLIBRE.md) sustituye la necesidad de configurar la clave Google para este proveedor. `hasMore` indica una salida limitada; no permite declarar todos los incidentes cargados. La fábrica y el modelo preparados no activan consultas en la interfaz.

**Validación 0.4.0a:** 266 pruebas distintas ejecutadas y aprobadas, 57 definiciones nuevas; 124 JVM de dominio/datos reutilizadas. Debug/release compilan, firma debug v2 válida, lint sin errores: app19 advertencias visuales, laboratorio2 por variante y datos0. [Validación](VALIDACION.md) documenta el pase y sus límites. La reapertura del mapa usa el mismo modelo/puente, exige renovación explícita e ignora capturas anteriores; el borrador no restaura autorización GPS ni guarda por sí solo.

## Corte del 6 de octubre de 2026

Versión actual: **0.4.0a / código Android 19**, del **6 de octubre de 2026**, oficial por instrucción humana; integración local y validación completadas; el estado de publicación se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a).

Se completan dos partes técnicas: **fase 6A**, puente de ubicación por ciclo de Activity y clicks Maps con gestos visibles; **fase 7**, borrador editable que conserva los campos al volver del mapa y restaura estado guardado por Android. No solicitan GPS ni guardan automáticamente. La interfaz de `main` del compañero se incorpora conservando sus cambios; el ejemplo temporal visible mantiene sus controles. Guardado real y consulta siguen ocultos.

El pendiente **0.3.11** quedó cerrado: 247 pruebas ejecutadas, 16 definiciones nuevas, firma debug correcta y lint sin errores. Son antecedentes, no resultados nuevos de 0.4.0a. [Validación](VALIDACION.md) separa los cortes.

**Avance del PDF: 7/28 = 25%.** Las dos partes técnicas no acreditan el formulario completo, GPS físico, mapa con clave ni autenticación. Fase 6 continúa abierta; [fase 7](FASE_7.md) documenta el borrador y sus límites.

## Antecedente local del 5 de octubre de 2026

Incremento anterior: **0.3.10 — Unreleased**, código Android **17**, fase técnica **6**. Se comprobó la composición permiso/captura/confirmación con las fuentes originales en `verification/location`, separado del producto. Permiso y ciclo Android reales, lectura/reloj controlados. Interfaz y login seguían a cargo del compañero; clave Google pendiente.

**Validación 0.3.10:** **21 casos Android distintos aprobados**, cero fallos, errores u omitidos: tres diálogos extendidos, 16 de composición del flujo y dos del proveedor/adaptador Android. Son **18 definiciones nuevas**; las **121 JVM de app quedaron `UP-TO-DATE`** y conservan la evidencia anterior. Debug/release compilan, firma debug v2 verificada y lint sin errores, con 13 advertencias previas de app y dos por variante técnica. [Validación](VALIDACION.md) separa las ejecuciones y los límites. La matriz usa lecturas y reloj controlados con permiso/ciclo Android reales; el registro GPS nativo se canceló antes de un callback, sin obtener una posición física.

**0.3.10 conserva 7/28 = 25%.** Se comprueba que permiso y pin no capturan, aproximado/rechazo no llegan a la fuente y la confirmación exige evidencia actual dentro de 5 km. `onStop`, timeout y respuestas tardías no dejan confirmar con una captura fallida. Estos casos no equivalen a una lectura del teléfono real o controles conectados. [Flujo Android](FLUJO_UBICACION_ANDROID.md) distingue composición controlada y registro GPS nativo; [Validación](VALIDACION.md) conserva su evidencia. No se cambia el criterio de los hitos ni se suman suites repetidas.

### Antecedente 0.3.9

0.3.9 / código 16 aprobó tres diálogos nativos con recreación y 121 JVM ejecutadas de nuevo, 124 pruebas. No capturó GPS ni comprobó confirmación de puntos; conservó 7/28 = 25%. Ese recuento pertenece a su corte, no a la nueva ejecución 0.3.10.

### Antecedente 0.3.8

Incremento anterior: **0.3.8 — Unreleased**, código Android **15**, fase técnica **6**. El usuario confirmó que todavía no dispone de una clave de Google y eligió avanzar con ubicación y permisos. Se añaden el modelo de permisos y un puente AndroidX por gesto explícito, conservando selección, cercanía, fuente GPS y composición del mapa de los incrementos anteriores. No se modifican interfaz/login ni dominio/persistencia en este paso y no se instala el incremento sobre la aplicación actual del usuario.

**0.3.8 conserva 7/28 = 25%.** El contrato distingue permiso preciso, aproximado y ausente, solicita `COARSE` y `FINE` juntos y relee hechos al recibir el resultado. Conservar el modelo al rotar evita solicitudes duplicadas; el puente dura toda la vida de su `ActivityResultCaller`. Ningún resultado inicia GPS, confirma un punto o guarda un reporte. La integración del diálogo, captura real y mapa visible siguen pendientes. [Permisos de ubicación](PERMISOS_UBICACION.md) explica la conexión y [Validación](VALIDACION.md) registra las pruebas ejecutadas y sus límites. La fase 6 continúa abierta; la versión no acredita aceptación de fases anteriores.

El plan funcional procede de los siete requisitos 4.1 a 4.7 del [PDF resumido en el plan](PLAN_DEL_PROYECTO.md). Las fases técnicas y las versiones sirven para organizar el trabajo; no son el denominador del avance del producto.

### Antecedente 0.3.7

La preparación **0.3.7 / código 14** acumuló la revisión de GPS de 0.3.5, el modelo cancelable de selección/captura de 0.3.6 y el refuerzo de persistencia/conexión al SDK de 0.3.7. Conservó selección de 0.3.2, cercanía de 0.3.3 y SDK de 0.3.4. Esas tres agrupaciones locales no representaron tres APK intermedios entregados.

**0.3.5–0.3.7 conservan 7/28 = 25%.** Se probaron fallos y cancelación de GPS, respuestas tardías, rotación del modelo, preservación frente a corrupción de SQLite y comprobaciones locales del mapa. No completan captura GPS real, permisos interactivos ni renderizado con clave válida. [Incrementos](INCREMENTOS_035_037.md) detalla cambios y conexión; [Validación](VALIDACION.md) registra la comprobación integral y su recuento. No se modificó la interfaz ni el login del compañero, no se activó guardado real/consulta y no se publicó el trabajo.

### Antecedente 0.3.4

La preparación 0.3.4 incorpora ahora el **Maps SDK for Android 20.0.0** real, metadato de clave local y puente `GoogleMapIncidentBinding` hacia `IncidentMapSession`. Se declaran internet y ubicación aproximada/precisa; el permiso interactivo y el mapa visible siguen pendientes. La clave Google Cloud todavía no está configurada. Véase [Google Maps](GOOGLE_MAPS.md). Compilación debug, firma v2, lint (13 advertencias previas) y **41 JVM de app, incluidas 11 nuevas de selección**, aprobadas. No se probó carga de mapas real ni se publicó nada. **Avance comprobado: 7/28 = 25%**.

El SDK ya está incorporado y el adaptador compila, pero el hito de SDK configurado no se suma sin clave habilitada y mapa real ejecutado. Los apartados previos de captura sin permisos describen el corte anterior a esta incorporación.

## Cómo se calcula

Se definen cuatro hitos de construcción por cada requisito del PDF: **28 hitos con el mismo peso**. Cada hito comprobado aporta aproximadamente 3.57 puntos al total; cada fila puede tener 0%, 25%, 50%, 75% o 100%. Esta es una estimación de construcción, no del tiempo o dificultad de las tareas, de las funciones visibles ni de la aceptación del usuario.

Una pantalla visual cuenta solo como preparación visual; una lógica probada cuenta solo como su hito técnico. No se declara el requisito completo mientras falten integración, pruebas y revisión. Las suites compartidas no se suman varias veces para aumentar el porcentaje.

| Requisito | Hitos de construcción | Comprobados | Avance estimado |
| --- | --- | --- | --- |
| 4.1 Usuarios | Pantalla de acceso; creación de cuentas; verificación real de acceso; identidad y aislamiento de datos | Pantalla de acceso del compañero | 1/4 · 25% |
| 4.2 Reportes | Modelo y validación; persistencia local técnica; formulario con guardado real autorizado; fotografías | Modelo/validación y persistencia técnica | 2/4 · 50% |
| 4.3 Geolocalización | Par de coordenadas validado y conservado; captura del dispositivo; permisos y estados GPS; flujo GPS probado | Coordenadas y captura física previa comprobadas; 0.5.1 conecta los controles y comprueba denegado/aproximado/preciso/ubicación desactivada contra Android real, junto con sus estados técnicos anteriores. Falta aceptación física del flujo visual completo | 3/4 · 75% |
| 4.4 Consulta | Lectura por folio; lista y permanencia; reportes propios por usuario; seguimiento de estados | Lectura por folio y lista/permanencia técnicas | 2/4 · 50% |
| 4.5 Mapas | Contrato de selección y confirmación; SDK configurado; mapa por zonas y marcadores; flujo visual integrado y probado | Contrato, SDK y mapa por zonas/marcadores comprobados. 0.5.1 muestra mapa, calles y pin del borrador; consulta real y mapa de reportes en el flujo final siguen pendientes | 3/4 · 75% |
| 4.6 Administración | Consulta administrativa; cambios de estado; roles y permisos comprobados; historial | Ninguno | 0/4 · 0% |
| 4.7 Notificaciones | Eventos de cambios; entrega de avisos; permisos y preferencias; flujo completo probado | Ninguno | 0/4 · 0% |

**Avance actual estimado: 11/28 = 39.3%.** 0.5.1 añade el hito existente de permisos y estados GPS, conservando los 28 hitos y pesos. Publicar y repetir pruebas no se cuentan como hitos. Faltan guardado real autorizado, GPS físico de la pantalla, mapa de reportes integrado, autenticación, fotos, administración y avisos.

**0.3.3 no suma otro hito ni cambia el porcentaje.** Fortalece el contrato con el radio de 5 km elegido por el usuario, exige evidencia reciente y suficientemente precisa y prepara un guard para la escritura productiva. No obtiene GPS ni configura SDK, permisos o pantalla de mapa. La regla y sus pruebas se documentan en [Proximidad de reportes](PROXIMIDAD_REPORTES.md) y [Validación](VALIDACION.md), separadas del corte 0.3.2.

**0.3.4 tampoco suma un hito todavía.** La solicitud y renovación de evidencia, estados, cancelación y tiempo de espera configurable se comprobaron en su paso anterior al SDK como parte de **185 pruebas diferentes aprobadas**: 86 de dominio, 30 de app, 20 JVM de datos y 49 instrumentadas API 37. La fuente Android se revisó con un backend controlado y el camino nativo de permiso denegado; no obtuvo una posición por GPS real. Ese paso inicial no declaró permisos; el posterior SDK sí los declaró, sin solicitud interactiva ni mapa activado. El hito de captura requiere observar una lectura procedente del dispositivo; permisos y flujo GPS tienen sus propios hitos y pruebas. [Obtener ubicación](OBTENER_UBICACION.md) y [Validación](VALIDACION.md) distinguen el contrato comprobado de estas comprobaciones pendientes. La repetición de las mismas 49 instrumentadas no aumenta el total ni el porcentaje.

No hay un requisito completo de extremo a extremo aceptado. Mapa base, marcadores por zonas y captura GPS física están comprobados técnicamente; falta integrarlos con los controles productivos. El login visual no autentica y los reportes propios y el seguimiento necesitan cuentas y administración. Una lectura física válida no demuestra que ocurrió un incidente ni garantiza disponibilidad GPS en cualquier lugar.

## Evidencia y límites

La preparación anterior 0.3.1 comprobó el ejemplo ficticio sin cambios en bases productivas y mantuvo ocultos el guardado real y toda consulta 0.4. Las comprobaciones técnicas históricas de persistencia y consulta respaldan sus hitos de construcción, aunque esos flujos no estén activados para el usuario. [Validación](VALIDACION.md) conserva comandos, resultados y límites por incremento.

El nuevo commit remoto del compañero `b665a56` añade y ajusta layouts, incluida creación de cuenta visual. No implementa cuentas ni modifica contratos, así que no aumenta los hitos de autenticación de este corte. La integración 0.4.0a ya incorpora esos layouts sin perder el ejemplo ficticio, como se registra en [Fases](FASES.md).

Los cortes históricos Unreleased fueron locales. Para 0.4.0a el usuario autorizó expresamente integrar, subir y publicar; la comprobación final y publicación se registran en [Validación](VALIDACION.md) y [Releases](RELEASES.md). La aceptación funcional de los hitos sigue pendiente. El criterio de los hitos se conserva en cada corte para que futuros porcentajes sean comparables; un cambio de alcance o de pesos se documentará antes de comparar resultados.

Las [mejoras futuras](MEJORAS_FUTURAS.md) recogen ideas priorizadas para una revisión posterior. No están implementadas, no fijan una versión oficial ni añaden hitos o porcentaje a este corte.
