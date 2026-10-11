package com.example.citizensecurity.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReportPhotoTest {
    private val photo = ReportPhoto("1e345678-1234-4567-89ab-0123456789ab", "a".repeat(64), 1024, 640, 480)
    private val now = Instant.parse("2026-10-10T05:00:00.123456789Z")

    @Test fun validMetadataAcceptsTheDocumentedSizeAndDimensionBoundaries() {
        assertEquals(1L, photo.copy(byteCount = 1, width = 1, height = 1).byteCount)
        val maximum = photo.copy(byteCount = ReportPhoto.MAX_BYTES, width = 2048, height = 2048)
        assertEquals(6L * 1024 * 1024, maximum.byteCount)
        assertEquals(2048, ReportPhoto.MAX_DIMENSION)
    }

    @Test fun identityRejectsShortUppercaseInvalidAndPathRepresentations() {
        listOf("1-2-3-4-5", photo.id.uppercase(), "../../image.jpg", " ${photo.id}",
            "ge345678-1234-4567-89ab-0123456789ab", "").forEach { id ->
            expectInvalid { photo.copy(id = id) }
        }
    }

    @Test fun sha256RejectsNoncanonicalAndIncompleteDigests() {
        listOf("A".repeat(64), "g".repeat(64), "a".repeat(63), "a".repeat(65),
            " " + "a".repeat(63), "").forEach { hash -> expectInvalid { photo.copy(sha256 = hash) } }
    }

    @Test fun byteCountRejectsEmptyNegativeAndOversizedPhotos() {
        listOf(0L, -1L, ReportPhoto.MAX_BYTES + 1, Long.MAX_VALUE).forEach { count ->
            expectInvalid { photo.copy(byteCount = count) }
        }
    }

    @Test fun dimensionsRejectInvalidWidthOrHeightWithoutIntegerOverflow() {
        listOf(0, -1, 2049, Int.MAX_VALUE, Int.MIN_VALUE).forEach { size ->
            expectInvalid { photo.copy(width = size) }
            expectInvalid { photo.copy(height = size) }
        }
    }

    @Test fun reportsRemainCompatibleWithoutAnOptionalPhoto() {
        val draft = draft(null)
        val report = Report(photo.id, draft.type, draft.priority, draft.description, draft.occurredAt,
            draft.location, now)
        assertNull(draft.photo)
        assertNull(report.photo)
        assertTrue(ReportValidator().validate(draft, now).isValid)
    }

    @Test fun durableReceiptMatchingIncludesEveryPhotoMetadataField() {
        val draft = draft(photo)
        val request = ReportSubmissionRequest(photo.id, draft, now)
        val report = Report(photo.id, draft.type, draft.priority, draft.description, draft.occurredAt,
            draft.location, now, photo = photo)
        assertTrue(request.matches(report))
        val changed = listOf<ReportPhoto?>(null,
            photo.copy(id = "2e345678-1234-4567-89ab-0123456789ab"),
            photo.copy(sha256 = "b".repeat(64)), photo.copy(byteCount = 2048),
            photo.copy(width = 641), photo.copy(height = 481))
        changed.forEach { candidate -> assertFalse(request.matches(report.copy(photo = candidate))) }
        assertFalse(ReportSubmissionRequest(photo.id, draft(null), now).matches(report))
    }

    private fun draft(photo: ReportPhoto?) = NewReport(
        IncidentType.RISK, Priority.MEDIUM, "Hay una luminaria dañada frente al mercado.",
        now.minusSeconds(60), ReportLocation("Frente al mercado", 19.4326, -99.1332), photo,
    )
    private fun expectInvalid(action: () -> Any) {
        try { action(); fail("Debió rechazar los metadatos inválidos.") }
        catch (_: IllegalArgumentException) { }
    }
}
