# Avance del plan del proyecto

## Entrega 0.6.3 validada — integración y mejoras de uso

**18 de octubre de 2026 · Android 0.6.3 / código 31.** La petición actual autoriza combinar las novedades de GitHub con fases 43–44 y publicar una nueva Release. Reúne **seis recorridos guiados/20 pasos** y el **resultado del incidente declarado por autor o administrador**, con motivo, actor e historial, separado del seguimiento. Se prepara también el acceso **app** de Android Studio. [Entrega](ENTREGA_0_6_3.md) · [Guía de prueba](PROBAR_0_6_3.md).

El porcentaje se mantiene en **28/28 = 100% de construcción funcional local**. E11/E12 amplían utilidad y no inflan el PDF. **1675 casos distintos aprobados: 1121 ejecutados y 554 recibos intactos reutilizados**, sin fallos, errores u omitidos finales. Binario/firma, 42 recorridos Android de app y actualización real desde 0.6.2 validados; esquema 4 → 5 y datos/foto/contactos/historial conservados. Kotlin, ensamblados y lint sin errores; app conserva 22 avisos por variante. La publicación y descarga verificada continúan pendientes. Las cifras históricas de fases 43/44 no se suman como nuevas pruebas. Las restricciones de publicación inferiores describen los cortes anteriores, sustituidas para esta entrega por la autorización actual. El refinamiento posterior del tutorial se registra aparte.

## Corte local Unreleased — fase 44: resultado del incidente

El usuario eligió que **autor y administrador** puedan indicar si un incidente se solucionó, identificando quién lo marcó. La mejora **E12**, implementada y validada localmente, separa **Sin confirmar / Solucionado / Sigue sin solucionarse** del seguimiento administrativo. Motivo, revisión y confirmación conservan decisiones explícitas; el historial mantiene actor por UUID, rol, fecha y declaraciones previas. Los reportes antiguos no heredan un resultado por estar Cerrados. [Fase 44 y criterios](FASE_44.md).

**1662 casos distintos aprobados / 108 nuevos**, sin fallos/errores/omitidos finales: 1451 JVM y 211 Android API 37, incluidos ocho recorridos nuevos y 23 regresiones de app. Compilación y lint sin errores; [validación y procedencia](VALIDACION.md). El plan original permanece en **28/28 = 100% de construcción funcional local**, con aceptación física y producción aparte. E12 no amplía el denominador ni acredita las pruebas anteriores otra vez. Oficial **0.6.2 / código 30** y sus archivos intactos; fases 43 y 44 siguen locales **Unreleased**, sin publicar por esta continuación.

## Corte local Unreleased — fase 43: recorrido guiado

La mejora **E11**, solicitada después de publicar 0.6.2, incorpora una guía breve por pantalla mediante el icono **?**. Se consulta voluntariamente en Inicio, Reportar, Revisión, Reportes, Detalle y Mapa; sus controles solo recorren las instrucciones. Las acciones reales y sus comprobaciones conservan gestos separados. **26 casos Android distintos aprobados/ocho nuevos**, sin fallos; compilación y lint sin errores. [Alcance y límites de validación](FASE_43.md).

El avance del PDF permanece en **28/28 = 100% de construcción funcional local**, con aceptación física completa y producción registradas aparte. E11 amplía utilidad, sin añadir criterios ni atribuir nuevas pruebas a la entrega anterior. Este corte queda local **Unreleased**; la oficial **0.6.2 / código 30**, sus archivos, tag, Release y GitHub/main conservan el corte de fases 35–42.

## Corte de entrega 0.6.2 — fases 35–42 acumuladas

**16 de octubre de 2026 · Android 0.6.2 / código 30 · Release oficial publicada.** La entrega reúne **todas las fases posteriores a la oficial 0.6.1: 35–42**. Actividad, avisos manuales, mensaje a contactos, legibilidad del borrador y administración local con consulta/cambio/historial quedan en un mismo corte. [Entrega](ENTREGA_0_6_2.md) · [Guía de prueba](PROBAR_0_6_2.md).

