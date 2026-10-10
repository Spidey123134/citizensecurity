# Fase 15 — guardado, consulta y mapa visibles · 0.5.3

Entrega oficial del proyecto académico, código Android 23. La instrucción actual «ya no mantengas nada oculto» sustituye la restricción anterior para el guardado y consulta construidos. Continúa la preferencia de Releases oficiales. Se conserva la interfaz de acceso del compañero y toda la historia anterior.

## Qué puede probarse

Desarrollador ofrece tres entradas en las dos variantes del APK:

1. **Nuevo reporte · guardado local**: tipo, prioridad, descripción, fecha/hora y referencia. Seleccionar punto en el mapa, buscar GPS por gesto, confirmar cercanía, revisar y guardar. El recibo muestra el folio y permite copiarlo, abrir el detalle o ver el punto en el mapa.
2. **Reportes en este teléfono**: lista SQLite, actualizar, filtros de tipo/estado local, búsqueda por folio y detalle completo. No hay cambios de estado ni cuentas reales. Un folio inválido se explica antes de consultar.
3. **Mapa de reportes por zona**: calles y nombres del mapa nativo MapLibre/OpenFreeMap. Consultar esta zona lee reportes SQLite dentro de la ventana; tipo/estado, límite 50–500 y aviso de resultados adicionales. El pin abre el detalle del mismo folio. Una zona vacía no se presenta como segura. El centro inicial de navegación no se interpreta como GPS.

Los reportes se conservan exclusivamente en el almacenamiento local de la aplicación. El producto explica que no se enviaron a autoridades ni se generó una solicitud de emergencia. Fotos, cuentas verificadas, aislamiento por autor, administración y avisos todavía requieren implementación; no se presentan controles ficticios para ellos.

## Guardado durable y errores corregidos

ReportSubmissionViewModel conserva UUID, contenido y fecha del intento en ReportSubmissionStore mediante AtomicFile privado **antes** de insertar. createOnce reutiliza esa identidad: si SQLite ya confirmó la fila, recupera el mismo resultado; usar la identidad con datos distintos se rechaza. El diario retiene intentos inciertos y resultados confirmados. Abrir la pantalla recupera por lectura; no captura GPS ni inserta automáticamente. Recuperar resultado y Reintentar guardado son gestos distintos. Solo Iniciar otro reporte retira el diario del recibo confirmado; la fila queda conservada.

Corrupción/lectura fallida bloquea una nueva identidad hasta recuperar. Reintentos pendientes mantienen contenido y folio. Si la inserción se rechaza con resultado conocido, se limpia únicamente el intento correspondiente y se vuelve a corregir conservando todos los campos. Dos fallos encontrados en el acoplamiento se corrigieron y tienen regresiones: perder el formulario tras recuperar/rechazar un intento y sobrescribir las correcciones al girar. Invalid.draft se restaura una vez mediante restoreForCorrection y se consume con acknowledgeCorrectionDraft; las coordenadas recuperadas son datos, nunca una credencial GPS.

Al combinar las pantallas apareció además un fallo de consulta del mapa con límite de resultados. Los mensajes de carga/resultado hacían crecer el panel y cambiaban la proyección mientras llegaba la lectura; el binding rechazaba correctamente esa región ya distinta. Se reserva una altura constante para los controles desplazables y la regresión nativa exige que mostrar hasMore no cambie los límites visuales del mapa. Se conserva la comprobación exacta de la región, sin aflojar validaciones.

El GPS de revisión se renueva por gesto separado, se cancela al pausar y las respuestas de capturas antiguas no sustituyen el estado vigente. El guard productivo de SQLite vuelve a comprobar permiso preciso, ubicación habilitada, lectura no simulada, precisión ≤100 m, antigüedad monotónica ≤2 minutos y distancia Haversine + precisión ≤5.000 m antes de insertar. Recuperar una fila ya confirmada no requiere inventar otra captura. No se modifica la versión/esquema SQLite ni se hacen migraciones destructivas.

## Comprobación ejecutada

**594 casos distintos aprobados**, sin sumar repeticiones. **65 definiciones nuevas** respecto a 0.5.2:

