# Administración local: cuenta y uso

**15 de octubre de 2026 · fase 40, código Unreleased.** El usuario autorizó elegir una cuenta local y documentarla para el equipo. Se eligió **`admin_local`**. El nombre abre la configuración; los permisos se conceden únicamente después de confirmar al propietario con Android y pulsar otra vez para activar.

## Cuenta y contraseñas

| Acceso | Uso |
|---|---|
| `admin_local` | Cuenta registrada normalmente en este teléfono. Puede preparar la asignación administrativa local. |
| Contraseña de `admin_local` | La elige quien registra la cuenta: **15 a 128 caracteres**. No hay contraseña predeterminada, creada automáticamente ni publicada en el repositorio. |
| `admin/admin` | Acceso académico anterior. Conserva su funcionamiento y no recibe permisos administrativos. |
| PIN, patrón o contraseña de Android | Pertenece al bloqueo del dispositivo; se confirma en la pantalla del sistema y la app no lo guarda. Es distinto de la contraseña de la cuenta. |

No subas contraseñas ni el PIN a GitHub, issues, capturas o archivos del proyecto. El compañero registra su propia cuenta en su teléfono; las cuentas y contraseñas no se sincronizan entre instalaciones.

## Activar por primera vez

1. Usa una compilación que incluya la fase 40. La APK oficial **0.6.1** todavía no contiene este recorrido.
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

| Capacidad | Estado en la fase 40 |
|---|---|
| Consultar reportes (`READ_REPORTS`) | Se comprueba antes y después de leer, sobre el dueño y actor capturados de `admin_local`. |
| Cambiar estado (`CHANGE_STATUS`) | Capacidad separada de la consulta. La asignación local la contempla, pero los controles de cambio y su recuperación visible corresponden a la fase siguiente. |
| Consultar otras cuentas o teléfonos | Fuera del alcance de esta asignación. No hay consulta global, servidor ni sincronización. |
| Historial administrativo completo | Pendiente del recorrido visible de cambios autorizados. Consultar un evento existente no completa ese criterio. |

El acceso es académico y local: no convierte la cuenta en una identidad policial, no recibe denuncias institucionales y no confirma atención o recepción oficial. Los reportes anteriores conservan su dueño; habilitar administración no los mueve al espacio de esta cuenta.

## Interrupciones y errores

La comprobación del propietario usa una solicitud única, solo en memoria, con una vigencia monotónica de **120 segundos**. Rechaza resultados sin una solicitud viva, caducados o de una sesión terminada. El último gesto y la escritura vuelven a comprobar la sesión, la vigencia y que la pantalla siga en primer plano y el dispositivo desbloqueado.

Si no se puede confirmar una escritura, usa **Comprobar asignación guardada**. La app relee el resultado antes de ofrecer otra activación y conserva un alta que ya se haya confirmado. Un archivo corrupto o pendiente no se trata como una configuración vacía: bloquea el acceso y se conserva, sin reparación o reemplazo automáticos. No borres datos o reinstales como paso para resolverlo; conserva el mensaje para que el equipo revise el caso.

## Android y entrega al equipo

La confirmación utiliza [KeyguardManager.createConfirmDeviceCredentialIntent](<https://developer.android.com/reference/android/app/KeyguardManager#createConfirmDeviceCredentialIntent(java.lang.CharSequence,%20java.lang.CharSequence)>), disponible desde API 21 y obsoleta desde API 29. Android recomienda actualmente BiometricPrompt; este recorrido nativo permite mantener el mínimo declarado del proyecto en **API 26** sin añadir otra biblioteca. Requiere soporte de bloqueo seguro y un componente del sistema resoluble. Declarar esa compatibilidad no acredita haber ejecutado el recorrido en API 26.

El cierre de fase 40 aprobó **1360 casos distintos, 76 nuevos**, sin fallos: 1304 JVM y 56 Android. Incluyó **confirmación real del PIN nativo de Android en el emulador API 37**, alta y consulta de la propia cuenta con archivos de prueba aislados. Esto no activa por sí mismo la administración en tu teléfono ni acredita ejecución API 26. El plan alcanza **22/28 = 78.6%**, con Administración en **2/4** por consulta y roles/permisos; quedan seis criterios de seguimiento, cambio/historial y notificaciones. [Detalle de validación](VALIDACION.md).

El código y esta guía se preparan para la rama **`development/administracion-local`** y un **PR en borrador de GitHub**, revisables por el equipo; la subida todavía debe confirmarse. La publicación prevista no integra estos cambios a main ni genera APK, tag o Release. La última APK oficial sigue siendo **0.6.1**. El candidato 0.6.2 archivado es anterior a las fases 38–40 y deberá prepararse de nuevo cuando se autorice la siguiente entrega.

[Fase 40 y criterios](FASE_40.md) · [Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Validación](VALIDACION.md)
