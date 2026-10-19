package com.example.citizensecurity.help

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.account.ReportWorkspaceActivityBinding
import com.example.citizensecurity.data.SqliteAccountRepository
import com.example.citizensecurity.data.TrustedContactsStore
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.util.UUID
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real account and private contact store; editing must survive configuration without a write. */
@RunWith(AndroidJUnit4::class)
class HelpContactsRecoveryUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private var accounts: SqliteAccountRepository? = null
    private var scenario: ActivityScenario<HelpContactsActivity>? = null

    @Before fun signInToAnOwnedAccount() {
        assertTrue("Use only the owned disposable emulator", Build.HARDWARE in listOf("ranchu", "goldfish"))
        accounts = SqliteAccountRepository(app, "contacts_recovery_${UUID.randomUUID()}.db")
        app.accountTestRepository = requireNotNull(accounts)
        val account = runBlocking { requireNotNull(accounts).register("contacto_giro", UUID.randomUUID().toString()) }
        instrumentation.runOnMainSync { app.accountSession.signIn(account) }
    }

    @After fun closeOnlyOwnedActivityAndSession() {
        scenario?.close()
        instrumentation.runOnMainSync { app.accountSession.signOut() }
        app.accountTestRepository = null
        accounts?.close()
        instrumentation.waitForIdleSync()
    }

    @Test fun rotatingTheRealContactScreenPreservesUnsubmittedFieldsWithoutSavingOrGps() {
        val session = app.accountSession.capture()
        val repository = app.openTrustedContactsRepository(session) {}
        val before = runBlocking { repository.read() }
        scenario = ActivityScenario.launch(ReportWorkspaceActivityBinding.stamp(
            Intent(app, HelpContactsActivity::class.java), session,
        ))
        await("help_contacts_empty")
        ui.onNodeWithTag("help_contact_name").performScrollTo().performTextInput("Contacto pendiente del giro")
        ui.onNodeWithTag("help_contact_phone").performScrollTo().performTextInput("+52 (55) 1234-5678")
        closeSoftKeyboard()
        var target = Configuration.ORIENTATION_UNDEFINED
        scenario!!.onActivity {
            val portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            target = if (portrait) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            it.requestedOrientation = if (portrait) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        ui.waitUntil(15_000) {
            var oriented = false
            scenario!!.onActivity { oriented = it.resources.configuration.orientation == target }
            oriented
        }
        await("help_contact_name")
        assertField("help_contact_name", "Contacto pendiente del giro")
        assertField("help_contact_phone", "+52 (55) 1234-5678")
        val after = runBlocking { repository.read() }
        assertEquals(before.revision, after.revision)
        assertEquals(before.contacts, after.contacts)
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun savingThenChangingAccountClearsPrivatePreparationWithoutWritingToEitherWrongSpace() {
        val firstSession = app.accountSession.capture()
        val firstRepository = app.openTrustedContactsRepository(firstSession) {}
        scenario = ActivityScenario.launch(ReportWorkspaceActivityBinding.stamp(
            Intent(app, HelpContactsActivity::class.java), firstSession,
        ))
        await("help_contacts_empty")
        ui.onNodeWithTag("help_contact_name").performScrollTo().performTextInput("Contacto guardado de la primera cuenta")
        ui.onNodeWithTag("help_contact_phone").performScrollTo().performTextInput("+52 (55) 1234-5678")
        closeSoftKeyboard()
        ui.onNodeWithTag("help_contact_save").performScrollTo().performClick()
        ui.waitUntil(15_000) { runBlocking { firstRepository.read().contacts.size == 1 } }
        await("help_contact_name")
        assertField("help_contact_name", "")
        assertField("help_contact_phone", "")
        val saved = runBlocking { firstRepository.read() }
        assertEquals("+525512345678", saved.contacts.single().phoneNumber)
        val firstFile = File(fixtureRule.fixture.contactsContext.filesDir,
            TrustedContactsStore.fileName(requireNotNull(firstSession.account).id))
        val savedBytes = firstFile.readBytes()

        ui.onNodeWithTag("help_contact_name").performScrollTo().performTextReplacement("Preparación privada sin guardar")
        ui.onNodeWithTag("help_contact_phone").performScrollTo().performTextReplacement("5523456789")
        closeSoftKeyboard()
        val next = runBlocking { requireNotNull(accounts).register("contacto_otro", UUID.randomUUID().toString()) }
        instrumentation.runOnMainSync { app.accountSession.signIn(next) }
        scenario!!.close()
        val nextSession = app.accountSession.capture()
        val nextRepository = app.openTrustedContactsRepository(nextSession) {}
        scenario = ActivityScenario.launch(ReportWorkspaceActivityBinding.stamp(
            Intent(app, HelpContactsActivity::class.java), nextSession,
        ))
        await("help_contacts_empty")
        assertField("help_contact_name", "")
        assertField("help_contact_phone", "")
        assertTrue(runBlocking { nextRepository.read().contacts.isEmpty() })
        assertArrayEquals(savedBytes, firstFile.readBytes())
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    private fun await(tag: String) {
        ui.waitUntil(15_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }

    private fun assertField(tag: String, value: String) {
        ui.onNodeWithTag(tag).performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)),
        )
    }
}
