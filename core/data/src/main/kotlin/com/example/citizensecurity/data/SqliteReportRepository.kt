package com.example.citizensecurity.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.citizensecurity.domain.IdempotentReportRepository
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportListPage
import com.example.citizensecurity.domain.ReportListPageQuery
import com.example.citizensecurity.domain.ReportListPageRepository
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.ReportMapMarker
import com.example.citizensecurity.domain.ReportMapPage
import com.example.citizensecurity.domain.ReportMapQuery
import com.example.citizensecurity.domain.ReportMapRepository
import com.example.citizensecurity.domain.ReportRequestConflictException
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportValidator
import com.example.citizensecurity.domain.ReportHistoryRepository
import com.example.citizensecurity.domain.ReportHistoryQuery
import com.example.citizensecurity.domain.ReportHistoryPage
import com.example.citizensecurity.domain.ReportActivityEntry
import com.example.citizensecurity.domain.ReportActivityPage
import com.example.citizensecurity.domain.ReportActivityQuery
import com.example.citizensecurity.domain.ReportActivityRepository
import com.example.citizensecurity.domain.ReportStatusRepository
import com.example.citizensecurity.domain.ReportStatusChangeCommand
import com.example.citizensecurity.domain.ReportStatusChangeReceipt
import com.example.citizensecurity.domain.ReportStatusEvent
import com.example.citizensecurity.domain.ReportStatusEventKind
import com.example.citizensecurity.domain.ReportStatusPermissionDeniedException
import com.example.citizensecurity.domain.ReportStatusConflictException
import com.example.citizensecurity.domain.ReportStatusOperationConflictException
import com.example.citizensecurity.domain.ReportStatusNotFoundException
import com.example.citizensecurity.domain.ReportResolution
import com.example.citizensecurity.domain.ReportResolutionActor
import com.example.citizensecurity.domain.ReportResolutionActorKind
import com.example.citizensecurity.domain.ReportResolutionCommand
import com.example.citizensecurity.domain.ReportResolutionEvent
import com.example.citizensecurity.domain.ReportResolutionQuery
import com.example.citizensecurity.domain.ReportResolutionPage
import com.example.citizensecurity.domain.ReportResolutionPermissionDeniedException
import com.example.citizensecurity.domain.ReportResolutionNotFoundException
import com.example.citizensecurity.domain.ReportResolutionConflictException
import com.example.citizensecurity.domain.ReportResolutionOperationConflictException
import com.example.citizensecurity.domain.SimilarReportMatch
import com.example.citizensecurity.domain.SimilarReportPage
import com.example.citizensecurity.domain.SimilarReportQuery
import com.example.citizensecurity.domain.SimilarReportRepository
import java.io.Closeable
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Persistencia local de reportes. La capa de aplicación es dueña de esta instancia y debe
 * cerrarla al terminar su uso. Conserva solo el contexto de aplicación; no retiene una actividad.
 * Las operaciones suspendidas hacen su trabajo de SQLite en [ioDispatcher].
 *
 * El nombre predeterminado corresponde exclusivamente al proyecto nuevo. [databaseName] permite
 * aislar archivos durante las pruebas; un valor nulo crea una base exclusivamente en memoria.
 * Después de [close], la instancia no se puede reutilizar. Una instancia nueva puede reabrir
 * un archivo, pero cada base en memoria empieza vacía y se pierde al cerrar.
 */
