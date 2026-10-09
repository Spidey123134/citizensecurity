# Fase 14 — revisión completa y guardado sin duplicados

Entrega oficial **0.5.2**, código Android **22**, 9 de octubre de 2026. Continúa los puntos **4.2 Reportes** y su conexión con **4.3 Ubicación**. La publicación está autorizada por la instrucción vigente «desde ahora puro released»; la activación del guardado y consulta reales requiere la autorización específica que el usuario pidió anteriormente y sigue pendiente.

## Función visible

Desarrollador → Nuevo reporte · borrador → Revisar borrador abre una pantalla con tipo, prioridad, descripción íntegra, fecha/hora y zona horaria, referencia y coordenadas si existen. Las coordenadas ausentes se explican; no se sustituyen por cero. «Corregir datos» y Volver recuperan el formulario, y otra revisión muestra los cambios nuevos. La revisión se recupera al recrear la Activity y se vuelve a validar: no recupera una autorización GPS. Seleccionar solo tipo/prioridad o tener texto rechazado también exige confirmar antes de salir del formulario.

La validación admite una referencia sin coordenadas para revisar el borrador, como antes. Eso **no permite insertar un reporte cercano**: una inserción productiva necesita evidencia GPS válida y coordenadas aceptadas por el guard. La nueva revisión no abre SQLite, inicia GPS ni envía reportes.

## Fallo corregido en la base técnica

Una escritura SQLite puede terminar y perder su respuesta si la corrutina se cancela al regresar al hilo principal. Crear de nuevo con un UUID aleatorio producía otro folio. `IdempotentReportRepository.createOnce(requestId, draft)` ahora usa una identidad estable como folio. Un reintento con la misma identidad y contenido normalizado recupera la fila original. Cambiar el contenido con una identidad usada produce conflicto sin modificarla. Dos solicitudes nuevas con el mismo contenido mantienen folios distintos: no se deduplican incidentes por semejanza.

La búsqueda dentro de la transacción protege solicitudes concurrentes desde dos instancias. No cambia el esquema ni la versión de SQLite. Las nuevas inserciones mantienen los tres controles de cercanía; recuperar una fila existente no exige otro GPS ni escribe datos. Un nombre de base inválido se rechaza antes de consultar rutas.

`ReportViewModel` conserva identidad y contenido durante reintentos dentro del mismo modelo, bloquea otro guardado tras `Saved` y exige `startNewReport` para iniciar otro. Si un resultado es incierto, no acepta reintentar con otros datos; una validación rechazada sí permite corregir. **Antes de activar el acceso real falta conectar y conservar duraderamente esta identidad ante muerte del proceso**, además de aceptación física. La pantalla de esta entrega no instancia el modelo de guardado.

## Comprobación

529 casos distintos aprobados: 294 JVM app, 96 JVM dominio, 38 JVM datos, 86 SQLite/fuente Android y 15 controles/formulario Android. Se añaden 21 definiciones: seis de formato de revisión, tres de guardado, cuatro de revisión visible y ocho de idempotencia SQLite. Las repeticiones no aumentan este total.

Se usa únicamente la instancia read-only del AVD en emulator-5586, sin instalar sobre un teléfono ni modificar la instancia del usuario. Las cuatro pruebas de permisos preparan los estados del sistema Android; no fabrican GPS. Los casos de datos usan bases temporales o en memoria aisladas. Los casos del formulario comprueban que sus archivos de base no cambian. Capturas de revisión/mapa contienen datos ficticios de laboratorio.

El primer pase de SQLite detectó el acceso prematuro a rutas con un nombre inválido. Se corrigió producción y se repitieron las 86 pruebas completas. JVM app/datos y controles Android se comprobaron con el corte final; dominio mantiene su pase inicial del mismo contrato. Debug/release compilan y lint tiene cero errores, 19 advertencias anteriores por variante de app y cero en datos. APK académico firmado con la firma debug del proyecto, conservando las entregas previas.

## Avance y siguiente cierre

**11/28 = 39.3%**, con los mismos criterios/pesos. Esta mejora no completa el hito 4.2 de formulario con guardado real autorizado ni el flujo GPS físico 4.3.4. La siguiente función propuesta es guardar localmente con GPS válido → obtener folio → consultar «Reportes en este teléfono» (no hay identidad de usuario real todavía). La consulta y guardado se mantienen ocultos mientras la confirmación esté pendiente. No se implementan avisos a autoridades, fotos, cuentas, roles ni administración.
