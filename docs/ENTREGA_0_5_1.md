# Entrega oficial 0.5.1

Código Android 21. El usuario cambió el flujo de entrega a Releases oficiales. Incluye las fases 12 y 13, antes locales, y su acoplamiento al código principal. Conserva las versiones publicadas y APK anteriores.

## Qué puede hacer el usuario ahora

En Desarrollador → Nuevo reporte · borrador puede elegir tipo/prioridad, escribir descripción, fecha y referencia, abrir un mapa con calles, tocar un punto, solicitar permiso preciso y buscar GPS por un gesto separado. Cancelar o recrear la pantalla conserva el formulario. Revisar borrador valida los campos y explica que no se ha guardado ni enviado.

El mapa distingue estilo cargado de mosaicos y fotograma completos; las capturas y pruebas comprueban nombres reales y pin. Los controles desplazables mantienen Cancelar accesible en horizontal. La Activity está incluida en main y en las dos variantes, ya no solo en debug. El APK académico está firmado con la firma debug del proyecto, igual que las entregas anteriores; no es una distribución de Play Store.

## Puntos del PDF

| Punto | Avance concreto de esta entrega | Pendiente |
| --- | --- | --- |
| 4.2 Reporte | Formulario editable, revisión de campos y recuperación al volver del mapa/recrear | Activación expresamente autorizada del guardado real y fotos; no se suma el hito de formulario con guardado |
| 4.3 Geolocalización | Controles conectados y estados de permiso denegado/aproximado/preciso y ubicación desactivada comprobados contra Android; mensajes de recuperación | GPS físico y confirmación completa dentro de esta pantalla |
| 4.5 Mapas | Calles y nombres visibles, selección por toque y pin real en el formulario | Consulta real, reportes por zona y aceptación del flujo completo |

Se completa únicamente el hito existente **permisos y estados GPS**, con respaldo de los modelos/fuente ya probados. Geolocalización pasa a 3/4; total 11/28 = 39.3%. No se convierte publicar en un hito ni se afirma aplicación terminada.

## Pruebas

285 casos JVM de app y 11 nativos: siete de formulario/mapa, más cuatro nuevos. Los nuevos se ejecutaron individualmente en una instancia AVD read-only, preparando permisos reales mediante Android antes de iniciar cada caso: ninguno, COARSE, COARSE+FINE, y ubicación del sistema desactivada con permiso preciso. El binding productivo leyó el estado del sistema; no se asignó acceso al ViewModel ni se fabricó GPS. Se comprobó que conceder permiso no captura y que ubicación desactivada no confirma ni modifica coordenadas. No acredita uso del diálogo de permiso por una persona ni GPS físico de esta pantalla.

Los siete casos de formulario comprueban campos, retención, texto rechazado, cancelación/recreación, mapa con nombres/pin y orientación horizontal; las bases previas conservaron sus hashes. El primer pase de los cuatro nuevos falló porque assertTextContains exigía coincidencia completa por defecto; se corrigió a substring=true para comprobar el fragmento observado, sin alterar producto o reglas. Las repeticiones no aumentan 296.

Debug/release y APK de pruebas compilan; firma debug v2 válida, lint cero errores y 19 advertencias anteriores por variante. Las clases de instrumentación quedan fuera del producto. Artefactos y recibos locales se conservan en .gradle/validacion; no se publica información GPS del teléfono ni claves. No había teléfono conectado en esta sesión.

## Límites y siguiente punto

Guardado y consulta reales permanecen ocultos conforme a la instrucción específica anterior. El borrador no envía avisos a emergencias. Se conserva login y acceso académico admin/admin; no son autenticación o administración reales. El punto 4.2 de guardado requiere decidir su activación explícita; el flujo 4.3.4 necesita teléfono físico. Administración y notificaciones todavía no se implementan.
