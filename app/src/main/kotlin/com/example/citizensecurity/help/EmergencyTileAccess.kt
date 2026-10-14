package com.example.citizensecurity.help

import androidx.annotation.MainThread

/** Only the public help route is offered: no account, number, contact or report input. */
internal interface EmergencyTilePlatform {
    fun isLocked(): Boolean
    fun unlockAndRun(action: () -> Unit)
    fun openHelp()
}

/** One explicit tile gesture owns an unlock callback until consumed or superseded. */
@MainThread
internal class EmergencyTileAccess(private val platform: EmergencyTilePlatform) {
    private var listening = false
    private var removed = false
    private var destroyed = false
    private var generation = 0L
    private var stopped = 0L
    private var pending: Gesture? = null

    fun startListening() {
        if (!destroyed && !removed) listening = true
    }

    fun stopListening() {
        listening = false
        stopped++
        // unlockAndRun normally closes the shade. Closing it alone does not retire that
        // explicit gesture; its callback may still open public help after a valid unlock.
    }

    fun added() {
        if (destroyed) return
        removed = false
        listening = false
        generation++
        pending = null
    }

    fun removed() {
        removed = true
        listening = false
        generation++
        pending = null
    }

    fun destroy() {
        destroyed = true
        listening = false
        generation++
        pending = null
    }

    fun click(): Boolean {
        if (!listening || removed || destroyed) return false
        val previous = pending
        if (previous != null && !(previous.awaitingUnlock && previous.stoppedAt != stopped)) return false
        // Android does not invoke the Runnable when unlocking is cancelled. A new gesture
        // after closing/reopening the shade may supersede that outstanding callback.
        val own = Gesture(++generation, stopped)
        pending = own
        return try {
            val locked = platform.isLocked()
            if (!current(own)) false
            else {
                if (locked) {
                    own.awaitingUnlock = true
                    platform.unlockAndRun { finish(own) }
                } else finish(own)
                true
            }
        } catch (_: Exception) {
            if (pending === own) pending = null
            false
        }
    }

    private fun finish(own: Gesture) {
        if (!current(own) || own.consumed) return
        own.consumed = true
        own.awaitingUnlock = false
        try {
            val locked = platform.isLocked()
            // A blocking/reentrant lock query can remove the tile or destroy the service.
            if (!locked && current(own)) platform.openHelp()
        } catch (_: Exception) {
            // A missing activity/system connection needs another explicit gesture.
        } finally {
            if (pending === own) pending = null
        }
    }

    private fun current(own: Gesture): Boolean =
        !destroyed && !removed && pending === own && own.generation == generation

    private class Gesture(val generation: Long, val stoppedAt: Long) {
        var awaitingUnlock = false
        var consumed = false
    }
}
