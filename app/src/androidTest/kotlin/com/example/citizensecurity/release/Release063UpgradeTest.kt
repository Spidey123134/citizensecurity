package com.example.citizensecurity.release

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.data.ReportPhotoStore
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.data.TrustedContactsStore
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportHistoryQuery
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.ReportResolution
import com.example.citizensecurity.domain.ReportResolutionQuery
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportStatusChangeCommand
import com.example.citizensecurity.domain.ReportStatusEventKind
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
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in acceptance, selected in two invocations around an actual adb install -r.
 * Requires AndroidJUnitRunner (the real installed Application), an emulator and a fresh nonce.
 * Own fixture names are retained; this test never clears app data, assigns roles or sends messages.
 */
@RunWith(AndroidJUnit4::class)
class Release063UpgradeTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val now = Instant.parse("2026-10-18T18:00:00.123456789Z")

    @Test fun seed062(): Unit = runBlocking {
        val token = requireUpgradeOptIn()
        assertVersion("0.6.2", 30)
        val database = context.getDatabasePath(databaseName(token))
        val bridge = bridgeFile(token)
        val contactsDirectory = privateFile(contactsDirectoryName(token))
        val photosDirectory = privateFile(photoDirectoryName(token))
        assertFalse("Never overwrite an earlier database", database.exists())
        assertFalse("Never overwrite an earlier bridge", bridge.exists())
        assertFalse("Never reuse a contacts directory", contactsDirectory.exists())
        assertFalse("Never reuse a photo directory", photosDirectory.exists())
        assertTrue(contactsDirectory.mkdir())
        assertTrue(photosDirectory.mkdir())

        val jpeg = controlledJpeg()
        val photo = ReportPhoto(id(token, "photo"), sha256(jpeg), jpeg.size.toLong(), 24, 16)
        val photoFile = File(photosDirectory, "${photo.id}.jpg")
        writeNew(photoFile, jpeg)
        val photoStore = ReportPhotoStore(context, photoDirectoryName(token))
        photoStore.check(photo)
        assertArrayEquals(jpeg, photoStore.read(photo))

        old062Repository(token, photoStore).use { repository ->
            repository.createOnce(id(token, "report"), draft(photo))
            transitions(token).forEach { repository.changeStatus(it) }
            assertEquals(expectedReport(token, photo), repository.findById(id(token, "report")))
            verifyHistory(repository, token)
        }
        readOnlyVersion(database, 4)
        assertNoPendingDatabaseWrites(database)

        val contacts = TrustedContactsStore(contactsContext(contactsDirectory), id(token, "owner"), {})
        val initial = contacts.read()
        assertEquals(null, initial.revision)
        val savedContacts = contacts.replace(initial.revision, listOf(contact(token)))
        val contactsFile = File(contactsDirectory, TrustedContactsStore.fileName(id(token, "owner")))
        assertEquals(listOf(contact(token)), contacts.read().contacts)
        val receipt = JSONObject().put("nonce", token)
            .put("databaseSha256", sha256(database.readBytes()))
            .put("contactsSha256", sha256(contactsFile.readBytes()))
            .put("contactsRevision", savedContacts.revision)
            .put("photoSha256", sha256(photoFile.readBytes()))
            .put("photoBytes", photo.byteCount)
        val bytes = receipt.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BRIDGE_BYTES)
        writeNew(bridge, bytes)
    }

    @Test fun verify063(): Unit = runBlocking {
        val token = requireUpgradeOptIn()
        assertVersion("0.6.3", 31)
        val bridge = bridgeFile(token)
        assertTrue("Run seed062 on the official APK before install -r", bridge.isFile)
        require(bridge.length() in 1..MAX_BRIDGE_BYTES.toLong())
        val receipt = JSONObject(bridge.readText(Charsets.UTF_8))
        assertEquals(token, receipt.getString("nonce"))
        val database = context.getDatabasePath(databaseName(token))
        val contactsDirectory = privateFile(contactsDirectoryName(token))
        val contactsFile = File(contactsDirectory, TrustedContactsStore.fileName(id(token, "owner")))
        val photoFile = File(privateFile(photoDirectoryName(token)), "${id(token, "photo")}.jpg")

        // Verify exact previous bytes before any current product repository can migrate the file.
        assertEquals(receipt.getString("databaseSha256"), sha256(database.readBytes()))
        assertEquals(receipt.getString("contactsSha256"), sha256(contactsFile.readBytes()))
        assertEquals(receipt.getString("photoSha256"), sha256(photoFile.readBytes()))
        assertNoPendingDatabaseWrites(database)
        readOnlyVersion(database, 4)
        assertEquals(receipt.getString("databaseSha256"), sha256(database.readBytes()))

        val photo = ReportPhoto(id(token, "photo"), receipt.getString("photoSha256"),
            receipt.getLong("photoBytes"), 24, 16)
        SqliteReportRepository(context, databaseName(token),
            clock = Clock.fixed(now, ZoneOffset.UTC), createGuard = {}).use { repository ->
            assertEquals(expectedReport(token, photo), repository.findById(id(token, "report")))
            verifyHistory(repository, token)
            val result = requireNotNull(repository.readResolution(ReportResolutionQuery(id(token, "report"))))
            assertEquals(ReportResolution.UNCONFIRMED, result.resolution)
            assertEquals(0L, result.latestSequence)
            assertTrue(result.events.isEmpty())
            assertFalse(result.hasMore)
            assertEquals(null, result.actor)
            assertEquals(ReportStatus.CLOSED, repository.findById(id(token, "report"))?.status)
        }
        readOnlyVersion(database, 5)
        val contacts = TrustedContactsStore(contactsContext(contactsDirectory), id(token, "owner"), {}).read()
        assertEquals(receipt.getString("contactsRevision"), contacts.revision)
        assertEquals(listOf(contact(token)), contacts.contacts)
        assertEquals(receipt.getString("contactsSha256"), sha256(contactsFile.readBytes()))
        assertEquals(receipt.getString("photoSha256"), sha256(photoFile.readBytes()))
        assertArrayEquals(photoFile.readBytes(), ReportPhotoStore(context, photoDirectoryName(token)).read(photo))
        assertTrue("Keep the bridge for inspection", bridge.isFile)
    }

    private fun requireUpgradeOptIn(): String {
        val arguments = InstrumentationRegistry.getArguments()
        val supplied = arguments.getString("upgradeNonce")
        assumeTrue("Manual upgrade acceptance requires upgradeNonce", !supplied.isNullOrEmpty())
        require(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Only an isolated emulator is supported" }
        val runner = InstrumentationRegistry.getInstrumentation()
        require(runner.javaClass.name == "androidx.test.runner.AndroidJUnitRunner") { "Use the real installed Application runner" }
        return requireNotNull(supplied).also {
            require(UUID.fromString(it).toString() == it) { "A canonical, fresh upgradeNonce is required" }
        }
    }

    private fun old062Repository(token: String, photoStore: ReportPhotoStore): SqliteReportRepository {
        // The new resolution callback adds a constructor parameter in 0.6.3. Calling its Kotlin
        // default constructor on the old APK would fail before we could exercise its real code.
        val constructor = SqliteReportRepository::class.java.constructors.single {
            it.parameterTypes.size == 7 && it.parameterTypes.first() == Context::class.java
        }
        val photoGuard: (NewReport) -> Unit = { it.photo?.let(photoStore::check) }
        val authority: (ReportStatusChangeCommand) -> String = { id(token, "author") }
        return constructor.newInstance(context, databaseName(token), Clock.fixed(now, ZoneOffset.UTC),
            Dispatchers.IO, photoGuard, id(token, "author"), authority) as SqliteReportRepository
    }

    private suspend fun verifyHistory(repository: SqliteReportRepository, token: String) {
        val history = requireNotNull(repository.readHistory(ReportHistoryQuery(id(token, "report"))))
        assertEquals(ReportStatus.CLOSED, history.currentStatus)
        assertEquals(4L, history.latestSequence)
        assertTrue(history.completeFromCreation)
        assertFalse(history.hasMore)
        assertEquals(4, history.events.size)
        val events = history.events.reversed()
        val creation = events.first()
        assertEquals(id(token, "report"), creation.operationId)
        assertEquals(ReportStatusEventKind.CREATED, creation.kind)
        assertEquals(1L, creation.sequence)
        assertEquals(null, creation.fromStatus)
        assertEquals(ReportStatus.REPORTED, creation.toStatus)
        assertEquals(id(token, "author"), creation.actorAccountId)
        assertEquals(now, creation.recordedAt)
        assertEquals("", creation.reason)
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
    }

    private fun transitions(token: String) = listOf(ReportStatus.REPORTED, ReportStatus.IN_REVIEW,
        ReportStatus.ATTENDED, ReportStatus.CLOSED).zipWithNext().mapIndexed { index, (from, to) ->
        ReportStatusChangeCommand(id(token, "transition_$index"), id(token, "report"), index + 1L,
            from, to, "Cambio controlado de aceptación ${index + 1}, sin atención real.")
    }

    private fun draft(photo: ReportPhoto) = NewReport(IncidentType.RISK, Priority.MEDIUM,
        "Evidencia controlada de actualización: señal caída, sin incidente real.", now.minusSeconds(60),
        ReportLocation("Referencia de aceptación, sin GPS", 19.4326, -99.1332), photo)

    private fun expectedReport(token: String, photo: ReportPhoto): Report {
        val value = draft(photo)
        return Report(id(token, "report"), value.type, value.priority, value.description, value.occurredAt,
            value.location, now, ReportStatus.CLOSED, photo)
    }

    private fun contact(token: String) = TrustedContact(id(token, "contact"), "Contacto de aceptación", "+525500000000")
    private fun contactsContext(directory: File): Context = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
    }

    @Suppress("DEPRECATION")
    private fun assertVersion(name: String, code: Int) {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals(name, installed.versionName)
        assertEquals(code, installed.versionCode)
    }

    private fun readOnlyVersion(file: File, expected: Int) {
        assertTrue(file.isFile)
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(expected, it.version)
            it.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        }
    }

    private fun assertNoPendingDatabaseWrites(database: File) {
        listOf("-wal", "-journal").forEach { suffix ->
            val sidecar = File(database.path + suffix)
            assertTrue("Closed database has no pending $suffix bytes", !sidecar.exists() || sidecar.length() == 0L)
        }
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

    private fun bridgeFile(token: String) = privateFile("release063_upgrade_$token.json")
    private fun contactsDirectoryName(token: String) = "release063_contacts_$token"
    private fun databaseName(token: String) = "reports_upgrade063_$token.db"
    private fun photoDirectoryName(token: String) = "upgrade063_$token"
    private fun id(token: String, purpose: String) = UUID.nameUUIDFromBytes("$purpose:$token".toByteArray(Charsets.UTF_8)).toString()
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object { const val MAX_BRIDGE_BYTES = 4_096 }
}
