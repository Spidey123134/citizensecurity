package com.example.citizensecurity.resolution

import android.app.Activity
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivityBinding
import com.example.citizensecurity.administration.AdministrationConsultationActivity
import com.example.citizensecurity.data.LocalAdministratorStore
import com.example.citizensecurity.data.ReportStorageNames
import com.example.citizensecurity.data.SqliteAccountRepository
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.reports.ReportsActivity
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

/**
 * Real registered accounts, captured application workspaces, role file and SQLite declarations.
 * Assignment is an isolated technical precondition (native credential enrollment was tested in
 * phase 40); this suite does not change a PIN or use a fake declaration backend/receipt. Only GPS
 * seeding is controlled by the established location fixture on the owned disposable emulator.
 */
@RunWith(AndroidJUnit4::class)
class ReportResolutionFlowUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private val fixture get() = fixtureRule.fixture
    private val scenarios = mutableListOf<ActivityScenario<*>>()
    private val workspaces = mutableListOf<ReportWorkspace>()
    private var accounts: SqliteAccountRepository? = null
    private var account: LocalAccount? = null
    private var screen = "report_detail"

    @Before fun useOnlyTheOwnedDisposableEmulator() {
        assertTrue("Run only on the owned disposable emulator", Build.HARDWARE in listOf("ranchu", "goldfish"))
        instrumentation.runOnMainSync { app.accountSession.signOut() }
    }

    @After fun closeOnlyTestWindowsAndSessions() {
        scenarios.asReversed().forEach { it.close() }
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

    @Test fun theRealAuthorCanDeclareAndCorrectWithoutLosingWhoWhenOrWhy() {
        register("autor_resultado")
        val owner = requireNotNull(account)
        val report = seedOwnReport()
        launchReport(report.id)
        awaitTag("resolution_current")
        assertEquals(0L, page(report.id).latestSequence)
        val firstReason = "El riesgo se retiró y pude comprobarlo al pasar por el lugar"
        val lowerBound = Instant.now()
        review(ReportResolution.RESOLVED, firstReason)
        val reviewed = reviewedCommand()
        assertEquals(0L, page(report.id).latestSequence)
        click("resolution_confirm")
        awaitTag("resolution_confirmed")
        val first = page(report.id).events.single()
        assertTrue("The saved receipt must match the exact reviewed UUID and body", first.matches(reviewed))
        assertEquals(ReportResolution.RESOLVED, first.toResolution)
        assertEquals(ReportResolutionActor(owner.id, ReportResolutionActorKind.AUTHOR), first.actor)
        assertEquals(firstReason, first.reason)
        assertFalse(first.recordedAt.isBefore(lowerBound))
        assertFalse(first.recordedAt.isAfter(Instant.now()))
        assertEquals(ReportStatus.REPORTED, reportStatus(report.id))
        node("resolution_event_1").assert(hasAnyDescendant(hasText(owner.id, substring = true)))
        acknowledgeAndRefresh()
        val secondReason = "El problema reapareció y sigue presente en el mismo punto"
        review(ReportResolution.UNRESOLVED, secondReason)
        click("resolution_confirm")
        awaitTag("resolution_confirmed")
        val corrected = page(report.id)
        assertEquals(2L, corrected.latestSequence)
        assertEquals(ReportResolution.UNRESOLVED, corrected.resolution)
        assertEquals(listOf(2L, 1L), corrected.events.map { it.sequence })
        assertEquals(listOf(secondReason, firstReason), corrected.events.map { it.reason })
        assertEquals(first, corrected.events.last())
        assertEquals(ReportResolution.RESOLVED, corrected.events.first().fromResolution)
        assertNotEquals(first.operationId, corrected.events.first().operationId)
        assertTrue(corrected.events.all { it.actor.accountId == owner.id && it.actor.kind == ReportResolutionActorKind.AUTHOR })
        acknowledgeAndRefresh()
        node("resolution_history").assert(hasAnyDescendant(hasText(firstReason, substring = true)))
        assertEquals(2L, page(report.id).latestSequence)
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun reviewingAndRecreatingTheRealScreenDoNotWriteADeclaration() {
        register("autor_revision")
        val report = seedOwnReport()
        val scenario = launchReport(report.id)
        awaitTag("resolution_current")
        val reason = "Motivo revisado que todavía no he decidido confirmar"
        review(ReportResolution.UNRESOLVED, reason)
        val reviewed = reviewedCommand()
        assertEquals(0L, page(report.id).latestSequence)
        scenario.recreate()
        awaitTag("resolution_confirm")
        assertSame("Rotation retains the exact in-memory reviewed command", reviewed, reviewedCommand())
        node("resolution_confirm").assertIsEnabled()
        assertTrue(ui.onAllNodesWithText(reason, substring = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals(0L, page(report.id).latestSequence)
        assertTrue(page(report.id).events.isEmpty())
        click("resolution_edit")
        awaitTag("resolution_review")
        assertEquals(0L, page(report.id).latestSequence)
        assertEquals(ReportStatus.REPORTED, reportStatus(report.id))
    }

    @Test fun theAssignedAdministratorDeclaresInItsOwnWorkspaceWithAnotherStoredAuthor() {
        register("admin_local", assign = true)
        val administrator = requireNotNull(account)
        val originalAuthor = UUID.randomUUID().toString()
        val report = seedLegacyReport(originalAuthor)
        launchAdministration(report.id)
        awaitTag("resolution_current")
        val reason = "Comprobación local registrada por la cuenta administrativa asignada"
        review(ReportResolution.RESOLVED, reason)
        click("resolution_confirm")
        awaitTag("resolution_confirmed")
        val event = page(report.id).events.single()
        assertEquals(ReportResolutionActor(administrator.id, ReportResolutionActorKind.LOCAL_ADMINISTRATOR), event.actor)
        assertNotEquals(originalAuthor, event.actor.accountId)
        assertEquals(reason, event.reason)
        assertEquals(ReportStatus.REPORTED, reportStatus(report.id))
        node("resolution_event_1").assert(hasAnyDescendant(hasText(administrator.id, substring = true)))
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun academicAccessCanReadAnExistingReportButCannotDeclareItsOutcome() {
        fixture.publishFreshFix()
        val report = runBlocking { fixture.repository.createOnce(UUID.randomUUID().toString(), draft()) }
        launchReport(report.id)
        awaitTag("resolution_current")
        node("resolution_current").assert(hasText("Sin confirmar", substring = true))
        ui.onNodeWithTag("resolution_review").assertDoesNotExist()
        ui.onNodeWithTag("resolution_confirm").assertDoesNotExist()
        ui.onNodeWithTag("resolution_reason").assertDoesNotExist()
        val saved = runBlocking { requireNotNull(fixture.repository.readResolution(ReportResolutionQuery(report.id))) }
        assertEquals(0L, saved.latestSequence)
        assertNull(saved.actor)
        assertTrue(saved.events.isEmpty())
    }

    @Test fun aClosedSchemaFourReportMigratesWithoutInventingAResolutionOrReplacingHistory() {
        register("autor_antiguo")
        val owner = requireNotNull(account)
        val report = seedLegacyReport(owner.id, closed = true)
        val file = databaseFile(owner)
        val originalStatus = reportStatus(report.id)
        assertEquals(ReportStatus.CLOSED, originalStatus)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
            // Owned fixture starts as a valid, empty-outcome schema 4 installation.
            assertEquals(5, it.version)
            it.execSQL("DROP TABLE report_resolution_events")
            it.version = 4
        }
        launchReport(report.id)
        awaitTag("resolution_current")
        node("resolution_current").assert(hasText("Sin confirmar", substring = true))
        val outcome = page(report.id)
        assertEquals(ReportResolution.UNCONFIRMED, outcome.resolution)
        assertEquals(0L, outcome.latestSequence)
        assertTrue(outcome.events.isEmpty())
        assertEquals(originalStatus, reportStatus(report.id))
        SqliteReportRepository(app, ReportStorageNames.fromAccountId(owner.id).databaseName).use {
            val history = runBlocking { requireNotNull(it.readHistory(ReportHistoryQuery(report.id))) }
            assertEquals(listOf(4L, 3L, 2L, 1L), history.events.map { event -> event.sequence })
            assertEquals(owner.id, history.events.last().actorAccountId)
        }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { assertEquals(5, it.version) }
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun switchingAccountsRemovesTheUnconfirmedReviewAndDoesNotLeakOrWriteIt() {
        register("autor_privado")
        val first = requireNotNull(account)
        val report = seedOwnReport()
        val firstScenario = launchReport(report.id)
        awaitTag("resolution_current")
        val secret = "Motivo privado de la primera cuenta que no debe llegar a la segunda"
        review(ReportResolution.RESOLVED, secret)
        val second = registerAdditional("segunda_cuenta")
        instrumentation.runOnMainSync { app.accountSession.signIn(second) }
        account = second
        ui.waitUntil(10_000) { firstScenario.state == Lifecycle.State.DESTROYED }
        assertFalse("The finished account screen must not retain its private review", textExistsOrNoHierarchy(secret))
        assertEquals(0L, page(report.id, first).latestSequence)
        launchReport(report.id)
        awaitTag("report_detail")
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Ese folio", substring = true).fetchSemanticsNodes().isNotEmpty() ||
            ui.onAllNodesWithText("No se encontró", substring = true).fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodesWithText(secret, substring = true).assertCountEquals(0)
        ui.onNodeWithTag("resolution_confirm").assertDoesNotExist()
        assertEquals(0L, page(report.id, first).latestSequence)
    }

    @Test fun losingTheAdministratorAssignmentBeforeConfirmationDoesNotWriteOrRepairIt() {
        register("admin_local", assign = true)
        val report = seedLegacyReport(UUID.randomUUID().toString())
        launchAdministration(report.id)
        awaitTag("resolution_current")
        val secret = "Motivo administrativo privado que requiere una asignación vigente"
        review(ReportResolution.RESOLVED, secret)
        val assignment = File(fixture.contactsContext.filesDir, LocalAdministratorStore.FILE_NAME)
        val corrupt = "owned unreadable assignment fixture".toByteArray()
        assignment.writeBytes(corrupt)
        click("resolution_confirm")
        // The declaration denial triggers a separate READ check. Corrupt authority revokes
        // that capability too, so the stable final screen must retire the whole consultation.
        awaitTag("admin_consult_denied")
        ui.onNodeWithTag("resolution_confirm").assertDoesNotExist()
        ui.onAllNodesWithText(secret, substring = true).assertCountEquals(0)
        assertArrayEquals(corrupt, assignment.readBytes())
        assertEquals(0L, page(report.id).latestSequence)
        assertEquals(ReportStatus.REPORTED, reportStatus(report.id))
    }

    @Test fun aPausedWindowCannotCommitAndRecoveryReadsBeforeAnExplicitRetryOfTheSameCommand() {
        register("autor_ventana")
        val report = seedOwnReport()
        val scenario = launchReport(report.id)
        awaitTag("resolution_current")
        review(ReportResolution.RESOLVED, "Resultado revisado que requiere una pantalla activa al guardar")
        lateinit var model: ReportResolutionViewModel
        lateinit var prepared: ReportResolutionState.Reviewing
        scenario.onActivity { model = it.resolutionModel; prepared = model.state.value as ReportResolutionState.Reviewing }
        val exactCommand = prepared.command
        scenario.moveToState(Lifecycle.State.CREATED)
        // Adversarial technical call after a real review; there is no visible confirm gesture here.
        // It must still be rejected by the actual last precommit guard, not just a disabled button.
        instrumentation.runOnMainSync { assertTrue(model.confirm(prepared)) }
        awaitModel(model) { it is ReportResolutionState.Uncertain }
        assertEquals(0L, page(report.id).latestSequence)
        assertTrue(page(report.id).events.isEmpty())
        scenario.moveToState(Lifecycle.State.RESUMED)
        awaitTag("resolution_recover")
        click("resolution_recover")
        awaitModel(model) { it is ReportResolutionState.Uncertain && it.canRetry }
        assertEquals(0L, page(report.id).latestSequence)
        assertSame(exactCommand, (model.state.value as ReportResolutionState.Uncertain).command)
        click("resolution_retry")
        awaitTag("resolution_confirmed")
        val event = page(report.id).events.single()
        assertTrue(event.matches(exactCommand))
        assertEquals(1L, page(report.id).latestSequence)
        assertEquals(ReportStatus.REPORTED, reportStatus(report.id))
    }

    private fun register(username: String, assign: Boolean = false) {
        accounts = SqliteAccountRepository(app, "resolution_accounts_test_${UUID.randomUUID()}.db")
        app.accountTestRepository = requireNotNull(accounts)
        account = registerAdditional(username)
        instrumentation.runOnMainSync { app.accountSession.signIn(requireNotNull(account)) }
        if (assign) {
            require(fixture.contactsContext.filesDir.name.startsWith("trusted_contacts_ui_"))
            app.openLocalAdministratorStore().assignFirst(requireNotNull(account), UUID.randomUUID().toString(), Instant.now()) {}
        }
    }

    private fun registerAdditional(username: String): LocalAccount = runBlocking {
        val password = UUID.randomUUID().toString()
        requireNotNull(accounts).register(username, password).also {
            assertEquals(it, requireNotNull(accounts).authenticate(username, password))
        }
    }

    private fun seedOwnReport(): Report = runBlocking {
        fixture.publishFreshFix()
        val workspace = app.openReportWorkspace(app.accountSession.capture()).also(workspaces::add)
        workspace.repository.createOnce(UUID.randomUUID().toString(), draft())
    }

    /** Constructs only this account UUID's historical fixture, then the real app owns all reads/writes. */
    private fun seedLegacyReport(creator: String?, closed: Boolean = false): Report {
        val owner = requireNotNull(account)
        return SqliteReportRepository(app, ReportStorageNames.fromAccountId(owner.id).databaseName,
            authorAccountId = creator, statusAuthority = { owner.id }).use { repository -> runBlocking {
            val report = repository.createOnce(UUID.randomUUID().toString(), draft())
            if (closed) {
                var status = ReportStatus.REPORTED
                listOf(ReportStatus.IN_REVIEW, ReportStatus.ATTENDED, ReportStatus.CLOSED).forEachIndexed { index, target ->
                    repository.changeStatus(ReportStatusChangeCommand(UUID.randomUUID().toString(), report.id,
                        index + 1L, status, target, "Seguimiento histórico local del paso ${index + 1}"))
                    status = target
                }
            }
            report
        } }
    }

    private fun draft() = NewReport(IncidentType.RISK, Priority.MEDIUM,
        "Reporte privado para comprobar el resultado real del incidente", Instant.now().minusSeconds(60),
        ReportLocation("Referencia local del recorrido de resultado", 19.4326, -99.1332))

    private fun databaseFile(owner: LocalAccount) = app.getDatabasePath(ReportStorageNames.fromAccountId(owner.id).databaseName)
    private fun page(id: String, owner: LocalAccount = requireNotNull(account)): ReportResolutionPage =
        SqliteReportRepository(app, ReportStorageNames.fromAccountId(owner.id).databaseName).use {
            runBlocking { requireNotNull(it.readResolution(ReportResolutionQuery(id))) }
        }
    private fun reportStatus(id: String): ReportStatus = SqliteReportRepository(app,
        ReportStorageNames.fromAccountId(requireNotNull(account).id).databaseName).use {
        runBlocking { requireNotNull(it.findById(id)).status }
    }

    private fun launchReport(id: String): ActivityScenario<ReportsActivity> {
        screen = "report_detail"
        return launch<ReportsActivity>(Intent(app, ReportsActivity::class.java).putExtra(ReportsActivity.EXTRA_FOLIO, id))
    }
    private fun launchAdministration(id: String) {
        screen = "admin_consult_screen"
        launch<AdministrationConsultationActivity>(Intent(app, AdministrationConsultationActivity::class.java))
        awaitTag("admin_consult_open_$id")
        click("admin_consult_open_$id")
    }
    private inline fun <reified T : Activity> launch(intent: Intent): ActivityScenario<T> = ActivityScenario.launch<T>(
        ReportWorkspaceActivityBinding.stamp(intent, app.accountSession.capture()),
    ).also(scenarios::add)

    private fun review(target: ReportResolution, reason: String) {
        click("resolution_choice_${target.name}")
        try {
            val afterChoice = resolutionState()
            assertTrue("Choice must leave an editable state: ${describe(afterChoice)}", afterChoice is ReportResolutionState.Ready)
            assertEquals("Immediately after the real chip click: ${describe(afterChoice)}", target,
                (afterChoice as ReportResolutionState.Ready).target)
            ui.onNodeWithTag("resolution_choice_${target.name}").assertIsSelected()
        } catch (failure: Throwable) {
            val capture = captureOwnedFixtureFailure()
            throw AssertionError("Choice did not select its real target; model=${describe(resolutionState())}; fixture screenshot/tree=$capture", failure)
        }
        node("resolution_reason").performTextReplacement(reason)
        closeSoftKeyboard()
        val before = resolutionState()
        assertTrue("Expected editable state before Review: ${describe(before)}", before is ReportResolutionState.Ready)
        before as ReportResolutionState.Ready
        assertEquals("The choice must survive text input and keyboard dismissal: ${describe(before)}", target, before.target)
        assertEquals("The actual reason must reach the real model", reason, before.reason)
        assertTrue("Review must be enabled: ${describe(before)}", before.canReview)
        node("resolution_review").assertIsEnabled()
        click("resolution_review")
        try { awaitTag("resolution_confirm") }
        catch (failure: Throwable) {
            val after = resolutionState()
            val capture = captureOwnedFixtureFailure()
            throw AssertionError("Review did not expose Confirm; actual model=${describe(after)}; fixture screenshot=$capture", failure)
        }
    }
    private fun acknowledgeAndRefresh() {
        click("resolution_acknowledge")
        awaitTag("resolution_current")
    }
    private fun reviewedCommand(): ReportResolutionCommand {
        return (resolutionState() as ReportResolutionState.Reviewing).command
    }
    private fun resolutionState(): ReportResolutionState {
        var state: ReportResolutionState? = null
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach { activity ->
                state = when (activity) {
                    is ReportsActivity -> activity.resolutionModel.state.value
                    is AdministrationConsultationActivity -> activity.resolutionModel.state.value
                    else -> state
                }
            }
        }
        return requireNotNull(state)
    }
    private fun describe(state: ReportResolutionState): String = when (state) {
        is ReportResolutionState.Ready -> "Ready(seq=${state.page.latestSequence}, actor=${state.page.actor}, target=${state.target}, reasonLength=${state.reason.length}, canReview=${state.canReview}, message=${state.message})"
        is ReportResolutionState.Reviewing -> "Reviewing(operation=${state.command.operationId}, target=${state.command.targetResolution})"
        is ReportResolutionState.Error -> "Error(${state.reportId})"
        is ReportResolutionState.Loading -> "Loading(${state.reportId})"
        is ReportResolutionState.Uncertain -> "Uncertain(retry=${state.canRetry}, ${state.message})"
        else -> state.javaClass.simpleName
    }
    private fun textExistsOrNoHierarchy(value: String): Boolean = try {
        ui.onAllNodesWithText(value, substring = true).fetchSemanticsNodes().isNotEmpty()
    } catch (failure: IllegalStateException) {
        if (failure.message?.startsWith("No compose hierarchies found") != true) throw failure
        false
    }
    private fun captureOwnedFixtureFailure(): String? = runCatching {
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val directory = requireNotNull(app.getExternalFilesDir(null))
        val file = File(directory, "resolution44-fixture-timeout-${UUID.randomUUID()}.png")
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        runCatching {
            File(directory, file.nameWithoutExtension + ".txt")
                .writeText(ui.onRoot(useUnmergedTree = true).printToString(), Charsets.UTF_8)
        }
        file.absolutePath.also { println("resolution44 fixture screenshot: $it") }
    }.getOrNull()
    private fun awaitModel(model: ReportResolutionViewModel, predicate: (ReportResolutionState) -> Boolean) {
        ui.waitUntil(15_000) {
            var matches = false
            instrumentation.runOnMainSync { matches = predicate(model.state.value) }
            matches
        }
    }
    private fun node(tag: String): SemanticsNodeInteraction {
        ui.onNodeWithTag(screen).performScrollToNode(hasTestTag(tag))
        exposeInsideViewport(tag)
        return ui.onNodeWithTag(tag).assertIsDisplayed()
    }
    private fun click(tag: String) {
        node(tag)
        if (tag.startsWith("resolution_choice_")) {
            // Its horizontal Row is nested in a LazyColumn. Compose's generic scrollTo may
            // move that column back to the item's start, leaving this chip below the viewport.
            ui.onNodeWithTag(tag).performScrollTo()
            exposeInsideViewport(tag)
        }
        ui.onNodeWithTag(tag).assertIsDisplayed().performClick()
    }
    private fun exposeInsideViewport(tag: String) {
        repeat(12) {
            val target = ui.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val scroller = ui.onNodeWithTag(screen)
            val viewport = scroller.getUnclippedBoundsInRoot()
            val centerY = (target.top.value + target.bottom.value) / 2f
            if (centerY > viewport.top.value + 8f && centerY < viewport.bottom.value - 8f) return
            // A default swipe flings from one end of this long item to the other. Scroll only
            // the missing distance in the OUTER column; the subsequent click remains a touch.
            val density = scroller.fetchSemanticsNode().boundsInRoot.height /
                (viewport.bottom.value - viewport.top.value)
            val margin = ((target.bottom.value - target.top.value) / 2f + 12f)
                .coerceIn(24f, (viewport.bottom.value - viewport.top.value) / 3f)
            val desiredCenter = centerY.coerceIn(viewport.top.value + margin, viewport.bottom.value - margin)
            val delta = (centerY - desiredCenter) * density
            scroller.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                check(scroll(0f, delta)) { "The outer LazyColumn rejected scrolling to $tag" }
            }
        }
        val actual = ui.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val viewport = ui.onNodeWithTag(screen).getUnclippedBoundsInRoot()
        throw AssertionError("The actual touch target $tag is outside its viewport after precise outer scrolling: target=$actual viewport=$viewport")
    }
    private fun awaitTag(tag: String) {
        ui.waitUntil(15_000) {
            if (ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) true
            else runCatching {
                // Real lazy screens may not compose a lower section until it is scrolled into view.
                ui.onNodeWithTag(screen).performScrollToNode(hasTestTag(tag))
                ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        if (tag != screen) node(tag)
    }
}
