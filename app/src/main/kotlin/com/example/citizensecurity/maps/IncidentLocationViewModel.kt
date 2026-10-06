package com.example.citizensecurity.maps

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.data.PhoneLocationRefreshResult
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.NearbyIncidentDecision
import com.example.citizensecurity.domain.NearbyIncidentPolicy
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.ReportLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface IncidentLocationIssue {
    val message: String

    data object PermissionRequired : IncidentLocationIssue {
        override val message = "Permite la ubicación precisa para comprobar la cercanía del incidente."
    }
    data object LocationDisabled : IncidentLocationIssue {
        override val message = "Activa la ubicación del teléfono y vuelve a intentarlo."
    }
    data object Unavailable : IncidentLocationIssue {
        override val message = "No se pudo obtener tu ubicación. Inténtalo de nuevo."
    }
    data object TimedOut : IncidentLocationIssue {
        override val message = "La ubicación tardó demasiado. Inténtalo de nuevo."
    }
    data object CaptureAlreadyRunning : IncidentLocationIssue {
        override val message = "Hay otra solicitud de ubicación en curso. Espera antes de reintentar."
    }
    data object RefreshRequired : IncidentLocationIssue {
        override val message = "Actualiza la ubicación del teléfono antes de confirmar el punto."
    }
    data object NoSelection : IncidentLocationIssue {
        override val message = "Selecciona en el mapa el punto del incidente."
    }
    data class InvalidPoint(override val message: String) : IncidentLocationIssue
    data class Blocked(val rejection: NearbyIncidentDecision.Rejected) : IncidentLocationIssue {
        override val message = rejection.message
    }
}

sealed interface IncidentLocationState {
    val proposal: LocationProposal

    /** Tener GPS listo no selecciona ni confirma un incidente. */
    data class Idle(val deviceFix: DeviceLocationFix? = null) : IncidentLocationState {
        override val proposal: LocationProposal = LocationProposal.None
    }
    data class Capturing(override val proposal: LocationProposal) : IncidentLocationState
    data class PointSelected(
        val point: LocationProposal.ValidPoint,
        val deviceFix: DeviceLocationFix? = null,
    ) : IncidentLocationState {
        override val proposal: LocationProposal = point
    }
    data class Error(
        val issue: IncidentLocationIssue,
        override val proposal: LocationProposal,
    ) : IncidentLocationState
    data class Confirmed(val location: ReportLocation, val distanceMeters: Double) : IncidentLocationState {
        override val proposal = LocationProposal.ValidPoint(
            requireNotNull(location.latitude), requireNotNull(location.longitude),
        )
    }
    data class Cancelled(val location: ReportLocation) : IncidentLocationState {
        override val proposal: LocationProposal = LocationProposal.None
    }
}

/**
 * Coordina la selección provisional y una lectura explícita del teléfono, sin vistas ni reportes.
 * La pantalla serializa sus eventos en el hilo principal. Envía cada toque a [select], observa
 * [state], llama [onStop] cuando deja de estar visible y [cancel] al abandonar el flujo.
 * No uses a la vez otro IncidentMapSession para la misma selección: este modelo posee la sesión.
 * Rotar puede conservar el modelo; volver de onStop requiere renovar GPS antes de confirmar.
 * Una confirmación únicamente entrega coordenadas: el guardado mantiene su comprobación propia.
 */
