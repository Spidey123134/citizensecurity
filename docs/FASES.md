# Fases de CITIZENSECURITY

Estado documentado al 2 de octubre de 2026. El trabajo se revisa una función pequeña por vez, aprovechando los contratos y la persistencia que ya existen. La fase actual es recibir los cinco datos de un reporte.

**Implementado**, **probado** y **revisado por el usuario** son estados distintos. La base de lógica y datos ya tiene código y pruebas; eso no significa que el usuario haya aceptado cada paso. Después de revisar la fase actual se elige el siguiente incremento.

## Estado y criterios de revisión

| Fase | Responsable | Implementación | Verificación disponible | Revisión del usuario | Criterio comprobable |
| --- | --- | --- | --- | --- | --- |
| 0. Preparar la base del proyecto | Ambos, para herramientas y contratos compartidos | Módulos `app`, `core/domain` y `core/data`, wrapper y contrato del repositorio preparados | Compilación del APK y lint ejecutados; resultados en [Validación](VALIDACION.md) | Sin aceptación registrada en estos documentos | El proyecto compila y el contrato permite conectar la interfaz sin depender de SQLite. |
| 1. Recibir los datos del reporte | Responsable de lógica y datos | `NewReport` y ejemplo de consola disponibles; reutilización de campos del respaldo documentada | Ejemplo ejecutado con valores predeterminados y personalizados; comprobado que no se incluye en el código productivo | Pendiente: paso entregado para revisión | La consola muestra tipo, descripción, prioridad, fecha del incidente y ubicación escrita recibidos. Véase [Recibir reporte](RECIBIR_REPORTE.md). |
| 2. Validar el contenido | Responsable de lógica y datos | Reglas y errores por campo ya implementados en `core/domain` | 28 pruebas unitarias aprobadas | Pendiente; siguiente revisión por elegir | Un borrador válido pasa; uno inválido devuelve errores en los campos correspondientes. Los límites están en [Contrato](GUARDAR_REPORTE.md). |
| 3. Guardar un reporte local | Responsable de lógica y datos | Creación transaccional en SQLite, folio y fecha de guardado ya implementados en `core/data` | Cubierto por las 5 pruebas instrumentadas de la base | Pendiente; siguiente revisión por elegir | Un borrador válido produce un folio y queda guardado con estado inicial `REPORTED`; uno inválido no inserta datos. |
| 4. Consultar y comprobar permanencia | Responsable de lógica y datos | `findById` y `list` ya implementados | Cubierto por las mismas 5 pruebas instrumentadas: reapertura, orden de consulta y conservación de datos | Pendiente; siguiente revisión por elegir | El reporte se recupera por su folio después de cerrar y reabrir la base; un folio inexistente devuelve `null`. |
| 5. Conectar la interfaz del reporte | Compañero (`vazdavr-sudo`); conexión al contrato coordinada por ambos | Pendiente; `app` tiene un contenedor vacío | Pendiente: no existe todavía un flujo visual de guardado | Pendiente | La pantalla construye `NewReport`, presenta errores por campo y confirma el guardado después de recibir el reporte con su folio. |

Las 5 pruebas instrumentadas son un conjunto compartido para el guardado y la consulta, no 5 pruebas nuevas por cada fase. Sus resultados corresponden a un emulador API 37; la verificación en teléfono físico y API 26 sigue pendiente. El detalle y las advertencias de lint están en [Validación](VALIDACION.md).

## Reparto y siguiente paso

Las interfaces, el menú, la navegación y el login corresponden al compañero (`vazdavr-sudo`). El login sigue pendiente y su alcance se acordará antes de conectarlo a servicios o cuentas. Los contratos y la composición compartida de dependencias se coordinan entre ambos, según [Trabajo del equipo](TRABAJO_EQUIPO.md).

Primero se revisa el resultado de la fase 1. Después se elige una sola revisión entre las piezas de validación, guardado y consulta que ya existen. Cada entrega debe mostrar su resultado observable y las pruebas pertinentes; si necesita ajustes, se atienden antes de pasar a otra función.

## Alcance pendiente de acuerdo

GPS, fotografías, sincronización, servidor, administración y notificaciones quedan fuera de estas fases hasta acordar un nuevo alcance. El guardado actual es local. El proyecto anterior y su respaldo se conservan como referencia, y solo se reutilizan las partes necesarias para el incremento elegido.
