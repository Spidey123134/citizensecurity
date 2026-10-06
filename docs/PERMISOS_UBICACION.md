# Permiso de ubicación en primer plano

Versión actual **0.4.0a / código Android 19**, del **6 de octubre de 2026**; Release oficial autorizada, con validación completada y [Release v0.4.0a](https://github.com/Spidey123134/citizensecurity/releases/tag/v0.4.0a) como referencia de publicación. Conserva permisos nativos y la regla de 5 km; añade el puente de ciclo de vida y el borrador sin activar guardado real ni consulta. La clave Google todavía está pendiente y el compañero conserva pantallas/login.

Corresponde a la [fase 6](FASE_6.md), que sigue abierta. El avance permanece en **7/28 hitos = 25%**: preparar este puente no acredita un mapa visible ni ubicación obtenida de un dispositivo real. La evidencia ejecutada y sus límites se registran en [Validación](VALIDACION.md).

## Conexión actual 0.4.0a

Para el flujo completo utiliza `IncidentLocationFlowBinding.forActivity(activity, permissions, locationModel)`, una vez en onCreate antes de STARTED. Sus controles llaman `requestPermission`, `beginSelection`, `select`, `refreshLocation` y `confirm`; el observer consulta al volver y cancela/cierra cuando corresponde. No combines este observer con los callbacks manuales del ejemplo básico posterior. Reabrir el mapa usa `beginSelection`, con el mismo modelo/registro y renovación explícita.

El puente básico anterior se conserva para usos independientes de permiso. Su ejemplo manual sigue siendo válido si no se utiliza el observer de 0.4.0a. [Flujo Android](FLUJO_UBICACION_ANDROID.md) y [borrador](FASE_7.md) muestran la composición actual. Las nuevas pruebas no sustituyen la revisión en la pantalla del compañero.

## Contrato compartido

[LocationPermissionViewModel](../app/src/main/kotlin/com/example/citizensecurity/maps/LocationPermissionViewModel.kt) no conserva una Activity ni un Context. La pantalla lo obtiene mediante `ViewModelProvider` y `LocationPermissionViewModel.factory()`, observa su `state: StateFlow<LocationPermissionState>` y conserva ese mismo modelo al rotar.

| Campo | Significado |
| --- | --- |
| `access` | `Unknown`, `Denied`, `Approximate` o `Precise`, según la comprobación actual. |
| `shouldExplain` | Android recomienda explicar por qué se necesita permiso preciso. |
| `requestInFlight` | Hay una petición coordinada pendiente; otro gesto no abre un segundo diálogo. |
| `issue` | `ExplanationRequired`, `Cancelled`, `PermissionCheckFailed`, `RequestLaunchFailed` o ningún error. |

`LocationPermissionSnapshot(preciseGranted, approximateGranted, shouldExplain)` contiene los hechos leídos del sistema. `inspect(snapshot)` actualiza esos hechos y elimina un error anterior, pero conserva una solicitud pendiente. `prepareRequest(snapshot, explanationAccepted)` prepara una sola petición; un permiso preciso ya concedido devuelve `AlreadyGranted`. El resultado final libera la petición. Si falla la comprobación durante la espera, se conserva `requestInFlight`; si falla durante el callback final, `onPermissionCheckFailed(requestFinished = true)` lo libera y deja el acceso en `Unknown` para permitir un nuevo gesto explícito.

El modelo no tiene un estado de denegación permanente. `shouldExplain = false` también puede aparecer antes de solicitar por primera vez o en otras decisiones del sistema: no demuestra que el usuario marcó «no volver a preguntar». No se abre Ajustes automáticamente ni se insiste al rechazar. La pantalla permite cancelar la explicación y seguir usando las funciones que no necesitan ubicación. [Guía oficial de permisos](https://developer.android.com/training/permissions/requesting).

## Puente AndroidX

[PreciseLocationPermissionBinding](../app/src/main/kotlin/com/example/citizensecurity/maps/PreciseLocationPermissionBinding.kt) registra `ActivityResultContracts.RequestMultiplePermissions`. Su fábrica `forActivity(activity, model)` consulta permisos mediante `ContextCompat.checkSelfPermission`, la explicación mediante `ActivityCompat.shouldShowRequestPermissionRationale` y la visibilidad mediante el estado `RESUMED` de esa Activity.

El constructor alternativo recibe `caller: ActivityResultCaller`, `model`, `readSnapshot` e `isResumed`. Permite adaptar el contrato a otro dueño o probarlo con un registro controlado; el consumidor conserva las mismas reglas de registro y ciclo de vida. Los eventos del puente y del modelo se usan en el hilo principal.

- Registrar **siempre**, en `onCreate` antes de `STARTED`, y en el mismo orden respecto de los demás contratos en cada recreación. Registrar no abre un diálogo ni lee GPS.
- Conservar el puente durante toda la vida del `ActivityResultCaller`. No recrearlo cuando cambia un `MapView` o se destruye únicamente su vista: registrar otro contrato en el mismo dueño puede cambiar su identidad y perder la entrega esperada.
- Para `forActivity`, ejecutar `close()` en `onDestroy` de la Activity. Es idempotente y retira el callback, pero no cancela el diálogo del sistema ni revoca permisos. No cerrar este puente en `onStop` mientras una petición está abierta.
- Al rotar, la nueva Activity registra sus contratos en el mismo orden y recupera el mismo ViewModel. Este conserva `requestInFlight`; el callback del nuevo dueño libera la petición. Los callbacks del puente cerrado se descartan.
- Tras morir el proceso, un modelo nuevo comienza en `Unknown`, sin recuperar una autorización de GPS ni lanzar otra petición. Se vuelven a comprobar permisos; el callback restaurado por Activity Result puede actualizar el modelo aunque no tenga una petición local pendiente. La restauración nativa requiere el registro estable anterior. [Guía oficial de Activity Result](https://developer.android.com/training/basics/intents/result).

`request()` se invoca por un gesto del usuario con el dueño en `RESUMED`. Devuelve `LaunchRequest`, `AlreadyGranted`, `ExplanationRequired`, `AlreadyRunning`, `Inactive`, `NotVisible` o `Unavailable`; únicamente `LaunchRequest` abre el diálogo. Tras aceptar una explicación, otro evento explícito llama a `request(explanationAccepted = true)`. `check()` relee el estado al volver a la pantalla o de Ajustes y no abre diálogos ni solicita ubicación.

La petición solicita **`ACCESS_COARSE_LOCATION` y `ACCESS_FINE_LOCATION` juntos**. Android 12 y posteriores permiten conceder solo ubicación aproximada y pueden ignorar una solicitud de `FINE` aislada. La regla de cercanía del proyecto exige permiso preciso y una lectura cuya precisión declarada sea como máximo **100 m**; por eso se prepara la solicitud de precisión. Conceder `FINE` todavía no garantiza que una lectura cumpla ese límite. [Permisos de ubicación en tiempo de ejecución](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime).

El callback no toma sus booleanos como una autorización definitiva: relee permisos actuales. Un resultado vacío señala cancelación, pero si esa comprobación confirma permiso preciso vigente, el modelo conserva `Precise` sin marcar `Cancelled`. Si solo sigue concedido el aproximado, conserva `Approximate` y puede informar la cancelación de la petición de precisión. Ninguno de esos resultados confirma un punto ni produce evidencia GPS válida.

## Ejemplo de conexión para la futura Activity

El siguiente fragmento ilustra la composición; **no se añadió esta pantalla a la aplicación**. Los controles y la representación de estados pertenecen al compañero. La implementación de la explicación debe ofrecer cancelar sin invocar `onAccepted`.

```kotlin
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.citizensecurity.maps.LocationPermissionAction
import com.example.citizensecurity.maps.LocationPermissionState
import com.example.citizensecurity.maps.LocationPermissionViewModel
import com.example.citizensecurity.maps.PreciseLocationPermissionBinding
import kotlinx.coroutines.launch

abstract class LocationPermissionHostExample : ComponentActivity() {
    private val permissions by lazy {
        ViewModelProvider(this, LocationPermissionViewModel.factory())
            .get(LocationPermissionViewModel::class.java)
    }
    private lateinit var permissionBinding: PreciseLocationPermissionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mantener esta posición respecto de los demás registros en cada recreación.
        permissionBinding = PreciseLocationPermissionBinding.forActivity(this, permissions)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                permissions.state.collect { renderPermissionState(it) }
            }
        }
    }

    // Conectar al gesto de los controles propios de la pantalla.
    fun onLocationPermissionPressed() {
        if (permissionBinding.request() == LocationPermissionAction.ExplanationRequired) {
            showLocationExplanation {
                permissionBinding.request(explanationAccepted = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionBinding.check()
    }

    override fun onDestroy() {
        permissionBinding.close()
        super.onDestroy()
    }

    protected abstract fun renderPermissionState(state: LocationPermissionState)
    protected abstract fun showLocationExplanation(onAccepted: () -> Unit)
}
```

## Conexión con ubicación y mapa

El permiso preciso, el proveedor GPS habilitado y una lectura aceptada son condiciones distintas. Una pantalla con permiso puede encontrar GPS desactivado, tiempo agotado o una lectura rechazada; los estados de [Obtener ubicación](OBTENER_UBICACION.md) siguen vigentes.

Cuando el usuario pulse el control de renovar ubicación, la futura pantalla puede llamar a `IncidentLocationViewModel.refreshLocation()` si dispone del permiso necesario. El resultado del diálogo y `onResume` **no realizan esa llamada automáticamente**. `IncidentLocationViewModel.onStop()` conserva su obligación independiente: cancela la captura, mantiene el punto provisional y exige renovar antes de confirmar al volver. Detener GPS no requiere cerrar el puente de permisos.

El puente tampoco habilita la capa de ubicación de Google Maps, selecciona un incidente, confirma un punto o guarda un reporte. La configuración y la pantalla del mapa siguen pendientes según [Google Maps](GOOGLE_MAPS.md). Guardado real y consulta conservan sus accesos ocultos.

## Compatibilidad del SDK

Al conectar Activity Result, lint detectó `Fragment 1.1.0` heredado de Google Maps; requiere al menos 1.3.0 para esa API. Se añadió una restricción de dependencia a `androidx.fragment:fragment:1.9.1`, estable según las [notas oficiales](https://developer.android.com/jetpack/androidx/releases/fragment). Gradle confirma esa versión resuelta, y lint debug/release aprobó sin su error. No se ocultó con suppress ni baseline, ni se cambió la pantalla a Fragments.

## Validación de composición Android 0.3.10

Los tres diálogos se extendieron para comprobar pin conservado y **cero capturas automáticas**. Una acción posterior no llama a la fuente si el permiso es aproximado o ausente. Con preciso llega una vez a una lectura del fixture marcada como simulada y la rechaza, sin publicar evidencia ni confirmar.

La variante `.flow` prueba 16 escenarios de composición con permiso nativo/ciclo reales y lecturas, reloj y estado GPS controlados. Dos casos más verifican registro GPS nativo cancelado antes del callback y rechazo de la marca simulada de `Location` a través del adaptador y flujo originales. **21 casos Android distintos aprobados, 18 definiciones nuevas**; las 121 JVM quedaron `UP-TO-DATE`. No se obtuvo una posición física. [Flujo Android](FLUJO_UBICACION_ANDROID.md) explica la conexión y [Validación](VALIDACION.md) los resultados y límites. Los permisos del paquete productivo y su instalación se conservaron.

## Antecedente de validación nativa 0.3.9

Tres flujos de permisos aprobaron en Android 17/API 37 usando este mismo puente: preciso, aproximado y rechazo. La Activity se recreó con el diálogo abierto; conservó su modelo y una sola petición, recibió el resultado en el nuevo dueño y comprobó permisos vigentes. También aprobaron las 121 JVM de app ejecutadas de nuevo. Se usaron paquetes técnicos separados; la app instalada conserva versión 0.3.1/código 8, hash y fecha de actualización.

No se prueba muerte del proceso o sensor de orientación, GPS físico, Google Maps renderizado ni los controles de la pantalla del compañero. [Laboratorio](PRUEBAS_PERMISOS_ANDROID.md) y [Validación](VALIDACION.md) conservan la evidencia y límites.

## Antecedente de validación 0.3.8

Las pruebas del modelo y del puente cubren precisión frente a ubicación aproximada, explicación previa, rechazo, cancelación, peticiones duplicadas, retención del modelo, callbacks restaurados y tardíos, lectura actual de permisos y fallos de lectura o lanzamiento. El puente usa el contrato AndroidX real con un `ActivityResultCaller` controlado en esas pruebas: no equivale a mostrar el diálogo nativo, rotar una Activity Android real ni conceder permisos mediante un usuario.

La primera compilación de pruebas detectó un acceso por índice separado por un salto de línea en Kotlin. Se corrigió usando `.get(...)`, sin habilitar flags experimentales. Solo [Validación](VALIDACION.md) acredita las ejecuciones finalmente aprobadas, sus cantidades y artefactos; no se confunden pruebas previstas con resultados ejecutados.

En 0.3.8 no se instaló la preparación sobre la aplicación del usuario ni se comprobó diálogo nativo, captura GPS o mapa reales. Su evidencia se conserva como antecedente. El paso 0.3.9 usa el mismo puente con una Activity Android técnica y comprueba el diálogo de permisos en paquetes aislados; [Validación](VALIDACION.md) registra el resultado realmente ejecutado. La clave de Google, controles del compañero, GPS físico y mapa renderizado siguen pendientes. Todo permanece local y Unreleased, sin commit, subida, integración, etiqueta ni Release.