**28/28 = 100% de construcción funcional local**, conservando los 28 criterios y sus pesos. Cada requisito 4.1–4.7 está en 4/4. Las mejoras E01–E10 tienen registro propio y no inflan ese porcentaje. Fase 42 cierra con 1431 casos distintos —1379 JVM, 52 Android—, sin sumar los totales de otras fases como pruebas adicionales de esta entrega. [Validación y procedencia](VALIDACION.md).

La aceptación física completa, pruebas de fabricantes/API 26, seguridad de producción y una integración institucional requieren evidencias separadas. Un cambio de estado o aviso Android no acredita atención oficial. La [Release 0.6.2](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.2) ya está publicada; los párrafos inferiores mantienen **los estados, versiones y resultados históricos** de cada fase, incluidos los que entonces eran Unreleased.

## Antecedentes de construcción por fase

**Fase 42, desarrollo local validado el 16 de octubre de 2026:** del recibo de cambio confirmado se abren **Ver actividad de esta cuenta** y **Revisar avisos de cambios**, sin reconocer la solicitud, transportar el cuerpo del cambio en extras o iniciar una revisión/publicación; las rutas conservan el sello de sesión. Avisos mantiene activación voluntaria por cuenta, permiso/canal y revisión manual de los eventos reales guardados en SQLite; el resumen general deduplica por UUID de operación. El endurecimiento del almacén conserva `.new`/`.bak` pendientes sin restaurar o limpiar. [Alcance](FASE_42.md) · [Guía](ADMINISTRADOR_LOCAL.md).

**28/28 = 100% de construcción funcional local**, con **4.7 Notificaciones en 4/4** al comprobar eventos de cambios autorizados, entrega del resumen y recorrido integrado. Quedan cero criterios funcionales locales pendientes del plan.

**1431 casos distintos aprobados/14 nuevos**, sin fallos/errores/omitidos finales: 1379 JVM —205 recibos intactos de dominio reutilizados, 148 datos y 1026 app ejecutados— y 52 Android API 37 ejecutados. Lint sin errores/22 avisos existentes por variante de app; datos sin incidencias. [Desglose y límites](VALIDACION.md).

El cierre se refiere exclusivamente a **construcción funcional local del plan**. La aceptación física completa, preparación de la entrega, seguridad de producción e integración institucional conservan sus alcances propios; no se atribuyen por completar criterios o ejecutar pruebas. Código local, sin actualizar GitHub/main o PR #30 y sin publicar APK/tag/Release. La oficial 0.6.1 mantiene su corte. Los párrafos inferiores conservan sus cifras históricas y las matrices vigentes reflejan fase 42.

**Fase 41, desarrollo local validado:** seguimiento administrativo con lectura de estado/revisión, motivo de 5–500 caracteres, revisión y confirmación separadas. Transiciones consecutivas, actor por UUID, fecha, motivo y secuencia reales; estados de este teléfono sin acreditar atención de una institución. Recuperación por lectura y reintento de la solicitud exacta, reconocimientos explícitos y protección de sesión/permisos/primer plano. [Alcance](FASE_41.md) · [Guía](ADMINISTRADOR_LOCAL.md).

**25/28 = 89.3%**, con seguimiento de **4.4 (4/4)** y cambio/historial de **4.6 (4/4)** completados mediante el recorrido integrado. **1488 casos distintos aprobados/103 nuevos**, sin fallos finales: 1379 JVM y 109 Android. Los tres pendientes de **4.7** requieren la siguiente integración de avisos sobre cambios reales; no se suman definiciones, repeticiones o publicaciones como criterios.

