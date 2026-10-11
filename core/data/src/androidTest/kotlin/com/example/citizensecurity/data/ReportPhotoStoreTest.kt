package com.example.citizensecurity.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.ReportPhoto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Sólo carpetas/imagenes nuevas de estas pruebas. No importa ni elimina fotos del usuario. */
@RunWith(AndroidJUnit4::class)
class ReportPhotoStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ownedDirectories = mutableListOf<File>()
    private val ownedSources = mutableListOf<File>()

    @After fun cleanOnlyTheOwnedFixtures() {
        ownedDirectories.forEach { directory ->
            check(directory.canonicalFile.parentFile == context.filesDir.canonicalFile)
            check(directory.name.startsWith("photos_test_"))
            directory.deleteRecursively()
        }
        ownedSources.forEach { source ->
            check(source.canonicalFile.parentFile == context.cacheDir.canonicalFile)
            check(source.name.startsWith("photo_fixture_"))
            source.delete()
        }
    }

    @Test fun pngSelectionBecomesPrivateJpegWithVerifiedBytesAndWhiteTransparency(): Unit = runBlocking {
        val source = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            Canvas(this).drawRect(0f, 0f, 40f, 40f, Paint().apply { color = Color.RED })
        }
        val encoded = encode(source, Bitmap.CompressFormat.PNG)
        val (store, directory) = store()
        val photo = store.importPhoto(uri(encoded))
        val bytes = store.read(photo)
        assertEquals(80, photo.width)
        assertEquals(40, photo.height)
        assertEquals(bytes.size.toLong(), photo.byteCount)
        assertEquals(digest(bytes), photo.sha256)
        assertTrue(File(directory, "${photo.id}.jpg").isFile)
        assertEquals(listOf("${photo.id}.jpg"), directory.list()!!.toList())
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertTrue(Color.red(bitmap.getPixel(10, 20)) > 200)
            assertTrue(Color.green(bitmap.getPixel(70, 20)) > 230)
            assertTrue(Color.blue(bitmap.getPixel(70, 20)) > 230)
        } finally { bitmap.recycle() }
        assertNull(ExifInterface(ByteArrayInputStream(bytes)).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
    }

    @Test fun rotatedJpegKeepsItsVisualOrientationAndDropsGpsAndCameraMetadata(): Unit = runBlocking {
        val bitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888).apply {
            Canvas(this).apply {
                drawColor(Color.RED)
                drawRect(60f, 0f, 120f, 60f, Paint().apply { color = Color.BLUE })
            }
        }
        val source = File(context.cacheDir, "photo_fixture_${UUID.randomUUID()}.jpg").also(ownedSources::add)
        source.writeBytes(encode(bitmap, Bitmap.CompressFormat.JPEG))
        ExifInterface(source).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "19/1,25/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_MAKE, "Fixture camera")
            saveAttributes()
        }
        val (store, _) = store()
        // El provider anuncia PNG intencionalmente: la selección se reconoce por sus bytes.
        val photo = store.importPhoto(uri(source.readBytes()))
        assertEquals(60, photo.width)
        assertEquals(120, photo.height)
        val bytes = store.read(photo)
        val output = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertTrue(Color.red(output.getPixel(30, 20)) > 200)
            assertTrue(Color.blue(output.getPixel(30, 100)) > 200)
        } finally { output.recycle() }
        assertExifMetadataStripped(bytes)
    }

    @Test fun largeImageIsSampledBeforeItsOutputAndRemainsWithinDimensionsAndBytes(): Unit = runBlocking {
        val (store, _) = store()
        val source = Bitmap.createBitmap(3000, 1800, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val photo = store.importPhoto(uri(encode(source, Bitmap.CompressFormat.PNG)))
        assertTrue(photo.width in 1..ReportPhoto.MAX_DIMENSION)
        assertTrue(photo.height in 1..ReportPhoto.MAX_DIMENSION)
        assertTrue(photo.width < 3000)
        assertTrue(photo.byteCount in 1..ReportPhoto.MAX_BYTES)
        store.check(photo)
    }

    @Test fun webpSelectionIsDecodedAndNormalizedAsJpeg(): Unit = runBlocking {
        val (store, _) = store()
        val bitmap = Bitmap.createBitmap(60, 45, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.YELLOW) }
        @Suppress("DEPRECATION") val source = encode(bitmap, Bitmap.CompressFormat.WEBP)
        val photo = store.importPhoto(uri(source))
        val bytes = store.read(photo)
        assertEquals(60, photo.width)
        assertEquals(45, photo.height)
        assertEquals(255, bytes[0].toInt() and 255)
        assertEquals(216, bytes[1].toInt() and 255)
    }

    @Test fun legacyDecoderCorrectlyNormalizesEveryExifOrientationAndStripsPrivateMetadata(): Unit = runBlocking {
        verifyEveryOrientation(legacy = true)
    }

    @Test fun modernDecoderCorrectlyNormalizesEveryExifOrientationAndStripsPrivateMetadata(): Unit = runBlocking {
        verifyEveryOrientation(legacy = false)
    }

    @Test fun legacyPngDecoderSamplesBeforeAllocationAndFlattensTransparency(): Unit = runBlocking {
        val (store, _) = store()
        val source = Bitmap.createBitmap(4200, 2200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            Canvas(this).drawRect(0f, 0f, 2100f, 2200f, Paint().apply { color = Color.BLUE })
        }
        val photo = store.importPhotoWithLegacyDecoder(uri(encode(source, Bitmap.CompressFormat.PNG)))
        assertTrue(photo.width in 1..ReportPhoto.MAX_DIMENSION)
        assertTrue(photo.height in 1..ReportPhoto.MAX_DIMENSION)
        assertTrue(photo.width < 4200)
        val bytes = store.read(photo)
        val output = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertTrue(Color.blue(output.getPixel(output.width / 4, output.height / 2)) > 230)
            val transparentArea = output.getPixel(output.width * 3 / 4, output.height / 2)
            assertTrue(Color.red(transparentArea) > 230)
            assertTrue(Color.green(transparentArea) > 230)
            assertTrue(Color.blue(transparentArea) > 230)
        } finally { output.recycle() }
    }

    @Test fun truncatedPngIsRejectedBeforeEitherDecoderCreatesAFile(): Unit = runBlocking {
        val (store, directory) = store()
        val complete = smallPng()
        val withoutEndChunk = complete.copyOf(complete.size - 12)
        expectFailure { store.importPhoto(uri(withoutEndChunk)) }
        expectFailure { store.importPhotoWithLegacyDecoder(uri(withoutEndChunk)) }
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test fun truncatedWebpIsRejectedBeforeEitherDecoderCreatesAFile(): Unit = runBlocking {
        val (store, directory) = store()
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        @Suppress("DEPRECATION") val complete = encode(bitmap, Bitmap.CompressFormat.WEBP)
        val truncated = complete.copyOf(complete.size - 3)
        expectFailure { store.importPhoto(uri(truncated)) }
        expectFailure { store.importPhotoWithLegacyDecoder(uri(truncated)) }
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test fun legacyDecoderAlsoRejectsAHeaderReadableTruncatedJpegWithoutWriting(): Unit = runBlocking {
        val (store, directory) = store()
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val complete = encode(bitmap, Bitmap.CompressFormat.JPEG)
        expectFailure { store.importPhotoWithLegacyDecoder(uri(complete.copyOf(complete.size - 2))) }
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test fun unsupportedGifIsRejectedRatherThanPublishingItsFirstFrame(): Unit = runBlocking {
        val (store, directory) = store()
        val gif = Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT)
        expectFailure { store.importPhoto(uri(gif)) }
        expectFailure { store.importPhotoWithLegacyDecoder(uri(gif)) }
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test fun corruptPngIsRejectedWithoutCreatingAnyPhoto(): Unit = runBlocking {
        val (store, directory) = store()
        val bytes = smallPng()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        expectFailure { store.importPhoto(uri(bytes)) }
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test fun truncatedJpegIsRejectedEvenWhenItsHeaderDecodes(): Unit = runBlocking {
        val (store, directory) = store()
        val bitmap = Bitmap.createBitmap(60, 45, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val complete = encode(bitmap, Bitmap.CompressFormat.JPEG)
        expectFailure { store.importPhoto(uri(complete.copyOf(complete.size - 2))) }
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test fun oversizedStreamStopsAtSixteenMiBWithoutWriting(): Unit = runBlocking {
        val (store, directory) = store()
        expectFailure { store.importPhoto(Uri.parse("content://${PhotoFixtureProvider.AUTHORITY}/oversize")) }
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test fun sixtyFourMegapixelLimitIsCheckedBeforeBitmapAllocation(): Unit = runBlocking {
        val (store, directory) = store()
        val bytes = smallPng()
        // IHDR falso pero con CRC válido: se deben leer límites antes de descomprimir sus píxeles.
        writeInt(bytes, 16, 9000)
        writeInt(bytes, 20, 9000)
        writeInt(bytes, 29, CRC32().apply { update(bytes, 12, 17) }.value.toInt())
        expectFailure { store.importPhoto(uri(bytes)) }
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test fun anotherStoreCanReadTheSameDurablePhotoWithoutChangingIt(): Unit = runBlocking {
        val (store, directory) = store()
        val photo = store.importPhoto(uri(smallPng()))
        val bytes = store.read(photo)
        val timestamp = File(directory, "${photo.id}.jpg").lastModified()
        val reopened = ReportPhotoStore(context, directory.name)
        reopened.check(photo)
        assertArrayEquals(bytes, reopened.read(photo))
        assertEquals(timestamp, File(directory, "${photo.id}.jpg").lastModified())
    }

    @Test fun importingAgainUsesANewIdAndNeverReplacesTheEarlierPhoto(): Unit = runBlocking {
        val (store, directory) = store()
        val selection = uri(smallPng())
        val first = store.importPhoto(selection)
        val before = store.read(first)
        val timestamp = File(directory, "${first.id}.jpg").lastModified()
        val second = store.importPhoto(selection)
        assertNotEquals(first.id, second.id)
        assertEquals(first.sha256, second.sha256)
        assertArrayEquals(before, store.read(first))
        assertEquals(timestamp, File(directory, "${first.id}.jpg").lastModified())
        assertEquals(2, directory.listFiles()!!.size)
    }

    @Test fun changedHashIsRejectedAndTheDamagedFileIsPreserved(): Unit = runBlocking {
        val (store, directory) = store()
        val photo = store.importPhoto(uri(smallPng()))
        val file = File(directory, "${photo.id}.jpg")
        val modified = file.readBytes().apply { this[size / 2] = (this[size / 2].toInt() xor 1).toByte() }
        file.writeBytes(modified)
        expectFailure { store.read(photo) }
        assertArrayEquals(modified, file.readBytes())
    }

    @Test fun mismatchedDimensionsAreRejectedWithoutModifyingTheOriginal(): Unit = runBlocking {
        val (store, _) = store()
        val photo = store.importPhoto(uri(smallPng()))
        val before = store.read(photo)
        expectFailure { store.read(photo.copy(width = photo.width + 1)) }
        assertArrayEquals(before, store.read(photo))
    }

    @Test fun aMissingPhotoDoesNotRemoveAnotherPhotoOrFabricateAnImage(): Unit = runBlocking {
        val (store, directory) = store()
        val photo = store.importPhoto(uri(smallPng()))
        expectFailure { store.read(photo.copy(id = UUID.randomUUID().toString())) }
        assertTrue(File(directory, "${photo.id}.jpg").isFile)
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test fun evenAnUpdatedHashCannotMakeATruncatedStoredJpegValid(): Unit = runBlocking {
        val (store, directory) = store()
        val photo = store.importPhoto(uri(smallPng()))
        val file = File(directory, "${photo.id}.jpg")
        val truncated = file.readBytes().let { it.copyOf(it.size - 2) }
        file.writeBytes(truncated)
        val falseDescriptor = photo.copy(sha256 = digest(truncated), byteCount = truncated.size.toLong())
        expectFailure { store.read(falseDescriptor) }
        assertArrayEquals(truncated, file.readBytes())
    }

    @Test fun traversalOrAbsoluteDirectoryNamesCannotEscapePrivateStorage() {
        listOf("../photos", "a/b", "a\\b", ".", "", "/sdcard/photos").forEach { name ->
            try { ReportPhotoStore(context, name); fail("Debe rechazar una ruta de carpeta: $name") }
            catch (_: IllegalArgumentException) { /* Rechazo anterior a cualquier escritura. */ }
        }
    }

    @Test fun nonContentUrisAreRejectedBeforeAccessingAnyExternalFile(): Unit = runBlocking {
        val (store, directory) = store()
        expectFailure { store.importPhoto(Uri.parse("file:///sdcard/photo.jpg")) }
        assertTrue(directory.listFiles().isNullOrEmpty())
    }

    @Test fun cancelledReadNeverPublishesAPhotoOrDeletesAnExistingPhoto(): Unit = runBlocking {
        val (store, directory) = store()
        val previous = store.importPhoto(uri(smallPng()))
        val bytes = store.read(previous)
        val bitmap = Bitmap.createBitmap(60, 45, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val importing = async { store.importPhoto(uri(encode(bitmap, Bitmap.CompressFormat.JPEG), slow = true)) }
        delay(50)
        assertFalse(importing.isCompleted)
        importing.cancelAndJoin()
        assertEquals(listOf("${previous.id}.jpg"), directory.list()!!.toList())
        assertArrayEquals(bytes, store.read(previous))
    }

    private fun store(): Pair<ReportPhotoStore, File> {
        val name = "photos_test_${UUID.randomUUID()}"
        val directory = File(context.filesDir, name).also(ownedDirectories::add)
        return ReportPhotoStore(context, name) to directory
    }

    private suspend fun verifyEveryOrientation(legacy: Boolean) {
        val (store, _) = store()
        val orientations = listOf(
            ExifInterface.ORIENTATION_NORMAL to listOf(Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to listOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN),
            ExifInterface.ORIENTATION_ROTATE_180 to listOf(Color.YELLOW, Color.GREEN, Color.BLUE, Color.RED),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to listOf(Color.GREEN, Color.YELLOW, Color.RED, Color.BLUE),
            ExifInterface.ORIENTATION_TRANSPOSE to listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
            ExifInterface.ORIENTATION_ROTATE_90 to listOf(Color.GREEN, Color.RED, Color.YELLOW, Color.BLUE),
            ExifInterface.ORIENTATION_TRANSVERSE to listOf(Color.YELLOW, Color.BLUE, Color.GREEN, Color.RED),
            ExifInterface.ORIENTATION_ROTATE_270 to listOf(Color.BLUE, Color.YELLOW, Color.RED, Color.GREEN),
        )
        orientations.forEach { (orientation, expectedCorners) ->
            val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply {
                Canvas(this).apply {
                    drawColor(Color.RED)
                    drawRect(40f, 0f, 80f, 20f, Paint().apply { color = Color.BLUE })
                    drawRect(0f, 20f, 40f, 40f, Paint().apply { color = Color.GREEN })
                    drawRect(40f, 20f, 80f, 40f, Paint().apply { color = Color.YELLOW })
                }
            }
            val source = File(context.cacheDir, "photo_fixture_${UUID.randomUUID()}.jpg").also(ownedSources::add)
            source.writeBytes(encode(bitmap, Bitmap.CompressFormat.JPEG))
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "19/1,25/1,0/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                setAttribute(ExifInterface.TAG_MAKE, "Fixture camera")
                saveAttributes()
            }
            val selection = uri(source.readBytes())
            val photo = if (legacy) store.importPhotoWithLegacyDecoder(selection) else store.importPhoto(selection)
            val swapsAxes = orientation in ExifInterface.ORIENTATION_TRANSPOSE..ExifInterface.ORIENTATION_ROTATE_270
            assertEquals("Width for EXIF $orientation", if (swapsAxes) 40 else 80, photo.width)
            assertEquals("Height for EXIF $orientation", if (swapsAxes) 80 else 40, photo.height)
            val bytes = store.read(photo)
            val output = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            try {
                val actualCorners = listOf(
                    output.getPixel(output.width / 4, output.height / 4),
                    output.getPixel(output.width * 3 / 4, output.height / 4),
                    output.getPixel(output.width / 4, output.height * 3 / 4),
                    output.getPixel(output.width * 3 / 4, output.height * 3 / 4),
                )
                actualCorners.zip(expectedCorners).forEachIndexed { index, (actual, expected) ->
                    assertTrue("Red for EXIF $orientation corner $index", kotlin.math.abs(Color.red(actual) - Color.red(expected)) < 40)
                    assertTrue("Green for EXIF $orientation corner $index", kotlin.math.abs(Color.green(actual) - Color.green(expected)) < 40)
                    assertTrue("Blue for EXIF $orientation corner $index", kotlin.math.abs(Color.blue(actual) - Color.blue(expected)) < 40)
                }
            } finally { output.recycle() }
            assertExifMetadataStripped(bytes)
        }
    }

    private fun assertExifMetadataStripped(bytes: ByteArray) {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        assertNull("El JPEG normalizado no conserva ubicación EXIF", exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull("El JPEG normalizado no conserva fabricante EXIF", exif.getAttribute(ExifInterface.TAG_MAKE))
        // ExifInterface 1.4.2 sintetiza 0 (UNDEFINED) cuando no existe orientación.
        // Ambos valores de identidad son válidos; los píxeles y dimensiones ya prueban
        // la rotación. Comprobar además el contenedor evita aceptar EXIF retenido.
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        assertTrue("No debe quedar una transformación EXIF por aplicar",
            orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL)
        var position = 2
        while (position < bytes.size) {
            assertEquals("La cabecera JPEG conserva marcadores válidos", 255, bytes[position].toInt() and 255)
            while (position < bytes.size && bytes[position].toInt() and 255 == 255) position++
            assertTrue(position < bytes.size)
            val marker = bytes[position++].toInt() and 255
            if (marker == 0xDA || marker == 0xD9) return
            if (marker == 0x01 || marker in 0xD0..0xD7) continue
            assertTrue(position + 2 <= bytes.size)
            val length = (bytes[position].toInt() and 255) * 256 + (bytes[position + 1].toInt() and 255)
            assertTrue(length >= 2 && position.toLong() + length <= bytes.size)
            if (marker == 0xE1 && length >= 8) {
                val signature = String(bytes, position + 2, 6, Charsets.US_ASCII)
                assertNotEquals("El JPEG normalizado elimina el segmento EXIF completo", "Exif\u0000\u0000", signature)
            }
            position += length
        }
        fail("El JPEG normalizado necesita datos de imagen completos")
    }

    private fun smallPng() = encode(Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }, Bitmap.CompressFormat.PNG)
    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat): ByteArray {
        try { return ByteArrayOutputStream().also { assertTrue(bitmap.compress(format, 95, it)) }.toByteArray() }
        finally { bitmap.recycle() }
    }

    private fun uri(bytes: ByteArray, slow: Boolean = false): Uri = Uri.Builder().scheme("content")
        .authority(PhotoFixtureProvider.AUTHORITY).appendPath(if (slow) "slow" else "bytes")
        .appendPath(Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP)).build()

    private suspend fun expectFailure(action: suspend () -> Unit) {
        try { action(); fail("La foto inválida debe ser rechazada.") }
        catch (_: IOException) { /* Error de lectura/integridad esperado. */ }
        catch (_: IllegalArgumentException) { /* URI/ruta no admitida. */ }
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (24 - 8 * index)).toByte() }
    }
}

/** Pipe de imágenes ficticias; existe únicamente en el APK de instrumentación. */
class PhotoFixtureProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/png"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = throw UnsupportedOperationException()

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r")
        val path = uri.pathSegments
        val pipe = ParcelFileDescriptor.createPipe()
        Thread({
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output ->
                    if (path.firstOrNull() == "oversize") {
                        val chunk = ByteArray(8_192)
                        repeat(ReportPhotoStore.MAX_SOURCE_BYTES / chunk.size) { output.write(chunk) }
                        output.write(0)
                    } else {
                        val bytes = Base64.decode(path[1], Base64.URL_SAFE or Base64.NO_WRAP)
                        if (path.firstOrNull() == "slow") {
                            bytes.asList().chunked(256).forEach { chunk ->
                                Thread.sleep(200)
                                output.write(chunk.toByteArray())
                            }
                        } else output.write(bytes)
                    }
                }
            } catch (_: IOException) { /* El lector cerró su pipe al cancelar o alcanzar el límite. */ }
        }, "photo-fixture-pipe").start()
        return pipe[0]
    }

    companion object { const val AUTHORITY = "com.example.citizensecurity.data.photos.fixture" }
}
