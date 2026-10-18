# CITIZENSECURITY

## Desarrollo local Unreleased — fase 45: ayuda del resultado

Se refina el recorrido guiado con un acceso de ayuda de **48 dp** junto a **Resultado del incidente**. Añade cuatro pasos sobre Cerrado y Solucionado, actor/rol/fecha/motivo, revisión y confirmación separadas e historial/recibo: **siete temas/24 pasos en desarrollo**, frente a los seis/20 de la oficial 0.6.3. La guía es voluntaria, general y no ejecuta acciones ni copia datos privados. [Alcance y validación](docs/FASE_45.md).

**18 Android distintos ejecutados y aprobados, dos nuevos**, sin fallos, errores u omitidos. La guía conserva revisión/UUID, paso al girar y datos SQLite, sin activar GPS; compilación y lint sin errores. Este corte queda **local Unreleased**, sin otra Release o APK pública y sin sumar cifras históricas como pruebas de esta fase. El **28/28 = 100% de construcción funcional local** del PDF se conserva; aceptación física aparte. La entrega oficial descrita abajo mantiene sus fuentes, archivos e identidad.

## Entrega oficial 0.6.3 — fases 43–44 e integración con GitHub

**18 de octubre de 2026 · Android 0.6.3 / código 31 · Release oficial publicada y descarga verificada.** Integra las novedades de GitHub con el **recorrido guiado** de seis temas/20 pasos y el **resultado del incidente** declarado por autor o administrador autorizado, con motivo e historial de quién lo marcó. Cerrado no implica Solucionado. **1675 casos distintos aprobados: 1121 ejecutados y 554 recibos intactos reutilizados**; compilación/lint sin errores y actualización real desde 0.6.2 comprobada, conservando datos, foto/contactos e historial y migrando esquema 4 → 5. El plan conserva **28/28 = 100% de construcción funcional local**; aceptación física e integración institucional requieren evidencias propias.

