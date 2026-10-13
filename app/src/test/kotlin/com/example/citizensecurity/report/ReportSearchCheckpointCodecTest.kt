package com.example.citizensecurity.report

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportListCursor
import com.example.citizensecurity.domain.ReportStatus
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSearchCheckpointCodecTest {
    @Test
    fun canonicalStringsPreserveCriteriaNegativeSecondsNanosecondsAndCursorTieBreakers() {
        val filters = ReportSearchFilters(
            IncidentType.FIRE, ReportStatus.ATTENDED, "arbol %_",
            Instant.ofEpochSecond(-12, 123_456_789), Instant.ofEpochSecond(40, 987_654_321), Priority.LOW,
        )
        val cursor = ReportListCursor(Instant.ofEpochSecond(-2, 999_999_999), "f".repeat(64))
        val history = listOf(ReportPageStart(null, 1), ReportPageStart(cursor, 2))
        val checkpoint = ReportSearchCheckpoint(
            ReportPageRequest(filters, cursor, 2, ReportPageNavigation.RELOAD, 1), history, 1,
        )
        val encoded = requireNotNull(ReportSearchCheckpointCodec.encode(checkpoint))
        val restored = requireNotNull(ReportSearchCheckpointCodec.decode(encoded))
        assertEquals(filters.type, restored.request.filters.type)
        assertEquals(filters.status, restored.request.filters.status)
        assertEquals(filters.priority, restored.request.filters.priority)
        assertEquals(filters.normalizedText, restored.request.filters.normalizedText)
        assertEquals(filters.startInclusive, restored.request.filters.startInclusive)
        assertEquals(filters.endExclusive, restored.request.filters.endExclusive)
        assertEquals(cursor, restored.request.before)
        assertEquals(history, restored.history)
        assertEquals(1, restored.historyIndex)
        assertEquals(encoded, ReportSearchCheckpointCodec.encode(restored))
        assertTrue(encoded.sumOf { it.length } <= ReportSearchCheckpointCodec.MAX_CHARACTERS)
    }

    @Test
    fun optionalCriteriaDatesAndEveryNavigationHaveAnUnambiguousRepresentation() {
        val filters = filters()
        val history = listOf(ReportPageStart(null, 1), start(2), start(3))
        val requests = listOf(
            ReportSearchCheckpoint(ReportPageRequest(filters, null, 1, ReportPageNavigation.SEARCH), emptyList(), 0),
            ReportSearchCheckpoint(ReportPageRequest(filters, start(2).before, 2, ReportPageNavigation.NEXT), history.take(1), 0),
            ReportSearchCheckpoint(ReportPageRequest(filters, start(2).before, 2, ReportPageNavigation.PREVIOUS, 1), history, 2),
            ReportSearchCheckpoint(ReportPageRequest(filters, start(2).before, 2, ReportPageNavigation.RELOAD, 1), history, 1),
        )
        requests.forEach { checkpoint ->
            val encoded = requireNotNull(ReportSearchCheckpointCodec.encode(checkpoint))
            val restored = requireNotNull(ReportSearchCheckpointCodec.decode(encoded))
            assertEquals(checkpoint.history, restored.history)
            assertEquals(checkpoint.request.navigation, restored.request.navigation)
            assertEquals(checkpoint.request.historyIndex, restored.request.historyIndex)
            assertNull(restored.request.filters.type)
            assertNull(restored.request.filters.status)
            assertNull(restored.request.filters.priority)
            assertNull(restored.request.filters.startInclusive)
            assertNull(restored.request.filters.endExclusive)
        }
        listOf(
            ReportSearchFilters(null, null, "", Instant.EPOCH, null),
            ReportSearchFilters(null, null, "", null, Instant.EPOCH),
        ).forEach { selected ->
            val checkpoint = ReportSearchCheckpoint(ReportPageRequest(selected, null, 1, ReportPageNavigation.SEARCH), emptyList(), 0)
            val restored = requireNotNull(ReportSearchCheckpointCodec.decode(ReportSearchCheckpointCodec.encode(checkpoint)))
            assertEquals(selected.startInclusive, restored.request.filters.startInclusive)
            assertEquals(selected.endExclusive, restored.request.filters.endExclusive)
        }
    }

    @Test
    fun fiftyTruncatedStartsAndTheMaximumIntegerPageRemainBounded() {
        val history = (2..51).map(::start)
        val checkpoint = ReportSearchCheckpoint(
            ReportPageRequest(filters(), history.last().before, 51, ReportPageNavigation.RELOAD, 49), history, 49,
        )
        val encoded = requireNotNull(ReportSearchCheckpointCodec.encode(checkpoint))
        assertEquals(217, encoded.size)
        assertEquals(history, requireNotNull(ReportSearchCheckpointCodec.decode(encoded)).history)
        val maximumStart = start(Int.MAX_VALUE)
        assertNotNull(ReportSearchCheckpointCodec.encode(ReportSearchCheckpoint(
            ReportPageRequest(filters(), maximumStart.before, Int.MAX_VALUE, ReportPageNavigation.RELOAD),
            listOf(maximumStart), 0,
        )))
    }

    @Test
    fun incompatibleTypesVersionsLengthsNumbersEnumsAndOptionalFieldsRejectEverything() {
        val valid = requireNotNull(ReportSearchCheckpointCodec.encode(reload()))
        val malformed = listOf<Any?>(
            null, "1", 1, valid.toTypedArray(), emptyList<String>(), valid + "extra",
            List(218) { "" }, List(217) { "a".repeat(100) },
            valid.toMutableList<Any?>().also { it[0] = null },
            valid.toMutableList<Any?>().also { it[12] = 2 },
            changed(valid, 0, "2"), changed(valid, 1, "UNKNOWN"), changed(valid, 2, "UNKNOWN"),
            changed(valid, 3, "UNKNOWN"), changed(valid, 4, "a".repeat(4_097)),
            changed(valid, 4, " Árbol "), changed(valid, 13, "UNKNOWN"),
            changed(valid, 12, "02"), changed(valid, 12, "+2"), changed(valid, 12, "0"),
            changed(valid, 12, "-1"), changed(valid, 12, "2147483648"),
            changed(valid, 11, "x".repeat(65)), changed(valid, 11, " "),
            changed(valid, 5, "1"), changed(valid, 6, "0"),
            changed(valid, 9, ""), changed(valid, 10, ""), changed(valid, 11, ""),
            changed(valid, 14, "-1"), changed(valid, 15, "50"), changed(valid, 16, "51"),
        )
        malformed.forEachIndexed { index, raw ->
            assertNull("Malformed checkpoint $index must be rejected completely", ReportSearchCheckpointCodec.decode(raw))
        }
    }

    @Test
    fun instantOverflowNegativeNanosAndNonCanonicalNumbersAreNeverNormalizedIntoValidDates() {
        val valid = requireNotNull(ReportSearchCheckpointCodec.encode(reload()))
        listOf(
            changed(valid, 9, Long.MAX_VALUE.toString()), changed(valid, 9, Long.MIN_VALUE.toString()),
            changed(valid, 10, "-1"), changed(valid, 10, "1000000000"),
            changed(valid, 10, "000000001"), changed(valid, 9, "-0"),
        ).forEach { assertNull(ReportSearchCheckpointCodec.decode(it)) }
        val dates = valid.toMutableList().also { it[5] = "3"; it[6] = "0"; it[7] = "2"; it[8] = "0" }
        assertNull(ReportSearchCheckpointCodec.decode(dates))
    }

    @Test
    fun fullLengthUnicodeInputKeepsItsExpandedNormalizedCriteria() {
        val selected = (parseReportSearchFilters(text = "각".repeat(512), zoneId = ZoneOffset.UTC)
            as ReportSearchFilterValidation.Valid).filters
        assertEquals(1_536, selected.normalizedText.length)
        val checkpoint = ReportSearchCheckpoint(
            ReportPageRequest(selected, null, 1, ReportPageNavigation.SEARCH), emptyList(), 0,
        )
        val encoded = requireNotNull(ReportSearchCheckpointCodec.encode(checkpoint))
        val restored = requireNotNull(ReportSearchCheckpointCodec.decode(encoded))
        assertEquals(selected.normalizedText, restored.request.filters.normalizedText)
        assertTrue(encoded.sumOf { it.length } <= ReportSearchCheckpointCodec.MAX_CHARACTERS)
    }

    @Test
    fun incoherentHistoryOrNavigationCannotInventOrPartiallyRestoreAPosition() {
        val valid = reload()
        val malformed = listOf(
            valid.copy(history = List(51) { start(it + 2) }, historyIndex = 50),
            valid.copy(historyIndex = -1),
            valid.copy(history = listOf(start(2), ReportPageStart(start(2).before, 3))),
            valid.copy(history = listOf(ReportPageStart(null, 2))),
            valid.copy(history = listOf(ReportPageStart(start(2).before, 1))),
            valid.copy(request = valid.request.copy(historyIndex = 0)),
            valid.copy(request = valid.request.copy(navigation = ReportPageNavigation.SEARCH)),
            valid.copy(request = valid.request.copy(navigation = ReportPageNavigation.NEXT)),
            valid.copy(request = valid.request.copy(navigation = ReportPageNavigation.PREVIOUS)),
            valid.copy(request = valid.request.copy(pageNumber = 3)),
        )
        malformed.forEach { assertNull(ReportSearchCheckpointCodec.encode(it)) }
        val encoded = requireNotNull(ReportSearchCheckpointCodec.encode(valid))
        // Replace the second start's exclusive cursor with the first start's null cursor.
        val repeated = encoded.toMutableList().also { it[21] = ""; it[22] = ""; it[23] = "" }
        assertNull(ReportSearchCheckpointCodec.decode(repeated))
    }

    private fun reload(): ReportSearchCheckpoint {
        val history = listOf(ReportPageStart(null, 1), start(2))
        return ReportSearchCheckpoint(
            ReportPageRequest(filters(), history.last().before, 2, ReportPageNavigation.RELOAD, 1), history, 1,
        )
    }

    private fun filters(): ReportSearchFilters =
        (parseReportSearchFilters(zoneId = ZoneOffset.UTC) as ReportSearchFilterValidation.Valid).filters

    private fun start(page: Int) = ReportPageStart(ReportListCursor(Instant.ofEpochSecond(-page.toLong(), 123), "folio-$page"), page)

    private fun changed(source: List<String>, index: Int, value: String) = source.toMutableList().also { it[index] = value }
}
