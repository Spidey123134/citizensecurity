package com.example.citizensecurity.release

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.MainActivity
import com.example.citizensecurity.SplashActivity
import com.example.citizensecurity.data.ReportDraftCheckpointStore
import com.example.citizensecurity.data.ReportPhotoStore
import com.example.citizensecurity.data.ReportSubmissionStore
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.data.TrustedContactsStore
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportDraftCheckpoint
import com.example.citizensecurity.domain.ReportHistoryQuery
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.ReportResolution
import com.example.citizensecurity.domain.ReportResolutionActor
import com.example.citizensecurity.domain.ReportResolutionActorKind
import com.example.citizensecurity.domain.ReportResolutionCommand
import com.example.citizensecurity.domain.ReportResolutionQuery
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportStatusChangeCommand
import com.example.citizensecurity.domain.ReportStatusEventKind
import com.example.citizensecurity.domain.ReportSubmissionRequest
import com.example.citizensecurity.domain.TrustedContact
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in acceptance across an actual 0.6.3 -> 0.7.0 install -r, in two invocations.
 * Uses the installed real Application and private nonce-owned stores. Never clears app data,
 * overwrites a previous fixture, grants an administrator role, calls or sends a message.
 * The same schema 5 is expected; an unchanged schema still needs a preservation check.
 */
