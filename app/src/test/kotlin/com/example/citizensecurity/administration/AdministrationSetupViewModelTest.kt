package com.example.citizensecurity.administration

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.LocalAccount
import com.example.citizensecurity.domain.LocalAdministratorAssignment
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdministrationSetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun configureMain() = Dispatchers.setMain(dispatcher)
    @After fun closeModels() {
        try { stores.forEach { it.clear() } } finally { Dispatchers.resetMain() }
    }

    @Test fun constructingAndCheckingAnEmptyStoreNeverAssignsOrAcceptsUnsolicitedResults() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = model(fixture)
        assertSame(AdministrationSetupState.Idle, model.state.value)
        assertEquals(0, fixture.reads)
        assertFalse(model.credentialResult(true, true))
        assertNull(model.beginCredential(true))
        assertFalse(model.activateAdministrator { true })
        model.activate()
        runCurrent()
        assertTrue(model.state.value is AdministrationSetupState.Available)
        assertEquals(1, fixture.reads)
        model.activate()
        runCurrent()
        assertEquals(1, fixture.reads)
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun anAcceptedNativeResultOnlyConfirmsTheOwnerAndCannotBeReplayed() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = model(fixture)
        model.activate(); runCurrent()
        val attempt = requireNotNull(model.beginCredential(true))
        assertSame(AdministrationSetupState.AwaitingCredential, model.state.value)
        assertTrue(model.credentialResult(true, true))
        runCurrent()
        assertSame(AdministrationSetupState.OwnerConfirmed, model.state.value)
        assertFalse(model.credentialResult(true, true))
        assertTrue(fixture.writes.isEmpty())
        assertNull(fixture.saved)
        assertNotNull(attempt.operationId)
    }

    @Test fun aSecondGestureCommitsTheExactAccountOperationAndCapturedTimestampOnce() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = model(fixture)
        model.activate(); runCurrent()
        val attempt = requireNotNull(model.beginCredential(true))
        assertTrue(model.credentialResult(true, true))
        assertTrue(model.activateAdministrator { true })
        assertSame(AdministrationSetupState.Assigning, model.state.value)
        runCurrent()
        val assigned = (model.state.value as AdministrationSetupState.Assigned).assignment
        assertEquals(ACCOUNT.id, assigned.accountId)
        assertEquals(ACCOUNT.username, assigned.username)
        assertEquals(attempt.operationId, assigned.operationId)
        assertEquals(TIME, assigned.grantedAt)
        assertEquals(listOf(attempt.operationId to TIME), fixture.writes)
        assertTrue(fixture.storeGuardCalls >= 2)
        assertFalse(model.activateAdministrator { true })
        assertFalse(model.credentialResult(true, true))
        assertEquals(1, fixture.writes.size)
    }

    @Test fun academicAccessAndAnotherRegisteredUsernameCannotObtainTheGrant() = runTest(dispatcher) {
        listOf(null, LocalAccount(OTHER_ID, "persona_local")).forEach { account ->
            val fixture = Fixture(account)
            val model = model(fixture)
            model.activate(); runCurrent()
            assertTrue(model.state.value is AdministrationSetupState.Blocked)
            assertNull(model.beginCredential(true))
            assertFalse(model.credentialResult(true, true))
            assertFalse(model.activateAdministrator { true })
            assertTrue(fixture.writes.isEmpty())
        }
    }

    @Test fun anExistingAssignmentForAnotherAccountIsKeptAndCannotBeReplaced() = runTest(dispatcher) {
        val fixture = Fixture().apply { saved = assignment(accountId = OTHER_ID) }
        val original = fixture.saved
        val model = model(fixture)
        model.activate(); runCurrent()
        assertTrue(model.state.value is AdministrationSetupState.Blocked)
        assertNull(model.beginCredential(true))
        assertFalse(model.activateAdministrator { true })
        assertSame(original, fixture.saved)
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun anExistingMatchingAssignmentIsReadWithoutReenrollingOrCreatingAnotherOperation() = runTest(dispatcher) {
        val fixture = Fixture().apply { saved = assignment() }
        val model = model(fixture)
        model.activate(); runCurrent()
        assertSame(fixture.saved, (model.state.value as AdministrationSetupState.Assigned).assignment)
        assertNull(model.beginCredential(true))
        assertFalse(model.activateAdministrator { true })
        assertTrue(model.reload())
        runCurrent()
        assertSame(fixture.saved, (model.state.value as AdministrationSetupState.Assigned).assignment)
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun cancellationAndRemovingTheDeviceLockNeverYieldAnOwnerProof() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = model(fixture)
        model.activate(); runCurrent()
        assertNull(model.beginCredential(false))
        assertTrue(model.state.value is AdministrationSetupState.Available)
        assertNotNull(model.beginCredential(true))
        assertFalse(model.credentialResult(false, true))
        assertFalse(model.activateAdministrator { true })
        assertNotNull(model.beginCredential(true))
        assertFalse(model.credentialResult(true, false))
        assertFalse(model.activateAdministrator { true })
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun anOlderLaunchFailureCannotDiscardTheCurrentNativeAttempt() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = model(fixture)
        model.activate(); runCurrent()
        val first = requireNotNull(model.beginCredential(true))
        assertNull(model.beginCredential(true))
        assertFalse(model.reload())
        model.launchFailed(first)
        assertTrue(model.state.value is AdministrationSetupState.Available)
        val second = requireNotNull(model.beginCredential(true))
        assertNotEquals(first.operationId, second.operationId)
        model.launchFailed(first)
        assertSame(AdministrationSetupState.AwaitingCredential, model.state.value)
        assertTrue(model.credentialResult(true, true))
        model.launchFailed(second)
        assertSame(AdministrationSetupState.OwnerConfirmed, model.state.value)
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun aProofExpiresBothDuringNativeConfirmationAndBeforeTheSecondGesture() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = model(fixture)
        model.activate(); runCurrent()
        assertNotNull(model.beginCredential(true))
        fixture.elapsed += AdministrationSetupViewModel.PROOF_LIFETIME_NANOS + 1
        assertFalse(model.credentialResult(true, true))
        assertTrue(model.state.value is AdministrationSetupState.Available)
        assertNotNull(model.beginCredential(true))
        assertTrue(model.credentialResult(true, true))
        fixture.elapsed += AdministrationSetupViewModel.PROOF_LIFETIME_NANOS + 1
        assertFalse(model.activateAdministrator { true })
        assertTrue(model.state.value is AdministrationSetupState.Available)
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun monotonicRollbackCannotExtendAProofOrApproveANativeResult() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = model(fixture)
        model.activate(); runCurrent()
        assertNotNull(model.beginCredential(true))
        fixture.elapsed--
        assertFalse(model.credentialResult(true, true))
        fixture.elapsed = 5_000L
        assertNotNull(model.beginCredential(true))
        assertTrue(model.credentialResult(true, true))
        fixture.elapsed--
        assertFalse(model.activateAdministrator { true })
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun duplicateActivationAndReloadAreRejectedWhileOneAssignmentIsPending() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = model(fixture)
        model.activate(); runCurrent()
        assertNotNull(model.beginCredential(true))
        assertTrue(model.credentialResult(true, true))
        assertTrue(model.activateAdministrator { true })
        assertFalse(model.activateAdministrator { true })
        assertFalse(model.reload())
        assertNull(model.beginCredential(true))
        assertTrue(fixture.writes.isEmpty())
        runCurrent()
        assertEquals(1, fixture.writes.size)
        assertTrue(model.state.value is AdministrationSetupState.Assigned)
    }

    @Test fun sessionRevocationBeforeCredentialAndInsideTheStoreGuardCannotGrant() = runTest(dispatcher) {
        val firstFixture = Fixture()
        val first = model(firstFixture)
        first.activate(); runCurrent()
        firstFixture.current = false
        assertNull(first.beginCredential(true))
        assertTrue(first.state.value is AdministrationSetupState.Blocked)
        assertTrue(firstFixture.writes.isEmpty())

        val fixture = Fixture()
        fixture.assignAnswer = { _, _, guard ->
            fixture.current = false
            guard()
            error("A revoked store guard must prevent a commit")
        }
        val model = model(fixture)
        model.activate(); runCurrent()
        assertNotNull(model.beginCredential(true))
        assertTrue(model.credentialResult(true, true))
        assertTrue(model.activateAdministrator { true })
        runCurrent()
        assertFalse(model.state.value is AdministrationSetupState.Assigned)
        assertNull(fixture.saved)
        assertFalse(model.activateAdministrator { true })
    }

    @Test fun losingTheResumedGestureAtCommitForcesARecheckWithoutAssigning() = runTest(dispatcher) {
        val fixture = Fixture()
        var eligible = true
        fixture.assignAnswer = { _, _, guard ->
            eligible = false
            guard()
            error("The final foreground check must prevent a commit")
        }
        val model = model(fixture)
        model.activate(); runCurrent()
        assertNotNull(model.beginCredential(true))
        assertTrue(model.credentialResult(true, true))
        assertTrue(model.activateAdministrator { eligible })
        runCurrent()
        assertTrue(model.state.value is AdministrationSetupState.Error)
        assertNull(fixture.saved)
        eligible = true
        assertFalse(model.activateAdministrator { eligible })
        assertNull(model.beginCredential(true))
    }

    @Test fun aCommittedButUnconfirmedAssignmentNeedsExplicitReadingRatherThanAnotherWrite() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.assignAnswer = { operation, time, guard ->
            guard()
            fixture.saved = assignment(operationId = operation, at = time)
            throw IOException("Write outcome unavailable")
        }
        val model = model(fixture)
        model.activate(); runCurrent()
        val attempt = requireNotNull(model.beginCredential(true))
        assertTrue(model.credentialResult(true, true))
        assertTrue(model.activateAdministrator { true })
        runCurrent()
        assertTrue(model.state.value is AdministrationSetupState.Error)
        assertEquals(attempt.operationId, fixture.saved?.operationId)
        assertNull(model.beginCredential(true))
        assertFalse(model.activateAdministrator { true })
        assertEquals(1, fixture.writes.size)
        assertTrue(model.reload())
        runCurrent()
        assertSame(fixture.saved, (model.state.value as AdministrationSetupState.Assigned).assignment)
        assertEquals(1, fixture.writes.size)
        assertEquals(2, fixture.reads)
    }

    @Test fun aCorruptStoreIsAnErrorRatherThanAnEmptyAssignmentAndLeaksNoPrivateCause() = runTest(dispatcher) {
        val fixture = Fixture()
        fixture.readAnswer = { throw IOException("Private /files/admin.json with PIN=123456") }
        val model = model(fixture)
        model.activate(); runCurrent()
        val failure = model.state.value as AdministrationSetupState.Error
        assertFalse(failure.message.contains("123456"))
        assertFalse(failure.message.contains("admin.json"))
        assertNull(model.beginCredential(true))
        assertFalse(model.activateAdministrator { true })
        assertTrue(fixture.writes.isEmpty())
        fixture.readAnswer = { fixture.saved }
        assertTrue(model.reload())
        runCurrent()
        assertTrue(model.state.value is AdministrationSetupState.Available)
        assertTrue(fixture.writes.isEmpty())
    }

    @Test fun closingTheCapturedModelDiscardsPendingNativeResultsAndOwnerProofs() = runTest(dispatcher) {
        val fixture = Fixture()
        val model = model(fixture)
        model.activate(); runCurrent()
        val attempt = requireNotNull(model.beginCredential(true))
        model.close()
        assertFalse(model.credentialResult(true, true))
        model.launchFailed(attempt)
        assertTrue(model.state.value is AdministrationSetupState.Blocked)
        assertFalse(model.reload())
        assertFalse(model.activateAdministrator { true })
        assertTrue(fixture.writes.isEmpty())

        val confirmed = model(Fixture())
        confirmed.activate(); runCurrent()
        assertNotNull(confirmed.beginCredential(true))
        assertTrue(confirmed.credentialResult(true, true))
        confirmed.close()
        assertFalse(confirmed.activateAdministrator { true })
        assertTrue(confirmed.state.value is AdministrationSetupState.Blocked)
    }

    private fun model(fixture: Fixture): AdministrationSetupViewModel {
        val store = ViewModelStore().also(stores::add)
        return ViewModelProvider(store, viewModelFactory {
            initializer {
                AdministrationSetupViewModel(
                    account = fixture.account,
                    checkCurrent = { check(fixture.current) { "Captured session ended" } },
                    read = { fixture.reads++; fixture.readAnswer() },
                    assign = { operation, at, guard ->
                        fixture.writes += operation to at
                        fixture.assignAnswer(operation, at, guard)
                    },
                    dispatcher = dispatcher,
                    clock = Clock.fixed(TIME, ZoneOffset.UTC),
                    elapsedNanos = { fixture.elapsed },
                )
            }
        })[AdministrationSetupViewModel::class.java]
    }

    private class Fixture(val account: LocalAccount? = ACCOUNT) {
        var current = true
        var elapsed = 1_000L
        var reads = 0
        var storeGuardCalls = 0
        var saved: LocalAdministratorAssignment? = null
        val writes = mutableListOf<Pair<String, Instant>>()
        var readAnswer: () -> LocalAdministratorAssignment? = { saved }
        var assignAnswer: (String, Instant, () -> Unit) -> LocalAdministratorAssignment = { operation, at, guard ->
            guard(); storeGuardCalls++
            val result = assignment(operationId = operation, at = at)
            guard(); storeGuardCalls++
            saved = result
            result
        }
    }

    private companion object {
        const val ACCOUNT_ID = "3d251270-d1a3-4be0-bc9e-31babd872a74"
        const val OTHER_ID = "66796120-5f8e-46c5-bf8f-4f134364f04f"
        const val OPERATION_ID = "f0889660-2885-463f-a2ae-4611fb33922d"
        val ACCOUNT = LocalAccount(ACCOUNT_ID, "admin_local")
        val TIME: Instant = Instant.parse("2026-10-15T20:00:00Z")
        fun assignment(accountId: String = ACCOUNT_ID, operationId: String = OPERATION_ID, at: Instant = TIME) =
            LocalAdministratorAssignment(accountId, "admin_local", operationId, at)
    }
}
