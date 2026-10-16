# Changelog

## [Unreleased]

### Fase 42 — desarrollo local validado

- El recibo de cambio confirmado incorpora **Ver actividad de esta cuenta** y **Revisar avisos de cambios** mediante gestos independientes. Las rutas conservan el workspace/sesión y no llevan datos privados del reporte en extras, reconocen la solicitud, revisan eventos o activan avisos automáticamente.
- Recorrido integrado entre cambio autorizado real, evento SQLite, revisión manual y resumen general de Android. Se conservan preferencia por cuenta, permiso/canal, registro por UUID de operación después de la aceptación y deduplicación; desactivar conserva los IDs aceptados.
- Lecturas de avisos con `.new` o `.bak` pendientes bloqueadas sin restaurar respaldos, limpiar residuos o reemplazar el registro. Archivos, preferencias e IDs conservados en las pruebas nativas de almacenamiento.
- **28/28 = 100% de construcción funcional local**, con Notificaciones **4/4** y cero criterios funcionales locales pendientes. Aceptación física completa, entrega y seguridad de producción conservan su alcance separado.
- **1431 casos distintos aprobados/14 nuevos**, cero fallos/errores/omitidos finales: 1379 JVM —205 recibos intactos de dominio reutilizados, 148 datos y 1026 app ejecutados— y 52 Android ejecutados. Lint app sin errores/22 avisos existentes por variante; datos sin incidencias. No se suman las ejecuciones de cortes anteriores.
- Trabajo local sin actualizar GitHub/main o PR #30, publicar APK/tag/Release o cambiar la descarga oficial 0.6.1. [Fase 42](docs/FASE_42.md) · [Guía](docs/ADMINISTRADOR_LOCAL.md) · [Validación](docs/VALIDACION.md).

### Fase 41 — desarrollo local validado

