# Contrato de la primera función

La interfaz reúne los datos de un incidente y construye un `NewReport`. El repositorio valida y guarda ese borrador en el dispositivo. Un guardado correcto devuelve un `Report` con folio UUID, fecha de creación y estado inicial `REPORTED`.

## API compartida

El contrato está en `com.example.citizensecurity.domain.ReportRepository`.

| Operación suspendida | Resultado |
| --- | --- |
| `create(draft: NewReport): Report` | Normaliza, valida y guarda en una transacción. Devuelve el reporte guardado. |
| `findById(id: String): Report?` | Busca por el folio exacto; devuelve `null` si no existe. |
| `list(): List<Report>` | Consulta todos los reportes del más reciente al más antiguo por `createdAt`; los empates se ordenan por ID descendente. |

`NewReport` recibe `type`, `priority`, `description`, `occurredAt: Instant` y `location: ReportLocation`. La ubicación contiene `reference: String = ""`, `latitude: Double? = null` y `longitude: Double? = null`.

| Campo | Valores |
| --- | --- |
| `type: IncidentType` | `EMERGENCY`, `THEFT`, `ACCIDENT`, `FIRE`, `RISK`, `OTHER` |
| `priority: Priority` | `LOW`, `MEDIUM`, `HIGH` |

El folio y `createdAt` los genera el repositorio; la interfaz conserva el folio devuelto para consultar el registro. Cada llamada válida a `create` crea un folio distinto, incluso si repite los datos. La interfaz debe evitar solicitudes duplicadas mientras un guardado está en curso.

## Validación y errores

Las reglas se comprueban otra vez dentro de `create`, aunque la interfaz haga una validación previa:

- Descripción: entre 10 y 1000 puntos Unicode después de quitar espacios de los extremos.
- Referencia escrita: entre 5 y 200 puntos Unicode cuando se proporciona. Puede quedar vacía si hay coordenadas completas y válidas.
- Coordenadas: se proporcionan juntas; deben ser finitas, con latitud entre −90 y 90 y longitud entre −180 y 180. Una coordenada inválida se rechaza incluso si hay una referencia válida.
- Fecha del incidente: igual o posterior a `1970-01-01T00:00:00Z`; se permite hasta un minuto por delante del reloj usado para guardar.
- Descripción y referencia rechazan el carácter nulo `U+0000`.

Los campos de texto se guardan sin espacios de los extremos. Las fechas conservan segundos y nanosegundos. Si el borrador es inválido, `create` lanza `InvalidReportException` y no inserta datos. `exception.errors.errors` es un `Map<ReportField, String>`: permite colocar cada mensaje junto a `DESCRIPTION`, `LOCATION_REFERENCE`, `LOCATION_COORDINATES` u `OCCURRED_AT`. Los errores de almacenamiento se propagan; la interfaz confirma el guardado solo después de recibir el `Report`.

Para validar antes de guardar se puede usar `ReportValidator().validate(draft, Instant.now())`. Su resultado expone `errors` e `isValid`; esa revisión no sustituye la validación del repositorio.

## Conectar la interfaz

`CitizenSecurityApplication.reportRepository` proporciona la instancia compartida. Desde una actividad, `application as CitizenSecurityApplication` obtiene la aplicación configurada. La siguiente función se llama desde una corrutina del ciclo de vida de la pantalla o del `ViewModel`, después de construir el borrador con los datos de la interfaz:

```kotlin
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Report

suspend fun guardarReporte(
    app: CitizenSecurityApplication,
    borrador: NewReport,
): Report {
    val repository = app.reportRepository
    return repository.create(borrador)
}
```

Las tres operaciones son `suspend`; SQLite se ejecuta internamente en `Dispatchers.IO`. No uses `runBlocking` en la interfaz ni crees un repositorio por cada recomposición. La instancia compartida pertenece a la aplicación y dura lo que su proceso; una pantalla no debe cerrarla. Las instancias separadas de `SqliteReportRepository` admiten `close()` cuando termina su uso: cerrar es idempotente, conserva el archivo y exige una instancia nueva para volver a consultarlo.

## Persistencia local

Los reportes quedan en el archivo privado `citizensecurity_local_v1.db`, esquema versión 1. Cerrar la conexión o reiniciar la aplicación conserva los registros; borrar los datos de la aplicación o desinstalarla elimina esta base local. Se utiliza un archivo nuevo, sin abrir la base del proyecto anterior. Los cambios de esquema posteriores requieren migraciones explícitas que conserven los datos.

Esta entrega permite guardar y consultar. El estado inicial no cambia mediante esta API y el guardado local no envía el reporte a autoridades.