[Release v0.6.3](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.3) · [Descargar APK](https://github.com/Spidey123134/citizensecurity/releases/download/v0.6.3/citizensecurity-0.6.3-academico.apk) · [Checksum](https://github.com/Spidey123134/citizensecurity/releases/download/v0.6.3/SHA256SUMS-0.6.3.txt) · [Cambios y validación](docs/ENTREGA_0_6_3.md) · [Probar y abrir en Android Studio](docs/PROBAR_0_6_3.md). En Studio abre la raíz del proyecto y selecciona **app**, con módulo **CITIZENSECURITY.app**; la configuración compartida evita ejecutar los laboratorios técnicos. Actualiza encima de la app anterior, conservando firma y datos. APK y checksum descargados de GitHub se verificaron contra los archivos finales.

Los apartados inferiores conservan **el estado histórico de cada corte**: las restricciones locales de fases 43–44 se sustituyen para esta entrega por la autorización expresa de publicar. Los refinamientos posteriores del tutorial se registrarán por separado.

## Desarrollo local Unreleased — fase 44: resultado del incidente

La ampliación **E12** añade **Sin confirmar, Solucionado y Sigue sin solucionarse**, independientes del seguimiento Reportado → En revisión → Atendido → Cerrado. El autor real o el administrador local autorizado pueden declarar un resultado mediante motivo, revisión y confirmación separadas. El historial identifica UUID, rol, fecha, resultado anterior/nuevo y motivo; corregir conserva las declaraciones anteriores. Un reporte antiguo Cerrado no se considera solucionado por inferencia. [Recorrido, permisos y límites](docs/FASE_44.md).

**1662 casos distintos aprobados / 108 nuevos**, sin fallos, errores u omitidos finales: 1451 JVM y 211 Android en emulador propio API 37. Incluye ocho recorridos nuevos con cuentas/roles/SQLite reales; revisar y girar no escriben, una ventana pausada no confirma y la recuperación exige otro gesto. Compilación y lint sin errores; [desglose y límites](docs/FASE_44.md). El porcentaje del PDF permanece en **28/28 = 100% de construcción funcional local**; E12 no agrega criterios ni aceptación física. Trabajo **local Unreleased**, conservando el recorrido guiado de fase 43 y la oficial **0.6.2 / código 30**, sin push, main, APK pública o Release por esta continuación.

## Desarrollo local Unreleased — fase 43: recorrido guiado

Un icono **?** permite abrir instrucciones breves de la pantalla actual: Inicio, Reportar, Revisión, Reportes, Detalle y Mapa. Muestra un paso por vez, con Anterior, Siguiente, Listo y Cerrar; se puede consultar otra vez sin ejecutar las acciones explicadas. Reduce la orientación repetida de Inicio y conserva los avisos de seguridad y decisiones de permiso/consentimiento. **26 casos Android distintos aprobados, ocho nuevos**, en emulador propio API 37. [Fase 43 y límites de validación](docs/FASE_43.md).

Es la mejora adicional **E11**; el PDF conserva **28/28 = 100% de construcción funcional local**. Este trabajo queda **Unreleased y local**, sin publicar APK, actualizar GitHub/main o cambiar la oficial **0.6.2 / código 30**. Login y loading del compañero se conservan.

## Entrega acumulada 0.6.2 — fases 35–42

**16 de octubre de 2026 · Android 0.6.2 / código 30 · Release oficial publicada.** Esta entrega reúne **todo el desarrollo posterior a la oficial 0.6.1: fases 35, 36, 37, 38, 39, 40, 41 y 42**. Incluye Actividad de reportes, avisos voluntarios con revisión manual, mensaje revisado para un contacto, corrección de las fotografías del borrador y administración local con consulta, cambios de estado e historial reales. Las mejoras anteriores de cámara/galería, PDF, contactos, ayuda, GPS y mapa siguen incluidas.

**`admin_local` se registra con una contraseña propia de 15–128 caracteres**. La primera asignación requiere el bloqueo nativo de Android y otro gesto para activar; después permite consultar y cambiar estados del espacio de esa cuenta en ese teléfono. `admin/admin` conserva acceso académico. Los estados avanzan **Reportado → En revisión → Atendido → Cerrado** con motivo, revisión, confirmación y recuperación del mismo recibo si se interrumpe. [Guía de administración](docs/ADMINISTRADOR_LOCAL.md).

**28/28 = 100% de construcción funcional local del plan original.** La aceptación física completa y la seguridad/integración de producción se comprueban aparte; un estado o aviso no confirma intervención de una autoridad. El corte final de fase 42 acredita **1431 casos distintos**, con 1379 JVM —205 recibos intactos reutilizados y 1174 ejecutados— y 52 Android API 37 ejecutados; no se suman los totales de fases anteriores como pruebas distintas de la entrega. [Validación](docs/VALIDACION.md).

[Cambios completos de 0.6.2](docs/ENTREGA_0_6_2.md) · [Pasos para probar](docs/PROBAR_0_6_2.md) · [Release 0.6.2](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.2) · [Descargar APK](https://github.com/Spidey123134/citizensecurity/releases/download/v0.6.2/citizensecurity-0.6.2-academico.apk) · [Checksum SHA-256](https://github.com/Spidey123134/citizensecurity/releases/download/v0.6.2/SHA256SUMS-0.6.2.txt). Instala la APK **encima de la instalación anterior, sin desinstalar ni borrar datos**. La Release y ambos archivos están publicados; el candidato interno antiguo de solo fases 35–37 queda separado del binario final.

Los bloques siguientes conservan el estado y las cifras **históricos** de cada corte. Sus marcas **Unreleased**, referencias a 0.6.1 como oficial o restricciones de publicación describen el momento en que se trabajaron; las **fases 35–42 se agrupan ahora en 0.6.2** por solicitud expresa del usuario.

## Desarrollo local Unreleased — fase 42: del cambio confirmado a sus avisos

El recibo administrativo ofrece **Ver actividad de esta cuenta** y **Revisar avisos de cambios**. Cada botón abre la pantalla correspondiente del mismo espacio; conserva el sello interno de sesión y no transmite el motivo, el folio o el cuerpo del cambio en el Intent. No reconoce la solicitud pendiente ni activa avisos o revisa eventos al entrar. Puedes volver al recibo para cerrarlo mediante su acción propia. [Recorrido de fase 42](docs/FASE_42.md) · [Guía para el equipo](docs/ADMINISTRADOR_LOCAL.md).

Los avisos mantienen la preferencia voluntaria por cuenta, el permiso/canal de Android y **Revisar cambios** como gesto separado. Solo las transiciones reales guardadas en SQLite pueden producir el resumen general; sus UUID de operación se registran después de la aceptación de Android para evitar repetirlos. Desactivar conserva esos registros. También se refuerza la conservación de archivos de avisos con `.new` o `.bak`: una lectura no restaura ni limpia resultados inciertos.

**Fase 42 validada el 16 de octubre de 2026: 28/28 = 100% de construcción funcional local del plan.** Notificaciones alcanza **4/4** al comprobar eventos de cambios autorizados, entrega del resumen y recorrido integrado. Quedan **0 criterios funcionales locales** pendientes; la aceptación física completa y la preparación de una entrega se registran aparte.

**1431 casos distintos aprobados, 14 nuevos**, sin fallos/errores/omitidos finales: 1379 JVM —205 recibos intactos reutilizados y 1174 ejecutados— y 52 Android API 37 ejecutados en este corte. Lint sin errores/22 avisos existentes por variante de app; datos sin incidencias. [Desglose y límites](docs/VALIDACION.md).

Este corte permanece **local**: no actualiza GitHub/main o el [PR #30 de fase 40](https://github.com/Spidey123134/citizensecurity/pull/30), ni publica APK/tag/Release. La descarga oficial **0.6.1** conserva su contenido. El cierre se refiere a construcción funcional local del plan; no acredita aceptación física final, atención institucional o seguridad de producción. Los bloques inferiores conservan los resultados de cada fase.

## Desarrollo local Unreleased — fase 41: cambios de estado reales

La cuenta **`admin_local` se registra en el teléfono** con su propia contraseña y requiere la asignación de fase 40; no se crea automáticamente. Desde un folio de la consulta administrativa, **Preparar cambio de estado** comprueba pendientes antes de consultar estado/revisión, escribir un motivo de 5–500 caracteres, revisar y confirmar mediante un segundo gesto. Solo permite avances consecutivos: **Reportado → En revisión → Atendido → Cerrado**.

El historial conserva actor por UUID, fecha, motivo, secuencia y operación. Recuperar busca el recibo sin repetir la escritura; reintentar reutiliza la misma solicitud y cerrar el recibo exige reconocerlo. Sesión, permisos, revisión y primer plano se revalidan antes de guardar. Los estados son locales y no confirman atención institucional. [Guía actualizada](docs/ADMINISTRADOR_LOCAL.md) · [Fase 41](docs/FASE_41.md).

**25/28 = 89.3%**: Consulta y Administración alcanzan **4/4**; restan tres criterios de Notificaciones. **1488 casos distintos aprobados/103 nuevos**, sin fallos finales; incluye recorrido nativo con SQLite, rol y diario reales. [Validación y límites](docs/VALIDACION.md). **Este corte permanece local**, sin actualizar GitHub/main, el [PR #30 de fase 40](https://github.com/Spidey123134/citizensecurity/pull/30) ni publicar APK/tag/Release. La descarga oficial sigue en **0.6.1**. Los bloques inferiores conservan sus cortes y cifras.

## Desarrollo Unreleased — fase 40: administración local

Se eligió **`admin_local`** con autorización del usuario. Se registra en el teléfono con una contraseña propia de 15 a 128 caracteres; no hay contraseña administrativa predeterminada ni publicada. **Inicio → Administración local** confirma al propietario con el bloqueo nativo de Android y exige un segundo gesto para activar. `admin/admin` mantiene su acceso académico.

La consulta protegida muestra reportes del espacio de esa cuenta, filtros de tipo/estado/prioridad, folio e historial, en páginas de 50. **22/28 = 78.6%**: Administración alcanza **2/4**, con consulta y roles/permisos comprobados. Quedan seis criterios: seguimiento, cambio e historial administrativo y tres de notificaciones. [Guía para el equipo](docs/ADMINISTRADOR_LOCAL.md) · [Fase 40](docs/FASE_40.md).

**1360 casos distintos aprobados, 76 nuevos**, sin fallos; incluyen la confirmación nativa con PIN real en el emulador API 37 y la consulta de la propia cuenta. No equivale a una activación en el teléfono del usuario. Código y guía se preparan para la rama `development/administracion-local` y un PR en borrador de GitHub, sin integrar main ni publicar APK/tag/Release. La APK oficial sigue en **0.6.1**; el candidato 0.6.2 archivado precede a las fases 38–40. Los bloques siguientes conservan el alcance y las cifras de sus respectivos cortes.

## Antecedente — fase 39: borrador y sesión administrativa

La revisión del borrador separa datos, lugar y fotografía en tarjetas legibles. La imagen completa se ajusta a un marco de 240 dp para que una foto vertical no invada el texto o las acciones; el archivo y el guardado conservan su funcionamiento. [Fase 39](docs/FASE_39.md).

Continúa 4.6 del PDF al ligar la consulta administrativa al espacio y la sesión capturados: salir e ingresar otra vez con la misma cuenta invalida el acceso anterior. Consulta/cambio mantienen permisos separados y denegados por defecto; asignación y panel pendientes, con `admin/admin` académico. **20/28 = 71.4% permanece**. Trabajo local, sin push ni publicación; la APK oficial sigue en **0.6.1** y el candidato 0.6.2 archivado precede a las fases 38/39.

## Desarrollo local Unreleased — fase 38: permisos administrativos separados

La base administrativa distingue **consultar reportes (`READ_REPORTS`)** de **cambiar estados (`CHANGE_STATUS`)**. La consulta por folio, lista paginada e historial usa dueño/actor y fuentes capturados, abre sus fuentes solo después de autorizar y vuelve a comprobar vigencia, permiso y cancelación al terminar. El escritor exige el permiso de cambio también dentro de la transacción antes del commit; consultar no autoriza modificar ni recuperar un recibo de cambio. [Fase 38](docs/FASE_38.md).

La asignación administrativa y su interfaz siguen pendientes: `admin/admin` conserva acceso académico. **20/28 = 71.4% permanece.** Este avance queda local, sin push, tag o Release. La preparación 0.6.2/código 30, su candidato y sus notas se guardan como borradores locales con numeración tentativa; la APK oficial sigue siendo **0.6.1**.

## Desarrollo Unreleased — fase 37: mensaje para un contacto

Desde el detalle de un folio, **Preparar mensaje para un contacto** permite elegir un contacto guardado, decidir si se incluye referencia/punto (inicialmente desmarcado) y revisar el texto completo. Otro gesto abre **Mensajes SMS**; el envío se confirma allí. Si cambian el reporte o los contactos, se exige revisar nuevamente. Se puede guardar un contacto desde Ayuda y regresar a la misma pantalla.

Sin GPS nuevo o archivos adjuntos; la ficha PDF conserva su acceso separado. El operador puede cobrar el SMS y abrir la composición no confirma entrega. [Fase 37](docs/FASE_37.md) · [Mejoras útiles E06](docs/MEJORAS_UTILES.md). **20/28 = 71.4%** permanece: es una ampliación de utilidad. Código Unreleased; la descarga oficial sigue en **0.6.1**.

## Desarrollo Unreleased — fases 35 y 36: actividad y avisos

Desde **Inicio → Actividad de reportes** se consultan guardados y cambios de estado del espacio actual. **Toda la actividad** incluye creaciones reales; **Cambios de estado** muestra únicamente transiciones registradas. Cada evento abre su folio; páginas de 50, anterior/siguiente, actualización desde el inicio y recuperación de errores conservan sesión y consulta al girar.

La base de reportes migra aditivamente a esquema 4 para añadir el índice de actividad, conservando fotos, folios e historial. Los históricos sin eventos permanecen sin actividad inventada. El guardado local se distingue de un cambio posterior y de una recepción institucional. [Fase 35](docs/FASE_35.md) · [Validación](docs/VALIDACION.md).

En **Actividad → Avisos de cambios de estado** puedes activar el permiso y la preferencia de tu acceso. **Revisar cambios** busca transiciones registradas y publica un resumen general de Android, sin datos privados; repetir la revisión evita avisar de las mismas operaciones. Es una revisión manual desde la app, sin ejecución en segundo plano. Desactivar conserva su registro y retira el resumen. [Fase 36](docs/FASE_36.md).

El avance del PDF llega a **20/28 = 71.4%** por permisos/preferencias comprobados. La asignación administrativa sigue pendiente: no se crean cambios ficticios ni se concede un rol a `admin/admin`. La descarga oficial siguiente corresponde al corte 0.6.1/fases 29–34; las fases 35/36 permanecen como código Unreleased. [Avance vigente](docs/AVANCE.md).

## Entrega oficial 0.6.1

**14 de octubre de 2026 · Android 0.6.1 / código 29.** La entrega agrupa **fases 29–34**: ficha PDF revisada para compartir desde un folio, hasta cinco contactos manuales por cuenta, Ayuda inmediata sin login/GPS, atajos del icono y mosaico voluntario de Ajustes rápidos. El reporte permite **tomar una foto con la cámara o elegirla de la galería**; cancelar/fallar conserva la fotografía anterior y los campos. La base durable de cambios de estado queda incluida sin activar un panel administrativo o conceder roles.

[Release y APK 0.6.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.1) · [Cambios y límites](docs/ENTREGA_0_6_1.md) · [Pasos para probar en tu teléfono](docs/PROBAR_0_6_1.md). La APK conserva el mismo paquete y certificado académico; instálala encima de la anterior, sin desinstalar ni borrar datos. La ficha se comparte por decisión explícita y los accesos de ayuda abren el marcador: no realizan llamadas o envíos automáticos.

**1237 casos distintos aprobados, 307 definiciones nuevas respecto a la APK 0.6.0**, cero fallos/errores/omitidos finales: 1074 JVM y 163 Android API 37. Lint sin errores, con 22 avisos por variante de app y ninguna incidencia de datos. Firma, paquete, versión y checksum del APK comprobados; no se atribuye una prueba nueva de instalación como actualización, cámara física, muerte completa del proceso o ejecución física API 26. [Validación](docs/VALIDACION.md).

En ese corte el PDF quedó en **19/28 = 67.9%**, con nueve criterios pendientes de seguimiento autorizado, administración y notificaciones. Cámara/ayuda amplían utilidad sin sumar criterios ya completos. La asignación administrativa sigue pendiente y `admin/admin` mantiene acceso académico; login/loading del compañero y datos históricos se conservan. El porcentaje del código posterior se registra arriba. [Plan](docs/PLAN_DEL_PROYECTO.md) · [Mejoras útiles](docs/MEJORAS_UTILES.md).

## Antecedente — preparación Unreleased de 0.6.1

Los siguientes bloques conservan el estado y los recuentos de cada corte de desarrollo. Sus marcas **Unreleased** corresponden a aquella preparación; las fases 29–34 ahora se agrupan en 0.6.1. No se suman sus ejecuciones como si fueran pruebas distintas del cierre.

## Desarrollo Unreleased — cámara para el reporte

**14 de octubre de 2026.** El formulario permite **Elegir de la galería** o **Tomar foto con la cámara**. Al volver se prepara y muestra la copia antes de revisar/guardar; cancelar o fallar conserva la foto anterior. Aperturas separadas evitan resultados cruzados; no se solicitan nuevos permisos ni se obtiene GPS por capturar. [Fase 33](docs/FASE_33.md) · [Mejoras útiles E01–E10](docs/MEJORAS_UTILES.md).

Código Unreleased en main; la APK oficial **0.6.0 / código 28** conserva el corte anterior. Avance del PDF **19/28 = 67.9%**, porque la fotografía ya estaba acreditada. [Pruebas y límites](docs/VALIDACION.md); cámara física se prueba por separado.

## Desarrollo Unreleased — utilidad y continuación del PDF

**14 de octubre de 2026.** [Mejoras útiles](docs/MEJORAS_UTILES.md) documenta **E01–E09**, propuestas adicionales al PDF/cliente con origen, prioridad, estado y aceptación: ficha/evidencia, contactos, atajos, mensaje revisado, varias fotos, uso institucional y datos por zonas. Ficha PDF, contactos y ayuda/atajos tienen implementación validada en código Unreleased; los demás estados se identifican individualmente.

La **fase 32** añade **Ayuda inmediata** pública: mantener pulsado el icono en un launcher compatible abre la ayuda sin login/GPS o datos privados. Desde esa pantalla se puede pedir un acceso en Inicio por un gesto explícito si el teléfono lo admite; la petición trabaja fuera del hilo de interfaz. Tocar un número abre el marcador para que tú decidas llamar; pedir fijar el acceso no confirma por sí mismo que el launcher lo creó. Las pruebas API 37 no realizaron llamadas ni aprobaron un icono fijado real. No se modifican botones físicos o SOS. [Recorrido y límites](docs/FASE_32.md).

La **fase 31** continúa seguimiento/administración del PDF: conserva una solicitud de cambio de estado en un diario, comprueba su mismo recibo después de una interrupción y requiere reconocimiento explícito antes de limpiar. Es una base interna; la asignación administrativa y el recorrido visible siguen pendientes, y `admin/admin` mantiene acceso académico. [Contrato](docs/FASE_31.md).

[Plan](docs/PLAN_DEL_PROYECTO.md) y [Avance](docs/AVANCE.md) mantienen **19/28 = 67.9%**, con nueve criterios originales pendientes. Las pruebas Android de la entrega 0.6.0 ya se ejecutaron; las comprobaciones nuevas del corte están en [Validación](docs/VALIDACION.md). Código **Unreleased**, Android **0.6.0 / código 28**; la APK/tag/Release publicada 0.6.0 conserva su corte y no incluye las mejoras siguientes. [Fases](docs/FASES.md).

**Cierre fases 31/32: 1062 casos distintos aprobados, 112 nuevos**, cero fallos/errores/omitidos finales. Lint sin errores; pruebas nativas nuevas en emulador API 37, sin llamadas o aprobación de un icono fijado real. Se corrige la comprobación de vigencia después de consultar bloqueo y antes de pedir el fijado. [Desglose y límites](docs/VALIDACION.md).

## Antecedente — fases 29 y 30

## Desarrollo Unreleased — ficha del incidente y contactos

El proyecto académico en México incorpora dos recorridos para usar la información del incidente. **Inicio → Ayuda y contactos** abre el marcador de emergencias o de un contacto de confianza; puedes guardar hasta cinco teléfonos manualmente en tu espacio local. **Inicio → Reportes guardados → folio → Ficha del incidente** prepara un PDF con los datos del reporte, revisando si incluyes ubicación y foto. Después puedes abrir el selector de Android y elegir a quién compartirlo.

La llamada la decides en el marcador. El PDF conserva datos locales y una copia normalizada de la foto si la eliges; permite entregarlos voluntariamente a otra persona o autoridad, sin acreditar recepción oficial ni verificación del hecho. Los accesos de México se basan en [SSPC: diferencia entre 089 y 911](https://www.gob.mx/sspc/es/articulos/sabes-cual-es-la-diferencia-entre-los-numeros-088-089-y-911). El alcance práctico y sus límites están en [Fase 29](docs/FASE_29.md) y [Fase 30](docs/FASE_30.md).

Estas mejoras son **código Unreleased** y todavía no están en la APK publicada 0.6.0. No se cambia versión Android, tag o Release; 0.6.0 / código 28 sigue vigente. El plan original conserva **19/28 = 67.9%** y la asignación administrativa pendiente. Para trabajar con el código conserva tus cambios y actualiza main con `git fetch origin`, `git switch main` y `git pull --ff-only origin main`; en Android Studio selecciona **app**. [Plan](docs/PLAN_DEL_PROYECTO.md) · [Validación](docs/VALIDACION.md).

## Antecedente — entrega 0.6.0

## Entrega 0.6.0

**0.6.0 / código Android 28** agrupa el avance de las fases 21 a 27 y una base pequeña de la fase 28. Incluye cuentas locales con datos propios por UUID, lista por páginas con recuperación de criterios, fechas/prioridad en el mapa, lista de la misma zona e historial real consultable por folio. Se conservan datos, firma académica, GPS 5 km y login/loading del compañero.

La asignación administrativa continúa pendiente por elección del usuario. El servicio de cambios está protegido y la base nueva conserva la identidad de una solicitud incierta; todavía no se conecta un escritor administrativo ni se conceden roles a admin/admin. Faltan permiso/actor y recuperación durable para ese recorrido. **19/28 = 67.9% de construcción**, mismos criterios del PDF.

[Release y APK 0.6.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.0) · [Cambios y límites](docs/ENTREGA_0_6_0.md) · [Pasos para probar](docs/PROBAR_0_6_0.md). **1164 casos distintos aprobados**, incluidos 271 Android; cero fallos, errores u omitidos. Compilación, lint y firma académica verificados. [Validación](docs/VALIDACION.md) · [Base de fase 28](docs/FASE_28.md).

Para actualizar el código conserva tus cambios y ejecuta `git fetch origin`, `git switch main` y `git pull --ff-only origin main`; selecciona **app** en Android Studio. Instala la APK 0.6.0 sobre la anterior, sin desinstalar ni borrar datos. Los siguientes bloques conservan los cortes anteriores; sus etiquetas Unreleased describen la preparación que se agrupa en esta entrega.

## Antecedente — preparación de 0.6.0

## Preparación de 0.6.0 — Unreleased

Ahora agrupamos los avances en **main** sin generar APK ni publicar una Release por cada fase. La próxima entrega prevista es **0.6.0**; la última APK publicada sigue siendo [0.5.7](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.7). El código nuevo de main todavía no está en esa APK.

**Fase 21:** en **Inicio → Mapa de reportes**, **Desde** y **Hasta** filtran las fechas del incidente. Puedes escribir `dd/MM/aaaa` o elegirlas en el calendario. Pulsa **Aplicar fechas** y luego **Consultar esta zona**; los marcadores respetan fechas, tipo, estado y límite. Una fecha inválida explica el error y conserva la consulta anterior. **Ver lista de esta zona** permite abrir los folios de la misma muestra del mapa, indicando si quedan más coincidencias. La lista general conserva su búsqueda por texto.

**Fase 22:** lista y mapa permiten filtrar **prioridad Alta, Media o Baja**, combinada con los demás criterios; es la prioridad indicada en el reporte. En el mapa elige la prioridad y pulsa **Consultar esta zona**. En la lista se aplica sobre los reportes cargados y **Limpiar búsqueda / Mostrar todos** también la restablecen. La búsqueda trabaja en segundo plano y muestra carga/reintento para evitar bloquear la interfaz con un historial grande. Abrir la lista del mapa comprueba también que la cámara siga en la zona consultada.

**Fase 23:** **Reportes guardados** muestra hasta **50 coincidencias por página**, con **Más reportes** y **Anterior**. **Actualizar lista · volver al inicio** empieza de nuevo con tus filtros. La lista consulta el historial por lotes y mantiene una página en memoria. Si una búsqueda todavía tiene reportes por revisar, lo indica y permite **Continuar búsqueda**; una página sin coincidencias no se presenta como búsqueda terminada cuando queda recorrido. Volver del detalle o girar conserva el inicio de esa página y vuelve a leerla. Cada página es una consulta actual; vuelve al inicio para revisar los reportes guardados más recientemente.

**Fase 24:** la consulta puede retomar su página cuando Android restaura la app tras cerrar el proceso para recuperar memoria. Conserva criterios y posición, y relee los resultados. No garantiza esa recuperación si fuerzas el cierre o quitas la tarea. **Buscar reportes**, **Limpiar búsqueda** y **Mostrar todos** ahora vuelven al inicio incluso cuando repites los mismos valores. [Alcance y comprobaciones](docs/FASE_24.md).

**Fase 25:** **Crear cuenta** registra un usuario en este teléfono y vuelve al login. Usuario de 3 a 30 letras sin acentos, números o guion bajo y contraseña de 15 a 128 caracteres. Iniciar sesión verifica esa contraseña; Inicio muestra la cuenta y ofrece Cerrar sesión. Se conservan los layouts del compañero. La separación de reportes se completa en fase 26; académico no concede roles. [Alcance](docs/FASE_25.md).

**Fase 26:** cada cuenta guarda y consulta sus reportes, fotos, borradores y solicitudes pendientes. Lista, folios, mapa y posibles repetidos usan ese espacio. Al cambiar sesión se cierran las pantallas anteriores; volver a ingresar recupera sus archivos. Los históricos permanecen en modo académico, sin atribuirlos a una cuenta nueva. Tras perderse el proceso, una pantalla de cuenta requiere login. [Alcance y pruebas](docs/FASE_26.md).

**Fase 27:** abre un folio y pulsa **Consultar historial** para ver los eventos de estado registrados realmente. Los reportes nuevos guardan su evento de creación junto al reporte; los anteriores conservan su estado y avisan cuando no existe un historial completo. Puedes actualizar, consultar eventos anteriores o reintentar sin perder la ficha. El cambio de estado queda protegido y la asignación administrativa continúa pendiente por elección del usuario; `admin/admin` conserva únicamente acceso académico. [Alcance y pruebas](docs/FASE_27.md).

Se comprueba la lógica, SQLite del host y compilación sin generar APK. **19/28 = 67.9% de construcción**, Usuarios 4/4 y Consulta 3/4, identidad/aislamiento locales; aceptación Android pendiente para 0.6.0. Esta fase aprobó 100 casos, 37 nuevos, y compiló 15 nuevos casos Android sin ejecutarlos. El historial de lectura prepara el seguimiento; siguen pendientes el recorrido autorizado de cambios, administración con permisos y notificaciones. El compañero conserva la interfaz del login.

Para obtener el código nuevo conserva tus cambios, ejecuta `git fetch origin`, `git switch main` y `git pull --ff-only origin main`. En Android Studio trabaja con el módulo **app**. Los siguientes bloques describen las entregas publicadas anteriores.

## Antecedente 0.5.7

## Versión 0.5.7 — encontrar reportes por texto y fecha

[Release y APK 0.5.7](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.7) · código Android 27. En **Desarrollador → Inicio → Reportes guardados** puedes buscar una palabra o frase de la descripción o referencia, sin distinguir mayúsculas ni acentos. **Desde** y **Hasta** filtran la fecha del incidente; cada límite es opcional y el último día se incluye completo. Puedes escribir `dd/MM/aaaa` o pulsar **Elegir** para abrir el calendario.

Pulsa **Buscar reportes** para aplicar texto y fechas. Una fecha imposible o un intervalo invertido muestra el error y conserva la búsqueda aplicada. Los filtros de tipo/estado se combinan con ella; **Limpiar búsqueda** retira todos los filtros. Girar o abrir un detalle y volver conserva lo escrito y la búsqueda aplicada. Volver del detalle cancela su lectura pendiente, evitando que la lista siga bloqueada por ese folio.

Esta mejora corresponde a la lista; los filtros nuevos no se trasladan todavía al mapa. Se mantienen datos, fotografías, folios, borradores, GPS con radio de 5 km y login/loading del compañero. **Avance: 15/28 = 53.6%**, mismos criterios del PDF. [Fase 20](docs/FASE_20.md) · [Pasos de prueba](docs/PROBAR_0_5_7.md) · [Validación](docs/VALIDACION.md). Los reportes siguen en el teléfono; cuentas reales, seguimiento administrativo y notificaciones están pendientes.

Para actualizar conserva tus cambios y ejecuta `git fetch origin`, `git switch main`, `git pull --ff-only origin main`. Instala la APK sobre la anterior, sin desinstalar ni borrar datos.

**En Android Studio selecciona la configuración/módulo `app` antes de Run.** `verification:maps` es una aplicación técnica separada, marcada `testOnly`, que puede aparecer como Laboratorio al ejecutarla desde Studio. La APK oficial de CitizenSecurity no instala ese laboratorio automáticamente. Para probar el producto usa `app` o la APK de la Release.

## Antecedente 0.5.6

## Versión 0.5.6 — inicio funcional

[Release y APK 0.5.6](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.6) · código Android 26. La pantalla verde que abre **Desarrollador** ahora es **Inicio**, con **Reportar**, **Reportes guardados** y **Mapa de reportes**. Cada acceso abre el recorrido real; ya no contiene formulario ficticio, ejemplos de coordenadas ni guardado de demostración.

El inicio indica si tienes un borrador guardado o un intento cuyo resultado debe comprobarse. Abrirlo solo consulta esa información: no guarda, restaura campos ni solicita GPS. Los errores se explican y permiten reintentar sin borrar el contenido. Fotos, folios, radio de 5 km y aviso de posibles repetidos siguen disponibles.

[Pasos para probar](docs/PROBAR_0_5_6.md) · [Fase 19](docs/FASE_19.md). **Avance del PDF: 15/28 = 53.6%**. Se acredita el recorrido físico de ubicación por la confirmación manual del usuario del 11 de octubre de 2026; no se atribuye guardado físico nuevo ni evidencia técnica no proporcionada. Cuentas reales, seguimiento administrativo y notificaciones siguen pendientes. Los reportes permanecen en el teléfono, sin envío a autoridades.

Instala encima de la APK anterior, sin desinstalar ni borrar datos. Para actualizar el código conserva tus cambios y ejecuta `git fetch origin`, `git switch main`, `git pull --ff-only origin main`. El acceso académico y login/loading del compañero se conservan.

## Antecedentes históricos del corte anterior

## Versión 0.5.5 — revisar posibles reportes repetidos

[Release y APK 0.5.5](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.5) · código Android 25. **Desarrollador → Nuevo reporte · guardado local → Revisar borrador → Guardar reporte** revisa si este teléfono conserva incidentes del mismo tipo a 200 metros y hasta 2 horas antes o después de la hora indicada.

El aviso permite abrir el folio anterior, volver conservando el borrador o elegir **Es otro incidente, guardar**. Una consulta fallida o incompleta se explica y necesita tu decisión; no se interpreta como ausencia de incidentes. Cada guardado mantiene la comprobación del GPS a 5 km. Fotos y recuperación del borrador siguen disponibles.

[Probar la APK](docs/PROBAR_0_5_5.md) · [Fase 18](docs/FASE_18.md). **Avance: 14/28 = 50%** con los mismos criterios del PDF; esta mejora refuerza reportes y consulta sin inventar otro hito. Los reportes siguen locales, sin cuentas reales ni envío a autoridades. La aceptación física final del GPS continúa pendiente.

Instala encima de la versión anterior, sin desinstalar ni borrar datos. Para actualizar el código conserva tus cambios: `git fetch origin`, `git switch main`, `git pull --ff-only origin main`.

## Antecedentes del corte anterior

## Versión 0.5.4 — fotografías y borrador recuperable

[Release y APK 0.5.4](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.4) · código Android 24. **Desarrollador → Nuevo reporte · guardado local** permite adjuntar una fotografía opcional, verla en la revisión y abrirla junto al folio guardado. La app conserva una copia privada que sigue disponible aunque desaparezca la foto de la galería.

Desde la revisión puedes **Guardar borrador para continuar después**. Al volver, recuperas esa copia por confirmación; conserva los campos y la foto. Guardar el borrador no crea un reporte ni obtiene GPS. Para guardar el reporte necesitas renovar la ubicación precisa y confirmar el punto dentro de 5 km. Una copia ya convertida en reporte abre su mismo folio.

[Cómo instalar y probar en Android](docs/PROBAR_0_5_4.md) · [Fase 16: fotografías](docs/FASE_16.md) · [Fase 17: recuperación](docs/FASE_17.md). Construcción: **14/28 hitos = 50%**, con los mismos criterios del PDF. Los reportes permanecen en este teléfono; cuentas reales, administración y avisos a autoridades siguen pendientes.

Instala el APK académico sobre la versión anterior, sin desinstalar ni borrar datos. Para actualizar el código, conserva tus cambios y usa `git fetch origin`, `git switch main`, `git pull --ff-only origin main`.

## Antecedentes del corte anterior
## Versión 0.5.3 — reporte, folio, consulta y mapa

[Release oficial v0.5.3](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.3) · código Android 23. En **Desarrollador** están disponibles **Nuevo reporte · guardado local**, **Reportes en este teléfono** y **Mapa de reportes por zona**. Llena el reporte, confirma un punto cercano con GPS preciso y guarda; recibe un folio para abrir el detalle, copiarlo o ubicar el reporte en el mapa. Consulta y filtros leen los datos guardados en este teléfono.

El diario privado conserva el mismo intento antes de insertar y recupera su resultado después de una interrupción. El guardado vuelve a comprobar el radio de 5 km. Los reportes son locales: no se envían a autoridades ni generan avisos de emergencia. No hay cuentas reales, fotografías o administración implementadas. [Fase 15](docs/FASE_15.md) detalla pruebas y avance por los puntos del PDF.

Para actualizar conservando tus cambios: `git fetch origin`, `git switch main`, `git pull --ff-only origin main`. También puedes instalar el APK académico de la Release sobre la versión anterior, con la misma firma y sin desinstalar.

## Antecedentes históricos

## Versión oficial 0.5.2 — revisión del reporte

En **Desarrollador → Nuevo reporte · borrador → Revisar borrador** puedes revisar todos los campos, corregirlos y volver sin perder lo escrito. La fecha muestra su zona horaria, y una referencia sin coordenadas se identifica claramente. La revisión también se conserva al recrear la pantalla. [Release y APK 0.5.2](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.2).

La base técnica de guardado conserva el mismo folio al reintentar una solicitud cuya respuesta se perdió, incluso después de confirmar SQLite. Esta pantalla **todavía no guarda ni envía reportes**; la activación del guardado y consulta reales sigue esperando la instrucción específica del usuario. [Fase 14](docs/FASE_14.md) explica la corrección, 529 pruebas aprobadas y pendientes. Avance: **11/28 = 39.3%**; no se cuenta publicar o mejorar revisión como completar el guardado.

Para actualizar, conserva tus cambios y ejecuta `git fetch origin`, `git switch main`, `git pull --ff-only origin main`.

## Antecedentes

## Entrega oficial 0.5.1 — formulario y mapa visibles

**9 de octubre de 2026 · código Android 21.** El usuario indicó «desde ahora puro released». Esta entrega incorpora las fases 12 y 13 a las variantes compartidas: Desarrollador → **Nuevo reporte · borrador** permite llenar el reporte, elegir un punto en MapLibre/OpenFreeMap, conservar el texto al volver y revisar los campos. El mapa muestra calles, nombres y pin. Los controles distinguen permiso denegado/aproximado/preciso, GPS desactivado y reintento, y funcionan al girar la pantalla. Publicación: [Release v0.5.1](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.1).

La confirmación mantiene GPS preciso, reciente, no simulado y radio de 5 km. El formulario **todavía no guarda ni envía reportes ni avisa a emergencias**; guardado y consulta reales siguen ocultos por la instrucción específica anterior. El login y el ejemplo ficticio del compañero se conservan. Se comprobaron **296 casos: 285 JVM y 11 nativos**, con cuatro nuevos casos de permisos/ubicación desactivada contra Android real, sin inyectar estados de permiso en el modelo ni ubicaciones falsas. Debug/release compilan, firma debug válida y lint sin errores, con 19 advertencias anteriores por variante.

**Avance: 11/28 = 39.3%.** Se acredita el hito existente de **4.3 permisos y estados GPS (3/4)** por las comprobaciones de los controles, respaldadas por las pruebas previas de fuente/estados. GPS físico y confirmación completa dentro de esta pantalla siguen pendientes: no se completa 4.3.4. Publicar una versión no suma otro hito. [Entrega 0.5.1](docs/ENTREGA_0_5_1.md) explica los puntos y los pendientes; los cortes Unreleased anteriores permanecen como historia y su código de interfaz se incluye en esta entrega.

Para actualizar el equipo, conserva tus cambios propios y ejecuta `git fetch origin`, `git switch main` y `git pull --ff-only origin main`. La pantalla nueva entra desde Desarrollador → Nuevo reporte · borrador.

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

Para actualizar el código del equipo, guarda o confirma primero tus cambios propios y ejecuta:

```bash
git fetch origin
git switch main
git pull --ff-only origin main
```

## Registro histórico anterior a 0.5.0

Los estados Unreleased y las autorizaciones siguientes describen sus cortes originales. El código de fases 8 a 11 se incluye en la entrega 0.5.0; los pendientes visuales se conservan.

**Avance compartido para el equipo — Unreleased.** La rama [development/fase-11-mapa-zonas](https://github.com/Spidey123134/citizensecurity/tree/development/fase-11-mapa-zonas) reúne las fases 8 a 11, necesarias para el mapa por zonas validado. El usuario autorizó subir este código el 8 de octubre de 2026. Se conserva main y la Release oficial 0.4.0a; no se crea otra Release ni se integra automáticamente. El siguiente paso sigue siendo acoplar la pantalla del compañero. Los resultados de 325 pruebas y 35.7% del plan pertenecen al cierre técnico previo a esta subida.

Para obtener esta preparación, conserva primero cualquier cambio propio y ejecuta `git fetch origin`; desde una copia que todavía no tenga la rama local, usa `git switch --track origin/development/fase-11-mapa-zonas`. La pantalla productiva y los accesos reales siguen pendientes de acuerdo.

**Trabajo activo: fase 11 — incidentes por zona y marcadores — Unreleased, local; cerrada técnicamente el 8 de octubre de 2026, iniciada el 7.** El circuito SQLite → `ReportMapViewModel` → `ReportMapSceneBinding` → `MapLibreReportSceneHost` → MapLibre representa marcadores mínimos sobre Liberty después de un gesto explícito. Se comprobó el mapa por zonas con cámara, filtros, límite, lista vacía, recarga y retirada/recreación de la vista. [Fase 11](docs/FASE_11.md) y [acoplamiento](docs/ACOPLAR_INTERFAZ.md) documentan el contrato. La oficial conserva **0.4.0a / código Android 19**, sin otra publicación.

El puente consulta solo en **RESUMED**, conserva filtros y sigue movimientos de cámara posteriores al gesto. Construir, recrear o volver a la pantalla no consulta automáticamente. Cambiar región retira marcadores anteriores; una respuesta tardía no pinta otra zona. Cada escena reserva un token puro en el modelo y usa un host propio: cerrar/pausar la vista anterior no cancela ni pinta la sucesora, incluso si ambas usan la misma consulta. El cierre retira solo sus recursos y mantiene abierto el repositorio compartido.

La [validación de fase 11](docs/VALIDACION.md) aprobó **325 casos distintos: 273 JVM app, 34 Android flow, ocho JVM monitor y diez Android mapas**. Se añaden **57 definiciones: 31 coordinador, 20 host y seis nativas**. App y nativos se repitieron tras ajustar dos aserciones a la precisión del SDK, sin cambios de producción ni reglas GPS y sin contar repeticiones. Flow34/monitor8 se ejecutaron en el primer pase y se reutilizaron en el final. Debug/release compilan, firma debug v2 válida y lint sin errores; advertencias y comprobación de conservación final se registran por separado.

También se obtuvo **GPS físico válido en el Motorola/API 36** con la fuente productiva real y captura por gesto: precisión aproximada **6.668 m**, antigüedad **46 ms**, cercanía permitida, punto distante rechazado y lectura no simulada. El primer intento terminó en TIMEOUT; el siguiente, cerca de una ventana, fue VALIDATED, conservando el límite de captura de 15 s. El laboratorio physical no guardó coordenadas ni creó reportes. Se acreditan dos hitos existentes: **4.3 Geolocalización en 2/4 y 4.5 Mapas en 3/4; total 10/28 = 35.7%**, manteniendo los mismos 28 hitos y pesos. El flujo visual productivo todavía no está acreditado.

El siguiente trabajo es coordinar la pantalla del compañero, que aún no existe en el repositorio. Conserva interfaces/login, navegación y accesos reales ocultos; no se activa guardado o consulta productivos. Los **18 APK anteriores**, la app del teléfono **0.4.0/código 7** y la del emulador **0.3.1/código 8** se preservan; el recibo cierre-fase11.json comprueba HEAD/índice, 63 archivos de base y hashes/fechas de ambos productos. Este cierre no incluye Git, publicación, Cloud, borrado ni apagado. Los bloques siguientes conservan sus resultados y pendientes históricos.

**Antecedente: fase 10 — MapLibre Native + OpenFreeMap — Unreleased, local; cerrada técnicamente el 7 de octubre de 2026.** El usuario eligió este proveedor para el mapa. Se integra **MapLibre Native Android 13.6.1** con el estilo **Liberty** y se adaptan los bindings a `MapLibreMap`; el runtime del mapa deja de usar SDK Google, metadato/lectura de clave y comprobación de Google Play Services. [Fase 10](docs/FASE_10.md) y [Mapa con MapLibre](docs/MAPA_MAPLIBRE.md) describen la configuración vigente. El laboratorio aislado comprobó **mapa real con calles y nombres legibles**, sin clave, tarjeta ni cuenta. La oficial sigue siendo **0.4.0a / código Android 19**, sin otro número ni publicación nueva.

La dependencia activa es `org.maplibre.gl:android-sdk-opengl:13.6.1`, con backend OpenGL ES estable. La inicialización local distingue `Ready`, `RendererUnavailable` y `VerificationFailed`; `Ready` todavía no prueba red, estilo cargado ni mapa renderizado. Esa carga y el PNG se comprobaron por separado: con GPU host aparecen Calle Tacuba, Calle Moneda y Calle Venustiano Carranza; el límite de etiquetas invisibles con renderizado software queda documentado. Se conservan GPS explícito, permisos, radio de 5 km, borrador, SQLite y consulta por zona. El compañero mantiene interfaz/login, pantalla del mapa, pines y controles; guardado real y consultas siguen ocultos. **Avance: 8/28 = 28.6%; 4.5 Mapas en 2/4**, al acreditar únicamente el hito existente de SDK configurado. La configuración Google siguiente pertenece a los antecedentes anteriores al cambio de proveedor.

La [validación de fase 10](docs/VALIDACION.md) aprobó **268 casos distintos: 222 JVM de app, 34 Android del flujo, ocho JVM del monitor y cuatro Android del mapa**. Se añaden **25 definiciones**; siete casos de readiness reemplazan nueve de Google, sin inflar el total con sustituciones o repeticiones. Debug/release compilan, firma debug v2 válida y lint sin errores: 19 advertencias anteriores app, dos flow y ocho del laboratorio de mapas. Los 16 APK anteriores, HEAD/índice, fuentes visuales y producto instalado 0.3.1/código 8 permanecen conservados. Los APK nuevos son artefactos locales separados; aceptación del flujo productivo pendiente.

**Antecedente: fase 9 — consulta de incidentes por zona — Unreleased, local; completada técnicamente.** Continúa la base oficial 0.4.0a sin asignar otra versión ni modificar su publicación. La nueva función recibe una región geográfica, filtros de tipo/estado y un límite; devuelve los datos mínimos de los marcadores guardados en SQLite. `ReportMapViewModel` carga solo mediante `load(query)`, conserva la consulta de cada resultado y descarta respuestas de una petición sustituida. [Fase 9](docs/FASE_9.md) y [acoplamiento](docs/ACOPLAR_INTERFAZ.md) documentan el contrato para el futuro mapa del compañero. **Aceptación visual pendiente.**

La [validación de fase 9](docs/VALIDACION.md) aprobó **457 casos distintos ejecutados, con 43 definiciones nuevas**, sin fallos, errores, omisiones ni resultados reutilizados: 96 de dominio, 38 JVM de datos, 78 SQLite Android, 211 JVM de app y 34 del laboratorio de flujo. Debug/release compilan, firma debug v2 válida y lint sin errores: 19 advertencias anteriores de app, ninguna de datos y dos del laboratorio. Dos regresiones comprueban que limpiar o pedir otra zona al recibir `Loading` no inicia la lectura sustituida; el job se reserva antes de emitir ese estado. La repetición final de app, compilación y lint no aumenta el recuento. Los resultados permanecen separados de la Release y de fases anteriores.

La petición actual autoriza migración y comprobación local, sin commit, subida, integración, etiqueta o Release nuevos. Para completar los hitos de 4.5 faltan el mapa por zonas con marcadores y su integración visual en la pantalla del compañero. GPS físico y aceptación siguen pendientes. La [guía MapLibre](docs/MAPA_MAPLIBRE.md) es la activa; la [guía de clave Google](docs/CONFIGURAR_CLAVE_MAPS.md) conserva el antecedente del proveedor anterior.

La **fase 8 quedó completada técnicamente**: coordinación del borrador y ubicación mediante `ReportLocationFlowBinding`, con **226 pruebas ejecutadas aprobadas y 27 definiciones nuevas**, compilación debug/release y lint sin errores con advertencias anteriores. Su cierre documental también terminó; la aceptación visual sigue pendiente. [Fase 8](docs/FASE_8.md) conserva ese corte como antecedente, sin trasladar sus pruebas a fase 9.

Versión oficial **0.4.0a / código Android 19**, del **6 de octubre de 2026**, con validación completada. El usuario pidió revisar GitHub, incorporar el trabajo del compañero y subir/publicar esta actualización como Release; su estado en GitHub se consulta en [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a). Esta autorización cubre esta entrega, sin autorizar automáticamente siguientes publicaciones.

Se completaron dos pasos técnicos: **fase 6A**, un puente para conectar permiso, captura explícita y ciclo de vida de la pantalla; y **fase 7**, un borrador editable que conserva los campos al ir al mapa y regresar, con recuperación mediante `SavedStateHandle` y protección ante resultados de otra sesión. También se cerró el endurecimiento de evidencia GPS de 0.3.11 que había quedado pendiente. Estas piezas quedan listas para que el compañero conecte sus controles; no añaden un formulario, login ni navegación por su cuenta, no inician GPS automáticamente y no habilitan guardado real o consulta.

La [validación de 0.4.0a](docs/VALIDACION.md) aprobó **266 pruebas ejecutadas**: 171 JVM de app, 64 instrumentadas de datos y 31 del laboratorio Android, con **57 definiciones nuevas** y sin fallos, errores u omisiones. **124 casos se reutilizaron** mediante `UP-TO-DATE` (86 de dominio y 38 JVM de datos); no son ejecuciones nuevas. Debug/release compilan y la firma debug v2 es válida. Lint terminó sin errores: **19 advertencias visuales del conjunto integrado** en app, dos por variante técnica y ninguna en datos. Los resultados de 0.3.10 y 0.3.11 conservan sus propios antecedentes. Clave Google Maps, lectura GPS física, mapa renderizado y aceptación del flujo completo siguen pendientes. El avance conserva **7/28 hitos = 25%**, sin sumar dos fases técnicas como dos requisitos completos del PDF. Véanse [fase 7: borrador](docs/FASE_7.md), [acoplamiento](docs/ACOPLAR_INTERFAZ.md) y [estado por hitos](docs/AVANCE.md).

La revisión de GitHub incorporó `main` del compañero (`b665a56a`) mediante el merge local `433b3fb`. Los layouts de inicio y crear cuenta coinciden con el remoto; herramientas conserva su rediseño y añade el bloque solicitado del ejemplo en memoria antes de Volver, manteniendo los IDs del compañero. El descriptor de Gradle quedó coordinado a **JDK 27**, sin URLs de JVM 25; el bytecode Android permanece en JVM 17. El conjunto integrado aprobó la validación registrada para esta entrega.

Los siguientes bloques conservan los cortes locales anteriores y sus límites; no describen el estado de publicación actual.

Antecedente de la preparación **0.3.11 / código Android 18 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el endurecimiento de la evidencia compartida después de `Ready`: al publicar o consultar una lectura se exige permiso preciso FINE y GPS habilitado. Si esa comprobación detecta GPS apagado o falla, descarta la evidencia; reactivarlo no recupera la lectura anterior y requiere otra captura explícita. El radio de 5 km, la precisión, vigencia y rechazo de simulaciones conservan sus reglas.

Producción comparte una misma `AndroidPhoneLocationSource` entre `PhoneLocationRefresh` y `PhoneLocationEvidence`; el guard de SQLite usa ese mismo lector protegido. Antes se comprobaba FINE al consultar el fix, pero faltaba exigir también GPS habilitado después de `Ready`; no era un fallo matemático ni una evasión del radio de `NearbyIncidentPolicy`. La [validación de 0.3.11](docs/VALIDACION.md) se cerró antes de preparar 0.4.0a: **247 pruebas ejecutadas y aprobadas** (121 JVM de app, 38 JVM de datos, 64 instrumentadas de datos y 24 del laboratorio), con **16 definiciones nuevas**. Dos regresiones fallaron contra la base 0.3.10 y aprobaron tras corregir la relectura de GPS; el estado del proveedor era controlado. Debug/release compilan, firma debug v2 válida y lint sin errores (13 advertencias previas de app, dos por variante técnica; datos sin incidencias). Las 86 de dominio no se ejecutaron de nuevo. Estos resultados pertenecen a 0.3.11, no al corte 0.4.0a. La interfaz y login del compañero, accesos ocultos a datos reales y consulta, clave Maps y GPS físico conservan su estado. **Fase 6, 7/28 = 25%**, local y Unreleased, sin acciones Git o publicación.

Antecedente **0.3.10 / código Android 17 — Unreleased**, local, del **5 de octubre de 2026**. Continúa fase 6 con el [flujo de ubicación comprobado en Android](docs/FLUJO_UBICACION_ANDROID.md): permiso, renovación explícita, evidencia y confirmación dentro de 5 km. El módulo `verification/location`, marcado `testOnly`, reutiliza las fuentes productivas de `app/.../maps` sin copiarlas y mantiene paquetes distintos del producto. Conserva los tres diálogos nativos de permiso y añade una variante `flow` para conectar los modelos y la captura con fuentes controladas. Esas lecturas sintéticas pertenecen solo al laboratorio y no acreditan GPS físico ni mapa visible.

El corte 0.3.10 ejecutó y aprobó **21 casos Android distintos en API 37: tres diálogos de permiso extendidos, 16 de flujo y dos de fuente GPS**. Son **18 definiciones nuevas**; repetir los 16 de flujo no añade casos. Los dos de GPS comprueban registro nativo con cancelación antes del callback y rechazo de una `Location` Android marcada como simulada a través de una fuente controlada; no obtuvieron un fix físico. Debug y release compilan; firma debug v2 válida. Lint terminó sin errores: app conserva 13 advertencias anteriores y cada variante del laboratorio dos (`DataExtractionRules` y `MissingApplicationIcon`). Las **121 JVM de app quedaron `UP-TO-DATE`**, como evidencia anterior, no ejecutada de nuevo. [Validación](docs/VALIDACION.md) registra comandos, resultados y límites.

Como antecedente, **0.3.9 ejecutó de nuevo y aprobó 121 JVM de app y tres flujos nativos de permiso**, junto con compilaciones debug/release, firma debug v2 y lint sin errores (13 advertencias anteriores). Las **47 JVM nuevas** pertenecen a **0.3.8 / código 15**. Las suites de dominio/datos y las 62 instrumentadas de 0.3.7 conservan su evidencia anterior. Se conserva la restricción estable de Fragment que corrigió la incompatibilidad heredada de Google Maps con Activity Result.

El compañero conserva sus pantallas y login. Sus controles aún no están conectados al puente; conceder permiso no inicia GPS, confirma un pin ni guarda un reporte. La prueba nativa recrea la Activity, sin acreditar rotación física ni muerte del proceso. GPS físico y mapa visible siguen pendientes. El SDK Google Maps está incorporado y su clave Cloud sigue pendiente, confirmado por el usuario. [Google Maps](docs/GOOGLE_MAPS.md) conserva el contrato de selección y configuración; [avance](docs/AVANCE.md): **7/28 = 25%**, sin sumar una integración visual no comprobada.

Las herramientas visibles siguen siendo las básicas 0.3.1 y el ejemplo ficticio en memoria; guardado real y consulta permanecen ocultos. El cierre de 0.3.10 confirmó que la app instalada seguía en **0.3.1 / código 8**, sin cambios en las fuentes visuales, y que los fixtures no estaban en el DEX productivo. Ningún paquete del laboratorio quedó instalado en ese cierre. El laboratorio no entrega esta preparación al compañero por GitHub: no se creó commit, subida, integración, etiqueta o Release. Las copias debug y release sin firma de 0.3.7, 0.3.8, 0.3.9 y 0.3.10 conservan su evidencia; [incrementos anteriores](docs/INCREMENTOS_035_037.md) mantiene su historia.

Proyecto Android académico en Kotlin para reportes locales de incidentes. No hay envío a autoridades ni servicio de atención de emergencias. Las [mejoras futuras](docs/MEJORAS_FUTURAS.md) continúan como propuestas para otra revisión. Las comprobaciones técnicas no sustituyen la revisión del flujo real.

## Primera función

Validar y guardar un reporte local con categoría, prioridad, descripción, fecha y ubicación escrita o coordenadas. La consulta por folio permite comprobar que el registro permanece después de reabrir la base.

El compañero añadió `SplashActivity` y un layout visual de login en [su commit `7e90109`](https://github.com/Spidey123134/citizensecurity/commit/7e90109c82b1b35e3dfdec76ac6624fa2f699fff). Completó el recurso `activity_splash.xml` en [el commit `e18e636`](https://github.com/Spidey123134/citizensecurity/commit/e18e636ff60ea19a13e01ddf98c3de67d158e692), ya incorporado en la base local. La autenticación todavía está pendiente; las interfaces continúan bajo su responsabilidad.

## Estructura

| Módulo | Responsabilidad |
| --- | --- |
| `app` | Entrada Android, interfaces y composición de dependencias |
| `core/domain` | Modelos, contrato del repositorio y validación independiente de Android |
| `core/data` | SQLite y persistencia del reporte |
| `verification/location` | Laboratorio Android `testOnly` de permisos y flujo de ubicación, con paquetes separados del producto, fuentes controladas para suites y variante physical para GPS real por gesto |
| `verification/maps` | Laboratorio Android `testOnly` aislado para carga y renderizado nativos de MapLibre/OpenFreeMap, sin GPS ni reportes productivos |

Lee [Trabajo del equipo](docs/TRABAJO_EQUIPO.md) y [Contrato de la primera función](docs/GUARDAR_REPORTE.md).

La [conexión de la interfaz con los reportes](docs/ACOPLAR_INTERFAZ.md) ofrece un `ReportViewModel` con progreso de guardado, errores por campo y confirmación con folio. La lógica se conserva, pero el guardado de datos reales permanece oculto en la aplicación. El compañero puede preparar su formulario sin activar ese flujo hasta que el usuario lo indique; la autenticación también sigue pendiente de su parte.

Para revisar la función por pasos, empieza con [Recibir los datos del reporte](docs/RECIBIR_REPORTE.md). Incluye un ejemplo de consola que reutiliza `NewReport` y permite cambiar los cinco datos sin una pantalla.

El incremento **0.2.0** permite [validar el reporte y su ubicación](docs/VALIDAR_REPORTE.md). Se puede comprobar un punto válido, un par de coordenadas incompleto y un punto fuera de rango. Esta es nuestra parte inicial para conectar después la selección de un punto en Google Maps.

Los cortes anteriores **0.3.3 / código 10**, **0.3.2 / código 9** y **0.3.1 / código 8** conservan su evidencia de preparación local, sin etiquetas o Releases propias. Se eligió entonces 0.3.x en lugar de la futura 0.5.0; el usuario fijó ahora **0.4.0a / código 19** para la actualización oficial. El historial 0.4.0 conserva sus identificadores. El código 11 correspondió a 0.3.4 y los artefactos posteriores se identifican por separado en [Validación](docs/VALIDACION.md).

El usuario confirmó **5 km alrededor del teléfono** como límite para ubicar un incidente. La [regla de proximidad de 0.3.3](docs/PROXIMIDAD_REPORTES.md) necesita una lectura del dispositivo independiente del punto seleccionado, reciente, suficientemente precisa y no marcada como simulada. Comprueba la distancia al confirmar y de nuevo antes de escribir. Así rechaza, por ejemplo, un punto de Francia desde una lectura en México; una persona cerca del incidente en Francia sí puede proponerlo. Es un filtro local, no una garantía de veracidad o de ausencia de falsificación.

La revisión remota anterior encontró [el commit del compañero `b665a56a`](https://github.com/Spidey123134/citizensecurity/commit/b665a56a9761ec5c083cabc638eba1674c7bf78b), **“cambiar interfaz”**, que declara **0.4.0 / código 7**. La [Release histórica v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0) permanece en `08ab2d5`: su integración y publicación se realizaron sin instrucción humana, pues se había pedido terminar y explicar los cambios. Por indicación posterior se conserva lo publicado. Los cortes locales 0.3.x partían de `08ab2d5`; ahora se incorpora el trabajo del compañero en la actualización 0.4.0a expresamente solicitada.

Ese commit añadió `activity_createaccount.xml`, modificó los layouts de inicio/herramientas y añadió el descriptor Daemon JVM 25. No cambió Kotlin ni los contratos de datos; crear cuenta continúa siendo solo visual. El acoplamiento de 0.4.0a conserva los controles del ejemplo en memoria y coordina el descriptor de Gradle a **JDK 27**, sin cambiar el bytecode Android JVM 17. La comprobación del conjunto se registra en [Validación](docs/VALIDACION.md).

El pulido de la [fase 4](docs/FASE_4.md) permanece **Unreleased**. Su lógica distingue folio inválido, inexistente y fallo de lectura; la demostración técnica `:core:data:consultarReporte` comprueba permanencia en bases de prueba. Toda consulta 0.4 queda oculta en la interfaz, en la base 0.3.1 y los incrementos 0.3.2 y 0.3.3. El contrato se conserva para trabajo futuro y no autoriza al compañero a activarlo. El [plan del proyecto](docs/PLAN_DEL_PROYECTO.md) relaciona este alcance con el PDF.

El botón de desarrollador y **admin / admin** conservan recepción, validación y coordenadas de fases 1 y 2. **0.3.3 hereda las herramientas básicas de 0.3.1**, incluido **Ejemplo de guardado**, con datos ficticios fijos en SQLite en memoria: no usa lo escrito en el formulario ni el repositorio de reportes reales y no crea archivos de base de datos. Conserva el resultado al rotar, bloquea solicitudes simultáneas y permite volver a verlo sin insertar otra vez hasta salir. El título **Funciones básicas · 0.3.1** sigue identificando esa base; el incremento interno no añade controles ni necesita GPS para este ejemplo. Son herramientas locales; no crean cuentas ni permisos administrativos. [Funciones visibles](docs/FUNCIONES_PUBLICADAS.md) explica el alcance.

La [fase 3 de guardado local](docs/FASE_3.md) y **0.3.0 permanecen en Unreleased**; solo se expone el ejemplo ficticio solicitado. Parte de su lógica ya estaba incluida en versiones anteriores y en el código público: Unreleased no significa que esa fuente quede privada o fuera del APK. El guardado real permanece oculto. El antiguo identificador provisional `0.3.1` / código 4 corresponde a otras pruebas y no a la base local 0.3.1 / código 8. La evidencia por versión está en [Validación](docs/VALIDACION.md).

## Seguimiento del proyecto

- [Inicio del equipo](docs/INICIO_EQUIPO.md): distinguir la base remota de la preparación local, configurar la PC y coordinar el trabajo.
- [Changelog](CHANGELOG.md): cambios realizados y comprobaciones ejecutadas.
- [Fases de trabajo](docs/FASES.md): estado de cada paso, responsable y resultado que se revisará.
- [Plan del proyecto](docs/PLAN_DEL_PROYECTO.md): requisitos del PDF, alcance de la consulta local y funciones pendientes.
- [Fase 6](docs/FASE_6.md): selección, confirmación y cercanía de ubicación en 0.3.2 y 0.3.3.
- [Fase 7](docs/FASE_7.md): borrador editable, recuperación de campos e ida/vuelta del mapa en 0.4.0a.
- [Fase 8](docs/FASE_8.md): coordinación del borrador, ubicación y vista del mapa, completada técnicamente y local.
- [Fase 9](docs/FASE_9.md): consulta geográfica acotada para los futuros marcadores de incidentes.
- [Fase 10](docs/FASE_10.md): cambio a MapLibre Native/OpenFreeMap y comprobación nativa del motor.
- [Fase 11](docs/FASE_11.md): conexión de incidentes por zona a sus marcadores y comprobación GPS física separada.
- [Mapa con MapLibre](docs/MAPA_MAPLIBRE.md): guía activa de inicialización, estilo, bindings y atribución.
- [Configurar la clave Google](docs/CONFIGURAR_CLAVE_MAPS.md): antecedente del proveedor anterior.
- [Proximidad de reportes](docs/PROXIMIDAD_REPORTES.md): radio de 5 km y evidencia del teléfono para confirmar y guardar.
- [Avance](docs/AVANCE.md): criterios para estimar el progreso del plan y lo que queda pendiente.
- [Releases](https://github.com/Spidey123134/citizensecurity/releases): historial publicado. El proceso de [Versiones](docs/RELEASES.md) exige una instrucción precisa antes de subir, integrar o publicar cualquier preparación nueva.

El compañero `vazdavr-sudo` desarrolla las interfaces y el login. Nuestra parte se centra en la lógica y los datos del reporte. Se revisa una función por vez antes de elegir la siguiente.

## Herramientas

La comprobación local **0.3.3** aprobó **148 pruebas diferentes** (86 de dominio, 30 de app y 32 instrumentadas API 37), compilación debug y lint sin errores, con las 13 advertencias visuales anteriores. [Validación](docs/VALIDACION.md) distingue las dos ejecuciones y sus repeticiones. La captura real del teléfono y el mapa siguen pendientes; el ejemplo ficticio mantiene su funcionamiento sin GPS.

Java 27 para ejecutar Gradle; Java/Kotlin JVM 17 para el código Android. El segundo ajuste determina el bytecode de la aplicación, no la versión del JDK instalado. Android mínimo 8.0 / API 26.

Las versiones de las dependencias están fijadas en `gradle/libs.versions.toml`; el wrapper verifica su descarga con SHA-256.

## Abrir y verificar

Abre esta carpeta completa en Android Studio. Usa JDK 27 para Gradle y Android SDK Platform 37.0 / Build Tools 37.0.0. La ruta del SDK se guarda en `local.properties`, fuera de Git. El JDK local del IDE se configura con `GRADLE_LOCAL_JAVA_HOME` en `.gradle/config.properties`.

```powershell
.\gradlew.bat :app:testDebugUnitTest :core:domain:test :core:data:lintDebug :app:lintDebug :app:assembleDebug
.\gradlew.bat :core:data:consultarReporte
```

El segundo comando es una comprobación técnica de la lógica conservada, requiere un emulador o teléfono y ejecuta la suite instrumentada antes de mostrar y exportar la consulta. Usa una base temporal y no consulta ni modifica los reportes productivos del usuario. No activa una pantalla de consulta.

La comprobación histórica de la **base local 0.3.1 / código 8** ejecutó y aprobó **30 JVM de app y 15 instrumentadas en API 37: 45 pruebas**. Las 32 de dominio conservaron su resultado anterior mediante `UP-TO-DATE`; no se presentan como una ejecución nueva. Los JSON de guardado y consulta fueron correctos. Se reprodujo un guardado que insertaba después de cancelarse durante la obtención de la fecha; la regresión aprobó tras comprobar la cancelación antes de iniciar y confirmar la transacción. No se afirma reversión de una escritura ya confirmada.

Esa base 0.3.1 también aprobó compilación, lint debug/release sin errores y comprobación real en el emulador del ejemplo, incluidos repetir, rotar y volver al inicio. Las bases productivas conservaron sus hashes y listado; permanecen 13 advertencias visuales previas. El APK debug 0.3.1 / código 8 tiene firma v2 comprobada y se identifica como compilación local para pruebas académicas. Esta evidencia corresponde a 0.3.1; no sustituye las comprobaciones de 0.3.2 y 0.3.3, que se registran por separado en [Validación](docs/VALIDACION.md).

Como historial, `0.2.1` aprobó 14 JVM de app y 9 instrumentadas SQLite, con dominio `UP-TO-DATE`; `0.4.0` aprobó 24 JVM de app, 32 de dominio ejecutadas de nuevo y 10 instrumentadas. [Validación](docs/VALIDACION.md) conserva las comprobaciones por versión. Consulta visual, teléfono físico, API 26 y aceptación funcional siguen pendientes.

## Antecedentes de ubicación

El primer paso de **0.3.2 Unreleased** preparó la lógica de ubicación provisional, confirmación y cancelación en `core/domain`. **0.3.3 Unreleased** añade la comprobación de cercanía a una lectura reciente del teléfono. Corresponden a la **fase técnica 6** y a los apartados **4.3 Geolocalización y 4.5 Mapa de incidentes** del PDF. La [guía de fase 6](docs/FASE_6.md) delimita los contratos y [Avance](docs/AVANCE.md) explica los criterios de progreso. El compañero desarrolla la vista del mapa, el pin y sus controles; nuestra parte recibe y valida coordenadas y evidencia de ubicación. Desde 0.3.4 están incorporados el SDK y la fuente de captura; 0.3.8 añadió el puente de permisos y 0.3.9 comprobó sus tres flujos nativos de forma aislada. La preparación 0.3.10 une permiso, renovación y confirmación en el laboratorio Android con fuentes controladas; sus resultados se registran por separado. Google Cloud, GPS físico, mapa renderizado y conexión de los controles siguen pendientes. Cuentas, sincronización, fotografías, administración y notificaciones necesitan incrementos propios.

El primer paso **0.3.2** aprobó **47 pruebas de dominio, 30 de app y 15 instrumentadas SQLite: 92 ejecutadas**. Lint debug y compilación debug terminaron sin errores, con las 13 advertencias visuales previas. El ejemplo de consola `:core:domain:seleccionarUbicacion` muestra el contrato sin una pantalla; su confirmación geométrica no comprueba cercanía al teléfono. La ejecución nueva de **0.3.3** se registra por separado en [Validación](docs/VALIDACION.md). El avance estimado sigue en **7/28 hitos, 25% del plan completo**: fortalecer esta regla no añade captura GPS ni un mapa real. La aceptación funcional sigue pendiente.
