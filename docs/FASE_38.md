# Fase 38 — permisos y consulta administrativa

**15 de octubre de 2026 · desarrollo local Unreleased.** Continúa **4.6 Administración** del PDF con una política explícita de capacidades y un límite de lectura administrativa. El trabajo se conserva localmente; no realiza push, tag, Release ni publicación de APK.

La publicación preparada se pospuso por instrucción del usuario. El candidato 0.6.2/código Android 30 y sus notas quedan guardados como borradores con numeración tentativa para la siguiente entrega. La APK oficial **0.6.1** conserva su archivo y su alcance. Esta fase no anuncia que sus cambios estén incluidos en esa descarga.

## Permisos independientes

`ReportAdministrationPolicy` comprueba un dueño y actor capturados junto con una capacidad concreta:

| Capacidad | Alcance |
|---|---|
| `READ_REPORTS` | Consulta administrativa por folio, lista paginada e historial del espacio capturado |
| `CHANGE_STATUS` | Cambio de estado y comprobación del recibo de esa solicitud mediante el acceso protegido existente |

Una capacidad no concede la otra. Una sesión local, un objeto con dueño/actor o una solicitud guardada en el diario no otorgan permisos. La política predeterminada deniega ambas capacidades. La excepción de denegación administrativa se traduce al contrato de denegación del escritor cuando corresponde, conservando el manejo existente de recuperación.

**La asignación administrativa sigue pendiente por decisión del usuario.** `admin/admin` mantiene acceso académico. No se añade un panel, un botón vacío, una cuenta privilegiada ni una concesión automática de roles. Las pantallas funcionales ya disponibles conservan su acceso; la consulta administrativa nueva requiere la asignación futura explícita.

## Consulta acotada y fuentes capturadas

`AuthorizedReportAdministration` recibe el espacio dueño/actor, la política, la comprobación de vigencia y un proveedor de fuentes vinculadas a ese mismo espacio. Las fuentes se inicializan de forma diferida: construir el lector no abre el repositorio, y una solicitud denegada falla antes de obtener sus fuentes. Se rechaza una fuente asociada a otro espacio.

El lector ofrece tres consultas: folio, página de reportes e historial paginado de un folio. Reutiliza los contratos acotados existentes, sin añadir una descarga de todos los reportes o una lectura de bytes de fotografías. Comprueba que el folio y el historial devueltos correspondan a lo pedido y que las páginas respeten el límite de la consulta.

La integración debe capturar el repositorio original. El proveedor no debe buscar una sesión nueva dinámicamente ni cambiar de dueño cuando varía la sesión. Quien administra el espacio conserva la responsabilidad de cerrar sus recursos; el lector no cambia esa propiedad.

## Revocación, cancelación y escritura

Antes de abrir fuentes, antes de consultar y antes de devolver el resultado se comprueban cancelación, vigencia y `READ_REPORTS`. Si cambia la autorización durante una consulta, el resultado no se entrega como autorizado. Los errores y cancelaciones se propagan, sin convertir una denegación o un fallo en una lista vacía válida.

`SqliteReportStatusChangeAccess` exige `CHANGE_STATUS` para ejecutar el cambio y para buscar su recibo. Conserva el espacio/actor y la identidad de la solicitud; el permiso de lectura no permite reconocer un intento de escritura. La política de cambio se vuelve a comprobar en el repositorio, dentro de la transacción antes del commit, además de los controles de entrada y salida. Esto conserva la coordinación durable y el contrato de recuperación de la fase 31.

La fase no cambia esquemas, migra reportes, borra datos ni modifica el login/loading. Tampoco incorpora administración remota, identidad institucional o recepción policial. La futura interfaz y la asignación de permisos deberán componer esta base con los recorridos durables y comprobar su resultado completo.

## Validación y siguiente condición

**1267 casos distintos aprobados, 25 nuevos; cero fallos, errores u omitidos.** JVM: 200 dominio (3 nuevos), 141 datos (14 nuevos) y 885 app de regresión. Android API 37: 41 pruebas de datos, con 8 nuevas de política/SQLite, 21 de recuperación protegida y 12 de actividad/migración. Los tres grupos JVM se ejecutaron en este corte; no se suman los cierres anteriores ni las dos etapas de actualización del candidato.

Compilación Kotlin debug/release y APK interna de pruebas de datos correctas. Lint app: cero errores/22 avisos existentes por variante; datos: sin incidencias. El emulador propio de solo lectura se cerró tras los pases. No se instaló ni borró nada en el teléfono del usuario. [Validación](VALIDACION.md) conserva el desglose, el recibo y los límites.

La APK candidata archivada precede a esta fase y no contiene estos cambios. Sigue pendiente ensamblar y comprobar el binario final cuando el usuario solicite la siguiente entrega; no se atribuyen pruebas físicas, interfaz administrativa, asignación de permisos o publicación.

**20/28 = 71.4% permanece.** Continúan ocho criterios originales pendientes: uno de seguimiento, cuatro administrativos y tres de notificaciones. Esta base permite avanzar en el contrato de permisos, pero no completa consulta administrativa visible, asignación, cambio autorizado o historial administrativo productivo. La siguiente condición es acordar el actor/permisos e integrar su recorrido visible con recuperación durable, manteniendo separadas consulta y modificación.

[Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Fases](FASES.md) · [Recuperación durable](FASE_31.md) · [Entrega oficial 0.6.1](ENTREGA_0_6_1.md)
