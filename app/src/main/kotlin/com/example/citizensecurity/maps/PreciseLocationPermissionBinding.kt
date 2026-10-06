package com.example.citizensecurity.maps

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.MainThread
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CancellationException

/**
 * Puente de permiso en primer plano. Registrar incondicionalmente en onCreate, antes de STARTED,
 * y en el mismo orden en cada recreación. [request] se llama únicamente por un gesto del usuario.
 * El resultado relee los permisos: nunca solicita GPS, confirma un pin ni guarda un reporte.
 * El ViewModel del dueño conserva la solicitud durante una rotación; cerrar este puente no
 * cancela el diálogo del sistema. El nuevo dueño registra otro puente con el mismo modelo.
 * Conservar el puente durante toda la vida del ActivityResultCaller, no recrearlo con MapView.
 */
@MainThread
class PreciseLocationPermissionBinding(
    caller: ActivityResultCaller,
    private val model: LocationPermissionViewModel,
    private val readSnapshot: () -> LocationPermissionSnapshot,
    private val isResumed: () -> Boolean,
) : AutoCloseable {
    private var closed = false
    private val launcher = caller.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (!closed && model.isActive) {
            try {
                val snapshot = readSnapshot()
                if (!closed) model.onPermissionResult(snapshot, cancelled = result.isEmpty())
            } catch (cancelled: CancellationException) {
                if (!closed) model.onPermissionCheckFailed(requestFinished = true)
                throw cancelled
            } catch (_: Exception) {
                if (!closed) model.onPermissionCheckFailed(requestFinished = true)
            }
        }
    }

    /** Consultar al volver a la pantalla o de Ajustes; no lanza diálogos ni GPS. */
    fun check() {
        if (closed || !model.isActive) return
        try {
            val snapshot = readSnapshot()
            if (!closed) model.inspect(snapshot)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (!closed) model.onPermissionCheckFailed()
        }
    }

    /** explanationAccepted solo después de aceptar la explicación en los controles del dueño. */
    fun request(explanationAccepted: Boolean = false): LocationPermissionAction {
        if (closed || !model.isActive) return LocationPermissionAction.Inactive
        if (model.state.value.requestInFlight) return LocationPermissionAction.AlreadyRunning
        val snapshot = try {
            if (!isResumed()) return LocationPermissionAction.NotVisible
            readSnapshot()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (closed || !model.isActive) return LocationPermissionAction.Inactive
            model.onPermissionCheckFailed()
            return LocationPermissionAction.Unavailable
        }
        if (closed || !model.isActive) return LocationPermissionAction.Inactive
        val action = model.prepareRequest(snapshot, explanationAccepted)
        if (action == LocationPermissionAction.LaunchRequest) {
            try {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            } catch (cancelled: CancellationException) {
                if (!closed) model.onRequestLaunchFailed()
                throw cancelled
            } catch (_: Exception) {
                if (closed || !model.isActive) return LocationPermissionAction.Inactive
                model.onRequestLaunchFailed()
                return LocationPermissionAction.Unavailable
            }
        }
        return action
    }

    /** Al destruir el ActivityResultCaller; para forActivity, en onDestroy de la Activity. */
    override fun close() {
        if (closed) return
        closed = true
        launcher.unregister()
    }

    companion object {
        fun forActivity(activity: ComponentActivity, model: LocationPermissionViewModel) =
            PreciseLocationPermissionBinding(
                activity,
                model,
                {
                    LocationPermissionSnapshot(
                        preciseGranted = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED,
                        approximateGranted = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED,
                        shouldExplain = ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION),
                    )
                },
                { activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
            )
    }
}
