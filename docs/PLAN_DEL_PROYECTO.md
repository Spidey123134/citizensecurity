# Plan del proyecto

## Desarrollo para 0.6.0 — fases 21 a 26, Unreleased

El equipo agrupa cambios de código en main sin publicar APK, tags o Releases por fase. [Fase 21](FASE_21.md) añade fechas del incidente al mapa y lista de la misma zona. [Fase 22](FASE_22.md) combina prioridad con los filtros de lista/mapa, mueve el cálculo de búsqueda fuera de Main y comprueba la cámara real antes de abrir una muestra de zona. [Fase 23](FASE_23.md) sustituye la lectura completa de la lista por páginas de SQLite con memoria acotada, cursor exclusivo, continuación de búsquedas incompletas y navegación. La validación Android de estos controles se reserva para la entrega agrupada; la última APK publicada sigue siendo 0.5.7.

Las fases 21 a 24 refuerzan Consulta/Mapas. [Fase 25](FASE_25.md) conecta cuentas y [fase 26](FASE_26.md) separa reportes/consultas, fotos y recuperación por UUID. **19/28 = 67.9% de construcción**, Usuarios 4/4 y Consulta 3/4, mismos criterios/pesos; aceptación Android pendiente. Identidad/aislamiento locales, sin servidor ni roles administrativos. Faltan seguimiento de estados, administración con permisos/historial y notificaciones. Los controles nuevos se aceptarán en 0.6.0; los cortes siguientes son históricos.

La [fase 24](FASE_24.md) refuerza recuperación de consulta. El compañero hace **solo la interfaz del login**; nuestras fases 25/26 conectan cuentas y espacios conservando sus layouts. Los históricos continúan en académico, sin inventar autoría. Sigue seguimiento/administración con permisos comprobados; el acceso académico no recibe roles reales.

## Antecedente 0.5.7

## Fase 20 — 0.5.7, búsqueda útil en la consulta

Se continúa **4.4 Consulta** con una mejora funcional de la lista: encontrar reportes por descripción o referencia y acotar la fecha del incidente. Se normalizan mayúsculas/acentos, las fechas pueden escribirse o elegirse en calendario y se combinan con tipo/estado. Texto y fechas se aplican por gesto; una entrada inválida conserva los resultados del filtro anterior. Volver del detalle cancela su lectura pendiente para permitir continuar consultando.

La pantalla conserva campos y filtros aplicados al girar o regresar. El período usa días del calendario en la zona horaria de la pantalla, incluyendo el último día completo; no filtra por la fecha en que se guardó el reporte. La implementación es de solo lectura y no modifica datos ni inicia GPS. Esta fase corresponde a la lista; no traslada estos filtros al mapa.

**15/28 = 53.6%**, mismos hitos y pesos. Refuerza consulta sin redefinir el requisito ni sumar puntos por filtros o publicación. Quedan cuentas reales/aislamiento por autor, seguimiento administrativo y notificaciones, con sus roles coordinados con el equipo. Conserva login/loading del compañero y las funciones reales anteriores. [Fase 20](FASE_20.md) · [Probar 0.5.7](PROBAR_0_5_7.md) · [Avance](AVANCE.md).

## Antecedente 0.5.6

## Fase 19 — 0.5.6, inicio funcional

La pantalla verde de Desarrollador ofrece tres rutas hacia funciones reales: **Nuevo reporte**, **Reportes en este teléfono** y **Mapa de reportes por zona**. El inicio lee los diarios privados para informar si hay una copia de borrador o un intento por comprobar; un intento pendiente puede haberse guardado y debe comprobarse en la pantalla del reporte. Los errores de lectura se muestran independientemente. Entrar o regresar al inicio no solicita GPS ni crea o modifica datos.

Se retiran los controles visuales del ejemplo ficticio y las pruebas manuales de coordenadas, conservando su código e historia. Login, loading, acceso académico, datos previos y las funciones reales permanecen. Esta fase mejora el recorrido de **4.2 Reportes**, **4.4 Consulta** y **4.5 Mapas** sin agregar criterios.

