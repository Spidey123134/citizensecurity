package com.example.citizensecurity.evidence

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.ReportStatus
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** PDF generation/rendering only: no production reports, GPS, activity or session mutation. */
class ReportEvidencePdfWriterTest {
    private val writer = ReportEvidencePdfWriter()
    private val zone = ZoneId.of("America/Mexico_City")
    private val exportedAt = Instant.parse("2026-10-13T19:21:30Z")
    private val ownedFiles = mutableListOf<File>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun cleanOwnedTemporaryFiles() { ownedFiles.forEach { it.delete() } }

    @Test fun aSavedReportProducesAReadableBoundedA4Document() {
        val report = report()
        val (bytes, count) = export(report)
        assertTrue(bytes.toString(Charsets.ISO_8859_1).startsWith("%PDF-"))
        assertTrue(bytes.size.toLong() <= ReportEvidencePdfWriter.MAX_OUTPUT_BYTES)
        render(bytes).use { renderer ->
            assertEquals(count, renderer.pageCount)
            assertTrue(count in 1..ReportEvidencePdfWriter.MAX_PAGES)
            renderer.openPage(0).use { page ->
                assertEquals(595, page.width)
                assertEquals(842, page.height)
                assertRenderedContent(page)
            }
        }
        assertEquals(report(), report)
    }

    @Test fun legacyReferenceWithoutCoordinatesCanBeExported() {
        val saved = report().copy(location = ReportLocation("Frente al árbol de la plaza"))
        val (bytes, count) = export(saved)
        render(bytes).use { renderer ->
            assertEquals(count, renderer.pageCount)
            renderer.openPage(0).use(::assertRenderedContent)
        }
    }