@MainThread
class IncidentLocationViewModel(
    originalLocation: ReportLocation,
    private val refreshPhoneLocation: suspend () -> PhoneLocationRefreshResult,
    private val currentDeviceFix: () -> DeviceLocationFix?,
    private val elapsedRealtimeNanos: () -> Long,
    private val policy: NearbyIncidentPolicy = NearbyIncidentPolicy(),
) : ViewModel() {
    private var session = IncidentMapSession(originalLocation, currentDeviceFix, elapsedRealtimeNanos, policy)
    private val mutableState = MutableStateFlow<IncidentLocationState>(IncidentLocationState.Idle())
    val state: StateFlow<IncidentLocationState> = mutableState.asStateFlow()

    private var proposal: LocationProposal = LocationProposal.None
    private var activeRequest: Any? = null
    private var captureJob: Job? = null
    private var refreshRequired = false

    /**
     * Abre otra selección explícita con este mismo modelo y registro de permisos de la Activity.
     * El dueño cierra primero el MapBinding anterior y cambia el token de su borrador por apertura.
     * Descarta el pin y la captura anteriores; exige una lectura nueva sin solicitarla automáticamente.
     */
    fun beginSelection(originalLocation: ReportLocation): Boolean {
        if (!viewModelScope.isActive) return false
        stopCapture()
        session.cancel()
        session = IncidentMapSession(originalLocation, currentDeviceFix, elapsedRealtimeNanos, policy)
        proposal = LocationProposal.None
        refreshRequired = true
        mutableState.value = IncidentLocationState.Idle()
        return true
    }

    fun select(latitude: Double, longitude: Double) {
        if (session.isClosed || !viewModelScope.isActive) return
        proposal = session.select(latitude, longitude) ?: return
        if (activeRequest != null) {
            mutableState.value = IncidentLocationState.Capturing(proposal)
        } else {
            showProposal()
        }
    }

    fun refreshLocation() {
        if (session.isClosed || !viewModelScope.isActive || activeRequest != null) return
        val request = Any()
        activeRequest = request
        refreshRequired = true
        mutableState.value = IncidentLocationState.Capturing(proposal)
        captureJob = viewModelScope.launch {
            try {
                val result = refreshPhoneLocation()
                ensureActive()
                if (activeRequest !== request || session.isClosed) return@launch
                when (result) {
                    is PhoneLocationRefreshResult.Ready -> {
                        // Ready es un aviso, no una autorización: vuelve a leer evidencia actual.
                        val fix = currentDeviceFix()
                        val rejection = policy.checkDeviceLocation(fix, elapsedRealtimeNanos())
                        ensureActive()
                        if (activeRequest !== request || session.isClosed) return@launch
                        if (rejection != null) showError(IncidentLocationIssue.Blocked(rejection))
                        else {
                            refreshRequired = false
                            showProposal(fix)
                        }
                    }
                    PhoneLocationRefreshResult.PermissionRequired -> showError(IncidentLocationIssue.PermissionRequired)
                    PhoneLocationRefreshResult.LocationDisabled -> showError(IncidentLocationIssue.LocationDisabled)
                    PhoneLocationRefreshResult.Unavailable -> showError(IncidentLocationIssue.Unavailable)
                    PhoneLocationRefreshResult.TimedOut -> showError(IncidentLocationIssue.TimedOut)
                    PhoneLocationRefreshResult.AlreadyRunning -> showError(IncidentLocationIssue.CaptureAlreadyRunning)
                    is PhoneLocationRefreshResult.Rejected -> showError(IncidentLocationIssue.Blocked(result.rejection))
                }
            } catch (cancelled: CancellationException) {
                if (activeRequest === request && !session.isClosed) showError(IncidentLocationIssue.RefreshRequired)
                throw cancelled
            } catch (_: SecurityException) {
                ensureActive()
                if (activeRequest === request && !session.isClosed) showError(IncidentLocationIssue.PermissionRequired)
            } catch (_: Exception) {
                ensureActive()
                if (activeRequest === request && !session.isClosed) showError(IncidentLocationIssue.Unavailable)
            } finally {
                if (activeRequest === request) activeRequest = null
            }
        }.also { job ->
            job.invokeOnCompletion { cause ->
                // También cubre cancelar antes de que la corrutina alcance a empezar.
                if (cause is CancellationException && activeRequest === request) {
                    activeRequest = null
                    if (!session.isClosed) showError(IncidentLocationIssue.RefreshRequired)
                }
            }
        }
    }

    /** null significa captura en curso, renovación pendiente o sesión ya terminada. */
    fun confirm(): NearbyLocationConfirmation? {
        if (session.isClosed || !viewModelScope.isActive || activeRequest != null) return null
        if (refreshRequired) {
            showError(IncidentLocationIssue.RefreshRequired)
            return null
        }
        val result = try {
            session.confirm()
        } catch (_: SecurityException) {
            refreshRequired = true
            showError(IncidentLocationIssue.PermissionRequired)
            return null
        } catch (_: Exception) {
            refreshRequired = true
            showError(IncidentLocationIssue.Unavailable)
            return null
        }
        when (result) {
            NearbyLocationConfirmation.NoSelection -> showError(IncidentLocationIssue.NoSelection)
            is NearbyLocationConfirmation.InvalidCoordinates -> showError(IncidentLocationIssue.InvalidPoint(result.message))
            is NearbyLocationConfirmation.Blocked -> showError(IncidentLocationIssue.Blocked(result.rejection))
            is NearbyLocationConfirmation.Confirmed -> mutableState.value =
                IncidentLocationState.Confirmed(result.location, result.distanceMeters)
        }
        return result
    }

    /** Detiene la captura; conserva el pin y nunca aplica coordenadas al borrador. */
    fun onStop() {
        if (session.isClosed) return
        stopCapture()
        refreshRequired = true
        showProposal()
    }

    /** Cierra el flujo. Un cierre posterior a confirmar conserva la ubicación aceptada. */
    fun cancel(): ReportLocation {
        stopCapture()
        val location = session.cancel()
        if (mutableState.value !is IncidentLocationState.Confirmed) {
            mutableState.value = IncidentLocationState.Cancelled(location)
        }
        return location
    }

    override fun onCleared() {
        cancel()
    }

    private fun stopCapture() {
        activeRequest = null
        captureJob?.cancel()
        captureJob = null
    }

    private fun showProposal(fix: DeviceLocationFix? = null) {
        mutableState.value = when (val point = proposal) {
            LocationProposal.None -> IncidentLocationState.Idle(fix)
            is LocationProposal.ValidPoint -> IncidentLocationState.PointSelected(point, fix)
            is LocationProposal.InvalidCoordinates -> IncidentLocationState.Error(
                IncidentLocationIssue.InvalidPoint(point.message), point,
            )
        }
    }

    private fun showError(issue: IncidentLocationIssue) {
        mutableState.value = IncidentLocationState.Error(issue, proposal)
    }

    companion object {
        fun factory(
            originalLocation: ReportLocation,
            refreshPhoneLocation: suspend () -> PhoneLocationRefreshResult,
            currentDeviceFix: () -> DeviceLocationFix?,
            elapsedRealtimeNanos: () -> Long,
            policy: NearbyIncidentPolicy = NearbyIncidentPolicy(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                IncidentLocationViewModel(
                    originalLocation, refreshPhoneLocation, currentDeviceFix, elapsedRealtimeNanos, policy,
                )
            }
        }
    }
}
