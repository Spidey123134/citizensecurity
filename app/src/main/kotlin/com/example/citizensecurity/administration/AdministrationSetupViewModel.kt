package com.example.citizensecurity.administration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.citizensecurity.domain.LocalAccount
import com.example.citizensecurity.domain.LocalAdministratorAssignment
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AdministrationSetupState {
    data object Idle : AdministrationSetupState
    data object Checking : AdministrationSetupState
    data class Available(val message: String? = null) : AdministrationSetupState
    data object AwaitingCredential : AdministrationSetupState
    data object OwnerConfirmed : AdministrationSetupState
    data object Assigning : AdministrationSetupState
    class Assigned(val assignment: LocalAdministratorAssignment) : AdministrationSetupState
    class Blocked(val message: String) : AdministrationSetupState
    class Error(val message: String) : AdministrationSetupState
}

/** First assignment requires a live native credential result and a second explicit gesture.
 * Proofs are process-only, expire monotonically, and cannot authorize another account/activity.
 */
class AdministrationSetupViewModel(
    private val account: LocalAccount?,
    private val checkCurrent: () -> Unit,
    private val read: () -> LocalAdministratorAssignment?,
    private val assign: (String, Instant, () -> Unit) -> LocalAdministratorAssignment,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.systemUTC(),
    private val elapsedNanos: () -> Long = System::nanoTime,
) : ViewModel() {
    private val mutableState = MutableStateFlow<AdministrationSetupState>(AdministrationSetupState.Idle)
    val state = mutableState.asStateFlow()
    private val generation = AtomicLong()
    @Volatile private var closed = false
    @Volatile private var verified: CredentialAttempt? = null
    private var launched: CredentialAttempt? = null
    private var pending: Job? = null
    private var readEmpty = false

    class CredentialAttempt internal constructor(val operationId: String, internal val started: Long)

    fun activate() { if (mutableState.value == AdministrationSetupState.Idle) reload() }

    fun reload(): Boolean {
        if (!canBegin() || launched != null) return false
        verified = null
        readEmpty = false
        if (account?.username != LocalAdministratorAssignment.USERNAME) {
            mutableState.value = AdministrationSetupState.Blocked(
                "Registra e inicia sesión con admin_local para configurar la administración de este teléfono.")
            return false
        }
        return begin(AdministrationSetupState.Checking) { token ->
            val saved = withContext(dispatcher) { ensureActive(); guard(token); read().also { guard(token); ensureActive() } }
            guard(token)
            mutableState.value = when {
                saved != null && saved.accountId == account?.id && saved.username == account?.username ->
                    AdministrationSetupState.Assigned(saved)
                saved != null -> AdministrationSetupState.Blocked("Este teléfono ya tiene una asignación administrativa. No se ha reemplazado.")
                account?.username != LocalAdministratorAssignment.USERNAME -> AdministrationSetupState.Blocked(
                    "Registra e inicia sesión con admin_local para configurar la administración de este teléfono.")
                else -> { readEmpty = true; AdministrationSetupState.Available() }
            }
        }
    }

    /** Called only by the visible credential button. A previous native result keeps its slot. */
    fun beginCredential(deviceSecure: Boolean): CredentialAttempt? {
        if (!canBegin() || launched != null || !readEmpty || mutableState.value !is AdministrationSetupState.Available) return null
        try { checkCurrent() } catch (_: Exception) { revoke(); return null }
        if (account?.username != LocalAdministratorAssignment.USERNAME) return null
        if (!deviceSecure) {
            mutableState.value = AdministrationSetupState.Available(
                "Configura un PIN, patrón o contraseña de bloqueo en Android y vuelve a comprobar.")
            return null
        }
        val attempt = CredentialAttempt(UUID.randomUUID().toString(), elapsedNanos())
        launched = attempt
        verified = null
        mutableState.value = AdministrationSetupState.AwaitingCredential
        return attempt
    }

    fun launchFailed(attempt: CredentialAttempt) {
        if (launched !== attempt) return
        launched = null
        verified = null
        mutableState.value = AdministrationSetupState.Available("No se pudo abrir la confirmación de Android. Vuelve a intentarlo.")
    }

    /** No assignment here: returning from Android never grants anything automatically. */
    fun credentialResult(accepted: Boolean, deviceSecure: Boolean): Boolean {
        val attempt = launched ?: return false
        launched = null
        if (closed || !viewModelScope.isActive || mutableState.value != AdministrationSetupState.AwaitingCredential) return false
        try { checkCurrent() } catch (_: Exception) { revoke(); return false }
        if (!accepted || !deviceSecure || !fresh(attempt)) {
            verified = null
            mutableState.value = AdministrationSetupState.Available("La verificación no se completó. Puedes intentarlo de nuevo.")
            return false
        }
        verified = attempt
        mutableState.value = AdministrationSetupState.OwnerConfirmed
        return true
    }

    /** Separate visible gesture; final checks also run inside the atomic store before commit. */
    fun activateAdministrator(canApply: () -> Boolean): Boolean {
        if (!canBegin() || mutableState.value != AdministrationSetupState.OwnerConfirmed) return false
        val proof = verified ?: return false
        try { checkCurrent() } catch (_: Exception) { revoke(); return false }
        if (!fresh(proof) || !canApply()) {
            verified = null
            mutableState.value = AdministrationSetupState.Available("Confirma de nuevo al propietario antes de activar los permisos.")
            return false
        }
        readEmpty = false
        val grantedAt = clock.instant()
        return begin(AdministrationSetupState.Assigning) { token ->
            val saved = withContext(dispatcher) {
                val checkpoint = {
                    ensureActive(); guard(token)
                    check(verified === proof && fresh(proof) && canApply())
                    ensureActive()
                }
                checkpoint()
                assign(proof.operationId, grantedAt, checkpoint).also { checkpoint() }
            }
            guard(token)
            check(saved.accountId == account?.id && saved.username == account?.username &&
                saved.operationId == proof.operationId && saved.grantedAt == grantedAt)
            verified = null
            mutableState.value = AdministrationSetupState.Assigned(saved)
        }
    }

    fun close() {
        closed = true
        generation.incrementAndGet()
        verified = null
        launched = null
        readEmpty = false
        pending?.cancel()
        mutableState.value = AdministrationSetupState.Blocked("Este acceso terminó. Inicia sesión de nuevo.")
    }

    private fun fresh(attempt: CredentialAttempt): Boolean {
        val now = elapsedNanos()
        return now >= attempt.started && now - attempt.started <= PROOF_LIFETIME_NANOS
    }

    private fun canBegin() = !closed && viewModelScope.isActive && pending == null
    private fun guard(token: Long) { check(!closed && generation.get() == token); checkCurrent() }
    private fun revoke() { close() }

    private fun begin(initial: AdministrationSetupState, work: suspend (Long) -> Unit): Boolean {
        if (!canBegin()) return false
        val token = generation.incrementAndGet()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try { work(token) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                ensureActive()
                if (!closed && generation.get() == token) {
                    verified = null
                    readEmpty = false
                    mutableState.value = AdministrationSetupState.Error(
                        "No se pudo confirmar la asignación. Comprueba su resultado antes de intentar otra activación.")
                }
            }
        }
        pending = job
        job.invokeOnCompletion { if (pending === job) pending = null }
        mutableState.value = initial
        job.start()
        return true
    }

    override fun onCleared() { close() }
    companion object { const val PROOF_LIFETIME_NANOS = 120_000_000_000L }
}
