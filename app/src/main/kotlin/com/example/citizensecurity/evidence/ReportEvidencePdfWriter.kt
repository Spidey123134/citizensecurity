package com.example.citizensecurity.evidence

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.ReportStatus
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Explicit choices for a copy of a saved report; they do not alter its stored contents. */
data class ReportEvidenceOptions(
    val includeLocation: Boolean = true,
    val includePhoto: Boolean = false,
)

/**
 * A bounded, paginated local handout. It does not assign civil identity, create GPS evidence,
 * fetch history, contact an authority or claim that an incident has been verified.
 * The caller owns the output stream and must discard its partial file if this method fails.
 */
class ReportEvidencePdfWriter {
    suspend fun write(
        report: Report,
        options: ReportEvidenceOptions,
        photoBytes: ByteArray?,
        exportedAt: Instant,
        zoneId: ZoneId,
        output: OutputStream,
        checkAccess: () -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        val scope = this
        val checkpoint = { scope.ensureActive(); checkAccess(); scope.ensureActive() }
        checkpoint()
        validateReport(report)
        val dates = listOf(report.occurredAt, report.createdAt, exportedAt).map { instant ->
            checkpoint()
            try {
                DATE_FORMAT.withZone(zoneId).format(instant)
            } catch (failure: RuntimeException) {
                throw IllegalArgumentException("Una fecha de la ficha no se puede representar.", failure)
            }
        }
        val bitmap = if (options.includePhoto) {
            decodeCheckedPhoto(report.photo, photoBytes, checkpoint)
        } else null
        try {
            checkpoint()
            val pages = plan(report, options, dates, zoneId, bitmap, checkpoint)
            checkpoint()
            val document = PdfDocument()
            try {
                pages.forEachIndexed { index, drawings ->
                    checkpoint()
                    val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
                    try {
                        drawHeader(page.canvas)
                        drawings.forEach { drawing -> checkpoint(); drawing.draw(page.canvas) }
                        drawFooter(page.canvas, index + 1, pages.size)
                        checkpoint()
                    } finally {
                        // A started page must be finished even when a permission/cancellation check fails.
                        document.finishPage(page)
                    }
                }
                checkpoint()
                val checkedOutput = CheckedOutputStream(output, MAX_OUTPUT_BYTES, checkpoint)
                document.writeTo(checkedOutput)
                // PdfDocument's native bridge can consume a Java stream exception. Keep and
                // rethrow the first failure ourselves before the caller can commit any bytes.
                checkedOutput.checkCompleted()
                checkpoint()
                pages.size
            } finally {
                document.close()
            }
        } finally {
            bitmap?.recycle()
        }
    }

    private fun plan(
        report: Report,
        options: ReportEvidenceOptions,
        dates: List<String>,
        zoneId: ZoneId,
        bitmap: Bitmap?,
        checkpoint: () -> Unit,
    ): List<List<Drawing>> {
        val planner = PagePlanner(checkpoint)
        planner.text(LOCAL_NOTICE, bodyPaint(), after = 14f)
        planner.field("Folio", report.id)
        planner.field("Tipo de incidente", typeLabel(report.type))
        planner.field("Prioridad declarada por el autor", priorityLabel(report.priority))
        planner.field("Estado local", statusLabel(report.status))
        planner.field("Fecha del incidente", dates[0])
        planner.field("Fecha de guardado en el teléfono", dates[1])
        planner.field("Fecha de exportación", dates[2])
        planner.field("Zona horaria de esta ficha", zoneId.id)
        planner.field("Descripción registrada", report.description)
        if (options.includeLocation) {
            planner.field("Ubicación del incidente · referencia", report.location.reference.ifBlank { "Sin referencia registrada." })
            val latitude = report.location.latitude
            val longitude = report.location.longitude
            planner.field("Ubicación del incidente · coordenadas", if (latitude == null || longitude == null) {
                "Sin coordenadas registradas."
            } else {
                // Preserve the stored Double values rather than rounding or reading the phone location.
                "Latitud: $latitude\nLongitud: $longitude"
            })
        } else {
            planner.field("Punto y referencia del incidente", "No incluidos por elección al exportar. La descripción o fotografía pueden mencionar o mostrar el lugar.")
        }
        if (bitmap != null) {
            val photo = checkNotNull(report.photo)
            planner.field("Fotografía adjunta", "Copia JPEG normalizada por la app, sin EXIF; no es el archivo original.")
            planner.field("SHA-256 del JPEG normalizado", photo.sha256)
            planner.field("Dimensiones y tamaño del JPEG", "${photo.width} × ${photo.height} píxeles · ${photo.byteCount} bytes")
            planner.photo(bitmap)
        } else {
            planner.field("Fotografía adjunta", "No incluida por elección al exportar.")
        }
        planner.text("Conserva el folio para consultar este registro en el teléfono. Compartir esta ficha no envía el reporte a una autoridad ni cambia su estado local.", bodyPaint(), after = 0f)
        return planner.pages
    }

