# Fase 26 — reportes separados por cuenta

Estado: **Unreleased para 0.6.0**, código en main. Sin APK, cambio de versión, tag o Release; última entrega binaria 0.5.7 / código 27.

## Recorrido

Después de ingresar, Reportar, Reportes guardados, folios, mapa y posibles repetidos trabajan en el espacio de esa cuenta. Fotos, borrador y solicitud pendiente también pertenecen a ese espacio. Cerrar sesión conserva los datos; volver a ingresar con el mismo UUID recupera sus archivos. Las pantallas anteriores se retiran al cambiar sesión, incluso al regresar al mismo usuario.

Los reportes históricos permanecen en el modo académico, con archivos y folios originales. No se copian o atribuyen a una cuenta nueva. admin/admin y Desarrollador mantienen el acceso académico sin roles ni acceso a cuentas registradas. El mapa y el aviso de repetidos consultan únicamente el espacio actual; no acreditan ausencia de incidentes de otras cuentas o teléfonos.

## Almacenamiento y sesión

ReportStorageNames selecciona base, diario, checkpoint y carpeta de fotos desde un UUID canónico, nunca desde el usuario o un Intent. Académico usa exactamente los nombres históricos. Se conservan reportes esquema 2 y cuentas esquema 1, sin migrar/reasignar filas; la autoría local corresponde al espacio de la cuenta.

ReportWorkspace captura dependencias y sesión por pantalla y se conserva al girar. El guard usa su propia evidencia GPS y la misma carpeta de fotos. Se mantienen permiso preciso, precisión/antigüedad/no-simulación, radio 5 km, integridad de foto, createOnce, diarios y CAS.

ScopedReportAccess comprueba sesión/cancelación antes y después de sus 13 entradas, conservando UUID, borrador, consulta y comparaciones de revisión/folio. Una escritura iniciada puede confirmarse en su espacio original: su recuperación no se transfiere a otra cuenta. Las fotos comprueban acceso antes de leer/importar/confirmar. SQLite se cierra en IO al terminar los modelos.

La sesión emite dueño/proceso/revisión; cada transición revoca contextos anteriores. El cierre condicional atómico impide que un gesto viejo cierre la cuenta nueva. Las cuatro actividades verifican sellos de Intent/Bundle antes de restaurar UI/modelos. Cuenta perdida requiere login, sin restaurar su borrador como académico. Académico puede recuperar estado tras pérdida de proceso cuando el nuevo contexto sigue siendo académico. Un estado incompatible/sin sello se descarta, conservando archivos duraderos.

Las rutas transmiten el sello y los gestos/callbacks comprueban el contexto. Las pantallas viejas se retiran sin abrir el login encima de una navegación nueva. Inicio evita renderizar sobre vistas retiradas. El folio inicial del mapa y las fotografías usan el contexto capturado; las fotos comprueban acceso después de lectura/decodificación.

## Comprobación

**73 casos distintos aprobados, cero fallos/errores/omitidos; 59 nuevos.** Nuevos: 32 de acceso, 5 de nombres, 4 SQLite del host, 10 de sellos/restauración y 8 de sesión. Los 14 anteriores de ingreso/sesión se repitieron por el cambio de contrato, sin contarlos como nuevos.

SQLite real del host utiliza esquema y selecciones productivos en tres archivos separados: folio, páginas, filtros de mapa, mismo UUID independiente, reapertura y conservación de filas/esquema/versión/huellas. Acceso prueba rechazo antes/después, respuesta tardía, objetos originales, CAS y errores; sesión cubre A→salir→A y cierre viejo que conserva B.

Kotlin debug/release y pruebas Android de app/datos compilados. Lint: **app 0 errores/22 advertencias anteriores; datos 0 errores/0 advertencias**. git diff --check aprobado. Evidencia local: .gradle/validacion/fase26-resultados y fase26-final.log.

**4 casos Android nuevos preparados y compilados, no ejecutados:** consultas por cuenta/históricos; diario/checkpoint/foto privados y reapertura; pantalla vieja que termina sin cerrar B; borrador conservado al recrearse dentro de A. Usan almacenes aislados y teléfono controlado, sin acreditar otra prueba física GPS. SQLiteOpenHelper, callbacks/Bundle reales y aceptación visual quedan para 0.6.0; la prueba del host no los sustituye.

## Avance

**19/28 = 67.9% de construcción**, mismos criterios/pesos del PDF: Usuarios 4/4, Reportes 4/4, GPS 4/4, Consulta 3/4, Mapas 4/4, Administración 0/4 y Notificaciones 0/4. Aceptación Android pendiente; identidad/aislamiento locales, sin servidor, identidad civil o permisos administrativos.

El siguiente bloque es seguimiento de estados y administración, con permisos comprobados e historial. Se conserva login/loading del compañero y el acceso académico separado de cualquier rol real.
