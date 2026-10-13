package com.example.citizensecurity.account

import com.example.citizensecurity.domain.LocalAccount
import org.junit.Assert.*
import org.junit.Test

class LocalAccountSessionTest {
    @Test fun logoutClearsIdentityAndRepeatedLogoutIsSafe() {
        val session = LocalAccountSession()
        val account = LocalAccount("00000000-0000-0000-0000-000000000001", "alumna1")
        assertNull(session.account)
        session.signIn(account)
        assertSame(account, session.account)
        session.signOut()
        session.signOut()
        assertNull(session.account)
    }

    @Test fun newProcessSessionHasNoAccountAndCannotRestoreThePreviousOne() {
        val previous = LocalAccountSession()
        previous.signIn(LocalAccount("00000000-0000-0000-0000-000000000001", "alumna1"))
        assertNull(LocalAccountSession().account)
    }
}
