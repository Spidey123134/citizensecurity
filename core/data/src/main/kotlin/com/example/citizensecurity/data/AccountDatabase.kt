package com.example.citizensecurity.data

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper

/** Base privada independiente. Nunca migra, reemplaza o abre la base histórica de reportes. */
internal class AccountDatabase(context: Context, databaseName: String?) :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, 1, PRESERVE_CORRUPTION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(AccountSql.CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException("No hay una migración de cuentas de la versión $oldVersion a $newVersion.")
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw SQLiteException("No se admite reducir la versión de la base local de cuentas.")
    }

    private companion object {
        val PRESERVE_CORRUPTION = DatabaseErrorHandler { database ->
            try { database.close() } catch (_: SQLiteException) { }
            throw SQLiteDatabaseCorruptException("La base de cuentas está dañada. No se ha eliminado ni recreado.")
        }
    }
}
