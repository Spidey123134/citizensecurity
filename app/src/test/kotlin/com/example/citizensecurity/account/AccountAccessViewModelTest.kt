package com.example.citizensecurity.account

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.AccountAlreadyExistsException
import com.example.citizensecurity.domain.AccountField
import com.example.citizensecurity.domain.AccountRepository
import com.example.citizensecurity.domain.AccountValidationErrors
import com.example.citizensecurity.domain.AccountValidationException
import com.example.citizensecurity.domain.LocalAccount
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountAccessViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val account = LocalAccount("00000000-0000-0000-0000-000000000001", "alumna1")
    private val password = "Una frase de prueba completa"

    @Before fun configureMain() { Dispatchers.setMain(dispatcher) }
    @After fun releaseModels() { stores.forEach { it.clear() }; Dispatchers.resetMain() }

    @Test fun registrationReturnsIdentityWithoutSigningInAndSuccessIsConsumedOnce() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        val session = LocalAccountSession()
        assertTrue(model.submit(AccountAccessMode.REGISTER, "alumna1", password))
        assertEquals(AccountAccessState.Loading(AccountAccessMode.REGISTER), model.state.value)
        advanceUntilIdle()
        val success = model.state.value as AccountAccessState.Registered
        assertEquals(account, success.account)
        assertNull(session.account)
        assertEquals(1, repository.registrations)
        assertEquals(0, repository.authentications)
        assertTrue(model.consumeSuccess(success))
        assertFalse(model.consumeSuccess(success))
        assertSame(AccountAccessState.Idle, model.state.value)
    }

    @Test fun repeatedLoginGestureAndUnconsumedSuccessDoNotStartAnotherRead() = runTest(dispatcher) {
        val gate = CompletableDeferred<LocalAccount?>()
        val repository = FakeRepository(authenticate = { gate.await() })
        val model = model(repository)
        assertTrue(model.submit(AccountAccessMode.LOGIN, "alumna1", password))
        assertFalse(model.submit(AccountAccessMode.LOGIN, "alumna1", password))
        runCurrent()
        assertEquals(1, repository.authentications)
        gate.complete(account)
        advanceUntilIdle()
        assertEquals(AccountAccessState.Authenticated(account), model.state.value)
        assertFalse(model.submit(AccountAccessMode.LOGIN, "alumna1", password))
    }

    @Test fun wrongCredentialsNeverBecomeAnAuthenticatedAccount() = runTest(dispatcher) {
        val repository = FakeRepository(authenticate = { null })
        val model = model(repository)
        model.submit(AccountAccessMode.LOGIN, "alumna1", password)
        advanceUntilIdle()
        assertSame(AccountAccessState.InvalidCredentials, model.state.value)
        assertFalse(model.consumeSuccess(model.state.value))
        assertEquals(0, repository.registrations)
    }

    @Test fun fieldValidationIsExposedWithoutStoringCredentialsInState() = runTest(dispatcher) {
        val errors = AccountValidationErrors(mapOf(AccountField.PASSWORD to "Longitud inválida"))
        val model = model(FakeRepository(register = { throw AccountValidationException(errors) }))
        model.submit(AccountAccessMode.REGISTER, "alumna1", password)
        advanceUntilIdle()
        assertEquals(AccountAccessState.Invalid(errors.errors), model.state.value)
        assertFalse(model.state.value.toString().contains(password))
    }

    @Test fun duplicateRegistrationDoesNotFallBackToAuthenticationOrAnotherAccount() = runTest(dispatcher) {
        val repository = FakeRepository(register = { throw AccountAlreadyExistsException() })
        val model = model(repository)
        model.submit(AccountAccessMode.REGISTER, "alumna1", password)
        advanceUntilIdle()
        assertSame(AccountAccessState.AlreadyExists, model.state.value)
        assertEquals(1, repository.registrations)
        assertEquals(0, repository.authentications)
    }

    @Test fun uncertainRegistrationRemainsAnExplicitFailureAndNeverRetriesAutomatically() = runTest(dispatcher) {
        val repository = FakeRepository(register = { throw IOException("uncertain write") })
        val model = model(repository)
        model.submit(AccountAccessMode.REGISTER, "alumna1", password)
        advanceUntilIdle()
        assertEquals(AccountAccessState.Failed(AccountAccessMode.REGISTER), model.state.value)
        assertEquals(1, repository.registrations)
    }

    @Test fun unavailableLoginDoesNotBecomeWrongPasswordOrOpenSession() = runTest(dispatcher) {
        val model = model(FakeRepository(authenticate = { throw IOException("unavailable") }))
        model.submit(AccountAccessMode.LOGIN, "alumna1", password)
        advanceUntilIdle()
        assertEquals(AccountAccessState.Failed(AccountAccessMode.LOGIN), model.state.value)
    }

    @Test fun canceledLoginCannotPublishAfterAReplacementEvenIfRepositoryIgnoresCancellation() = runTest(dispatcher) {
        val old = CompletableDeferred<LocalAccount?>()
        var attempt = 0
        val replacement = LocalAccount("00000000-0000-0000-0000-000000000002", "alumno2")
        val model = model(FakeRepository(authenticate = {
            if (++attempt == 1) withContext(NonCancellable) { old.await() } else replacement
        }))
        model.submit(AccountAccessMode.LOGIN, "alumna1", password)
        runCurrent()
        model.cancel()
        assertEquals(AccountAccessState.Interrupted(AccountAccessMode.LOGIN), model.state.value)
        model.submit(AccountAccessMode.LOGIN, "alumno2", password)
        runCurrent()
        assertEquals(AccountAccessState.Authenticated(replacement), model.state.value)
        old.complete(account)
        advanceUntilIdle()
        assertEquals(AccountAccessState.Authenticated(replacement), model.state.value)
    }

    @Test fun canceledRegistrationAcknowledgesUncertainOutcomeAndLateSuccessCannotNavigate() = runTest(dispatcher) {
        val gate = CompletableDeferred<LocalAccount>()
        val model = model(FakeRepository(register = { withContext(NonCancellable) { gate.await() } }))
        model.submit(AccountAccessMode.REGISTER, "alumna1", password)
        runCurrent()
        model.cancel()
        gate.complete(account)
        advanceUntilIdle()
        assertEquals(AccountAccessState.Interrupted(AccountAccessMode.REGISTER), model.state.value)
        assertFalse(model.consumeSuccess(model.state.value))
    }

    @Test fun loadingCollectorCanCancelBeforeRepositoryStarts() = runTest(dispatcher) {
        val repository = FakeRepository()
        val model = model(repository)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it is AccountAccessState.Loading) model.cancel() }
        }
        model.submit(AccountAccessMode.LOGIN, "alumna1", password)
        advanceUntilIdle()
        assertEquals(0, repository.authentications)
        assertEquals(AccountAccessState.Interrupted(AccountAccessMode.LOGIN), model.state.value)
        collector.cancel()
    }

    @Test fun equivalentStaleSuccessCannotConsumeTheCurrentEvent() = runTest(dispatcher) {
        val model = model(FakeRepository())
        model.submit(AccountAccessMode.LOGIN, "alumna1", password)
        advanceUntilIdle()
        val success = model.state.value as AccountAccessState.Authenticated
        assertFalse(model.consumeSuccess(success.copy()))
        assertTrue(model.consumeSuccess(success))
    }

    @Test fun destroyedModelRejectsNewGesturesAndCannotPublishPendingResult() = runTest(dispatcher) {
        val gate = CompletableDeferred<LocalAccount?>()
        val model = model(FakeRepository(authenticate = { withContext(NonCancellable) { gate.await() } }))
        model.submit(AccountAccessMode.LOGIN, "alumna1", password)
        runCurrent()
        stores.single().clear()
        gate.complete(account)
        advanceUntilIdle()
        assertFalse(model.state.value is AccountAccessState.Authenticated)
        assertFalse(model.submit(AccountAccessMode.LOGIN, "alumna1", password))
    }

    private fun model(repository: AccountRepository): AccountAccessViewModel {
        val store = ViewModelStore().also(stores::add)
        return ViewModelProvider(store, AccountAccessViewModel.factory(repository))[AccountAccessViewModel::class.java]
    }

    private inner class FakeRepository(
        val register: suspend () -> LocalAccount = { account },
        val authenticate: suspend () -> LocalAccount? = { account },
    ) : AccountRepository {
        var registrations = 0
        var authentications = 0
        override suspend fun register(username: String, password: String): LocalAccount { ++registrations; return register() }
        override suspend fun authenticate(username: String, password: String): LocalAccount? { ++authentications; return authenticate() }
    }
}
