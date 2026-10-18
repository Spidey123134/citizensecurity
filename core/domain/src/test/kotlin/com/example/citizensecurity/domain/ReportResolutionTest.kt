package com.example.citizensecurity.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ReportResolutionTest {
    private val report = "10000000-0000-0000-0000-000000000001"
    private val operation = "10000000-0000-0000-0000-000000000002"
    private val account = "10000000-0000-0000-0000-000000000003"
    private val actor = ReportResolutionActor(account, ReportResolutionActorKind.AUTHOR)
    private val now = Instant.parse("2026-10-17T01:02:03.123456789Z")
    private fun command(reason: String = "Se retiró el árbol frente al café 🌳") = ReportResolutionCommand(
        operation, report, 0, ReportResolution.UNCONFIRMED, ReportResolution.RESOLVED, actor, reason)
    private fun event(command: ReportResolutionCommand = command()) = ReportResolutionEvent(
        command.operationId, command.reportId, command.expectedSequence + 1, command.expectedResolution,
        command.targetResolution, now, command.actor, command.reason)

    @Test fun canonicalIdentitiesAreRequiredForActorOperationAndReport() {
        listOf("", "1-1-1-1-1", "X".repeat(36), "10000000-0000-0000-0000-00000000000A").forEach { bad ->
            invalid { actor.copy(accountId = bad) }
            invalid { command().copy(operationId = bad) }
            invalid { command().copy(reportId = bad) }
            invalid { event().copy(operationId = bad) }
            invalid { event().copy(reportId = bad) }
        }
    }

    @Test fun revisionHasRoomForExactlyOneAppendAndCannotWrap() {
        invalid { command().copy(expectedSequence = -1) }
        invalid { command().copy(expectedSequence = Long.MAX_VALUE) }
        val last = command().copy(expectedSequence = Long.MAX_VALUE - 1,
            expectedResolution = ReportResolution.UNRESOLVED)
        assertEquals(Long.MAX_VALUE, event(last).sequence)
    }

    @Test fun sameResultIsRejectedButBothDirectionsAndResetRetainExplicitChronology() {
        ReportResolution.entries.forEach { result ->
            invalid { command().copy(expectedResolution = result, targetResolution = result) }
            invalid { event().copy(sequence = 2, fromResolution = result, toResolution = result) }
        }
        assertEquals(ReportResolution.UNRESOLVED, command().copy(targetResolution = ReportResolution.UNRESOLVED).targetResolution)
        assertEquals(ReportResolution.UNCONFIRMED, command().copy(expectedSequence = 1,
            expectedResolution = ReportResolution.RESOLVED, targetResolution = ReportResolution.UNCONFIRMED).targetResolution)
    }

    @Test fun firstEventMustStartUnconfirmedAndSequenceMustBePositive() {
        invalid { event().copy(sequence = 0) }
        invalid { event().copy(sequence = -1) }
        invalid { event().copy(fromResolution = ReportResolution.UNRESOLVED) }
        assertEquals(2L, event().copy(sequence = 2, fromResolution = ReportResolution.UNRESOLVED).sequence)
    }

    @Test fun reasonCountsUnicodeCodePointsAtFiveAndFiveHundredBoundaries() {
        listOf("🌳".repeat(5), "á".repeat(500), "🌳".repeat(500), "Esto ' ; -- sigue siendo texto").forEach { reason ->
            assertEquals(reason, command(reason).reason); assertEquals(reason, event(command(reason)).reason)
        }
        listOf("🌳".repeat(4), "a".repeat(501), "🌳".repeat(501)).forEach { bad -> invalid { command(bad) } }
    }

    @Test fun reasonRejectsWhitespaceControlsNullAndMalformedSurrogates() {
        listOf(" Motivo válido", "Motivo válido ", "Sin\ncontrol", "Sin\tcontrol", "Sin\u0000control", "Sin\u007fcontrol",
            "\uD800motivo", "motivo\uDC00", "\uD800\uD800motivo", "\uDC00\uD800motivo").forEach { bad ->
            invalid { command(bad) }; invalid { event().copy(reason = bad) }
        }
    }

    @Test fun exactReceiptMatchesEveryFieldButTimestampIsServerChosen() {
        val requested = command(); val saved = event(requested)
        assertTrue(saved.matches(requested))
        assertTrue(saved.copy(recordedAt = now.minusSeconds(100)).matches(requested))
        listOf(requested.copy(operationId = account), requested.copy(reportId = account),
            requested.copy(expectedSequence = 1), requested.copy(expectedResolution = ReportResolution.UNRESOLVED),
            requested.copy(targetResolution = ReportResolution.UNRESOLVED),
            requested.copy(actor = actor.copy(kind = ReportResolutionActorKind.LOCAL_ADMINISTRATOR)),
            requested.copy(actor = actor.copy(accountId = operation)), requested.copy(reason = "Otro motivo válido"))
            .forEach { assertFalse(saved.matches(it)) }
    }

    @Test fun queryUsesCanonicalReportAndStrictPositiveExclusiveCursorAndBoundedLimit() {
        assertEquals(50, ReportResolutionQuery(report).limit)
        assertEquals(100, ReportResolutionQuery(report, Long.MAX_VALUE, 100).limit)
        assertEquals(1, ReportResolutionQuery(report, 1, 1).limit)
        listOf(0L, -1L).forEach { bad -> invalid { ReportResolutionQuery(report, bad) } }
        listOf(0, -1, 101, Int.MAX_VALUE).forEach { bad -> invalid { ReportResolutionQuery(report, limit = bad) } }
        invalid { ReportResolutionQuery("missing") }
    }

    @Test fun emptyPageMeansUnconfirmedAndNeverInventsAnActorOrReceipt() {
        val page = ReportResolutionPage(report, ReportResolution.UNCONFIRMED, 0, emptyList(), false, null)
        assertTrue(page.events.isEmpty()); assertNull(page.actor); assertNull(page.nextBeforeSequence)
        invalid { ReportResolutionPage(report, ReportResolution.RESOLVED, 0, emptyList(), false, null) }
        invalid { ReportResolutionPage(report, ReportResolution.UNCONFIRMED, -1, emptyList(), false, null) }
        invalid { ReportResolutionPage(report, ReportResolution.UNCONFIRMED, 0, emptyList(), true, null) }
    }

    @Test fun pageCopiesInputAndIsUnmodifiableAndReturnsExclusiveLastCursor() {
        val newer = event().copy(operationId = account, sequence = 2,
            fromResolution = ReportResolution.RESOLVED, toResolution = ReportResolution.UNRESOLVED)
        val mutable = mutableListOf(newer, event())
        val page = ReportResolutionPage(report, ReportResolution.UNRESOLVED, 2, mutable, true, actor)
        mutable.clear()
        assertEquals(listOf(newer, event()), page.events); assertEquals(1L, page.nextBeforeSequence)
        try { (page.events as MutableList<ReportResolutionEvent>).clear(); fail("Must be immutable") }
        catch (_: UnsupportedOperationException) { }
    }

    @Test fun pageRejectsWrongReportDuplicateOrAscendingSequenceAndUnboundedPayload() {
        val first = event(); val second = first.copy(operationId = account, sequence = 2,
            fromResolution = ReportResolution.RESOLVED, toResolution = ReportResolution.UNRESOLVED)
        invalid { ReportResolutionPage(report, ReportResolution.RESOLVED, 1, listOf(first.copy(reportId = account)), false, null) }
        invalid { ReportResolutionPage(report, ReportResolution.UNRESOLVED, 2, listOf(first, second), false, null) }
        invalid { ReportResolutionPage(report, ReportResolution.RESOLVED, 1, listOf(first, first), false, null) }
        invalid { ReportResolutionPage(report, ReportResolution.RESOLVED, 0, listOf(first), false, null) }
        val large = (101 downTo 1).map { index -> first.copy(sequence = index.toLong(),
            operationId = "10000000-0000-0000-0000-${index.toString().padStart(12, '0')}") }
        invalid { ReportResolutionPage(report, ReportResolution.RESOLVED, 101, large, true, null) }
    }

    private inline fun invalid(action: () -> Unit) {
        try { action(); fail("Expected invalid outcome declaration") }
        catch (_: IllegalArgumentException) { }
    }
}
