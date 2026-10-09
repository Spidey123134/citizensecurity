package com.example.citizensecurity.report

import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.maps.MapLibreMapClickHost
import com.example.citizensecurity.maps.IncidentLocationFlowBinding
import com.example.citizensecurity.maps.IncidentMapClickHost
import com.example.citizensecurity.maps.LocationPermissionAction
import org.maplibre.android.maps.MapLibreMap
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Un único contrato para la ida y vuelta entre borrador y ubicación, sin UI ni repositorio.
 * Crear una vez en onCreate, antes de STARTED, después del flujo de la misma Activity.
 * Retener los mismos modelos, nunca este puente o el mapa, al recrear la Activity.
 * [currentSelection] recupera la apertura retenida sin renovar GPS ni cambiar el pin.
 *
 * Los controles conservan el token de su apertura y lo entregan en cada gesto. El puente lo
 * comprueba antes de tocar el flujo: una confirmación, cancelación o vista antigua no afecta
 * una selección posterior. Confirmar usa la evidencia actual del flujo y aplica únicamente
 * las coordenadas; la referencia y los campos editados entretanto permanecen en el borrador.
 * Cerrar por ciclo de vida detiene la captura y conserva la apertura retenida.
 */
@MainThread
class ReportLocationFlowBinding(
    owner: LifecycleOwner,
    private val flow: IncidentLocationFlowBinding,
    private val draft: ReportDraftViewModel,
) : AutoCloseable {
    private val lifecycle = owner.lifecycle
    private var closed = false
    private var activeMap: ReportLocationMapBinding? = null
    private val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) close()
    }

    init {
        check(lifecycle.currentState != Lifecycle.State.DESTROYED &&
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            "Registra el puente del borrador antes de STARTED, una vez en onCreate."
        }
        check(flow.isOpen && flow.belongsTo(owner)) {
            "El flujo y el borrador deben pertenecer a la misma Activity vigente."
        }
        check(activeBindings[lifecycle]?.get() == null) {
            "El dueño ya tiene un puente de borrador y ubicación activo."
        }
        activeBindings[lifecycle] = WeakReference(this)
        lifecycle.addObserver(observer)
    }

    val currentSelection: ReportLocationSelectionRequest?
        get() = if (closed || !flow.isOpen) null else draft.currentLocationSelection()

    /** Gesto explícito: una pantalla inactiva no invalida el token ni altera la selección. */
    fun openSelection(): ReportLocationSelectionRequest? {
        if (closed || !flow.isInteractive) return null
        closeActiveMap()
        val request = draft.beginLocationSelection() ?: return null
        if (flow.beginSelection(request.originalLocation)) return request
        draft.cancelLocationSelection(request.token)
        return null
    }

    fun select(token: ReportLocationSelectionToken, latitude: Double, longitude: Double): Boolean =
        isCurrent(token) && flow.select(latitude, longitude)

    fun refreshLocation(token: ReportLocationSelectionToken): Boolean =
        isCurrent(token) && flow.refreshLocation()

    fun requestPermission(
        token: ReportLocationSelectionToken,
        explanationAccepted: Boolean = false,
    ): LocationPermissionAction = if (isCurrent(token)) flow.requestPermission(explanationAccepted)
        else LocationPermissionAction.Inactive

    /** Un resultado bloqueado conserva la apertura; un resultado cercano se aplica una sola vez. */
    fun confirm(token: ReportLocationSelectionToken): NearbyLocationConfirmation? {
        if (!isCurrent(token)) return null
        val result = flow.confirm()
        if (result is NearbyLocationConfirmation.Confirmed && !draft.applyLocationSelection(token, result)) return null
        return result
    }

    fun cancelSelection(token: ReportLocationSelectionToken): Boolean {
        if (!isCurrent(token) || !flow.cancelSelection()) return false
        draft.cancelLocationSelection(token)
        closeActiveMap()
        return true
    }

    /** Para un mapa ya obtenido; getMapAsync debe conservar el handle de [prepareMap]. */
    fun attachMap(token: ReportLocationSelectionToken, map: MapLibreMap): ReportLocationMapBinding? =
        attachMap(token, MapLibreMapClickHost(map))

    internal fun attachMap(token: ReportLocationSelectionToken, clicks: IncidentMapClickHost): ReportLocationMapBinding? {
        val binding = prepareMap(token) ?: return null
        if (binding.attachMap(clicks)) return binding
        binding.close()
        return null
    }

    /**
     * Reservar antes de pedir getMapAsync identifica esta vista, incluso dentro de una apertura.
     * El mapa puede llegar en CREATED/STARTED; adjuntarlo no entrega un gesto ni inicia GPS.
     */
    fun prepareMap(token: ReportLocationSelectionToken): ReportLocationMapBinding? {
        if (!isCurrent(token)) return null
        closeActiveMap()
        if (!isCurrent(token)) return null
        lateinit var binding: ReportLocationMapBinding
        binding = ReportLocationMapBinding(
            canAttach = { activeMap === binding && isCurrent(token) },
            onClick = { latitude, longitude ->
                activeMap === binding && select(token, latitude, longitude)
            },
            onClose = { closedMap ->
                if (activeMap === closedMap) {
                    activeMap = null
                    // Reset/cancel directo pudo retirar el token mientras esta vista capturaba.
                    // Sin una apertura nueva, retirar la vista también debe detener su GPS.
                    if (isCurrent(token) || (flow.isOpen && draft.currentLocationSelection() == null)) {
                        flow.onMapViewClosed()
                    }
                }
            },
        )
        activeMap = binding
        return binding
    }

    override fun close() {
        if (closed) return
        closed = true
        lifecycle.removeObserver(observer)
        if (activeBindings[lifecycle]?.get() === this) activeBindings.remove(lifecycle)
        try {
            closeActiveMap()
        } finally {
            flow.onMapViewClosed()
        }
    }

    private fun isCurrent(token: ReportLocationSelectionToken): Boolean =
        !closed && flow.isOpen && draft.isCurrentLocationSelection(token)

    private fun closeActiveMap() { activeMap?.close() }

    companion object {
        private val activeBindings = WeakHashMap<Lifecycle, WeakReference<ReportLocationFlowBinding>>()
    }
}
