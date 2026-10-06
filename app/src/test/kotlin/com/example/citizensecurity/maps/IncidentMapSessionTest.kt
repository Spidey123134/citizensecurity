package com.example.citizensecurity.maps

import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.NearbyIncidentRejection
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.ReportLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentMapSessionTest {
    private val now = 300_000_000_000L
    private val original = ReportLocation("  Parque de referencia 🏠  ", 19.4326077, -99.1332088)
    private val fix = DeviceLocationFix(19.4326077, -99.1332088, 20.0, now, false)

    @Test
    fun originalCoordinatesDoNotCountAsANewSelection() {
        val session = session()
        assertSame(NearbyLocationConfirmation.NoSelection, session.confirm())
        assertFalse(session.isClosed)
    }

    @Test
    fun tappingOnlyProposesAPointAndDoesNotReadPhoneEvidence() {
        var evidenceReads = 0
        val session = IncidentMapSession(original, { evidenceReads++; fix }, { now })
        val proposal = session.select(19.433, -99.134) as LocationProposal.ValidPoint
        assertEquals(19.433, proposal.latitude, 0.0)
        assertSame(original, session.originalLocation)
        assertEquals(0, evidenceReads)
        assertFalse(session.isClosed)
    }

    @Test
    fun confirmationUsesTheLatestTapAndPreservesReferenceAndCoordinatePrecision() {
        val session = session()
        session.select(19.433, -99.134)
        session.select(19.4326081234567, -99.1332077654321)
        val result = session.confirm() as NearbyLocationConfirmation.Confirmed
        assertEquals(original.reference, result.location.reference)
        assertEquals(19.4326081234567.toRawBits(), result.location.latitude!!.toRawBits())
        assertEquals((-99.1332077654321).toRawBits(), result.location.longitude!!.toRawBits())
        assertSame(original, session.originalLocation)
        assertTrue(session.isClosed)
    }

    @Test
    fun selectedPointCannotReplaceMissingPhoneEvidence() {
        var currentFix: DeviceLocationFix? = null
        val session = IncidentMapSession(original, { currentFix }, { now })
        session.select(fix.latitude, fix.longitude)
        assertRejected(NearbyIncidentRejection.DEVICE_LOCATION_REQUIRED, session.confirm())
        assertFalse(session.isClosed)
        currentFix = fix
        assertTrue(session.confirm() is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun distantPointIsRejectedAndTheOpenSessionAllowsCorrection() {
        val session = session()
        session.select(48.8566, 2.3522)
        assertRejected(NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS, session.confirm())
        assertFalse(session.isClosed)
        session.select(fix.latitude, fix.longitude)
        assertTrue(session.confirm() is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun evidenceCanExpireAfterATapAndMustBeRenewedBeforeConfirming() {
        var currentTime = now
        var currentFix = fix
        val session = IncidentMapSession(original, { currentFix }, { currentTime })
        session.select(fix.latitude, fix.longitude)
        currentTime += 120_000_000_001L
        assertRejected(NearbyIncidentRejection.STALE_DEVICE_LOCATION, session.confirm())
        assertFalse(session.isClosed)
        currentFix = fix.copy(elapsedRealtimeNanos = currentTime)
        assertTrue(session.confirm() is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun confirmationReadsTheCurrentPhonePositionInsteadOfThePositionAtTheTap() {
        var currentFix = fix
        val session = IncidentMapSession(original, { currentFix }, { now })
        session.select(fix.latitude, fix.longitude)
        currentFix = fix.copy(latitude = 48.8566, longitude = 2.3522)
        assertRejected(NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS, session.confirm())
        assertFalse(session.isClosed)
    }

    @Test
    fun mockedPhoneEvidenceDoesNotCloseOrApproveTheSelection() {
        val session = IncidentMapSession(original, { fix.copy(isMock = true) }, { now })
        session.select(fix.latitude, fix.longitude)
        assertRejected(NearbyIncidentRejection.MOCK_DEVICE_LOCATION, session.confirm())
        assertFalse(session.isClosed)
    }

    @Test
    fun invalidNewTapCannotReuseThePreviousValidPoint() {
        val session = session()
        session.select(fix.latitude, fix.longitude)
        assertTrue(session.select(Double.NaN, 0.0) is LocationProposal.InvalidCoordinates)
        assertTrue(session.confirm() is NearbyLocationConfirmation.InvalidCoordinates)
        assertFalse(session.isClosed)
        session.select(fix.latitude, fix.longitude)
        assertTrue(session.confirm() is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun cancellingRestoresTheExactOriginalAndIgnoresLateEvents() {
        var evidenceReads = 0
        val partialOriginal = ReportLocation("  Referencia original  ", 19.4326077, null)
        val session = IncidentMapSession(partialOriginal, { evidenceReads++; fix }, { now })
        session.select(fix.latitude, fix.longitude)
        assertSame(partialOriginal, session.cancel())
        assertTrue(session.isClosed)
        assertNull(session.select(48.8566, 2.3522))
        assertSame(NearbyLocationConfirmation.NoSelection, session.confirm())
        assertSame(partialOriginal, session.cancel())
        assertEquals(0, evidenceReads)
    }

    @Test
    fun successfulConfirmationCannotBeReusedByRepeatedOrLateEvents() {
        var evidenceReads = 0
        val session = IncidentMapSession(original, { evidenceReads++; fix }, { now })
        session.select(fix.latitude, fix.longitude)
        assertTrue(session.confirm() is NearbyLocationConfirmation.Confirmed)
        assertNull(session.select(48.8566, 2.3522))
        assertSame(NearbyLocationConfirmation.NoSelection, session.confirm())
        assertEquals(1, evidenceReads)
    }

    @Test
    fun cancellingAfterConfirmationKeepsTheAcceptedPointInsteadOfRestoringOldCoordinates() {
        var evidenceReads = 0
        val session = IncidentMapSession(original, { evidenceReads++; fix }, { now })
        session.select(19.433, -99.134)
        val accepted = (session.confirm() as NearbyLocationConfirmation.Confirmed).location

        assertSame(accepted, session.cancel())
        assertSame(accepted, session.cancel())
        assertEquals(19.433, accepted.latitude!!, 0.0)
        assertEquals(-99.134, accepted.longitude!!, 0.0)
        assertTrue(session.isClosed)
        assertNull(session.select(48.8566, 2.3522))
        assertSame(NearbyLocationConfirmation.NoSelection, session.confirm())
        assertEquals(1, evidenceReads)
    }

    private fun session() = IncidentMapSession(original, { fix }, { now })

    private fun assertRejected(reason: NearbyIncidentRejection, result: NearbyLocationConfirmation) {
        assertEquals(reason, (result as NearbyLocationConfirmation.Blocked).rejection.reason)
    }
}
