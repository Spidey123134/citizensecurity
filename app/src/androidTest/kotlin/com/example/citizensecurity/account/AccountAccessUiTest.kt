package com.example.citizensecurity.account

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.citizensecurity.MainActivity
import com.example.citizensecurity.R
import com.example.citizensecurity.data.SqliteAccountRepository
import com.example.citizensecurity.release.ReleasedToolsActivity
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixture
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real isolated accounts/report stores; real view gestures also cover the nested, scrollable form. */
@RunWith(AndroidJUnit4::class)
class AccountAccessUiTest {
    private lateinit var app: ReportTestApplication
    private lateinit var repository: SqliteAccountRepository
    private lateinit var reports: ReportTestFixture
    private var scenario: ActivityScenario<MainActivity>? = null
    private val password = "Una frase completa para pruebas"

    @Before fun isolatedStores() {
        app = ApplicationProvider.getApplicationContext()
        repository = SqliteAccountRepository(app, "accounts_ui_${UUID.randomUUID()}.db")
        app.accountTestRepository = repository
        app.accountSession.signOut()
        reports = app.enableFixture()
    }

    @After fun closeOwnedStoresAndActivities() {
        scenario?.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filter { it.packageName == app.packageName }.forEach(Activity::finish)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        app.accountSession.signOut()
        app.accountTestRepository = null
        repository.close()
        app.disableFixture(reports)
    }

