package com.example.citizensecurity.help

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.TrustedContact
import com.example.citizensecurity.domain.TrustedContactsRepository
import com.example.citizensecurity.domain.TrustedContactsSnapshot
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

sealed interface HelpContactsState {
    data object Idle : HelpContactsState
    class Reading : HelpContactsState
    class Ready(val snapshot: TrustedContactsSnapshot, val validationMessage: String? = null) : HelpContactsState
    class Writing : HelpContactsState
    class ReadFailure(val requiresReload: Boolean) : HelpContactsState
    class WriteUncertain : HelpContactsState
}

/** Unsubmitted contact fields stay in this workspace's memory, never in saved instance state. */
data class HelpContactDraft(val name: String = "", val phoneNumber: String = "")

/** Reads are harmless; any uncertain mutation blocks other writes until a fresh successful read. */
class HelpContactsViewModel(
    private val repository: TrustedContactsRepository,
    private val checkAccess: () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<HelpContactsState>(HelpContactsState.Idle)
    val state: StateFlow<HelpContactsState> = mutableState.asStateFlow()
    private val mutableDraft = MutableStateFlow(HelpContactDraft())
    val draft: StateFlow<HelpContactDraft> = mutableDraft.asStateFlow()
    private var pending: Job? = null
    private var generation = 0L
    private var uncertain = false

    fun editName(value: String): Boolean {
        if (!canEdit() || value.length > 180) return false
        mutableDraft.value = mutableDraft.value.copy(name = value)
        return true
    }

    fun editPhoneNumber(value: String): Boolean {
        if (!canEdit() || value.length > 80) return false
        mutableDraft.value = mutableDraft.value.copy(phoneNumber = value)
        return true
    }

    fun load() {
        if (!viewModelScope.isActive || mutableState.value is HelpContactsState.Writing ||
            mutableState.value is HelpContactsState.Reading) return
        start(writing = false) {
            repository.read().also {
                TrustedContactsSnapshot(it.revision, it.contacts)
            }
        }
    }

    fun add(name: String, phoneNumber: String): Boolean {
        if (!canEdit()) return false
        val ready = mutableState.value as? HelpContactsState.Ready ?: return false
        if (uncertain) return false
        val next = try {
            ready.snapshot.contacts + TrustedContact.create(name, phoneNumber)
        } catch (_: IllegalArgumentException) {
            mutableState.value = HelpContactsState.Ready(ready.snapshot,
                "Revisa el nombre (1 a 60 caracteres) y el teléfono (10 a 15 dígitos, + opcional).")
            return false
        }
        try {
            TrustedContactsSnapshot.validateContacts(next)
        } catch (_: IllegalArgumentException) {
            mutableState.value = HelpContactsState.Ready(ready.snapshot,
                if (next.size > TrustedContact.MAX_CONTACTS) "Puedes conservar hasta 5 contactos."
                else "Ese teléfono ya está en tus contactos.")
            return false
        }
        return replace(ready, next).also { accepted ->
            if (accepted) clearDraft()
        }
    }

    /** The caller confirms deletion from the exact visible reading, never an old dialog. */
    fun remove(contactId: String, shownState: HelpContactsState.Ready): Boolean {
        if (mutableState.value !== shownState || uncertain) return false
        if (shownState.snapshot.contacts.none { it.id == contactId }) return false
        return replace(shownState, shownState.snapshot.contacts.filterNot { it.id == contactId })
    }

    fun cancel() {
        clearDraft()
        val writing = mutableState.value is HelpContactsState.Writing
        ++generation
        pending?.cancel()
        pending = null
        if (writing) uncertain = true
        mutableState.value = if (uncertain) HelpContactsState.WriteUncertain() else HelpContactsState.Idle
    }

    private fun canEdit(): Boolean {
        if (!viewModelScope.isActive) {
            clearDraft()
            return false
        }
        return try { checkAccess(); true } catch (_: Exception) {
            clearDraft()
            false
        }
    }

    private fun clearDraft() { mutableDraft.value = HelpContactDraft() }

    override fun onCleared() {
        clearDraft()
    }

    private fun replace(ready: HelpContactsState.Ready, contacts: List<TrustedContact>): Boolean {
        if (mutableState.value !== ready) return false
        return start(writing = true) {
            repository.replace(ready.snapshot.revision, contacts).also {
                require(it.revision != null && it.revision != ready.snapshot.revision && it.contacts == contacts)
            }
        }
    }

    private fun start(writing: Boolean, operation: suspend () -> TrustedContactsSnapshot): Boolean {
        if (!viewModelScope.isActive) return false
        try { checkAccess() } catch (_: Exception) { clearDraft(); return false }
        val request = ++generation
        pending?.cancel()
        val loading: HelpContactsState = if (writing) HelpContactsState.Writing() else HelpContactsState.Reading()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val snapshot = withContext(dispatcher) {
                    ensureActive(); checkAccess()
                    operation().also { ensureActive(); checkAccess() }
                }
                ensureActive(); checkAccess()
                if (generation == request) {
                    uncertain = false
                    mutableState.value = HelpContactsState.Ready(snapshot)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                if (generation == request) {
                    if (writing) uncertain = true
                    mutableState.value = if (writing) HelpContactsState.WriteUncertain()
                        else HelpContactsState.ReadFailure(uncertain)
                }
            }
        }
        pending = job
        job.invokeOnCompletion { cause ->
            if (generation == request) {
                pending = null
                if (cause is CancellationException && mutableState.value === loading) {
                    if (writing) uncertain = true
                    mutableState.value = if (uncertain) HelpContactsState.WriteUncertain() else HelpContactsState.Idle
                }
            }
        }
        mutableState.value = loading
        if (generation == request) job.start() else job.cancel()
        return true
    }

    companion object {
        fun factory(repository: TrustedContactsRepository, checkAccess: () -> Unit,
            dispatcher: CoroutineDispatcher = Dispatchers.IO): ViewModelProvider.Factory =
            viewModelFactory { initializer { HelpContactsViewModel(repository, checkAccess, dispatcher) } }
    }
}
