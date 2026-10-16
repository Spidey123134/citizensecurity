# Fases de CITIZENSECURITY

## Fase 41 — cambio de estado e historial administrativo, local validada

Continúa **4.4 Consulta y 4.6 Administración**: desde un folio, comprobar pendientes, leer revisión/estado, escribir un motivo válido de 5–500 caracteres, revisar y confirmar con otro gesto. Solo avances consecutivos **Reportado → En revisión → Atendido → Cerrado**. El recibo y el historial conservan actor por UUID, fecha, motivo, secuencia y operación; son estados locales, sin acreditar atención oficial.

Recuperar lee diario y recibo; reintentar conserva cuerpo y UUID completos, y reconocer exige un gesto. La relectura de un rechazo puede mostrar un recibo confirmado tardío. Sesión, permisos, revisión, primer plano y bloqueo se comprueban antes de guardar; giro y navegación no escriben. [Recorrido y límites](FASE_41.md) · [Guía](ADMINISTRADOR_LOCAL.md).

**25/28 = 89.3%**: Consulta y Administración alcanzan **4/4**, con tres criterios pendientes de Notificaciones. **1488 casos distintos aprobados/103 nuevos**, sin fallos finales; 1379 JVM y 109 Android, con lectura/rol/diario/SQLite reales y tres avances visibles. Lint sin errores; [desglose y límites](VALIDACION.md). Corte **local**, sin actualizar GitHub/main o el [PR #30 de fase 40](https://github.com/Spidey123134/citizensecurity/pull/30), ni publicar APK/tag/Release. Oficial **0.6.1**. Los siguientes bloques conservan los antecedentes.

## Fase 40 — administración local y consulta protegida, Unreleased

Cuenta elegida **`admin_local`**, con registro y contraseña propia; confirmación nativa del propietario, segundo gesto de activación y asignación única por UUID. Consulta visible de su espacio local con filtros, folio e historial de 50 por página; permiso/sesión vigentes, respuestas tardías descartadas y reintento exacto. [Guía para el equipo](ADMINISTRADOR_LOCAL.md) · [Alcance, criterios y validación](FASE_40.md).

**22/28 = 78.6%**, con **4.6 Administración en 2/4** por consulta y roles/permisos. Quedan seis criterios: seguimiento, cambio/historial administrativo y tres de notificaciones. **1360 casos distintos aprobados/76 nuevos**, sin fallos; incluye confirmación nativa con PIN real en emulador API 37. Código/documentación preparados para `development/administracion-local` y un PR en borrador de GitHub, sin integrar main ni publicar APK/tag/Release. `admin/admin` sigue académico; APK oficial **0.6.1**, candidato 0.6.2 anterior a fases 38–40. Los estados inferiores son antecedentes de cada corte.

## Fase 39 — borrador y vinculación administrativa, Unreleased local

Ordena la revisión en tarjetas de datos, lugar y fotografía; la imagen completa se ajusta a un marco de 240 dp para evitar solapamientos, sin modificar el archivo o el guardado. Continúa 4.6 del PDF vinculando la política administrativa al espacio/sesión capturados: revocación al salir, incluso al volver con la misma cuenta, y comprobación de vigencia antes/después de autorizar. [Alcance, pruebas y límites](FASE_39.md).

Consulta y cambio conservan permisos separados, denegados por defecto. Asignación y panel pendientes; `admin/admin` académico. **20/28 = 71.4% permanece**. Trabajo local sin push/tag/Release; APK oficial **0.6.1** y candidato archivado anterior a fases 38/39.

## Fase 38 — permisos y consulta administrativa, Unreleased local

Separa `READ_REPORTS` de `CHANGE_STATUS`, sin permisos implícitos por iniciar sesión. El lector administrativo captura dueño/actor y fuentes para consultar folio, lista paginada e historial; autoriza antes de abrirlas y comprueba revocación/cancelación antes de devolver resultados. El escritor exige permiso de cambio también antes del commit, y la recuperación de recibos permanece bajo esa capacidad. **1267 casos distintos aprobados/25 nuevos**. [Alcance y validación](FASE_38.md).

La asignación y el panel administrativo siguen pendientes; `admin/admin` conserva acceso académico. **20/28 = 71.4% permanece**, sin acreditar otro criterio por preparar esta base. Trabajo local sin push/tag/Release; la preparación 0.6.2/código 30 queda guardada como candidato y borradores tentativos. La APK oficial continúa en **0.6.1**.

## Fase 37 — mensaje revisado para un contacto, Unreleased

E06 permite preparar texto desde un folio, elegir un contacto vigente y revisar la ubicación opcional antes de abrir Mensajes SMS. Relectura antes de compartir, cambio de destinatario/reporte exige otra revisión, giro sin nueva apertura y datos conservados. Ayuda comparte la fábrica de contactos; volver de guardar uno actualiza la lista sin elegirlo automáticamente. No envía mensajes ni adjunta la ficha PDF. [Alcance y límites](FASE_37.md) · [Validación](VALIDACION.md).

**20/28 = 71.4% permanece**, mejora adicional al PDF. Asignación administrativa pendiente; Android 0.6.1/código 29 y APK oficial conservados.

## Fase 36 — preferencias y avisos de cambios, Unreleased

Desde Actividad se activa voluntariamente el permiso y la preferencia por acceso. Revisar cambios consulta transiciones reales y prepara un único aviso general de Android, sin datos privados. Registro durable por dueño, deduplicación, revisión acotada con continuación y recuperación explícita ante incertidumbre. Sin revisión en segundo plano ni administración habilitada. [Alcance y límites](FASE_36.md) · [Validación](VALIDACION.md) · [Avance vigente](AVANCE.md).

Android conserva 0.6.1/código 29 y su APK oficial; las fases 35/36 son código Unreleased. La asignación administrativa sigue pendiente.

**20/28 = 71.4%:** se completa permisos/preferencias de 4.7. Los otros tres criterios de avisos esperan el recorrido productivo autorizado; los cambios de pruebas no conceden roles. Fase 36 añade 87 definiciones aprobadas (57 JVM/30 Android).

## Antecedente — fase 35

## Fase 35 — actividad de reportes, Unreleased

Centro visible desde Inicio para consultar creaciones y cambios realmente registrados, filtrar transiciones y abrir el folio. Fuente única SQL paginada, una página de 50 y hasta 50 posiciones anteriores; reintento, giro y sesión revocada comprobados en el cierre. Migración aditiva del índice a esquema 4, fotos/historial conservados y recibos 3/4 de solo lectura. [Alcance y límites](FASE_35.md) · [Validación](VALIDACION.md).

Retoma 4.4/4.7 sin activar administración o presentar un guardado como cambio/recepción. Avisos Android y preferencias continúan pendientes; **19/28 = 67.9% permanece**. La APK oficial 0.6.1 incluye fases 29–34, y el código nuevo se mantiene Unreleased.

## Entrega agrupada 0.6.1 — fases 29–34

**14 de octubre de 2026 · Android 0.6.1 / código 29**, entrega solicitada expresamente con APK/Release. Agrupa los siguientes recorridos y la base protegida del PDF:

| Fase | Contenido incluido en 0.6.1 |
|---|---|
| [29](FASE_29.md) | Ficha PDF desde un folio real, opciones revisadas y compartir por gesto separado |
| [30](FASE_30.md) | Ayuda y hasta cinco contactos manuales privados por cuenta |
| [31](FASE_31.md) | Solicitud durable de cambio, mismo UUID/recibo y reconocimiento antes de limpiar; sin administración activada |
| [32](FASE_32.md) | Ayuda inmediata pública, atajo del icono y petición de acceso directo |
| [33](FASE_33.md) | Cámara además de galería, conservación del borrador al cancelar/fallar y limpieza del temporal exacto |
| [34](FASE_34.md) | Mosaico voluntario en Ajustes rápidos, alta explícita y apertura de ayuda por toque/desbloqueo |

**1237 casos distintos aprobados, 307 definiciones nuevas frente a 0.6.0**, cero fallos/errores/omitidos finales: 1074 JVM + 163 Android API 37. Fase 34 añade 33 casos (28 JVM + cinco Android). Lint app cero errores/22 avisos por variante y datos sin incidencias. Recibos, reutilización de resultados intactos y límites en [Validación](VALIDACION.md); no se declara cámara física, muerte completa del proceso, actualización por instalación o ejecución física API 26.

**19/28 = 67.9% permanece**, nueve criterios originales pendientes. El mosaico/cámara amplían utilidad; asignación y recorrido administrativos siguen pendientes. Datos/esquemas/firma y login/loading se conservan. [Release y APK](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.1) · [Entrega](ENTREGA_0_6_1.md) · [Probar](PROBAR_0_6_1.md).

## Antecedente — preparación Unreleased de 0.6.1

Los bloques siguientes describen sus cortes de desarrollo y mantienen sus cifras originales. Fases 29–34 se incluyen ahora en 0.6.1; las marcas **Unreleased** y la ausencia de otra APK descritas en aquella preparación no vuelven a dejar pendiente su entrega.

## Fase 33 — cámara del reporte, Unreleased

**14 de octubre de 2026.** [Tomar fotografía](FASE_33.md) amplía 4.2 con captura desde la cámara además de galería: URI temporal propia, normalización/revisión y conservación de foto anterior ante fallo/cancelación. Apertura vinculada al origen/token, giro sin relanzar, revocación/limpieza solo del temporal y controles bloqueados hasta terminar. Sin nuevos permisos o modificación de login/loading; Android **0.6.0 / código 28**, sin nueva APK/Release. El criterio de foto ya estaba completo: **19/28 = 67.9%** permanece. [Validación](VALIDACION.md).

## Fases 31 y 32 — seguimiento durable y acceso rápido, Unreleased

**14 de octubre de 2026.** Se continúa el PDF/cliente con [fase 31](FASE_31.md): diario privado de una solicitud de cambio, recuperación explícita por lectura del recibo y reconocimiento con reverificación antes de limpiar el cuerpo exacto. Conserva dueño/actor/UUID y autorización denegada por defecto, sin componer administración o asignar roles.

[Fase 32](FASE_32.md) implementa la idea del usuario sobre atajos: Ayuda inmediata pública, sin login/GPS ni contactos privados, atajo estático del icono y solicitud explícita de acceso en Inicio si el launcher la admite. La comprobación/petición de fijado trabaja fuera del hilo de interfaz. El marcador exige gesto visible y dispositivo desbloqueado. No modifica botones físicos/SOS ni implementa un mosaico de Ajustes rápidos.

El nuevo [catálogo E01–E09](MEJORAS_UTILES.md) documenta origen, prioridad, estado y aceptación de las mejoras adicionales al PDF: evidencia, contactos, atajos, mensajes revisados, múltiples fotos, institución y zonas. [Plan](PLAN_DEL_PROYECTO.md) y [Avance](AVANCE.md) corrigen la matriz vigente, separando los pendientes históricos de los actuales.

Las implementaciones están validadas con JVM y pruebas nativas en un emulador aislado **API 37**; resultados ejecutados y recibos en [Validación](VALIDACION.md). No se realizaron llamadas ni se aprobó un acceso fijado real, y no se atribuye ejecución API 26 o prueba física de botones/launcher. **19/28 = 67.9% permanece**, nueve criterios originales pendientes. Código Unreleased, Android **0.6.0 / código 28**, sin otra APK publicada, tag o Release. Asignación administrativa y composición del panel pendientes; login/loading del compañero y datos conservados.

El cierre aprobó **1062 casos distintos, 112 nuevos**, cero fallos/errores/omitidos finales: 997 JVM y 65 Android API 37. Lint app cero errores/22 avisos por variante, datos sin incidencias. Las cuatro de Ayuda volvieron a aprobar tras revalidar vigencia después de la consulta Keyguard previa al fijado; la repetición no aumenta el total.

## Antecedente — fases 29 y 30

## Fases 29 y 30 — utilidad práctica, Unreleased

El usuario confirmó **proyecto académico en México** y pidió evidencia, contactos y utilidad para un posible uso por policías. [Fase 29](FASE_29.md) añade una ficha PDF revisada desde un folio real, con compartir explícito. [Fase 30](FASE_30.md) añade Ayuda y contactos: marcador de 911/089 y hasta cinco contactos manuales privados por cuenta. Una llamada no necesita preparar reporte o conseguir GPS; una ficha permite conservar y entregar información organizada después.

Código **Unreleased**, sin nueva versión, tag, Release ni APK publicada. Android conserva **0.6.0 / código 28**; la APK de esa entrega no contiene estos recorridos. El resultado de sus comprobaciones se registra por separado en [Validación](VALIDACION.md), sin trasladar ni volver a sumar las 1164 pruebas del cierre anterior.

**19/28 = 67.9% permanece**, mismos criterios del PDF. Estas fases refuerzan reportes/consulta y amplían la utilidad fuera de los 28 criterios originales; no acreditan seguimiento policial, administración o notificaciones. La asignación de permisos sigue pendiente y admin/admin conserva su acceso académico.

## Antecedente — entrega 0.6.0

## Entrega agrupada 0.6.0 — fases 21–28

El usuario autorizó la APK 0.6.0 / código 28. La entrega incluye las fases 21–27 y la base interna de [fase 28](FASE_28.md): coordinación de estado, identidad retenida y reintento explícito, sin diario durable ni escritor administrativo conectado. La asignación sigue pendiente. **19/28 = 67.9% permanece**; el historial no acredita el recorrido administrativo completo. [Entrega y límites](ENTREGA_0_6_0.md) · [Prueba Android](PROBAR_0_6_0.md) · [Cierre agrupado](VALIDACION.md).

**1164 casos distintos aprobados**, cero fallos, errores u omitidos, incluidos 271 Android. Los 15 Android de fase 27 ya se ejecutaron. Los bloques Unreleased siguientes conservan los cortes previos a esta entrega y su evidencia original; su código se incluye en 0.6.0 y el cierre no duplica sus recuentos. [Release y APK](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.0).

## Fase 28 — base siguiente

[Fase 28](FASE_28.md) añade ReportStatusChangeViewModel y 12 pruebas JVM: comando completo, single flight, verificación de recibo e incertidumbre que conserva UUID y exige reintento explícito. No se activa administración ni se promete recuperación tras muerte del proceso. Falta actor/permiso y diario durable antes de conectar escritura.

## Antecedente — preparación de 0.6.0

## Fase 27 — historial real de estados, Unreleased

[Fase 27](FASE_27.md) añade consulta explícita del historial desde el folio y creación atómica de eventos para los reportes nuevos. Migración aditiva a esquema 3 sin reconstruir los cambios anteriores. El servicio de transiciones consecutivas usa CAS, operationId y autorización denegada por defecto; la asignación administrativa queda pendiente por elección del usuario. **100 aprobados/37 nuevos**; 15 Android nuevos compilados, no ejecutados. **19/28 = 67.9% permanece**, sin acreditar el recorrido administrativo. Código para 0.6.0, sin APK, versión nueva, tag o Release.

## Fase 26 — reportes por cuenta, Unreleased

[Fase 26](FASE_26.md) separa reportes, consultas, fotos, borradores y solicitudes por UUID; históricos conservados en académico, esquema 2. Contextos fijos, GPS propio y revocación de pantallas. **73 aprobados/59 nuevos**; 4 Android nuevos compilados, ejecución pendiente. **19/28 = 67.9% de construcción**, Usuarios 4/4 y Consulta 3/4, solo locales. Código en main para 0.6.0, sin APK, versión nueva, tag o Release.

## Fase 25 — cuentas locales, Unreleased

[Fase 25](FASE_25.md) conecta Crear cuenta, comprobación de contraseña y cierre de sesión a los layouts existentes del compañero. SQLite separado, verificadores PBKDF2 y operaciones cancelables sin guardar contraseñas en estado restaurable. **36 casos nuevos aprobados**, incluidos 6 con SQLite del host; 12 Android compilados, ejecución pendiente. **17/28 = 60.7% de construcción**, Usuarios 3/4: cuentas solo locales, reportes aún compartidos, sin aislamiento/roles. Código en main para 0.6.0, sin APK, incremento de versión, tags o Release.

## Fase 24 — recuperación de consulta y reinicio explícito, Unreleased

[Fase 24](FASE_24.md) añade un checkpoint pequeño de criterios/cursor/página para restauración del proceso por Android, con lectura fresca, validación completa y sin resultados/GPS guardados. Corrige Buscar/Limpiar/Mostrar todos con valores repetidos para volver al inicio. **34 casos aprobados, 16 nuevos**; pruebas Android compiladas, ejecución pendiente para la entrega agrupada. **15/28 = 53.6%**, mismos criterios del PDF. Código en main para 0.6.0, sin APK, incremento de versión, tags o Release.

## Fase 23 — historial por páginas, Unreleased

[Fase 23](FASE_23.md) conecta la lista a consultas acotadas de SQLite: hasta 50 coincidencias, lotes de 100 y 500 candidatos examinados por gesto. Cursor exclusivo sin OFFSET, texto con la semántica anterior, continuación explícita si queda recorrido, navegación/reintento y página conservada al volver del detalle o recrearse. Refuerza **4.4 Consulta**, con **15/28 = 53.6%**, mismos hitos/pesos. Código en main para 0.6.0, sin APK, incremento de versión, tags o Release; aceptación Android pendiente.

## Fase 22 — prioridad y búsqueda en segundo plano, Unreleased

[Fase 22](FASE_22.md) añade prioridad a lista/mapa y desplaza el cálculo de búsqueda fuera del hilo de interfaz. Filtra el mapa antes del límite, invalida búsquedas anteriores y comprueba la región real de cámara al abrir su lista/folios. Refuerza **4.4 Consulta / 4.5 Mapas**; **15/28 = 53.6%**, mismos criterios/pesos. Código nuevo en main para 0.6.0, sin APK, incremento de versión, tags o Release. Aceptación Android pendiente para la entrega agrupada.

## Fase 21 — preparación de 0.6.0, Unreleased

[Fase 21](FASE_21.md) amplía **4.4 Consulta / 4.5 Mapas**: fechas del incidente aplicadas antes del límite de marcadores y lista de los folios de la misma consulta de zona. Intervalos inválidos conservan la consulta anterior; una muestra invalidada no habilita abrir sus folios. El texto sigue siendo un filtro de la lista general.

Actualizaciones de código en main, sin APK, tags ni Releases por fase. La última APK sigue siendo **0.5.7 / código Android 27**; la próxima entrega agrupada prevista es **0.6.0**. Pruebas centradas en la lógica, SQLite y compilación Kotlin; aceptación de controles Android pendiente para esa entrega. **15/28 = 53.6%**, mismos hitos/pesos: este bloque refuerza consulta/mapas, sin completar cuentas, administración o notificaciones.

## Antecedente 0.5.7

## Fase 20 — 0.5.7, encontrar reportes locales

[Fase 20](FASE_20.md) amplía **4.4 Consulta**: búsqueda en descripción/referencia sin distinguir mayúsculas ni acentos, fechas opcionales del incidente, calendario y combinación con tipo/estado. Buscar reportes aplica texto y fechas; un error conserva el filtro válido anterior. Limpiar retira todos los filtros. Se conservan entradas y filtros al girar y volver del detalle; salir de un detalle cancela su consulta pendiente e invalida sus resultados tardíos.

Código Android **27**, entrega **0.5.7**. Los filtros nuevos son de la lista; el mapa mantiene su consulta por zona anterior. No cambia login/loading, GPS, persistencia o datos. [Probar en Android](PROBAR_0_5_7.md) · [Validación](VALIDACION.md).

**15/28 = 53.6%**, mismos 28 hitos/pesos. Esta mejora refuerza consulta sin completar cuentas/aislamiento por autor, estados administrativos o notificaciones y sin añadir un hito de mapas. La confirmación manual del recorrido GPS registrada en 0.5.6 permanece vigente.

## Antecedente 0.5.6

## Fase 19 — 0.5.6, inicio funcional

[Fase 19](FASE_19.md) sustituye la interfaz ficticia de la pantalla verde de Desarrollador por tres entradas útiles: **Nuevo reporte**, **Reportes en este teléfono** y **Mapa de reportes por zona**. Muestra información leída de los diarios privados sobre la copia del borrador o un intento por comprobar, con errores independientes y actualización al regresar. No obtiene GPS ni crea reportes desde el inicio; conserva login, loading, datos y código histórico de las demostraciones. Todas las funciones reales terminadas siguen accesibles.

Código Android **26**, entrega **0.5.6**. [Prueba en Android](PROBAR_0_5_6.md) documenta instalación y recorrido; [Validación](VALIDACION.md) registra las comprobaciones técnicas.

El avance es **15/28 = 53.6%** con los mismos criterios del PDF. **4.3 Geolocalización queda en 4/4** por la aceptación manual que el usuario informó el **11 de octubre de 2026**: el teléfono localizó el punto, lo aceptó dentro del radio y mostró el pin. No especificó versión APK; no se atribuyen logs, precisión medida, coordenadas guardadas ni un guardado físico nuevo. Comprobar guardado/folio en ese teléfono sería una aceptación transversal adicional de 4.2/4.4, sin redefinir el hito GPS. Cuentas, aislamiento por autor, administración, seguimiento y notificaciones continúan pendientes. El cambio del inicio no inventa otro hito.

## Antecedente 0.5.5 — fase 18

[Fase 18](FASE_18.md) añadió el aviso local de posibles reportes repetidos del mismo tipo a **200 m y ±2 h**, con revisión del folio anterior y decisión explícita. Entrega oficial **0.5.5 / código Android 25**, construcción **14/28 = 50%** en aquel corte. [Probar 0.5.5](PROBAR_0_5_5.md). Esa ampliación no completó un hito adicional; la confirmación manual del GPS se recibió después.

## Antecedentes históricos del corte anterior

## Fases 16 y 17 — 0.5.4

[Fotografías](FASE_16.md): selector real de Android, copia privada validada y vista previa en formulario, revisión y detalle; migración SQLite que conserva reportes sin foto. Completa fotografías en 4.2. [Recuperación del borrador](FASE_17.md): guardar por gesto desde revisión, recuperar con confirmación y conservar el folio al convertir la copia en reporte. Mejora el mismo requisito, sin sumar otro hito.

Código Android 24; entrega oficial con APK académico. Construcción **14/28 = 50%**, pesos originales. [Prueba en Android](PROBAR_0_5_4.md) explica instalación y recorrido. GPS físico final, cuentas, seguimiento administrativo y avisos conservan sus pendientes.

## Antecedentes del corte anterior
## Fase 15 — 0.5.3, recorrido local completo

[FASE_15](FASE_15.md) integra formulario → punto validado → guardado → folio → consulta → mapa por zona. Retira la ocultación del guardado y consulta por autorización explícita. Incluye identidad durable ante interrupción y corrección sin pérdida de campos tras rechazo del GPS. Código Android 23; entrega oficial con APK académico. La validación nativa usa SQLite y MapLibre reales con una fuente de teléfono controlada solo en pruebas; no acredita GPS físico del flujo final.

## Antecedentes históricos

## Fase 14 — oficial 0.5.2

[Fase 14](FASE_14.md) completa la revisión visible del reporte y corrige reintentos de SQLite que podían duplicar un folio. Son 529 casos distintos aprobados, 21 definiciones nuevas y APK académico con código 22. Los puntos trabajados son 4.2 y su relación con ubicación 4.3; guardado y consulta reales siguen pendientes de autorización específica. **11/28 = 39.3%**, mismos hitos y pesos. La preferencia vigente es entregar los avances validados como Releases oficiales, conservando sus versiones previas.

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

**Fase activa: 11 — incidentes por zona y marcadores — Unreleased, local; cerrada técnicamente el 8 de octubre de 2026, iniciada el 7.** [Fase 11](FASE_11.md) conecta SQLite, modelo, binding y host MapLibre, sin crear una pantalla productiva. **325 casos distintos aprobados, 57 definiciones nuevas**, compilación debug/release, firma debug v2 y lint sin errores; [Validación](VALIDACION.md) separa pase, repeticiones, advertencias y conservación final. La oficial conserva **0.4.0a / código Android 19**; no hay publicación nueva.

Se comprobaron el mapa por zonas con marcadores sobre Liberty y GPS físico válido en Motorola/API36 después de un primer TIMEOUT: fuente real, aproximadamente 6.668 m de precisión, 46 ms de antigüedad, cercanía permitida y distancia rechazada, sin marca de simulación, coordenadas guardadas o reportes creados. Se acreditan únicamente dos hitos existentes: **4.3 captura del dispositivo en 2/4 y 4.5 mapa por zonas/marcadores en 3/4; 10/28 = 35.7%**, manteniendo los mismos hitos y pesos. La pantalla del compañero aún no existe en el repositorio; su integración y aceptación visual permanecen pendientes. Guardado real/consulta continúan ocultos. Los 18 APK anteriores y productos del teléfono 0.4.0/código 7 y emulador 0.3.1/código 8 se conservaron por hash y fechas; el recibo cierre-fase11.json acredita HEAD/índice y 63 archivos de base intactos.

Los bloques siguientes conservan los cortes anteriores con sus cifras y pendientes históricos.

**Antecedente: fase 10 — MapLibre Native + OpenFreeMap — Unreleased, local; cerrada técnicamente el 7 de octubre de 2026.** El usuario eligió el nuevo proveedor; se adapta el motor a MapLibre Native Android 13.6.1 y el estilo Liberty. [Fase 10](FASE_10.md) define el alcance y [Mapa con MapLibre](MAPA_MAPLIBRE.md) es la guía activa. **268 casos distintos aprobados y mapa real con nombres legibles comprobado**: 222 JVM app, 34 Android flow, ocho JVM monitor y cuatro Android mapas; 25 definiciones añadidas y siete de readiness que reemplazan nueve de Google. Inicializar el SDK no acredita renderizado; el pase nativo y el PNG con GPU host acreditan solo **SDK configurado**, dejando **8/28 = 28.6%, Mapas 2/4**. La oficial sigue siendo **0.4.0a / código Android 19**, sin otra versión o publicación nueva.

**Fase 9 completada técnicamente:** consulta por zona con filtros y datos mínimos para marcadores, **457 casos distintos ejecutados y aprobados, 43 definiciones nuevas**, sin fallos, errores, omisiones ni reutilizados; debug/release, firma y lint aprobados. [Fase 9](FASE_9.md) conserva su corte como antecedente, con aceptación visual pendiente. El cambio de proveedor conserva GPS, radio de 5 km, permisos, borrador, SQLite y consulta por zona; el compañero mantiene interfaz/login y accesos reales ocultos.

**Fase 8 completada técnicamente:** el coordinador comprueba la apertura antes de ejecutar acciones, limita callbacks a su vista y recupera una apertura retenida sin capturar GPS. Aprobó **226 ejecuciones, 27 definiciones nuevas**, compilación y lint sin errores con advertencias anteriores; su documentación quedó cerrada. [Fase 8](FASE_8.md) conserva ese antecedente. Interfaz/login del compañero, ejemplo ficticio y accesos ocultos a guardado real/consulta permanecen. **Avance del PDF: 7/28 = 25%**; la consulta geográfica nueva prepara el mapa por zonas y marcadores, cuyo renderizado e integración siguen pendientes.

**Validación 0.4.0a:** 266 pruebas distintas ejecutadas y aprobadas, 57 definiciones nuevas; 124 JVM de dominio/datos reutilizadas. Debug/release compilan, firma debug v2 válida, lint sin errores: app19 advertencias visuales, laboratorio2 por variante y datos0. [Validación](VALIDACION.md) documenta el pase y sus límites. La reapertura del mapa usa el mismo modelo/puente, exige renovación explícita e ignora capturas anteriores; el borrador no restaura autorización GPS ni guarda por sí solo.

Versión actual: **0.4.0a / código Android 19**, del **6 de octubre de 2026**, oficial por instrucción humana; integración local y validación completadas; el estado de publicación se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a).

