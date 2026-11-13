# Trabajo del equipo

## Reparto para la base beta

La revisión `0.1.0-rev.2-beta` reúne el proyecto en `app` y organiza sus responsabilidades según el pizarrón de clase.

| Responsable | Área | Archivos |
| --- | --- | --- |
| Compañero (`vazdavr-sudo`) | Interfaces, menú, diseño y navegación | `MainActivity.kt`, `navigation/`, `screen/` y recursos visuales de `app/src/main` |
| Nuestra parte | Validar, guardar y consultar reportes locales | `app/src/main/kotlin/com/example/citizensecurity/data/`, con pruebas JVM e instrumentadas |
| Ambos | Contratos, composición de dependencias, manifiesto y herramientas | Se coordinan antes de modificar |

`MainActivity` inicia `Home` mediante `Navigation`. `Home` y `ReportDetails` son contenedores vacíos, preparados para desarrollar sus interfaces. Esta revisión no añade formularios, listas visuales, datos de demostración ni otras funciones.

La función de datos se conserva en `AdminReportes`, que implementa `ReportRepository` y usa `ReportValidator` y `ReportDatabase`. Su contrato permite crear, buscar por folio y listar. No incluye edición o borrado.

## Ciclo de cada función

1. Elegir una función pequeña y su resultado observable.
2. Identificar los archivos que toca y el contrato con la otra área.
3. Implementarla en una rama de tarea.
4. Ejecutar las pruebas necesarias y mostrar el resultado al usuario.
5. Ajustarla a su gusto; pasar a otra función después de revisar la actual.

Usen ramas separadas, por ejemplo `feature/interfaz-inicial` y `feature/guardar-reporte`, y revisen los cambios antes de mezclarlos. La rama `beta/0.1.0-rev2` conserva esta revisión histórica; la versión oficial 0.7 sigue por separado. El repositorio público permite consultar el código; escribir cambios requiere ser colaborador y aceptar la invitación.

## Referencias y alcance

El [documento inicial](ESTRUCTURA_INICIAL.md) explica la organización de 0.1.0 antes de esta revisión, cuando los datos estaban en `core/domain` y `core/data`. La [estructura de la beta](ESTRUCTURA_BETA.md) muestra los archivos actuales, su diagrama de clases y un guion de presentación.

La versión anterior y su respaldo se conservan como referencia. No se incorporan automáticamente sus pantallas o funciones a esta base. Mapas, cuentas, fotografías, sincronización y notificaciones corresponden a otros incrementos.

## Criterio de esta entrega

Un reporte válido produce un folio y queda guardado; uno inválido devuelve errores por campo y no se inserta. Cerrar y reabrir la base conserva el reporte. La futura interfaz puede conectarse al contrato sin depender de los detalles de SQLite.

Las verificaciones de esta revisión deben ejecutar las tareas del módulo `app`, indicadas en el [README](../README.md), y registrar los resultados reales en [Validación](VALIDACION.md).
