# Avance del plan del proyecto

**Validación 0.4.0a:** 266 pruebas distintas ejecutadas y aprobadas, 57 definiciones nuevas; 124 JVM de dominio/datos reutilizadas. Debug/release compilan, firma debug v2 válida, lint sin errores: app19 advertencias visuales, laboratorio2 por variante y datos0. [Validación](VALIDACION.md) documenta el pase y sus límites. La reapertura del mapa usa el mismo modelo/puente, exige renovación explícita e ignora capturas anteriores; el borrador no restaura autorización GPS ni guarda por sí solo.

## Corte del 6 de octubre de 2026

Versión actual: **0.4.0a / código Android 19**, del **6 de octubre de 2026**, oficial por instrucción humana; integración local y validación completadas; el estado de publicación se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a).

Se completan dos partes técnicas: **fase 6A**, puente de ubicación por ciclo de Activity y clicks Maps con gestos visibles; **fase 7**, borrador editable que conserva los campos al volver del mapa y restaura estado guardado por Android. No solicitan GPS ni guardan automáticamente. La interfaz de `main` del compañero se incorpora conservando sus cambios; el ejemplo temporal visible mantiene sus controles. Guardado real y consulta siguen ocultos.

El pendiente **0.3.11** quedó cerrado: 247 pruebas ejecutadas, 16 definiciones nuevas, firma debug correcta y lint sin errores. Son antecedentes, no resultados nuevos de 0.4.0a. [Validación](VALIDACION.md) separa los cortes.

**Avance del PDF: 7/28 = 25%.** Las dos partes técnicas no acreditan el formulario completo, GPS físico, mapa con clave ni autenticación. Fase 6 continúa abierta; [fase 7](FASE_7.md) documenta el borrador y sus límites.

## Antecedente local del 5 de octubre de 2026

Incremento activo: **0.3.10 — Unreleased**, código Android **17**, fase técnica **6**. Se comprueba la composición permiso/captura/confirmación con las fuentes originales en `verification/location`, separado del producto. Permiso y ciclo Android reales, lectura/reloj controlados. Interfaz y login siguen a cargo del compañero; clave Google pendiente.

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
| 4.3 Geolocalización | Par de coordenadas validado y conservado; captura del dispositivo; permisos y estados GPS; flujo GPS probado | Par de coordenadas; fuente/renovación y modelo de captura comprobados con fuentes controladas. Puente de permisos y tres decisiones nativas con recreación aprobadas; 0.3.10 añade composición Android, cancelación y confirmación con lecturas controladas y registro GPS nativo cancelado sin fix. Pantalla y lectura física pendientes. [Validación](VALIDACION.md) distingue cada comprobación | 1/4 · 25% |
| 4.4 Consulta | Lectura por folio; lista y permanencia; reportes propios por usuario; seguimiento de estados | Lectura por folio y lista/permanencia técnicas | 2/4 · 50% |
| 4.5 Mapas | Contrato de selección y confirmación; SDK configurado; mapa por zonas y marcadores; flujo visual integrado y probado | Contrato de selección/confirmación, cercanía de 5 km y ciclo del modelo probados; SDK incorporado y binding preparado. Clave habilitada, mapa renderizado y flujo visual pendientes | 1/4 · 25% |
| 4.6 Administración | Consulta administrativa; cambios de estado; roles y permisos comprobados; historial | Ninguno | 0/4 · 0% |
| 4.7 Notificaciones | Eventos de cambios; entrega de avisos; permisos y preferencias; flujo completo probado | Ninguno | 0/4 · 0% |

**Avance actual estimado: 7/28 = 25% del plan completo.** La base anterior tenía 6/28 = 21.4%; el primer paso de 0.3.2 suma únicamente el contrato de selección y confirmación comprobado. Sus 14 pruebas nuevas y la regresión de mensajes aprobaron dentro de las 47 de dominio; también aprobaron 30 de app y 15 instrumentadas SQLite. [Validación](VALIDACION.md) registra esta ejecución por separado. La fase 6 sigue abierta y no se considera implementado el mapa real.

**0.3.3 no suma otro hito ni cambia el porcentaje.** Fortalece el contrato con el radio de 5 km elegido por el usuario, exige evidencia reciente y suficientemente precisa y prepara un guard para la escritura productiva. No obtiene GPS ni configura SDK, permisos o pantalla de mapa. La regla y sus pruebas se documentan en [Proximidad de reportes](PROXIMIDAD_REPORTES.md) y [Validación](VALIDACION.md), separadas del corte 0.3.2.

**0.3.4 tampoco suma un hito todavía.** La solicitud y renovación de evidencia, estados, cancelación y tiempo de espera configurable se comprobaron en su paso anterior al SDK como parte de **185 pruebas diferentes aprobadas**: 86 de dominio, 30 de app, 20 JVM de datos y 49 instrumentadas API 37. La fuente Android se revisó con un backend controlado y el camino nativo de permiso denegado; no obtuvo una posición por GPS real. Ese paso inicial no declaró permisos; el posterior SDK sí los declaró, sin solicitud interactiva ni mapa activado. El hito de captura requiere observar una lectura procedente del dispositivo; permisos y flujo GPS tienen sus propios hitos y pruebas. [Obtener ubicación](OBTENER_UBICACION.md) y [Validación](VALIDACION.md) distinguen el contrato comprobado de estas comprobaciones pendientes. La repetición de las mismas 49 instrumentadas no aumenta el total ni el porcentaje.

No hay un requisito completo de extremo a extremo aceptado. En particular, el mapa real sigue pendiente, el login visual no autentica, y los reportes propios y el seguimiento necesitan cuentas y administración. Tener coordenadas no equivale a tener GPS ni un mapa de incidentes.

## Evidencia y límites

La preparación anterior 0.3.1 comprobó el ejemplo ficticio sin cambios en bases productivas y mantuvo ocultos el guardado real y toda consulta 0.4. Las comprobaciones técnicas históricas de persistencia y consulta respaldan sus hitos de construcción, aunque esos flujos no estén activados para el usuario. [Validación](VALIDACION.md) conserva comandos, resultados y límites por incremento.

El nuevo commit remoto del compañero `b665a56` añade y ajusta layouts, incluida creación de cuenta visual. No implementa cuentas ni modifica contratos, así que no aumenta los hitos de autenticación de este corte. La integración 0.4.0a ya incorpora esos layouts sin perder el ejemplo ficticio, como se registra en [Fases](FASES.md).

Los cortes históricos Unreleased fueron locales. Para 0.4.0a el usuario autorizó expresamente integrar, subir y publicar; la comprobación final y publicación se registran en [Validación](VALIDACION.md) y [Releases](RELEASES.md). La aceptación funcional de los hitos sigue pendiente. El criterio de los hitos se conserva en cada corte para que futuros porcentajes sean comparables; un cambio de alcance o de pesos se documentará antes de comparar resultados.

Las [mejoras futuras](MEJORAS_FUTURAS.md) recogen ideas priorizadas para una revisión posterior. No están implementadas, no fijan una versión oficial ni añaden hitos o porcentaje a este corte.
