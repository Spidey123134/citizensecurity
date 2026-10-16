package com.example.citizensecurity.data

import android.content.Context
import android.content.ContextWrapper
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import com.example.citizensecurity.domain.LocalAccount
import com.example.citizensecurity.domain.LocalAdministratorAssignment
import com.example.citizensecurity.domain.LocalAdministratorConflictException
import com.example.citizensecurity.domain.ReportAdministrationPermission
import com.example.citizensecurity.domain.ReportAdministrationPermissionDeniedException
import com.example.citizensecurity.domain.ReportStatusChangeScope
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Assignment and revocation fixtures are isolated from the installed app's real authority. */
class LocalAdministratorStoreTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var directory: File
    private lateinit var context: Context
    private val account = LocalAccount(id(1), "admin_local")
    private val operation = id(2)
    private val grantedAt = Instant.parse("2026-10-15T12:00:00.123456789Z")
    private val base get() = File(directory, LocalAdministratorStore.FILE_NAME)
    private val links = mutableListOf<File>()

    @Before fun ownFiles() {
        directory = File(app.filesDir.canonicalFile, "administrator_test_${UUID.randomUUID()}")
        assertTrue(directory.mkdir())
        context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
    }

    @After fun clearOnlyOwnFiles() {
        links.forEach { it.delete() }
        require(directory.name.startsWith("administrator_test_"))
        require(directory.canonicalFile.parentFile == app.filesDir.canonicalFile)
        directory.deleteRecursively()
    }

    @Test fun absentReadAndPolicyCreateNothingAndGrantNothing() {
        assertNull(store().read())
        ReportAdministrationPermission.entries.forEach { permission ->
            expect<ReportAdministrationPermissionDeniedException> { store().policy().check(scope(), permission) }
        }
        assertTrue(directory.list()!!.isEmpty())
    }

    @Test fun assignReopenAndExactRetryPreserveTheReceiptAndBytes() {
        val saved = assign()
        val before = base.readBytes()
        assertEquals(LocalAdministratorAssignment(account.id, account.username, operation, grantedAt), saved)
        assertEquals(saved, store().read())
        assertEquals(saved, assign())
        assertArrayEquals(before, base.readBytes())
        assertEquals(listOf(LocalAdministratorStore.FILE_NAME), directory.list()!!.toList())
    }

    @Test fun existingGrantCannotBeReplacedByAccountOperationOrTimestampChanges() {
        val saved = assign()
        val before = base.readBytes()
        expect<LocalAdministratorConflictException> { assign(account.copy(id = id(3))) }
        expect<LocalAdministratorConflictException> { assign(operationId = id(3)) }
        expect<LocalAdministratorConflictException> { assign(time = grantedAt.plusNanos(1)) }
        assertArrayEquals(before, base.readBytes()); assertEquals(saved, store().read())
    }

    @Test fun anotherUsernameInvalidOperationAndInvalidTimeNeverCreateAuthority() {
        expect<IllegalArgumentException> { assign(account.copy(username = "another_user")) }
        expect<IllegalArgumentException> { assign(operationId = "1-1-1-1-1") }
        expect<IllegalArgumentException> { assign(time = Instant.EPOCH.minusNanos(1)) }
        assertTrue(directory.list()!!.isEmpty())
    }

    @Test fun aRetainedPolicyRereadsAndNeverTransfersAuthorityToAcademicOrAnotherIdentity() {
        val policy = store().policy()
        expect<ReportAdministrationPermissionDeniedException> { policy.check(scope(), ReportAdministrationPermission.READ_REPORTS) }
        assign()
        ReportAdministrationPermission.entries.forEach { permission ->
            policy.check(scope(), permission)
            listOf(scope().copy(ownerAccountId = null), scope().copy(ownerAccountId = id(3)),
                scope().copy(actorAccountId = id(3)), ReportStatusChangeScope(id(3), id(3)))
                .forEach { other -> expect<ReportAdministrationPermissionDeniedException> { policy.check(other, permission) } }
        }
        val before = base.readBytes()
        base.writeText("corrupt")
        expect<IOException> { policy.check(scope(), ReportAdministrationPermission.READ_REPORTS) }
        assertEquals("corrupt", base.readText())
        base.writeBytes(before)
        policy.check(scope(), ReportAdministrationPermission.CHANGE_STATUS)
    }

    @Test fun concurrentDifferentFirstOperationsHaveOneWinnerAndKeepItsExactReceipt() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val results = listOf(id(2), id(3)).map { op -> pool.submit<LocalAdministratorAssignment?> {
                ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS))
                try { assign(operationId = op) } catch (_: LocalAdministratorConflictException) { null }
            } }
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown()
            val successful = results.mapNotNull { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, successful.size)
            assertEquals(successful.single(), store().read())
            assertEquals(successful.single(), LocalAdministratorCodec.decode(base.readText()))
        } finally { pool.shutdownNow() }
    }

    @Test fun concurrentExactRetriesBothObserveOneUnchangedReceipt() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val results = (1..2).map { pool.submit<LocalAdministratorAssignment> {
                assertTrue(start.await(5, TimeUnit.SECONDS)); assign()
            } }
            start.countDown()
            val saved = results.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(saved[0], saved[1]); assertEquals(saved[0], store().read())
            assertEquals(listOf(LocalAdministratorStore.FILE_NAME), directory.list()!!.toList())
        } finally { pool.shutdownNow() }
    }

    @Test fun corruptMalformedUtf8AndOversizedFilesArePreservedAndNeverReplaceable() {
        listOf("{\"schema\":1}".toByteArray(), byteArrayOf(0xc3.toByte(), 0x28),
            ByteArray(LocalAdministratorCodec.MAX_BYTES + 1) { ' '.code.toByte() }).forEach { damaged ->
            base.writeBytes(damaged)
            expect<IOException> { store().read() }
            expect<IOException> { assign() }
            expect<IOException> { store().policy().check(scope(), ReportAdministrationPermission.READ_REPORTS) }
            assertArrayEquals(damaged, base.readBytes())
        }
    }

    @Test fun incompleteNewAndBackupAreNeverRepairedOrDeletedByReadOrAssignment() {
        listOf(".new", ".bak").forEach { suffix ->
            val pending = File(base.path + suffix).apply { writeText("unconfirmed") }
            expect<IOException> { store().read() }
            expect<IOException> { assign() }
            assertEquals("unconfirmed", pending.readText()); assertFalse(base.exists())
            assertTrue(pending.delete())
        }
    }

    @Test fun pendingSidecarsBesideAValidGrantArePreservedAndDoNotAllowAnExactRetry() {
        assign()
        val before = base.readBytes()
        listOf(".new", ".bak").forEach { suffix ->
            val pending = File(base.path + suffix).apply { writeText("preserve pending") }
            expect<IOException> { store().read() }
            expect<IOException> { assign() }
            assertEquals("preserve pending", pending.readText()); assertArrayEquals(before, base.readBytes())
            assertTrue(pending.delete())
        }
    }

    @Test fun revokedGuardBeforeAccessOrBeforeCommitCreatesNoGrant() {
        expect<IllegalStateException> { assign(guard = { error("Revoked") }) }
        assertTrue(directory.list()!!.isEmpty())
        expect<IllegalStateException> { assign(guard = {
            if (File(base.path + ".new").exists()) error("Revoked before commit")
        }) }
        assertNull(store().read()); assertTrue(directory.list()!!.isEmpty())
    }

    @Test fun revokedGuardAfterCommitRequiresRereadingTheExactReceiptAndCannotReplaceIt() {
        expect<IllegalStateException> { assign(guard = { if (base.exists()) error("Revoked after commit") }) }
        val recovered = requireNotNull(store().read())
        assertEquals(operation, recovered.operationId)
        assertEquals(recovered, assign())
        expect<LocalAdministratorConflictException> { assign(operationId = id(3)) }
    }

    @Test fun fixtureOverrideCannotTargetAnotherFileOrEscapeItsDirectory() {
        listOf("../local_administrator.json", "accounts.db", LocalAdministratorStore.FILE_NAME,
            "local_administrator_test_1-1-1-1-1.json", "local_administrator_test_${UUID.randomUUID().toString().uppercase()}.json")
            .forEach { name -> expect<IllegalArgumentException> { LocalAdministratorStore(context, name) } }
        val name = "local_administrator_test_${UUID.randomUUID()}.json"
        val fixture = LocalAdministratorStore(context, name)
        assertNull(fixture.read())
        val saved = fixture.assignFirst(account, operation, grantedAt) { }
        assertEquals(saved, LocalAdministratorStore(context, name).read())
        assertFalse(base.exists()); assertEquals(listOf(name), directory.list()!!.toList())
    }

    @Test fun symlinkBaseNewAndBackupCannotRedirectReadingOrAssigningAuthority() {
        val sentinel = File(directory, "sentinel.json").apply { writeText("Preserve me") }
        listOf(base, File(base.path + ".new"), File(base.path + ".bak")).forEach { link ->
            Os.symlink(sentinel.absolutePath, link.absolutePath); links += link
            expect<IOException> { store().read() }
            expect<IOException> { assign() }
            assertEquals("Preserve me", sentinel.readText()); assertTrue(link.delete())
        }
    }

    @Test fun aDirectoryAtAnyAtomicPathIsNotReadOrOverwritten() {
        listOf(base, File(base.path + ".new"), File(base.path + ".bak")).forEach { path ->
            assertTrue(path.mkdir())
            expect<IOException> { store().read() }; expect<IOException> { assign() }
            assertTrue(path.isDirectory); assertTrue(path.delete())
        }
    }

    private fun store() = LocalAdministratorStore(context)
    private fun scope() = ReportStatusChangeScope(account.id, account.id)
    private fun assign(selected: LocalAccount = account, operationId: String = operation,
        time: Instant = grantedAt, guard: () -> Unit = {}) = store().assignFirst(selected, operationId, time, guard)
    private fun id(number: Int) = "d0000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
    private inline fun <reified T : Throwable> expect(action: () -> Unit) {
        try { action(); fail("Expected ${T::class.java.simpleName}") }
        catch (failure: Throwable) { if (failure !is T) throw failure }
    }
}
