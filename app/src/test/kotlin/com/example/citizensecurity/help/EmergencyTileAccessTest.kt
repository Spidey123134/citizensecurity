package com.example.citizensecurity.help

import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class EmergencyTileAccessTest {
    private val platform = Platform()
    private val access = EmergencyTileAccess(platform)

    @Test fun constructionAddingAndListeningDoNotQueryLockOrLaunchAnything() {
        access.added()
        access.startListening()
        access.stopListening()
        assertEquals(0, platform.lockChecks)
        assertEquals(0, platform.opened)
        assertTrue(platform.unlocks.isEmpty())
    }

    @Test fun aClickOutsideListeningCannotOpenHelpOrAskToUnlock() {
        assertFalse(access.click())
        access.startListening()
        access.stopListening()
        assertFalse(access.click())
        assertEquals(0, platform.lockChecks)
        assertTrue(platform.unlocks.isEmpty())
        assertEquals(0, platform.opened)
    }

    @Test fun anUnlockedExplicitClickOpensPublicHelpOnceWithoutUnlocking() {
        access.startListening()
        assertTrue(access.click())
        assertEquals(1, platform.opened)
        assertTrue(platform.unlocks.isEmpty())
    }

    @Test fun aLockedClickWaitsForSuccessfulUnlockAndConsumesItsCallbackOnce() {
        platform.locked = true
        access.startListening()
        assertTrue(access.click())
        assertEquals(0, platform.opened)
        assertEquals(1, platform.unlocks.size)
        platform.locked = false
        val callback = platform.unlocks.single()
        callback()
        callback()
        assertEquals(1, platform.opened)
    }

    @Test fun repeatedClicksDuringTheSameUnlockPromptRemainSingleFlight() {
        platform.locked = true
        access.startListening()
        assertTrue(access.click())
        assertFalse(access.click())
        assertFalse(access.click())
        assertEquals(1, platform.unlocks.size)
        assertEquals(0, platform.opened)
    }

    @Test fun closingTheShadeForUnlockDoesNotInvalidateThatLegitimateCallback() {
        platform.locked = true
        access.startListening()
        access.click()
        access.stopListening()
        platform.locked = false
        platform.unlocks.single()()
        assertEquals(1, platform.opened)
        assertFalse(access.click())
    }

    @Test fun reopeningTheShadeAloneDoesNotRestartOrDiscardAPendingUnlock() {
        platform.locked = true
        access.startListening()
        access.click()
        access.stopListening()
        access.startListening()
        assertEquals(1, platform.unlocks.size)
        assertEquals(0, platform.opened)
        platform.locked = false
        platform.unlocks.single()()
        assertEquals(1, platform.opened)
    }

    @Test fun anotherGestureAfterCancelledUnlockSupersedesTheOldCallback() {
        platform.locked = true
        access.startListening()
        access.click()
        val old = platform.unlocks.single()
        access.stopListening()
        access.startListening()
        assertTrue(access.click())
        assertEquals(2, platform.unlocks.size)
        platform.locked = false
        old()
        assertEquals(0, platform.opened)
        platform.unlocks.last()()
        assertEquals(1, platform.opened)
        old()
        assertEquals(1, platform.opened)
    }

    @Test fun removingTheTileRetiresItsCallbackAndBlocksClicks() {
        platform.locked = true
        access.startListening()
        access.click()
        access.removed()
        access.startListening()
        platform.locked = false
        platform.unlocks.single()()
        assertFalse(access.click())
        assertEquals(0, platform.opened)
    }

    @Test fun destroyedServiceCannotBeRevivedByAddedOrListeningCallbacks() {
        platform.locked = true
        access.startListening()
        access.click()
        access.destroy()
        access.added()
        access.startListening()
        platform.locked = false
        platform.unlocks.single()()
        assertFalse(access.click())
        assertEquals(0, platform.opened)
    }

    @Test fun reAddingATileRequiresANewGestureAndNeverAdoptsTheRemovedCallback() {
        platform.locked = true
        access.startListening()
        access.click()
        val old = platform.unlocks.single()
        access.removed()
        access.added()
        access.startListening()
        platform.locked = false
        old()
        assertEquals(0, platform.opened)
        assertTrue(access.click())
        assertEquals(1, platform.opened)
    }

    @Test fun anUnlockCallbackWhileStillLockedCannotOpenAndAllowsAnExplicitRetry() {
        platform.locked = true
        access.startListening()
        access.click()
        platform.unlocks.single()()
        assertEquals(0, platform.opened)
        platform.locked = false
        assertTrue(access.click())
        assertEquals(1, platform.opened)
    }

    @Test fun lockQueryFailureDoesNotLaunchAndNeedsANewGesture() {
        platform.lockAction = { throw IOException("Unavailable system binder") }
        access.startListening()
        assertFalse(access.click())
        assertEquals(0, platform.opened)
        assertTrue(platform.unlocks.isEmpty())
        platform.lockAction = { false }
        assertTrue(access.click())
        assertEquals(1, platform.opened)
    }

    @Test fun failedUnlockRequestDoesNotLeaveTheTilePermanentlyBusy() {
        platform.locked = true
        platform.unlockAction = { throw IOException("Unavailable unlock connection") }
        access.startListening()
        assertFalse(access.click())
        assertEquals(0, platform.opened)
        platform.unlockAction = { platform.unlocks += it }
        assertTrue(access.click())
        assertEquals(1, platform.unlocks.size)
    }

    @Test fun failedLaunchDoesNotAutomaticallyRetryAndReleasesTheGesture() {
        platform.openAction = { throw IOException("Help activity unavailable") }
        access.startListening()
        assertTrue(access.click())
        assertEquals(1, platform.opened)
        access.stopListening()
        access.startListening()
        assertEquals(1, platform.opened)
        platform.openAction = {}
        assertTrue(access.click())
        assertEquals(2, platform.opened)
    }

    @Test fun removalDuringTheLastLockCheckPreventsLaunchingAfterThatCheckReturns() {
        platform.lockAction = {
            if (platform.lockChecks == 2) access.removed()
            false
        }
        access.startListening()
        access.click()
        assertEquals(0, platform.opened)
        assertFalse(access.click())
    }

    @Test fun destructionDuringUnlockRegistrationRejectsItsLateCallback() {
        platform.locked = true
        platform.unlockAction = { callback -> platform.unlocks += callback; access.destroy() }
        access.startListening()
        access.click()
        platform.locked = false
        platform.unlocks.single()()
        assertEquals(0, platform.opened)
    }

    @Test fun aReentrantClickWhileTheActivityLaunchIsRunningCannotLaunchTwice() {
        platform.openAction = { assertFalse(access.click()) }
        access.startListening()
        access.click()
        assertEquals(1, platform.opened)
    }

    @Test fun aSynchronousUnlockCallbackIsReservedBeforeThePlatformInvokesIt() {
        platform.locked = true
        platform.unlockAction = { callback ->
            assertFalse(access.click())
            platform.locked = false
            callback()
            callback()
        }
        access.startListening()
        assertTrue(access.click())
        assertEquals(1, platform.opened)
    }

    @Test fun lockingAgainBetweenChecksPreventsAnUnlockedClickFromOpeningHelp() {
        platform.lockAction = { platform.lockChecks != 1 }
        access.startListening()
        access.click()
        assertEquals(0, platform.opened)
        assertTrue(platform.unlocks.isEmpty())
        platform.lockAction = { false }
        access.click()
        assertEquals(1, platform.opened)
    }

    private class Platform : EmergencyTilePlatform {
        var locked = false
        var lockChecks = 0
        var opened = 0
        val unlocks = mutableListOf<() -> Unit>()
        var lockAction: () -> Boolean = { locked }
        var unlockAction: ((() -> Unit)) -> Unit = { unlocks += it }
        var openAction: () -> Unit = {}
        override fun isLocked(): Boolean { lockChecks++; return lockAction() }
        override fun unlockAndRun(action: () -> Unit) = unlockAction(action)
        override fun openHelp() { opened++; openAction() }
    }
}
