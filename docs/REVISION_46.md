# Revisión de errores y guía de estudio

18 de octubre de 2026. Trabajo local posterior a fase 45, sin cambio de versión, publicación o subida a GitHub. La oficial 0.6.3 conserva su código etiquetado y archivos.

## Lectura del código y textos

Se revisaron las fuentes y recursos del producto para localizar etiquetas de prueba, textos transitorios y comentarios innecesarios. La limpieza afecta a la redacción visible y a comentarios que repetían el XML; conserva el historial de Git, la autoría, los registros de colaboración y las guías históricas.

El botón de acceso alternativo ahora dice «Acceso académico sin cuenta», en lugar de «Desarrollador · funciones básicas». La indicación de admin/admin y el mensaje de Inicio explican el mismo acceso académico, separado de las cuentas registradas y sin administración real. Se retiraron comentarios de XML que repetían el nombre de los controles; se conservaron las notas sobre IDs compartidos. Una comparación estructural de los cuatro layouts confirmó que no cambiaron atributos, textos, vistas u orden por esa limpieza.

## Errores reproducidos

### Ajustar el pin descartaba una lectura GPS válida

Después de obtener ubicación, elegir o mover el punto convertía la pantalla en PointSelected con deviceFix nulo. El control de confirmar dejaba de estar disponible y obligaba a repetir una captura aun cuando la lectura seguía vigente.

IncidentLocationViewModel.select conserva ahora solo la lectura visible obtenida en la misma apertura. No captura otra, confirma el punto anterior ni convierte el pin en evidencia del teléfono. Confirmar vuelve a leer y validar precisión, antigüedad, simulación y distancia. Al salir de la pantalla sigue siendo obligatorio renovar GPS.

Se añadieron tres regresiones. Antes del arreglo, las dos primeras fallaron por la lectura perdida y el caso de salida de pantalla pasó. Después del cambio pasó la suite JVM completa de la app: 1080 casos, sin fallos, errores u omitidos. La prueba también rechaza un punto lejano aunque se conserve una lectura válida.

### Consultar historial administrativo descartaba una declaración preparada

Al actualizar el historial de estados del mismo folio, la consulta pasa por Loading. La pantalla administrativa limpiaba el modelo independiente del resultado en cualquier Loading, descartando el motivo y objetivo preparados o el comando ya revisado. No implica pérdida de un reporte guardado ni escritura o acceso no autorizado.

AdministrationConsultationContent conserva ahora la preparación durante Loading únicamente cuando se consulta el mismo folio. Un listado, otro folio, un error o una denegación siguen retirando ese estado. La sección continúa comprobando el dueño del reporte y las escrituras conservan sus controles de sesión, autoridad, revisión y ventana activa.

Dos pruebas Android usan cuenta administrativa real, pantalla y SQLite del espacio aislado. Una transacción sin cambios retrasa la lectura para reproducir la carga lenta sin depender de la velocidad del emulador. Con el APK anterior, ambas fallaron: la preparación y revisión se convertían en Idle. Con el arreglo conservaron el mismo estado y comando/UUID, sin guardar eventos. El cierre del corte registra las regresiones completas.

El entorno también necesitó una corrección independiente. Las herramientas de línea de comandos antiguas crearon el AVD privado con target android-0 para una imagen android-37.0. Se corrigió solo ese AVD de pruebas, conservando el emulador del usuario. Antes de resolverlo, SurfaceFlinger interrumpía las instrumentaciones; esos intentos no acreditan un fallo del producto ni cuentan como pruebas aprobadas. Hay un [reporte de reproducción similar en las herramientas Android](https://github.com/actions/runner-images/issues/14484).

## Guía para estudiar

[Guía editable](GUIA_ESTUDIO_APP.md). La copia de lectura se encuentra en output/pdf/Guia_estudio_CitizenSecurity.pdf de esta carpeta de proyecto. Explica arquitectura, recorrido del reporte, GPS y mapas, fotografías, cuentas, SQLite, resultados, operaciones repetidas, contactos, PDF, avisos y tutorial. Contiene preguntas y ejercicios para demostrar comprensión con el código real.

El material conserva los límites del proyecto: datos locales, sin recepción institucional, sincronización entre teléfonos o identidad policial verificada. Cada explicación incluye referencias para comprobarla en el código.

## Alcance de la comprobación

El corte final ejecutó **1103 casos distintos, cinco nuevos**, sin fallos, errores u omitidos: **1080 JVM de app** y **23 Android**. En Android se ejecutaron resultado del incidente 10, guía del resultado 2, recorrido guiado general 8 y acceso de cuentas 3. La instrumentación final terminó en 159.029 segundos; los dos casos administrativos rojos anteriores terminaron con los fallos previstos en 45.006 segundos. No se suman esas repeticiones al total final.

Kotlin debug/release y ensamblados internos correctos. Lint de ambas variantes: cero errores y 22 avisos anteriores por variante. No cambiaron dominio, datos, esquema o numeración. APK interna SHA-256 `6e187a7acd32fc0bfed7b6e8287dfa71be0c895c702ec8071be3f79e65575eb5`, sin publicar; oficial 0.6.3 conservada. Recibo ignorado `.gradle/validacion/revision46/test-proof.json`; logs `revision46-location-red.log`, `revision46-unit-green.log`, `revision46-admin-red-final.log`, `revision46-final-build.log` y `revision46-native-final.log`.

La guía de ocho páginas se renderizó e inspeccionó completa. PDF de 80011 bytes, SHA-256 `154a64f8ff8e7ae6c363bbd45e4cbc92a8e649ac63208277ef705f100baf34c5`; fuente Markdown conservada. El emulador propio de solo lectura se cerró tras restaurar teclado y letra; el emulador del usuario y el teléfono físico no se modificaron.

Los intentos rojos y los problemas del entorno se guardan como evidencia de diagnóstico; no se suman como casos finales aprobados. Los resultados de fases anteriores no se presentan como nuevas ejecuciones. Sin pruebas en teléfono físico, cambios de PIN, borrado de datos del usuario o nueva aceptación física completa. El 28/28 de construcción funcional local del PDF conserva sus límites.
