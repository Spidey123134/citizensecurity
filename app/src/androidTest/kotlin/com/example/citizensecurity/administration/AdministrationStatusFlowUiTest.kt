package com.example.citizensecurity.administration

import android.app.Activity
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivityBinding
import com.example.citizensecurity.data.LocalAdministratorStore
import com.example.citizensecurity.data.ReportStatusChangeStore
import com.example.citizensecurity.data.ReportStorageNames
import com.example.citizensecurity.data.SqliteAccountRepository
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.report.ReportStatusRecoveryState
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real account SQLite, role file, report/history and journal; no simulated write receipt. */
class AdministrationStatusFlowUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private val fixture get() = fixtureRule.fixture
    private val scenarios = mutableListOf<ActivityScenario<*>>()
    private val workspaces = mutableListOf<ReportWorkspace>()
    private var accounts: SqliteAccountRepository? = null
    private lateinit var account: LocalAccount

    @After fun closeOnlyFixtureWindowsAndSessions() {
        scenarios.asReversed().forEach { it.close() }
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filter { it.packageName == app.packageName }.forEach(Activity::finish)
            app.accountSession.signOut()
        }
        workspaces.forEach(ReportWorkspace::close)
        app.accountTestRepository = null
        accounts?.close()
    }

    @Test fun reviewedChangeSurvivesRotationAndReturnsToTheUpdatedConsultation() {
        registerAndAssign()
        val report = seedReport()
        launch<AdministrationConsultationActivity>()
        awaitConsult { it is AdministrationConsultationState.ReadyPage }
        clickConsult("admin_consult_open_${report.id}")
        awaitConsult { it is AdministrationConsultationState.ReadyReport }
        clickConsult("admin_consult_change_status")
        awaitRecovery { it === ReportStatusRecoveryState.AwaitingRecovery }
        assertEquals(1L, history(report.id).latestSequence)
        clickStatus("admin_status_recover")
        awaitRecovery { it === ReportStatusRecoveryState.Ready }
        clickStatus("admin_status_load")
        awaitPreparation { it is AdministrationStatusChangeState.Editing }
        val reason = "Se revisó la información local del reporte"
        status("admin_status_reason").performTextInput(reason)
        clickStatus("admin_status_review")
        awaitPreparation { it is AdministrationStatusChangeState.Reviewing }
        assertEquals(1L, history(report.id).latestSequence)
        recreateStatus()
        awaitPreparation { it is AdministrationStatusChangeState.Reviewing }
        assertEquals(1L, history(report.id).latestSequence)
        clickStatus("admin_status_confirm")
        awaitRecovery { it is ReportStatusRecoveryState.Confirmed }
        val event = history(report.id).events.first()
        assertEquals(2L, event.sequence)
        assertEquals(ReportStatus.IN_REVIEW, event.toStatus)
        assertEquals(account.id, event.actorAccountId)
        assertEquals(reason, event.reason)
        status("admin_status_confirmed")
        assertTrue(ui.onAllNodesWithText(account.id, substring = true).fetchSemanticsNodes().isNotEmpty())
        clickStatus("admin_status_acknowledge")
        awaitRecovery { it === ReportStatusRecoveryState.Ready }
        assertNull(runBlocking { journal().read() })
        assertEquals(2L, history(report.id).latestSequence)
        clickStatus("admin_status_close")
        awaitConsult { it is AdministrationConsultationState.ReadyReport && it.history.latestSequence == 2L }
        ui.onNodeWithTag("admin_consult_screen").performScrollToNode(hasTestTag("admin_consult_history_event_2"))
        ui.onNodeWithText(reason, substring = true).assertExists()
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun openingWithoutAnAssignmentDeniesBeforeCreatingReportOrJournalFiles() {
        registerAndAssign(assign = false)
        val database = app.getDatabasePath(ReportStorageNames.fromAccountId(account.id).databaseName)
        val pending = File(fixture.contactsContext.filesDir,
            ReportStatusChangeStore.fileName(ReportStatusChangeScope(account.id, account.id)))
        assertFalse(database.exists())
        launch<AdministrationStatusChangeActivity>()
        awaitRecovery { it === ReportStatusRecoveryState.Closed }
        ui.onNodeWithTag("admin_status_denied").assertExists()
        assertFalse(database.exists())
        assertFalse(pending.exists())
        assertFalse(File(fixture.contactsContext.filesDir, LocalAdministratorStore.FILE_NAME).exists())
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun aNewWindowRecoversACommittedReceiptWithoutAnotherTransition() {
        registerAndAssign()
        val report = seedReport()
        val request = request(report)
        val binding = app.openReportAdministration(workspace())
        val access = binding.openStatusChangeAccess()
        val journal = app.openReportStatusChangeJournal(binding, access)
        runBlocking { journal.retain(request); access.change(request) }
        assertEquals(2L, history(report.id).latestSequence)
        val first = launch<AdministrationStatusChangeActivity>()
        awaitRecovery { it === ReportStatusRecoveryState.AwaitingRecovery }
        assertEquals(request, runBlocking { journal.read() })
        clickStatus("admin_status_recover")
        awaitRecovery { it is ReportStatusRecoveryState.Confirmed }
        first.close()
        launch<AdministrationStatusChangeActivity>()
        awaitRecovery { it === ReportStatusRecoveryState.AwaitingRecovery }
        clickStatus("admin_status_recover")
        awaitRecovery { it is ReportStatusRecoveryState.Confirmed }
        assertEquals(2L, history(report.id).latestSequence)
        assertEquals(request.command.operationId, history(report.id).events.first().operationId)
        clickStatus("admin_status_acknowledge")
        awaitRecovery { it === ReportStatusRecoveryState.Ready }
        assertNull(runBlocking { journal.read() })
        assertEquals(2L, history(report.id).latestSequence)
    }

    @Test fun anUnwrittenRetainedRequestNeedsAnExplicitRetryOfTheExactBody() {
        registerAndAssign()
        val report = seedReport()
        val request = request(report)
        val journal = journal()
        runBlocking { journal.retain(request) }
        launch<AdministrationStatusChangeActivity>()
        awaitRecovery { it === ReportStatusRecoveryState.AwaitingRecovery }
        assertEquals(1L, history(report.id).latestSequence)
        clickStatus("admin_status_recover")
        awaitRecovery { it is ReportStatusRecoveryState.Pending && !it.requiresRecovery }
        assertEquals(1L, history(report.id).latestSequence)
        recreateStatus()
        awaitRecovery { it is ReportStatusRecoveryState.Pending && !it.requiresRecovery }
        assertEquals(request, runBlocking { journal.read() })
        clickStatus("admin_status_retry")
        awaitRecovery { it is ReportStatusRecoveryState.Confirmed }
        assertEquals(request.command.operationId, history(report.id).events.first().operationId)
        assertEquals(request.command.reason, history(report.id).events.first().reason)
        assertEquals(2L, history(report.id).latestSequence)
        assertEquals(request, runBlocking { journal.read() })
    }

    @Test fun aChangedRevisionRequiresReviewAgainInsteadOfSubmittingTheOldSnapshot() {
        registerAndAssign()
        val report = seedReport()
        launch<AdministrationStatusChangeActivity>(report.id)
        awaitRecovery { it === ReportStatusRecoveryState.AwaitingRecovery }
        clickStatus("admin_status_recover")
        awaitRecovery { it === ReportStatusRecoveryState.Ready }
        clickStatus("admin_status_load")
        awaitPreparation { it is AdministrationStatusChangeState.Editing }
        status("admin_status_reason").performTextInput("Motivo revisado antes de una edición concurrente")
        clickStatus("admin_status_review")
        awaitPreparation { it is AdministrationStatusChangeState.Reviewing }
        val concurrent = request(report, reason = "Cambio confirmado desde otra ventana local")
        val binding = app.openReportAdministration(workspace())
        runBlocking { binding.openStatusChangeAccess().change(concurrent) }
        clickStatus("admin_status_confirm")
        awaitPreparation { it is AdministrationStatusChangeState.Editing && it.snapshot.sequence == 2L && it.message != null }
        assertEquals(ReportStatusRecoveryState.Ready, currentRecovery())
        assertEquals(2L, history(report.id).latestSequence)
        assertEquals(concurrent.command.operationId, history(report.id).events.first().operationId)
        assertNull(runBlocking { journal().read() })
        ui.onNodeWithTag("admin_status_confirm").assertDoesNotExist()
    }

    @Test fun eachLocalStateAdvancesOnceAndAClosedReportHasNoFurtherChange() {
        registerAndAssign()
        val report = seedReport()
        launch<AdministrationStatusChangeActivity>(report.id)
        awaitRecovery { it === ReportStatusRecoveryState.AwaitingRecovery }
        clickStatus("admin_status_recover")
        awaitRecovery { it === ReportStatusRecoveryState.Ready }
        listOf(ReportStatus.IN_REVIEW, ReportStatus.ATTENDED, ReportStatus.CLOSED).forEachIndexed { index, target ->
            clickStatus("admin_status_load")
            awaitPreparation { it is AdministrationStatusChangeState.Editing && it.snapshot.nextStatus == target }
            status("admin_status_reason").performTextInput("Seguimiento local documentado en el paso ${index + 1}")
            clickStatus("admin_status_review")
            awaitPreparation { it is AdministrationStatusChangeState.Reviewing }
            clickStatus("admin_status_confirm")
            awaitRecovery { it is ReportStatusRecoveryState.Confirmed }
            assertEquals(target, history(report.id).currentStatus)
            assertEquals(index + 2L, history(report.id).latestSequence)
            assertEquals(account.id, history(report.id).events.first().actorAccountId)
            clickStatus("admin_status_acknowledge")
            awaitRecovery { it === ReportStatusRecoveryState.Ready }
        }
        clickStatus("admin_status_load")
        awaitPreparation { it is AdministrationStatusChangeState.Closed }
        status("admin_status_terminal").assertExists()
        ui.onNodeWithTag("admin_status_review").assertDoesNotExist()
        ui.onNodeWithTag("admin_status_confirm").assertDoesNotExist()
        assertEquals(listOf(4L, 3L, 2L, 1L), history(report.id).events.map { it.sequence })
        assertNull(runBlocking { journal().read() })
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun aDefinitivelyStaleRequestIsKeptUntilItsExplicitAcknowledgement() {
        registerAndAssign()
        val report = seedReport()
        val stale = request(report)
        val concurrent = request(report, "Cambio local confirmado antes del reintento pendiente")
        val binding = app.openReportAdministration(workspace())
        runBlocking { binding.openStatusChangeAccess().change(concurrent) }
        val journal = journal()
        runBlocking { journal.retain(stale) }
        launch<AdministrationStatusChangeActivity>(report.id)
        awaitRecovery { it === ReportStatusRecoveryState.AwaitingRecovery }
        clickStatus("admin_status_recover")
        awaitRecovery { it is ReportStatusRecoveryState.Pending && !it.requiresRecovery }
        clickStatus("admin_status_retry")
        awaitRecovery { it is ReportStatusRecoveryState.Rejected }
        assertEquals(stale, runBlocking { journal.read() })
        assertEquals(2L, history(report.id).latestSequence)
        clickStatus("admin_status_acknowledge_rejected")
        awaitRecovery { it === ReportStatusRecoveryState.Ready }
        assertNull(runBlocking { journal.read() })
        assertEquals(2L, history(report.id).latestSequence)
        clickStatus("admin_status_load")
        awaitPreparation { it is AdministrationStatusChangeState.Editing && it.snapshot.sequence == 2L }
        ui.onNodeWithTag("admin_status_confirm").assertDoesNotExist()
    }

    @Test fun corruptAuthorityBeforeConfirmationRemovesTheReviewWithoutWritingOrRepairing() {
        registerAndAssign()
        val report = seedReport()
        launch<AdministrationStatusChangeActivity>(report.id)
        awaitRecovery { it === ReportStatusRecoveryState.AwaitingRecovery }
        clickStatus("admin_status_recover")
        awaitRecovery { it === ReportStatusRecoveryState.Ready }
        clickStatus("admin_status_load")
        awaitPreparation { it is AdministrationStatusChangeState.Editing }
        val reason = "Motivo privado retirado al perder el permiso administrativo"
        status("admin_status_reason").performTextInput(reason)
        clickStatus("admin_status_review")
        awaitPreparation { it is AdministrationStatusChangeState.Reviewing }
        val assignment = File(fixture.contactsContext.filesDir, LocalAdministratorStore.FILE_NAME)
        assignment.writeText("unreadable authority fixture")
        clickStatus("admin_status_confirm")
        awaitRecovery { it === ReportStatusRecoveryState.Closed }
        ui.onNodeWithTag("admin_status_denied").assertExists()
        ui.onNodeWithTag("admin_status_review_summary").assertDoesNotExist()
        ui.onNodeWithTag("admin_status_confirm").assertDoesNotExist()
        ui.onAllNodesWithText(reason, substring = true).assertCountEquals(0)
        val pending = File(fixture.contactsContext.filesDir,
            ReportStatusChangeStore.fileName(ReportStatusChangeScope(account.id, account.id)))
        assertFalse(pending.exists())
        assertEquals("unreadable authority fixture", assignment.readText())
        assertEquals(1L, history(report.id).latestSequence)
        assertEquals(0, fixture.refreshCalls)
    }

    private fun registerAndAssign(assign: Boolean = true) = runBlocking {
        val repository = SqliteAccountRepository(app, "administrator_status_accounts_test_${UUID.randomUUID()}.db")
        accounts = repository
        app.accountTestRepository = repository
        val password = UUID.randomUUID().toString()
        account = repository.register("admin_local", password)
        assertEquals(account, repository.authenticate("admin_local", password))
        instrumentation.runOnMainSync { app.accountSession.signIn(account) }
        if (assign) {
            require(fixture.contactsContext.filesDir.name.startsWith("trusted_contacts_ui_"))
            app.openLocalAdministratorStore().assignFirst(account, UUID.randomUUID().toString(), Instant.now()) {}
        }
    }

    private fun workspace() = app.openReportWorkspace(app.accountSession.capture()).also(workspaces::add)
    private fun seedReport(): Report = runBlocking {
        fixture.publishFreshFix()
        workspace().repository.createOnce(UUID.randomUUID().toString(), NewReport(IncidentType.RISK, Priority.MEDIUM,
            "Reporte local para comprobar el recorrido administrativo real", Instant.now().minusSeconds(60),
            ReportLocation("Referencia del recorrido de prueba", 19.4326, -99.1332)))
    }
    private fun history(id: String) = runBlocking { requireNotNull(workspace().historyRepository.readHistory(ReportHistoryQuery(id))) }
    private fun request(report: Report, reason: String = "Revisión local confirmada por la cuenta asignada") =
        ReportStatusChangeRequest(ReportStatusChangeScope(account.id, account.id), ReportStatusChangeCommand(
            UUID.randomUUID().toString(), report.id, 1, ReportStatus.REPORTED, ReportStatus.IN_REVIEW, reason), Instant.now())
    private fun journal(): ReportStatusChangeJournal {
        val binding = app.openReportAdministration(workspace())
        return app.openReportStatusChangeJournal(binding, binding.openStatusChangeAccess())
    }

    private inline fun <reified T : Activity> launch(folio: String? = null): ActivityScenario<T> = ActivityScenario.launch<T>(
        ReportWorkspaceActivityBinding.stamp(Intent(app, T::class.java).apply {
            folio?.let { putExtra(AdministrationStatusChangeActivity.EXTRA_REPORT_ID, it) }
        }, app.accountSession.capture()),
    ).also(scenarios::add)

    private fun status(tag: String): SemanticsNodeInteraction {
        ui.onNodeWithTag("admin_status_screen").performScrollToNode(hasTestTag(tag))
        return ui.onNodeWithTag(tag)
    }
    private fun clickStatus(tag: String) { status(tag).performClick() }
    private fun clickConsult(tag: String) {
        ui.onNodeWithTag("admin_consult_screen").performScrollToNode(hasTestTag(tag))
        ui.onNodeWithTag(tag).performClick()
    }
    private fun currentRecovery(): ReportStatusRecoveryState {
        var value: ReportStatusRecoveryState? = null
        instrumentation.runOnMainSync { resumedStatus()?.let { value = it.recovery.state.value } }
        return requireNotNull(value)
    }
    private fun resumedStatus() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        .filterIsInstance<AdministrationStatusChangeActivity>().singleOrNull()
    private fun awaitRecovery(condition: (ReportStatusRecoveryState) -> Boolean) = ui.waitUntil(20_000) {
        var accepted = false
        instrumentation.runOnMainSync { resumedStatus()?.let { accepted = condition(it.recovery.state.value) } }
        accepted
    }
    private fun awaitPreparation(condition: (AdministrationStatusChangeState) -> Boolean) = ui.waitUntil(20_000) {
        var accepted = false
        instrumentation.runOnMainSync { resumedStatus()?.let { accepted = condition(it.preparation.state.value) } }
        accepted
    }
    private fun awaitConsult(condition: (AdministrationConsultationState) -> Boolean) = ui.waitUntil(20_000) {
        var accepted = false
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<AdministrationConsultationActivity>().forEach { activity ->
                    accepted = condition(androidx.lifecycle.ViewModelProvider(activity)[AdministrationConsultationViewModel::class.java].state.value)
                }
        }
        accepted
    }
    private fun recreateStatus() {
        var previous: AdministrationStatusChangeActivity? = null
        instrumentation.runOnMainSync { previous = requireNotNull(resumedStatus()).also { it.recreate() } }
        ui.waitUntil(20_000) {
            var changed = false
            instrumentation.runOnMainSync { changed = resumedStatus()?.let { it !== previous } == true }
            changed
        }
    }
}