**15/28 = 53.6%**, mismos hitos y pesos. **4.3 Geolocalización pasa a 4/4** porque el usuario confirmó manualmente el **11 de octubre de 2026** que el teléfono localizó el punto, lo aceptó dentro del radio y lo mostró en el mapa. La versión de la APK no fue especificada; no hay logs, nuevas medidas de precisión, coordenadas registradas ni un guardado físico nuevo atribuibles a esa confirmación. Guardar y abrir un folio físico sigue siendo una comprobación transversal opcional de 4.2/4.4, sin ampliar el criterio original de ubicación.

Las partes pendientes del plan son cuentas reales/aislamiento de autores, seguimiento administrativo y notificaciones. Sus alcances y roles deben coordinarse con el equipo. [Fase 19](FASE_19.md) · [Avance y criterios](AVANCE.md) · [Probar la APK](PROBAR_0_5_6.md). Los cortes siguientes conservan sus cifras y restricciones históricas.

## Antecedentes históricos del corte anterior

## Fase 18 — 0.5.5

Revisar posibles reportes repetidos refuerza **4.2 Reportes** y **4.4 Consulta**: una lectura local permite revisar folios cercanos del mismo tipo antes de confirmar otro incidente. Usa el acuerdo de 200 m y ±2 h; es una advertencia, sin clasificar veracidad ni atribuir autores.

**14/28 = 50%** con los mismos hitos y pesos. Esta ampliación no completa cuentas/aislamiento, seguimiento administrativo, notificaciones ni aceptación física final de GPS. [Fase 18](FASE_18.md) y [Avance](AVANCE.md) registran alcance y pendientes.

## Antecedentes del corte anterior

## Fases 16 y 17 — 0.5.4

**4.2 Reportes** incorpora fotografía opcional en el formulario, revisión y folio, con copia privada y migración de datos. La recuperación explícita de una copia completa del borrador mejora el mismo recorrido y su relación con **4.3 Geolocalización**: recuperar coordenadas no sustituye un GPS reciente y válido para guardar.

Se conserva el plan original: **14/28 = 50%** de construcción, sin añadir hitos por fases, versiones o número de pruebas. [Fase 16](FASE_16.md), [Fase 17](FASE_17.md) y [Avance](AVANCE.md) registran criterios. La aceptación física del recorrido final, cuentas reales/aislamiento, administración y notificaciones siguen pendientes.

## Antecedentes del corte anterior
## Fase 15 — 0.5.3

Se conectan **4.2 Reportes** (formulario con guardado local autorizado y folio durable), **4.4 Consulta** (lista, detalle, búsqueda y filtros visibles) y **4.5 Mapas** (zona, marcadores SQLite y navegación al detalle). **4.3 Geolocalización** mantiene permiso preciso y radio de 5 km al confirmar y guardar; la aceptación física del recorrido final sigue pendiente y tiene su hito separado.

La orden actual retira la ocultación de las funciones construidas y mantiene entregas oficiales. Fotografías, cuentas/aislamiento por usuario, seguimiento administrativo y notificaciones aún requieren implementación. La consulta local no acredita reportes propios por usuario ni cambia estados. [Fase 15](FASE_15.md) explica evidencia y avance; se conservan los 28 hitos y pesos de AVANCE.md.

## Antecedentes históricos

## Entrega 0.5.2 — fase 14

Se trabaja **4.2 Reportes**: revisión completa del formulario y corrección de duplicados al reintentar después de una respuesta perdida de SQLite. Mantiene los controles de **4.3 Geolocalización** y el radio de 5 km. La revisión visible no es autorización de cercanía ni guardado; guardado y consulta reales siguen ocultos hasta la instrucción específica pendiente. [Fase 14](FASE_14.md) conserva los criterios y registra 529 casos aprobados. **11/28 = 39.3%**; no se acreditan fotos, identidad de usuario, administración o avisos por esta entrega.

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

