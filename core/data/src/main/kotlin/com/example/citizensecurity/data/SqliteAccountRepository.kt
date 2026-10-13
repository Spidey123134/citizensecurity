package com.example.citizensecurity.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.example.citizensecurity.domain.AccountAlreadyExistsException
import com.example.citizensecurity.domain.AccountCredentialsValidator
import com.example.citizensecurity.domain.AccountRepository
import com.example.citizensecurity.domain.AccountValidationException
import com.example.citizensecurity.domain.LocalAccount
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Cuentas en este teléfono. No concede roles, no mantiene sesión ni identifica reportes. */
class SqliteAccountRepository internal constructor(
    context: Context,
    databaseName: String?,
    private val ioDispatcher: CoroutineDispatcher,
    private val verifier: AccountPasswordDerivation,
) : AccountRepository, Closeable {
    constructor(
        context: Context,
        databaseName: String? = DATABASE_NAME,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(context, databaseName, ioDispatcher, AccountPasswordVerifier())

    private val lock = Any()
    private val helper: AccountDatabase
    private val databaseFile: java.io.File?
    private var databaseWasOpened = false
    private var closed = false

    init {
        require(databaseName == null || (
            databaseName.isNotBlank() && databaseName.endsWith(".db") &&
                '/' !in databaseName && '\\' !in databaseName &&
                !databaseName.equals(SqliteReportRepository.DATABASE_NAME, ignoreCase = true)
            )) { "La base de cuentas requiere un archivo .db propio, sin ruta." }
        databaseFile = databaseName?.let { context.applicationContext.getDatabasePath(it) }
        helper = AccountDatabase(context, databaseName)
    }

    override suspend fun register(username: String, password: String): LocalAccount = withRepository {
        val normalized = validatedUsername(username, password)
        // El KDF es bloqueante, pero siempre se ejecuta fuera de Main y se comprueba
        // cancelación de nuevo antes de abrir SQLite y antes de cada escritura.
        val encoded = verifier.create(password)
        ensureActive()
        val account = LocalAccount(UUID.randomUUID().toString(), normalized)
        val database = databaseForWrite()
        ensureActive()
        database.beginTransaction()
        try {
            if (findAccount(database, normalized) != null) throw AccountAlreadyExistsException()
            ensureActive()
            try {
                database.compileStatement(AccountSql.INSERT).use { statement ->
                    statement.bindString(1, account.id)
                    statement.bindString(2, account.username)
                    statement.bindString(3, encoded)
                    statement.executeInsert()
                }
            } catch (failure: SQLiteConstraintException) {
                // UNIQUE protege también a dos instancias: no sustituye el usuario existente.
                if (findAccount(database, normalized) != null) throw AccountAlreadyExistsException()
                throw failure
            }
            ensureActive()
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        account
    }

    override suspend fun authenticate(username: String, password: String): LocalAccount? = withRepository {
        val normalized = validatedUsername(username, password)
        val stored = if (databaseWasOpened || databaseFile?.exists() == true) {
            findAccount(databaseForRead(), normalized)
        } else null
        ensureActive()
        val matches = verifier.matches(password, stored?.encoded ?: AccountPasswordVerifier.MISSING_ACCOUNT_VERIFIER)
        ensureActive()
        if (matches) stored?.account else null
    }

    private fun validatedUsername(username: String, password: String): String {
        val errors = AccountCredentialsValidator.validate(username, password)
        if (!errors.isValid) throw AccountValidationException(errors)
        return AccountCredentialsValidator.normalizeUsername(username)
    }

    private fun findAccount(database: SQLiteDatabase, username: String): StoredAccount? =
        database.rawQuery(AccountSql.LOOKUP, arrayOf(username)).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val id = cursor.getString(0) ?: throw AccountRecordCorruptException()
            val storedUsername = cursor.getString(1) ?: throw AccountRecordCorruptException()
            val account = try { LocalAccount(id, storedUsername) }
            catch (_: IllegalArgumentException) { throw AccountRecordCorruptException() }
            if (account.username != username) throw AccountRecordCorruptException()
            val encoded = cursor.getString(2) ?: throw AccountRecordCorruptException()
            verifier.validate(encoded)
            StoredAccount(account, encoded)
        }

    private fun databaseForRead(): SQLiteDatabase = helper.readableDatabase.also { databaseWasOpened = true }
    private fun databaseForWrite(): SQLiteDatabase = helper.writableDatabase.also { databaseWasOpened = true }

    private suspend fun <T> withRepository(block: CoroutineScope.() -> T): T = withContext(ioDispatcher) {
        synchronized(lock) {
            check(!closed) { "La base local de cuentas está cerrada." }
            ensureActive()
            block()
        }
    }

    override fun close() = synchronized(lock) {
        if (!closed) { closed = true; helper.close() }
    }

    private class StoredAccount(val account: LocalAccount, val encoded: String)

    companion object {
        const val DATABASE_NAME = "citizensecurity_accounts.db"
    }
}
