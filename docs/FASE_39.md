# Fase 39 — revisión legible y permisos ligados a la sesión

**15 de octubre de 2026 · desarrollo local Unreleased.** Atiende la lectura del borrador con una fotografía adjunta y continúa **4.6 Administración** del PDF mediante la vinculación de los permisos de fase 38 al espacio y la sesión que los originaron.

## Borrador con fotografía

La revisión separa **Datos del incidente**, **Lugar del incidente** y **Fotografía adjunta** en tarjetas con títulos, etiquetas y espacio entre campos. La descripción, fecha, referencia y coordenadas conservan su contenido y permiten desplazarse hasta las acciones de corregir o guardar.

La vista previa utiliza un marco de **240 dp de alto** y ajusta la imagen completa dentro de él. Antes, la combinación de altura limitada y relación de aspecto podía hacer que una fotografía vertical dibujara fuera de su espacio y se juntara con el texto. El marco fija el área y recorta únicamente el dibujo que pudiera salir de sus límites; no recorta ni modifica el archivo guardado. Una fotografía horizontal, vertical o cuadrada mantiene sus proporciones.

El bloque conserva los estados de carga, ausencia de fotografía y error con reintento. Esta corrección de presentación no cambia la importación de cámara/galería, el guardado de reportes o la comprobación del GPS. Revisar no renueva la ubicación ni confirma recepción por una autoridad.

## Continuación de Administración del PDF

La vinculación administrativa captura el `ReportWorkspace` ya abierto y su sesión. Conserva las fuentes de ese espacio para la consulta por folio, lista paginada e historial, en lugar de buscar otra sesión al terminar una operación. El dueño y el actor corresponden a la cuenta capturada; construir el objeto o iniciar sesión no concede una capacidad.

Se conservan los permisos independientes `READ_REPORTS` y `CHANGE_STATUS` de fase 38, denegados por defecto. Se comprueban espacio y vigencia antes y después de consultar la política. Un permiso concedido a una consulta no permite cambiar estados; el futuro escritor deberá comprobar también la política dentro de su transacción, antes del commit.

Cerrar sesión invalida la vinculación anterior incluso al volver a entrar con la misma cuenta. El acceso académico tampoco se convierte en un actor administrativo. La asignación administrativa sigue pendiente por decisión del usuario: no se añade una cuenta privilegiada, un panel sin funciones o una concesión automática para `admin/admin`.

## Validación y límites

**1265 casos distintos aprobados, 25 nuevos; cero fallos, errores u omitidos finales.** La app ejecutó 907 pruebas JVM (22 nuevas de sesión/permisos); se reutilizaron los recibos intactos de dominio (200) y datos (141), cuya fuente no cambió desde fase 38. Android API 37 aprobó 17 pruebas: fotografía (6), revisión del borrador (4), aislamiento entre cuentas (4) y la fábrica administrativa real (3 nuevas).

El mismo recorrido de galería se comprobó además con letra al **130%**, conservando guardado, corrección, foto privada y campos legibles. Es una repetición del caso existente y no aumenta los 1265. Se capturó y revisó el desbordamiento en la APK previa, y después el acomodo corregido en el emulador, con foto vertical de 480 × 960. Los demás recorridos de fotografía conservan su fuente horizontal. No se atribuye una comprobación específica con foto cuadrada.

Compilación debug/release y ensamblados internos correctos. Lint app: cero errores/22 avisos existentes por variante; datos: sin incidencias. El ajuste de letra se restauró a su valor previo y se cerró el emulador propio. [Validación](VALIDACION.md) detalla el recibo, los intentos iniciales del arnés y los límites.

Para revisar la presentación en el teléfono: abre un borrador con una fotografía vertical, entra a la revisión y comprueba que la foto queda dentro de su tarjeta, la descripción y el lugar se leen por separado y se llega a las acciones al desplazarse. Repite con una fotografía horizontal y otra cuadrada. La revisión visual ejecutada corresponde al emulador; la aceptación física sigue pendiente.

No cambia esquemas, datos históricos, login/loading ni asignación de permisos. El candidato archivado 0.6.2/código 30 precede a las fases 38/39 y a esta corrección visual; no contiene estos cambios. Se conserva para preparar una entrega posterior cuando el usuario la solicite, con numeración tentativa. No se publica APK, tag, Release ni se realiza push en este corte; la APK oficial continúa en **0.6.1**.

**20/28 = 71.4% permanece.** Continúan ocho criterios originales pendientes: uno de seguimiento, cuatro administrativos y tres de notificaciones. El arreglo visual mejora un recorrido ya construido y esta vinculación prepara el alcance administrativo; no completan otro criterio del PDF. Sigue pendiente acordar la asignación de permisos y componer el recorrido autorizado visible con recuperación durable.

[Fase 38](FASE_38.md) · [Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Fases](FASES.md) · [Validación](VALIDACION.md)
