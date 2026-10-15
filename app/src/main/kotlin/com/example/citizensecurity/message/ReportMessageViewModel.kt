package com.example.citizensecurity.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportMessageDraft
import com.example.citizensecurity.domain.ReportMessageFormatter
import com.example.citizensecurity.domain.ReportRepository
import com.example.citizensecurity.domain.TrustedContactsRepository
import com.example.citizensecurity.domain.TrustedContactsSnapshot
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReportMessageState {
    data object Idle : ReportMessageState
    class Loading(val reportId: String) : ReportMessageState
    class Choose(
        val report: Report,
        val contacts: TrustedContactsSnapshot,
        val selectedContactId: String? = null,
        val includeLocation: Boolean = false,
        val message: String? = null,
    ) : ReportMessageState
    class Preparing(val choice: Choose) : ReportMessageState
    class Review(
        val report: Report,
        val contacts: TrustedContactsSnapshot,
        val draft: ReportMessageDraft,
        val includeLocation: Boolean,
        val message: String? = null,
    ) : ReportMessageState
    class Opening(val review: Review) : ReportMessageState
    class Opened(val review: Review) : ReportMessageState
    class Error(val reportId: String, val message: String) : ReportMessageState
}

/** Read-only composition tied to one captured owner; opening always needs another live gesture. */
class ReportMessageViewModel(
    private val repository: ReportRepository,
    private val contacts: TrustedContactsRepository,
    private val checkCurrent: () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportMessageState>(ReportMessageState.Idle)
    val state: StateFlow<ReportMessageState> = mutableState.asStateFlow()
    private val generation = AtomicLong()
    private var pending: Job? = null

    /** Returning to or rotating the screen preserves the settled choice/review, never opens it. */
    fun activate(reportId: String) {
        if (mutableState.value === ReportMessageState.Idle) load(reportId)
    }

    fun load(reportId: String) {
        if (!canBegin()) return
        if (!canonicalId(reportId)) {
            mutableState.value = ReportMessageState.Error("", "Se necesita el folio completo del reporte.")
            return
        }
        begin(ReportMessageState.Loading(reportId), reportId) { checkpoint ->
            val reading = readFresh(reportId, checkpoint)
            ReportMessageState.Choose(reading.report, reading.contacts)
        }
    }

    fun selectContact(expected: ReportMessageState.Choose, contactId: String?) {
        if (!currentChoice(expected)) return
        if (contactId != null && expected.contacts.contacts.none { it.id == contactId }) return
        mutableState.value = ReportMessageState.Choose(expected.report, expected.contacts,
            contactId, expected.includeLocation)
    }

    fun setIncludeLocation(expected: ReportMessageState.Choose, includeLocation: Boolean) {
        if (!currentChoice(expected)) return
        mutableState.value = ReportMessageState.Choose(expected.report, expected.contacts,
            expected.selectedContactId, includeLocation)
    }

    fun prepare(expected: ReportMessageState.Choose) {
        if (!currentChoice(expected)) return
        val selected = expected.contacts.contacts.singleOrNull { it.id == expected.selectedContactId }
            ?: return
        begin(ReportMessageState.Preparing(expected), expected.report.id) { checkpoint ->
            val reading = readFresh(expected.report.id, checkpoint)
            val currentContact = reading.contacts.contacts.singleOrNull { it.id == selected.id }
            if (currentContact != selected) {
                ReportMessageState.Choose(reading.report, reading.contacts,
                    includeLocation = expected.includeLocation,
                    message = "El contacto cambió o ya no está disponible. Elige nuevamente con quién compartir.")
            } else {
                val draft = withContext(dispatcher) {
                    checkpoint()
                    ReportMessageFormatter.prepare(reading.report, currentContact,
                        expected.includeLocation, zoneId).also { checkpoint() }
                }
                ReportMessageState.Review(reading.report, reading.contacts, draft, expected.includeLocation)
            }
        }
    }

    /** Editing an opened composition still requires preparing and reviewing it again. */
    fun edit(expected: ReportMessageState.Review) {
        val shown = mutableState.value
        if (!canBegin() || shown !== expected &&
            !(shown is ReportMessageState.Opened && shown.review === expected)) return
        if (!hasAccess()) return
        mutableState.value = ReportMessageState.Choose(expected.report, expected.contacts,
            expected.draft.contact.id, expected.includeLocation)
    }

    /**
     * Reread both sources before handing private text to an external app. The activity must check
     * its own RESUMED lifecycle in canOpen: a callback from its pre-rotation instance cannot open.
     * launch runs on Main and only once for the exact visible review. It never sends a message.
     */
    fun open(expected: ReportMessageState.Review, canOpen: () -> Boolean, launch: (ReportMessageDraft) -> Unit) {
        if (!canBegin() || mutableState.value !== expected || !hasAccess()) return
        begin(ReportMessageState.Opening(expected), expected.report.id) { checkpoint ->
            val reading = readFresh(expected.report.id, checkpoint)
            val contact = reading.contacts.contacts.singleOrNull { it.id == expected.draft.contact.id }
            val unchanged = reading.report == expected.report && contact == expected.draft.contact &&
                reading.contacts.revision == expected.contacts.revision &&
                reading.contacts.contacts == expected.contacts.contacts
            if (!unchanged) {
                ReportMessageState.Choose(reading.report, reading.contacts,
                    selectedContactId = contact?.takeIf { it == expected.draft.contact }?.id,
                    includeLocation = expected.includeLocation,
                    message = "El reporte o los contactos cambiaron. Vuelve a preparar y revisar el mensaje.")
            } else {
                checkpoint()
                val allowed = canOpen()
                checkpoint()
                if (!allowed) {
                    reviewWithMessage(expected, "La pantalla dejó de estar activa. Revisa el mensaje antes de volver a abrirlo.")
                } else {
                    try {
                        // No suspension, storage access or application lookup after this final guard.
                        checkpoint()
                        launch(expected.draft)
                        ReportMessageState.Opened(expected)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        checkpoint()
                        reviewWithMessage(expected, "No se pudo abrir una aplicación de mensajes. Puedes revisar el texto e intentar nuevamente.")
                    }
                }
            }
        }
    }

    fun cancel() = clear()

    fun clear() {
        generation.incrementAndGet()
        pending?.cancel()
        pending = null
        mutableState.value = ReportMessageState.Idle
    }

    private fun currentChoice(expected: ReportMessageState.Choose): Boolean =
        canBegin() && mutableState.value === expected && hasAccess()

    private fun canBegin(): Boolean = viewModelScope.isActive && pending == null &&
        mutableState.value !is ReportMessageState.Loading &&
        mutableState.value !is ReportMessageState.Preparing && mutableState.value !is ReportMessageState.Opening

    private fun hasAccess(): Boolean = try {
        checkCurrent()
        true
    } catch (_: Exception) {
        clear()
        false
    }

    private suspend fun readFresh(reportId: String, checkpoint: () -> Unit): Reading = withContext(dispatcher) {
        checkpoint()
        val report = repository.findById(reportId) ?: throw IllegalStateException("Reporte no disponible.")
        checkpoint()
        require(report.id == reportId) { "El folio consultado no coincide." }
        val snapshot = contacts.read()
        checkpoint()
        // Revalidate the bounded immutable contract without trusting an injected repository.
        TrustedContactsSnapshot(snapshot.revision, snapshot.contacts)
        Reading(report, snapshot)
    }

    private fun begin(
        busy: ReportMessageState,
        reportId: String,
        operation: suspend (checkpoint: () -> Unit) -> ReportMessageState,
    ) {
        val scope = viewModelScope
        if (!scope.isActive) return
        val revision = generation.incrementAndGet()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val task = this
            val checkpoint = {
                task.ensureActive()
                if (generation.get() != revision) throw CancellationException("La composición dejó de estar vigente.")
                checkCurrent()
                task.ensureActive()
                if (generation.get() != revision) throw CancellationException("La composición dejó de estar vigente.")
            }
            try {
                checkpoint()
                val result = operation(checkpoint)
                checkpoint()
                mutableState.compareAndSet(busy, result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                task.ensureActive()
                if (generation.get() == revision) mutableState.compareAndSet(busy,
                    ReportMessageState.Error(reportId,
                        "No se pudo consultar el reporte y sus contactos. Vuelve a cargarlos antes de preparar el mensaje."))
            }
        }
        pending = job
        job.invokeOnCompletion { cause ->
            if (generation.get() == revision) {
                pending = null
                if (cause is CancellationException) mutableState.compareAndSet(busy, ReportMessageState.Idle)
            }
        }
        mutableState.value = busy
        if (generation.get() == revision && mutableState.value === busy) job.start() else job.cancel()
    }

    override fun onCleared() { clear() }

    private data class Reading(val report: Report, val contacts: TrustedContactsSnapshot)

    private fun reviewWithMessage(review: ReportMessageState.Review, message: String) =
        ReportMessageState.Review(review.report, review.contacts, review.draft, review.includeLocation, message)

    private fun canonicalId(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)

    companion object {
        fun factory(
            repository: ReportRepository,
            contacts: TrustedContactsRepository,
            checkCurrent: () -> Unit,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportMessageViewModel(repository, contacts, checkCurrent, dispatcher, zoneId) }
        }
    }
}
