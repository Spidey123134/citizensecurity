package com.example.citizensecurity.help

import android.app.KeyguardManager
import android.app.StatusBarManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.citizensecurity.R
import java.util.concurrent.atomic.AtomicBoolean

enum class TileAddResult { ADDED, ALREADY_ADDED, DECLINED, UNSUPPORTED, FAILED, NOT_CURRENT, LOCKED }

internal interface EmergencyTileAddPlatform {
    fun supported(): Boolean
    fun locked(): Boolean
    fun request(result: (TileAddResult) -> Unit)
}

/** Requests Android's confirmation, never adds a tile or opens help automatically. */
object EmergencyTileRequest {
    fun request(context: Context, canAct: () -> Boolean, result: (TileAddResult) -> Unit) = request(
        object : EmergencyTileAddPlatform {
            override fun supported() = Build.VERSION.SDK_INT >= 33 && context.getSystemService(StatusBarManager::class.java) != null
            override fun locked() = context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked != false
            override fun request(result: (TileAddResult) -> Unit) {
                if (Build.VERSION.SDK_INT >= 33) requestNative(context, result) else result(TileAddResult.UNSUPPORTED)
            }
        }, canAct, result,
    )

    internal fun request(platform: EmergencyTileAddPlatform, canAct: () -> Boolean, result: (TileAddResult) -> Unit) {
        val delivered = AtomicBoolean()
        fun deliver(value: TileAddResult) { if (delivered.compareAndSet(false, true)) result(value) }
        try {
            when {
                !canAct() -> deliver(TileAddResult.NOT_CURRENT)
                platform.locked() -> deliver(TileAddResult.LOCKED)
                !platform.supported() -> deliver(TileAddResult.UNSUPPORTED)
                !canAct() -> deliver(TileAddResult.NOT_CURRENT)
                platform.locked() -> deliver(TileAddResult.LOCKED)
                !canAct() -> deliver(TileAddResult.NOT_CURRENT)
                else -> platform.request(::deliver)
            }
        } catch (_: Exception) { deliver(TileAddResult.FAILED) }
    }

    @RequiresApi(33)
    private fun requestNative(context: Context, result: (TileAddResult) -> Unit) {
        val manager = context.getSystemService(StatusBarManager::class.java)
        manager.requestAddTileService(EmergencyHelpTileService.component(context), "Ayuda",
            Icon.createWithResource(context, R.drawable.ic_help_shortcut), context.mainExecutor) { code ->
            result(when (code) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> TileAddResult.ADDED
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> TileAddResult.ALREADY_ADDED
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> TileAddResult.DECLINED
                else -> TileAddResult.FAILED
            })
        }
    }
}
