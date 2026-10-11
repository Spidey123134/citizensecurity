package com.example.citizensecurity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportDraftCheckpoint
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.ReportSubmissionRequest
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Archivos de cada fixture: ninguna prueba abre o borra el diario productivo. */
@RunWith(AndroidJUnit4::class)
class ReportDraftCheckpointStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = mutableListOf<File>()
    private val now = Instant.parse("2026-10-10T03:00:00.123456789Z")
    private val id = "1e345678-1234-4567-89ab-0123456789ab"
    private val folio = "3e345678-1234-4567-89ab-0123456789ab"

    @After fun cleanOnlyOwnedFixtures() {
        files.forEach { file ->
            listOf(file, File(file.path + ".bak"), File(file.path + ".new")).forEach { it.delete() }
        }
    }

    @Test fun absentFileReadsAsAbsentWithoutCreatingFilesOrDatabases(): Unit = runBlocking {
        val file = temporaryFile()
        val databases = context.databaseList().toSet()
        assertNull(store(file).read())
        assertFalse(file.exists())
        assertEquals(databases, context.databaseList().toSet())
    }

    @Test fun reopeningPreservesUnicodeNanosecondsCoordinatesAndPhotoMetadata(): Unit = runBlocking {
        val file = temporaryFile()
        val photo = ReportPhoto("2e345678-1234-4567-89ab-0123456789ab", "a".repeat(64), 1536, 640, 480)
        val checkpoint = checkpoint().let { it.copy(draft = it.draft.copy(
            description = "Hay un árbol caído 🌳.\nEstá frente al mercado y deja pasar peatones.",
            photo = photo,
        )) }
        store(file).write(checkpoint, null)
        assertEquals(checkpoint, store(file).read())
        val text = file.readText()
        assertTrue(text.contains(photo.sha256))
        listOf("content://", "accuracy", "permission", "elapsedRealtime", "mock").forEach {
            assertFalse(text.contains(it))
        }
    }

    @Test fun anIdenticalRetryDoesNotRewriteTheCommittedBytes(): Unit = runBlocking {
        val file = temporaryFile()
        val value = checkpoint()
        store(file).write(value, null)
        val bytes = file.readBytes()
        val modified = file.lastModified()
        // Una respuesta perdida requiere leer el token actual antes de reintentar.
        expectFailure<IllegalStateException> { store(file).write(value, null) }
        store(file).write(value, value.id)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(modified, file.lastModified())
    }

    @Test fun negativeZeroRoundTripsAndAnIdenticalRetryKeepsItsRevision(): Unit = runBlocking {
        val file = temporaryFile()
        val value = checkpoint().let { it.copy(draft = it.draft.copy(
            location = ReportLocation("Frente al mercado", -0.0, -0.0),
        )) }
        store(file).write(value, null)
        val read = store(file).read()!!
        assertEquals(0.0.toBits(), read.draft.location.latitude!!.toBits())
        assertEquals(0.0.toBits(), read.draft.location.longitude!!.toBits())
        store(file).write(value, value.id)
        assertEquals(read, store(file).read())
    }

    @Test fun replacementRequiresTheCurrentRevisionToken(): Unit = runBlocking {
        val file = temporaryFile()
        val first = checkpoint()
        val next = first.copy(id = UUID.randomUUID().toString(), savedAt = now.plusNanos(1))
        store(file).write(first, null)
        val bytes = file.readBytes()
        expectFailure<IllegalStateException> { store(file).write(next, null) }
        expectFailure<IllegalStateException> { store(file).write(next, UUID.randomUUID().toString()) }
        assertArrayEquals(bytes, file.readBytes())
        store(file).write(next, first.id)
        assertEquals(next, store(file).read())
    }

    @Test fun changingFieldsWithoutANewRevisionCannotReuseAnOldToken(): Unit = runBlocking {
        val file = temporaryFile()
        val first = checkpoint()
        store(file).write(first, null)
        val changed = first.copy(draft = first.draft.copy(priority = Priority.HIGH))
        expectFailure<IllegalStateException> { store(file).write(changed, first.id) }
        assertEquals(first, store(file).read())
    }

    @Test fun aStaleScreenCannotReplaceOrMarkANewerCopy(): Unit = runBlocking {
        val file = temporaryFile()
        val first = checkpoint()
        val next = first.copy(id = UUID.randomUUID().toString(), savedAt = now.plusSeconds(1))
        store(file).write(first, null)
        store(file).write(next, first.id)
        val bytes = file.readBytes()
        expectFailure<IllegalStateException> { store(file).write(first.copy(id = UUID.randomUUID().toString()), first.id) }
        expectFailure<IllegalStateException> { store(file).markSubmitted(first.id, folio) }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(next, store(file).read())
    }

    @Test fun markingSubmittedPreservesAllFieldsAndIsIdempotent(): Unit = runBlocking {
        val file = temporaryFile()
        val first = checkpoint().let { it.copy(draft = it.draft.copy(
            photo = ReportPhoto("2e345678-1234-4567-89ab-0123456789ab", "b".repeat(64), 444, 320, 240),
        )) }
        store(file).write(first, null)
        store(file).markSubmitted(first.id, folio)
        val expected = first.copy(submittedFolio = folio)
        assertEquals(expected, store(file).read())
        val bytes = file.readBytes()
        val modified = file.lastModified()
        store(file).markSubmitted(first.id, folio)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(modified, file.lastModified())
        expectFailure<IllegalStateException> { store(file).markSubmitted(first.id, UUID.randomUUID().toString()) }
        assertEquals(expected, store(file).read())
    }

    @Test fun aMarkedCopyCanBeReplacedOnlyByAnExplicitNewRevision(): Unit = runBlocking {
        val file = temporaryFile()
        val first = checkpoint()
        store(file).write(first, null)
        store(file).markSubmitted(first.id, folio)
        val next = first.copy(id = UUID.randomUUID().toString(), savedAt = now.plusSeconds(1))
        store(file).write(next, first.id, expectedSubmittedFolio = folio)
        assertEquals(next, store(file).read())
        assertNull(store(file).read()!!.submittedFolio)
    }

    @Test fun aScreenThatHasNotObservedTheFolioCannotReplaceTheMarkedCopy(): Unit = runBlocking {
        val file = temporaryFile()
        val first = checkpoint()
        store(file).write(first, null)
        val staleRead = store(file).read()!!
        store(file).markSubmitted(first.id, folio)
        val bytes = file.readBytes()
        val next = staleRead.copy(id = UUID.randomUUID().toString(), savedAt = now.plusSeconds(1))
        expectFailure<IllegalStateException> {
            store(file).write(next, staleRead.id, staleRead.submittedFolio)
        }
        // Incluso repetir la copia vieja no puede ignorar una marca recién confirmada.
        expectFailure<IllegalStateException> {
            store(file).write(staleRead, staleRead.id, staleRead.submittedFolio)
        }
        assertArrayEquals(bytes, file.readBytes())
        val confirmed = store(file).read()!!
        assertEquals(folio, confirmed.submittedFolio)
        store(file).write(next, confirmed.id, confirmed.submittedFolio)
        assertEquals(next, store(file).read())
    }

    @Test fun writeCannotInventASubmittedFolioForAnUnstoredDraft(): Unit = runBlocking {
        val file = temporaryFile()
        expectFailure<IllegalArgumentException> { store(file).write(checkpoint().copy(submittedFolio = folio), null) }
        assertNull(store(file).read())
        assertFalse(file.exists())
    }

    @Test fun markCannotInventACopyOrAcceptInvalidIds(): Unit = runBlocking {
        val file = temporaryFile()
        expectFailure<IllegalStateException> { store(file).markSubmitted(id, folio) }
        store(file).write(checkpoint(), null)
        expectFailure<IllegalArgumentException> { store(file).markSubmitted("1-1-1-1-1", folio) }
        expectFailure<IllegalArgumentException> { store(file).markSubmitted(id, folio.uppercase()) }
        expectFailure<IllegalArgumentException> { store(file).write(checkpoint(), "1-1-1-1-1") }
        expectFailure<IllegalArgumentException> { store(file).write(checkpoint(), id, "1-1-1-1-1") }
        assertEquals(checkpoint(), store(file).read())
    }

    @Test fun twoInstancesRacingToCreateAllowExactlyOneCommittedCopy(): Unit = runBlocking {
        val file = temporaryFile()
        val copies = listOf(checkpoint(), checkpoint().copy(id = UUID.randomUUID().toString()))
        val accepted = AtomicInteger()
        val rejected = AtomicInteger()
        copies.map { value -> async(Dispatchers.IO) {
            try {
                store(file).write(value, null)
                accepted.incrementAndGet()
            } catch (_: IllegalStateException) {
                rejected.incrementAndGet()
            }
        } }.awaitAll()
        assertEquals(1, accepted.get())
        assertEquals(1, rejected.get())
        assertTrue(store(file).read() in copies)
    }

    @Test fun aCorruptCopyIsNeverTreatedAsEmptyOrOverwritten(): Unit = runBlocking {
        val file = temporaryFile()
        val bytes = "{\"schema\":1,\"id\":\"$id\",\"description\":".toByteArray()
        file.writeBytes(bytes)
        assertCorruptionIsRetained(file, bytes)
    }

    @Test fun unknownSchemaMissingFieldsAndWrongPrimitiveTypesAreRetained(): Unit = runBlocking {
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("schema", 2) },
            { it.remove("submittedFolio") },
            { it.remove("photo") },
            { it.put("description", 123) },
            { it.put("latitude", "19.4326") },
            { it.put("savedAtNanos", 1_000_000_000L) },
            { it.put("savedAtSeconds", "0") },
            { it.put("savedAtSeconds", 1.5) },
            { it.put("longitude", JSONObject.NULL) },
            { it.put("type", "UNKNOWN") },
            { it.put("submittedFolio", 100) },
            { it.put("photo", JSONObject().put("id", id)) },
            { it.put("description", "Corto") },
            { it.put("reference", "Referencia\u0000 inválida") },
            { it.put("latitude", 91.0) },
            { it.put("occurredAtSeconds", now.plusSeconds(61).epochSecond) },
            { it.put("savedAtSeconds", -1L) },
        )
        mutations.forEach { change ->
            val file = temporaryFile()
            store(file).write(checkpoint(), null)
            val json = JSONObject(file.readText())
            change(json)
            val bytes = json.toString().toByteArray()
            file.writeBytes(bytes)
            assertCorruptionIsRetained(file, bytes)
        }
    }

    @Test fun malformedUtf8AndOversizedInputRemainUntouched(): Unit = runBlocking {
        listOf(byteArrayOf(0x7b, 0xc3.toByte(), 0x28, 0x7d), ByteArray(16_385) { 0x20 }).forEach { bytes ->
            val file = temporaryFile()
            file.writeBytes(bytes)
            assertCorruptionIsRetained(file, bytes)
        }
    }

    @Test fun invalidReportCannotReplaceTheLastGoodCopy(): Unit = runBlocking {
        val file = temporaryFile()
        val first = checkpoint()
        store(file).write(first, null)
        val bytes = file.readBytes()
        listOf(
            first.draft.copy(description = "Corto"),
            first.draft.copy(description = "Descripción inválida\u0000 aquí."),
            first.draft.copy(occurredAt = now.plusSeconds(61)),
            first.draft.copy(location = ReportLocation("Frente al mercado", 91.0, 0.0)),
        ).forEach { invalid ->
            expectFailure<InvalidReportException> {
                store(file).write(first.copy(id = UUID.randomUUID().toString(), draft = invalid), first.id)
            }
            assertArrayEquals(bytes, file.readBytes())
        }
        assertEquals(first, store(file).read())
    }

    @Test fun oversizedEncodedWhitespaceCannotReplaceTheLastGoodCopy(): Unit = runBlocking {
        val file = temporaryFile()
        val first = checkpoint()
        store(file).write(first, null)
        val bytes = file.readBytes()
        // La descripción recortada es válida; el límite aplica también al JSON completo.
        val oversized = first.copy(id = UUID.randomUUID().toString(), draft = first.draft.copy(
            description = " ".repeat(16_384) + first.draft.description,
        ))
        expectFailure<IllegalArgumentException> { store(file).write(oversized, first.id) }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(first, store(file).read())
    }

    @Test fun aCapturedTimestampIsValidatedAgainstItsOwnSavedAtInsteadOfTodaysClock(): Unit = runBlocking {
        val file = temporaryFile()
        val savedAt = Instant.parse("2030-01-01T00:00:00.000000001Z")
        val value = checkpoint().copy(savedAt = savedAt, draft = checkpoint().draft.copy(occurredAt = savedAt.plusSeconds(60)))
        store(file).write(value, null)
        assertEquals(value, store(file).read())
    }

    @Test fun theAtomicBackupIsRecoveredWithoutDiscardingTheLastCompleteCopy(): Unit = runBlocking {
        val file = temporaryFile()
        store(file).write(checkpoint(), null)
        val backup = File(file.path + ".bak")
        assertTrue(file.renameTo(backup))
        file.writeText("incompleto")
        assertEquals(checkpoint(), store(file).read())
        assertTrue(file.exists())
        assertFalse(backup.exists())
    }

    @Test fun savingACopyDoesNotChangeTheIndependentPendingSubmission(): Unit = runBlocking {
        val pendingFile = temporaryFile()
        val copyFile = temporaryFile()
        val pending = ReportSubmissionRequest(folio, checkpoint().draft, now)
        val pendingStore = ReportSubmissionStore(context, pendingFile.name, Dispatchers.Unconfined)
        pendingStore.write(pending)
        val bytes = pendingFile.readBytes()
        store(copyFile).write(checkpoint(), null)
        store(copyFile).markSubmitted(id, folio)
        assertArrayEquals(bytes, pendingFile.readBytes())
        assertEquals(pending, pendingStore.read())
    }

    @Test fun pathNamesCannotEscapeTheApplicationPrivateDirectory() {
        listOf("../report.json", "folder\\report.json", "report.db", "").forEach { invalid ->
            expectFailure<IllegalArgumentException> { ReportDraftCheckpointStore(context, invalid) }
        }
    }

    private suspend fun assertCorruptionIsRetained(file: File, bytes: ByteArray) {
        expectFailure<IOException> { store(file).read() }
        expectFailure<IOException> { store(file).write(checkpoint(), null) }
        expectFailure<IOException> { store(file).markSubmitted(id, folio) }
        assertArrayEquals(bytes, file.readBytes())
    }

    private fun checkpoint() = ReportDraftCheckpoint(
        id = id,
        savedAt = now,
        draft = NewReport(IncidentType.RISK, Priority.MEDIUM,
            "Hay un árbol caído frente al mercado.", now.minusSeconds(30),
            ReportLocation("Frente al mercado", 19.4326, -99.1332)),
    )

    private fun temporaryFile(): File = File(context.filesDir, "draft_checkpoint_test_${UUID.randomUUID()}.json")
        .also { files += it }

    private fun store(file: File) = ReportDraftCheckpointStore(context, file.name, Dispatchers.Unconfined)

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try {
            block()
            fail("Se esperaba ${T::class.java.simpleName}.")
        } catch (failure: Throwable) {
            if (failure !is T) throw failure
        }
    }
}
