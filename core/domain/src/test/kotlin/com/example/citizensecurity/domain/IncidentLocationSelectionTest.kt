package com.example.citizensecurity.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentLocationSelectionTest {
    private val now = Instant.parse("2026-10-03T03:00:00Z")
    private val original = ReportLocation("  Frente al parque 🏠\n", 19.4326077, -99.1332088)

    @Test
    fun confirmingWithoutAProposalDoesNotImplicitlyConfirmOriginalCoordinates() {
        val selection = IncidentLocationSelection(original)

        assertSame(LocationProposal.None, selection.proposal)
        assertSame(LocationConfirmation.NoSelection, selection.confirm())
        assertSame(original, selection.cancel())
    }

    @Test
    fun confirmingWithoutAProposalIsExplicitWhenOriginalHasNoCoordinates() {
        val selection = IncidentLocationSelection(ReportLocation("Frente a la biblioteca"))

        assertSame(LocationConfirmation.NoSelection, selection.confirm())
    }

    @Test
    fun proposingAndConfirmingDoNotMutateTheReportOrOriginalSession() {
        val draft = draft(original)
        val selection = IncidentLocationSelection(draft.location)
        val proposed = selection.propose(20.123456789012345, -98.98765432109876)
        val confirmed = confirmedLocation(proposed)

        assertSame(original, draft.location)
        assertSame(original, selection.originalLocation)
        assertSame(original, proposed.originalLocation)
        assertSame(LocationProposal.None, selection.proposal)
        assertSame(LocationConfirmation.NoSelection, selection.confirm())
        assertEquals(19.4326077, draft.location.latitude!!, 0.0)
        assertEquals(-99.1332088, draft.location.longitude!!, 0.0)
        assertEquals(original.reference, confirmed.reference)
        assertSame(original, proposed.cancel())
    }

    @Test
    fun confirmingPreservesReferenceAndFullCoordinatePrecision() {
        val latitude = 20.123456789012345
        val longitude = -98.98765432109876

        val confirmed = confirmedLocation(IncidentLocationSelection(original).propose(latitude, longitude))

        assertEquals(original.reference, confirmed.reference)
        assertEquals(latitude.toRawBits(), confirmed.latitude!!.toRawBits())
        assertEquals(longitude.toRawBits(), confirmed.longitude!!.toRawBits())
    }

    @Test
    fun zeroAndInclusiveCoordinateBoundariesAreAccepted() {
        for (latitude in listOf(-90.0, 0.0, 90.0)) {
            for (longitude in listOf(-180.0, 0.0, 180.0)) {
                val proposed = IncidentLocationSelection(original).propose(latitude, longitude)
                val confirmed = confirmedLocation(proposed)

                assertEquals(latitude, confirmed.latitude!!, 0.0)
                assertEquals(longitude, confirmed.longitude!!, 0.0)
                assertTrue(ReportValidator().validate(draft(confirmed), now).isValid)
            }
        }
    }

    @Test
    fun negativeZeroIsPreservedInsteadOfBeingTreatedAsMissing() {
        val confirmed = confirmedLocation(IncidentLocationSelection(original).propose(-0.0, -0.0))

        assertEquals((-0.0).toRawBits(), confirmed.latitude!!.toRawBits())
        assertEquals((-0.0).toRawBits(), confirmed.longitude!!.toRawBits())
    }

    @Test
    fun coordinatesJustOutsideTheGeographicLimitsAreRejected() {
        for ((latitude, longitude) in listOf(
            Math.nextUp(90.0) to 0.0,
            Math.nextDown(-90.0) to 0.0,
            0.0 to Math.nextUp(180.0),
            0.0 to Math.nextDown(-180.0),
        )) {
            assertRejected(latitude, longitude)
        }
    }

    @Test
    fun nonFiniteLatitudeOrLongitudeCannotBeConfirmed() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertRejected(value, 0.0)
            assertRejected(0.0, value)
        }
    }

    @Test
    fun anInvalidProposalDoesNotFallBackToAPreviousValidPoint() {
        val valid = IncidentLocationSelection(original).propose(20.0, -98.0)
        val invalid = valid.propose(Double.NaN, -97.0)

        assertTrue(valid.confirm() is LocationConfirmation.Confirmed)
        assertTrue(invalid.proposal is LocationProposal.InvalidCoordinates)
        assertTrue(invalid.confirm() is LocationConfirmation.InvalidCoordinates)
        assertSame(original, invalid.cancel())
    }

    @Test
    fun aValidProposalAfterAnInvalidOneCanBeConfirmed() {
        val invalid = IncidentLocationSelection(original).propose(91.0, 0.0)
        val corrected = invalid.propose(21.0, -97.0)

        assertTrue(invalid.confirm() is LocationConfirmation.InvalidCoordinates)
        assertEquals(original.copy(latitude = 21.0, longitude = -97.0), confirmedLocation(corrected))
    }

    @Test
    fun repeatedProposalsConfirmTheLatestPointWithoutChangingOlderSessions() {
        val selection = IncidentLocationSelection(original)
        val first = selection.propose(20.0, -98.0)
        val latest = first.propose(21.0, -97.0)

        assertSame(LocationConfirmation.NoSelection, selection.confirm())
        assertEquals(original.copy(latitude = 20.0, longitude = -98.0), confirmedLocation(first))
        assertEquals(original.copy(latitude = 21.0, longitude = -97.0), confirmedLocation(latest))
    }

    @Test
    fun cancelKeepsOriginalLocationEvenWhenItsCoordinatesWereIncomplete() {
        val incomplete = ReportLocation("Referencia original", latitude = 19.4)
        val selection = IncidentLocationSelection(incomplete)

        assertSame(incomplete, selection.cancel())
        assertSame(incomplete, selection.propose(20.0, -98.0).cancel())
        assertSame(incomplete, selection.propose(91.0, 0.0).cancel())
    }

    @Test
    fun confirmingCoordinatesLeavesReferenceAndReportValidationToReportValidator() {
        val invalidReference = ReportLocation("abc")
        val confirmed = confirmedLocation(IncidentLocationSelection(invalidReference).propose(0.0, 0.0))
        val errors = ReportValidator().validate(draft(confirmed), now)

        assertEquals("abc", confirmed.reference)
        assertFalse(errors.isValid)
        assertEquals(setOf(ReportField.LOCATION_REFERENCE), errors.errors.keys)
    }

    @Test
    fun confirmedPointCanBeExplicitlyAppliedToANewDraftWithoutChangingOriginalDraft() {
        val originalDraft = draft(ReportLocation())
        val selected = IncidentLocationSelection(originalDraft.location).propose(19.4, -99.1)
        val updatedDraft = originalDraft.copy(location = confirmedLocation(selected))

        assertEquals(ReportLocation(), originalDraft.location)
        assertEquals(originalDraft.type, updatedDraft.type)
        assertEquals(originalDraft.priority, updatedDraft.priority)
        assertEquals(originalDraft.description, updatedDraft.description)
        assertEquals(originalDraft.occurredAt, updatedDraft.occurredAt)
        assertTrue(ReportValidator().validate(updatedDraft, now).isValid)
    }

    private fun assertRejected(latitude: Double, longitude: Double) {
        val selection = IncidentLocationSelection(original).propose(latitude, longitude)
        val proposal = selection.proposal as LocationProposal.InvalidCoordinates
        val result = selection.confirm() as LocationConfirmation.InvalidCoordinates
        val errors = ReportValidator().validate(
            draft(original.copy(latitude = latitude, longitude = longitude)),
            now,
        )

        assertEquals(errors.errors[ReportField.LOCATION_COORDINATES], proposal.message)
        assertEquals(proposal.message, result.message)
        assertSame(original, selection.cancel())
    }

    private fun confirmedLocation(selection: IncidentLocationSelection): ReportLocation =
        (selection.confirm() as LocationConfirmation.Confirmed).location

    private fun draft(location: ReportLocation) = NewReport(
        type = IncidentType.RISK,
        priority = Priority.MEDIUM,
        description = "Hay una luminaria dañada frente al parque.",
        occurredAt = now,
        location = location,
    )
}
