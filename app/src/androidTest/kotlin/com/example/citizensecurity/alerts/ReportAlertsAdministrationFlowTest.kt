package com.example.citizensecurity.alerts

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivityBinding
import com.example.citizensecurity.administration.AdministrationStatusChangeActivity
import com.example.citizensecurity.administration.AdministrationStatusChangeState
import com.example.citizensecurity.data.SqliteAccountRepository
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.report.ReportStatusRecoveryState
import com.example.citizensecurity.report.ReportActivityState
import com.example.citizensecurity.report.ReportActivityViewModel
import com.example.citizensecurity.reports.ReportActivityActivity
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Run first on the owned disposable API 37 emulator with POST_NOTIFICATIONS denied. The second
 * case accepts Android's real permission dialog. Never revoke that permission from instrumentation.
 * Every notified operation comes from the production review/confirm UI, real role, SQLite and journal.
 * Only the already-tested administrator assignment is a fixture precondition; no PIN is changed here.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReportAlertsAdministrationFlowTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private val fixture get() = fixtureRule.fixture
    private val device get() = UiDevice.getInstance(instrumentation)
    private val manager get() = app.getSystemService(NotificationManager::class.java)
    private val publisher get() = AndroidReportAlertsPublisher(app)
    private val scenarios = mutableListOf<ActivityScenario<*>>()
    private val workspaces = mutableListOf<ReportWorkspace>()
    private var accounts: SqliteAccountRepository? = null
    private lateinit var account: LocalAccount

    @Before fun useOnlyTheOwnedEmulatorAndRetireItsSummary() {
        assertTrue("This suite exercises Android's real runtime notification permission", Build.VERSION.SDK_INT >= 33)
        assertTrue("Run only on the disposable emulator", Build.HARDWARE in listOf("ranchu", "goldfish"))
        instrumentation.runOnMainSync { app.accountSession.signOut() }
        publisher.cancel()
    }

    @After fun closeOnlyOwnedFixturesAndWindows() {
        scenarios.asReversed().forEach { it.close() }
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filter { it.packageName == app.packageName }.forEach(Activity::finish)
            app.accountSession.signOut()
        }
        publisher.cancel()
        workspaces.forEach(ReportWorkspace::close)
        app.accountTestRepository = null
        accounts?.close()
        instrumentation.waitForIdleSync()
    }

    @Test fun aOpeningFromARealConfirmedChangeDoesNotEnableAskOrNotify() {
        assertEquals("Run this class before granting POST_NOTIFICATIONS", PackageManager.PERMISSION_DENIED,
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        registerAndAssign()
        val report = seedReport()
        val confirmed = changeThroughTheRealScreen(report)
        val retained = readJournal()
        assertEquals(confirmed.request, retained)
        assertEquals(2L, history(report.id).latestSequence)
        assertFalse(preferenceFile().exists())
        clickStatus("admin_status_activity")
        awaitActivityOperation(confirmed.request.command.operationId)
        val eventTag = "activity_event_${confirmed.request.command.operationId}"
        ui.onNodeWithTag("report_activity").performScrollToNode(hasTestTag(eventTag))
        ui.onNodeWithTag(eventTag)
            .assert(hasAnyDescendant(hasText(report.id, substring = true)))
            .assert(hasAnyDescendant(hasText("Reportado → En revisión")))
            .assert(hasAnyDescendant(hasText(confirmed.request.command.reason)))
        assertFalse(preferenceFile().exists())
        assertTrue(ownActive().isEmpty())
        assertEquals(retained, readJournal())
        assertEquals(2L, history(report.id).latestSequence)
        ui.onNodeWithTag("report_activity").performScrollToNode(hasTestTag("activity_close"))
        ui.onNodeWithTag("activity_close").performClick()
        awaitRecovery { it is ReportStatusRecoveryState.Confirmed }
        assertEquals(confirmed.receipt, (currentRecovery() as ReportStatusRecoveryState.Confirmed).receipt)
        assertEquals(retained, readJournal())
        openAlertsFromConfirmedReceipt()
        awaitPreference(false)
        assertFalse(device.hasObject(permissionButton()))
        assertEquals(ReportAlertsAvailability.RUNTIME_PERMISSION_DENIED, publisher.availability())
        ui.onNodeWithTag("alerts_review").assertIsNotEnabled()
        assertFalse(preferenceFile().exists())
        assertNull(readPreferences().revision)
        assertTrue(readPreferences().presented.isEmpty())
        assertTrue(ownActive().isEmpty())
        assertEquals(retained, readJournal())
        assertEquals(2L, history(report.id).latestSequence)
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun bExplicitPermissionAndReviewNotifyTheRealOperationOnlyOnceThenDisableKeepsIt() {
        assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        registerAndAssign()
        val report = seedReport()
        val confirmed = changeThroughTheRealScreen(report)
        val operation = confirmed.request.command.operationId
        assertEquals(operation, history(report.id).events.first().operationId)
        openAlertsFromConfirmedReceipt()
        awaitPreference(false)
        clickAlert("alerts_enable")
        chooseAndroidAllow()
        awaitPreference(true)
        assertEquals(PackageManager.PERMISSION_GRANTED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        assertEquals(ReportAlertsAvailability.ALLOWED, publisher.availability())
        assertTrue(readPreferences().presented.isEmpty())
        assertTrue(ownActive().isEmpty())
        clickAlert("alerts_review")
        awaitMessage("Android aceptó un aviso general")
        awaitNative { ownActive().size == 1 }
        val accepted = readPreferences()
        assertEquals(setOf(operation), accepted.presented)
        assertFalse(accepted.presented.contains(report.id))
        assertGenericSummary(report, confirmed)
        assertEquals(confirmed.request, readJournal())
        assertEquals(2L, history(report.id).latestSequence)

        clickAlert("alerts_review")
        awaitMessage("No hay cambios nuevos")
        assertEquals(accepted, readPreferences())
        assertEquals(1, ownActive().size)
        recreateAlerts()
        awaitPreference(true)
        assertEquals(accepted, readPreferences())
        assertEquals(1, ownActive().size)
        clickAlert("alerts_close")
        awaitRecovery { it is ReportStatusRecoveryState.Confirmed }
        assertEquals(confirmed.request, readJournal())
        assertEquals(confirmed.receipt, (currentRecovery() as ReportStatusRecoveryState.Confirmed).receipt)
        openAlertsFromConfirmedReceipt()
        awaitPreference(true)
        assertEquals(accepted, readPreferences())
        assertEquals(1, ownActive().size)
        clickAlert("alerts_disable")
        awaitPreference(false)
        awaitNative { ownActive().isEmpty() }
        val disabled = readPreferences()
        assertFalse(disabled.enabled)
        assertEquals(accepted.presented, disabled.presented)
        assertNotEquals(accepted.revision, disabled.revision)
        assertEquals(confirmed.request, readJournal())
        assertEquals(2L, history(report.id).latestSequence)
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun cAnotherRealAccountHasNeitherTheRoleNorTheEventsOrPreferencesOfTheAdministrator() {
        assertEquals(PackageManager.PERMISSION_GRANTED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        registerAndAssign()
        val first = account
        val report = seedReport()
        val confirmed = changeThroughTheRealScreen(report)
        openAlertsFromConfirmedReceipt()
        awaitPreference(false)
        clickAlert("alerts_enable")
        awaitPreference(true)
        clickAlert("alerts_review")
        awaitMessage("Android aceptó un aviso general")
        awaitNative { ownActive().size == 1 }
        val firstPreferences = readPreferences()
        val firstBytes = preferenceFile().readBytes()
        assertEquals(setOf(confirmed.request.command.operationId), firstPreferences.presented)

        val second = register("avisos_segunda")
        instrumentation.runOnMainSync { app.accountSession.signIn(second) }
        account = second
        awaitNative { ownActive().isEmpty() }
        launch<AdministrationStatusChangeActivity>(report.id)
        awaitRecovery { it === ReportStatusRecoveryState.Closed }
        ui.onNodeWithTag("admin_status_denied").assertExists()
        val secondActivity = runBlocking { workspace().activityRepository.readActivity(ReportActivityQuery()) }
        assertTrue(secondActivity.entries.isEmpty())
        launch<ReportAlertsActivity>()
        awaitPreference(false)
        assertNull(readPreferences().revision)
        assertTrue(readPreferences().presented.isEmpty())
        assertFalse(preferenceFile().exists())
        clickAlert("alerts_enable")
        awaitPreference(true)
        clickAlert("alerts_review")
        awaitMessage("No hay cambios nuevos")
        assertTrue(readPreferences().presented.isEmpty())
        assertTrue(ownActive().isEmpty())
        assertArrayEquals(firstBytes, preferenceFile(first).readBytes())
        assertNotEquals(fixture.alertsFileNameFor(first.id), fixture.alertsFileNameFor(second.id))

        instrumentation.runOnMainSync { app.accountSession.signIn(first) }
        account = first
        launch<ReportAlertsActivity>()
        awaitPreference(true)
        assertEquals(firstPreferences, readPreferences())
        assertTrue(ownActive().isEmpty())
        assertEquals(confirmed.request, readJournal())
        assertEquals(2L, history(report.id).latestSequence)
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun dUnreadablePreferencesKeepTheirBytesAndRestoredFixtureNeedsAnotherReviewGesture() {
        assertEquals(PackageManager.PERMISSION_GRANTED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        registerAndAssign()
        val report = seedReport()
        val confirmed = changeThroughTheRealScreen(report)
        // Prepare only this fixture's opt-in file, then make it unreadable before opening the route.
        val before = runBlocking {
            val repository = app.openReportAlertsRepository(app.accountSession.capture()) {}
            repository.setEnabled(true, repository.read().revision)
        }
        val file = preferenceFile()
        val validBytes = file.readBytes()
        val corruptBytes = "{\"version\":1,\"enabled\":true,\"presented\":[broken]}".toByteArray(Charsets.UTF_8)
        file.writeBytes(corruptBytes)
        openAlertsFromConfirmedReceipt()
        awaitTag("alerts_error")
        assertArrayEquals(corruptBytes, file.readBytes())
        ui.onNodeWithTag("alerts_enable").assertDoesNotExist()
        ui.onNodeWithTag("alerts_review").assertDoesNotExist()
        clickAlert("alerts_reload")
        awaitTag("alerts_error")
        assertArrayEquals(corruptBytes, file.readBytes())
        assertTrue(ownActive().isEmpty())
        assertEquals(confirmed.request, readJournal())
        assertEquals(2L, history(report.id).latestSequence)

        // Harness restoration is explicit; the product never repairs or replaces corrupt preferences.
        file.writeBytes(validBytes)
        clickAlert("alerts_reload")
        awaitPreference(true)
        assertEquals(before, readPreferences())
        assertTrue(ownActive().isEmpty())
        clickAlert("alerts_review")
        awaitMessage("Android aceptó un aviso general")
        awaitNative { ownActive().size == 1 }
        assertEquals(setOf(confirmed.request.command.operationId), readPreferences().presented)
        assertGenericSummary(report, confirmed)
        assertEquals(confirmed.request, readJournal())
        assertEquals(2L, history(report.id).latestSequence)
        assertEquals(0, fixture.refreshCalls)
    }

    private fun registerAndAssign() {
        accounts = SqliteAccountRepository(app, "administrator_alerts_accounts_test_${UUID.randomUUID()}.db")
        app.accountTestRepository = requireNotNull(accounts)
        account = register("admin_local")
        instrumentation.runOnMainSync { app.accountSession.signIn(account) }
        require(fixture.contactsContext.filesDir.name.startsWith("trusted_contacts_ui_"))
        app.openLocalAdministratorStore().assignFirst(account, UUID.randomUUID().toString(), Instant.now()) {}
    }

    private fun register(username: String): LocalAccount = runBlocking {
        val password = UUID.randomUUID().toString()
        requireNotNull(accounts).register(username, password).also {
            assertEquals(it, requireNotNull(accounts).authenticate(username, password))
        }
    }

    private fun workspace() = app.openReportWorkspace(app.accountSession.capture()).also(workspaces::add)

    private fun seedReport(): Report = runBlocking {
        fixture.publishFreshFix()
        workspace().repository.createOnce(UUID.randomUUID().toString(), NewReport(IncidentType.RISK, Priority.MEDIUM,
            "Descripción privada del reporte y su seguimiento administrativo real", Instant.now().minusSeconds(60),
            ReportLocation("Referencia privada del ensayo de avisos", 19.4326, -99.1332)))
    }

    private fun changeThroughTheRealScreen(report: Report): ReportStatusRecoveryState.Confirmed {
        launch<AdministrationStatusChangeActivity>(report.id)
        awaitRecovery { it === ReportStatusRecoveryState.AwaitingRecovery }
        clickStatus("admin_status_recover")
        awaitRecovery { it === ReportStatusRecoveryState.Ready }
        clickStatus("admin_status_load")
        awaitPreparation { it is AdministrationStatusChangeState.Editing }
        status("admin_status_reason").performTextInput("Motivo privado de revisión local para el aviso de cambios")
        clickStatus("admin_status_review")
        awaitPreparation { it is AdministrationStatusChangeState.Reviewing }
        assertEquals(1L, history(report.id).latestSequence)
        assertNull(readJournal())
        clickStatus("admin_status_confirm")
        awaitRecovery { it is ReportStatusRecoveryState.Confirmed }
        val confirmed = currentRecovery() as ReportStatusRecoveryState.Confirmed
        val event = history(report.id).events.first()
        assertEquals(2L, event.sequence)
        assertEquals(ReportStatus.IN_REVIEW, event.toStatus)
        assertEquals(account.id, event.actorAccountId)
        assertEquals(confirmed.request.command.operationId, event.operationId)
        assertEquals(confirmed.request.command.reason, event.reason)
        assertEquals(confirmed.request, readJournal())
        return confirmed
    }

    private fun openAlertsFromConfirmedReceipt() {
        status("admin_status_confirmed").assertExists()
        clickStatus("admin_status_alerts")
        ui.waitUntil(20_000) {
            var resumed = false
            instrumentation.runOnMainSync { resumed = resumedAlerts() != null }
            resumed
        }
    }

    private fun history(id: String) = runBlocking { requireNotNull(workspace().historyRepository.readHistory(ReportHistoryQuery(id))) }
    private fun readJournal(): ReportStatusChangeRequest? = runBlocking {
        val binding = app.openReportAdministration(workspace())
        app.openReportStatusChangeJournal(binding, binding.openStatusChangeAccess()).read()
    }
    private fun readPreferences(): ReportAlertsSnapshot = runBlocking {
        app.openReportAlertsRepository(app.accountSession.capture()) {}.read()
    }
    private fun preferenceFile(owner: LocalAccount = account) = File(app.filesDir, fixture.alertsFileNameFor(owner.id)).also {
        assertEquals(app.filesDir.canonicalFile, it.canonicalFile.parentFile)
    }

    private fun assertGenericSummary(report: Report, confirmed: ReportStatusRecoveryState.Confirmed) {
        val notification = ownActive().single().notification
        assertEquals(AndroidReportAlertsPublisher.CHANNEL_ID, notification.channelId)
        assertEquals(AndroidReportAlertsPublisher.TITLE, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(AndroidReportAlertsPublisher.TEXT, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(Notification.VISIBILITY_SECRET, notification.visibility)
        assertTrue(notification.contentIntent.isImmutable)
        assertTrue(notification.actions.isNullOrEmpty())
        listOf(account.id, report.id, confirmed.request.command.operationId, confirmed.request.command.reason,
            report.description, "Referencia privada del ensayo de avisos", "19.4326", "-99.1332").forEach {
            assertFalse("Android's generic summary must not contain report or account data",
                notification.extras.toString().contains(it))
        }
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
    private fun clickAlert(tag: String) {
        ui.onNodeWithTag("report_alerts").performScrollToNode(hasTestTag(tag))
        ui.onNodeWithTag(tag).performClick()
    }
    private fun resumedStatus() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        .filterIsInstance<AdministrationStatusChangeActivity>().singleOrNull()
    private fun resumedAlerts() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
        .filterIsInstance<ReportAlertsActivity>().singleOrNull()
    private fun currentRecovery(): ReportStatusRecoveryState {
        var state: ReportStatusRecoveryState? = null
        instrumentation.runOnMainSync { state = requireNotNull(resumedStatus()).recovery.state.value }
        return requireNotNull(state)
    }
    private fun awaitRecovery(predicate: (ReportStatusRecoveryState) -> Boolean) = ui.waitUntil(20_000) {
        var accepted = false
        instrumentation.runOnMainSync { resumedStatus()?.let { accepted = predicate(it.recovery.state.value) } }
        accepted
    }
    private fun awaitPreparation(predicate: (AdministrationStatusChangeState) -> Boolean) = ui.waitUntil(20_000) {
        var accepted = false
        instrumentation.runOnMainSync { resumedStatus()?.let { accepted = predicate(it.preparation.state.value) } }
        accepted
    }
    private fun awaitActivityOperation(operationId: String) = ui.waitUntil(20_000) {
        var accepted = false
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ReportActivityActivity>().singleOrNull()?.let { activity ->
                    val state = ViewModelProvider(activity)[ReportActivityViewModel::class.java].state.value
                    accepted = state is ReportActivityState.Ready && state.page.entries.any { it.event.operationId == operationId }
                }
        }
        accepted
    }
    private fun awaitPreference(enabled: Boolean) {
        val text = if (enabled) "Preferencia activada" else "Avisos desactivados"
        ui.waitUntil(20_000) {
            ui.onAllNodes(hasTestTag("alerts_preference") and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun awaitMessage(text: String) = ui.waitUntil(20_000) {
        ui.onAllNodes(hasTestTag("alerts_message") and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }
    private fun awaitTag(tag: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun recreateAlerts() {
        var previous: ReportAlertsActivity? = null
        instrumentation.runOnMainSync { previous = requireNotNull(resumedAlerts()).also { it.recreate() } }
        ui.waitUntil(20_000) {
            var changed = false
            instrumentation.runOnMainSync { changed = resumedAlerts()?.let { it !== previous } == true }
            changed
        }
    }
    private fun permissionButton() = By.res(Pattern.compile(".*:id/permission_allow_button"))
    private fun chooseAndroidAllow() {
        val button = device.wait(Until.findObject(permissionButton()), 10_000)
        assertNotNull("Android must display the actual notification permission dialog", button)
        button!!.click()
        assertTrue(device.wait(Until.gone(permissionButton()), 10_000))
        instrumentation.waitForIdleSync()
    }
    private fun ownActive() = manager.activeNotifications.filter {
        it.tag == AndroidReportAlertsPublisher.SUMMARY_TAG && it.id == AndroidReportAlertsPublisher.SUMMARY_ID
    }
    private fun awaitNative(predicate: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (!predicate()) {
            if (SystemClock.uptimeMillis() >= until) fail("The expected native state did not arrive")
            SystemClock.sleep(20)
        }
    }
}
