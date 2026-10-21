package com.example.citizensecurity.acceptance

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.data.ReportDraftCheckpointStore
import com.example.citizensecurity.data.ReportPhotoStore
import com.example.citizensecurity.data.ReportSubmissionStore
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.domain.*
import com.example.citizensecurity.report.ReportDraftCheckpointViewModel
import com.example.citizensecurity.report.ReportSubmissionState
import com.example.citizensecurity.report.ReportSubmissionViewModel
import com.example.citizensecurity.resolution.ReportResolutionState
import com.example.citizensecurity.resolution.ReportResolutionViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in, two-process storage acceptance using the installed real Application and product
 * SQLite/AtomicFile/ViewModels. Run seed(), force-stop the package, then verifyAfterDeath()
 * with the same fresh processDeathNonce and AndroidJUnitRunner. The PID must change.
 * Names are unique and retained for inspection; no user store is cleared or replaced.
 * This tests durable recovery, not GPS, camera hardware, navigation or account enrollment.
 */
@RunWith(AndroidJUnit4::class)
class ReportProcessDeathAcceptanceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val now = Instant.parse("2026-10-18T18:00:00.123456789Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun seed(): Unit = runBlocking {
        val token = requireOptIn()
        val database = context.getDatabasePath(databaseName(token))
        val bridge = file(bridgeName(token))
        val photosDirectory = file(photoDirectoryName(token))
        listOf(database, bridge, photosDirectory).forEach {
            assertFalse("Never replace an earlier acceptance fixture: ${it.name}", it.exists())
        }
        durableNames(token).forEach { name ->
            listOf("", ".bak", ".new").forEach { suffix -> assertFalse(file(name + suffix).exists()) }
        }
        assertTrue(photosDirectory.mkdir())
        val jpeg = jpeg()
        val photo = ReportPhoto(id(token, "photo"), sha256(jpeg), jpeg.size.toLong(), 24, 16)
        writeNew(File(photosDirectory, "${photo.id}.jpg"), jpeg)
        val photos = ReportPhotoStore(context, photoDirectoryName(token))
        photos.check(photo)
        val draft = draft(photo)
        val checkpoint = ReportDraftCheckpoint(id(token, "checkpoint"), draft, now)
        val pendingDraft = draft.copy(description = "Copia pendiente de aceptación, sin incidente real.")
        val pending = ReportSubmissionRequest(id(token, "pending"), pendingDraft, now)
        val submittedJournal = ReportSubmissionStore(context, submittedJournalName(token))
        val pendingJournal = ReportSubmissionStore(context, pendingJournalName(token))
        val submittedCheckpoint = ReportDraftCheckpointStore(context, submittedCheckpointName(token))
        val pendingCheckpoint = ReportDraftCheckpointStore(context, pendingCheckpointName(token))
        submittedCheckpoint.write(checkpoint, null)
        pendingCheckpoint.write(ReportDraftCheckpoint(id(token, "pending_checkpoint"), pendingDraft, now), null)

        var reviewed: ReportResolutionCommand? = null
        repository(token, createGuard = { it.photo?.let(photos::check) }).use { repository ->
            val writes = AtomicInteger()
            val model = onMain { ReportSubmissionViewModel(counted(repository, writes), submittedJournal, clock) }
            await(model.state) { it == ReportSubmissionState.Idle }
            onMain { model.save(draft, id(token, "report")) }
            val saved = await(model.state) { it is ReportSubmissionState.Saved } as ReportSubmissionState.Saved
            assertEquals(1, writes.get())
            assertEquals(id(token, "report"), saved.report.id)
            submittedCheckpoint.markSubmitted(checkpoint.id, saved.report.id)

            // A second durable request has not reached SQLite. Opening another process must
            // show Pending, never insert it merely because it appears in the journal.
            pendingJournal.write(pending)
            val pendingModel = onMain { ReportSubmissionViewModel(counted(repository, writes), pendingJournal, clock) }
            val pendingState = await(pendingModel.state) { it is ReportSubmissionState.Pending } as ReportSubmissionState.Pending
            assertEquals(pending, pendingState.request)
            assertEquals(1, writes.get())

            val calls = AtomicInteger()
            val resolution = onMain { ReportResolutionViewModel(resolutions(repository, calls, loseResponse = true)) }
            assertTrue(onMain { resolution.activate(saved.report.id) })
            val ready = await(resolution.state) { it is ReportResolutionState.Ready } as ReportResolutionState.Ready
            assertEquals(ReportResolution.UNCONFIRMED, ready.page.resolution)
            assertTrue(onMain { resolution.select(ready, ReportResolution.RESOLVED) })
            val selected = resolution.state.value as ReportResolutionState.Ready
            assertTrue(onMain { resolution.setReason(selected, RESOLUTION_REASON) })
            val reasoned = resolution.state.value as ReportResolutionState.Ready
            assertTrue(onMain { resolution.review(reasoned) })
            val review = await(resolution.state) { it is ReportResolutionState.Reviewing } as ReportResolutionState.Reviewing
            reviewed = review.command
            assertTrue(onMain { resolution.confirm(review) })
            val uncertain = await(resolution.state) { it is ReportResolutionState.Uncertain } as ReportResolutionState.Uncertain
            assertEquals(review.command, uncertain.command)
            assertFalse(uncertain.canRetry)
            assertEquals(1, calls.get())
            assertTrue(requireNotNull(repository.findResolutionReceipt(review.command)).matches(review.command))
            assertEquals(1, repository.list().size)
            assertNull(repository.findById(pending.requestId))
            // Keep the unresolved in-memory command until this process is terminated.
            retainedModels.addAll(listOf(model, pendingModel, resolution))
        }

        assertIntegrity(database)
        val receipt = JSONObject().put("format", 1).put("nonce", token).put("seedPid", Process.myPid())
            .put("databaseSha256", sha256(database.readBytes()))
            .put("photoSha256", photo.sha256).put("photoBytes", photo.byteCount)
            .put("resolutionOperationId", requireNotNull(reviewed).operationId)
        val durableHashes = JSONObject()
        durableNames(token).forEach { name -> durableHashes.put(name, sha256(file(name).readBytes())) }
        receipt.put("durableHashes", durableHashes)
        writeNew(bridge, receipt.toString().toByteArray(Charsets.UTF_8))
        println("PROCESS_DEATH_SEED pid=${Process.myPid()} nonce=$token rows=1 resolutionEvents=1")
    }

    @Test fun verifyAfterDeath(): Unit = runBlocking {
        val token = requireOptIn()
        val bridge = file(bridgeName(token))
        assertTrue("Run seed in a prior process with the same nonce", bridge.isFile)
        require(bridge.length() in 1..MAX_BRIDGE_BYTES.toLong())
        val receipt = JSONObject(bridge.readText(Charsets.UTF_8))
        assertEquals(1, receipt.getInt("format"))
        assertEquals(token, receipt.getString("nonce"))
        assertNotEquals("This acceptance requires a genuinely different process", receipt.getInt("seedPid"), Process.myPid())
        assertTrue("No seed ViewModel can survive process death", retainedModels.isEmpty())
        val database = context.getDatabasePath(databaseName(token))
        assertEquals(receipt.getString("databaseSha256"), sha256(database.readBytes()))
        assertIntegrity(database)
        assertDurableHashes(token, receipt)
        val photo = ReportPhoto(id(token, "photo"), receipt.getString("photoSha256"), receipt.getLong("photoBytes"), 24, 16)
        val photos = ReportPhotoStore(context, photoDirectoryName(token))
        assertEquals(photo.sha256, sha256(photos.read(photo)))
        assertArrayEquals(jpeg(), photos.read(photo))
        val expectedDraft = draft(photo)
        val pendingDraft = expectedDraft.copy(description = "Copia pendiente de aceptación, sin incidente real.")
        val submittedJournal = ReportSubmissionStore(context, submittedJournalName(token))
        val pendingJournal = ReportSubmissionStore(context, pendingJournalName(token))
        val submittedCheckpoint = ReportDraftCheckpointStore(context, submittedCheckpointName(token))
        val pendingCheckpoint = ReportDraftCheckpointStore(context, pendingCheckpointName(token))
        assertEquals(ReportSubmissionRequest(id(token, "report"), expectedDraft, now), submittedJournal.read())
        assertEquals(ReportSubmissionRequest(id(token, "pending"), pendingDraft, now), pendingJournal.read())
        assertEquals(ReportDraftCheckpoint(id(token, "checkpoint"), expectedDraft, now, id(token, "report")), submittedCheckpoint.read())
        assertEquals(ReportDraftCheckpoint(id(token, "pending_checkpoint"), pendingDraft, now), pendingCheckpoint.read())

        var createGuardCalls = 0
        repository(token, createGuard = { createGuardCalls++; error("Recovery must not insert or request location") }).use { repository ->
            val writes = AtomicInteger()
            val recovered = onMain { ReportSubmissionViewModel(counted(repository, writes), submittedJournal, clock) }
            val saved = await(recovered.state) { it is ReportSubmissionState.Saved } as ReportSubmissionState.Saved
            assertEquals(id(token, "report"), saved.report.id)
            assertEquals(expectedDraft.description, saved.report.description)
            assertEquals(photo, saved.report.photo)
            assertEquals(ReportStatus.REPORTED, saved.report.status)
            val restoredPending = onMain { ReportSubmissionViewModel(counted(repository, writes), pendingJournal, clock) }
            val pending = await(restoredPending.state) { it is ReportSubmissionState.Pending } as ReportSubmissionState.Pending
            assertEquals(id(token, "pending"), pending.request.requestId)
            assertEquals(pendingDraft, pending.request.draft)
            val checkpoint = onMain { ReportDraftCheckpointViewModel(pendingCheckpoint, clock) }
            val copy = await(checkpoint.state) { it.loaded && !it.busy }
            assertEquals(pendingCheckpoint.read(), copy.checkpoint)
            assertNull("Reading a checkpoint must not restore a form automatically", copy.restoreReady)
            assertNull(copy.uncertainWrite)

            val resolutionWrites = AtomicInteger()
            val resolution = onMain { ReportResolutionViewModel(resolutions(repository, resolutionWrites)) }
            assertEquals(ReportResolutionState.Idle, resolution.state.value)
            assertTrue(onMain { resolution.activate(saved.report.id) })
            val ready = await(resolution.state) { it is ReportResolutionState.Ready } as ReportResolutionState.Ready
            assertEquals(ReportResolution.RESOLVED, ready.page.resolution)
            assertEquals(1L, ready.page.latestSequence)
            assertEquals(1, ready.page.events.size)
            assertEquals(receipt.getString("resolutionOperationId"), ready.page.events.single().operationId)
            assertEquals(RESOLUTION_REASON, ready.page.events.single().reason)
            assertEquals(ReportResolutionActor(id(token, "author"), ReportResolutionActorKind.AUTHOR), ready.page.events.single().actor)
            assertNull("A prior in-memory command must not be restored or retried", ready.target)
            assertEquals("", ready.reason)
            assertFalse(ready.blockedByPending)
            assertFalse(ready.canReview)
            assertEquals(0, resolutionWrites.get())
            assertEquals(0, writes.get())
            assertEquals(0, createGuardCalls)
            assertEquals(1, repository.list().size)
            assertNull(repository.findById(pending.request.requestId))
            val history = requireNotNull(repository.readHistory(ReportHistoryQuery(saved.report.id)))
            assertEquals(1L, history.latestSequence)
            assertEquals(ReportStatusEventKind.CREATED, history.events.single().kind)
            retainedModels.addAll(listOf(recovered, restoredPending, checkpoint, resolution))
        }
        assertDurableHashes(token, receipt)
        assertEquals("Read-only recovery must preserve all database bytes", receipt.getString("databaseSha256"), sha256(database.readBytes()))
        assertEquals(photo.sha256, sha256(photos.read(photo)))
        assertIntegrity(database)
        println("PROCESS_DEATH_VERIFIED seedPid=${receipt.getInt("seedPid")} pid=${Process.myPid()} rows=1 resolutionEvents=1 automaticWrites=0")
    }

    private fun repository(token: String, createGuard: (NewReport) -> Unit) = SqliteReportRepository(
        context, databaseName(token), clock = clock, createGuard = createGuard, authorAccountId = id(token, "author"),
        resolutionAuthority = { _, creator ->
            check(creator == id(token, "author"))
            ReportResolutionActor(id(token, "author"), ReportResolutionActorKind.AUTHOR)
        },
    )

    private fun counted(repository: SqliteReportRepository, writes: AtomicInteger) = object : IdempotentReportRepository by repository {
        override suspend fun createOnce(requestId: String, draft: NewReport): Report {
            writes.incrementAndGet()
            return repository.createOnce(requestId, draft)
        }
    }

    private fun resolutions(repository: SqliteReportRepository, writes: AtomicInteger, loseResponse: Boolean = false) = object : ReportResolutionRepository {
        override suspend fun read(query: ReportResolutionQuery) = repository.readResolution(query)
        override suspend fun receipt(command: ReportResolutionCommand) = repository.findResolutionReceipt(command)
        override suspend fun record(command: ReportResolutionCommand): ReportResolutionEvent {
            writes.incrementAndGet()
            check(loseResponse) { "Recovery must never invoke record" }
            repository.recordResolution(command)
            throw IOException("Controlled response loss after the real SQL commit")
        }
    }

    private fun requireOptIn(): String {
        val supplied = InstrumentationRegistry.getArguments().getString("processDeathNonce")
        assumeTrue("Manual two-process acceptance requires processDeathNonce", !supplied.isNullOrEmpty())
        require(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Only the owned isolated emulator is supported" }
        require(instrumentation.javaClass.name == "androidx.test.runner.AndroidJUnitRunner") { "Use the real installed Application runner" }
        assertEquals(CitizenSecurityApplication::class.java, context.applicationContext.javaClass)
        return requireNotNull(supplied).also { require(UUID.fromString(it).toString() == it) }
    }

    private fun assertDurableHashes(token: String, receipt: JSONObject) {
        val hashes = receipt.getJSONObject("durableHashes")
        durableNames(token).forEach { name -> assertEquals("Durable bytes must survive: $name", hashes.getString(name), sha256(file(name).readBytes())) }
    }

    private fun assertIntegrity(database: File) {
        SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(5, it.version)
            it.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        }
        listOf("-wal", "-journal").forEach { suffix ->
            val sidecar = File(database.path + suffix)
            assertTrue("No pending writes may cross the controlled stop", !sidecar.exists() || sidecar.length() == 0L)
        }
    }

    private suspend fun <T> await(state: StateFlow<T>, predicate: (T) -> Boolean): T = withTimeout(15_000) { state.first(predicate) }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    private fun draft(photo: ReportPhoto) = NewReport(IncidentType.RISK, Priority.MEDIUM,
        "Copia controlada de aceptación de proceso, sin incidente real.", now.minusSeconds(60),
        ReportLocation("Referencia de aceptación sin GPS", 19.4326, -99.1332), photo)

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.rgb(42, 96, 140))
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private fun file(name: String): File {
        require('/' !in name && '\\' !in name)
        val parent = context.filesDir.canonicalFile
        return File(parent, name).also { assertEquals(parent, it.canonicalFile.parentFile) }
    }

    private fun writeNew(file: File, bytes: ByteArray) {
        require(bytes.size <= MAX_BRIDGE_BYTES || file.extension == "jpg")
        assertTrue("Never overwrite an acceptance fixture", file.createNewFile())
        FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    }

    private fun durableNames(token: String) = listOf(submittedJournalName(token), pendingJournalName(token),
        submittedCheckpointName(token), pendingCheckpointName(token))
    private fun submittedJournalName(token: String) = "process47_submitted_$token.json"
    private fun pendingJournalName(token: String) = "process47_pending_$token.json"
    private fun submittedCheckpointName(token: String) = "process47_checkpoint_$token.json"
    private fun pendingCheckpointName(token: String) = "process47_copy_$token.json"
    private fun bridgeName(token: String) = "process47_bridge_$token.json"
    private fun databaseName(token: String) = "process47_reports_$token.db"
    private fun photoDirectoryName(token: String) = "process47_photos_$token"
    private fun id(token: String, purpose: String) = UUID.nameUUIDFromBytes("$purpose:$token".toByteArray(Charsets.UTF_8)).toString()
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val MAX_BRIDGE_BYTES = 8_192
        const val RESOLUTION_REASON = "Resultado controlado de aceptación local, sin intervención de autoridades."
        val retainedModels = mutableListOf<Any>()
    }
}
