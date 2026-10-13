package com.example.citizensecurity.data

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Ejecuta esquema/insert/lookup reales en SQLite del host; no acredita el repositorio Android. */
class AccountSqlStorageTest {
    @Test fun reaperturaConservaIdUsuarioYVerificadorSinTextoPlano() = execute("reopen")
    @Test fun usuarioDuplicadoNoSustituyeIdentidadNiVerificador() = execute("unique")
    @Test fun esquemaRechazaIdentidadesUsuariosYLongitudesNoCanonicos() = execute("constraints")
    @Test fun rollbackRetiraSoloLaInsercionPendienteYConservaLaAnterior() = execute("rollback")
    @Test fun usuarioInexistenteNoCambiaFilasEsquemaOVersion() = execute("missing")
    @Test fun parametroConComillasSeComparaLiteralSinExponerOtrasCuentas() = execute("literal")

    private fun execute(scenario: String) {
        val python = System.getenv("REPORT_SQLITE_TEST_PYTHON").orEmpty()
        assumeTrue("Set REPORT_SQLITE_TEST_PYTHON to run the host SQLite integration tests.", python.isNotBlank())
        assertFalse(AccountSql.INSERT.contains("REPLACE", ignoreCase = true))
        assertTrue(AccountSql.LOOKUP.contains("username = ?"))
        val directory = Files.createTempDirectory("account-sqlite-")
        try {
            val script = directory.resolve("storage.py")
            requireNotNull(javaClass.getResourceAsStream("/sqlite_account_storage.py")).use { Files.copy(it, script) }
            val input = directory.resolve("input.txt")
            Files.writeString(input, listOf(scenario, AccountSql.CREATE_TABLE, AccountSql.INSERT,
                AccountSql.LOOKUP, AccountPasswordVerifier.MISSING_ACCOUNT_VERIFIER)
                .joinToString("\n") { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) })
            val output = directory.resolve("output.txt")
            val process = ProcessBuilder(python, script.toString(), input.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start()
            try {
                assertTrue("SQLite test process timed out.", process.waitFor(10, TimeUnit.SECONDS))
                val result = Files.readString(output)
                assertEquals(result, 0, process.exitValue())
                assertEquals("ok:$scenario", result.trim())
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                    process.waitFor(5, TimeUnit.SECONDS)
                }
            }
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder<Path>()).forEach { Files.deleteIfExists(it) }
            }
        }
    }
}
