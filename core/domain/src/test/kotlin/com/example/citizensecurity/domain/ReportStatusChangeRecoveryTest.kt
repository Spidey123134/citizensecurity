package com.example.citizensecurity.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ReportStatusChangeRecoveryTest {
    @Test fun academicOwnerDoesNotInventAnAccountAndEveryActorNeedsACanonicalUuid() {
        assertNull(ReportStatusChangeScope(null, ACTOR).ownerAccountId)
        assertEquals(OWNER, ReportStatusChangeScope(OWNER, ACTOR).ownerAccountId)
        listOf("", "1-1-1-1-1", ACTOR.uppercase(), "../admin").forEach {
            try { ReportStatusChangeScope(null, it); fail("Invalid actor accepted") } catch (_: IllegalArgumentException) { }
            try { ReportStatusChangeScope(it, ACTOR); fail("Invalid owner accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun receiptRequiresExactFolioActorOperationRevisionAndEntireChangeBody() {
        val request = request()
        val receipt = receipt(request)
        assertTrue(receipt.matches(request))
        assertFalse(receipt.copy(reportId = OWNER).matches(request))
        assertFalse(receipt.copy(event = receipt.event.copy(actorAccountId = OWNER)).matches(request))
        assertFalse(receipt.copy(event = receipt.event.copy(operationId = OWNER)).matches(request))
        assertFalse(receipt.copy(event = receipt.event.copy(sequence = 8)).matches(request))
        assertFalse(receipt.copy(event = receipt.event.copy(reason = "Otro motivo de revisión")).matches(request))
        assertFalse(receipt.matches(request.copy(command = request.command.copy(expectedSequence = 0))))
    }

    @Test fun createdEventIsNeverAStatusChangeReceipt() {
        try {
            ReportStatusChangeReceipt(FOLIO, ReportStatusEvent(OPERATION, 1, ReportStatusEventKind.CREATED,
                null, ReportStatus.REPORTED, TIME, null))
            fail("Creation used as change receipt")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun durableRequestKeepsNanosecondsAndWholeIdentityWithoutTransientSessionFields() {
        val request = request()
        assertEquals(TIME, request.requestedAt)
        assertEquals(123456789, request.requestedAt.nano)
        assertEquals(request, request.copy())
        assertNotEquals(request, request.copy(scope = request.scope.copy(actorAccountId = OWNER)))
        assertNotEquals(request, request.copy(requestedAt = TIME.plusNanos(1)))
    }

    private fun request() = ReportStatusChangeRequest(ReportStatusChangeScope(OWNER, ACTOR),
        ReportStatusChangeCommand(OPERATION, FOLIO, 1, ReportStatus.REPORTED, ReportStatus.IN_REVIEW,
            "Se revisó el incidente local"), TIME)
    private fun receipt(request: ReportStatusChangeRequest) = ReportStatusChangeReceipt(FOLIO,
        ReportStatusEvent(OPERATION, 2, ReportStatusEventKind.STATUS_CHANGED, ReportStatus.REPORTED,
            ReportStatus.IN_REVIEW, TIME.plusSeconds(1), request.scope.actorAccountId, request.command.reason))
    private companion object {
        const val ACTOR = "ac000000-0000-4000-8000-000000000001"
        const val OWNER = "ac000000-0000-4000-8000-000000000002"
        const val FOLIO = "ac000000-0000-4000-8000-000000000003"
        const val OPERATION = "ac000000-0000-4000-8000-000000000004"
        val TIME: Instant = Instant.parse("2026-10-13T17:00:00.123456789Z")
    }
}
