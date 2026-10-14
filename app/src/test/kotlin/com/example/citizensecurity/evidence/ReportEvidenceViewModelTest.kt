package com.example.citizensecurity.evidence

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
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
class ReportEvidenceViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { stores.forEach { it.clear() }; Dispatchers.resetMain() }

    @Test fun onlyExplicitGestureExportsAndRetainsExactOptions() = runTest(dispatcher) {
        val requests = mutableListOf<ReportEvidenceRequest>()
        val model = model { request, check -> check(); requests.add(request); artifact(request) }
        runCurrent()
        assertTrue(requests.isEmpty())
        val request = request().copy(options = ReportEvidenceOptions(false, true))
        model.prepare(request)
        val loading = model.state.value as ReportEvidenceState.Loading
        runCurrent()
        val ready = model.state.value as ReportEvidenceState.Ready
        assertSame(request, loading.request)
        assertSame(request, requests.single())
        assertSame(request, ready.request)
        assertEquals(FOLIO, ready.artifact.reportId)
    }

    @Test fun repeatedPendingGestureUsesOneProducerCall() = runTest(dispatcher) {
        val complete = CompletableDeferred<Unit>()
        var calls = 0
        val model = model { request, check -> calls++; complete.await(); check(); artifact(request) }
        model.prepare(request())
        model.prepare(request())
        runCurrent()
        assertEquals(1, calls)
        complete.complete(Unit)
        runCurrent()
        assertTrue(model.state.value is ReportEvidenceState.Ready)
    }

    @Test fun replacingFolioPreventsLateProducerFromFinishingOrReplacingNewResult() = runTest(dispatcher) {
        val old = CompletableDeferred<Unit>()
        var committedOld = false
        val model = model { request, check ->
            if (request.reportId == FOLIO) withContext(NonCancellable) {
                old.await()
                check() // Required before an AtomicFile finish, even in an uncooperative producer.
                committedOld = true
            }
            artifact(request)
        }
        model.prepare(request())
        runCurrent()
        model.prepare(request(OTHER))
        runCurrent()
        old.complete(Unit)
        advanceUntilIdle()
        assertFalse(committedOld)
        assertEquals(OTHER, (model.state.value as ReportEvidenceState.Ready).artifact.reportId)
    }

    @Test fun cancelPreventsLateCommitAndReturnsIdle() = runTest(dispatcher) {
        val old = CompletableDeferred<Unit>()
        var committed = false
        val model = model { request, check ->
            withContext(NonCancellable) { old.await(); check(); committed = true }
            artifact(request)
        }
        model.prepare(request()); runCurrent(); model.cancel()
        old.complete(Unit); advanceUntilIdle()
        assertFalse(committed)
        assertSame(ReportEvidenceState.Idle, model.state.value)
    }

    @Test fun loadingCollectorCanCancelBeforeProducerBegins() = runTest(dispatcher) {
        var calls = 0
        val model = model { request, _ -> calls++; artifact(request) }
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it is ReportEvidenceState.Loading) model.clear() }
        }
        model.prepare(request()); runCurrent()
        assertEquals(0, calls)
        assertSame(ReportEvidenceState.Idle, model.state.value)
        observer.cancel()
    }

    @Test fun failureIsGenericAndAnotherGestureReadsAgain() = runTest(dispatcher) {
        var calls = 0
        val model = model { request, _ ->
            if (++calls == 1) throw IOException("C:/private_account/report_1/photo.jpg")
            artifact(request)
        }
        model.prepare(request()); runCurrent()
        val error = model.state.value as ReportEvidenceState.Error
        assertFalse(error.message.contains("private_account"))
        model.prepare(error.request); runCurrent()
        assertEquals(2, calls)
        assertTrue(model.state.value is ReportEvidenceState.Ready)
    }

    @Test fun cancellationIsNotShownAsExportFailure() = runTest(dispatcher) {
        val model = model { _, _ -> throw CancellationException("Sesión retirada") }
        model.prepare(request()); runCurrent()
        assertSame(ReportEvidenceState.Idle, model.state.value)
    }

    @Test fun fullQuotaExplainsCacheOnlyRecoveryWithoutClaimingThatRetryWillFixIt() = runTest(dispatcher) {
        var calls = 0
        val model = model { request, _ ->
            if (++calls == 1) throw ReportEvidenceQuotaException()
            artifact(request)
        }
        val request = request().copy(options = ReportEvidenceOptions(false, true))
        model.prepare(request); runCurrent()
        val error = model.state.value as ReportEvidenceState.Error
        assertSame(request, error.request)
        assertTrue(error.message.contains("Borrar caché"))
        assertTrue(error.message.contains("No pulses Borrar almacenamiento ni Borrar datos"))
        assertFalse(error.message.contains("intenta nuevamente"))
        assertEquals(1, calls)
        model.prepare(error.request); runCurrent()
        assertSame(request, (model.state.value as ReportEvidenceState.Ready).request)
        assertEquals(2, calls)
    }

    @Test fun malformedReceiptsNeverBecomeReady() = runTest(dispatcher) {
        val bad = listOf<(ReportEvidenceArtifact) -> ReportEvidenceArtifact>(
            { it.copy(token = "../photo") }, { it.copy(token = TOKEN.uppercase()) },
            { it.copy(reportId = OTHER) }, { it.copy(byteCount = 0) },
            { it.copy(byteCount = ReportEvidencePdfWriter.MAX_OUTPUT_BYTES + 1) },
            { it.copy(sha256 = "A".repeat(64)) }, { it.copy(pageCount = 0) },
            { it.copy(pageCount = ReportEvidencePdfWriter.MAX_PAGES + 1) },
        )
        bad.forEach { mutate ->
            val model = model { request, _ -> mutate(artifact(request)) }
            model.prepare(request()); runCurrent()
            assertTrue(model.state.value is ReportEvidenceState.Error)
        }
    }

    @Test fun chooserCanBeReservedExactlyOnceAndDoesNotClaimDelivery() = runTest(dispatcher) {
        val model = model { request, _ -> artifact(request) }
        model.prepare(request()); runCurrent()
        val ready = model.state.value as ReportEvidenceState.Ready
        val sharing = checkNotNull(model.consumeForShare(ready))
        assertNull(model.consumeForShare(ready))
        model.checkShareCurrent(sharing)
        model.shareComplete(sharing)
        assertTrue(model.state.value is ReportEvidenceState.ChooserOpened)
        assertNull(model.consumeForShare(ready))
    }

    @Test fun failedChooserKeepsTheSamePdfAndRequiresAnotherGesture() = runTest(dispatcher) {
        var calls = 0
        val model = model { request, _ -> calls++; artifact(request) }
        model.prepare(request()); runCurrent()
        val ready = model.state.value as ReportEvidenceState.Ready
        val sharing = checkNotNull(model.consumeForShare(ready))
        model.prepare(request(OTHER)) // Reserved sharing cannot be overwritten by export.
        assertSame(sharing, model.state.value)
        model.shareFailure(sharing)
        val retry = model.state.value as ReportEvidenceState.Ready
        assertSame(ready.artifact, retry.artifact)
        assertNotNull(retry.message)
        assertEquals(1, calls)
        assertNull(model.consumeForShare(ready))
        assertNotNull(model.consumeForShare(retry))
    }

    @Test fun clearingAReservationPreventsStaleCompletionOrFailureFromRestoringIt() = runTest(dispatcher) {
        val model = model { request, _ -> artifact(request) }
        model.prepare(request()); runCurrent()
        val sharing = checkNotNull(model.consumeForShare(model.state.value as ReportEvidenceState.Ready))
        model.clear()
        try { model.checkShareCurrent(sharing); fail("Stale chooser was accepted") }
        catch (_: CancellationException) { }
        model.shareComplete(sharing); model.shareFailure(sharing)
        assertSame(ReportEvidenceState.Idle, model.state.value)
    }

    @Test fun releasingViewModelStorePreventsProducerAndFutureGestures() = runTest(dispatcher) {
        val store = ViewModelStore().also(stores::add)
        var calls = 0
        val producer = ReportEvidenceProducer { request, _ -> calls++; artifact(request) }
        val model = ViewModelProvider(store, ReportEvidenceViewModel.factory(producer, dispatcher))[ReportEvidenceViewModel::class.java]
        model.prepare(request()); store.clear(); runCurrent()
        model.prepare(request(OTHER)); runCurrent()
        assertEquals(0, calls)
        assertSame(ReportEvidenceState.Idle, model.state.value)
    }

    @Test fun requestRejectsNoncanonicalOrIncompleteFolios() {
        listOf("", "../file", "1-1-1-1-1", FOLIO.uppercase(), " $FOLIO ").forEach {
            try { request(it); fail("Invalid request was accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    private fun model(producer: suspend (ReportEvidenceRequest, () -> Unit) -> ReportEvidenceArtifact): ReportEvidenceViewModel {
        val store = ViewModelStore().also(stores::add)
        return ViewModelProvider(store, ReportEvidenceViewModel.factory(ReportEvidenceProducer(producer), dispatcher))[ReportEvidenceViewModel::class.java]
    }
    private fun request(folio: String = FOLIO) = ReportEvidenceRequest(folio, zoneId = ZoneId.of("America/Mexico_City"))
    private fun artifact(request: ReportEvidenceRequest) = ReportEvidenceArtifact(TOKEN, request.reportId,
        Instant.parse("2026-10-13T17:00:00Z"), 234L, "b".repeat(64), 1)
    private companion object {
        const val FOLIO = "4b2c9510-0ca0-46fb-94f1-1c1f7ee79f0d"
        const val OTHER = "a2159b14-ae7d-4f73-815d-6d612b82027c"
        const val TOKEN = "5b2c9510-0ca0-46fb-94f1-1c1f7ee79f0d"
    }
}
