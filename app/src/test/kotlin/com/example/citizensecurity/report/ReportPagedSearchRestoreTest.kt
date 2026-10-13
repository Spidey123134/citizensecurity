package com.example.citizensecurity.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportListPage
import com.example.citizensecurity.domain.ReportListPageQuery
import com.example.citizensecurity.domain.ReportListPageRepository
import com.example.citizensecurity.domain.ReportLocation
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportPagedSearchRestoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun configureDispatcher() { Dispatchers.setMain(dispatcher) }

    @After
    fun releaseModels() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test
    fun restoredSecondPageWaitsForActivationRebindsCriteriaAndReadsFreshBeforeNextAndPrevious() = runTest(dispatcher) {
        val source = (0 until 160).map(::report)
        val originalRepository = RecordingRepository(source)
        val handle = SavedStateHandle()
        val original = model(originalRepository, handle)
        original.search(filters())
        runCurrent()
        original.nextPage()
        runCurrent()
        val secondStart = originalRepository.queries.last().before
        val freshSource = source.map { it.copy(description = "Descripción actualizada") }
        val repository = RecordingRepository(freshSource)
        val restored = model(repository, copyHandle(handle))
        assertSame(ReportPagedSearchState.Idle, restored.state.value)
        restored.nextPage()
        restored.previousPage()
        restored.retry()
        runCurrent()
        assertTrue(repository.queries.isEmpty())
        val selected = filters()
        restored.activate(selected)
        assertNull(currentPagedResult(selected, restored.state.value))
        runCurrent()
        assertEquals(secondStart, repository.queries.single().before)
        assertEquals(2, ready(restored).pageNumber)
        assertSame(selected, ready(restored).filters)
        assertEquals(freshSource.subList(50, 100), ready(restored).reports)
        restored.nextPage()
        runCurrent()
        assertEquals(3, ready(restored).pageNumber)
        restored.previousPage()
        runCurrent()
        assertEquals(2, ready(restored).pageNumber)
        restored.previousPage()
        runCurrent()
        assertEquals(1, ready(restored).pageNumber)
        assertFalse(ready(restored).hasPrevious)
        assertNull(repository.queries.last().before)
    }

    @Test
    fun pendingNextIsCheckpointedBeforeLoadingAndCancellationRetainsItsExactTarget() = runTest(dispatcher) {
        val source = (0 until 120).map(::report)
        val handle = SavedStateHandle()
        val original = model(RecordingRepository(source), handle)
        original.search(filters())
        runCurrent()
        val nextStart = ready(original).nextCursor
        var captured: ReportSearchCheckpoint? = null
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            original.state.collect { state ->
                if (state is ReportPagedSearchState.Loading) captured = checkpoint(handle)
            }
        }
        original.nextPage()
        assertEquals(ReportPageNavigation.NEXT, captured?.request?.navigation)
        assertEquals(nextStart, captured?.request?.before)
        assertEquals(2, captured?.request?.pageNumber)
        assertEquals(listOf(ReportPageStart(null, 1)), captured?.history)
        val saved = copyHandle(handle)
        original.cancelSearch()
        assertEquals(saved.get<Any?>(ReportSearchCheckpointCodec.KEY), handle.get<Any?>(ReportSearchCheckpointCodec.KEY))
        collector.cancel()
        val repository = RecordingRepository(source)
        val restored = model(repository, saved)
        assertTrue(repository.queries.isEmpty())
        restored.activate(filters())
        runCurrent()
        assertEquals(nextStart, repository.queries.single().before)
        assertEquals(2, ready(restored).pageNumber)
        assertTrue(ready(restored).hasPrevious)
        assertEquals(ReportPageNavigation.RELOAD, checkpoint(saved).request.navigation)
        restored.previousPage()
        runCurrent()
        assertEquals(1, ready(restored).pageNumber)
    }

    @Test
    fun aRestoredFailedNextCanFailAgainAndRetryWithoutCommittingHistoryTwice() = runTest(dispatcher) {
        val source = (0 until 120).map(::report)
        val originalHandle = SavedStateHandle()
        val original = model(ReportListPageRepository { query ->
            if (query.before != null) throw IOException("Private original failure")
            page(source, query)
        }, originalHandle)
        original.search(filters())
        runCurrent()
        original.nextPage()
        runCurrent()
        assertTrue(original.state.value is ReportPagedSearchState.Error)
        val saved = copyHandle(originalHandle)
        val failedStart = checkpoint(saved).request.before
        val queries = mutableListOf<ReportListPageQuery>()
        var fails = true
        val restored = model(ReportListPageRepository { query ->
            queries.add(query)
            if (fails) { fails = false; throw IOException("Private restored failure") }
            page(source, query)
        }, saved)
        assertTrue(queries.isEmpty())
        val selected = filters()
        restored.activate(selected)
        runCurrent()
        assertSame(selected, (restored.state.value as ReportPagedSearchState.Error).filters)
        assertEquals(1, checkpoint(saved).history.size)
        restored.retry()
        runCurrent()
        assertEquals(failedStart, queries[0].before)
        assertEquals(queries[0], queries[1])
        assertEquals(2, checkpoint(saved).history.size)
        assertEquals(2, ready(restored).pageNumber)
        restored.previousPage()
        runCurrent()
        assertEquals(1, ready(restored).pageNumber)
        assertFalse(ready(restored).hasPrevious)
    }

    @Test
    fun pendingPreviousRestoresItsTargetAndKeepsTheSuccessfulForwardStarts() = runTest(dispatcher) {
        val source = (0 until 160).map(::report)
        val handle = SavedStateHandle()
        val original = model(RecordingRepository(source), handle)
        original.search(filters())
        runCurrent()
        repeat(2) { original.nextPage(); runCurrent() }
        original.previousPage()
        val saved = copyHandle(handle)
        val pending = checkpoint(saved)
        assertEquals(ReportPageNavigation.PREVIOUS, pending.request.navigation)
        assertEquals(2, pending.historyIndex)
        assertEquals(1, pending.request.historyIndex)
        original.cancelSearch()
        val restored = model(RecordingRepository(source), saved)
        restored.activate(filters())
        runCurrent()
        assertEquals(2, ready(restored).pageNumber)
        assertEquals(3, checkpoint(saved).history.size)
        assertEquals(1, checkpoint(saved).historyIndex)
        restored.nextPage()
        runCurrent()
        assertEquals(3, ready(restored).pageNumber)
        assertEquals(3, checkpoint(saved).history.size)
        restored.previousPage()
        runCurrent()
        restored.previousPage()
        runCurrent()
        assertEquals(1, ready(restored).pageNumber)
    }

    @Test
    fun restoredTruncatedHistoryRetainsExactlyFiftyStartsAndTheOldestAvailablePage() = runTest(dispatcher) {
        val source = (0 until 2550).map(::report)
        val handle = SavedStateHandle()
        val original = model(RecordingRepository(source), handle)
        original.search(filters())
        runCurrent()
        repeat(50) { original.nextPage(); runCurrent() }
        assertEquals(50, checkpoint(handle).history.size)
        assertEquals(2, checkpoint(handle).history.first().pageNumber)
        original.cancelSearch()
        val repository = RecordingRepository(source)
        val saved = copyHandle(handle)
        val restored = model(repository, saved)
        assertTrue(repository.queries.isEmpty())
        restored.activate(filters())
        runCurrent()
        assertEquals(51, ready(restored).pageNumber)
        repeat(49) { restored.previousPage(); runCurrent() }
        assertEquals(2, ready(restored).pageNumber)
        assertFalse(ready(restored).hasPrevious)
        val count = repository.queries.size
        restored.previousPage()
        runCurrent()
        assertEquals(count, repository.queries.size)
        restored.nextPage()
        runCurrent()
        assertEquals(3, ready(restored).pageNumber)
        assertEquals(2, checkpoint(saved).history.size)
    }

    @Test
    fun changedCriteriaAndExplicitSearchDiscardTheRestoredTraversal() = runTest(dispatcher) {
        val source = (0 until 120).map(::report)
        val handle = SavedStateHandle()
        val original = model(RecordingRepository(source), handle)
        original.search(filters())
        runCurrent()
        original.nextPage()
        runCurrent()
        val repository = RecordingRepository(source)
        val saved = copyHandle(handle)
        val restored = model(repository, saved)
        val changed = filters("parque")
        restored.activate(changed)
        runCurrent()
        assertNull(repository.queries.single().before)
        assertEquals(1, ready(restored).pageNumber)
        assertSame(changed, ready(restored).filters)
        assertEquals(1, checkpoint(saved).history.size)
        restored.nextPage()
        runCurrent()
        assertEquals(2, ready(restored).pageNumber)
        restored.search(changed)
        runCurrent()
        assertEquals(1, ready(restored).pageNumber)
        assertFalse(ready(restored).hasPrevious)
        assertNull(repository.queries.last().before)
    }

    @Test
    fun malformedSavedStateIsRemovedAsAWholeAndNeverReadsBeforeActivation() = runTest(dispatcher) {
        val source = (0 until 120).map(::report)
        val originalHandle = SavedStateHandle()
        val original = model(RecordingRepository(source), originalHandle)
        original.search(filters())
        runCurrent()
        original.nextPage()
        runCurrent()
        val fields = requireNotNull(originalHandle.get<ArrayList<String>>(ReportSearchCheckpointCodec.KEY))
        val badValues = listOf<Any?>(
            null, "unknown", 4, arrayListOf("1"),
            ArrayList<Any?>(fields).also { it[0] = null },
            ArrayList<Any?>(fields).also { it[12] = 2 },
            ArrayList(fields).also { it[0] = "99" },
            ArrayList(fields).also { it[15] = "-1" },
            ArrayList(fields).also { it[9] = Long.MAX_VALUE.toString() },
        )
        badValues.forEach { raw ->
            val handle = SavedStateHandle(mapOf(ReportSearchCheckpointCodec.KEY to raw))
            val repository = RecordingRepository(source)
            val restored = model(repository, handle)
            assertSame(ReportPagedSearchState.Idle, restored.state.value)
            assertFalse(handle.contains(ReportSearchCheckpointCodec.KEY))
            runCurrent()
            assertTrue(repository.queries.isEmpty())
            restored.activate(filters())
            runCurrent()
            assertEquals(1, ready(restored).pageNumber)
            assertFalse(ready(restored).hasPrevious)
            assertNull(repository.queries.single().before)
            assertEquals(1, checkpoint(handle).history.size)
        }
    }

    @Test
    fun savedStateContainsOnlyBoundedReadingMetadataAndUnicodeNormalizationKeepsItsCriteria() = runTest(dispatcher) {
        val privateDescription = "Descripción privada que no forma parte del checkpoint"
        val repository = RecordingRepository(listOf(report(0).copy(description = privateDescription)))
        val handle = SavedStateHandle()
        val model = model(repository, handle)
        model.search(filters())
        runCurrent()
        assertEquals(setOf(ReportSearchCheckpointCodec.KEY), handle.keys())
        val raw = handle.get<Any?>(ReportSearchCheckpointCodec.KEY)
        assertTrue(raw is ArrayList<*> && raw.all { it is String })
        val fields = requireNotNull(handle.get<ArrayList<String>>(ReportSearchCheckpointCodec.KEY))
        assertTrue(fields.size <= 217)
        assertTrue(fields.sumOf { it.length } <= ReportSearchCheckpointCodec.MAX_CHARACTERS)
        assertFalse(fields.any { privateDescription in it || "Frente al parque" in it })
        // Hangul decomposition expands valid input beyond the UI's input-length cap.
        val expanded = filters("각".repeat(512))
        assertTrue(expanded.normalizedText.length > 512)
        model.search(expanded)
        runCurrent()
        assertSame(expanded, ready(model).filters)
        assertTrue(ready(model).reports.isEmpty())
        assertEquals(expanded.normalizedText, checkpoint(handle).request.filters.normalizedText)
        val restored = model(repository, copyHandle(handle))
        restored.activate(filters("각".repeat(512)))
        runCurrent()
        assertEquals(expanded.normalizedText, ready(restored).filters.normalizedText)
    }

    @Test
    fun replacingARequestFromItsLoadingCollectorAlsoReplacesTheCheckpointBeforeReading() = runTest(dispatcher) {
        val queries = mutableListOf<ReportListPageQuery>()
        val handle = SavedStateHandle()
        val model = model(ReportListPageRepository { query -> queries.add(query); ReportListPage(emptyList(), false) }, handle)
        val original = filters("anterior")
        val replacement = filters("nueva")
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { state ->
                if (state is ReportPagedSearchState.Loading && state.filters === original) model.search(replacement)
            }
        }
        model.search(original)
        runCurrent()
        assertEquals(1, queries.size)
        assertSame(replacement, ready(model).filters)
        assertEquals(replacement.normalizedText, checkpoint(handle).request.filters.normalizedText)
        assertEquals(ReportPageNavigation.RELOAD, checkpoint(handle).request.navigation)
        collector.cancel()
    }

    private fun model(repository: ReportListPageRepository, handle: SavedStateHandle): ReportPagedSearchViewModel {
        val store = ViewModelStore().also(stores::add)
        return ViewModelProvider(store, viewModelFactory {
            initializer { ReportPagedSearchViewModel(repository, dispatcher, handle) }
        })[ReportPagedSearchViewModel::class.java]
    }

    private fun ready(model: ReportPagedSearchViewModel) = model.state.value as ReportPagedSearchState.Ready

    private fun checkpoint(handle: SavedStateHandle) = requireNotNull(
        ReportSearchCheckpointCodec.decode(handle.get<Any?>(ReportSearchCheckpointCodec.KEY)),
    )

    private fun copyHandle(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith {
        val raw = handle.get<Any?>(it)
        if (raw is ArrayList<*>) ArrayList(raw) else raw
    })

    private fun filters(text: String = "") =
        (parseReportSearchFilters(text = text, zoneId = ZoneOffset.UTC) as ReportSearchFilterValidation.Valid).filters

    private class RecordingRepository(private val source: List<Report>) : ReportListPageRepository {
        val queries = mutableListOf<ReportListPageQuery>()
        override suspend fun listPage(query: ReportListPageQuery): ReportListPage {
            queries.add(query)
            return page(source, query)
        }
    }

    companion object {
        private fun page(source: List<Report>, query: ReportListPageQuery): ReportListPage {
            val startInclusive = query.startInclusive
            val endExclusive = query.endExclusive
            val candidates = source.filter { report ->
                val before = query.before
                (query.type == null || query.type == report.type) &&
                    (query.status == null || query.status == report.status) &&
                    (query.priority == null || query.priority == report.priority) &&
                    (startInclusive == null || report.occurredAt >= startInclusive) &&
                    (endExclusive == null || report.occurredAt < endExclusive) &&
                    (before == null || report.createdAt < before.createdAt ||
                        (report.createdAt == before.createdAt && report.id < before.id))
            }
            return ReportListPage(candidates.take(query.limit), candidates.size > query.limit)
        }

        private fun report(index: Int) = Report(
            id = String.format(Locale.ROOT, "00000000-0000-4000-8000-%012x", index),
            type = IncidentType.RISK, priority = Priority.HIGH, description = "Luminaria dañada",
            occurredAt = Instant.parse("2026-10-11T18:00:00.987654321Z"),
            createdAt = Instant.parse("2026-10-11T18:00:10.123456789Z").minusSeconds(index.toLong()),
            location = ReportLocation(reference = "Frente al parque"),
        )
    }
}