Se completan dos partes técnicas: **fase 6A**, puente de ubicación por ciclo de Activity y clicks Maps con gestos visibles; **fase 7**, borrador editable que conserva los campos al volver del mapa y restaura estado guardado por Android. No solicitan GPS ni guardan automáticamente. La interfaz de `main` del compañero se incorpora conservando sus cambios; el ejemplo temporal visible mantiene sus controles. Guardado real y consulta siguen ocultos.

El pendiente **0.3.11** quedó cerrado: 247 pruebas ejecutadas, 16 definiciones nuevas, firma debug correcta y lint sin errores. Son antecedentes, no resultados nuevos de 0.4.0a. [Validación](VALIDACION.md) separa los cortes.

**Avance del PDF: 7/28 = 25%.** Las dos partes técnicas no acreditan el formulario completo, GPS físico, mapa con clave ni autenticación. Fase 6 continúa abierta; [fase 7](FASE_7.md) documenta el borrador y sus límites.

## Antecedente 0.3.10 — Unreleased

Preparación anterior: **0.3.10 / código Android 17 — Unreleased**, local, del **5 de octubre de 2026**. Continuó la **fase técnica 6**, relacionada con geolocalización y mapas del PDF. Se comprobaron juntos permiso, captura y confirmación con los componentes originales mediante `verification/location`, sin modificar la interfaz o login del compañero.

