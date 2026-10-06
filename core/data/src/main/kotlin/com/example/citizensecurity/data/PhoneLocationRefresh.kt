package com.example.citizensecurity.data

import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.NearbyIncidentDecision
import com.example.citizensecurity.domain.NearbyIncidentPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/** Una lectura independiente del marcador. La implementación cancela su suscripción al salir. */
interface PhoneLocationSource {
    fun hasPrecisePermission(): Boolean
    fun isLocationEnabled(): Boolean
    suspend fun awaitFix(): DeviceLocationFix?
}

sealed interface PhoneLocationRefreshResult {
    data class Ready(val fix: DeviceLocationFix) : PhoneLocationRefreshResult
    data object PermissionRequired : PhoneLocationRefreshResult
    data object LocationDisabled : PhoneLocationRefreshResult
    data object Unavailable : PhoneLocationRefreshResult
    data object TimedOut : PhoneLocationRefreshResult
    data object AlreadyRunning : PhoneLocationRefreshResult
    data class Rejected(val rejection: NearbyIncidentDecision.Rejected) : PhoneLocationRefreshResult
}

/**
 * Solicitud explícita y acotada. No pide permisos, mueve el pin ni guarda reportes.
 * La pantalla debe cancelar la corrutina al abandonar el flujo o dejar de estar visible.
 */
class PhoneLocationRefresh(
    private val source: PhoneLocationSource,
    private val evidence: PhoneLocationEvidence,
    private val elapsedRealtimeNanos: () -> Long,
    private val policy: NearbyIncidentPolicy = NearbyIncidentPolicy(),
    val timeoutMillis: Long = 15_000L,
) {
    private val request = Mutex()

    init {
        require(timeoutMillis > 0L) { "El tiempo de espera debe ser positivo." }
    }

    suspend fun refresh(): PhoneLocationRefreshResult {
        currentCoroutineContext().ensureActive()
        if (!request.tryLock()) return PhoneLocationRefreshResult.AlreadyRunning
        try {
            // Una renovación fallida no conserva una autorización obtenida anteriormente.
            evidence.clear()
            if (!source.hasPrecisePermission()) return PhoneLocationRefreshResult.PermissionRequired
            if (!source.isLocationEnabled()) return PhoneLocationRefreshResult.LocationDisabled
            val result = withTimeoutOrNull(timeoutMillis) {
                val fix = source.awaitFix()
                currentCoroutineContext().ensureActive()
                if (!source.hasPrecisePermission()) {
                    return@withTimeoutOrNull PhoneLocationRefreshResult.PermissionRequired
                }
                if (!source.isLocationEnabled()) {
                    return@withTimeoutOrNull PhoneLocationRefreshResult.LocationDisabled
                }
                if (fix == null) return@withTimeoutOrNull PhoneLocationRefreshResult.Unavailable
                policy.checkDeviceLocation(fix, elapsedRealtimeNanos())?.let {
                    return@withTimeoutOrNull PhoneLocationRefreshResult.Rejected(it)
                }
                currentCoroutineContext().ensureActive()
                val published = evidence.updateFix(fix)
                currentCoroutineContext().ensureActive()
                if (!source.hasPrecisePermission()) {
                    evidence.clear()
                    return@withTimeoutOrNull PhoneLocationRefreshResult.PermissionRequired
                }
                if (!source.isLocationEnabled()) {
                    evidence.clear()
                    return@withTimeoutOrNull PhoneLocationRefreshResult.LocationDisabled
                }
                if (!published) {
                    // La disponibilidad pudo perderse y volver entre los lectores.
                    // No inventar una denegación actual ni entregar Ready sin evidencia.
                    evidence.clear()
                    return@withTimeoutOrNull PhoneLocationRefreshResult.Unavailable
                }
                // La comprobación del permiso al publicar puede coincidir con un cambio
                // de proveedor o consumir tiempo: Ready necesita evidencia aún vigente.
                policy.checkDeviceLocation(fix, elapsedRealtimeNanos())?.let {
                    evidence.clear()
                    return@withTimeoutOrNull PhoneLocationRefreshResult.Rejected(it)
                }
                currentCoroutineContext().ensureActive()
                PhoneLocationRefreshResult.Ready(fix)
            }
            if (result == null) {
                evidence.clear()
                return PhoneLocationRefreshResult.TimedOut
            }
            currentCoroutineContext().ensureActive()
            return result
        } catch (cancelled: CancellationException) {
            evidence.clear()
            throw cancelled
        } catch (_: SecurityException) {
            evidence.clear()
            return PhoneLocationRefreshResult.PermissionRequired
        } catch (_: Exception) {
            // Fallos del servicio, proveedor o callback no conceden una ubicación ni
            // deben escapar hacia la pantalla. La cancelación se conserva arriba.
            evidence.clear()
            return PhoneLocationRefreshResult.Unavailable
        } finally {
            request.unlock()
        }
    }
}