    private fun validateReport(report: Report) {
        require(runCatching { UUID.fromString(report.id).toString() == report.id }.getOrDefault(false)) {
            "La ficha necesita un folio completo y canónico."
        }
        requireValidText(report.description, 1_000, "descripción")
        require(report.description.isNotBlank()) { "La descripción guardada está vacía." }
        requireValidText(report.location.reference, 200, "referencia")
        val latitude = report.location.latitude
        val longitude = report.location.longitude
        require((latitude == null) == (longitude == null)) { "Las coordenadas guardadas están incompletas." }
        require(latitude == null || (latitude.isFinite() && latitude in -90.0..90.0)) { "La latitud guardada no es válida." }
        require(longitude == null || (longitude.isFinite() && longitude in -180.0..180.0)) { "La longitud guardada no es válida." }
    }

    private fun requireValidText(value: String, maximum: Int, label: String) {
        require(value.codePointCount(0, value.length) <= maximum) { "La $label supera el límite de la ficha." }
        var index = 0
        while (index < value.length) {
            val character = value[index]
            require(character != '\u0000' && (!Character.isISOControl(character) || character in "\n\r\t")) {
                "La $label contiene un carácter que no se puede representar."
            }
            if (Character.isHighSurrogate(character)) {
                require(index + 1 < value.length && Character.isLowSurrogate(value[index + 1])) {
                    "La $label contiene un carácter Unicode incompleto."
                }
                index += 2
            } else {
                require(!Character.isLowSurrogate(character)) { "La $label contiene un carácter Unicode incompleto." }
                index++
            }
        }
    }