**Validación 0.3.10:** **21 casos Android distintos aprobados**, cero fallos, errores u omitidos: tres diálogos extendidos, 16 de composición del flujo y dos del proveedor/adaptador Android. Son **18 definiciones nuevas**; las **121 JVM de app quedaron `UP-TO-DATE`** y conservan la evidencia anterior. Debug/release compilan, firma debug v2 verificada y lint sin errores, con 13 advertencias previas de app y dos por variante técnica. [Validación](VALIDACION.md) separa las ejecuciones y los límites. La matriz usa lecturas y reloj controlados con permiso/ciclo Android reales; el registro GPS nativo se canceló antes de un callback, sin obtener una posición física.

Los cuatro paquetes técnicos `.precise`, `.approximate`, `.denied` y `.flow` están marcados `testOnly`, sin entrada en el menú, clave Cloud o Application productiva. El flujo de prueba requiere un gesto separado de captura, conserva el pin y exige renovar al volver de `onStop`. La fuente controlada está solo en el laboratorio y no llega al APK productivo. [Flujo Android](FLUJO_UBICACION_ANDROID.md) explica la conexión y [pruebas de permisos](PRUEBAS_PERMISOS_ANDROID.md) el aislamiento.

**Avance: 7/28 = 25%**. Controles del compañero, lectura GPS física y mapa con clave continúan pendientes. La composición comprobada en laboratorio no completa el flujo visual ni acredita aceptación del usuario. Guardado real y consulta siguen ocultos; no hay commit, subida, integración, etiqueta o publicación.

