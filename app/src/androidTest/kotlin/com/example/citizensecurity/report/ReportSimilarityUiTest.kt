package com.example.citizensecurity.report

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportDraftCheckpoint
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.SimilarReportPage
import com.example.citizensecurity.domain.SimilarReportRepository
import com.example.citizensecurity.preview.ReportDraftActivity
import com.example.citizensecurity.testing.ReportTestFixture
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises visible advisory decisions with real isolated SQLite/AtomicFile stores. Draft setup
 * and the phone fix are androidTest fixtures; these cases do not claim physical GPS acceptance.
 */
@RunWith(AndroidJUnit4::class)
class ReportSimilarityUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private var scenario: ActivityScenario<ReportDraftActivity>? = null
    private var ownedDelayedRead: CompletableDeferred<SimilarReportPage>? = null
    private val fixture get() = fixtureRule.fixture
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @After
    fun closeOwnedActivities() {
        scenario?.close()
        scenario = null
        ownedDelayedRead?.complete(SimilarReportPage(emptyList(), searchIncomplete = false))
        ownedDelayedRead = null
        instrumentation.runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .filter { it.packageName == instrumentation.targetContext.packageName }
                .forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun warningOpensActualFolioAndSurvivesRotationWithoutWritingOrRequestingGps() {
        val draft = reviewedDraft()
        val existing = seedSimilar(draft)
        fixture.clearFix()
        launchReview(draft)
        requestSave()
        awaitWarning()
        ui.onNodeWithTag("similar_folio_${existing.id}").performScrollTo().assertTextEquals(existing.id)
        assertUnchanged(listOf(existing.id))
        assertEquals(0, fixture.refreshCalls)
        capture("release055-similares.png")

        scenario!!.recreate()
        awaitWarning()
        ui.onNodeWithTag("open_similar_${existing.id}").performScrollTo().performClick()
        ui.waitUntil(15_000) { ui.onAllNodesWithTag("report_folio").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("report_folio").assertTextEquals(existing.id)
        ui.onNodeWithTag("report_description").performScrollTo().assertTextEquals(existing.description)
        ui.onNodeWithTag("report_back_list").performScrollTo().performClick()
        ui.onNodeWithTag("report_back_list").performScrollTo().performClick()
        awaitWarning()
        scenario!!.onActivity { assertEquals(draft.description, it.draftModel.state.value.description) }
        assertUnchanged(listOf(existing.id))
        assertEquals(0, fixture.refreshCalls)
    }

    @Test
    fun confirmingAnotherIncidentStillRequiresFreshGpsAndKeepsBothReports() {
        val draft = reviewedDraft()
        val existing = seedSimilar(draft)
        fixture.expireFix()
        launchReview(draft)
        requestSave()
        awaitWarning()
        ui.onNodeWithTag("confirm_distinct_report").performClick()
        ui.waitUntil(15_000) { submissionState() is ReportSubmissionState.Invalid }
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("draft_description").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("draft_description").performScrollTo().assertTextContains(draft.description)
        assertUnchanged(listOf(existing.id))
        assertEquals(0, fixture.refreshCalls)

        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithTag("refresh_save_gps").performScrollTo().performClick()
        ui.waitUntil(10_000) { fixture.refreshCalls == 1 }
        ui.onNodeWithTag("save_gps_message").assertTextContains("Ubicación actualizada", substring = true)
        requestSave()
        awaitWarning()
        ui.onNodeWithTag("confirm_distinct_report").performClick()
        val saved = awaitReceipt()
        assertNotEquals(existing.id, saved.id)
        assertEquals(draft.description, saved.description)
        val reports = runBlocking { fixture.repository.list() }
        assertEquals(2, reports.size)
        assertEquals(existing, reports.single { it.id == existing.id })
        assertEquals(1, fixture.refreshCalls)
    }

    @Test
    fun cancellingThenCorrectingTypeRequiresANewCheckAndPreservesTheExistingReport() {
        val draft = reviewedDraft()
        val existing = seedSimilar(draft)
        fixture.clearFix()
        val queries = AtomicInteger()
        fixture.similarLookup = SimilarReportRepository { query ->
            queries.incrementAndGet()
            fixture.repository.findSimilar(query)
        }
        launchReview(draft)
        requestSave()
        awaitWarning()
        ui.onNodeWithTag("cancel_similar_save").performClick()
        ui.onAllNodesWithTag("similar_warning").assertCountEquals(0)
        assertEquals(ReportSimilarityState.Idle, similarityState())
        assertUnchanged(listOf(existing.id))

        ui.onNodeWithTag("edit_review").performScrollTo().performClick()
        ui.onNodeWithTag("type_OTHER").performScrollTo().performClick()
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        fixture.publishFreshFix()
        requestSave()
        val saved = awaitReceipt()
        assertEquals(IncidentType.OTHER, saved.type)
        assertEquals(2, queries.get())
        assertEquals(0, fixture.refreshCalls)
        assertEquals(setOf(existing.id, saved.id), runBlocking { fixture.repository.list().map { it.id }.toSet() })
    }

    @Test
    fun failedReadDoesNotWriteUntilTheUserExplicitlyContinues() {
        fixture.similarLookup = SimilarReportRepository { throw IOException("Isolated advisory read failure") }
        fixture.publishFreshFix()
        launchReview(reviewedDraft())
        requestSave()
        awaitWarning()
        ui.onNodeWithTag("similar_failure").performScrollTo().assertExists()
        assertUnchanged(emptyList())
        assertEquals(0, fixture.refreshCalls)

        ui.onNodeWithTag("confirm_distinct_report").assertIsEnabled().performClick()
        val saved = awaitReceipt()
        assertEquals(listOf(saved.id), runBlocking { fixture.repository.list().map { it.id } })
        assertEquals(saved.id, runBlocking { fixture.journal.read() }!!.requestId)
        assertEquals(0, fixture.refreshCalls)
    }

    @Test
    fun incompleteEmptySearchStillRequiresAnExplicitDecision() {
        fixture.similarLookup = SimilarReportRepository { SimilarReportPage(emptyList(), searchIncomplete = true) }
        fixture.publishFreshFix()
        launchReview(reviewedDraft())
        requestSave()
        awaitWarning()
        ui.onNodeWithTag("similar_incomplete").performScrollTo().assertTextContains("limitada", substring = true)
        assertUnchanged(emptyList())
        assertEquals(0, fixture.refreshCalls)
        ui.onNodeWithTag("confirm_distinct_report").assertIsEnabled().performClick()
        val saved = awaitReceipt()
        assertEquals(listOf(saved.id), runBlocking { fixture.repository.list().map { it.id } })
    }

    @Test
    fun delayedReadCannotSaveAfterLeavingAndReturningWithoutAnotherGesture() {
        val started = CompletableDeferred<Unit>()
        val result = CompletableDeferred<SimilarReportPage>()
        ownedDelayedRead = result
        val queries = AtomicInteger()
        fixture.similarLookup = SimilarReportRepository {
            queries.incrementAndGet()
            started.complete(Unit)
            // Simulates an uncancellable storage callback arriving after the view was stopped.
            withContext(NonCancellable) { result.await() }
        }
        fixture.publishFreshFix()
        launchReview(reviewedDraft())
        requestSave()
        runBlocking { withTimeout(10_000) { started.await() } }
        ui.onNodeWithTag("similar_checking").assertExists()
        scenario!!.moveToState(Lifecycle.State.CREATED)
        result.complete(SimilarReportPage(emptyList(), searchIncomplete = false))
        instrumentation.waitForIdleSync()
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        ui.waitForIdle()
        assertEquals(ReportSimilarityState.Idle, similarityState())
        assertEquals(ReportSubmissionState.Idle, submissionState())
        assertUnchanged(emptyList())
        assertEquals(0, fixture.refreshCalls)

        requestSave()
        val saved = awaitReceipt()
        assertEquals(2, queries.get())
        assertEquals(listOf(saved.id), runBlocking { fixture.repository.list().map { it.id } })
    }

    @Test
    fun ownCheckpointFolioIsExcludedAndRecoveredWithoutAnotherRowOrGps() {
        val draft = reviewedDraft()
        val id = UUID.randomUUID().toString()
        fixture.publishFreshFix()
        val existing = runBlocking { fixture.repository.createOnce(id, draft) }
        val checkpoint = ReportDraftCheckpoint(id, draft, Instant.now(), submittedFolio = id)
        runBlocking {
            fixture.checkpoint.write(checkpoint.copy(submittedFolio = null), expectedId = null)
            fixture.checkpoint.markSubmitted(id, id)
        }
        fixture.clearFix()
        val excluded = AtomicReference<String?>()
        fixture.similarLookup = SimilarReportRepository { query ->
            excluded.set(query.excludedId)
            fixture.repository.findSimilar(query)
        }
        launchReview(draft)
        requestSave()
        val saved = awaitReceipt()
        assertEquals(existing, saved)
        assertEquals(id, excluded.get())
        assertEquals(listOf(id), runBlocking { fixture.repository.list().map { it.id } })
        assertEquals(id, runBlocking { fixture.journal.read() }!!.requestId)
        assertEquals(checkpoint, runBlocking { fixture.checkpoint.read() })
        assertEquals(0, fixture.refreshCalls)
        ui.onAllNodesWithTag("similar_warning").assertCountEquals(0)
    }

    private fun launchReview(draft: NewReport) {
        scenario = ActivityScenario.launch(ReportDraftActivity::class.java)
        ui.waitUntil(15_000) {
            var ready = false
            scenario!!.onActivity { ready = it.submissionModel.state.value == ReportSubmissionState.Idle && it.checkpointModel.state.value.loaded }
            ready
        }
        // Map interactions have separate native acceptance; setting fixture fields does not
        // create GPS evidence and the actual createOnce guard still executes before insertion.
        scenario!!.onActivity { assertTrue(it.draftModel.restoreForCorrection(draft)) }
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithTag("review_description").performScrollTo().assertTextEquals(draft.description)
    }

    private fun requestSave() = ui.onNodeWithTag("save_report").performScrollTo().assertIsEnabled().performClick()

    private fun awaitWarning() {
        ui.waitUntil(15_000) { ui.onAllNodesWithTag("similar_warning").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("confirm_distinct_report").assertIsEnabled()
    }

    private fun awaitReceipt(): Report {
        ui.waitUntil(15_000) { submissionState() is ReportSubmissionState.Saved }
        ui.onNodeWithTag("saved_folio").assertExists()
        return (submissionState() as ReportSubmissionState.Saved).report
    }

    private fun submissionState(): ReportSubmissionState {
        var value: ReportSubmissionState = ReportSubmissionState.Restoring
        scenario!!.onActivity { value = it.submissionModel.state.value }
        return value
    }

    private fun similarityState(): ReportSimilarityState {
        var value: ReportSimilarityState = ReportSimilarityState.Idle
        scenario!!.onActivity { value = it.similarityModel.state.value }
        return value
    }

    private fun assertUnchanged(ids: List<String>) {
        assertEquals(ids.toSet(), runBlocking { fixture.repository.list().map { it.id }.toSet() })
        assertNull(runBlocking { fixture.journal.read() })
    }

    private fun seedSimilar(draft: NewReport): Report {
        fixture.publishFreshFix()
        return runBlocking { fixture.repository.create(draft.copy(description = "Una rama cayó junto a la entrada del parque.")) }
    }

    private fun reviewedDraft() = NewReport(
        IncidentType.RISK,
        Priority.MEDIUM,
        "Una luminaria quedó dañada cerca de la entrada del parque.",
        Instant.now().minusSeconds(60),
        ReportLocation("Entrada norte del parque", ReportTestFixture.LATITUDE, ReportTestFixture.LONGITUDE),
    )

    private fun capture(name: String) {
        ui.waitForIdle()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(screenshot)
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        screenshot.recycle()
    }
}