- Cambio de estado desde un folio administrativo: consulta de revisión real, motivo válido de 5–500 caracteres, revisión explícita y segundo gesto de confirmación. Avance consecutivo Reportado → En revisión → Atendido → Cerrado; sin saltos ni cambios desde Cerrado.
- Recibo con folio, UUID del actor, fecha/zona, motivo, secuencia y operación; evento permanente en el historial local. Atendido/Cerrado no acreditan intervención de una autoridad.
- Comprobación de pendientes antes de preparar otra solicitud; recuperación por lectura y reintento explícito del cuerpo completo con el mismo UUID. Reconocer el resultado verifica solicitud/evento exactos; cerrar un rechazo definitivo vuelve a buscar el recibo y muestra Confirmado si apareció uno coincidente.
- Relectura coherente de estado/secuencia al revisar y confirmar; un cambio concurrente exige revisar otra vez. Permisos en IO y antes del commit, sesión capturada y ventana activa retenida al girar. Revocación retira datos; cancelar no autoriza otra solicitud y la incertidumbre conserva la identidad.
- Diario con rechazo conservador de `.new` y `.bak` pendientes: leer no restaura respaldos, reemplaza archivos ni limpia residuos desconocidos. La cancelación tras un lector no cooperativo se comprueba antes de continuar otras lecturas.
- **25/28 = 89.3%**, Consulta y Administración **4/4**; restan tres criterios de Notificaciones. **1488 casos distintos aprobados/103 nuevos**, cero fallos/errores/omitidos finales: 1379 JVM y 109 Android. Lint app sin errores/22 avisos existentes por variante; datos sin incidencias. [Fase 41](docs/FASE_41.md) · [Guía](docs/ADMINISTRADOR_LOCAL.md) · [Validación](docs/VALIDACION.md).
- Corte local sin push, cambios a main, actualización del [PR #30 de fase 40](https://github.com/Spidey123134/citizensecurity/pull/30), APK, tag o Release. Oficial 0.6.1 y candidato anterior archivado conservados.

### Antecedente — fase 40

- Fase 40: cuenta elegida `admin_local`, registrada normalmente con contraseña propia de 15 a 128 caracteres, sin credenciales predeterminadas o publicadas. Alta local por confirmación nativa del propietario y segundo gesto, con plazo de 120 segundos, UUID y recibo exacto; sin reemplazar asignaciones o reparar corrupción. `admin/admin` sigue académico.
- Consulta administrativa visible del espacio de esa cuenta: lista y filtros de tipo/estado/prioridad, folio completo, detalle e historial de 50 por página. Sesión y permiso comprobados al leer; revocación limpia resultados y los gestos/respuestas anteriores no actúan sobre otra consulta. Cambio de estado e historial administrativo completo pendientes para la fase siguiente. [Guía](docs/ADMINISTRADOR_LOCAL.md) · [Fase 40](docs/FASE_40.md).
- Fase 40 validada: **1360 casos distintos aprobados/76 nuevos**, sin fallos; 1304 JVM y 56 Android. Confirmación nativa con PIN real en emulador API 37 y consulta del espacio de la propia cuenta. **22/28 = 78.6%**, Administración **2/4** por consulta y roles/permisos; quedan seis criterios de seguimiento, cambio/historial administrativo y notificaciones. [Validación](docs/VALIDACION.md).
- Código y guía preparados para la rama `development/administracion-local` y un PR en borrador revisable en GitHub, sin integrar main ni publicar APK, tag o Release; la subida todavía debe confirmarse. La APK oficial sigue en 0.6.1; el candidato 0.6.2 archivado es anterior a fases 38–40 y deberá prepararse otra vez.

### Antecedentes de este desarrollo

- Fase 39, trabajo local: revisión del borrador en tarjetas de datos, lugar y fotografía; marco fijo de 240 dp con imagen completa ajustada y dibujo contenido. Evita que una foto vertical invada otros bloques, sin alterar el archivo, el guardado o el GPS.
- Vinculación administrativa al espacio y sesión capturados, con revalidación antes/después de la política y revocación al salir, incluso si se vuelve a entrar con la misma cuenta. `READ_REPORTS` y `CHANGE_STATUS` siguen separados y denegados por defecto; acceso académico sin permisos administrativos. [Fase 39](docs/FASE_39.md). **20/28 = 71.4% permanece**; sin push, tag, Release o APK publicada. El candidato archivado precede a las fases 38/39.
- Fase 39 validada: 1265 casos distintos aprobados/25 nuevos; 907 JVM app ejecutados, 341 recibos intactos reutilizados y 17 Android. Foto vertical real del selector, comparación visual y repetición con letra al 130% sin solape. Cero fallos/errores/omitidos finales; lint sin errores y con 22 avisos existentes por variante.
- Fase 38, trabajo local: política administrativa explícita con capacidades independientes `READ_REPORTS` y `CHANGE_STATUS`, denegadas por defecto. Lectura administrativa por folio/lista paginada/historial sobre fuentes capturadas, con apertura diferida hasta autorizar y controles de vigencia/cancelación antes y después de consultar.
- El escritor y la recuperación de recibos requieren `CHANGE_STATUS`; el permiso se vuelve a comprobar dentro de la transacción antes del commit. Consultar no concede escritura, y tener permiso de cambio no concede consulta administrativa general. Asignación y panel pendientes; `admin/admin` sigue académico. [Fase 38](docs/FASE_38.md): 1267 casos distintos aprobados/25 nuevos, sin fallos/errores/omitidos; **20/28 = 71.4% permanece**.
- Publicación pospuesta por el usuario: candidato y notas 0.6.2/código 30 conservados localmente, con numeración tentativa para la próxima entrega. Este avance no realiza push, tag, Release ni publicación de APK; la entrega oficial **0.6.1** mantiene su archivo.
- Fase 37 / E06: preparar un mensaje desde un folio, elegir contacto del mismo acceso, revisar destinatario/texto y abrir Mensajes SMS mediante un segundo gesto. Referencia/punto inicialmente omitidos; inclusión voluntaria con coordenadas y enlace al mapa. La ficha PDF, con foto elegida, conserva su recorrido separado.
- Relectura del reporte y contactos al preparar/abrir, rechazo de cambios posteriores a la revisión y una sola apertura por gesto vigente. Guardar un contacto desde Ayuda y regresar actualiza la elección sin seleccionar o preparar automáticamente.
- Composición sin permisos SMS/agenda/GPS, archivos adjuntos o envío automático. Errores, corrupción, giro, cambio de cuenta y cancelación conservan los datos y requieren una decisión nueva. Abrir una app no acredita envío; el operador puede cobrarlo.
- Fase 37 validada: 1231 casos distintos/37 nuevos, sin fallos/errores/omitidos; aperturas SMS interceptadas, sin mensajes enviados. Lint sin errores; 20/28 = 71.4% permanece y la APK oficial 0.6.1 conserva su corte.
- Fase 36: avisos locales optativos desde Actividad, preferencia por cuenta y permiso de Android solicitado únicamente al activar. Apertura sin diálogo, consulta manual de cambios, estado del canal y acceso a Ajustes.
- Resumen silencioso con texto general y entrada normal inmutable, sin folios, ubicación, fotografías o cuenta en su contenido. Solo STATUS_CHANGED confirmados; guardar un reporte no genera un aviso de cambio.
- Registro privado por dueño con CAS/AtomicFile y límite de 4096 operaciones; revisión en páginas de 50, hasta 500 eventos por gesto y continuación explícita. No se recortan recibos ni se sobrescribe corrupción con vacío. Desactivar conserva el registro y cancela el resumen propio.
- Conflictos o cancelaciones después de publicar retiran únicamente su intento: una respuesta antigua no puede cancelar un resumen más reciente. Sin entrega en segundo plano, cambios administrativos habilitados ni otra APK/Release.
- Fase 36 validada: 1223 casos distintos, 87 nuevos, cero fallos/errores/omitidos finales. Lint app sin errores/22 avisos por variante y datos sin incidencias. Avance 20/28 = 71.4% por permisos/preferencias, conservando los pendientes del recorrido autorizado.
- Fase 35: **Actividad de reportes** desde Inicio, con creaciones y cambios reales del espacio vigente, filtros Todos/Cambios y acceso al folio. Páginas de 50 eventos, navegación anterior/siguiente, actualización desde el inicio y reintento exacto.
- Consulta global en SQLite con filtro antes del límite y cursor exclusivo por segundos/nanosegundos/operación. Índice aditivo de esquema 4; conservación de reportes, fotos e historial y recibos compatibles con 3/4 sin migrar al recuperar.
- Resultados y navegación vinculados a su sesión, página y solicitud. Corregido el desplazamiento al actualizar una consulta con valores iguales: los eventos nuevos aparecen desde arriba aunque Loading/Ready se resuelvan rápidamente.
- No se inventan eventos para reportes antiguos; guardar y cambiar estado son eventos distintos. La fase 35 aporta la fuente que consume la fase 36; asignación administrativa pendiente.

[Fase 35](docs/FASE_35.md) · [Fase 36](docs/FASE_36.md) · [Fase 37](docs/FASE_37.md) · [Avance](docs/AVANCE.md) · [Validación](docs/VALIDACION.md). Código Unreleased; la APK oficial 0.6.1 conserva su entrega anterior.

## [0.6.1] — 2026-10-14

- Entrega solicitada expresamente: **fases 29–34 agrupadas**, APK 0.6.1 / código Android 29, mismo paquete y firma académica. Conserva esquemas, datos e interfaz de login/loading; APK y assets anteriores intactos.
- **Cámara y galería:** captura completa con URI temporal propia, importación/normalización privada, selección vinculada al borrador y origen, vista previa antes de guardar. Cancelación/fallo conserva foto anterior y campos; giro sin relanzar, sesión revocada y callbacks antiguos descartados. Limpieza/revocación únicamente de su temporal, sin nuevos permisos CAMERA/almacenamiento.
- **Ficha y contactos:** PDF paginado desde un folio guardado, inclusión de ubicación/foto revisada y compartir separado; hasta cinco contactos manuales por cuenta y accesos al marcador. Un parcial no queda listo, no se lee la agenda ni se llama/envía automáticamente.
- **Ayuda inmediata y fase 34:** ruta pública sin login/GPS, atajo del icono, solicitud de acceso directo y mosaico voluntario de Ajustes rápidos. Alta mediante diálogo del sistema desde API 33 o edición manual; toque/desbloqueo abre ayuda, con PendingIntent desde API 34 y ruta Intent protegida por versión en API 26–33. Pedir el alta no se anuncia como confirmación.
- **Recuperación durable:** conserva el mismo intento/UUID de cambio de estado y verifica su recibo antes de reconocer/limpiar. Asignación y panel administrativos permanecen pendientes; `admin/admin` no concede roles.
- **1237 casos distintos aprobados, 307 definiciones nuevas frente a 0.6.0**, cero fallos/errores/omitidos finales: JVM 169 dominio + 111 datos + 794 app; Android 65 datos + 98 app. Fase 34 aporta 33 nuevos (28 JVM + cinco Android). Lint app cero errores/22 avisos por variante y datos sin incidencias.
- APK **55.217.523 bytes**, SHA-256 `7395a049aee8cf92324ac31f3c4e08453fb639bcf5103fc8eaa48484302815f1`; paquete, 0.6.1/código 29, min26/target37 y certificado académico verificados. Pruebas de cámara con protocolo controlado; sin nueva aceptación física, muerte completa del proceso, instalación como actualización o ejecución física API 26.
- **19/28 = 67.9% permanece**: las ampliaciones no completan seguimiento autorizado, administración o notificaciones.

[Release y APK](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.1) · [Entrega](docs/ENTREGA_0_6_1.md) · [Probar 0.6.1](docs/PROBAR_0_6_1.md) · [Validación](docs/VALIDACION.md).

### Antecedente de preparación de 0.6.1 — fases 29–33

Las marcas Unreleased y cifras siguientes conservan los cortes originales de desarrollo; su código se incluye en 0.6.1 junto a fase 34. Los recuentos no se suman otra vez al cierre agrupado, y la ausencia de APK descrita aquí corresponde a esos antecedentes.

- Fase 33: tomar una foto con la cámara junto a la galería. Captura completa en URI temporal propia, normalización privada existente y revisión antes del guardado; cancelación/fallo conserva foto anterior y campos. Sin nuevo permiso de cámara/almacenamiento.
- Validación fase 33: **794 casos distintos aprobados, 71 nuevos** (766 JVM + 28 Android API 37); galería real y captura mediante protocolo controlado, sin cámara física. Cero fallos/errores/omitidos finales; lint cero errores/22 avisos por variante. Detalle en [Validación](docs/VALIDACION.md).
- Aperturas ligadas a su propio origen/token y bloqueo de doble gesto. Giro conserva la captura sin volver a abrirla; un callback de otro proceso o sesión no adjunta una imagen. Limpieza/revocación del temporal exacto incluso al cerrar antes del job de resultado; los controles esperan a que termine la limpieza. [Alcance](docs/FASE_33.md).

- 14 de octubre de 2026: catálogo [E01–E09 de mejoras útiles](docs/MEJORAS_UTILES.md), con origen de las ideas, prioridad, estado y criterios de aceptación. Se separan las ampliaciones del usuario de los siete requisitos/28 criterios del PDF; se conserva 19/28 = 67.9%.
- Fase 31 implementada/validada: solicitud durable de cambio de estado por dueño/actor, diario privado acotado, CAS/exclusión compartida y recuperación explícita mediante consulta de recibo de solo lectura. Reconocer vuelve a verificar SQLite antes de limpiar exactamente el pendiente; permiso denegado por defecto y actor comprobado antes del commit. Sin asignación o controles administrativos productivos.
- Fase 32 implementada/validada: Ayuda inmediata pública y sin datos privados, atajo estático del icono y solicitud explícita de fijado en Inicio si el launcher lo admite. Petición fuera del hilo de interfaz; abrir el marcador exige pantalla visible y dispositivo desbloqueado. Las pruebas API 37 no realizaron llamadas ni aprobaron un icono fijado real. La aceptación de la solicitud de fijado no se presenta como icono confirmado; SOS/botones físicos conservados.
- Roadmap/matriz actualizados: cuentas locales, guardado/fotos, GPS/mapa, consulta propia e historial de lectura ya construidos. La comprobación Android de 0.6.0 está ejecutada; estados anteriores de ocultación/falta de cuentas quedan identificados como históricos. Asignación administrativa, seguimiento autorizado y notificaciones pendientes.
- Mosaico de Ajustes rápidos, botones/gestos por fabricante, mensajes revisados, múltiples fotos, institución receptora y datos verificados por zona documentados con sus límites; el mosaico y los servicios externos no se implementan en este corte. Resultados ejecutados de fases 31/32 en [Validación](docs/VALIDACION.md).
- Validación fases 31/32: **1062 casos distintos aprobados, 112 nuevos**, cero fallos/errores/omitidos finales. JVM 997 (169 dominio, 111 datos, 717 app); Android API 37 65 (53 datos, 12 app). Fijado revalida la vigencia después de la última consulta Keyguard; sus cuatro pruebas públicas volvieron a pasar sobre el guard final. Lint app sin errores/22 avisos por variante y datos sin incidencias. Sin llamadas, icono fijado real o ejecución API 26; repeticiones no duplicadas.

- Validación fases 29/30 — 13 de octubre de 2026, corte anterior: 91 casos nuevos aprobados, 1000 distintos ejecutados; PDF revisado en todas sus páginas y comprobación de campos omitidos. Corregida propagación de fallos de escritura que el puente PDF nativo podía consumir; un parcial no queda listo para compartir. Lint sin errores. Detalle en [Validación](docs/VALIDACION.md).

- Fase 29: ficha PDF desde un reporte guardado, con folio, descripción, tipo, prioridad indicada, estado local y fechas/zona. Revisión de ubicación y fotografía opcionales antes de preparar; compartir requiere otro gesto y abre el selector Android.
- PDF paginado con fotografía normalizada opcional, comprobación de integridad y copia de caché compartida solo para lectura. Un fallo de preparación conserva el reporte; no se obtiene GPS nuevo ni se envía automáticamente.
- Fase 30: Ayuda y contactos desde Inicio. Accesos al marcador de 911/089 para México y hasta cinco contactos ingresados manualmente, con guardado privado por cuenta, espacio académico separado, eliminación confirmada y recuperación por lectura ante un cambio incierto.
- Los gestos comprueban sesión y resultado vigente; no se lee la agenda, no se llama automáticamente y no se solicitan permisos de llamadas, SMS o ubicación para estas funciones.
- El alcance sigue siendo académico: la ficha organiza datos para entregarlos voluntariamente, sin afirmar recepción policial, verificación del incidente o autenticidad forense. Administración y asignación de permisos siguen pendientes. El plan original conserva 19/28 = 67.9%.
- Código de desarrollo sin nueva versión, tag, Release o APK publicada. Android permanece en 0.6.0 / código 28; la APK oficial 0.6.0 corresponde al corte anterior. Alcance y comprobaciones en [Fase 29](docs/FASE_29.md), [Fase 30](docs/FASE_30.md) y [Validación](docs/VALIDACION.md).

## [0.6.0] — 2026-10-13

- Agrupa fases 21–27 y una base pequeña de fase 28, con APK académica y código Android 28; conserva datos, firma y login/loading del compañero.
- Cuentas locales con registro/verificación y espacio propio por UUID para reportes, consultas, fotos, borrador y solicitudes. El modo académico conserva los históricos.
- Lista paginada con memoria acotada, recuperación de criterios/posición y reinicio explícito. Fechas del incidente y prioridad en mapa, lista de la misma muestra y comprobación de cámara antes de abrirla.
- Historial real del folio consultable por gesto, creación atómica del evento y migración aditiva de reportes a esquema 3 sin inventar cambios anteriores. Cuentas mantienen esquema 1.
- Fase 28: comando de estado retenido durante la vida del modelo, recibo comprobado y reintento explícito con la misma identidad ante incertidumbre/cancelación. No hay diario durable ni composición administrativa; asignación pendiente y escritura denegada por defecto.
- Avance permanece en 19/28 = 67.9%; publicar o preparar el servicio no completa permisos, seguimiento administrativo o notificaciones. El cierre agrupado se registra en [Validación](docs/VALIDACION.md).
- 1164 casos distintos aprobados: 159 dominio JVM, 88 datos JVM, 646 app JVM, 207 datos Android y 64 app Android. 14 nuevos en este incremento; repeticiones e incorporaciones previas no se duplican. Los 15 Android de fase 27 ya se ejecutaron.
- Debug/release/androidTest ensamblados, firma v2/certificado académico conservados, sin fixtures en el producto; lint cero errores y 22 advertencias por variante de app, datos sin incidencias.

[Release y APK](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.0) · [Entrega](docs/ENTREGA_0_6_0.md) · [Probar 0.6.0](docs/PROBAR_0_6_0.md).

## Antecedente de preparación de 0.6.0 — fases 21–27

Los cortes Unreleased siguientes se agrupan en 0.6.0. Sus recuentos y pruebas solo compiladas describen aquella preparación; el cierre agrupado no los suma de nuevo.

- Fase 27: Historial de estados visible desde cada folio por consulta explícita, con actualización, eventos anteriores, cancelación y reintento de la página fallida. La ficha permanece disponible si falla solo el historial.
- Los reportes nuevos y su evento real de creación se guardan en una misma transacción. Migración aditiva de reportes a esquema 3; conserva filas, fotos y estados anteriores sin inventar eventos ni asignarles un autor. Cuentas mantienen esquema 1.
- Servicio de cambios consecutivos Reportado → En revisión → Atendido → Cerrado, con estado/secuencia esperados, motivo, actor autorizado y operationId idempotente. Escritura denegada por defecto antes de abrir la base; sin controles de escritura ni asignación administrativa. El usuario eligió dejar esa asignación pendiente; admin/admin sigue académico.
- Historial acotado a una página de 50 eventos, ordenada por secuencia descendente con cursor exclusivo; autorización de lectura por sesión antes/después, cancelación/generaciones y gestos vinculados al folio vigente.
- 100 casos distintos aprobados, 37 nuevos; 15 Android nuevos compilados sin ejecutar. 19/28 = 67.9% permanece: no se acredita todavía seguimiento administrativo ni sus permisos.
- Fase 26: reportes propios en guardado, folio, páginas, mapa y posibles repetidos. Base, diarios, checkpoint y fotos por UUID; históricos conservados en modo académico sin reasignarlos.
- Contexto fijo y GPS propio por pantalla; comprobación de sesión antes/después. Conserva esquema 2, integridad de fotos, CAS y createOnce; escritura incierta permanece con su dueño.
- Sellos de dueño/proceso/revisión antes de restaurar UI y al navegar. Retira pantallas viejas sin interrumpir la navegación nueva; cierre antiguo no termina otra cuenta. No restaura borrador de cuenta como académico ni renderiza sobre vistas retiradas.
- 73 casos aprobados, 59 nuevos; 4 Android nuevos compilados, aceptación pendiente. 19/28 = 67.9% de construcción local.
- Fase 25: Crear cuenta registra usuarios locales y vuelve al login; Iniciar sesión comprueba su contraseña y abre Inicio con identidad local. Cerrar sesión/volver limpia la sesión en memoria, conservando datos y cuentas. Se reutilizan los layouts del compañero.
- Base de cuentas independiente, usuario único normalizado, admin reservado y contraseña de 15 a 128 caracteres Unicode conservada exactamente. Verificador PBKDF2-HMAC-SHA256 con sal aleatoria; sin contraseñas en texto plano o estado restaurable. Registro transaccional sin reemplazar usuarios existentes.
- Registro/ingreso trabajan fuera de Main, bloquean dobles gestos y descartan respuestas tras cancelación. Un registro incierto pide comprobar el ingreso antes de repetir. El acceso académico permanece sin roles y limpia cualquier identidad real previa.
- Las cuentas son exclusivamente del teléfono. Fase 25 construye registro/verificación; fase 26 completa separación local de reportes. Aceptación Android pendiente para la entrega agrupada.
- Fase 24: recuperación de criterios, cursor, página y navegación si Android restaura el estado después de cerrar el proceso. Se guardan metadatos acotados; los resultados se vuelven a consultar. La restauración no recupera evidencia GPS ni inicia una lectura desde el constructor.
- Los checkpoints incompatibles, corruptos o incoherentes se rechazan completos. La representación versionada conserva nanosegundos, solicitudes pendientes/fallidas e historial truncado; soporta la expansión Unicode del texto normalizado sin guardar páginas enteras.
- Corregido Buscar reportes con valores idénticos: ahora reinicia el recorrido. Limpiar búsqueda y Mostrar todos también vuelven al inicio aunque sus valores ya estuvieran vacíos; fechas inválidas mantienen la consulta aplicada.
- Fase 23: historial por páginas de hasta 50 coincidencias, con Más reportes, Anterior y actualización desde el inicio. La lista productiva consulta lotes acotados en SQLite en lugar de cargar todos los reportes; conserva criterios y página al recrearse o regresar del detalle.
- Cursor exclusivo por fecha de guardado, nanosegundos y folio, sin OFFSET. Fechas del incidente, tipo, estado y prioridad filtran antes del límite; el texto conserva la normalización y coincidencia literal anteriores, en segundo plano.
- Una búsqueda revisa hasta 500 candidatos por gesto. Si aún queda recorrido, lo explica y permite Continuar búsqueda incluso cuando esa página no tiene coincidencias. La página siguiente reemplaza la anterior; no se acumula todo el historial en memoria.
- Reintentos conservan el inicio de la página fallida y las respuestas anteriores no sustituyen un recorrido nuevo. Los accesos de navegación/detalle comprueban que la página mostrada siga vigente.
- Fase 22: filtro Todas/Alta/Media/Baja en lista y mapa, combinado con los criterios anteriores. La prioridad del mapa se aplica en SQL antes del límite; ambas pantallas conservan la selección al recrearse. Limpiar/Mostrar todos en la lista incluye prioridad.
- La búsqueda general se calcula en segundo plano con cancelación por fila, carga, error y reintento. Fuente y filtros exactos identifican cada resultado; respuestas anteriores no reemplazan una búsqueda nueva ni aparecen como cero coincidencias durante la carga.
- Corregido el acceso a la lista de zona durante movimiento de cámara: antes de abrir la muestra o sus folios, se comprueba que la región real sigue coincidiendo. Si cambió, se retira la muestra y se pide consultar nuevamente.
- Fase 21: fechas opcionales del incidente en el mapa, con calendario, validación estricta y zona horaria conservada. Se combinan con zona, tipo, estado y límite; el SQL filtra antes de limitar la muestra y respeta nanosegundos.
- Lista de la misma zona: muestra los folios de la consulta vigente del mapa y permite abrir sus detalles. Advierte si la muestra está limitada; los resultados anteriores no se ofrecen durante una carga, cambio de filtros o error.
- Las fechas inválidas mantienen la consulta válida anterior; entradas y fechas aplicadas se conservan por separado al recrear la pantalla. Cambiar el intervalo invalida respuestas anteriores incluso para la misma zona.
- Se actualiza el flujo del equipo: código y GitHub durante el desarrollo; APK, versión Android, tags y Releases se agruparán en una futura entrega 0.6.0. La última entrega binaria continúa siendo 0.5.7 / código 27.

[Fases 21–27](docs/FASES.md) registran pruebas y aceptación Android pendiente. **19/28 = 67.9% de construcción**, mismos criterios; cuentas/reportes propios e historial de lectura locales construidos, recorrido administrativo y notificaciones pendientes.

## [0.5.7] — fase 20 · búsqueda local

- Búsqueda en descripción y referencia, sin distinguir mayúsculas ni acentos, combinada con los filtros de tipo/estado de la lista.
- Rango opcional de fechas del incidente con escritura estricta dd/MM/aaaa y calendario. El día final se incluye completo según la zona horaria conservada al abrir la pantalla.
- Buscar reportes aplica texto/fechas por gesto; las fechas inválidas o invertidas explican el problema y mantienen la búsqueda válida anterior. Limpiar búsqueda retira todos los filtros; se conservan entradas y filtros aplicados al girar o volver del detalle.
- Corregida la consulta por folio: nuevas solicitudes sustituyen las anteriores y volver del detalle cancela su lectura pendiente. Los resultados y fallos tardíos no reemplazan una consulta nueva ni mantienen bloqueada la lista.
- Documentada la ejecución de `app` en Android Studio para distinguir el producto de `verification:maps`, laboratorio técnico separado testOnly.
- Mantiene login/loading, datos, esquema SQLite, fotografías, identidad durable, borradores, aviso de repetidos y GPS de 5 km. Los filtros nuevos pertenecen a la lista; no modifican la consulta del mapa.

[Probar APK](docs/PROBAR_0_5_7.md) · [Fase 20](docs/FASE_20.md) · [Validación](docs/VALIDACION.md). Código Android 27. Avance **15/28 = 53.6%**, mismos criterios y pesos.

## Antecedente 0.5.6

## [0.5.6] — 2026-10-11

- Fase 19: la pantalla verde pasa a Inicio con tres accesos reales: Reportar, Reportes guardados y Mapa de reportes. Se retiran los controles ficticios de esa pantalla, conservando su código histórico y pruebas.
- Indicadores reales del borrador y del intento por comprobar; fallos independientes con reintento. Abrir/regresar solo lee y no restaura, guarda, solicita GPS ni elimina contenido.
- Corregido el desplazamiento con letra grande y orientación horizontal: los botones quedan dentro del área visible, por encima de las barras del sistema.
- Continuidad: conserva navegación, diario/UUID, fotografías, aviso de repetidos, radio de 5 km, datos y login/loading del compañero. Botones adaptables a fuente grande y orientación horizontal.
- Documentación corregida: GPS 4/4 por la prueba manual de punto/ubicación/radio confirmada por el usuario; guardado/folio físico es una comprobación adicional distinta. Avance **15/28 = 53.6%**, sin cambiar pesos ni sumar hitos por el menú.

[Probar APK](docs/PROBAR_0_5_6.md) · [Fase 19](docs/FASE_19.md) · [Validación](docs/VALIDACION.md). Código Android 26, entrega oficial.

## Antecedentes históricos del corte anterior

## [0.5.5] — fase 18 · Release oficial

### Añadido
- Aviso previo al guardado de posibles reportes repetidos: mismo tipo, 200 m y hasta 2 h antes/después de la fecha del incidente, solo en este teléfono.
- Folios consultables desde el aviso, regreso con borrador íntegro y decisión explícita para un incidente distinto.
- Explicación de búsqueda fallida o limitada, incluyendo páginas vacías incompletas.

### Protección y continuidad
- Consulta limitada a 200 candidatos y cinco coincidencias, con distancia real calculada, antimeridiano/polos y nanosegundos; excluye el folio de la copia coincidente.
- Resultados vinculados a todos los campos y UUID; cancelar, corregir o salir durante la lectura impide usar una respuesta antigua para guardar.
- Mantiene identidad durable, reintentos pendientes, comprobación GPS de 5 km, fotos, migración y copia del borrador. Sin cambios al esquema SQLite, login/splash o roles.

[Pasos de prueba](docs/PROBAR_0_5_5.md) · [Fase 18](docs/FASE_18.md) · [Validación](docs/VALIDACION.md). Avance del PDF: 14/28 = 50%.

## Antecedentes del corte anterior

## [0.5.4] — 2026-10-10

- Fase 16: fotografía opcional mediante el selector del sistema, copia privada, vista previa en formulario/revisión y fotografía en el detalle del folio. Cancelar una sustitución conserva la foto anterior.
- JPEG, PNG y WebP se comprueban por contenido. La copia se orienta correctamente, reduce hasta 2048 píxeles y normaliza a JPEG sin metadatos EXIF; controles de tamaño, dimensiones e integridad evitan aceptar archivos incompletos o alterados.
- SQLite pasa de esquema 1 a 2 mediante migración aditiva; conserva reportes anteriores sin fotografía y acepta diarios de guardado históricos. Una fotografía dañada impide un nuevo guardado, permite corregir y no oculta el resto de un reporte ya confirmado.
- Fase 17: guardar una copia completa del borrador por gesto desde la revisión, recuperarla tras cerrar la app y abrir el folio cuando ya fue guardada como reporte. Leer la copia no restaura campos, inserta datos ni inicia GPS automáticamente.
- Protección de revisiones entre pantallas: recuperación con lectura actual, comprobación de identidad y folio al escribir, recarga ante resultado incierto y reutilización del UUID para el mismo contenido. Los reintentos del mismo reporte conservan su folio.
- Entrega oficial con APK académico, código Android 24. Construcción **14/28 = 50%**: se completa el hito de fotografías de 4.2; recuperación mejora fiabilidad y no agrega criterios al plan. Login/splash del compañero y versiones previas se conservan.

## Antecedentes del corte anterior
## [0.5.3] — 2026-10-09

- Activado por autorización: formulario con guardado SQLite real, recibo/folio, lista, búsqueda, filtros y detalle; mapa por zona con calles/pines locales y navegación al mismo detalle. Todo accesible desde Desarrollador en debug y release.
- Añadido: diario privado AtomicFile con UUID y contenido antes de insertar; recuperación de resultado y reintento conservan identidad ante interrupción, sin GPS/guardado automático.
- Corregido: rechazo del GPS tras recuperar un intento conserva todos los campos; girar después de corregirlos no sobrescribe cambios. Cancelación de GPS de revisión ignora respuestas antiguas.
- Corregido: el panel del mapa conserva su altura al mostrar carga/resultados/límite; no cambia la zona visible por su propia respuesta ni descarta una consulta correcta.
- Conservado: guard preciso/reciente/no simulado y radio 5 km al confirmar y guardar; login del compañero, esquema SQLite, datos e historia. Consulta es local y no identifica autores ni edita estados.
- Validación: 594 casos distintos aprobados, 65 definiciones nuevas. Debug/release/androidTest compilan; lint sin errores. Pruebas de integración usan fuente de teléfono controlada solo en androidTest y no acreditan GPS físico del recorrido final.
- Estado: Release oficial, código Android 23; 13/28 hitos = 46.4%. Fotografías, cuentas, administración y avisos siguen pendientes de implementación. [Fase 15](docs/FASE_15.md) detalla evidencia y límites.

## [0.5.2] — 2026-10-09

- Añadido: revisión completa del borrador con fecha/zona, texto íntegro, referencia y coordenadas; corregir, volver y recrear sin perder el formulario.
- Corregido: salir con solo tipo/prioridad elegidos o entrada rechazada también pide confirmar.
- Corregido: reintentos técnicos después de confirmar SQLite y perder la respuesta recuperan el mismo folio mediante identidad estable. Una identidad usada con otros datos se rechaza; las solicitudes nuevas siguen siendo reportes distintos.
- Corregido: el modelo conserva el folio después de guardar y requiere iniciar otro reporte expresamente. Un intento incierto no permite cambiar su contenido al reintentar.
- Validación: 529 casos distintos aprobados, 21 definiciones nuevas; debug/release compilan, lint sin errores. Detalle y límites en [Fase 14](docs/FASE_14.md).
- Estado: Release oficial, código Android 22. Guardado/consulta reales siguen ocultos hasta autorización específica; 11/28 hitos = 39.3%. Falta retener identidad ante muerte de proceso antes de activar el guardado y aceptar GPS físico en la pantalla.

## Antecedentes

## Entrega oficial 0.5.1 — formulario y mapa visibles

**9 de octubre de 2026 · código Android 21.** El usuario indicó «desde ahora puro released». Esta entrega incorpora las fases 12 y 13 a las variantes compartidas: Desarrollador → **Nuevo reporte · borrador** permite llenar el reporte, elegir un punto en MapLibre/OpenFreeMap, conservar el texto al volver y revisar los campos. El mapa muestra calles, nombres y pin. Los controles distinguen permiso denegado/aproximado/preciso, GPS desactivado y reintento, y funcionan al girar la pantalla. Publicación: [Release v0.5.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.1).

La confirmación mantiene GPS preciso, reciente, no simulado y radio de 5 km. El formulario **todavía no guarda ni envía reportes ni avisa a emergencias**; guardado y consulta reales siguen ocultos por la instrucción específica anterior. El login y el ejemplo ficticio del compañero se conservan. Se comprobaron **296 casos: 285 JVM y 11 nativos**, con cuatro nuevos casos de permisos/ubicación desactivada contra Android real, sin inyectar estados de permiso en el modelo ni ubicaciones falsas. Debug/release compilan, firma debug válida y lint sin errores, con 19 advertencias anteriores por variante.

**Avance: 11/28 = 39.3%.** Se acredita el hito existente de **4.3 permisos y estados GPS (3/4)** por las comprobaciones de los controles, respaldadas por las pruebas previas de fuente/estados. GPS físico y confirmación completa dentro de esta pantalla siguen pendientes: no se completa 4.3.4. Publicar una versión no suma otro hito. [Entrega 0.5.1](docs/ENTREGA_0_5_1.md) explica los puntos y los pendientes; los cortes Unreleased anteriores permanecen como historia y su código de interfaz se incluye en esta entrega.

## Antecedentes conservados

## Fase 13 — permisos y recuperación del GPS — Unreleased

**Avance local del 9 de octubre de 2026**, autorizado por el usuario al pedir el siguiente paso. La vista de reporte ahora explica permiso aproximado, denegación, cancelación y fallos al comprobar o abrir el permiso. Un GPS sin respuesta indica cómo reintentar conservando el punto; ubicación desactivada pide volver a buscar por gesto. Los controles del mapa se pueden desplazar en orientación horizontal y usan el tamaño real de la ventana.

Se mantienen radio 5 km, GPS preciso/reciente/no simulado y confirmación validada; no hay GPS automático ni apertura automática de Ajustes. Debug se identifica como **0.5.0-fase13 / código 20**. La interfaz nueva sigue excluida de release, el login se conserva y guardado/consulta reales permanecen ocultos. **Sin commit, subida ni publicación; main y Release 0.5.0 intactos.** [Fase 13](docs/FASE_13.md) registra comprobación y pendientes. Avance acreditado **10/28 = 35.7%**; la aceptación con GPS físico de esta pantalla sigue pendiente.

## Antecedente: fase 12

## Fase 12 — interfaz de nuevo reporte — Unreleased

**Trabajo local del 9 de octubre de 2026.** El usuario autorizó empezar la interfaz y reutilizar el primer borrador. Se recuperan su estilo azul/verde y la organización del formulario, conectados a los modelos actuales. Login y entrada del compañero se conservan. En debug, Desarrollador → Nuevo reporte · vista previa abre campos de incidente, revisión y selección de punto en MapLibre/OpenFreeMap con calles. No envía avisos ni guarda reportes reales; la consulta real permanece oculta.

La confirmación mantiene permiso preciso, GPS reciente y válido, rechazo de ubicación simulada y radio de 5 km. Abrir el mapa o volver no inicia GPS. El APK local se identifica como **0.5.0-fase12 / código 20**; la variante release excluye la nueva Activity y oculta su botón. **Main, tag y Release oficial 0.5.0 no cambian**. Esta instrucción autoriza este desarrollo local, no commit, subida, integración o nueva publicación. [Fase 12](docs/FASE_12.md) detalla pruebas y pendientes. El avance acreditado sigue **10/28 = 35.7%** hasta aceptar el flujo visual con GPS físico.

## Antecedentes conservados

## Entrega oficial 0.5.0

**Entrega 0.5.0 / código Android 20 — 9 de octubre de 2026.** El usuario autorizó integrar el avance del PR #8 a main y publicar esta Release oficial, al confirmar 0.5.0. Incluye las fases 8 a 11: coordinación de borrador y ubicación, consulta SQLite por zona, MapLibre/OpenFreeMap con calles y marcadores. La publicación se verifica en [Release v0.5.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.0). La interfaz y el login del compañero se conservan: la pantalla productiva del mapa todavía no está acoplada y los accesos al guardado y consulta reales siguen ocultos. Oficial identifica esta entrega del proyecto, no acredita aceptación productiva completa. Avance: **10/28 = 35.7%**, sin cambiar los hitos o sus pesos.

La evidencia funcional previa de fase 11 conserva **325 casos distintos aprobados y 57 definiciones nuevas**; el GPS físico se comprobó por separado. Para 0.5.0 se compilaron de nuevo debug/release y lint, sin errores; no se presenta la evidencia anterior como pruebas nuevas. Se conservan las versiones y APK anteriores.

## Registro histórico anterior a 0.5.0

Los estados Unreleased y las autorizaciones siguientes describen sus cortes originales. El código de fases 8 a 11 se incluye en la entrega 0.5.0; los pendientes visuales se conservan.

Registro de cambios del proyecto. Las fechas usan la hora de México. Distingue preparación local, integración, publicación y revisión funcional; terminar un trabajo no autoriza subirlo.

## Unreleased — Fase 11: zonas y marcadores — cierre 2026-10-08

Subida autorizada el 8 de octubre de 2026: el conjunto validado de fases 8 a 11 se comparte en la rama `development/fase-11-mapa-zonas`, con propuesta en borrador para revisión. Main y Release oficial 0.4.0a se conservan; no se asigna otra versión, se integra ni se publica una Release nueva.


Trabajo iniciado el **7 de octubre de 2026**, cerrado técnicamente el **8**, posterior a fase 10. Continúa local y Unreleased, con **0.4.0a / código Android 19** conservado como metadato, sin nueva versión o publicación. [Fase 11](docs/FASE_11.md) documenta implementación, comprobaciones y límites; las cifras de fases anteriores conservan sus propios cortes.

- `ReportMapSceneBinding` conecta lectura explícita RESUMED a la región nativa. Copia filtros y límite, sigue camera idle solo después de un gesto, retira marcadores antes de cambiar consulta y exige identidad/consulta/región vigentes antes de pintar. Pause/stop cancelan y limpian; volver exige otro gesto. Estados Idle, Loading, Ready con count/hasMore, Error, ViewportUnavailable y Closed; construir/recrear/observar no lee SQLite.
- `ReportMapViewModel` reserva un token puro de propiedad por escena, sin retener Activity, MapView o callbacks. Una vista reemplazada no cancela ni pinta la sucesora mediante close/pause/resultado/idle, incluso con la misma consulta. Reentradas al limpiar/renderizar/notificar respetan la operación nueva.
- `MapLibreReportSceneHost` representa GeoJSON mínimo con fuente/capa/listeners propios, conserva recursos ajenos y sustituye listas vacías. Host nuevo por vista; recarga de estilo reconecta solo datos vigentes sin consultar otra vez. El constructor map+MapView observa el estilo; map solo utiliza onStyleLoaded. Cierre antes de MapView.onDestroy, sin cerrar el repositorio compartido.
- `verification/maps` comprueba SQLite real en memoria → modelo → binding → GeoJSON/capa SDK sobre Liberty mediante un paquete testOnly. Gesto, otra zona por cámara, filtros/límite/hasMore/vacío, recarga, recursos ajenos, cancelación y recreación; captura revisada sin usar una base productiva o GPS ficticio.
- **325 casos distintos aprobados**: 273 JVM app, 34 Android flow, ocho JVM monitor y diez Android mapas. **57 definiciones nuevas:** 31 coordinador, 20 host y seis nativas. Dos aserciones se ajustaron a la precisión/serialización del SDK, sin modificar producción, almacenamiento o reglas GPS. App/nativos se repitieron; flow34/monitor8 se ejecutaron primero y se reutilizaron al final, sin doble recuento.
- Debug/release compilan, firma debug v2 válida y lint sin errores. [Validación](docs/VALIDACION.md) registra advertencias y conservación final. Los 18 APK anteriores y productos teléfono 0.4.0/código 7 y emulador 0.3.1/código 8 se preservan; recibo cierre-fase11.json aprobado con HEAD/índice y 63 archivos de base conservados. Hashes/tamaños de los artefactos técnicos locales están en Validación.
- Variante técnica physical separada del producto para GPS por gesto en el Motorola/API 36, sin coordenadas persistidas ni reportes creados. Primer intento TIMEOUT, siguiente VALIDATED cerca de una ventana con fuente real, captura de 15 s, precisión aproximada 6.668 m y antigüedad 46 ms. nearbyAllowed, distantBlocked y notMock true, sin ampliar radio/precisión/vigencia ni usar simulación.
- Dos hitos existentes acreditados: **captura del dispositivo** y **mapa por zonas con marcadores**. **4.3 en 2/4, 4.5 en 3/4; 10/28 = 35.7%**, con los mismos hitos/pesos. Pantalla y aceptación del flujo productivo pendientes del compañero; no existe aún esa pantalla en el repositorio. Interfaces/login y accesos reales ocultos conservados; no hay Git, publicación, Cloud, borrado o apagado nuevos.

## Unreleased — Fase 10: MapLibre Native y OpenFreeMap — cierre 2026-10-07

El usuario eligió cambiar el proveedor del mapa el 6 de octubre. Se cerró el 7 de octubre el trabajo interrumpido por el límite de uso: **implementación y validación técnica completadas; mapa real con nombres legibles comprobado**. Trabajo local posterior a fase 9 y a **0.4.0a / código Android 19**, sin otro número de versión. Las cifras de fases 9 (457/43) y 8 (226/27) conservan sus cortes; aceptación del flujo productivo pendiente.

- Motor activo **MapLibre Native Android 13.6.1**, artefacto **`org.maplibre.gl:android-sdk-opengl:13.6.1`**, con estilo **Liberty de OpenFreeMap**, `MapsConfiguration.STYLE_URL` e inicialización previa a `MapView`. OpenGL ES es el backend estable elegido para la compatibilidad; HTTP 200 del estilo no acredita renderizado. SDK Google, metadato/lectura de clave y prerequisito Google Play Services salen del runtime del mapa. [Mapa con MapLibre](docs/MAPA_MAPLIBRE.md) es la guía activa; las guías Google se conservan como antecedentes.
- `MapsReadinessChecker` inicializa el SDK y distingue `Ready`, `RendererUnavailable` y `VerificationFailed`; no acredita red, carga de estilo ni renderizado.
- Bindings de ubicación y borrador aceptan `MapLibreMap`. `MapLibreMapClickHost` añade/retira su propio listener por identidad y devuelve `false` en callbacks retirados o sustituidos; conserva listeners ajenos. `MapLibreIncidentBinding` es el puente simple nuevo; `GoogleMapIncidentBinding.kt` permanece como wrapper deprecated que delega a MapLibre.
- Nuevo laboratorio `verification/maps`, `testOnly` y con paquete aislado, sin GPS: ocho JVM y cuatro nativas aprobadas para carga real de estilo/mapa/fotograma, objetos de calles/etiquetas/edificios, tap SDK, recreación, fallo y cancelación. El PNG se revisa por separado, porque una consulta de etiquetas no acredita texto legible. [Fase 10](docs/FASE_10.md) y [Validación](docs/VALIDACION.md) conservan la evidencia.
- El primer renderizado software mostró nombres invisibles; la prueba con GPU host NVIDIA RTX 3070/OpenGL, el mismo SDK, estilo y código muestra Calle Tacuba, Calle Moneda y Calle Venustiano Carranza. Los cuatro nativos vuelven a aprobar sin aumentar el total. `-gpu host` se aplicó solo a la ejecución, sin cambiar configuración o datos del AVD. La limitación y los reportes abiertos de MapLibre se documentan en [Mapa con MapLibre](docs/MAPA_MAPLIBRE.md); no se incluye un parche sin publicar.
- **268 casos distintos aprobados**, cero fallos, errores u omisiones: 222 JVM app, 34 Android flow, ocho JVM del monitor y cuatro Android mapas. **25 definiciones añadidas:** 13 adaptadores, ocho monitor y cuatro nativas; siete casos de readiness reemplazan nueve de Google y se registran aparte. Dominio/datos de fase 9 no se ejecutaron otra vez en este corte; las repeticiones no se suman.
- Debug/release compilan, firma debug v2 válida y lint sin errores: app19 advertencias anteriores, flow2 y mapas8 (seis `LogNotTimber` de diagnóstico y dos del contenedor técnico). Recibo local ignorado `.gradle/validacion/cierre-fase10.json`: 16 APK anteriores, HEAD/índice, fuentes visuales y producto instalado 0.3.1/código 8 conservados. APK técnicos nuevos separados de los anteriores y de la Release.
- GPS explícito, permisos, radio de 5 km, borrador, SQLite y consulta por zona conservan sus reglas. El compañero mantiene interfaces/login/mapa/pines y accesos reales ocultos. **8/28 = 28.6%, Mapas 2/4**, al acreditar solo **SDK configurado** con carga/renderizado reales y nombres visibles. Los hitos y pesos se conservan; mapa por zonas con marcadores, integración visual productiva, GPS físico y aceptación quedan pendientes.
- Sin Git, publicación, Cloud, facturación, tarjeta, borrado o apagado nuevos.

## Unreleased — Fase 9: consulta de incidentes por zona — 2026-10-06

Nueva función local posterior a la fase 8 y a **0.4.0a / código Android 19**, sin otro número de versión. **Implementación y validación técnica completadas; aceptación visual pendiente.** [Validación](docs/VALIDACION.md) conserva las ejecuciones efectivas de este corte.

- `MapRegion` y `ReportMapQuery` describen límites geográficos, cruce del antimeridiano, filtros de tipo/estado y límite de 1 a 500 marcadores (200 por defecto). Los filtros se conservan como una instantánea independiente de las colecciones del llamador.
- `ReportMapRepository.queryMarkers` devuelve `ReportMapPage` con la proyección `ReportMapMarker`: folio, tipo, prioridad, estado y coordenadas. No expone descripción ni referencia de ubicación.
- `SqliteReportRepository` consulta directamente con parámetros, excluye registros sin coordenadas, ordena por fecha de creación y folio descendentes y pide un resultado adicional para calcular `hasMore`. Reutiliza la base y el repositorio protegido sin migraciones, inserciones ni cambios de estado; conserva el contrato previo de `ReportRepository`.
- `ReportMapViewModel` expone la consulta de cada estado, carga solo por `load(query)`, cancela una petición sustituida e ignora sus respuestas tardías. Repetir la consulta pendiente no abre otra; `clear()` cancela y vuelve a `Idle`. Crear u observar el modelo no lee SQLite.
- Corregida la reentrada al observar `Loading`: el job lazy se reserva antes de emitir el estado, de modo que un `clear()` o `load(B)` inmediato no inicia la lectura de A. Dos regresiones están incluidas en las 19 definiciones nuevas del modelo.
- La fábrica lazy de `CitizenSecurityApplication` utiliza el mismo repositorio productivo compartido. [Fase 9](docs/FASE_9.md) y [acoplamiento](docs/ACOPLAR_INTERFAZ.md) preparan el consumo por el compañero después de un gesto o de terminar el movimiento de cámara; `hasMore` señala truncamiento y no entrega un token de página.
- Nuevo entregable técnico de **4.5 Mapas**, con **7/28 = 25%** conservado: faltan renderizado real, marcadores visibles, clave, GPS físico y aceptación del flujo. La [guía de clave Maps](docs/CONFIGURAR_CLAVE_MAPS.md) prepara el siguiente paso manual. Pantallas/login, ejemplo ficticio y accesos ocultos permanecen; no hay acciones Git, publicación o Cloud nuevas.
- **457 casos distintos ejecutados y aprobados**, cero fallos, errores u omitidos y cero reutilizados: 96 dominio, 38 JVM datos, 78 SQLite Android, 211 JVM app y 34 laboratorio flow. **43 definiciones nuevas:** 10 dominio, 14 SQLite y 19 modelo de consulta. Pase amplio de 2 min 9 s; repetición final de app, compilación y lint de 16 s tras corregir la reentrada, sin sumar los casos repetidos.
- Debug/release compilan, firma debug v2 válida y lint sin errores: app conserva 19 advertencias anteriores en debug/release, datos ninguna y flow dos anteriores. El recibo ignorado `.gradle/validacion/cierre-fase9.json` confirma conservación de los 14 APK previos, HEAD/índice, fuentes visuales, Gradle y producto instalado 0.3.1/código 8; los fixtures están fuera del DEX productivo.

## Unreleased — Fase 8: borrador y ubicación coordinados — 2026-10-06

Trabajo local posterior a **0.4.0a / código Android 19**, sin otro número de versión asignado y sin acciones Git o publicación nuevas. Implementación y verificación técnica completadas; aceptación funcional pendiente. Se cerró el registro que quedó interrumpido por el límite de uso.

- `ReportLocationFlowBinding` concentra apertura, selección, permisos, captura explícita, confirmación y cancelación. Cada evento comprueba primero el token del borrador para impedir que controles de otra apertura cambien la sesión vigente.
- `ReportLocationMapBinding` delimita carga asíncrona, clicks y cierre a una vista concreta. `prepareMap(token)` reserva la conexión antes de solicitar el mapa; su handle valida `attachMap(map)`. El coordinador cierra el attachment anterior antes de sustituirlo; un callback de la vista anterior no debe afectar a su reemplazo aunque compartan token.
- La recreación de la Activity recupera la apertura vigente del borrador retenido, conserva campos y pin y exige renovar la captura por un gesto. No crea otra apertura ni inicia GPS automáticamente.
- Confirmar consulta el flujo actual con la regla de **5 km** y aplica solo las coordenadas al borrador, conservando sus ediciones actuales; no inserta en SQLite.
- [Fase 8](docs/FASE_8.md), [acoplamiento](docs/ACOPLAR_INTERFAZ.md), [fases](docs/FASES.md) y [avance](docs/AVANCE.md) distinguen este trabajo local de la Release oficial. Interfaz/login del compañero y funciones visibles se conservan. **7/28 = 25%**, sin nuevo hito del PDF ni aceptación funcional registrada.
- **226 pruebas ejecutadas y aprobadas: 192 JVM de app y 34 Android del flujo; 27 definiciones nuevas (21 JVM y seis Android)**. Debug/release compilan, firma debug v2 válida y lint sin errores: 19 advertencias anteriores en app y dos del laboratorio. [Validación](docs/VALIDACION.md) registra este corte; dominio/datos y los tres diálogos separados no se ejecutaron de nuevo.
- Cerrar el mapa vigente después de un reset detiene la captura aunque se haya invalidado el token; los callbacks y cierres de una vista sustituida conservan la captura nueva. La app instalada y doce APK archivados conservan sus hashes. Sin cambios visuales, de dominio/datos, Gradle, HEAD o índice; no se instala el producto ni se reemplaza la Release 0.4.0a.

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