@RunWith(AndroidJUnit4::class)
class Release070UpgradeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val now = Instant.parse("2026-10-21T18:00:00.123456789Z")

    @Test fun seed063(): Unit = runBlocking {
        val token = requireUpgradeOptIn()
        assertVersion("0.6.3", 31)
        val database = context.getDatabasePath(databaseName(token))
        val bridge = privateFile(bridgeName(token))
        val contactsDirectory = privateFile(contactsDirectoryName(token))
        val photosDirectory = privateFile(photoDirectoryName(token))
        listOf(database, bridge, contactsDirectory, photosDirectory).forEach {
            assertFalse("Never replace an earlier upgrade fixture: ${it.name}", it.exists())
        }
        durableNames(token).forEach { name ->
            listOf("", ".bak", ".new").forEach { suffix -> assertFalse(privateFile(name + suffix).exists()) }
        }
        assertTrue(contactsDirectory.mkdir())
        assertTrue(photosDirectory.mkdir())

        val jpeg = controlledJpeg()
        val photo = ReportPhoto(id(token, "photo"), sha256(jpeg), jpeg.size.toLong(), 24, 16)
        val photoFile = File(photosDirectory, "${photo.id}.jpg")
        writeNew(photoFile, jpeg)
        val photos = ReportPhotoStore(context, photoDirectoryName(token))
        photos.check(photo)
        assertArrayEquals(jpeg, photos.read(photo))

        // Supply every constructor argument reflectively. Seed must execute the installed
        // official 0.6.3 implementation, not a new Kotlin default-constructor bridge.
        installedRepository(token) { it.photo?.let(photos::check) }.use { repository ->
            repository.createOnce(id(token, "report"), draft(photo))
            transitions(token).forEach { repository.changeStatus(it) }
            repository.recordResolution(resolutionCommand(token))
            verifyStoredReport(repository, token, photo)
        }
        assertIntegrity(database)

        val checkpoint = ReportDraftCheckpoint(id(token, "checkpoint"), draft(photo), now)
        val checkpoints = ReportDraftCheckpointStore(context, checkpointName(token))
        checkpoints.write(checkpoint, null)
        checkpoints.markSubmitted(checkpoint.id, id(token, "report"))
        assertEquals(checkpoint.copy(submittedFolio = id(token, "report")), checkpoints.read())
        val request = pendingRequest(token, photo)
        ReportSubmissionStore(context, pendingName(token)).write(request)
        assertEquals(request, ReportSubmissionStore(context, pendingName(token)).read())

        val contactsStore = TrustedContactsStore(contactsContext(contactsDirectory), id(token, "owner"), {})
        val initial = contactsStore.read()
        assertNull(initial.revision)
        val savedContacts = contactsStore.replace(initial.revision, listOf(contact(token)))
        val contactsFile = File(contactsDirectory, TrustedContactsStore.fileName(id(token, "owner")))
        assertEquals(listOf(contact(token)), contactsStore.read().contacts)

        val receipt = JSONObject().put("format", 1).put("nonce", token).put("seedPid", Process.myPid())
            .put("sourceVersion", "0.6.3").put("sourceVersionCode", 31).put("schema", 5)
            .put("signerSha256", JSONArray(signatureDigests()))
            .put("databaseSha256", sha256(database.readBytes()))
            .put("contactsSha256", sha256(contactsFile.readBytes()))
            .put("contactsRevision", savedContacts.revision)
            .put("photoSha256", sha256(photoFile.readBytes())).put("photoBytes", photo.byteCount)
        val durable = JSONObject()
        durableNames(token).forEach { durable.put(it, sha256(privateFile(it).readBytes())) }
        receipt.put("durableSha256", durable)
        val bytes = receipt.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BRIDGE_BYTES)
        writeNew(bridge, bytes)
        println("UPGRADE070_SEED nonce=$token pid=${Process.myPid()} version=0.6.3 schema=5 rows=1 resolutionEvents=1 pendingRows=0")
    }

    @Test fun verify070(): Unit = runBlocking {
        val token = requireUpgradeOptIn()
        assertVersion("0.7.0", 32)
        val bridge = privateFile(bridgeName(token))
        assertTrue("Run seed063 on the official APK before install -r", bridge.isFile)
        require(bridge.length() in 1..MAX_BRIDGE_BYTES.toLong())
        val receipt = JSONObject(bridge.readText(Charsets.UTF_8))
        assertEquals(1, receipt.getInt("format"))
        assertEquals(token, receipt.getString("nonce"))
        assertEquals("0.6.3", receipt.getString("sourceVersion"))
        assertEquals(31, receipt.getInt("sourceVersionCode"))
        assertEquals(5, receipt.getInt("schema"))
        assertNotEquals("The installed update runs in a different process", receipt.getInt("seedPid"), Process.myPid())
        val previousSigners = receipt.getJSONArray("signerSha256")
        assertEquals((0 until previousSigners.length()).map(previousSigners::getString), signatureDigests())

        val database = context.getDatabasePath(databaseName(token))
        val contactsDirectory = privateFile(contactsDirectoryName(token))
        val contactsFile = File(contactsDirectory, TrustedContactsStore.fileName(id(token, "owner")))
        val photoFile = File(privateFile(photoDirectoryName(token)), "${id(token, "photo")}.jpg")
        // Check exact bytes before a current repository can open, migrate or otherwise touch them.
        assertBytes(receipt, database, contactsFile, photoFile, token)
        assertIntegrity(database)
        assertEquals(receipt.getString("databaseSha256"), sha256(database.readBytes()))

        val photo = ReportPhoto(id(token, "photo"), receipt.getString("photoSha256"), receipt.getLong("photoBytes"), 24, 16)
        installedRepository(token) { error("Reading an upgraded report must not invoke a create guard") }.use { repository ->
            verifyStoredReport(repository, token, photo)
            assertEquals(listOf(expectedReport(token, photo)), repository.list())
            assertNull("A durable pending request must not be inserted merely by updating", repository.findById(id(token, "pending")))
        }
        val contacts = TrustedContactsStore(contactsContext(contactsDirectory), id(token, "owner"), {}).read()
        assertEquals(receipt.getString("contactsRevision"), contacts.revision)
        assertEquals(listOf(contact(token)), contacts.contacts)
        val photos = ReportPhotoStore(context, photoDirectoryName(token))
        assertArrayEquals(controlledJpeg(), photos.read(photo))
        assertArrayEquals(photoFile.readBytes(), photos.read(photo))
        assertEquals(ReportDraftCheckpoint(id(token, "checkpoint"), draft(photo), now, id(token, "report")),
            ReportDraftCheckpointStore(context, checkpointName(token)).read())
        assertEquals(pendingRequest(token, photo), ReportSubmissionStore(context, pendingName(token)).read())
        assertBytes(receipt, database, contactsFile, photoFile, token)
        assertIntegrity(database)

        verifyNormalLauncherEntry()
        assertBytes(receipt, database, contactsFile, photoFile, token)
        println("UPGRADE070_VERIFIED nonce=$token seedPid=${receipt.getInt("seedPid")} pid=${Process.myPid()} version=0.7.0 schema=5 rows=1 resolutionEvents=1 pendingRows=0")
    }

    private fun requireUpgradeOptIn(): String {
        val supplied = InstrumentationRegistry.getArguments().getString("release070UpgradeNonce")
        assumeTrue("Manual two-APK acceptance requires release070UpgradeNonce", !supplied.isNullOrEmpty())
        require(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Only an owned isolated emulator is supported" }
        require(instrumentation.javaClass.name == "androidx.test.runner.AndroidJUnitRunner") { "Use the installed real Application runner" }
        assertEquals(CitizenSecurityApplication::class.java, context.applicationContext.javaClass)
        return requireNotNull(supplied).also { require(UUID.fromString(it).toString() == it) { "Use a fresh canonical UUID" } }
    }

    private fun installedRepository(token: String, createGuard: (NewReport) -> Unit): SqliteReportRepository {
        val constructor = SqliteReportRepository::class.java.constructors.single {
            it.parameterTypes.size == 8 && it.parameterTypes.first() == Context::class.java
        }
        val statusAuthority: (ReportStatusChangeCommand) -> String = { id(token, "author") }
        val resolutionAuthority: (String, String?) -> ReportResolutionActor = { reportId, creator ->
            check(reportId == id(token, "report") && creator == id(token, "author"))
            ReportResolutionActor(id(token, "author"), ReportResolutionActorKind.AUTHOR)
        }
        return constructor.newInstance(context, databaseName(token), Clock.fixed(now, ZoneOffset.UTC),
            Dispatchers.IO, createGuard, id(token, "author"), statusAuthority, resolutionAuthority) as SqliteReportRepository
    }

    private suspend fun verifyStoredReport(repository: SqliteReportRepository, token: String, photo: ReportPhoto) {
        assertEquals(expectedReport(token, photo), repository.findById(id(token, "report")))
        assertEquals(1, repository.list().size)
        val history = requireNotNull(repository.readHistory(ReportHistoryQuery(id(token, "report"))))
        assertEquals(ReportStatus.CLOSED, history.currentStatus)
        assertEquals(4L, history.latestSequence)
        assertTrue(history.completeFromCreation)
        assertFalse(history.hasMore)
        assertEquals(4, history.events.size)
        val events = history.events.reversed()
        val created = events.first()
        assertEquals(id(token, "report"), created.operationId)
        assertEquals(ReportStatusEventKind.CREATED, created.kind)
        assertEquals(1L, created.sequence)
        assertNull(created.fromStatus)
        assertEquals(ReportStatus.REPORTED, created.toStatus)
        assertEquals(id(token, "author"), created.actorAccountId)
        assertEquals(now, created.recordedAt)
        assertEquals("", created.reason)
        events.drop(1).zip(transitions(token)).forEach { (event, command) ->
            assertEquals(command.operationId, event.operationId)
            assertEquals(command.expectedSequence + 1L, event.sequence)
            assertEquals(ReportStatusEventKind.STATUS_CHANGED, event.kind)
            assertEquals(command.expectedStatus, event.fromStatus)
            assertEquals(command.targetStatus, event.toStatus)
            assertEquals(command.reason, event.reason)
            assertEquals(id(token, "author"), event.actorAccountId)
            assertEquals(now, event.recordedAt)
        }
        val result = requireNotNull(repository.readResolution(ReportResolutionQuery(id(token, "report"))))
        assertEquals(ReportResolution.RESOLVED, result.resolution)
        assertEquals(1L, result.latestSequence)
        assertFalse(result.hasMore)
        assertEquals(1, result.events.size)
        val command = resolutionCommand(token)
        val event = result.events.single()
        assertTrue(event.matches(command))
        assertEquals(now, event.recordedAt)
        assertEquals(command.actor, result.actor)
        assertEquals(event, repository.findResolutionReceipt(command))
        assertEquals(ReportStatus.CLOSED, repository.findById(id(token, "report"))?.status)
    }

    private fun transitions(token: String) = listOf(ReportStatus.REPORTED, ReportStatus.IN_REVIEW,
        ReportStatus.ATTENDED, ReportStatus.CLOSED).zipWithNext().mapIndexed { index, (from, to) ->
        ReportStatusChangeCommand(id(token, "transition_$index"), id(token, "report"), index + 1L,
            from, to, "Cambio de actualización ${index + 1}, sin intervención institucional.")
    }

    private fun resolutionCommand(token: String) = ReportResolutionCommand(id(token, "resolution"),
        id(token, "report"), 0L, ReportResolution.UNCONFIRMED, ReportResolution.RESOLVED,
        ReportResolutionActor(id(token, "author"), ReportResolutionActorKind.AUTHOR),
        "Declaración controlada anterior a la actualización, sin incidente real.")

    private fun draft(photo: ReportPhoto) = NewReport(IncidentType.RISK, Priority.MEDIUM,
        "Reporte controlado de actualización, sin incidente real.", now.minusSeconds(60),
        ReportLocation("Referencia de actualización sin GPS", 19.4326, -99.1332), photo)

    private fun expectedReport(token: String, photo: ReportPhoto): Report {
        val value = draft(photo)
        return Report(id(token, "report"), value.type, value.priority, value.description, value.occurredAt,
            value.location, now, ReportStatus.CLOSED, photo)
    }

    private fun pendingRequest(token: String, photo: ReportPhoto) = ReportSubmissionRequest(id(token, "pending"),
        draft(photo).copy(description = "Solicitud pendiente de actualización, todavía sin reporte guardado."), now)

    private fun assertBytes(receipt: JSONObject, database: File, contacts: File, photo: File, token: String) {
        assertEquals(receipt.getString("databaseSha256"), sha256(database.readBytes()))
        assertEquals(receipt.getString("contactsSha256"), sha256(contacts.readBytes()))
        assertEquals(receipt.getString("photoSha256"), sha256(photo.readBytes()))
        val durable = receipt.getJSONObject("durableSha256")
        durableNames(token).forEach { assertEquals(it, durable.getString(it), sha256(privateFile(it).readBytes())) }
    }

    private fun verifyNormalLauncherEntry() {
        val entry = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        assertEquals(Intent.ACTION_MAIN, entry.action)
        assertTrue(entry.categories.orEmpty().contains(Intent.CATEGORY_LAUNCHER))
        assertEquals(ComponentName(context, SplashActivity::class.java), entry.component)
        var scenario: ActivityScenario<Activity>? = null
        try {
            scenario = ActivityScenario.launch(entry)
            val deadline = SystemClock.elapsedRealtime() + 15_000
            var loginShown = false
            while (!loginShown && SystemClock.elapsedRealtime() < deadline) {
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    loginShown = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .any { it is MainActivity }
                }
                if (!loginShown) SystemClock.sleep(25)
            }
            assertTrue("The public launcher must reach the normal login", loginShown)
            assertNull((context.applicationContext as CitizenSecurityApplication).accountSession.account)
        } finally {
            scenario?.close()
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filter { it.packageName == context.packageName }.forEach(Activity::finish)
            }
            instrumentation.waitForIdleSync()
        }
    }

    @Suppress("DEPRECATION")
    private fun signatureDigests(): List<String> {
        val info = context.packageManager.getPackageInfo(context.packageName,
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        return requireNotNull(signatures).map { sha256(it.toByteArray()) }.sorted().also { require(it.isNotEmpty()) }
    }

    @Suppress("DEPRECATION")
    private fun assertVersion(name: String, code: Int) {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals(name, installed.versionName)
        assertEquals(code, installed.versionCode)
    }

    private fun assertIntegrity(database: File) {
        assertTrue(database.isFile)
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
            assertTrue("No pending database writes may cross the update", !sidecar.exists() || sidecar.length() == 0L)
        }
    }

    private fun contact(token: String) = TrustedContact(id(token, "contact"), "Contacto de actualización", "+525500000000")
    private fun contactsContext(directory: File): Context = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
    }

    private fun controlledJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.rgb(24, 90, 150))
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
                output.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    private fun privateFile(name: String): File {
        require('/' !in name && '\\' !in name)
        val parent = context.filesDir.canonicalFile
        return File(parent, name).also { assertEquals(parent, it.canonicalFile.parentFile) }
    }

    private fun writeNew(file: File, bytes: ByteArray) {
        assertTrue("Never replace an existing acceptance fixture", file.createNewFile())
        FileOutputStream(file).use { output -> output.write(bytes); output.fd.sync() }
    }

    private fun durableNames(token: String) = listOf(checkpointName(token), pendingName(token))
    private fun checkpointName(token: String) = "release070_checkpoint_$token.json"
    private fun pendingName(token: String) = "release070_pending_$token.json"
    private fun bridgeName(token: String) = "release070_upgrade_$token.json"
    private fun contactsDirectoryName(token: String) = "release070_contacts_$token"
    private fun databaseName(token: String) = "reports_upgrade070_$token.db"
    private fun photoDirectoryName(token: String) = "upgrade070_$token"
    private fun id(token: String, purpose: String) = UUID.nameUUIDFromBytes("$purpose:$token".toByteArray(Charsets.UTF_8)).toString()
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object { const val MAX_BRIDGE_BYTES = 8_192 }
}
