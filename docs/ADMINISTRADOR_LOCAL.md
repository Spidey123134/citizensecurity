# Administración local: cuenta y uso

## Ampliación local Unreleased — resultado del incidente, fase 44

El detalle guardado y la consulta administrativa incorporan **Resultado del incidente**: elegir Solucionado, Sigue sin solucionarse o corregir a Sin confirmar; escribir un motivo de 5–500 caracteres, revisar y confirmar mediante otro gesto. El historial muestra cuenta por UUID, rol, fecha y motivo y conserva las declaraciones anteriores. Este resultado es independiente del estado del trámite: **Cerrado no implica Solucionado**. [Recorrido completo](FASE_44.md).

El autor se comprueba contra el evento real de creación. Para declarar como administrador se relee la asignación y el permiso separado **DECLARE_RESOLUTION**, dentro del mismo espacio local de cuenta; no da acceso a otras cuentas o teléfonos. Si el administrador también creó el reporte, se registra como Autor. El acceso académico `admin/admin` solo consulta. Al perder la autoridad se retira la preparación y se comprueba por separado si sigue vigente el permiso de consulta.

Ante una confirmación incierta se comprueba el recibo por lectura; cualquier reintento requiere otro gesto y conserva el mismo UUID. La preparación vive en memoria durante el giro. Tras cerrar el proceso se consulta lo realmente guardado sin repetir escrituras. Esta ampliación no está en la oficial 0.6.2; las secciones inferiores describen aquella entrega y sus antecedentes.

**Entrega oficial 0.6.2 / código Android 30, publicada — 16 de octubre de 2026.** Los recorridos de esta guía, desarrollados en fases 40–42, se incluyen en el mismo corte que todas las fases 35–42 posteriores a 0.6.1. Usa la APK final publicada de esa entrega. [Cambios y archivo](ENTREGA_0_6_2.md) · [Pasos de prueba](PROBAR_0_6_2.md). Las referencias inferiores a desarrollo local, PR #30 o APK oficial 0.6.1 conservan el momento histórico de cada validación; no describen el alcance final de 0.6.2.

**16 de octubre de 2026 · ampliación local de fase 42, validada.** Desde el recibo de un cambio confirmado se puede abrir su actividad y el panel de avisos. El alta y el cambio de estado conservan los pasos de fases 40/41 descritos debajo; esta ampliación todavía no está en la APK oficial **0.6.1** ni actualiza el PR #30 de fase 40.

## Del recibo a la actividad y los avisos

1. Confirma un cambio mediante el recorrido administrativo y comprueba su recibo. Desde ahí, **Ver actividad de esta cuenta** abre los eventos del mismo espacio; **Revisar avisos de cambios** abre la configuración de avisos. Las rutas conservan el sello interno de sesión, sin llevar el motivo o el cuerpo del cambio en extras. Ninguna reconoce o borra la solicitud pendiente, revisa o publica avisos automáticamente.
2. En Avisos, **Activar avisos** pide el permiso de Android cuando corresponde y guarda la preferencia para esa cuenta. Si el permiso o canal están bloqueados, revisa **Ajustes de avisos de Android** y vuelve a comprobar; tus eventos y reportes se conservan.
3. Pulsa **Revisar cambios** para consultar las transiciones registradas en SQLite. El evento de un cambio ya guardado puede avisarse aunque su recibo administrativo siga pendiente de recuperar o reconocer; el aviso y ese reconocimiento son operaciones separadas. Guardar un reporte y su evento de creación no equivale a un cambio de estado para avisar. Una revisión puede examinar hasta **500 eventos** en páginas de **50**; si queda recorrido, usa **Seguir revisando cambios**.
4. Cuando Android acepta el resumen, la app conserva los UUID de esas operaciones. Otra revisión no vuelve a avisar de los mismos cambios ya registrados. El mensaje del sistema es general: no incluye cuenta, folio, motivo, fotografía o ubicación y no confirma que se haya leído o atendido el incidente por una autoridad.
5. **Desactivar avisos** retira el resumen y conserva el registro de operaciones aceptadas. Salir de Avisos o Actividad conserva la solicitud administrativa: vuelve al recibo y usa **Entendido: cerrar esta solicitud** cuando quieras reconocerla. Abrir una ruta, girar o regresar no sustituye ese gesto.

