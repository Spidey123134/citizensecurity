package com.example.citizensecurity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportSubmissionRequest
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

/** Solo diarios y bases temporales propiedad de cada prueba; nunca los archivos productivos. */
@RunWith(AndroidJUnit4::class)
class ReportSubmissionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = mutableListOf<File>()
    private val databaseNames = mutableListOf<String>()
    private val now = Instant.parse("2026-10-09T03:00:00.123456789Z")
    private val requestId = "1e345678-1234-4567-89ab-0123456789ab"

    @After fun cleanOnlyOwnedFixtures() {
        files.forEach { file ->
            listOf(file, File(file.path + ".bak"), File(file.path + ".new")).forEach { it.delete() }
        }
        databaseNames.forEach { context.deleteDatabase(it) }
    }

    @Test fun aNewStoreInstanceRestoresTheSameIdentityFieldsAndNanoseconds(): Unit = runBlocking {
        val file = temporaryFile()
        val first = store(file)
        assertNull(first.read())
        val request = request().copy(draft = request().draft.copy(
            description = "Hay un árbol caído 🌳.\nEstá frente al mercado; no bloquea toda la calle.",
        ))
        first.write(request)
        assertTrue(file.exists())
        assertEquals(request, store(file).read())
        val text = file.readText()
        assertTrue(text.contains(requestId))
        assertFalse(text.contains("accuracy"))
        assertFalse(text.contains("elapsedRealtime"))
        assertFalse(text.contains("permission"))
        assertFalse(text.contains("mock"))
    }

    @Test fun repeatedWriteOfTheSameRequestLeavesTheDurableSnapshotUnchanged(): Unit = runBlocking {
        val file = temporaryFile()
        val request = request()
        store(file).write(request)
        val bytes = file.readBytes()
        val modified = file.lastModified()
        store(file).write(request)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(modified, file.lastModified())
        assertEquals(request, store(file).read())
    }

    @Test fun negativeZeroCoordinatesCanRetryTheSameIdentityAfterReopening(): Unit = runBlocking {
        val file = temporaryFile()
        val request = request().copy(draft = request().draft.copy(
            location = ReportLocation("Frente al mercado", -0.0, -0.0),
        ))
        store(file).write(request)
        val restored = store(file).read()!!
        assertEquals(0.0, restored.draft.location.latitude!!, 0.0)
        assertEquals(0.0, restored.draft.location.longitude!!, 0.0)
        store(file).write(request)
        assertEquals(restored, store(file).read())
    }

    @Test fun anotherIdentityOrChangedContentCannotReplaceThePendingRequest(): Unit = runBlocking {
        val file = temporaryFile()
        val pending = request()
        store(file).write(pending)
        val bytes = file.readBytes()
        val changed = listOf(
            pending.copy(requestId = UUID.randomUUID().toString()),
            pending.copy(draft = pending.draft.copy(priority = Priority.HIGH)),
            pending.copy(requestedAt = pending.requestedAt.plusNanos(1)),
        )
        changed.forEach { attempted ->
            expectFailure<IllegalStateException> { store(file).write(attempted) }
            assertArrayEquals(bytes, file.readBytes())
            assertEquals(pending, store(file).read())
        }
    }

    @Test fun onlyTheOwnedIdentityCanClearTheJournal(): Unit = runBlocking {
        val file = temporaryFile()
        val journal = store(file)
        journal.write(request())
        expectFailure<IllegalStateException> { journal.clear(UUID.randomUUID().toString()) }
        assertEquals(request(), journal.read())
        journal.clear(requestId)
        assertNull(store(file).read())
        assertFalse(file.exists())
        // Ya retirado, repetir el cierre no toca ningún otro archivo.
        journal.clear(requestId)
        assertNull(journal.read())
    }

    @Test fun corruptJournalCannotBeMistakenForEmptyOrOverwritten(): Unit = runBlocking {
        val file = temporaryFile()
        val corrupted = "{\"schema\":1,\"requestId\":\"$requestId\",\"description\":".toByteArray()
        file.writeBytes(corrupted)
        expectFailure<IOException> { store(file).read() }
        expectFailure<IOException> { store(file).write(request()) }
        expectFailure<IOException> { store(file).clear(requestId) }
        assertArrayEquals(corrupted, file.readBytes())
    }

    @Test fun unsupportedSchemaAndOversizedJournalFailClosedWithoutReplacingBytes(): Unit = runBlocking {
        val file = temporaryFile()
        listOf("{\"schema\":99}", "x".repeat(65_537)).forEach { corrupted ->
            file.writeText(corrupted)
            val bytes = file.readBytes()
            expectFailure<IOException> { store(file).read() }
            expectFailure<IOException> { store(file).write(request()) }
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun invalidUtf8CannotSilentlyChangeThePendingRequest(): Unit = runBlocking {
        val file = temporaryFile()
        store(file).write(request())
        val bytes = file.readBytes()
        // Un carácter UTF-8 inválido no se sustituye por otro al reconstruir los campos.
        file.writeBytes(bytes + byteArrayOf(0xc3.toByte(), 0x28))
        val corrupted = file.readBytes()
        expectFailure<IOException> { store(file).read() }
        assertArrayEquals(corrupted, file.readBytes())
    }

    @Test fun atomicFileRestoresItsBackupBeforeReadingTheRequest(): Unit = runBlocking {
        val file = temporaryFile()
        val request = request()
        store(file).write(request)
        val backup = File(file.path + ".bak")
        assertTrue(file.renameTo(backup))
        file.writeText("an interrupted replacement")
        assertEquals(request, store(file).read())
        assertFalse(backup.exists())
        assertEquals(request, store(file).read())
    }

    @Test fun simultaneousStoreInstancesAcceptOnlyOneDistinctIdentity(): Unit = runBlocking {
        val file = temporaryFile()
        val first = request()
        val second = first.copy(requestId = UUID.randomUUID().toString())
        val attempts = listOf(first, second).map { value ->
            async(Dispatchers.IO) { runCatching { store(file).write(value) } }
        }.awaitAll()
        assertEquals(1, attempts.count { it.isSuccess })
        assertEquals(1, attempts.count { it.exceptionOrNull() is IllegalStateException })
        val restored = store(file).read()
        assertTrue(restored == first || restored == second)
    }

    @Test fun committedReportCanBeRecoveredAfterReopeningJournalAndDatabaseWithoutGps(): Unit = runBlocking {
        val file = temporaryFile()
        val database = temporaryDatabase()
        val request = request()
        store(file).write(request)
        val saved = SqliteReportRepository(context, database, clock()).use {
            it.createOnce(request.requestId, request.draft)
        }
        val restored = store(file).read()!!
        assertEquals(request, restored)
        // Todo intento nuevo se rechazaría: recuperar el ya confirmado no consulta ese guard.
        var guardCalls = 0
        SqliteReportRepository(context, database, clock(), createGuard = {
            guardCalls++
            error("No hay una captura GPS autorizada tras restaurar el proceso.")
        }).use { repository ->
            assertEquals(saved, repository.findById(restored.requestId))
            assertTrue(restored.matches(saved))
            assertEquals(saved, repository.createOnce(restored.requestId, restored.draft))
            assertEquals(listOf(saved), repository.list())
        }
        assertEquals(0, guardCalls)
        assertEquals(request, store(file).read())
    }

    @Test fun pendingJournalDoesNotBypassGpsGuardOrOpenANewDatabase(): Unit = runBlocking {
        val file = temporaryFile()
        val database = temporaryDatabase()
        val request = request()
        store(file).write(request)
        val guard = NearbySqliteReportGuard({ null }, { 1_000_000_000L })
        SqliteReportRepository(context, database, clock(), createGuard = guard::check).use { repository ->
            expectFailure<InvalidReportException> { repository.createOnce(request.requestId, request.draft) }
        }
        assertFalse(context.getDatabasePath(database).exists())
        assertEquals(request, store(file).read())
    }

    @Test fun aJournalNameCannotEscapePrivateFilesDirectory() {
        listOf("../report.json", "C:\\report.json", "folder/report.json", "report.txt", "").forEach { name ->
            try {
                ReportSubmissionStore(context, name)
                fail("No debe aceptar una ruta externa.")
            } catch (_: IllegalArgumentException) { /* Rechazo antes de tocar archivos. */ }
        }
    }

    private fun store(file: File) = ReportSubmissionStore(context, file.name)
    private fun temporaryFile() = File(context.filesDir, "submission_test_${UUID.randomUUID()}.json")
        .also(files::add)
    private fun temporaryDatabase() = "submission_test_${UUID.randomUUID()}.db".also(databaseNames::add)
    private fun clock() = Clock.fixed(now, ZoneOffset.UTC)
    private fun request() = ReportSubmissionRequest(
        requestId, NewReport(
            IncidentType.RISK, Priority.MEDIUM, "Hay una luminaria dañada frente al mercado.",
            now.minusSeconds(60), ReportLocation("Frente al mercado", 19.4326, -99.1332),
        ), now,
    )

    private suspend inline fun <reified T : Throwable> expectFailure(noinline block: suspend () -> Unit) {
        try {
            block()
            fail("Se esperaba ${T::class.java.simpleName}.")
        } catch (failure: Throwable) {
            if (failure !is T) throw failure
        }
    }
}
