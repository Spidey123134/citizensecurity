# Trabajo del equipo

## Reparto inicial

| Responsable | Área | Archivos |
| --- | --- | --- |
| Compañero (`vazdavr-sudo`) | Interfaces, menú, diseño y navegación | `app/src/main`, excepto la composición compartida de dependencias |
| Nuestra parte | Primera función: validar y guardar un reporte local | `core/domain` y `core/data`, con sus pruebas |
| Ambos | Contratos, manifiesto, herramientas y cambios que conecten las dos áreas | Se coordinan antes de modificar |

La aplicación inicial es un contenedor vacío para desarrollar las interfaces. El trabajo de datos no añade pantallas, textos internos de desarrollo, login ni navegación por iniciativa propia.

## Ciclo de cada función

1. Elegir una función pequeña y su resultado observable.
2. Identificar los archivos que toca y el contrato con la otra área.
3. Implementarla en una rama de tarea.
4. Ejecutar las pruebas necesarias y mostrar el resultado al usuario.
5. Ajustarla a su gusto; pasar a otra función después de revisar la actual.

Usen ramas separadas, por ejemplo `feature/interfaz-inicial` y `feature/guardar-reporte`, y revisen los cambios antes de mezclarlos con `main`. El repositorio público permite consultar el código; escribir cambios requiere ser colaborador y aceptar la invitación.

## Trabajo anterior

La versión anterior y su respaldo se conservan en la PC como referencia. No se mezclan automáticamente todas sus funciones en esta base nueva. Las reglas de validación y lo aprendido sobre persistencia sirven como antecedente para este primer incremento.

## Criterio de la primera entrega

Un reporte válido produce un folio y queda guardado. Un reporte inválido devuelve errores por campo y no se inserta. Cerrar y reabrir la base conserva el reporte. El compañero puede conectar su interfaz sin depender de detalles de SQLite.
