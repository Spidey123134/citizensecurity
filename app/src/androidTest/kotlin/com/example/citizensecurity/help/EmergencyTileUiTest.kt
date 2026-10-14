package com.example.citizensecurity.help

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.example.citizensecurity.R
import com.example.citizensecurity.domain.LocalAccount
import com.example.citizensecurity.testing.ReportTestApplication
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Native public-entry, manifest and system confirmation checks on the owned API 37 emulator.
 * Tests never approve adding a tile and do not impersonate a bound TileService/SystemUI token.
 */
class EmergencyTileUiTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private val device get() = UiDevice.getInstance(instrumentation)
    private var scenario: ActivityScenario<EmergencyAccessActivity>? = null
    private val outgoing = ConcurrentLinkedQueue<Intent>()
    private val dialerMonitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_DIAL && intent.action != Intent.ACTION_CALL) return null
            outgoing.add(Intent(intent))
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    @Before fun interceptAnyTelephoneIntent() {
        declineOwnTileDialog()
        instrumentation.addMonitor(dialerMonitor)
    }

    @After fun closeOwnActivities() {
        declineOwnTileDialog()
        scenario?.close()
        scenario = null
        instrumentation.removeMonitor(dialerMonitor)
        instrumentation.runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .filter { it is EmergencyAccessActivity }.forEach { it.finish() }
        }
        app.accountSession.signOut()
    }

    @Test fun advertisedTileServiceHasSystemBindingPermissionAndOnlyItsExplicitPublicEntry() {
        val component = EmergencyHelpTileService.component(app)
        assertEquals(app.packageName, component.packageName)
        assertEquals(EmergencyHelpTileService::class.java.name, component.className)
        val service = app.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        assertTrue(service.exported)
        assertEquals(Manifest.permission.BIND_QUICK_SETTINGS_TILE, service.permission)
        assertEquals(R.drawable.ic_help_shortcut, service.icon)
        assertTrue(service.loadLabel(app.packageManager).toString().contains("Ayuda"))
        val matches = app.packageManager.queryIntentServices(
            Intent(TileService.ACTION_QS_TILE).setPackage(app.packageName), PackageManager.GET_META_DATA,
        ).filter { it.serviceInfo.name == component.className }
        assertEquals(1, matches.size)
        val entry = EmergencyHelpTileService.entryIntent(app)
        assertEquals(Intent.ACTION_VIEW, entry.action)
        assertEquals(ComponentName(app, EmergencyAccessActivity::class.java), entry.component)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, entry.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertNull(entry.data)
        assertNull(entry.clipData)
        assertTrue(entry.extras == null || entry.extras!!.isEmpty)
        assertFalse((service.metaData?.getBoolean("android.service.quicksettings.ACTIVE_TILE", false)) == true)
        assertTrue(outgoing.isEmpty())
    }

    @Test fun separateTestApplicationHasNoSystemSignaturePermissionRequiredByTheTile() {
        // Instrumentation executes under the target application's UID, which may bind its own
        // service. This checks the installed separate test APK's permission registry, not IPC.
        val testPackage = instrumentation.context.packageName
        assertNotEquals(app.packageName, testPackage)
        val installedTest = app.packageManager.getApplicationInfo(testPackage, 0)
        assertNotEquals(app.applicationInfo.uid, installedTest.uid)
        val permission = Manifest.permission.BIND_QUICK_SETTINGS_TILE
        assertEquals(PackageManager.PERMISSION_DENIED, app.packageManager.checkPermission(permission, testPackage))
        val definition = app.packageManager.getPermissionInfo(permission, 0)
        assertEquals(PermissionInfo.PROTECTION_SIGNATURE, definition.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
        val service = app.packageManager.getServiceInfo(EmergencyHelpTileService.component(app), 0)
        assertEquals(permission, service.permission)
        assertTrue(outgoing.isEmpty())
    }

    @Test fun immutablePendingIntentIgnoresFillInAndOpensOnlyPublicHelpWithoutReadingPrivateData() {
        assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        assertEquals(PackageManager.PERMISSION_DENIED, app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION))
        app.accountSession.signIn(LocalAccount(UUID.randomUUID().toString(), "cuenta_privada_tile"))
        val session = app.accountSession.capture()
        val fixture = app.enableFixture()
        try {
            // Give the real PendingIntent sender a foreground Activity rather than relying on
            // instrumentation/background-launch exemptions that SystemUI does not need.
            scenario = ActivityScenario.launch(EmergencyHelpShortcut.entryIntent(app))
            await("emergency_title")
            val databases = app.databaseList().toSet()
            val privateFiles = app.filesDir.list().orEmpty().filter(::isPrivateData).toSet()
            val request = EmergencyHelpTileService.pendingIntent(app)
            assertTrue(request.isImmutable)
            assertEquals(app.packageName, request.creatorPackage)
            val malicious = Intent(Intent.ACTION_CALL, Uri.parse("tel:1234567890"))
                .putExtra("phone", "+525599999999")
                .putExtra("report_folio", UUID.randomUUID().toString())
                .putExtra("account_id", session.account!!.id)
            val delivered = AtomicReference<Intent?>()
            request.send(app, 0, malicious, PendingIntent.OnFinished { _, sent, _, _, _ ->
                delivered.set(Intent(sent))
            }, Handler(Looper.getMainLooper()))
            ui.waitUntil(10_000) { delivered.get() != null }
            // NEW_TASK can bring an existing help Activity to the front. Inspect the actual
            // platform-delivered intent rather than requiring another onCreate/ActivityMonitor.
            val sent = checkNotNull(delivered.get())
            assertEquals(ComponentName(app, EmergencyAccessActivity::class.java), sent.component)
            assertEquals(Intent.ACTION_VIEW, sent.action)
            assertNull(sent.data)
            assertNull(sent.clipData)
            assertTrue(sent.extras == null || sent.extras!!.isEmpty)
            await("emergency_title")
            assertEquals(app.packageName, device.currentPackageName)
            ui.onNodeWithText("cuenta_privada_tile").assertDoesNotExist()
            assertSame(session, app.accountSession.capture())
            assertEquals(0, fixture.refreshCalls)
            assertEquals(databases, app.databaseList().toSet())
            assertEquals(privateFiles, app.filesDir.list().orEmpty().filter(::isPrivateData).toSet())
            assertTrue(outgoing.isEmpty())
        } finally {
            app.disableFixture(fixture)
        }
    }

    @Test fun publicHelpOffersTheTileButtonWithoutRequestingAdditionOnOpeningOrRecreation() {
        val tilesBefore = tileList()
        scenario = ActivityScenario.launch(EmergencyHelpShortcut.entryIntent(app))
        await("emergency_add_tile")
        ui.onNodeWithTag("emergency_add_tile").performScrollTo().assertIsEnabled()
        assertEquals(app.packageName, device.currentPackageName)
        ui.onAllNodesWithTag("emergency_tile_message").assertCountEquals(0)
        scenario!!.recreate()
        await("emergency_add_tile")
        ui.onNodeWithTag("emergency_add_tile").performScrollTo().assertIsEnabled()
        assertEquals(app.packageName, device.currentPackageName)
        assertEquals(tilesBefore, tileList())
        ui.onAllNodesWithTag("emergency_tile_message").assertCountEquals(0)
        assertTrue(outgoing.isEmpty())
    }

    @Test fun explicitAddTileRequestCanBeCancelledWithoutAddingAnItemOrClaimingItWasAdded() {
        val tilesBefore = tileList()
        scenario = ActivityScenario.launch(EmergencyHelpShortcut.entryIntent(app))
        await("emergency_add_tile")
        ui.onNodeWithTag("emergency_add_tile").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(10_000) {
            ownsTileDialog()
        }
        // Decline SystemUI's confirmation. No test approves a tile or changes a physical device.
        assertTrue(declineOwnTileDialog())
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("No se añadió Ayuda", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithTag("emergency_tile_message").performScrollTo().assertTextContains("No se añadió Ayuda", substring = true)
        ui.onNodeWithTag("emergency_add_tile").performScrollTo().assertIsEnabled()
        assertEquals(tilesBefore, tileList())
        assertTrue(outgoing.isEmpty())
    }

    private fun await(tag: String) = ui.waitUntil(10_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun ownsTileDialog(): Boolean = device.currentPackageName == "com.android.systemui" &&
        device.hasObject(By.textContains("CITIZENSECURITY wants to add a tile")) &&
        (device.hasObject(By.textContains("Ayuda")) || device.hasObject(By.descContains("Ayuda")))

    private fun declineOwnTileDialog(): Boolean {
        if (!ownsTileDialog()) return false
        // API 37 exposes Ayuda in size-radio content descriptions. Its negative action is the
        // observed English SystemUI button; selecting it changes no tile or launcher icon.
        val decline = device.findObject(By.text("Don't add tile"))
        if (decline != null) decline.click() else device.pressBack()
        return true
    }
    private fun tileList(): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand("settings get secure sysui_qs_tiles")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes().toString(Charsets.UTF_8).trim() }
    }
    private fun isPrivateData(name: String) =
        listOf("trusted_contacts", "report_", "submission", "draft", "status_change", "photos").any(name::startsWith)
}
