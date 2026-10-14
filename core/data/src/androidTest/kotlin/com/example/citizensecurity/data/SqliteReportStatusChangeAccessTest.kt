package com.example.citizensecurity.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite only in random, owned test files. Permissions are explicit fixtures, never roles. */
@RunWith(AndroidJUnit4::class)
class SqliteReportStatusChangeAccessTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = mutableListOf<String>()
    private val owner = UUID.randomUUID().toString()
    private val actor = UUID.randomUUID().toString()
    private val id = UUID.randomUUID().toString()
    private val now = Instant.parse("2026-10-14T01:02:03.123456789Z")
    private val scope = ReportStatusChangeScope(owner, actor)
    private fun name() = "status_recovery_test_${UUID.randomUUID()}.db".also(names::add)
    private fun command() = ReportStatusChangeCommand(UUID.randomUUID().toString(), id, 1,
        ReportStatus.REPORTED, ReportStatus.IN_REVIEW, "Revisión local del incidente 🌼")
    private fun request(command: ReportStatusChangeCommand = command()) = ReportStatusChangeRequest(scope, command, now)
    private fun repository(name: String?, authority: (ReportStatusChangeCommand) -> String = { actor }) =
        SqliteReportRepository(context, name, Clock.fixed(now, ZoneOffset.UTC), authorAccountId = actor, statusAuthority = authority)
    private fun draft() = NewReport(IncidentType.RISK, Priority.HIGH, "Hay un árbol caído frente al café.",
        now.minusSeconds(60), ReportLocation("Frente al café", 19.0, -99.0))
    private fun access(repository: SqliteReportRepository, guard: () -> Unit = {}) = SqliteReportStatusChangeAccess(scope, repository, guard)
    @After fun cleanOnlyOwnedDatabases() { names.forEach { context.deleteDatabase(it) } }

    @Test fun defaultRepositoryDeniesReceiptAndWriteBeforeCreatingDatabase() = runBlocking {
        val name = name()
        SqliteReportRepository(context, name).use { repository ->
            expect<ReportStatusPermissionDeniedException> { repository.findStatusChange(command()) }
            expect<ReportStatusPermissionDeniedException> { repository.changeStatusForActor(command(), actor) }
        }
        assertFalse(context.getDatabasePath(name).exists())
    }
    @Test fun defaultAccessDeniesEvenAnExplicitlyAuthorizedRepositoryBeforeSql() = runBlocking {
        val name = name(); var authorityChecks = 0
        repository(name) { authorityChecks++; actor }.use { repository ->
            val denied = SqliteReportStatusChangeAccess(scope, repository)
            expect<ReportStatusPermissionDeniedException> { denied.findReceipt(request()) }
            expect<ReportStatusPermissionDeniedException> { denied.change(request()) }
        }
        assertEquals(0, authorityChecks); assertFalse(context.getDatabasePath(name).exists())
    }
    @Test fun wrongExpectedActorCannotCreateDatabaseOrMutate() = runBlocking {
        val name = name(); val wrong = UUID.randomUUID().toString()
        repository(name).use { repository ->
            expect<ReportStatusPermissionDeniedException> { repository.findStatusChange(command(), wrong) }
            expect<ReportStatusPermissionDeniedException> { repository.changeStatusForActor(command(), wrong) }
        }
        assertFalse(context.getDatabasePath(name).exists())
    }
    @Test fun anotherOwnerOrActorRequestIsRejectedBeforeAuthorityOrSql() = runBlocking {
        val name = name(); var checks = 0
        repository(name) { checks++; actor }.use { repository ->
            listOf(ReportStatusChangeScope(UUID.randomUUID().toString(), actor),
                ReportStatusChangeScope(owner, UUID.randomUUID().toString()),
                ReportStatusChangeScope(null, actor)).forEach { other ->
                val foreign = request().copy(scope = other)
                expect<ReportStatusPermissionDeniedException> { access(repository).findReceipt(foreign) }
                expect<ReportStatusPermissionDeniedException> { access(repository).change(foreign) }
            }
        }
        assertEquals(0, checks); assertFalse(context.getDatabasePath(name).exists())
    }
    @Test fun authorizedMissingReceiptDoesNotCreateFileOrAnyDatabase() = runBlocking {
        val name = name(); val previous = context.databaseList().toSet()
        repository(name).use { assertNull(access(it).findReceipt(request())) }
        assertFalse(context.getDatabasePath(name).exists())
        assertEquals(previous, context.databaseList().toSet())
    }
    @Test fun receiptLookupDoesNotInitializeMissingInMemoryDatabase() = runBlocking {
        repository(null).use { repository ->
            assertNull(repository.findStatusChange(command(), actor))
            repository.createOnce(id, draft())
            assertNull(repository.findStatusChange(command(), actor))
        }
    }
    @Test fun existingOlderSchemaIsPreservedWithoutMigrationOrAddingHistory() = runBlocking {
        val name = name()
        repository(name).use { it.createOnce(id, draft()) }
        raw(name) { it.execSQL("DROP TABLE report_status_events"); it.version = 2 }
        val bytes = context.getDatabasePath(name).readBytes()
        repository(name).use { expect<IllegalStateException> { access(it).findReceipt(request()) } }
        assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
        raw(name) { db ->
            assertEquals(2, db.version)
            db.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE name = 'report_status_events'", null).use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0))
            }
        }
    }
    @Test fun exactReceiptReopensReadOnlyAndKeepsFolioActorBodyAndTimestamp() = runBlocking {
        val name = name(); val request = request()
        val event = repository(name).use { it.createOnce(id, draft()); access(it).change(request) }
        val bytes = context.getDatabasePath(name).readBytes()
        repository(name).use { repository ->
            val recovered = access(repository).findReceipt(request)!!
            assertEquals(id, recovered.reportId); assertEquals(event, recovered.event)
            assertEquals(actor, recovered.event.actorAccountId); assertEquals(now, recovered.event.recordedAt)
            assertTrue(recovered.matches(request))
        }
        assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
        raw(name) { assertEquals(3, it.version) }
    }
    @Test fun missingOperationInExistingSchemaDoesNotMutateAnyRows() = runBlocking {
        val name = name(); repository(name).use { it.createOnce(id, draft()) }
        val bytes = context.getDatabasePath(name).readBytes()
        repository(name).use { assertNull(access(it).findReceipt(request())) }
        assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
    }
    @Test fun operationReceiptRejectsDifferentFolioReasonSequenceAndTransition() = runBlocking {
        val name = name(); val request = request()
        repository(name).use { it.createOnce(id, draft()); access(it).change(request) }
        val bytes = context.getDatabasePath(name).readBytes()
        repository(name).use { repository ->
            listOf(request.command.copy(reportId = UUID.randomUUID().toString()),
                request.command.copy(reason = "Un motivo diferente"), request.command.copy(expectedSequence = 2),
                request.command.copy(expectedStatus = ReportStatus.IN_REVIEW, targetStatus = ReportStatus.ATTENDED))
                .forEach { changed -> expect<ReportStatusOperationConflictException> { access(repository).findReceipt(request.copy(command = changed)) } }
        }
        assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
    }
    @Test fun receiptOwnedByAnotherAuthorizedActorIsRejected() = runBlocking {
        val name = name(); val request = request(); val other = UUID.randomUUID().toString()
        repository(name) { other }.use { it.createOnce(id, draft()); it.changeStatus(request.command) }
        repository(name).use { expect<ReportStatusOperationConflictException> { access(it).findReceipt(request) } }
    }
    @Test fun creationOperationCannotBeReturnedAsAChangeReceipt() = runBlocking {
        val name = name(); repository(name).use { it.createOnce(id, draft()) }
        repository(name).use { repository ->
            expect<ReportStatusOperationConflictException> { repository.findStatusChange(command().copy(operationId = id), actor) }
        }
    }
    @Test fun authorityRevokedAfterReadDoesNotReturnReceiptOrModifyHistory() = runBlocking {
        val name = name(); val request = request()
        repository(name).use { it.createOnce(id, draft()); access(it).change(request) }
        val bytes = context.getDatabasePath(name).readBytes(); var checks = 0
        repository(name) { if (++checks == 3) throw ReportStatusPermissionDeniedException(); actor }.use { repository ->
            expect<ReportStatusPermissionDeniedException> { access(repository).findReceipt(request) }
        }
        assertEquals(3, checks); assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
    }
    @Test fun changedAuthorityBeforeCommitRollsBackStatusAndEvent() = runBlocking {
        val name = name(); val request = request(); var checks = 0
        repository(name) { if (++checks == 3) UUID.randomUUID().toString() else actor }.use { repository ->
            repository.createOnce(id, draft())
            expect<ReportStatusPermissionDeniedException> { access(repository).change(request) }
            assertEquals(ReportStatus.REPORTED, repository.findById(id)!!.status)
            assertEquals(1L, repository.readHistory(ReportHistoryQuery(id))!!.latestSequence)
        }
    }
    @Test fun replayAfterReopeningReturnsOriginalReceiptWithoutDuplicatingEvents() = runBlocking {
        val name = name(); val request = request()
        val first = repository(name).use { it.createOnce(id, draft()); access(it).change(request) }
        repository(name).use { repository ->
            assertEquals(first, access(repository).change(request))
            assertEquals(first, access(repository).findReceipt(request)!!.event)
            assertEquals(2, repository.readHistory(ReportHistoryQuery(id))!!.events.size)
        }
    }
    @Test fun sessionRevokedInsideTransactionBeforeCommitRollsBackBothMutations() = runBlocking {
        val name = name(); val request = request(); var checks = 0
        repository(name).use { repository ->
            repository.createOnce(id, draft())
            val guarded = access(repository) { if (++checks == 4) throw ReportStatusPermissionDeniedException() }
            expect<ReportStatusPermissionDeniedException> { guarded.change(request) }
            assertEquals(ReportStatus.REPORTED, repository.findById(id)!!.status)
            assertEquals(1L, repository.readHistory(ReportHistoryQuery(id))!!.latestSequence)
        }
    }
    @Test fun sessionRevokedAfterCommitRequiresFreshRecoveryInsteadOfAnotherOperation() = runBlocking {
        val name = name(); val request = request(); var checks = 0
        repository(name).use { repository ->
            repository.createOnce(id, draft())
            val guarded = access(repository) { if (++checks == 5) throw ReportStatusPermissionDeniedException() }
            expect<ReportStatusPermissionDeniedException> { guarded.change(request) }
        }
        repository(name).use { repository ->
            assertEquals(2L, access(repository).findReceipt(request)!!.event.sequence)
            assertEquals(2, repository.readHistory(ReportHistoryQuery(id))!!.events.size)
        }
    }
    @Test fun lostCallbackAfterCommitCanBeReadWithoutReissuingWrite() = runBlocking {
        val name = name(); val request = request(); val caller = QueuedDispatcher()
        repository(name).use { repository ->
            repository.createOnce(id, draft()); var received = false
            val pending = launch(caller) { access(repository).change(request); received = true }
            caller.awaitTask().run()
            val result = caller.awaitTask(); pending.cancel(); result.run(); pending.join()
            assertFalse(received); assertTrue(pending.isCancelled)
        }
        repository(name).use { repository ->
            assertEquals(2L, access(repository).findReceipt(request)!!.event.sequence)
            assertEquals(2, repository.readHistory(ReportHistoryQuery(id))!!.events.size)
        }
    }
    @Test fun anotherDatabaseCannotExposeTheOriginalOperationsReceipt() = runBlocking {
        val a = name(); val b = name(); val request = request()
        repository(a).use { it.createOnce(id, draft()); access(it).change(request) }
        repository(b).use { repository ->
            repository.createOnce(id, draft())
            assertNull(access(repository).findReceipt(request))
            assertEquals(ReportStatus.REPORTED, repository.findById(id)!!.status)
        }
    }
    @Test fun corruptDatabaseIsPreservedWithoutRepairOrRecreation() = runBlocking {
        val name = name(); val bytes = "not a SQLite database".toByteArray()
        context.getDatabasePath(name).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        repository(name).use { expect<SQLiteDatabaseCorruptException> { access(it).findReceipt(request()) } }
        assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
    }
    @Test fun cancelledReceiptLookupDoesNotCreateDatabase() = runBlocking {
        val name = name(); val cancelled = Job().also { it.cancel() }
        repository(name).use { repository ->
            expect<CancellationException> { withContext(cancelled) { access(repository).findReceipt(request()) } }
        }
        assertFalse(context.getDatabasePath(name).exists())
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
