# Proximidad del incidente al teléfono

La regla de 0.3.3 se conserva en la preparación **0.3.4 — Unreleased**. [Obtener ubicación](OBTENER_UBICACION.md) añade la fuente y el controlador de renovación, preparados para una invocación futura: valida la calidad de la lectura sin exigir que ya exista un pin. La confirmación y el guard productivo siguen comprobando aparte el radio de 5 km. No hay captura automática, permisos nuevos en el manifiesto ni flujo GPS o mapa real comprobado. El bloque siguiente conserva el alcance y la evidencia histórica de 0.3.3.

## 0.3.3 — Unreleased

El usuario pidió impedir reportes demasiado lejanos y confirmó un límite de **5 km alrededor del teléfono**. La preparación local **0.3.3 / código Android 10** añade esta regla al contrato de ubicación y a la composición del repositorio productivo. Conserva la base 0.3.2 y el ejemplo ficticio de 0.3.1. No incorpora SDK, mapa, captura GPS, permisos nuevos ni acceso visual al guardado real o a la consulta. Continúa Unreleased, sin commit, subida, integración, etiqueta ni publicación.

La futura pantalla de Google Maps podrá entregar el punto elegido al contrato. El SDK admite eventos sobre puntos del mapa y movimiento de marcadores; la selección, el pin y sus controles corresponden al compañero. La lógica preparada todavía no muestra ese mapa. Véase la [documentación oficial de eventos de Google Maps](https://developers.google.com/maps/documentation/android-sdk/events).

## Regla de cercanía

El punto del incidente y la lectura del teléfono son datos separados. El marcador nunca se usa como ubicación del dispositivo. `NearbyIncidentPolicy.evaluate(incident, fix, nowElapsedRealtimeNanos)` exige:

| Dato | Condición |
| --- | --- |
| Punto del incidente | Latitud y longitud completas, finitas y dentro de sus rangos geográficos. |
| Lectura del teléfono | Una `DeviceLocationFix` independiente, con coordenadas válidas. |
| Precisión de la lectura | Finita, no negativa y como máximo **100 m**. Sin precisión disponible se convierte a `NaN` y se rechaza. |
| Momento de la lectura | Tiempo monotónico no negativo, no futuro y con antigüedad de hasta **2 minutos**. |
| Ubicación simulada | Se rechaza una lectura marcada como simulada por Android. |
| Cercanía | **Distancia aproximada + precisión <= 5.000 m**. La imprecisión no amplía el radio permitido. |

El radio de 5 km fue elegido por el usuario. Los 100 m de precisión y 2 minutos de vigencia son límites técnicos configurables, no requisitos del PDF. La distancia usa Haversine con radio terrestre medio de 6.371.008,8 m: es una aproximación sobre una esfera, no una medición geodésica exacta. Android describe la precisión como una estimación con confianza del 68%; por ello, sumar esa precisión aplica un margen conservador sin garantizar la posición real. El tiempo transcurrido usa el reloj monotónico y evita depender de cambios de fecha del teléfono. Véase [Android Location](https://developer.android.com/reference/android/location/Location).

No se prohíbe reportar en Francia u otro país. Una lectura del teléfono en México y un punto en Francia quedan fuera del radio. Una persona cerca del punto en Francia puede reportarlo si satisface la misma regla. La condición se aplica igual a emergencias, robos y las demás categorías; no depende del tipo de incidente.

## Confirmar sin guardar

`IncidentLocationSelection.propose(latitude, longitude)` conserva la selección provisional de 0.3.2. El nuevo camino **`confirmNearby`** evalúa la cercanía antes de devolver la ubicación confirmada:

```kotlin
val proposed = selection.propose(latitude, longitude)
val app = application as CitizenSecurityApplication

when (val result = proposed.confirmNearby(
    fix = app.phoneLocationEvidence.currentFix(),
    nowElapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
)) {
    is NearbyLocationConfirmation.Confirmed -> {
        val updatedDraft = draft.copy(location = result.location)
        // Devolver el borrador al formulario; todavía no guardar.
    }
    is NearbyLocationConfirmation.Blocked -> {
        // Mostrar result.rejection.message y solicitar otra lectura o punto.
    }
    NearbyLocationConfirmation.NoSelection -> { /* Pedir seleccionar un punto. */ }
    is NearbyLocationConfirmation.InvalidCoordinates -> { /* Mostrar result.message. */ }
}
```

El ejemplo muestra la conexión futura y requiere sus importaciones habituales. La pantalla debe conservar la sesión devuelta por `propose`. `cancel()` conserva exactamente la ubicación original, sin guardar. **`confirm()` sigue siendo el ejemplo geométrico inicial de 0.3.2 y no acredita proximidad**; no debe utilizarse como aprobación para reportar un incidente.

Las respuestas de proximidad distinguen coordenadas faltantes o inválidas, ausencia de lectura, mala precisión, lectura simulada, reloj inválido, lectura futura o antigua y punto fuera del radio. Cambiar el pin después de confirmar exige una confirmación nueva. El borrador completo continúa pasando por `ReportValidator` para sus demás campos.

## Evidencia Android y escritura protegida

`PhoneLocationEvidence` recibe una función que comprueba el permiso preciso vigente. Su `update(location)` transforma una `android.location.Location` recibida del proveedor en una instantánea `DeviceLocationFix`; no conserva la instancia mutable de `Location`. `currentFix()` devuelve la evidencia disponible únicamente con ese permiso vigente y `clear()` la descarta. La extensión `Location.toDeviceLocationFix()` copia coordenadas, precisión y tiempo monotónico; usa `isMock` desde API 31 y `isFromMockProvider` en las API anteriores admitidas. Un dato sin precisión no recibe un valor ficticio aceptable. Estas propiedades y el cambio de API están descritos en [Android Location](https://developer.android.com/reference/android/location/Location).

La preparación 0.3.4 ofrece `application.phoneLocationRefresh.refresh()` para la futura solicitud explícita desde una pantalla visible. Su fuente aporta una lectura del proveedor GPS y el controlador comprueba su calidad antes de alimentar la evidencia compartida; no se alimenta desde el pin, formulario o valores predeterminados. [Obtener ubicación](OBTENER_UBICACION.md) explica los estados y la cancelación. La solicitud de permisos y conexión visual siguen pendientes; no hay permisos de ubicación en el manifiesto ni captura automática. **Sin evidencia válida, el repositorio productivo rechaza un guardado**.

`CitizenSecurityApplication.reportRepository` incorpora `NearbySqliteReportGuard`, que obtiene la evidencia actual y `SystemClock.elapsedRealtimeNanos()` cada vez. El constructor de `SqliteReportRepository` recibe `createGuard: (NewReport) -> Unit`; su composición productiva verifica el punto:

1. Antes de abrir la base de escritura, para rechazar una petición inválida sin crearla.
2. Después de abrirla, antes de iniciar la transacción, por si la lectura caducó durante la espera.
3. Antes de confirmar la transacción, para rechazar y revertir una inserción si la evidencia caducó o dejó de cumplir la regla durante el guardado.

El guard lanza `InvalidReportException` con un error del campo `LOCATION_COORDINATES` y el motivo de proximidad. Confirmar previamente un punto no evita esta nueva comprobación ni autoriza un guardado indefinido. No se afirma reversión de una escritura ya confirmada; tampoco se eliminan o migran reportes anteriores.

El repositorio genérico conserva un guard vacío por defecto para ejemplos y pruebas independientes. **La aplicación productiva usa siempre la composición protegida de `CitizenSecurityApplication`**; una pantalla futura no debe construir por su cuenta un repositorio sin ese guard. El ejemplo ficticio usa SQLite en memoria y datos fijos, aislado del repositorio productivo: no requiere GPS, no acepta datos reales y no modifica la base del usuario.

## Pruebas y límites

El cierre local comprobó **148 pruebas diferentes**, con cero fallos: 86 de dominio, 30 de app y 32 instrumentadas Android en API 37. La primera ejecución aprobó 147; después se agregó y comprobó la caducidad antes de confirmar la transacción, repitiendo la suite instrumentada. Compilación y lint debug aprobaron, con las 13 advertencias visuales previas. La demostración `:core:domain:comprobarProximidad` mostró los casos con datos ficticios sin guardar. [Validación](VALIDACION.md) conserva los comandos y distingue ambas ejecuciones.

La futura pantalla debe pedir una confirmación nueva después de cambiar el pin. Ese gesto todavía no se implementa ni se verifica; el repositorio comprueba por su cuenta la proximidad de las coordenadas finales, sin confiar en una bandera de aprobación.

Las comprobaciones nuevas cubren la regla pura, el contrato de confirmación, la conversión y evidencia Android y el rechazo transaccional. Se revisan puntos cercanos y lejanos —incluido México/Francia—, ambos lados del límite, precisión sumada al radio, ausencia de lectura, coordenadas inválidas, antigüedad, tiempo futuro y marca de simulación. La persistencia se comprueba con bases de prueba, verificando que un rechazo no inserte un reporte y que la caducidad durante una escritura provoque rollback. Los resultados realmente ejecutados y sus cantidades están en [Validación](VALIDACION.md); las 92 pruebas anteriores corresponden a 0.3.2 y no sustituyen esta ejecución.

Este filtro local reduce reportes a distancia. **No verifica la veracidad del incidente ni impide toda falsificación de ubicación**: una aplicación alterada o un dispositivo manipulado pueden falsificar evidencia o saltar comprobaciones locales. Un eventual servicio tendría que comprobar sus propias reglas; no se implementan servidor, cuentas, límites por usuario ni retardos en este incremento. No hay pruebas de mapa real, GPS real o teléfono físico en este paso.

La función pertenece a **fase técnica 6**, apartados **4.3 y 4.5** del PDF. Refuerza el hito del contrato del mapa: [Avance](AVANCE.md) conserva **7/28 = 25%** porque siguen pendientes captura GPS, permisos y SDK. Las interfaces y el login permanecen a cargo de `vazdavr-sudo`.