## Antecedente 0.3.9

La preparación 0.3.9 / código 16 aprobó tres diálogos nativos y 121 JVM ejecutadas de nuevo, 124 pruebas. Comprobó recreación con el diálogo abierto y conservó la app instalada. Sus resultados y APK siguen como antecedente; no se convierten en ejecuciones nuevas de 0.3.10.

## Antecedente 0.3.8

Preparación anterior: **0.3.8 / código Android 15 — Unreleased**, local, con corte del **5 de octubre de 2026**. El usuario indicó que todavía no tiene clave de Google y eligió continuar con ubicación y permisos. Se prepara `LocationPermissionViewModel` y el puente `PreciseLocationPermissionBinding`, sin modificar la interfaz o login del compañero. [Permisos de ubicación](PERMISOS_UBICACION.md) documenta el contrato, los estados y su conexión futura.

Se mantiene la **fase técnica 6**. La petición de permisos es por gesto explícito, con `COARSE` y `FINE` juntos; releer el resultado no inicia GPS ni confirma un punto. El puente se registra siempre antes de `STARTED` y dura toda la vida del `ActivityResultCaller`; no se recrea con `MapView`. El modelo conserva una solicitud al rotar y acepta callbacks restaurados sin lanzar otra. No se activa guardado real o consulta, no hay cambios a dominio/persistencia ni se instala el incremento sobre la app actual. [Validación](VALIDACION.md) distingue la comprobación de este paso de las ejecuciones históricas y del diálogo/GPS/mapa reales aún pendientes.

