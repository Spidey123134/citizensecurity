package com.example.citizensecurity.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.AccountAlreadyExistsException
import com.example.citizensecurity.domain.AccountValidationException
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Fixtures privadas por prueba; no abre cuentas, fotos o reportes del usuario. */
@RunWith(AndroidJUnit4::class)
class SqliteAccountRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val password = "Una frase local de fixture 🧭"

    @Test fun reaperturaConservaIdentidadYFallosNoCambianFilas() = runBlocking {
        withFixture { name ->
            val account = SqliteAccountRepository(context, name).use { it.register(" ANA_123 ", password) }
            val before = snapshot(name)
            SqliteAccountRepository(context, name).use { repository ->
                assertEquals(account, repository.authenticate("ana_123", password))
                assertNull(repository.authenticate("ana_123", "Otra frase de fixture incorrecta"))
                assertNull(repository.authenticate("usuario_inexistente", password))
                assertEquals(before, snapshot(name))
            }
            assertEquals("ana_123", account.username)
            val verifier = before.single()[2]
            assertFalse(verifier.contains(password))
            assertTrue(verifier.startsWith("pbkdf2-sha256:v1:600000:"))
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
                assertEquals(1, it.version)
            }
        }
    }

    @Test fun duplicadoConMayusculasNoSustituyeIdentidadNiContrasena() = runBlocking {
        withFixture { name ->
            SqliteAccountRepository(context, name).use { repository ->
                val original = repository.register("ana", password)
                val before = snapshot(name)
                try { repository.register(" ANA ", "Otra frase local de fixture"); fail("Debe rechazar duplicados.") }
                catch (_: AccountAlreadyExistsException) { }
                assertEquals(before, snapshot(name))
                assertEquals(original, repository.authenticate("ANA", password))
                assertNull(repository.authenticate("ana", "Otra frase local de fixture"))
            }
        }
    }

    @Test fun credencialesInvalidasYUsuarioInexistenteNoCreanArchivo() = runBlocking {
        withFixture { name ->
            SqliteAccountRepository(context, name).use { repository ->
                try { repository.register("admin", password); fail("admin no es una cuenta real.") }
                catch (_: AccountValidationException) { }
                try { repository.register("ana", "corta"); fail("Debe exigir una frase larga.") }
                catch (_: AccountValidationException) { }
                assertNull(repository.authenticate("ana", password))
                assertFalse(context.getDatabasePath(name).exists())
            }
        }
    }

    @Test fun dosInstanciasConcurrentesSoloCreanUnaCuenta() = runBlocking {
        withFixture { name ->
            SqliteAccountRepository(context, name).use { first ->
                SqliteAccountRepository(context, name).use { second ->
                    val results = listOf(first, second).map { repository ->
                        async(Dispatchers.IO) {
                            try { repository.register("ana", password); "registered" }
                            catch (_: AccountAlreadyExistsException) { "duplicate" }
                        }
                    }.awaitAll()
                    assertEquals(1, results.count { it == "registered" })
                    assertEquals(1, results.count { it == "duplicate" })
                    assertEquals(1, snapshot(name).size)
                    assertEquals(first.authenticate("ana", password), second.authenticate("ana", password))
                }
            }
        }
    }

    @Test fun cancelacionTrasKdfAntesDeInsertarNoCreaArchivo() = runBlocking {
        withFixture { name ->
            lateinit var operation: Job
            val actual = AccountPasswordVerifier()
            val cancelsAfterDerivation = object : AccountPasswordDerivation by actual {
                override fun create(password: String): String = actual.create(password).also { operation.cancel() }
            }
            SqliteAccountRepository(context, name, Dispatchers.IO, cancelsAfterDerivation).use { repository ->
                operation = launch(start = CoroutineStart.LAZY) { repository.register("ana", password) }
                operation.start()
                operation.join()
                assertTrue(operation.isCancelled)
                assertFalse(context.getDatabasePath(name).exists())
            }
        }
    }

    @Test fun verificadorDanadoFallaSinModificarLaCuenta() = runBlocking {
        withFixture { name ->
            SqliteAccountRepository(context, name).use { it.register("ana", password) }
            AccountDatabase(context, name).use { helper ->
                helper.writableDatabase.update("local_accounts", ContentValues().apply {
                    put("password_verifier", "pbkdf2-sha256:v1:2147483647:bad:bad")
                }, "username = ?", arrayOf("ana"))
            }
            val before = snapshot(name)
            SqliteAccountRepository(context, name).use { repository ->
                try { repository.authenticate("ana", password); fail("No debe ejecutar parámetros dañados.") }
                catch (_: AccountRecordCorruptException) { }
            }
            assertEquals(before, snapshot(name))
        }
    }

    @Test fun cerrarImpideReutilizarPeroOtraInstanciaConservaLaCuenta() = runBlocking {
        withFixture { name ->
            val repository = SqliteAccountRepository(context, name)
            val account = repository.register("ana", password)
            repository.close()
            repository.close()
            try { repository.authenticate("ana", password); fail("La instancia cerrada no es reutilizable.") }
            catch (_: IllegalStateException) { }
            SqliteAccountRepository(context, name).use { assertEquals(account, it.authenticate("ana", password)) }
        }
    }

    @Test fun archivoCorruptoNoSeBorraNiSeRecreaAlConsultar() = runBlocking {
        withFixture { name ->
            val bytes = ByteArray(4096) { 0x5a.toByte() }
            val file = context.getDatabasePath(name)
            check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
            file.writeBytes(bytes)
            SqliteAccountRepository(context, name).use { repository ->
                repeat(2) {
                    try { repository.authenticate("ana", password); fail("Debe fallar conservando el archivo.") }
                    catch (_: SQLiteDatabaseCorruptException) { }
                    assertTrue(file.exists())
                    assertArrayEquals(bytes, file.readBytes())
                }
            }
        }
    }

    @Test fun cuentasSeparadasConservanLosReportesYEsquema2() = runBlocking {
        withFixture { accountsName ->
            val reportsName = "citizensecurity_accounts_test_${UUID.randomUUID()}_reports.db"
            val now = Instant.parse("2026-10-12T18:00:00Z")
            try {
                val report = SqliteReportRepository(context, reportsName, Clock.fixed(now, ZoneOffset.UTC)).use {
                    it.create(NewReport(IncidentType.RISK, Priority.LOW,
                        "Una luminaria rota frente al parque de la fixture.", now.minusSeconds(10),
                        ReportLocation("Frente al parque central")))
                }
                SqliteAccountRepository(context, accountsName).use { it.register("ana", password) }
                SqliteReportRepository(context, reportsName).use { assertEquals(report, it.findById(report.id)) }
                SQLiteDatabase.openDatabase(context.getDatabasePath(reportsName).path, null, SQLiteDatabase.OPEN_READONLY).use {
                    assertEquals(2, it.version)
                }
            } finally {
                check(reportsName.matches(Regex("citizensecurity_accounts_test_[0-9a-f-]{36}_reports\\.db")))
                context.deleteDatabase(reportsName)
            }
        }
    }

    private fun snapshot(name: String): List<List<String>> =
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.query("local_accounts", arrayOf("id", "username", "password_verifier"),
                null, null, null, null, "username ASC").use { cursor ->
                buildList { while (cursor.moveToNext()) add(List(3) { cursor.getString(it) }) }
            }
        }

    private suspend fun withFixture(action: suspend (String) -> Unit) {
        val name = "citizensecurity_accounts_test_${UUID.randomUUID()}.db"
        try { action(name) }
        finally {
            check(name.matches(Regex("citizensecurity_accounts_test_[0-9a-f-]{36}\\.db")))
            context.deleteDatabase(name)
        }
    }
}
