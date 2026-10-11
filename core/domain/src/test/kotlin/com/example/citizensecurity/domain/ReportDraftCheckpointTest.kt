package com.example.citizensecurity.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ReportDraftCheckpointTest {
    private val id = "1e345678-1234-4567-89ab-0123456789ab"
    private val now = Instant.parse("2026-10-10T03:00:00.123456789Z")
    private val draft = NewReport(
        IncidentType.RISK, Priority.MEDIUM, "Hay un árbol caído frente al mercado.",
        now.minusSeconds(30), ReportLocation("Frente al mercado", 19.4326, -99.1332),
    )

    @Test fun aCheckpointKeepsItsRevisionSeparateFromTheReportIdentity() {
        val checkpoint = ReportDraftCheckpoint(id, draft, now)
        assertEquals(id, checkpoint.id)
        assertEquals(draft, checkpoint.draft)
        assertEquals(now, checkpoint.savedAt)
        assertNull(checkpoint.submittedFolio)
    }

    @Test fun aConfirmedFolioPreservesTheDraftPhotoAndTimestamp() {
        val photo = ReportPhoto("2e345678-1234-4567-89ab-0123456789ab", "a".repeat(64), 1234, 640, 480)
        val original = ReportDraftCheckpoint(id, draft.copy(photo = photo), now)
        val marked = original.copy(submittedFolio = "3e345678-1234-4567-89ab-0123456789ab")
        assertEquals(original.id, marked.id)
        assertEquals(original.draft, marked.draft)
        assertEquals(original.savedAt, marked.savedAt)
        assertEquals(photo, marked.draft.photo)
    }

    @Test fun abbreviatedOrUppercaseUuidCannotBeARevision() {
        listOf("1-1-1-1-1", id.uppercase(), "../$id", "", " $id").forEach { invalid ->
            expectInvalid { ReportDraftCheckpoint(invalid, draft, now) }
        }
    }

    @Test fun abbreviatedOrUppercaseUuidCannotBeAFolio() {
        listOf("1-1-1-1-1", id.uppercase(), "", " $id").forEach { invalid ->
            expectInvalid { ReportDraftCheckpoint(id, draft, now, invalid) }
        }
    }

    @Test fun aTimestampBeforeTheEpochCannotBeStored() {
        expectInvalid { ReportDraftCheckpoint(id, draft, Instant.EPOCH.minusNanos(1)) }
    }

    @Test fun theEpochIsAnAllowedCheckpointTimestamp() {
        val checkpoint = ReportDraftCheckpoint(id, draft.copy(occurredAt = Instant.EPOCH), Instant.EPOCH)
        assertEquals(Instant.EPOCH, checkpoint.savedAt)
    }

    private fun expectInvalid(block: () -> Unit) {
        try {
            block()
            fail("La copia inválida debió rechazarse.")
        } catch (_: IllegalArgumentException) {
            // Contrato de construcción, antes de acceder a almacenamiento.
        }
    }
}
