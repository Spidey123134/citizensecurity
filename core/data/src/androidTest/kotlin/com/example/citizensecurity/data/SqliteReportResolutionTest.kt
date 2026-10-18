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

/** Real SQL, owned random fixture files only. Test callbacks never assign application roles. */
@RunWith(AndroidJUnit4::class)
class SqliteReportResolutionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = mutableListOf<String>()
    private val id = UUID.randomUUID().toString()
    private val owner = UUID.randomUUID().toString()
    private val other = UUID.randomUUID().toString()
    private val now = Instant.parse("2026-10-17T01:02:03.123456789Z")
    private val author get() = ReportResolutionActor(owner, ReportResolutionActorKind.AUTHOR)
    private val administrator get() = ReportResolutionActor(owner, ReportResolutionActorKind.LOCAL_ADMINISTRATOR)
    private fun name() = "resolution_test_${UUID.randomUUID()}.db".also(names::add)
    private fun draft() = NewReport(IncidentType.RISK, Priority.HIGH, "Hay un árbol caído frente al café.",
        now.minusSeconds(60), ReportLocation("Frente al café", 19.0, -99.0))
    private fun repository(name: String? = null,
        account: String? = owner,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        authority: (String, String?) -> ReportResolutionActor = { _, creator ->
            if (creator != owner) throw ReportResolutionPermissionDeniedException()
            author
        }) = SqliteReportRepository(context, name, Clock.fixed(now, ZoneOffset.UTC), dispatcher,
            authorAccountId = account, statusAuthority = { owner }, resolutionAuthority = authority)
    private fun command(sequence: Long = 0, from: ReportResolution = ReportResolution.UNCONFIRMED,
        to: ReportResolution = ReportResolution.RESOLVED, operation: String = UUID.randomUUID().toString(),
        actor: ReportResolutionActor = author, reason: String = "Ya retiraron el árbol frente al café 🌳") =
        ReportResolutionCommand(operation, id, sequence, from, to, actor, reason)
    private fun status(sequence: Long = 1, from: ReportStatus = ReportStatus.REPORTED,
        to: ReportStatus = ReportStatus.IN_REVIEW, operation: String = UUID.randomUUID().toString()) =
        ReportStatusChangeCommand(operation, id, sequence, from, to, "Revisión local del incidente")
    @After fun cleanOnlyOwnedFiles() { names.forEach { context.deleteDatabase(it) } }

    @Test fun defaultDeniesWriteAndReceiptWithoutCreatingStorage() = runBlocking {
        val name = name()
        SqliteReportRepository(context, name).use {
            expect<ReportResolutionPermissionDeniedException> { it.recordResolution(command()) }
            expect<ReportResolutionPermissionDeniedException> { it.findResolutionReceipt(command()) }
        }
        assertFalse(context.getDatabasePath(name).exists())
    }

    @Test fun closedReportStillStartsUnconfirmedWithNoInventedOutcomeEvent() = runBlocking {
        repository().use {
            it.createOnce(id, draft())
            it.changeStatus(status())
            it.changeStatus(status(2, ReportStatus.IN_REVIEW, ReportStatus.ATTENDED))
            it.changeStatus(status(3, ReportStatus.ATTENDED, ReportStatus.CLOSED))
            val before = it.readHistory(ReportHistoryQuery(id))!!.events
            val result = it.readResolution(ReportResolutionQuery(id))!!
            assertEquals(ReportResolution.UNCONFIRMED, result.resolution)
            assertEquals(0L, result.latestSequence)
            assertTrue(result.events.isEmpty()); assertFalse(result.hasMore)
            assertEquals(author, result.actor)
            assertEquals(before, it.readHistory(ReportHistoryQuery(id))!!.events)
            assertEquals(ReportStatus.CLOSED, it.findById(id)!!.status)
        }
    }

    @Test fun authorDeclarationKeepsIdentityReasonNanosecondsAndReopensExactly() = runBlocking {
        val name = name(); val requested = command()
        val saved = repository(name).use { it.createOnce(id, draft()); it.recordResolution(requested) }
        repository(name).use {
            val page = it.readResolution(ReportResolutionQuery(id))!!
            assertEquals(saved, page.events.single()); assertEquals(1L, page.latestSequence)
            assertEquals(ReportResolution.RESOLVED, page.resolution)
            assertEquals(author, saved.actor); assertEquals(now, saved.recordedAt)
            assertEquals(requested.reason, saved.reason); assertTrue(saved.matches(requested))
            assertEquals(saved, it.findResolutionReceipt(requested))
            assertEquals(ReportStatus.REPORTED, it.findById(id)!!.status)
            assertEquals(1L, it.readHistory(ReportHistoryQuery(id))!!.latestSequence)
        }
    }

    @Test fun independentAdministratorAuthorityCanDeclareAndRecordsItsDifferentKind() = runBlocking {
        repository(authority = { _, _ -> administrator }).use {
            it.createOnce(id, draft())
            val saved = it.recordResolution(command(actor = administrator, to = ReportResolution.UNRESOLVED))
            assertEquals(administrator, saved.actor)
            assertEquals(ReportResolution.UNRESOLVED, it.readResolution(ReportResolutionQuery(id))!!.resolution)
            expect<ReportResolutionPermissionDeniedException> { it.recordResolution(command()) }
        }
    }

    @Test fun forgedAuthorIdentityCannotOverrideTrueCreatedEventAuthor() = runBlocking {
        val name = name()
        repository(name, account = other).use { it.createOnce(id, draft()) }
        repository(name, authority = { _, _ -> author }).use {
            assertNull(it.readResolution(ReportResolutionQuery(id))!!.actor)
            expect<ReportResolutionPermissionDeniedException> { it.recordResolution(command()) }
        }
        raw(name) { assertEquals(0L, count(it)) }
    }

    @Test fun callbackCannotGrantActorOutsideCapturedAccountPartition() = runBlocking {
        repository(authority = { _, _ -> ReportResolutionActor(other, ReportResolutionActorKind.LOCAL_ADMINISTRATOR) }).use {
            it.createOnce(id, draft())
            val forged = command(actor = ReportResolutionActor(other, ReportResolutionActorKind.LOCAL_ADMINISTRATOR))
            expect<ReportResolutionPermissionDeniedException> { it.recordResolution(forged) }
            assertNull(it.readResolution(ReportResolutionQuery(id))!!.actor)
        }
    }

    @Test fun academicNullAccountCannotAcquireAuthorityEvenWithPermissiveCallback() = runBlocking {
        repository(account = null, authority = { _, _ -> administrator }).use {
            it.createOnce(id, draft())
            expect<ReportResolutionPermissionDeniedException> { it.recordResolution(command(actor = administrator)) }
            expect<ReportResolutionPermissionDeniedException> { it.findResolutionReceipt(command(actor = administrator)) }
            assertNull(it.readResolution(ReportResolutionQuery(id))!!.actor)
        }
    }

    @Test fun unknownLegacyCreatorCannotBeInventedAsAuthorButIndependentAdminCanDeclare() = runBlocking {
        val name = name()
        repository(name, account = null).use { it.createOnce(id, draft()) }
        repository(name).use {
            assertNull(it.readResolution(ReportResolutionQuery(id))!!.actor)
            expect<ReportResolutionPermissionDeniedException> { it.recordResolution(command()) }
        }
        repository(name, authority = { _, creator -> assertNull(creator); administrator }).use {
            assertEquals(administrator, it.recordResolution(command(actor = administrator)).actor)
        }
    }

    @Test fun replayReturnsExactOriginalReceiptAfterLaterDeclarationWithoutAnotherAppend() = runBlocking {
        repository().use {
            it.createOnce(id, draft()); val requested = command()
            val first = it.recordResolution(requested)
            it.recordResolution(command(1, ReportResolution.RESOLVED, ReportResolution.UNRESOLVED))
            assertEquals(first, it.recordResolution(requested))
            assertEquals(first, it.findResolutionReceipt(requested))
            assertEquals(listOf(2L, 1L), it.readResolution(ReportResolutionQuery(id))!!.events.map { e -> e.sequence })
        }
    }

    @Test fun operationBodyCollisionCannotChangeReasonTargetOrExpectedRevision() = runBlocking {
        repository().use {
            it.createOnce(id, draft()); val requested = command(); it.recordResolution(requested)
            listOf(requested.copy(reason = "Todavía queda un problema distinto"),
                requested.copy(targetResolution = ReportResolution.UNRESOLVED),
                requested.copy(expectedSequence = 4)).forEach { changed ->
                expect<ReportResolutionOperationConflictException> { it.recordResolution(changed) }
                expect<ReportResolutionOperationConflictException> { it.findResolutionReceipt(changed) }
            }
            assertEquals(1L, it.readResolution(ReportResolutionQuery(id))!!.latestSequence)
        }
    }

    @Test fun staleRevisionOrStaleResolutionCannotOverwriteCurrentResult() = runBlocking {
        repository().use {
            it.createOnce(id, draft()); it.recordResolution(command())
            expect<ReportResolutionConflictException> { it.recordResolution(command()) }
            expect<ReportResolutionConflictException> {
                it.recordResolution(command(1, ReportResolution.UNCONFIRMED, ReportResolution.UNRESOLVED))
            }
            assertEquals(ReportResolution.RESOLVED, it.readResolution(ReportResolutionQuery(id))!!.resolution)
        }
    }

    @Test fun operationNamespaceIsGlobalAcrossCreationStatusAndResolutionInBothDirections() = runBlocking {
        repository().use { repository ->
            repository.createOnce(id, draft())
            expect<ReportResolutionOperationConflictException> { repository.recordResolution(command(operation = id)) }
            val statusRequested = status(); repository.changeStatus(statusRequested)
            expect<ReportResolutionOperationConflictException> { repository.recordResolution(command(operation = statusRequested.operationId)) }
            expect<ReportRequestConflictException> { repository.createOnce(statusRequested.operationId, draft()) }
            val resolved = command(); repository.recordResolution(resolved)
            expect<ReportStatusOperationConflictException> {
                repository.changeStatus(status(2, ReportStatus.IN_REVIEW, ReportStatus.ATTENDED, resolved.operationId))
            }
            expect<ReportStatusOperationConflictException> {
                repository.findStatusChange(status(2, ReportStatus.IN_REVIEW, ReportStatus.ATTENDED, resolved.operationId))
            }
            expect<ReportRequestConflictException> { repository.createOnce(resolved.operationId, draft()) }
            assertEquals(1, repository.list().size)
        }
    }

    @Test fun authorityRevokedAtFinalCheckRollsBackEventBeforeCommitHook() = runBlocking {
        var checks = 0; var hookCalled = false
        repository(authority = { _, _ -> if (++checks == 3) throw ReportResolutionPermissionDeniedException(); author }).use {
            it.createOnce(id, draft())
            expect<ReportResolutionPermissionDeniedException> { it.recordResolution(command()) { hookCalled = true } }
            assertFalse(hookCalled)
            assertEquals(0L, it.readResolution(ReportResolutionQuery(id))!!.latestSequence)
        }
    }

    @Test fun finalForegroundHookRunsAfterAuthorityAndItsFailureRollsBack() = runBlocking {
        var checks = 0
        repository(authority = { _, _ -> checks++; author }).use {
            it.createOnce(id, draft())
            expect<SecurityException> {
                it.recordResolution(command()) { assertEquals(3, checks); throw SecurityException("Salió de primer plano") }
            }
            assertEquals(0L, it.readResolution(ReportResolutionQuery(id))!!.latestSequence)
        }
    }

    @Test fun cancellationInFinalHookRollsBackEvent() = runBlocking {
        val job = Job()
        repository().use {
            it.createOnce(id, draft())
            val task = async(context = job + Dispatchers.Default) { it.recordResolution(command()) { job.cancel() } }
            expect<CancellationException> { task.await() }
            assertEquals(0L, it.readResolution(ReportResolutionQuery(id))!!.latestSequence)
        }
    }

    @Test fun changedAuthorityIdentityAtFinalCheckAlsoRollsBack() = runBlocking {
        var checks = 0
        repository(authority = { _, _ -> if (++checks >= 3) administrator else author }).use {
            it.createOnce(id, draft())
            expect<ReportResolutionPermissionDeniedException> { it.recordResolution(command()) }
        }
    }

    @Test fun receiptForAbsentDatabaseCanBeReadOnlyByAuthorizedAdminWithoutCreatingIt() = runBlocking {
        val name = name()
        repository(name, authority = { _, _ -> administrator }).use {
            assertNull(it.findResolutionReceipt(command(actor = administrator)))
            expect<ReportResolutionNotFoundException> { it.recordResolution(command(actor = administrator)) }
        }
        assertFalse(context.getDatabasePath(name).exists())
    }

    @Test fun versionFourReceiptCheckPreservesBytesAndVersionAndDoesNotInventAnEvent() = runBlocking {
        val name = name()
        val requested = status()
        val statusEvent = repository(name).use { it.createOnce(id, draft()); it.changeStatus(requested) }
        raw(name) { it.execSQL("DROP TABLE report_resolution_events"); it.version = 4 }
        val before = context.getDatabasePath(name).readBytes()
        repository(name).use {
            assertNull(it.findResolutionReceipt(command()))
            assertEquals(statusEvent, it.findStatusChange(requested)!!.event)
        }
        assertArrayEquals(before, context.getDatabasePath(name).readBytes())
        raw(name) { assertEquals(4, it.version) }
    }

    @Test fun upgradeFromFourPreservesPhotoReportHistoryAndExistingIndexWithoutInventingResolution() = runBlocking {
        val name = name()
        val photo = ReportPhoto(UUID.randomUUID().toString(), "a".repeat(64), 1024, 640, 480)
        val saved = repository(name).use { it.createOnce(id, draft().copy(photo = photo)); it.changeStatus(status()); it.findById(id)!! }
        val history = repository(name).use { it.readHistory(ReportHistoryQuery(id))!!.events }
        raw(name) { it.execSQL("DROP TABLE report_resolution_events"); it.version = 4 }
        repository(name).use {
            assertEquals(saved, it.findById(id)); assertEquals(photo, it.findById(id)!!.photo)
            assertEquals(history, it.readHistory(ReportHistoryQuery(id))!!.events)
            assertTrue(it.readResolution(ReportResolutionQuery(id))!!.events.isEmpty())
        }
        raw(name) {
            assertEquals(5, it.version)
            it.rawQuery("PRAGMA index_info(${ReportActivitySql.INDEX_NAME})", null).use { cursor ->
                val columns = buildList { while (cursor.moveToNext()) add(cursor.getString(2)) }
                assertEquals(listOf("recorded_at_seconds", "recorded_at_nanos", "operation_id"), columns)
            }
        }
    }

    @Test fun failedResolutionMigrationPreservesLegacyRowsVersionAndUnexpectedTableBytes() = runBlocking {
        val name = name(); val saved = repository(name).use { it.createOnce(id, draft()) }
        raw(name) {
            it.execSQL("DROP TABLE report_resolution_events")
            it.execSQL("CREATE TABLE report_resolution_events (note TEXT)")
            it.execSQL("INSERT INTO report_resolution_events VALUES ('conservar')"); it.version = 4
        }
        val before = context.getDatabasePath(name).readBytes()
        repository(name).use { expect<SQLiteException> { it.readResolution(ReportResolutionQuery(id)) } }
        assertArrayEquals(before, context.getDatabasePath(name).readBytes())
        raw(name) {
            assertEquals(4, it.version)
            it.rawQuery("SELECT description FROM reports WHERE id = ?", arrayOf(id)).use { c -> assertTrue(c.moveToFirst()); assertEquals(saved.description, c.getString(0)) }
            it.rawQuery("SELECT note FROM report_resolution_events", null).use { c -> assertTrue(c.moveToFirst()); assertEquals("conservar", c.getString(0)) }
        }
    }

    @Test fun keysetPagesRemainExclusiveAndTheirHeadIsFreshAfterAnotherAppend() = runBlocking {
        repository().use {
            it.createOnce(id, draft())
            repeat(5) { index ->
                val from = if (index == 0) ReportResolution.UNCONFIRMED else if (index % 2 == 1) ReportResolution.RESOLVED else ReportResolution.UNRESOLVED
                val to = if (index % 2 == 0) ReportResolution.RESOLVED else ReportResolution.UNRESOLVED
                it.recordResolution(command(index.toLong(), from, to))
            }
            val first = it.readResolution(ReportResolutionQuery(id, limit = 2))!!
            assertEquals(listOf(5L, 4L), first.events.map { e -> e.sequence }); assertTrue(first.hasMore)
            it.recordResolution(command(5, ReportResolution.RESOLVED, ReportResolution.UNRESOLVED))
            val older = it.readResolution(ReportResolutionQuery(id, first.nextBeforeSequence, 2))!!
            assertEquals(listOf(3L, 2L), older.events.map { e -> e.sequence })
            assertEquals(6L, older.latestSequence); assertEquals(ReportResolution.UNRESOLVED, older.resolution)
            val last = it.readResolution(ReportResolutionQuery(id, older.nextBeforeSequence, 2))!!
            assertEquals(listOf(1L), last.events.map { e -> e.sequence }); assertFalse(last.hasMore); assertNull(last.nextBeforeSequence)
            assertNull(it.readResolution(ReportResolutionQuery(UUID.randomUUID().toString())))
        }
    }

    @Test fun permissionChangeDuringSnapshotPreventsReturningStaleWritableActor() = runBlocking {
        var checks = 0
        repository(authority = { _, _ -> if (++checks == 2) throw ReportResolutionPermissionDeniedException(); author }).use {
            it.createOnce(id, draft())
            expect<ReportResolutionPermissionDeniedException> { it.readResolution(ReportResolutionQuery(id)) }
        }
    }

    @Test fun malformedNumericOrUnicodeRowFailsReadAndWriteWithoutChangingStoredBytes() = runBlocking {
        listOf("recorded_at_nanos = 1000000000", "recorded_at_seconds = 1.5", "actor_account_id = '${"x".repeat(36)}'",
            "reason = char(10) || 'motivo'").forEach { damage ->
            val name = name()
            repository(name).use { it.createOnce(id, draft()); it.recordResolution(command()) }
            raw(name) { it.execSQL("PRAGMA ignore_check_constraints = ON"); it.execSQL("UPDATE report_resolution_events SET $damage") }
            val before = context.getDatabasePath(name).readBytes()
            repository(name).use {
                expect<RuntimeException> { it.readResolution(ReportResolutionQuery(id)) }
                expect<RuntimeException> { it.recordResolution(command(1, ReportResolution.RESOLVED, ReportResolution.UNRESOLVED)) }
            }
            assertArrayEquals(before, context.getDatabasePath(name).readBytes())
        }
    }

    @Test fun brokenHistoricalChainOutsideRequestedPageFailsClosedAndPreservesBytes() = runBlocking {
        listOf("DELETE FROM report_resolution_events WHERE sequence = 1",
            "UPDATE report_resolution_events SET from_resolution = 'UNRESOLVED' WHERE sequence = 1").forEach { damage ->
            val name = name()
            repository(name).use {
                it.createOnce(id, draft()); it.recordResolution(command())
                it.recordResolution(command(1, ReportResolution.RESOLVED, ReportResolution.UNRESOLVED))
            }
            raw(name) { it.execSQL("PRAGMA ignore_check_constraints = ON"); it.execSQL(damage) }
            val before = context.getDatabasePath(name).readBytes()
            repository(name).use {
                expect<IllegalStateException> { it.readResolution(ReportResolutionQuery(id, limit = 1)) }
                expect<IllegalStateException> { it.recordResolution(command(2, ReportResolution.UNRESOLVED, ReportResolution.RESOLVED)) }
            }
            assertArrayEquals(before, context.getDatabasePath(name).readBytes())
        }
    }

    @Test fun reusedOperationWithMissingFolioIsCollisionRatherThanMissingOrAbsentReceipt() = runBlocking {
        repository(authority = { _, _ -> administrator }).use {
            it.createOnce(id, draft())
            val requested = command(actor = administrator)
            it.recordResolution(requested)
            val changed = requested.copy(reportId = UUID.randomUUID().toString())
            expect<ReportResolutionOperationConflictException> { it.findResolutionReceipt(changed) }
            expect<ReportResolutionOperationConflictException> { it.recordResolution(changed) }
            assertEquals(1L, it.readResolution(ReportResolutionQuery(id))!!.latestSequence)
        }
    }

    @Test fun exactReceiptAndReplayCannotConfirmAnOperationInsideBrokenChronology() = runBlocking {
        val name = name(); val requested = command()
        repository(name).use {
            it.createOnce(id, draft()); it.recordResolution(requested)
            it.recordResolution(command(1, ReportResolution.RESOLVED, ReportResolution.UNRESOLVED))
        }
        raw(name) { it.execSQL("PRAGMA ignore_check_constraints = ON"); it.execSQL("UPDATE report_resolution_events SET from_resolution = 'UNCONFIRMED' WHERE sequence = 2") }
        val before = context.getDatabasePath(name).readBytes()
        repository(name).use {
            expect<IllegalStateException> { it.findResolutionReceipt(requested) }
            expect<IllegalStateException> { it.recordResolution(requested) }
        }
        assertArrayEquals(before, context.getDatabasePath(name).readBytes())
    }

    @Test fun twoRepositoryInstancesWithSameReviewedRevisionCommitExactlyOneDeclaration() = runBlocking {
        val name = name(); repository(name).use { it.createOnce(id, draft()) }
        val workers = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val barrier = CountDownLatch(2)
        fun authority(): (String, String?) -> ReportResolutionActor {
            var checks = 0
            return { _, _ -> if (++checks == 1) { barrier.countDown(); check(barrier.await(10, TimeUnit.SECONDS)) }; author }
        }
        val a = repository(name, dispatcher = workers, authority = authority())
        val b = repository(name, dispatcher = workers, authority = authority())
        try {
            val first = async { runCatching { a.recordResolution(command()) } }
            val second = async { runCatching { b.recordResolution(command(to = ReportResolution.UNRESOLVED)) } }
            val outcomes = listOf(first.await(), second.await())
            assertEquals(1, outcomes.count { it.isSuccess })
            assertTrue(outcomes.single { it.isFailure }.exceptionOrNull() is ReportResolutionConflictException)
            assertEquals(1L, a.readResolution(ReportResolutionQuery(id))!!.latestSequence)
        } finally { a.close(); b.close(); workers.close() }
    }

    @Test fun responseLostAfterCommitIsRecoveredReadOnlyWithOriginalReceiptAndNoDuplicate() = runBlocking {
        val caller = QueuedDispatcher()
        repository().use {
            it.createOnce(id, draft()); val requested = command(); var delivered = false
            val task = launch(caller) { it.recordResolution(requested); delivered = true }
            caller.awaitTask().run()
            val result = caller.awaitTask(); task.cancel(); result.run(); task.join()
            assertFalse(delivered)
            val recovered = it.findResolutionReceipt(requested)!!
            assertEquals(1L, recovered.sequence); assertEquals(now, recovered.recordedAt)
            assertEquals(recovered, it.recordResolution(requested))
            assertEquals(1, it.readResolution(ReportResolutionQuery(id))!!.events.size)
        }
    }

    private fun raw(name: String, action: (SQLiteDatabase) -> Unit) =
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use(action)
    private fun count(db: SQLiteDatabase) = db.rawQuery("SELECT COUNT(*) FROM report_resolution_events", null).use {
        check(it.moveToFirst()); it.getLong(0)
    }
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
