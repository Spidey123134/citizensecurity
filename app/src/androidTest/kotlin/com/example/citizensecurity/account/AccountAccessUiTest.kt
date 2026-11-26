package com.example.citizensecurity.account

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.MainActivity
import com.example.citizensecurity.R
import com.example.citizensecurity.data.SqliteAccountRepository
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixture
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Compose routes exercise the real account store and the isolated report store. */
@RunWith(AndroidJUnit4::class)
class AccountAccessUiTest {
    @get:Rule val ui = createEmptyComposeRule()

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
        app.accountSession.signOut()
        app.accountTestRepository = null
        repository.close()
        app.disableFixture(reports)
    }

    @Test fun createAccountReturnsToLoginThenRealLoginAndLogoutPreserveSharedStores() {
        openLogin()
        ui.onNodeWithTag("create_account_link").performScrollTo().performClick()
        awaitTag("login_link")
        awaitTag("user")
        ui.onNodeWithTag("user").performScrollTo().performTextReplacement("  Alumna_1  ")
        enterPassword()
        pressSubmitWithKeyboard()
        awaitTag("create_account_link")
        awaitTag("user")
        awaitText(R.string.account_created)
        assertNull(app.accountSession.account)
        assertField("user", "alumna_1")
        assertField("pass", "")

        enterPassword()
        pressSubmitWithKeyboard()
        awaitTag("home_account")
        assertEquals("alumna_1", app.accountSession.account?.username)
        ui.onNodeWithTag("home_account").assertTextContains("alumna_1", substring = true)
        ui.onNodeWithTag("tools_back").performScrollTo()
            .assertTextContains(app.getString(R.string.account_sign_out)).performClick()
        awaitTag("released_tools_entry")
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
        val login = openLogin()
        ui.onNodeWithTag("user").performTextReplacement("alumna_1")
        enterPassword()
        login.recreate()
        awaitTag("user")
        assertField("user", "alumna_1")
        assertField("pass", "")
        assertNull(app.accountSession.account)
    }

    @Test fun academicEntryNeverRetainsARealAccountIdentity() {
        val account = runBlocking { repository.register("alumna_1", password) }
        app.accountSession.signIn(account)
        openLogin()
        enterPassword()
        ui.onNodeWithTag("released_tools_entry").performScrollTo().assertIsDisplayed().performTouchInput { click() }
        awaitTag("home_account")
        assertNull(app.accountSession.account)
        ui.onNodeWithTag("home_account").assertTextContains(app.getString(R.string.account_home_guest))
        assertEquals(0, reports.refreshCalls)
    }

    private fun openLogin(): ActivityScenario<MainActivity> {
        val login = ActivityScenario.launch(MainActivity::class.java)
        scenario = login
        awaitTag("create_account_link")
        return login
    }

    private fun enterPassword() {
        ui.onNodeWithTag("pass").performScrollTo().performTouchInput { click() }.performTextReplacement(password)
        awaitKeyboardViewport()
    }

    private fun pressSubmitWithKeyboard() {
        ui.onNodeWithTag("login_button").performScrollTo().assertIsDisplayed().performTouchInput { click() }
    }

    private fun awaitKeyboardViewport() {
        ui.waitUntil(timeoutMillis = 10_000) {
            var keyboardTop = 0
            checkNotNull(scenario).onActivity { activity ->
                val decor = activity.window.decorView
                val insets = ViewCompat.getRootWindowInsets(decor)
                if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                    keyboardTop = decor.height - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                }
            }
            keyboardTop > 0 &&
                ui.onNodeWithTag("account_scroll").fetchSemanticsNode().boundsInWindow.bottom <= keyboardTop + 1
        }
    }

    private fun awaitTag(tag: String) {
        ui.waitUntil(timeoutMillis = 20_000) {
            ui.onAllNodesWithTag(tag).fetchSemanticsNodes().size == 1
        }
    }

    private fun awaitText(resource: Int) {
        val text = app.getString(resource)
        ui.waitUntil(timeoutMillis = 20_000) {
            ui.onAllNodesWithText(text).fetchSemanticsNodes().size == 1
        }
    }

    private fun assertField(tag: String, expected: String) {
        ui.onNodeWithTag(tag).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)),
        )
    }
}
