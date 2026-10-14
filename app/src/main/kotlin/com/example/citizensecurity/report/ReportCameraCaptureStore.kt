package com.example.citizensecurity.report

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ReportCameraCapture(val id: String, val source: String)

interface ReportCameraCaptureFiles {
    suspend fun create(): ReportCameraCapture
    suspend fun release(capture: ReportCameraCapture)
}

class ReportCameraCapacityException : IOException("El espacio temporal de la cámara está lleno.")

/** Temporary camera output only; importing it never changes the saved photo or report. */
class ReportCameraCaptureStore(context: Context) : ReportCameraCaptureFiles {
    private val context = context.applicationContext
    private val authority = "${this.context.packageName}.camera_capture"

    override suspend fun create(): ReportCameraCapture {
        var created: ReportCameraCapture? = null
        try {
            return withContext(Dispatchers.IO) {
                ensureActive()
                val paths = paths()
                lock(paths).withLock {
                    ensureActive()
                    ensureDirectory(paths)
                    checkCapacity(paths)
                    var file: File
                    var id: String
                    do {
                        ensureActive()
                        id = UUID.randomUUID().toString()
                        file = ownedFile(paths, id)
                    } while (!file.createNewFile())
                    try {
                        ensureRegularFile(paths, file)
                        val capture = ReportCameraCapture(id, uriFor(file).toString())
                        created = capture
                        ensureActive()
                        capture
                    } catch (failure: Throwable) {
                        // Never sweep the directory: this attempt owns only the just-created file.
                        if (file.canonicalFile == file.absoluteFile && file.parentFile == paths.directory) {
                            file.delete()
                        }
                        throw failure
                    }
                }
            }
        } catch (failure: Throwable) {
            // withContext may discard a result when its caller is cancelled on the return hop.
            created?.let { capture ->
                try { release(capture) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            }
            throw failure
        }
    }

    override suspend fun release(capture: ReportCameraCapture) = withContext(NonCancellable + Dispatchers.IO) {
        val paths = paths()
        lock(paths).withLock {
            validateId(capture.id)
            val expected = expectedUri(paths, capture.id)
            if (capture.source != expected.toString()) throw IOException("La captura temporal no corresponde a su archivo.")
            // A camera grant must end even if its app returned cancellation or no readable photo.
            context.revokeUriPermission(expected,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            checkDirectoryPath(paths)
            val file = ownedFile(paths, capture.id)
            if (file.exists()) {
                ensureRegularFile(paths, file)
                if (!file.delete()) throw IOException("No se pudo retirar la captura temporal.")
            }
        }
    }

    private fun paths(): Paths {
        val parent = context.cacheDir.canonicalFile
        return Paths(parent, File(parent, DIRECTORY_NAME))
    }

    private fun ensureDirectory(paths: Paths) {
        checkDirectoryPath(paths)
        if (!paths.directory.exists() && !paths.directory.mkdir()) {
            throw IOException("No se pudo preparar el espacio temporal de la cámara.")
        }
        if (!paths.directory.isDirectory) throw IOException("El espacio temporal de la cámara no está disponible.")
        checkDirectoryPath(paths)
    }

    private fun checkDirectoryPath(paths: Paths) {
        if (paths.directory.canonicalFile != paths.directory.absoluteFile || paths.directory.parentFile != paths.parent) {
            throw IOException("El espacio temporal de la cámara no pertenece a la caché privada.")
        }
    }

    private fun checkCapacity(paths: Paths) {
        val files = paths.directory.listFiles() ?: throw IOException("No se pudo consultar el espacio temporal de la cámara.")
        if (files.size >= MAX_PENDING_FILES) throw ReportCameraCapacityException()
        var occupied = 0L
        for (file in files) {
            ensureRegularFile(paths, file)
            val length = file.length()
            if (length > MAX_CACHE_BYTES - occupied) throw ReportCameraCapacityException()
            occupied += length
        }
        if (occupied >= MAX_CACHE_BYTES) throw ReportCameraCapacityException()
    }

    private fun ownedFile(paths: Paths, id: String): File {
        validateId(id)
        return File(paths.directory, "$id.jpg").also { file ->
            if (file.canonicalFile != file.absoluteFile || file.parentFile != paths.directory) {
                throw IOException("La captura temporal no pertenece al espacio privado.")
            }
        }
    }

    private fun ensureRegularFile(paths: Paths, file: File) {
        if (!file.isFile || file.canonicalFile != file.absoluteFile || file.parentFile != paths.directory) {
            throw IOException("La captura temporal no es un archivo privado válido.")
        }
    }

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, authority, file)

    private fun expectedUri(paths: Paths, id: String): Uri {
        // Resolve the provider's actual cache root without following a replaced output file.
        // This lets cleanup revoke its exact original grant before rejecting a symlink target.
        val packageContext = context.createPackageContext(context.packageName, 0)
        val providerRoot = File(packageContext.cacheDir.canonicalFile, DIRECTORY_NAME).absoluteFile
        val file = File(paths.directory, "$id.jpg").absoluteFile
        val prefix = providerRoot.path + File.separator
        if (!file.path.startsWith(prefix)) throw IOException("La captura temporal no pertenece al proveedor privado.")
        val relative = file.path.removePrefix(prefix)
        return Uri.Builder().scheme("content").authority(authority).appendPath(DIRECTORY_NAME).apply {
            relative.split(File.separatorChar).forEach { appendPath(it) }
        }.build()
    }

    private fun validateId(id: String) {
        if (!runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
            throw IOException("El identificador de la captura temporal no es válido.")
        }
    }

    private fun lock(paths: Paths): Mutex = locks.getOrPut(paths.directory.absolutePath) { Mutex() }
    private data class Paths(val parent: File, val directory: File)

    companion object {
        const val DIRECTORY_NAME = "report_camera"
        const val MAX_PENDING_FILES = 4
        const val MAX_CACHE_BYTES = 64L * 1024 * 1024
        private val locks = ConcurrentHashMap<String, Mutex>()
    }
}
