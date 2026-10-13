package com.example.citizensecurity.data

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper

/** Base local propia; un nombre nulo crea una base en memoria que se descarta al cerrar. */
internal class ReportDatabase(context: Context, databaseName: String?) :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, VERSION, PRESERVE_CORRUPTION) {

    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE reports (
                id TEXT NOT NULL PRIMARY KEY CHECK (length(id) = 36),
                incident_type TEXT NOT NULL CHECK (
                    incident_type IN ('EMERGENCY', 'THEFT', 'ACCIDENT', 'FIRE', 'RISK', 'OTHER')
                ),
                priority TEXT NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
                description TEXT NOT NULL CHECK (
                    length(description) BETWEEN 10 AND 1000 AND instr(description, char(0)) = 0
                ),
                occurred_at_seconds INTEGER NOT NULL CHECK (occurred_at_seconds >= 0),
                occurred_at_nanos INTEGER NOT NULL CHECK (
                    occurred_at_nanos BETWEEN 0 AND 999999999
                ),
                location_reference TEXT NOT NULL DEFAULT '' CHECK (
                    (length(location_reference) = 0 OR
                        length(location_reference) BETWEEN 5 AND 200)
                    AND instr(location_reference, char(0)) = 0
                ),
                latitude REAL,
                longitude REAL,
                created_at_seconds INTEGER NOT NULL,
                created_at_nanos INTEGER NOT NULL CHECK (
                    created_at_nanos BETWEEN 0 AND 999999999
                ),
                status TEXT NOT NULL CHECK (
                    status IN ('REPORTED', 'IN_REVIEW', 'ATTENDED', 'CLOSED')
                ),
                ${PHOTO_COLUMNS.joinToString(",\n                ")},
                CHECK (
                    (latitude IS NULL AND longitude IS NULL AND length(location_reference) > 0)
                    OR
                    (latitude IS NOT NULL AND longitude IS NOT NULL
                        AND latitude BETWEEN -90.0 AND 90.0
                        AND longitude BETWEEN -180.0 AND 180.0)
                ),
                CHECK (
                    occurred_at_seconds < created_at_seconds + 60 OR
                    (occurred_at_seconds = created_at_seconds + 60
                        AND occurred_at_nanos <= created_at_nanos)
                )
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX reports_created_at_idx
                ON reports (created_at_seconds DESC, created_at_nanos DESC, id DESC)
            """.trimIndent(),
        )
        db.execSQL(ReportHistorySql.CREATE_TABLE)
    }

    /** Additive upgrades preserve every row; unknown earlier history is never reconstructed. */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion !in 1..2 || newVersion != VERSION) {
            throw SQLiteException(
                "No hay una migración de reportes de la versión $oldVersion a $newVersion.",
            )
        }
        // SQLiteOpenHelper ejecuta este bloque dentro de una transacción. Si falla cualquier
        // columna/constraint, las filas y la versión anterior permanecen íntegramente.
        if (oldVersion == 1) {
            PHOTO_COLUMNS.forEach { declaration -> db.execSQL("ALTER TABLE reports ADD COLUMN $declaration") }
        }
        db.execSQL(ReportHistorySql.CREATE_TABLE)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException(
            "No se admite reducir la base de reportes de la versión $oldVersion a $newVersion.",
        )
    }

    private companion object {
        const val VERSION = 3
        val PHOTO_COLUMNS = listOf(
            """
            photo_id TEXT CHECK (photo_id IS NULL OR (
                length(photo_id) = 36 AND photo_id = lower(photo_id)
                AND substr(photo_id, 9, 1) = '-' AND substr(photo_id, 14, 1) = '-'
                AND substr(photo_id, 19, 1) = '-' AND substr(photo_id, 24, 1) = '-'
                AND length(replace(photo_id, '-', '')) = 32
                AND photo_id NOT GLOB '*[^0-9a-f-]*'
            ))
            """.trimIndent(),
            """
            photo_sha256 TEXT CHECK (photo_sha256 IS NULL OR (
                length(photo_sha256) = 64 AND photo_sha256 NOT GLOB '*[^0-9a-f]*'
            ))
            """.trimIndent(),
            """
            photo_byte_count INTEGER CHECK (photo_byte_count IS NULL OR (
                typeof(photo_byte_count) = 'integer' AND photo_byte_count BETWEEN 1 AND 6291456
            ))
            """.trimIndent(),
            """
            photo_width INTEGER CHECK (photo_width IS NULL OR (
                typeof(photo_width) = 'integer' AND photo_width BETWEEN 1 AND 2048
            ))
            """.trimIndent(),
            """
            photo_height INTEGER CHECK (
                (photo_height IS NULL OR (typeof(photo_height) = 'integer' AND photo_height BETWEEN 1 AND 2048))
                AND (
                    (photo_id IS NULL AND photo_sha256 IS NULL AND photo_byte_count IS NULL
                        AND photo_width IS NULL AND photo_height IS NULL)
                    OR
                    (photo_id IS NOT NULL AND photo_sha256 IS NOT NULL AND photo_byte_count IS NOT NULL
                        AND photo_width IS NOT NULL AND photo_height IS NOT NULL)
                )
            )
            """.trimIndent(),
        )
        // El manejador predeterminado borra la base corrupta. Fallar impide ese borrado y
        // el reintento que podría recrearla vacía; reparar datos requiere otro alcance.
        val PRESERVE_CORRUPTION = DatabaseErrorHandler { database ->
            try {
                database.close()
            } catch (_: SQLiteException) {
                // Cerrar puede fallar por la propia corrupción; nunca se eliminan archivos.
            }
            throw SQLiteDatabaseCorruptException(
                "La base local contiene datos dañados. No se ha eliminado ni recreado.",
            )
        }
    }
}
