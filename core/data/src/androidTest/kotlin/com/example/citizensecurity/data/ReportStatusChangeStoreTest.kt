package com.example.citizensecurity.data

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.*
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Random owner/actor files only; no academic production journal or report database is touched. */
@RunWith(AndroidJUnit4::class)
class ReportStatusChangeStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scopes = mutableListOf<ReportStatusChangeScope>()
    private fun scope(owner: String? = UUID.randomUUID().toString(), actor: String = UUID.randomUUID().toString()) =
        ReportStatusChangeScope(owner, actor).also(scopes::add)
    private fun request(scope: ReportStatusChangeScope) = ReportStatusChangeRequest(scope,
        ReportStatusChangeCommand(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1,
            ReportStatus.REPORTED, ReportStatus.IN_REVIEW, "Revisión local del café 🌼"), Instant.parse("2026-10-14T01:02:03.123456789Z"))
    private fun store(scope: ReportStatusChangeScope, guard: () -> Unit = {}) = ReportStatusChangeStore(context, scope, guard)
    private fun file(scope: ReportStatusChangeScope) = File(context.filesDir, ReportStatusChangeStore.fileName(scope))
    @After fun cleanOnlyOwnedFiles() { scopes.forEach { scope ->
        val base = file(scope)
        listOf(base, File(base.path + ".bak"), File(base.path + ".new")).forEach { it.delete() }
    } }

    @Test fun readingEmptyJournalDoesNotCreateFilesOrDatabase() = runBlocking {
        val scope = scope(); val databases = context.databaseList().toSet()
        assertNull(store(scope).read()); assertFalse(file(scope).exists())
        assertEquals(databases, context.databaseList().toSet())
    }
    @Test fun reopeningKeepsEntireRequestAndRetainingItAgainDoesNotRewrite() = runBlocking {
        val scope = scope(); val request = request(scope)
        store(scope).retain(request); val bytes = file(scope).readBytes()
        assertEquals(request, store(scope).read())
        store(scope).retain(request)
        assertArrayEquals(bytes, file(scope).readBytes())
    }
    @Test fun ownerActorAndAcademicBindingsHaveSeparatePaths() = runBlocking {
        val a = scope(); val otherActor = scope(a.ownerAccountId)
        val otherOwner = scope(actor = a.actorAccountId); val academic = scope(null, a.actorAccountId)
        store(a).retain(request(a))
        listOf(otherActor, otherOwner, academic).forEach { assertNull(store(it).read()); assertFalse(file(it).exists()) }
        assertEquals(4, listOf(a, otherActor, otherOwner, academic).map { ReportStatusChangeStore.fileName(it) }.toSet().size)
    }
    @Test fun anotherOperationCannotReplaceTheWholePendingRequest() = runBlocking {
        val scope = scope(); val request = request(scope); store(scope).retain(request)
        val bytes = file(scope).readBytes()
        expect<IllegalStateException> { store(scope).retain(request(scope)) }
        assertArrayEquals(bytes, file(scope).readBytes())
    }
    @Test fun sameOperationWithDifferentReasonOrTimestampCannotBeRetainedOrCleared() = runBlocking {
        val scope = scope(); val request = request(scope); store(scope).retain(request)
        listOf(request.copy(command = request.command.copy(reason = "Un motivo diferente")),
            request.copy(requestedAt = request.requestedAt.plusNanos(1))).forEach { changed ->
            expect<IllegalStateException> { store(scope).retain(changed) }
            expect<IllegalStateException> { store(scope).clear(changed) }
            assertEquals(request, store(scope).read())
        }
    }
    @Test fun wrongScopeIsRejectedWithoutCreatingEitherFile() = runBlocking {
        val a = scope(); val b = scope(); val request = request(b)
        expect<IllegalArgumentException> { store(a).retain(request) }
        expect<IllegalArgumentException> { store(a).clear(request) }
        assertFalse(file(a).exists()); assertFalse(file(b).exists())
    }
    @Test fun malformedUnknownOversizedOrUtf8BodiesAreNeverOverwrittenOrCleared() = runBlocking {
        listOf("{\"schema\":2}".toByteArray(), "{".toByteArray(), byteArrayOf(0xc3.toByte(), 0x28),
            ByteArray(8_193) { 0x20 }).forEach { bytes ->
            val scope = scope(); val request = request(scope); file(scope).writeBytes(bytes)
            expect<IOException> { store(scope).read() }
            expect<IOException> { store(scope).retain(request) }
            expect<IOException> { store(scope).clear(request) }
            assertArrayEquals(bytes, file(scope).readBytes())
        }
    }
    @Test fun validBodyUnderWrongOwnerFileIsCorruptAndPreserved() = runBlocking {
        val a = scope(); val b = scope(); val bytes = ReportStatusChangeCodec.encode(request(b))
        file(a).writeBytes(bytes)
        expect<IOException> { store(a).read() }
        expect<IOException> { store(a).retain(request(a)) }
        assertArrayEquals(bytes, file(a).readBytes())
    }
    @Test fun atomicBackupRestoresLastCompleteBodyInsteadOfPartialBase() = runBlocking {
        val scope = scope(); val expected = request(scope); store(scope).retain(expected)
        assertTrue(file(scope).renameTo(File(file(scope).path + ".bak")))
        file(scope).writeText("partial")
        assertEquals(expected, store(scope).read())
    }
    @Test fun backupAloneIsPendingAndCannotBeOverwrittenByAnotherOperation() = runBlocking {
        val scope = scope(); val expected = request(scope); store(scope).retain(expected)
        assertTrue(file(scope).renameTo(File(file(scope).path + ".bak")))
        expect<IllegalStateException> { store(scope).retain(request(scope)) }
        assertEquals(expected, store(scope).read())
    }
    @Test fun exactClearIsIdempotentAndRemovesOnlyItsOwnedJournal() = runBlocking {
        val a = scope(); val b = scope(); val request = request(a); val second = request(b)
        store(a).retain(request); store(b).retain(second)
        store(a).clear(request); store(a).clear(request)
        assertNull(store(a).read()); assertEquals(second, store(b).read())
    }
    @Test fun concurrentInstancesRetainExactlyOneCompleteOperation() = runBlocking {
        val scope = scope(); val attempts = List(2) { request(scope) }
        val outcomes = attempts.map { request -> async(Dispatchers.IO) { runCatching { store(scope).retain(request) } } }.awaitAll()
        assertEquals(1, outcomes.count { it.isSuccess })
        assertTrue(outcomes.single { it.isFailure }.exceptionOrNull() is IllegalStateException)
        assertTrue(store(scope).read() in attempts)
    }
    @Test fun accessRevokedBeforeCommitRollsBackWithoutConfirming() = runBlocking {
        val scope = scope(); var checks = 0
        val guarded = store(scope) { if (++checks == 3) error("Revoked before commit") }
        expect<IllegalStateException> { guarded.retain(request(scope)) }
        assertNull(store(scope).read())
    }
    @Test fun lostReceiptAfterCommitRetainsTheSameOperationForRecovery() = runBlocking {
        val scope = scope(); val request = request(scope); var checks = 0
        val guarded = store(scope) { if (++checks == 4) error("Revoked after commit") }
        expect<IllegalStateException> { guarded.retain(request) }
        assertEquals(request, store(scope).read())
        store(scope).retain(request)
        assertEquals(request, store(scope).read())
    }
    @Test fun cancellationTriggeredByLastCommitGuardDoesNotConfirmPendingBody() = runBlocking {
        val scope = scope(); val request = request(scope); var checks = 0
        val pending = async {
            val writeJob = currentCoroutineContext()[Job]!!
            val guarded = store(scope) { if (++checks == 3) writeJob.cancel() }
            guarded.retain(request)
        }
        expect<CancellationException> { pending.await() }
        assertNull(store(scope).read())
    }
    @Test fun revocationBeforeClearPreservesRequest() = runBlocking {
        val scope = scope(); val request = request(scope); store(scope).retain(request); var checks = 0
        expect<IllegalStateException> { store(scope) { if (++checks == 2) error("Revoked") }.clear(request) }
        assertEquals(request, store(scope).read())
    }
    @Test fun cancelledRetainDoesNotWriteAndFileOperationsUseBackgroundDispatcher() = runBlocking {
        val scope = scope(); val request = request(scope)
        val cancelled = Job().also { it.cancel() }
        expect<CancellationException> { withContext(cancelled) { store(scope).retain(request) } }
        assertFalse(file(scope).exists())
        store(scope) { assertNotEquals(Looper.getMainLooper().thread, Thread.currentThread()) }.retain(request)
        assertEquals(request, store(scope).read())
    }
    @Test fun deniedLeaseOrReadingNeverTouchesStorage() = runBlocking {
        val scope = scope(); val denied = store(scope) { error("Denied") }; var entered = false
        expect<IllegalStateException> { denied.exclusive { entered = true } }
        expect<IllegalStateException> { denied.read() }
        assertFalse(entered); assertFalse(file(scope).exists())
    }
    @Test fun cancellationCannotReleaseLeaseWhileNonCooperativeWriterStillRuns() = runBlocking {
        val scope = scope(); val first = store(scope); val second = store(scope)
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val followerEntered = AtomicInteger()
        val writer = launch(Dispatchers.IO) { first.exclusive {
            withContext(NonCancellable + Dispatchers.IO) {
                entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
            }
        } }
        assertTrue(entered.await(10, TimeUnit.SECONDS)); writer.cancel()
        val follower = launch(Dispatchers.IO) { second.exclusive { followerEntered.incrementAndGet() } }
        try { delay(100); assertEquals(0, followerEntered.get()) }
        finally { release.countDown() }
        writer.join(); follower.join()
        assertEquals(1, followerEntered.get())
    }
    private suspend inline fun <reified T : Throwable> expect(noinline action: suspend () -> Any?) {
        try { action(); fail("Expected ${T::class.java.simpleName}") }
        catch (error: Throwable) { if (error !is T) throw error }
    }
}
