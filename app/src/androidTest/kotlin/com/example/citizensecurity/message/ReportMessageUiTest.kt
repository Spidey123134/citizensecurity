package com.example.citizensecurity.message

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.citizensecurity.data.TrustedContactsStore
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.reports.ReportsActivity
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Real owned SQLite/AtomicFile data, with every ACTION_SENDTO intercepted before another app opens. */
class ReportMessageUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private val fixture get() = fixtureRule.fixture
    private var reportsRoute: ActivityScenario<ReportsActivity>? = null
    private var directRoute: ActivityScenario<ReportMessageActivity>? = null
    private val messages = ConcurrentLinkedQueue<Intent>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_SENDTO) return null
            messages.add(Intent(intent))
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    @Before fun captureAcademicLeaseAndInterceptMessages() {
        instrumentation.runOnMainSync { app.accountSession.signOut() }
        instrumentation.addMonitor(monitor)
    }

    @After fun closeOnlyOwnedActivitiesAndRemoveInterceptor() {
        directRoute?.close()
        reportsRoute?.close()
        instrumentation.runOnMainSync {
            listOf(Stage.RESUMED, Stage.STARTED, Stage.CREATED).flatMap {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it).toList()
            }.filter { it.packageName == app.packageName }.distinct().forEach(Activity::finish)
            app.accountSession.signOut()
        }
        instrumentation.removeMonitor(monitor)
        instrumentation.waitForIdleSync()
    }

    @Test fun savedDetailRequiresReviewedRecipientAndSecondGestureAndRotationNeverSends() {
        val report = seedReport()
        val contact = TrustedContact.create("Persona de confianza", "+52 (55) 1234-5678")
        val savedContacts = replaceContacts(listOf(contact))
        val bytes = contactsFile().readBytes()
        openFromDetail(report)
        awaitChoose()
        scrollTo("message_location")
        ui.onNodeWithTag("message_location").assertIsOff()
        scrollTo("message_prepare")
        ui.onNodeWithTag("message_prepare").assertIsNotEnabled()
        click("message_contact_${contact.id}")
        click("message_prepare")
        var review = awaitReview()
        assertEquals(ReportMessageFormatter.prepare(report, contact, zoneId = ZoneId.systemDefault()), review.draft)
        assertFalse(review.draft.body.contains(report.location.reference))
        assertFalse(review.draft.body.contains("19.4326"))
        assertFalse(review.draft.body.contains("-99.1332"))
        assertEquals(contact, review.draft.contact)
        ui.onNodeWithText(contact.name, substring = true).assertExists()
        ui.onNodeWithText(contact.phoneNumber, substring = true).assertExists()
        assertTrue(messages.isEmpty())
        val originalBody = review.draft.body

        recreateMessageScreen()
        review = awaitReview()
        assertEquals(originalBody, review.draft.body)
        assertTrue(messages.isEmpty())
        click("message_edit")
        awaitChoose()
        click("message_location")
        ui.onNodeWithTag("message_location").assertIsOn()
        click("message_prepare")
        review = awaitReview()
        assertEquals(ReportMessageFormatter.prepare(report, contact, includeLocation = true,
            zoneId = ZoneId.systemDefault()), review.draft)
        assertTrue(review.draft.body.contains(report.location.reference))
        assertTrue(review.draft.body.contains("19.4326"))
        assertTrue(review.draft.body.contains("-99.1332"))
        assertTrue(messages.isEmpty())
        val body = review.draft.body
        click("message_open")
        awaitTag("message_opened")
        assertEquals(1, messages.size)
        assertSafeSms(messages.single(), contact.phoneNumber, body)

        recreateMessageScreen()
        awaitTag("message_opened")
        assertEquals(1, messages.size)
        assertEquals(report, runBlocking { fixture.repository.findById(report.id) })
        assertEquals(savedContacts.revision, readContacts().revision)
        assertEquals(listOf(contact), readContacts().contacts)
        assertArrayEquals(bytes, contactsFile().readBytes())
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun editingTheSavedContactAfterReviewRequiresChoosingAndPreparingTheNewTarget() {
        val report = seedReport()
        val first = TrustedContact.create("Contacto antes", "5512345678")
        val snapshot = replaceContacts(listOf(first))
        openDirect(report)
        awaitChoose()
        click("message_contact_${first.id}")
        click("message_prepare")
        awaitReview()
        val updated = first.copy(name = "Contacto actualizado", phoneNumber = "5598765432")
        runBlocking { contactsRepository().replace(snapshot.revision, listOf(updated)) }

        click("message_open")
        val choice = awaitChoose()
        assertNull(choice.selectedContactId)
        assertEquals(listOf(updated), choice.contacts.contacts)
        assertTrue(messages.isEmpty())
        ui.onNodeWithTag("message_body").assertDoesNotExist()
        scrollTo("message_prepare")
        ui.onNodeWithTag("message_prepare").assertIsNotEnabled()
        click("message_contact_${updated.id}")
        click("message_prepare")
        val renewed = awaitReview()
        assertEquals(updated, renewed.draft.contact)
        click("message_open")
        awaitTag("message_opened")
        assertEquals(1, messages.size)
        assertSafeSms(messages.single(), updated.phoneNumber, renewed.draft.body)
        assertEquals(report, runBlocking { fixture.repository.findById(report.id) })
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun changingAccountRetiresTheReviewedScreenWithoutLeakingItsContactOrMessage() {
        val report = seedReport()
        val contact = TrustedContact.create("Contacto privado anterior", "5512345678")
        replaceContacts(listOf(contact))
        val before = contactsFile().readBytes()
        openDirect(report)
        awaitChoose()
        click("message_contact_${contact.id}")
        click("message_prepare")
        awaitReview()
        val old = currentMessageActivity()
        val second = LocalAccount(UUID.randomUUID().toString(), "mensajes_dos")
        instrumentation.runOnMainSync { app.accountSession.signIn(second) }
        ui.waitUntil(15_000) { old.isDestroyed }
        assertTrue(messages.isEmpty())
        assertArrayEquals(before, contactsFile(owner = null).readBytes())
        assertTrue(readContacts().contacts.isEmpty())
        openDirect(report)
        awaitTag("message_error")
        ui.onNodeWithTag("message_body").assertDoesNotExist()
        assertTrue(messages.isEmpty())
        assertEquals(report, runBlocking { fixture.repository.findById(report.id) })
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun emptyThenMalformedContactsNeverBecomeAWritableInventedListOrOpenAMessage() {
        val report = seedReport()
        val file = contactsFile()
        assertFalse(file.exists())
        openDirect(report)
        awaitChoose()
        awaitTag("message_empty_contacts")
        scrollTo("message_prepare")
        ui.onNodeWithTag("message_prepare").assertIsNotEnabled()
        assertFalse(file.exists())
        val malformed = "{\"version\":1,\"contacts\":[broken]}".toByteArray(Charsets.UTF_8)
        file.writeBytes(malformed)
        click("message_reload")
        awaitTag("message_error")
        assertArrayEquals(malformed, file.readBytes())
        click("message_reload")
        awaitTag("message_error")
        assertArrayEquals(malformed, file.readBytes())
        ui.onNodeWithTag("message_open").assertDoesNotExist()
        assertTrue(messages.isEmpty())
        assertEquals(report, runBlocking { fixture.repository.findById(report.id) })
        assertEquals(0, fixture.refreshCalls)
    }

    @Test fun addingAContactInTheRealHelpScreenRefreshesTheMessageChoiceWithoutPreparingOrOpening() {
        val report = seedReport()
        openDirect(report)
        awaitChoose()
        awaitTag("message_empty_contacts")
        click("message_contacts")
        awaitTag("help_contacts_empty")
        ui.onNodeWithTag("help_contact_name").performScrollTo().performTextInput("Contacto del formulario real")
        ui.onNodeWithTag("help_contact_phone").performScrollTo().performTextInput("+52 (55) 1234-5678")
        ui.onNodeWithTag("help_contact_save").performScrollTo().performClick()
        ui.waitUntil(20_000) { readContacts().contacts.size == 1 }
        val saved = readContacts()
        val contact = saved.contacts.single()
        assertEquals("Contacto del formulario real", contact.name)
        assertEquals("+525512345678", contact.phoneNumber)
        assertTrue(contactsFile().isFile)
        assertTrue(messages.isEmpty())
        ui.onNodeWithText("Volver", substring = false).performScrollTo().performClick()
        ui.waitUntil(20_000) {
            resumedMessageActivity() != null && (state() as? ReportMessageState.Choose)?.contacts?.revision == saved.revision
        }
        val refreshed = awaitChoose()
        assertEquals(saved.revision, refreshed.contacts.revision)
        assertEquals(listOf(contact), refreshed.contacts.contacts)
        assertNull(refreshed.selectedContactId)
        assertFalse(refreshed.includeLocation)
        scrollTo("message_contact_${contact.id}")
        ui.onNodeWithTag("message_contact_${contact.id}").assertIsNotSelected()
        scrollTo("message_prepare")
        ui.onNodeWithTag("message_prepare").assertIsNotEnabled()
        ui.onNodeWithTag("message_body").assertDoesNotExist()
        assertTrue(messages.isEmpty())
        assertEquals(report, runBlocking { fixture.repository.findById(report.id) })
        assertEquals(0, fixture.refreshCalls)
    }

    private fun openFromDetail(report: Report) {
        reportsRoute = ActivityScenario.launch(Intent(app, ReportsActivity::class.java)
            .putExtra(ReportsActivity.EXTRA_FOLIO, report.id))
        awaitTag("report_detail_message")
        ui.onNodeWithTag("report_detail_message").performScrollTo().performClick()
        awaitTag("message_screen")
    }
    private fun openDirect(report: Report) {
        directRoute = ActivityScenario.launch(Intent(app, ReportMessageActivity::class.java)
            .putExtra(ReportsActivity.EXTRA_FOLIO, report.id))
        awaitTag("message_screen")
    }
    private fun recreateMessageScreen() {
        val old = currentMessageActivity()
        instrumentation.runOnMainSync { old.recreate() }
        ui.waitUntil(15_000) { old.isDestroyed && resumedMessageActivity()?.let { it !== old } == true }
    }
    private fun resumedMessageActivity(): ReportMessageActivity? {
        val result = AtomicReference<ReportMessageActivity?>()
        instrumentation.runOnMainSync {
            result.set(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<ReportMessageActivity>().singleOrNull())
        }
        return result.get()
    }
    private fun currentMessageActivity(): ReportMessageActivity = checkNotNull(resumedMessageActivity())
    private fun state(): ReportMessageState {
        val activity = currentMessageActivity()
        val result = AtomicReference<ReportMessageState>()
        instrumentation.runOnMainSync {
            result.set(ViewModelProvider(activity)[ReportMessageViewModel::class.java].state.value)
        }
        return result.get()
    }
    private fun awaitChoose(): ReportMessageState.Choose {
        ui.waitUntil(20_000) { resumedMessageActivity() != null && state() is ReportMessageState.Choose }
        return state() as ReportMessageState.Choose
    }
    private fun awaitReview(): ReportMessageState.Review {
        ui.waitUntil(20_000) { resumedMessageActivity() != null && state() is ReportMessageState.Review }
        awaitTag("message_body")
        val review = state() as ReportMessageState.Review
        scrollTo("message_body")
        ui.onNodeWithTag("message_body").assertTextEquals(review.draft.body)
        return review
    }
    private fun awaitTag(tag: String) = ui.waitUntil(20_000) {
        ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    private fun scrollTo(tag: String) = ui.onNodeWithTag("message_screen").performScrollToNode(hasTestTag(tag))
    private fun click(tag: String) { scrollTo(tag); ui.onNodeWithTag(tag).performClick() }
    private fun contactsRepository(): TrustedContactsRepository = app.openTrustedContactsRepository(app.accountSession.capture()) {}
    private fun readContacts() = runBlocking { contactsRepository().read() }
    private fun replaceContacts(contacts: List<TrustedContact>) = runBlocking {
        val repository = contactsRepository()
        repository.replace(repository.read().revision, contacts)
    }
    private fun contactsFile(owner: String? = app.accountSession.account?.id): File {
        val parent = fixture.contactsContext.filesDir.canonicalFile
        return File(parent, TrustedContactsStore.fileName(owner)).canonicalFile.also {
            require(it.parentFile == parent)
        }
    }
    private fun assertSafeSms(intent: Intent, target: String, body: String) {
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals(Uri.fromParts("smsto", target, null), intent.data)
        assertEquals(body, intent.getStringExtra("sms_body"))
        assertEquals(setOf("sms_body"), intent.extras!!.keySet())
        assertNull(intent.component)
        assertNull(intent.clipData)
        assertNull(intent.type)
        assertEquals(0, intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
    }
    private fun seedReport(): Report = runBlocking {
        fixture.publishFreshFix()
        fixture.repository.createOnce(UUID.randomUUID().toString(), NewReport(IncidentType.RISK, Priority.MEDIUM,
            "Luminaria dañada frente al parque. Evitar pasar por la banqueta.", Instant.now().minusSeconds(300),
            ReportLocation(reference = "Frente al parque de esta prueba", latitude = 19.4326, longitude = -99.1332)))
    }
}
