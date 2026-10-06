package com.example.citizensecurity.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyLocationConfirmationTest {
    private val now = 300_000_000_000L
    private val original = ReportLocation("  Parque de ejemplo 🏠  ", 19.4326077, -99.1332088)
    private val fix = DeviceLocationFix(19.4326077, -99.1332088, 20.0, now, false)

    @Test
    fun noPinAndInvalidPinHaveExplicitResultsWithoutUsingTheOriginalPoint() {
        val selection = IncidentLocationSelection(original)
        assertSame(NearbyLocationConfirmation.NoSelection, selection.confirmNearby(fix, now))
        assertTrue(selection.propose(Double.NaN, 0.0).confirmNearby(fix, now) is
            NearbyLocationConfirmation.InvalidCoordinates)
    }

    @Test
    fun aNearbyPointPreservesReferenceAndCoordinatePrecision() {
        val selection = IncidentLocationSelection(original).propose(19.4326081234567, -99.1332077654321)
        val result = selection.confirmNearby(fix, now) as NearbyLocationConfirmation.Confirmed
        assertEquals(original.reference, result.location.reference)
        assertEquals(19.4326081234567.toRawBits(), result.location.latitude!!.toRawBits())
        assertEquals((-99.1332077654321).toRawBits(), result.location.longitude!!.toRawBits())
        assertSame(original, selection.cancel())
    }

    @Test
    fun geometricConfirmationDoesNotApproveFranceForADeviceInMexico() {
        val selection = IncidentLocationSelection(original).propose(48.8566, 2.3522)
        assertTrue(selection.confirm() is LocationConfirmation.Confirmed)
        val rejected = selection.confirmNearby(fix, now) as NearbyLocationConfirmation.Blocked
        assertEquals(NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS, rejected.rejection.reason)
        assertSame(original, selection.cancel())
    }

    @Test
    fun missingDeviceEvidenceCannotBeReplacedByTheChosenPin() {
        val selection = IncidentLocationSelection(original).propose(fix.latitude, fix.longitude)
        val rejected = selection.confirmNearby(null, now) as NearbyLocationConfirmation.Blocked
        assertEquals(NearbyIncidentRejection.DEVICE_LOCATION_REQUIRED, rejected.rejection.reason)
    }

    @Test
    fun expiredEvidenceCannotReuseAPreviousApproval() {
        val selection = IncidentLocationSelection(original).propose(fix.latitude, fix.longitude)
        assertTrue(selection.confirmNearby(fix, now) is NearbyLocationConfirmation.Confirmed)
        val expired = selection.confirmNearby(fix, now + 120_000_000_001L) as NearbyLocationConfirmation.Blocked
        assertEquals(NearbyIncidentRejection.STALE_DEVICE_LOCATION, expired.rejection.reason)
    }

    @Test
    fun replacingThePinRequiresCheckingTheNewPoint() {
        val nearby = IncidentLocationSelection(original).propose(fix.latitude, fix.longitude)
        val distant = nearby.propose(48.8566, 2.3522)
        assertTrue(nearby.confirmNearby(fix, now) is NearbyLocationConfirmation.Confirmed)
        assertTrue(distant.confirmNearby(fix, now) is NearbyLocationConfirmation.Blocked)
        assertTrue(distant.propose(fix.latitude, fix.longitude).confirmNearby(fix, now) is
            NearbyLocationConfirmation.Confirmed)
    }
}
