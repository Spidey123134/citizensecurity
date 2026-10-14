package com.example.citizensecurity.data

import android.content.Context
import android.util.AtomicFile
import com.example.citizensecurity.domain.TrustedContact
import com.example.citizensecurity.domain.TrustedContactsConflictException
import com.example.citizensecurity.domain.TrustedContactsRepository
import com.example.citizensecurity.domain.TrustedContactsSnapshot
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Private, owner-scoped contacts. A failed reading preserves the file and prohibits overwrite. */
class TrustedContactsStore(
    context: Context,
    ownerAccountId: String?,
    private val checkAccess: () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TrustedContactsRepository {
    private val file: AtomicFile
    private val lock: Any

    init {
        val base = File(context.applicationContext.filesDir, fileName(ownerAccountId))
        file = AtomicFile(base)
        lock = locks.getOrPut(base.canonicalPath) { Any() }
    }

    override suspend fun read(): TrustedContactsSnapshot = withContext(dispatcher) {
        synchronized(lock) {
            ensureActive(); checkAccess()
            readLocked().also { ensureActive(); checkAccess() }
        }
    }

    override suspend fun replace(expectedRevision: String?, contacts: List<TrustedContact>): TrustedContactsSnapshot {
        val captured = ArrayList(contacts)
        TrustedContactsSnapshot(expectedRevision, if (expectedRevision == null) emptyList() else captured)
        TrustedContactsSnapshot.validateContacts(captured)
        return withContext(dispatcher) {
            synchronized(lock) {
                ensureActive(); checkAccess()
                val previous = readLocked()
                if (previous.revision != expectedRevision) throw TrustedContactsConflictException()
                if (previous.contacts == captured) return@synchronized previous.also { ensureActive(); checkAccess() }
                val next = TrustedContactsSnapshot(UUID.randomUUID().toString(), captured)
                val bytes = TrustedContactsCodec.encode(next).toByteArray(Charsets.UTF_8)
                require(bytes.size <= TrustedContactsCodec.MAX_BYTES)
                ensureActive(); checkAccess()
                val output = file.startWrite()
                try {
                    output.write(bytes)
                    ensureActive(); checkAccess()
                    file.finishWrite(output)
                } catch (failure: Exception) {
                    file.failWrite(output)
                    throw failure
                }
                // AtomicFile can report filesystem failures without throwing. Confirm the exact
                // bytes through the same strict reader before returning a successful receipt.
                val confirmed = readLocked()
                check(confirmed.revision == next.revision && confirmed.contacts == next.contacts) {
                    "No se pudo confirmar el guardado de contactos."
                }
                // A lost receipt or revoked session after commit is recovered through a fresh read.
                ensureActive(); checkAccess()
                next
            }
        }
    }

    private fun readLocked(): TrustedContactsSnapshot {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) {
            return TrustedContactsSnapshot(null, emptyList())
        }
        try {
            val bytes = file.openRead().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4_096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= TrustedContactsCodec.MAX_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            return TrustedContactsCodec.decode(text)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            throw IOException("No se pudieron leer los contactos; el archivo se conserva.", failure)
        }
    }

    companion object {
        fun fileName(ownerAccountId: String?): String {
            require(ownerAccountId == null || runCatching {
                UUID.fromString(ownerAccountId).toString() == ownerAccountId
            }.getOrDefault(false)) { "Cuenta inválida." }
            return if (ownerAccountId == null) "trusted_contacts_v1_academic.json"
                else "trusted_contacts_v1_account_$ownerAccountId.json"
        }
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
