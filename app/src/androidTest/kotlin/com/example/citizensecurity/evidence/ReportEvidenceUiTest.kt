package com.example.citizensecurity.evidence

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.reports.ReportsActivity
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Isolated saved reports and intercepted chooser: this suite sends nothing to another app. */
class ReportEvidenceUiTest {
    @get:Rule(order = 0) val fixtureRule = ReportTestFixtureRule()
    @get:Rule(order = 1) val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as ReportTestApplication
    private var scenario: ActivityScenario<ReportsActivity>? = null
    private val choosers = ConcurrentLinkedQueue<Intent>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != Intent.ACTION_CHOOSER) return null
            choosers.add(Intent(intent))
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    @Before fun startChooserMonitor() {
        app.accountSession.signOut()
        instrumentation.addMonitor(monitor)
    }

    @After fun closeOwnedActivityAndMonitor() {
        scenario?.close()
        instrumentation.removeMonitor(monitor)
        app.accountSession.signOut()
    }

    @Test fun prepareRequiresConfirmationPhotoDefaultsOffAndLocationCanBeOmittedWithoutSharing() {
        val report = seed(withPhoto = true)
        open(report)
        awaitTag("evidence_prepare")
        assertNoPreparedPdfOrChooser()
        ui.onNodeWithTag("evidence_prepare").performScrollTo().performClick()
        ui.onNodeWithTag("evidence_photo").assertIsEnabled().assertIsOff()
        ui.onNodeWithTag("evidence_location").assertIsOn().performClick().assertIsOff()
        assertNoPreparedPdfOrChooser()
        ui.onNodeWithTag("evidence_confirm").performClick()
        awaitTag("evidence_ready")
        ui.onNodeWithText("Punto y referencia: omitidos. Fotografía: omitida.").performScrollTo()
        assertTrue(choosers.isEmpty())
        scenario!!.onActivity {
            val ready = model(it).state.value as ReportEvidenceState.Ready
            assertFalse(ready.request.options.includeLocation)
            assertFalse(ready.request.options.includePhoto)
            assertEquals(report.id, ready.artifact.reportId)
        }
        assertEquals(report, runBlocking { fixtureRule.fixture.repository.findById(report.id) })
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun shareOpensOneReadOnlyPdfChooserWithClipDataAndDoesNotClaimDelivery() {
        val report = seed()
        open(report)
        preparePdf()
        assertTrue(choosers.isEmpty())
        ui.onNodeWithTag("evidence_share").performScrollTo().performClick()
        awaitTag("evidence_chooser_opened")
        assertEquals(1, choosers.size)
        val chooser = choosers.single()
        assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, chooser.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
        @Suppress("DEPRECATION")
        val send = checkNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        @Suppress("DEPRECATION")
        val uri = checkNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("content", uri.scheme)
        assertEquals("${app.packageName}.evidence", uri.authority)
        assertTrue(uri.path!!.startsWith("/incident_pdf/"))
        assertEquals(uri, send.clipData!!.getItemAt(0).uri)
        assertEquals(uri, chooser.clipData!!.getItemAt(0).uri)
        assertEquals(1, send.clipData!!.itemCount)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, send.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
        assertEquals("application/pdf", app.contentResolver.getType(uri))
        app.contentResolver.openInputStream(uri)!!.use { input ->
            val header = ByteArray(4)
            assertEquals(4, input.read(header))
            assertEquals("%PDF", header.toString(Charsets.US_ASCII))
        }
        ui.onNodeWithTag("evidence_chooser_opened").assertTextContains("no puede confirmar", substring = true)
        assertTrue(ui.onAllNodesWithTag("evidence_share").fetchSemanticsNodes().isEmpty())
        assertEquals(report, runBlocking { fixtureRule.fixture.repository.findById(report.id) })
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun cancelOptionsDoesNotPrepareOrOpenChooser() {
        open(seed())
        awaitTag("evidence_prepare")
        ui.onNodeWithTag("evidence_prepare").performScrollTo().performClick()
        ui.onNodeWithText("Cancelar").performClick()
        assertNoPreparedPdfOrChooser()
        scenario!!.onActivity { assertSame(ReportEvidenceState.Idle, model(it).state.value) }
    }

    @Test fun returningToListClearsPreparedFolioAndAnotherFolioCannotShareIt() {
        val old = seed()
        val fresh = seed()
        open(old)
        preparePdf()
        ui.onNodeWithTag("report_back_list").performScrollTo().performClick()
        awaitTag("report_list")
        scenario!!.onActivity { assertSame(ReportEvidenceState.Idle, model(it).state.value) }
        ui.onNodeWithTag("report_search").performScrollTo().performTextReplacement(fresh.id)
        ui.onNodeWithTag("report_query").performScrollTo().performClick()
        awaitTag("evidence_prepare")
        ui.onNodeWithTag("report_folio").performScrollTo().assertTextContains(fresh.id)
        assertNoPreparedPdfOrChooser()
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun cancelLoadingInContentBlocksLateProducerBeforeCommit() {
        val report = seed()
        val complete = CompletableDeferred<Unit>()
        val finished = CountDownLatch(1)
        val committed = AtomicBoolean(false)
        open(report)
        scenario!!.onActivity { activity ->
            val factory = ReportEvidenceViewModel.factory(ReportEvidenceProducer { request, check ->
                withContext(NonCancellable) {
                    try { complete.await(); check(); committed.set(true) } finally { finished.countDown() }
                }
                ReportEvidenceArtifact(UUID.randomUUID().toString(), request.reportId, Instant.now(), 12L, "a".repeat(64), 1)
            })
            val model = ViewModelProvider(activity, factory)["cancel_content_fixture", ReportEvidenceViewModel::class.java]
            activity.setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ReportEvidenceContent(report, ZoneId.of("America/Mexico_City"), model, { true }) { _, _ ->
                            fail("Cancel triggered share")
                        }
                    }
                }
            }
        }
        awaitTag("evidence_prepare")
        ui.onNodeWithTag("evidence_prepare").performScrollTo().performClick()
        ui.onNodeWithTag("evidence_confirm").performClick()
        awaitTag("evidence_loading")
        ui.onNodeWithText("Cancelar preparación").performScrollTo().performClick()
        complete.complete(Unit)
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        ui.waitForIdle()
        assertFalse(committed.get())
        assertNoPreparedPdfOrChooser()
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun fileProviderDoesNotExposeReportDatabaseOrEntireCache() {
        val authority = "${app.packageName}.evidence"
        val provider = checkNotNull(app.packageManager.resolveContentProvider(authority, 0))
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        listOf(app.getDatabasePath(fixtureRule.fixture.databaseName), File(app.cacheDir, "outside_evidence.pdf")).forEach {
            try { FileProvider.getUriForFile(app, authority, it); fail("Provider exposed $it") }
            catch (_: IllegalArgumentException) { }
        }
        assertTrue(choosers.isEmpty())
    }

    private fun open(report: Report) {
        scenario = ActivityScenario.launch(Intent(app, ReportsActivity::class.java)
            .putExtra(ReportsActivity.EXTRA_FOLIO, report.id))
    }
    private fun preparePdf() {
        awaitTag("evidence_prepare")
        ui.onNodeWithTag("evidence_prepare").performScrollTo().performClick()
        ui.onNodeWithTag("evidence_confirm").performClick()
        awaitTag("evidence_ready")
    }
    private fun assertNoPreparedPdfOrChooser() {
        assertTrue(ui.onAllNodesWithTag("evidence_ready").fetchSemanticsNodes().isEmpty())
        assertTrue(choosers.isEmpty())
    }
    private fun awaitTag(tag: String) {
        ui.waitUntil(20_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun model(activity: ReportsActivity) = ViewModelProvider(activity)[ReportEvidenceViewModel::class.java]
    private fun seed(withPhoto: Boolean = false): Report = runBlocking {
        fixtureRule.fixture.publishFreshFix()
        val photo = if (withPhoto) {
            val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val bytes = try { ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)); output.toByteArray()
            } } finally { bitmap.recycle() }
            val photoId = UUID.randomUUID().toString()
            val identity = fixtureRule.fixture.databaseName.removePrefix("reports_ui_").removeSuffix(".db")
            val directory = File(app.filesDir, "photos_ui_$identity")
            require(directory.canonicalFile.parentFile == app.filesDir.canonicalFile)
            assertTrue(directory.isDirectory || directory.mkdir())
            File(directory, "$photoId.jpg").writeBytes(bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            ReportPhoto(photoId, hash, bytes.size.toLong(), 8, 8)
        } else null
        fixtureRule.fixture.repository.createOnce(UUID.randomUUID().toString(), NewReport(IncidentType.RISK,
            Priority.MEDIUM, "Hay una luminaria dañada frente al parque.", Instant.now().minusSeconds(30),
            ReportLocation("Frente al parque de prueba", 19.4326, -99.1332), photo))
    }
}
