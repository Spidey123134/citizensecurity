package com.example.citizensecurity.data

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import com.example.citizensecurity.domain.ReportAlertsCapacityException
import com.example.citizensecurity.domain.ReportAlertsConflictException
import com.example.citizensecurity.domain.ReportAlertsRepository
import com.example.citizensecurity.domain.ReportAlertsSnapshot
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
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
            readLocked(paths) { ensureActive() }.also {
                checkpoint()
                checkPaths(paths); requireNoSidecars(paths)
            }
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
                if (next == previous) return@synchronized previous.also {
                    checkpoint()
                    checkPaths(paths); requireNoSidecars(paths)
                }
                val bytes = ReportAlertsCodec.encode(next).toByteArray(Charsets.UTF_8)
                check(bytes.size <= ReportAlertsCodec.MAX_BYTES)
                checkpoint()
                checkPaths(paths); requireNoSidecars(paths)
                val previousIdentity = identityIfPresent(paths.atomic.baseFile)
                val output = paths.atomic.startWrite()
                var committed = false
                try {
                    output.write(bytes)
                    checkpoint()
                    checkPaths(paths)
                    checkpoint()
                    requireOwnedWrite(paths, output, previousIdentity)
                    paths.atomic.finishWrite(output)
                    committed = true
                } catch (failure: Throwable) {
                    if (!committed) rollbackOwnedWrite(paths, output, previousIdentity)
                    throw failure
                }
                // finishWrite can log an I/O failure without throwing; verify its exact receipt.
                val receipt = readBytesLocked(paths) { ensureActive() }
                if (receipt == null || !receipt.contentEquals(bytes) || decode(receipt) != next) {
                    throw IOException("No se pudo confirmar el guardado de avisos. Vuelve a consultar.")
                }
                checkpoint()
                checkPaths(paths); requireNoSidecars(paths)
                next
            }
        }
    }

    private fun next(previous: ReportAlertsSnapshot, enabled: Boolean, presented: Set<String>): ReportAlertsSnapshot {
        val revision = generateSequence { UUID.randomUUID().toString() }.first { it != previous.revision }
        return ReportAlertsSnapshot(revision, enabled, presented)
    }

    private fun paths(): Paths {
        val privateDirectory = context.filesDir
        if (Files.isSymbolicLink(privateDirectory.toPath())) throw IOException("El espacio privado no es una carpeta directa.")
        val parent = privateDirectory.canonicalFile
        if (!parent.isDirectory) throw IOException("No se encontró el espacio privado de avisos.")
        val base = File(parent, filename)
        return Paths(parent, AtomicFile(base)).also(::checkPaths)
    }

    private fun checkPaths(paths: Paths) {
        if (Files.isSymbolicLink(paths.parent.toPath()) || !paths.parent.isDirectory ||
            paths.parent.canonicalFile != paths.parent.absoluteFile) {
            throw IOException("El espacio privado de avisos no es válido.")
        }
        paths.files.forEach { file ->
            if (Files.isSymbolicLink(file.toPath()) || file.canonicalFile != file.absoluteFile ||
                file.parentFile != paths.parent ||
                (Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
                    !Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))) {
                throw IOException("El archivo de avisos no pertenece al espacio privado.")
            }
        }
    }

    private fun requireNoSidecars(paths: Paths) {
        if (paths.files.drop(1).any { Files.exists(it.toPath(), LinkOption.NOFOLLOW_LINKS) }) {
            throw IOException("Hay un guardado de avisos sin confirmar; sus archivos se conservan.")
        }
    }

    private fun readLocked(paths: Paths, checkCancelled: () -> Unit): ReportAlertsSnapshot =
        readBytesLocked(paths, checkCancelled)?.let(::decode) ?: ReportAlertsSnapshot(null)

    /** AtomicFile.openRead may restore .bak or delete .new; a read must never change either. */
    private fun readBytesLocked(paths: Paths, checkCancelled: () -> Unit): ByteArray? {
        checkPaths(paths)
        requireNoSidecars(paths)
        val base = paths.atomic.baseFile
        if (!Files.exists(base.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        return try {
            Files.newByteChannel(base.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteBuffer.allocate(4_096)
                while (true) {
                    checkCancelled()
                    buffer.clear()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > ReportAlertsCodec.MAX_BYTES - output.size()) throw IOException("El archivo de avisos supera el límite.")
                    output.write(buffer.array(), 0, count)
                }
                checkCancelled()
                checkPaths(paths); requireNoSidecars(paths)
                output.toByteArray()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw IOException("No se pudieron leer los avisos; el archivo se conserva.", failure)
        }
    }

    private fun decode(bytes: ByteArray): ReportAlertsSnapshot = try {
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        ReportAlertsCodec.decode(text)
    } catch (failure: Exception) {
        throw IOException("No se pudieron leer los avisos; el archivo se conserva.", failure)
    }

    private fun identityIfPresent(file: File): FileIdentity? =
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) null else Os.lstat(file.path).let {
            FileIdentity(it.st_dev, it.st_ino)
        }

    /** Recognize both modern .new staging and the older base/.bak AtomicFile layout. */
    private fun requireOwnedWrite(paths: Paths, output: FileOutputStream, previous: FileIdentity?): File {
        checkPaths(paths)
        val opened = Os.fstat(output.fd).let { FileIdentity(it.st_dev, it.st_ino) }
        val base = identityIfPresent(paths.files[0])
        val staging = identityIfPresent(paths.files[1])
        val backup = identityIfPresent(paths.files[2])
        return when {
            staging == opened && base == previous && backup == null -> paths.files[1]
            base == opened && staging == null && backup == previous -> paths.files[0]
            else -> throw IOException("El guardado de avisos cambió durante la escritura; sus archivos se conservan.")
        }
    }

    /** Never delegate unknown sidecars to AtomicFile.failWrite, which may remove or restore them. */
    private fun rollbackOwnedWrite(paths: Paths, output: FileOutputStream, previous: FileIdentity?) {
        try {
            val owned = requireOwnedWrite(paths, output, previous)
            // A legacy backup is the previous receipt, not this descriptor's file. Preserve the
            // uncertain pair rather than restoring/deleting that receipt automatically.
            if (owned == paths.files[0] && previous != null) { output.close(); return }
            val opened = Os.fstat(output.fd).let { FileIdentity(it.st_dev, it.st_ino) }
            output.close()
            checkPaths(paths)
            if (identityIfPresent(owned) == opened) Files.delete(owned.toPath())
        } catch (_: Exception) { runCatching { output.close() } }
    }

    private fun lock(paths: Paths): Any = locks.getOrPut(paths.atomic.baseFile.absolutePath) { Any() }
    private data class FileIdentity(val device: Long, val inode: Long)
    private data class Paths(val parent: File, val atomic: AtomicFile) {
        val files = listOf(atomic.baseFile, File(atomic.baseFile.path + ".new"), File(atomic.baseFile.path + ".bak"))
    }

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
