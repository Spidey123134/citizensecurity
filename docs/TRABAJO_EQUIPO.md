# Trabajo del equipo

## Reparto inicial

| Responsable | Área | Archivos |
| --- | --- | --- |
| Compañero (`vazdavr-sudo`) | Interfaces, menú, diseño, navegación y login; futura vista de Google Maps con pin y confirmación | `app/src/main`, excepto la composición compartida de dependencias; la conexión del mapa y el login se coordinan al definir ese alcance |
| Nuestra parte | Primera función: validar y guardar un reporte local | `core/domain` y `core/data`, con sus pruebas |
| Ambos | Contratos, manifiesto, herramientas y cambios que conecten las dos áreas | Se coordinan antes de modificar |

La aplicación inicial es un contenedor vacío para desarrollar las interfaces. El trabajo de datos no añade pantallas, textos internos de desarrollo, login ni navegación por iniciativa propia.

La guía [Inicio del equipo](INICIO_EQUIPO.md) explica cómo clonar la base y comenzar desde una rama propia. Los estados de cada paso se mantienen en [Fases de trabajo](FASES.md) y los cambios entregados en el [Changelog](../CHANGELOG.md). Una prueba técnica aprobada no sustituye la revisión del usuario sobre el comportamiento de la función.

## Incremento actual y ubicación con mapa

El 2 de octubre de 2026 el usuario autorizó la fase 2: validar el reporte y sus coordenadas, y preparar la versión `0.2.0` en GitHub Releases. Se reutilizan el validador y el contrato existentes; la aceptación funcional de los nuevos resultados permanece pendiente. La guía [Validar reporte](VALIDAR_REPORTE.md) describe los comandos y resultados esperados.

Google Maps quedó acordado como próxima línea de trabajo. Nuestra parte recibe, valida y conserva `latitude` y `longitude` en `ReportLocation` y SQLite. El compañero desarrolla la vista del mapa, el pin y la confirmación de ubicación. Ambos coordinan la conexión con el contrato y la configuración compartida. El SDK, el proyecto de Google, la clave y la facturación siguen pendientes; no hay un mapa real en esta entrega ni captura GPS.

## Ciclo de cada función

1. Elegir una función pequeña y su resultado observable.
2. Identificar los archivos que toca y el contrato con la otra área.
3. Implementarla en una rama de tarea.
4. Ejecutar las pruebas necesarias y mostrar el resultado al usuario.
5. Ajustarla a su gusto; pasar a otra función después de revisar la actual.

Cada versión publicable actualiza la versión de la aplicación, el changelog, las fases y las notas en español de GitHub Releases, siguiendo [Releases](RELEASES.md). Se registran solo comprobaciones ejecutadas y se conserva la distinción entre autorización del trabajo y aceptación del resultado.

Usen ramas separadas, por ejemplo `feature/interfaz-inicial` y `feature/guardar-reporte`, y revisen los cambios antes de mezclarlos con `main`. El repositorio público permite consultar el código; escribir cambios requiere ser colaborador y aceptar la invitación.

## Trabajo anterior

La versión anterior y su respaldo se conservan en la PC como referencia. No se mezclan automáticamente todas sus funciones en esta base nueva. Las reglas de validación y lo aprendido sobre persistencia sirven como antecedente para este primer incremento.

## Criterio de la primera entrega

Un reporte válido produce un folio y queda guardado. Un reporte inválido devuelve errores por campo y no se inserta. Cerrar y reabrir la base conserva el reporte. El compañero puede conectar su interfaz sin depender de detalles de SQLite.
