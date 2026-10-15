package com.example.citizensecurity.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.ReportRequestConflictException
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportSubmissionRequest
import com.example.citizensecurity.domain.ValidationErrors
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Metadatos SQLite en bases aisladas; no sustituye la comprobación del JPEG productivo. */
@RunWith(AndroidJUnit4::class)
class SqliteReportPhotoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = mutableListOf<String>()
    private val now = Instant.parse("2026-10-10T05:00:00.123456789Z")
    private val requestId = "1e345678-1234-4567-89ab-0123456789ab"
    private val photo = ReportPhoto("2e345678-1234-4567-89ab-0123456789ab", "a".repeat(64), 1024, 640, 480)

    @After fun cleanOnlyOwnedDatabases() { names.forEach { context.deleteDatabase(it) } }

    @Test fun upgradingARealVersionOneDatabasePreservesEveryOldFieldAndIndex(): Unit = runBlocking {
        val name = legacyDatabase()
        val expected = oldReports()
        SqliteReportRepository(context, name, clock()).use { repository ->
            assertEquals(expected.sortedByDescending { it.id }, repository.list())
            expected.forEach { old -> assertEquals(old, repository.findById(old.id)) }
        }
        readRaw(name) { db ->
            assertEquals(4, db.version)
            db.rawQuery("SELECT photo_id, photo_sha256, photo_byte_count, photo_width, photo_height FROM reports", null).use { cursor ->
                assertEquals(2, cursor.count)
                while (cursor.moveToNext()) (0..4).forEach { assertTrue(cursor.isNull(it)) }
            }
            db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'reports_created_at_idx'", null).use {
                assertEquals(1, it.count)
            }
        }
    }

    @Test fun aPhotoReportCanBeAppendedAfterUpgradeAndReopenedWithoutChangingOldRows(): Unit = runBlocking {
        val name = legacyDatabase()
        val saved = SqliteReportRepository(context, name, clock()).use { it.createOnce(requestId, draft(photo)) }
        SqliteReportRepository(context, name, clock()).use { repository ->
            assertEquals(saved, repository.findById(requestId))
            assertEquals(photo, repository.findById(requestId)!!.photo)
            oldReports().forEach { assertEquals(it, repository.findById(it.id)) }
            assertEquals(3, repository.list().size)
        }
    }

    @Test fun idempotentPhotoRetryRecoversTheSameReceiptWithoutAnotherGuardOrInsert(): Unit = runBlocking {
        var checks = 0
        SqliteReportRepository(context, null, clock(), createGuard = { checks++ }).use { repository ->
            val draft = draft(photo)
            val saved = repository.createOnce(requestId, draft)
            assertEquals(3, checks)
            assertEquals(photo, saved.photo)
            assertEquals(saved, repository.createOnce(requestId, draft))
            assertEquals(3, checks)
            assertEquals(listOf(saved), repository.list())
            assertTrue(ReportSubmissionRequest(requestId, draft, now).matches(saved))
        }
    }

    @Test fun changingAnyPhotoMetadataUnderTheSameRequestIdConflictsWithoutOverwriting(): Unit = runBlocking {
        SqliteReportRepository(context, null, clock()).use { repository ->
            val saved = repository.createOnce(requestId, draft(photo))
            val changed = listOf<ReportPhoto?>(null,
                photo.copy(id = "3e345678-1234-4567-89ab-0123456789ab"),
                photo.copy(sha256 = "b".repeat(64)), photo.copy(byteCount = 1025),
                photo.copy(width = 641), photo.copy(height = 481))
            changed.forEach { replacement ->
                expectFailure<ReportRequestConflictException> { repository.createOnce(requestId, draft(replacement)) }
                assertEquals(saved, repository.findById(requestId))
                assertEquals(listOf(saved), repository.list())
            }
        }
    }

    @Test fun aRequestOriginallyWithoutPhotoCannotAcquireOneOnRetry(): Unit = runBlocking {
        SqliteReportRepository(context, null, clock()).use { repository ->
            val saved = repository.createOnce(requestId, draft(null))
            expectFailure<ReportRequestConflictException> { repository.createOnce(requestId, draft(photo)) }
            assertNull(repository.findById(requestId)!!.photo)
            assertEquals(listOf(saved), repository.list())
        }
    }

    @Test fun anExplicitNewRequestMayReferenceTheSamePhotoWithoutDeduplicatingItsReport(): Unit = runBlocking {
        SqliteReportRepository(context, null, clock()).use { repository ->
            val first = repository.createOnce(requestId, draft(photo))
            val second = repository.createOnce(UUID.randomUUID().toString(), draft(photo))
            assertEquals(photo, first.photo)
            assertEquals(photo, second.photo)
            assertEquals(setOf(first, second), repository.list().toSet())
        }
    }

    @Test fun rejectedPhotoGuardDoesNotCreateADatabaseOrReport(): Unit = runBlocking {
        val name = temporaryName()
        val errors = mapOf(ReportField.PHOTO to "No se pudo comprobar la fotografía guardada.")
        SqliteReportRepository(context, name, clock(), createGuard = {
            throw InvalidReportException(ValidationErrors(errors))
        }).use { repository ->
            expectFailure<InvalidReportException> { repository.createOnce(requestId, draft(photo)) }
        }
        assertFalse(context.getDatabasePath(name).exists())
    }

    @Test fun sqliteConstraintsRejectPartialOrMalformedPhotoMetadataAndPreserveTheRow(): Unit = runBlocking {
        val name = temporaryName()
        val saved = SqliteReportRepository(context, name, clock()).use { it.createOnce(requestId, draft(photo)) }
        writeRaw(name) { db ->
            val statements = listOf(
                "UPDATE reports SET photo_id = NULL",
                "UPDATE reports SET photo_sha256 = NULL",
                "UPDATE reports SET photo_byte_count = NULL",
                "UPDATE reports SET photo_width = NULL",
                "UPDATE reports SET photo_height = NULL",
                "UPDATE reports SET photo_id = 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'",
                "UPDATE reports SET photo_sha256 = '${"A".repeat(64)}'",
                "UPDATE reports SET photo_byte_count = 0",
                "UPDATE reports SET photo_byte_count = 6291457",
                "UPDATE reports SET photo_byte_count = 1.5",
                "UPDATE reports SET photo_width = 2049",
                "UPDATE reports SET photo_height = 0",
            )
            statements.forEach { sql ->
                try { db.execSQL(sql); fail("Debe rechazar metadatos inválidos.") }
                catch (_: SQLiteConstraintException) { }
            }
        }
        SqliteReportRepository(context, name, clock()).use { assertEquals(listOf(saved), it.list()) }
    }

    @Test fun aPartialPhotoCannotBeAddedToAnOldNullPhotoRow(): Unit = runBlocking {
        val name = legacyDatabase()
        SqliteReportRepository(context, name, clock()).use { it.list() }
        writeRaw(name) { db ->
            try { db.execSQL("UPDATE reports SET photo_id = '${photo.id}'"); fail("Debe rechazar una foto parcial.") }
            catch (_: SQLiteConstraintException) { }
        }
        SqliteReportRepository(context, name, clock()).use { repository ->
            oldReports().forEach { assertEquals(it, repository.findById(it.id)) }
        }
    }

    @Test fun malformedPhotoInAnAlreadyDamagedRowFailsReadWithoutReplacingDatabaseBytes(): Unit = runBlocking {
        val name = temporaryName()
        SqliteReportRepository(context, name, clock()).use { it.createOnce(requestId, draft(photo)) }
        writeRaw(name) { db ->
            db.execSQL("PRAGMA ignore_check_constraints = ON")
            db.execSQL("UPDATE reports SET photo_width = 4294967297")
        }
        val bytes = context.getDatabasePath(name).readBytes()
        repeat(2) {
            SqliteReportRepository(context, name, clock()).use { repository ->
                expectFailure<IllegalArgumentException> { repository.findById(requestId) }
            }
            assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
        }
    }

    @Test fun aFutureSchemaIsRejectedWithoutDroppingItsReportsOrColumns(): Unit = runBlocking {
        val name = legacyDatabase()
        SqliteReportRepository(context, name, clock()).use { it.list() }
        writeRaw(name) { db ->
            db.execSQL("ALTER TABLE reports ADD COLUMN future_note TEXT DEFAULT 'conservar'")
            db.version = 5
        }
        val bytes = context.getDatabasePath(name).readBytes()
        SqliteReportRepository(context, name, clock()).use { repository ->
            expectFailure<SQLiteException> { repository.list() }
        }
        assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
        readRaw(name) { db ->
            assertEquals(5, db.version)
            db.rawQuery("SELECT future_note FROM reports", null).use { cursor ->
                assertEquals(2, cursor.count)
                while (cursor.moveToNext()) assertEquals("conservar", cursor.getString(0))
            }
        }
    }

    @Test fun unsupportedPartialMigrationRollsBackWithoutChangingTheLegacyVersion(): Unit = runBlocking {
        val name = legacyDatabase()
        // Fallar en la cuarta columna obliga a revertir las tres adiciones anteriores.
        writeRaw(name) { db -> db.execSQL("ALTER TABLE reports ADD COLUMN photo_width INTEGER") }
        val bytes = context.getDatabasePath(name).readBytes()
        SqliteReportRepository(context, name, clock()).use { repository ->
            expectFailure<SQLiteException> { repository.list() }
        }
        assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
        readRaw(name) { db ->
            assertEquals(1, db.version)
            db.rawQuery("SELECT id FROM reports", null).use { assertEquals(2, it.count) }
            db.rawQuery("PRAGMA table_info(reports)", null).use { cursor ->
                val columns = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertFalse(columns.contains("photo_id"))
                assertFalse(columns.contains("photo_sha256"))
                assertFalse(columns.contains("photo_byte_count"))
                assertTrue(columns.contains("photo_width"))
            }
        }
    }

    private fun temporaryName() = "report_photo_metadata_test_${UUID.randomUUID()}.db".also(names::add)
    private fun clock() = Clock.fixed(now, ZoneOffset.UTC)
    private fun draft(photo: ReportPhoto?) = NewReport(IncidentType.RISK, Priority.MEDIUM,
        "Hay una luminaria dañada frente al mercado.", now.minusSeconds(60),
        ReportLocation("Frente al mercado", 19.4326, -99.1332), photo)
    private fun oldReports() = listOf(
        Report("4e345678-1234-4567-89ab-0123456789ab", IncidentType.RISK, Priority.LOW,
            "Hay un árbol caído 🌳 frente al café.", now.minusSeconds(120),
            ReportLocation("Frente al café", 19.4326, -99.1332), now, ReportStatus.REPORTED),
        Report("5e345678-1234-4567-89ab-0123456789ab", IncidentType.THEFT, Priority.HIGH,
            "Se reportó un robo junto a la segunda entrada del mercado.", now.minusSeconds(300),
            ReportLocation("Segunda entrada del mercado"), now, ReportStatus.ATTENDED),
    )
    private fun legacyDatabase(): String {
        val name = temporaryName()
        writeRaw(name) { db ->
            db.execSQL(LEGACY_CREATE_TABLE)
            db.execSQL("CREATE INDEX reports_created_at_idx ON reports (created_at_seconds DESC, created_at_nanos DESC, id DESC)")
            oldReports().forEach { report ->
                db.insertOrThrow("reports", null, ContentValues().apply {
                    put("id", report.id); put("incident_type", report.type.name); put("priority", report.priority.name)
                    put("description", report.description); put("occurred_at_seconds", report.occurredAt.epochSecond)
                    put("occurred_at_nanos", report.occurredAt.nano); put("location_reference", report.location.reference)
                    put("latitude", report.location.latitude); put("longitude", report.location.longitude)
                    put("created_at_seconds", report.createdAt.epochSecond); put("created_at_nanos", report.createdAt.nano)
                    put("status", report.status.name)
                })
            }
            db.version = 1
        }
        return name
    }
    private fun writeRaw(name: String, action: (SQLiteDatabase) -> Unit) {
        val file = context.getDatabasePath(name)
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        SQLiteDatabase.openOrCreateDatabase(file, null).use(action)
    }
    private fun readRaw(name: String, action: (SQLiteDatabase) -> Unit) =
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use(action)
    private suspend inline fun <reified T : Throwable> expectFailure(noinline action: suspend () -> Unit) {
        try { action(); fail("Se esperaba ${T::class.java.simpleName}.") }
        catch (failure: Throwable) { if (failure !is T) throw failure }
    }

    private companion object {
        // DDL independiente conservado del esquema publicado en 0.5.3, sin columnas de foto.
        val LEGACY_CREATE_TABLE = """
            CREATE TABLE reports (
                id TEXT NOT NULL PRIMARY KEY CHECK (length(id) = 36),
                incident_type TEXT NOT NULL CHECK (incident_type IN ('EMERGENCY','THEFT','ACCIDENT','FIRE','RISK','OTHER')),
                priority TEXT NOT NULL CHECK (priority IN ('LOW','MEDIUM','HIGH')),
                description TEXT NOT NULL CHECK (length(description) BETWEEN 10 AND 1000 AND instr(description,char(0)) = 0),
                occurred_at_seconds INTEGER NOT NULL CHECK (occurred_at_seconds >= 0),
                occurred_at_nanos INTEGER NOT NULL CHECK (occurred_at_nanos BETWEEN 0 AND 999999999),
                location_reference TEXT NOT NULL DEFAULT '' CHECK (
                    (length(location_reference) = 0 OR length(location_reference) BETWEEN 5 AND 200)
                    AND instr(location_reference,char(0)) = 0),
                latitude REAL, longitude REAL,
                created_at_seconds INTEGER NOT NULL,
                created_at_nanos INTEGER NOT NULL CHECK (created_at_nanos BETWEEN 0 AND 999999999),
                status TEXT NOT NULL CHECK (status IN ('REPORTED','IN_REVIEW','ATTENDED','CLOSED')),
                CHECK ((latitude IS NULL AND longitude IS NULL AND length(location_reference) > 0) OR
                    (latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90.0 AND 90.0
                        AND longitude BETWEEN -180.0 AND 180.0)),
                CHECK (occurred_at_seconds < created_at_seconds + 60 OR
                    (occurred_at_seconds = created_at_seconds + 60 AND occurred_at_nanos <= created_at_nanos))
            )
        """.trimIndent()
    }
}
