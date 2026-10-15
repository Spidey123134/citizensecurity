package com.example.citizensecurity.data

import android.content.Context
import android.util.AtomicFile
import com.example.citizensecurity.domain.ReportAlertsCapacityException
import com.example.citizensecurity.domain.ReportAlertsConflictException
import com.example.citizensecurity.domain.ReportAlertsRepository
import com.example.citizensecurity.domain.ReportAlertsSnapshot
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Captured-owner preferences, independent of reports/accounts schemas and administrative roles. */
class ReportAlertsStore(
    context: Context,
    ownerAccountId: String?,
    private val checkAccess: () -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    filenameOverride: String? = null,
) : ReportAlertsRepository {
    private val context = context.applicationContext
    private val filename = fileName(ownerAccountId).let { normal ->
        (filenameOverride ?: normal).also { selected ->
            require(selected.matches(Regex("report_alerts_[a-z0-9_-]{1,128}\\.json"))) {
                "Los avisos necesitan un archivo privado sin ruta."
            }
        }
    }

    override suspend fun read(): ReportAlertsSnapshot = withContext(ioDispatcher) {
        val checkpoint = { ensureActive(); checkAccess(); ensureActive() }
        checkpoint()
        val paths = paths()
        synchronized(lock(paths)) {
            checkpoint()
            readLocked(paths) { ensureActive() }.also { checkpoint() }
        }
    }

    override suspend fun setEnabled(enabled: Boolean, expectedRevision: String?): ReportAlertsSnapshot =
        update(expectedRevision) { previous ->
            if (previous.enabled == enabled) previous else next(previous, enabled, previous.presented)
        }

    override suspend fun recordPresented(operationIds: Set<String>, expectedRevision: String?): ReportAlertsSnapshot {
        val captured = LinkedHashSet(operationIds)
        if (captured.size > ReportAlertsSnapshot.MAX_PRESENTED) throw ReportAlertsCapacityException()
        // Validation does not turn an operation ID into permission or a delivery/read receipt.
        captured.forEach(::requireCanonicalId)
        return update(expectedRevision) { previous ->
            if (previous.presented.containsAll(captured)) previous else {
                val combined = LinkedHashSet(previous.presented).apply { addAll(captured) }
                if (combined.size > ReportAlertsSnapshot.MAX_PRESENTED) throw ReportAlertsCapacityException()
                next(previous, previous.enabled, combined)
            }
        }
    }

    private suspend fun update(expectedRevision: String?, transform: (ReportAlertsSnapshot) -> ReportAlertsSnapshot): ReportAlertsSnapshot {
        ReportAlertsSnapshot(expectedRevision)
        return withContext(ioDispatcher) {
            val checkpoint = { ensureActive(); checkAccess(); ensureActive() }
            checkpoint()
            val paths = paths()
            synchronized(lock(paths)) {
                checkpoint()
                val previous = readLocked(paths) { ensureActive() }
                checkpoint()
                if (previous.revision != expectedRevision) throw ReportAlertsConflictException()
                val next = transform(previous)
                if (next == previous) return@synchronized previous.also { checkpoint() }
                val bytes = ReportAlertsCodec.encode(next).toByteArray(Charsets.UTF_8)
                check(bytes.size <= ReportAlertsCodec.MAX_BYTES)
                checkPaths(paths)
                checkpoint()
                val output = paths.atomic.startWrite()
                var committed = false
                try {
                    output.write(bytes)
                    checkpoint()
                    checkPaths(paths)
                    checkpoint()
                    paths.atomic.finishWrite(output)
                    committed = true
                } catch (failure: Throwable) {
                    if (!committed) paths.atomic.failWrite(output)
                    throw failure
                }
                // finishWrite can log an I/O failure without throwing; verify its exact receipt.
                val confirmed = readLocked(paths) { ensureActive() }
                if (confirmed != next) throw IOException("No se pudo confirmar el guardado de avisos. Vuelve a consultar.")
                checkpoint()
                next
            }
        }
    }

    private fun next(previous: ReportAlertsSnapshot, enabled: Boolean, presented: Set<String>): ReportAlertsSnapshot {
        val revision = generateSequence { UUID.randomUUID().toString() }.first { it != previous.revision }
        return ReportAlertsSnapshot(revision, enabled, presented)
    }

    private fun paths(): Paths {
        val parent = context.filesDir.canonicalFile
        val base = File(parent, filename)
        return Paths(parent, AtomicFile(base)).also(::checkPaths)
    }

    private fun checkPaths(paths: Paths) {
        listOf(paths.atomic.baseFile, File(paths.atomic.baseFile.path + ".new"), File(paths.atomic.baseFile.path + ".bak"))
            .forEach { file ->
                if (Files.isSymbolicLink(file.toPath()) || file.canonicalFile != file.absoluteFile ||
                    file.parentFile != paths.parent || (file.exists() && !file.isFile)) {
                    throw IOException("El archivo de avisos no pertenece al espacio privado.")
                }
            }
    }

    private fun readLocked(paths: Paths, checkCancelled: () -> Unit): ReportAlertsSnapshot {
        checkPaths(paths)
        val base = paths.atomic.baseFile
        if (!base.exists() && !File(base.path + ".bak").exists()) {
            if (File(base.path + ".new").exists()) throw IOException("Hay un guardado de avisos sin confirmar; el archivo se conserva.")
            return ReportAlertsSnapshot(null)
        }
        try {
            val bytes = paths.atomic.openRead().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4_096)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > ReportAlertsCodec.MAX_BYTES - output.size()) throw IOException("El archivo de avisos supera el límite.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            checkCancelled()
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            return ReportAlertsCodec.decode(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw IOException("No se pudieron leer los avisos; el archivo se conserva.", failure)
        }
    }

    private fun lock(paths: Paths): Any = locks.getOrPut(paths.atomic.baseFile.absolutePath) { Any() }
    private data class Paths(val parent: File, val atomic: AtomicFile)

    companion object {
        fun fileName(ownerAccountId: String?): String {
            ownerAccountId?.let(::requireCanonicalId)
            return "report_alerts_${ownerAccountId ?: "academic"}.json"
        }
        private fun requireCanonicalId(id: String) {
            require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
                "Los avisos necesitan identidades UUID completas y canónicas."
            }
        }
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
