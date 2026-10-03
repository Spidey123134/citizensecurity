# Fases de CITIZENSECURITY

Estado documentado al 2 de octubre de 2026. La **fase 3: guardar un reporte local** está implementada y probada, aprovechando el modelo y el repositorio SQLite existentes. La auditoría solicitada de la fase 2 reprodujo y corrigió problemas de conservación de texto Unicode y de la representación del cero en coordenadas. El trabajo nuevo se mantiene en **`0.3.0 — Unreleased`**, sin etiqueta ni publicación de una release; la aceptación funcional sigue pendiente.

**Implementado**, **probado** y **revisado por el usuario** son estados distintos. La base de lógica y datos ya tiene código y pruebas; eso no significa que el usuario haya aceptado cada paso. Después de revisar la fase actual se elige el siguiente incremento.

## Estado y criterios de revisión

| Fase | Responsable | Implementación | Verificación disponible | Revisión del usuario | Criterio comprobable |
| --- | --- | --- | --- | --- | --- |
| 0. Preparar la base del proyecto | Ambos, para herramientas y contratos compartidos | Módulos `app`, `core/domain` y `core/data`, wrapper y contrato del repositorio preparados | Compilación del APK y lint ejecutados; resultados en [Validación](VALIDACION.md) | Sin aceptación registrada en estos documentos | El proyecto compila y el contrato permite conectar la interfaz sin depender de SQLite. |
| 1. Recibir los datos del reporte | Responsable de lógica y datos | `NewReport` y ejemplo de consola entregados; reutilización de campos del respaldo documentada | Ejemplo ejecutado con valores predeterminados y personalizados; comprobado que no se incluye en el código productivo | Entregado; aceptación funcional no registrada | La consola muestra tipo, descripción, prioridad, fecha del incidente y ubicación escrita recibidos. Véase [Recibir reporte](RECIBIR_REPORTE.md). |
| 2. Validar el reporte y sus coordenadas | Responsable de lógica y datos | Base publicada en `v0.2.0`; bugfix Unicode implementado en `Unreleased`: rechazo por campo de texto que SQLite no conserva | Fallos reproducidos antes de corregir; 32 unitarias y 7 instrumentadas aprobadas después, con los ejemplos y lint | Auditoría autorizada y corrección técnica comprobada; aceptación funcional pendiente | Un borrador válido pasa; texto Unicode no representable devuelve error de descripción o referencia y no se inserta. Véase [Validar reporte](VALIDAR_REPORTE.md). |
| 3. Guardar un reporte local | Responsable de lógica y datos | Fase actual implementada en `0.3.0 — Unreleased`: tarea `guardarReporte`, evidencia JSON y normalización del cero de coordenadas | Probado: folio, coordenadas, reapertura y rechazo sin inserción; 7 instrumentadas aprobadas en API 37 y APK `0.3.0` construido | Trabajo autorizado; resultado listo para revisión y aceptación funcional pendiente | Un borrador válido produce folio y estado `REPORTED`; conserva sus coordenadas al reabrir. Uno inválido no añade registros. Véase [Fase 3](FASE_3.md). |
| 4. Consultar y comprobar permanencia | Responsable de lógica y datos | `findById` y `list` ya implementados | Cubierto por la suite instrumentada actual de 7 pruebas: reapertura, orden de consulta y conservación de datos | Pendiente; siguiente revisión por elegir | El reporte se recupera por su folio después de cerrar y reabrir la base; un folio inexistente devuelve `null`. |
| 5. Conectar la interfaz del reporte | Compañero (`vazdavr-sudo`); conexión al contrato coordinada por ambos | Pendiente; `app` tiene un contenedor vacío | Pendiente: no existe todavía un flujo visual de guardado | Pendiente | La pantalla construye `NewReport`, presenta errores por campo y confirma el guardado después de recibir el reporte con su folio. |
| 6. Elegir ubicación con Google Maps | Compañero: mapa, pin y confirmación. Lógica y datos: recibir, validar y conservar coordenadas. Configuración compartida: ambos | Siguiente línea acordada; modelo y SQLite ya admiten coordenadas. Mapa y conexión del SDK pendientes | Base técnica cubierta por las pruebas existentes; flujo con mapa pendiente | Plan autorizado; implementación y aceptación funcional pendientes | El usuario confirma un par de coordenadas en el mapa, el reporte lo valida y la consulta recupera el mismo par después de reabrir la base. |

La suite de `v0.2.0` tenía 5 pruebas instrumentadas. En `0.3.0 — Unreleased` se añadieron 2 regresiones para los fallos reproducidos: texto Unicode y cero negativo; las 7 aprobaron en API 37. Es un conjunto compartido para guardado y consulta, no 7 pruebas nuevas por fase. La verificación en teléfono físico y API 26 sigue pendiente. La evidencia actual está en [Fase 3](FASE_3.md); el contexto de la verificación anterior está en [Validación](VALIDACION.md).

## Reparto y siguiente paso

Las interfaces, el menú, la navegación y el login corresponden al compañero (`vazdavr-sudo`). El login sigue pendiente y su alcance se acordará antes de conectarlo a servicios o cuentas. Los contratos y la composición compartida de dependencias se coordinan entre ambos, según [Trabajo del equipo](TRABAJO_EQUIPO.md).

La fase 3 permite revisar un reporte guardado y recuperado por su folio, junto con un rechazo que conserva el número de registros. Las correcciones de la fase 2 tienen regresiones que fallaron antes y aprobaron después. La integración visual de Google Maps sigue siendo una línea acordada posterior, con el reparto del equipo ya definido.

El resultado de `0.3.0` está comprobado localmente y disponible para revisión funcional. Sigue en `Unreleased`, sin una nueva etiqueta o release. Las comprobaciones de esta fase están documentadas con sus resultados reales, separadas de la evidencia anterior.

La [release v0.2.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.0) se publicó el 2 de octubre de 2026, hora de México, sobre el commit `aa29990` integrado en `main`. Las comprobaciones están en [Validación](VALIDACION.md) y el proceso de publicación en [Releases](RELEASES.md). Las fases 1 y 2 siguen disponibles para revisión funcional; una versión publicada no sustituye la aceptación funcional del usuario.

## Alcance pendiente de acuerdo

La conexión de Google Maps requiere definir el SDK, proyecto de Google, clave y facturación antes de implementarla. Captura GPS, fotografías, sincronización, servidor, administración y notificaciones quedan pendientes de un nuevo acuerdo de alcance. El guardado actual es local. El proyecto anterior y su respaldo se conservan como referencia, y solo se reutilizan las partes necesarias para el incremento elegido.
