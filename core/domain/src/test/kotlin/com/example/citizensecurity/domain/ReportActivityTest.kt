package com.example.citizensecurity.domain

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ReportActivityTest {
    private val at = Instant.parse("2026-10-14T18:00:00.123456789Z")
    private val actor = id(999)

    @Test fun defaultsAndStructuralQueryIdentityRetainTheSamePosition() {
        assertEquals(ReportActivityFilter.ALL, ReportActivityQuery().filter)
        assertEquals(50, ReportActivityQuery().limit)
        val cursor = ReportActivityCursor(at, id(1))
        assertEquals(ReportActivityQuery(before = cursor), ReportActivityQuery(before = cursor.copy()))
        assertEquals(cursor, ReportActivityCursor(at, id(1)))
    }

    @Test fun queryLimitIsBoundedBeforeAnyStorageRead() {
        listOf(Int.MIN_VALUE, 0, 101, Int.MAX_VALUE).forEach { limit -> rejected { ReportActivityQuery(limit = limit) } }
        assertEquals(1, ReportActivityQuery(limit = 1).limit)
        assertEquals(100, ReportActivityQuery(limit = 100).limit)
    }

    @Test fun cursorAndReportIdentityRequireCanonicalUuid() {
        listOf("", "1-1-1-1-1", " ${id(1)}", "A0000000-0000-0000-0000-000000000001", "../${id(1)}").forEach { bad ->
            rejected { ReportActivityCursor(at, bad) }
            rejected { ReportActivityEntry(bad, IncidentType.RISK, created(1)) }
        }
    }

    @Test fun emptyActivityHasNoSyntheticCreationOrContinuation() {
        val page = ReportActivityPage(emptyList(), false)
        assertTrue(page.entries.isEmpty())
        assertFalse(page.hasMore)
        assertNull(page.nextBefore)
    }

    @Test fun pageCopiesInputAndProtectsItsRecordedEvents() {
        val input = mutableListOf(entry(2), entry(1))
        val page = ReportActivityPage(input, true)
        input.clear()
        assertEquals(listOf(id(2), id(1)), page.entries.map { it.event.operationId })
        try { (page.entries as MutableList<ReportActivityEntry>).clear(); fail("Activity list was mutable") }
        catch (_: UnsupportedOperationException) { }
    }

    @Test fun continuationUsesLastVisibleTimeAndOperationOnlyWhenMoreExists() {
        val latest = entry(4, at.plusNanos(1))
        val last = entry(2, at)
        assertEquals(ReportActivityCursor(at, id(2)), ReportActivityPage(listOf(latest, last), true).nextBefore)
        assertNull(ReportActivityPage(listOf(latest, last), false).nextBefore)
    }

    @Test fun orderKeepsNanosecondsAndUsesUuidForExactTimeTies() {
        val page = ReportActivityPage(listOf(entry(1, at.plusNanos(1)), entry(3), entry(2)), true)
        assertEquals(listOf(id(1), id(3), id(2)), page.entries.map { it.event.operationId })
    }

    @Test fun orderRejectsAscendingDatesAscendingTiesAndRepeatedOperations() {
        rejected { ReportActivityPage(listOf(entry(1), entry(2, at.plusNanos(1))), false) }
        rejected { ReportActivityPage(listOf(entry(1), entry(2)), false) }
        rejected { ReportActivityPage(listOf(entry(1), entry(1)), false) }
        rejected { ReportActivityPage(listOf(entry(1, at.plusSeconds(1)), entry(1)), false) }
    }

    @Test fun pageRejectsMoreThanOneHundredAndEmptyContinuation() {
        rejected { ReportActivityPage(emptyList(), true) }
        rejected { ReportActivityPage((101 downTo 1).map { entry(it) }, false) }
        assertEquals(100, ReportActivityPage((100 downTo 1).map { entry(it) }, true).entries.size)
    }

    @Test fun existingCreationAndChangeKeepActorBodyAndLocalClockWithoutInventingBaseline() {
        val event = ReportStatusEvent(id(2), 1L, ReportStatusEventKind.STATUS_CHANGED,
            ReportStatus.REPORTED, ReportStatus.IN_REVIEW, at.minusSeconds(1), actor, "Cambio local registrado")
        val page = ReportActivityPage(listOf(entry(1), ReportActivityEntry(id(10), IncidentType.THEFT, event)), false)
        assertEquals(event, page.entries.last().event)
        assertEquals(1L, page.entries.last().event.sequence)
        assertEquals(IncidentType.THEFT, page.entries.last().incidentType)
        assertNull(page.entries.first().event.actorAccountId)
    }

    private fun created(number: Int, time: Instant = at) = ReportStatusEvent(id(number), 1L,
        ReportStatusEventKind.CREATED, null, ReportStatus.REPORTED, time, null)
    private fun entry(number: Int, time: Instant = at) = ReportActivityEntry(id(number), IncidentType.RISK, created(number, time))
    private fun id(number: Int) = "a0000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Invalid activity contract was accepted") } catch (_: IllegalArgumentException) { }
    }
}
