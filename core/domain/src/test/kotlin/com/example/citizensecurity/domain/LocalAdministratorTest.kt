package com.example.citizensecurity.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class LocalAdministratorTest {
    private val owner = id(1)
    private val assignment = LocalAdministratorAssignment(owner, "admin_local", id(2), Instant.parse("2026-10-15T12:00:00Z"))

    @Test fun exactOwnerAndActorAreRequiredAndAcademicNeverMatches() {
        assertTrue(assignment.allows(ReportStatusChangeScope(owner, owner)))
        listOf(ReportStatusChangeScope(null, owner), ReportStatusChangeScope(id(3), owner),
            ReportStatusChangeScope(owner, id(3)), ReportStatusChangeScope(id(3), id(3)))
            .forEach { assertFalse(assignment.allows(it)) }
    }

    @Test fun usernameCannotStandInForTheChosenAccount() {
        listOf("admin", "ADMIN_LOCAL", "admin_local ", "another", "").forEach { name ->
            reject { assignment.copy(username = name) }
        }
    }

    @Test fun accountAndOperationMustBeCompleteCanonicalUuids() {
        listOf("", "1-1-1-1-1", owner.uppercase(), "../$owner").forEach { malformed ->
            reject { assignment.copy(accountId = malformed) }
            reject { assignment.copy(operationId = malformed) }
        }
    }

    @Test fun unsupportedDatesAreRejectedInsteadOfOverflowingOrBecomingAnotherTime() {
        listOf(Instant.MIN, Instant.EPOCH.minusNanos(1), Instant.parse("+10000-01-01T00:00:00Z"), Instant.MAX)
            .forEach { time -> reject { assignment.copy(grantedAt = time) } }
        assertEquals(Instant.EPOCH, assignment.copy(grantedAt = Instant.EPOCH).grantedAt)
        val maximum = Instant.parse("9999-12-31T23:59:59.999999999Z")
        assertEquals(maximum, assignment.copy(grantedAt = maximum).grantedAt)
    }

    @Test fun anExactReceiptIncludesIdentityOperationAndNanosecondTime() {
        assertEquals(assignment, assignment.copy())
        assertNotEquals(assignment, assignment.copy(accountId = id(3)))
        assertNotEquals(assignment, assignment.copy(operationId = id(3)))
        assertNotEquals(assignment, assignment.copy(grantedAt = assignment.grantedAt.plusNanos(1)))
    }

    private fun id(number: Int) = "d0000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
    private fun reject(action: () -> Unit) {
        try { action(); fail("Invalid administrator assignment accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
