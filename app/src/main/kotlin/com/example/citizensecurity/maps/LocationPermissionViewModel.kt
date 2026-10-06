package com.example.citizensecurity.maps

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Hechos actuales del sistema; una respuesta antigua del diálogo no sustituye esta lectura. */
data class LocationPermissionSnapshot(
    val preciseGranted: Boolean,
    val approximateGranted: Boolean,
    val shouldExplain: Boolean,
)

enum class LocationPermissionAccess { Unknown, Denied, Approximate, Precise }

enum class LocationPermissionIssue {
    ExplanationRequired, Cancelled, PermissionCheckFailed, RequestLaunchFailed,
}

data class LocationPermissionState(
    val access: LocationPermissionAccess = LocationPermissionAccess.Unknown,
    val shouldExplain: Boolean = false,
    val requestInFlight: Boolean = false,
    val issue: LocationPermissionIssue? = null,
)

enum class LocationPermissionAction {
    LaunchRequest, AlreadyGranted, ExplanationRequired, AlreadyRunning,
    Inactive, NotVisible, Unavailable,
}

/**
 * Coordina una petición explícita de permiso, sin Activity, GPS, mapa ni guardado.
 * Se conserva con ViewModelProvider al recrear la vista para evitar solicitudes duplicadas.
 * Un proceso nuevo vuelve a leer permisos; recibir un resultado restaurado no lanza otra petición.
 * El binding registra el contrato y la pantalla decide cómo explicar o representar cada estado.
 * shouldExplain=false no acredita una denegación permanente ni autoriza abrir Ajustes.
 */
@MainThread
class LocationPermissionViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(LocationPermissionState())
    val state: StateFlow<LocationPermissionState> = mutableState.asStateFlow()
    private var active = true
    val isActive: Boolean get() = active

    /** Una comprobación exitosa sustituye errores previos; no cancela una petición pendiente. */
    fun inspect(snapshot: LocationPermissionSnapshot) {
        if (!active) return
        mutableState.value = mutableState.value.copy(
            access = snapshot.access(),
            shouldExplain = snapshot.shouldExplain,
            issue = null,
        )
    }

    /** La pantalla llama esto por gesto explícito; solo LaunchRequest permite abrir el diálogo. */
    fun prepareRequest(
        snapshot: LocationPermissionSnapshot,
        explanationAccepted: Boolean = false,
    ): LocationPermissionAction {
        if (!active) return LocationPermissionAction.Inactive
        inspect(snapshot)
        if (mutableState.value.requestInFlight) return LocationPermissionAction.AlreadyRunning
        if (snapshot.preciseGranted) return LocationPermissionAction.AlreadyGranted
        if (snapshot.shouldExplain && !explanationAccepted) {
            mutableState.value = mutableState.value.copy(issue = LocationPermissionIssue.ExplanationRequired)
            return LocationPermissionAction.ExplanationRequired
        }
        mutableState.value = mutableState.value.copy(requestInFlight = true)
        return LocationPermissionAction.LaunchRequest
    }

    /** Admite también el callback que ActivityResultRegistry entrega tras recrear el proceso. */
    fun onPermissionResult(snapshot: LocationPermissionSnapshot, cancelled: Boolean) {
        if (!active) return
        mutableState.value = LocationPermissionState(
            access = snapshot.access(),
            shouldExplain = snapshot.shouldExplain,
            issue = if (cancelled && !snapshot.preciseGranted) LocationPermissionIssue.Cancelled else null,
        )
    }

    /**
     * Sin una comprobación fiable no se acredita acceso. Un fallo durante la espera conserva
     * pending; el callback final lo libera incluso si no pudo volver a leer el permiso.
     */
    fun onPermissionCheckFailed(requestFinished: Boolean = false) {
        if (!active) return
        mutableState.value = LocationPermissionState(
            requestInFlight = !requestFinished && mutableState.value.requestInFlight,
            issue = LocationPermissionIssue.PermissionCheckFailed,
        )
    }

    fun onRequestLaunchFailed() {
        if (!active) return
        mutableState.value = mutableState.value.copy(
            requestInFlight = false,
            issue = LocationPermissionIssue.RequestLaunchFailed,
        )
    }

    override fun onCleared() {
        active = false
        // Termina nuestra coordinación, sin afirmar que se cerró el diálogo del sistema.
        mutableState.value = mutableState.value.copy(requestInFlight = false)
    }

    private fun LocationPermissionSnapshot.access(): LocationPermissionAccess = when {
        preciseGranted -> LocationPermissionAccess.Precise
        approximateGranted -> LocationPermissionAccess.Approximate
        else -> LocationPermissionAccess.Denied
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { LocationPermissionViewModel() }
        }
    }
}
