package com.example.citizensecurity.data

import android.content.Context
import android.content.ContextWrapper
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import com.example.citizensecurity.domain.*
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Real AtomicFile fixtures in a private unique folder, never the user's preference file. */
class ReportAlertsStoreTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var directory: File
    private lateinit var context: Context
    private val owner = "f0000000-0000-0000-0000-000000009999"
    private val links = mutableListOf<File>()
    private val base get() = File(directory, ReportAlertsStore.fileName(owner))
    @Before fun ownFiles() {
        directory = File(app.filesDir.canonicalFile, "alerts_test_${UUID.randomUUID()}")
        assertTrue(directory.mkdir())
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
    }
    @After fun clearOnlyOwnFiles() {
        links.forEach { it.delete() }
        require(directory.name.startsWith("alerts_test_"))
        require(directory.canonicalFile.parentFile == app.filesDir.canonicalFile)
        directory.deleteRecursively()
    }

    @Test fun missingReadAndDisabledNoOpsDoNotCreateAPreferenceFile() = runBlocking {
        val store = store()
        assertEquals(ReportAlertsSnapshot(null), store.read())
        assertEquals(ReportAlertsSnapshot(null), store.setEnabled(false, null))
        assertEquals(ReportAlertsSnapshot(null), store.recordPresented(emptySet(), null))
        assertTrue(directory.list()!!.isEmpty())
    }
    @Test fun enablingRecordingDisablingAndReopeningPreserveMonotonicPresentationIds() = runBlocking {
        val enabled = store().setEnabled(true, null)
        val first = store().recordPresented(setOf(id(1)), enabled.revision)
        val second = store().recordPresented(setOf(id(1), id(2)), first.revision)
        val disabled = store().setEnabled(false, second.revision)
        assertTrue(first.enabled); assertTrue(second.enabled); assertFalse(disabled.enabled)
        assertEquals(setOf(id(1), id(2)), disabled.presented)
        assertEquals(4, listOf(enabled.revision, first.revision, second.revision, disabled.revision).toSet().size)
        assertEquals(disabled, store().read())
    }
    @Test fun samePathConcurrentCasAllowsOnlyOneWriterWithoutDroppingItsIds() = runBlocking {
        val initial = store().setEnabled(true, null)
        val results = listOf(id(1), id(2)).map { operation -> async(Dispatchers.Default) {
            try { store().recordPresented(setOf(operation), initial.revision) }
            catch (_: ReportAlertsConflictException) { null }
        } }.awaitAll()
        assertEquals(1, results.filterNotNull().size)
        assertEquals(results.filterNotNull().single(), store().read())
        assertEquals(1, store().read().presented.size)
    }
    @Test fun staleRevisionFailsEvenForANoOpAndPreservesExactBytes() = runBlocking {
        val initial = store().setEnabled(true, null)
        val changed = store().recordPresented(setOf(id(1)), initial.revision)
        val before = base.readBytes()
        expect<ReportAlertsConflictException> { store().setEnabled(true, initial.revision) }
        expect<ReportAlertsConflictException> { store().recordPresented(setOf(id(1)), initial.revision) }
        assertArrayEquals(before, base.readBytes()); assertEquals(changed, store().read())
    }
    @Test fun ownersAndAcademicUseSeparateFilesAndKeepTheirOwnPreference() = runBlocking {
        val another = id(8000)
        val first = store().setEnabled(true, null)
        val second = ReportAlertsStore(context, another).recordPresented(setOf(id(2)), null)
        val academic = ReportAlertsStore(context, null).recordPresented(setOf(id(3)), null)
        assertEquals(first, store().read())
        assertEquals(second, ReportAlertsStore(context, another).read())
        assertEquals(academic, ReportAlertsStore(context, null).read())
        assertEquals(3, directory.list()!!.size)
        assertEquals("report_alerts_academic.json", ReportAlertsStore.fileName(null))
        assertNotEquals(ReportAlertsStore.fileName(owner), ReportAlertsStore.fileName(another))
    }
    @Test fun invalidOwnersAndForeignOrTraversalOverridePathsAreRejected() {
        listOf("", "1-1-1-1-1", owner.uppercase(), "../$owner").forEach { malformed ->
            expectSync<IllegalArgumentException> { ReportAlertsStore(context, malformed) }
        }
        listOf("../report_alerts_x.json", "accounts.db", "report_alerts_x.json/other", "REPORT_ALERTS_x.json")
            .forEach { malformed -> expectSync<IllegalArgumentException> { ReportAlertsStore(context, owner, filenameOverride = malformed) } }
        assertTrue(directory.list()!!.isEmpty())
    }
    @Test fun explicitOwnedFixtureFilenameCanReopenWithoutUsingTheOwnersDefaultFile() = runBlocking {
        val filename = "report_alerts_test_${UUID.randomUUID()}_academic.json"
        val store = ReportAlertsStore(context, null, filenameOverride = filename)
        val saved = store.setEnabled(true, null)
        assertEquals(saved, ReportAlertsStore(context, null, filenameOverride = filename).read())
        assertEquals(listOf(filename), directory.list()!!.toList())
        assertFalse(base.exists())
    }
    @Test fun fullLedgerRejectsAnExtraIdWithoutTrimmingAndCanStillBeDisabled() = runBlocking {
        val initial = store().setEnabled(true, null)
        val ids = (1..4096).map(::id).toSet()
        val full = store().recordPresented(ids, initial.revision)
        val before = base.readBytes()
        expect<ReportAlertsCapacityException> { store().recordPresented(setOf(id(5000)), full.revision) }
        assertArrayEquals(before, base.readBytes())
        val disabled = store().setEnabled(false, full.revision)
        assertFalse(disabled.enabled); assertEquals(ids, disabled.presented)
        assertEquals(disabled, store().recordPresented(ids, disabled.revision))
    }
    @Test fun corruptedMalformedUtf8AndOversizedFilesNeverBecomeWritableDefaults() = runBlocking {
        listOf("{\"enabled\":true}".toByteArray(), byteArrayOf(0xc3.toByte(), 0x28),
            ByteArray(ReportAlertsCodec.MAX_BYTES + 1) { ' '.code.toByte() }).forEach { damaged ->
            base.writeBytes(damaged)
            expect<IOException> { store().read() }
            expect<IOException> { store().setEnabled(true, null) }
            expect<IOException> { store().recordPresented(setOf(id(1)), null) }
            assertArrayEquals(damaged, base.readBytes())
        }
    }
    @Test fun unconfirmedNewFileIsPreservedAndNeverReadAsAbsent() = runBlocking {
        val pending = File(base.path + ".new").apply { writeText("unconfirmed") }
        expect<IOException> { store().read() }
        expect<IOException> { store().setEnabled(true, null) }
        expect<IOException> { store().recordPresented(setOf(id(1)), null) }
        assertEquals("unconfirmed", pending.readText()); assertFalse(base.exists())
    }
    @Test fun baseAndNewRemainUntouchedInsteadOfDiscardingAnUnconfirmedWriteOnRead() = runBlocking {
        val enabled = store().setEnabled(true, null)
        val current = store().recordPresented(setOf(id(1), id(2)), enabled.revision)
        val before = base.readBytes()
        val pending = File(base.path + ".new").apply { writeText("unconfirmed") }
        expect<IOException> { store().read() }
        expect<IOException> { store().setEnabled(false, current.revision) }
        expect<IOException> { store().recordPresented(setOf(id(3)), current.revision) }
        assertArrayEquals(before, base.readBytes()); assertEquals("unconfirmed", pending.readText())
    }
    @Test fun staleBackupCannotRevertEnabledPreferenceOrForgetAlreadyPresentedOperations() = runBlocking {
        val old = store().setEnabled(true, null)
        val oldBytes = base.readBytes()
        val presented = store().recordPresented(setOf(id(1), id(2)), old.revision)
        val disabled = store().setEnabled(false, presented.revision)
        val currentBytes = base.readBytes()
        val backup = File(base.path + ".bak").apply { writeBytes(oldBytes) }
        expect<IOException> { store().read() }
        expect<IOException> { store().setEnabled(true, disabled.revision) }
        expect<IOException> { store().recordPresented(setOf(id(3)), disabled.revision) }
        assertArrayEquals(currentBytes, base.readBytes()); assertArrayEquals(oldBytes, backup.readBytes())
    }
    @Test fun backupAloneIsNeitherRestoredNorTreatedAsAWritableMissingPreference() = runBlocking {
        val saved = store().recordPresented(setOf(id(1)), null)
        val before = base.readBytes()
        val backup = File(base.path + ".bak")
        assertTrue(base.renameTo(backup))
        expect<IOException> { store().read() }
        expect<IOException> { store().setEnabled(true, saved.revision) }
        expect<IOException> { store().setEnabled(false, null) }
        expect<IOException> { store().recordPresented(setOf(id(2)), null) }
        assertFalse(base.exists()); assertArrayEquals(before, backup.readBytes())
        assertEquals(listOf(backup.name), directory.list()!!.toList())
    }
    @Test fun directoriesAtAnyPreferencePathArePreservedAndNeverTreatedAsMissing() = runBlocking {
        listOf(base, File(base.path + ".bak"), File(base.path + ".new")).forEach { folder ->
            assertTrue(folder.mkdir())
            val sentinel = File(folder, "sentinel").apply { writeText("Keep directory data") }
            expect<IOException> { store().read() }
            expect<IOException> { store().setEnabled(true, null) }
            expect<IOException> { store().recordPresented(setOf(id(1)), null) }
            assertEquals("Keep directory data", sentinel.readText())
            assertTrue(sentinel.delete()); assertTrue(folder.delete())
        }
    }
    @Test fun symlinkPrivateDirectoryIsRejectedWithoutTouchingItsTarget() = runBlocking {
        val target = File(directory, "direct").apply { assertTrue(mkdir()) }
        val sentinel = File(target, ReportAlertsStore.fileName(owner)).apply { writeText("Keep target") }
        val link = File(directory, "linked")
        Os.symlink(target.path, link.path); links += link
        val redirected = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = link
        }
        val store = ReportAlertsStore(redirected, owner)
        expect<IOException> { store.read() }
        expect<IOException> { store.setEnabled(true, null) }
        assertEquals("Keep target", sentinel.readText())
        assertTrue(java.nio.file.Files.isSymbolicLink(link.toPath()))
    }
    @Test fun fileInPlaceOfPrivateDirectoryIsRejectedWithoutCreatingOrReplacingAnything() = runBlocking {
        val path = File(directory, "not_a_folder").apply { writeText("Preserve private-path file") }
        val redirected = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = path
        }
        val store = ReportAlertsStore(redirected, owner)
        expect<IOException> { store.read() }
        expect<IOException> { store.setEnabled(true, null) }
        assertEquals("Preserve private-path file", path.readText())
    }
    @Test fun danglingLinksAtEveryPreferencePathCannotBeReplacedByAnEmptyPreference() = runBlocking {
        val absent = File(directory, "absent_target")
        listOf(base, File(base.path + ".bak"), File(base.path + ".new")).forEach { link ->
            Os.symlink(absent.path, link.path); links += link
            expect<IOException> { store().read() }
            expect<IOException> { store().setEnabled(true, null) }
            assertTrue(java.nio.file.Files.isSymbolicLink(link.toPath())); assertFalse(absent.exists())
            assertTrue(link.delete()); links.remove(link)
        }
    }
    @Test fun foreignBackupAppearingDuringWriteIsNotDeletedOrRestoredByRollback() = runBlocking {
        val previous = store().recordPresented(setOf(id(1)), null)
        val before = base.readBytes()
        val backup = File(base.path + ".bak")
        var checks = 0
        val guarded = store { if (++checks == 5) backup.writeText("Foreign backup: preserve") }
        expect<IOException> { guarded.setEnabled(true, previous.revision) }
        assertArrayEquals(before, base.readBytes()); assertEquals("Foreign backup: preserve", backup.readText())
        assertTrue(File(base.path + ".new").isFile)
        expect<IOException> { store().read() }
    }
    @Test fun replacedStagingLinkIsNotFollowedOrDeletedByRollback() = runBlocking {
        val previous = store().recordPresented(setOf(id(1)), null)
        val before = base.readBytes()
        val sentinel = File(directory, "sentinel.json").apply { writeText("Do not follow or delete") }
        val staging = File(base.path + ".new")
        var checks = 0
        val guarded = store {
            if (++checks == 5) {
                assertTrue(staging.delete())
                Os.symlink(sentinel.path, staging.path); links += staging
            }
        }
        expect<IOException> { guarded.setEnabled(true, previous.revision) }
        assertArrayEquals(before, base.readBytes()); assertEquals("Do not follow or delete", sentinel.readText())
        assertTrue(java.nio.file.Files.isSymbolicLink(staging.toPath()))
    }
    @Test fun sidecarAppearingInTheFinalAccessCheckIsPreservedAndBlocksASettledReceipt() = runBlocking {
        val previous = store().recordPresented(setOf(id(1)), null)
        val backup = File(base.path + ".bak")
        var checks = 0
        val guarded = store { if (++checks == 7) backup.writeText("Uncertain after commit") }
        expect<IOException> { guarded.setEnabled(true, previous.revision) }
        assertEquals(7, checks)
        val committed = ReportAlertsCodec.decode(base.readText())
        assertTrue(committed.enabled); assertEquals(setOf(id(1)), committed.presented)
        assertEquals("Uncertain after commit", backup.readText())
        expect<IOException> { store().read() }
    }
    @Test fun guardDeniedBeforeAccessCreatesNoFileAndBeforeCommitKeepsThePriorOne() = runBlocking {
        expect<IllegalStateException> { store { error("Revoked") }.read() }
        assertTrue(directory.list()!!.isEmpty())
        val previous = store().setEnabled(true, null)
        val before = base.readBytes()
        var checks = 0
        expect<IllegalStateException> { store { if (++checks == 5) error("Revoked before commit") }
            .setEnabled(false, previous.revision) }
        assertEquals(5, checks); assertArrayEquals(before, base.readBytes())
        assertEquals(previous, store().read()); assertFalse(File(base.path + ".new").exists())
    }
    @Test fun guardFailureAfterCommitRequiresFreshReadingRatherThanOverwritingAsMissing() = runBlocking {
        var checks = 0
        expect<IllegalStateException> { store { if (++checks == 7) error("Revoked after commit") }.setEnabled(true, null) }
        assertEquals(7, checks)
        val recovered = store().read()
        assertTrue(recovered.enabled); assertNotNull(recovered.revision)
        expect<ReportAlertsConflictException> { store().setEnabled(false, null) }
        assertEquals(recovered, store().read())
    }
    @Test fun cancellationBeforeCommitRetiresOnlyTheNewFileAndPreservesThePreviousSnapshot() = runBlocking {
        val previous = store().setEnabled(true, null)
        val before = base.readBytes()
        var checks = 0
        lateinit var operation: Deferred<ReportAlertsSnapshot>
        val guarded = store { if (++checks == 5) operation.cancel() }
        operation = async(start = CoroutineStart.LAZY) { guarded.setEnabled(false, previous.revision) }
        operation.start()
        expect<CancellationException> { operation.await() }
        assertArrayEquals(before, base.readBytes()); assertEquals(previous, store().read())
        assertFalse(File(base.path + ".new").exists())
    }
    @Test fun cancellationAfterCommitLeavesAnUncertainReceiptRecoverableByRead() = runBlocking {
        val previous = store().setEnabled(true, null)
        var checks = 0
        lateinit var operation: Deferred<ReportAlertsSnapshot>
        val guarded = store { if (++checks == 7) operation.cancel() }
        operation = async(start = CoroutineStart.LAZY) { guarded.setEnabled(false, previous.revision) }
        operation.start()
        expect<CancellationException> { operation.await() }
        val recovered = store().read()
        assertFalse(recovered.enabled); assertNotEquals(previous.revision, recovered.revision)
        expect<ReportAlertsConflictException> { store().setEnabled(true, previous.revision) }
    }
    @Test fun symlinkBaseBackupAndNewFilesCannotRedirectReadsOrWrites() = runBlocking {
        val sentinel = File(directory, "outside.json").apply { writeText("Preserve me") }
        listOf(base, File(base.path + ".bak"), File(base.path + ".new")).forEach { link ->
            Os.symlink(sentinel.absolutePath, link.absolutePath); links += link
            expect<IOException> { store().read() }
            expect<IOException> { store().setEnabled(true, null) }
            assertEquals("Preserve me", sentinel.readText())
            assertTrue(link.delete()); links.remove(link)
        }
    }
    @Test fun pendingInputSetIsCapturedBeforeItCanChangeAcrossTheIoDispatcher() = runBlocking {
        val initial = store().setEnabled(true, null)
        val dispatcher = QueuedDispatcher()
        val ids = linkedSetOf(id(1))
        val store = ReportAlertsStore(context, owner, ioDispatcher = dispatcher)
        val operation = async(Dispatchers.Default) { store.recordPresented(ids, initial.revision) }
        val task = dispatcher.awaitTask()
        ids += id(2)
        task.run()
        assertEquals(setOf(id(1)), operation.await().presented)
        assertEquals(setOf(id(1)), store().read().presented)
    }

    private fun store(checkAccess: () -> Unit = {}) = ReportAlertsStore(context, owner, checkAccess)
    private fun id(number: Int) = "f0000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
    private suspend inline fun <reified T : Throwable> expect(noinline action: suspend () -> Any?) {
        try { action(); fail("Expected ${T::class.java.simpleName}") } catch (failure: Throwable) { if (failure !is T) throw failure }
    }
    private inline fun <reified T : Throwable> expectSync(action: () -> Unit) {
        try { action(); fail("Expected ${T::class.java.simpleName}") } catch (failure: Throwable) { if (failure !is T) throw failure }
    }
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = LinkedBlockingQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun awaitTask(): Runnable = checkNotNull(tasks.poll(10, TimeUnit.SECONDS))
    }
}
