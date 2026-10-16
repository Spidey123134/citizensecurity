# Entrega acumulada 0.6.2

**16 de octubre de 2026 · Android 0.6.2 / código 30 · Release oficial publicada.** Esta entrega agrupa **todas las fases 35–42 posteriores a la oficial 0.6.1**, incluidas las que habían quedado Unreleased o solo locales. La petición de publicar reemplaza el aplazamiento anterior; las fases no se entregan como ocho APK separadas. [Guía para probar](PROBAR_0_6_2.md) · [Release 0.6.2](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.2).

Conserva paquete, certificado académico, datos, cuentas y el login/loading del compañero. Las funciones anteriores de reportes con cámara/galería, mapa con calles, punto/GPS a 5 km, posibles repetidos a 200 m/±2 h, ficha PDF, contactos y accesos de ayuda siguen incluidas. Los estados y avisos describen información de este teléfono: no acreditan envío o atención institucional.

## Todos los cambios desde 0.6.1

| Fase | Cambio incluido en 0.6.2 |
|---|---|
| [35](FASE_35.md) | **Actividad de reportes:** creaciones y cambios realmente registrados, filtro de cambios, páginas de 50 y acceso al folio. Actualizar vuelve al inicio; los históricos conservan sus datos sin inventarles eventos. |
| [36](FASE_36.md) | **Avisos voluntarios por cuenta:** permiso/canal de Android, preferencia inicialmente desactivada, revisión manual y resumen general sin datos privados. Registro durable por operación para evitar repetir avisos aceptados. |
| [37](FASE_37.md) / E06 | **Mensaje revisado para un contacto:** destinatario del mismo espacio, ubicación inicialmente omitida, texto completo y otro gesto para abrir SMS. El usuario decide enviar allí; el operador puede cobrar. La ficha PDF conserva su acceso propio. |
| [38](FASE_38.md) | **Permisos separados:** consultar y cambiar son capacidades independientes, denegadas por defecto, comprobadas antes/después de lecturas y dentro de la transacción antes de confirmar un cambio. |
| [39](FASE_39.md) | **Borrador legible y sesión protegida:** tarjetas de datos, lugar y foto; imagen completa contenida en un marco de 240 dp. Consulta administrativa ligada al espacio y sesión capturados; salir invalida el acceso anterior incluso si vuelve la misma cuenta. |
| [40](FASE_40.md) | **Administración local real:** registro de admin_local con contraseña propia, confirmación nativa del propietario y segundo gesto de asignación. Consulta de su espacio con filtros, folio e historial paginado. |
| [41](FASE_41.md) | **Cambios con motivo e historial:** Reportado → En revisión → Atendido → Cerrado, un paso por solicitud, revisión y confirmación separadas. Actor, fecha, motivo, secuencia y operación durables; recuperar consulta el mismo recibo y reintentar conserva el mismo UUID. |
| [42](FASE_42.md) | **Del cambio confirmado a Actividad y Avisos:** accesos independientes que conservan el pendiente. Integración del cambio autorizado real, evento SQLite, revisión manual y resumen Android. Archivos inciertos .new/.bak se conservan y bloquean sin restaurar o limpiar preferencias/IDs. |

## Administración, actividad y avisos

**admin_local no se crea automáticamente.** Se registra con la interfaz existente y contraseña elegida por su dueño, de **15–128 caracteres**. La primera asignación requiere confirmar PIN/patrón/contraseña en la pantalla nativa de Android y, al regresar, pulsar **Activar administración local** dentro de su vigencia. No se publica una contraseña predeterminada. **admin/admin conserva acceso académico**, sin permisos administrativos. [Guía completa](ADMINISTRADOR_LOCAL.md).

La asignación administra **solo el espacio de esa cuenta en esa instalación**; no accede a otras cuentas o teléfonos. Cada cambio exige motivo de **5–500 caracteres**, revisión y segundo gesto. Las interrupciones conservan la solicitud; recuperar busca el recibo sin repetir el cambio, y reconocerlo exige su acción propia. **Atendido/Cerrado no confirman atención de una autoridad.**

Desde el recibo puedes abrir Actividad o Avisos. Esa navegación no reconoce el recibo, activa avisos o revisa/publica cambios automáticamente. Para un resumen se requieren preferencia de esa cuenta, permiso/canal de Android y **Revisar cambios**. Guardar un reporte genera su creación, pero por sí solo no es un cambio de estado para avisar.

