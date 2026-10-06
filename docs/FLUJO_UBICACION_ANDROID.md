# Permiso, captura y confirmación de ubicación en Android

Versión actual **0.4.0a / código Android 19**, del **6 de octubre de 2026**; Release oficial autorizada, con validación completada y [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a) como referencia de publicación. Conserva permisos nativos y la regla de 5 km; añade el puente de ciclo de vida y el borrador sin activar guardado real ni consulta. La clave Google todavía está pendiente y el compañero conserva pantallas/login.

## Secuencia que conectarán sus controles

1. En `onCreate`, antes de STARTED y en orden estable, obtiene modelos con `ViewModelProvider` y registra una vez `IncidentLocationFlowBinding.forActivity(this, permissionModel, locationModel)`. Conserva modelos al recrear la Activity; cada Activity usa un puente nuevo. El formulario comparte `ReportDraftViewModel` con el mapa mediante el mismo dueño y clave.
2. Al abrir el mapa, obtiene `request = draft.beginLocationSelection()`, cierra el MapBinding anterior y llama a `flow.beginSelection(request.originalLocation)`. Solo procede en RESUMED; un rechazo cancela ese token del borrador. La nueva sesión exige renovar GPS, sin capturarlo automáticamente. Conecta `IncidentLocationMapBinding(googleMap, flow)` y conserva el token de esta apertura para aplicar/cancelar.
3. El botón de permiso llama a `flow.requestPermission()`. Cuando se necesita explicación, únicamente tras aceptación humana llama con `explanationAccepted = true`. El resultado actualiza permisos, sin iniciar GPS. El observer consulta permisos al volver; cancela captura en STOP y cierra el registro en DESTROY.
4. Un gesto separado en Obtener ubicación llama a `flow.refreshLocation()`. Con aproximado/ausente, la renovación devuelve `PermissionRequired` sin llamar a la fuente. Requiere preciso, GPS habilitado, precisión máxima **100 m**, antigüedad monotónica máxima **2 minutos**, coordenadas válidas y ausencia de marca simulada; timeout productivo **15 segundos**.
5. Clicks del SDK llaman a `flow.select`; ese pin no es evidencia del teléfono. El control de confirmar de la apertura vigente llama a `flow.confirm()`. Solo un `Confirmed` se entrega a `draft.applyLocationSelection(request.token, result)`. Exige **distancia aproximada + precisión <= 5.000 m**, evidencia actual y dueño RESUMED. Recibir Ready no confirma ni guarda.
6. Cancelar el token vigente del borrador conserva su ubicación y ediciones; después cancela el modelo de ubicación y cierra la vista. Cerrar MapBinding llama a `flow.onMapViewClosed()`: cancela captura sin cerrar permisos de la Activity. Una segunda apertura reinicia la selección en el mismo modelo y puente; un callback GPS anterior no cambia la nueva sesión.

El dueño descarta callbacks de controles/vistas anteriores antes de operar el flujo actual. Los tokens del borrador protegen la aplicación de resultados, pero no reemplazan la retirada de listeners de controles destruidos. No se registra otro puente en RESUMED ni se cierra el permiso al aparecer su diálogo.

La fábrica productiva sigue siendo `CitizenSecurityApplication.incidentLocationViewModelFactory(originalLocation)`: comparte `PhoneLocationRefresh`, `PhoneLocationEvidence` y reloj monotónico Android. El guard de SQLite revalida evidencia antes de escribir; `draft.review(now)` comprueba campos y no autoriza guardado. [Borrador](FASE_7.md) detalla entradas, token y restauración.

## Qué se verifica en el laboratorio

Se amplía `verification/location`, separado del APK productivo y marcado `testOnly`. Los paquetes existentes `.precise`, `.approximate` y `.denied` prueban las decisiones del diálogo real. Después del resultado comprueban el modelo del incidente: el pin sigue igual y hay **cero llamadas de captura automática**. El gesto posterior no llega a la fuente con permiso aproximado/ausente. Con permiso preciso, una lectura del fixture marcada como simulada se rechaza y no publica evidencia.

