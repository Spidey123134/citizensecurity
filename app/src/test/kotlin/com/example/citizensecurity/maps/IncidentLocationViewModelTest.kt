package com.example.citizensecurity.maps

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.citizensecurity.data.PhoneLocationRefreshResult
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.LocationProposal
import com.example.citizensecurity.domain.NearbyIncidentDecision
import com.example.citizensecurity.domain.NearbyIncidentRejection
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.ReportLocation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentLocationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val original = ReportLocation("  Frente al parque 🏡  ", 19.4326077, -99.1332088)
    private val now = 300_000_000_000L
    private val fix = DeviceLocationFix(19.4326077, -99.1332088, 20.0, now, false)

    @Before
    fun configureMain() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun clearModels() {
        try {
            stores.forEach { it.clear() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun constructingAndTappingDoNotCaptureOrTreatOriginalCoordinatesAsANewPoint() = runTest(dispatcher) {
        var calls = 0
        var evidenceReads = 0
        val model = model(refresh = { calls++; PhoneLocationRefreshResult.Ready(fix) }, evidence = {
            evidenceReads++; fix
        })
        runCurrent()
        assertEquals(IncidentLocationState.Idle(), model.state.value)
        assertSame(NearbyLocationConfirmation.NoSelection, model.confirm())
        assertEquals(0, calls)
        model.select(19.433, -99.134)
        assertTrue(model.state.value is IncidentLocationState.PointSelected)
        assertEquals(0, calls)
        // IncidentMapSession consulta evidencia al confirmar; un toque nunca la consulta.
        assertEquals(1, evidenceReads)
    }

    @Test
    fun aSelectedPinCannotProvideItsOwnMissingPhoneEvidence() = runTest(dispatcher) {
        val model = model(evidence = { null })
        model.select(fix.latitude, fix.longitude)
        val result = model.confirm() as NearbyLocationConfirmation.Blocked
        assertEquals(NearbyIncidentRejection.DEVICE_LOCATION_REQUIRED, result.rejection.reason)
        assertTrue(model.state.value is IncidentLocationState.Error)
    }

    @Test
    fun readyPhoneEvidenceWithoutAPinDoesNotConfirmAnIncident() = runTest(dispatcher) {
        val model = model()
        model.refreshLocation()
        assertTrue(model.state.value is IncidentLocationState.Capturing)
        runCurrent()
        assertEquals(IncidentLocationState.Idle(fix), model.state.value)
        assertSame(NearbyLocationConfirmation.NoSelection, model.confirm())
        assertSame(IncidentLocationIssue.NoSelection, (model.state.value as IncidentLocationState.Error).issue)
    }

    @Test
    fun doubleTapsRequestOnlyOneCaptureAndConfirmationWaitsForItsCompletion() = runTest(dispatcher) {
        val completion = CompletableDeferred<PhoneLocationRefreshResult>()
        var calls = 0
        var evidenceReads = 0
        val model = model(refresh = { calls++; completion.await() }, evidence = { evidenceReads++; fix })
        model.select(fix.latitude, fix.longitude)
        model.refreshLocation()
        repeat(5) { model.refreshLocation() }
        runCurrent()
        assertEquals(1, calls)
        assertNull(model.confirm())
        assertEquals(0, evidenceReads)
        assertTrue(model.state.value is IncidentLocationState.Capturing)
        completion.complete(PhoneLocationRefreshResult.Ready(fix))
        runCurrent()
        assertTrue(model.confirm() is NearbyLocationConfirmation.Confirmed)
        assertEquals(2, evidenceReads)
    }

    @Test
    fun tappingDuringCaptureKeepsTheLastPinAndConfirmationPreservesTheOriginalReference() = runTest(dispatcher) {
        val completion = CompletableDeferred<PhoneLocationRefreshResult>()
        val model = model(refresh = { completion.await() })
        model.select(48.8566, 2.3522)
        model.refreshLocation()
        runCurrent()
        model.select(19.4326081234567, -99.1332077654321)
        val point = (model.state.value as IncidentLocationState.Capturing).proposal as LocationProposal.ValidPoint
        completion.complete(PhoneLocationRefreshResult.Ready(fix))
        runCurrent()
        val result = model.confirm() as NearbyLocationConfirmation.Confirmed
        assertEquals(point.latitude.toRawBits(), result.location.latitude!!.toRawBits())
        assertEquals(point.longitude.toRawBits(), result.location.longitude!!.toRawBits())
        assertEquals(original.reference, result.location.reference)
        assertTrue(model.state.value is IncidentLocationState.Confirmed)
    }

    @Test
    fun failedRefreshPreservesThePinAndRequiresANewSuccessEvenIfOldEvidenceRemains() = runTest(dispatcher) {
        var result: PhoneLocationRefreshResult = PhoneLocationRefreshResult.TimedOut
        val model = model(refresh = { result })
        model.select(fix.latitude, fix.longitude)
        val point = model.state.value.proposal
        model.refreshLocation()
        runCurrent()
        val error = model.state.value as IncidentLocationState.Error
        assertEquals(point, error.proposal)
        assertSame(IncidentLocationIssue.TimedOut, error.issue)
        assertNull(model.confirm())
        assertSame(IncidentLocationIssue.RefreshRequired, (model.state.value as IncidentLocationState.Error).issue)
        result = PhoneLocationRefreshResult.Ready(fix)
        model.refreshLocation()
        runCurrent()
        assertTrue(model.confirm() is NearbyLocationConfirmation.Confirmed)
        assertEquals(original.reference, (model.state.value as IncidentLocationState.Confirmed).location.reference)
    }

    @Test
    fun eachRefreshFailureHasAnActionableIssueAndDoesNotMoveThePin() = runTest(dispatcher) {
        val rejected = NearbyIncidentDecision.Rejected(NearbyIncidentRejection.MOCK_DEVICE_LOCATION, "Usa una lectura real.")
        val failures = listOf(
            PhoneLocationRefreshResult.PermissionRequired to IncidentLocationIssue.PermissionRequired,
            PhoneLocationRefreshResult.LocationDisabled to IncidentLocationIssue.LocationDisabled,
            PhoneLocationRefreshResult.Unavailable to IncidentLocationIssue.Unavailable,
            PhoneLocationRefreshResult.TimedOut to IncidentLocationIssue.TimedOut,
            PhoneLocationRefreshResult.AlreadyRunning to IncidentLocationIssue.CaptureAlreadyRunning,
            PhoneLocationRefreshResult.Rejected(rejected) to IncidentLocationIssue.Blocked(rejected),
        )
        failures.forEach { (result, issue) ->
            val model = model(refresh = { result })
            model.select(fix.latitude, fix.longitude)
            val pin = model.state.value.proposal
            model.refreshLocation()
            runCurrent()
            val error = model.state.value as IncidentLocationState.Error
            assertEquals(issue, error.issue)
            assertFalse(error.issue.message.isBlank())
            assertEquals(pin, error.proposal)
            assertSame(original, model.cancel())
        }
    }

    @Test
    fun readyResponseWithoutPublishedEvidenceCannotAuthorizeThePin() = runTest(dispatcher) {
        val model = model(evidence = { null })
        model.select(fix.latitude, fix.longitude)
        model.refreshLocation()
        runCurrent()
        val issue = (model.state.value as IncidentLocationState.Error).issue as IncidentLocationIssue.Blocked
        assertEquals(NearbyIncidentRejection.DEVICE_LOCATION_REQUIRED, issue.rejection.reason)
        assertNull(model.confirm())
    }

    @Test
    fun stoppingDuringTheFinalEvidenceCheckCannotPublishReadyState() = runTest(dispatcher) {
        lateinit var model: IncidentLocationViewModel
        model = this@IncidentLocationViewModelTest.model(clock = { model.onStop(); now })
        model.select(fix.latitude, fix.longitude)
        model.refreshLocation()
        runCurrent()
        val point = model.state.value as IncidentLocationState.PointSelected
        assertNull(point.deviceFix)
        assertNull(model.confirm())
        assertSame(IncidentLocationIssue.RefreshRequired, (model.state.value as IncidentLocationState.Error).issue)
    }

    @Test
    fun revokingPermissionAfterReadyIsCheckedAgainBeforeConfirming() = runTest(dispatcher) {
        var evidence: DeviceLocationFix? = fix
        val model = model(evidence = { evidence })
        model.select(fix.latitude, fix.longitude)
        model.refreshLocation()
        runCurrent()
        evidence = null
        val result = model.confirm() as NearbyLocationConfirmation.Blocked
        assertEquals(NearbyIncidentRejection.DEVICE_LOCATION_REQUIRED, result.rejection.reason)
        assertTrue(model.state.value is IncidentLocationState.Error)
    }

    @Test
    fun evidenceCanExpireBetweenReadyAndConfirmation() = runTest(dispatcher) {
        var currentTime = now
        val model = model(clock = { currentTime })
        model.select(fix.latitude, fix.longitude)
        model.refreshLocation()
        runCurrent()
        currentTime += 120_000_000_001L
        val result = model.confirm() as NearbyLocationConfirmation.Blocked
        assertEquals(NearbyIncidentRejection.STALE_DEVICE_LOCATION, result.rejection.reason)
    }

    @Test
    fun stoppingWhileCaptureIsQueuedDoesNotInvokeTheSourceAndKeepsThePin() = runTest(dispatcher) {
        var calls = 0
        val model = model(refresh = { calls++; PhoneLocationRefreshResult.Ready(fix) })
        model.select(fix.latitude, fix.longitude)
        val pin = model.state.value.proposal
        model.refreshLocation()
        model.onStop()
        runCurrent()
        assertEquals(0, calls)
        assertEquals(pin, model.state.value.proposal)
        assertTrue(model.state.value is IncidentLocationState.PointSelected)
        assertNull(model.confirm())
    }

    @Test
    fun stoppingRequiresAnExplicitRefreshEvenWhenPhoneEvidenceHasNotExpired() = runTest(dispatcher) {
        val model = model()
        model.select(fix.latitude, fix.longitude)
        model.onStop()
        assertNull(model.confirm())
        model.refreshLocation()
        runCurrent()
        assertTrue(model.confirm() is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun aLateResponseThatIgnoresCancellationCannotChangeAStoppedOrCancelledFlow() = runTest(dispatcher) {
        val completion = CompletableDeferred<PhoneLocationRefreshResult>()
        val model = model(refresh = { withContext(NonCancellable) { completion.await() } })
        model.select(fix.latitude, fix.longitude)
        model.refreshLocation()
        runCurrent()
        model.onStop()
        val stopped = model.state.value
        completion.complete(PhoneLocationRefreshResult.Ready(fix))
        runCurrent()
        assertEquals(stopped, model.state.value)
        assertNull(model.confirm())
        assertSame(original, model.cancel())
        assertEquals(IncidentLocationState.Cancelled(original), model.state.value)
    }

    @Test
    fun anAbandonedRequestCannotOverrideANewerSuccessfulRequest() = runTest(dispatcher) {
        val oldCompletion = CompletableDeferred<PhoneLocationRefreshResult>()
        var calls = 0
        val model = model(refresh = {
            if (++calls == 1) withContext(NonCancellable) { oldCompletion.await() }
            else PhoneLocationRefreshResult.Ready(fix)
        })
        model.select(fix.latitude, fix.longitude)
        model.refreshLocation()
        runCurrent()
        model.onStop()
        model.refreshLocation()
        runCurrent()
        assertTrue(model.state.value is IncidentLocationState.PointSelected)
        val ready = model.state.value
        oldCompletion.complete(PhoneLocationRefreshResult.PermissionRequired)
        runCurrent()
        assertEquals(ready, model.state.value)
        assertEquals(2, calls)
        assertTrue(model.confirm() is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun cancellingBeforeCaptureStartsRestoresTheExactOriginalAndIgnoresAllLateActions() = runTest(dispatcher) {
        var calls = 0
        val model = model(refresh = { calls++; PhoneLocationRefreshResult.Ready(fix) })
        model.select(48.8566, 2.3522)
        model.refreshLocation()
        assertSame(original, model.cancel())
        runCurrent()
        model.select(fix.latitude, fix.longitude)
        model.refreshLocation()
        model.onStop()
        assertNull(model.confirm())
        assertSame(original, model.cancel())
        assertEquals(0, calls)
        assertEquals(IncidentLocationState.Cancelled(original), model.state.value)
    }

    @Test
    fun clearingTheStoreClosesTheSessionAndCancelsItsCapture() = runTest(dispatcher) {
        val completion = CompletableDeferred<PhoneLocationRefreshResult>()
        val store = store()
        val model = model(refresh = { completion.await() }, store = store)
        model.select(fix.latitude, fix.longitude)
        model.refreshLocation()
        runCurrent()
        store.clear()
        runCurrent()
        assertEquals(IncidentLocationState.Cancelled(original), model.state.value)
        completion.complete(PhoneLocationRefreshResult.Ready(fix))
        model.refreshLocation()
        runCurrent()
        assertNull(model.confirm())
        assertEquals(IncidentLocationState.Cancelled(original), model.state.value)
    }

    @Test
    fun rotatingWithTheSameStoreRetainsOneModelAndDoesNotRestartCapture() = runTest(dispatcher) {
        val store = store()
        var calls = 0
        val factory = factory(refresh = { calls++; PhoneLocationRefreshResult.Ready(fix) })
        val first = ViewModelProvider(ownerOf(store), factory)[IncidentLocationViewModel::class.java]
        first.select(fix.latitude, fix.longitude)
        first.refreshLocation()
        runCurrent()
        val restored = ViewModelProvider(ownerOf(store), factory)[IncidentLocationViewModel::class.java]
        assertSame(first, restored)
        assertTrue(restored.state.value is IncidentLocationState.PointSelected)
        assertEquals(1, calls)
    }

    @Test
    fun cancellingAfterConfirmationPreservesAcceptedCoordinatesAndCannotDeliverThemTwice() = runTest(dispatcher) {
        val model = model()
        model.select(19.433, -99.134)
        val accepted = (model.confirm() as NearbyLocationConfirmation.Confirmed).location
        val confirmed = model.state.value
        model.onStop()
        assertSame(accepted, model.cancel())
        assertSame(accepted, model.cancel())
        assertEquals(confirmed, model.state.value)
        model.select(48.8566, 2.3522)
        model.refreshLocation()
        runCurrent()
        assertEquals(confirmed, model.state.value)
        assertNull(model.confirm())
    }

    @Test
    fun aNewInvalidPinCannotReuseThePreviouslyValidCoordinates() = runTest(dispatcher) {
        val model = model()
        model.select(fix.latitude, fix.longitude)
        model.select(Double.NaN, -99.0)
        assertTrue((model.state.value as IncidentLocationState.Error).issue is IncidentLocationIssue.InvalidPoint)
        assertTrue(model.confirm() is NearbyLocationConfirmation.InvalidCoordinates)
        model.select(fix.latitude, fix.longitude)
        assertTrue(model.confirm() is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun aDistantPinCanBeCorrectedWithoutStartingAnotherSession() = runTest(dispatcher) {
        val model = model()
        model.select(48.8566, 2.3522)
        val result = model.confirm() as NearbyLocationConfirmation.Blocked
        assertEquals(NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS, result.rejection.reason)
        model.select(fix.latitude, fix.longitude)
        assertTrue(model.confirm() is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun providerExceptionsDoNotEscapeToTheScreenAndAllowRetry() = runTest(dispatcher) {
        var calls = 0
        val model = model(refresh = {
            if (++calls == 1) throw IllegalStateException("No expongas datos internos al usuario.")
            PhoneLocationRefreshResult.Ready(fix)
        })
        model.select(fix.latitude, fix.longitude)
        model.refreshLocation()
        runCurrent()
        assertSame(IncidentLocationIssue.Unavailable, (model.state.value as IncidentLocationState.Error).issue)
        model.refreshLocation()
        runCurrent()
        assertTrue(model.confirm() is NearbyLocationConfirmation.Confirmed)
    }

    @Test
    fun permissionExceptionsWhileCapturingOrConfirmingHaveAnExplicitPermissionIssue() = runTest(dispatcher) {
        val capture = model(refresh = { throw SecurityException("Permiso revocado") })
        capture.refreshLocation()
        runCurrent()
        assertSame(IncidentLocationIssue.PermissionRequired, (capture.state.value as IncidentLocationState.Error).issue)
        val confirmation = model(evidence = { throw SecurityException("Permiso revocado") })
        confirmation.select(fix.latitude, fix.longitude)
        assertNull(confirmation.confirm())
        assertSame(IncidentLocationIssue.PermissionRequired, (confirmation.state.value as IncidentLocationState.Error).issue)
    }

    @Test
    fun reopeningAfterConfirmationRequiresExplicitRenewalAndUsesTheNewOriginalReference() = runTest(dispatcher) {
        var calls = 0
        val model = model(refresh = { calls++; PhoneLocationRefreshResult.Ready(fix) })
        model.select(fix.latitude, fix.longitude)
        model.refreshLocation()
        runCurrent()
        assertTrue(model.confirm() is NearbyLocationConfirmation.Confirmed)
        val nextOriginal = ReportLocation("  Otro incidente junto al parque  ", 19.434, -99.134)
        assertTrue(model.beginSelection(nextOriginal))
        assertEquals(IncidentLocationState.Idle(), model.state.value)
        assertEquals(1, calls)
        model.select(fix.latitude, fix.longitude)
        assertNull(model.confirm())
        assertSame(IncidentLocationIssue.RefreshRequired, (model.state.value as IncidentLocationState.Error).issue)
        model.refreshLocation()
        runCurrent()
        val accepted = model.confirm() as NearbyLocationConfirmation.Confirmed
        assertEquals(nextOriginal.reference, accepted.location.reference)
        assertEquals(2, calls)
    }

    @Test
    fun reopeningAfterCancellationStartsOneNewSelectionAndCancellationReturnsItsExactOriginal() = runTest(dispatcher) {
        val model = model()
        model.select(48.8566, 2.3522)
        assertSame(original, model.cancel())
        val nextOriginal = ReportLocation("  Entrada de otra calle  ", 19.435, -99.135)
        assertTrue(model.beginSelection(nextOriginal))
        assertEquals(IncidentLocationState.Idle(), model.state.value)
        model.select(fix.latitude, fix.longitude)
        assertNull(model.confirm())
        assertSame(nextOriginal, model.cancel())
        assertEquals(IncidentLocationState.Cancelled(nextOriginal), model.state.value)
        model.select(48.8566, 2.3522)
        assertEquals(IncidentLocationState.Cancelled(nextOriginal), model.state.value)
    }

    @Test
    fun beginningAnotherSelectionInvalidatesACaptureThatIgnoresCancellation() = runTest(dispatcher) {
        val late = CompletableDeferred<PhoneLocationRefreshResult>()
        var calls = 0
        val model = model(refresh = {
            if (++calls == 1) withContext(NonCancellable) { late.await() }
            else PhoneLocationRefreshResult.Ready(fix)
        })
        try {
            model.select(48.8566, 2.3522)
            model.refreshLocation()
            runCurrent()
            val nextOriginal = ReportLocation("Segunda selección", 19.434, -99.134)
            assertTrue(model.beginSelection(nextOriginal))
            assertEquals(IncidentLocationState.Idle(), model.state.value)
            model.select(fix.latitude, fix.longitude)
            model.refreshLocation()
            runCurrent()
            val ready = model.state.value
            late.complete(PhoneLocationRefreshResult.Unavailable)
            runCurrent()
            assertEquals(ready, model.state.value)
            val accepted = model.confirm() as NearbyLocationConfirmation.Confirmed
            assertEquals(nextOriginal.reference, accepted.location.reference)
            assertEquals(2, calls)
        } finally {
            late.complete(PhoneLocationRefreshResult.Unavailable)
        }
    }

    @Test
    fun clearingTheStorePreventsReopeningAnySelection() = runTest(dispatcher) {
        val store = store()
        val model = model(store = store)
        store.clear()
        val closed = model.state.value
        assertFalse(model.beginSelection(ReportLocation("Otra ubicación", 19.435, -99.135)))
        assertEquals(closed, model.state.value)
    }

    private fun model(
        refresh: suspend () -> PhoneLocationRefreshResult = { PhoneLocationRefreshResult.Ready(fix) },
        evidence: () -> DeviceLocationFix? = { fix },
        clock: () -> Long = { now },
        store: ViewModelStore = store(),
    ) = ViewModelProvider(ownerOf(store), factory(refresh, evidence, clock))[IncidentLocationViewModel::class.java]

    private fun factory(
        refresh: suspend () -> PhoneLocationRefreshResult = { PhoneLocationRefreshResult.Ready(fix) },
        evidence: () -> DeviceLocationFix? = { fix },
        clock: () -> Long = { now },
    ) = IncidentLocationViewModel.factory(original, refresh, evidence, clock)

    private fun store() = ViewModelStore().also(stores::add)

    private fun ownerOf(store: ViewModelStore) = object : ViewModelStoreOwner {
        override val viewModelStore = store
    }
}
