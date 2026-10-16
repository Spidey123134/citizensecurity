package com.example.citizensecurity.data

import android.content.Context
import android.util.AtomicFile
import com.example.citizensecurity.domain.LocalAccount
import com.example.citizensecurity.domain.LocalAdministratorAssignment
import com.example.citizensecurity.domain.LocalAdministratorConflictException
import com.example.citizensecurity.domain.ReportAdministrationPermission
import com.example.citizensecurity.domain.ReportAdministrationPermissionDeniedException
import com.example.citizensecurity.domain.ReportAdministrationPolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Synchronous, installation-local authority. Call on IO with a captured-session guard.
 * Only a separately authenticated device-credential gesture may invoke assignFirst in the app.
 * There is no password, default account creation, replacement or reset API here.
 */
class LocalAdministratorStore(context: Context, filenameOverride: String? = null) {
    private val context = context.applicationContext
    private val filename = filenameOverride?.also { selected ->
        val match = FIXTURE_NAME.matchEntire(selected)
        require(match != null && runCatching {
            UUID.fromString(match.groupValues[1]).toString() == match.groupValues[1]
        }.getOrDefault(false)) { "La asignación de prueba requiere un archivo privado propio." }
    } ?: FILE_NAME

    /** Absence is the only empty state. Reading never creates, deletes or restores a file. */
    fun read(): LocalAdministratorAssignment? {
        val paths = paths()
        return synchronized(lock(paths)) { readLocked(paths)?.let(::decode) }
    }

    fun assignFirst(
        account: LocalAccount,
        operationId: String,
        grantedAt: Instant,
        checkCurrent: () -> Unit,
    ): LocalAdministratorAssignment {
        val requested = LocalAdministratorAssignment(account.id, account.username, operationId, grantedAt)
        checkCurrent()
        val paths = paths()
        return synchronized(lock(paths)) {
            checkCurrent()
            val previous = readLocked(paths)?.let(::decode)
            checkCurrent()
            if (previous != null) {
                if (previous != requested) throw LocalAdministratorConflictException()
                return@synchronized previous.also { checkCurrent() }
            }
            val bytes = LocalAdministratorCodec.encode(requested).toByteArray(Charsets.UTF_8)
            check(bytes.size <= LocalAdministratorCodec.MAX_BYTES)
            checkPaths(paths)
            checkCurrent()
            val output = paths.atomic.startWrite()
            var committed = false
            try {
                output.write(bytes)
                checkCurrent()
                checkPaths(paths)
                checkCurrent()
                paths.atomic.finishWrite(output)
                committed = true
            } catch (failure: Throwable) {
                if (!committed) paths.atomic.failWrite(output)
                throw failure
            }
            // AtomicFile can report an I/O error only to logcat. Require its exact receipt.
            val receipt = readLocked(paths)
            if (receipt == null || !receipt.contentEquals(bytes) || decode(receipt) != requested) {
                throw IOException("No se pudo confirmar la asignación. Vuelve a consultar; no la reemplaces.")
            }
            checkCurrent()
            requested
        }
    }

    /** Reread on every boundary, including a transaction's final permission check. */
    fun policy(): ReportAdministrationPolicy = ReportAdministrationPolicy { scope, permission ->
        when (permission) {
            ReportAdministrationPermission.READ_REPORTS, ReportAdministrationPermission.CHANGE_STATUS -> {
                val assignment = read()
                if (assignment?.allows(scope) != true) throw ReportAdministrationPermissionDeniedException()
            }
        }
    }

    private fun decode(bytes: ByteArray): LocalAdministratorAssignment = try {
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        LocalAdministratorCodec.decode(text)
    } catch (failure: Exception) {
        throw IOException("No se pudo leer la asignación local; el archivo se conserva.", failure)
    }

    private fun paths(): Paths {
        val parent = context.filesDir.canonicalFile
        if (!parent.isDirectory) throw IOException("No se encontró el espacio privado de la asignación.")
        return Paths(parent, AtomicFile(File(parent, filename))).also(::checkPaths)
    }

    private fun checkPaths(paths: Paths) {
        listOf(paths.atomic.baseFile, File(paths.atomic.baseFile.path + ".new"), File(paths.atomic.baseFile.path + ".bak"))
            .forEach { file ->
                if (Files.isSymbolicLink(file.toPath()) || file.canonicalFile != file.absoluteFile ||
                    file.parentFile != paths.parent || (file.exists() && !file.isFile)) {
                    throw IOException("El archivo de asignación no pertenece al espacio privado.")
                }
            }
    }

    private fun readLocked(paths: Paths): ByteArray? {
        checkPaths(paths)
        val base = paths.atomic.baseFile
        // Do not use AtomicFile.openRead: it can remove .new or restore .bak during a read.
        if (File(base.path + ".new").exists() || File(base.path + ".bak").exists()) {
            throw IOException("Hay una asignación sin confirmar; sus archivos se conservan.")
        }
        if (!base.exists()) return null
        return try {
            Files.newByteChannel(base.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteBuffer.allocate(1024)
                while (true) {
                    buffer.clear()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > LocalAdministratorCodec.MAX_BYTES - output.size()) {
                        throw IOException("El archivo de asignación supera el límite.")
                    }
                    output.write(buffer.array(), 0, count)
                }
                checkPaths(paths)
                output.toByteArray()
            }
        } catch (failure: Exception) {
            throw IOException("No se pudo leer la asignación local; el archivo se conserva.", failure)
        }
    }

    private fun lock(paths: Paths): Any = locks.getOrPut(paths.atomic.baseFile.absolutePath) { Any() }
    private data class Paths(val parent: File, val atomic: AtomicFile)

    companion object {
        const val FILE_NAME = "local_administrator.json"
        private val FIXTURE_NAME = Regex("local_administrator_test_([0-9a-f-]{36})\\.json")
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
