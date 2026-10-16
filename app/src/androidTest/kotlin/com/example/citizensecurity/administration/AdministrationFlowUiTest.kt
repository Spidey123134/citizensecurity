package com.example.citizensecurity.administration

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivityBinding
import com.example.citizensecurity.data.LocalAdministratorStore
import com.example.citizensecurity.data.ReportStorageNames
import com.example.citizensecurity.data.SqliteAccountRepository
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.File
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.regex.Pattern
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real private stores and native Android credential UI; never runs PIN changes on a phone. */
class AdministrationFlowUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private val fixture get() = fixtureRule.fixture
    private val device get() = UiDevice.getInstance(instrumentation)
    private val keyguard get() = app.getSystemService(KeyguardManager::class.java)
    private val scenarios = mutableListOf<ActivityScenario<*>>()
    private val workspaces = mutableListOf<ReportWorkspace>()
    private var accounts: SqliteAccountRepository? = null
    private val assignmentFile get() = File(fixture.contactsContext.filesDir, LocalAdministratorStore.FILE_NAME)

    @After fun closeOnlyOwnedActivitiesAndSessions() {
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

    @Test fun nativePinConfirmationRequiresASecondGestureAndSurvivesRecreationBeforeConsulting() = withOwnedPin {
        val account = registeredAccount()
        val report = seedReport("Reporte de aceptación de la consulta administrativa")
        val setup = launch<AdministrationSetupActivity>()
        awaitTag("admin_setup_confirm_owner")
        click("admin_setup_confirm_owner")
        completeNativeCredential(it)
        awaitTag("admin_setup_confirmed")
        assertNull(app.openLocalAdministratorStore().read())
        assertFalse(assignmentFile.exists())
        ui.onNodeWithTag("admin_setup_assigned").assertDoesNotExist()
        setup.recreate()
        awaitTag("admin_setup_confirmed")
        assertNull(app.openLocalAdministratorStore().read())
        click("admin_setup_activate")
        awaitTag("admin_setup_assigned")
        val assignment = requireNotNull(app.openLocalAdministratorStore().read())
        assertEquals(account.id, assignment.accountId)
        assertEquals("admin_local", assignment.username)
        click("admin_setup_consult")
        awaitConsult { it is AdministrationConsultationState.ReadyPage }
        click("admin_consult_open_${report.id}")
        awaitConsult { it is AdministrationConsultationState.ReadyReport }
        scroll("admin_consult_detail")
        ui.onNodeWithText(report.description).assertIsDisplayed()
        scroll("admin_consult_history_event_1")
        ui.onNodeWithTag("admin_consult_history_event_1").assertIsDisplayed()
        assertEquals(assignment, app.openLocalAdministratorStore().read())
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun cancellingTheRealNativeCredentialNeverCreatesAnAssignment() = withOwnedPin {
        registeredAccount()
        launch<AdministrationSetupActivity>()
        awaitTag("admin_setup_confirm_owner")
        click("admin_setup_confirm_owner")
        awaitNativeCredential()
        assertNull(app.openLocalAdministratorStore().read())
        device.pressBack()
        awaitTag("admin_setup_confirm_owner")
        assertNull(app.openLocalAdministratorStore().read())
        assertFalse(assignmentFile.exists())
        ui.onNodeWithTag("admin_setup_activate").assertDoesNotExist()
        ui.onNodeWithTag("admin_setup_consult").assertDoesNotExist()
    }

    @Test fun directConsultationWithoutAGrantDeniesBeforeCreatingTheAccountDatabase() {
        val account = registeredAccount()
        val database = app.getDatabasePath(ReportStorageNames.fromAccountId(account.id).databaseName)
        assertFalse(database.exists())
        launch<AdministrationConsultationActivity>()
        awaitConsult { it === AdministrationConsultationState.Denied }
        awaitTag("admin_consult_denied")
        assertFalse(database.exists())
        assertFalse(assignmentFile.exists())
        ui.onNodeWithTag("admin_consult_folio").assertDoesNotExist()
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun aGrantDoesNotTransferToTheSameUsernameWithAnotherUuidOrAcademicAccess() {
        val original = registeredAccount()
        val assignment = assignFixture(original)
        val bytes = assignmentFile.readBytes()
        val other = LocalAccount(UUID.randomUUID().toString(), "admin_local")
        instrumentation.runOnMainSync { app.accountSession.signIn(other) }
        val database = app.getDatabasePath(ReportStorageNames.fromAccountId(other.id).databaseName)
        launch<AdministrationConsultationActivity>()
        awaitConsult { it === AdministrationConsultationState.Denied }
        awaitTag("admin_consult_denied")
        assertFalse(database.exists())
        scenarios.last().close()
        instrumentation.runOnMainSync { app.accountSession.signOut() }
        launch<AdministrationSetupActivity>()
        awaitTag("admin_setup_blocked")
        ui.onNodeWithTag("admin_setup_confirm_owner").assertDoesNotExist()
        assertEquals(assignment, app.openLocalAdministratorStore().read())
        assertArrayEquals(bytes, assignmentFile.readBytes())
    }

    @Test fun realFiltersFolioAndRotationRemainReadOnlyAndCorruptAuthorityClearsPrivateData() {
        val account = registeredAccount()
        assignFixture(account)
        val report = seedReport("Descripción privada de la consulta que debe retirarse al fallar el permiso")
        seedReport("Otro tipo para comprobar el filtro", IncidentType.THEFT)
        val database = app.getDatabasePath(ReportStorageNames.fromAccountId(account.id).databaseName)
        val before = database.readBytes()
        val consultation = launch<AdministrationConsultationActivity>()
        awaitConsult { it is AdministrationConsultationState.ReadyPage && it.page.reports.size == 2 }
        click("admin_consult_type")
        ui.onNodeWithTag("admin_consult_type_RISK").performClick()
        awaitConsult { it is AdministrationConsultationState.ReadyPage && it.page.reports.map(Report::id) == listOf(report.id) }
        scroll("admin_consult_folio")
        ui.onNodeWithTag("admin_consult_folio").performTextInput(report.id)
        click("admin_consult_find")
        awaitConsult { it is AdministrationConsultationState.ReadyReport }
        consultation.recreate()
        awaitConsult { it is AdministrationConsultationState.ReadyReport && it.report.id == report.id }
        scroll("admin_consult_detail")
        ui.onNodeWithText(report.description).assertIsDisplayed()
        scroll("admin_consult_history_event_1")
        ui.onNodeWithTag("admin_consult_history_event_1").assertIsDisplayed()
        assignmentFile.writeText("corrupt authority fixture")
        click("admin_consult_history_first")
        awaitConsult { it === AdministrationConsultationState.Denied || it is AdministrationConsultationState.Error }
        ui.onNodeWithText(report.description).assertDoesNotExist()
        ui.onNodeWithTag("admin_consult_detail").assertDoesNotExist()
        assertEquals("corrupt authority fixture", assignmentFile.readText())
        assertArrayEquals(before, database.readBytes())
        assertEquals(0, fixture.refreshCalls)
    }

    private fun registeredAccount(): LocalAccount = runBlocking {
        val repository = SqliteAccountRepository(app, "administrator_accounts_test_${UUID.randomUUID()}.db")
        accounts = repository
        app.accountTestRepository = repository
        val password = UUID.randomUUID().toString()
        val account = repository.register("admin_local", password)
        assertEquals(account, repository.authenticate("admin_local", password))
        instrumentation.runOnMainSync { app.accountSession.signIn(account) }
        account
    }

    /** Test-only preconditions for negative/query cases, exclusively in the fixture directory. */
    private fun assignFixture(account: LocalAccount): LocalAdministratorAssignment {
        require(assignmentFile.canonicalFile.parentFile == fixture.contactsContext.filesDir.canonicalFile)
        require(fixture.contactsContext.filesDir.name.startsWith("trusted_contacts_ui_"))
        return app.openLocalAdministratorStore().assignFirst(account, UUID.randomUUID().toString(), Instant.now()) {}
    }

    private fun seedReport(description: String, type: IncidentType = IncidentType.RISK): Report = runBlocking {
        fixture.publishFreshFix()
        val workspace = app.openReportWorkspace(app.accountSession.capture()).also(workspaces::add)
        workspace.repository.createOnce(UUID.randomUUID().toString(), NewReport(type, Priority.MEDIUM,
            description, Instant.now().minusSeconds(60), ReportLocation("Referencia de la prueba local", 19.4326, -99.1332)))
    }

    private inline fun <reified T : Activity> launch(): ActivityScenario<T> = ActivityScenario.launch<T>(
        ReportWorkspaceActivityBinding.stamp(Intent(app, T::class.java), app.accountSession.capture()),
    ).also(scenarios::add)

    private fun awaitConsult(condition: (AdministrationConsultationState) -> Boolean) {
        ui.waitUntil(20_000) {
            var accepted = false
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<AdministrationConsultationActivity>().forEach { activity ->
                        val model = ViewModelProvider(activity)[AdministrationConsultationViewModel::class.java]
                        accepted = condition(model.state.value)
                    }
            }
            accepted
        }
    }

    private fun awaitTag(tag: String) = ui.waitUntil(15_000) {
        ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }

    private fun scroll(tag: String) {
        if (tag.startsWith("admin_consult_")) {
            ui.onNodeWithTag("admin_consult_screen").performScrollToNode(hasTestTag(tag))
        } else ui.onNodeWithTag(tag).performScrollTo()
    }

    private fun click(tag: String) { scroll(tag); ui.onNodeWithTag(tag).performClick() }

    private fun awaitNativeCredential() {
        credentialDiagnostics("awaiting-input", capture = false)
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (nativeCredentialPackage() && (nativeCredentialEntry() != null || nativePinKey('1') != null)) return
            SystemClock.sleep(100)
        }
        val diagnostic = credentialDiagnostics("missing-native-input", capture = true)
        fail("Android did not expose its native credential input; $diagnostic")
    }

    private fun completeNativeCredential(pin: String) {
        awaitNativeCredential()
        // Older Settings uses EditText; newer SystemUI can expose PasswordTextView plus a keypad.
        val editable = device.findObject(By.clazz("android.widget.EditText"))
        if (editable != null) editable.text = pin else pin.forEach { digit ->
            assertTrue("The native credential panel closed before the PIN was complete", nativeCredentialPackage())
            val key = nativePinKey(digit)
            if (key == null) {
                val diagnostic = credentialDiagnostics("missing-pin-key", capture = false)
                fail("Android's native PIN keypad is missing; $diagnostic")
            }
            key!!.click()
        }
        if (nativeCredentialPackage()) {
            val submit = device.findObject(By.res(Pattern.compile(".*:id/(key_enter|confirm_button|next_button|ok_button|button1)")))
            if (submit != null) submit.click() else device.pressEnter()
        }
        instrumentation.waitForIdleSync()
    }

    private fun nativeCredentialPackage() = device.currentPackageName in setOf("com.android.settings", "com.android.systemui")

    private fun nativeCredentialEntry() = device.findObject(By.clazz("android.widget.EditText"))
        ?: device.findObject(By.res(Pattern.compile(".*:id/(pin_entry|password_entry|pinEntry)")))

    private fun nativePinKey(digit: Char) = device.findObject(By.res(Pattern.compile(".*:id/key$digit")))
        ?: device.findObject(By.desc(digit.toString()))
        ?: device.findObject(By.text(digit.toString()))

    /** Metadata contains no account, report, operation, Intent extras or credential text. */
    @Suppress("DEPRECATION")
    private fun credentialDiagnostics(label: String, capture: Boolean): String {
        val intent = keyguard.createConfirmDeviceCredentialIntent("Administration fixture", "Owned emulator acceptance")
        val handler = intent?.let { app.packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo }
        var setupState = "not-found"
        var setupMessage = ""
        instrumentation.runOnMainSync {
            listOf(Stage.RESUMED, Stage.PAUSED, Stage.STARTED, Stage.STOPPED).forEach { stage ->
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)
                    .filterIsInstance<AdministrationSetupActivity>().forEach { activity ->
                        val state = activity.model.state.value
                        setupState = state.javaClass.simpleName
                        setupMessage = (state as? AdministrationSetupState.Available)?.message.orEmpty()
                    }
            }
        }
        val info = "label=$label currentPackage=${device.currentPackageName} secure=${keyguard.isDeviceSecure}" +
            " locked=${keyguard.isKeyguardLocked} action=${intent?.action} package=${intent?.`package`}" +
            " handler=${handler?.packageName}/${handler?.name} exported=${handler?.exported}" +
            " enabled=${handler?.enabled} systemFlags=${handler?.applicationInfo?.flags}" +
            " secureFeature=${app.packageManager.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN)}" +
            " setup=$setupState message=$setupMessage"
        Log.i("Admin40Credential", info)
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "\nADMIN40_NATIVE: $info\n") })
        if (capture) {
            // This is called only before any PIN digit is entered. FLAG_SECURE may blank the screenshot.
            val directory = File(checkNotNull(app.getExternalFilesDir(null)), "admin40_credential_fixture")
            check(directory.isDirectory || directory.mkdir())
            val name = "${label}_${UUID.randomUUID()}"
            File(directory, "$name.txt").writeText(info)
            device.dumpWindowHierarchy(File(directory, "$name.xml"))
            device.takeScreenshot(File(directory, "$name.png"))
            Log.i("Admin40Credential", "diagnostics=${directory.absolutePath}/$name")
        }
        return info
    }

    private fun withOwnedPin(test: (String) -> Unit) {
        val owned = InstrumentationRegistry.getArguments().getString("ownedReadOnlyEmulator")
        assertEquals("The executor must explicitly identify the disposable emulator", "admin40", owned)
        assertTrue("Refusing to change a physical device credential", Build.HARDWARE in setOf("ranchu", "goldfish"))
        assertFalse("An existing device credential must never be changed", keyguard.isDeviceSecure)
        val disabled = device.executeShellCommand("locksettings get-disabled").trim()
        assertTrue("Cannot capture the emulator's previous lock setting", disabled in listOf("true", "false"))
        val pin = (100_000 + SecureRandom().nextInt(900_000)).toString()
        var created = false
        try {
            val result = device.executeShellCommand("locksettings set-pin $pin")
            created = result.contains("Pin set", ignoreCase = true) ||
                result.contains("successfully", ignoreCase = true) || keyguard.isDeviceSecure
            assertTrue("Could not create the owned emulator fixture PIN", created)
            waitSecure(true)
            test(pin)
        } finally {
            if (created) {
                val result = device.executeShellCommand("locksettings clear --old $pin")
                assertTrue("Could not retire the PIN owned by this test", result.contains("cleared", ignoreCase = true))
                waitSecure(false)
                device.executeShellCommand("locksettings set-disabled $disabled")
                assertEquals(disabled, device.executeShellCommand("locksettings get-disabled").trim())
            }
        }
    }

    private fun waitSecure(expected: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (keyguard.isDeviceSecure != expected && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertEquals(expected, keyguard.isDeviceSecure)
    }
}
