package com.example.citizensecurity.data

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import com.example.citizensecurity.domain.ReportStatusChangeJournal
import com.example.citizensecurity.domain.ReportStatusChangeRequest
import com.example.citizensecurity.domain.ReportStatusChangeScope
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One private owner/actor journal. It retains intent, never administrative permission. */
class ReportStatusChangeStore(
    context: Context,
    private val scope: ReportStatusChangeScope,
    private val checkAccess: () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReportStatusChangeJournal {
    private val context = context.applicationContext
    private val filename = fileName(scope)

    override suspend fun read(): ReportStatusChangeRequest? = withContext(ioDispatcher) {
        val paths = paths()
        val coordination = coordination(paths)
        synchronized(coordination.fileLock) {
            ensureActive(); checkAccess(); ensureActive()
            readLocked(paths).also {
                ensureActive(); checkAccess(); ensureActive()
                checkPaths(paths); requireNoSidecars(paths)
            }
        }
    }

    override suspend fun retain(request: ReportStatusChangeRequest): Unit {
        requireScope(request)
        val bytes = ReportStatusChangeCodec.encode(request)
        withContext(ioDispatcher) {
            val paths = paths()
            val coordination = coordination(paths)
            synchronized(coordination.fileLock) {
                ensureActive(); checkAccess(); ensureActive()
                val previous = readLocked(paths)
                if (previous != null) {
                    check(previous == request) { "Hay otra solicitud pendiente; recupera su resultado antes de reemplazarla." }
                    ensureActive(); checkAccess(); ensureActive()
                    checkPaths(paths); requireNoSidecars(paths)
                    return@synchronized
                }
                ensureActive(); checkAccess(); ensureActive()
                checkPaths(paths); requireNoSidecars(paths)
                val output = paths.atomic.startWrite()
                var committed = false
                try {
                    output.write(bytes)
                    ensureActive(); checkAccess(); ensureActive()
                    checkPaths(paths)
                    paths.atomic.finishWrite(output)
                    committed = true
                } catch (failure: Throwable) {
                    if (!committed) rollbackOwnedWrite(paths, output)
                    throw failure
                }
                // AtomicFile may log a failed rename without throwing. Confirm the whole body.
                val receipt = readBytesLocked(paths)
                check(receipt != null && receipt.contentEquals(bytes) && decode(receipt) == request) {
                    "No se pudo confirmar la solicitud de cambio de estado."
                }
                ensureActive(); checkAccess(); ensureActive()
                checkPaths(paths); requireNoSidecars(paths)
            }
        }
    }

    override suspend fun clear(expected: ReportStatusChangeRequest): Unit {
        requireScope(expected)
        withContext(ioDispatcher) {
            val paths = paths()
            val coordination = coordination(paths)
            synchronized(coordination.fileLock) {
                ensureActive(); checkAccess(); ensureActive()
                val previous = readLocked(paths)
                if (previous == null) {
                    ensureActive(); checkAccess(); ensureActive()
                    checkPaths(paths); requireNoSidecars(paths)
                    return@synchronized
                }
                check(previous == expected) { "La solicitud pendiente cambió y no puede retirarse." }
                ensureActive(); checkAccess(); ensureActive()
                checkPaths(paths); requireNoSidecars(paths)
                // Unlink only this exact base; AtomicFile.delete would also remove unknown sidecars.
                Files.delete(paths.atomic.baseFile.toPath())
                check(readBytesLocked(paths) == null) { "No se pudo retirar la solicitud confirmada." }
                ensureActive(); checkAccess(); ensureActive()
                checkPaths(paths); requireNoSidecars(paths)
            }
        }
    }

    override suspend fun <T> exclusive(block: suspend () -> T): T = withContext(ioDispatcher) {
        val paths = paths()
        val coordination = coordination(paths)
        coordination.workflow.withLock {
            // Structured children are joined before releasing this lease. A cancelled caller cannot
            // let another recovery race a blocking/non-cooperative backend that is still executing.
            coroutineScope {
                ensureActive(); checkAccess(); ensureActive()
                synchronized(coordination.fileLock) { readLocked(paths) }
                block().also {
                    ensureActive(); checkAccess(); ensureActive()
                    synchronized(coordination.fileLock) { readLocked(paths) }
                }
            }
        }
    }

    private fun requireScope(request: ReportStatusChangeRequest) {
        require(request.scope == scope) { "La solicitud pertenece a otro espacio o actor." }
    }

    private fun readLocked(paths: Paths): ReportStatusChangeRequest? = readBytesLocked(paths)?.let(::decode)

    private fun decode(bytes: ByteArray): ReportStatusChangeRequest = try {
        ReportStatusChangeCodec.decode(bytes).also(::requireScope)
    } catch (failure: Exception) {
        throw IOException("No se pudo recuperar el cambio pendiente; el archivo se conserva.", failure)
    }

    /** Never use AtomicFile.openRead: it can restore .bak and delete .new while merely recovering. */
    private fun readBytesLocked(paths: Paths): ByteArray? {
        checkPaths(paths)
        requireNoSidecars(paths)
        val base = paths.atomic.baseFile
        if (!Files.exists(base.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        try {
            return Files.newByteChannel(base.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteBuffer.allocate(1_024)
                while (true) {
                    buffer.clear()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > ReportStatusChangeCodec.MAX_BYTES - output.size()) {
                        throw IOException("La solicitud supera el tamaño permitido.")
                    }
                    output.write(buffer.array(), 0, count)
                }
                checkPaths(paths); requireNoSidecars(paths)
                output.toByteArray()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw IOException("No se pudo recuperar el cambio pendiente; el archivo se conserva.", failure)
        }
    }

    private fun paths(): Paths {
        val privateDirectory = context.filesDir
        if (Files.isSymbolicLink(privateDirectory.toPath())) throw IOException("El espacio privado no es una carpeta directa.")
        val parent = privateDirectory.canonicalFile
        if (!parent.isDirectory) throw IOException("No se encontró el espacio privado del cambio.")
        return Paths(parent, AtomicFile(File(parent, filename))).also(::checkPaths)
    }

    private fun checkPaths(paths: Paths) {
        if (Files.isSymbolicLink(paths.parent.toPath()) || !paths.parent.isDirectory ||
            paths.parent.canonicalFile != paths.parent.absoluteFile) {
            throw IOException("El espacio privado del cambio no es válido.")
        }
        paths.files.forEach { file ->
            if (Files.isSymbolicLink(file.toPath()) || file.canonicalFile != file.absoluteFile ||
                file.parentFile != paths.parent ||
                (Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS))) {
                throw IOException("El archivo de cambio no pertenece al espacio privado.")
            }
        }
    }

    private fun requireNoSidecars(paths: Paths) {
        if (paths.files.drop(1).any { Files.exists(it.toPath(), LinkOption.NOFOLLOW_LINKS) }) {
            throw IOException("Hay un cambio sin confirmar; sus archivos se conservan.")
        }
    }

    /** Roll back only the file actually opened by this write, never a replaced link or sidecar. */
    private fun rollbackOwnedWrite(paths: Paths, output: FileOutputStream) {
        try {
            checkPaths(paths)
            val opened = Os.fstat(output.fd)
            val existing = paths.files.filter { Files.exists(it.toPath(), LinkOption.NOFOLLOW_LINKS) }
            val owned = existing.singleOrNull()?.let { file ->
                val actual = Os.lstat(file.path)
                actual.st_dev == opened.st_dev && actual.st_ino == opened.st_ino
            } == true
            if (owned) paths.atomic.failWrite(output) else output.close()
        } catch (_: Exception) { runCatching { output.close() } }
    }

    private fun coordination(paths: Paths): Coordination = locks.getOrPut(paths.atomic.baseFile.absolutePath) { Coordination() }
    private data class Paths(val parent: File, val atomic: AtomicFile) {
        val files = listOf(atomic.baseFile, File(atomic.baseFile.path + ".new"), File(atomic.baseFile.path + ".bak"))
    }

    private class Coordination {
        val fileLock = Any()
        val workflow = Mutex()
    }

    companion object {
        fun fileName(scope: ReportStatusChangeScope): String =
            "report_status_change_v1_" + (scope.ownerAccountId?.let { "account_$it" } ?: "academic") +
                "_actor_${scope.actorAccountId}.json"

        private val locks = ConcurrentHashMap<String, Coordination>()
    }
}
