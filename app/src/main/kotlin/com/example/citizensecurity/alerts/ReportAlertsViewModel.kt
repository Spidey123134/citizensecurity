package com.example.citizensecurity.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.ReportActivityCursor
import com.example.citizensecurity.domain.ReportActivityFilter
import com.example.citizensecurity.domain.ReportActivityPage
import com.example.citizensecurity.domain.ReportActivityQuery
import com.example.citizensecurity.domain.ReportActivityRepository
import com.example.citizensecurity.domain.ReportAlertsRepository
import com.example.citizensecurity.domain.ReportAlertsSnapshot
import com.example.citizensecurity.domain.ReportStatusEventKind
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReportAlertsAction { LOAD, ENABLE, DISABLE, REVIEW }

sealed interface ReportAlertsState {
    data object Idle : ReportAlertsState
    data class Loading(val action: ReportAlertsAction) : ReportAlertsState
    data class Ready(
        val snapshot: ReportAlertsSnapshot,
        val availability: ReportAlertsAvailability,
        val message: String? = null,
        val canContinue: Boolean = false,
        val examined: Int = 0,
    ) : ReportAlertsState
    data class Error(val message: String, val needsReload: Boolean = true) : ReportAlertsState
}

/** Explicit foreground review only. A presentation receipt never means a user read the notice. */
class ReportAlertsViewModel(
    private val activity: ReportActivityRepository,
    private val preferences: ReportAlertsRepository,
    private val publisher: ReportAlertsPublisher,
    private val checkCurrent: () -> Unit = {},
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportAlertsState>(ReportAlertsState.Idle)
    val state = mutableState.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    @Volatile private var pending: Job? = null
    private var generation = 0L
    @Volatile private var closed = false
    private var continuation: ReportActivityCursor? = null

    /** Rotation keeps a settled reading; construction and repeated activation do no I/O. */
    fun activate(): Boolean = if (mutableState.value == ReportAlertsState.Idle) load() else false

    fun load(): Boolean {
        if (!canBegin(requireReady = false)) return false
        return run(ReportAlertsAction.LOAD) {
            val snapshot = readPreferences()
            Outcome(snapshot, availability(), "Estado local comprobado.")
        }
    }

    fun setEnabled(enabled: Boolean): Boolean {
        if (!canBegin()) return false
        return run(if (enabled) ReportAlertsAction.ENABLE else ReportAlertsAction.DISABLE) {
            val before = readPreferences()
            if (enabled) {
                val available = availability()
                if (available != ReportAlertsAvailability.ALLOWED) return@run blocked(before, available)
                guard()
                publisher.prepare()
                guard()
                val prepared = availability()
                if (prepared != ReportAlertsAvailability.ALLOWED) return@run blocked(before, prepared)
            }
            val after = if (before.enabled == enabled) before else {
                guard()
                preferences.setEnabled(enabled, before.revision).also {
                    guard()
                    validateWrite(before, it, enabled, before.presented)
                }
            }
            guard()
            if (!enabled) {
                publisher.cancel()
                guard()
            }
            val available = availability()
            if (enabled && available != ReportAlertsAvailability.ALLOWED) throw AvailabilityChanged()
            Outcome(after, available, if (enabled)
                "Preferencia activada. Revisa los cambios cuando quieras; no se revisan en segundo plano."
            else "Avisos desactivados. Se conserva el registro de intentos ya aceptados por Android.")
        }
    }

    /** A new review restarts from the head, including changes with an older recorded date. */
    fun review(): Boolean = startReview(from = null)

    fun continueReview(): Boolean {
        if (!canBegin()) return false
        val from = continuation ?: return false
        return startReview(from)
    }

    private fun startReview(from: ReportActivityCursor?): Boolean {
        if (!canBegin()) return false
        return run(ReportAlertsAction.REVIEW) { operation ->
            val before = readPreferences()
            val available = availability()
            if (!before.enabled) return@run Outcome(before, available, "Activa la preferencia antes de revisar avisos.")
            if (available != ReportAlertsAvailability.ALLOWED) return@run blocked(before, available)
            val newIds = linkedSetOf<String>()
            val scannedIds = hashSetOf<String>()
            var cursor = from
            var examined = 0
            var more = true
            while (more && examined < MAX_EXAMINED) {
                val query = ReportActivityQuery(ReportActivityFilter.STATUS_CHANGES, cursor, PAGE_SIZE)
                guard()
                val page = activity.readActivity(query)
                guard()
                validatePage(page, query)
                for (entry in page.entries) {
                    guard()
                    check(scannedIds.add(entry.event.operationId))
                    if (entry.event.operationId !in before.presented) newIds += entry.event.operationId
                }
                examined += page.entries.size
                more = page.hasMore
                cursor = page.nextBefore
                if (before.presented.size + newIds.size > ReportAlertsSnapshot.MAX_PRESENTED) throw CapacityReached()
            }
            // The delivery decision belongs to the exact preference revision seen at scan start.
            // A concurrent toggle/receipt is not silently overwritten or merged by this screen.
            val fresh = readPreferences()
            check(fresh == before)
            val freshAvailability = availability()
            if (freshAvailability != ReportAlertsAvailability.ALLOWED) return@run blocked(fresh, freshAvailability)
            var after = fresh
            if (newIds.isNotEmpty()) {
                guard()
                operation.mayHavePublished.set(true)
                val result = publisher.publish(::guard)
                guard()
                if (result != ReportAlertsPublishResult.ACCEPTED) {
                    return@run Outcome(fresh, availability(), "Android no aceptó el aviso. No se registró como presentado; puedes revisar de nuevo.")
                }
                guard()
                after = preferences.recordPresented(newIds.toSet(), fresh.revision)
                guard()
                validateWrite(fresh, after, enabled = true, presented = fresh.presented + newIds)
            }
            val finalAvailability = availability()
            Outcome(after, finalAvailability,
                if (newIds.isEmpty()) "No hay cambios nuevos para avisar en los eventos revisados."
                else "Android aceptó un aviso general. Esto no confirma que se haya visto o leído.",
                next = if (more) checkNotNull(cursor) else null, examined = examined)
        }
    }

    private suspend fun readPreferences(): ReportAlertsSnapshot {
        guard()
        return preferences.read().also { guard() }
    }

    private fun availability(): ReportAlertsAvailability {
        guard()
        return publisher.availability().also { guard() }
    }

    private fun validateWrite(before: ReportAlertsSnapshot, after: ReportAlertsSnapshot,
        enabled: Boolean, presented: Set<String>) {
        check(after.enabled == enabled && after.presented == presented)
        check(after.revision != null && after.revision != before.revision)
    }

    private fun validatePage(page: ReportActivityPage, query: ReportActivityQuery) {
        require(page.entries.size <= query.limit)
        if (page.hasMore) require(page.entries.size == query.limit)
        require(page.entries.all { it.event.kind == ReportStatusEventKind.STATUS_CHANGED })
        query.before?.let { before ->
            require(page.entries.all { it.event.recordedAt < before.recordedAt ||
                it.event.recordedAt == before.recordedAt && it.event.operationId < before.operationId })
        }
    }

    private fun blocked(snapshot: ReportAlertsSnapshot, available: ReportAlertsAvailability) = Outcome(snapshot, available,
        "Android bloquea los avisos o falta permiso. No se cambió la preferencia ni se registraron presentaciones.")

    private fun canBegin(requireReady: Boolean = true): Boolean {
        if (closed || !viewModelScope.isActive || pending != null) return false
        try { checkLease() } catch (_: AccessRevoked) { revoke(); return false }
        return !requireReady || mutableState.value is ReportAlertsState.Ready
    }

    private fun checkLease() {
        try { checkCurrent() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw AccessRevoked() }
    }

    private fun guard() {
        if (closed || !viewModelScope.isActive || pending?.isActive != true) throw CancellationException()
        checkLease()
        if (closed || !viewModelScope.isActive || pending?.isActive != true) throw CancellationException()
    }

    private fun run(action: ReportAlertsAction, operation: suspend (Operation) -> Outcome): Boolean {
        val request = ++generation
        val context = Operation()
        val loading = ReportAlertsState.Loading(action)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val outcome = withContext(dispatcher) { guard(); operation(context).also { guard() } }
                ensureActive()
                if (!closed && generation == request) {
                    checkLease()
                    continuation = outcome.next
                    mutableState.value = ReportAlertsState.Ready(outcome.snapshot, outcome.availability,
                        outcome.message, outcome.next != null, outcome.examined)
                }
            } catch (cancelled: CancellationException) {
                retireAttempt(context, cancelled)
                throw cancelled
            } catch (failure: AccessRevoked) {
                retireAttempt(context, failure)
                if (!closed && generation == request) revoke()
            } catch (failure: Exception) {
                retireAttempt(context, failure)
                ensureActive()
                if (!closed && generation == request) {
                    try {
                        checkLease()
                        continuation = null
                        mutableState.value = ReportAlertsState.Error(when {
                            failure is CapacityReached -> "Se alcanzó el límite local de cambios presentados. No se envió otro aviso ni se borraron recibos; carga el estado para revisarlo."
                            context.mayHavePublished.get() -> "El resultado del aviso o su registro es incierto. Carga el estado antes de intentar otra revisión."
                            failure is AvailabilityChanged -> "Android bloqueó los avisos al cambiar la preferencia. Carga el estado para comprobar lo que quedó guardado."
                            else -> "No se pudo completar la operación. Carga el estado antes de continuar; no se reemplazaron datos por una copia vacía."
                        })
                    } catch (_: AccessRevoked) { revoke() }
                }
            }
        }
        pending = job
        job.invokeOnCompletion { cause ->
            if (pending === job) {
                pending = null
                mutableBusy.value = false
                if (!closed && generation == request && cause is CancellationException) {
                    continuation = null
                    mutableState.compareAndSet(loading, ReportAlertsState.Error(
                        "La operación se interrumpió. Carga el estado antes de continuar."))
                }
            }
        }
        mutableBusy.value = true
        if (!closed && generation == request) mutableState.value = loading
        if (!closed && generation == request) job.start() else job.cancel()
        return true
    }

    /** A failed/abandoned review may retire only its own publication, never a newer summary.
     * Cancellation and revoked sessions still need cleanup; this API uses an opaque publisher
     * ticket and does not reread private data or mutate the presentation ledger.
     */
    private suspend fun retireAttempt(operation: Operation, failure: Throwable) {
        if (!operation.mayHavePublished.get()) return
        try {
            withContext(NonCancellable) { publisher.cancelAttempt() }
        } catch (cleanup: Throwable) {
            if (cleanup !== failure) failure.addSuppressed(cleanup)
        }
    }

    private fun revoke() {
        closed = true
        ++generation
        continuation = null
        pending?.cancel()
        pending = null
        mutableBusy.value = false
        mutableState.value = ReportAlertsState.Idle
    }

    override fun onCleared() = revoke()

    private class Operation { val mayHavePublished = AtomicBoolean() }
    private data class Outcome(val snapshot: ReportAlertsSnapshot, val availability: ReportAlertsAvailability,
        val message: String, val next: ReportActivityCursor? = null, val examined: Int = 0)
    private class AccessRevoked : IllegalStateException()
    private class AvailabilityChanged : IllegalStateException()
    private class CapacityReached : IllegalStateException()

    companion object {
        const val PAGE_SIZE = 50
        const val MAX_EXAMINED = 500
        fun factory(activity: ReportActivityRepository, preferences: ReportAlertsRepository,
            publisher: ReportAlertsPublisher, checkCurrent: () -> Unit = {},
            dispatcher: CoroutineDispatcher = Dispatchers.IO): ViewModelProvider.Factory = viewModelFactory {
                initializer { ReportAlertsViewModel(activity, preferences, publisher, checkCurrent, dispatcher) }
            }
    }
}
