package com.example.citizensecurity.data

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportStatus
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TemporaryReportSaveExampleTest {

    @Test
    fun ejemploGuardaSoloDatosFicticiosSinAbrirArchivos(): Unit = runBlocking {
        val context = DatabaseGuardContext(ApplicationProvider.getApplicationContext())
        val filesBefore = context.databaseList().toSet()
        val saved = TemporaryReportSaveExample(context, CLOCK).run()

        assertEquals(IncidentType.RISK, saved.type)
        assertEquals(Priority.MEDIUM, saved.priority)
        assertEquals("Ejemplo ficticio: luminaria dañada frente al parque.", saved.description)
        assertEquals(NOW.minusSeconds(30), saved.occurredAt)
        assertEquals(NOW, saved.createdAt)
        assertEquals(ReportStatus.REPORTED, saved.status)
        assertEquals(
            ReportLocation("Parque de ejemplo", latitude = 19.4326077, longitude = -99.1332088),
            saved.location,
        )
        assertEquals(saved.id, UUID.fromString(saved.id).toString())
        assertEquals(0, context.fileAccessCount)
        assertEquals(filesBefore, context.databaseList().toSet())
    }

    @Test
    fun baseEnMemoriaSePierdeAlCerrarYNoMezclaInstancias(): Unit = runBlocking {
        val context = DatabaseGuardContext(ApplicationProvider.getApplicationContext())
        val filesBefore = context.databaseList().toSet()
        val saved = SqliteReportRepository(context, databaseName = null, clock = CLOCK).use { first ->
            val report = first.create(sentinelDraft())
            SqliteReportRepository(context, databaseName = null, clock = CLOCK).use { second ->
                assertTrue(second.list().isEmpty())
                assertNull(second.findById(report.id))
            }
            assertEquals(listOf(report), first.list())
            report
        }

        SqliteReportRepository(context, databaseName = null, clock = CLOCK).use { reopened ->
            assertTrue(reopened.list().isEmpty())
            assertNull(reopened.findById(saved.id))
        }
        assertEquals(0, context.fileAccessCount)
        assertEquals(filesBefore, context.databaseList().toSet())
    }

    @Test
    fun ejemploNoModificaBaseNombradaDePruebas(): Unit = runBlocking {
        // Único archivo permitido: una base nueva de instrumentación, nunca la del usuario.
        // Se conserva como evidencia de pruebas; aquí no se elimina ningún archivo.
        val testName = "citizensecurity_example_test_${UUID.randomUUID()}.db"
        val context = DatabaseGuardContext(ApplicationProvider.getApplicationContext(), testName)
        val sentinel = SqliteReportRepository(context, testName, CLOCK).use { repository ->
            repository.create(sentinelDraft())
        }
        val namedDatabase = context.getDatabasePath(testName)
        assertTrue(namedDatabase.isFile)
        val originalBytes = namedDatabase.readBytes()
        val filesBefore = context.databaseList().toSet()
        val accessCountBefore = context.fileAccessCount

        val example = TemporaryReportSaveExample(context, CLOCK)
        val first = example.run()
        val second = example.run()

        assertNotEquals(first.id, second.id)
        assertEquals(accessCountBefore, context.fileAccessCount)
        assertArrayEquals(originalBytes, namedDatabase.readBytes())
        assertEquals(filesBefore, context.databaseList().toSet())
        SqliteReportRepository(context, testName, CLOCK).use { reopened ->
            assertEquals(listOf(sentinel), reopened.list())
            assertNull(reopened.findById(first.id))
            assertNull(reopened.findById(second.id))
        }
    }

    @Test
    fun nombresNoNulosConservanValidacionDeArchivosDb() {
        val context = DatabaseGuardContext(ApplicationProvider.getApplicationContext())
        listOf("", " ", ":memory:", "reporte.txt", "../reporte.db", "carpeta\\reporte.db").forEach { name ->
            try {
                SqliteReportRepository(context, name, CLOCK).use { }
                fail("Debe rechazarse el nombre de base: '$name'.")
            } catch (expected: IllegalArgumentException) {
                assertEquals(
                    "El nombre de la base debe ser un archivo .db sin una ruta.",
                    expected.message,
                )
            }
        }
        assertEquals(0, context.fileAccessCount)
    }

    private fun sentinelDraft() = NewReport(
        type = IncidentType.RISK,
        priority = Priority.LOW,
        description = "Registro centinela ficticio de una base aislada de pruebas.",
        occurredAt = NOW.minusSeconds(60),
        location = ReportLocation("Referencia ficticia de pruebas"),
    )

    /** Detecta cualquier intento de abrir o borrar una base fuera del archivo de prueba. */
    private class DatabaseGuardContext(context: Context, private val allowedName: String? = null) :
        ContextWrapper(context) {

        var fileAccessCount = 0
            private set

        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File {
            requireAllowedName(name)
            fileAccessCount += 1
            return super.getDatabasePath(name)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
        ): SQLiteDatabase {
            requireAllowedName(name)
            fileAccessCount += 1
            return super.openOrCreateDatabase(name, mode, factory)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
            errorHandler: DatabaseErrorHandler?,
        ): SQLiteDatabase {
            requireAllowedName(name)
            fileAccessCount += 1
            return super.openOrCreateDatabase(name, mode, factory, errorHandler)
        }

        override fun deleteDatabase(name: String): Boolean {
            throw AssertionError("El ejemplo no debe borrar bases de datos: $name.")
        }

        private fun requireAllowedName(name: String) {
            if (allowedName == null || name != allowedName) {
                throw AssertionError("El ejemplo no debe abrir archivos de base de datos: $name.")
            }
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-03T03:00:00.987654321Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    }
}
