package com.example.citizensecurity.maps

import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.ReportLocation
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Puente técnico de los controles de ubicación, sin vistas, otra sesión o guardado.
 * Crear una vez e incondicionalmente en onCreate, antes de STARTED y en orden estable.
 * Su dueño debe vivir tanto como el ActivityResultCaller del [permissionBinding].
 * Conservar los modelos con ViewModelProvider y crear un puente nuevo al recrear la Activity.
 * El puente pertenece a esa Activity, nunca a su ViewModel ni a una MapView.
 *
 * onResume solo consulta permisos; onStop detiene la captura y obliga a renovarla; onDestroy
 * cierra el registro. Ni conceder permiso ni volver a la pantalla inicia GPS automáticamente.
 * Los controles llaman [select], [refreshLocation] y [confirm] mediante este puente en el hilo
 * principal. Su estado visual procede del modelo ya compartido. No mezclar otra sesión sobre
 * esa selección. Una confirmación entrega coordenadas; no las aplica ni guarda un reporte.
 *
 * El constructor adaptable recibe un registro ya creado; si falla una precondición, ese registro
 * sigue siendo responsabilidad del llamador. [forActivity] valida antes de crear el registro.
 */
@MainThread
class IncidentLocationFlowBinding(
    owner: LifecycleOwner,
    val permissionBinding: PreciseLocationPermissionBinding,
    private val model: IncidentLocationViewModel,
) : AutoCloseable {
    private val lifecycle = owner.lifecycle
    private var closed = false
    private val observer = LifecycleEventObserver { _, event ->
        if (!closed) when (event) {
            Lifecycle.Event.ON_RESUME -> permissionBinding.check()
            Lifecycle.Event.ON_STOP -> model.onStop()
            Lifecycle.Event.ON_DESTROY -> close()
            else -> Unit
        }
    }

    init {
        checkCanRegister(lifecycle)
        activeBindings[lifecycle] = WeakReference(this)
        lifecycle.addObserver(observer)
    }

    /** Solo un gesto visible solicita permiso. Una respuesta no inicia captura ni confirma. */
    fun requestPermission(explanationAccepted: Boolean = false): LocationPermissionAction {
        if (closed) return LocationPermissionAction.Inactive
        if (!isResumed()) return LocationPermissionAction.NotVisible
        return permissionBinding.request(explanationAccepted)
    }

    /** true indica que se entregó el gesto al modelo, no que el punto sea válido. */
    fun select(latitude: Double, longitude: Double): Boolean {
        if (!isResumed()) return false
        model.select(latitude, longitude)
        return true
    }

    /** Apertura explícita del mapa para otro borrador; conserva modelos y registro de permisos. */
    fun beginSelection(originalLocation: ReportLocation): Boolean =
        isResumed() && model.beginSelection(originalLocation)

    /** true indica gesto entregado; el estado del modelo expresa captura, bloqueo o error. */
    fun refreshLocation(): Boolean {
        if (!isResumed()) return false
        model.refreshLocation()
        return true
    }

    /** null también indica dueño inactivo; el modelo conserva las demás reglas de confirmación. */
    fun confirm(): NearbyLocationConfirmation? = if (isResumed()) model.confirm() else null

    /** Una MapView puede terminar antes que la Activity; detiene GPS sin cerrar sus permisos. */
    fun onMapViewClosed() {
        if (!closed) model.onStop()
    }

    /** Desconecta al dueño y detiene su captura sin cancelar la sesión retenida al recrear. */
    override fun close() {
        if (closed) return
        closed = true
        lifecycle.removeObserver(observer)
        if (activeBindings[lifecycle]?.get() === this) activeBindings.remove(lifecycle)
        try {
            model.onStop()
        } finally {
            permissionBinding.close()
        }
    }

    private fun isResumed() = !closed && lifecycle.currentState == Lifecycle.State.RESUMED

    companion object {
        // Referencias débiles: el registro no prolonga la vida de una Activity o de su puente.
        // Todos los accesos se serializan en el hilo principal por el contrato de la clase.
        private val activeBindings = WeakHashMap<Lifecycle, WeakReference<IncidentLocationFlowBinding>>()

        fun forActivity(
            activity: ComponentActivity,
            permissionModel: LocationPermissionViewModel,
            locationModel: IncidentLocationViewModel,
        ): IncidentLocationFlowBinding {
            checkCanRegister(activity.lifecycle)
            val permissions = PreciseLocationPermissionBinding.forActivity(activity, permissionModel)
            return try {
                IncidentLocationFlowBinding(activity, permissions, locationModel)
            } catch (failure: Exception) {
                permissions.close()
                throw failure
            }
        }

        private fun checkCanRegister(lifecycle: Lifecycle) {
            check(lifecycle.currentState != Lifecycle.State.DESTROYED &&
                !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                "Registra el flujo de ubicación antes de STARTED, una vez en onCreate."
            }
            check(activeBindings[lifecycle]?.get() == null) {
                "El dueño ya tiene un puente de ubicación activo."
            }
        }
    }
}