La revisión es **manual dentro de la app**, en páginas de 50 y hasta 500 eventos por gesto; si queda recorrido ofrece continuar. El registro conserva hasta 4096 identificadores en un archivo acotado. Desactivar retira el resumen propio y conserva los IDs aceptados. No hay revisión en segundo plano, push o sincronización. La aceptación por Android no prueba lectura o recepción institucional.

## Binario final y conservación de datos

- APK final publicada: [citizensecurity-0.6.2-academico.apk](https://github.com/Spidey123134/citizensecurity/releases/download/v0.6.2/citizensecurity-0.6.2-academico.apk), junto con [SHA256SUMS-0.6.2.txt](https://github.com/Spidey123134/citizensecurity/releases/download/v0.6.2/SHA256SUMS-0.6.2.txt). Ambos archivos descargados de GitHub se verificaron contra el binario final.
- Tamaño: **56,200,431 bytes**.
- SHA-256: `114323c0a97e9adc07fa3f2f7996d6054df65b982f1d0bf19699dacd11d836a4`.
- Paquete: `com.example.citizensecurity`; minSdk **26**, targetSdk **37**.
- Certificado SHA-256: `0a4006a1d2ef2ad02a0dad7c51b55c0ba5ffcd096a226eed6456326b2fed3ebd`, el mismo de la oficial 0.6.1.

Instala **encima de la app anterior, sin desinstalar ni borrar datos**. Si Android la rechaza, conserva la instalación y revisa el archivo. La base de reportes migra aditivamente de **esquema 3 a 4**, conservando reportes, fotografías e historial; las cuentas mantienen esquema 1.

La actualización **0.6.1 → esta APK final 0.6.2** se comprobó en emulador propio API 37 mediante instalación encima de la anterior: **seed061, 1 caso aprobado; verify062, 1 caso aprobado**. Antes de migrar se contrastaron los bytes de base de datos, contactos y fotografía; después se comprobó esquema 3 → 4, integridad de SQLite y recuperación de reporte, historial, contactos y foto. El emulador se cerró; no se instaló o alteró el teléfono físico. Recibos locales: `.gradle/validacion/release062-final/upgrade-seed.log` y `upgrade-verify.log`. Esta comprobación usa el archivo final y es distinta de la prueba histórica del candidato de solo fases 35–37. [Validación](VALIDACION.md).

**Antecedente archivado, separado del archivo final:** el candidato interno antiguo de solo fases 35–37, conservado en .gradle/validacion/citizensecurity-0.6.2-academico.apk, mide 55,302,267 bytes y tiene SHA-256 `ee75a653fc468153d062747bfeca5727104a236d29dcf876bacd6494162d3b2f`. No identifica la entrega acumulada. El binario final se conserva en .gradle/validacion/release062-final/; el archivo antiguo se conserva y no se publica como si incluyera fases 38–42.

## Validación y avance del plan

El corte final de **fase 42** acredita **1431 casos distintos aprobados, 14 nuevos**, cero fallos/errores/omitidos finales:

- **1379 JVM:** 205 recibos intactos de dominio reutilizados sin cambios, 148 de datos y 1026 de app ejecutados en ese corte.
- **52 Android API 37 ejecutados:** 26 de almacenamiento de avisos —10 nuevos—, cuatro de integración nueva sobre cambio autorizado/rol/diario/SQLite reales, cuatro de avisos, diez de publicador y ocho de cambios administrativos.
- Lint app sin errores y con **22 avisos existentes por variante**; datos sin incidencias.

Las fases anteriores conservan evidencias propias. Fase 41 aprobó 1488 casos distintos de un conjunto diferente; **no se suman 1431 y 1488 ni las repeticiones de cada fase** para inventar un total de Release. Compilación, identidad del APK y actualización tienen recibos separados. [Desglose completo](VALIDACION.md).

Permiso nativo y denegación/concesión, canal Android, deduplicación, aislamiento, PIN nativo del emulador y cambios reales se probaron en cortes documentados. Los SMS de pruebas se interceptaron: no se enviaron mensajes. Estas ejecuciones API 37 no acreditan otra prueba de cámara/GPS físicos, aceptación completa en teléfono, API 26, fabricantes o muerte completa del proceso.

**Avance: 28/28 = 100% de construcción funcional local del plan original**, con cada requisito 4.1–4.7 en 4/4. Quedan cero criterios funcionales locales de ese denominador; aceptación física completa, seguridad de producción e integración institucional se comprueban aparte. E01–E10 amplían utilidad sin aumentar el porcentaje del PDF.

[Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Fases](FASES.md) · [Guía para probar](PROBAR_0_6_2.md) · [Entrega anterior](ENTREGA_0_6_1.md)
