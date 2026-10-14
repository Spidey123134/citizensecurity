package com.example.citizensecurity.evidence

import android.content.Context
import android.util.AtomicFile
import com.example.citizensecurity.account.AccountSessionState
import com.example.citizensecurity.account.ReportWorkspace
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ReportEvidenceRequest(
    val reportId: String,
    val options: ReportEvidenceOptions = ReportEvidenceOptions(),
    val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    init { require(canonicalId(reportId)) { "Se necesita el folio completo del reporte." } }
}

data class ReportEvidenceArtifact(
    val token: String,
    val reportId: String,
    val createdAt: Instant,
    val byteCount: Long,
    val sha256: String,
    val pageCount: Int,
)

fun interface ReportEvidenceProducer {
    suspend fun prepare(request: ReportEvidenceRequest, checkCurrent: () -> Unit): ReportEvidenceArtifact
}

/** Capacity failure is recoverable by clearing only the Android cache, never account/report data. */
class ReportEvidenceQuotaException : IOException("El espacio temporal de fichas está lleno.")

internal fun canonicalId(value: String) = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)

internal fun validateEvidenceArtifact(artifact: ReportEvidenceArtifact, request: ReportEvidenceRequest) {
    require(canonicalId(artifact.token) && artifact.reportId == request.reportId)
    require(artifact.byteCount in 1..ReportEvidencePdfWriter.MAX_OUTPUT_BYTES)
    require(Regex("[0-9a-f]{64}").matches(artifact.sha256))
    require(artifact.pageCount in 1..ReportEvidencePdfWriter.MAX_PAGES)
}

