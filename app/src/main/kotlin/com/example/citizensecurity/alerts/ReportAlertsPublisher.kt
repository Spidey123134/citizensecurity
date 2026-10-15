package com.example.citizensecurity.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.citizensecurity.R
import com.example.citizensecurity.SplashActivity
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class ReportAlertsAvailability { ALLOWED, RUNTIME_PERMISSION_DENIED, APP_BLOCKED, CHANNEL_BLOCKED }
enum class ReportAlertsPublishResult { ACCEPTED, BLOCKED }

interface ReportAlertsPublisher {
    fun availability(): ReportAlertsAvailability
    /** Explicit enabling may prepare the generic channel; reading availability never creates it. */
    fun prepare() = Unit
    suspend fun publish(checkCurrent: () -> Unit): ReportAlertsPublishResult
    /** Withdraw only this publisher's latest attempt, without cancelling a newer summary. */
    suspend fun cancelAttempt()
    fun cancel()
}

/** A single generic summary. No account, report, event, location or image enters the OS payload.
 * ACCEPTED means NotificationManager returned while gates were allowed, not proof it was read.
 */
class AndroidReportAlertsPublisher internal constructor(
    context: Context,
    private val channelId: String,
    private val summaryTag: String,
) : ReportAlertsPublisher {
    constructor(context: Context) : this(context, CHANNEL_ID, SUMMARY_TAG)
    private val context = context.applicationContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val summaryKey = channelId to summaryTag
    private var lastAttempt: Any? = null

    init {
        val production = channelId == CHANNEL_ID && summaryTag == SUMMARY_TAG
        val suffix = channelId.removePrefix(TEST_CHANNEL_PREFIX)
        val isolatedTest = channelId.startsWith(TEST_CHANNEL_PREFIX) &&
            runCatching { UUID.fromString(suffix).toString() == suffix }.getOrDefault(false) &&
            summaryTag == TEST_TAG_PREFIX + suffix
        require(production || isolatedTest) { "El canal y resumen deben pertenecer a los avisos de reportes." }
    }

    override fun availability(): ReportAlertsAvailability = availabilityChecked {}

    private fun availabilityChecked(checkCurrent: () -> Unit): ReportAlertsAvailability {
        checkCurrent()
        val granted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        checkCurrent()
        if (!granted) return ReportAlertsAvailability.RUNTIME_PERMISSION_DENIED
        val notifications = manager
        checkCurrent()
        if (notifications == null) return ReportAlertsAvailability.APP_BLOCKED
        val enabled = notifications.areNotificationsEnabled()
        checkCurrent()
        if (!enabled) return ReportAlertsAvailability.APP_BLOCKED
        val channel = notifications.getNotificationChannel(channelId)
        checkCurrent()
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return ReportAlertsAvailability.CHANNEL_BLOCKED
        if (Build.VERSION.SDK_INT >= 28 && channel?.group != null) {
            val blocked = notifications.getNotificationChannelGroup(channel.group)?.isBlocked == true
            checkCurrent()
            if (blocked) return ReportAlertsAvailability.CHANNEL_BLOCKED
        }
        return ReportAlertsAvailability.ALLOWED
    }

    override fun prepare() = prepareChecked {}

    private fun prepareChecked(checkCurrent: () -> Unit) {
        checkCurrent()
        val notifications = checkNotNull(manager)
        checkCurrent()
        val existing = notifications.getNotificationChannel(channelId)
        checkCurrent()
        if (existing == null) {
            val channel = NotificationChannel(channelId, TITLE, NotificationManager.IMPORTANCE_LOW).apply {
                description = "Avisos generales de cambios de estado guardados en este teléfono."
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
            }
            checkCurrent()
            notifications.createNotificationChannel(channel)
            checkCurrent()
        }
    }

    // availabilityChecked verifies POST_NOTIFICATIONS immediately around every platform action.
    @SuppressLint("MissingPermission")
    override suspend fun publish(checkCurrent: () -> Unit): ReportAlertsPublishResult = withContext(Dispatchers.Main.immediate) {
        synchronized(attemptLock) { lastAttempt = null }
        ensureActive()
        if (availabilityChecked(checkCurrent) != ReportAlertsAvailability.ALLOWED) return@withContext ReportAlertsPublishResult.BLOCKED
        prepareChecked(checkCurrent)
        if (availabilityChecked(checkCurrent) != ReportAlertsAvailability.ALLOWED) return@withContext ReportAlertsPublishResult.BLOCKED
        checkCurrent()
        val pending = PendingIntent.getActivity(context, SUMMARY_ID,
            Intent(context, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        checkCurrent()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_report_alert)
            .setContentTitle(TITLE)
            .setContentText(TEXT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(TEXT))
            .setContentIntent(pending)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(Notification.CATEGORY_STATUS)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setSilent(true)
            .apply { if (Build.VERSION.SDK_INT >= 29) setAllowSystemGeneratedContextualActions(false) }
            .build()
        if (availabilityChecked(checkCurrent) != ReportAlertsAvailability.ALLOWED) return@withContext ReportAlertsPublishResult.BLOCKED
        checkCurrent()
        ensureActive()
        val notifications = checkNotNull(manager)
        checkCurrent()
        try {
            synchronized(attemptLock) {
                checkCurrent()
                ensureActive()
                val attempt = Any()
                lastAttempt = attempt
                currentAttempts[summaryKey] = attempt
                notifications.notify(summaryTag, SUMMARY_ID, notification)
            }
            checkCurrent()
            val after = availabilityChecked(checkCurrent)
            checkCurrent()
            if (after == ReportAlertsAvailability.ALLOWED) ReportAlertsPublishResult.ACCEPTED
            else { cancelOwnedAttempt(); ReportAlertsPublishResult.BLOCKED }
        } catch (failure: Throwable) {
            // A session change after notify must not leave this process's old summary behind.
            try { cancelOwnedAttempt() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    override suspend fun cancelAttempt() = withContext(Dispatchers.Main.immediate) { cancelOwnedAttempt() }

    private fun cancelOwnedAttempt() {
        synchronized(attemptLock) {
            val attempt = lastAttempt ?: return
            if (currentAttempts[summaryKey] === attempt) {
                manager?.cancel(summaryTag, SUMMARY_ID)
                currentAttempts.remove(summaryKey)
            }
            lastAttempt = null
        }
    }

    /** Never cancel other notifications belonging to this app or to another test instance. */
    override fun cancel() {
        synchronized(attemptLock) {
            manager?.cancel(summaryTag, SUMMARY_ID)
            currentAttempts.remove(summaryKey)
            lastAttempt = null
        }
    }

    companion object {
        const val CHANNEL_ID = "report_status_changes"
        const val SUMMARY_TAG = "citizensecurity_report_changes"
        const val SUMMARY_ID = 3601
        const val TITLE = "Cambios en tus reportes"
        const val TEXT = "Hay cambios de estado guardados en este teléfono. Abre la app para revisar la actividad."
        internal const val TEST_CHANNEL_PREFIX = "report_status_changes_test_"
        internal const val TEST_TAG_PREFIX = "citizensecurity_report_changes_test_"
        private val attemptLock = Any()
        private val currentAttempts = mutableMapOf<Pair<String, String>, Any>()
    }
}