La variante `.flow` usa el paquete exacto `com.example.citizensecurity.verification.location.flow`. La instrumentación concede COARSE/FINE exclusivamente a ese fixture con [UiAutomation](https://developer.android.com/reference/android/app/UiAutomation#grantRuntimePermission(java.lang.String,%20java.lang.String)); los otros tres paquetes mantienen sus decisiones mediante el diálogo. No se revocan permisos del producto ni se instalan APK de `:app`.

`LocationFlowHarnessActivity` retiene los modelos y las dependencias del fixture mediante `ViewModelProvider`, registra el permiso antes de STARTED y conecta `onStop`/`onDestroy`. No tiene entrada en el launcher, mapa, formulario, botones de producto, autenticación, guardado o consulta. Solo conserva applicationContext en sus dependencias.

`ControllablePhoneLocationSource` existe únicamente en `verification/location`: consulta el **permiso nativo real**, pero la lectura, GPS habilitado y reloj se controlan para comprobar los límites de forma reproducible. El reloj sintético es coherente con las lecturas del fixture. Una lectura sintética con `isMock=false` sirve para probar las ramas de aceptación; **no acredita una ubicación física** ni cambia la implementación o política productivas. Los tiempos cortos de espera se pasan exclusivamente al laboratorio, sin cambiar los 15 segundos del producto.

| Grupo | Comprobación |
| --- | --- |
| Acción explícita | Permiso y pin no capturan; una renovación válida permite confirmar sin alterar la referencia original |
| Cercanía | Punto en Francia frente al fixture en México bloqueado; corregir a un punto cercano permite confirmar |
| Calidad | Lectura imprecisa, antigua, futura, simulada o sin precisión rechazadas, sin evidencia utilizable |
| Estados | GPS controlado apagado descarta evidencia; timeout cancela; error/reintento conserva el pin |
| Concurrencia | Gestos repetidos no abren dos capturas ni permiten confirmar durante espera |
| Ciclo Android | `onStop` cancela; recrear conserva modelos/dependencias y exige renovar; respuesta tardía no publica |
| Vigencia y cierre | Evidencia caducada al confirmar se rechaza; cancelar devuelve el original y cierra gestos posteriores |

## Comprobación del proveedor Android

`AndroidGpsSourceNativeTest` utiliza `AndroidPhoneLocationSource` original con el permiso real del paquete `.flow` y una Activity visible. Comprueba que el registro GPS nativo deja una solicitud esperando, la cancela **antes de entregar un callback** y verifica que no publicó evidencia. Requiere GPS habilitado en el emulador de prueba y falla explícitamente si está apagado; no cambia ajustes o proveedores, no inyecta coordenadas y no acepta una ubicación virtual como GPS físico.

Otra prueba convierte un objeto Android `Location` marcado como simulado con el adaptador productivo, y comprueba su rechazo a través de renovación, evidencia y modelo de confirmación. No se instala una aplicación de ubicación simulada ni se llama a `addTestProvider`.

La variante `.flow` selecciona una sola suite JUnit, `LocationFlowAndroidSuite`, que incluye integración controlada y proveedor nativo. El primer cierre dejó fuera los dos casos del proveedor al seleccionar las clases por lista; se corrigió con esta suite explícita. El resultado se contrasta con los XML por clase: un `BUILD SUCCESSFUL` por sí solo no acredita que se ejecutaron todos los casos previstos.

## Antecedente de resultados 0.3.10

En Android 17/API 37 aprobaron **21 casos distintos**: tres diálogos extendidos, 16 de composición y dos del proveedor/adaptador; **18 definiciones nuevas**. La suite `.flow` final acredita 18 por clase en sus XML, sin sumar de nuevo las 16 repeticiones. Las **121 JVM de app quedaron `UP-TO-DATE`** y conservan la evidencia previa. Debug/release compilan; firma debug v2 válida y lint sin errores, con 13 advertencias anteriores de app y dos por variante técnica.

El APK productivo excluye los fixtures. La app instalada sigue en 0.3.1/código 8 con la misma fecha/hash; recursos, login, accesos básicos y fuentes visuales se preservaron. [Validación](VALIDACION.md) registra comandos, artefactos y límites. No se declara completa la lectura física ni la pantalla integrada.

## Repetir

Con JDK 27 y el emulador de prueba API 37 conectado:

```powershell
.\gradlew.bat :verification:location:connectedPreciseDebugAndroidTest :verification:location:connectedApproximateDebugAndroidTest :verification:location:connectedDeniedDebugAndroidTest :verification:location:connectedFlowDebugAndroidTest --console=plain
```

Para repetir solo la suite de flujo:

```powershell
.\gradlew.bat :verification:location:connectedFlowDebugAndroidTest :verification:location:lintFlowDebug --console=plain
```

Los filtros de clase de cada variante se encuentran en el Gradle del laboratorio. No sustituir estos comandos por `:app:connectedAndroidTest`, que instalaría el producto. La [guía anterior](PRUEBAS_PERMISOS_ANDROID.md) explica el aislamiento de los tres diálogos y permisos iniciales. Resultados y APK del laboratorio quedan en su `build`, ignorado por Git; [Validación](VALIDACION.md) acredita las ejecuciones, recuentos y advertencias.

## Composición para la pantalla del compañero

Los controles usan el puente registrado en `onCreate`, sin duplicar observers manuales de STOP/DESTROY. La representación visual lee los estados de los modelos existentes mediante `repeatOnLifecycle`; los collectors representan datos y no capturan GPS ni guardan.

```kotlin
// locationModel y permissionModel se obtienen con ViewModelProvider antes de STARTED.
val flow = IncidentLocationFlowBinding.forActivity(this, permissionModel, locationModel)

fun onObtenerUbicacionPressed() {
    flow.refreshLocation() // Boolean = gesto entregado; leer locationModel.state para el resultado.
}

fun onConfirmarPuntoPressed(request: ReportLocationSelectionRequest) {
    // Ejecutar únicamente para los controles de la apertura vigente.
    val result = flow.confirm()
    if (result is NearbyLocationConfirmation.Confirmed) {
        draft.applyLocationSelection(request.token, result)
    }
}
```

La confirmación cambia exclusivamente las coordenadas del borrador y conserva la referencia actual. No conecta `ReportViewModel.save`, activa consulta o crea una cuenta. La integración completa y reapertura están en [Acoplar interfaz](ACOPLAR_INTERFAZ.md); los imports y controles corresponden a la futura pantalla del compañero.

## Límites del corte

La prueba de registro/cancelación no obtiene una lectura GPS física ni comprueba toda la retirada del listener ante fallos del servicio. La composición con fuente controlada no equivale a usar el teléfono real. Recrear Activity no demuestra muerte del proceso, sensor de orientación o un flujo visual aceptado por el usuario. Este corte tampoco comprueba revocación/cambio de FINE durante captura nativa o Android API 26–30.

La conexión de los controles del compañero, lectura válida en un dispositivo físico, estados GPS dentro de esa pantalla y Google Maps renderizado siguen pendientes. La clave Google aún no está configurada. Fase 6 abierta, **7/28 = 25%**, sin aceptar el flujo completo. El usuario autorizó integrar y publicar 0.4.0a; su estado y evidencia se registran en [Releases](RELEASES.md) y [Validación](VALIDACION.md). La aceptación del flujo visual sigue pendiente.
