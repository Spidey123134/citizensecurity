package com.example.citizensecurity.help

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import com.example.citizensecurity.SplashActivity
import com.example.citizensecurity.domain.LocalAccount
import com.example.citizensecurity.testing.ReportTestApplication
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Run with GPS permissions denied; only explicit dialer intents are intercepted, never called. */
class EmergencyAccessUiTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private val calls = ConcurrentLinkedQueue<Intent>()
    private var scenario: ActivityScenario<EmergencyAccessActivity>? = null
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_DIAL) return null
            calls.add(Intent(intent))
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }
    @Before fun interceptOnlyTheDialer() { instrumentation.addMonitor(monitor) }
    @After fun closeOwnActivity() {
        scenario?.close()
        instrumentation.removeMonitor(monitor)
        app.accountSession.signOut()
    }

    @Test fun openingFromOutsideAndRecreatingIgnorePhoneExtrasAndPreserveTheSessionWithoutGps() {
        assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
        app.accountSession.signIn(LocalAccount(UUID.randomUUID().toString(), "persona_privada"))
        val before = app.accountSession.capture()
        val fixture = app.enableFixture()
        try {
            val databases = app.databaseList().toSet()
            val privateFiles = app.filesDir.list().orEmpty().filter(::isPrivateData).toSet()
            val malicious = EmergencyHelpShortcut.entryIntent(app).setData(android.net.Uri.parse("tel:1234567890"))
                .putExtra("phone", "+525599999999").putExtra("report_folio", UUID.randomUUID().toString())
            scenario = ActivityScenario.launch(malicious)
            await("emergency_title")
            scenario!!.recreate()
            await("emergency_title")
            ui.onNodeWithText("persona_privada").assertDoesNotExist()
            assertTrue(calls.isEmpty())
            assertSame(before, app.accountSession.capture())
            assertEquals(0, fixture.refreshCalls)
            assertEquals(databases, app.databaseList().toSet())
            assertEquals(privateFiles, app.filesDir.list().orEmpty().filter(::isPrivateData).toSet())
        } finally { scenario?.close(); scenario = null; app.disableFixture(fixture) }
    }

    @Test fun emergencyNumbersOpenOnlyTheDialerAfterTheirExplicitGestures() {
        scenario = ActivityScenario.launch(EmergencyHelpShortcut.entryIntent(app))
        await("emergency_title")
        assertTrue(calls.isEmpty())
        ui.onNodeWithTag("emergency_dial_911").performScrollTo().performClick()
        ui.onNodeWithTag("emergency_dial_089").performScrollTo().performClick()
        assertEquals(listOf("911", "089"), calls.map { it.data?.schemeSpecificPart })
        calls.forEach {
            assertEquals(Intent.ACTION_DIAL, it.action)
            assertEquals("tel", it.data?.scheme)
            assertNull(it.extras)
        }
        assertFalse(calls.any { it.action == Intent.ACTION_CALL })
    }

    @Test fun publishedStaticShortcutTargetsOnlyPublicHelpWithGenericMetadata() {
        val manager = app.getSystemService(ShortcutManager::class.java)
        val shortcuts = manager.manifestShortcuts.filter { it.id == EmergencyHelpShortcut.ID }
        assertEquals(1, shortcuts.size)
        val shortcut = shortcuts.single()
        assertEquals("Ayuda", shortcut.shortLabel.toString())
        assertEquals("Ayuda inmediata", shortcut.longLabel.toString())
        assertEquals(1, shortcut.intents!!.size)
        val target = shortcut.intents!!.single()
        assertEquals(EmergencyAccessActivity::class.java.name, target.component?.className)
        assertEquals(Intent.ACTION_VIEW, target.action)
        assertNull(target.data)
        assertTrue(target.extras == null || target.extras!!.isEmpty)
        assertTrue(app.packageManager.getActivityInfo(target.component!!, 0).exported)
        val splash = app.packageManager.getActivityInfo(android.content.ComponentName(app, SplashActivity::class.java), PackageManager.GET_META_DATA)
        assertTrue(splash.metaData.getInt("android.app.shortcuts") != 0)
        assertTrue(calls.isEmpty())
    }

    @Test fun pinRequestCanBeCancelledWithoutClaimingOrCreatingAnAccess() {
        val manager = app.getSystemService(ShortcutManager::class.java)
        val before = manager.pinnedShortcuts.map { it.id }.toSet()
        scenario = ActivityScenario.launch(EmergencyHelpShortcut.entryIntent(app))
        await("emergency_pin")
        ui.onNodeWithTag("emergency_pin").performScrollTo().performClick()
        if (manager.isRequestPinShortcutSupported) {
            val device = UiDevice.getInstance(instrumentation)
            // Resolving HOME from an app can produce a resolver rather than the actual pin
            // confirmation host. Observe the system window and this shortcut's generic label.
            ui.waitUntil(10_000) {
                device.currentPackageName != app.packageName && device.hasObject(By.textContains("Ayuda"))
            }
            // Dismiss the system confirmation; tests never approve a new pinned item.
            device.pressBack()
            await("emergency_message")
            ui.onNodeWithTag("emergency_message").assertTextContains("todavía no confirma", substring = true)
        } else {
            await("emergency_message")
            ui.onNodeWithTag("emergency_message").assertTextContains("no permite fijar", substring = true)
        }
        assertEquals(before, manager.pinnedShortcuts.map { it.id }.toSet())
        assertTrue(calls.isEmpty())
    }

    private fun await(tag: String) = ui.waitUntil(10_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun isPrivateData(name: String) = listOf("trusted_contacts", "report_", "submission", "draft", "status_change", "photos").any(name::startsWith)
}
