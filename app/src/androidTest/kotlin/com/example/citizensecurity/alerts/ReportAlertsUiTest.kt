package com.example.citizensecurity.alerts

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.domain.*
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
 * Run this class first on the disposable API 37 emulator, with POST_NOTIFICATIONS initially denied.
 * The first two cases exercise Android's real Deny/Allow dialog in order. Never revoke an already
 * granted runtime permission in instrumentation: Android kills the instrumented app on revocation.
 * Reports, status changes and per-access preferences use real, nonce-owned private stores.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReportAlertsUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = ApplicationProvider.getApplicationContext<ReportTestApplication>()
    private val fixture get() = fixtureRule.fixture
    private val device get() = UiDevice.getInstance(instrumentation)
    private val manager get() = app.getSystemService(NotificationManager::class.java)
    private val publisher get() = AndroidReportAlertsPublisher(app)
    private var scenario: ActivityScenario<ReportAlertsActivity>? = null
    private var activityRoute: ActivityScenario<ReportActivityActivity>? = null

    @Before fun beginAcademicLease() {
        assertTrue("This acceptance suite requires Android's runtime notification permission", Build.VERSION.SDK_INT >= 33)
        instrumentation.runOnMainSync { app.accountSession.signOut() }
        publisher.cancel()
    }

    @After fun closeOnlyOwnedTestActivitiesAndSummary() {
        scenario?.close(); activityRoute?.close()
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filter { it.packageName == app.packageName }.forEach(Activity::finish)
            app.accountSession.signOut()
        }
        publisher.cancel()
        instrumentation.waitForIdleSync()
    }

    @Test fun aOpeningFromActivityDoesNotAskAndExplicitDenialKeepsThePreferenceOff() {
        assertEquals("Run this class before any suite grants POST_NOTIFICATIONS", PackageManager.PERMISSION_DENIED,
            app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        assertNull(readPreferences().revision)
        activityRoute = ActivityScenario.launch(ReportActivityActivity::class.java)
        awaitTag("activity_empty")
        ui.onNodeWithTag("report_activity").performScrollToNode(hasTestTag("activity_alerts"))
        ui.onNodeWithTag("activity_alerts").performClick()
        awaitPreference(false)
        assertFalse(device.hasObject(permissionButton("permission_allow_button")))
        assertFalse(device.hasObject(permissionButton("permission_deny_button")))
        ui.onNodeWithTag("alerts_review").assertIsNotEnabled()
        assertEquals(ReportAlertsAvailability.RUNTIME_PERMISSION_DENIED, publisher.availability())

        clickTag("alerts_enable")
        chooseAndroidPermission("permission_deny_button")
        awaitPreference(false)
        scrollTo("alerts_permission_message")
        ui.onNodeWithTag("alerts_permission_message").assertTextContains("Android no autorizó", substring = true)
        assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        assertEquals(ReportAlertsAvailability.RUNTIME_PERMISSION_DENIED, publisher.availability())
        assertEquals(ReportAlertsPublishResult.BLOCKED, runBlocking { publisher.publish {} })
        assertNull(readPreferences().revision)
        assertTrue(readPreferences().presented.isEmpty())
        assertTrue(ownActive().isEmpty())
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun bExplicitGrantReviewsOnlyRealChangesDeduplicatesAndDisablingPreservesReceipts() {
        assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        val report = seedReport()
        launchAlerts(); awaitPreference(false)
        clickTag("alerts_enable")
        chooseAndroidPermission("permission_allow_button")
        awaitPreference(true)
        assertEquals(PackageManager.PERMISSION_GRANTED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        assertEquals(ReportAlertsAvailability.ALLOWED, publisher.availability())

        // A CREATED event is a local save, not a later change of status to notify.
        clickTag("alerts_review")
        awaitMessage("No hay cambios nuevos")
        assertTrue(readPreferences().presented.isEmpty())
        assertTrue(ownActive().isEmpty())

        val operation = UUID.randomUUID().toString()
        val actor = UUID.randomUUID().toString()
        runBlocking {
            SqliteReportRepository(app, fixture.databaseName, statusAuthority = { actor }).use {
                it.changeStatus(ReportStatusChangeCommand(operation, report.id, 1, ReportStatus.REPORTED,
                    ReportStatus.IN_REVIEW, "Cambio confirmado en el almacenamiento aislado de esta prueba"))
            }
        }
        clickTag("alerts_review")
        awaitMessage("Android aceptó un aviso general")
        awaitNative { ownActive().size == 1 }
        val accepted = readPreferences()
        assertEquals(setOf(operation), accepted.presented)
        assertFalse(accepted.presented.contains(report.id))
        val notification = ownActive().single().notification
        assertEquals(AndroidReportAlertsPublisher.CHANNEL_ID, notification.channelId)
        assertEquals(AndroidReportAlertsPublisher.TITLE, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(AndroidReportAlertsPublisher.TEXT, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(Notification.VISIBILITY_SECRET, notification.visibility)
        assertTrue(notification.contentIntent.isImmutable)
        listOf(report.id, operation, actor, report.description, "19.4326", "-99.1332").forEach {
            assertFalse("The Android summary must not carry report data", notification.extras.toString().contains(it))
        }

        clickTag("alerts_review")
        awaitMessage("No hay cambios nuevos")
        assertEquals(accepted, readPreferences())
        assertEquals(1, ownActive().size)
        scenario!!.recreate(); awaitPreference(true)
        assertEquals(accepted, readPreferences())
        clickTag("alerts_settings")
        awaitNative { scenario!!.state != Lifecycle.State.RESUMED }
        device.pressBack()
        awaitNative { scenario!!.state == Lifecycle.State.RESUMED }
        awaitPreference(true)
        assertEquals(accepted, readPreferences())
        clickTag("alerts_disable"); awaitPreference(false)
        awaitNative { ownActive().isEmpty() }
        val disabled = readPreferences()
        assertFalse(disabled.enabled)
        assertEquals(accepted.presented, disabled.presented)
        assertNotEquals(accepted.revision, disabled.revision)
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun cAccountTransitionsRetireTheOldScreenAndKeepPreferencesInTheirCapturedOwner() {
        assertEquals(PackageManager.PERMISSION_GRANTED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        val first = LocalAccount(UUID.randomUUID().toString(), "avisos_uno")
        val second = LocalAccount(UUID.randomUUID().toString(), "avisos_dos")
        instrumentation.runOnMainSync { app.accountSession.signIn(first) }
        launchAlerts(); awaitPreference(false)
        clickTag("alerts_enable"); awaitPreference(true)
        val firstPreferences = readPreferences()
        assertTrue(firstPreferences.enabled)
        assertTrue(firstPreferences.presented.isEmpty())
        // A session transition must also withdraw the app's generic summary.
        assertEquals(ReportAlertsPublishResult.ACCEPTED, runBlocking { publisher.publish {} })
        awaitNative { ownActive().size == 1 }

        instrumentation.runOnMainSync { app.accountSession.signIn(second) }
        ui.waitUntil(15_000) { scenario!!.state == Lifecycle.State.DESTROYED }
        awaitNative { ownActive().isEmpty() }
        launchAlerts(); awaitPreference(false)
        val secondPreferences = readPreferences()
        assertNull(secondPreferences.revision)
        assertFalse(secondPreferences.enabled)
        assertTrue(secondPreferences.presented.isEmpty())
        assertNotEquals(fixture.alertsFileNameFor(first.id), fixture.alertsFileNameFor(second.id))
        scenario!!.close()

        instrumentation.runOnMainSync { app.accountSession.signIn(first) }
        launchAlerts(); awaitPreference(true)
        assertEquals(firstPreferences, readPreferences())
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun dMalformedPreferencesShowRecoveryWithoutInventingAnEmptyWritableCopy() {
        val file = File(app.filesDir, fixture.alertsFileNameFor(null))
        assertEquals(app.filesDir.canonicalFile, file.canonicalFile.parentFile)
        assertFalse(file.exists())
        val original = "{\"version\":1,\"enabled\":true,\"presented\":[broken]}".toByteArray(Charsets.UTF_8)
        file.writeBytes(original)
        launchAlerts(); awaitTag("alerts_error")
        assertArrayEquals(original, file.readBytes())
        ui.onNodeWithTag("alerts_enable").assertDoesNotExist()
        ui.onNodeWithTag("alerts_review").assertDoesNotExist()
        clickTag("alerts_reload"); awaitTag("alerts_error")
        assertArrayEquals(original, file.readBytes())
        assertTrue(ownActive().isEmpty())
        assertEquals(0, fixture.refreshCalls)
    }

    private fun launchAlerts() { scenario = ActivityScenario.launch(ReportAlertsActivity::class.java) }
    private fun readPreferences(): ReportAlertsSnapshot = runBlocking {
        val session = app.accountSession.capture()
        app.openReportAlertsRepository(session) {}.read()
    }
    private fun seedReport(): Report = runBlocking {
        fixture.publishFreshFix()
        fixture.repository.createOnce(UUID.randomUUID().toString(), NewReport(IncidentType.THEFT, Priority.MEDIUM,
            "Descripción privada que no debe aparecer en el aviso Android", Instant.now().minusSeconds(300),
            ReportLocation(reference = "Referencia privada del ensayo", latitude = 19.4326, longitude = -99.1332)))
    }
    private fun permissionButton(id: String) = By.res(Pattern.compile(".*:id/$id"))
    private fun chooseAndroidPermission(id: String) {
        val button = device.wait(Until.findObject(permissionButton(id)), 10_000)
        assertNotNull("Android did not show the real notification permission dialog ($id)", button)
        button!!.click()
        device.wait(Until.gone(permissionButton(id)), 10_000)
        instrumentation.waitForIdleSync()
    }
    private fun awaitPreference(enabled: Boolean) {
        val text = if (enabled) "Preferencia activada" else "Avisos desactivados"
        ui.waitUntil(15_000) {
            ui.onAllNodes(hasTestTag("alerts_preference") and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun awaitMessage(text: String) {
        ui.waitUntil(15_000) {
            ui.onAllNodes(hasTestTag("alerts_message") and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo("alerts_message")
    }
    private fun awaitTag(tag: String) = ui.waitUntil(15_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun scrollTo(tag: String) = ui.onNodeWithTag("report_alerts").performScrollToNode(hasTestTag(tag))
    private fun clickTag(tag: String) { scrollTo(tag); ui.onNodeWithTag(tag).performClick() }
    private fun ownActive() = manager.activeNotifications.filter {
        it.tag == AndroidReportAlertsPublisher.SUMMARY_TAG && it.id == AndroidReportAlertsPublisher.SUMMARY_ID
    }
    private fun awaitNative(predicate: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (!predicate()) {
            if (SystemClock.uptimeMillis() >= until) fail("The expected native notification state did not arrive")
            SystemClock.sleep(20)
        }
    }
}
