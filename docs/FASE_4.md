# Fase 4: consultar un reporte local

El usuario autorizó cerrar la entrega **`0.4.0`**, integrarla a `main` y publicarla con la etiqueta [v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0). Este incremento incorpora consulta por folio y estado, reutilizando `ReportRepository.findById` y `list`. La aplicación usa `versionName = "0.4.0"` y número interno Android `versionCode = 7`. La comprobación técnica aprobó; la aceptación funcional sigue pendiente y `0.3.0` conserva su estado Unreleased.

La consulta corresponde a la organización y lectura de reportes del [plan del proyecto](PLAN_DEL_PROYECTO.md), especialmente las secciones 2 y 4.4 del PDF. La base es local y no tiene cuentas ni particiones por usuario: esta entrega no completa la consulta de reportes propios ni el seguimiento administrativo del documento.

## Demostración de permanencia

Con el entorno Android configurado y un dispositivo o emulador disponible, ejecutar desde la raíz del repositorio:

```powershell
.\gradlew.bat :core:data:consultarReporte
```

La tarea reutiliza instrumentación de SQLite. El escenario comprueba primero una base vacía, prepara dos reportes de prueba, cierra y reabre la base y muestra la consulta por folio, el estado `REPORTED`, el listado y un folio inexistente. El orden del listado considera la fecha de guardado y el desempate por folio cuando las fechas son iguales; en esta demostración, ambas fechas de guardado son iguales.

Los reportes que se crean para preparar el escenario son datos de prueba en una base temporal. No se consultan datos productivos ni se incorporan reportes a la base de la aplicación. La consulta por sí misma no inserta ni modifica registros. La ejecución aprobó en API 37: ambos reportes se recuperaron exactamente después de reabrir, el folio inexistente devolvió null y quedaron 2 registros antes y después de consultar.

La evidencia se exporta a `core/data/build/reports/fase4/<dispositivo>/consulta.json`, fuera de Git. Contiene los reportes encontrados, los folios ordenados, el resultado inexistente y la cantidad de registros antes y después de consultar. La fecha de guardado usa un reloj fijo declarado con `clock = "fixed"`; `generatedAt` corresponde al momento real de la ejecución. La base temporal se elimina al terminar la prueba.

## Contrato para la futura pantalla

`com.example.citizensecurity.report.ReportQueryViewModel` recibe el repositorio compartido y ofrece:

```kotlin
fun findByFolio(folio: String)
val state: StateFlow<ReportQueryState>
```

El estado es de solo lectura para la pantalla:

| Estado | Contenido y uso para el compañero |
| --- | --- |
| `Idle` | Espera la primera consulta. |
| `Loading(folio: String)` | Consulta en curso; deshabilitar otra solicitud. |
| `Found(report: Report)` | Mostrar el folio, los datos recuperados y su estado local. |
| `NotFound(folio: String)` | Explicar que el folio no existe en esta base local. |
| `InvalidFolio(message: String)` | Mostrar el error de formato antes de consultar SQLite. |
| `Error(message: String)` | Presentar un fallo de lectura con un mensaje legible. |

La entrada se recorta en los extremos y se normaliza a minúsculas. Se exige la forma UUID de 36 caracteres: grupos hexadecimales de 8, 4, 4, 4 y 12 caracteres, separados por guiones. Una cadena con formato incorrecto no inicia una lectura. Mientras el estado es `Loading`, otra solicitud se ignora.

`ReportQueryViewModel.factory(repository)` y `CitizenSecurityApplication.reportQueryViewModelFactory` usan el repositorio compartido; `MainActivity.reportQueryViewModel` permite que el compañero conecte su futuro control de consulta:

```kotlin
reportQueryViewModel.findByFolio(folioEscrito)
```

La futura pantalla observa `state` con su ciclo de vida, siguiendo [Acoplar interfaz](ACOPLAR_INTERFAZ.md). El modelo mantiene la operación en su ámbito, propaga la cancelación y devuelve el estado a `Idle` cuando se cancela una consulta en curso. Una vez liberado el modelo, no admite consultas nuevas. La pantalla no abre ni cierra el repositorio singleton. Los layouts, listeners, navegación y login siguen con el compañero; esta entrega no añade una pantalla de consulta.

## Criterios comprobables y estado

- Recuperar por folio después de cerrar y reabrir la base, conservando datos, coordenadas y estado inicial.
- Obtener `NotFound` para un UUID inexistente y `InvalidFolio` para una entrada con formato incorrecto.
- Listar los registros en orden de fecha, con desempate estable por folio.
- Evitar lecturas simultáneas desde el mismo modelo y conservar la cancelación y el ciclo de vida.
- Consultar sin insertar, cambiar estados ni cerrar el repositorio compartido.

La comprobación de `0.4.0` aprobó: 24 JVM de app (8 de consulta, 8 de guardado y 8 de herramientas oficiales), 32 unitarias de dominio reejecutadas y 10 instrumentadas SQLite en API 37. Las tareas de consulta y guardado exportaron sus JSON; lint debug/release y ambas compilaciones aprobaron, manteniendo 13 advertencias visuales previas. [Validación](VALIDACION.md) registra comandos, regresiones y evidencia.

Se reprodujeron y corrigieron dos errores: publicar un error de consulta o un resultado de guardado después de cancelarse el trabajo. Los modelos comprueban su actividad antes de publicar resultados o errores y vuelven a Idle al cancelar, incluso antes de iniciar. La cancelación del modelo no equivale a revertir una escritura que ya haya terminado.

La entrega incorpora la base de `main` y las herramientas introducidas en 0.2.1. Ese botón conserva solo las funciones de fases 1 y 2; no expone la consulta de 0.4. La aplicación usa código Android 7, superior al 6 de 0.2.1. La consulta de esta entrega se comprueba con la tarea Gradle y queda disponible mediante el contrato para la futura pantalla del compañero; instalar su APK no añade esa pantalla.

No se afirma un flujo visual probado ni rotación real de la pantalla. Tampoco hay cambios de estado, administración, autenticación, sincronización, servidor, GPS, SDK de mapas, fotografías o notificaciones. Las coordenadas guardadas se leen mediante el contrato existente; no se añade una migración.
