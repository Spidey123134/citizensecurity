package com.example.citizensecurity.acceptance

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivityBinding
import com.example.citizensecurity.data.SqliteAccountRepository
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.evidence.ReportEvidenceExport
import com.example.citizensecurity.evidence.ReportEvidenceState
import com.example.citizensecurity.evidence.ReportEvidenceViewModel
import com.example.citizensecurity.preview.ReportDraftActivity
import com.example.citizensecurity.report.ReportSubmissionState
import com.example.citizensecurity.reports.ReportsActivity
import com.example.citizensecurity.resolution.ReportResolutionState
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One real account-owned route across the product screens and durable stores. The selected photo
 * and phone fix are controlled inputs; no camera hardware, physical GPS or process death is claimed.
 * PDF preparation is local: a monitor rejects any accidental external chooser.
 */
@RunWith(AndroidJUnit4::class)
class ReportEndToEndAcceptanceTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private val fixture get() = fixtureRule.fixture
    private var draft: ActivityScenario<ReportDraftActivity>? = null
    private var detail: ActivityScenario<ReportsActivity>? = null
    private val scopes = mutableListOf<ReportWorkspace>()
    private var accounts: SqliteAccountRepository? = null
    private var source: File? = null
    private val externalChoosers = AtomicInteger()
    private val chooserMonitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_CHOOSER) return null
            externalChoosers.incrementAndGet()
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    @Before fun ownOnlyDisposableEmulatorState() {
        assertTrue("Acceptance runs only on the owned emulator", Build.HARDWARE in listOf("ranchu", "goldfish"))
        instrumentation.runOnMainSync { app.accountSession.signOut() }
        instrumentation.addMonitor(chooserMonitor)
    }

    @After fun closeOwnedSessionsAndWindows() {
        detail?.close()
        draft?.close()
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filter { it.packageName == app.packageName }.forEach(Activity::finish)
            app.accountSession.signOut()
        }
        scopes.forEach(ReportWorkspace::close)
        app.accountTestRepository = null
        accounts?.close()
        instrumentation.removeMonitor(chooserMonitor)
        // Only this test's source; the app's private normalized photo is deliberately preserved.
        source?.let { if (it.exists()) check(it.delete()) }
        instrumentation.waitForIdleSync()
    }

    @Test fun accountCanReportReopenReadItsPhotoPreparePdfAndDeclareResultWithoutLeakingToAnotherAccount() {
        val accountStore = SqliteAccountRepository(app, "acceptance_accounts_${UUID.randomUUID()}.db")
            .also { accounts = it; app.accountTestRepository = it }
        val owner = register(accountStore, "autor_recorrido")
        signIn(owner)
        val ownerScope = workspace()
        draft = ActivityScenario.launch(ReportWorkspaceActivityBinding.stamp(
            Intent(app, ReportDraftActivity::class.java), app.accountSession.capture(),
        ))
        awaitDraftIdle()
        ui.onNodeWithTag("type_RISK").performScrollTo().performClick()
        ui.onNodeWithTag("priority_MEDIUM").performScrollTo().performClick()
        ui.onNodeWithTag("draft_description").performScrollTo().performTextInput(DESCRIPTION)
        ui.onNodeWithTag("draft_reference").performScrollTo().performTextInput(REFERENCE)
        closeSoftKeyboard()

        importOwnedPhoto()
        val photo = draftPhoto()
        assertTrue("The source must be normalized to the supported bound", photo.width <= ReportPhoto.MAX_DIMENSION)
        assertTrue(photo.width < SOURCE_WIDTH)
        val privateBytes = runBlocking { ownerScope.readPhoto(photo) }
        assertEquals(photo.sha256, sha256(privateBytes))
        assertTrue(requireNotNull(source).delete())

        // These are the same map controls used by a person. Only the independent phone reading
        // is controlled; the product still enforces the real permission and nearby policy.
        ui.onNodeWithTag("open_incident_map").performScrollTo().performClick()
        ui.waitUntil(60_000) { var ready = false; draft!!.onActivity { ready = it.previewMapReady }; ready }
        ui.onNodeWithTag("incident_map").performTouchInput { click(center) }
        ui.waitUntil(10_000) {
            var selected = false
            draft!!.onActivity { selected = it.locationModel.state.value.proposal is LocationProposal.ValidPoint }
            selected
        }
        ui.onNodeWithTag("confirm_point").assertIsNotEnabled()
        ui.onNodeWithTag("locate_phone").performScrollTo().performClick()
        ui.onNodeWithTag("confirm_point").performScrollTo().assertIsEnabled().performClick()
        ui.onNodeWithTag("review_draft").performScrollTo().performClick()
        ui.onNodeWithTag("review_description").performScrollTo().assertTextEquals(DESCRIPTION)
        ui.onNodeWithTag("review_reference").performScrollTo().assertTextEquals(REFERENCE)
        ui.onNodeWithTag("review_photo_image").performScrollTo().assertIsDisplayed()
        assertTrue(runBlocking { ownerScope.repository.list().isEmpty() })
        assertNull(runBlocking { ownerScope.submissionJournal.read() })
        ui.onNodeWithTag("save_report").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(20_000) { submissionState() is ReportSubmissionState.Saved }
        val folio = ui.onNodeWithTag("saved_folio").fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
        assertEquals(folio, UUID.fromString(folio).toString())
        val saved = runBlocking { requireNotNull(ownerScope.repository.findById(folio)) }
        assertEquals(DESCRIPTION, saved.description)
        assertEquals(photo, saved.photo)
        assertEquals(REFERENCE, saved.location.reference)
        assertEquals(ReportStatus.REPORTED, saved.status)
        assertEquals(listOf(folio), runBlocking { ownerScope.repository.list().map { it.id } })
        assertEquals(folio, runBlocking { requireNotNull(ownerScope.submissionJournal.read()).requestId })
        val captures = fixture.refreshCalls

        // A new Activity, not recreate(): no retained ViewModel can supply this receipt/detail.
        draft!!.close()
        draft = null
        fixture.clearFix()
        openDetail(folio)
        awaitDetailTag("report_folio")
        node("report_folio").assertTextEquals(folio)
        node("report_description").assertTextEquals(DESCRIPTION)
        node("report_photo_image").assertIsDisplayed()
        assertArrayEquals(privateBytes, runBlocking { ownerScope.readPhoto(photo) })
        assertEquals(captures, fixture.refreshCalls)

        click("history_open")
        awaitDetailTag("history_event_1")
        val creation = runBlocking { requireNotNull(ownerScope.historyRepository.readHistory(ReportHistoryQuery(folio))) }
        assertTrue(creation.completeFromCreation)
        assertEquals(1L, creation.latestSequence)
        assertEquals(ReportStatusEventKind.CREATED, creation.events.single().kind)
        assertEquals(owner.id, creation.events.single().actorAccountId)

        click("evidence_prepare")
        ui.onNodeWithTag("evidence_photo").assertIsOff().performClick().assertIsOn()
        ui.onNodeWithTag("evidence_location").assertIsOn()
        ui.onNodeWithTag("evidence_confirm").performClick()
        awaitDetailTag("evidence_ready")
        val ready = evidenceState() as ReportEvidenceState.Ready
        assertEquals(folio, ready.artifact.reportId)
        assertTrue(ready.request.options.includePhoto)
        assertTrue(ready.request.options.includeLocation)
        verifyRenderedPdfWithPhoto(ready)
        assertEquals("Preparing a PDF must not open another app", 0, externalChoosers.get())

        awaitDetailTag("resolution_current")
        click("resolution_choice_RESOLVED")
        node("resolution_reason").performTextReplacement(REASON)
        closeSoftKeyboard()
        click("resolution_review")
        awaitDetailTag("resolution_confirm")
        val command = (resolutionState() as ReportResolutionState.Reviewing).command
        val results = ownerScope.openResolutionRepository { error("Acceptance reads must never request a write") }
        assertEquals(0L, runBlocking { requireNotNull(results.read(ReportResolutionQuery(folio))).latestSequence })
        click("resolution_confirm")
        awaitDetailTag("resolution_confirmed")
        val confirmed = runBlocking { requireNotNull(results.read(ReportResolutionQuery(folio))) }
        assertEquals(ReportResolution.RESOLVED, confirmed.resolution)
        assertEquals(1L, confirmed.latestSequence)
        assertTrue(confirmed.events.single().matches(command))
        assertEquals(REASON, confirmed.events.single().reason)
        assertEquals(ReportResolutionActor(owner.id, ReportResolutionActorKind.AUTHOR), confirmed.events.single().actor)
        assertEquals(saved, runBlocking { ownerScope.repository.findById(folio) })
        assertEquals(creation.events, runBlocking { requireNotNull(ownerScope.historyRepository.readHistory(ReportHistoryQuery(folio))).events })

        detail!!.close()
        detail = null
        openDetail(folio)
        awaitDetailTag("resolution_current")
        node("resolution_current").assertTextEquals("Resultado: Solucionado")
        node("resolution_event_1").assert(hasAnyDescendant(hasText(REASON)))
        assertEquals(1L, runBlocking { requireNotNull(results.read(ReportResolutionQuery(folio))).latestSequence })
        assertEquals(captures, fixture.refreshCalls)
        detail!!.close()
        detail = null

        val other = register(accountStore, "otro_recorrido")
        signIn(other)
        val otherScope = workspace()
        assertTrue(runBlocking { otherScope.repository.list().isEmpty() })
        assertNull(runBlocking { otherScope.repository.findById(folio) })
        assertNull(runBlocking { otherScope.submissionJournal.read() })
        try { runBlocking { otherScope.readPhoto(photo) }; fail("Another account could read the private photo") }
        catch (_: IOException) { }
        openDetail(folio)
        ui.waitUntil(15_000) { ui.onAllNodesWithText("Ese folio no está guardado en este teléfono.").fetchSemanticsNodes().isNotEmpty() }
        ui.onAllNodesWithTag("report_photo_image").assertCountEquals(0)
        ui.onAllNodesWithTag("resolution_confirm").assertCountEquals(0)
        assertEquals(0, externalChoosers.get())
        assertEquals(captures, fixture.refreshCalls)
    }

    private fun register(repository: SqliteAccountRepository, username: String): LocalAccount = runBlocking {
        val password = UUID.randomUUID().toString()
        repository.register(username, password).also { assertEquals(it, repository.authenticate(username, password)) }
    }
    private fun signIn(account: LocalAccount) = instrumentation.runOnMainSync { app.accountSession.signIn(account) }
    private fun workspace() = app.openReportWorkspace(app.accountSession.capture()).also(scopes::add)
    private fun awaitDraftIdle() = ui.waitUntil(15_000) { submissionState() == ReportSubmissionState.Idle }
    private fun submissionState(): ReportSubmissionState {
        var state: ReportSubmissionState = ReportSubmissionState.Restoring
        draft!!.onActivity { state = it.submissionModel.state.value }
        return state
    }
    private fun draftPhoto(): ReportPhoto {
        var photo: ReportPhoto? = null
        ui.waitUntil(15_000) { draft!!.onActivity { photo = it.draftModel.state.value.photo }; photo != null }
        return requireNotNull(photo)
    }

    private fun importOwnedPhoto() {
        val directory = File(app.cacheDir, "report_camera")
        check(directory.canonicalFile.parentFile == app.cacheDir.canonicalFile)
        check(directory.isDirectory || directory.mkdir())
        source = File(directory, "acceptance-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(SOURCE_WIDTH, 1200, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).apply {
                drawColor(Color.rgb(15, 205, 45))
                drawRect(120f, 120f, 900f, 400f, Paint().apply { color = Color.rgb(20, 30, 70) })
            }
            requireNotNull(source).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally { bitmap.recycle() }
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.camera_capture", requireNotNull(source))
        // The native chooser/camera is exercised separately. This fixture supplies only its URI
        // through the same importer; no private normalized bytes or metadata are seeded directly.
        draft!!.onActivity { activity ->
            val token = requireNotNull(activity.draftModel.beginPhotoSelection())
            assertTrue(activity.photoImportModel.importPhoto(token, uri.toString()))
        }
        draftPhoto()
        ui.waitUntil(15_000) {
            var pending = true
            draft!!.onActivity { pending = it.draftModel.state.value.photoSelectionPending }
            !pending
        }
    }

    private fun openDetail(folio: String) {
        detail = ActivityScenario.launch(ReportWorkspaceActivityBinding.stamp(
            Intent(app, ReportsActivity::class.java).putExtra(ReportsActivity.EXTRA_FOLIO, folio),
            app.accountSession.capture(),
        ))
    }
    private fun evidenceState(): ReportEvidenceState {
        var state: ReportEvidenceState? = null
        detail!!.onActivity { state = ViewModelProvider(it)[ReportEvidenceViewModel::class.java].state.value }
        return requireNotNull(state)
    }
    private fun resolutionState(): ReportResolutionState {
        var state: ReportResolutionState? = null
        detail!!.onActivity { state = it.resolutionModel.state.value }
        return requireNotNull(state)
    }
    private fun verifyRenderedPdfWithPhoto(ready: ReportEvidenceState.Ready) {
        val file = File(File(app.cacheDir, ReportEvidenceExport.DIRECTORY_NAME), "${ready.artifact.token}.pdf")
        assertEquals(ready.artifact.byteCount, file.length())
        assertEquals(ready.artifact.sha256, sha256(file.readBytes()))
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertEquals(ready.artifact.pageCount, renderer.pageCount)
            var greenPixels = 0
            repeat(renderer.pageCount) { index -> renderer.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                try {
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
                        val pixel = bitmap.getPixel(x, y)
                        if (Color.green(pixel) > 160 && Color.red(pixel) < 80 && Color.blue(pixel) < 100) greenPixels++
                    }
                } finally { bitmap.recycle() }
            } }
            assertTrue("The real PDF must render the selected photo, not just a photo label", greenPixels > 1_000)
        }
    }
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun awaitDetailTag(tag: String) {
        ui.waitUntil(20_000) {
            if (ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) true else runCatching {
                ui.onNodeWithTag("report_detail").performScrollToNode(hasTestTag(tag))
                ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        node(tag)
    }
    private fun node(tag: String): SemanticsNodeInteraction {
        ui.onNodeWithTag("report_detail").performScrollToNode(hasTestTag(tag))
        exposeInsideViewport(tag)
        return ui.onNodeWithTag(tag).assertIsDisplayed()
    }
    private fun click(tag: String) {
        node(tag)
        if (tag.startsWith("resolution_choice_")) {
            ui.onNodeWithTag(tag).performScrollTo()
            exposeInsideViewport(tag)
        }
        ui.onNodeWithTag(tag).assertIsDisplayed().performClick()
    }
    private fun exposeInsideViewport(tag: String) {
        repeat(12) {
            val target = ui.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val scroller = ui.onNodeWithTag("report_detail")
            val viewport = scroller.getUnclippedBoundsInRoot()
            val centerY = (target.top.value + target.bottom.value) / 2f
            if (centerY > viewport.top.value + 8f && centerY < viewport.bottom.value - 8f) return
            val density = scroller.fetchSemanticsNode().boundsInRoot.height / (viewport.bottom.value - viewport.top.value)
            val margin = ((target.bottom.value - target.top.value) / 2f + 12f)
                .coerceIn(24f, (viewport.bottom.value - viewport.top.value) / 3f)
            val desired = centerY.coerceIn(viewport.top.value + margin, viewport.bottom.value - margin)
            scroller.performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> check(scroll(0f, (centerY - desired) * density)) }
        }
        throw AssertionError("The actual touch target $tag is outside the report viewport")
    }

    private companion object {
        const val SOURCE_WIDTH = 2400
        const val DESCRIPTION = "La luminaria frente al acceso del parque tiene un cable colgando y requiere revisión."
        const val REFERENCE = "Entrada norte del parque, al lado de la banca azul"
        const val REASON = "El cable fue retirado y pude comprobar que el paso quedó libre"
    }
}
