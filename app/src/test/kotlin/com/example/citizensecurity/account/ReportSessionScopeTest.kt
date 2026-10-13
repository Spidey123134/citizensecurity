package com.example.citizensecurity.account

import com.example.citizensecurity.domain.LocalAccount
import org.junit.Assert.*
import org.junit.Test

class ReportSessionScopeTest {
    private val alice = LocalAccount("00000000-0000-0000-0000-000000000001", "alice_1")
    private val bob = LocalAccount("00000000-0000-0000-0000-000000000002", "bob_123")

    @Test fun signingInRevokesTheAcademicLeaseWithoutChangingItsCapturedOwner() {
        val session = LocalAccountSession()
        val academic = session.capture()
        session.signIn(alice)
        assertFalse(session.isCurrent(academic))
        assertNull(academic.account)
        assertSame(alice, session.capture().account)
    }

    @Test fun returningToTheSameAccountNeverReactivatesAnOldLease() {
        val session = LocalAccountSession()
        session.signIn(alice)
        val previous = session.capture()
        session.signOut()
        session.signIn(bob)
        session.signOut()
        session.signIn(alice)
        assertFalse(session.isCurrent(previous))
        assertEquals(alice, session.account)
        assertTrue(session.capture().revision > previous.revision)
        assertEquals(previous.processId, session.capture().processId)
    }

    @Test fun repeatedAcademicEntryAlsoRevokesThePreviousAcademicLease() {
        val session = LocalAccountSession()
        val previous = session.capture()
        session.signOut()
        assertFalse(session.isCurrent(previous))
        assertNull(session.account)
        assertTrue(session.isCurrent(session.capture()))
    }

    @Test fun equivalentCopiedSnapshotIsNotAnIssuedLease() {
        val session = LocalAccountSession()
        val issued = session.capture()
        assertTrue(session.isCurrent(issued))
        assertFalse(session.isCurrent(issued.copy()))
    }

    @Test fun restartedProcessHasADifferentStampAndNoRestoredIdentity() {
        val previous = LocalAccountSession()
        previous.signIn(alice)
        val next = LocalAccountSession()
        assertNotEquals(previous.capture().processId, next.capture().processId)
        assertNull(next.account)
        assertFalse(next.isCurrent(previous.capture()))
    }

    @Test fun repeatedSignInToTheSameAccountIsANewSessionNotAReusedLease() {
        val session = LocalAccountSession()
        session.signIn(alice)
        val previous = session.capture()
        session.signIn(alice)
        assertFalse(session.isCurrent(previous))
        assertEquals(previous.revision + 1, session.capture().revision)
    }

    @Test fun staleLogoutCannotEndTheNewAccountsSession() {
        val session = LocalAccountSession()
        session.signIn(alice)
        val old = session.capture()
        session.signIn(bob)
        assertFalse(session.signOutIfCurrent(old))
        assertEquals(bob, session.account)
    }

    @Test fun conditionalLogoutEndsOnlyItsIssuedCurrentSession() {
        val session = LocalAccountSession()
        session.signIn(alice)
        val current = session.capture()
        assertTrue(session.signOutIfCurrent(current))
        assertNull(session.account)
        assertFalse(session.signOutIfCurrent(current))
    }
}
