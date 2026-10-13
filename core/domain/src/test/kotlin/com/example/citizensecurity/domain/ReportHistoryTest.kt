package com.example.citizensecurity.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ReportHistoryTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val actor = "00000000-0000-0000-0000-000000000002"
    private val at = Instant.parse("2026-10-13T01:00:00.123456789Z")

    @Test fun transitionPolicyAllowsOnlyThreeConsecutiveForwardSteps() {
        ReportStatus.entries.forEach { from -> ReportStatus.entries.forEach { to ->
            assertEquals(to.ordinal == from.ordinal + 1, canAdvanceReportStatus(from, to))
        } }
    }
    @Test fun queryRequiresCanonicalIdentityPositiveCursorAndBoundedLimit() {
        listOf(id.uppercase().replaceFirst('0', 'A'), "1-1-1-1-1", " $id", "").forEach {
            rejected { ReportHistoryQuery(it) }
        }
        listOf(-1L, 0L).forEach { rejected { ReportHistoryQuery(id, it) } }
        listOf(0, 101, Int.MAX_VALUE).forEach { rejected { ReportHistoryQuery(id, limit = it) } }
        assertEquals(100, ReportHistoryQuery(id, Long.MAX_VALUE, 100).limit)
    }
    @Test fun commandsCannotJumpReopenOverflowOrUseNonCanonicalOperation() {
        rejected { command(target = ReportStatus.CLOSED) }
        rejected { command(from = ReportStatus.CLOSED, target = ReportStatus.REPORTED) }
        rejected { command(sequence = -1) }
        rejected { command(sequence = Long.MAX_VALUE) }
        rejected { command(operation = "1-1-1-1-1") }
    }
    @Test fun reasonRequiresTrimmedValidUnicodeAndCountsCodepoints() {
        listOf("", "1234", " moti", "motivo ", "mot\nivo", "mot\u0000ivo", "mot\uD800ivo", "mot\uDC00ivo", "a".repeat(501))
            .forEach { rejected { command(reason = it) } }
        assertEquals("🌳".repeat(500), command(reason = "🌳".repeat(500)).reason)
    }
    @Test fun creationHasActualInitialStateAndOptionalAuthorButNoInventedTransition() {
        val event = ReportStatusEvent(id, 1, ReportStatusEventKind.CREATED, null, ReportStatus.REPORTED, at, actor)
        assertEquals(at, event.recordedAt)
        rejected { event.copy(sequence = 2) }
        rejected { event.copy(fromStatus = ReportStatus.REPORTED) }
        rejected { event.copy(toStatus = ReportStatus.ATTENDED) }
        rejected { event.copy(reason = "motivo") }
    }
    @Test fun statusEventRequiresActorAndConsecutiveStatuses() {
        val event = changed(1)
        assertEquals(actor, event.actorAccountId)
        rejected { event.copy(actorAccountId = null) }
        rejected { event.copy(actorAccountId = "1-1-1-1-1") }
        rejected { event.copy(fromStatus = null) }
        rejected { event.copy(toStatus = ReportStatus.CLOSED) }
    }
    @Test fun legacyCanHaveNoEventsOrFirstChangeWithoutInventedCreation() {
        val original = ReportHistoryPage(id, ReportStatus.ATTENDED, 0, emptyList(), false, false)
        assertFalse(original.completeFromCreation)
        assertNull(original.nextBeforeSequence)
        val later = ReportHistoryPage(id, ReportStatus.IN_REVIEW, 1, listOf(changed(1)), false, false)
        assertFalse(later.completeFromCreation)
    }
    @Test fun pageCopiesEventsAndContinuationUsesLastVisibleSequence() {
        val input = mutableListOf(changed(3), changed(2))
        val page = ReportHistoryPage(id, ReportStatus.IN_REVIEW, 3, input, true, false)
        input.clear()
        assertEquals(2, page.events.size)
        assertEquals(2L, page.nextBeforeSequence)
        try { (page.events as MutableList<ReportStatusEvent>).clear(); fail("immutable") } catch (_: UnsupportedOperationException) { }
    }
    @Test fun pageRejectsOutOfOrderDuplicateSequenceOversizeAndEmptyContinuation() {
        rejected { ReportHistoryPage(id, ReportStatus.IN_REVIEW, 3, listOf(changed(2), changed(3)), false, false) }
        rejected { ReportHistoryPage(id, ReportStatus.IN_REVIEW, 3, listOf(changed(2), changed(2)), false, false) }
        rejected { ReportHistoryPage(id, ReportStatus.IN_REVIEW, 0, listOf(changed(1)), false, false) }
        rejected { ReportHistoryPage(id, ReportStatus.IN_REVIEW, 3, emptyList(), true, false) }
        rejected { ReportHistoryPage(id, ReportStatus.REPORTED, 0, emptyList(), false, true) }
        rejected { ReportHistoryPage(id, ReportStatus.IN_REVIEW, 101, (101L downTo 1).map(::changed), false, false) }
    }
    private fun changed(sequence: Long) = ReportStatusEvent(id, sequence, ReportStatusEventKind.STATUS_CHANGED,
        ReportStatus.REPORTED, ReportStatus.IN_REVIEW, at, actor, "Revisar incidente")
    private fun command(operation: String = id, sequence: Long = 0, from: ReportStatus = ReportStatus.REPORTED,
        target: ReportStatus = ReportStatus.IN_REVIEW, reason: String = "Revisar incidente") =
        ReportStatusChangeCommand(operation, id, sequence, from, target, reason)
    private fun rejected(action: () -> Any) {
        try { action(); fail("Expected validation rejection") } catch (_: IllegalArgumentException) { }
    }
}
