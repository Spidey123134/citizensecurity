package com.example.citizensecurity.help

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.annotation.WorkerThread

enum class EmergencyPinResult { REQUESTED, UNSUPPORTED, LOCKED, NOT_CURRENT, FAILED }

internal interface EmergencyPinPlatform {
    fun isLocked(): Boolean
    fun isSupported(): Boolean
    fun request(): Boolean
}

/** A generic public entry. Shortcut metadata never contains an account, contact or report. */
object EmergencyHelpShortcut {
    const val ID = "help_immediate"
    fun entryIntent(context: Context): Intent = Intent(context, EmergencyAccessActivity::class.java)
        .setAction(Intent.ACTION_VIEW)

    @WorkerThread
    fun requestPin(context: Context, canAct: () -> Boolean): EmergencyPinResult = requestPin(
        object : EmergencyPinPlatform {
            private val shortcuts = context.getSystemService(ShortcutManager::class.java)
            private val keyguard = context.getSystemService(KeyguardManager::class.java)
            override fun isLocked() = keyguard == null || keyguard.isDeviceLocked
            override fun isSupported() = shortcuts?.isRequestPinShortcutSupported == true
            override fun request(): Boolean {
                // This ID is already declared as an immutable static shortcut. Asking to pin it
                // uses its existing intent/labels; it does not replace or publish another entry.
                val shortcut = ShortcutInfo.Builder(context, ID).build()
                return shortcuts?.requestPinShortcut(shortcut, null) == true
            }
        }, canAct,
    )

    internal fun requestPin(platform: EmergencyPinPlatform, canAct: () -> Boolean): EmergencyPinResult {
        try {
            if (!canAct()) return EmergencyPinResult.NOT_CURRENT
            if (platform.isLocked()) return EmergencyPinResult.LOCKED
            if (!platform.isSupported()) return EmergencyPinResult.UNSUPPORTED
            if (!canAct()) return EmergencyPinResult.NOT_CURRENT
            if (platform.isLocked()) return EmergencyPinResult.LOCKED
            if (!canAct()) return EmergencyPinResult.NOT_CURRENT
            return if (platform.request()) EmergencyPinResult.REQUESTED else EmergencyPinResult.FAILED
        } catch (_: Exception) {
            return EmergencyPinResult.FAILED
        }
    }
}