    @Test fun explicitlyOmittingPhotoDoesNotDecodeOrRequireItsBytes() {
        val saved = report().copy(photo = ReportPhoto(UUID.randomUUID().toString(), "0".repeat(64), 4, 1, 1))
        val (bytes, _) = export(saved, ReportEvidenceOptions(includeLocation = false, includePhoto = false), byteArrayOf(1, 2, 3))
        render(bytes).use { it.openPage(0).use(::assertRenderedContent) }
        val testContext = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(testContext.externalCacheDir ?: testContext.cacheDir, "evidence-pdf-qa")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "report-omitted.pdf").writeBytes(bytes)
    }

    @Test fun requestedNormalizedPhotoIsIncludedAndStillRenders() {
        val photo = fixturePhoto()
        val saved = report().copy(photo = photo.metadata)
        val (bytes, count) = export(saved, ReportEvidenceOptions(includePhoto = true), photo.bytes)
        render(bytes).use { renderer ->
            assertEquals(count, renderer.pageCount)
            var greenPixels = 0
            repeat(renderer.pageCount) { index ->
                renderer.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        for (y in 100 until bitmap.height - 50 step 2) for (x in 40 until bitmap.width - 40 step 2) {
                            val color = bitmap.getPixel(x, y)
                            if (Color.green(color) > 160 && Color.red(color) < 80 && Color.blue(color) < 100) greenPixels++
                        }
                    } finally { bitmap.recycle() }
                }
            }
            assertTrue("The fixture photograph must be visible, not only its metadata.", greenPixels > 1_000)
        }
    }

    @Test fun askingForAnAbsentPhotoFailsBeforeWriting() {
        assertRejected<IOException> { output -> exportTo(report(), ReportEvidenceOptions(includePhoto = true), null, output) }
    }

    @Test fun askingForPhotoWithoutReadableBytesFailsBeforeWriting() {
        assertRejected<IOException> { output -> exportTo(report().copy(photo = fixturePhoto().metadata), ReportEvidenceOptions(includePhoto = true), null, output) }
    }

    @Test fun photoWithWrongFingerprintFailsBeforeWriting() {
        val photo = fixturePhoto()
        assertRejected<IOException> { output ->
            exportTo(report().copy(photo = photo.metadata.copy(sha256 = "0".repeat(64))), ReportEvidenceOptions(includePhoto = true), photo.bytes, output)
        }
    }

    @Test fun photoWithWrongDimensionsFailsBeforeWriting() {
        val photo = fixturePhoto()
        assertRejected<IOException> { output ->
            exportTo(report().copy(photo = photo.metadata.copy(width = photo.metadata.width + 1)), ReportEvidenceOptions(includePhoto = true), photo.bytes, output)
        }
    }

    @Test fun truncatedJpegWithMatchingHashStillFailsBeforeWriting() {
        val photo = fixturePhoto()
        val truncated = photo.bytes.copyOf(photo.bytes.size - 2)
        assertRejected<IOException> { output ->
            exportTo(report().copy(photo = photo.metadata.copy(byteCount = truncated.size.toLong(), sha256 = hash(truncated))),
                ReportEvidenceOptions(includePhoto = true), truncated, output)
        }
    }

    @Test fun nonJpegBytesWithMatchingHashStillFailBeforeWriting() {
        val bytes = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        val metadata = ReportPhoto(UUID.randomUUID().toString(), hash(bytes), bytes.size.toLong(), 1, 1)
        assertRejected<IOException> { output ->
            exportTo(report().copy(photo = metadata), ReportEvidenceOptions(includePhoto = true), bytes, output)
        }
    }

    @Test fun matchingPhotoContainingExifIsRejected() {
        val photo = fixturePhoto()
        val app1 = byteArrayOf(-1, -31, 0, 8, 69, 120, 105, 102, 0, 0)
        val bytes = photo.bytes.copyOfRange(0, 2) + app1 + photo.bytes.copyOfRange(2, photo.bytes.size)
        assertRejected<IOException> { output ->
            exportTo(report().copy(photo = photo.metadata.copy(byteCount = bytes.size.toLong(), sha256 = hash(bytes))),
                ReportEvidenceOptions(includePhoto = true), bytes, output)
        }
    }

    @Test fun malformedCoordinatesAreNeverRenderedAsValidLocation() {
        assertRejected<IllegalArgumentException> { output ->
            exportTo(report().copy(location = ReportLocation("Frente a la plaza", 19.0, null)), ReportEvidenceOptions(), null, output)
        }
    }

    @Test fun permissionWithdrawalDuringPlanningStopsBeforeOutput() {
        val output = ByteArrayOutputStream()
        var calls = 0
        try {
            runBlocking { writer.write(report(), ReportEvidenceOptions(), null, exportedAt, zone, output) {
                if (++calls == 9) throw SecurityException("The captured workspace was revoked.")
            } }
            fail("A revoked workspace must not export.")
        } catch (_: SecurityException) { }
        assertEquals(0, output.size())
    }

    @Test fun cancellationStopsGenerationBeforeOutput() {
        val output = ByteArrayOutputStream()
        val job = Job()
        try {
            runBlocking { withContext(job) {
                writer.write(report(), ReportEvidenceOptions(), null, exportedAt, zone, output) { job.cancel() }
            } }
            fail("A cancelled export must not write.")
        } catch (_: CancellationException) { }
        assertEquals(0, output.size())
    }

    @Test fun failureOfTheCallerStreamIsPropagatedWithoutClosingIt() {
        val output = object : OutputStream() {
            var closed = false
            override fun write(value: Int) { throw IOException("Storage unavailable.") }
            override fun write(bytes: ByteArray, offset: Int, length: Int) { throw IOException("Storage unavailable.") }
            override fun close() { closed = true }
        }
        try {
            exportTo(report(), ReportEvidenceOptions(), null, output)
            fail("A failed file must not be presented as a complete PDF.")
        } catch (_: IOException) { }
        assertFalse(output.closed)
    }

    @Test fun extremeLineBreakCountFailsExplicitlyInsteadOfTruncating() {
        val saved = report().copy(description = "\n".repeat(999) + "a")
        assertRejected<IOException> { output -> exportTo(saved, ReportEvidenceOptions(), null, output) }
    }

    @Test fun streamFailureAfterAPartialWriteIsNeverReturnedAsACompletePdf() {
        val written = ByteArrayOutputStream()
        val output = object : OutputStream() {
            override fun write(value: Int) { throw IOException("Storage stopped after partial bytes") }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                written.write(bytes, offset, minOf(length, 64))
                throw IOException("Storage stopped after partial bytes")
            }
        }
        try {
            exportTo(report(), ReportEvidenceOptions(), null, output)
            fail("A partial PDF must not succeed")
        } catch (_: IOException) { }
        assertTrue(written.size() > 0)
    }

    @Test fun permissionWithdrawalWhileWritingCannotReturnACompletePdf() {
        var allowed = true
        val written = ByteArrayOutputStream()
        val output = object : OutputStream() {
            override fun write(value: Int) { written.write(value); allowed = false }
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                written.write(bytes, offset, length); allowed = false
            }
        }
        try {
            runBlocking { writer.write(report(), ReportEvidenceOptions(), null, exportedAt, zone, output) {
                if (!allowed) throw SecurityException("Session revoked during native output")
            } }
            fail("A revoked export must not succeed")
        } catch (_: SecurityException) { }
        assertTrue(written.size() > 0)
    }

    /** Pull these test-only artifacts for Poppler visual/text QA; they are not product examples. */
    @Test fun longUnicodeFixtureExportsEveryPageForVisualInspection() {
        val description = (1..30).joinToString("\n") { "Línea $it: árbol caído 🚧." } +
            "\n" + "Ñ".repeat(90) + "\nFIN-DESCRIPCIÓN-27"
        val photo = fixturePhoto()
        val saved = report().copy(description = description, location = ReportLocation(
            "Referencia larga: Álvaro Obregón; cruce frente al árbol, junto a la señal amarilla y la entrada de la plaza. " + "á".repeat(50),
            19.432608, -99.133209,
        ), photo = photo.metadata)
        assertTrue(saved.description.codePointCount(0, saved.description.length) <= 1_000)
        val (bytes, count) = export(saved, ReportEvidenceOptions(includePhoto = true), photo.bytes)
        assertTrue("The long multiline fixture must exercise pagination.", count >= 2)
        val testContext = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(testContext.externalCacheDir ?: testContext.cacheDir, "evidence-pdf-qa")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "report-unicode.pdf").writeBytes(bytes)
        File(directory, "report-unicode-expected.txt").writeText(
            listOf(saved.id, "FIN-DESCRIPCIÓN-27", "Latitud: 19.432608", "Longitud: -99.133209", photo.metadata.sha256,
                "Fecha del incidente", "Fecha de guardado en el teléfono", "Fecha de exportación", "America/Mexico_City",
                "Prioridad declarada por el autor", "Estado local").joinToString("\n"), Charsets.UTF_8,
        )
        render(bytes).use { renderer ->
            assertEquals(count, renderer.pageCount)
            repeat(count) { index ->
                renderer.openPage(index).use { page ->
                    assertRenderedContent(page)
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        File(directory, "report-unicode-page-${index + 1}.png").outputStream().use {
                            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    } finally { bitmap.recycle() }
                }
            }
        }
    }

    private fun report() = Report(
        id = "718d8b5e-57b8-4f8a-829b-f82017cdf1a9",
        type = IncidentType.RISK,
        priority = Priority.HIGH,
        description = "Una rama cayó sobre el paso peatonal; hay señal dañada cerca del árbol.",
        occurredAt = Instant.parse("2026-10-13T17:00:00Z"),
        location = ReportLocation("Cruce frente a la plaza", 19.432608, -99.133209),
        createdAt = Instant.parse("2026-10-13T17:05:00Z"),
        status = ReportStatus.REPORTED,
    )

    private fun export(saved: Report, options: ReportEvidenceOptions = ReportEvidenceOptions(), photo: ByteArray? = null): Pair<ByteArray, Int> {
        val output = ByteArrayOutputStream()
        val count = exportTo(saved, options, photo, output)
        return output.toByteArray() to count
    }

    private fun exportTo(saved: Report, options: ReportEvidenceOptions, photo: ByteArray?, output: OutputStream): Int = runBlocking {
        writer.write(saved, options, photo, exportedAt, zone, output)
    }

    private inline fun <reified T : Throwable> assertRejected(block: (ByteArrayOutputStream) -> Unit) {
        val output = ByteArrayOutputStream()
        try {
            block(output)
            fail("An invalid export must fail.")
        } catch (failure: Throwable) {
            assertTrue("Expected ${T::class.java.simpleName}, received ${failure.javaClass.simpleName}.", failure is T)
        }
        assertEquals("Validation failure must occur before PDF output.", 0, output.size())
    }

    private fun render(bytes: ByteArray): PdfRenderer {
        val file = File(context.cacheDir, "evidence-test-${UUID.randomUUID()}.pdf")
        ownedFiles.add(file)
        file.writeBytes(bytes)
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try { PdfRenderer(descriptor) } catch (failure: Throwable) { descriptor.close(); throw failure }
    }

    private fun assertRenderedContent(page: PdfRenderer.Page) {
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        try {
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            var darkPixels = 0
            // Count actual body content separately from the repeated header/footer.
            for (y in 100 until bitmap.height - 55 step 3) for (x in 40 until bitmap.width - 40 step 3) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) > 0 && Color.red(color) < 200 && Color.green(color) < 200 && Color.blue(color) < 200) darkPixels++
            }
            assertTrue("The PDF page must contain a rendered body.", darkPixels > 12)
        } finally { bitmap.recycle() }
    }

    private data class PhotoFixture(val metadata: ReportPhoto, val bytes: ByteArray)
    private fun fixturePhoto(): PhotoFixture {
        val bitmap = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888)
        val bytes = try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(20, 205, 40))
            canvas.drawRect(15f, 15f, 70f, 145f, Paint().apply { color = Color.BLUE })
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
            output.toByteArray()
        } finally { bitmap.recycle() }
        return PhotoFixture(ReportPhoto(UUID.randomUUID().toString(), hash(bytes), bytes.size.toLong(), 320, 160), bytes)
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 255) }
}
