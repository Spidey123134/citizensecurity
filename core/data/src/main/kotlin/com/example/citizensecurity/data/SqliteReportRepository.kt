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
import com.example.citizensecurity.domain.ReportMapMarker
import com.example.citizensecurity.domain.ReportMapPage
import com.example.citizensecurity.domain.ReportMapQuery
import com.example.citizensecurity.domain.ReportMapRepository
import com.example.citizensecurity.domain.ReportRequestConflictException
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportValidator
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
) : IdempotentReportRepository, ReportMapRepository, Closeable {

    private val lock = Any()
    private val validator = ReportValidator()
    private val helper: ReportDatabase
    private val databaseFile: java.io.File?
    private var databaseWasOpened = false
    private var closed = false

    init {
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
            database.insertOrThrow(TABLE, null, report.toValues())
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
            existing.location != draft.location
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
        val region = query.region
        val arguments = mutableListOf(region.south.toString(), region.north.toString())
        val longitudeRange = if (region.west <= region.east) {
            "longitude BETWEEN ? AND ?"
        } else {
            "longitude >= ? OR longitude <= ?"
        }
        arguments += region.west.toString()
        arguments += region.east.toString()
        val longitudeSelection = if (region.includesAntimeridian) {
            "($longitudeRange OR longitude IN (-180.0, 180.0))"
        } else {
            "($longitudeRange)"
        }
        val selections = mutableListOf(
            "latitude IS NOT NULL",
            "longitude IS NOT NULL",
            "latitude BETWEEN ? AND ?",
            longitudeSelection,
        )
        if (query.types.isNotEmpty()) {
            selections += "incident_type IN (${query.types.joinToString { "?" }})"
            arguments += query.types.map { it.name }.sorted()
        }
        if (query.statuses.isNotEmpty()) {
            selections += "status IN (${query.statuses.joinToString { "?" }})"
            arguments += query.statuses.map { it.name }.sorted()
        }
        ensureActive()
        val database = databaseForRead()
        ensureActive()
        database.query(
            TABLE,
            MAP_PROJECTION,
            selections.joinToString(" AND "),
            arguments.toTypedArray(),
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
    )

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
        )
        private val MAP_PROJECTION = arrayOf(
            "id", "incident_type", "priority", "status", "latitude", "longitude",
        )
    }
}
