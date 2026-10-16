# Fase 42 — del cambio confirmado a la actividad y los avisos

**16 de octubre de 2026 · desarrollo local Unreleased, validado.** Completa **4.7 Notificaciones** del PDF utilizando los cambios autorizados reales de fase 41. Conserva la cuenta y asignación `admin_local`, el espacio de datos capturado, los esquemas, login/loading y el reconocimiento separado de las solicitudes.

## Recorrido incorporado

El recibo administrativo de un cambio confirmado ofrece dos accesos por gesto explícito:

- **Ver actividad de esta cuenta** abre las creaciones y transiciones guardadas en el mismo espacio. Sus filtros permiten revisar solo cambios y abrir el folio correspondiente.
- **Revisar avisos de cambios** abre el panel de preferencia, permiso/canal de Android y revisión manual. El nombre del acceso no significa que se ejecute una revisión al abrirlo.

Las rutas no llevan folio, motivo, actor del cambio, revisión del reporte o cuerpo de la solicitud en extras. Conservan el sello interno de dueño/proceso/revisión de sesión que verifica el workspace; ese sello no concede autoridad. No reconocen el recibo, limpian el diario, reinician la preparación o activan preferencias. Al volver, la solicitud administrativa continúa disponible; **Entendido: cerrar esta solicitud** conserva su propio gesto y revalidación.

El botón actúa únicamente sobre el recibo y la sesión que lo presentaron, con la pantalla vigente. Una respuesta anterior, un giro o una sesión terminada no autoriza abrir otro espacio. Actividad y Avisos son consultas del propio workspace; no adoptan permisos administrativos desde la ruta o el nombre de usuario.

## Aviso sobre una transición real

El recorrido comprobado utiliza el escritor de fase 41 con asignación local, política, SQLite y diario reales. Una transición guardada conserva su UUID de operación en el evento SQL. **Revisar cambios** consulta únicamente `STATUS_CHANGED`: la creación de un reporte no se convierte en aviso de cambio y una transacción rechazada o que no guardó el cambio no origina un evento. Un cambio ya confirmado por la transacción SQL puede avisarse aunque la interfaz todavía deba recuperar o reconocer su recibo administrativo; ese reconocimiento no determina la existencia del evento.

La preferencia se activa voluntariamente para el acceso actual. Android conserva su decisión de permiso, canal y ajustes; entrar a la pantalla, volver del recibo o girar no activa el permiso ni publica. La revisión es manual, en páginas de **50**, hasta **500 eventos por gesto**, con continuación explícita cuando queda recorrido.

El resumen usa texto general, sin cuenta, folio, actor, motivo, ubicación o fotografías. Su entrada normal no transporta una solicitud privada ni concede acceso. La aceptación del sistema no prueba que una persona haya visto el aviso o que una autoridad haya recibido o atendido el incidente.

Después de que Android acepta el resumen, la app registra los UUID de operaciones con la revisión CAS leída. Repetir la revisión evita volver a avisar de las operaciones ya registradas. El límite permanece en **4096 IDs**: alcanzar la capacidad conserva el registro y muestra el problema, sin recortarlo. Desactivar cancela el resumen propio y mantiene esos IDs.

Un permiso/canal bloqueado conserva los eventos para otra revisión autorizada. Un resultado incierto de publicación o persistencia exige volver a leer las preferencias antes de otra revisión; no se acredita éxito mediante una copia en memoria. Cancelación, cambio de sesión o una respuesta tardía retiran únicamente el intento propio, sin cancelar un resumen posterior de otro revisor.

## Conservación de archivos de avisos

El endurecimiento del almacén impide que leer mediante `AtomicFile` restaure un `.bak` sobre el archivo vigente o retire un `.new` desconocido. Un residuo pendiente, con o sin archivo base, bloquea lectura y escritura y se conserva. No se elige silenciosamente una copia antigua, convierte corrupción en ausencia o reinicia la preferencia y sus IDs.