Este plan resume los requisitos del **Documento de Visión Inicial: Desarrollo de una Aplicación Móvil para el Reporte de Emergencias y Robos con Geolocalización**, de septiembre de 2026, y los contrasta con CITIZENSECURITY. Se revisaron sus tres páginas. El PDF original y los documentos del respaldo permanecen fuera del repositorio; aquí no se incluyen datos personales.

El paso local activo es **[fase 11 — Unreleased](FASE_11.md)**, cerrado técnicamente el **8 de octubre de 2026** después de conectar lectura por zona y marcadores al SDK y probar GPS real en el teléfono del usuario. **325 casos distintos aprobados y 57 definiciones nuevas**, compilación debug/release, firma debug y lint sin errores. Se mantienen criterios y pesos del PDF: mapa por zonas/marcadores (**4.5, 3/4**) y captura del dispositivo (**4.3, 2/4**) completan dos hitos existentes. [Avance](AVANCE.md) registra **10/28 = 35.7%**. La pantalla/login/diseño del compañero, integración productiva, activación de datos reales y aceptación siguen pendientes; oficial 0.4.0a/código19, sin acciones Git ni otra Release.

## Antecedentes de construcción hasta fase 10

El trabajo activo es la **fase 10 — MapLibre Native + OpenFreeMap — Unreleased, local, cerrada técnicamente el 7 de octubre de 2026**, sin un nuevo número de versión. El usuario eligió cambiar el proveedor del mapa; se integra SDK MapLibre Native Android 13.6.1 con backend OpenGL ES, estilo Liberty y bindings `MapLibreMap`. [Fase 10](FASE_10.md) define el alcance y [Mapa con MapLibre](MAPA_MAPLIBRE.md) es la guía activa. **268 casos distintos aprobados y mapa real con nombres legibles comprobado**: 222 JVM app, 34 Android flow, ocho JVM monitor y cuatro Android mapas; 25 definiciones añadidas y siete de readiness que reemplazan nueve de Google. Debug/release, firma debug y lint sin errores comprobados. Inicializar no acredita red ni un fotograma: el SDK nativo y el PNG con GPU host se verificaron por separado. El compañero conserva interfaz/login/mapa/pines; guardado real y consultas permanecen ocultos. El trabajo local no autoriza acciones Git ni publicación.

La **fase 9 quedó completada técnicamente**: consulta por zona con filtros y datos mínimos para marcadores, **457 casos distintos ejecutados y aprobados, 43 definiciones nuevas**, sin fallos, errores, omisiones ni reutilizados; debug/release, firma y lint aprobados. La repetición final no aumenta el recuento. [Fase 9](FASE_9.md) conserva ese corte como antecedente, con aceptación visual pendiente.

La **fase 8 quedó completada técnicamente**: coordinación de borrador, ubicación y vista, con **226 ejecuciones aprobadas y 27 definiciones nuevas**, compilación y lint sin errores con advertencias anteriores. Su registro interrumpido quedó cerrado. [Fase 8](FASE_8.md) conserva el corte como antecedente; aceptación visual pendiente y pruebas separadas de fase 9.

La versión oficial vigente es **0.4.0a / código Android 19**, del **6 de octubre de 2026**, con validación completada para la **Release solicitada expresamente por el usuario**. Se cerró el endurecimiento de evidencia GPS de 0.3.11 y se completaron dos pasos técnicos: [fase 6](FASE_6.md), conexión entre permiso, captura explícita y ciclo de vida; y [fase 7](FASE_7.md), borrador editable con ida/vuelta del mapa y recuperación de campos. La revisión de GitHub, incorporación de la interfaz del compañero y publicación de esa actualización estuvieron autorizadas; sus comprobaciones están registradas en [Validación](VALIDACION.md), y su estado en GitHub se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a). El desarrollo posterior conserva el requisito de instrucciones precisas para las acciones externas.