La revisión es manual; no existe un servicio que revise cambios en segundo plano. Actividad y Avisos consultan el espacio de la sesión vigente sin recibir autoridad administrativa de los extras. Un archivo de avisos corrupto o con `.new`/`.bak` pendiente bloquea su lectura/escritura y conserva esos archivos para revisión; no borres datos ni reinstales como solución.

**Fase 42 cierra 28/28 = 100% de construcción funcional local del plan**, con Notificaciones **4/4** y cero criterios funcionales locales pendientes. Se comprobaron los eventos reales, entrega del resumen y recorrido integrado sobre los cambios autorizados de fase 41.

**1431 casos distintos aprobados/14 nuevos**, cero fallos/errores/omitidos finales: 1379 JVM —205 recibos intactos de dominio reutilizados, 148 datos y 1026 app ejecutados— y 52 Android API 37 ejecutados. Los 14 nuevos son 10 de almacenamiento y cuatro del recorrido integrado. Las pruebas nativas comprobaron la concesión real del permiso, el cambio con rol/diario/SQLite reales, la misma operación en el aviso, deduplicación y conservación del pendiente al navegar; las regresiones volvieron a comprobar denegación/concesión, publisher y cambio de estado. Lint app sin errores/22 avisos existentes por variante; datos sin incidencias. [Desglose y límites](VALIDACION.md).

El cierre describe **construcción funcional local del plan**. La aceptación física completa, preparación de una entrega y seguridad de producción institucional conservan sus comprobaciones propias; el emulador API 37 no acredita pruebas físicas, API 26 o muerte completa del proceso. [Fase 42](FASE_42.md) · [Validación](VALIDACION.md). La siguiente sección conserva el registro del alta y los resultados de fases anteriores.

**15 de octubre de 2026 · guía ampliada para fase 41, desarrollo local Unreleased validado.** Se eligió **`admin_local`** con autorización del usuario. La cuenta se registra en el teléfono y **no se crea automáticamente**. El nombre abre la configuración; los permisos se conceden después de confirmar al propietario con Android y pulsar otra vez para activar. El alta de fase 40 se conserva; una asignación ya comprobada no requiere repetir ese alta para cada cambio de estado.

## Cuenta y contraseñas

| Acceso | Uso |
|---|---|
| `admin_local` | Cuenta registrada normalmente en este teléfono. Puede preparar la asignación administrativa local. |
| Contraseña de `admin_local` | La elige quien registra la cuenta: **15 a 128 caracteres**. No hay contraseña predeterminada, creada automáticamente ni publicada en el repositorio. |
| `admin/admin` | Acceso académico anterior. Conserva su funcionamiento y no recibe permisos administrativos. |
| PIN, patrón o contraseña de Android | Pertenece al bloqueo del dispositivo; se confirma en la pantalla del sistema y la app no lo guarda. Es distinto de la contraseña de la cuenta. |

No subas contraseñas ni el PIN a GitHub, issues, capturas o archivos del proyecto. El compañero registra su propia cuenta en su teléfono; las cuentas y contraseñas no se sincronizan entre instalaciones.

## Activar por primera vez

1. Usa **0.6.2 / código 30**, que incluye fases 40–42. **0.6.1** conserva su contenido anterior y no contiene este recorrido.
2. En el login, entra a **Crear cuenta**, registra `admin_local` con la contraseña que elijas e inicia sesión con ella. Si ya existe en ese teléfono, utiliza su contraseña actual.
3. En **Inicio**, abre **Administración local**. El acceso aparece para `admin_local`; verlo no significa que ya tenga permisos.
4. Pulsa **Confirmar propietario con Android**. Completa el bloqueo del dispositivo en la pantalla nativa de Android. Si no hay PIN, patrón o contraseña configurados, configura uno desde los ajustes del teléfono y vuelve a comprobar. Cancelar conserva la configuración sin activar permisos.
5. Al regresar verás **Propietario confirmado**. Pulsa **Activar administración local** dentro de los dos minutos siguientes al inicio de la confirmación. Volver desde Android por sí solo no activa nada.
6. Cuando la app confirme la asignación guardada, pulsa **Consultar reportes**.

El alta queda ligada al **UUID de esa cuenta**, al teléfono y a una operación concreta. Solo se prepara la primera asignación: no se reemplaza una existente, aunque otra cuenta tenga el mismo nombre. No hay acción de borrado o reinicio administrativo en esta fase.

## Consultar los reportes

