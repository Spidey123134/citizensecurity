package com.example.citizensecurity.report

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.preview.ReportDraftActivity
import com.example.citizensecurity.testing.ReportTestFixture
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual system photo picker plus private files, journal and SQLite on an isolated emulator. */
@RunWith(AndroidJUnit4::class)
@android.annotation.TargetApi(29)
class ReportPhotoUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private var scenario: ActivityScenario<ReportDraftActivity>? = null
    private val fixture get() = fixtureRule.fixture
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val media = mutableListOf<Uri>()

    @After fun closeOwnedResources() {
        scenario?.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .filter { it.packageName == context.packageName }.forEach { it.finish() }
        }
        // Only MediaStore items made by this test, never existing gallery photographs.
        media.forEach { context.contentResolver.delete(it, null, null) }
    }

    @Test fun actualPickerCopySurvivesRotationCancellationSaveAndSourceRemoval() {
        val source = insertSource()
        launchDraft()
        prepareFields()
        ui.onNodeWithTag("choose_photo").performScrollTo().performClick()
        val image = device.wait(Until.findObject(By.descContains("Photo taken on")), 15_000)
        if (image == null) {
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null), "release054-picker.xml"))
            fail("System picker image was not found; hierarchy saved for diagnosis")
        }
        image!!.click()
        device.wait(Until.findObject(By.text("Done")), 10_000)!!.click()
        awaitPhoto()
        val photo = draftPhoto()!!
        assertEquals(480, photo.width)
        assertEquals(240, photo.height)
        ui.onNodeWithTag("draft_photo_image").performScrollTo().assertExists()
        capture("release054-fotografia.png")
        scenario!!.recreate()
        ui.waitForIdle()
        assertEquals(photo, draftPhoto())
        ui.onNodeWithTag("choose_photo").performScrollTo().performClick()
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.photopicker")), 10_000))
        device.pressBack()
        ui.waitUntil(10_000) { !photoPending() }
        assertEquals(photo, draftPhoto())
        // The report uses a private copy, even when its source URI can no longer be opened.
        assertEquals(1, context.contentResolver.delete(source, null, null))
        media.remove(source)
        fixture.photoStore.check(photo)
        reviewAndSave()
        val saved = savedReport()
        assertEquals(photo, saved.photo)
        assertEquals(1, runBlocking { fixture.repository.list().size })
        assertEquals(photo, runBlocking { fixture.journal.read() }!!.draft.photo)
        ui.onNodeWithTag("open_saved_report").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("report_photo_image").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("report_photo_image").performScrollTo().assertExists()
        capture("release054-detalle-fotografia.png")
    }

    @Test fun corruptedCopyRejectsSavePreservesFieldsAndAllowsExplicitRemoval() {
        val source = insertSource()
        launchDraft()
        prepareFields()
        importViaPickerCallback(source)
        awaitPhoto()
        val photo = draftPhoto()!!
        photoFile(photo).writeBytes("corrupt fixture".toByteArray())
        fixture.publishFreshFix()
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithTag("save_report").performScrollTo().performClick()
        ui.waitUntil(15_000) {
            var rejected = false
            scenario!!.onActivity { rejected = it.submissionModel.state.value is ReportSubmissionState.Invalid }
            rejected
        }
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("remove_photo").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertNull(runBlocking { fixture.journal.read() })
        scenario!!.onActivity {
            assertEquals(DESCRIPTION, it.draftModel.state.value.description)
            assertEquals(photo, it.draftModel.state.value.photo)
        }
        ui.onNodeWithTag("remove_photo").performScrollTo().performClick()
        ui.onNodeWithTag("draft_photo_empty").assertExists()
        reviewAndSave()
        assertNull(savedReport().photo)
        assertEquals(1, runBlocking { fixture.repository.list().size })
    }

    @Test fun unreadableSavedPhotoStillShowsActualFolioAndDescription() {
        val source = insertSource()
        launchDraft()
        prepareFields()
        importViaPickerCallback(source)
        awaitPhoto()
        reviewAndSave()
        val saved = savedReport()
        photoFile(saved.photo!!).writeBytes("bad copy after commit".toByteArray())
        ui.onNodeWithTag("open_saved_report").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("report_photo_error").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("report_description").performScrollTo().assertTextEquals(DESCRIPTION)
        ui.onNodeWithTag("report_folio").performScrollTo().assertTextEquals(saved.id)
        assertEquals(saved, runBlocking { fixture.repository.findById(saved.id) })
        assertEquals(1, runBlocking { fixture.repository.list().size })
    }

    @Test fun savedCheckpointRecoversPhotoWithoutGpsOrInsertAndLinksConfirmedFolio() {
        val source = insertSource()
        launchDraft()
        prepareFields()
        importViaPickerCallback(source)
        awaitPhoto()
        val photo = draftPhoto()!!
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithTag("save_checkpoint").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(15_000) {
            var done = false
            scenario!!.onActivity { done = it.checkpointModel.state.value.checkpoint != null && !it.checkpointModel.state.value.busy }
            done
        }
        val stored = runBlocking { fixture.checkpoint.read() }!!
        assertEquals(photo, stored.draft.photo)
        assertNull(stored.submittedFolio)
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertNull(runBlocking { fixture.journal.read() })
        capture("release054-borrador-guardado.png")
        scenario!!.close()
        scenario = null
        assertEquals(1, context.contentResolver.delete(source, null, null))
        media.remove(source)
        fixture.clearFix()
        val captures = fixture.refreshCalls
        launchDraft()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("restore_checkpoint").fetchSemanticsNodes().isNotEmpty() }
        assertNull(draftPhoto())
        assertEquals(captures, fixture.refreshCalls)
        ui.onNodeWithTag("restore_checkpoint").performScrollTo().performClick()
        ui.onNodeWithTag("confirm_restore_checkpoint").performClick()
        awaitPhoto()
        scenario!!.onActivity { assertEquals(stored.draft.description, it.draftModel.state.value.description) }
        ui.onNodeWithTag("draft_description").performScrollTo().assertTextContains(DESCRIPTION)
        assertEquals(captures, fixture.refreshCalls)
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithTag("save_report").performScrollTo().performClick()
        ui.waitUntil(10_000) {
            var rejected = false
            scenario!!.onActivity { rejected = it.submissionModel.state.value is ReportSubmissionState.Invalid }
            rejected
        }
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("review_draft").fetchSemanticsNodes().isNotEmpty() }
        reviewAndSave()
        val saved = savedReport()
        ui.waitUntil(10_000) { runBlocking { fixture.checkpoint.read() }?.submittedFolio == saved.id }
        val linked = runBlocking { fixture.checkpoint.read() }!!
        assertEquals(stored.draft, linked.draft)
        assertEquals(photo, saved.photo)
        assertEquals(stored.id, saved.id)
        assertEquals(1, runBlocking { fixture.repository.list().size })
        ui.onNodeWithTag("new_report").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("open_checkpoint_report").fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodesWithTag("restore_checkpoint").assertCountEquals(0)
        ui.onNodeWithTag("open_checkpoint_report").performScrollTo().performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("report_folio").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("report_folio").assertTextEquals(saved.id)
        assertEquals(1, runBlocking { fixture.repository.list().size })
    }

    @Test fun cancelCheckpointRecoveryRetainsTheFieldsBeingEdited() {
        val checkpoint = ReportDraftCheckpoint(java.util.UUID.randomUUID().toString(), NewReport(
            IncidentType.THEFT, Priority.LOW, "Otra descripción de un borrador distinto.", Instant.now().minusSeconds(30),
            ReportLocation("Referencia de la copia guardada"),
        ), Instant.now())
        runBlocking { fixture.checkpoint.write(checkpoint, null) }
        launchDraft()
        prepareFields()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("restore_checkpoint").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag("restore_checkpoint").performScrollTo().performClick()
        ui.onNodeWithText("Cancelar").performClick()
        scenario!!.onActivity {
            assertEquals(DESCRIPTION, it.draftModel.state.value.description)
            assertEquals(IncidentType.RISK, it.draftModel.state.value.type)
        }
        assertEquals(checkpoint, runBlocking { fixture.checkpoint.read() })
        assertEquals(0, fixture.refreshCalls)
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
    }

    @Test fun staleCheckpointWriteCannotCreateAnotherFolioBeforeReloadingTheConfirmedCopy() {
        val stored = ReportDraftCheckpoint(java.util.UUID.randomUUID().toString(), NewReport(
            IncidentType.RISK, Priority.MEDIUM, DESCRIPTION, Instant.now().minusSeconds(30),
            ReportLocation("Parque de prueba", ReportTestFixture.LATITUDE, ReportTestFixture.LONGITUDE),
        ), Instant.now())
        runBlocking { fixture.checkpoint.write(stored, null) }
        launchDraft()
        prepareFields()
        scenario!!.onActivity { assertTrue(it.draftModel.restoreForCorrection(stored.draft)) }
        ui.waitUntil(10_000) {
            var ready = false
            scenario!!.onActivity { ready = it.checkpointModel.state.value.loaded }
            ready
        }
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        fixture.publishFreshFix()
        val confirmed = runBlocking { fixture.repository.createOnce(stored.id, stored.draft) }
        runBlocking { fixture.checkpoint.markSubmitted(stored.id, confirmed.id) }
        ui.onNodeWithTag("save_checkpoint").performScrollTo().performClick()
        ui.waitUntil(10_000) {
            var failed = false
            scenario!!.onActivity { failed = it.checkpointModel.state.value.failure }
            failed
        }
        ui.onNodeWithTag("save_report").performScrollTo().assertIsNotEnabled()
        assertEquals(1, runBlocking { fixture.repository.list().size })
        ui.onNodeWithTag("reload_checkpoint").performScrollTo().performClick()
        ui.waitUntil(10_000) {
            var ready = false
            scenario!!.onActivity { ready = it.checkpointModel.state.value.loaded && !it.checkpointModel.state.value.busy }
            ready
        }
        ui.onNodeWithTag("save_report").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(10_000) {
            var saved = false
            scenario!!.onActivity { saved = it.submissionModel.state.value is ReportSubmissionState.Saved }
            saved
        }
        assertEquals(confirmed, savedReport())
        assertEquals(1, runBlocking { fixture.repository.list().size })
    }

    private fun launchDraft() {
        scenario = ActivityScenario.launch(ReportDraftActivity::class.java)
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("draft_description").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun prepareFields() {
        ui.onNodeWithTag("type_RISK").performScrollTo().performClick()
        ui.onNodeWithTag("priority_MEDIUM").performScrollTo().performClick()
        ui.onNodeWithTag("draft_description").performScrollTo().performTextInput(DESCRIPTION)
        ui.onNodeWithTag("draft_reference").performScrollTo().performTextInput("Parque de prueba")
        // Location/form UI already has separate native acceptance. Seed only those fields here;
        // the same SQLite nearby guard still checks the fixture's current phone reading.
        scenario!!.onActivity { it.draftModel.restoreForCorrection(NewReport(
            IncidentType.RISK, Priority.MEDIUM, DESCRIPTION, Instant.now().minusSeconds(60),
            ReportLocation("Parque de prueba", ReportTestFixture.LATITUDE, ReportTestFixture.LONGITUDE),
        )) }
    }

    private fun importViaPickerCallback(uri: Uri) {
        scenario!!.onActivity {
            val token = it.draftModel.beginPhotoSelection()!!
            assertTrue(it.photoImportModel.importPhoto(token, uri.toString()))
        }
    }

    private fun awaitPhoto() {
        ui.waitUntil(15_000) { draftPhoto() != null && !photoPending() }
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("draft_photo_image").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun draftPhoto(): ReportPhoto? {
        var photo: ReportPhoto? = null
        scenario!!.onActivity { photo = it.draftModel.state.value.photo }
        return photo
    }
    private fun photoPending(): Boolean {
        var pending = true
        scenario!!.onActivity { pending = it.draftModel.state.value.photoSelectionPending }
        return pending
    }
    private fun photoFile(photo: ReportPhoto): File =
        context.filesDir.listFiles()!!.single { it.name.startsWith("photos_ui_") && File(it, "${photo.id}.jpg").exists() }
            .resolve("${photo.id}.jpg")

    private fun reviewAndSave() {
        fixture.publishFreshFix()
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithTag("save_report").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(15_000) {
            var saved = false
            scenario!!.onActivity { saved = it.submissionModel.state.value is ReportSubmissionState.Saved }
            saved
        }
    }
    private fun savedReport(): Report {
        var saved: Report? = null
        scenario!!.onActivity { saved = (it.submissionModel.state.value as ReportSubmissionState.Saved).report }
        return saved!!
    }
    private fun insertSource(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "citizensecurity-test-${System.nanoTime()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CitizenSecurityTest")
            // Newest and distinguishable from inherited AVD images; entirely test-owned.
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis() + 86_400_000L)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        media.add(uri)
        val bitmap = Bitmap.createBitmap(480, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(232, 239, 255))
        val paint = Paint().apply { color = Color.rgb(57, 69, 190); textSize = 28f; isAntiAlias = true }
        canvas.drawText("FOTOGRAFÍA DE PRUEBA", 34f, 120f, paint)
        context.contentResolver.openOutputStream(uri)!!.use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        bitmap.recycle()
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
    private fun capture(name: String) {
        ui.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        // Compose puede estar quieto mientras Android todavía anima la nueva Activity.
        automation.waitForIdle(500, 5_000)
        val screenshot = automation.takeScreenshot()!!
        File(context.getExternalFilesDir(null), name).outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
    }
    private companion object { const val DESCRIPTION = "Luminaria de prueba frente al parque para verificar la fotografía." }
}