    private fun decodeCheckedPhoto(metadata: ReportPhoto?, source: ByteArray?, checkpoint: () -> Unit): Bitmap {
        val photo = metadata ?: throw IOException("Se pidió incluir una fotografía, pero el reporte no tiene una.")
        val provided = source ?: throw IOException("La fotografía solicitada no está disponible.")
        checkpoint()
        if (provided.size.toLong() != photo.byteCount || provided.size.toLong() !in 1..ReportPhoto.MAX_BYTES) {
            throw IOException("El tamaño de la fotografía no coincide con el reporte.")
        }
        // The caller may own a mutable array; retain a private bounded copy for all checks/drawing.
        val bytes = provided.copyOf()
        checkpoint()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 255) }
        if (hash != photo.sha256) throw IOException("La huella de la fotografía no coincide con el reporte.")
        validateNormalizedJpeg(bytes, checkpoint)
        checkpoint()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outMimeType != "image/jpeg" || bounds.outWidth != photo.width || bounds.outHeight != photo.height) {
            throw IOException("Las dimensiones de la fotografía no coinciden con el reporte.")
        }
        checkpoint()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw IOException("No se pudo recuperar la fotografía solicitada.")
        try {
            checkpoint()
            if (bitmap.width != photo.width || bitmap.height != photo.height) {
                throw IOException("La fotografía no se pudo recuperar con sus dimensiones guardadas.")
            }
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        }
    }

    /** Validate the complete JPEG container and reject metadata segments in a normalized copy. */
    private fun validateNormalizedJpeg(bytes: ByteArray, checkpoint: () -> Unit) {
        if (bytes.size < 4 || unsigned(bytes[0]) != 0xFF || unsigned(bytes[1]) != 0xD8) {
            throw IOException("La fotografía solicitada no es un JPEG normalizado.")
        }
        var position = 2
        var scanned = false
        while (position < bytes.size) {
            checkpoint()
            if (unsigned(bytes[position++]) != 0xFF) break
            while (position < bytes.size && unsigned(bytes[position]) == 0xFF) position++
            if (position >= bytes.size) break
            val marker = unsigned(bytes[position++])
            if (marker == 0xD9) {
                if (scanned && position == bytes.size) return
                break
            }
            if (marker == 0xD8 || marker == 0 || marker in 0xD0..0xD7) break
            if (marker == 0x01) continue
            if (position + 2 > bytes.size) break
            val length = unsigned(bytes[position]) * 256 + unsigned(bytes[position + 1])
            if (length < 2 || position.toLong() + length > bytes.size) break
            if (marker == 0xE1 || marker == 0xFE) {
                throw IOException("La fotografía conserva metadatos; se necesita el JPEG normalizado sin EXIF.")
            }
            position += length
            if (marker == 0xDA) {
                scanned = true
                while (position < bytes.size) {
                    if (position % 8_192 == 0) checkpoint()
                    if (unsigned(bytes[position]) != 0xFF) { position++; continue }
                    val start = position
                    while (position < bytes.size && unsigned(bytes[position]) == 0xFF) position++
                    if (position >= bytes.size) break
                    val next = unsigned(bytes[position])
                    if (next == 0 || next in 0xD0..0xD7) { position++; continue }
                    position = start
                    break
                }
            }
        }
        throw IOException("La fotografía JPEG está incompleta o dañada.")
    }

    private class PagePlanner(private val checkpoint: () -> Unit) {
        val pages = mutableListOf<MutableList<Drawing>>(mutableListOf())
        private var y = BODY_TOP

        fun field(label: String, value: String) {
            room(38f)
            text(label, labelPaint(), after = 3f)
            text(value, bodyPaint(), after = 12f)
        }

        fun text(value: String, paint: TextPaint, after: Float) {
            checkpoint()
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, CONTENT_WIDTH.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(true)
                .setLineSpacing(2f, 1f)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
            var firstLine = 0
            while (firstLine < layout.lineCount) {
                checkpoint()
                val firstTop = layout.getLineTop(firstLine)
                val firstHeight = layout.getLineBottom(firstLine) - firstTop
                room(firstHeight.toFloat())
                var endLine = firstLine + 1
                while (endLine < layout.lineCount && layout.getLineBottom(endLine) - firstTop <= BODY_BOTTOM - y) endLine++
                val bottom = layout.getLineBottom(endLine - 1)
                val height = (bottom - firstTop).toFloat()
                pages.last().add(TextDrawing(layout, y, firstTop.toFloat(), height))
                y += height
                firstLine = endLine
                if (firstLine < layout.lineCount) nextPage()
            }
            y += after
        }

        fun photo(bitmap: Bitmap) {
            checkpoint()
            val desiredHeight = bitmap.height * minOf(CONTENT_WIDTH / bitmap.width, 285f / bitmap.height, 1f)
            // Keep room for the short closing paragraph instead of creating a page with one line.
            val closingSpace = 55f
            if (BODY_BOTTOM - y - closingSpace < minOf(100f, desiredHeight)) nextPage()
            val maximumHeight = minOf(285f, BODY_BOTTOM - y - closingSpace)
            val scale = minOf(CONTENT_WIDTH / bitmap.width, maximumHeight / bitmap.height, 1f)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            room(height + 12f)
            val left = MARGIN + (CONTENT_WIDTH - width) / 2f
            pages.last().add(PhotoDrawing(bitmap, RectF(left, y, left + width, y + height)))
            y += height + 14f
        }

        private fun room(height: Float) {
            if (y + height > BODY_BOTTOM) nextPage()
            if (y + height > BODY_BOTTOM) throw IOException("Un elemento de la ficha no cabe en una página.")
        }

        private fun nextPage() {
            checkpoint()
            if (pages.size >= MAX_PAGES) throw IOException("La ficha supera el límite de $MAX_PAGES páginas.")
            pages.add(mutableListOf())
            y = BODY_TOP
        }
    }

    private sealed interface Drawing { fun draw(canvas: Canvas) }

    private class TextDrawing(private val layout: StaticLayout, private val y: Float, private val offset: Float, private val height: Float) : Drawing {
        override fun draw(canvas: Canvas) {
            val save = canvas.save()
            try {
                canvas.clipRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + height)
                canvas.translate(MARGIN, y - offset)
                layout.draw(canvas)
            } finally { canvas.restoreToCount(save) }
        }
    }

    private class PhotoDrawing(private val bitmap: Bitmap, private val destination: RectF) : Drawing {
        override fun draw(canvas: Canvas) {
            canvas.drawBitmap(bitmap, null, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    private class CheckedOutputStream(private val delegate: OutputStream, private val limit: Long, private val checkpoint: () -> Unit) : OutputStream() {
        private var count = 0L
        private var firstFailure: Throwable? = null
        override fun write(value: Int) = checked {
            checkpoint()
            reserve(1)
            delegate.write(value)
            checkpoint()
        }
        override fun write(bytes: ByteArray, offset: Int, length: Int) = checked {
            if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
            var written = 0
            while (written < length) {
                checkpoint()
                val next = minOf(32_768, length - written)
                reserve(next)
                delegate.write(bytes, offset + written, next)
                written += next
                checkpoint()
            }
        }
        private inline fun checked(action: () -> Unit) {
            firstFailure?.let { throw it }
            try { action() } catch (failure: Throwable) {
                firstFailure = failure
                throw failure
            }
        }
        fun checkCompleted() {
            firstFailure?.let { throw it }
            if (count == 0L) throw IOException("La ficha no se pudo escribir.")
        }
        private fun reserve(length: Int) {
            if (length.toLong() > limit - count) throw IOException("La ficha supera el límite de 12 MiB.")
            count += length
        }
    }

    companion object {
        const val MAX_PAGES = 12
        const val MAX_OUTPUT_BYTES: Long = 12L * 1024 * 1024
        const val LOCAL_NOTICE = "Ficha local con datos del autor; no confirma recepción por una autoridad."
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 40f
        private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2
        private const val BODY_TOP = 100f
        private const val BODY_BOTTOM = 786f
        private val BLUE = Color.rgb(24, 67, 92)
        private val GREEN = Color.rgb(26, 110, 89)
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss XXX", Locale.forLanguageTag("es-MX"))

        private fun bodyPaint() = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 45, 52)
            textSize = 11f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private fun labelPaint() = bodyPaint().apply { color = GREEN; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
        private fun drawHeader(canvas: Canvas) {
            canvas.drawColor(Color.WHITE)
            val title = bodyPaint().apply { color = BLUE; textSize = 19f; typeface = Typeface.create("sans-serif", Typeface.BOLD) }
            canvas.drawText("CITIZENSECURITY", MARGIN, 51f, title)
            canvas.drawText("Ficha de un reporte guardado en este teléfono", MARGIN, 72f, bodyPaint())
            canvas.drawRect(MARGIN, 83f, MARGIN + CONTENT_WIDTH, 85f, Paint().apply { color = GREEN })
        }
        private fun drawFooter(canvas: Canvas, page: Int, total: Int) {
            val paint = bodyPaint().apply { textSize = 9f; color = BLUE }
            canvas.drawLine(MARGIN, 801f, MARGIN + CONTENT_WIDTH, 801f, Paint().apply { color = GREEN; strokeWidth = 0.7f })
            canvas.drawText("Ficha local · No acredita recepción oficial", MARGIN, 819f, paint)
            val text = "Página $page de $total"
            canvas.drawText(text, PAGE_WIDTH - MARGIN - paint.measureText(text), 819f, paint)
        }
        private fun unsigned(byte: Byte) = byte.toInt() and 255
        private fun typeLabel(type: IncidentType) = when (type) {
            IncidentType.EMERGENCY -> "Emergencia"
            IncidentType.THEFT -> "Robo"
            IncidentType.ACCIDENT -> "Accidente"
            IncidentType.FIRE -> "Incendio"
            IncidentType.RISK -> "Riesgo"
            IncidentType.OTHER -> "Otro"
        }
        private fun priorityLabel(priority: Priority) = when (priority) {
            Priority.LOW -> "Baja"
            Priority.MEDIUM -> "Media"
            Priority.HIGH -> "Alta"
        }
        private fun statusLabel(status: ReportStatus) = when (status) {
            ReportStatus.REPORTED -> "Guardado localmente"
            ReportStatus.IN_REVIEW -> "En revisión"
            ReportStatus.ATTENDED -> "Atendido"
            ReportStatus.CLOSED -> "Cerrado"
        }
    }
}
