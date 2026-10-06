package com.example.citizensecurity.maps

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPermissionViewModelTest {
    private val stores = mutableListOf<ViewModelStore>()
    private val denied = LocationPermissionSnapshot(false, false, false)
    private val approximate = LocationPermissionSnapshot(false, true, false)
    private val precise = LocationPermissionSnapshot(true, true, false)

    @After
    fun clearModels() {
        stores.forEach { it.clear() }
    }

    @Test
    fun aNewModelDoesNotAssumePermissionOrPrepareARequest() {
        val model = model()
        assertTrue(model.isActive)
        assertEquals(LocationPermissionState(), model.state.value)
    }

    @Test
    fun preciseAccessDoesNotRequestPermissionAgain() {
        val model = model()
        assertEquals(LocationPermissionAction.AlreadyGranted, model.prepareRequest(precise))
        assertEquals(LocationPermissionState(LocationPermissionAccess.Precise), model.state.value)
    }

    @Test
    fun preciseAccessTakesPriorityOverAnInconsistentRationaleFlag() {
        val model = model()
        assertEquals(
            LocationPermissionAction.AlreadyGranted,
            model.prepareRequest(precise.copy(shouldExplain = true)),
        )
        assertNull(model.state.value.issue)
        assertFalse(model.state.value.requestInFlight)
    }

    @Test
    fun fineAccessIsCheckedIndependentlyOfTheCoarseFlag() {
        val model = model()
        model.inspect(precise.copy(approximateGranted = false))
        assertEquals(LocationPermissionAccess.Precise, model.state.value.access)
    }

    @Test
    fun approximateAccessStillNeedsAnExplicitPreciseRequest() {
        val model = model()
        model.inspect(approximate)
        assertFalse(model.state.value.requestInFlight)
        assertEquals(LocationPermissionAction.LaunchRequest, model.prepareRequest(approximate))
        assertEquals(LocationPermissionAccess.Approximate, model.state.value.access)
        assertTrue(model.state.value.requestInFlight)
    }

    @Test
    fun absenceOfRationaleDoesNotBecomeAPermanentDenial() {
        val model = model()
        assertEquals(LocationPermissionAction.LaunchRequest, model.prepareRequest(denied))
        assertEquals(LocationPermissionAccess.Denied, model.state.value.access)
        assertNull(model.state.value.issue)
    }

    @Test
    fun rationaleRequiresAcknowledgementBeforePreparingTheDialog() {
        val model = model()
        val snapshot = denied.copy(shouldExplain = true)
        assertEquals(LocationPermissionAction.ExplanationRequired, model.prepareRequest(snapshot))
        assertFalse(model.state.value.requestInFlight)
        assertEquals(LocationPermissionIssue.ExplanationRequired, model.state.value.issue)
        assertEquals(LocationPermissionAction.LaunchRequest, model.prepareRequest(snapshot, true))
        assertTrue(model.state.value.requestInFlight)
        assertNull(model.state.value.issue)
    }

    @Test
    fun decliningTheExplanationAndInspectingAgainDoNotOpenARequest() {
        val model = model()
        val snapshot = approximate.copy(shouldExplain = true)
        model.prepareRequest(snapshot)
        model.inspect(snapshot)
        assertEquals(LocationPermissionAccess.Approximate, model.state.value.access)
        assertTrue(model.state.value.shouldExplain)
        assertFalse(model.state.value.requestInFlight)
        assertNull(model.state.value.issue)
    }

    @Test
    fun repeatedGesturesPrepareOnlyOneRequest() {
        val model = model()
        assertEquals(LocationPermissionAction.LaunchRequest, model.prepareRequest(denied))
        repeat(5) {
            assertEquals(LocationPermissionAction.AlreadyRunning, model.prepareRequest(denied, true))
        }
        assertTrue(model.state.value.requestInFlight)
    }

    @Test
    fun aFreshInspectionUpdatesAccessWithoutClearingThePendingRequest() {
        val model = model()
        model.prepareRequest(denied)
        model.inspect(precise)
        assertEquals(LocationPermissionAccess.Precise, model.state.value.access)
        assertTrue(model.state.value.requestInFlight)
        assertEquals(LocationPermissionAction.AlreadyRunning, model.prepareRequest(precise))
        model.onPermissionResult(precise, false)
        assertFalse(model.state.value.requestInFlight)
    }

    @Test
    fun recreatingTheOwnerWithTheSameStoreRetainsTheRequest() {
        val store = ViewModelStore().also(stores::add)
        val first = ViewModelProvider.create(store, LocationPermissionViewModel.factory())
            .get(LocationPermissionViewModel::class.java)
        first.prepareRequest(denied)
        val recreated = ViewModelProvider.create(store, LocationPermissionViewModel.factory())
            .get(LocationPermissionViewModel::class.java)
        assertSame(first, recreated)
        assertEquals(LocationPermissionAction.AlreadyRunning, recreated.prepareRequest(denied))
        recreated.onPermissionResult(approximate, false)
        assertEquals(LocationPermissionAccess.Approximate, first.state.value.access)
        assertFalse(first.state.value.requestInFlight)
    }

    @Test
    fun aNewProcessModelDoesNotRestoreAnOldRequestOrPermissionAssumption() {
        val oldProcess = model()
        oldProcess.prepareRequest(denied)
        val newProcess = model()
        assertEquals(LocationPermissionState(), newProcess.state.value)
        newProcess.inspect(approximate)
        assertFalse(newProcess.state.value.requestInFlight)
        assertTrue(oldProcess.state.value.requestInFlight)
    }

    @Test
    fun aRestoredCallbackIsAcceptedWithoutPreparingAnotherRequest() {
        val model = model()
        model.onPermissionResult(precise, false)
        assertEquals(LocationPermissionState(LocationPermissionAccess.Precise), model.state.value)
        model.onPermissionResult(approximate, false)
        assertEquals(LocationPermissionState(LocationPermissionAccess.Approximate), model.state.value)
    }

    @Test
    fun anApproximateResultDoesNotRetryTheRequestAutomatically() {
        val model = model()
        model.prepareRequest(denied)
        model.onPermissionResult(approximate, false)
        assertEquals(LocationPermissionAccess.Approximate, model.state.value.access)
        assertFalse(model.state.value.requestInFlight)
        assertNull(model.state.value.issue)
    }

    @Test
    fun aDeniedResultIsAStateAndDoesNotRetryAutomatically() {
        val model = model()
        model.prepareRequest(denied)
        model.onPermissionResult(denied.copy(shouldExplain = true), false)
        assertEquals(LocationPermissionAccess.Denied, model.state.value.access)
        assertTrue(model.state.value.shouldExplain)
        assertFalse(model.state.value.requestInFlight)
        assertNull(model.state.value.issue)
    }

    @Test
    fun aCancelledDialogWithoutFineAccessClearsThePendingRequest() {
        val model = model()
        model.prepareRequest(denied)
        model.onPermissionResult(denied, true)
        assertEquals(LocationPermissionIssue.Cancelled, model.state.value.issue)
        assertEquals(LocationPermissionAccess.Denied, model.state.value.access)
        assertFalse(model.state.value.requestInFlight)
    }

    @Test
    fun cancellationCanPreserveExistingApproximateAccess() {
        val model = model()
        model.prepareRequest(approximate)
        model.onPermissionResult(approximate, true)
        assertEquals(LocationPermissionAccess.Approximate, model.state.value.access)
        assertEquals(LocationPermissionIssue.Cancelled, model.state.value.issue)
    }

    @Test
    fun aCancelledCallbackDoesNotOverrideCurrentlyGrantedFineAccess() {
        val model = model()
        model.prepareRequest(denied)
        model.onPermissionResult(precise, true)
        assertEquals(LocationPermissionState(LocationPermissionAccess.Precise), model.state.value)
    }

    @Test
    fun aFailedPermissionCheckCannotPreserveAnAccessClaim() {
        val model = model()
        model.inspect(precise)
        model.onPermissionCheckFailed()
        assertEquals(
            LocationPermissionState(issue = LocationPermissionIssue.PermissionCheckFailed),
            model.state.value,
        )
    }

    @Test
    fun aFailedPermissionCheckKeepsAPendingDialogToPreventDuplicates() {
        val model = model()
        model.prepareRequest(denied)
        model.onPermissionCheckFailed()
        assertEquals(LocationPermissionAccess.Unknown, model.state.value.access)
        assertTrue(model.state.value.requestInFlight)
        assertEquals(LocationPermissionIssue.PermissionCheckFailed, model.state.value.issue)
        assertEquals(LocationPermissionAction.AlreadyRunning, model.prepareRequest(denied))
    }

    @Test
    fun aFailedCheckDuringTheFinalCallbackReleasesTheRequestForAnExplicitRetry() {
        val model = model()
        model.prepareRequest(denied)
        model.onPermissionCheckFailed(requestFinished = true)
        assertEquals(LocationPermissionAccess.Unknown, model.state.value.access)
        assertFalse(model.state.value.requestInFlight)
        assertEquals(LocationPermissionIssue.PermissionCheckFailed, model.state.value.issue)
        assertEquals(LocationPermissionAction.LaunchRequest, model.prepareRequest(denied))
    }

    @Test
    fun aSuccessfulInspectionCanRecoverAfterThePermissionServiceFails() {
        val model = model()
        model.onPermissionCheckFailed()
        model.inspect(precise)
        assertEquals(LocationPermissionState(LocationPermissionAccess.Precise), model.state.value)
    }

    @Test
    fun aLaunchFailureAllowsAnotherExplicitGesture() {
        val model = model()
        model.prepareRequest(approximate)
        model.onRequestLaunchFailed()
        assertEquals(LocationPermissionAccess.Approximate, model.state.value.access)
        assertFalse(model.state.value.requestInFlight)
        assertEquals(LocationPermissionIssue.RequestLaunchFailed, model.state.value.issue)
        assertEquals(LocationPermissionAction.LaunchRequest, model.prepareRequest(approximate))
        assertNull(model.state.value.issue)
    }

    @Test
    fun revocationIsReflectedWithoutStartingARequest() {
        val model = model()
        model.inspect(precise)
        model.inspect(approximate)
        assertEquals(LocationPermissionAccess.Approximate, model.state.value.access)
        model.inspect(denied)
        assertEquals(LocationPermissionAccess.Denied, model.state.value.access)
        assertFalse(model.state.value.requestInFlight)
    }

    @Test
    fun clearingTheModelMakesEveryLateCallbackAndGestureInert() {
        val store = ViewModelStore().also(stores::add)
        val model = ViewModelProvider.create(store, LocationPermissionViewModel.factory())
            .get(LocationPermissionViewModel::class.java)
        model.prepareRequest(denied)
        store.clear()
        assertFalse(model.isActive)
        assertFalse(model.state.value.requestInFlight)
        val closedState = model.state.value
        model.inspect(precise)
        model.onPermissionResult(precise, false)
        model.onPermissionCheckFailed()
        model.onRequestLaunchFailed()
        assertEquals(LocationPermissionAction.Inactive, model.prepareRequest(precise, true))
        assertEquals(closedState, model.state.value)
    }

    private fun model(): LocationPermissionViewModel {
        val store = ViewModelStore().also(stores::add)
        return ViewModelProvider.create(store, LocationPermissionViewModel.factory())
            .get(LocationPermissionViewModel::class.java)
    }
}
