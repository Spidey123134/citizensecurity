package com.example.citizensecurity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.ReportSubmissionRequest
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportSubmissionPhotoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val files = mutableListOf<File>()
    private val databases = mutableListOf<String>()
    private val now = Instant.parse("2026-10-10T05:00:00.123456789Z")
    private val requestId = "1e345678-1234-4567-89ab-0123456789ab"
    private val photo = ReportPhoto("2e345678-1234-4567-89ab-0123456789ab", "a".repeat(64), 1024, 640, 480)

    @After fun cleanOnlyOwnedFixtures() {
        files.forEach { file -> listOf(file, File(file.path + ".bak"), File(file.path + ".new")).forEach { it.delete() } }
        databases.forEach { context.deleteDatabase(it) }
    }

    @Test fun aLegacyJournalWithoutPhotoRestoresItsIdentityAndDoesNotRewriteBytes(): Unit = runBlocking {
        val file = temporaryFile()
        val legacy = legacyJson().toString().toByteArray(Charsets.UTF_8)
        file.writeBytes(legacy)
        val restored = store(file).read()!!
        assertEquals(request(null), restored)
        assertNull(restored.draft.photo)
        store(file).write(restored)
        assertArrayEquals(legacy, file.readBytes())
    }

    @Test fun photoMetadataRestoresAcrossANewStoreWithTheExactRequestAndNanoseconds(): Unit = runBlocking {
        val file = temporaryFile()
        val pending = request(photo)
        store(file).write(pending)
        assertEquals(pending, store(file).read())
        val json = JSONObject(file.readText())
        assertEquals(1, json.getInt("schema"))
        assertEquals(photo.id, json.getJSONObject("photo").getString("id"))
        assertEquals(photo.byteCount, json.getJSONObject("photo").getLong("byteCount"))
        assertEquals(now.nano, json.getInt("requestedAtNanos"))
        val bytes = file.readBytes()
        store(file).write(pending)
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun aPhotoCannotBeChangedOrRemovedFromAnUncertainRequest(): Unit = runBlocking {
        val file = temporaryFile()
        val pending = request(photo)
        store(file).write(pending)
        val bytes = file.readBytes()
        val changed = listOf<ReportPhoto?>(null,
            photo.copy(id = UUID.randomUUID().toString()), photo.copy(sha256 = "b".repeat(64)),
            photo.copy(byteCount = 1025), photo.copy(width = 641), photo.copy(height = 481))
        changed.forEach { candidate ->
            expectFailure<IllegalStateException> { store(file).write(request(candidate)) }
            assertEquals(pending, store(file).read())
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun anOldPendingJournalCannotSilentlyAcquireAPhoto(): Unit = runBlocking {
        val file = temporaryFile()
        val bytes = legacyJson().toString().toByteArray(Charsets.UTF_8)
        file.writeBytes(bytes)
        expectFailure<IllegalStateException> { store(file).write(request(photo)) }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(request(null), store(file).read())
    }

    @Test fun invalidPhotoFieldsFailClosedAndCannotBeClearedOrOverwritten(): Unit = runBlocking {
        val file = temporaryFile()
        val invalid = listOf(
            "id" to "../../outside.jpg", "sha256" to "A".repeat(64),
            "byteCount" to 0, "byteCount" to (ReportPhoto.MAX_BYTES + 1),
            "byteCount" to 1.5, "width" to 0, "height" to 2049,
            "width" to 4_294_967_297L, "height" to "480",
        )
        invalid.forEach { (key, badValue) ->
            val json = legacyJson().put("photo", photoJson().put(key, badValue))
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            file.writeBytes(bytes)
            expectFailure<IOException> { store(file).read() }
            expectFailure<IOException> { store(file).write(request(null)) }
            expectFailure<IOException> { store(file).clear(requestId) }
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun wrongPhotoShapeOrMissingMetadataNeverBecomesAnEmptyPhoto(): Unit = runBlocking {
        val file = temporaryFile()
        val missingHash = photoJson().apply { remove("sha256") }
        listOf<Any>("an external URI", 123, JSONObject(), missingHash).forEach { invalid ->
            val bytes = legacyJson().put("photo", invalid).toString().toByteArray(Charsets.UTF_8)
            file.writeBytes(bytes)
            expectFailure<IOException> { store(file).read() }
            assertArrayEquals(bytes, file.readBytes())
        }
    }

    @Test fun reopenedPhotoRequestMatchesItsCommittedReceiptWithoutRunningTheNewInsertGuard(): Unit = runBlocking {
        val file = temporaryFile()
        val database = "submission_photo_test_${UUID.randomUUID()}.db".also(databases::add)
        val pending = request(photo)
        store(file).write(pending)
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val saved = SqliteReportRepository(context, database, clock).use {
            it.createOnce(pending.requestId, pending.draft)
        }
        val restored = store(file).read()!!
        assertEquals(pending, restored)
        SqliteReportRepository(context, database, clock, createGuard = {
            error("Una lectura recuperada no puede requerir GPS o comprobar una nueva inserción.")
        }).use { repository ->
            assertEquals(saved, repository.findById(requestId))
            assertTrue(restored.matches(saved))
            assertEquals(saved, repository.createOnce(requestId, restored.draft))
            assertEquals(listOf(saved), repository.list())
        }
        assertEquals(photo, saved.photo)
    }

    private fun temporaryFile() = File(context.filesDir, "submission_photo_test_${UUID.randomUUID()}.json").also(files::add)
    private fun store(file: File) = ReportSubmissionStore(context, file.name)
    private fun request(photo: ReportPhoto?) = ReportSubmissionRequest(requestId,
        NewReport(IncidentType.RISK, Priority.MEDIUM, "Hay una luminaria dañada frente al mercado.",
            now.minusSeconds(60), ReportLocation("Frente al mercado", 19.4326, -99.1332), photo), now)
    private fun photoJson() = JSONObject().apply {
        put("id", photo.id); put("sha256", photo.sha256); put("byteCount", photo.byteCount)
        put("width", photo.width); put("height", photo.height)
    }
    private fun legacyJson() = JSONObject().apply {
        put("schema", 1); put("requestId", requestId)
        put("requestedAtSeconds", now.epochSecond); put("requestedAtNanos", now.nano)
        put("type", "RISK"); put("priority", "MEDIUM")
        put("description", "Hay una luminaria dañada frente al mercado.")
        put("occurredAtSeconds", now.minusSeconds(60).epochSecond); put("occurredAtNanos", now.nano)
        put("reference", "Frente al mercado"); put("latitude", 19.4326); put("longitude", -99.1332)
    }
    private suspend inline fun <reified T : Throwable> expectFailure(noinline action: suspend () -> Unit) {
        try { action(); fail("Se esperaba ${T::class.java.simpleName}.") }
        catch (failure: Throwable) { if (failure !is T) throw failure }
    }
}
