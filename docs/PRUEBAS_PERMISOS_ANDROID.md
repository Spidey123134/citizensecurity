# Pruebas Android del permiso de ubicación

Versión actual **0.4.0a / código Android 19**, del **6 de octubre de 2026**; Release oficial autorizada, con validación completada y [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a) como referencia de publicación. Conserva permisos nativos y la regla de 5 km; añade el puente de ciclo de vida y el borrador sin activar guardado real ni consulta. La clave Google todavía está pendiente y el compañero conserva pantallas/login.

## Aislamiento

`verification/location` es una aplicación técnica con manifiesto `testOnly`, sin entrada en el launcher, Activity no exportada y respaldo desactivado. Comparte los directorios productivos `app/src/main/kotlin/com/example/citizensecurity/maps` y `app/src/main/kotlin/com/example/citizensecurity/report` mediante `sourceSets`: no copia el puente ni crea una implementación alternativa. Se registra en `AndroidSourceSet.kotlin.directories`, como exige el [Kotlin integrado de AGP 9](https://developer.android.com/build/migrate-to-built-in-kotlin); añadirlo al conjunto Java no compilaba las fuentes y se corrigió durante la primera validación.

| Variante | Paquete técnico |
| --- | --- |
| `precise` | `com.example.citizensecurity.verification.location.precise` |
| `approximate` | `com.example.citizensecurity.verification.location.approximate` |
| `denied` | `com.example.citizensecurity.verification.location.denied` |
| `flow` | `com.example.citizensecurity.verification.location.flow` |

Estos paquetes y sus APK de instrumentación son distintos de `com.example.citizensecurity`. No incluyen `CitizenSecurityApplication`, login, herramientas de reportes ni metadato de clave Maps. Los permisos se declaran únicamente para probar el contrato; no se solicita ubicación de fondo. Las Activities técnicas no abren una base de reportes ni capturan automáticamente. La suite de flujo selecciona puntos y pide captura explícita para comprobar la lógica; sus fuentes controladas y reloj están exclusivamente en el laboratorio. Un caso registra el proveedor GPS real y cancela antes del callback, sin inyectar coordenadas.

No se utiliza `:app:connectedAndroidTest`: ese comando instalaría el paquete productivo. El laboratorio se ejecuta exclusivamente mediante las tareas `:verification:location:...`. Las tres decisiones quedan aisladas por paquete, evitando revocar un permiso mientras su proceso de instrumentación está activo.

## Tres decisiones del diálogo nativo

La prueba parte de permisos sin conceder y exige una acción explícita para abrir el diálogo. Comprueba una sola solicitud pendiente, recrea la Activity mediante su API nativa mientras el diálogo está abierto y conserva el mismo ViewModel. Responde a los controles del PermissionController mediante identificadores de recurso, sin depender del texto o idioma.

El resultado se contrasta con `checkSelfPermission`: preciso debe conceder `FINE` y `COARSE`; aproximado debe conceder solo `COARSE`; rechazo debe dejar ambos ausentes. También se comprueban la recreación después del resultado, el cierre del puente anterior y la nueva Activity con hechos actuales. Conceder permiso no es una lectura GPS. Los casos extendidos 0.3.10 conservan el pin y comprueban cero capturas al recibir permiso. El gesto posterior no llega a la fuente con aproximado o rechazo; con preciso rechaza una lectura del fixture marcada como simulada, sin evidencia publicada.

Se usan AndroidX Test y [UI Automator 2.4.0 estable](https://developer.android.com/jetpack/androidx/releases/test-uiautomator). El manifiesto `testOnly` sigue la [referencia Android](https://developer.android.com/guide/topics/manifest/application-element#testOnly). Los identificadores del diálogo pueden cambiar en otros sistemas; una ausencia produce un fallo explícito, nunca una concesión automática mediante un watcher.

## Ejecutar

Desde la raíz del proyecto, con JDK 27 configurado y un emulador API 37 conectado:

```powershell
.\gradlew.bat :verification:location:connectedPreciseDebugAndroidTest :verification:location:connectedApproximateDebugAndroidTest :verification:location:connectedDeniedDebugAndroidTest --console=plain
```

La instalación exige un dispositivo de prueba; no se usa el teléfono personal como destino por defecto. Los permisos de los tres paquetes de diálogo deben estar sin conceder antes de ejecutar. La prueba falla si descubre un estado anterior, para que una autorización conservada no simule una respuesta al diálogo.

Para repetir, restablecer **solo** los datos de los tres paquetes técnicos anteriores, fuera de una instrumentación en ejecución. Nunca sustituir los nombres por el paquete productivo ni usar prefijos, comodines o un borrado general. El restablecimiento de fixtures no elimina los reportes de la aplicación. Los APK y resultados se generan en `verification/location/build`, ignorado por Git.

## Suite de composición

```powershell
.\gradlew.bat :verification:location:connectedFlowDebugAndroidTest :verification:location:lintFlowDebug --console=plain
```

El filtro de `.flow` selecciona `LocationFlowAndroidSuite` con 16 casos de `LocationFlowNativeTest` y dos de `AndroidGpsSourceNativeTest`. La instrumentación concede COARSE/FINE solo al paquete exacto `.flow` con UiAutomation; la fuente consulta ese permiso real. Las lecturas, reloj y estado GPS de la matriz son controlados; no relajan la política del producto ni se usan en su DEX. [Flujo Android](FLUJO_UBICACION_ANDROID.md) detalla cada grupo y sus límites.

La selección inicial por lista omitió los dos casos del proveedor. Se corrigió con la suite JUnit explícita y se verificó el resultado **18 = 16 + 2** por clase en los XML. El recuento debe comprobarse aunque Gradle diga `BUILD SUCCESSFUL`.

## Evidencia y límites

**Antecedente 0.3.10:** 21 casos Android distintos aprobados, cero fallos, errores u omitidos; 18 definiciones nuevas y tres casos anteriores extendidos. Las 121 JVM quedaron `UP-TO-DATE`, sin ejecución nueva. Debug/release y firma debug v2 correctos; lint sin errores, 13 advertencias previas de app y dos por cada una de las cuatro variantes técnicas. App instalada, fuentes visuales y APK anteriores conservados. El runner retiró los fixtures al concluir; no quedaron paquetes técnicos instalados. [Validación](VALIDACION.md) conserva recibos y diferencias entre lecturas controladas y registro nativo sin fix físico.

**Antecedente 0.3.9:** aprobó los **tres flujos nativos** de esta guía en **Android 17/API 37**, sin fallos, errores u omitidos. También se ejecutaron de nuevo las **121 JVM de app**: 124 pruebas en este corte. Debug y release compilan; lint no tiene errores (13 advertencias previas de app y 2 del laboratorio por variante). Se conservan los APK y fuentes visuales previos, y la aplicación instalada sigue en 0.3.1/código 8 con el mismo hash y fecha de actualización.

En esta ejecución, el runner retiró los paquetes técnicos al concluir. No fue necesario restablecer permisos manualmente; si un entorno conserva una instalación anterior, la comprobación inicial falla antes de solicitar. La preparación no contempla borrados de la aplicación productiva.

[Validación](VALIDACION.md) registra los resultados ejecutados, el sistema probado y la comprobación de que la instalación productiva conservó su versión y fecha. La evidencia JVM anterior de 0.3.8 se mantiene por separado.

La recreación de Activity con el diálogo abierto no acredita muerte de proceso. El laboratorio tampoco comprueba un teléfono físico, una lectura GPS aceptada, Google Maps renderizado, configuración Cloud o la conexión de los controles del compañero. El caso aproximado no autoriza reportar: la regla productiva sigue exigiendo permiso preciso y evidencia independiente, reciente y suficientemente precisa dentro de **5 km**.

No se rebaja la política de calidad para aceptar ubicaciones simuladas del emulador. Clave Google y mapa visible siguen pendientes. La preparación permanece local y Unreleased; no hay commit, subida, integración, etiqueta ni Release nueva.

## Ampliación 0.4.0a

La suite `LocationFlowAndroidSuite` conserva flujo/proveedor y añade `IncidentLocationFlowBindingNativeTest` y `ReportDraftNativeTest`. Comprueba registro único, visibilidad, cancelación, recreación, reapertura en la misma Activity, factory de borrador y restauración de SavedStateRegistry/Parcel con otro dueño. El borrador no incorpora SQLite, mapas renderizados o evidencia GPS. Los XML finales y recuentos están en [Validación](VALIDACION.md).
