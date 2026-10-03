# Fases de CITIZENSECURITY

Estado documentado al 2 de octubre de 2026. El trabajo se revisa una función pequeña por vez, aprovechando los contratos y la persistencia que ya existen. La fase actual es **2: validar el reporte**, incluyendo el par de coordenadas que utilizará la futura integración de Google Maps. El usuario autorizó comenzar esta fase y preparar la versión `0.2.0`.

**Implementado**, **probado** y **revisado por el usuario** son estados distintos. La base de lógica y datos ya tiene código y pruebas; eso no significa que el usuario haya aceptado cada paso. Después de revisar la fase actual se elige el siguiente incremento.

## Estado y criterios de revisión

| Fase | Responsable | Implementación | Verificación disponible | Revisión del usuario | Criterio comprobable |
| --- | --- | --- | --- | --- | --- |
| 0. Preparar la base del proyecto | Ambos, para herramientas y contratos compartidos | Módulos `app`, `core/domain` y `core/data`, wrapper y contrato del repositorio preparados | Compilación del APK y lint ejecutados; resultados en [Validación](VALIDACION.md) | Sin aceptación registrada en estos documentos | El proyecto compila y el contrato permite conectar la interfaz sin depender de SQLite. |
| 1. Recibir los datos del reporte | Responsable de lógica y datos | `NewReport` y ejemplo de consola entregados; reutilización de campos del respaldo documentada | Ejemplo ejecutado con valores predeterminados y personalizados; comprobado que no se incluye en el código productivo | Entregado; aceptación funcional no registrada | La consola muestra tipo, descripción, prioridad, fecha del incidente y ubicación escrita recibidos. Véase [Recibir reporte](RECIBIR_REPORTE.md). |
| 2. Validar el reporte y sus coordenadas | Responsable de lógica y datos | Reglas, errores por campo y nuevos ejemplos de consola implementados | Probado: ejemplos verificados; 28 unitarias y 5 instrumentadas aprobadas; lint sin errores y APK `0.2.0` generado | Trabajo autorizado por el usuario; aceptación funcional de resultados pendiente | Un borrador válido pasa; un par incompleto o fuera de rango devuelve error de coordenadas, aunque tenga referencia escrita. Véase [Validar reporte](VALIDAR_REPORTE.md). |
| 3. Guardar un reporte local | Responsable de lógica y datos | Creación transaccional en SQLite, folio y fecha de guardado ya implementados en `core/data` | Cubierto por las 5 pruebas instrumentadas de la base | Pendiente; siguiente revisión por elegir | Un borrador válido produce un folio y queda guardado con estado inicial `REPORTED`; uno inválido no inserta datos. |
| 4. Consultar y comprobar permanencia | Responsable de lógica y datos | `findById` y `list` ya implementados | Cubierto por las mismas 5 pruebas instrumentadas: reapertura, orden de consulta y conservación de datos | Pendiente; siguiente revisión por elegir | El reporte se recupera por su folio después de cerrar y reabrir la base; un folio inexistente devuelve `null`. |
| 5. Conectar la interfaz del reporte | Compañero (`vazdavr-sudo`); conexión al contrato coordinada por ambos | Pendiente; `app` tiene un contenedor vacío | Pendiente: no existe todavía un flujo visual de guardado | Pendiente | La pantalla construye `NewReport`, presenta errores por campo y confirma el guardado después de recibir el reporte con su folio. |
| 6. Elegir ubicación con Google Maps | Compañero: mapa, pin y confirmación. Lógica y datos: recibir, validar y conservar coordenadas. Configuración compartida: ambos | Siguiente línea acordada; modelo y SQLite ya admiten coordenadas. Mapa y conexión del SDK pendientes | Base técnica cubierta por las pruebas existentes; flujo con mapa pendiente | Plan autorizado; implementación y aceptación funcional pendientes | El usuario confirma un par de coordenadas en el mapa, el reporte lo valida y la consulta recupera el mismo par después de reabrir la base. |

Las 5 pruebas instrumentadas son un conjunto compartido para el guardado y la consulta, no 5 pruebas nuevas por cada fase. Se ejecutaron de nuevo para la versión `0.2.0`. La verificación en teléfono físico y API 26 sigue pendiente. La evidencia de este incremento está en [Validar reporte](VALIDAR_REPORTE.md); el contexto de la verificación inicial en emulador API 37 está en [Validación](VALIDACION.md).

## Reparto y siguiente paso

Las interfaces, el menú, la navegación y el login corresponden al compañero (`vazdavr-sudo`). El login sigue pendiente y su alcance se acordará antes de conectarlo a servicios o cuentas. Los contratos y la composición compartida de dependencias se coordinan entre ambos, según [Trabajo del equipo](TRABAJO_EQUIPO.md).

La fase 2 está implementada y probada, lista para revisar sus resultados. Después de presentarlos, se elige el siguiente incremento pequeño; la integración visual de Google Maps quedó acordada como próxima línea de trabajo. Cada entrega debe mostrar su resultado observable y las pruebas pertinentes; si necesita ajustes, se atienden antes de pasar a otra función.

La [release v0.2.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.2.0) se publicó el 2 de octubre de 2026, hora de México, sobre el commit `aa29990` integrado en `main`. Las comprobaciones están en [Validación](VALIDACION.md) y el proceso de publicación en [Releases](RELEASES.md). Las fases 1 y 2 siguen disponibles para revisión funcional; una versión publicada no sustituye la aceptación funcional del usuario.

## Alcance pendiente de acuerdo

La conexión de Google Maps requiere definir el SDK, proyecto de Google, clave y facturación antes de implementarla. Captura GPS, fotografías, sincronización, servidor, administración y notificaciones quedan pendientes de un nuevo acuerdo de alcance. El guardado actual es local. El proyecto anterior y su respaldo se conservan como referencia, y solo se reutilizan las partes necesarias para el incremento elegido.
