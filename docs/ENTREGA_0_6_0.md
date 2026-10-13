# Entrega 0.6.0

**0.6.0 / código Android 28**, entrega autorizada por el usuario, reúne las fases 21 a 27 y una base pequeña de la fase 28. Conserva el paquete, firma académica, datos y login/loading del compañero. [Release y APK 0.6.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.6.0).

## Cambios respecto a 0.5.7

- **Cuentas locales:** registro, verificación de contraseña y cierre de sesión, conectados a los layouts existentes. Cada UUID tiene sus reportes, consultas, fotos, borrador y solicitudes pendientes. Históricos conservados en académico, sin atribuirlos a otra cuenta.
- **Consulta por páginas:** hasta 50 coincidencias visibles, búsqueda de texto/fechas/tipo/estado/prioridad y continuación explícita cuando aún queda recorrido. Cambiar o repetir una búsqueda vuelve al inicio. Recuperación de criterios y posición cuando Android restaura el estado, sin guardar páginas completas o evidencia GPS.
- **Mapa de reportes:** fechas del incidente y prioridad combinadas con la zona, tipo y estado. La lista de zona representa los mismos pines; cámara y filtros invalidan los resultados anteriores. La búsqueda general trabaja fuera del hilo de interfaz.
- **Historial del folio:** consulta por gesto de eventos realmente guardados, actualización y reintento independientes de la ficha. El reporte nuevo y su evento de creación se confirman juntos; los antiguos avisan cuando no hay un registro completo.
- **Base de la siguiente fase:** coordinación interna de un cambio de estado con identidad retenida, resultado incierto y reintento explícito. No está conectada a una pantalla de escritura ni concede permisos.

## Datos, acceso y límites

La migración de reportes a SQLite esquema 3 es aditiva; conserva filas, fotografías y estados. La base de cuentas mantiene esquema 1. Reintentar un guardado usa el mismo UUID y no duplica el reporte ni su creación. Cerrar sesión conserva los archivos y revoca pantallas/callbacks anteriores. Una sesión perdida requiere login; no se reasigna su borrador al académico.

Los estados futuros siguen el orden Reportado → En revisión → Atendido → Cerrado. El servicio exige motivo, operación idempotente, estado/secuencia esperados y actor autorizado, con CAS y evento en una transacción. La composición productiva deniega escritura. El usuario eligió **dejar pendiente la asignación administrativa**; admin/admin conserva únicamente el acceso académico. Falta identidad/permiso y recuperación durable de solicitudes para activar ese recorrido.

Se mantiene GPS preciso, reciente, no simulado y radio 5 km, fotografías privadas y aviso local de repetidos a 200 m / ±2 h. Las cuentas y los reportes existen solo en el teléfono, sin servidor o sincronización. Consultar un estado no confirma atención por una autoridad; el producto no envía reportes a emergencias ni genera notificaciones administrativas.

## Validación de la entrega

**1164 casos distintos aprobados, cero fallos, errores u omitidos:** 159 dominio JVM, 88 datos JVM, 646 app JVM, 207 datos Android y 64 app Android. Los 14 nuevos de este incremento son 12 de coordinación de cambios y dos de filtros/lista del mapa. Los 15 Android de fase 27 ya se ejecutaron. Los resultados de preparación y las repeticiones no se suman nuevamente.

Debug/release/androidTest ensamblados; firma v2 con certificado académico conservado, minSdk 26 y targetSdk 37. Lint: cero errores y 22 advertencias por variante de app; datos sin incidencias. El APK no contiene fixtures o instrumentación de pruebas. La APK anterior 0.5.7 conserva su hash. El [cierre agrupado](VALIDACION.md) explica las aserciones antiguas del harness corregidas, los recibos finales y los límites de aceptación.

**citizensecurity-0.6.0-academico.apk:** 54.951.553 bytes, SHA-256 `c32201071bb245ef8fa3236e124788ba100bd0fc9ba2614afeaf9bb6549ee039`. Las pruebas Android utilizan un emulador aislado, sin instalar en el teléfono del usuario. La ubicación controlada no acredita otra captura GPS física.

La guía [Probar 0.6.0](PROBAR_0_6_0.md) describe cuentas, actualización conservando datos, lista, mapa e historial. La aceptación del usuario permanece separada de las pruebas automatizadas.

## Plan del PDF

**19/28 = 67.9% de construcción**, mismos criterios y pesos: Usuarios 4/4, Reportes 4/4, GPS 4/4, Consulta 3/4, Mapas 4/4, Administración 0/4 y Notificaciones 0/4. El historial y la base de cambios no completan el recorrido administrativo mientras falten permisos/actor y recuperación durable. Publicar la APK no suma otro hito.

Para continuar: identidad y permiso administrativos acordados, diario durable de operaciones y recorrido autorizado de consulta/cambio; después, notificaciones. [Fase 27](FASE_27.md) · [Fase 28](FASE_28.md) · [Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md).