Corte local, sin push, cambios a main, actualización del [PR #30 de fase 40](https://github.com/Spidey123134/citizensecurity/pull/30), APK, tag o Release. La oficial **0.6.1** conserva su entrega. Los párrafos de fases anteriores mantienen sus cifras históricas; las matrices vigentes reflejan fase 41.

**Fase 40, Unreleased:** cuenta elegida **`admin_local`**, sin contraseña predeterminada, con alta por confirmación nativa del propietario y segundo gesto. Consulta protegida del espacio de esa cuenta: filtros, folio, detalle e historial por páginas de 50. [Guía de uso](ADMINISTRADOR_LOCAL.md) · [Alcance y validación](FASE_40.md).

**22/28 = 78.6%**, con **4.6 Administración en 2/4**: consulta visible y roles/permisos reales. Quedan seis criterios: cambio e historial administrativo completo, seguimiento de 4.4 y los otros tres de 4.7. **1360 casos distintos aprobados/76 nuevos**, sin fallos; incluye confirmación nativa con PIN real en emulador API 37 y consulta de la propia cuenta. Código y guía preparados para `development/administracion-local` y un PR en borrador de GitHub; sin integrar main, APK, tag o Release. La descarga oficial **0.6.1** conserva su corte y el candidato 0.6.2 archivado es anterior a fases 38–40. Este párrafo conserva las cifras históricas de fase 40; las matrices vigentes se actualizan al cierre de fase 41.

**Fase 39, Unreleased local:** revisión del borrador con tarjetas de datos/lugar/foto y marco de 240 dp que contiene la imagen completa. Continúa 4.6 ligando la consulta administrativa al espacio y sesión capturados, revocados al salir incluso si se ingresa de nuevo con la misma cuenta. Permisos separados y denegados por defecto; asignación/panel pendientes. [Alcance, pruebas y límites](FASE_39.md).

**20/28 = 71.4% permanece**: el arreglo visual y la base de sesión no completan otro criterio del PDF. Sin push, tag o publicación; APK oficial **0.6.1**. El candidato 0.6.2 archivado es anterior a las fases 38/39 y requerirá actualizarse en la próxima entrega autorizada.

**Fase 38, Unreleased local:** base de consulta administrativa acotada y política explícita con `READ_REPORTS` separado de `CHANGE_STATUS`. Captura fuentes/dueño/actor, autoriza antes de abrirlas y revalida vigencia/cancelación al consultar; escritura y recibos exigen permiso de cambio, también antes del commit. [Fase 38](FASE_38.md), con validación registrada.

**20/28 = 71.4% permanece**, mismos ocho criterios pendientes: no se completa administración por añadir contratos o controles internos. La asignación y la interfaz administrativa siguen pendientes; `admin/admin` conserva acceso académico. Fase 38 queda local sin push/tag/Release. El usuario pospuso la publicación y se guardaron candidato y borradores 0.6.2/código 30 con numeración tentativa; la APK oficial **0.6.1** conserva su entrega.

**Fase 37 / E06, Unreleased:** mensaje revisado desde un folio para un contacto, ubicación voluntaria y composición SMS por segundo gesto, sin envío automático. **20/28 = 71.4% permanece**: amplía utilidad sin sumar criterios del PDF. [Fase 37](FASE_37.md) · [Validación](VALIDACION.md).

**Fase 36, Unreleased:** preferencia de avisos por cuenta, permiso de Android por gesto y revisión manual de cambios registrados desde Actividad. Resumen general y registro durable de operaciones aceptadas; sin datos privados en el aviso. [Fase 36](FASE_36.md) · [Validación](VALIDACION.md).

**Entrega 0.6.1 — 14 de octubre de 2026 · código Android 29:** fases 29–34 agrupadas, con cámara/galería, ficha PDF, contactos, Ayuda inmediata/atajos y mosaico de Ajustes rápidos. Amplían utilidad y la fotografía ya construida, por lo que **19/28 = 67.9%** conserva los mismos criterios y nueve pendientes. [Entrega](ENTREGA_0_6_1.md) · [Pruebas y límites](VALIDACION.md). Administración y asignación siguen pendientes.

## Avance vigente — 16 de octubre de 2026

**28/28 = 100% de construcción funcional local**, mismos criterios y pesos del PDF/cliente. La fase 42 completa **4.7 Notificaciones** al comprobar avisos manuales sobre los eventos reales del escritor autorizado de fase 41. Se conservan seguimiento/cambio/historial administrativo de fase 41, alta/consulta/roles de fase 40 y permisos/preferencias de fase 36. Las extensiones E01–E04/E10 incluidas en 0.6.1 amplían utilidad sin sumar criterios ya completos.

| Punto original | Construidos | Pendientes |
|---|---|---|
| 4.1 Usuarios | 4/4 | 0 |
| 4.2 Reportes | 4/4 | 0 |
| 4.3 Geolocalización | 4/4 | 0 |
| 4.4 Consulta | 4/4 | 0 |
| 4.5 Mapas | 4/4 | 0 |
| 4.6 Administración | 4/4 | 0 |
| 4.7 Notificaciones | 4/4 | 0 |

Quedan **0 criterios funcionales locales** pendientes. La cuenta elegida es `admin_local`; su activación requiere la credencial nativa del propietario y un segundo gesto en ese teléfono. Consulta, cambio y avisos tienen un recorrido integrado comprobado en emulador, limitado al propio espacio. La aceptación física completa y preparación de la entrega permanecen separadas; no se afirma que ya se activó en el teléfono del usuario o hubo atención de una autoridad. Las comprobaciones se registran por corte en [Validación](VALIDACION.md) y no se suman como puntos ni otra prueba física GPS.

La entrega acumulada **0.6.2 / código 30**, en preparación por solicitud de publicar, reúne las fases **35–42**; no modifica el archivo histórico 0.6.1, que conserva sus fases 29–34 y su cierre de 1237 casos distintos. El binario final se identifica en [Entrega 0.6.2](ENTREGA_0_6_2.md), con firma académica conservada y actualización 0.6.1 → 0.6.2 comprobada en dos etapas del emulador API 37. Los resultados de cada corte están separados en [Validación](VALIDACION.md), sin sumar repeticiones. Las nativas API 37 no acreditan ejecución física, muerte completa del proceso o recepción institucional.

[Criterios vigentes](#cómo-se-calcula--criterios-vigentes) · [Plan](PLAN_DEL_PROYECTO.md) · [Fases](FASES.md) · [Entrega 0.6.2](ENTREGA_0_6_2.md). Android **0.6.2 / código 30** preparado para publicar; reportes esquema 4 y cuentas 1. Los bloques siguientes conservan sus cifras y pendientes históricos; sus marcas Unreleased describen aquella preparación.

## Antecedente — entrega 0.6.0

## Entrega agrupada 0.6.0 — fases 21–28

**19/28 = 67.9% de construcción**, mismos 28 criterios/pesos. La entrega reúne cuentas/aislamiento local, consulta paginada/restaurable, mapa filtrado e historial real. La [fase 28](FASE_28.md) prepara coordinación de solicitudes de cambio con identidad retenida y reintento explícito; falta diario durable y no se conecta escritura administrativa.

| Punto del PDF | Hitos construidos |
| --- | --- |
| 4.1 Usuarios | 4/4 |
| 4.2 Reportes | 4/4 |
| 4.3 Geolocalización | 4/4 |
| 4.4 Consulta | 3/4 |
| 4.5 Mapas | 4/4 |
| 4.6 Administración | 0/4 |
| 4.7 Notificaciones | 0/4 |

La asignación administrativa sigue pendiente por elección del usuario. Preparar historial/servicio/modelo o publicar una APK no suma permisos, seguimiento completo o notificaciones. El [cierre agrupado](VALIDACION.md) aprobó **1164 casos distintos**, incluidos 271 Android y los 15 de historial que antes estaban solo compilados. Esas pruebas se separan de la aceptación del usuario y no acreditan otra captura GPS física. [Entrega 0.6.0](ENTREGA_0_6_0.md).

## Antecedente — preparación de 0.6.0

## Preparación de 0.6.0 — fase 27, Unreleased

**19/28 = 67.9% de construcción**, mismos 28 criterios/pesos. [Fase 27](FASE_27.md) añade historial real consultable por folio y un servicio protegido de transiciones; no suma hitos todavía. Usuarios 4/4, Reportes 4/4, GPS 4/4, Consulta 3/4, Mapas 4/4, Administración 0/4 y Notificaciones 0/4.

**100 casos aprobados, 37 nuevos**; 15 Android nuevos compilados, no ejecutados. Lectura y creación atómica de eventos, migración aditiva y rechazo por permisos/CAS comprobados en JVM/SQLite del host; aceptación Android pendiente. El usuario eligió dejar pendiente la asignación administrativa. La escritura productiva permanece denegada por defecto: sin actor/permiso y recorrido integrado no se acreditan seguimiento de estados, cambios administrativos o historial administrativo completo. Sin APK, incremento de versión, tag o Release por fase.

## Antecedente — fase 26

## Preparación de 0.6.0 — fase 26, Unreleased

**19/28 = 67.9% de construcción**, mismos 28 criterios/pesos. [Fase 26](FASE_26.md) completa identidad/aislamiento local de Usuarios **4/4** y reportes propios de Consulta **3/4**. Reportes 4/4, GPS 4/4, Mapas 4/4, Administración 0/4 y Notificaciones 0/4. Reportes, fotos, diarios y pantallas separados por cuenta; históricos conservados como académicos.

**73 aprobados/59 nuevos**, con SQL/esquema productivos en archivos independientes y comprobación de accesos/sellos/sesión. Cuatro Android nuevos compilados, no ejecutados: aceptación pendiente para 0.6.0. Identidad/aislamiento locales; sin servidor, identidad civil o roles administrativos. Faltan seguimiento de estados, administración con historial y avisos. Sin APK/Release por fase.

## Antecedente — fase 25

## Preparación de 0.6.0 — fase 25, Unreleased

**17/28 = 60.7% de construcción**, mismos 28 criterios/pesos. [Fase 25](FASE_25.md) añade creación local y verificación de credenciales: Usuarios **3/4**; Reportes 4/4, GPS 4/4, Consulta 2/4, Mapas 4/4, Administración 0/4 y Notificaciones 0/4. Los dos hitos técnicos se apoyan en validación/KDF reales, SQLite del host con SQL productivo y coordinación/integración compilada: **36 casos nuevos aprobados**.

La aceptación del repositorio y del recorrido en Android queda pendiente para 0.6.0; 12 casos nativos se compilaron, no ejecutaron. Cuentas solo del teléfono, reportes todavía compartidos: sesión no acredita aislamiento, autoría, permisos administrativos, servidor o sincronización. El cuarto hito de Usuarios y el tercero de Consulta requieren separar datos por autor, conservando los reportes históricos sin atribuirlos a una cuenta nueva. Código en main; sin APK/Release por fase.

## Antecedente 0.5.7

## Avance 0.5.7 — fase 20

**15/28 = 53.6%** con los mismos criterios y pesos. [Fase 20](FASE_20.md) fortalece **4.4 Consulta** mediante búsqueda real por descripción/referencia y fechas del incidente, filtros combinados, recuperación de entradas al girar y cancelación del folio al volver. No agrega criterios, completa identidad de autor ni implementa seguimiento administrativo. La mejora pertenece a la lista, sin acreditar un hito adicional de mapas.

| Punto del PDF | Hitos construidos |
| --- | --- |
| 4.1 Usuarios | 1/4 |
| 4.2 Reportes | 4/4 |
| 4.3 Geolocalización | 4/4 |
| 4.4 Consulta | 2/4 |
| 4.5 Mapas | 4/4 |
| 4.6 Administración | 0/4 |
| 4.7 Notificaciones | 0/4 |

La aceptación manual GPS del 11 de octubre de 2026 ya registrada conserva sus límites: versión APK no especificada, sin logs, nuevas medidas ni guardado físico adicional. Esta entrega no cuenta fixtures ni lecturas locales como otra prueba GPS física. Siguen pendientes cuentas reales/aislamiento de autores, seguimiento administrativo y notificaciones. [Criterios vigentes](#cómo-se-calcula--criterios-vigentes) · [Validación](VALIDACION.md).

## Antecedente 0.5.6

## Avance 0.5.6 — fase 19

**15/28 = 53.6%** con los mismos 28 hitos y pesos. Se completa **4.3 Geolocalización (4/4)** por la aceptación manual del usuario del **11 de octubre de 2026**: el teléfono localizó el punto, la acción de ubicación lo aceptó dentro del radio y el mapa mostró el pin. El usuario no especificó la versión de la APK probada. Esta confirmación no aporta logs, medidas de precisión, coordenadas ni un guardado físico nuevo; tampoco se suma al recuento de pruebas automatizadas.

| Punto del PDF | Hitos construidos |
| --- | --- |
| 4.1 Usuarios | 1/4 |
| 4.2 Reportes | 4/4 |
| 4.3 Geolocalización | 4/4 |
| 4.4 Consulta | 2/4 |
| 4.5 Mapas | 4/4 |
| 4.6 Administración | 0/4 |
| 4.7 Notificaciones | 0/4 |

[Fase 19](FASE_19.md) convierte la pantalla verde de Desarrollador en un inicio útil: **Nuevo reporte**, **Reportes en este teléfono** y **Mapa de reportes por zona**. Sus indicadores leen los diarios privados reales para mostrar una copia de borrador o un intento por comprobar; un fallo de lectura se explica y no equivale a ausencia. Abrir el inicio no obtiene GPS, guarda reportes ni escribe en los diarios. Se retira la interfaz ficticia anterior conservando código, datos e historia; las funciones reales siguen visibles.

El hito original de GPS es comprobar el recorrido de ubicación y confirmación. Guardar y abrir un folio físico sería una comprobación adicional transversal de **4.2/4.4**, no una condición nueva para completar 4.3. Siguen pendientes cuentas reales/aislamiento de autores, seguimiento administrativo y notificaciones. La mejora del inicio no añade otro hito. [Criterios vigentes](#cómo-se-calcula--criterios-vigentes) · [Validación](VALIDACION.md).

## Antecedentes históricos del corte anterior

## Avance 0.5.5 — fase 18

**14/28 = 50%** de construcción por los criterios del PDF. El aviso de posibles repetidos mejora 4.2 y 4.4, pero no agrega un criterio ni representa autenticación o revisión administrativa.

| Punto del PDF | Hitos construidos |
| --- | --- |
| 4.1 Usuarios | 1/4 |
| 4.2 Reportes | 4/4 |
| 4.3 Geolocalización | 3/4 |
| 4.4 Consulta | 2/4 |
| 4.5 Mapas | 4/4 |
| 4.6 Administración | 0/4 |
| 4.7 Notificaciones | 0/4 |

La mitad pendiente requiere cuentas reales/aislamiento de autores, aceptación física del recorrido final con GPS, seguimiento administrativo y notificaciones reales. Las pruebas con fuente controlada no acreditan GPS físico. [Fase 18](FASE_18.md) · [Validación](VALIDACION.md).

## Antecedentes del corte anterior

## Avance 0.5.4 — fases 16 y 17

**14/28 = 50%** de construcción con los mismos criterios y pesos del PDF. Fotografías completa el cuarto hito de **4.2 Reportes (4/4)**: selección, copia privada, revisión/detalle, guardado real y migración. La recuperación del borrador fortalece ese recorrido y no inventa un hito adicional.

**4.3 permanece 3/4**: las pruebas nuevas usan Android real y una fuente de ubicación controlada en integración, sin acreditar GPS físico final. **4.4 sigue 2/4** porque todavía no hay autor autenticado ni seguimiento administrativo. [Fase 16](FASE_16.md), [Fase 17](FASE_17.md) y [Validación](VALIDACION.md) separan construcción de aceptación.

## Antecedentes del corte anterior
## Avance 0.5.3 — fase 15

**13/28 = 46.4%**, con los mismos hitos y pesos. Se incorporan dos hitos existentes: **4.2 formulario con guardado real autorizado** (3/4) y **4.5 flujo visual integrado y probado** (4/4 de construcción). La orden «ya no mantengas nada oculto» permite conectar el guardado/consulta construidos. La aceptación nativa usa SQLite, AtomicFile y MapLibre reales; la fuente de teléfono es controlada únicamente en androidTest. Esto no acredita GPS físico nuevo: **4.3 permanece 3/4**. La consulta visible mantiene **4.4 en 2/4**, porque aún no hay identidad de usuario ni seguimiento administrativo.

[Fase 15](FASE_15.md) registra evidencia, validación y límites. Los cortes anteriores se conservan como antecedentes; su restricción de ocultación ya fue sustituida.

## Antecedentes históricos

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

## Cómo se calcula — criterios vigentes

Se definen cuatro hitos de construcción por cada requisito del PDF: **28 hitos con el mismo peso**. Cada hito comprobado aporta aproximadamente 3.57 puntos al total; cada fila puede tener 0%, 25%, 50%, 75% o 100%. Esta es una estimación de construcción, no del tiempo o dificultad de las tareas, de las funciones visibles ni de la aceptación del usuario.

Una pantalla visual cuenta solo como preparación visual; una lógica probada cuenta solo como su hito técnico. No se declara el requisito completo mientras falten integración, pruebas y revisión. Las suites compartidas no se suman varias veces para aumentar el porcentaje.

| Requisito | Hitos de construcción | Comprobados | Avance estimado |
| --- | --- | --- | --- |
| 4.1 Usuarios | Pantalla de acceso; creación de cuentas; verificación real de acceso; identidad y aislamiento de datos | Pantalla del compañero; cuentas de fase 25; separación por UUID de fase 26. Recorrido Android ejecutado en 0.6.0, sin servidor | 4/4 · 100% de construcción local |
| 4.2 Reportes | Modelo y validación; persistencia local técnica; formulario con guardado real autorizado; fotografías | Modelo/validación, persistencia técnica, formulario autorizado y fotografías con copia privada/migración probadas en 0.5.4 | 4/4 · 100% de construcción |
| 4.3 Geolocalización | Par de coordenadas validado y conservado; captura del dispositivo; permisos y estados GPS; flujo GPS probado | Coordenadas, captura física previa y permisos/estados comprobados. El 11 de octubre de 2026 el usuario confirmó manualmente localización del punto, aceptación dentro del radio y pin visible; versión APK no especificada, sin logs, medidas o guardado físico nuevo | 4/4 · 100% de construcción |
| 4.4 Consulta | Lectura por folio; lista y permanencia; reportes propios por usuario; seguimiento de estados | Folio, lista/permanencia y espacio de cuenta de fase 26; historial de fase 27. Fase 41 compone seguimiento visible con cambios autorizados, revisión y recuperación del recibo comprobados en Android con SQLite/diario reales | 4/4 · 100% de construcción local |
| 4.5 Mapas | Contrato de selección y confirmación; SDK configurado; mapa por zonas y marcadores; flujo visual integrado y probado | Contrato, SDK, zonas/marcadores y flujo visual integrado en 0.5.3: guardado/folio, mapa nativo con calles y pines SQLite, filtros/límite y pin→detalle. La validación nativa usa una fuente de teléfono controlada; la aceptación manual física posterior se acredita por separado en 4.3 | 4/4 · 100% de construcción |
| 4.6 Administración | Consulta administrativa; cambios de estado; roles y permisos comprobados; historial | Fase 40: alta/consulta y permisos por UUID/sesión. Fase 41: cambios consecutivos visibles con motivo revisado, confirmación separada, recuperación exacta e historial con actor/fecha/secuencia, comprobados en Android | 4/4 · 100% de construcción local |
| 4.7 Notificaciones | Eventos de cambios; entrega de avisos; permisos y preferencias; flujo completo probado | Fase 36: permiso/preferencias por cuenta y persistencia. Fase 42: cambios autorizados reales de fase 41, evento SQL, revisión manual, resumen Android y deduplicación por operación comprobados en el recorrido nativo; desactivar conserva IDs y navegar conserva el pendiente administrativo | 4/4 · 100% de construcción funcional local |

**Avance actual: 28/28 = 100% de construcción funcional local.** La fase 42 cierra eventos, entrega de avisos y recorrido integrado; las fases 40/41 conservan administración y seguimiento, y fase 36 permisos/preferencias. Quedan cero criterios funcionales locales pendientes; aceptación física completa y preparación de la entrega se comprueban aparte. Fotografías y aceptación manual GPS conservan sus hitos/límites. Publicar y repetir pruebas no suman puntos. No declara servidor/sincronización, seguridad de producción, recepción institucional o veracidad de incidentes.

## Antecedentes de cálculo y evidencia

Los porcentajes y pendientes siguientes pertenecen a sus cortes históricos; no sustituyen la tabla vigente anterior ni la confirmación manual registrada para 0.5.6.

**0.3.3 no suma otro hito ni cambia el porcentaje.** Fortalece el contrato con el radio de 5 km elegido por el usuario, exige evidencia reciente y suficientemente precisa y prepara un guard para la escritura productiva. No obtiene GPS ni configura SDK, permisos o pantalla de mapa. La regla y sus pruebas se documentan en [Proximidad de reportes](PROXIMIDAD_REPORTES.md) y [Validación](VALIDACION.md), separadas del corte 0.3.2.

**0.3.4 tampoco suma un hito todavía.** La solicitud y renovación de evidencia, estados, cancelación y tiempo de espera configurable se comprobaron en su paso anterior al SDK como parte de **185 pruebas diferentes aprobadas**: 86 de dominio, 30 de app, 20 JVM de datos y 49 instrumentadas API 37. La fuente Android se revisó con un backend controlado y el camino nativo de permiso denegado; no obtuvo una posición por GPS real. Ese paso inicial no declaró permisos; el posterior SDK sí los declaró, sin solicitud interactiva ni mapa activado. El hito de captura requiere observar una lectura procedente del dispositivo; permisos y flujo GPS tienen sus propios hitos y pruebas. [Obtener ubicación](OBTENER_UBICACION.md) y [Validación](VALIDACION.md) distinguen el contrato comprobado de estas comprobaciones pendientes. La repetición de las mismas 49 instrumentadas no aumenta el total ni el porcentaje.

Mapas tiene sus cuatro hitos de construcción comprobados en 0.5.3. Eso no equivale a aceptar físicamente todo el recorrido de reporte: la captura GPS física previa fue técnica y el flujo visual final sigue pendiente con un teléfono.  El login visual no autentica y los reportes propios y el seguimiento necesitan cuentas y administración. Una lectura física válida no demuestra que ocurrió un incidente ni garantiza disponibilidad GPS en cualquier lugar.

## Evidencia y límites

La preparación anterior 0.3.1 comprobó el ejemplo ficticio sin cambios en bases productivas y mantuvo ocultos el guardado real y toda consulta 0.4. Las comprobaciones técnicas históricas de persistencia y consulta respaldan sus hitos de construcción, aunque esos flujos no estén activados para el usuario. [Validación](VALIDACION.md) conserva comandos, resultados y límites por incremento.

El nuevo commit remoto del compañero `b665a56` añade y ajusta layouts, incluida creación de cuenta visual. No implementa cuentas ni modifica contratos, así que no aumenta los hitos de autenticación de este corte. La integración 0.4.0a ya incorpora esos layouts sin perder el ejemplo ficticio, como se registra en [Fases](FASES.md).

Los cortes históricos Unreleased fueron locales. Para 0.4.0a el usuario autorizó expresamente integrar, subir y publicar; la comprobación final y publicación se registran en [Validación](VALIDACION.md) y [Releases](RELEASES.md). La aceptación funcional de los hitos sigue pendiente. El criterio de los hitos se conserva en cada corte para que futuros porcentajes sean comparables; un cambio de alcance o de pesos se documentará antes de comparar resultados.

El catálogo vigente [Mejoras útiles](MEJORAS_UTILES.md) distingue E01–E03 implementadas/validadas Unreleased y E04–E09 propuestas o guía por fabricante. [Mejoras futuras](MEJORAS_FUTURAS.md) conserva los antecedentes. No fijan una versión oficial ni añaden hitos o porcentaje a este corte.
