package com.example.citizensecurity.account

import com.example.citizensecurity.domain.LocalAccount

/** Process-only identity. This is not a role, an access token or report authorization. */
class LocalAccountSession {
    @Volatile var account: LocalAccount? = null
        private set

    fun signIn(account: LocalAccount) { this.account = account }
    fun signOut() { account = null }
}
