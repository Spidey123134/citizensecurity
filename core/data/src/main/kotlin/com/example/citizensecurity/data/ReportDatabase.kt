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
    }

    /**
     * Al aumentar VERSION se debe implementar cada paso de migración aquí.
     * Hasta entonces, una versión inesperada falla conservando las tablas y sus datos.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException(
            "No hay una migración de reportes de la versión $oldVersion a $newVersion.",
        )
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException(
            "No se admite reducir la base de reportes de la versión $oldVersion a $newVersion.",
        )
    }

    private companion object {
        const val VERSION = 1
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
