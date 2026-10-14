package com.example.citizensecurity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.TrustedContact
import com.example.citizensecurity.domain.TrustedContactsConflictException
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Only randomly named, owned account fixtures; never opens the academic contact file. */
@RunWith(AndroidJUnit4::class)
class TrustedContactsStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val owners = arrayListOf<String>()
    private val contact get() = TrustedContact.create("Casa 🌼", "+525512345678")
    @After fun cleanOnlyOwnedFixtures() {
        owners.forEach { owner ->
            val file = file(owner)
            listOf(file, File(file.path + ".bak"), File(file.path + ".new")).forEach { it.delete() }
        }
    }

    @Test fun emptyReadingDoesNotCreateFilesOrOpenReportDatabases() = runBlocking {
        val owner = owner()
        val databases = context.databaseList().toSet()
        val result = store(owner).read()
        assertNull(result.revision)
        assertTrue(result.contacts.isEmpty())
        assertFalse(file(owner).exists())
        assertEquals(databases, context.databaseList().toSet())
    }

    @Test fun reopeningPreservesContactsAndDoesNotMixAccountOwners() = runBlocking {
        val a = owner(); val b = owner()
        val expected = store(a).replace(null, listOf(contact))
        val read = store(a).read()
        assertEquals(expected.revision, read.revision)
        assertEquals(expected.contacts, read.contacts)
        assertTrue(store(b).read().contacts.isEmpty())
        assertFalse(file(b).exists())
        assertNotEquals(TrustedContactsStore.fileName(null), TrustedContactsStore.fileName(a))
    }

    @Test fun aStaleRevisionCannotOverwriteAnyNewerContactBytes() = runBlocking {
        val owner = owner()
        val first = store(owner).replace(null, listOf(contact))
        val second = store(owner).replace(first.revision, first.contacts + TrustedContact.create("Persona", "5523456789"))
        val bytes = file(owner).readBytes()
        expectFailure<TrustedContactsConflictException> { store(owner).replace(first.revision, emptyList()) }
        assertArrayEquals(bytes, file(owner).readBytes())
        assertEquals(second.revision, store(owner).read().revision)
    }

    @Test fun concurrentInstancesAllowExactlyOneCasCommit() = runBlocking {
        val owner = owner()
        val accepted = AtomicInteger(); val rejected = AtomicInteger()
        (0..1).map { n -> async(Dispatchers.IO) {
            try {
                store(owner).replace(null, listOf(TrustedContact.create("Persona $n", "551234567$n")))
                accepted.incrementAndGet()
            } catch (_: TrustedContactsConflictException) { rejected.incrementAndGet() }
        } }.awaitAll()
        assertEquals(1, accepted.get()); assertEquals(1, rejected.get())
        assertEquals(1, store(owner).read().contacts.size)
    }

    @Test fun corruptOrUnknownJsonIsPreservedAndNeverOverwritten() = runBlocking {
        listOf("{\"schema\":2}", "{\"schema\":1", "{\"schema\":1,\"schema\":1}").forEach { document ->
            val owner = owner(); val bytes = document.toByteArray()
            file(owner).writeBytes(bytes)
            expectFailure<IOException> { store(owner).read() }
            expectFailure<IOException> { store(owner).replace(null, listOf(contact)) }
            assertArrayEquals(bytes, file(owner).readBytes())
        }
    }

    @Test fun malformedUtf8AndOversizedFilesArePreservedWithoutReplacement() = runBlocking {
        listOf(byteArrayOf(0xc3.toByte(), 0x28), ByteArray(32_769) { 0x20 }).forEach { bytes ->
            val owner = owner(); file(owner).writeBytes(bytes)
            expectFailure<IOException> { store(owner).read() }
            expectFailure<IOException> { store(owner).replace(null, listOf(contact)) }
            assertArrayEquals(bytes, file(owner).readBytes())
        }
    }

    @Test fun deniedAccessDoesNotReadOrCreateAnyContactFile() = runBlocking {
        val owner = owner()
        val denied = store(owner) { error("Revoked") }
        expectFailure<IllegalStateException> { denied.read() }
        expectFailure<IllegalStateException> { denied.replace(null, listOf(contact)) }
        assertFalse(file(owner).exists())
    }

    @Test fun revocationImmediatelyBeforeCommitRollsBackAndKeepsTheOldRevision() = runBlocking {
        val owner = owner()
        val first = store(owner).replace(null, listOf(contact))
        val bytes = file(owner).readBytes()
        var checks = 0
        val guarded = store(owner) { if (++checks == 3) error("Revoked before commit") }
        expectFailure<IllegalStateException> { guarded.replace(first.revision, emptyList()) }
        assertArrayEquals(bytes, file(owner).readBytes())
        assertEquals(first.revision, store(owner).read().revision)
    }

    @Test fun lostResponseAfterCommitIsRecoveredByFreshReadWithoutRepeatingTheMutation() = runBlocking {
        val owner = owner()
        var checks = 0
        val guarded = store(owner) { if (++checks == 4) error("Revoked after commit") }
        val next = listOf(contact)
        expectFailure<IllegalStateException> { guarded.replace(null, next) }
        val read = store(owner).read()
        assertEquals(next, read.contacts)
        assertNotNull(read.revision)
        expectFailure<TrustedContactsConflictException> { store(owner).replace(null, next) }
    }

    @Test fun atomicBackupRestoresTheLastCompleteReading() = runBlocking {
        val owner = owner()
        val first = store(owner).replace(null, listOf(contact))
        assertTrue(file(owner).renameTo(File(file(owner).path + ".bak")))
        file(owner).writeText("incomplete")
        assertEquals(first.contacts, store(owner).read().contacts)
        assertEquals(first.revision, store(owner).read().revision)
    }

    @Test fun removalWritesAnEmptyRevisionedListAndAnIdenticalSnapshotDoesNotRewrite() = runBlocking {
        val owner = owner()
        val first = store(owner).replace(null, listOf(contact))
        val empty = store(owner).replace(first.revision, emptyList())
        assertNotEquals(first.revision, empty.revision)
        assertTrue(store(owner).read().contacts.isEmpty())
        val bytes = file(owner).readBytes()
        assertEquals(empty.revision, store(owner).replace(empty.revision, emptyList()).revision)
        assertArrayEquals(bytes, file(owner).readBytes())
    }

    @Test fun ownerFilenameCannotTraverseDirectoriesOrUseAbbreviatedIds() {
        listOf("../contacts", "a/b", "a\\b", "1-1-1-1-1", "876A378A-D8CE-4A21-A091-5C662008DDAA")
            .forEach { bad -> expectFailure<IllegalArgumentException> { TrustedContactsStore.fileName(bad) } }
    }

    private fun owner() = UUID.randomUUID().toString().also { owners.add(it) }
    private fun file(owner: String) = File(context.filesDir, TrustedContactsStore.fileName(owner))
    private fun store(owner: String, guard: () -> Unit = {}) = TrustedContactsStore(context, owner, guard, Dispatchers.Unconfined)
    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try { block(); fail("Se esperaba ${T::class.java.simpleName}.") }
        catch (failure: Throwable) { if (failure !is T) throw failure }
    }
}
