package com.example.citizensecurity.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportRepository
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportValidator
import java.io.Closeable
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persistencia local de reportes. La capa de aplicación es dueña de esta instancia y debe
 * cerrarla al terminar su uso. Conserva solo el contexto de aplicación; no retiene una actividad.
 * Las operaciones suspendidas hacen su trabajo de SQLite en [ioDispatcher].
 *
 * El nombre predeterminado corresponde exclusivamente al proyecto nuevo. [databaseName] permite
 * aislar archivos durante las pruebas. Después de [close], la instancia no se puede reutilizar;
 * se crea otra instancia para reabrir el mismo archivo.
 */
class SqliteReportRepository(
    context: Context,
    databaseName: String = DATABASE_NAME,
    private val clock: Clock = Clock.systemUTC(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ReportRepository, Closeable {

    private val lock = Any()
    private val validator = ReportValidator()
    private val helper: ReportDatabase
    private var closed = false

    init {
        require(
            databaseName.isNotBlank() &&
                databaseName.endsWith(".db") &&
                '/' !in databaseName && '\\' !in databaseName,
        ) { "El nombre de la base debe ser un archivo .db sin una ruta." }
        helper = ReportDatabase(context.applicationContext, databaseName)
    }

    override suspend fun create(draft: NewReport): Report = withOpenRepository {
        val normalized = draft.copy(
            description = draft.description.trim(),
            location = draft.location.copy(
                reference = draft.location.reference.trim(),
                latitude = draft.location.latitude.canonicalizeZero(),
                longitude = draft.location.longitude.canonicalizeZero(),
            ),
        )
        val createdAt = clock.instant()
        val errors = validator.validate(normalized, createdAt)
        if (!errors.isValid) throw InvalidReportException(errors)

        val report = Report(
            id = UUID.randomUUID().toString(),
            type = normalized.type,
            priority = normalized.priority,
            description = normalized.description,
            occurredAt = normalized.occurredAt,
            location = normalized.location,
            createdAt = createdAt,
            status = ReportStatus.REPORTED,
        )
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            database.insertOrThrow(TABLE, null, report.toValues())
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        report
    }

    override suspend fun findById(id: String): Report? = withOpenRepository {
        helper.readableDatabase.query(
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
    }

    override suspend fun list(): List<Report> = withOpenRepository {
        helper.readableDatabase.query(
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

    /** Cierre idempotente: espera cualquier operación activa y libera la conexión. */
    override fun close() {
        synchronized(lock) {
            if (!closed) {
                helper.close()
                closed = true
            }
        }
    }

    private suspend fun <T> withOpenRepository(action: () -> T): T = withContext(ioDispatcher) {
        synchronized(lock) {
            check(!closed) { "El repositorio de reportes está cerrado." }
            action()
        }
    }

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
    }
}
