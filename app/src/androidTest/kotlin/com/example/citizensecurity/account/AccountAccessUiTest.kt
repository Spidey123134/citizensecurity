package com.example.citizensecurity.account

import android.app.Activity
import android.os.SystemClock
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.citizensecurity.MainActivity
import com.example.citizensecurity.R
import com.example.citizensecurity.data.SqliteAccountRepository
import com.example.citizensecurity.release.ReleasedToolsActivity
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixture
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real isolated accounts/report stores; compiled now, execution belongs to grouped 0.6.0. */
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
        withResumed<MainActivity> { it.findViewById<TextView>(R.id.create_account_link).performClick() }
        awaitActivity<CreateAccountActivity>()
        withResumed<CreateAccountActivity> {
            it.findViewById<EditText>(R.id.user).setText("  Alumna_1  ")
            it.findViewById<EditText>(R.id.pass).setText(password)
            it.findViewById<Button>(R.id.login_button).performClick()
        }
        awaitActivity<MainActivity>()
        assertNull(app.accountSession.account)
        withResumed<MainActivity> {
            assertEquals("alumna_1", it.findViewById<EditText>(R.id.user).text.toString())
            assertEquals("", it.findViewById<EditText>(R.id.pass).text.toString())
            assertEquals(it.getString(R.string.account_created), it.findViewById<TextView>(R.id.account_status).text.toString())
            it.findViewById<EditText>(R.id.pass).setText(password)
            it.findViewById<Button>(R.id.login_button).performClick()
        }
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
        withResumed<MainActivity> { it.findViewById<Button>(R.id.released_tools_entry).performClick() }
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
}