/** A fresh copy from one immutable owner. Failures never remove reports, photos or older copies. */
class ReportEvidenceExport(
    context: Context,
    private val workspace: ReportWorkspace,
    private val writer: ReportEvidencePdfWriter = ReportEvidencePdfWriter(),
    private val clock: Clock = Clock.systemUTC(),
) : ReportEvidenceProducer {
    private val parent = context.applicationContext.cacheDir.canonicalFile
    private val directory = File(parent, DIRECTORY_NAME)
    private val mutex = locks.getOrPut(directory.absolutePath) { Mutex() }

    override suspend fun prepare(request: ReportEvidenceRequest, checkCurrent: () -> Unit): ReportEvidenceArtifact =
        withContext(Dispatchers.IO) {
            val scope = this
            val checkpoint = { scope.ensureActive(); workspace.checkActive(); checkCurrent(); scope.ensureActive() }
            checkpoint()
            mutex.withLock {
                checkpoint()
                // Serialize the photo/PDF allocations as well as the quota and final write.
                // Do not export the potentially old Report currently displayed in the screen.
                val report = workspace.repository.findById(request.reportId)
                    ?: throw IOException("Este reporte ya no está disponible en el teléfono.")
                checkpoint()
                require(report.id == request.reportId) { "El folio consultado no corresponde al solicitado." }
                val photoBytes = if (request.options.includePhoto) {
                    workspace.readPhoto(report.photo ?: throw IOException("Este reporte no tiene fotografía."))
                } else null
                checkpoint()
                ensureDirectory()
                val existing = directory.listFiles() ?: throw IOException("No se pudo consultar el espacio temporal.")
                var occupied = 0L
                var count = 0
                existing.forEach { file ->
                    checkpoint()
                    if (OWNED_NAME.matches(file.name)) {
                        requireOwnedFile(file)
                        val length = file.length()
                        if (length > MAX_CACHE_BYTES - occupied) throw ReportEvidenceQuotaException()
                        occupied += length
                        count++
                        if (occupied > MAX_CACHE_BYTES) throw ReportEvidenceQuotaException()
                    }
                }
                val retainedTokens = existing.filter { it.name.endsWith(".pdf") }.map { it.name.removeSuffix(".pdf") }.toSet()
                receipts.entries.removeIf { it.value.directoryPath == directory.absolutePath && it.key !in retainedTokens }
                if (count >= MAX_CACHE_FILES || occupied >= MAX_CACHE_BYTES) {
                    throw ReportEvidenceQuotaException()
                }
                val token = generateSequence { UUID.randomUUID().toString() }.first { id ->
                    listOf("", ".new", ".bak").none { File(directory, "$id.pdf$it").exists() }
                }
                val base = ownedFile(token)
                val atomic = AtomicFile(base)
                val exportedAt = clock.instant()
                checkpoint()
                val raw = atomic.startWrite()
                var committed = false
                try {
                    val output = DigestLimitedOutput(raw, MAX_CACHE_BYTES - occupied, checkpoint)
                    val pages = writer.write(report, request.options, photoBytes, exportedAt,
                        request.zoneId, output, checkpoint)
                    checkpoint()
                    val artifact = ReportEvidenceArtifact(token, report.id, exportedAt,
                        output.byteCount, output.hash(), pages)
                    validateEvidenceArtifact(artifact, request)
                    checkpoint()
                    // The callback also checks the generation: a late folio cannot finish this file.
                    atomic.finishWrite(raw)
                    committed = true
                    checkpoint()
                    verifyFile(artifact, checkpoint)
                    checkpoint()
                    receipts[token] = IssuedReceipt(workspace.session, artifact, directory.absolutePath)
                    checkpoint()
                    artifact
                } catch (failure: Throwable) {
                    if (!committed) atomic.failWrite(raw)
                    throw failure
                }
            }
        }

    /** Blocking integrity check; call on IO immediately before granting a temporary content URI. */
    fun fileForSharing(artifact: ReportEvidenceArtifact, checkCurrent: () -> Unit = {}): File {
        val checkpoint = { workspace.checkActive(); checkCurrent() }
        checkpoint()
        val issued = receipts[artifact.token]
        if (issued == null || issued.session !== workspace.session || issued.artifact != artifact ||
            issued.directoryPath != directory.absolutePath) {
            throw IOException("Esta ficha no corresponde a la sesión vigente.")
        }
        val file = verifyFile(artifact, checkpoint)
        checkpoint()
        return file
    }

    private fun verifyFile(artifact: ReportEvidenceArtifact, checkpoint: () -> Unit): File {
        require(canonicalId(artifact.reportId))
        validateEvidenceArtifact(artifact, ReportEvidenceRequest(artifact.reportId))
        val file = ownedFile(artifact.token)
        checkDirectory()
        requireOwnedFile(file)
        if (file.length() != artifact.byteCount) throw IOException("La ficha temporal ya no está disponible.")
        val digest = MessageDigest.getInstance("SHA-256")
        var read = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(8_192)
            while (true) {
                checkpoint()
                val size = input.read(buffer)
                if (size < 0) break
                read += size
                if (read > artifact.byteCount) throw IOException("La ficha temporal cambió de tamaño.")
                digest.update(buffer, 0, size)
            }
        }
        if (read != artifact.byteCount || hex(digest.digest()) != artifact.sha256) {
            throw IOException("La ficha temporal no coincide con su huella.")
        }
        checkpoint()
        return file
    }

    private fun ownedFile(token: String): File {
        require(canonicalId(token))
        return File(directory, "$token.pdf").also {
            require(it.canonicalFile == it.absoluteFile && it.canonicalFile.parentFile == directory.absoluteFile)
        }
    }

    private fun ensureDirectory() {
        require(directory.canonicalFile == directory.absoluteFile && directory.parentFile == parent)
        if (!directory.exists() && !directory.mkdir()) throw IOException("No se pudo preparar la ficha temporal.")
        checkDirectory()
    }

    private fun checkDirectory() {
        if (!directory.isDirectory || directory.canonicalFile != directory.absoluteFile || directory.parentFile != parent) {
            throw IOException("El espacio temporal de fichas no está disponible.")
        }
    }

    private fun requireOwnedFile(file: File) {
        if (!file.isFile || file.canonicalFile != file.absoluteFile || file.canonicalFile.parentFile != directory.absoluteFile) {
            throw IOException("La ficha temporal no está disponible.")
        }
    }

    private class DigestLimitedOutput(
        private val target: OutputStream,
        private val limit: Long,
        private val checkpoint: () -> Unit,
    ) : OutputStream() {
        private val digest = MessageDigest.getInstance("SHA-256")
        var byteCount = 0L
            private set
        override fun write(value: Int) = write(byteArrayOf(value.toByte()), 0, 1)
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            checkpoint()
            if (length < 0 || offset < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
            if (length.toLong() > limit - byteCount) throw ReportEvidenceQuotaException()
            target.write(bytes, offset, length)
            digest.update(bytes, offset, length)
            byteCount += length
            checkpoint()
        }
        fun hash(): String = hex(digest.digest())
    }

    companion object {
        const val DIRECTORY_NAME = "evidence_shared"
        const val MAX_CACHE_BYTES = 64L * 1024 * 1024
        const val MAX_CACHE_FILES = 32
        private val OWNED_NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf(?:\\.new|\\.bak)?")
        private val locks = ConcurrentHashMap<String, Mutex>()
        private val receipts = ConcurrentHashMap<String, IssuedReceipt>()
        private class IssuedReceipt(val session: AccountSessionState, val artifact: ReportEvidenceArtifact, val directoryPath: String)
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
