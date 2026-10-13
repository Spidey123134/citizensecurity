package com.example.citizensecurity.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.AccountAlreadyExistsException
import com.example.citizensecurity.domain.AccountField
import com.example.citizensecurity.domain.AccountRepository
import com.example.citizensecurity.domain.AccountValidationException
import com.example.citizensecurity.domain.LocalAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AccountAccessMode { LOGIN, REGISTER }

sealed interface AccountAccessState {
    data object Idle : AccountAccessState
    data class Loading(val mode: AccountAccessMode) : AccountAccessState
    data class Invalid(val errors: Map<AccountField, String>) : AccountAccessState
    data object InvalidCredentials : AccountAccessState
    data object AlreadyExists : AccountAccessState
    data class Failed(val mode: AccountAccessMode) : AccountAccessState
    data class Interrupted(val mode: AccountAccessMode) : AccountAccessState
    data class Authenticated(val account: LocalAccount) : AccountAccessState
    data class Registered(val account: LocalAccount) : AccountAccessState
}

/** Credentials exist only in the active call, never in UI state or SavedStateHandle. */
class AccountAccessViewModel(private val repository: AccountRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<AccountAccessState>(AccountAccessState.Idle)
    val state: StateFlow<AccountAccessState> = mutableState.asStateFlow()
    private var activeJob: Job? = null
    private var generation = 0L

    /** Single flight: repeated gestures cannot create duplicate jobs or navigation events. */
    fun submit(mode: AccountAccessMode, username: String, password: String): Boolean {
        if (!viewModelScope.isActive || activeJob != null || state.value is AccountAccessState.Authenticated ||
            state.value is AccountAccessState.Registered) return false
        val expected = ++generation
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val result = try {
                when (mode) {
                    AccountAccessMode.REGISTER -> AccountAccessState.Registered(repository.register(username, password))
                    AccountAccessMode.LOGIN -> repository.authenticate(username, password)
                        ?.let { AccountAccessState.Authenticated(it) } ?: AccountAccessState.InvalidCredentials
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (invalid: AccountValidationException) {
                AccountAccessState.Invalid(invalid.errors.errors)
            } catch (_: AccountAlreadyExistsException) {
                AccountAccessState.AlreadyExists
            } catch (_: Exception) {
                // A failed/cancelled registration might already have committed. Do not claim otherwise.
                AccountAccessState.Failed(mode)
            }
            ensureActive()
            if (generation == expected) mutableState.value = result
        }
        activeJob = job
        job.invokeOnCompletion { cause ->
            if (generation == expected && activeJob === job) {
                activeJob = null
                if (cause is CancellationException) mutableState.value = AccountAccessState.Interrupted(mode)
            }
        }
        mutableState.value = AccountAccessState.Loading(mode)
        job.start()
        return true
    }

    /** Called when leaving/backgrounding the form, except for configuration recreation. */
    fun cancel() {
        val loading = mutableState.value as? AccountAccessState.Loading
        ++generation
        val previous = activeJob
        activeJob = null
        mutableState.value = loading?.let { AccountAccessState.Interrupted(it.mode) } ?: AccountAccessState.Idle
        previous?.cancel()
    }

    /** UI consumes success only while RESUMED; no session or navigation is created in background. */
    fun consumeSuccess(expected: AccountAccessState): Boolean {
        if (mutableState.value !== expected ||
            (expected !is AccountAccessState.Registered && expected !is AccountAccessState.Authenticated)) return false
        mutableState.value = AccountAccessState.Idle
        return true
    }

    companion object {
        fun factory(repository: AccountRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AccountAccessViewModel(repository) }
        }
    }
}