    @Test fun createAccountReturnsToLoginThenRealLoginAndLogoutPreserveSharedStores() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.create_account_link)).perform(scrollTo(), click())
        awaitActivity<CreateAccountActivity>()
        onView(withId(R.id.user)).perform(scrollTo(), click(), replaceText("  Alumna_1  "))
        onView(withId(R.id.pass)).perform(scrollTo(), click(), replaceText(password))
        awaitKeyboard<CreateAccountActivity>()
        onView(withId(R.id.login_button)).perform(scrollTo(), click())
        awaitActivity<MainActivity>()
        assertNull(app.accountSession.account)
        withResumed<MainActivity> {
            assertEquals("alumna_1", it.findViewById<EditText>(R.id.user).text.toString())
            assertEquals("", it.findViewById<EditText>(R.id.pass).text.toString())
            assertEquals(it.getString(R.string.account_created), it.findViewById<TextView>(R.id.account_status).text.toString())
        }
        onView(withId(R.id.pass)).perform(scrollTo(), click(), replaceText(password))
        awaitKeyboard<MainActivity>()
        onView(withId(R.id.login_button)).perform(scrollTo(), click())
        awaitActivity<ReleasedToolsActivity>()
        assertEquals("alumna_1", app.accountSession.account?.username)
        withResumed<ReleasedToolsActivity> {
            assertTrue(it.findViewById<TextView>(R.id.home_account).text.contains("alumna_1"))
            assertEquals(it.getString(R.string.account_sign_out), it.findViewById<Button>(R.id.tools_back).text.toString())
            it.findViewById<Button>(R.id.tools_back).performClick()
        }
        awaitActivity<MainActivity>()
        assertNull(app.accountSession.account)
        runBlocking {
            assertNotNull(repository.authenticate("ALUMNA_1", password))
            assertTrue(reports.repository.list().isEmpty())
            assertNull(reports.journal.read())
            assertNull(reports.checkpoint.read())
        }
        assertEquals(0, reports.refreshCalls)
    }

    @Test fun rotationRestoresUsernameButNeverTheUnsubmittedPassword() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity {
            it.findViewById<EditText>(R.id.user).setText("alumna_1")
            it.findViewById<EditText>(R.id.pass).setText(password)
        }
        scenario!!.recreate()
        scenario!!.onActivity {
            assertEquals("alumna_1", it.findViewById<EditText>(R.id.user).text.toString())
            val field = it.findViewById<EditText>(R.id.pass)
            assertEquals("", field.text.toString())
            assertFalse(field.isSaveEnabled)
            assertFalse(field.isSaveFromParentEnabled)
        }
        assertNull(app.accountSession.account)
    }

    @Test fun academicEntryNeverRetainsARealAccountIdentity() {
        val account = runBlocking { repository.register("alumna_1", password) }
        app.accountSession.signIn(account)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        onView(withId(R.id.pass)).perform(scrollTo(), click(), replaceText(password))
        awaitKeyboard<MainActivity>()
        try {
            onView(withId(R.id.released_tools_entry)).perform(scrollTo(), click())
        } catch (failure: Throwable) {
            recordScrollFailure()
            throw failure
        }
        awaitActivity<ReleasedToolsActivity>()
        assertNull(app.accountSession.account)
        withResumed<ReleasedToolsActivity> {
            assertEquals(it.getString(R.string.account_home_guest), it.findViewById<TextView>(R.id.home_account).text.toString())
        }
        assertEquals(0, reports.refreshCalls)
    }

    private inline fun <reified T : Activity> withResumed(crossinline block: (T) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<T>().single()
            block(activity)
        }
    }

    private inline fun <reified T : Activity> awaitActivity() {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            var ready = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ready = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).any { it is T }
            }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail("Expected activity ${T::class.java.simpleName}")
    }

    private inline fun <reified T : Activity> awaitKeyboard() {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var previousGeometry: String? = null
        var stableSince = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() < deadline) {
            var visible = false
            var geometry = ""
            var viewportBottom = 0
            var keyboardTop = 0
            withResumed<T> { activity ->
                val root = activity.findViewById<View>(R.id.account_scroll)
                val insets = ViewCompat.getRootWindowInsets(root)
                visible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
                geometry = "${root.width}:${root.height}:${root.paddingTop}:${root.paddingBottom}:" +
                    "${root.scrollY}:${insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom}"
                val location = IntArray(2)
                root.getLocationInWindow(location)
                viewportBottom = location[1] + root.height
                keyboardTop = activity.window.decorView.height -
                    (insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0)
            }
            // isVisible becomes true before the keyboard's viewport animation has finished.
            // A real scroll/click must use its settled bounds, not that intermediate frame.
            if (!visible || geometry != previousGeometry) stableSince = SystemClock.elapsedRealtime()
            if (visible && geometry == previousGeometry && SystemClock.elapsedRealtime() - stableSince >= 300) {
                assertTrue("The scroll viewport ($viewportBottom) extends below the keyboard ($keyboardTop)",
                    viewportBottom <= keyboardTop)
                return
            }
            previousGeometry = if (visible) geometry else null
            SystemClock.sleep(50)
        }
        fail("The keyboard did not reach a stable visible viewport for ${T::class.java.simpleName}")
    }

    private fun recordScrollFailure() {
        withResumed<MainActivity> { activity ->
            val scroll = activity.findViewById<View>(R.id.account_scroll)
            val entry = activity.findViewById<View>(R.id.released_tools_entry)
            val rootBounds = Rect()
            val entryBounds = Rect()
            scroll.getGlobalVisibleRect(rootBounds)
            entry.getGlobalVisibleRect(entryBounds)
            val insets = ViewCompat.getRootWindowInsets(scroll)
            Log.e("AccountAccessUi", "Academic scroll failure: viewport=$rootBounds " +
                "padding=${scroll.paddingTop},${scroll.paddingBottom} scrollY=${scroll.scrollY} " +
                "entry=$entryBounds entryHeight=${entry.height} " +
                "imeVisible=${insets?.isVisible(WindowInsetsCompat.Type.ime())} " +
                "ime=${insets?.getInsets(WindowInsetsCompat.Type.ime())}")
        }
        runCatching {
            val capture = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return@runCatching
            try {
                File(app.cacheDir, "release063-account-scroll-failure.png").outputStream().use {
                    capture.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { capture.recycle() }
        }
    }
}
