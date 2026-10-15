package com.example.citizensecurity.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.citizensecurity.MainActivity
import com.example.citizensecurity.SplashActivity
import com.example.citizensecurity.domain.LocalAccount
import com.example.citizensecurity.help.EmergencyAccessActivity
import com.example.citizensecurity.help.EmergencyHelpShortcut
import com.example.citizensecurity.testing.ReportTestApplication
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Actual API 37 NotificationManager and immutable PendingIntent, using only nonce-owned IDs.
 * Permission is granted on the disposable emulator, never revoked during instrumentation.
 * The UI suite separately exercises runtime denial before this class grants that permission.
 */
@RunWith(AndroidJUnit4::class)
class ReportAlertsPublisherTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private val manager get() = app.getSystemService(NotificationManager::class.java)
    private val device get() = UiDevice.getInstance(instrumentation)
    private val nonce = UUID.randomUUID().toString()
    private val channelId = AndroidReportAlertsPublisher.TEST_CHANNEL_PREFIX + nonce
    private val summaryTag = AndroidReportAlertsPublisher.TEST_TAG_PREFIX + nonce
    private val otherTag = "report_alerts_unrelated_test_$nonce"
    private lateinit var publisher: AndroidReportAlertsPublisher
    private var scenario: ActivityScenario<EmergencyAccessActivity>? = null

    @Before fun prepareIsolatedPublisher() {
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        assertEquals(PackageManager.PERMISSION_GRANTED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        publisher = AndroidReportAlertsPublisher(app, channelId, summaryTag)
        assertNull(manager.getNotificationChannel(channelId))
    }

    @After fun retireOnlyOwnedNotificationsChannelsAndActivities() {
        if (::publisher.isInitialized) publisher.cancel()
        manager.cancel(otherTag, 777)
        // Every channel/tag here contains this test instance's fresh nonce. Never touch production.
        manager.deleteNotificationChannel(channelId)
        scenario?.close()
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filter { it.packageName == app.packageName }.forEach { it.finish() }
        }
        app.accountSession.signOut()
    }

    @Test fun constructorAndAvailabilityDoNotCreateAChannelOrPostAnAlert() {
        assertEquals(ReportAlertsAvailability.ALLOWED, publisher.availability())
        assertNull(manager.getNotificationChannel(channelId))
        assertTrue(ownActive().isEmpty())
        assertEquals(ReportAlertsAvailability.ALLOWED, publisher.availability())
        assertNull(manager.getNotificationChannel(channelId))
    }

    @Test fun explicitPrepareCreatesOnlyALowSilentGenericChannelWithoutPosting() {
        publisher.prepare()
        val channel = checkNotNull(manager.getNotificationChannel(channelId))
        assertEquals(AndroidReportAlertsPublisher.TITLE, channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertFalse(channel.shouldShowLights())
        assertFalse(channel.canShowBadge())
        // Android reserves persisted channel lockscreen overrides to the system/ranker. API 37
        // returns the untouched channel default (NO_OVERRIDE, -1000), even though the app asked
        // for SECRET. This is not Notification.visibility: the real posted payload below must
        // still be SECRET. Do not mistake the platform's absent override for a privacy failure.
        val noOverride = NotificationChannel("${channelId}_unregistered", "Unset control", NotificationManager.IMPORTANCE_LOW)
            .lockscreenVisibility
        assertTrue(channel.lockscreenVisibility == noOverride || channel.lockscreenVisibility == Notification.VISIBILITY_SECRET)
        publisher.prepare()
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(channelId).importance)
        assertTrue(ownActive().isEmpty())
    }

    @Test fun nativeSummaryContainsOnlyGenericTextAndAnImmutableNormalEntryWithoutPrivateValues() {
        val account = LocalAccount(UUID.randomUUID().toString(), "usuario_privado_notificacion")
        app.accountSession.signIn(account)
        assertEquals(ReportAlertsPublishResult.ACCEPTED, publish())
        val status = awaitSummary()
        assertEquals(summaryTag, status.tag)
        assertEquals(AndroidReportAlertsPublisher.SUMMARY_ID, status.id)
        val notification = status.notification
        assertEquals(channelId, notification.channelId)
        assertEquals(AndroidReportAlertsPublisher.TITLE, notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(AndroidReportAlertsPublisher.TEXT, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(AndroidReportAlertsPublisher.TEXT, notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString())
        assertEquals(Notification.VISIBILITY_SECRET, notification.visibility)
        assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertTrue(notification.flags and Notification.FLAG_LOCAL_ONLY != 0)
        assertEquals(0, notification.number)
        assertNull(notification.fullScreenIntent)
        assertTrue(notification.actions.isNullOrEmpty())
        assertTrue(notification.contentIntent.isImmutable)
        assertEquals(app.packageName, notification.contentIntent.creatorPackage)
        assertFalse(notification.allowSystemGeneratedContextualActions)
        val forbiddenKeys = listOf("reportId", "report_id", "folio", "actor", "ownerAccountId", "account_id", "latitude", "longitude", "photo", "reason")
        forbiddenKeys.forEach { assertFalse(notification.extras.containsKey(it)) }
        val extrasDescription = notification.extras.toString()
        assertFalse(extrasDescription.contains(account.id))
        assertFalse(extrasDescription.contains(account.username))
        assertTrue(notification.publicVersion == null ||
            notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == AndroidReportAlertsPublisher.TEXT)
        assertEquals(account, app.accountSession.account)
    }

    @Test fun repeatedPublishUpdatesOnlyOneSummaryAndCancelPreservesAnUnrelatedNotification() {
        assertEquals(ReportAlertsPublishResult.ACCEPTED, publish())
        awaitSummary()
        assertEquals(ReportAlertsPublishResult.ACCEPTED, publish())
        assertEquals(1, ownActive().size)
        val unrelated = Notification.Builder(app, channelId).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Aviso controlado del ensayo nativo").setContentText("Este identificador pertenece sólo a esta prueba.").build()
        manager.notify(otherTag, 777, unrelated)
        await { manager.activeNotifications.any { it.tag == otherTag && it.id == 777 } }
        publisher.cancel()
        await { ownActive().isEmpty() }
        assertTrue(manager.activeNotifications.any { it.tag == otherTag && it.id == 777 })
    }

    @Test fun immutableContentIntentIgnoresFillInAndOpensSplashThroughTheNormalLoginRoute() {
        assertEquals(ReportAlertsPublishResult.ACCEPTED, publish())
        val pending = awaitSummary().notification.contentIntent
        scenario = ActivityScenario.launch(EmergencyHelpShortcut.entryIntent(app))
        val delivered = AtomicReference<Intent?>()
        val malicious = Intent(Intent.ACTION_CALL, Uri.parse("tel:1234567890"))
            .putExtra("ownerAccountId", UUID.randomUUID().toString())
            .putExtra("report_folio", UUID.randomUUID().toString())
        pending.send(app, 0, malicious, PendingIntent.OnFinished { _, sent, _, _, _ ->
            delivered.set(Intent(sent))
        }, Handler(Looper.getMainLooper()))
        await { delivered.get() != null }
        val intent = checkNotNull(delivered.get())
        assertEquals(ComponentName(app, SplashActivity::class.java), intent.component)
        assertNull(intent.action)
        assertNull(intent.data)
        assertNull(intent.clipData)
        assertTrue(intent.extras == null || intent.extras!!.isEmpty)
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(flags, intent.flags and flags)
        assertTrue(pending.isImmutable)
        // Let the existing splash's delayed transition finish before closing this test's route.
        await {
            var login = false
            instrumentation.runOnMainSync {
                login = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .any { it is MainActivity }
            }
            login
        }
    }

    @Test fun cancellingAnOlderAttemptCannotRemoveANewerSummaryFromAnotherPublisher() {
        val newer = AndroidReportAlertsPublisher(app, channelId, summaryTag)
        assertEquals(ReportAlertsPublishResult.ACCEPTED, publish())
        awaitSummary()
        assertEquals(ReportAlertsPublishResult.ACCEPTED, runBlocking { newer.publish {} })
        awaitSummary()
        runBlocking { publisher.cancelAttempt() }
        assertEquals(1, ownActive().size)
        runBlocking { newer.cancelAttempt() }
        await { ownActive().isEmpty() }
        // A retired attempt cannot later remove an explicitly posted replacement either.
        assertEquals(ReportAlertsPublishResult.ACCEPTED, publish())
        awaitSummary()
        runBlocking { newer.cancelAttempt() }
        assertEquals(1, ownActive().size)
    }

    @Test fun existingBlockedChannelCannotBeRecreatedOrBypassedToPostASummary() {
        manager.createNotificationChannel(NotificationChannel(channelId, "Canal bloqueado del ensayo", NotificationManager.IMPORTANCE_NONE))
        assertEquals(ReportAlertsAvailability.CHANNEL_BLOCKED, publisher.availability())
        publisher.prepare()
        assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(channelId).importance)
        assertEquals(ReportAlertsPublishResult.BLOCKED, publish())
        assertTrue(ownActive().isEmpty())
    }

    @Test fun actualUserChannelBlockIsRespectedWithoutRevokingTheAppsRuntimePermission() {
        assertEquals(ReportAlertsAvailability.ALLOWED, publisher.availability())
        publisher.prepare()
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel(channelId).importance)
        scenario = ActivityScenario.launch(EmergencyHelpShortcut.entryIntent(app))
        var openedSettings = false
        try {
            scenario!!.onActivity { activity ->
                activity.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId))
            }
            openedSettings = true
            // Inspected on the owned API 37 emulator. This is the channel's main switch, not
            // the app-wide permission or unrelated lower settings such as a notification dot.
            val mainSwitch = device.wait(Until.findObject(By.res("com.android.settings", "main_switch_bar")), 10_000)
            assertNotNull("Android did not open this nonce-owned channel's notification settings", mainSwitch)
            val checked = mainSwitch!!.findObject(By.res("android:id/switch_widget"))
            assertNotNull("Android's channel switch was not present", checked)
            assertTrue(checked!!.isChecked)
            mainSwitch.click()
            await { manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE }

            assertEquals(PackageManager.PERMISSION_GRANTED, app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            assertEquals(ReportAlertsAvailability.CHANNEL_BLOCKED, publisher.availability())
            publisher.prepare()
            assertEquals(NotificationManager.IMPORTANCE_NONE, manager.getNotificationChannel(channelId).importance)
            assertEquals(ReportAlertsPublishResult.BLOCKED, publish())
            assertTrue(ownActive().isEmpty())
        } finally {
            if (openedSettings) {
                device.pressBack()
                await {
                    var returned = false
                    instrumentation.runOnMainSync {
                        returned = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .any { it is EmergencyAccessActivity }
                    }
                    returned
                }
            }
        }
    }

    @Test fun revokedScopeBeforePublicationCannotCreateAChannelOrAnAlert() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { publisher.publish { throw IllegalStateException("This captured session was revoked") } }
        }
        assertNull(manager.getNotificationChannel(channelId))
        assertTrue(ownActive().isEmpty())
    }

    @Test fun isolatedTestOverridesCannotTargetArbitraryProductionOrOtherNotificationNames() {
        listOf(
            "../outside" to summaryTag,
            channelId to AndroidReportAlertsPublisher.SUMMARY_TAG,
            AndroidReportAlertsPublisher.CHANNEL_ID to summaryTag,
            AndroidReportAlertsPublisher.TEST_CHANNEL_PREFIX + "invalid" to summaryTag,
        ).forEach { (channel, tag) ->
            assertThrows(IllegalArgumentException::class.java) { AndroidReportAlertsPublisher(app, channel, tag) }
        }
        assertNull(manager.getNotificationChannel(channelId))
        assertTrue(ownActive().isEmpty())
    }

    private fun publish() = runBlocking { publisher.publish {} }
    private fun ownActive(): List<StatusBarNotification> = manager.activeNotifications.filter {
        it.tag == summaryTag && it.id == AndroidReportAlertsPublisher.SUMMARY_ID
    }
    private fun awaitSummary(): StatusBarNotification {
        await { ownActive().size == 1 }
        return ownActive().single()
    }
    private fun await(predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!predicate()) {
            if (SystemClock.uptimeMillis() >= end) fail("The expected native notification/entry did not arrive")
            SystemClock.sleep(20)
        }
    }
}
