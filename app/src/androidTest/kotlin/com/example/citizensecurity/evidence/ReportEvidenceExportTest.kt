package com.example.citizensecurity.evidence

import android.content.Context
import android.content.ContextWrapper
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** All generated files belong to a uniquely named test cache; no production cache cleanup. */
class ReportEvidenceExportTest {
    @get:Rule val fixtureRule = ReportTestFixtureRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ReportTestApplication
    private lateinit var cacheRoot: File
    private lateinit var context: Context
    private lateinit var workspace: ReportWorkspace
    private lateinit var service: ReportEvidenceExport
    private val directory get() = File(cacheRoot, ReportEvidenceExport.DIRECTORY_NAME)

    @Before fun ownScope() {
        app.accountSession.signOut()
        cacheRoot = File(app.cacheDir, "evidence_test_${UUID.randomUUID()}")
        assertTrue(cacheRoot.mkdir())
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getCacheDir(): File = cacheRoot
        }
        workspace = app.openReportWorkspace(app.accountSession.capture())
        service = ReportEvidenceExport(context, workspace, clock = Clock.fixed(EXPORTED, ZoneOffset.UTC))
    }

    @After fun closeOwnScope() {
        workspace.close()
        app.accountSession.signOut()
        // Only this generated fixture directory may be removed, never the app's export directory.
        require(cacheRoot.canonicalFile.parentFile == app.cacheDir.canonicalFile)
        require(cacheRoot.name.startsWith("evidence_test_"))
        cacheRoot.deleteRecursively()
    }

    @Test fun freshSavedFolioProducesAReadableBoundedPdfAndKeepsReportAndJournals() = runBlocking {
        val report = seed()
        val artifact = service.prepare(request(report)) {}
        val file = service.fileForSharing(artifact)
        val bytes = file.readBytes()
        assertEquals("%PDF", bytes.take(4).toByteArray().toString(Charsets.US_ASCII))
        assertEquals(report.id, artifact.reportId)
        assertEquals(EXPORTED, artifact.createdAt)
        assertEquals(bytes.size.toLong(), artifact.byteCount)
        assertEquals(hash(bytes), artifact.sha256)
        assertEquals("${artifact.token}.pdf", file.name)
        assertTrue(artifact.pageCount in 1..12)
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use {
            assertEquals(artifact.pageCount, it.pageCount)
            it.openPage(0).use { page -> assertTrue(page.width > 0 && page.height > 0) }
        }
        assertEquals(report, workspace.repository.findById(report.id))
        assertNull(workspace.submissionJournal.read())
        assertNull(workspace.checkpointJournal.read())
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun absentFolioDoesNotCreateAFileOrUseGps() = runBlocking {
        try { service.prepare(ReportEvidenceRequest(UUID.randomUUID().toString())) {}; fail("Missing report exported") }
        catch (_: IOException) { }
        assertFalse(directory.exists())
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun cancellationDuringPreparationRemovesOnlyItsTransientAndKeepsEarlierPdf() = runBlocking {
        val report = seed()
        val existing = service.prepare(request(report)) {}
        val previousBytes = service.fileForSharing(existing).readBytes()
        var checks = 0
        try {
            service.prepare(request(report)) { if (++checks >= 16) throw CancellationException("Left folio") }
            fail("Cancelled export returned")
        } catch (_: CancellationException) { }
        assertTrue(checks >= 16)
        assertEquals(listOf("${existing.token}.pdf"), directory.list()!!.toList())
        assertArrayEquals(previousBytes, service.fileForSharing(existing).readBytes())
        assertEquals(report, workspace.repository.findById(report.id))
    }

    @Test fun optedInAbsentPhotoFailsWithoutRemovingPriorPdfOrReport() = runBlocking {
        val report = seed()
        val existing = service.prepare(request(report)) {}
        try {
            service.prepare(request(report).copy(options = ReportEvidenceOptions(includePhoto = true))) {}
            fail("Absent photo exported")
        } catch (_: IOException) { }
        assertEquals(listOf("${existing.token}.pdf"), directory.list()!!.toList())
        assertTrue(service.fileForSharing(existing).isFile)
        assertEquals(report, workspace.repository.findById(report.id))
    }

    @Test fun exhaustedFileQuotaPreservesAllExistingCopies() = runBlocking {
        val report = seed()
        assertTrue(directory.mkdir())
        val previous = (1..ReportEvidenceExport.MAX_CACHE_FILES).map {
            File(directory, "${UUID.randomUUID()}.pdf").apply { writeText("fixture $it") }
        }
        val before = previous.associate { it.name to it.readText() }
        try { service.prepare(request(report)) {}; fail("Quota exceeded") } catch (_: ReportEvidenceQuotaException) { }
        assertEquals(before, directory.listFiles()!!.associate { it.name to it.readText() })
        assertEquals(report, workspace.repository.findById(report.id))
    }

    @Test fun damagedOrForgedReceiptCannotBeShared() = runBlocking {
        val report = seed()
        val artifact = service.prepare(request(report)) {}
        val file = service.fileForSharing(artifact)
        try { service.fileForSharing(artifact.copy(reportId = UUID.randomUUID().toString())); fail("Forged receipt shared") }
        catch (_: IOException) { }
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        try { service.fileForSharing(artifact); fail("Corrupt PDF shared") } catch (_: IOException) { }
        assertTrue(file.exists())
        assertEquals(report, workspace.repository.findById(report.id))
    }

    @Test fun revokedOwnerCannotExportOrShareAndAnotherSessionCannotReuseTheirReceipt() = runBlocking {
        val report = seed()
        val artifact = service.prepare(request(report)) {}
        app.accountSession.signOut()
        try { service.fileForSharing(artifact); fail("Revoked owner shared") } catch (_: IllegalStateException) { }
        try { service.prepare(request(report)) {}; fail("Revoked owner exported") } catch (_: IllegalStateException) { }
        val fresh = app.openReportWorkspace(app.accountSession.capture())
        try {
            val freshService = ReportEvidenceExport(context, fresh)
            try { freshService.fileForSharing(artifact); fail("Another session reused receipt") }
            catch (_: IOException) { }
            assertTrue(File(directory, "${artifact.token}.pdf").exists())
            assertEquals(report, fresh.repository.findById(report.id))
        } finally { fresh.close() }
    }

    @Test fun recreatedExporterInSameSessionCanCheckPreviouslyPreparedArtifact() = runBlocking {
        val report = seed()
        val artifact = service.prepare(request(report)) {}
        val recreated = ReportEvidenceExport(context, workspace)
        assertEquals(service.fileForSharing(artifact), recreated.fileForSharing(artifact))
    }

    private suspend fun seed(): Report {
        fixtureRule.fixture.publishFreshFix()
        return workspace.repository.createOnce(UUID.randomUUID().toString(), NewReport(IncidentType.RISK,
            Priority.MEDIUM, "Hay una luminaria dañada frente al parque.", Instant.now().minusSeconds(30),
            ReportLocation("Frente al parque de prueba", 19.4326, -99.1332)))
    }
    private fun request(report: Report) = ReportEvidenceRequest(report.id, zoneId = ZoneId.of("America/Mexico_City"))
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private companion object { val EXPORTED: Instant = Instant.parse("2026-10-13T17:00:00Z") }
}
