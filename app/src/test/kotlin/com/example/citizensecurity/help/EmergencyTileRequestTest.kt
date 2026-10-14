package com.example.citizensecurity.help

import org.junit.Assert.*
import org.junit.Test

class EmergencyTileRequestTest {
    @Test fun staleGestureDoesNotConsultThePlatform() {
        val platform = Platform(); val results = mutableListOf<TileAddResult>()
        EmergencyTileRequest.request(platform, { false }, results::add)
        assertEquals(listOf(TileAddResult.NOT_CURRENT), results); assertEquals(0, platform.checks)
    }
    @Test fun lockedDeviceNeverRequestsTheTile() {
        val platform = Platform().apply { locked = true }; val results = mutableListOf<TileAddResult>()
        EmergencyTileRequest.request(platform, { true }, results::add)
        assertEquals(listOf(TileAddResult.LOCKED), results); assertEquals(0, platform.requests)
    }
    @Test fun olderDeviceReportsManualAdditionWithoutCallingTheApi() {
        val platform = Platform().apply { supported = false }; val results = mutableListOf<TileAddResult>()
        EmergencyTileRequest.request(platform, { true }, results::add)
        assertEquals(listOf(TileAddResult.UNSUPPORTED), results); assertEquals(0, platform.requests)
    }
    @Test fun gestureLostDuringSupportCheckDoesNotSendARequest() {
        var current = true
        val platform = Platform().apply { onSupport = { current = false } }; val results = mutableListOf<TileAddResult>()
        EmergencyTileRequest.request(platform, { current }, results::add)
        assertEquals(listOf(TileAddResult.NOT_CURRENT), results); assertEquals(0, platform.requests)
    }
    @Test fun gestureLostDuringTheLastLockCheckDoesNotSendARequest() {
        var current = true
        val platform = Platform().apply { onLock = { if (checks == 2) current = false } }; val results = mutableListOf<TileAddResult>()
        EmergencyTileRequest.request(platform, { current }, results::add)
        assertEquals(listOf(TileAddResult.NOT_CURRENT), results); assertEquals(0, platform.requests)
    }
    @Test fun requestDoesNotClaimAdditionBeforeTheSystemCallback() {
        val platform = Platform(); val results = mutableListOf<TileAddResult>()
        EmergencyTileRequest.request(platform, { true }, results::add)
        assertEquals(1, platform.requests); assertTrue(results.isEmpty())
        platform.callback!!(TileAddResult.DECLINED)
        assertEquals(listOf(TileAddResult.DECLINED), results)
    }
    @Test fun duplicateSystemRepliesAreConsumedOnlyOnce() {
        val platform = Platform(); val results = mutableListOf<TileAddResult>()
        EmergencyTileRequest.request(platform, { true }, results::add)
        platform.callback!!(TileAddResult.ADDED); platform.callback!!(TileAddResult.FAILED)
        assertEquals(listOf(TileAddResult.ADDED), results)
    }
    @Test fun receiverFailureReturnsOneFailureWithoutRetrying() {
        val platform = Platform().apply { fail = true }; val results = mutableListOf<TileAddResult>()
        EmergencyTileRequest.request(platform, { true }, results::add)
        assertEquals(listOf(TileAddResult.FAILED), results); assertEquals(1, platform.requests)
    }
    private class Platform : EmergencyTileAddPlatform {
        var locked = false; var supported = true; var checks = 0; var requests = 0; var fail = false
        var onSupport: () -> Unit = {}; var onLock: () -> Unit = {}
        var callback: ((TileAddResult) -> Unit)? = null
        override fun supported(): Boolean { onSupport(); return supported }
        override fun locked(): Boolean { checks++; onLock(); return locked }
        override fun request(result: (TileAddResult) -> Unit) { requests++; if (fail) error("Status bar unavailable"); callback = result }
    }
}