**Avance: 7/28 = 25%**. Preparar este contrato no completa el hito de permisos y estados GPS integrados. No se registra aceptación funcional, ausencia total de errores ni autorización para commit, subida, integración, etiqueta o publicación.

## Antecedente 0.3.7

La preparación **0.3.7 / código Android 14 — Unreleased** permanece local. El usuario pidió avanzar varias funciones y pulirlas sin publicar. Los incrementos 0.3.5 a 0.3.7 son agrupaciones del trabajo acumulado: fallos/limpieza de GPS, modelo compartido de selección y captura, y protección de SQLite con comprobación local de prerrequisitos del mapa. No se entregaron tres APK separados ni se creó commit, subida, integración, etiqueta o Release. [Incrementos](INCREMENTOS_035_037.md) detalla los cambios y su conexión con las pantallas del compañero.

Se mantiene la **fase técnica 6**, junto con refuerzo de persistencia de fase 3. `CitizenSecurityApplication.incidentLocationViewModelFactory(originalLocation)` y `IncidentLocationMapBinding` permiten que el compañero conecte su mapa al modelo cancelable. `MapsReadinessChecker` revisa clave presente y servicios locales, sin validar Google Cloud ni cargar el mapa. No se modifican interfaz/login ni se activa guardado real o consulta. La verificación integral aprobó pruebas JVM/instrumentadas, debug/release y lint; el recuento y los límites están en [Validación](VALIDACION.md).

**Avance comprobado: 7/28 = 25%**. Mejorar cancelación, calidad GPS y conservación de bases no acredita nuevos hitos de mapa real, permisos interactivos o captura GPS. No se registra aceptación funcional ni ausencia total de errores.

## Antecedentes de 0.3.4 y versiones previas

La preparación 0.3.4 incorpora ahora el **Maps SDK for Android 20.0.0** real, metadato de clave local y puente `GoogleMapIncidentBinding` hacia `IncidentMapSession`. Se declaran internet y ubicación aproximada/precisa; el permiso interactivo y el mapa visible siguen pendientes. La clave Google Cloud todavía no está configurada. Véase [Google Maps](GOOGLE_MAPS.md). Compilación debug, firma v2, lint (13 advertencias previas) y **41 JVM de app, incluidas 11 nuevas de selección**, aprobadas. No se probó carga de mapas real ni se publicó nada. **Avance comprobado: 7/28 = 25%**.

Los bloques previos de 0.3.4 sobre captura sin SDK ni permisos conservan la comprobación anterior a esta incorporación; sus resultados no prueban mapas reales.

El incremento local anterior **0.3.4**, código Android **11**, permaneció **Unreleased**. Continuó 0.3.3 / código 10 y 0.3.2 / código 9; conservó la numeración 0.3.x elegida en lugar de la futura 0.5.0, sin renumerar el historial. Reutilizó la preparación 0.3.1 / código 8: interfaz, splash, login visual, **admin / admin**, recepción, validación, coordenadas y el ejemplo ficticio de guardado en SQLite en memoria. El guardado real, toda consulta 0.4 y la selección de ubicación siguen sin acceso visual. El usuario confirmó **5 km alrededor del teléfono** para la [regla de proximidad](PROXIMIDAD_REPORTES.md); 0.3.4 preparó [obtener y renovar su evidencia](OBTENER_UBICACION.md), sin llamada automática ni controles nuevos.

