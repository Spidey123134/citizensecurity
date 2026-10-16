# Fase 40 — administración local y consulta protegida

**15 de octubre de 2026 · desarrollo Unreleased.** Continúa **4.6 Administración** del PDF. Después de dejar la asignación pendiente en los cortes anteriores, el usuario autorizó «elige cuenta local pero documéntala bien en GitHub». La cuenta elegida es **`admin_local`**, con registro normal y contraseña propia de 15 a 128 caracteres. No se crea automáticamente una contraseña ni se publica una; `admin/admin` conserva su acceso académico.

## Resultado construido

Desde Inicio, `admin_local` tiene **Administración local**. La primera activación requiere confirmar al propietario mediante la credencial nativa de bloqueo de Android y después pulsar **Activar administración local**. La solicitud usa un identificador único y un plazo monotónico de 120 segundos; no persiste la prueba de autenticación ni concede permisos al volver automáticamente desde Android.

La asignación se guarda por UUID, operación y fecha en un archivo privado de la instalación. Antes del commit se comprueban sesión, prueba vigente, primer plano y dispositivo desbloqueado. La lectura del resultado confirma el recibo exacto. Un resultado incierto exige **Comprobar asignación guardada**; no se reemplaza una asignación existente ni se borran o reparan automáticamente archivos corruptos, `.new` o `.bak` pendientes.

La consulta visible utiliza el binding de fase 39 y la política de permisos de fase 38. Abre lista, filtros combinados de tipo/estado/prioridad y búsqueda directa por folio completo; muestra detalle e historial coherentes, con páginas de 50. Conserva una sola página y hasta 50 posiciones anteriores, sin acumular reportes ni leer fotografías. Autorización y sesión se comprueban en IO antes y después de leer; la sesión también se comprueba al gesto y antes de mostrar el resultado. Una revocación retira los datos de la pantalla y desactiva el acceso retenido.

Actualizar vuelve al inicio; reintentar conserva exactamente la consulta fallida. Las respuestas tardías y los gestos de una página anterior no sustituyen la lectura vigente. Giro conserva la consulta en el ViewModel; un proceso nuevo exige comprobar y releer, sin restaurar una prueba como permiso.

La [guía de administración local](ADMINISTRADOR_LOCAL.md) documenta las cuentas, los pasos para el compañero, el comportamiento ante errores y el límite al espacio de esa cuenta en ese teléfono. El alta no reasigna reportes históricos, consulta datos de otros usuarios o sincroniza con un servidor. No se representa a una institución ni se acredita recepción de reportes por autoridades.

## Relación con el PDF

**22/28 = 78.6% de construcción**, con los mismos 28 criterios del PDF. **4.6 Administración alcanza 2/4**: consulta administrativa visible y roles/permisos de acceso reales. La comprobación integrada cubrió el alta mediante la confirmación nativa con PIN en el emulador y la consulta autorizada del espacio de la propia cuenta.

Los dos hitos acreditados corresponden a ese recorrido local y a sus límites por UUID, sesión y teléfono. No se conceden por elegir un nombre, preparar contratos o sumar pruebas. **Quedan seis criterios originales**: uno de seguimiento de 4.4, dos administrativos de 4.6 y tres de notificaciones de 4.7.

Permanecen para la siguiente fase los controles de **cambio de estado autorizado** y su **historial administrativo completo**, utilizando la coordinación y recuperación durable ya construidas. Por eso también continúan pendientes el criterio de seguimiento de 4.4 y los otros tres de 4.7: todavía no hay un recorrido visible de cambios que cierre su integración con los avisos. La consulta por sí sola no añade un cambio, una notificación o un punto extra al plan.

## Validación y límites

**1360 casos distintos aprobados, 76 definiciones nuevas**, sin fallos: **1304 JVM** —205 de dominio, 148 de datos y 951 de app— y **56 Android API 37** —36 de datos y 20 de app—. Los nuevos son 56 JVM y 20 Android; repeticiones y regresiones no se vuelven a sumar como novedades. Lint app termina sin errores; los avisos y el desglose de ejecuciones se registran en [Validación](VALIDACION.md).

Las comprobaciones cubrieron alta, permiso, revocación, caducidad, doble gesto, recibo incierto, filtros, paginación, folio, coherencia del historial y protección ante respuestas tardías. El recorrido nativo utilizó el **diálogo real de Android y su PIN en el emulador**, con cuentas y archivos de prueba aislados; no sustituyó el resultado de autenticación por uno aceptado artificialmente. Eso no acredita una activación en el teléfono del usuario, ejecución API 26 o muerte completa del proceso. El recibo de [Validación](VALIDACION.md) conserva las ejecuciones y sus límites.

La API nativa elegida permite mantener el mínimo declarado API 26; [Android documenta createConfirmDeviceCredentialIntent](<https://developer.android.com/reference/android/app/KeyguardManager#createConfirmDeviceCredentialIntent(java.lang.CharSequence,%20java.lang.CharSequence)>) como disponible desde API 21 y obsoleta desde API 29, con recomendación de BiometricPrompt. El dispositivo debe tener soporte de bloqueo seguro y una actividad del sistema disponible. La compatibilidad declarada no sustituye la prueba en cada fabricante.

## Estado de publicación

Código y documentación se preparan para la rama **`development/administracion-local`** y un **PR en borrador de GitHub**, para revisión del equipo, sin integrar main. Este documento no acredita que la subida ya se haya realizado. La publicación de APK, tag y Release continúa pospuesta por el usuario. La APK oficial **0.6.1** no incluye esta fase. El candidato 0.6.2/código 30 archivado precede a las fases 38–40 y requiere una nueva preparación para la próxima entrega autorizada.

[Guía para el equipo](ADMINISTRADOR_LOCAL.md) · [Fase 39](FASE_39.md) · [Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Fases](FASES.md)
