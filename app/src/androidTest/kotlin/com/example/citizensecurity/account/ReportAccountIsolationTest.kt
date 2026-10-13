package com.example.citizensecurity.account

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.data.ReportStorageNames
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.preview.ReportDraftActivity
import com.example.citizensecurity.report.ReportDraftViewModel
import com.example.citizensecurity.reports.ReportsActivity
import com.example.citizensecurity.testing.ReportTestApplication
import com.example.citizensecurity.testing.ReportTestFixtureRule
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real independent account stores. The fixture phone does not count as physical GPS acceptance. */
class ReportAccountIsolationTest {
    @get:Rule val fixtureRule = ReportTestFixtureRule()
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ReportTestApplication
    private val workspaces = mutableListOf<ReportWorkspace>()
    private val scenarios = mutableListOf<ActivityScenario<*>>()
    private val alice = LocalAccount(UUID.randomUUID().toString(), "alice_1")
    private val bob = LocalAccount(UUID.randomUUID().toString(), "bob_123")

    @After fun closeOwnedScopes() {
        scenarios.forEach { it.close() }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.accountSession.signOut() }
        workspaces.forEach { it.close() }
    }

    @Test fun accountFolioPagesMapAndSimilarNeverIncludeAnotherAccountOrAcademicHistory() = runBlocking {
        fixtureRule.fixture.publishFreshFix()
        app.accountSession.signOut()
        val historical = workspace()
        val guestReport = historical.repository.createOnce(UUID.randomUUID().toString(), draft("Historia académica conservada"))
        app.accountSession.signIn(alice)
        val a = workspace()
        val aReport = a.repository.createOnce(UUID.randomUUID().toString(), draft("Reporte privado de la cuenta A"))
        assertNull(a.repository.findById(guestReport.id))
        assertEquals(listOf(aReport.id), a.listPageRepository.listPage(ReportListPageQuery()).reports.map { it.id })
        app.accountSession.signIn(bob)
        val b = workspace()
        assertNull(b.repository.findById(aReport.id))
        assertTrue(b.listPageRepository.listPage(ReportListPageQuery()).reports.isEmpty())
        val bReport = b.repository.createOnce(UUID.randomUUID().toString(), draft("Reporte privado de la cuenta B"))
        assertEquals(listOf(bReport.id), b.mapRepository.queryMarkers(ReportMapQuery(MapRegion(19.0, 20.0, -100.0, -99.0))).markers.map { it.id })
        val query = checkNotNull(SimilarReportQuery.fromDraft(draft("Consulta local de repetidos")))
        assertEquals(listOf(bReport.id), b.similarRepository.findSimilar(query).matches.map { it.report.id })
        try { a.repository.findById(aReport.id); fail("A stale account lease was accepted") }
        catch (_: IllegalStateException) { }
        app.accountSession.signOut()
        val academicAgain = workspace()
        assertEquals(guestReport, academicAgain.repository.findById(guestReport.id))
        assertNull(academicAgain.repository.findById(aReport.id))
        assertNull(academicAgain.repository.findById(bReport.id))
        app.accountSession.signIn(alice)
        assertEquals(aReport, workspace().repository.findById(aReport.id))
    }

    @Test fun pendingJournalCheckpointAndPhotoRemainWithTheirOriginalAccount() = runBlocking {
        app.accountSession.signIn(alice)
        val a = workspace()
        val photo = ownedJpeg(alice.id)
        val body = draft("Borrador con foto de la cuenta A").copy(photo = photo)
        val request = ReportSubmissionRequest(UUID.randomUUID().toString(), body, Instant.now())
        val checkpoint = ReportDraftCheckpoint(UUID.randomUUID().toString(), body, Instant.now())
        a.submissionJournal.write(request)
        a.checkpointJournal.write(checkpoint, null)
        assertEquals(photo.byteCount.toInt(), a.readPhoto(photo).size)
        app.accountSession.signIn(bob)
        val b = workspace()
        assertNull(b.submissionJournal.read())
        assertNull(b.checkpointJournal.read())
        try { b.readPhoto(photo); fail("Another account's photo was exposed") }
        catch (_: IOException) { }
        app.accountSession.signOut()
        val academic = workspace()
        assertNull(academic.submissionJournal.read())
        assertNull(academic.checkpointJournal.read())
        app.accountSession.signIn(alice)
        val reopened = workspace()
        assertEquals(request, reopened.submissionJournal.read())
        assertEquals(checkpoint, reopened.checkpointJournal.read())
        assertEquals(photo.byteCount.toInt(), reopened.readPhoto(photo).size)
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    @Test fun oldScreenEndsOnAccountChangeWithoutClosingTheNewAccountsScreen() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.accountSession.signIn(alice) }
        val old = ActivityScenario.launch<ReportsActivity>(ReportWorkspaceActivityBinding.stamp(
            Intent(app, ReportsActivity::class.java), app.accountSession.capture(),
        )).also(scenarios::add)
        var oldActivity: ReportsActivity? = null
        old.onActivity { oldActivity = it }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.accountSession.signIn(bob) }
        val fresh = ActivityScenario.launch<ReportsActivity>(ReportWorkspaceActivityBinding.stamp(
            Intent(app, ReportsActivity::class.java), app.accountSession.capture(),
        )).also(scenarios::add)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertTrue(checkNotNull(oldActivity).isFinishing || checkNotNull(oldActivity).isDestroyed)
        fresh.onActivity { assertFalse(it.isFinishing) }
        assertEquals(bob, app.accountSession.account)
    }

    @Test fun rotatingWithinTheSameAccountRetainsItsDraftWithoutLocationRequests() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { app.accountSession.signIn(alice) }
        val scenario = ActivityScenario.launch<ReportDraftActivity>(ReportWorkspaceActivityBinding.stamp(
            Intent(app, ReportDraftActivity::class.java), app.accountSession.capture(),
        )).also(scenarios::add)
        var original: ReportDraftViewModel? = null
        scenario.onActivity {
            original = it.draftModel
            it.draftModel.setDescription("Texto conservado solo en la cuenta A")
        }
        scenario.recreate()
        scenario.onActivity {
            assertSame(original, it.draftModel)
            assertEquals("Texto conservado solo en la cuenta A", it.draftModel.state.value.description)
        }
        assertEquals(0, fixtureRule.fixture.refreshCalls)
    }

    private fun workspace() = app.openReportWorkspace(app.accountSession.capture()).also(workspaces::add)
    private fun draft(description: String) = NewReport(IncidentType.RISK, Priority.MEDIUM, description,
        Instant.now().minusSeconds(30), ReportLocation("Frente al parque de prueba", 19.4326, -99.1332))

    private fun ownedJpeg(accountId: String): ReportPhoto {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val bytes = try { ByteArrayOutputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)); output.toByteArray()
        } } finally { bitmap.recycle() }
        val id = UUID.randomUUID().toString()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val directory = File(app.filesDir, ReportStorageNames.fromAccountId(accountId).photoDirectoryName)
        assertTrue(directory.isDirectory || directory.mkdirs())
        File(directory, "$id.jpg").writeBytes(bytes)
        return ReportPhoto(id, hash, bytes.size.toLong(), 8, 8)
    }
}
