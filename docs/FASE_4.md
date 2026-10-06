# Fase 4: consultar un reporte local

El incremento activo **0.3.2 — Unreleased** hereda de la base 0.3.1 los accesos ocultos a consulta y guardado real. Esta fase conserva su pulido Unreleased; el siguiente contrato de ubicación se documenta en [Fase 6](FASE_6.md).

El pulido de **fase 4 / `0.4.0` permanece Unreleased** y se hace localmente. La consulta por folio y estado reutiliza `ReportRepository.findById` y `list`; su lógica y pruebas se conservan, pero todo acceso visual a la consulta permanece oculto en la preparación **0.3.1 / código Android 8**.

La [Release v0.4.0](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0), código **7**, sí fue integrada y publicada anteriormente. Eso ocurrió sin instrucción humana: el usuario pidió terminarla y contar los cambios, no subirla o integrarla. El registro anterior que afirmaba autorización era incorrecto. Por indicación posterior se conserva esa publicación, sin retirar ni revertir; no se ha publicado la preparación local actual. `0.3.0` también conserva su estado Unreleased, aunque parte de su lógica ya estaba en los APK publicados.

La consulta corresponde a la organización y lectura de reportes del [plan del proyecto](PLAN_DEL_PROYECTO.md), especialmente las secciones 2 y 4.4 del PDF. La base es local y no tiene cuentas ni particiones por usuario: esta lógica no completa la consulta de reportes propios ni el seguimiento administrativo del documento.

## Demostración de permanencia

Esta es una comprobación técnica del código conservado, no una función habilitada en el botón de desarrollador. No debe añadirse un control que la active sin una instrucción precisa.

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

`ReportQueryViewModel.factory(repository)` y `CitizenSecurityApplication.reportQueryViewModelFactory` usan el repositorio compartido; el contrato conservado de `MainActivity.reportQueryViewModel` permitirá conectar un control futuro cuando se autorice su activación. La llamada técnica es:

```kotlin
reportQueryViewModel.findByFolio(folioEscrito)
```

La futura pantalla observará `state` con su ciclo de vida, siguiendo [Acoplar interfaz](ACOPLAR_INTERFAZ.md). El modelo mantiene la operación en su ámbito, propaga la cancelación y devuelve el estado a `Idle` cuando se cancela una consulta en curso. Una vez liberado el modelo, no admite consultas nuevas. La pantalla no abre ni cierra el repositorio singleton. Los layouts, listeners, navegación y login siguen con el compañero. Puede preparar su diseño, pero debe dejar los eventos de consulta sin conectar y el acceso oculto hasta una instrucción precisa; disponer del contrato no autoriza habilitarlo.

## Criterios comprobables y estado

- Recuperar por folio después de cerrar y reabrir la base, conservando datos, coordenadas y estado inicial.
- Obtener `NotFound` para un UUID inexistente y `InvalidFolio` para una entrada con formato incorrecto.
- Listar los registros en orden de fecha, con desempate estable por folio.
- Evitar lecturas simultáneas desde el mismo modelo y conservar la cancelación y el ciclo de vida.
- Consultar sin insertar, cambiar estados ni cerrar el repositorio compartido.

La comprobación histórica de `0.4.0` aprobó: 24 JVM de app (8 de consulta, 8 de guardado y 8 de herramientas oficiales), 32 unitarias de dominio reejecutadas y 10 instrumentadas SQLite en API 37. Las tareas de consulta y guardado exportaron sus JSON; lint debug/release y ambas compilaciones aprobaron, manteniendo 13 advertencias visuales previas. Estos resultados no afirman comprobaciones nuevas: [Validación](VALIDACION.md) registra por separado los comandos, regresiones y evidencia del pulido local actual.

Se reprodujeron y corrigieron dos errores: publicar un error de consulta o un resultado de guardado después de cancelarse el trabajo. Los modelos comprueban su actividad antes de publicar resultados o errores y vuelven a Idle al cancelar, incluso antes de iniciar. La cancelación del modelo no equivale a revertir una escritura que ya haya terminado.

En el pulido local actual se reprodujo un fallo adicional del guardado: cancelar durante la obtención de la fecha, después de adquirir el monitor, todavía insertaba 1 registro cuando se esperaban 0. Se comprueba la cancelación antes de iniciar la transacción y antes de confirmar su éxito; la nueva regresión aprobó en API 37. Esto no revierte reportes cuya transacción ya esté confirmada.

La comprobación actual ejecutó y aprobó **30 JVM de app y 15 instrumentadas API 37: 45 pruebas**. Incluye el nuevo ejemplo ficticio y la regresión anterior; las 32 de dominio mantuvieron su resultado anterior mediante `UP-TO-DATE`, sin ejecutarse otra vez. Los JSON de consulta y guardado fueron correctos. El estado actual de compilación, lint y comprobación visual del ejemplo se registra en [Validación](VALIDACION.md); no se atribuye una pantalla de consulta a estas pruebas.

La preparación 0.3.1 conserva las herramientas introducidas en 0.2.1 y añade solo el ejemplo ficticio de guardado de [Fase 3](FASE_3.md). Ese ejemplo usa datos fijos y SQLite en memoria, sin leer el formulario ni el repositorio productivo. El guardado real y la consulta de 0.4 quedan ocultos tanto en debug como en release. La aplicación local usa código Android 8, superior al 7 de la publicación histórica 0.4.0. El código fuente ya publicado sigue siendo público; ocultar estos accesos no lo retira.

No se afirma un flujo visual probado ni rotación real de la pantalla. Tampoco hay cambios de estado, administración, autenticación, sincronización, servidor, GPS, SDK de mapas, fotografías o notificaciones. Las coordenadas guardadas se leen mediante el contrato existente; no se añade una migración.
