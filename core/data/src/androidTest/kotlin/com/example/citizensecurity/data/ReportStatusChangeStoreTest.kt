package com.example.citizensecurity.data

import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
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
    private val extraOwnedFiles = mutableListOf<File>()
    private fun scope(owner: String? = UUID.randomUUID().toString(), actor: String = UUID.randomUUID().toString()) =
        ReportStatusChangeScope(owner, actor).also(scopes::add)
    private fun request(scope: ReportStatusChangeScope) = ReportStatusChangeRequest(scope,
        ReportStatusChangeCommand(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1,
            ReportStatus.REPORTED, ReportStatus.IN_REVIEW, "Revisión local del café 🌼"), Instant.parse("2026-10-14T01:02:03.123456789Z"))
    private fun store(scope: ReportStatusChangeScope, guard: () -> Unit = {}) = ReportStatusChangeStore(context, scope, guard)
    private fun file(scope: ReportStatusChangeScope) = File(context.filesDir, ReportStatusChangeStore.fileName(scope))
    @After fun cleanOnlyOwnedFiles() {
        extraOwnedFiles.asReversed().forEach { it.delete() }
        scopes.forEach { scope ->
            val base = file(scope)
            listOf(base, File(base.path + ".bak"), File(base.path + ".new")).forEach { it.delete() }
        }
    }

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
    @Test fun backupAndPartialBaseAreUncertainAndNeitherIsRestoredOrRemoved() = runBlocking {
        val scope = scope(); val expected = request(scope); store(scope).retain(expected)
        val backup = File(file(scope).path + ".bak")
        assertTrue(file(scope).renameTo(backup))
        file(scope).writeText("partial")
        val backupBytes = backup.readBytes()
        assertBlockedWithoutEnteringWriter(scope, expected)
        assertEquals("partial", file(scope).readText())
        assertArrayEquals(backupBytes, backup.readBytes())
    }
    @Test fun backupAloneIsPendingAndCannotBeOverwrittenByAnotherOperation() = runBlocking {
        val scope = scope(); val expected = request(scope); store(scope).retain(expected)
        val backup = File(file(scope).path + ".bak")
        assertTrue(file(scope).renameTo(backup))
        val bytes = backup.readBytes()
        assertBlockedWithoutEnteringWriter(scope, expected)
        assertFalse(file(scope).exists())
        assertArrayEquals(bytes, backup.readBytes())
    }

    @Test fun stagingAloneIsNeverTreatedAsAnEmptyJournalEvenWhenItContainsACompleteRequest() = runBlocking {
        listOf(false, true).forEach { complete ->
            val scope = scope(); val pending = request(scope)
            val bytes = if (complete) ReportStatusChangeCodec.encode(pending) else "partial".toByteArray()
            val staging = File(file(scope).path + ".new").apply { writeBytes(bytes) }
            assertBlockedWithoutEnteringWriter(scope, pending)
            assertFalse(file(scope).exists())
            assertArrayEquals(bytes, staging.readBytes())
        }
    }

    @Test fun completeBaseWithStagingIsNotReadClearedOrReplaced() = runBlocking {
        val scope = scope(); val pending = request(scope); store(scope).retain(pending)
        val baseBytes = file(scope).readBytes()
        val stagingBytes = "unconfirmed write".toByteArray()
        val staging = File(file(scope).path + ".new").apply { writeBytes(stagingBytes) }
        assertBlockedWithoutEnteringWriter(scope, pending)
        assertArrayEquals(baseBytes, file(scope).readBytes())
        assertArrayEquals(stagingBytes, staging.readBytes())
    }

    @Test fun bothSidecarsArePreservedAlongWithTheExactBase() = runBlocking {
        val scope = scope(); val pending = request(scope); store(scope).retain(pending)
        val baseBytes = file(scope).readBytes()
        val backup = File(file(scope).path + ".bak").apply { writeText("old unknown request") }
        val staging = File(file(scope).path + ".new").apply { writeText("new unknown request") }
        assertBlockedWithoutEnteringWriter(scope, pending)
        assertArrayEquals(baseBytes, file(scope).readBytes())
        assertEquals("old unknown request", backup.readText())
        assertEquals("new unknown request", staging.readText())
    }

    @Test fun symlinkAtBaseOrEitherSidecarNeverFollowsOrModifiesItsTarget() = runBlocking {
        listOf("", ".new", ".bak").forEach { suffix ->
            val scope = scope(); val pending = request(scope)
            val target = File.createTempFile("status_target_", ".txt", context.cacheDir).also(extraOwnedFiles::add)
            val bytes = "outside the journal".toByteArray()
            target.writeBytes(bytes)
            val link = File(file(scope).path + suffix)
            Files.createSymbolicLink(link.toPath(), target.toPath())
            assertBlockedWithoutEnteringWriter(scope, pending)
            assertTrue(Files.isSymbolicLink(link.toPath()))
            assertArrayEquals(bytes, target.readBytes())
        }
    }

    @Test fun danglingSymlinksAreUncertainRatherThanAbsent() = runBlocking {
        listOf("", ".new", ".bak").forEach { suffix ->
            val scope = scope(); val pending = request(scope)
            val target = File(context.cacheDir, "missing_status_${UUID.randomUUID()}")
            val link = File(file(scope).path + suffix)
            Files.createSymbolicLink(link.toPath(), target.toPath())
            assertBlockedWithoutEnteringWriter(scope, pending)
            assertTrue(Files.isSymbolicLink(link.toPath()))
            assertFalse(target.exists())
        }
    }

    @Test fun directoryAtBaseOrSidecarIsNeverRecursivelyDeletedOrTreatedAsMissing() = runBlocking {
        listOf("", ".new", ".bak").forEach { suffix ->
            val scope = scope(); val pending = request(scope)
            val directory = File(file(scope).path + suffix).apply { check(mkdir()) }
            val child = File(directory, "owned_fixture.txt").apply { writeText("keep this file") }
                .also(extraOwnedFiles::add)
            assertBlockedWithoutEnteringWriter(scope, pending)
            assertTrue(directory.isDirectory)
            assertEquals("keep this file", child.readText())
        }
    }

    @Test fun symlinkAsPrivateDirectoryIsRejectedWithoutTouchingTheDestination() = runBlocking {
        val scope = scope(); val pending = request(scope)
        val target = File(context.cacheDir, "status_directory_${UUID.randomUUID()}").apply { check(mkdir()) }
            .also(extraOwnedFiles::add)
        val sentinel = File(target, "sentinel.txt").apply { writeText("keep private directory") }
            .also(extraOwnedFiles::add)
        val link = File(context.cacheDir, "status_directory_link_${UUID.randomUUID()}").also(extraOwnedFiles::add)
        Files.createSymbolicLink(link.toPath(), target.toPath())
        val wrapped = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = link
        }
        val bound = ReportStatusChangeStore(wrapped, scope, {})
        expect<IOException> { bound.read() }
        expect<IOException> { bound.retain(pending) }
        expect<IOException> { bound.clear(pending) }
        var entered = false
        expect<IOException> { bound.exclusive { entered = true } }
        assertFalse(entered)
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertEquals("keep private directory", sentinel.readText())
        assertFalse(File(target, ReportStatusChangeStore.fileName(scope)).exists())
    }

    @Test fun sidecarAppearingAtClearGuardIsPreservedAndPreventsDeletingTheBase() = runBlocking {
        val scope = scope(); val pending = request(scope); store(scope).retain(pending)
        val bytes = file(scope).readBytes()
        val staging = File(file(scope).path + ".new")
        var checks = 0
        val bound = store(scope) { if (++checks == 2) staging.writeText("unknown write during guard") }
        expect<IOException> { bound.clear(pending) }
        assertArrayEquals(bytes, file(scope).readBytes())
        assertEquals("unknown write during guard", staging.readText())
    }

    @Test fun sidecarAppearingAtReadGuardIsPreservedAndDoesNotReturnAStaleBody() = runBlocking {
        val scope = scope(); val pending = request(scope); store(scope).retain(pending)
        val bytes = file(scope).readBytes()
        val backup = File(file(scope).path + ".bak")
        var checks = 0
        val bound = store(scope) { if (++checks == 2) backup.writeText("unknown backup during guard") }
        expect<IOException> { bound.read() }
        assertArrayEquals(bytes, file(scope).readBytes())
        assertEquals("unknown backup during guard", backup.readText())
    }

    @Test fun replacedAtomicOutputLinkAtCommitGuardIsPreservedRatherThanCleanedAsOurWrite() = runBlocking {
        val scope = scope(); val pending = request(scope)
        val target = File.createTempFile("status_foreign_", ".txt", context.cacheDir).also(extraOwnedFiles::add)
            .apply { writeText("keep foreign contents") }
        val staging = File(file(scope).path + ".new")
        var replaced: File? = null
        var checks = 0
        val guarded = store(scope) {
            if (++checks == 3) {
                // Older AtomicFile writes the base directly; newer versions write .new.
                val currentOutput = if (staging.exists()) staging else file(scope)
                check(currentOutput.delete())
                Files.createSymbolicLink(currentOutput.toPath(), target.toPath())
                replaced = currentOutput
            }
        }
        expect<IOException> { guarded.retain(pending) }
        assertTrue(Files.isSymbolicLink(checkNotNull(replaced).toPath()))
        assertEquals("keep foreign contents", target.readText())
        expect<IOException> { store(scope).read() }
    }

    @Test fun malformedJournalCannotEnterAnExclusiveBackendBlock() = runBlocking {
        val scope = scope(); val bytes = "not a verified journal".toByteArray(); file(scope).writeBytes(bytes)
        var writerEntered = false
        expect<IOException> { store(scope).exclusive { writerEntered = true } }
        assertFalse(writerEntered)
        assertArrayEquals(bytes, file(scope).readBytes())
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
    private suspend fun assertBlockedWithoutEnteringWriter(scope: ReportStatusChangeScope, request: ReportStatusChangeRequest) {
        expect<IOException> { store(scope).read() }
        expect<IOException> { store(scope).retain(request) }
        expect<IOException> { store(scope).clear(request) }
        var entered = false
        expect<IOException> { store(scope).exclusive { entered = true } }
        assertFalse(entered)
    }

    private suspend inline fun <reified T : Throwable> expect(noinline action: suspend () -> Any?) {
        try { action(); fail("Expected ${T::class.java.simpleName}") }
        catch (error: Throwable) { if (error !is T) throw error }
    }
}
