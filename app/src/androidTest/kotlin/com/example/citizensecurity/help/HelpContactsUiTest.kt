package com.example.citizensecurity.help

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.TrustedContact
import com.example.citizensecurity.domain.TrustedContactsRepository
import com.example.citizensecurity.domain.TrustedContactsSnapshot
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.reports.ReportsActivity
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The screen uses an owned in-memory contacts repository: no production contact file or dialer. */
class HelpContactsUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val modelStore = ViewModelStore()
    private var scenario: ActivityScenario<ReportsActivity>? = null
    private lateinit var model: HelpContactsViewModel
    private val emergencyTargets = CopyOnWriteArrayList<String>()
    private val contactTargets = CopyOnWriteArrayList<String>()

    @After fun closeOnlyOwnedScreenAndModel() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { modelStore.clear() }
        scenario?.close()
    }

    @Test fun addingManualContactShowsNormalizedNumberAndItsDialGestureUsesThatNumber() {
        val repository = Repository()
        open(repository)
        awaitTag("help_contacts_empty")
        ui.onNodeWithTag("help_contact_name").performScrollTo().performTextInput("Persona de confianza")
        ui.onNodeWithTag("help_contact_phone").performScrollTo().performTextInput("+52 (55) 1234-5678")
        ui.onNodeWithTag("help_contact_save").performScrollTo().performClick()
        awaitReadyContacts(1)
        val saved = repository.snapshot.contacts.single()
        ui.onNodeWithText("Persona de confianza").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("+525512345678").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("help_contact_dial_${saved.id}").performScrollTo().performClick()
        assertEquals(listOf("+525512345678"), contactTargets.toList())
        assertEquals(1, repository.writes.get())
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun emergencyDialGesturesRemainAvailableWhenTheContactsReadFails() {
        val repository = Repository().apply { readFailure = IOException("Owned fixture read failed") }
        open(repository)
        awaitTag("help_contacts_error")
        ui.onNodeWithTag("help_dial_911").performScrollTo().performClick()
        ui.onNodeWithTag("help_dial_089").performScrollTo().performClick()
        assertEquals(listOf("911", "089"), emergencyTargets.toList())
        assertEquals(0, repository.writes.get())
        assertTrue(contactTargets.isEmpty())
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun removingContactRequiresConfirmationAndCancelPreservesTheExactSnapshot() {
        val original = snapshot(listOf(TrustedContact.create("Contacto guardado", "5512345678")))
        val repository = Repository(original)
        open(repository)
        awaitReadyContacts(1)
        ui.onNodeWithText("Eliminar contacto").performScrollTo().performClick()
        ui.onNodeWithText("¿Eliminar contacto?").assertIsDisplayed()
        ui.onNodeWithText("Cancelar").performClick()
        ui.onNodeWithText("¿Eliminar contacto?").assertDoesNotExist()
        assertSame(original, repository.snapshot)
        assertEquals(0, repository.writes.get())
        ui.onNodeWithText("Eliminar contacto").performScrollTo().performClick()
        ui.onNodeWithText("Eliminar").performClick()
        awaitTag("help_contacts_empty")
        assertEquals(1, repository.writes.get())
        assertNotEquals(original.revision, repository.snapshot.revision)
        assertTrue(repository.snapshot.contacts.isEmpty())
    }

    @Test fun aNewReadingRetiresTheOldDeletionDialogAndItsStaleConfirmation() {
        val contact = TrustedContact.create("Contacto anterior", "5512345678")
        val repository = Repository(snapshot(listOf(contact)))
        open(repository)
        awaitReadyContacts(1)
        val oldReading = model.state.value as HelpContactsState.Ready
        ui.onNodeWithText("Eliminar contacto").performScrollTo().performClick()
        ui.onNodeWithText("¿Eliminar contacto?").assertIsDisplayed()
        val updated = contact.copy(name = "Contacto actualizado")
        ui.runOnIdle {
            repository.snapshot = snapshot(listOf(updated))
            model.load()
        }
        awaitReadyContacts(1, differentFrom = oldReading)
        ui.onNodeWithText("¿Eliminar contacto?").assertDoesNotExist()
        ui.onNodeWithText("Contacto actualizado").performScrollTo().assertIsDisplayed()
        ui.runOnIdle { assertFalse(model.remove(contact.id, oldReading)) }
        assertEquals(listOf(updated), repository.snapshot.contacts)
        assertEquals(0, repository.writes.get())
    }

    @Test fun unsafePhoneShowsValidationWithoutWritingOrOfferingADialTarget() {
        val repository = Repository()
        open(repository)
        awaitTag("help_contacts_empty")
        ui.onNodeWithTag("help_contact_name").performScrollTo().performTextInput("Contacto")
        ui.onNodeWithTag("help_contact_phone").performScrollTo().performTextInput("5512345678;911")
        ui.onNodeWithTag("help_contact_save").performScrollTo().performClick()
        awaitTag("help_contacts_validation")
        assertEquals(0, repository.writes.get())
        assertTrue(repository.snapshot.contacts.isEmpty())
        assertTrue(contactTargets.isEmpty())
        ui.onNodeWithTag("help_contacts_empty").performScrollTo().assertIsDisplayed()
    }

    private fun open(repository: Repository) {
        // ReportsActivity is already declared and receives an isolated report workspace from
        // the test runner. Replacing its content never constructs HelpContactsActivity/Store.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(Intent(context, ReportsActivity::class.java))
        scenario!!.onActivity { activity ->
            model = ViewModelProvider(modelStore, HelpContactsViewModel.factory(repository, {}))[
                HelpContactsViewModel::class.java]
            activity.setContent {
                PreviewTheme {
                    HelpContactsScreen(model, dialMessage = null, academic = false,
                        onEmergencyDial = { emergencyTargets.add(it) },
                        onContactDial = { contact, shown ->
                            assertSame(model.state.value, shown)
                            contactTargets.add(contact.phoneNumber)
                        }, onClose = {}, checkGesture = { true })
                }
            }
        }
    }

    private fun awaitReadyContacts(count: Int, differentFrom: HelpContactsState.Ready? = null) {
        ui.waitUntil(20_000) {
            (model.state.value as? HelpContactsState.Ready)?.let {
                it !== differentFrom && it.snapshot.contacts.size == count
            } == true
        }
        ui.waitForIdle()
    }

    private fun awaitTag(tag: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
    }

    private fun snapshot(contacts: List<TrustedContact>) = TrustedContactsSnapshot(UUID.randomUUID().toString(), contacts)

    private class Repository(@Volatile var snapshot: TrustedContactsSnapshot = TrustedContactsSnapshot(null, emptyList())) : TrustedContactsRepository {
        val writes = AtomicInteger()
        @Volatile var readFailure: Exception? = null
        override suspend fun read(): TrustedContactsSnapshot {
            readFailure?.let { throw it }
            return snapshot
        }
        override suspend fun replace(expectedRevision: String?, contacts: List<TrustedContact>): TrustedContactsSnapshot = synchronized(this) {
            check(snapshot.revision == expectedRevision)
            writes.incrementAndGet()
            TrustedContactsSnapshot(UUID.randomUUID().toString(), contacts).also { snapshot = it }
        }
    }
}
