package com.example.citizensecurity.report

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.SimilarReportMatch
import com.example.citizensecurity.domain.SimilarReportPage
import com.example.citizensecurity.domain.SimilarReportQuery
import com.example.citizensecurity.domain.SimilarReportRepository
import java.io.IOException
import java.time.Instant
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
class ReportSimilarityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val incidentTime = Instant.parse("2026-10-10T18:22:33.123456789Z")
    private val excludedId = "1e345678-1234-4567-89ab-0123456789ab"
    private val otherId = "2e345678-1234-4567-89ab-0123456789ab"

    @Before fun setMain() = Dispatchers.setMain(dispatcher)
    @After fun finish() {
        try { stores.forEach { it.clear() } } finally { Dispatchers.resetMain() }
    }

    @Test fun creationIsIdleAndDoesNotStartLocationOrRepositoryWork() = runTest(dispatcher) {
        var calls = 0
        val model = model { ++calls; emptyPage() }
        advanceUntilIdle()
        assertEquals(ReportSimilarityState.Idle, model.state.value)
        assertEquals(0, calls)
        assertFalse(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun checkingCapturesCompleteDraftBeforeStartingRead() = runTest(dispatcher) {
        var calls = 0
        val input = draft().copy(photo = photo())
        val model = model { ++calls; emptyPage() }
        assertTrue(model.check(input, excludedId))
        assertEquals(ReportSimilarityState.Checking(ReportSimilarityKey(input, excludedId)), model.state.value)
        assertEquals(0, calls)
        assertFalse(model.consumeAuthorization(input, excludedId, explicitOverride = true))
        runCurrent()
        assertEquals(1, calls)
    }

    @Test fun queryUsesIncidentTimeDefaultTwoHoursTwoHundredMetersAndExcludedFolio() = runTest(dispatcher) {
        val queries = mutableListOf<SimilarReportQuery>()
        val model = model { queries += it; emptyPage() }
        model.check(draft(), excludedId)
        runCurrent()
        assertEquals(SimilarReportQuery.fromDraft(draft(), excludedId), queries.single())
        assertEquals(200.0, queries.single().radiusMeters, 0.0)
        assertEquals(incidentTime.minusSeconds(7200), queries.single().earliest)
        assertEquals(incidentTime.plusSeconds(7200), queries.single().latest)
    }

    @Test fun completedEmptySearchAuthorizesExactlyOnceForTheSameBody() = runTest(dispatcher) {
        val model = model { emptyPage() }
        model.check(draft())
        runCurrent()
        assertTrue(model.consumeAuthorization(draft()))
        assertEquals(ReportSimilarityState.Idle, model.state.value)
        assertFalse(model.consumeAuthorization(draft()))
        assertFalse(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun matchesRequireExplicitOverrideAndStillAuthorizeOnlyOnce() = runTest(dispatcher) {
        val model = model { warningPage() }
        model.check(draft(), excludedId)
        runCurrent()
        assertFalse(model.consumeAuthorization(draft(), excludedId))
        assertTrue(model.state.value is ReportSimilarityState.Ready)
        assertTrue(model.consumeAuthorization(draft(), excludedId, explicitOverride = true))
        assertFalse(model.consumeAuthorization(draft(), excludedId, explicitOverride = true))
    }

    @Test fun incompleteEmptySearchCannotSilentlySave() = runTest(dispatcher) {
        val model = model { emptyPage(incomplete = true) }
        model.check(draft())
        runCurrent()
        assertFalse(model.consumeAuthorization(draft()))
        assertTrue(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun incompleteSearchWithMatchesAlsoRequiresExplicitOverride() = runTest(dispatcher) {
        val model = model { warningPage(incomplete = true) }
        model.check(draft())
        runCurrent()
        assertFalse(model.consumeAuthorization(draft()))
        assertTrue(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun readFailureExplainsLocalUncertaintyWithoutLeakingExceptionAndRequiresOverride() = runTest(dispatcher) {
        val model = model { throw IOException("private path / database internals") }
        model.check(draft())
        runCurrent()
        val failed = model.state.value as ReportSimilarityState.Failed
        assertEquals(ReportSimilarityKey(draft(), null), failed.key)
        assertTrue(failed.message.contains("este teléfono"))
        assertFalse(failed.message.contains("private path"))
        assertFalse(model.consumeAuthorization(draft()))
        assertTrue(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun retryAfterReadFailureRequiresNewReadAndCanAuthorizeEmptyResult() = runTest(dispatcher) {
        var calls = 0
        val model = model { if (++calls == 1) throw IOException() else emptyPage() }
        model.check(draft())
        runCurrent()
        assertTrue(model.state.value is ReportSimilarityState.Failed)
        assertTrue(model.check(draft()))
        assertFalse(model.consumeAuthorization(draft(), explicitOverride = true))
        runCurrent()
        assertEquals(2, calls)
        assertTrue(model.consumeAuthorization(draft()))
    }

    @Test fun missingCoordinatesFailWithoutReadingAndWithoutAutomaticAuthorization() = runTest(dispatcher) {
        var calls = 0
        val input = draft().copy(location = ReportLocation("Entrada norte"))
        val model = model { ++calls; emptyPage() }
        model.check(input)
        runCurrent()
        assertEquals(0, calls)
        assertTrue(model.state.value is ReportSimilarityState.Failed)
        assertFalse(model.consumeAuthorization(input))
        assertTrue(model.consumeAuthorization(input, explicitOverride = true))
    }

    @Test fun invalidCoordinatesCannotBecomeAnEmptySuccess() = runTest(dispatcher) {
        var calls = 0
        val model = model { ++calls; emptyPage() }
        for (location in listOf(
            ReportLocation(latitude = Double.NaN, longitude = -99.0),
            ReportLocation(latitude = 19.0, longitude = Double.POSITIVE_INFINITY),
            ReportLocation(latitude = 91.0, longitude = -99.0),
            ReportLocation(latitude = 19.0, longitude = -181.0),
            ReportLocation(latitude = 19.0),
        )) {
            val input = draft().copy(location = location)
            model.check(input)
            runCurrent()
            assertTrue(model.state.value is ReportSimilarityState.Failed)
            assertFalse(model.consumeAuthorization(input))
        }
        assertEquals(0, calls)
    }

    @Test fun malformedExcludedFolioFailsRatherThanSearchWithoutTheExclusion() = runTest(dispatcher) {
        var calls = 0
        val model = model { ++calls; emptyPage() }
        model.check(draft(), "1-2-3-4-5")
        runCurrent()
        assertEquals(0, calls)
        assertTrue(model.state.value is ReportSimilarityState.Failed)
        assertFalse(model.consumeAuthorization(draft(), "1-2-3-4-5"))
    }

    @Test fun changingAnyRawFieldCannotConsumePriorAuthorizationEvenWithOverride() = runTest(dispatcher) {
        val input = draft().copy(photo = photo())
        val model = model { warningPage() }
        model.check(input, excludedId)
        runCurrent()
        val changes = listOf(
            input.copy(type = IncidentType.FIRE),
            input.copy(priority = Priority.HIGH),
            input.copy(description = input.description + " "),
            input.copy(occurredAt = input.occurredAt.plusNanos(1)),
            input.copy(location = input.location.copy(reference = input.location.reference + " ")),
            input.copy(location = input.location.copy(latitude = 19.40001)),
            input.copy(location = input.location.copy(longitude = -99.10001)),
            input.copy(photo = null),
            input.copy(photo = photo().copy(id = otherId)),
            input.copy(photo = photo().copy(sha256 = "b".repeat(64))),
            input.copy(photo = photo().copy(byteCount = 1235)),
            input.copy(photo = photo().copy(width = 641)),
            input.copy(photo = photo().copy(height = 481)),
        )
        for (changed in changes) {
            assertFalse("Changed input $changed", model.consumeAuthorization(changed, excludedId))
            assertFalse("Changed input with override $changed", model.consumeAuthorization(changed, excludedId, true))
        }
        assertTrue(model.consumeAuthorization(input, excludedId, true))
    }

    @Test fun changingExcludedFolioCannotUsePriorResult() = runTest(dispatcher) {
        val model = model { emptyPage() }
        model.check(draft(), excludedId)
        runCurrent()
        assertFalse(model.consumeAuthorization(draft(), otherId, true))
        assertFalse(model.consumeAuthorization(draft(), null, true))
        assertTrue(model.consumeAuthorization(draft(), excludedId))
    }

    @Test fun signedZeroAndRawWhitespaceRemainDifferentReviewedBodies() = runTest(dispatcher) {
        val input = draft().copy(location = ReportLocation(" Plaza ", 0.0, 0.0))
        val model = model { emptyPage() }
        model.check(input)
        runCurrent()
        assertFalse(model.consumeAuthorization(input.copy(location = input.location.copy(latitude = -0.0)), explicitOverride = true))
        assertFalse(model.consumeAuthorization(input.copy(location = input.location.copy(reference = "Plaza")), explicitOverride = true))
        assertTrue(model.consumeAuthorization(input))
    }

    @Test fun lateUncancellableResponseCannotReplaceTheLatestGestureResult() = runTest(dispatcher) {
        val oldCompletion = CompletableDeferred<SimilarReportPage>()
        var calls = 0
        val model = model {
            if (++calls == 1) withContext(NonCancellable) { oldCompletion.await() } else emptyPage()
        }
        model.check(draft())
        runCurrent()
        val newest = draft().copy(description = "Otro incidente revisado")
        model.check(newest, excludedId)
        runCurrent()
        val expected = model.state.value
        oldCompletion.complete(warningPage())
        advanceUntilIdle()
        assertEquals(expected, model.state.value)
        assertTrue(model.consumeAuthorization(newest, excludedId))
        assertFalse(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun lateUncancellableFailureCannotReplaceLatestSuccess() = runTest(dispatcher) {
        val releaseOld = CompletableDeferred<Unit>()
        var calls = 0
        val model = model {
            if (++calls == 1) {
                withContext(NonCancellable) { releaseOld.await(); throw IOException() }
            } else emptyPage()
        }
        model.check(draft())
        runCurrent()
        model.check(draft(), excludedId)
        runCurrent()
        releaseOld.complete(Unit)
        advanceUntilIdle()
        assertTrue(model.consumeAuthorization(draft(), excludedId))
    }

    @Test fun repeatingSameKeyStillUsesOnlyLatestRead() = runTest(dispatcher) {
        val oldCompletion = CompletableDeferred<SimilarReportPage>()
        var calls = 0
        val model = model { if (++calls == 1) withContext(NonCancellable) { oldCompletion.await() } else warningPage() }
        model.check(draft())
        runCurrent()
        model.check(draft())
        runCurrent()
        oldCompletion.complete(emptyPage())
        advanceUntilIdle()
        assertEquals(2, calls)
        assertFalse(model.consumeAuthorization(draft()))
        assertTrue(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun newCheckRevokesPriorReadyAuthorizationUntilItsReadFinishes() = runTest(dispatcher) {
        val next = CompletableDeferred<SimilarReportPage>()
        var calls = 0
        val model = model { if (++calls == 1) emptyPage() else next.await() }
        model.check(draft())
        runCurrent()
        model.check(draft())
        assertFalse(model.consumeAuthorization(draft(), explicitOverride = true))
        runCurrent()
        assertFalse(model.consumeAuthorization(draft(), explicitOverride = true))
        next.complete(emptyPage())
        runCurrent()
        assertTrue(model.consumeAuthorization(draft()))
    }

    @Test fun clearingBeforeLazyReadStartsPreventsRepositoryWork() = runTest(dispatcher) {
        var calls = 0
        val model = model { ++calls; emptyPage() }
        model.check(draft())
        model.clear()
        runCurrent()
        assertEquals(0, calls)
        assertEquals(ReportSimilarityState.Idle, model.state.value)
    }

    @Test fun clearingPendingSearchRejectsLateUncancellableResponse() = runTest(dispatcher) {
        val completion = CompletableDeferred<SimilarReportPage>()
        val model = model { withContext(NonCancellable) { completion.await() } }
        model.check(draft())
        runCurrent()
        model.clear()
        completion.complete(emptyPage())
        advanceUntilIdle()
        assertEquals(ReportSimilarityState.Idle, model.state.value)
        assertFalse(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun clearingReadyResultRequiresAnotherUserCheck() = runTest(dispatcher) {
        val model = model { emptyPage() }
        model.check(draft())
        runCurrent()
        model.clear()
        assertFalse(model.consumeAuthorization(draft(), explicitOverride = true))
        model.check(draft())
        runCurrent()
        assertTrue(model.consumeAuthorization(draft()))
    }

    @Test fun reentrantCheckingCollectorCanReplaceRequestBeforeOldReadStarts() = runTest(dispatcher) {
        val queries = mutableListOf<SimilarReportQuery>()
        val model = model { queries += it; emptyPage() }
        var replaced = false
        val latest = draft().copy(type = IncidentType.FIRE)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect {
                if (it is ReportSimilarityState.Checking && !replaced) {
                    replaced = true
                    model.check(latest, excludedId)
                }
            }
        }
        model.check(draft())
        runCurrent()
        assertEquals(listOf(SimilarReportQuery.fromDraft(latest, excludedId)), queries)
        assertTrue(model.consumeAuthorization(latest, excludedId))
        collector.cancel()
    }

    @Test fun reentrantClearingCollectorPreventsSupersededLazyJobFromStarting() = runTest(dispatcher) {
        var calls = 0
        val model = model { ++calls; emptyPage() }
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it is ReportSimilarityState.Checking) model.clear() }
        }
        model.check(draft())
        runCurrent()
        assertEquals(0, calls)
        assertEquals(ReportSimilarityState.Idle, model.state.value)
        collector.cancel()
    }

    @Test fun reentrantReadyCollectorCanConsumeWithoutOldCompletionReopeningAuthorization() = runTest(dispatcher) {
        var authorizations = 0
        val model = model { emptyPage() }
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect {
                if (it is ReportSimilarityState.Ready && model.consumeAuthorization(draft())) ++authorizations
            }
        }
        model.check(draft())
        runCurrent()
        assertEquals(1, authorizations)
        assertEquals(ReportSimilarityState.Idle, model.state.value)
        assertFalse(model.consumeAuthorization(draft()))
        collector.cancel()
    }

    @Test fun destroyedModelNeverStartsReadAndRejectsFurtherCheckOrAuthorization() = runTest(dispatcher) {
        var calls = 0
        val store = ViewModelStore()
        val model = model(store) { ++calls; emptyPage() }
        model.check(draft())
        store.clear()
        runCurrent()
        assertEquals(0, calls)
        assertFalse(model.check(draft()))
        assertFalse(model.consumeAuthorization(draft(), explicitOverride = true))
        assertEquals(ReportSimilarityState.Idle, model.state.value)
    }

    @Test fun destroyedModelIgnoresAlreadyRunningUncancellableRead() = runTest(dispatcher) {
        val completion = CompletableDeferred<SimilarReportPage>()
        val store = ViewModelStore()
        val model = model(store) { withContext(NonCancellable) { completion.await() } }
        model.check(draft())
        runCurrent()
        store.clear()
        completion.complete(emptyPage())
        advanceUntilIdle()
        assertEquals(ReportSimilarityState.Idle, model.state.value)
        assertFalse(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun repositoryCancellationReturnsToIdleAndAllowsNewGesture() = runTest(dispatcher) {
        var calls = 0
        val model = model { if (++calls == 1) throw CancellationException() else emptyPage() }
        model.check(draft())
        runCurrent()
        assertEquals(ReportSimilarityState.Idle, model.state.value)
        assertFalse(model.consumeAuthorization(draft(), explicitOverride = true))
        assertTrue(model.check(draft()))
        runCurrent()
        assertTrue(model.consumeAuthorization(draft()))
    }

    @Test fun pauseCancelsCheckingSoBackgroundResponseCannotAuthorizeAutomaticSave() = runTest(dispatcher) {
        val completion = CompletableDeferred<SimilarReportPage>()
        val model = model { withContext(NonCancellable) { completion.await() } }
        model.check(draft())
        runCurrent()
        model.pauseAutomaticSave()
        completion.complete(emptyPage())
        advanceUntilIdle()
        assertEquals(ReportSimilarityState.Idle, model.state.value)
        assertFalse(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun pauseDiscardsCompletedEmptySearchBeforeAutomaticSave() = runTest(dispatcher) {
        val model = model { emptyPage() }
        model.check(draft())
        runCurrent()
        model.pauseAutomaticSave()
        assertEquals(ReportSimilarityState.Idle, model.state.value)
        assertFalse(model.consumeAuthorization(draft()))
    }

    @Test fun pausePreservesWarningForUserReturnWithoutAutomaticPermission() = runTest(dispatcher) {
        val model = model { warningPage() }
        model.check(draft())
        runCurrent()
        val warning = model.state.value
        model.pauseAutomaticSave()
        assertEquals(warning, model.state.value)
        assertFalse(model.consumeAuthorization(draft()))
        assertTrue(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun pausePreservesEmptyIncompleteWarningForExplicitDecision() = runTest(dispatcher) {
        val model = model { emptyPage(incomplete = true) }
        model.check(draft())
        runCurrent()
        val warning = model.state.value
        model.pauseAutomaticSave()
        assertEquals(warning, model.state.value)
        assertFalse(model.consumeAuthorization(draft()))
        assertTrue(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun pausePreservesFailureForRetryOrExplicitDecision() = runTest(dispatcher) {
        val model = model { throw IOException() }
        model.check(draft())
        runCurrent()
        val failure = model.state.value
        model.pauseAutomaticSave()
        assertEquals(failure, model.state.value)
        assertFalse(model.consumeAuthorization(draft()))
        assertTrue(model.consumeAuthorization(draft(), explicitOverride = true))
    }

    @Test fun warningSurvivesRetainedViewModelButProcessReplacementHasNoAuthorization() = runTest(dispatcher) {
        val repository = SimilarReportRepository { warningPage() }
        val store = ViewModelStore().also(stores::add)
        val factory = ReportSimilarityViewModel.factory(repository)
        val first = ViewModelProvider(store, factory)[ReportSimilarityViewModel::class.java]
        first.check(draft(), excludedId)
        runCurrent()
        val recreatedScreenModel = ViewModelProvider(store, factory)[ReportSimilarityViewModel::class.java]
        assertSame(first, recreatedScreenModel)
        assertTrue(recreatedScreenModel.state.value is ReportSimilarityState.Ready)
        val replacementProcessModel = model { warningPage() }
        assertEquals(ReportSimilarityState.Idle, replacementProcessModel.state.value)
        assertFalse(replacementProcessModel.consumeAuthorization(draft(), excludedId, true))
    }

    private fun model(
        store: ViewModelStore = ViewModelStore(),
        read: suspend (SimilarReportQuery) -> SimilarReportPage,
    ): ReportSimilarityViewModel {
        stores += store
        return ViewModelProvider(store, ReportSimilarityViewModel.factory(SimilarReportRepository(read)))[ReportSimilarityViewModel::class.java]
    }

    private fun draft() = NewReport(
        IncidentType.RISK, Priority.MEDIUM, "Luminaria dañada frente al parque", incidentTime,
        ReportLocation("Entrada norte", 19.4, -99.1),
    )

    private fun photo() = ReportPhoto(excludedId, "a".repeat(64), 1234, 640, 480)
    private fun emptyPage(incomplete: Boolean = false) = SimilarReportPage(emptyList(), incomplete)
    private fun warningPage(incomplete: Boolean = false): SimilarReportPage {
        val input = draft()
        val report = Report(otherId, input.type, input.priority, input.description, input.occurredAt, input.location, incidentTime)
        return SimilarReportPage(listOf(SimilarReportMatch(report, 12.5)), incomplete)
    }
}
