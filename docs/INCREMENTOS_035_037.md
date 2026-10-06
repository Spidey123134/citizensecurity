# Preparación local de 0.3.5 a 0.3.7

La preparación actual es **0.3.7 / código Android 14 — Unreleased**. El usuario pidió avanzar varias funciones y revisar fallos de una aplicación de incidentes. Estos tres números agrupan cambios acumulados del trabajo local; no son tres APK entregados, etiquetas o Releases nuevas. Se conserva el trabajo anterior y la interfaz del compañero. No se creó un commit, subida, integración ni publicación.

Continúa la **fase técnica 6**, relacionada con **4.3 Geolocalización** y **4.5 Mapa de incidentes** del PDF. El refuerzo de persistencia también mantiene la fase 3. Las funciones visibles siguen siendo las herramientas básicas de 0.3.1 y el ejemplo ficticio en memoria; el guardado real y la consulta permanecen ocultos. La autenticación y las pantallas corresponden al compañero.

## 0.3.5: fallos de GPS y liberación de solicitudes

La renovación explícita del teléfono sigue acotada a **15 segundos**, con una sola solicitud a la vez. Los fallos del proveedor o del servicio producen un estado identificable y no conceden evidencia. La cancelación se propaga y descarta la lectura; no se convierte en un éxito ni en una espera en segundo plano.

La fuente Android intenta retirar el listener al terminar o cancelarse, incluido un callback que llegue mientras se registra. Si el retiro falla por un error transitorio del servicio, realiza **un segundo intento inmediato**, sin bucle, tarea diferida ni reintentos ilimitados. Una excepción de permiso al retirar no desencadena ese segundo intento. Si no logra la limpieza, no entrega una lectura utilizable como éxito; conserva el fallo anterior cuando ya lo había. No se promete que una plataforma que rechace ambos intentos haya liberado el recurso.

Antes de devolver `Ready`, se revisan de nuevo el permiso, el proveedor habilitado y la vigencia de la lectura después de publicarla. Así, revocar el permiso, apagar el GPS o dejar caducar la evidencia durante ese paso no conserva una autorización anterior.

## 0.3.6: modelo compartido para selección y captura

`IncidentLocationViewModel` posee una sola sesión de selección y ofrece `StateFlow<IncidentLocationState>`. No crea vistas, pide permisos, mueve automáticamente el pin ni guarda reportes. Construir el modelo no inicia GPS.

| Estado | Significado para los controles del compañero |
| --- | --- |
| `Idle` | No se ha seleccionado un incidente. Puede contener una lectura del teléfono, que no selecciona un pin. |
| `Capturing` | Se está renovando el GPS; conserva la propuesta. No confirmar ni iniciar otra captura. |
| `PointSelected` | Hay un punto provisional; todavía no se ha aplicado al borrador. Confirmar vuelve a comprobar la evidencia actual. |
| `Error` | Muestra `issue.message` y conserva la propuesta para corregirla o renovar la ubicación. `Blocked` conserva el motivo estructurado de proximidad. |
| `Confirmed` | Entrega las coordenadas aceptadas y la distancia aproximada. No significa que se haya guardado o enviado un reporte. |
| `Cancelled` | El flujo terminó devolviendo exactamente la ubicación original. |

Cada toque sustituye la propuesta anterior, incluso si es inválido. Una actualización fallida no modifica la ubicación original ni mueve el pin. Mientras se captura, confirmar no reutiliza evidencia previa. `Ready` de la fuente tampoco basta: el modelo vuelve a consultar la evidencia publicada y su tiempo.

`onStop()` cancela la captura, conserva la propuesta y exige una renovación explícita antes de confirmar al volver. `cancel()` termina el flujo; las respuestas tardías no pueden reabrirlo. Si ya se confirmó, un cierre posterior conserva el punto aceptado. Rotación con el mismo `ViewModelStore` conserva el modelo y el pin; no existe recuperación del borrador ni de evidencia tras muerte del proceso.

`confirm()` devuelve `NearbyLocationConfirmation?`: `null` indica captura en curso, renovación pendiente o sesión terminada. Solo `Confirmed` permite devolver una ubicación al formulario. Rechazos, falta de pin o coordenadas inválidas dejan la sesión abierta para corregir. La regla sigue siendo **distancia aproximada + precisión <= 5.000 m**, con lectura reciente, suficientemente precisa y sin marca de simulación.

## 0.3.7: persistencia conservadora y conexión al SDK