Las 26 pruebas nativas del almacén aprobaron conservación de archivos e IDs, lectura ordinaria, preferencias, capacidad, CAS, cancelación y acceso. Los diez casos nuevos incluyen base con residuos y respaldo sin base; leer no restaura ni limpia los bytes inciertos. [Validación](VALIDACION.md) registra el cierre y los límites del corte.

## Relación con el PDF

**28/28 = 100% de construcción funcional local.** La fase 42 acredita los tres criterios restantes de **4.7**: eventos de cambios autorizados, entrega de avisos y recorrido completo probado. Notificaciones alcanza **4/4**, conservando permiso/preferencias de fase 36; los otros seis requisitos permanecen en **4/4**. Quedan **cero criterios funcionales locales** pendientes del plan.

La aceptación física completa y preparación de una entrega conservan sus comprobaciones propias. El porcentaje no acredita seguridad de producción, servidor, sincronización entre teléfonos, operación policial o veracidad de incidentes. Las ampliaciones E01–E10 mantienen su catálogo y no se añaden al denominador.

## Validación y límites

**1431 casos distintos aprobados, 14 definiciones nuevas**, sin fallos, errores u omitidos finales. Son **1379 JVM** —205 recibos intactos de dominio reutilizados, 148 datos y 1026 app ejecutados— y **52 Android API 37 ejecutados en este corte**. Los 14 nuevos corresponden a diez casos del almacén y cuatro del recorrido integrado; no se suman ejecuciones repetidas o cortes anteriores.

Los **52 Android** se desglosan en **26 de almacenamiento** y **26 de app**: cuatro nuevos de integración, cuatro regresiones de Avisos, diez de publisher y ocho del cambio de estado. El recorrido nuevo usó asignación local, política, diario y escritor reales; comprobó la misma operación desde la transición SQL hasta el registro de avisos, deduplicación y navegación que conserva recibo/solicitud. También comprobó aislamiento entre cuentas y errores de preferencias, sin atribuir una prueba de asignación administrativa ilegible a estos cuatro casos.

La integración aceptó el diálogo nativo real de permiso. Las regresiones de Avisos ejecutaron además denegación y concesión reales; el permiso y sus marcas se restablecieron exclusivamente entre instrumentaciones terminadas del emulador propio para repetir ese recorrido, sin revocarlo durante una instrumentación activa. Las pruebas de publisher comprobaron resumen genérico y controles Android; el cambio de estado conservó sus regresiones. Lint terminó con **cero errores y 22 avisos existentes por variante de app**, datos sin incidencias. Compilación Kotlin debug/release y ensamblados internos de depuración y pruebas aprobados.

El emulador propio API 37 de solo lectura quedó cerrado. No se modificó un teléfono físico ni se acredita ejecución API 26 o muerte completa del proceso. Los ensamblados son material de QA: la APK interna final tiene SHA-256 `114323c0a97e9adc07fa3f2f7996d6054df65b982f1d0bf19699dacd11d836a4`, sin constituir una descarga oficial. El recibo ignorado `alerts42-test-proof.json` y [Validación](VALIDACION.md) registran la evidencia.

El cierre de fase 41 conserva **1488 casos distintos/103 nuevos**: incluyó otras selecciones nativas. Este corte usa su propia selección de 52 Android y no implica que se hayan retirado pruebas; los totales de ambos cortes no se suman.

## Publicación

**Este corte permanece local:** no actualiza GitHub/main, la rama compartida o el [PR borrador #30 de fase 40](https://github.com/Spidey123134/citizensecurity/pull/30), ni publica APK, tag o Release. La descarga oficial **0.6.1** conserva su contenido. El candidato 0.6.2 archivado precede a las fases 38–42 y necesitará prepararse otra vez cuando se autorice la siguiente entrega.

[Guía para el equipo](ADMINISTRADOR_LOCAL.md) · [Fase 41](FASE_41.md) · [Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Fases](FASES.md) · [Validación](VALIDACION.md)