La consulta abre el espacio de `admin_local` en ese teléfono. Permite combinar **tipo, estado y prioridad**, actualizar desde el inicio, avanzar o retroceder y buscar un **folio completo** independientemente de los filtros del listado. Cada página contiene como máximo **50 reportes**; el resultado no se presenta como un total de todos los reportes.

Al abrir un folio se leen su descripción, fechas, lugar, estado e historial local, con un máximo de **50 eventos por página**. Las secciones permiten desplazarse y conservan el texto completo. La consulta informa si existe una fotografía adjunta y no lee sus bytes. Una página vacía, un folio ausente y un error de lectura se distinguen; **Reintentar** conserva la consulta que falló y **Actualizar** vuelve al inicio.

Girar la pantalla conserva el ViewModel y su consulta. Un proceso nuevo vuelve a comprobar permisos y releer; los resultados, folios escritos y pruebas del bloqueo no se restauran como autorización. Salir de la cuenta, cerrarla o volver a iniciar sesión invalida el acceso anterior, incluso con el mismo usuario.

## Alcance de los permisos

| Capacidad | Recorrido validado en la fase 41 |
|---|---|
| Consultar reportes (`READ_REPORTS`) | Se comprueba antes y después de leer, sobre el dueño y actor capturados de `admin_local`. |
| Cambiar estado (`CHANGE_STATUS`) | Capacidad independiente: consulta de la revisión, revisión del motivo y segundo gesto de confirmación. Se revalida en IO y dentro de la transacción antes del commit. |
| Consultar otras cuentas o teléfonos | Fuera del alcance de esta asignación. No hay consulta global, servidor ni sincronización. |
| Historial administrativo | Cada cambio confirmado conserva folio, UUID del actor, motivo, fecha, secuencia y operación. Se consulta desde el folio; reconocer el recibo no borra el evento. |

El acceso es académico y local: no convierte la cuenta en una identidad policial, no recibe denuncias institucionales y no confirma atención o recepción oficial. Los reportes anteriores conservan su dueño; habilitar administración no los mueve al espacio de esta cuenta.

## Cambiar un estado local

Este recorrido se incluye en **0.6.2**, junto con fases 35–42; no aparece en la APK histórica 0.6.1.

1. En la consulta administrativa abre un folio y pulsa **Preparar cambio de estado**. Para comprobar una operación anterior también puedes usar **Comprobar cambio pendiente**, sin seleccionar un folio nuevo.
2. Pulsa **Comprobar solicitudes pendientes**. La app lee el diario y busca su recibo; esta acción no cambia estados. Si hay una operación por resolver, resuélvela antes de preparar otra.
3. Con el diario vacío comprobado, pulsa **Consultar estado actual** con el folio completo. Comprueba descripción, estado y revisión del historial.
4. Escribe el motivo de **5 a 500 caracteres válidos**, sin controles ni espacios exteriores. Pulsa **Revisar cambio antes de confirmar** y lee el motivo y la transición completos. Todavía no se guarda un cambio.
5. Pulsa **Confirmar y guardar cambio local**. Si el reporte cambió, la app exige otra revisión; no aplica la solicitud vieja a una revisión nueva.
6. Comprueba el recibo con UUID de cuenta/operación, motivo, fecha y secuencia. Pulsa **Entendido: cerrar esta solicitud** para poder preparar otra. El cambio sigue en el historial.

Solo se permite **Reportado → En revisión → Atendido → Cerrado**, un paso por solicitud. Un reporte Cerrado no ofrece otro avance. Estos nombres organizan el trabajo local; **Atendido no significa que una autoridad haya atendido el incidente**. La app no envía el reporte a la policía desde este recorrido.

Volver a editar o cancelar invalida la revisión anterior. Abrir, girar o regresar a la pantalla no confirma ni reintenta cambios. El giro conserva la preparación en memoria; un proceso nuevo exige recuperar el diario y comprobar de nuevo los datos. Los controles requieren sesión vigente, permiso, pantalla activa y dispositivo desbloqueado.

## Si se interrumpe un cambio

**Recuperar resultado de esta solicitud** busca el recibo sin repetir el cambio. Si falta el recibo y la app permite **Reintentar la misma solicitud**, ese botón conserva folio, revisión, estados, motivo, fecha y UUID completos. No prepares otro reporte para intentar resolver la incertidumbre del anterior.

Si aparece un rechazo definitivo por revisión cambiada o folio ausente, **Cerrar solicitud rechazada** verifica otra vez la misma solicitud y su recibo. Si apareció un recibo coincidente, muestra el cambio confirmado en lugar de descartar su resultado. Después de cerrar un rechazo, consulta el folio y revisa una solicitud nueva; el cierre no reintenta el cambio.

