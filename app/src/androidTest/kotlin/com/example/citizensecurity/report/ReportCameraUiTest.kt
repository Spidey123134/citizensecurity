package com.example.citizensecurity.report

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.preview.ReportDraftActivity
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixture
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real ActivityResult camera protocol and private normalization, with the camera app intercepted.
 * This verifies output handling, not a physical camera, vendor camera UI, or process death.
 */
@RunWith(AndroidJUnit4::class)
class ReportCameraUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as ReportTestApplication
    private val fixture get() = fixtureRule.fixture
    private var scenario: ActivityScenario<ReportDraftActivity>? = null
    private val cameraCalls = ConcurrentLinkedQueue<Intent>()
    private val outputs = ConcurrentLinkedQueue<Uri>()
    private var response: (Uri) -> Instrumentation.ActivityResult = { uri ->
        writeJpeg(uri)
        Instrumentation.ActivityResult(Activity.RESULT_OK, null)
    }
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != MediaStore.ACTION_IMAGE_CAPTURE) return null
            cameraCalls.add(Intent(intent))
            val output = intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)
            assertNotNull("Camera must receive its reserved content URI", output)
            val uri = output!!
            outputs.add(uri)
            assertEquals("content", uri.scheme)
            assertEquals("${context.packageName}.camera_capture", uri.authority)
            assertEquals(1, intent.clipData?.itemCount)
            assertEquals(uri, intent.clipData!!.getItemAt(0).uri)
            val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            assertEquals(grants, intent.flags and grants)
            return response(uri)
        }
    }

    @Before fun interceptOnlyCamera() { instrumentation.addMonitor(monitor) }

    @After fun closeOwnResources() {
        scenario?.close()
        scenario = null
        instrumentation.removeMonitor(monitor)
        instrumentation.runOnMainSync {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                .filter { it.packageName == context.packageName }.forEach { it.finish() }
        }
        // Production owns retirement. The test never sweeps this shared cache directory.
        outputs.forEach { assertFalse("This capture's temporary output must be retired", outputFile(it).exists()) }
    }

    @Test fun outputJpegIsNormalizedWithoutReturnedExtrasAndPersistsWithTheSavedReport() {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.CAMERA))
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertFalse(requested.contains(Manifest.permission.CAMERA))
        response = { uri ->
            writeJpeg(uri, includeMetadata = true)
            // Some camera apps return a thumbnail/data URI. They are not the reserved output.
            val thumbnail = Bitmap.createBitmap(13, 7, Bitmap.Config.ARGB_8888)
            val returned = Intent().setData(Uri.parse("content://untrusted.example/ignored-photo.jpg"))
                .putExtra("data", thumbnail)
                .putExtra(MediaStore.EXTRA_OUTPUT, Uri.parse("content://untrusted.example/other.jpg"))
            Instrumentation.ActivityResult(Activity.RESULT_OK, returned)
        }
        launchAndPrepare()
        assertTrue(cameraCalls.isEmpty())
        takePhoto()
        awaitPhoto()
        val photo = currentPhoto()!!
        assertEquals(640, photo.width)
        assertEquals(320, photo.height)
        val normalized = runBlocking { fixture.photoStore.read(photo) }
        assertEquals(normalized.size.toLong(), photo.byteCount)
        val exif = ExifInterface(ByteArrayInputStream(normalized))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_MODEL))
        assertEquals(DESCRIPTION, currentDescription())
        assertEquals(0, fixture.refreshCalls)
        assertEquals(1, cameraCalls.size)
        awaitRetiredOutputs()
        scenario!!.recreate()
        await("draft_description")
        assertEquals(photo, currentPhoto())
        ui.onNodeWithTag("draft_photo_image").performScrollTo().assertExists()
        fixture.publishFreshFix()
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithTag("save_report").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(15_000) {
            var saved = false
            scenario!!.onActivity { saved = it.submissionModel.state.value is ReportSubmissionState.Saved }
            saved
        }
        val saved = runBlocking { fixture.repository.list() }.single()
        assertEquals(photo, saved.photo)
        assertEquals(DESCRIPTION, saved.description)
        assertEquals(photo, runBlocking { fixture.journal.read() }!!.draft.photo)
    }

    @Test fun cancelledCameraPreservesTheExistingPhotoAndFormEvenIfItWroteAnotherOutput() {
        launchAndPrepare()
        takePhoto()
        awaitPhoto()
        val prior = currentPhoto()!!
        response = { uri ->
            writeJpeg(uri, width = 80, height = 40)
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED,
                Intent().setData(Uri.parse("content://untrusted.example/cancelled.jpg")))
        }
        takePhoto()
        awaitNotPending()
        assertEquals(prior, currentPhoto())
        assertFieldsAndNoSubmission()
        fixture.photoStore.check(prior)
        assertEquals(2, cameraCalls.size)
        awaitRetiredOutputs()
        ui.onNodeWithTag("choose_photo").performScrollTo().assertIsEnabled()
        ui.onNodeWithTag("take_photo").assertIsEnabled()
    }

    @Test fun resultOkWithEmptyOutputRejectsItAndPreservesThePriorPhoto() {
        launchAndPrepare()
        takePhoto()
        awaitPhoto()
        val prior = currentPhoto()!!
        response = { Instrumentation.ActivityResult(Activity.RESULT_OK,
            Intent().setData(Uri.parse("content://untrusted.example/not-the-output.jpg"))) }
        takePhoto()
        awaitCameraError()
        assertEquals(prior, currentPhoto())
        assertFieldsAndNoSubmission()
        fixture.photoStore.check(prior)
        awaitRetiredOutputs()
        ui.onNodeWithTag("choose_photo").performScrollTo().assertIsEnabled()
    }

    @Test fun resultOkWithCorruptOutputRejectsItAndPermitsAnExplicitValidRetry() {
        launchAndPrepare()
        takePhoto()
        awaitPhoto()
        val prior = currentPhoto()!!
        response = { uri ->
            context.contentResolver.openOutputStream(uri, "w")!!.use { it.write("broken camera output".toByteArray()) }
            Instrumentation.ActivityResult(Activity.RESULT_OK, null)
        }
        takePhoto()
        awaitCameraError()
        assertEquals(prior, currentPhoto())
        assertFieldsAndNoSubmission()
        awaitRetiredOutputs()
        response = { uri ->
            writeJpeg(uri, width = 96, height = 48)
            Instrumentation.ActivityResult(Activity.RESULT_OK, null)
        }
        takePhoto()
        ui.waitUntil(15_000) { currentPhoto() != prior && !photoPending() }
        assertEquals(96, currentPhoto()!!.width)
        assertEquals(48, currentPhoto()!!.height)
        assertFieldsAndNoSubmission()
        awaitRetiredOutputs()
    }

    @Test fun absentCameraHandlerReturnsToTheUnmodifiedFormWithGalleryAvailable() {
        response = { throw ActivityNotFoundException("Camera handler unavailable in this controlled test") }
        launchAndPrepare()
        takePhoto()
        awaitCameraError()
        assertNull(currentPhoto())
        assertFieldsAndNoSubmission()
        ui.onNodeWithTag("choose_photo").performScrollTo().assertIsEnabled()
        ui.onNodeWithTag("take_photo").assertIsEnabled()
        awaitRetiredOutputs()
    }

    @Test fun recreatedActivityKeepsOneLivePendingCaptureUntilTheExplicitSimulatedCallback() {
        launchAndPrepare()
        val pendingSource = AtomicReference<String?>()
        var original: ReportCameraViewModel? = null
        scenario!!.onActivity {
            original = it.cameraModel
            assertTrue(it.cameraModel.prepare({ true }) { source -> pendingSource.set(source) })
        }
        // This case deliberately holds the external camera callback. Other cases above exercise
        // the actual ActivityResult launch/result path; this one verifies retained model ownership.
        ui.waitUntil(10_000) { pendingSource.get() != null }
        val uri = Uri.parse(pendingSource.get()!!)
        outputs.add(uri)
        writeJpeg(uri, width = 320, height = 160)
        assertTrue(photoPending())
        scenario!!.recreate()
        await("draft_description")
        scenario!!.onActivity {
            assertSame(original, it.cameraModel)
            assertEquals(ReportCameraState.Waiting, it.cameraModel.state.value)
            assertFalse(it.cameraModel.prepare({ true }) { fail("Rotation must not launch another capture") })
            assertTrue(it.cameraModel.complete(true))
            assertFalse(it.cameraModel.complete(true))
        }
        awaitPhoto()
        assertEquals(320, currentPhoto()!!.width)
        assertEquals(160, currentPhoto()!!.height)
        assertTrue(cameraCalls.isEmpty())
        assertFieldsAndNoSubmission()
        awaitRetiredOutputs()
    }

    @Test fun revokedWorkspaceCannotAttachTheCameraResultOrCreateAPhotoForAnotherSession() {
        launchAndPrepare()
        var originalDraft: ReportDraftViewModel? = null
        scenario!!.onActivity { originalDraft = it.draftModel }
        val privatePhotosBefore = ownPhotoFiles()
        response = { uri ->
            writeJpeg(uri)
            // Even another academic login changes the lease. The old Activity must retire.
            app.accountSession.signOut()
            Instrumentation.ActivityResult(Activity.RESULT_OK, null)
        }
        takePhoto()
        ui.waitUntil(10_000) { scenario!!.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
        val retiredDraft = checkNotNull(originalDraft)
        assertNull(retiredDraft.state.value.photo)
        assertEquals(DESCRIPTION, retiredDraft.state.value.description)
        assertEquals(privatePhotosBefore, ownPhotoFiles())
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertNull(runBlocking { fixture.journal.read() })
        assertEquals(0, fixture.refreshCalls)
        awaitRetiredOutputs()
    }

    private fun launchAndPrepare() {
        scenario = ActivityScenario.launch(ReportDraftActivity::class.java)
        await("draft_description")
        scenario!!.onActivity { assertTrue(it.draftModel.restoreForCorrection(NewReport(
            IncidentType.RISK, Priority.MEDIUM, DESCRIPTION, Instant.now().minusSeconds(60),
            ReportLocation("Parque de prueba", ReportTestFixture.LATITUDE, ReportTestFixture.LONGITUDE),
        ))) }
        // The form retains its editable field text in rememberSaveable. Seed model location,
        // then enter the description through the same UI gesture as the user.
        ui.onNodeWithTag("draft_description").performScrollTo().performTextReplacement(DESCRIPTION)
        ui.onNodeWithTag("draft_description").performScrollTo().assertTextContains(DESCRIPTION)
        ui.onNodeWithTag("choose_photo").performScrollTo().assertIsEnabled()
        ui.onNodeWithTag("take_photo").assertIsEnabled()
    }

    private fun takePhoto() {
        val before = cameraCalls.size
        ui.onNodeWithTag("take_photo").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(10_000) { cameraCalls.size > before }
    }

    private fun awaitPhoto() {
        ui.waitUntil(15_000) { currentPhoto() != null && !photoPending() && !cameraBusy() }
        await("draft_photo_image")
    }

    private fun awaitCameraError() {
        ui.waitUntil(15_000) {
            var failed = false
            scenario!!.onActivity { failed = it.cameraModel.state.value is ReportCameraState.Error }
            failed && !photoPending() && !cameraBusy()
        }
    }

    private fun awaitNotPending() = ui.waitUntil(15_000) { !photoPending() && !cameraBusy() }
    private fun cameraBusy(): Boolean {
        var busy = true
        scenario!!.onActivity { busy = it.cameraModel.busy.value }
        return busy
    }
    private fun await(tag: String) = ui.waitUntil(10_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun awaitRetiredOutputs() = ui.waitUntil(15_000) { outputs.all { !outputFile(it).exists() } }

    private fun currentPhoto(): ReportPhoto? {
        var photo: ReportPhoto? = null
        scenario!!.onActivity { photo = it.draftModel.state.value.photo }
        return photo
    }
    private fun currentDescription(): String {
        var value = ""
        scenario!!.onActivity { value = it.draftModel.state.value.description }
        return value
    }
    private fun photoPending(): Boolean {
        var pending = true
        scenario!!.onActivity { pending = it.draftModel.state.value.photoSelectionPending }
        return pending
    }
    private fun assertFieldsAndNoSubmission() {
        scenario!!.onActivity {
            assertEquals(DESCRIPTION, it.draftModel.state.value.description)
            assertEquals(IncidentType.RISK, it.draftModel.state.value.type)
            assertEquals(Priority.MEDIUM, it.draftModel.state.value.priority)
            assertEquals("Parque de prueba", it.draftModel.state.value.location.reference)
            assertEquals(ReportTestFixture.LATITUDE, it.draftModel.state.value.location.latitude)
            assertEquals(ReportTestFixture.LONGITUDE, it.draftModel.state.value.location.longitude)
        }
        assertTrue(runBlocking { fixture.repository.list().isEmpty() })
        assertNull(runBlocking { fixture.journal.read() })
        assertEquals(0, fixture.refreshCalls)
    }
    private fun outputFile(uri: Uri): File = File(context.cacheDir,
        "${ReportCameraCaptureStore.DIRECTORY_NAME}/${uri.lastPathSegment}")
    private fun ownPhotoFiles(): Set<String> = context.filesDir.listFiles().orEmpty()
        .filter { it.name.startsWith("photos_ui_") }
        .flatMap { folder -> folder.listFiles().orEmpty().map { it.absolutePath } }.toSet()

    private fun writeJpeg(uri: Uri, width: Int = 640, height: Int = 320, includeMetadata: Boolean = false) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).apply {
                drawColor(Color.rgb(217, 244, 232))
                drawText("CAPTURA CONTROLADA", 18f, 70f,
                    Paint().apply { color = Color.rgb(26, 95, 72); textSize = 24f; isAntiAlias = true })
            }
            context.contentResolver.openOutputStream(uri, "w")!!.use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it))
            }
        } finally { bitmap.recycle() }
        if (includeMetadata) context.contentResolver.openFileDescriptor(uri, "rw")!!.use { descriptor ->
            val exif = ExifInterface(descriptor.fileDescriptor)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "19/1,0/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "99/1,0/1,0/1")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W")
            exif.setAttribute(ExifInterface.TAG_MODEL, "CONTROLLED_CAMERA_FIXTURE")
            exif.saveAttributes()
        }
    }

    private companion object {
        const val DESCRIPTION = "Luminaria dañada frente al parque para verificar la foto tomada con cámara."
    }
}
