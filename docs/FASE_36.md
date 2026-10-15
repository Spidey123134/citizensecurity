# Fase 36 — preferencias y avisos locales de cambios

**15 de octubre de 2026 · Unreleased.** Esta fase conecta la actividad de reportes con avisos reales de Android y una preferencia voluntaria por cuenta. La revisión se inicia con un gesto dentro de la app; no hay revisión en segundo plano, servidor ni avisos push.

Android conserva **0.6.1 / código 29**. La APK oficial 0.6.1 contiene las fases 29–34; este código no modifica sus archivos, firma o checksum. El ensamblado interno para comprobar la implementación no constituye una nueva APK publicada, tag o Release.

## Relación con el PDF

El punto **4.7 Notificaciones** pide avisos relacionados con cambios de estado de un reporte. Se reutiliza la fuente real de la [fase 35](FASE_35.md): eventos `STATUS_CHANGED` confirmados en el espacio de reportes capturado. Un `CREATED` acredita un guardado local y no produce este aviso. Tampoco se generan cambios ficticios para demostrar la pantalla.

La asignación administrativa continúa pendiente por decisión del usuario. El servicio productivo mantiene la escritura denegada por defecto; `admin/admin` conserva acceso académico. Preparar el consumidor de avisos no concede un rol, habilita un panel ni acredita seguimiento institucional.

**20/28 = 71.4%** acredita únicamente permisos y preferencias, un criterio existente de 4.7, comprobado con denegación/concesión reales y persistencia por cuenta. Los eventos productivos, la entrega dentro del recorrido autorizado y su aceptación completa conservan sus pendientes. Una pantalla, cantidad de pruebas o publicación no crea criterios nuevos. [Matriz vigente](AVANCE.md).

## Recorrido de uso

Desde **Actividad de reportes** se abre **Avisos de cambios de estado**. Entrar consulta únicamente la preferencia del espacio vigente y el estado actual de Android. No solicita permiso, cambia la preferencia ni muestra una notificación automáticamente.

La preferencia comienza desactivada. **Activar avisos** es una decisión explícita; en Android 13 o posterior, si falta el permiso, ese gesto inicia el diálogo del sistema. Denegarlo conserva la preferencia desactivada. La pantalla distingue permiso pendiente, bloqueo general de la app y bloqueo del canal. Ofrece acceso explícito a Ajustes y vuelve a comprobar su estado al regresar. No intenta eludir una decisión de Android recreando un canal bloqueado.

Con la preferencia activada y Android disponible, **Revisar cambios** consulta los cambios registrados. Si encuentra operaciones todavía sin registrar como aceptadas por Android, prepara un único resumen general. Cuando quedan candidatos tras el límite de esa revisión, **Seguir revisando cambios** continúa el recorrido; un resultado parcial no acredita que se revisó todo el historial. Una revisión nueva comienza desde el principio, incluyendo cambios con fechas registradas anteriores.

**Desactivar avisos** cancela el resumen propio y conserva los identificadores ya registrados. Cambiar de cuenta o reiniciar el proceso retira también ese resumen. Ninguna de esas acciones borra reportes, fotos, historial o preferencias de otra cuenta.

## Información del aviso

El resumen utiliza un canal de importancia baja, sin sonido, vibración ni insignia, con visibilidad secreta solicitada a Android. El título y texto son generales: indican que existen cambios de estado guardados en este teléfono e invitan a abrir la actividad. No incorporan folio, cuenta, coordenadas, referencia, foto, motivo o descripción del incidente al contenido, extras o destino de la notificación.

El acceso utiliza un `PendingIntent` explícito e inmutable hacia `SplashActivity`; pasa por la entrada normal de la app. No restaura una cuenta ni abre directamente datos privados basándose en un payload. Solo se cancela el resumen propio, sin retirar otras notificaciones de la app.

Que `NotificationManager.notify` termine con las comprobaciones de permiso permitidas significa **aceptación de la solicitud por Android**. No demuestra que el dispositivo enseñó el aviso, que alguien lo leyó, que una autoridad recibió el reporte o que un incidente fue atendido.

## Persistencia, límites y recuperación

Preferencia e identificadores de operaciones se almacenan en un archivo privado independiente por UUID de cuenta; el acceso académico mantiene su propio espacio compartido local. La pantalla y el repositorio capturan ese dueño y la sesión original. Una sesión revocada impide continuar el trabajo con la identidad siguiente.

El archivo usa revisión CAS, `AtomicFile`, validación estricta de identidades y contenido, límite de **256 KiB** y exclusión compartida por ruta. No cambia el esquema de reportes **4** ni el de cuentas **1**. Una lectura corrupta, demasiado grande o incierta muestra un error y exige volver a consultar; no autoriza sobrescribir con una preferencia vacía.

La consulta usa páginas de **50** y examina como máximo **500 eventos por gesto**, sin acumular todos los reportes. El registro admite hasta **4096 identificadores de operación**. Alcanzar su capacidad detiene otro aviso antes de publicarlo; no recorta identificadores antiguos ni reinicia la deduplicación silenciosamente.

Los identificadores se escriben después de que Android acepta el resumen y se comprueba la vigencia de la sesión. No son marcas de leído/no leído. Una interrupción entre el aviso y su registro puede dejar un resultado incierto: exige releer antes de otra decisión. Un fallo, cancelación o conflicto posterior retira únicamente el intento propio mediante un ticket de publicación; nunca cancela un resumen más reciente de otra revisión. El identificador estable permite reemplazar el resumen, pero no garantiza una entrega exactamente una vez ni la lectura del usuario.

## Aceptación y validación

Las comprobaciones de esta fase cubren:

- Preferencia desactivada de origen, apertura sin diálogo, denegación y concesión reales del permiso de Android; retorno desde Ajustes y bloqueo del canal.
- Persistencia y aislamiento por cuenta, giro, revocación de sesión y cancelación del resumen propio al desactivar o cambiar de identidad.
- Consulta de cambios confirmados, exclusión de creaciones, paginación acotada, continuación, repetición sin nuevos identificadores y límite de capacidad antes de publicar.
- Aviso nativo con contenido general, destino explícito e inmutable y ausencia de información privada; guardar identificadores solo tras la aceptación de Android.
- Conflictos CAS, corrupción conservada, cancelación y resultado incierto sin reemplazo por vacío ni reintento automático.
- Compilación, comprobaciones JVM y Android, lint y regresiones necesarias de actividad y sesión sobre la fuente final.

**87 definiciones nuevas aprobadas:** 57 JVM (8 dominio, 8 codec, 37 modelo y 4 coordinación del permiso) y 30 Android (16 almacén, 10 publicador y 4 recorridos de interfaz). Se probaron los diálogos reales, retorno desde Ajustes, bloqueo del canal por su interruptor de Android, resumen nativo, deduplicación y aislamiento. [Validación](VALIDACION.md) registra el cierre completo, las regresiones, intentos iniciales y sus límites.

Los cambios de estado creados con autorización controlada exclusivamente en pruebas no conceden permisos productivos ni acreditan el recorrido administrativo real. No se atribuyen pruebas físicas, muerte completa del proceso, ejecución en todas las versiones Android o entrega en segundo plano.

[Plan del proyecto](PLAN_DEL_PROYECTO.md) · [Avance y criterios](AVANCE.md) · [Actividad de reportes](FASE_35.md) · [Recuperación durable de cambios](FASE_31.md) · [Entrega oficial 0.6.1](ENTREGA_0_6_1.md)