class SqliteReportRepository(
    context: Context,
    databaseName: String? = DATABASE_NAME,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val createGuard: (NewReport) -> Unit = {},
    private val authorAccountId: String? = null,
    private val statusAuthority: (ReportStatusChangeCommand) -> String = { throw ReportStatusPermissionDeniedException() },
    private val resolutionAuthority: (reportId: String, creatorAccountId: String?) -> ReportResolutionActor =
        { _, _ -> throw ReportResolutionPermissionDeniedException() },
) : IdempotentReportRepository, ReportMapRepository, SimilarReportRepository, ReportListPageRepository,
    ReportHistoryRepository, ReportActivityRepository, ReportStatusRepository, Closeable {

    private val lock = Any()
    private val validator = ReportValidator()
    private val helper: ReportDatabase
    private val databaseFile: java.io.File?
    private var databaseWasOpened = false
    private var closed = false

    init {
        authorAccountId?.let { requireCanonicalActor(it) }
        require(
            databaseName == null || (
                databaseName.isNotBlank() &&
                    databaseName.endsWith(".db") &&
                    '/' !in databaseName && '\\' !in databaseName
                ),
        ) { "El nombre de la base debe ser un archivo .db sin una ruta." }
        databaseFile = databaseName?.let { context.applicationContext.getDatabasePath(it) }
        helper = ReportDatabase(context.applicationContext, databaseName)
    }

    override suspend fun create(draft: NewReport): Report = createOnce(UUID.randomUUID().toString(), draft)

    override suspend fun createOnce(requestId: String, draft: NewReport): Report = withOpenRepository {
        require(runCatching { UUID.fromString(requestId).toString() == requestId }.getOrDefault(false)) {
            "La solicitud necesita un UUID completo y canónico."
        }
        val normalized = draft.copy(
            description = draft.description.trim(),
            location = draft.location.copy(
                reference = draft.location.reference.trim(),
                latitude = draft.location.latitude.canonicalizeZero(),
                longitude = draft.location.longitude.canonicalizeZero(),
            ),
        )
        // Recuperar un resultado anterior es lectura: no requiere otra captura GPS. Una base
        // todavía inexistente no se abre aquí; se conserva el rechazo del primer guard sin archivo.
        if (databaseWasOpened || databaseFile?.exists() == true) {
            findStoredReport(databaseForRead(), requestId)?.let { existing ->
                ensureActive()
                return@withOpenRepository resolveExisting(existing, normalized)
            }
        }
        val createdAt = clock.instant()
        val errors = validator.validate(normalized, createdAt)
        if (!errors.isValid) throw InvalidReportException(errors)

        val report = Report(
            id = requestId,
            type = normalized.type,
            priority = normalized.priority,
            description = normalized.description,
            occurredAt = normalized.occurredAt,
            location = normalized.location,
            createdAt = createdAt,
            status = ReportStatus.REPORTED,
            photo = normalized.photo,
        )
        ensureActive()
        createGuard(normalized)
        ensureActive()
        val database = databaseForWrite()
        ensureActive()
        createGuard(normalized)
        ensureActive()
        database.beginTransaction()
        try {
            // Otra instancia pudo insertar entre la primera lectura y adquirir la transacción.
            // El bloqueo SQLite y la clave primaria hacen única la escritura también en ese caso.
            findStoredReport(database, requestId)?.let { existing ->
                ensureActive()
                return@withOpenRepository resolveExisting(existing, normalized)
            }
            if (hasResolutionOperation(database, requestId) || hasHistoryOperation(database, requestId)) {
                throw ReportRequestConflictException(requestId)
            }
            database.insertOrThrow(TABLE, null, report.toValues())
            insertHistoryEvent(database, report.id, ReportStatusEvent(report.id, 1L,
                ReportStatusEventKind.CREATED, null, report.status, report.createdAt, authorAccountId))
            ensureActive()
            createGuard(normalized)
            ensureActive()
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        report
    }

    override suspend fun findById(id: String): Report? = withOpenRepository {
        findStoredReport(databaseForRead(), id)
    }

    /** Current state and one bounded page share a fresh SQLite snapshot. No GPS or status write. */
    override suspend fun readHistory(query: ReportHistoryQuery): ReportHistoryPage? = withOpenRepository {
        val database = databaseForRead()
        ensureActive()
        database.beginTransactionNonExclusive()
        try {
            val head = readHistoryHead(database, query.reportId) ?: return@withOpenRepository null
            val events = database.rawQuery(ReportHistorySql.page(query), ReportHistorySql.pageArguments(query)).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) { ensureActive(); add(cursor.toHistoryEvent()) }
                }
            }
            ensureActive()
            database.setTransactionSuccessful()
            ReportHistoryPage(query.reportId, head.status, head.sequence, events.take(query.limit),
                events.size > query.limit, head.complete)
        } finally { database.endTransaction() }
    }

    /** One bounded read of recorded activity. The captured workspace supplies account access. */
    override suspend fun readActivity(query: ReportActivityQuery): ReportActivityPage = withOpenRepository {
        val database = databaseForRead()
        ensureActive()
        val entries = database.rawQuery(ReportActivitySql.page(query), ReportActivitySql.pageArguments(query)).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    ensureActive()
                    add(ReportActivityEntry(cursor.getString(cursor.getColumnIndexOrThrow("report_id")),
                        IncidentType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("incident_type"))),
                        cursor.toHistoryEvent()))
                }
            }
        }
        ensureActive()
        ReportActivityPage(entries.take(query.limit), entries.size > query.limit)
    }

    /** Denied before opening storage by default; the app exposes no administrative authority. */
    override suspend fun changeStatus(command: ReportStatusChangeCommand): ReportStatusEvent =
        changeStatusWithActor(command, null, {})

    /** Recovery fixes the actor before storage is opened; changing authorization cannot mutate. */
    suspend fun changeStatusForActor(command: ReportStatusChangeCommand, expectedActor: String,
        checkCurrent: () -> Unit = {}): ReportStatusEvent {
        requireCanonicalActor(expectedActor)
        return changeStatusWithActor(command, expectedActor, checkCurrent)
    }

    private suspend fun changeStatusWithActor(command: ReportStatusChangeCommand, expectedActor: String?,
        checkCurrent: () -> Unit): ReportStatusEvent = withOpenRepository {
        checkCurrent()
        ensureActive()
        val actor = authorizedActor(command)
        if (expectedActor != null && actor != expectedActor) throw ReportStatusPermissionDeniedException()
        ensureActive()
        val database = databaseForWrite()
        ensureActive()
        checkCurrent()
        requireSameAuthority(command, actor)
        ensureActive()
        database.beginTransaction()
        try {
            if (hasResolutionOperation(database, command.operationId)) throw ReportStatusOperationConflictException()
            val existing = database.rawQuery(ReportHistorySql.FIND_OPERATION, arrayOf(command.operationId)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow("report_id")) to cursor.toHistoryEvent()
                else null
            }
            if (existing != null) {
                val event = existing.second
                if (existing.first != command.reportId || event.kind != ReportStatusEventKind.STATUS_CHANGED ||
                    event.sequence != command.expectedSequence + 1 || event.fromStatus != command.expectedStatus ||
                    event.toStatus != command.targetStatus || event.reason != command.reason || event.actorAccountId != actor
                ) throw ReportStatusOperationConflictException()
                ensureActive()
                requireSameAuthority(command, actor)
                checkCurrent()
                ensureActive()
                database.setTransactionSuccessful()
                return@withOpenRepository event
            }
            val head = readHistoryHead(database, command.reportId) ?: throw ReportStatusNotFoundException()
            if (head.sequence != command.expectedSequence || head.status != command.expectedStatus) {
                throw ReportStatusConflictException()
            }
            val event = ReportStatusEvent(command.operationId, command.expectedSequence + 1,
                ReportStatusEventKind.STATUS_CHANGED, command.expectedStatus, command.targetStatus,
                clock.instant(), actor, command.reason)
            val update = database.compileStatement(ReportHistorySql.CAS_UPDATE)
            val changed = update.use {
                it.bindString(1, command.targetStatus.name); it.bindString(2, command.reportId)
                it.bindString(3, command.expectedStatus.name); it.bindLong(4, command.expectedSequence)
                it.executeUpdateDelete()
            }
            if (changed != 1) throw ReportStatusConflictException()
            insertHistoryEvent(database, command.reportId, event)
            ensureActive()
            requireSameAuthority(command, actor)
            checkCurrent()
            ensureActive()
            database.setTransactionSuccessful()
            event
        } finally { database.endTransaction() }
    }

    /** Read an exact receipt without creating, upgrading, repairing, or opening a missing file. */
    suspend fun findStatusChange(command: ReportStatusChangeCommand, expectedActor: String? = null,
        checkCurrent: () -> Unit = {}): ReportStatusChangeReceipt? =
        withOpenRepository {
            checkCurrent()
            ensureActive()
            expectedActor?.let(::requireCanonicalActor)
            val actor = authorizedActor(command)
            if (expectedActor != null && actor != expectedActor) throw ReportStatusPermissionDeniedException()
            ensureActive()
            val receipt = if (databaseFile == null) {
                // A previously opened in-memory helper already owns the current schema; never create it here.
                if (!databaseWasOpened) null else readStatusChangeReceipt(databaseForRead(), command, actor)
            } else if (!databaseFile.exists()) null else {
                check(databaseFile.isFile) { "La base local no es un archivo válido." }
                SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY,
                    android.database.DatabaseErrorHandler { broken ->
                        runCatching { broken.close() }
                        throw android.database.sqlite.SQLiteDatabaseCorruptException(
                            "La base local contiene datos dañados. No se ha eliminado ni recreado.")
                    }).use { database ->
                    check(database.version in 3..5) { "La recuperación requiere el esquema de historial existente." }
                    ensureActive(); checkCurrent(); requireSameAuthority(command, actor); ensureActive()
                    readStatusChangeReceipt(database, command, actor)
                }
            }
            ensureActive(); checkCurrent(); requireSameAuthority(command, actor); ensureActive()
            receipt
        }

    private fun readStatusChangeReceipt(database: SQLiteDatabase, command: ReportStatusChangeCommand,
        actor: String): ReportStatusChangeReceipt? {
        if (database.version >= 5 && hasResolutionOperation(database, command.operationId)) {
            throw ReportStatusOperationConflictException()
        }
        return database.rawQuery(ReportHistorySql.FIND_OPERATION, arrayOf(command.operationId)).use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val reportId = cursor.getString(cursor.getColumnIndexOrThrow("report_id"))
                val event = cursor.toHistoryEvent()
                if (reportId != command.reportId || event.kind != ReportStatusEventKind.STATUS_CHANGED ||
                    event.operationId != command.operationId || event.sequence != command.expectedSequence + 1L ||
                    event.fromStatus != command.expectedStatus || event.toStatus != command.targetStatus ||
                    event.reason != command.reason || event.actorAccountId != actor
                ) throw ReportStatusOperationConflictException()
                ReportStatusChangeReceipt(reportId, event)
            }
        }
    }

    /** Current result and one page share a fresh snapshot. Absence of events means unconfirmed. */
    suspend fun readResolution(query: ReportResolutionQuery): ReportResolutionPage? = withOpenRepository {
        val database = databaseForRead()
        ensureActive()
        database.beginTransactionNonExclusive()
        try {
            if (findStoredReport(database, query.reportId) == null) return@withOpenRepository null
            val creator = readResolutionCreator(database, query.reportId)
            val actor = optionalResolutionActor(query.reportId, creator)
            val head = readResolutionHead(database, query.reportId)
            val events = database.rawQuery(ReportResolutionSql.page(query), ReportResolutionSql.pageArguments(query)).use { cursor ->
                buildList { while (cursor.moveToNext()) { ensureActive(); add(cursor.toResolutionEvent()) } }
            }
            ensureActive()
            if (optionalResolutionActor(query.reportId, creator) != actor) throw ReportResolutionPermissionDeniedException()
            ensureActive()
            database.setTransactionSuccessful()
            ReportResolutionPage(query.reportId, head.first, head.second, events.take(query.limit),
                events.size > query.limit, actor)
        } finally { database.endTransaction() }
    }

    /** Exact idempotent append; an independent authority supplies author/admin identity from SQL. */
    suspend fun recordResolution(command: ReportResolutionCommand, beforeCommit: () -> Unit = {}): ReportResolutionEvent =
        withOpenRepository {
            // Do not create storage, or migrate an existing file, merely to reject a writer.
            val preflight = withExistingResolutionDatabase { database ->
                val stored = findStoredReport(database, command.reportId)
                val creator = if (database.version >= 3) readResolutionCreator(database, command.reportId) else null
                requireResolutionActor(command, creator).also {
                    // A reused UUID is not a missing request, even if its supplied folio changed.
                    if (database.version >= 3) readResolutionReceipt(database, command)
                    if (stored == null) throw ReportResolutionNotFoundException()
                }
            }
            if (preflight == null) {
                requireResolutionActor(command, null)
                throw ReportResolutionNotFoundException()
            }
            ensureActive()
            val database = databaseForWrite()
            ensureActive()
            database.beginTransaction()
            try {
                if (findStoredReport(database, command.reportId) == null) throw ReportResolutionNotFoundException()
                val creator = readResolutionCreator(database, command.reportId)
                val actor = requireResolutionActor(command, creator)
                if (actor != preflight) throw ReportResolutionPermissionDeniedException()
                ensureActive()
                val receipt = readResolutionReceipt(database, command)
                val event = if (receipt != null) receipt else {
                    val head = readResolutionHead(database, command.reportId)
                    if (head.first != command.expectedResolution || head.second != command.expectedSequence) {
                        throw ReportResolutionConflictException()
                    }
                    ReportResolutionEvent(command.operationId, command.reportId, command.expectedSequence + 1L,
                        command.expectedResolution, command.targetResolution, clock.instant(), actor, command.reason).also {
                        database.execSQL(ReportResolutionSql.INSERT, arrayOf<Any?>(it.operationId, it.reportId, it.sequence,
                            it.fromResolution.name, it.toResolution.name, it.recordedAt.epochSecond, it.recordedAt.nano,
                            it.actor.accountId, it.actor.kind.name, it.reason))
                    }
                }
                ensureActive()
                requireResolutionActor(command, readResolutionCreator(database, command.reportId))
                // Role checks may block on independent storage. The caller's foreground/session
                // guard must therefore run LAST, after that final authority read, not before it.
                beforeCommit()
                ensureActive()
                database.setTransactionSuccessful()
                event
            } finally { database.endTransaction() }
        }

    /** A receipt read never creates or migrates storage and never retries a declaration. */
    suspend fun findResolutionReceipt(command: ReportResolutionCommand): ReportResolutionEvent? = withOpenRepository {
        var existingDatabase = false
        val receipt = withExistingResolutionDatabase { database ->
            existingDatabase = true
            check(database.version in 3..5) { "La recuperación requiere el esquema de historial existente." }
            val stored = findStoredReport(database, command.reportId)
            val creator = readResolutionCreator(database, command.reportId)
            requireResolutionActor(command, creator)
            ensureActive()
            val result = readResolutionReceipt(database, command)
            if (stored == null) {
                check(result == null) { "El recibo guardado perdió su reporte. Se conservó sin modificar." }
                return@withExistingResolutionDatabase null
            }
            requireResolutionActor(command, readResolutionCreator(database, command.reportId))
            ensureActive()
            result
        }
        if (!existingDatabase) requireResolutionActor(command, null)
        ensureActive()
        receipt
    }

    private fun <T> withExistingResolutionDatabase(action: (SQLiteDatabase) -> T): T? {
        if (databaseFile == null) return if (databaseWasOpened) action(databaseForRead()) else null
        if (!databaseFile.exists()) return null
        check(databaseFile.isFile) { "La base local no es un archivo válido." }
        return SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY,
            android.database.DatabaseErrorHandler { broken ->
                runCatching { broken.close() }
                throw android.database.sqlite.SQLiteDatabaseCorruptException(
                    "La base local contiene datos dañados. No se ha eliminado ni recreado.")
            }).use { database ->
            check(database.version in 1..5) { "El esquema local no admite esta consulta." }
            action(database)
        }
    }

    private fun readResolutionReceipt(database: SQLiteDatabase, command: ReportResolutionCommand): ReportResolutionEvent? {
        if (hasHistoryOperation(database, command.operationId)) {
            throw ReportResolutionOperationConflictException()
        }
        if (database.version < 5) return null
        val result = database.rawQuery(ReportResolutionSql.FIND_OPERATION, arrayOf(command.operationId)).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.toResolutionEvent().also {
                if (!it.matches(command)) throw ReportResolutionOperationConflictException()
            }
        }
        // Exact body alone cannot confirm a declaration in a broken chronology. This is a
        // read-only aggregate/index check; legacy 3/4 returned above without opening a helper.
        if (result != null) {
            check(readResolutionHead(database, command.reportId).second >= result.sequence) {
                "El recibo no pertenece al historial guardado. Se conservó sin modificar."
            }
        }
        return result
    }

    private fun hasResolutionOperation(database: SQLiteDatabase, operationId: String): Boolean =
        database.rawQuery(ReportResolutionSql.FIND_OPERATION, arrayOf(operationId)).use { it.moveToFirst() }

    private fun hasHistoryOperation(database: SQLiteDatabase, operationId: String): Boolean =
        database.rawQuery(ReportHistorySql.FIND_OPERATION, arrayOf(operationId)).use { it.moveToFirst() }

    private fun readResolutionCreator(database: SQLiteDatabase, reportId: String): String? =
        database.rawQuery(ReportResolutionSql.CREATOR, arrayOf(reportId)).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.toHistoryEvent().also { event ->
                check(event.operationId == reportId) { "El autor guardado no corresponde a la creación de este folio." }
            }.actorAccountId
        }

    private fun optionalResolutionActor(reportId: String, creator: String?): ReportResolutionActor? = try {
        authorizedResolutionActor(reportId, creator)
    } catch (_: ReportResolutionPermissionDeniedException) { null }

    private fun authorizedResolutionActor(reportId: String, creator: String?): ReportResolutionActor =
        resolutionAuthority(reportId, creator).also { actor ->
            if (authorAccountId == null || actor.accountId != authorAccountId ||
                (actor.kind == ReportResolutionActorKind.AUTHOR && actor.accountId != creator)
            ) throw ReportResolutionPermissionDeniedException()
        }

    private fun requireResolutionActor(command: ReportResolutionCommand, creator: String?): ReportResolutionActor =
        authorizedResolutionActor(command.reportId, creator).also {
            if (it != command.actor) throw ReportResolutionPermissionDeniedException()
        }

    private fun readResolutionHead(database: SQLiteDatabase, reportId: String): Pair<ReportResolution, Long> {
        val sequence = database.rawQuery(ReportResolutionSql.CHAIN, arrayOf(reportId)).use { cursor ->
            check(cursor.moveToFirst())
            val count = cursor.getLong(cursor.getColumnIndexOrThrow("event_count"))
            val first = cursor.getLong(cursor.getColumnIndexOrThrow("first_sequence"))
            val last = cursor.getLong(cursor.getColumnIndexOrThrow("last_sequence"))
            check(cursor.getLong(cursor.getColumnIndexOrThrow("broken_links")) == 0L &&
                (count == 0L || (first == 1L && count == last))) {
                "El historial de resultados contiene una secuencia incompleta. Se conservó sin modificar."
            }
            last
        }
        return database.rawQuery(ReportResolutionSql.LATEST, arrayOf(reportId)).use { cursor ->
            if (!cursor.moveToFirst()) {
                check(sequence == 0L)
                ReportResolution.UNCONFIRMED to 0L
            } else cursor.toResolutionEvent().let { event ->
                check(event.sequence == sequence)
                event.toResolution to event.sequence
            }
        }
    }

    private fun Cursor.toResolutionEvent(): ReportResolutionEvent {
        listOf("sequence", "recorded_at_seconds", "recorded_at_nanos").forEach { name ->
            check(getType(getColumnIndexOrThrow(name)) == Cursor.FIELD_TYPE_INTEGER) {
                "El resultado guardado contiene tipos numéricos inválidos."
            }
        }
        val nanos = getLong(getColumnIndexOrThrow("recorded_at_nanos"))
        check(nanos in 0..999999999) { "La fecha guardada del resultado es inválida." }
        return ReportResolutionEvent(getString(getColumnIndexOrThrow("operation_id")),
            getString(getColumnIndexOrThrow("report_id")), getLong(getColumnIndexOrThrow("sequence")),
            ReportResolution.valueOf(getString(getColumnIndexOrThrow("from_resolution"))),
            ReportResolution.valueOf(getString(getColumnIndexOrThrow("to_resolution"))),
            Instant.ofEpochSecond(getLong(getColumnIndexOrThrow("recorded_at_seconds")), nanos),
            ReportResolutionActor(getString(getColumnIndexOrThrow("actor_account_id")),
                ReportResolutionActorKind.valueOf(getString(getColumnIndexOrThrow("actor_kind")))),
            getString(getColumnIndexOrThrow("reason")))
    }

    private data class HistoryHead(val status: ReportStatus, val sequence: Long, val complete: Boolean)
    private fun readHistoryHead(database: SQLiteDatabase, reportId: String): HistoryHead? =
        database.rawQuery(ReportHistorySql.HEAD, arrayOf(reportId)).use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val latestIndex = cursor.getColumnIndexOrThrow("latest_sequence")
                check(cursor.getType(latestIndex) == Cursor.FIELD_TYPE_INTEGER && cursor.getLong(latestIndex) >= 0) {
                    "La secuencia guardada del historial es inválida."
                }
                HistoryHead(ReportStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
                    cursor.getLong(latestIndex), cursor.getInt(cursor.getColumnIndexOrThrow("complete_from_creation")) == 1)
            }
        }

    private fun authorizedActor(command: ReportStatusChangeCommand): String =
        statusAuthority(command).also(::requireCanonicalActor)
    private fun requireSameAuthority(command: ReportStatusChangeCommand, expected: String) {
        if (authorizedActor(command) != expected) throw ReportStatusPermissionDeniedException()
    }
    private fun requireCanonicalActor(actor: String) {
        require(runCatching { UUID.fromString(actor).toString() == actor }.getOrDefault(false)) {
            "El actor autorizado necesita una identidad canónica."
        }
    }

    private fun insertHistoryEvent(database: SQLiteDatabase, reportId: String, event: ReportStatusEvent) {
        database.execSQL(ReportHistorySql.INSERT, arrayOf<Any?>(event.operationId, reportId, event.sequence,
            event.kind.name, event.fromStatus?.name, event.toStatus.name, event.recordedAt.epochSecond,
            event.recordedAt.nano, event.actorAccountId, event.reason))
    }

    private fun Cursor.toHistoryEvent(): ReportStatusEvent {
        listOf("sequence", "recorded_at_seconds", "recorded_at_nanos").forEach { name ->
            check(getType(getColumnIndexOrThrow(name)) == Cursor.FIELD_TYPE_INTEGER) {
                "El historial guardado contiene tipos numéricos inválidos."
            }
        }
        val nanos = getLong(getColumnIndexOrThrow("recorded_at_nanos"))
        check(nanos in 0..999999999) { "La fecha guardada del historial es inválida." }
        return ReportStatusEvent(
            getString(getColumnIndexOrThrow("operation_id")), getLong(getColumnIndexOrThrow("sequence")),
            ReportStatusEventKind.valueOf(getString(getColumnIndexOrThrow("kind"))),
            getColumnIndexOrThrow("from_status").let { if (isNull(it)) null else ReportStatus.valueOf(getString(it)) },
            ReportStatus.valueOf(getString(getColumnIndexOrThrow("to_status"))),
            Instant.ofEpochSecond(getLong(getColumnIndexOrThrow("recorded_at_seconds")), nanos),
            getColumnIndexOrThrow("actor_account_id").let { if (isNull(it)) null else getString(it) },
            getString(getColumnIndexOrThrow("reason")),
        )
    }

    private fun findStoredReport(database: SQLiteDatabase, id: String): Report? =
        database.query(
            TABLE,
            PROJECTION,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toReport() else null
        }

    private fun resolveExisting(existing: Report, draft: NewReport): Report {
        if (existing.type != draft.type || existing.priority != draft.priority ||
            existing.description != draft.description || existing.occurredAt != draft.occurredAt ||
            existing.location != draft.location || existing.photo != draft.photo
        ) throw ReportRequestConflictException(existing.id)
        return existing
    }

    override suspend fun list(): List<Report> = withOpenRepository {
        databaseForRead().query(
            TABLE,
            PROJECTION,
            null,
            null,
            null,
            null,
            "created_at_seconds DESC, created_at_nanos DESC, id DESC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toReport())
            }
        }
    }

    override suspend fun queryMarkers(query: ReportMapQuery): ReportMapPage = withOpenRepository {
        val selection = mapSelection(query)
        ensureActive()
        val database = databaseForRead()
        ensureActive()
        database.query(
            TABLE,
            MAP_PROJECTION,
            selection.selection,
            selection.arguments.toTypedArray(),
            null,
            null,
            "created_at_seconds DESC, created_at_nanos DESC, id DESC",
            (query.limit + 1).toString(),
        ).use { cursor ->
            val markers = buildList {
                while (cursor.moveToNext()) {
                    ensureActive()
                    add(cursor.toMapMarker())
                }
            }
            ensureActive()
            ReportMapPage(markers.take(query.limit), hasMore = markers.size > query.limit)
        }
    }

    override suspend fun listPage(query: ReportListPageQuery): ReportListPage = withOpenRepository {
        val selection = listPageSelection(query)
        ensureActive()
        val database = databaseForRead()
        ensureActive()
        database.query(
            TABLE,
            PROJECTION,
            selection.selection,
            selection.arguments.toTypedArray(),
            null,
            null,
            REPORT_LIST_PAGE_ORDER,
            (query.limit + 1).toString(),
        ).use { cursor ->
            val reports = buildList {
                while (cursor.moveToNext()) {
                    ensureActive()
                    add(cursor.toReport())
                }
            }
            ensureActive()
            ReportListPage(reports.take(query.limit), hasMore = reports.size > query.limit)
        }
    }

    override suspend fun findSimilar(query: SimilarReportQuery): SimilarReportPage = withOpenRepository {
        val region = query.region
        val longitudeRange = if (region.crossesAntimeridian) "longitude >= ? OR longitude <= ?" else "longitude BETWEEN ? AND ?"
        val longitudeSelection = if (region.includesAntimeridian) "($longitudeRange OR longitude IN (-180.0, 180.0))" else "($longitudeRange)"
        val selections = mutableListOf(
            "incident_type = ?",
            "typeof(latitude) IN ('real', 'integer')",
            "typeof(longitude) IN ('real', 'integer')",
            "latitude BETWEEN ? AND ?",
            longitudeSelection,
            "(occurred_at_seconds > ? OR (occurred_at_seconds = ? AND occurred_at_nanos >= ?))",
            "(occurred_at_seconds < ? OR (occurred_at_seconds = ? AND occurred_at_nanos <= ?))",
        )
        val arguments = mutableListOf(
            query.type.name, region.south.toString(), region.north.toString(), region.west.toString(), region.east.toString(),
            query.earliest.epochSecond.toString(), query.earliest.epochSecond.toString(), query.earliest.nano.toString(),
            query.latest.epochSecond.toString(), query.latest.epochSecond.toString(), query.latest.nano.toString(),
        )
        query.excludedId?.let { excludedId ->
            selections += "id <> ?"
            arguments += excludedId
        }
        ensureActive()
        val database = databaseForRead()
        ensureActive()
        database.query(
            TABLE, PROJECTION, selections.joinToString(" AND "), arguments.toTypedArray(), null, null,
            "occurred_at_seconds DESC, occurred_at_nanos DESC, id DESC", (SimilarReportQuery.MAX_CANDIDATES + 1).toString(),
        ).use { cursor ->
            val matches = mutableListOf<SimilarReportMatch>()
            var candidates = 0
            var truncatedCandidates = false
            while (cursor.moveToNext()) {
                ensureActive()
                if (candidates == SimilarReportQuery.MAX_CANDIDATES) {
                    truncatedCandidates = true
                    break
                }
                candidates++
                val distance = query.distanceTo(cursor.getOptionalDouble("latitude"), cursor.getOptionalDouble("longitude"))
                if (distance != null && query.includesDistance(distance)) {
                    // Fallar ante metadatos dañados evita ocultar un error como una búsqueda vacía.
                    matches += SimilarReportMatch(cursor.toReport(), distance)
                }
            }
            ensureActive()
            val ordered = matches.sortedWith(compareBy<SimilarReportMatch> { it.distanceMeters }
                .thenByDescending { it.report.occurredAt }.thenByDescending { it.report.id })
            SimilarReportPage(ordered.take(SimilarReportQuery.MAX_MATCHES), truncatedCandidates || ordered.size > SimilarReportQuery.MAX_MATCHES)
        }
    }

    /** Cierre idempotente: espera cualquier operación activa y libera la conexión. */
    override fun close() {
        synchronized(lock) {
            if (!closed) {
                helper.close()
                closed = true
            }
        }
    }

    private suspend fun <T> withOpenRepository(action: CoroutineScope.() -> T): T = withContext(ioDispatcher) {
        synchronized(lock) {
            // Esperar el monitor no comprueba la cancelación de la corrutina.
            ensureActive()
            check(!closed) { "El repositorio de reportes está cerrado." }
            action()
        }
    }

    /** Solo se llaman bajo [lock], después de comprobar que el repositorio continúa abierto. */
    private fun databaseForRead(): SQLiteDatabase = helper.readableDatabase.also { databaseWasOpened = true }

    private fun databaseForWrite(): SQLiteDatabase = helper.writableDatabase.also { databaseWasOpened = true }

    private fun Report.toValues() = ContentValues().apply {
        put("id", id)
        put("incident_type", type.name)
        put("priority", priority.name)
        put("description", description)
        put("occurred_at_seconds", occurredAt.epochSecond)
        put("occurred_at_nanos", occurredAt.nano)
        put("location_reference", location.reference)
        put("latitude", location.latitude)
        put("longitude", location.longitude)
        put("created_at_seconds", createdAt.epochSecond)
        put("created_at_nanos", createdAt.nano)
        put("status", status.name)
        put("photo_id", photo?.id)
        put("photo_sha256", photo?.sha256)
        put("photo_byte_count", photo?.byteCount)
        put("photo_width", photo?.width)
        put("photo_height", photo?.height)
    }

    private fun Cursor.toReport() = Report(
        id = getString(getColumnIndexOrThrow("id")),
        type = IncidentType.valueOf(getString(getColumnIndexOrThrow("incident_type"))),
        priority = Priority.valueOf(getString(getColumnIndexOrThrow("priority"))),
        description = getString(getColumnIndexOrThrow("description")),
        occurredAt = getInstant("occurred_at"),
        location = ReportLocation(
            reference = getString(getColumnIndexOrThrow("location_reference")),
            latitude = getOptionalDouble("latitude"),
            longitude = getOptionalDouble("longitude"),
        ),
        createdAt = getInstant("created_at"),
        status = ReportStatus.valueOf(getString(getColumnIndexOrThrow("status"))),
        photo = getPhoto(),
    )

    private fun Cursor.getPhoto(): ReportPhoto? {
        val columns = arrayOf("photo_id", "photo_sha256", "photo_byte_count", "photo_width", "photo_height")
        val indices = columns.map(::getColumnIndexOrThrow)
        if (indices.all(::isNull)) return null
        check(indices.none(::isNull)) { "La fotografía guardada contiene metadatos incompletos." }
        check(getType(indices[0]) == Cursor.FIELD_TYPE_STRING && getType(indices[1]) == Cursor.FIELD_TYPE_STRING &&
            indices.drop(2).all { getType(it) == Cursor.FIELD_TYPE_INTEGER }
        ) { "La fotografía guardada contiene tipos de metadatos inválidos." }
        val width = getLong(indices[3])
        val height = getLong(indices[4])
        require(width in 1..ReportPhoto.MAX_DIMENSION.toLong() && height in 1..ReportPhoto.MAX_DIMENSION.toLong()) {
            "La fotografía guardada contiene dimensiones inválidas."
        }
        return ReportPhoto(
            id = getString(indices[0]), sha256 = getString(indices[1]), byteCount = getLong(indices[2]),
            width = width.toInt(), height = height.toInt(),
        )
    }

    private fun Cursor.toMapMarker() = ReportMapMarker(
        id = getString(getColumnIndexOrThrow("id")),
        type = IncidentType.valueOf(getString(getColumnIndexOrThrow("incident_type"))),
        priority = Priority.valueOf(getString(getColumnIndexOrThrow("priority"))),
        status = ReportStatus.valueOf(getString(getColumnIndexOrThrow("status"))),
        latitude = getDouble(getColumnIndexOrThrow("latitude")),
        longitude = getDouble(getColumnIndexOrThrow("longitude")),
    )

    private fun Cursor.getInstant(prefix: String): Instant = Instant.ofEpochSecond(
        getLong(getColumnIndexOrThrow("${prefix}_seconds")),
        getLong(getColumnIndexOrThrow("${prefix}_nanos")),
    )

    private fun Cursor.getOptionalDouble(column: String): Double? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getDouble(index)
    }

    /** SQLite conserva cero sin signo; el reporte devuelto usa la misma representación. */
    private fun Double?.canonicalizeZero(): Double? = this?.let { value ->
        if (value == 0.0) 0.0 else value
    }

    companion object {
        const val DATABASE_NAME = "citizensecurity_local_v1.db"
        private const val TABLE = "reports"
        private val PROJECTION = arrayOf(
            "id", "incident_type", "priority", "description", "occurred_at_seconds",
            "occurred_at_nanos", "location_reference", "latitude", "longitude",
            "created_at_seconds", "created_at_nanos", "status",
            "photo_id", "photo_sha256", "photo_byte_count", "photo_width", "photo_height",
        )
        private val MAP_PROJECTION = arrayOf(
            "id", "incident_type", "priority", "status", "latitude", "longitude",
        )
    }
}
