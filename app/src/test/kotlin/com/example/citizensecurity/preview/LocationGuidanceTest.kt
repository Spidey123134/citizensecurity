package com.example.citizensecurity.preview

import com.example.citizensecurity.maps.*
import org.junit.Assert.*
import org.junit.Test

class LocationGuidanceTest {
    @Test fun approximatePermissionExplainsWhyItCannotConfirm() {
        val text = permissionGuidance(LocationPermissionState(access = LocationPermissionAccess.Approximate))
        assertTrue(text.contains("no basta")); assertTrue(text.contains("5 km")); assertTrue(text.contains("Precisa"))
    }
    @Test fun deniedDoesNotClaimPermanentDenial() {
        val text = permissionGuidance(LocationPermissionState(access = LocationPermissionAccess.Denied))
        assertTrue(text.contains("si Android no muestra")); assertFalse(text.contains("permanente"))
    }
    @Test fun deniedWithRationaleKeepsTheDraft() {
        assertTrue(permissionGuidance(LocationPermissionState(access = LocationPermissionAccess.Denied, shouldExplain = true)).contains("este borrador"))
    }
    @Test fun precisePermissionStillRequiresAnExplicitGpsGesture() {
        assertTrue(permissionGuidance(LocationPermissionState(access = LocationPermissionAccess.Precise)).contains("no inicia el GPS"))
    }
    @Test fun inFlightTakesPrecedenceOverPreviousAccessAndErrors() {
        val text = permissionGuidance(LocationPermissionState(access = LocationPermissionAccess.Precise, requestInFlight = true, issue = LocationPermissionIssue.PermissionCheckFailed))
        assertTrue(text.contains("Responde")); assertTrue(text.contains("Todavía no"))
    }
    @Test fun failedInspectionDoesNotClaimPreciseAccess() {
        val text = permissionGuidance(LocationPermissionState(access = LocationPermissionAccess.Precise, issue = LocationPermissionIssue.PermissionCheckFailed))
        assertTrue(text.contains("No pudimos comprobar")); assertFalse(text.contains("disponible"))
    }
    @Test fun failedLaunchExplainsRetryAndManualSettings() {
        assertTrue(permissionGuidance(LocationPermissionState(issue = LocationPermissionIssue.RequestLaunchFailed)).contains("Ajustes"))
    }
    @Test fun cancelledPermissionKeepsTheDraft() {
        assertTrue(permissionGuidance(LocationPermissionState(issue = LocationPermissionIssue.Cancelled)).contains("tu borrador sigue"))
    }
    @Test fun unknownAccessDoesNotClaimPermission() {
        assertTrue(permissionGuidance(LocationPermissionState()).contains("Primero permite"))
    }
    @Test fun timeoutOffersSignalAdviceWithoutChangingTheGpsPolicy() {
        val text = gpsGuidance(IncidentLocationIssue.TimedOut)
        assertTrue(text.contains("ventana")); assertTrue(text.contains("se conserva")); assertFalse(text.contains("confirmar"))
    }
    @Test fun disabledLocationDoesNotPromiseAutomaticCaptureOnReturn() {
        assertTrue(gpsGuidance(IncidentLocationIssue.LocationDisabled).contains("no se reinicia automáticamente"))
    }
    @Test fun otherRejectionsKeepTheirOriginalReason() {
        assertEquals(IncidentLocationIssue.RefreshRequired.message, gpsGuidance(IncidentLocationIssue.RefreshRequired))
        assertEquals("Punto inválido", gpsGuidance(IncidentLocationIssue.InvalidPoint("Punto inválido")))
    }
}
