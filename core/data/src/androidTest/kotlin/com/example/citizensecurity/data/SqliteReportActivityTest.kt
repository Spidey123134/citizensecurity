package com.example.citizensecurity.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** New fixture files only; the administrative writer is supplied exclusively inside these tests. */
@RunWith(AndroidJUnit4::class)
class SqliteReportActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = mutableListOf<String>()
    private val photoDirectories = mutableListOf<File>()
    private val actor = id(999)
    private val now = Instant.parse("2026-10-14T18:00:00.123456789Z")

    @After fun cleanOnlyOwnedFixtures() {
        names.forEach { name ->
            require(name.startsWith("report_activity_test_") ||
                name.matches(Regex("citizensecurity_account_[0-9a-f-]{36}\\.db")))
            context.deleteDatabase(name)
        }
        photoDirectories.forEach { directory ->
            require(directory.name.startsWith("activity_photos_test_"))
            require(directory.canonicalFile.parentFile == context.filesDir.canonicalFile)
            directory.deleteRecursively()
        }
    }

    @Test fun committedCreationAndStatusChangeAppearWithActualFolioActorTypeAndNanos(): Unit = runBlocking {
        val databaseName = name()
        val clock = MovingClock(now)
        repository(databaseName, clock).use { repository ->
            repository.createOnce(id(1), draft(IncidentType.THEFT))
            repository.createOnce(id(2), draft(IncidentType.FIRE))
            clock.value = now.plusNanos(1)
            val command = command(id(1), id(3))
            val changed = repository.changeStatus(command)
            val activity = repository.readActivity(ReportActivityQuery())
            assertEquals(listOf(id(3), id(2), id(1)), activity.entries.map { it.event.operationId })
            assertEquals(id(1), activity.entries.first().reportId)
            assertEquals(IncidentType.THEFT, activity.entries.first().incidentType)
            assertEquals(changed, activity.entries.first().event)
            assertEquals(actor, activity.entries.first().event.actorAccountId)
            assertEquals(now.plusNanos(1), activity.entries.first().event.recordedAt)
            assertEquals(listOf(ReportStatusEventKind.STATUS_CHANGED, ReportStatusEventKind.CREATED,
                ReportStatusEventKind.CREATED), activity.entries.map { it.event.kind })
            assertFalse(activity.hasMore)
            assertNull(activity.nextBefore)
        }
    }

    @Test fun globalPaginationIsExclusiveAcrossSameTimestampUuidsAndNanoseconds(): Unit = runBlocking {
        val databaseName = name()
        val clock = MovingClock(now)
        repository(databaseName, clock).use { repository ->
            for (number in 1..5) repository.createOnce(id(number), draft())
            clock.value = now.plusNanos(1)
            repository.createOnce(id(6), draft())
            val first = repository.readActivity(ReportActivityQuery(limit = 2))
            val second = repository.readActivity(ReportActivityQuery(before = first.nextBefore, limit = 2))
            val last = repository.readActivity(ReportActivityQuery(before = second.nextBefore, limit = 2))
            assertEquals(listOf(id(6), id(5)), first.entries.map { it.event.operationId })
            assertEquals(listOf(id(4), id(3)), second.entries.map { it.event.operationId })
            assertEquals(listOf(id(2), id(1)), last.entries.map { it.event.operationId })
            assertTrue(first.hasMore); assertTrue(second.hasMore); assertFalse(last.hasMore)
            assertEquals(ReportActivityCursor(now, id(5)), first.nextBefore)
            assertEquals(6, (first.entries + second.entries + last.entries).map { it.event.operationId }.toSet().size)
        }
    }

    @Test fun statusFilterPrecedesLimitAndDoesNotTurnNewCreationsIntoChanges(): Unit = runBlocking {
        val databaseName = name()
        val clock = MovingClock(now)
        repository(databaseName, clock).use { repository ->
            repository.createOnce(id(1), draft())
            val changed = repository.changeStatus(command(id(1), id(20)))
            clock.value = now.plusSeconds(1)
            for (number in 2..12) repository.createOnce(id(number), draft())
            val page = repository.readActivity(ReportActivityQuery(ReportActivityFilter.STATUS_CHANGES, limit = 1))
            assertEquals(changed, page.entries.single().event)
            assertFalse(page.hasMore)
            assertNull(page.nextBefore)
            assertEquals(ReportStatusEventKind.CREATED, repository.readActivity(ReportActivityQuery(limit = 1)).entries.single().event.kind)
        }
    }

    @Test fun retryAndRepeatedReadsDoNotCallClockGuardAuthorityOrWriteEvents(): Unit = runBlocking {
        val databaseName = name()
        val clock = MovingClock(now)
        var guards = 0
        var authorities = 0
        SqliteReportRepository(context, databaseName, clock, createGuard = { guards++ },
            statusAuthority = { authorities++; throw ReportStatusPermissionDeniedException() }).use { repository ->
            repository.createOnce(id(1), draft())
            val recordedGuards = guards
            val recordedClock = clock.reads.get()
            val bytes = context.getDatabasePath(databaseName).readBytes()
            val query = ReportActivityQuery()
            val first = repository.readActivity(query)
            repeat(3) { assertEquals(first.entries, repository.readActivity(query).entries) }
            assertEquals(recordedGuards, guards)
            assertEquals(recordedClock, clock.reads.get())
            assertEquals(0, authorities)
            assertArrayEquals(bytes, context.getDatabasePath(databaseName).readBytes())
            assertEquals(1, repository.readHistory(ReportHistoryQuery(id(1)))!!.events.size)
        }
    }

    @Test fun upgradeFromThreePreservesReportNormalizedPhotoFileAndAllHistoryRows(): Unit = runBlocking {
        val databaseName = name()
        val photoName = "activity_photos_test_${UUID.randomUUID()}"
        val directory = File(context.filesDir.canonicalFile, photoName).also(photoDirectories::add)
        val photoStore = ReportPhotoStore(context, photoName)
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val encoded = try { ByteArrayOutputStream().also { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 95, it)) }.toByteArray() }
            finally { bitmap.recycle() }
        val uri = Uri.Builder().scheme("content").authority(PhotoFixtureProvider.AUTHORITY).appendPath("bytes")
            .appendPath(Base64.encodeToString(encoded, Base64.URL_SAFE or Base64.NO_WRAP)).build()
        val photo = photoStore.importPhoto(uri)
        val photoBytes = photoStore.read(photo)
        val report = repository(databaseName).use { repository ->
            repository.createOnce(id(1), draft(photo = photo)).also { repository.changeStatus(command(id(1), id(2))) }
        }.copy(status = ReportStatus.IN_REVIEW)
        val events = repository(databaseName).use { it.readHistory(ReportHistoryQuery(id(1)))!!.events }
        raw(databaseName) { it.execSQL("DROP INDEX ${ReportActivitySql.INDEX_NAME}"); it.version = 3 }
        repository(databaseName).use { repository ->
            assertEquals(report, repository.findById(id(1)))
            assertEquals(events, repository.readHistory(ReportHistoryQuery(id(1)))!!.events)
            assertEquals(2, repository.readActivity(ReportActivityQuery()).entries.size)
        }
        assertArrayEquals(photoBytes, photoStore.read(photo))
        assertEquals(photo.id + ".jpg", directory.list()!!.single())
        raw(databaseName) { db ->
            assertEquals(4, db.version)
            db.rawQuery("PRAGMA index_info(${ReportActivitySql.INDEX_NAME})", null).use { cursor ->
                val columns = buildList { while (cursor.moveToNext()) add(cursor.getString(2)) }
                assertEquals(listOf("recorded_at_seconds", "recorded_at_nanos", "operation_id"), columns)
            }
        }
    }

    @Test fun migratedLegacyRowsWithoutRecordedEventsStayEmpty(): Unit = runBlocking {
        val databaseName = name()
        val report = repository(databaseName).use { it.createOnce(id(1), draft()) }
        raw(databaseName) { db ->
            db.execSQL("DELETE FROM report_status_events")
            db.execSQL("DROP INDEX ${ReportActivitySql.INDEX_NAME}")
            db.version = 3
        }
        repository(databaseName).use { repository ->
            assertTrue(repository.readActivity(ReportActivityQuery()).entries.isEmpty())
            assertTrue(repository.readActivity(ReportActivityQuery(ReportActivityFilter.STATUS_CHANGES)).entries.isEmpty())
            assertEquals(report, repository.findById(id(1)))
            assertFalse(repository.readHistory(ReportHistoryQuery(id(1)))!!.completeFromCreation)
        }
        raw(databaseName) { assertEquals(4, it.version) }
    }

    @Test fun failedIndexMigrationKeepsVersionRowsPhotoFieldsAndEvents(): Unit = runBlocking {
        val databaseName = name()
        repository(databaseName).use { it.createOnce(id(1), draft()); it.changeStatus(command(id(1), id(2))) }
        raw(databaseName) { db ->
            db.execSQL("DROP INDEX ${ReportActivitySql.INDEX_NAME}")
            db.execSQL("CREATE TABLE ${ReportActivitySql.INDEX_NAME} (note TEXT)")
            db.execSQL("INSERT INTO ${ReportActivitySql.INDEX_NAME} VALUES ('conservar')")
            db.version = 3
        }
        val bytes = context.getDatabasePath(databaseName).readBytes()
        repository(databaseName).use { repository -> expect<SQLiteException> { repository.readActivity(ReportActivityQuery()) } }
        assertArrayEquals(bytes, context.getDatabasePath(databaseName).readBytes())
        raw(databaseName) { db ->
            assertEquals(3, db.version)
            db.rawQuery("SELECT COUNT(*) FROM reports", null).use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            db.rawQuery("SELECT COUNT(*) FROM report_status_events", null).use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
            db.rawQuery("SELECT note FROM ${ReportActivitySql.INDEX_NAME}", null).use {
                assertTrue(it.moveToFirst()); assertEquals("conservar", it.getString(0))
            }
        }
    }

    @Test fun sameFolioIdStaysInsideItsCapturedAccountDatabase(): Unit = runBlocking {
        val firstOwner = UUID.randomUUID().toString()
        val secondOwner = UUID.randomUUID().toString()
        val firstName = ownedName(ReportStorageNames.fromAccountId(firstOwner).databaseName)
        val secondName = ownedName(ReportStorageNames.fromAccountId(secondOwner).databaseName)
        SqliteReportRepository(context, firstName, Clock.fixed(now, ZoneOffset.UTC), authorAccountId = firstOwner).use { repository ->
            repository.createOnce(id(1), draft(IncidentType.THEFT))
        }
        SqliteReportRepository(context, secondName, Clock.fixed(now, ZoneOffset.UTC), authorAccountId = secondOwner).use { repository ->
            repository.createOnce(id(1), draft(IncidentType.FIRE))
        }
        repository(firstName).use { repository ->
            val entry = repository.readActivity(ReportActivityQuery()).entries.single()
            assertEquals(firstOwner, entry.event.actorAccountId); assertEquals(IncidentType.THEFT, entry.incidentType)
        }
        repository(secondName).use { repository ->
            val entry = repository.readActivity(ReportActivityQuery()).entries.single()
            assertEquals(secondOwner, entry.event.actorAccountId); assertEquals(IncidentType.FIRE, entry.incidentType)
        }
    }

    @Test fun receiptLookupAcceptsThreeWithoutUpgradeThenAcceptsFourWithoutAnotherWrite(): Unit = runBlocking {
        val databaseName = name()
        val scope = ReportStatusChangeScope(null, actor)
        val request = ReportStatusChangeRequest(scope, command(id(1), id(2)), now)
        val event = repository(databaseName).use { repository ->
            repository.createOnce(id(1), draft())
            SqliteReportStatusChangeAccess(scope, repository) {}.change(request)
        }
        raw(databaseName) { it.execSQL("DROP INDEX ${ReportActivitySql.INDEX_NAME}"); it.version = 3 }
        val versionThreeBytes = context.getDatabasePath(databaseName).readBytes()
        repository(databaseName).use { repository ->
            assertEquals(event, SqliteReportStatusChangeAccess(scope, repository) {}.findReceipt(request)!!.event)
        }
        assertArrayEquals(versionThreeBytes, context.getDatabasePath(databaseName).readBytes())
        raw(databaseName) { assertEquals(3, it.version) }
        repository(databaseName).use { it.readActivity(ReportActivityQuery()) }
        val versionFourBytes = context.getDatabasePath(databaseName).readBytes()
        repository(databaseName).use { repository ->
            assertEquals(event, SqliteReportStatusChangeAccess(scope, repository) {}.findReceipt(request)!!.event)
        }
        assertArrayEquals(versionFourBytes, context.getDatabasePath(databaseName).readBytes())
        raw(databaseName) { assertEquals(4, it.version) }
    }

    @Test fun invalidRecordedNumericFieldsFailWithoutNormalizingOrReplacingTheDatabase(): Unit = runBlocking {
        listOf("recorded_at_nanos = 1000000000", "recorded_at_seconds = 1.5").forEach { malformed ->
            val databaseName = name()
            repository(databaseName).use { it.createOnce(id(1), draft()) }
            raw(databaseName) { db ->
                db.execSQL("PRAGMA ignore_check_constraints = ON")
                db.execSQL("UPDATE report_status_events SET $malformed")
            }
            val bytes = context.getDatabasePath(databaseName).readBytes()
            repository(databaseName).use { repository -> expect<IllegalStateException> { repository.readActivity(ReportActivityQuery()) } }
            assertArrayEquals(bytes, context.getDatabasePath(databaseName).readBytes())
        }
    }

    @Test fun cancelledQueuedReadCannotCreateTheMissingDatabase(): Unit = runBlocking {
        val databaseName = name()
        val dispatcher = QueuedDispatcher()
        val repository = SqliteReportRepository(context, databaseName, ioDispatcher = dispatcher)
        try {
            val operation = async(Dispatchers.Default) { repository.readActivity(ReportActivityQuery()) }
            val task = dispatcher.awaitTask()
            operation.cancel()
            task.run()
            operation.join()
            assertTrue(operation.isCancelled)
            assertFalse(context.getDatabasePath(databaseName).exists())
        } finally { repository.close() }
    }

    @Test fun closedRepositoryRejectsActivityBeforeOpeningStorage(): Unit = runBlocking {
        val databaseName = name()
        val repository = repository(databaseName)
        repository.close()
        expect<IllegalStateException> { repository.readActivity(ReportActivityQuery()) }
        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    private fun name() = ownedName("report_activity_test_${UUID.randomUUID()}.db")
    private fun ownedName(name: String): String {
        check(!context.getDatabasePath(name).exists()) { "Fixture must own a fresh database." }
        names += name
        return name
    }
    private fun repository(name: String, clock: Clock = Clock.fixed(now, ZoneOffset.UTC)) =
        SqliteReportRepository(context, name, clock, authorAccountId = actor, statusAuthority = { actor })
    private fun draft(type: IncidentType = IncidentType.RISK, photo: ReportPhoto? = null) = NewReport(type,
        Priority.HIGH, "Reporte local del árbol caído frente al café.", now.minusSeconds(30),
        ReportLocation("Frente al café", 19.0, -99.0), photo)
    private fun command(folio: String, operation: String) = ReportStatusChangeCommand(operation, folio, 1,
        ReportStatus.REPORTED, ReportStatus.IN_REVIEW, "Cambio local registrado")
    private fun id(number: Int) = "c0000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
    private fun raw(name: String, block: (SQLiteDatabase) -> Unit) = SQLiteDatabase.openDatabase(
        context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use(block)
    private suspend inline fun <reified T : Throwable> expect(noinline block: suspend () -> Any?) {
        try { block(); fail("Expected ${T::class.java.simpleName}") } catch (failure: Throwable) { if (failure !is T) throw failure }
    }
    private class MovingClock(@Volatile var value: Instant) : Clock() {
        val reads = AtomicInteger()
        override fun instant(): Instant { reads.incrementAndGet(); return value }
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(value, zone)
    }
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = LinkedBlockingQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun awaitTask(): Runnable = checkNotNull(tasks.poll(10, TimeUnit.SECONDS))
    }
}
