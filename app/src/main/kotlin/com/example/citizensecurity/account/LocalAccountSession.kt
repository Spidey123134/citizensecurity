package com.example.citizensecurity.account

import com.example.citizensecurity.domain.LocalAccount
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owner/task stamp only; never a credential or an administrative permission. */
data class AccountSessionState(val account: LocalAccount?, val processId: String, val revision: Long)

/** Process-only identity. Every transition revokes old leases, including A -> logout -> A. */
class LocalAccountSession {
    private val mutableState = MutableStateFlow(AccountSessionState(null, UUID.randomUUID().toString(), 0L))
    val state: StateFlow<AccountSessionState> = mutableState.asStateFlow()
    val account: LocalAccount? get() = mutableState.value.account

    fun capture(): AccountSessionState = mutableState.value
    fun isCurrent(expected: AccountSessionState): Boolean = mutableState.value === expected

    @Synchronized fun signIn(account: LocalAccount) { transition(account) }
    @Synchronized fun signOut() { transition(null) }

    @Synchronized fun signOutIfCurrent(expected: AccountSessionState): Boolean {
        if (!isCurrent(expected)) return false
        transition(null)
        return true
    }

    private fun transition(account: LocalAccount?) {
        val previous = mutableState.value
        mutableState.value = AccountSessionState(account, previous.processId, Math.incrementExact(previous.revision))
    }
}
