package com.example.citizensecurity.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android repository fixtures, compiled separately; never installs or assigns app roles. */
@RunWith(AndroidJUnit4::class)
class SqliteReportHistoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = mutableListOf<String>()
    private val id = "30000000-0000-0000-0000-000000000001"
    private val actor = "30000000-0000-0000-0000-000000000002"
    private val now = Instant.parse("2026-10-13T01:02:03.123456789Z")
    private fun clock() = Clock.fixed(now, ZoneOffset.UTC)
    private fun draft() = NewReport(IncidentType.RISK, Priority.HIGH, "Hay un árbol caído frente al café.",
        now.minusSeconds(60), ReportLocation("Frente al café", 19.0, -99.0))
    private fun name() = "report_history_test_${UUID.randomUUID()}.db".also(names::add)
    @After fun cleanOnlyOwnedDatabases() { names.forEach { context.deleteDatabase(it) } }
    private fun command(seq: Long = 1, from: ReportStatus = ReportStatus.REPORTED,
        to: ReportStatus = ReportStatus.IN_REVIEW, operation: String = UUID.randomUUID().toString(),
        reason: String = "Revisión local del incidente") = ReportStatusChangeCommand(operation, id, seq, from, to, reason)
    private fun authorized(name: String? = null, authority: (ReportStatusChangeCommand) -> String = { actor }) =
        SqliteReportRepository(context, name, clock(), authorAccountId = actor, statusAuthority = authority)

    @Test fun createOnceStoresOneCreationWithOriginalActorAndNanoseconds(): Unit = runBlocking {
        authorized().use { repository ->
            val first = repository.createOnce(id, draft())
            assertEquals(first, repository.createOnce(id, draft()))
            val page = repository.readHistory(ReportHistoryQuery(id))!!
            assertTrue(page.completeFromCreation)
            assertEquals(1L, page.latestSequence)
            assertEquals(1, page.events.size)
            assertEquals(ReportStatusEventKind.CREATED, page.events.single().kind)
            assertEquals(now, page.events.single().recordedAt)
            assertEquals(actor, page.events.single().actorAccountId)
        }
    }
    @Test fun deniedWriterDoesNotOpenDatabaseOrCallCreationGuard(): Unit = runBlocking {
        val databaseName = name()
        var guarded = false
        SqliteReportRepository(context, databaseName, clock(), createGuard = { guarded = true }).use {
            expect<ReportStatusPermissionDeniedException> { it.changeStatus(command()) }
        }
        assertFalse(guarded)
        assertFalse(context.getDatabasePath(databaseName).exists())
    }
    @Test fun migrationFromTwoPreservesOriginalReportAndLeavesHistoryUnknown(): Unit = runBlocking {
        val databaseName = name()
        val saved = authorized(databaseName).use { it.createOnce(id, draft()) }
        raw(databaseName) { db ->
            db.execSQL("DROP TABLE report_status_events")
            db.execSQL("UPDATE reports SET status = 'ATTENDED' WHERE id = '$id'")
            db.version = 2
        }
        authorized(databaseName).use { repository ->
            assertEquals(saved.copy(status = ReportStatus.ATTENDED), repository.findById(id))
            val page = repository.readHistory(ReportHistoryQuery(id))!!
            assertEquals(ReportStatus.ATTENDED, page.currentStatus)
            assertEquals(0L, page.latestSequence)
            assertFalse(page.completeFromCreation)
            assertTrue(page.events.isEmpty())
            val event = repository.changeStatus(command(0, ReportStatus.ATTENDED, ReportStatus.CLOSED))
            assertEquals(1L, event.sequence)
            assertFalse(repository.readHistory(ReportHistoryQuery(id))!!.completeFromCreation)
        }
        raw(databaseName) { assertEquals(3, it.version) }
    }
    @Test fun failedMigrationFromTwoPreservesRowsVersionAndExistingTable(): Unit = runBlocking {
        val databaseName = name()
        val saved = authorized(databaseName).use { it.createOnce(id, draft()) }
        raw(databaseName) { db ->
            db.execSQL("DROP TABLE report_status_events")
            db.execSQL("CREATE TABLE report_status_events (note TEXT)")
            db.execSQL("INSERT INTO report_status_events VALUES ('conservar')")
            db.version = 2
        }
        val bytes = context.getDatabasePath(databaseName).readBytes()
        authorized(databaseName).use { expect<SQLiteException> { it.readHistory(ReportHistoryQuery(id)) } }
        assertArrayEquals(bytes, context.getDatabasePath(databaseName).readBytes())
        raw(databaseName) { db ->
            assertEquals(2, db.version)
            db.rawQuery("SELECT status, description FROM reports WHERE id = ?", arrayOf(id)).use {
                assertTrue(it.moveToFirst()); assertEquals(saved.status.name, it.getString(0)); assertEquals(saved.description, it.getString(1))
            }
            db.rawQuery("SELECT note FROM report_status_events", null).use {
                assertTrue(it.moveToFirst()); assertEquals("conservar", it.getString(0))
            }
        }
    }
    @Test fun consecutiveChangesAndReplayKeepOriginalReceiptAndFolio(): Unit = runBlocking {
        authorized().use { repository ->
            repository.createOnce(id, draft())
            val first = command()
            val receipt = repository.changeStatus(first)
            repository.changeStatus(command(2, ReportStatus.IN_REVIEW, ReportStatus.ATTENDED))
            repository.changeStatus(command(3, ReportStatus.ATTENDED, ReportStatus.CLOSED))
            assertEquals(receipt, repository.changeStatus(first))
            val page = repository.readHistory(ReportHistoryQuery(id))!!
            assertEquals(ReportStatus.CLOSED, page.currentStatus)
            assertEquals(listOf(4L, 3L, 2L, 1L), page.events.map { it.sequence })
            assertEquals(id, repository.createOnce(id, draft()).id)
            assertEquals(4, repository.readHistory(ReportHistoryQuery(id))!!.events.size)
        }
    }
    @Test fun staleStatusAndReusedOperationWithChangedPayloadAreRejected(): Unit = runBlocking {
        authorized().use { repository ->
            repository.createOnce(id, draft())
            val requested = command()
            repository.changeStatus(requested)
            expect<ReportStatusConflictException> { repository.changeStatus(command()) }
            expect<ReportStatusOperationConflictException> { repository.changeStatus(requested.copy(reason = "Otro motivo de revisión")) }
            expect<ReportStatusOperationConflictException> {
                repository.changeStatus(requested.copy(reportId = UUID.randomUUID().toString()))
            }
            assertEquals(2, repository.readHistory(ReportHistoryQuery(id))!!.events.size)
        }
    }
    @Test fun authorityRevokedBeforeCommitRollsBackStatusAndEvent(): Unit = runBlocking {
        var checks = 0
        authorized(authority = { if (++checks == 3) throw ReportStatusPermissionDeniedException(); actor }).use { repository ->
            repository.createOnce(id, draft())
            expect<ReportStatusPermissionDeniedException> { repository.changeStatus(command()) }
            assertEquals(ReportStatus.REPORTED, repository.findById(id)!!.status)
            assertEquals(1L, repository.readHistory(ReportHistoryQuery(id))!!.latestSequence)
        }
    }
    @Test fun changedAuthorityIdentityBeforeCommitAlsoRollsBack(): Unit = runBlocking {
        var checks = 0
        authorized(authority = { if (++checks >= 3) id else actor }).use { repository ->
            repository.createOnce(id, draft())
            expect<ReportStatusPermissionDeniedException> { repository.changeStatus(command()) }
            assertEquals(ReportStatus.REPORTED, repository.findById(id)!!.status)
            assertEquals(1L, repository.readHistory(ReportHistoryQuery(id))!!.latestSequence)
        }
    }
    @Test fun cancellationBeforeCommitRollsBack(): Unit = runBlocking {
        var checks = 0
        val writeJob = Job()
        authorized(authority = { if (++checks == 3) writeJob.cancel(); actor }).use { repository ->
            repository.createOnce(id, draft())
            val task = async(writeJob) { repository.changeStatus(command()) }
            expect<CancellationException> { task.await() }
            assertEquals(ReportStatus.REPORTED, repository.findById(id)!!.status)
            assertEquals(1L, repository.readHistory(ReportHistoryQuery(id))!!.latestSequence)
        }
    }
    @Test fun responseLostAfterCommitIsRecoveredWithSameOperationWithoutSecondEvent(): Unit = runBlocking {
        val caller = QueuedDispatcher()
        authorized().use { repository ->
            repository.createOnce(id, draft())
            val requested = command()
            var received = false
            val pending = launch(caller) { repository.changeStatus(requested); received = true }
            caller.awaitTask().run()
            val result = caller.awaitTask()
            pending.cancel(); result.run(); pending.join()
            assertFalse(received)
            assertTrue(pending.isCancelled)
            val receipt = repository.changeStatus(requested)
            assertEquals(2L, receipt.sequence)
            assertEquals(listOf(receipt, repository.readHistory(ReportHistoryQuery(id))!!.events.last()),
                repository.readHistory(ReportHistoryQuery(id))!!.events)
        }
    }
    @Test fun twoRepositoryInstancesWithSameExpectedRevisionCommitOneChange(): Unit = runBlocking {
        val databaseName = name()
        authorized(databaseName).use { it.createOnce(id, draft()) }
        val workers = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val started = CountDownLatch(2)
        fun permission(): (ReportStatusChangeCommand) -> String {
            var checks = 0
            return { if (++checks == 1) { started.countDown(); check(started.await(10, TimeUnit.SECONDS)) }; actor }
        }
        val first = SqliteReportRepository(context, databaseName, clock(), workers, statusAuthority = permission())
        val second = SqliteReportRepository(context, databaseName, clock(), workers, statusAuthority = permission())
        try {
            val a = async { runCatching { first.changeStatus(command()) } }
            val b = async { runCatching { second.changeStatus(command()) } }
            val outcomes = listOf(a.await(), b.await())
            assertEquals(1, outcomes.count { it.isSuccess })
            assertTrue(outcomes.single { it.isFailure }.exceptionOrNull() is ReportStatusConflictException)
            assertEquals(2L, first.readHistory(ReportHistoryQuery(id))!!.latestSequence)
        } finally { first.close(); second.close(); workers.close() }
    }
    @Test fun boundedKeysetReadKeepsCurrentStatusAndDoesNotWrite(): Unit = runBlocking {
        val databaseName = name()
        authorized(databaseName).use { repository ->
            repository.createOnce(id, draft())
            repository.changeStatus(command())
            repository.changeStatus(command(2, ReportStatus.IN_REVIEW, ReportStatus.ATTENDED))
            repository.changeStatus(command(3, ReportStatus.ATTENDED, ReportStatus.CLOSED))
        }
        val before = context.getDatabasePath(databaseName).readBytes()
        authorized(databaseName).use { repository ->
            val first = repository.readHistory(ReportHistoryQuery(id, limit = 2))!!
            val last = repository.readHistory(ReportHistoryQuery(id, first.nextBeforeSequence, 2))!!
            assertEquals(listOf(4L, 3L), first.events.map { it.sequence })
            assertTrue(first.hasMore)
            assertEquals(listOf(2L, 1L), last.events.map { it.sequence })
            assertFalse(last.hasMore)
            assertTrue(last.completeFromCreation)
            assertEquals(ReportStatus.CLOSED, last.currentStatus)
            assertNull(repository.readHistory(ReportHistoryQuery(UUID.randomUUID().toString())))
        }
        assertArrayEquals(before, context.getDatabasePath(databaseName).readBytes())
    }
    @Test fun corruptedNumericHistoryFailsWithoutNormalizingOrReplacingData(): Unit = runBlocking {
        listOf("recorded_at_nanos = 1000000000", "sequence = 1.5").forEach { corruption ->
            val databaseName = name()
            authorized(databaseName).use { it.createOnce(id, draft()) }
            raw(databaseName) { db ->
                db.execSQL("PRAGMA ignore_check_constraints = ON")
                db.execSQL("UPDATE report_status_events SET $corruption")
            }
            val bytes = context.getDatabasePath(databaseName).readBytes()
            authorized(databaseName).use { repository ->
                expect<IllegalStateException> { repository.readHistory(ReportHistoryQuery(id)) }
            }
            assertArrayEquals(bytes, context.getDatabasePath(databaseName).readBytes())
        }
    }
    private fun raw(name: String, action: (SQLiteDatabase) -> Unit) =
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use(action)
    private suspend inline fun <reified T : Throwable> expect(noinline action: suspend () -> Any?) {
        try { action(); fail("Expected ${T::class.java.simpleName}") }
        catch (error: Throwable) { if (error !is T) throw error }
    }
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = LinkedBlockingQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun awaitTask(): Runnable = checkNotNull(tasks.poll(10, TimeUnit.SECONDS))
    }
}
