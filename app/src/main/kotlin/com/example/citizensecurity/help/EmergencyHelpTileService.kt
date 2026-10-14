package com.example.citizensecurity.help

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.citizensecurity.R

/** A user-added action tile; listening never calls, requests GPS or opens an account. */
class EmergencyHelpTileService : TileService() {
    private val access by lazy {
        EmergencyTileAccess(object : EmergencyTilePlatform {
            override fun isLocked(): Boolean = this@EmergencyHelpTileService.isLocked
            override fun unlockAndRun(action: () -> Unit) {
                this@EmergencyHelpTileService.unlockAndRun(Runnable(action))
            }
            override fun openHelp() {
                if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(pendingIntent(this@EmergencyHelpTileService))
                else openLegacyHelp()
            }
        })
    }

    override fun onStartListening() {
        super.onStartListening()
        access.startListening()
        try {
            qsTile?.apply {
                label = getString(R.string.emergency_short_label)
                contentDescription = getString(R.string.emergency_long_label)
                icon = Icon.createWithResource(this@EmergencyHelpTileService, R.drawable.ic_help_shortcut)
                state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= 29) subtitle = "México · 911 y 089"
                if (Build.VERSION.SDK_INT >= 30) stateDescription = "Abre ayuda inmediata"
                updateTile()
            }
        } catch (_: Exception) {
            // System UI may unbind while a label update is in flight. No action is launched.
        }
    }

    override fun onStopListening() {
        access.stopListening()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        access.click()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        access.added()
    }

    override fun onTileRemoved() {
        access.removed()
        super.onTileRemoved()
    }

    override fun onDestroy() {
        access.destroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openLegacyHelp() {
        // The PendingIntent overload starts at API 34. API 26–33 require Intent; targetSdk
        // alone triggers this lint detector despite the checked runtime compatibility path.
        check(Build.VERSION.SDK_INT < 34)
        startActivityAndCollapse(entryIntent(this))
    }

    companion object {
        private const val REQUEST_CODE = 911089
        internal fun component(context: Context): ComponentName = ComponentName(context, EmergencyHelpTileService::class.java)
        internal fun entryIntent(context: Context): Intent = EmergencyHelpShortcut.entryIntent(context)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        internal fun pendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, REQUEST_CODE, entryIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