| Grupo | Casos | Nuevos | Evidencia |
| --- | ---: | ---: | --- |
| App JVM | 337 | 43 | Submission 25, lista 12 y corrección de borrador 6; reentrancia, cancelación, identidad incierta, respuestas perdidas y restauración |
| Dominio JVM | 96 | 0 | Validación y reglas de cercanía existentes |
| Datos JVM | 38 | 0 | Fuente y renovación existentes |
| Android SQLite/fuente/diario | 99 | 13 | AtomicFile real, corrupción, concurrencia entre instancias, backups, nueva instancia de diario y SQLite con una sola fila |
| Android UI | 24 | 9 | 11 formulario/revisión anteriores, 4 permisos anteriores, 5 guardado/consulta y 4 mapa nuevos |
| Total | 594 | 65 | Cero fallos/errores/omitidos en las suites contabilizadas |

Las pruebas nuevas de interfaz utilizan **SQLite, AtomicFile, Activity y SDK MapLibre reales**. Solo el origen de la lectura del teléfono se controla en una Application de androidTest; se comprueban el permiso y servicio reales de Android. La fixture está ausente de los DEX de debug y release. Cada caso usa nombres de base y diario únicos, sin cambiar la base productiva. El runner usa dependencias reales por defecto para los casos anteriores y las pruebas de permisos.

El recorrido nativo comprueba guardar una fila, copiar/consultar su folio, recrear y abrir otra Activity sin duplicarla, corregir después del GPS caducado, conservar correcciones al girar, recuperar un intento pendiente rechazado y comenzar otro reporte sin borrar el anterior. El mapa comprueba calles/pines realmente dibujados, filtros/vacío, límite/hasMore, pin→detalle y recreación sin consulta automática. La prueba del diario/modelo representa reinicio mediante instancias nuevas y cancelación en los límites del commit; no se declara una prueba de matar todo el proceso Android con un GPS físico.

Emulador API 37 en copia read-only, sin alterar el AVD original. Build debug, release y androidTest aprobados. Lint sin errores, con 19 advertencias anteriores por variante; core/data sin incidencias. APK académico firmado v2 con la misma firma debug de entregas anteriores; no equivale a certificación de distribución productiva. Se conservaron los hashes de APK 0.5.1 y 0.5.2.

Comandos utilizados: :app:testDebugUnitTest, :core:domain:test, :core:data:testDebugUnitTest; :app:assembleDebug, :app:assembleRelease, :app:assembleDebugAndroidTest; :app:lintDebug, :app:lintRelease, :core:data:lintDebug; :core:data:connectedDebugAndroidTest. Las clases UI se ejecutan por am instrument contra ReportTestRunner; los cuatro casos de permiso se preparan/ejecutan por separado con denegación, aproximado, preciso y ubicación desactivada reales del emulador. Los logs y capturas permanecen en .gradle/validacion, sin publicar datos personales.

## Avance del PDF y siguiente aceptación

**13/28 = 46.4%**, mismos hitos y pesos de [AVANCE](AVANCE.md). Se añaden formulario con guardado real autorizado (4.2 pasa a 3/4) y flujo visual de mapa integrado/probado (4.5 pasa a 4/4 de construcción). Consulta permanece 2/4: no hay identidad de usuario ni seguimiento administrativo. Geolocalización permanece 3/4: el origen controlado en las pruebas nuevas **no acredita GPS físico del recorrido final**. No se suman puntos por publicar o repetir suites.

Para la aceptación física: instalar sobre la versión anterior sin desinstalar, entrar a Desarrollador → Nuevo reporte, autorizar ubicación precisa, elegir un punto, buscar GPS cerca de una ventana si hace falta, confirmar/revisar/guardar y abrir el mismo folio en lista/mapa. Comprobar un punto lejano rechazado y que reabrir conserve un solo reporte. No registrar ubicaciones reales en Git. Después, fotografías es el hito restante de 4.2; cuentas y seguimiento requieren coordinar la parte de acceso del equipo.

## Entrega

[Release v0.5.3](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.5.3), APK académico y SHA256SUMS. main contiene el código compartido validado; las entregas anteriores se conservan. Para el compañero: conservar cambios propios, git fetch origin, git switch main y git pull --ff-only origin main. No usar reset --hard para actualizar.
