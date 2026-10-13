package com.example.citizensecurity.account

import com.example.citizensecurity.domain.LocalAccount
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportWorkspaceStampTest {
    private val accountA = LocalAccount("00000000-0000-0000-0000-000000000001", "alumna1")
    private val accountB = LocalAccount("00000000-0000-0000-0000-000000000002", "alumna2")
    private val process = "10000000-0000-0000-0000-000000000001"
    private val laterProcess = "10000000-0000-0000-0000-000000000002"
    private fun session(account: LocalAccount? = accountA, processId: String = process, revision: Long = 4L) =
        AccountSessionState(account, processId, revision)

    @Test fun sameOwnerAndSessionRetainsRotationState() {
        val current = session()
        assertTrue(ReportWorkspaceStamp.from(current).matches(current))
    }

    @Test fun anotherAccountCannotRestoreOldScreen() {
        assertFalse(ReportWorkspaceStamp.from(session()).matches(session(accountB)))
    }

    @Test fun signingBackIntoSameAccountCannotReuseEarlierOpening() {
        assertFalse(ReportWorkspaceStamp.from(session()).matches(session(revision = 6L)))
    }

    @Test fun newAcademicOpeningCannotReuseEarlierAcademicSession() {
        assertFalse(ReportWorkspaceStamp.from(session(null)).matches(session(null, revision = 5L)))
    }

    @Test fun lostAccountAfterProcessDeathRejectsSavedAccountScreen() {
        assertFalse(ReportWorkspaceStamp.from(session()).matches(session(null, laterProcess, 0L)))
    }

    @Test fun accountStateRequiresSameLiveProcessEvenWithSameOwnerAndRevision() {
        assertFalse(ReportWorkspaceStamp.from(session()).matches(session(processId = laterProcess)))
    }

    @Test fun academicStateMayRestoreAcrossProcessesWhenBothOwnersAreAcademic() {
        assertTrue(ReportWorkspaceStamp.from(session(null)).matches(session(null, laterProcess, 0L)))
    }

    @Test fun academicStateCannotRestoreIntoAnAccountInAnotherProcess() {
        assertFalse(ReportWorkspaceStamp.from(session(null)).matches(session(processId = laterProcess)))
    }

    @Test fun negativeRevisionAndMalformedProcessStampAreRejected() {
        val current = session()
        assertFalse(ReportWorkspaceStamp(accountA.id, process, -1L).matches(current))
        assertFalse(ReportWorkspaceStamp(accountA.id, "invalid", 4L).matches(current))
    }

    @Test fun malformedOwnerCannotMatchCurrentAcademicWorkspace() {
        assertFalse(ReportWorkspaceStamp("", process, 4L).matches(session(null)))
    }
}