La regla de **5 km alrededor del teléfono** se mantiene. [Proximidad](PROXIMIDAD_REPORTES.md) conserva la escritura protegida, y [Avance](AVANCE.md) pasa a **8/28 hitos, 28.6% estimado**. Se conservan criterios y pesos: carga/renderizado nativos y nombres legibles completan solo **SDK configurado**, dejando **4.5 en 2/4**; no acreditan mapa por zonas con marcadores ni integración visual productiva. MapLibre/OpenFreeMap elimina el requisito de clave Google para el mapa activo. La limitación de nombres invisibles en renderizado software queda documentada; el pase usó `-gpu host`, sin editar el AVD ni incluir un parche sin publicar. GPS físico, controles conectados y aceptación siguen pendientes. El borrador conserva datos de entrada, sin permiso ni evidencia GPS como autorización; la consulta sigue explícita y de solo lectura. Las [mejoras posteriores](MEJORAS_FUTURAS.md) siguen como propuestas independientes.

La revisión remota anterior detectó [`b665a56`, «cambiar interfaz»](https://github.com/Spidey123134/citizensecurity/commit/b665a56a9761ec5c083cabc638eba1674c7bf78b), del compañero: rediseño de herramientas, ajustes de login, layout de crear cuenta y configuración del Daemon JVM. Ese commit no cambia lógica de reportes ni contratos. Su acoplamiento se incorporó mediante el merge local `433b3fb`: inicio y crear cuenta coinciden con el remoto, y herramientas conserva su rediseño e IDs más el ejemplo ficticio. El daemon quedó coordinado a JDK 27 mediante Gradle, sin las URLs anteriores de JVM 25. El layout de crear cuenta por sí solo no implementa cuentas ni suma otro hito de autenticación.

La página 1 contiene la portada, el resumen ejecutivo y los puntos clave. La página 2 presenta las secciones 1, 2, 3 y el inicio de 4. La página 3 completa las funcionalidades 4.2 a 4.7 y la sección 5. La visión describe el producto académico deseado; no fija versiones, fases técnicas ni proveedores.

## 1. El Problema

El documento identifica información dispersa, dificultad para ubicar un incidente, falta de seguimiento y evidencia separada del reporte. Recibir, validar, guardar y consultar un reporte local permite avanzar sobre la organización y la conservación de los datos. No resuelve por sí solo la comunicación con autoridades ni la atención del incidente.

## 2. La Solución Deseada

La propuesta permite registrar y organizar incidentes, conservar su información y consultar los reportes realizados. Incluye emergencia, robo, accidente, incendio, situación de riesgo y otros incidentes.

La fase 4 se apoya en esta sección y en la funcionalidad 4.4: consulta local por folio y estado, reutilizando el almacenamiento existente. Cerrar y reabrir SQLite para comprobar la permanencia es un criterio técnico de nuestra entrega, derivado de la necesidad de almacenar y consultar; el PDF no prescribe ese procedimiento.

El alcance sigue siendo académico y de simulación. Guardar o consultar localmente no envía unidades policiales, médicas o de protección civil.

## 3. Usuarios y Público Objetivo

La visión contempla ciudadanos, usuarios registrados, personal administrativo y estudiantes o desarrolladores. La base actual no tiene autenticación, propietario del reporte ni particiones por usuario. Por tanto, la consulta local todavía no acredita que cada usuario vea únicamente sus reportes.

El compañero desarrolla interfaces, navegación y autenticación. A petición del usuario, la base oficial 0.2.1 añadió un botón de herramientas y el acceso local admin / admin para demostrar recepción y validación; no implementa cuentas ni roles. La lógica y los datos usan contratos compartidos para que pueda conectar después sus pantallas. La autenticación, los permisos y los servicios externos necesitan su propio alcance y comprobaciones antes de afirmar que los distintos perfiles están implementados.

## 4. Funcionalidades Principales

| Requisito del PDF | Situación actual y trabajo acotado | Pendiente |
| --- | --- | --- |
| 4.1. Registro de usuarios, página 2 | El compañero aportó splash y login visual. Se conserva su interfaz y el acceso local admin / admin solicitado para demostración; no es autenticación real. | Cuenta, ingreso real, identificación del autor y aislamiento de datos. El login corresponde al compañero. |
| 4.2. Reporte de emergencia o robo, página 3 | El modelo recibe tipo, fecha y hora, descripción, ubicación y prioridad. 0.4.0a incluye el borrador editable con recuperación de campos; fase 8 local coordina sus acciones con ubicación, sin insertar datos. La lógica de guardado se conserva y su acceso real queda oculto. El ejemplo visible utiliza datos ficticios fijos en SQLite en memoria. | Formulario del compañero, aceptación visual y autorización para activar guardado real. Fotografías y almacenamiento necesitan otro alcance. Fase 3 / 0.3.0 sigue sin entrega separada. |
| 4.3. Geolocalización, página 3 | Evidencia del teléfono separada del pin, precisa, vigente y no marcada como simulada, con permiso FINE y GPS habilitado al usarla. SDK, fuente de captura y puente de permisos implementados. El laboratorio comprueba integración Android mediante lecturas controladas y registro nativo cancelado antes del callback. 0.4.0a prepara el puente de ciclo de vida para conectar controles. | Lectura GPS física, controles conectados y comprobación del flujo en un teléfono. Una lectura controlada de laboratorio no acredita posición física. |
| 4.4. Consulta de reportes, página 3 | La lógica de 0.4 conserva consulta por folio, estado `REPORTED` y demostración técnica mediante Gradle. Su pulido queda Unreleased y toda consulta está oculta en la interfaz de la preparación 0.3.1. | Pantalla del compañero y autorización para activarla; reportes propios por usuario y seguimiento entre Reportado, En revisión, Atendido y Cerrado. El contrato no completa todo el requisito. |
| 4.5. Mapa de incidentes, página 3 | El PDF contempla un mapa por zonas con marcadores. Fase 10 adapta MapLibre Native/OpenFreeMap y acredita SDK configurado: carga/renderizado nativos y PNG con calles y nombres legibles, sin clave Google. Fase 8 coordina selección/borrador y fase 9 entrega consulta geográfica validada, con filtros, límite y proyección sin descripción ni referencia. El radio de 5 km se conserva. | Conectar vista/controles del compañero y renderizar los marcadores por zona en el flujo productivo. `hasMore` indica truncamiento. Mapa base del laboratorio no completa el mapa de incidentes integrado. El PDF no fija proveedor ni prescribe el radio de 5 km. |
| 4.6. Panel administrativo, página 3 | No hay administración ni cambio de estado en este incremento. | Consulta administrativa, clasificación, cambios de estado, permisos comprobados e historial. El alcance se acuerda antes de implementarlo. |
| 4.7. Notificaciones, página 3 | No se implementan avisos en este incremento. | Notificaciones por cambios de estado, configuración, permisos y pruebas del flujo. |

Los estados futuros descritos por 4.4 y los cambios administrativos de 4.6 son requisitos relacionados. Mostrar el estado inicial de un reporte no demuestra atención, cierre ni autorización administrativa.

## 5. Ventajas de la Delimitación

La visión busca una demostración académica con registro, organización y seguimiento, una arquitectura manejable y tareas repartidas. También contempla incorporar bases de datos, GPS, mapas, fotografías y notificaciones. Esa lista describe posibilidades del producto; no significa que todas estén activas o autorizadas en cada incremento.

Nuestra estructura de `app`, `core/domain` y `core/data` se conserva. La consulta por zona reutiliza el repositorio SQLite compartido y sus coordenadas, con parámetros y proyección directa; no necesita migración ni escritura. El trabajo de ubicación y borrador también conserva sus contratos, sin autenticación ni servicios en segundo plano. El ejemplo visible usa SQLite en memoria y datos ficticios fijos; no lee el formulario ni la base productiva. El compañero conserva sus pantallas y login, mientras nuestra parte entrega lógica, composición y pruebas.

## Secuencia y revisión

Para **0.4.0a**, el 6 de octubre el usuario autorizó expresamente revisar GitHub, subir la actualización y publicarla como Release oficial. Esta autorización cubre ese lote y la incorporación necesaria del trabajo del compañero; no permite publicar entregas futuras por haber terminado pruebas. [Versiones](RELEASES.md) registra el resultado de la publicación, y [Validación](VALIDACION.md) conserva la evidencia propia de cada corte. Los antecedentes siguientes no trasladan sus estados de publicación al lote actual.

El usuario pidió terminar **fase 4 / `0.4.0`** y explicarle los cambios. No autorizó integrarla en `main` ni publicarla. La [Release v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0), código Android **7**, se publicó por una interpretación equivocada; la afirmación anterior de autorización era incorrecta. Por indicación posterior se conserva lo publicado y no se retira ni revierte.

Como antecedente, la preparación **0.3.1 / código Android 8** quedó como base local para la futura línea oficial. **0.3.2 / código Android 9 — Unreleased** preparó el contrato de selección; el usuario continuó con **0.3.3 / código Android 10 — Unreleased** para impedir reportes lejanos, con un radio confirmado de 5 km. Se conservó entonces la línea 0.3.x en lugar de la futura 0.5.0, sin renumerar publicaciones históricas ni crear commit, subida, integración, etiqueta o Release de esos cortes. Sigue visible únicamente el ejemplo de guardado ficticio en memoria, además de recepción, validación y coordenadas. El guardado real, toda consulta 0.4 y la selección nueva permanecen sin acceso visual. El compañero conserva interfaz y login y puede preparar sus pantallas, pero no activar esos contratos hasta una instrucción precisa.

La nueva restricción exige evidencia del teléfono y revalida el punto antes de una escritura productiva. No bloquea un país: desde México rechaza un punto distante en Francia y desde una ubicación cercana en Francia aplica la misma regla. El filtro local no demuestra que ocurrió el incidente ni evita toda falsificación; captura GPS, permisos y servidor siguen pendientes. Este alcance procede de la petición posterior del usuario, no de un requisito de seguridad inventado para el PDF.

**0.3.0 y el pulido de 0.4.0 permanecen Unreleased**. Parte de su lógica ya está incluida en el código público y APK anteriores; no se afirma que esa fuente quede oculta o fuera de lo publicado. La [guía de fase 4](FASE_4.md) conserva los criterios técnicos; [Fases](FASES.md) distingue implementación, pruebas y revisión. Las tareas Gradle son comprobaciones de bases de prueba, no rutas visibles de la aplicación.

La entrega anterior [v0.2.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.1) se conserva como historial. Terminar, probar o pulir no autoriza crear commits, subir, integrar, etiquetar, publicar ni borrar: cada acción requiere una instrucción humana precisa. Las pruebas técnicas y la publicación previa no registran aceptación funcional.

La ruta del respaldo es una referencia histórica: su entrega 4 era administración y describía otra integración en `appmovile`, con pantallas, fotografías e historial propios. Esa numeración y arquitectura no se trasladan a las fases actuales ni justifican copiar el proyecto completo. La asignación actual de interfaces y login al compañero prevalece sobre el reparto antiguo.

Las pruebas y límites se registran en [Validación](VALIDACION.md); los cambios, en el [Changelog](../CHANGELOG.md). La base 0.3.1 tiene comprobación real en el emulador del ejemplo y las herramientas anteriores, sin cambios en las bases productivas. El primer paso 0.3.2 aprobó el contrato de selección, 92 pruebas y lint/compilación debug; conserva las 13 advertencias visuales y no se instaló sobre la app 0.3.1 del emulador. Esa evidencia es histórica y no sustituye la comprobación de 0.3.3 ni la de fases 10 y 11. [Avance](AVANCE.md) registra ahora **35.7% por hitos**: mapa base, incidentes por zona/marcadores y captura física comprobados técnicamente; integración productiva pendiente. La consulta de 0.4 sigue sin pantalla y oculta; su futuro flujo visual corresponde al compañero y queda pendiente de autorización para activarlo.