La [Release v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0), código **7**, conserva su etiqueta en `08ab2d5`. El último `main` remoto observado fue el commit del compañero [`b665a56`, «cambiar interfaz»](https://github.com/Spidey123134/citizensecurity/commit/b665a56a9761ec5c083cabc638eba1674c7bf78b), comprobado mediante lectura remota al iniciar 0.3.2. La publicación anterior no tuvo instrucción humana; por indicación posterior se conserva, sin retirar ni revertir. Las preparaciones 0.3.1, 0.3.2, 0.3.3 y 0.3.4 siguen locales, sin commit, subida, integración, etiqueta o Release propia; todavía parten de `08ab2d5` y no incorporan el rediseño nuevo.

**Fase 3 / 0.3.0 y el pulido de fase 4 / 0.4.0 siguen en Unreleased**. Eso identifica trabajo pendiente de entrega: parte de su lógica ya está en fuente pública y APK anteriores. Ocultar sus accesos en la aplicación no vuelve privada esa fuente.

El [plan del proyecto](PLAN_DEL_PROYECTO.md) relaciona estas fases técnicas con el PDF original. La consulta local de fase 4 avanza sobre organización y lectura del apartado 4.4; las cuentas, los reportes propios por usuario y los cambios administrativos de estado siguen pendientes. 0.3.2, 0.3.3 y 0.3.4 pertenecen a la [fase técnica 6](FASE_6.md), sobre selección, cercanía y renovación de ubicación, relacionada con 4.3 y la preparación de 4.5. Aquellos cortes conservaron **7/28 hitos, 25%**; fase 10 añade solo SDK configurado y [Avance](AVANCE.md) registra **8/28 = 28.6%**. Preparar una fuente no acredita captura GPS física. El porcentaje no se calcula dividiendo fases ni versiones.

**Implementado**, **probado** y **revisado por el usuario** son estados distintos. La base de lógica y datos ya tiene código y pruebas; eso no significa que el usuario haya aceptado cada paso. Después de revisar la fase actual se elige el siguiente incremento.

## Estado y criterios de revisión

| Fase | Responsable | Implementación | Verificación disponible | Revisión del usuario | Criterio comprobable |
| --- | --- | --- | --- | --- | --- |
| 0. Preparar la base del proyecto | Ambos, para herramientas y contratos compartidos | Módulos y contratos preparados; splash del compañero completo en `e18e636` | Mantenimiento `0.2.1` compilado y comprobado: lint sin errores y 9 instrumentadas aprobadas. Véase [Validación](VALIDACION.md) | Sin aceptación registrada en estos documentos | El proyecto compila y el contrato permite conectar la interfaz sin depender de SQLite. |
| 1. Recibir los datos del reporte | Responsable de lógica y datos | `NewReport`, ejemplo de consola y recepción en las herramientas de 0.2.1 disponibles | Ejemplo ejecutado con valores predeterminados y personalizados; comprobado que no se incluye en el código productivo | Entregado; aceptación funcional no registrada | La consola muestra tipo, descripción, prioridad, fecha del incidente y ubicación escrita recibidos. Véase [Recibir reporte](RECIBIR_REPORTE.md). |
| 2. Validar el reporte y sus coordenadas | Responsable de lógica y datos | Validación publicada en `v0.2.0`, con acceso desde las herramientas oficiales de `0.2.1` | Ejemplos correctos y 9 instrumentadas aprobadas; resultado de 32 unitarias reutilizado mediante `UP-TO-DATE` | Mantenimiento autorizado; aceptación funcional pendiente | Un borrador válido pasa; texto Unicode no representable devuelve error de descripción o referencia y no se inserta. Véase [Validar reporte](VALIDAR_REPORTE.md). |
| 3. Guardar un reporte local | Responsable de lógica y datos | Lógica y tarea `guardarReporte` conservadas; entrega `0.3.0 — Unreleased`. Preparación 0.3.1 expone solo el ejemplo ficticio en memoria; guardado real oculto | Historial: suite de 10 instrumentadas y JSON de guardado correctos en 0.4. La comprobación del nuevo ejemplo se registra por separado en [Validación](VALIDACION.md) | El usuario eligió mostrar solo un ejemplo; revisión de su resultado pendiente | El ejemplo usa datos fijos y memoria, conserva resultado al rotar y no vuelve a insertar al repetir mientras siga abierta la herramienta. No usa el formulario ni escribe en la base productiva. Véase [Fase 3](FASE_3.md). |
| 4. Consultar y comprobar permanencia | Responsable de lógica y datos | `findById`, `list` y `ReportQueryViewModel` conservados; pulido `0.4.0 — Unreleased`, sin acceso visual en 0.3.1 | Historial: 8 JVM de consulta y suite de 10 instrumentadas aprobadas; JSON correcto. Las comprobaciones actuales se distinguen en [Validación](VALIDACION.md) | Se pidió terminar y pulir; no se autorizó su integración ni publicación anterior. Aceptación funcional pendiente | La consulta técnica conserva datos y estado tras reabrir y no modifica registros. Ningún botón de la preparación abre ese flujo. Véase [Fase 4](FASE_4.md). |
| 5. Conectar la interfaz del reporte | Compañero (`vazdavr-sudo`): formulario, consulta y login. Ambos: conexión al contrato | Puentes de guardado y consulta conservados; XML del compañero intacto. La conexión de guardado real y consulta queda pendiente de instrucción precisa | Historial 0.4: 8 JVM de guardado y 8 de consulta aprobadas; flujo visual de estas funciones pendiente | Acoplamiento técnico previo solicitado; activar estos flujos ahora no está autorizado | El compañero prepara sus pantallas usando los contratos, pero no conecta eventos que guarden datos reales o consulten hasta acordar su activación. |
| 6 / 6A. Elegir ubicación en el mapa | Compañero: mapa, pin, controles y conexión visual. Nuestra parte: selección, cercanía, renovación y puente técnico. Configuración compartida: ambos | SDK Google y puente incluidos en la oficial 0.4.0a como antecedente; fase 10 local adapta bindings a MapLibre. Fase 11 comprueba lectura física por separado; controles integrados y mapa productivo pendientes | El cierre 0.4.0a aprobó 266 ejecuciones distintas, 57 definiciones nuevas; 124 reutilizadas, compilación y lint sin errores. Sus casos compartidos se cuentan una sola vez en [Validación](VALIDACION.md), con lecturas controladas | Usuario autorizó la entrega 0.4.0a; aceptación funcional del mapa pendiente | Solo un gesto de captura con permiso preciso y una lectura aceptada permiten evaluar 5 km. Detener cancela y exige renovar; permiso y Ready no confirman ni guardan. Véase [Flujo Android](FLUJO_UBICACION_ANDROID.md). |
| 7. Borrador editable y regreso del mapa | Nuestra parte: modelo y recuperación. Compañero: formulario y controles | `ReportDraftViewModel` incluido en 0.4.0a; conserva campos y usa tokens de apertura | 29 JVM y dos Android nuevas incluidas en el cierre 0.4.0a; no se suman otra vez al total | Entrega oficial autorizada; formulario y aceptación visual pendientes | Cancelar conserva el borrador; confirmar aplica coordenadas sin borrar ediciones; recuperar campos no autoriza GPS o guardado. Véase [Fase 7](FASE_7.md). |
| 8. Coordinar borrador, ubicación y vista del mapa | Nuestra parte: contrato, ciclo de vida y pruebas. Compañero: conexión de sus controles | Unreleased local; `ReportLocationFlowBinding` y `ReportLocationMapBinding` implementados y comprobados técnicamente, sin cambiar pantallas | 226 ejecuciones aprobadas (192 JVM app y 34 Android), 27 nuevas; compilación y lint sin errores con advertencias anteriores. Resultados propios en [Validación](VALIDACION.md), separados de 0.4.0a | Siguiente fase solicitada; revisión funcional pendiente | Un evento de otra apertura o callback de un mapa sustituido no modifica la selección vigente. Recrear conserva la apertura retenida y exige renovar; confirmar aplica solo el punto aceptado al borrador. Véase [Fase 8](FASE_8.md). |
| 9. Consultar incidentes por zona | Nuestra parte: consulta geográfica, proyección y modelo. Compañero: futuro mapa y marcadores visibles | Unreleased local; `MapRegion`, `ReportMapQuery`, `ReportMapRepository` y `ReportMapViewModel` implementados y comprobados técnicamente, sobre SQLite compartida | 457 casos distintos ejecutados y aprobados; 43 definiciones nuevas (10 dominio, 14 SQLite, 19 modelo); cero reutilizados. Debug/release, firma y lint aprobados. [Validación](VALIDACION.md) conserva el corte sin sumar repeticiones | Continuación solicitada; aceptación visual pendiente | Una región con filtros devuelve como máximo el límite de marcadores y señala truncamiento; una consulta sustituida no publica resultados tardíos. Limpiar o sustituir desde `Loading` tampoco inicia la lectura anterior. No hay lectura al crear el modelo, escritura, migración ni acceso visible nuevo. Véase [Fase 9](FASE_9.md). |
| 10. MapLibre Native y OpenFreeMap | Nuestra parte: migración del motor, bindings y laboratorio. Compañero: mapa productivo y pines | Cierre técnico local Unreleased del 7 de octubre; `android-sdk-opengl:13.6.1`, estilo Liberty, inicialización sin clave, bindings `MapLibreMap` y wrapper deprecated conservado | 268 casos distintos aprobados; 25 definiciones añadidas y siete que reemplazan nueve de Google. Debug/release, firma debug y lint sin errores comprobados. Mapa real y nombres legibles con GPU host; repetición de cuatro nativos sin inflar el total. [Validación](VALIDACION.md) registra el recibo y las advertencias | Cambio de proveedor elegido por el usuario; aceptación del flujo productivo pendiente | SDK configurado acreditado; total 8/28 = 28.6%, Mapas 2/4. Mapa por zonas con marcadores, GPS físico e integración visual productiva pendientes. Limitación del renderizador software documentada, sin parche sin publicar. Véase [Fase 10](FASE_10.md). |
| 11. Incidentes por zona y marcadores | Nuestra parte: binding, host, ownership y comprobaciones. Compañero: futura pantalla y controles | Cierre técnico local Unreleased del 8 de octubre; circuito SQLite → modelo → binding → host → MapLibre, capas propias y sin consulta automática | 325 casos distintos aprobados (273 app, 34 flow, ocho monitor, diez mapas), 57 nuevos; debug/release, firma y lint sin errores. GPS físico VALIDATED en Motorola tras TIMEOUT, sin coordenadas persistidas o reportes. Conservación final del recibo en comprobación | Avance solicitado; pantalla y aceptación del flujo productivo pendientes | Gesto RESUMED, filtros/límite/hasMore, región y propietario vigentes antes de pintar; close/pause anteriores no afectan a su sucesor. Captura del dispositivo y mapa por zonas acreditados: 10/28 = 35.7%, 4.3 en 2/4 y 4.5 en 3/4. Accesos reales ocultos. Véase [Fase 11](FASE_11.md). |

Las pruebas instrumentadas forman una suite compartida para guardado y consulta, no una cantidad nueva por fase. Los resultados del avance `0.3.0` y de la entrega oficial `0.2.1` se conservan como historial; los de la entrega `0.4.0` se registran por separado. La captura física se comprobó por separado en fase 11; la verificación del flujo productivo en teléfono y API 26 sigue pendiente. Las demostraciones están en [Fase 3](FASE_3.md) y [Fase 4](FASE_4.md), y la evidencia por versión en [Validación](VALIDACION.md).

## Reparto y siguiente paso

El paso local activo es la **fase 11 — Unreleased, cerrada técnicamente**, posterior a fase 10 y a la oficial **0.4.0a**. Une la consulta geográfica al host de marcadores, conserva propiedad por vista y comprueba el circuito real del SDK; la captura física se verifica en un laboratorio independiente. [Mapa con MapLibre](MAPA_MAPLIBRE.md), [Acoplar interfaz](ACOPLAR_INTERFAZ.md) y [Fase 11](FASE_11.md) describen su conexión. Fases 10 (268/25 más siete sustituciones), 9 (457/43) y 8 (226/27) conservan sus cortes. El siguiente alcance por acordar es la pantalla del compañero, todavía ausente del repositorio, y su integración visual; los accesos reales permanecen ocultos mientras no se acuerde su activación.

El hito de SDK configurado quedó acreditado por carga/renderizado nativos y PNG real con nombres legibles; la inicialización local por sí sola sigue sin acreditarlo. Faltan el mapa productivo por zonas con marcadores, los controles del compañero y GPS físico con sus estados y confirmación. MapLibre/OpenFreeMap no requiere la clave Google del proveedor anterior. La consulta se conectará al gesto o final del movimiento de cámara conservando la identidad de su región. Interfaz y login continúan con el compañero; guardado real/consulta siguen ocultos. Las [mejoras futuras](MEJORAS_FUTURAS.md) conservan su condición de propuestas.

### Antecedentes de verificación y reparto

**0.3.4 Unreleased** continúa la fase 6 con `PhoneLocationRefresh.refresh()`: permiso preciso, GPS habilitado, una solicitud a la vez, espera técnica configurable de 15 segundos y validación de la primera lectura. Si esa lectura es imprecisa, antigua o marcada como simulada, devuelve `Rejected` y requiere otro intento explícito; no espera hasta mejorar la precisión. Su propiedad compartida en la aplicación no inicia captura automáticamente. La futura pantalla debe cancelar el trabajo al salir o en `onStop`. Los estados y la conexión prevista están en [Obtener ubicación](OBTENER_UBICACION.md). El guardado real y la consulta siguen ocultos. Las [mejoras futuras](MEJORAS_FUTURAS.md) quedan como ideas para otra revisión, sin implementación ni versión oficial comprometida.

El cierre local 0.3.4 aprobó **185 pruebas diferentes**: **86 de dominio, 30 de app, 20 JVM de datos y 49 instrumentadas API 37**. La ejecución completa aprobó en 20 segundos; después de retirar un uso obsoleto en una prueba, se repitieron las mismas 49 instrumentadas y lint de datos en 10 segundos, sin contarlas como casos nuevos. Se corrigió una carrera que podía conservar evidencia al agotarse el tiempo durante su publicación; su regresión determinista aprobó. Lint de datos quedó sin incidencias y app sin errores, con las 13 advertencias visuales previas; compilación debug aprobó y se verificaron la firma v2 y los metadatos del APK **0.3.4 / código 11**. Sus 43 recursos coinciden con 0.3.3. [Validación](VALIDACION.md) registra ambas ejecuciones.

La fuente Android se probó mediante backend controlado y el camino nativo de permiso denegado. No se obtuvo GPS real, no se probó API 26–29 ni Google Maps y no se modificaron interfaz, login o permisos del manifiesto. El APK no se instaló sobre la app del usuario, que conserva **0.3.1 / código 8**. La captura real y su flujo visual siguen pendientes; **Avance permanece en 7/28 = 25%** y no se registra aceptación funcional.

El cierre local de **0.3.3** comprobó **148 pruebas diferentes**: 86 de dominio, 30 de app y 32 instrumentadas API 37. Compilación y lint debug aprobaron sin errores, con las 13 advertencias visuales previas. La regla de 5 km se verifica al confirmar el punto y antes de una escritura productiva; las pruebas comprobaron rechazo sin insertar y rollback por evidencia caducada o marcada mock. [Validación](VALIDACION.md) separa las dos ejecuciones. GPS, permisos y mapa integrados siguen pendientes y no se registra aceptación funcional.

El nuevo commit remoto cambia los layouts de login y herramientas, añade un layout de crear cuenta y una configuración de Daemon JVM 25; no cambia Kotlin, contratos de dominio ni SQLite. El rediseño de herramientas todavía no contiene `tools_save_example` ni `tools_example_result`, que usa la base local. Acoplarlo requiere conservar esos controles y revisar la configuración del JDK; copiar el XML directamente no completa la conexión. Se registra como trabajo de coordinación pendiente, sin modificar las pantallas del compañero ni mezclar el historial en este incremento.

Las interfaces, el menú, la navegación y el login corresponden al compañero (`vazdavr-sudo`). El acceso local de demostración `admin / admin` fue solicitado por el usuario. La autenticación real sigue pendiente y se acordará antes de conectarla a servicios o cuentas. Los contratos y la composición compartida de dependencias se coordinan entre ambos, según [Trabajo del equipo](TRABAJO_EQUIPO.md).

El puente técnico previo de [Acoplar interfaz](ACOPLAR_INTERFAZ.md) y el de consulta de [Fase 4](FASE_4.md) se conservan. El compañero puede preparar su formulario y controles; la indicación actual exige dejar ocultos el guardado real y la consulta. No debe conectar sus eventos ni activarlos por iniciativa propia. El ejemplo ficticio es el único acceso nuevo solicitado. La integración visual de Google Maps sigue pendiente. El nuevo `IncidentLocationViewModel.confirm()` usa la comprobación de cercanía; solo el `IncidentLocationSelection.confirm()` geométrico anterior omite esa regla. Una escritura productiva necesita además la lectura real independiente del pin y el repositorio protegido de `CitizenSecurityApplication`. Sin evidencia válida no se permite guardar.

El splash y el login visual del compañero se conservan. La entrega oficial `0.2.1` aprobó 14 JVM de app y 9 instrumentadas SQLite; sus 32 de dominio conservaron el resultado mediante `UP-TO-DATE`. [Funciones visibles](FUNCIONES_PUBLICADAS.md) describe recepción, validación, coordenadas y el nuevo ejemplo ficticio en memoria. Las comprobaciones históricas del botón y de admin / admin no sustituyen la validación actual del ejemplo; [Validación](VALIDACION.md) distingue sus resultados.

Como historial, la comprobación técnica de `0.4.0` aprobó 24 JVM de app (8 de guardado, 8 de consulta y 8 de herramientas), 32 de dominio ejecutadas de nuevo y 10 instrumentadas en API 37; los JSON fueron correctos. Lint y compilación debug/release terminaron sin errores, con 13 advertencias visuales previas. Las dos regresiones de cancelación JVM fallaron antes de la corrección y aprobaron después. Esas pruebas no autorizaron integración, publicación ni aceptación funcional. La consulta visual y su rotación real siguen pendientes.

La preparación local anterior **0.3.1** ejecutó y aprobó **30 JVM de app y 15 instrumentadas API 37: 45 pruebas**. Las 32 de dominio conservaron su resultado anterior mediante `UP-TO-DATE`. Los JSON de guardado y consulta fueron correctos. Una nueva regresión reprodujo una inserción después de cancelar durante la obtención de la fecha y aprobó al comprobar la cancelación antes de iniciar y confirmar la transacción. Compilación, lint sin errores y comprobación real en el emulador del ejemplo aprobaron; no cambió el listado ni los hashes de las bases productivas. [Validación](VALIDACION.md) registra la evidencia completa.

El primer paso de **0.3.2** aprobó **47 de dominio, 30 de app y 15 instrumentadas SQLite en API 37: 92 pruebas ejecutadas**, con cero fallos, errores u omisiones. Lint debug y compilación debug aprobaron; app conserva las 13 advertencias anteriores. El ejemplo de selección en consola confirmó el punto válido, conservó la ubicación original al cancelar y rechazó el punto inválido posterior. Son comprobaciones distintas de las históricas de 0.3.1 y no se presentan como resultados de 0.3.3. [Avance](AVANCE.md) conserva **7/28 hitos, 25% estimado del plan funcional**, con mapa real y revisión del usuario pendientes.

**0.3.3** prepara la regla solicitada contra reportes lejanos: radio de 5 km, precisión máxima de 100 m y lectura de hasta 2 minutos, sin marca de simulación. Se comprueba al confirmar y en la escritura productiva; no se utiliza una lectura ficticia por defecto. El filtro es local y no acredita la veracidad del incidente ni evita toda manipulación del dispositivo. [Proximidad](PROXIMIDAD_REPORTES.md) registra el contrato y [Validación](VALIDACION.md) sus pruebas nuevas. Captura GPS, permisos, SDK, pantalla y login real siguen pendientes.

Terminar, probar o pulir una versión no autoriza crear commits, subir, integrar, etiquetar ni publicar. Esas acciones y los borrados requieren una instrucción humana precisa. La preparación local actual no modifica el historial publicado.

## Alcance pendiente de acuerdo

El motor activo usa MapLibre Native/OpenFreeMap por elección del usuario. `MapsReadinessChecker` comprueba inicialización local; carga del estilo y renderizado se acreditan por separado en el laboratorio nativo. La guía Google y sus requisitos de Cloud/clave/facturación se conservan como antecedentes. Fase 11 comprobó técnicamente marcadores por zona y captura GPS física por separado. La pantalla del compañero, su diseño y controles y el flujo GPS integrado siguen pendientes. Fotografías, sincronización, servidor, administración y notificaciones necesitan su propio acuerdo de alcance. El guardado actual es local. El proyecto anterior y su respaldo se conservan como referencia.
