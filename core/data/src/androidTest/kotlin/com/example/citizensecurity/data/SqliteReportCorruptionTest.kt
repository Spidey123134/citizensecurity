package com.example.citizensecurity.data

import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Archivo deliberadamente dañado y aislado; nunca abre una base de usuarios. */
@RunWith(AndroidJUnit4::class)
class SqliteReportCorruptionTest {
    @Test
    fun aperturasFallidasConservanElArchivoCorruptoSinRecrearlo() {
        withCorruptFixture { context, name, bytes ->
            repeat(2) {
                val helper = ReportDatabase(context, name)
                try {
                    try { helper.readableDatabase; fail("Debe rechazar el archivo dañado.") }
                    catch (_: SQLiteDatabaseCorruptException) { }
                } finally { helper.close() }
                val file = context.getDatabasePath(name)
                assertTrue(file.exists())
                assertArrayEquals(bytes, file.readBytes())
            }
        }
    }

    @Test
    fun consultarNoBorraNiReemplazaUnaBaseCorrupta() = runBlocking {
        withCorruptFixture { context, name, bytes ->
            SqliteReportRepository(context, name).use { repository ->
                runBlocking {
                    repeat(2) {
                        try { repository.list(); fail("No debe presentar una base nueva vacía.") }
                        catch (_: SQLiteDatabaseCorruptException) { }
                        assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
                    }
                }
            }
        }
    }

    @Test
    fun crearNoReemplazaUnaBaseCorruptaConUnReporteNuevo() = runBlocking {
        withCorruptFixture { context, name, bytes ->
            val now = Instant.parse("2026-10-03T03:00:00Z")
            SqliteReportRepository(context, name, Clock.fixed(now, ZoneOffset.UTC)).use { repository ->
                runBlocking {
                    val draft = NewReport(
                        IncidentType.RISK, Priority.LOW, "Hay una luminaria dañada junto al parque.",
                        now.minusSeconds(10), ReportLocation("Frente al parque central"),
                    )
                    try { repository.create(draft); fail("Debe conservar y rechazar la base dañada.") }
                    catch (_: SQLiteDatabaseCorruptException) { }
                    assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
                }
            }
        }
    }

    private fun withCorruptFixture(action: (Context, String, ByteArray) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "citizensecurity_corrupt_test_${UUID.randomUUID()}.db"
        val bytes = ByteArray(4096) { 0x5a.toByte() }
        val file = context.getDatabasePath(name)
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        check(!file.exists())
        file.writeBytes(bytes)
        try { action(context, name, bytes) }
        finally {
            check(name.matches(Regex("citizensecurity_corrupt_test_[0-9a-f-]{36}\\.db")))
            context.deleteDatabase(name)
        }
    }
}
