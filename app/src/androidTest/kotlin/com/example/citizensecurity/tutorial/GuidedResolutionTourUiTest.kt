package com.example.citizensecurity.tutorial

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivityBinding
import com.example.citizensecurity.data.ReportStorageNames
import com.example.citizensecurity.data.SqliteAccountRepository
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.reports.ReportsActivity
import com.example.citizensecurity.resolution.ReportResolutionState
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real scoped screens and SQL snapshots: reading help must never declare an outcome. */
@RunWith(AndroidJUnit4::class)
class GuidedResolutionTourUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private val fixture get() = fixtureRule.fixture
    private var scenario: ActivityScenario<ReportsActivity>? = null
    private var accounts: SqliteAccountRepository? = null
    private var owner: LocalAccount? = null
    private val workspaces = mutableListOf<ReportWorkspace>()

    @Before fun useOnlyAnOwnedDisposableEmulator() {
        assertTrue("Run only on the owned disposable emulator", Build.HARDWARE in listOf("ranchu", "goldfish"))
        instrumentation.runOnMainSync { app.accountSession.signOut() }
    }

    @After fun closeOnlyOwnedWindowsAndSessions() {
        scenario?.close()
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filter { it.packageName == app.packageName }.forEach(Activity::finish)
            app.accountSession.signOut()
        }
        workspaces.forEach(ReportWorkspace::close)
        app.accountTestRepository = null
        accounts?.close()
        instrumentation.waitForIdleSync()
    }

    @Test fun authorHelpPreservesPreparationAndTheExactReviewedCommandAcrossRotationWithoutWriting() {
        accounts = SqliteAccountRepository(app, "tour_resolution_accounts_${UUID.randomUUID()}.db")
        app.accountTestRepository = requireNotNull(accounts)
        owner = runBlocking { requireNotNull(accounts).register("autor_recorrido", UUID.randomUUID().toString()) }
        instrumentation.runOnMainSync { app.accountSession.signIn(requireNotNull(owner)) }
        fixture.publishFreshFix()
        val workspace = app.openReportWorkspace(app.accountSession.capture()).also(workspaces::add)
        val report = runBlocking { workspace.repository.createOnce(UUID.randomUUID().toString(), draft()) }
        fixture.clearFix()
        val before = snapshot(report.id)
        val screen = launch(report.id)
        awaitTag("resolution_current")
        val reason = "Motivo privado preparado que la ayuda no debe copiar ni guardar"
        click("resolution_choice_UNRESOLVED")
        node("resolution_reason").performTextReplacement(reason)
        closeSoftKeyboard()
        val prepared = resolutionState() as ReportResolutionState.Ready
        assertEquals(ReportResolution.UNRESOLVED, prepared.target)
        assertEquals(reason, prepared.reason)
        assertEquals(ReportResolutionActor(requireNotNull(owner).id, ReportResolutionActorKind.AUTHOR), prepared.page.actor)

        openResolutionHelp()
        progress(1)
        assertStaticBodyDoesNotContain(report.id, requireNotNull(owner).id, reason)
        next(); progress(2)
        assertStaticBodyDoesNotContain(report.id, requireNotNull(owner).id, reason)
        closeHelp()
        assertSame("Help must leave the existing preparation untouched", prepared, resolutionState())
        assertEquals(before, snapshot(report.id))

        click("resolution_review")
        awaitTag("resolution_confirm")
        val command = (resolutionState() as ReportResolutionState.Reviewing).command
        openResolutionHelp()
        next(); progress(2)
        val target = otherOrientation(screen)
        screen.onActivity { it.requestedOrientation = target.requested }
        ui.waitUntil(15_000) {
            var rotated = false
            screen.onActivity { rotated = it.resources.configuration.orientation == target.configuration }
            rotated
        }
        progress(2)
        assertSame("The open guide must retain the exact reviewed UUID/body", command,
            (resolutionState() as ReportResolutionState.Reviewing).command)
        next(); progress(3)
        assertStaticBodyDoesNotContain(report.id, requireNotNull(owner).id, reason)
        previous(); progress(2)
        capture("tour45-resolution-rotated.png")
        closeHelp()
        assertSame(command, (resolutionState() as ReportResolutionState.Reviewing).command)
        assertEquals(before, snapshot(report.id))
        click("resolution_edit")
        awaitTag("resolution_reason")
        val editable = resolutionState() as ReportResolutionState.Ready
        assertEquals(reason, editable.reason)
        assertEquals(ReportResolution.UNRESOLVED, editable.target)
        assertEquals(before, snapshot(report.id))
        assertEquals(0, fixture.refreshCalls)
        assertNull(fixture.currentFix())
    }

    @Test fun academicHelpExplainsTheLocalOutcomeWithoutGrantingAuthorityOrChangingSql() {
        fixture.publishFreshFix()
        val report = runBlocking { fixture.repository.createOnce(UUID.randomUUID().toString(), draft()) }
        fixture.clearFix()
        val before = snapshot(report.id)
        assertNull(before.actor)
        launch(report.id)
        awaitTag("resolution_current")
        assertNull((resolutionState() as ReportResolutionState.Ready).page.actor)
        ui.onNodeWithTag("resolution_reason").assertDoesNotExist()
        ui.onNodeWithTag("resolution_review").assertDoesNotExist()

        // Detail discovers the separate contextual guide without changing its existing step count.
        click("tour_open_detail")
        next()
        ui.onNodeWithTag("tour_body").assertTextContains("Resultado del incidente", substring = true)
        closeHelp()
        openResolutionHelp()
        progress(1)
        ui.onNodeWithTag("tour_body").assertTextContains("Cerrado no significa Solucionado", substring = true)
        next(); progress(2)
        ui.onNodeWithTag("tour_body").assertTextContains("El acceso académico permite consultar", substring = true)
        next(); progress(3)
        next(); progress(4)
        ui.onNodeWithTag("tour_body").assertTextContains("no se conserva ni se repite automáticamente", substring = true)
        ui.onNodeWithTag("tour_next").assertTextEquals("Listo")
        next()
        ui.onNodeWithTag("tour_progress").assertDoesNotExist()
        openResolutionHelp(); progress(1)
        closeHelp()
        ui.onNodeWithTag("resolution_reason").assertDoesNotExist()
        ui.onNodeWithTag("resolution_review").assertDoesNotExist()
        ui.onNodeWithTag("resolution_confirm").assertDoesNotExist()
        assertEquals(before, snapshot(report.id))
        assertNull(snapshot(report.id).actor)
        assertNull((resolutionState() as ReportResolutionState.Ready).page.actor)
        assertEquals(0, fixture.refreshCalls)
        assertNull(fixture.currentFix())
    }

    private fun draft() = NewReport(IncidentType.RISK, Priority.MEDIUM,
        "Reporte privado para comprobar la guía contextual del resultado", Instant.now().minusSeconds(60),
        ReportLocation("Referencia del recorrido de ayuda", 19.4326, -99.1332))

    private fun launch(id: String): ActivityScenario<ReportsActivity> = ActivityScenario.launch<ReportsActivity>(
        ReportWorkspaceActivityBinding.stamp(Intent(app, ReportsActivity::class.java)
            .putExtra(ReportsActivity.EXTRA_FOLIO, id), app.accountSession.capture()),
    ).also { scenario = it }

    private fun snapshot(id: String): ResolutionSnapshot {
        val account = owner
        if (account == null) return runBlocking { snapshot(fixture.repository, id) }
        return SqliteReportRepository(app, ReportStorageNames.fromAccountId(account.id).databaseName).use {
            runBlocking { snapshot(it, id) }
        }
    }
    private suspend fun snapshot(repository: SqliteReportRepository, id: String): ResolutionSnapshot {
        val page = requireNotNull(repository.readResolution(ReportResolutionQuery(id)))
        return ResolutionSnapshot(requireNotNull(repository.findById(id)).status, page.resolution,
            page.latestSequence, page.events, page.actor)
    }
    private data class ResolutionSnapshot(
        val status: ReportStatus,
        val resolution: ReportResolution,
        val sequence: Long,
        val events: List<ReportResolutionEvent>,
        val actor: ReportResolutionActor?,
    )
    private fun resolutionState(): ReportResolutionState {
        var state: ReportResolutionState? = null
        requireNotNull(scenario).onActivity { state = it.resolutionModel.state.value }
        return requireNotNull(state)
    }
    private fun openResolutionHelp() {
        node("tour_open_resolution").assert(hasContentDescription("Abrir recorrido de Resultado del incidente"))
        click("tour_open_resolution")
        ui.waitUntil(15_000) { ui.onAllNodesWithTag("tour_progress").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun next() = ui.onNodeWithTag("tour_next").performScrollTo().performClick()
    private fun previous() = ui.onNodeWithTag("tour_previous").performScrollTo().performClick()
    private fun closeHelp() {
        ui.onNodeWithTag("tour_close").performScrollTo().performClick()
        ui.onNodeWithTag("tour_progress").assertDoesNotExist()
    }
    private fun progress(step: Int) = ui.onNodeWithTag("tour_progress").assertTextEquals("Paso $step de 4")
    private fun assertStaticBodyDoesNotContain(vararg privateValues: String) {
        val content = ui.onNodeWithTag("tour_body").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        privateValues.forEach { assertFalse("Private data must not be copied into guide content", content.contains(it)) }
    }
    private fun otherOrientation(screen: ActivityScenario<ReportsActivity>): Orientation {
        var portrait = false
        screen.onActivity { portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
        return if (portrait) Orientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        else Orientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
    }
    private data class Orientation(val requested: Int, val configuration: Int)
    private fun capture(name: String) {
        ui.waitForIdle()
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(app.getExternalFilesDir(null), name).outputStream().use {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { image.recycle() }
    }
    private fun node(tag: String): SemanticsNodeInteraction {
        ui.onNodeWithTag("report_detail").performScrollToNode(hasTestTag(tag))
        exposeInsideViewport(tag)
        return ui.onNodeWithTag(tag).assertIsDisplayed()
    }
    private fun click(tag: String) {
        node(tag)
        if (tag.startsWith("resolution_choice_")) {
            ui.onNodeWithTag(tag).performScrollTo()
            exposeInsideViewport(tag)
        }
        ui.onNodeWithTag(tag).assertIsDisplayed().performClick()
    }
    private fun exposeInsideViewport(tag: String) {
        repeat(12) {
            val target = ui.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val scroller = ui.onNodeWithTag("report_detail")
            val viewport = scroller.getUnclippedBoundsInRoot()
            val centerY = (target.top.value + target.bottom.value) / 2f
            if (centerY > viewport.top.value + 8f && centerY < viewport.bottom.value - 8f) return
            val density = scroller.fetchSemanticsNode().boundsInRoot.height /
                (viewport.bottom.value - viewport.top.value)
            val margin = ((target.bottom.value - target.top.value) / 2f + 12f)
                .coerceIn(24f, (viewport.bottom.value - viewport.top.value) / 3f)
            val desiredCenter = centerY.coerceIn(viewport.top.value + margin, viewport.bottom.value - margin)
            scroller.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                check(scroll(0f, (centerY - desiredCenter) * density))
            }
        }
        throw AssertionError("The real touch target $tag remains outside the outer detail viewport")
    }
    private fun awaitTag(tag: String) {
        ui.waitUntil(15_000) {
            if (ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) true
            else runCatching {
                ui.onNodeWithTag("report_detail").performScrollToNode(hasTestTag(tag))
                ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        node(tag)
    }
}
