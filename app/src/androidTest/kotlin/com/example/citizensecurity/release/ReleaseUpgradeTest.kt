package com.example.citizensecurity.release

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Color
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
import com.example.citizensecurity.domain.ReportStatus
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
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two separately selected invocations around an actual adb install -r, with the same nonce.
 * All product APIs referenced here already exist in 0.6.1. Fixtures are private and retained;
 * this test neither deletes existing app data nor assigns permissions or sends messages.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseUpgradeTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val nonce: String get() = requireNotNull(InstrumentationRegistry.getArguments().getString("upgradeNonce"))
        .also { require(UUID.fromString(it).toString() == it) { "A canonical, fresh upgradeNonce is required" } }
    private val now = Instant.parse("2026-10-14T18:00:00.123456789Z")

    @Test fun seed061(): Unit = runBlocking {
        assertVersion("0.6.1", 29)
        val token = nonce
        val databaseName = databaseName(token)
        val database = context.getDatabasePath(databaseName)
        val bridge = bridgeFile(token)
        val contactsDirectory = privateFile("release062_contacts_$token")
        val photosDirectory = privateFile(photoDirectoryName(token))
        assertFalse("A seed must never overwrite an earlier database", database.exists())
        assertFalse("A seed must never overwrite an earlier bridge", bridge.exists())
        assertFalse("A seed must never reuse a contacts directory", contactsDirectory.exists())
        assertFalse("A seed must never reuse a photo directory", photosDirectory.exists())
        assertTrue(contactsDirectory.mkdir())
        assertTrue(photosDirectory.mkdir())

        // A controlled JPEG is stored in the real photo-store layout and checked by that store.
        // It tests preservation of saved evidence, not camera or gallery import acceptance.
        val jpeg = controlledJpeg()
        val photo = ReportPhoto(id(token, "photo"), sha256(jpeg), jpeg.size.toLong(), 24, 16)
        val photoFile = File(photosDirectory, "${photo.id}.jpg")
        writeNew(photoFile, jpeg)
        val photoStore = ReportPhotoStore(context, photoDirectoryName(token))
        photoStore.check(photo)
        assertArrayEquals(jpeg, photoStore.read(photo))

        val saved = SqliteReportRepository(context, databaseName,
            clock = Clock.fixed(now, ZoneOffset.UTC), createGuard = { draft -> draft.photo?.let(photoStore::check) },
            authorAccountId = id(token, "author")).use { repository ->
            repository.createOnce(id(token, "report"), draft(photo)).also { report ->
                assertEquals(report, repository.findById(report.id))
                val history = requireNotNull(repository.readHistory(ReportHistoryQuery(report.id)))
                assertEquals(1, history.events.size)
                assertEquals(ReportStatusEventKind.CREATED, history.events.single().kind)
            }
        }
        assertEquals(expectedReport(token, photo), saved)
        readOnlyVersion(database, 3)

        val contacts = TrustedContactsStore(contactsContext(contactsDirectory), id(token, "owner"), {})
        val initial = contacts.read()
        assertEquals(null, initial.revision)
        val contact = contact(token)
        val savedContacts = contacts.replace(initial.revision, listOf(contact))
        val contactsFile = File(contactsDirectory, TrustedContactsStore.fileName(id(token, "owner")))
        assertTrue(contactsFile.isFile)
        assertEquals(listOf(contact), contacts.read().contacts)
        assertNoPendingDatabaseWrites(database)

        val json = JSONObject().put("nonce", token)
            .put("databaseSha256", sha256(database.readBytes()))
            .put("contactsSha256", sha256(contactsFile.readBytes()))
            .put("contactsRevision", savedContacts.revision)
            .put("photoSha256", sha256(photoFile.readBytes()))
            .put("photoBytes", photo.byteCount)
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BRIDGE_BYTES)
        writeNew(bridge, bytes)
    }

    @Test fun verify062(): Unit = runBlocking {
        assertVersion("0.6.2", 30)
        val token = nonce
        val bridge = bridgeFile(token)
        assertTrue("Seed 0.6.1 must run before install -r", bridge.isFile)
        require(bridge.length() in 1..MAX_BRIDGE_BYTES.toLong())
        val receipt = JSONObject(bridge.readText(Charsets.UTF_8))
        assertEquals(token, receipt.getString("nonce"))
        val database = context.getDatabasePath(databaseName(token))
        val contactsDirectory = privateFile("release062_contacts_$token")
        val contactsFile = File(contactsDirectory, TrustedContactsStore.fileName(id(token, "owner")))
        val photoFile = File(privateFile(photoDirectoryName(token)), "${id(token, "photo")}.jpg")

        // Byte preservation is checked before opening any new product repository or migration.
        assertEquals(receipt.getString("databaseSha256"), sha256(database.readBytes()))
        assertEquals(receipt.getString("contactsSha256"), sha256(contactsFile.readBytes()))
        assertEquals(receipt.getString("photoSha256"), sha256(photoFile.readBytes()))
        assertNoPendingDatabaseWrites(database)
        readOnlyVersion(database, 3)
        assertEquals(receipt.getString("databaseSha256"), sha256(database.readBytes()))

        val photo = ReportPhoto(id(token, "photo"), receipt.getString("photoSha256"),
            receipt.getLong("photoBytes"), 24, 16)
        SqliteReportRepository(context, databaseName(token),
            clock = Clock.fixed(now, ZoneOffset.UTC), createGuard = {}).use { repository ->
            assertEquals(expectedReport(token, photo), repository.findById(id(token, "report")))
            val history = requireNotNull(repository.readHistory(ReportHistoryQuery(id(token, "report"))))
            assertEquals(ReportStatus.REPORTED, history.currentStatus)
            assertEquals(1L, history.latestSequence)
            assertFalse(history.hasMore)
            assertTrue(history.completeFromCreation)
            assertEquals(1, history.events.size)
            val event = history.events.single()
            assertEquals(id(token, "report"), event.operationId)
            assertEquals(ReportStatusEventKind.CREATED, event.kind)
            assertEquals(1L, event.sequence)
            assertEquals(null, event.fromStatus)
            assertEquals(ReportStatus.REPORTED, event.toStatus)
            assertEquals(id(token, "author"), event.actorAccountId)
            assertEquals(now, event.recordedAt)
            assertEquals("", event.reason)
        }
        readOnlyVersion(database, 4)
        val contacts = TrustedContactsStore(contactsContext(contactsDirectory), id(token, "owner"), {}).read()
        assertEquals(receipt.getString("contactsRevision"), contacts.revision)
        assertEquals(listOf(contact(token)), contacts.contacts)
        assertEquals(receipt.getString("contactsSha256"), sha256(contactsFile.readBytes()))
        assertEquals(receipt.getString("photoSha256"), sha256(photoFile.readBytes()))
        assertArrayEquals(photoFile.readBytes(), ReportPhotoStore(context, photoDirectoryName(token)).read(photo))
        assertTrue("Bridge remains available for inspection", bridge.isFile)
    }

    private fun draft(photo: ReportPhoto) = NewReport(IncidentType.RISK, Priority.MEDIUM,
        "Evidencia controlada de actualización: señal caída, sin incidente real.", now.minusSeconds(60),
        ReportLocation(reference = "Referencia controlada de aceptación, sin GPS", latitude = 19.4326, longitude = -99.1332), photo)

    private fun expectedReport(token: String, photo: ReportPhoto): Report {
        val value = draft(photo)
        return Report(id(token, "report"), value.type, value.priority, value.description, value.occurredAt,
            value.location, now, ReportStatus.REPORTED, photo)
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

    private fun bridgeFile(token: String) = privateFile("release062_upgrade_$token.json")
    private fun databaseName(token: String) = "reports_upgrade_$token.db"
    private fun photoDirectoryName(token: String) = "upgrade062_$token"
    private fun id(token: String, purpose: String) = UUID.nameUUIDFromBytes("$purpose:$token".toByteArray(Charsets.UTF_8)).toString()
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object { private const val MAX_BRIDGE_BYTES = 4_096 }
}
