# Fase 43 — recorrido guiado contextual

**16 de octubre de 2026 · desarrollo local Unreleased, validado.** Atiende la petición de un icono de instrucciones y un recorrido tipo “take a tour” para entender la app con menos texto permanente. Es la ampliación **E11** del [catálogo de utilidad](MEJORAS_UTILES.md); los requisitos del PDF mantienen **28/28 = 100% de construcción funcional local**.

## Recorrido de esta entrega

Un icono **?**, identificado para accesibilidad como **Abrir recorrido de…**, abre voluntariamente la guía de la pantalla actual. Cada paso presenta una explicación breve y su posición en el recorrido. **Anterior** y **Siguiente** recorren instrucciones; **Listo** termina la guía y **Cerrar** permite salir antes. Se puede abrir de nuevo para empezar desde el primer paso; girar conserva el paso abierto. Cambiar de tema inicia su propia guía, sin guardar un perfil de tutorial completado.

| Pantalla | Pasos | Qué explica la guía |
|---|---|---|
| Inicio | 4 | Guardar lo ocurrido, encontrar reportes, consultar el mapa y abrir ayuda/seguimiento. |
| Nuevo reporte | 4 | Completar datos, confirmar un punto dentro de 5 km del teléfono, incorporar una foto y revisar el borrador. |
| Revisar reporte | 3 | Comprobar datos/lugar/foto, renovar la ubicación antes de guardar y conservar el folio. |
| Reportes guardados | 3 | Consultar el espacio actual, aplicar filtros y abrir un detalle. |
| Detalle del reporte | 3 | Revisar identificación e historial y preparar voluntariamente lo que se compartirá. |
| Reportes en el mapa | 3 | Explorar calles, consultar la zona con filtros y abrir un reporte de la muestra local. |

La guía explica funciones de la pantalla, sin abrir otros recorridos o realizar las acciones descritas. El selector de ubicación del borrador reutiliza la guía de Nuevo reporte; la consulta de reportes en el mapa tiene su tema propio. No se ofrece la guía del formulario sobre un recibo guardado, durante restauración o ante un error de recuperación sin solicitud disponible. Los datos del reporte no se copian a los pasos y no se crean ejemplos, folios ficticios o registros para enseñar su uso. La primera entrega cubre estas seis pantallas; no implica guías completas para todas las opciones administrativas, avisos o ayuda.

## Texto visible y decisiones

Las orientaciones repetidas de Inicio pasan a la guía. Se conservan los títulos/acciones y los mensajes necesarios para decidir: avisos de seguridad, carácter local del proyecto, errores, permisos, revisión de datos sensibles y confirmaciones. Reducir instrucciones no convierte un avance de estado o un aviso en atención de una institución.

Abrir, recorrer, cerrar, volver o girar no guarda reportes, cambia estados, reconoce recibos, solicita permisos, obtiene GPS, abre cámara/galería, inicia mensajes ni publica avisos. Esas funciones mantienen sus propios gestos y controles de sesión. No se añade un personaje animado, servicio de IA, API, cuenta externa o seguimiento de lecturas. El login y loading del compañero se conservan.

## Criterios de aceptación

- El icono es accesible por su nombre y abre la guía de la pantalla correcta; entrar a una pantalla no muestra el recorrido automáticamente.
- Se ve un paso completo por vez, con posición y controles de navegación coherentes en el primer y último paso.
- Cerrar/Listo devuelve el control a la misma pantalla, sin perder campos o selecciones; reabrir empieza de nuevo.
- Giro con la guía abierta conserva su paso; texto grande/pantalla reducida permiten leer y alcanzar las acciones.
- Las instrucciones son generales y la guía no provoca efectos de reportes, permisos, ubicación o avisos.
- La disminución de texto conserva los avisos y decisiones necesarios; login/loading, datos y esquemas quedan intactos.

## Validación y límites

**26 casos Android distintos aprobados, ocho nuevos**, sin fallos, errores u omitidos. Ejecutados en emulador propio API 37: **ocho de `GuidedTourUiTest`** y **18 regresiones** —Inicio ocho, revisión del borrador cuatro, búsqueda de reportes cuatro y filtros de mapa dos—. Una repetición del caso de letra grande para obtener una captura estática no añade un caso distinto: el total permanece en 26, no 27.

Compilación Kotlin debug/release y ensamblados internos de depuración/pruebas correctos. Lint debug/release terminó con **cero errores y 22 avisos existentes por variante**, sin incremento. Este cambio se limita a interfaz; no se ejecutaron de nuevo las suites JVM/dominio/datos ni se incorporan sus resultados históricos al total de este corte. La APK interna de QA tiene SHA-256 `1056c2bfa6a5f4f0870deea5a50c1d40812c0380832614968d85700cfebe4bad`; no sustituye el archivo oficial.

**Revisión visual final comprobada** en la captura estable `tour43-large-text-static.png`, conservada con el material interno de QA: tarjeta opaca sin superposición, texto legible en horizontal al **130%** y acciones alcanzables mediante desplazamiento. La instrumentación comprobó Siguiente, Anterior y Cerrar en ese contexto. La captura inicial coincidió con la transición de la ventana; se ajustó únicamente el punto de captura del test después de navegar y volver, sin cambiar el producto después de los 26 casos. La repetición pasó y la APK de QA conserva el mismo hash.

La aceptación física, API 26/fabricantes, TalkBack y muerte completa del proceso conservan sus comprobaciones separadas. Las pruebas de giro o recreación no acreditan por sí solas muerte del proceso. Las comprobaciones históricas de fases 35–42 pertenecen a la oficial 0.6.2 y no se suman como nuevas pruebas de este recorrido. [Validación](VALIDACION.md) registra la procedencia y los límites del corte.

El porcentaje original se mantiene en **28/28 = 100% de construcción funcional local**; E11 no amplía el denominador ni acredita aceptación física completa, producción o integración institucional. La guía es una ayuda contextual y no sustituye las confirmaciones de las acciones reales.

## Publicación

Este trabajo permanece **local Unreleased**, por petición expresa del usuario. No actualiza GitHub/main, publica APK, crea tag/Release o cambia los archivos de la oficial **0.6.2 / código Android 30**, publicada con fases 35–42. La siguiente numeración se decidirá cuando se solicite otra entrega.

[Plan](PLAN_DEL_PROYECTO.md) · [Avance](AVANCE.md) · [Fases](FASES.md) · [Mejoras útiles](MEJORAS_UTILES.md) · [Validación](VALIDACION.md) · [Entrega oficial 0.6.2](ENTREGA_0_6_2.md)
