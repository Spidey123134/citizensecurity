package com.example.citizensecurity.domain

import org.junit.Assert.*
import org.junit.Test

class ReportAlertsTest {
    private val revision = id(9999)
    @Test fun absentPreferencesAreDisabledAndHaveNoPresentationHistory() {
        val initial = ReportAlertsSnapshot(null)
        assertFalse(initial.enabled); assertTrue(initial.presented.isEmpty()); assertNull(initial.revision)
    }
    @Test fun unrevisionedStateCannotPretendToBeEnabledOrHavePresentedIds() {
        reject { ReportAlertsSnapshot(null, true) }
        reject { ReportAlertsSnapshot(null, presented = setOf(id(1))) }
    }
    @Test fun snapshotCopiesInputAndKeepsStableEqualityAndHash() {
        val input = linkedSetOf(id(1))
        val snapshot = ReportAlertsSnapshot(revision, true, input)
        val expectedHash = snapshot.hashCode()
        input.clear(); input += id(2)
        assertEquals(setOf(id(1)), snapshot.presented)
        assertEquals(expectedHash, snapshot.hashCode())
        assertEquals(ReportAlertsSnapshot(revision, true, setOf(id(1))), snapshot)
    }
    @Test fun publishedSetCannotBeModifiedThroughAMutableCast() {
        val snapshot = ReportAlertsSnapshot(revision, presented = setOf(id(1)))
        try { (snapshot.presented as MutableSet<String>).clear(); fail("Presentation history was mutable") }
        catch (_: UnsupportedOperationException) { }
        assertEquals(setOf(id(1)), snapshot.presented)
    }
    @Test fun revisionAndEventIdsRequireFullCanonicalUuids() {
        listOf("", "1-1-1-1-1", "A0000000-0000-0000-0000-000000000001", " ${id(1)}", "../${id(1)}").forEach { value ->
            reject { ReportAlertsSnapshot(value) }
            reject { ReportAlertsSnapshot(revision, presented = setOf(value)) }
        }
    }
    @Test fun capacityIsExactlyFourThousandNinetySixWithoutTrimming() {
        val full = (1..ReportAlertsSnapshot.MAX_PRESENTED).map(::id).toSet()
        assertEquals(4096, ReportAlertsSnapshot(revision, true, full).presented.size)
        reject { ReportAlertsSnapshot(revision, true, full + id(5000)) }
    }
    @Test fun disabledRevisionedPreferencesCanRetainEarlierPresentedIds() {
        val snapshot = ReportAlertsSnapshot(revision, false, setOf(id(1), id(2)))
        assertFalse(snapshot.enabled); assertEquals(2, snapshot.presented.size)
    }
    @Test fun setOrderingDoesNotChangeSnapshotIdentity() {
        assertEquals(ReportAlertsSnapshot(revision, true, linkedSetOf(id(1), id(2))),
            ReportAlertsSnapshot(revision, true, linkedSetOf(id(2), id(1))))
    }
    private fun id(number: Int) = "d0000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
    private fun reject(action: () -> Unit) {
        try { action(); fail("Invalid alerts snapshot accepted") } catch (_: IllegalArgumentException) { }
    }
}
