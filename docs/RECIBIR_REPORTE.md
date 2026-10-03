# Paso 1: recibir los datos de un reporte

La entrada de nuestra función usa el modelo `NewReport` que ya existe en `core/domain`. El compañero puede construirlo con los valores de su interfaz. Este paso deja comprobable la recepción de los cinco datos, antes de revisar juntos las reglas y el guardado.

| Dato | Campo del contrato | Ejemplo |
| --- | --- | --- |
| Tipo de incidente | `type: IncidentType` | `RISK` |
| Descripción | `description: String` | Hay una luminaria dañada frente al parque. |
| Prioridad | `priority: Priority` | `MEDIUM` |
| Fecha del incidente | `occurredAt: Instant` | `2026-10-02T23:30:00Z` |
| Ubicación escrita | `location.reference: String` | Frente al parque central |

La fecha corresponde al incidente y la elige quien reporta; la fecha de guardado se genera más adelante. `Instant` representa un momento concreto; `Z` indica UTC. Si la pantalla muestra hora local, debe convertirla a `Instant` conservando la zona horaria elegida.

## Reutilización del proyecto anterior

El respaldo ya tenía tipo, descripción, prioridad, fecha y referencia. Se conserva esa información usando el contrato actual:

| Entrada anterior | Entrada actual |
| --- | --- |
| `ReportDraft.type` | `NewReport.type` |
| `ReportDraft.description` | `NewReport.description` |
| `ReportDraft.priority` | `NewReport.priority` |
| `ReportDraft.occurredAt: Long`, milisegundos Unix | `Instant.ofEpochMilli(fechaDelIncidente)` |
| `ReportDraft.placeReference` | `ReportLocation(reference = ubicacionEscrita)` |

Se aprovechan los campos del respaldo en el modelo ya preparado. Las pantallas y el login mantienen su desarrollo independiente.

## Ejemplo de conexión

```kotlin
val borrador = NewReport(
    type = IncidentType.RISK,
    description = "Hay una luminaria dañada frente al parque.",
    priority = Priority.MEDIUM,
    occurredAt = Instant.ofEpochMilli(fechaDelIncidente),
    location = ReportLocation(reference = "Frente al parque central"),
)
```

`fechaDelIncidente` representa el momento elegido expresado en milisegundos Unix. En una pantalla nueva también puede proporcionarse directamente un `Instant`. Los valores de tipo y prioridad están definidos en `Report.kt`.

## Probarlo sin una pantalla

En una terminal con `JAVA_HOME` apuntando a un JDK 27:

```powershell
.\gradlew.bat :core:domain:recibirReporte --console=plain
```

Para cambiar los cinco datos:

```powershell
.\gradlew.bat :core:domain:recibirReporte --console=plain `
    '-Ptipo=THEFT' `
    '-Pdescripcion=Se reporta el robo de una bicicleta.' `
    '-Pprioridad=HIGH' `
    '-Pfecha=2026-10-02T23:45:00Z' `
    '-Pubicacion=Entrada del parque central'
```

La consola muestra los valores que recibió el modelo. Los nombres de tipo y prioridad son los del contrato; el ejemplo admite minúsculas. Una categoría inexistente o una fecha que no pueda convertirse produce un error de ejecución.

Este ejemplo está en `src/examples`, separado del código de la aplicación. No se incluye en el APK. Recibe y muestra datos, sin crear folios ni llamar al repositorio. La validación del contenido y la persistencia ya existentes se revisarán en los siguientes pasos del trabajo acordado.

## Verificación de este paso

El 2 de octubre de 2026 se ejecutó el ejemplo con sus valores predeterminados y con los cinco valores personalizados del segundo comando. Ambos recibieron y mostraron los datos correctamente, incluidos tipo y prioridad enviados en minúsculas. Se comprobaron las 28 pruebas unitarias existentes del dominio, lint y la generación del APK. La revisión del `domain.jar` confirmó que el ejemplo no se empaqueta junto al código productivo.
