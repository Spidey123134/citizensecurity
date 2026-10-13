package com.example.citizensecurity.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.example.citizensecurity.domain.ReportPhoto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Una selección explícita se convierte en JPEG privado, sin URI ni metadatos externos.
 * Las importaciones son nuevas identidades; nunca sustituyen ni borran una foto conservada.
 */
class ReportPhotoStore(
    context: Context,
    directoryName: String = DIRECTORY_NAME,
    private val accessCheck: () -> Unit = {},
) {
    private val resolver = context.applicationContext.contentResolver
    private val parent = context.applicationContext.filesDir.canonicalFile
    private val directory: File
    private val lock: Any

    init {
        require(DIRECTORY_PATTERN.matches(directoryName)) { "Las fotos necesitan un nombre de carpeta sin ruta." }
        directory = File(parent, directoryName)
        require(directory.canonicalFile.parentFile == parent) { "La carpeta de fotos debe permanecer en el espacio privado." }
        lock = locks.getOrPut(directory.canonicalPath) { Any() }
    }

    suspend fun importPhoto(uri: Uri): ReportPhoto = importPhoto(uri, useLegacyDecoder = false)

    /** Verifica la ruta API 26/27 en instrumentación sin introducir un selector en la aplicación. */
    internal suspend fun importPhotoWithLegacyDecoder(uri: Uri): ReportPhoto =
        importPhoto(uri, useLegacyDecoder = true)

    private suspend fun importPhoto(uri: Uri, useLegacyDecoder: Boolean): ReportPhoto = withContext(Dispatchers.IO) {
        accessCheck()
        require(uri.scheme == "content") { "Selecciona una imagen desde el selector de fotos." }
        ensureActive()
        val source = resolver.openInputStream(uri)?.use { input ->
            input.readLimited(MAX_SOURCE_BYTES) { ensureActive() }
        } ?: throw IOException("No se pudo abrir la imagen seleccionada. Selecciónala nuevamente.")
        ensureActive()
        validateContainer(source)
        val bounds = imageBounds(source)
        requireSourceBounds(bounds.outWidth, bounds.outHeight)
        val decoded = if (!useLegacyDecoder && Build.VERSION.SDK_INT >= 28) decodeModern(source) else decodeLegacy(source, bounds)
        val opaque = if (decoded.hasAlpha()) {
            try {
                Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888).also { output ->
                    Canvas(output).apply { drawColor(Color.WHITE); drawBitmap(decoded, 0f, 0f, null) }
                }
            } finally { decoded.recycle() }
        } else decoded
        val width = opaque.width
        val height = opaque.height
        val bytes = try {
            ensureActive()
            val output = LimitedOutputStream(ReportPhoto.MAX_BYTES.toInt())
            if (!opaque.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                throw IOException("No se pudo preparar la foto seleccionada.")
            }
            ensureActive()
            output.bytes()
        } finally { opaque.recycle() }
        if (bytes.isEmpty()) throw IOException("La foto preparada está vacía.")
        val checksum = sha256(bytes)
        ensureActive()
        synchronized(lock) {
            accessCheck()
            ensurePrivateDirectory()
            val id = generateSequence { UUID.randomUUID().toString() }.first { candidate ->
                val base = photoFile(candidate)
                !base.exists() && !File(base.path + ".new").exists() && !File(base.path + ".bak").exists()
            }
            val photo = ReportPhoto(id, checksum, bytes.size.toLong(), width, height)
            val atomic = AtomicFile(photoFile(id))
            ensureActive()
            val output = atomic.startWrite()
            try {
                output.write(bytes)
                ensureActive()
                accessCheck()
                atomic.finishWrite(output)
            } catch (failure: Exception) {
                // failWrite retira solamente el .new de esta identidad recién reservada.
                atomic.failWrite(output)
                throw failure
            }
            // Si se canceló después de confirmar, se conserva el archivo duradero: no se borra.
            ensureActive()
            accessCheck()
            photo
        }
    }

    /** Bloqueante: llamar desde el guard de SQLite en IO. No restaura, sustituye ni borra archivos. */
    fun check(photo: ReportPhoto) {
        accessCheck()
        readChecked(photo)
        accessCheck()
    }

    suspend fun read(photo: ReportPhoto): ByteArray = withContext(Dispatchers.IO) {
        ensureActive()
        accessCheck()
        val bytes = readChecked(photo)
        ensureActive()
        accessCheck()
        bytes
    }

    private fun readChecked(photo: ReportPhoto): ByteArray = synchronized(lock) {
        accessCheck()
        checkPrivateDirectory()
        val base = photoFile(photo.id)
        if (!base.isFile) throw IOException("La foto guardada no está disponible; se conserva la referencia del reporte.")
        val bytes = base.inputStream().use { it.readLimited(ReportPhoto.MAX_BYTES.toInt()) }
        if (bytes.size.toLong() != photo.byteCount || sha256(bytes) != photo.sha256) {
            throw IOException("La foto guardada no coincide con su tamaño o huella; no se modifica el archivo.")
        }
        if (!isJpeg(bytes)) throw IOException("La foto guardada no es el JPEG preparado por la aplicación.")
        validateJpeg(bytes)
        val bounds = imageBounds(bytes)
        if (bounds.outMimeType != "image/jpeg" || bounds.outWidth != photo.width || bounds.outHeight != photo.height) {
            throw IOException("Las dimensiones de la foto guardada no coinciden con el reporte.")
        }
        // También rechazar una carga codificada que sólo conserve una cabecera válida.
        val bitmap = if (Build.VERSION.SDK_INT >= 28) decodeModern(bytes) else decodeLegacy(bytes, bounds)
        try {
            if (bitmap.width != photo.width || bitmap.height != photo.height) {
                throw IOException("La foto guardada no se pudo recuperar íntegramente.")
            }
        } finally { bitmap.recycle() }
        bytes
    }

    @RequiresApi(28)
    private fun decodeModern(source: ByteArray): Bitmap = ImageDecoder.decodeBitmap(
        ImageDecoder.createSource(ByteBuffer.wrap(source)),
    ) { decoder, info, _ ->
        requireSourceBounds(info.size.width, info.size.height)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setOnPartialImageListener { false }
        val scale = maxOf(info.size.width, info.size.height).toDouble() / ReportPhoto.MAX_DIMENSION
        if (scale > 1.0) decoder.setTargetSize(
            (info.size.width / scale).toInt().coerceAtLeast(1),
            (info.size.height / scale).toInt().coerceAtLeast(1),
        )
    }

    private fun decodeLegacy(source: ByteArray, bounds: BitmapFactory.Options): Bitmap {
        var sample = 1
        while ((bounds.outWidth + sample - 1) / sample > ReportPhoto.MAX_DIMENSION ||
            (bounds.outHeight + sample - 1) / sample > ReportPhoto.MAX_DIMENSION) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw IOException("La imagen está dañada o no se pudo leer íntegramente.")
        try {
            val orientation = ExifInterface(ByteArrayInputStream(source)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
            )
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
                }
            }
            if (matrix.isIdentity) return bitmap
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            return rotated
        } catch (failure: Exception) {
            bitmap.recycle()
            throw failure
        }
    }

    private fun imageBounds(bytes: ByteArray) = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
        if (outWidth <= 0 || outHeight <= 0 || outMimeType !in SUPPORTED_MIMES) {
            throw IOException("La imagen está dañada. Elige una foto JPEG, PNG o WebP completa.")
        }
    }

    private fun requireSourceBounds(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_SOURCE_PIXELS) {
            throw IOException("La imagen supera el límite de 64 megapíxeles.")
        }
    }

    private fun validateContainer(bytes: ByteArray) = when {
        isJpeg(bytes) -> validateJpeg(bytes)
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE) -> validatePng(bytes)
        bytes.size >= 12 && bytes.ascii(0, 4) == "RIFF" && bytes.ascii(8, 4) == "WEBP" -> validateWebp(bytes)
        else -> throw IOException("Elige una foto JPEG, PNG o WebP completa; no se pudo reconocer la imagen.")
    }

    private fun validateJpeg(bytes: ByteArray) {
        var position = 2
        var scan = false
        while (position < bytes.size) {
            if (bytes[position].toInt() and 255 != 255) throw IOException("La foto JPEG está incompleta o dañada.")
            while (position < bytes.size && bytes[position].toInt() and 255 == 255) position++
            if (position >= bytes.size) break
            val marker = bytes[position++].toInt() and 255
            if (marker == 0xD9) {
                if (scan && position == bytes.size) return
                throw IOException("La foto JPEG está incompleta o dañada.")
            }
            if (marker == 0x00 || marker == 0xD8 || marker in 0xD0..0xD7) throw IOException("La foto JPEG está dañada.")
            if (marker == 0x01) continue
            if (position + 2 > bytes.size) break
            val length = (bytes[position].toInt() and 255) * 256 + (bytes[position + 1].toInt() and 255)
            if (length < 2 || position.toLong() + length > bytes.size) break
            position += length
            if (marker == 0xDA) {
                scan = true
                while (position < bytes.size) {
                    if (bytes[position].toInt() and 255 != 255) { position++; continue }
                    val start = position
                    while (position < bytes.size && bytes[position].toInt() and 255 == 255) position++
                    if (position >= bytes.size) break
                    val next = bytes[position].toInt() and 255
                    if (next == 0x00 || next in 0xD0..0xD7) { position++; continue }
                    position = start
                    break
                }
            }
        }
        throw IOException("La foto JPEG está incompleta o dañada.")
    }

    private fun validatePng(bytes: ByteArray) {
        var position = 8
        var header = false
        var imageData = false
        while (position + 12 <= bytes.size) {
            val length = bytes.unsignedInt(position)
            if (length > MAX_SOURCE_BYTES || position.toLong() + 12 + length > bytes.size) break
            val size = length.toInt()
            val type = bytes.ascii(position + 4, 4)
            val crc = CRC32().apply { update(bytes, position + 4, size + 4) }.value
            if (crc != bytes.unsignedInt(position + size + 8)) throw IOException("La imagen PNG está dañada.")
            when (type) {
                "IHDR" -> {
                    if (header || position != 8 || size != 13) throw IOException("La imagen PNG está dañada.")
                    header = true
                }
                "IDAT" -> imageData = true
                "IEND" -> {
                    if (header && imageData && size == 0 && position + 12 == bytes.size) return
                    throw IOException("La imagen PNG está incompleta.")
                }
            }
            position += size + 12
        }
        throw IOException("La imagen PNG está incompleta o dañada.")
    }

    private fun validateWebp(bytes: ByteArray) {
        if (bytes.unsignedInt(4, littleEndian = true) + 8 != bytes.size.toLong()) {
            throw IOException("La imagen WebP está incompleta o dañada.")
        }
        var position = 12
        var imageData = false
        while (position + 8 <= bytes.size) {
            val size = bytes.unsignedInt(position + 4, littleEndian = true)
            val padded = size + size % 2
            if (position.toLong() + 8 + padded > bytes.size) throw IOException("La imagen WebP está incompleta.")
            if (bytes.ascii(position, 4) in setOf("VP8 ", "VP8L", "ANMF")) imageData = true
            position += 8 + padded.toInt()
        }
        if (!imageData || position != bytes.size) throw IOException("La imagen WebP está incompleta o dañada.")
    }

    private fun checkPrivateDirectory() {
        if (directory.canonicalFile.parentFile != parent) throw IOException("La carpeta de fotos no pertenece al espacio privado.")
    }

    private fun ensurePrivateDirectory() {
        checkPrivateDirectory()
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("No se pudo preparar el espacio privado de fotos.")
        checkPrivateDirectory()
    }

    private fun photoFile(id: String): File {
        if (runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false).not()) {
            throw IOException("La foto necesita un identificador completo y canónico.")
        }
        checkPrivateDirectory()
        return File(directory, "$id.jpg").also {
            if (it.canonicalFile.parentFile != directory.canonicalFile) throw IOException("La foto no pertenece a la carpeta privada.")
        }
    }

    private fun InputStream.readLimited(maxBytes: Int, active: () -> Unit = {}): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            active()
            val count = read(buffer)
            if (count < 0) break
            if (output.size().toLong() + count > maxBytes) throw IOException("La imagen supera el tamaño máximo permitido.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private class LimitedOutputStream(private val limit: Int) : OutputStream() {
        private val output = ByteArrayOutputStream()
        override fun write(value: Int) {
            if (output.size() >= limit) throw IOException("La foto preparada supera los 6 MiB.")
            output.write(value)
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (output.size().toLong() + length > limit) throw IOException("La foto preparada supera los 6 MiB.")
            output.write(bytes, offset, length)
        }
        fun bytes(): ByteArray = output.toByteArray()
    }

    private fun ByteArray.ascii(offset: Int, length: Int) = String(this, offset, length, Charsets.US_ASCII)
    private fun ByteArray.unsignedInt(offset: Int, littleEndian: Boolean = false): Long {
        var result = 0L
        repeat(4) { index ->
            val source = offset + if (littleEndian) 3 - index else index
            result = result * 256 + (this[source].toInt() and 255)
        }
        return result
    }

    private fun isJpeg(bytes: ByteArray) = bytes.size >= 4 && bytes[0].toInt() and 255 == 255 && bytes[1].toInt() and 255 == 0xD8
    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val DIRECTORY_NAME = "report_photos"
        const val MAX_SOURCE_BYTES = 16 * 1024 * 1024
        const val MAX_SOURCE_PIXELS = 64_000_000L
        private val DIRECTORY_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
        private val SUPPORTED_MIMES = setOf("image/jpeg", "image/png", "image/webp")
        private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
