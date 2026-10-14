package com.example.citizensecurity.data

import android.content.Context
import android.util.AtomicFile
import com.example.citizensecurity.domain.ReportStatusChangeJournal
import com.example.citizensecurity.domain.ReportStatusChangeRequest
import com.example.citizensecurity.domain.ReportStatusChangeScope
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
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
    private val file: AtomicFile
    private val coordination: Coordination

    init {
        val base = File(context.applicationContext.filesDir, fileName(scope))
        file = AtomicFile(base)
        coordination = locks.getOrPut(base.canonicalPath) { Coordination() }
    }

    override suspend fun read(): ReportStatusChangeRequest? = withContext(ioDispatcher) {
        synchronized(coordination.fileLock) {
            ensureActive(); checkAccess(); ensureActive()
            readLocked().also { ensureActive(); checkAccess(); ensureActive() }
        }
    }

    override suspend fun retain(request: ReportStatusChangeRequest): Unit {
        requireScope(request)
        val bytes = ReportStatusChangeCodec.encode(request)
        withContext(ioDispatcher) {
            synchronized(coordination.fileLock) {
                ensureActive(); checkAccess(); ensureActive()
                val previous = readLocked()
                if (previous != null) {
                    check(previous == request) { "Hay otra solicitud pendiente; recupera su resultado antes de reemplazarla." }
                    ensureActive(); checkAccess(); ensureActive()
                    return@synchronized
                }
                ensureActive(); checkAccess(); ensureActive()
                val output = file.startWrite()
                try {
                    output.write(bytes)
                    ensureActive(); checkAccess(); ensureActive()
                    file.finishWrite(output)
                } catch (failure: Exception) {
                    file.failWrite(output)
                    throw failure
                }
                // AtomicFile may log a failed rename without throwing. Confirm the whole body.
                check(readLocked() == request) { "No se pudo confirmar la solicitud de cambio de estado." }
                ensureActive(); checkAccess(); ensureActive()
            }
        }
    }

    override suspend fun clear(expected: ReportStatusChangeRequest): Unit {
        requireScope(expected)
        withContext(ioDispatcher) {
            synchronized(coordination.fileLock) {
                ensureActive(); checkAccess(); ensureActive()
                val previous = readLocked()
                if (previous == null) {
                    ensureActive(); checkAccess(); ensureActive()
                    return@synchronized
                }
                check(previous == expected) { "La solicitud pendiente cambió y no puede retirarse." }
                ensureActive(); checkAccess(); ensureActive()
                file.delete()
                check(!file.baseFile.exists() && !backup().exists() && !staging().exists()) {
                    "No se pudo retirar la solicitud confirmada."
                }
                ensureActive(); checkAccess(); ensureActive()
            }
        }
    }

    override suspend fun <T> exclusive(block: suspend () -> T): T = coordination.workflow.withLock {
        // Structured children are joined before releasing this lease. A cancelled caller cannot
        // let another recovery race a blocking/non-cooperative backend that is still executing.
        coroutineScope {
            ensureActive(); checkAccess(); ensureActive()
            block().also { ensureActive(); checkAccess(); ensureActive() }
        }
    }

    private fun requireScope(request: ReportStatusChangeRequest) {
        require(request.scope == scope) { "La solicitud pertenece a otro espacio o actor." }
    }

    private fun readLocked(): ReportStatusChangeRequest? {
        if (!file.baseFile.exists() && !backup().exists()) return null
        try {
            val bytes = file.openRead().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4_096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > ReportStatusChangeCodec.MAX_BYTES) {
                        throw IOException("La solicitud supera el tamaño permitido.")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return ReportStatusChangeCodec.decode(bytes).also(::requireScope)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw IOException("No se pudo recuperar el cambio pendiente; el archivo se conserva.", failure)
        }
    }

    private fun backup() = File(file.baseFile.path + ".bak")
    private fun staging() = File(file.baseFile.path + ".new")

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