Una lectura fallida, archivo corrupto o recibo incompatible no se sustituye por un resultado vacío. Conserva el mensaje para revisión del equipo; no borres datos, archivos o reinstales como solución. Salir durante una escritura no demuestra que se revirtió: al volver comprueba su recibo.

Si se pierde el permiso, termina la sesión o no se puede comprobar la asignación administrativa, la pantalla cierra el acceso y retira los datos privados, incluso durante un guardado. El diario se conserva: ese cierre no borra la solicitud ni demuestra que el cambio se revirtió. Para conocer su resultado se necesita recuperar el acceso autorizado y consultar de nuevo el recibo.

## Interrupciones y errores

La comprobación del propietario usa una solicitud única, solo en memoria, con una vigencia monotónica de **120 segundos**. Rechaza resultados sin una solicitud viva, caducados o de una sesión terminada. El último gesto y la escritura vuelven a comprobar la sesión, la vigencia y que la pantalla siga en primer plano y el dispositivo desbloqueado.

Si no se puede confirmar una escritura, usa **Comprobar asignación guardada**. La app relee el resultado antes de ofrecer otra activación y conserva un alta que ya se haya confirmado. Un archivo corrupto o pendiente no se trata como una configuración vacía: bloquea el acceso y se conserva, sin reparación o reemplazo automáticos. No borres datos o reinstales como paso para resolverlo; conserva el mensaje para que el equipo revise el caso.

## Android y entrega al equipo

La confirmación utiliza [KeyguardManager.createConfirmDeviceCredentialIntent](<https://developer.android.com/reference/android/app/KeyguardManager#createConfirmDeviceCredentialIntent(java.lang.CharSequence,%20java.lang.CharSequence)>), disponible desde API 21 y obsoleta desde API 29. Android recomienda actualmente BiometricPrompt; este recorrido nativo permite mantener el mínimo declarado del proyecto en **API 26** sin añadir otra biblioteca. Requiere soporte de bloqueo seguro y un componente del sistema resoluble. Declarar esa compatibilidad no acredita haber ejecutado el recorrido en API 26.

Como antecedente, el cierre de fase 40 aprobó **1360 casos distintos, 76 nuevos**, sin fallos: 1304 JVM y 56 Android. Incluyó **confirmación real del PIN nativo de Android en el emulador API 37**, alta y consulta de la propia cuenta con archivos aislados. No activa por sí mismo la administración en tu teléfono ni acredita ejecución API 26. Aquel cierre alcanzó **22/28 = 78.6%**, Administración **2/4** por consulta y roles/permisos. [Detalle de validación](VALIDACION.md).

**La fase 41 alcanza 25/28 = 89.3%**, con Consulta y Administración en **4/4**. Permanecen tres criterios de Notificaciones; la nueva pantalla no completa su recorrido de avisos ni recibe reportes de otras cuentas o teléfonos.

**1488 casos distintos aprobados, 103 nuevos**, cero fallos/errores/omitidos finales: 1379 JVM y 109 Android. Se ejecutaron las 148 pruebas JVM de datos y 1026 de app; 205 recibos intactos de dominio de fase 40 se reutilizaron. El recorrido Android comprobó los tres avances, el bloqueo de un paso adicional desde Cerrado, recuperación del mismo UUID y del cambio confirmado sin repetirlo, ventana nueva y giro, usando rol local, SQLite y diario reales en datos de prueba. Incluye regresiones del alta con el PIN nativo real del emulador, sin contarlas como nuevas. El PIN y el ajuste previo se restauraron y el emulador se cerró. Lint sin errores; [desglose completo](VALIDACION.md). No acredita prueba física, API 26 o muerte completa del proceso.

La rama **`development/administracion-local`** y el [PR borrador #30](https://github.com/Spidey123134/citizensecurity/pull/30) corresponden al corte compartido de **fase 40**. Las ampliaciones de **fase 41 permanecen locales**: este corte no actualiza GitHub/main o ese PR ni genera APK, tag o Release. La última APK oficial sigue siendo **0.6.1**. El candidato 0.6.2 archivado es anterior a las fases 38–41 y deberá prepararse otra vez cuando se autorice la siguiente entrega.

[Fase 41](FASE_41.md) · [Fase 40 y alta local](FASE_40.md) · [Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Validación](VALIDACION.md)
