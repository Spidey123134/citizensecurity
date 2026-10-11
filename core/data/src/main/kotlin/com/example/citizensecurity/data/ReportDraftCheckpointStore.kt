package com.example.citizensecurity.data

import android.content.Context
import android.util.AtomicFile
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportDraftCheckpoint
import com.example.citizensecurity.domain.ReportDraftCheckpointJournal
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.ReportValidator
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Copia durable privada; no conserva permisos, ubicaciones autorizadas ni URI externas. */
class ReportDraftCheckpointStore(
    context: Context,
    fileName: String = FILE_NAME,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReportDraftCheckpointJournal {
    private val file: AtomicFile
    private val lock: Any
    private val validator = ReportValidator()

    init {
        require(fileName.isNotBlank() && fileName.endsWith(".json") &&
            '/' !in fileName && '\\' !in fileName
        ) { "El borrador necesita un nombre de archivo .json sin ruta." }
        val base = File(context.applicationContext.filesDir, fileName)
        file = AtomicFile(base)
        lock = locks.getOrPut(base.canonicalPath) { Any() }
    }

    override suspend fun read(): ReportDraftCheckpoint? = withContext(ioDispatcher) {
        synchronized(lock) {
            ensureActive()
            readLocked().also { ensureActive() }
        }
    }

    override suspend fun write(
        checkpoint: ReportDraftCheckpoint,
        expectedId: String?,
        expectedSubmittedFolio: String?,
    ): Unit =
        withContext(ioDispatcher) {
            synchronized(lock) {
                ensureActive()
                require(expectedId == null || expectedId.isCanonicalUuid()) { "Revisión esperada inválida." }
                require(expectedSubmittedFolio == null || expectedSubmittedFolio.isCanonicalUuid()) {
                    "Folio esperado inválido."
                }
                val canonical = checkpoint.canonicalCoordinates()
                validate(canonical)
                val previous = readLocked()
                check(previous?.id == expectedId && previous?.submittedFolio == expectedSubmittedFolio) {
                    "El borrador cambió en otra pantalla; vuelve a leerlo antes de reemplazarlo."
                }
                // La respuesta perdida se resuelve por lectura; incluso un retry idéntico
                // necesita conocer la revisión actual antes de acceder a esta escritura.
                if (previous == canonical) return@synchronized
                check(previous == null || previous.id != canonical.id) {
                    "Cambiar el borrador necesita una revisión nueva."
                }
                require(canonical.submittedFolio == null) {
                    "El folio solo puede marcarse después de confirmar el guardado del reporte."
                }
                persistLocked(canonical) { ensureActive() }
                ensureActive()
            }
        }

    override suspend fun markSubmitted(id: String, folio: String): Unit = withContext(ioDispatcher) {
        synchronized(lock) {
            ensureActive()
            require(id.isCanonicalUuid() && folio.isCanonicalUuid()) { "Revisión o folio inválidos." }
            val previous = readLocked()
            check(previous != null && previous.id == id) {
                "La revisión indicada ya no es el borrador conservado."
            }
            if (previous.submittedFolio == folio) return@synchronized
            check(previous.submittedFolio == null) { "El borrador ya pertenece a otro folio." }
            persistLocked(previous.copy(submittedFolio = folio)) { ensureActive() }
            ensureActive()
        }
    }

    private fun validate(checkpoint: ReportDraftCheckpoint) {
        // La fecha de captura forma parte de la copia. No cambia al recuperar días después.
        val errors = validator.validate(checkpoint.draft, checkpoint.savedAt)
        if (!errors.isValid) throw InvalidReportException(errors)
    }

    private fun persistLocked(checkpoint: ReportDraftCheckpoint, ensureStillActive: () -> Unit) {
        val bytes = encode(checkpoint).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "El borrador supera el tamaño permitido." }
        ensureStillActive()
        val output = file.startWrite()
        try {
            output.write(bytes)
            ensureStillActive()
            file.finishWrite(output)
        } catch (failure: Exception) {
            file.failWrite(output)
            throw failure
        }
    }

    private fun readLocked(): ReportDraftCheckpoint? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        try {
            val bytes = file.openRead().use { it.readBoundedBytes() }
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
            val checkpoint = decode(JSONObject(text))
            validate(checkpoint)
            return checkpoint
        } catch (failure: Exception) {
            throw IOException("No se pudo recuperar el borrador local; se conserva el archivo.", failure)
        }
    }

    private fun InputStream.readBoundedBytes(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4_096)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_BYTES) throw IOException("El borrador supera el tamaño permitido.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun encode(checkpoint: ReportDraftCheckpoint): String = JSONObject().apply {
        put("schema", 1)
        put("id", checkpoint.id)
        putInstant("savedAt", checkpoint.savedAt)
        put("submittedFolio", checkpoint.submittedFolio ?: JSONObject.NULL)
        put("type", checkpoint.draft.type.name)
        put("priority", checkpoint.draft.priority.name)
        put("description", checkpoint.draft.description)
        putInstant("occurredAt", checkpoint.draft.occurredAt)
        put("reference", checkpoint.draft.location.reference)
        put("latitude", checkpoint.draft.location.latitude ?: JSONObject.NULL)
        put("longitude", checkpoint.draft.location.longitude ?: JSONObject.NULL)
        put("photo", checkpoint.draft.photo?.let { photo -> JSONObject().apply {
            put("id", photo.id)
            put("sha256", photo.sha256)
            put("byteCount", photo.byteCount)
            put("width", photo.width)
            put("height", photo.height)
        } } ?: JSONObject.NULL)
    }.toString()

    private fun decode(json: JSONObject): ReportDraftCheckpoint {
        check(json.longValue("schema") == 1L) { "Versión del borrador desconocida." }
        val latitude = json.coordinate("latitude")
        val longitude = json.coordinate("longitude")
        require((latitude == null) == (longitude == null)) { "Coordenadas incompletas." }
        return ReportDraftCheckpoint(
            id = json.stringValue("id"),
            savedAt = json.instant("savedAt"),
            submittedFolio = if (json.get("submittedFolio") == JSONObject.NULL) null
                else json.stringValue("submittedFolio"),
            draft = NewReport(
                type = IncidentType.valueOf(json.stringValue("type")),
                priority = Priority.valueOf(json.stringValue("priority")),
                description = json.stringValue("description"),
                occurredAt = json.instant("occurredAt"),
                location = ReportLocation(json.stringValue("reference"), latitude, longitude),
                photo = json.photo(),
            ),
        )
    }

    private fun JSONObject.photo(): ReportPhoto? {
        if (get("photo") == JSONObject.NULL) return null
        val photo = get("photo") as? JSONObject ?: throw IOException("Fotografía inválida.")
        val width = photo.longValue("width")
        val height = photo.longValue("height")
        require(width in 1..ReportPhoto.MAX_DIMENSION.toLong() && height in 1..ReportPhoto.MAX_DIMENSION.toLong()) {
            "Dimensiones inválidas en la fotografía del borrador."
        }
        return ReportPhoto(
            id = photo.stringValue("id"), sha256 = photo.stringValue("sha256"),
            byteCount = photo.longValue("byteCount"), width = width.toInt(), height = height.toInt(),
        )
    }

    private fun JSONObject.stringValue(key: String): String = get(key) as? String
        ?: throw IOException("Campo de texto inválido: $key")

    private fun JSONObject.longValue(key: String): Long {
        val value = get(key)
        if (value !is Number) throw IOException("Campo numérico inválido: $key")
        return value.toString().toLongOrNull() ?: throw IOException("Entero inválido: $key")
    }

    private fun JSONObject.coordinate(key: String): Double? {
        val value = get(key)
        if (value == JSONObject.NULL) return null
        if (value !is Number || !value.toDouble().isFinite()) throw IOException("Coordenada inválida: $key")
        return value.toDouble().let { if (it == 0.0) 0.0 else it }
    }

    private fun JSONObject.putInstant(key: String, value: Instant) {
        put("${key}Seconds", value.epochSecond)
        put("${key}Nanos", value.nano)
    }

    private fun JSONObject.instant(key: String): Instant {
        val nanos = longValue("${key}Nanos")
        require(nanos in 0..999_999_999L) { "Nanosegundos inválidos." }
        return Instant.ofEpochSecond(longValue("${key}Seconds"), nanos)
    }

    private fun ReportDraftCheckpoint.canonicalCoordinates(): ReportDraftCheckpoint = copy(
        draft = draft.copy(location = draft.location.copy(
            latitude = draft.location.latitude?.let { if (it == 0.0) 0.0 else it },
            longitude = draft.location.longitude?.let { if (it == 0.0) 0.0 else it },
        )),
    )

    private fun String.isCanonicalUuid(): Boolean =
        runCatching { UUID.fromString(this).toString() == this }.getOrDefault(false)

    companion object {
        const val FILE_NAME = "report_draft_v1.json"
        private const val MAX_BYTES = 16_384
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
