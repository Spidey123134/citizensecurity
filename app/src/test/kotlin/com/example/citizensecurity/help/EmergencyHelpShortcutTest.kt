package com.example.citizensecurity.help

import org.junit.Assert.*
import org.junit.Test

class EmergencyHelpShortcutTest {
    @Test fun staleGestureDoesNotEvenConsultThePlatform() {
        val platform = Platform()
        assertEquals(EmergencyPinResult.NOT_CURRENT, EmergencyHelpShortcut.requestPin(platform) { false })
        assertEquals(0, platform.checks)
        assertEquals(0, platform.requests)
    }
    @Test fun lockedPhoneDoesNotAskTheLauncher() {
        val platform = Platform(locked = true)
        assertEquals(EmergencyPinResult.LOCKED, EmergencyHelpShortcut.requestPin(platform) { true })
        assertEquals(0, platform.requests)
        assertEquals(0, platform.supportChecks)
    }
    @Test fun unsupportedLauncherHasAFallbackWithoutSendingARequest() {
        val platform = Platform(supported = false)
        assertEquals(EmergencyPinResult.UNSUPPORTED, EmergencyHelpShortcut.requestPin(platform) { true })
        assertEquals(0, platform.requests)
    }
    @Test fun gestureInvalidatedAfterCheckingSupportCannotPin() {
        val platform = Platform()
        var gestures = 0
        assertEquals(EmergencyPinResult.NOT_CURRENT, EmergencyHelpShortcut.requestPin(platform) { ++gestures == 1 })
        assertEquals(0, platform.requests)
    }
    @Test fun phoneLockedAfterTheFirstCheckCannotPin() {
        val platform = Platform().apply { lockOnSecondCheck = true }
        assertEquals(EmergencyPinResult.LOCKED, EmergencyHelpShortcut.requestPin(platform) { true })
        assertEquals(0, platform.requests)
    }
    @Test fun gestureInvalidatedDuringTheLastLockCheckCannotPin() {
        var current = true
        val platform = Platform().apply { onLockCheck = { if (checks == 2) current = false } }
        assertEquals(EmergencyPinResult.NOT_CURRENT, EmergencyHelpShortcut.requestPin(platform) { current })
        assertEquals(2, platform.checks)
        assertEquals(0, platform.requests)
    }
    @Test fun acceptedRequestIsOnlyRequestedAndDoesNotClaimAnInstalledShortcut() {
        val platform = Platform()
        assertEquals(EmergencyPinResult.REQUESTED, EmergencyHelpShortcut.requestPin(platform) { true })
        assertEquals(1, platform.requests)
    }
    @Test fun rejectedRequestIsAnExplicitFailure() {
        val platform = Platform(accepted = false)
        assertEquals(EmergencyPinResult.FAILED, EmergencyHelpShortcut.requestPin(platform) { true })
        assertEquals(1, platform.requests)
    }
    @Test fun platformErrorsAreExplainedWithoutAnotherAttempt() {
        val platform = Platform().apply { fail = true }
        assertEquals(EmergencyPinResult.FAILED, EmergencyHelpShortcut.requestPin(platform) { true })
        assertEquals(1, platform.requests)
    }
    private class Platform(val locked: Boolean = false, val supported: Boolean = true, val accepted: Boolean = true) : EmergencyPinPlatform {
        var checks = 0; var supportChecks = 0; var requests = 0
        var lockOnSecondCheck = false; var fail = false
        var onLockCheck: () -> Unit = {}
        override fun isLocked(): Boolean { checks++; onLockCheck(); return locked || (lockOnSecondCheck && checks > 1) }
        override fun isSupported(): Boolean { supportChecks++; return supported }
        override fun request(): Boolean { requests++; if (fail) error("Launcher stopped"); return accepted }
    }
}