La base utiliza un `DatabaseErrorHandler` propio. Si SQLite detecta corrupción, cierra cuando es posible y lanza `SQLiteDatabaseCorruptException`; evita el borrado y la recreación vacía que podría efectuar el manejador predeterminado. No repara datos ni vuelve legible una base dañada. Las migraciones o versiones inesperadas siguen fallando sin recreación destructiva.

La escritura comprueba cancelación también **después de cada guard de proximidad**: antes de abrir la base, antes de comenzar la transacción y antes de marcarla correcta. Si el guard coincide con cancelar la operación, no se continúa con una inserción o confirmación que ignore esa cancelación. Una cancelación dentro de la transacción produce rollback; no se promete deshacer una escritura ya confirmada.

`MapsReadinessChecker` distingue clave ausente, servicios de Google Play no disponibles y fallo de comprobación. Su `Ready` comprueba únicamente **prerrequisitos locales**. No valida la autorización de la clave en Google Cloud, restricciones de paquete/firma, facturación, red ni descarga/renderizado del mapa. No debe presentarse como confirmación de que Google Maps funciona.

`IncidentLocationMapBinding` conecta los toques del `GoogleMap` real con `model.select(...)`. El compañero dibuja y controla su vista observando el modelo. El binding posee exclusivamente el listener de toque; no se debe registrar otro a la vez. `close()` lo retira y llama `model.onStop()`, conservando el pin pero exigiendo renovación. Se mantiene el puente anterior `GoogleMapIncidentBinding` como antecedente de 0.3.4; para este flujo se usa el modelo nuevo, sin crear una segunda sesión para el mismo borrador.

## Conexión que prepara el compañero

1. Obtener el modelo desde `CitizenSecurityApplication.incidentLocationViewModelFactory(originalLocation)` y el `ViewModelStore` de su pantalla. La ubicación original se suministra al crear ese flujo; otra edición independiente requiere otro modelo/propietario, no reutilizar un modelo terminado.
2. Consultar `application.mapsReadinessChecker.check()` antes de crear la vista del mapa. Mostrar el estado correspondiente en sus controles, sin tratar `Ready` como validación remota de Google Cloud.
3. Solicitar permiso de ubicación precisa mediante su interfaz y un gesto explícito; el modelo no concede permisos. Llamar `model.refreshLocation()` desde una pantalla visible cuando el usuario lo solicite.
4. Al recibir `GoogleMap` en `getMapAsync`, crear `IncidentLocationMapBinding(map, model)`. Observar `model.state` mientras la pantalla esté visible para dibujar el pin, el progreso y `Error.issue.message`.
5. Enviar el botón de confirmar a `model.confirm()`. Devolver `result.location` al formulario únicamente para `NearbyLocationConfirmation.Confirmed`; no guardar ni enviar desde ese evento. El repositorio productivo mantiene su guard independiente si se autoriza ese flujo después.
6. Llamar `model.onStop()` al dejar de estar visible; cerrar el binding antes de destruir la vista o sustituir su listener. Al abandonar definitivamente el flujo, `model.cancel()` devuelve el original o el punto ya aceptado. Conservar el ciclo de vida propio de `MapView` o `MapFragment` en la interfaz del compañero.

La fábrica productiva compone la renovación, `phoneLocationEvidence.currentFix()` y `SystemClock.elapsedRealtimeNanos`. No obtiene la posición del teléfono del marcador ni del formulario. La aplicación no inicia captura al construir la fábrica.

## Verificación y pendientes

La comprobación integral aprobó pruebas JVM e instrumentadas en API 37, compilaciones debug/release y lint. [Validación](VALIDACION.md) registra el comando, el recuento final, resultados reutilizados mediante `UP-TO-DATE`, advertencias y artefactos. Las pruebas de corrupción y escritura usan bases aisladas de prueba; no borran ni reparan bases productivas.

Estos resultados comprueban contratos, cancelación, carreras controladas y persistencia. No prueban captura GPS real, carga del mapa con clave válida, permisos interactivos ni un flujo visual integrado. No equivalen a ausencia total de errores, aceptación del usuario o preparación para producción.

El avance se conserva en **7/28 hitos = 25%**. Falta configurar y comprobar Google Cloud y el mapa real, verificar GPS y permisos en el flujo del compañero y coordinar las pantallas sin activar guardado real ni consulta. Autenticación, fotos, reportes propios, administración y notificaciones conservan sus pendientes del plan. El filtro local de cercanía no demuestra que ocurrió un incidente ni impide toda falsificación del dispositivo.
