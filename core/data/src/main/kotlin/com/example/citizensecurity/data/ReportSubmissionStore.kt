package com.example.citizensecurity.data

import android.content.Context
import android.util.AtomicFile
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportSubmissionJournal
import com.example.citizensecurity.domain.ReportSubmissionRequest
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Solicitud durable en almacenamiento privado. AtomicFile conserva el intento anterior si
 * falla una escritura; el bloqueo por archivo protege también instancias de Activity distintas
 * dentro del proceso. No abre SQLite, no guarda credenciales GPS ni crea un reporte.
 */
class ReportSubmissionStore(
    context: Context,
    fileName: String = FILE_NAME,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReportSubmissionJournal {
    private val file: AtomicFile
    private val lock: Any

    init {
        require(fileName.isNotBlank() && fileName.endsWith(".json") &&
            '/' !in fileName && '\\' !in fileName
        ) { "El diario necesita un nombre de archivo .json sin ruta." }
        val base = File(context.applicationContext.filesDir, fileName)
        file = AtomicFile(base)
        lock = locks.getOrPut(base.canonicalPath) { Any() }
    }

    override suspend fun read(): ReportSubmissionRequest? = withContext(ioDispatcher) {
        synchronized(lock) {
            ensureActive()
            readLocked().also { ensureActive() }
        }
    }

    override suspend fun write(request: ReportSubmissionRequest): Unit = withContext(ioDispatcher) {
        synchronized(lock) {
            ensureActive()
            // JSON y SQLite conservan cero sin signo. Comparar esa misma representación evita
            // rechazar un reintento válido después de reabrir una coordenada -0.0.
            val canonical = request.copy(draft = request.draft.copy(location = request.draft.location.copy(
                latitude = request.draft.location.latitude?.let { if (it == 0.0) 0.0 else it },
                longitude = request.draft.location.longitude?.let { if (it == 0.0) 0.0 else it },
            )))
            val previous = readLocked()
            if (previous != null) {
                check(previous == canonical) {
                    "Hay otra solicitud pendiente; recupera su resultado antes de reemplazarla."
                }
                return@synchronized
            }
            val bytes = encode(canonical).toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_BYTES) { "La solicitud supera el tamaño permitido." }
            ensureActive()
            val output = file.startWrite()
            try {
                output.write(bytes)
                // finishWrite sincroniza el archivo y confirma su sustitución atómica.
                file.finishWrite(output)
            } catch (failure: Exception) {
                file.failWrite(output)
                throw failure
            }
            ensureActive()
        }
    }

    override suspend fun clear(requestId: String): Unit = withContext(ioDispatcher) {
        synchronized(lock) {
            ensureActive()
            val previous = readLocked() ?: return@synchronized
            check(previous.requestId == requestId) {
                "El diario pertenece a otra solicitud y no puede descartarse."
            }
            file.delete()
            check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
                "No se pudo retirar la solicitud terminada."
            }
            ensureActive()
        }
    }

    private fun readLocked(): ReportSubmissionRequest? {
        val backup = File(file.baseFile.path + ".bak")
        // Una copia de recuperación también cuenta como solicitud; nunca la ignoramos.
        if (!file.baseFile.exists() && !backup.exists()) return null
        try {
            val bytes = file.openRead().use { input -> input.readBytesLimited() }
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
            return decode(JSONObject(text))
        } catch (failure: Exception) {
            throw IOException("No se pudo recuperar la solicitud pendiente; se conserva el archivo.", failure)
        }
    }

    private fun java.io.InputStream.readBytesLimited(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4_096)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_BYTES) throw IOException("El diario supera el tamaño permitido.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun encode(request: ReportSubmissionRequest): String = JSONObject().apply {
        put("schema", 1)
        put("requestId", request.requestId)
        putInstant("requestedAt", request.requestedAt)
        put("type", request.draft.type.name)
        put("priority", request.draft.priority.name)
        put("description", request.draft.description)
        putInstant("occurredAt", request.draft.occurredAt)
        put("reference", request.draft.location.reference)
        put("latitude", request.draft.location.latitude ?: JSONObject.NULL)
        put("longitude", request.draft.location.longitude ?: JSONObject.NULL)
    }.toString()

    private fun decode(json: JSONObject): ReportSubmissionRequest {
        check(json.longValue("schema") == 1L) { "Versión de diario desconocida." }
        val latitude = json.optionalCoordinate("latitude")
        val longitude = json.optionalCoordinate("longitude")
        require((latitude == null) == (longitude == null)) { "Coordenadas incompletas." }
        require(latitude == null || latitude in -90.0..90.0) { "Latitud inválida." }
        require(longitude == null || longitude in -180.0..180.0) { "Longitud inválida." }
        return ReportSubmissionRequest(
            requestId = json.stringValue("requestId"),
            requestedAt = json.instant("requestedAt"),
            draft = NewReport(
                type = IncidentType.valueOf(json.stringValue("type")),
                priority = Priority.valueOf(json.stringValue("priority")),
                description = json.stringValue("description"),
                occurredAt = json.instant("occurredAt"),
                location = ReportLocation(json.stringValue("reference"), latitude, longitude),
            ),
        )
    }

    private fun JSONObject.stringValue(key: String): String = get(key) as? String
        ?: throw IOException("Campo de texto inválido: $key")

    private fun JSONObject.longValue(key: String): Long {
        val value = get(key)
        if (value !is Number) throw IOException("Campo numérico inválido: $key")
        return value.toString().toLongOrNull() ?: throw IOException("Entero inválido: $key")
    }

    private fun JSONObject.optionalCoordinate(key: String): Double? {
        val value = get(key)
        if (value == JSONObject.NULL) return null
        if (value !is Number || !value.toDouble().isFinite()) {
            throw IOException("Coordenada inválida: $key")
        }
        return value.toDouble()
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

    companion object {
        const val FILE_NAME = "report_submission_v1.json"
        private const val MAX_BYTES = 65_536
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
