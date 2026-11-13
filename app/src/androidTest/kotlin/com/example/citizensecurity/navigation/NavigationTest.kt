package com.example.citizensecurity.navigation

import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.MainActivity
import com.example.citizensecurity.R
import com.example.citizensecurity.screen.Home
import com.example.citizensecurity.screen.ReportDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @Test
    fun iniciarMuestraHomeVacioSinCrearBaseDeDatos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databasesBefore = context.databaseList().toSet()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val container = activity.findViewById<FrameLayout>(R.id.main_container)
                assertEquals(1, container.childCount)
                val home = container.getChildAt(0)
                assertTrue(home is Home)
                assertEquals(0, (home as Home).childCount)
            }
        }

        assertEquals(databasesBefore, context.databaseList().toSet())
    }

    @Test
    fun navegarExplicitamenteConservaPantallasVaciasSinCrearBaseDeDatos() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databasesBefore = context.databaseList().toSet()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val container = activity.findViewById<FrameLayout>(R.id.main_container)
                val navigation = Navigation(container)

                navigation.showReportDetails()

                assertEquals(1, container.childCount)
                val details = container.getChildAt(0)
                assertTrue(details is ReportDetails)
                assertEquals(0, (details as ReportDetails).childCount)

                navigation.showHome()

                assertEquals(1, container.childCount)
                val home = container.getChildAt(0)
                assertTrue(home is Home)
                assertEquals(0, (home as Home).childCount)
            }
        }

        assertEquals(databasesBefore, context.databaseList().toSet())
    }
}
