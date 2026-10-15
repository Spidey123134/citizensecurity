package com.example.citizensecurity.alerts

import org.junit.Assert.*
import org.junit.Test

class ReportAlertsPermissionChoiceTest {
    @Test fun callbackWithoutLiveGestureOrInNewProcessCannotEnablePreferences() {
        assertFalse(ReportAlertsPermissionChoice().complete(true))
        val old = ReportAlertsPermissionChoice()
        assertTrue(old.begin())
        assertFalse(ReportAlertsPermissionChoice().complete(true))
    }
    @Test fun pendingGestureAndResultCannotBeDuplicated() {
        val choice = ReportAlertsPermissionChoice()
        assertTrue(choice.begin()); assertFalse(choice.begin())
        assertTrue(choice.complete(false)); assertFalse(choice.complete(true))
        val result = choice.state.value!!
        assertEquals(false, result.granted)
        assertTrue(choice.consume(result)); assertFalse(choice.consume(result))
        assertNull(choice.state.value)
    }
    @Test fun previousResultCannotConsumeAReplacementGesture() {
        val choice = ReportAlertsPermissionChoice()
        choice.begin(); choice.complete(true)
        val old = choice.state.value!!
        choice.abort(); choice.begin(); choice.complete(false)
        assertFalse(choice.consume(old))
        assertEquals(false, choice.state.value!!.granted)
        assertTrue(choice.consume(choice.state.value!!))
    }
    @Test fun aPendingChoiceSurvivesTheViewModelWithoutPretendingItAlreadyHasAResult() {
        val choice = ReportAlertsPermissionChoice()
        choice.begin()
        val pending = choice.state.value!!
        assertFalse(choice.consume(pending))
        assertTrue(choice.complete(true))
        assertTrue(choice.consume(choice.state.value!!))
    }
}
