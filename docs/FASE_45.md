# Fase 45 — guía del resultado del incidente

**18 de octubre de 2026 · desarrollo local Unreleased, validado.** Después de completar la [entrega oficial 0.6.3](ENTREGA_0_6_3.md), el usuario pidió continuar refinando el recorrido guiado. Este corte amplía la mejora **E11** para explicar el resultado del incidente de E12 justo donde se consulta y declara. No agrega criterios al PDF: se conserva el **28/28 = 100% de construcción funcional local** previamente acreditado.

## Ayuda junto a Resultado del incidente

Un icono de ayuda junto al título **Resultado del incidente**, con un área de toque de **48 dp** y nombre accesible, abre voluntariamente una guía general de cuatro pasos. El recorrido completo pasa de los **seis temas/20 pasos de la APK oficial 0.6.3** a **siete temas/24 pasos en este desarrollo**.

| Paso | Qué explica |
|---|---|
| Resultado y seguimiento | Sin confirmar, Solucionado y Sigue sin solucionarse son declaraciones locales independientes de Reportado → En revisión → Atendido → Cerrado. Cerrado no significa Solucionado. |
| Quién y cuándo | El historial identifica quién declaró, su cuenta y rol, fecha y motivo. Autor y administrador necesitan su autoridad real en el mismo espacio local. El tutorial no introduce un actor o dato de ejemplo. |
| Revisar antes de confirmar | Elegir otro resultado y escribir un motivo válido prepara una decisión. Revisar permite leerla; confirmar requiere otro gesto y sus comprobaciones vigentes. |
| Historial y recibo | Corregir agrega una declaración y conserva las anteriores. Comprobar un recibo solo lee; cualquier reintento exige otro gesto y conserva la operación original. |

La ayuda mantiene **Anterior, Siguiente, Listo y Cerrar**, progreso y reapertura desde el primer paso. Girar con la guía abierta conserva el paso. El texto de la guía es general: no incorpora folio, descripción, ubicación, fotografía, cuenta, UUID, motivo ni datos de otro reporte. Mencionar qué campo identifica al actor no copia su valor al tutorial.

Abrir o recorrer instrucciones no declara un resultado, guarda un reporte, cambia estados, solicita permisos, captura GPS, abre cámara/galería, reconoce recibos, publica avisos o envía mensajes. Las acciones reales mantienen sus gestos y controles independientes. No se añade IA externa, chatbot, personaje, telemetría o tutorial automático.

## Alcance conservado

La declaración de resultado conserva su almacenamiento e historial de fase 44: autor real o administrador autorizado del mismo espacio, motivo de 5–500 caracteres, revisión y confirmación separadas, UUID/rol/fecha y correcciones aditivas. La incertidumbre se consulta mediante un recibo explícito; el pendiente sigue solo en el modelo vivo, sin diario durable ni escritura automática tras reabrir el proceso. La guía no amplía esos permisos o límites.

Login/loading, mapa/GPS, reportes, fotografías, contactos y avisos conservan sus funciones. Este refinamiento no necesita cambiar esquema, paquete, versión Android o permisos. La oficial **0.6.3 / código 31**, sus fuentes etiquetadas, APK y checksum publicados conservan su identidad; siete temas/24 pasos todavía no describen aquella APK de seis/20.

## Probar el refinamiento en Android Studio

La carpeta local ya está en **development/phase45-tour**. En Studio selecciona **app → Run ▶**, entra con tu acceso propio y abre **Reportes guardados → Ver detalle** de un folio ya guardado en ese mismo espacio. Pulsa el icono de ayuda junto a **Resultado del incidente**, recorre los cuatro pasos y cierra la guía; puedes girar con un paso abierto para comprobar que lo conserva. Abrir, cerrar o girar no confirma una declaración. No hace falta crear datos de ejemplo. La APK pública 0.6.3 conserva únicamente los seis temas previos; esta séptima guía se prueba con el código local.

## Criterios y validación

- El acceso de ayuda se identifica y alcanza junto al resultado, sin abrir automáticamente el recorrido.
- Los cuatro pasos explican resultado/seguimiento, actor y motivo, revisión/confirmación e historial/recibo de manera general.
- Consultar y cerrar instrucciones no crea una declaración ni modifica una revisión, campos o datos guardados.
- Navegación, reapertura y giro conservan las reglas del recorrido existente; texto grande y orientación reducida mantienen acceso al contenido y las acciones.
- Los seis temas anteriores conservan sus instrucciones y comportamiento; la APK publicada permanece separada del desarrollo.

**18 casos Android distintos ejecutados y aprobados, dos nuevos**, cero fallos, errores u omitidos finales:

| Suite Android | Casos | Procedencia |
|---|---:|---|
| `GuidedResolutionTourUiTest` | 2 | Nuevos, 15.039 segundos |
| `GuidedTourUiTest` | 8 | Regresión ejecutada de nuevo |
| `ReportResolutionFlowUiTest` | 8 | Regresión ejecutada de nuevo |
| **Total** | **18** | **Dos nuevos y 16 regresiones** |

Las 16 regresiones finalizaron en 101.083 segundos. Los casos nuevos comprueban cuenta autora real y acceso académico: el actor autorizado del estado real de la app se identifica como **AUTHOR**; académico conserva actor nulo y ningún formulario de declaración. Abrir y recorrer la guía conserva resultado, secuencia, eventos de resultado y estado de seguimiento en SQLite, sin capturas GPS. Una revisión preparada mantiene el mismo objeto de comando y UUID; girar con el paso 2 abierto conserva ese paso, sin guardar una declaración.

Kotlin debug/release y ensamblados internos debug/test correctos. Lint ambas variantes: **cero errores y 22 avisos anteriores por variante**, sin incremento. Se revisó una captura horizontal con contenido y controles alcanzables mediante desplazamiento. No se ejecutaron suites JVM o de core para este corte; sus resultados históricos y los 1675 casos de la entrega 0.6.3 no se suman a estos 18.

Recibo `.gradle/validacion/tour45-test-proof.json`; logs ignorados `tour45-build`, `tour45-test-final-build`, `tour45-lint-final`, `tour45-new-native` y `tour45-regression-native`. APK interna de QA SHA-256 `e513cbe0cb7ecb397f2926474c7c7b1c1c89229d9816ab844c17be3260fafe6e`, diferente de la oficial conservada. [Validación](VALIDACION.md) registra la procedencia final.

La ejecución corresponde a un emulador propio aislado API 37, cerrado al terminar después de restablecer sus ajustes de teclado y tamaño de letra. La aceptación física completa, TalkBack real, API 26/fabricantes y muerte completa del proceso conservan comprobaciones propias; giro no equivale a matar el proceso. La fase no cambia el denominador del PDF ni acredita atención institucional o seguridad de producción.

## Publicación

Trabajo **local Unreleased**, posterior a la publicación verificada de 0.6.3. No implica otra Release, APK pública, integración en main o modificación de los archivos publicados. La siguiente entrega se decide mediante su autorización específica.

[Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Fases](FASES.md) · [Recorrido inicial](FASE_43.md) · [Resultado del incidente](FASE_44.md) · [Entrega oficial 0.6.3](ENTREGA_0_6_3.md)
