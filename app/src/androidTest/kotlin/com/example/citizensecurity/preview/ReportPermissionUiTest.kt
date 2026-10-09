package com.example.citizensecurity.preview

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.maps.IncidentLocationIssue
import com.example.citizensecurity.maps.IncidentLocationState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cada caso se ejecuta por separado, con permisos reales preparados en el AVD aislado. */
@RunWith(AndroidJUnit4::class)
class ReportPermissionUiTest {
    @get:Rule val ui = createAndroidComposeRule<ReportDraftActivity>()

    private fun openMap() {
        ui.onNodeWithTag("open_incident_map").performScrollTo().performClick()
        ui.onNodeWithTag("confirm_point").assertIsNotEnabled()
        ui.runOnIdle {
            assertFalse(ui.activity.locationModel.state.value is IncidentLocationState.Capturing)
            assertNull(ui.activity.draftModel.state.value.location.latitude)
        }
    }

    @Test fun deniedAccessExplainsPermissionWithoutStartingGps() {
        openMap()
        ui.onNodeWithTag("permission_guidance").assertTextContains("no tiene permiso", substring = true)
        ui.onNodeWithText("Permitir ubicación precisa").assertExists()
    }

    @Test fun approximateAccessCannotUseTheGpsAction() {
        openMap()
        ui.onNodeWithTag("permission_guidance").assertTextContains("aproximada no basta", substring = true)
        ui.onNodeWithText("Buscar mi ubicación").assertDoesNotExist()
        ui.onNodeWithText("Permitir ubicación precisa").assertExists()
    }

    @Test fun preciseAccessRequiresAnExplicitGpsGesture() {
        openMap()
        ui.onNodeWithTag("permission_guidance").assertTextContains("no inicia el GPS", substring = true)
        ui.onNodeWithText("Buscar mi ubicación").assertExists()
    }

    @Test fun disabledGpsExplainsRecoveryAndKeepsThePointUnconfirmed() {
        openMap()
        ui.onNodeWithTag("locate_phone").performScrollTo().performClick()
        ui.waitUntil(timeoutMillis = 10_000) {
            (ui.activity.locationModel.state.value as? IncidentLocationState.Error)?.issue == IncidentLocationIssue.LocationDisabled
        }
        ui.onNodeWithTag("gps_guidance").performScrollTo().assertTextContains("Activa Ubicación", substring = true)
        ui.onNodeWithTag("confirm_point").assertIsNotEnabled()
        ui.runOnIdle { assertNull(ui.activity.draftModel.state.value.location.latitude) }
        ui.onNodeWithTag("cancel_map").performScrollTo().performClick()
        ui.onNodeWithTag("open_incident_map").performScrollTo().assertExists()
    }
}
