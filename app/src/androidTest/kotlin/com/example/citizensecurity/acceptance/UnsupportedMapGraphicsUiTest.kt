package com.example.citizensecurity.acceptance

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.citizensecurity.maps.MapsConfiguration
import com.example.citizensecurity.maps.ReportMapSceneState
import com.example.citizensecurity.preview.ReportDraftActivity
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import com.example.citizensecurity.reports.ReportsMapActivity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapView

/** Opt-in acceptance on the owned API 26 AVD genuinely reporting GLES 2; no capability override. */
@RunWith(AndroidJUnit4::class)
class UnsupportedMapGraphicsUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private var draft: ActivityScenario<ReportDraftActivity>? = null
    private var reportsMap: ActivityScenario<ReportsMapActivity>? = null

    @Before fun requireOwnedRealGles2Emulator() {
        val nonce = InstrumentationRegistry.getArguments().getString("unsupportedMapGraphicsNonce")
        assumeTrue("Requires explicit unsupportedMapGraphicsNonce", !nonce.isNullOrEmpty())
        require(UUID.fromString(nonce).toString() == nonce)
        require(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Only the owned isolated emulator is supported" }
        val version = requireNotNull(app.getSystemService(ActivityManager::class.java)).deviceConfigurationInfo.reqGlEsVersion
        assumeTrue("Requires an API 26 AVD actually reporting GLES 2", Build.VERSION.SDK_INT == 26 && version == 0x00020000)
        instrumentation.runOnMainSync { app.accountSession.signOut() }
        println("UNSUPPORTED_MAP_GRAPHICS nonce=$nonce api=${Build.VERSION.SDK_INT} gles=${version.toString(16)}")
    }

    @After fun closeOnlyOwnedWindows() {
        draft?.close()
        reportsMap?.close()
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filter { it.packageName == app.packageName }.forEach(Activity::finish)
            app.accountSession.signOut()
        }
        instrumentation.waitForIdleSync()
    }

    @Test fun unsupportedSelectionExplainsGraphicsAndReturnsToIntactFormWithoutGpsOrWrites() {
        draft = ActivityScenario.launch(Intent(app, ReportDraftActivity::class.java))
        await("draft_description")
        ui.onNodeWithTag("draft_description").performScrollTo().performTextInput(DESCRIPTION)
        ui.onNodeWithTag("draft_reference").performScrollTo().performTextInput(REFERENCE)
        closeSoftKeyboard()
        ui.onNodeWithTag("open_incident_map").performScrollTo().performClick()
        await("incident_map_unavailable")
        ui.onNodeWithTag("incident_map_unavailable").assertTextEquals(MapsConfiguration.UNSUPPORTED_GRAPHICS_MESSAGE)
        ui.onNodeWithTag("incident_map").assertDoesNotExist()
        ui.onNodeWithTag("locate_phone").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("confirm_point").performScrollTo().assertIsNotEnabled()
        draft!!.onActivity {
            assertFalse(it.previewMapReady)
            assertFalse(containsMapView(it.window.decorView))
        }
        assertNoGpsOrWrites()
        ui.onNodeWithTag("cancel_map").performScrollTo().performClick()
        await("draft_description")
        draft!!.onActivity {
            val state = it.draftModel.state.value
            assertEquals(DESCRIPTION, state.description)
            assertEquals(REFERENCE, state.location.reference)
            assertNull(state.location.latitude)
            assertNull(state.location.longitude)
            assertFalse(state.locationSelectionPending)
        }
        assertNoGpsOrWrites()
    }

    @Test fun unsupportedReportsMapKeepsQueryDisabledAndStillOpensTheStoredReportList() {
        reportsMap = ActivityScenario.launch(Intent(app, ReportsMapActivity::class.java))
        await("reports_map_unavailable")
        ui.onNodeWithTag("reports_map_unavailable").assertTextEquals(MapsConfiguration.UNSUPPORTED_GRAPHICS_MESSAGE)
        ui.onNodeWithTag("reports_map_canvas").assertDoesNotExist()
        ui.onNodeWithTag("query_reports_zone").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("open_map_zone_list").performScrollTo().assertIsNotEnabled()
        reportsMap!!.onActivity {
            assertFalse(it.reportsMapReady)
            assertEquals(ReportMapSceneState.Idle, it.currentSceneState)
            assertFalse(containsMapView(it.window.decorView))
        }
        assertNoGpsOrWrites()
        ui.onNodeWithTag("open_map_report_list").performScrollTo().performClick()
        await("report_no_data")
        assertNoGpsOrWrites()
    }

    private fun await(tag: String) {
        ui.waitUntil(15_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun assertNoGpsOrWrites() = runBlocking {
        assertEquals(0, fixtureRule.fixture.refreshCalls)
        assertTrue(fixtureRule.fixture.repository.list().isEmpty())
        assertNull(fixtureRule.fixture.journal.read())
    }

    private fun containsMapView(view: View): Boolean = view is MapView ||
        (view is ViewGroup && (0 until view.childCount).any { containsMapView(view.getChildAt(it)) })

    private companion object {
        const val DESCRIPTION = "Preparación local para comprobar gráficos incompatibles sin un incidente real."
        const val REFERENCE = "Referencia conservada al volver del mapa no disponible"
    }
}
