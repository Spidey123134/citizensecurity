package com.example.citizensecurity.help

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.TrustedContact
import com.example.citizensecurity.domain.TrustedContactsRepository
import com.example.citizensecurity.domain.TrustedContactsSnapshot
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
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
class HelpContactsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = arrayListOf<ViewModelStore>()
    @Before fun main() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { stores.forEach { it.clear() }; Dispatchers.resetMain() }

    @Test fun constructorDoesNotReadAndInitialReadNeverWrites() = runTest(dispatcher) {
        val repo = Repo()
        val model = model(repo)
        assertEquals(HelpContactsState.Idle, model.state.value)
        assertEquals(0, repo.reads)
        model.load(); model.load(); runCurrent()
        assertEquals(1, repo.reads)
        assertEquals(0, repo.writes)
        assertTrue((model.state.value as HelpContactsState.Ready).snapshot.contacts.isEmpty())
    }

    @Test fun addsNormalizedManualContactOnceAndRejectsDoubleGesture() = runTest(dispatcher) {
        val repo = Repo()
        val model = model(repo)
        model.load(); runCurrent()
        assertTrue(model.add(" Mamá 🌼 ", "+52 (55) 1234-5678"))
        assertFalse(model.add("Otro", "5523456789"))
        model.load(); runCurrent()
        assertEquals(1, repo.writes)
        assertEquals(1, repo.reads)
        val contact = (model.state.value as HelpContactsState.Ready).snapshot.contacts.single()
        assertEquals("Mamá 🌼", contact.name)
        assertEquals("+525512345678", contact.phoneNumber)
    }

    @Test fun inputValidationAndDuplicatesNeverReachStorage() = runTest(dispatcher) {
        val repo = Repo(snapshot(listOf(TrustedContact.create("Casa", "5512345678"))))
        val model = model(repo)
        model.load(); runCurrent()
        assertFalse(model.add("Casa", "tel:5512345678"))
        assertFalse(model.add("", "5523456789"))
        assertFalse(model.add("Repetido", "+5512345678"))
        assertEquals(0, repo.writes)
        assertEquals(1, (model.state.value as HelpContactsState.Ready).snapshot.contacts.size)
    }

    @Test fun fiveContactsIsAHardLimit() = runTest(dispatcher) {
        val repo = Repo(snapshot((0..4).map { TrustedContact.create("Casa $it", "551234567$it") }))
        val model = model(repo)
        model.load(); runCurrent()
        assertFalse(model.add("Sexto", "5523456789"))
        assertEquals(0, repo.writes)
        assertEquals(5, (model.state.value as HelpContactsState.Ready).snapshot.contacts.size)
    }

    @Test fun deletionRequiresTheExactShownReadingAndUsesItsRevision() = runTest(dispatcher) {
        val first = snapshot(listOf(TrustedContact.create("Casa", "5512345678")))
        val repo = Repo(first)
        val model = model(repo)
        model.load(); runCurrent()
        val old = model.state.value as HelpContactsState.Ready
        model.load(); runCurrent()
        assertFalse(model.remove(first.contacts.single().id, old))
        val current = model.state.value as HelpContactsState.Ready
        assertTrue(model.remove(first.contacts.single().id, current))
        runCurrent()
        assertEquals(first.revision, repo.expected.single())
        assertTrue((model.state.value as HelpContactsState.Ready).snapshot.contacts.isEmpty())
    }

    @Test fun committedButLostReceiptBlocksChangesUntilFreshRead() = runTest(dispatcher) {
        val repo = Repo().apply { afterWrite = { throw IOException("private/path/data") } }
        val model = model(repo)
        model.load(); runCurrent()
        model.add("Casa", "5512345678"); runCurrent()
        assertTrue(model.state.value is HelpContactsState.WriteUncertain)
        assertFalse(model.add("Otro", "5523456789"))
        repo.readFailure = IOException("no read")
        model.load(); runCurrent()
        assertTrue((model.state.value as HelpContactsState.ReadFailure).requiresReload)
        assertFalse(model.add("Otro", "5523456789"))
        repo.readFailure = null
        model.load(); runCurrent()
        assertEquals(1, (model.state.value as HelpContactsState.Ready).snapshot.contacts.size)
        repo.afterWrite = {}
        assertTrue(model.add("Otro", "5523456789")); runCurrent()
        assertEquals(2, repo.writes)
    }

    @Test fun cancellingAnInFlightMutationStillRequiresReadAndIgnoresLateReceipt() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repo = Repo().apply { afterWrite = { withContext(NonCancellable) { gate.await() } } }
        val model = model(repo)
        model.load(); runCurrent()
        model.add("Casa", "5512345678"); runCurrent()
        model.cancel()
        assertTrue(model.state.value is HelpContactsState.WriteUncertain)
        gate.complete(Unit); advanceUntilIdle()
        assertTrue(model.state.value is HelpContactsState.WriteUncertain)
        model.load(); runCurrent()
        assertEquals(1, (model.state.value as HelpContactsState.Ready).snapshot.contacts.size)
    }

    @Test fun sessionRevokedBeforeReadingOrAfterCommitNeverPublishesPrivateResults() = runTest(dispatcher) {
        var active = false
        val repo = Repo()
        val model = model(repo) { check(active) }
        model.load(); runCurrent()
        assertEquals(0, repo.reads)
        active = true
        model.load(); runCurrent()
        repo.afterWrite = { active = false }
        model.add("Casa", "5512345678"); runCurrent()
        assertTrue(model.state.value is HelpContactsState.WriteUncertain)
        assertEquals(1, repo.current.contacts.size)
    }

    @Test fun aMalformedWriteReceiptIsUncertainAndCannotAuthorizeAnotherWrite() = runTest(dispatcher) {
        val repo = Repo().apply { fakeReceipt = TrustedContactsSnapshot(null, emptyList()) }
        val model = model(repo)
        model.load(); runCurrent()
        model.add("Casa", "5512345678"); runCurrent()
        assertTrue(model.state.value is HelpContactsState.WriteUncertain)
        assertFalse(model.add("Otro", "5523456789"))
        assertEquals(1, repo.writes)
    }

    @Test fun aLoadingCollectorCanCancelBeforeAnyWriteBegins() = runTest(dispatcher) {
        val repo = Repo()
        val model = model(repo)
        model.load(); runCurrent()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it is HelpContactsState.Writing) model.cancel() }
        }
        model.add("Casa", "5512345678"); runCurrent()
        assertEquals(0, repo.writes)
        assertTrue(model.state.value is HelpContactsState.WriteUncertain)
        collector.cancel()
    }

    @Test fun canceledReadingCannotReplaceAFreshSnapshot() = runTest(dispatcher) {
        val gate = CompletableDeferred<TrustedContactsSnapshot>()
        val repo = Repo().apply { slowRead = { withContext(NonCancellable) { gate.await() } } }
        val model = model(repo)
        model.load(); runCurrent(); model.cancel()
        repo.slowRead = null
        val fresh = snapshot(listOf(TrustedContact.create("Casa", "5512345678")))
        repo.current = fresh
        model.load(); runCurrent()
        gate.complete(TrustedContactsSnapshot(null, emptyList())); advanceUntilIdle()
        assertSame(fresh, (model.state.value as HelpContactsState.Ready).snapshot)
    }

    @Test fun clearingTheModelDuringMutationCannotPublishOrStartAnotherWrite() = runTest(dispatcher) {
        val store = ViewModelStore().also { stores.add(it) }
        val repo = Repo().apply { afterWrite = { throw CancellationException() } }
        val model = ViewModelProvider(store, HelpContactsViewModel.factory(repo, {}, dispatcher))[HelpContactsViewModel::class.java]
        model.load(); runCurrent(); model.add("Casa", "5512345678"); runCurrent()
        assertTrue(model.state.value is HelpContactsState.WriteUncertain)
        store.clear()
        model.load(); runCurrent()
        assertFalse(model.add("Otro", "5523456789"))
        assertEquals(1, repo.writes)
    }

    private fun model(repo: TrustedContactsRepository, guard: () -> Unit = {}): HelpContactsViewModel {
        val store = ViewModelStore().also { stores.add(it) }
        return ViewModelProvider(store, HelpContactsViewModel.factory(repo, guard, dispatcher))[HelpContactsViewModel::class.java]
    }
    private fun snapshot(contacts: List<TrustedContact>) = TrustedContactsSnapshot(UUID.randomUUID().toString(), contacts)
    private class Repo(var current: TrustedContactsSnapshot = TrustedContactsSnapshot(null, emptyList())) : TrustedContactsRepository {
        var reads = 0
        var writes = 0
        val expected = arrayListOf<String?>()
        var readFailure: Exception? = null
        var afterWrite: suspend () -> Unit = {}
        var slowRead: (suspend () -> TrustedContactsSnapshot)? = null
        var fakeReceipt: TrustedContactsSnapshot? = null
        override suspend fun read(): TrustedContactsSnapshot { reads++; readFailure?.let { throw it }; return slowRead?.invoke() ?: current }
        override suspend fun replace(expectedRevision: String?, contacts: List<TrustedContact>): TrustedContactsSnapshot {
            writes++; expected.add(expectedRevision)
            check(current.revision == expectedRevision)
            current = TrustedContactsSnapshot(UUID.randomUUID().toString(), contacts)
            afterWrite()
            return fakeReceipt ?: current
        }
    }
}
