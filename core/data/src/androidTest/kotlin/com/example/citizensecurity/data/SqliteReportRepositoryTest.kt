package com.example.citizensecurity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteReportRepositoryTest {

    private lateinit var context: Context
    private lateinit var databaseName: String
    private val repositories = mutableListOf<SqliteReportRepository>()

    @Before
    fun prepareIsolatedDatabase() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "citizensecurity_test_${UUID.randomUUID()}.db"
    }

    @After
    fun closeAndDeleteOnlyTheTestDatabase() {
        repositories.forEach { it.close() }
        check(databaseName.matches(TEST_DATABASE_PATTERN))
        context.deleteDatabase(databaseName)
    }

    @Test
    fun guardarYReabrirConservaReporteYNormalizaTexto() = runBlocking {
        val repository = newRepository()
        val saved = repository.create(
            draft().copy(
                description = "\n  Hay una luminaria dañada frente al parque.  \t",
                location = ReportLocation(reference = "  Frente al parque central  "),
            ),
        )
        assertEquals("Hay una luminaria dañada frente al parque.", saved.description)
        assertEquals("Frente al parque central", saved.location.reference)
        assertEquals(ReportStatus.REPORTED, saved.status)
        assertEquals(NOW, saved.createdAt)
        assertEquals(saved.id, UUID.fromString(saved.id).toString())
        repository.close()

        val reopened = newRepository()
        assertEquals(saved, reopened.findById(saved.id))
        assertEquals(listOf(saved), reopened.list())
        assertNull(reopened.findById(UUID.randomUUID().toString()))
    }

    @Test
    fun reporteInvalidoNoModificaLosDatosGuardados() = runBlocking {
        val repository = newRepository()
        val saved = repository.create(draft())
        val invalid = draft().copy(
            description = "Corto",
            occurredAt = NOW.plusSeconds(61),
            location = ReportLocation("Parque central", latitude = Double.NaN, longitude = -99.0),
        )

        try {
            repository.create(invalid)
            fail("Un reporte inválido debe devolver errores por campo.")
        } catch (error: InvalidReportException) {
            assertTrue(error.errors.errors.containsKey(ReportField.DESCRIPTION))
            assertTrue(error.errors.errors.containsKey(ReportField.OCCURRED_AT))
            assertTrue(error.errors.errors.containsKey(ReportField.LOCATION_COORDINATES))
        }
        repository.close()

        assertEquals(listOf(saved), newRepository().list())
    }

    @Test
    fun listarOrdenaPorFechaDeGuardadoYConservaFoliosDistintos() = runBlocking {
        val clock = MutableClock(NOW)
        val repository = newRepository(clock)
        val first = repository.create(draft())
        clock.current = NOW.plusNanos(1)
        val second = repository.create(draft())
        clock.current = NOW.plusSeconds(1)
        val third = repository.create(draft())
        assertNotEquals(first.id, second.id)
        assertNotEquals(second.id, third.id)
        repository.close()

        assertEquals(listOf(third, second, first), newRepository().list())
    }

    @Test
    fun coordenadasSinReferenciaYFechaPrecisaSeConservanAlReabrir() = runBlocking {
        val repository = newRepository()
        val saved = repository.create(
            draft().copy(
                occurredAt = NOW.minusSeconds(10).plusNanos(123456789),
                location = ReportLocation(latitude = 19.4326077, longitude = -99.1332088),
            ),
        )
        repository.close()

        val restored = newRepository().findById(saved.id)
        assertEquals(saved, restored)
        assertEquals("", restored!!.location.reference)
        assertEquals(19.4326077, restored.location.latitude!!, 0.0)
        assertEquals(-99.1332088, restored.location.longitude!!, 0.0)
    }

    @Test
    fun cerrarEsIdempotenteEImpideUsarLaInstanciaSinBorrarSuArchivo() = runBlocking {
        val repository = newRepository()
        val saved = repository.create(draft())
        repository.close()
        repository.close()

        try {
            repository.findById(saved.id)
            fail("Una instancia cerrada no debe reabrirse de manera implícita.")
        } catch (error: IllegalStateException) {
            assertEquals("El repositorio de reportes está cerrado.", error.message)
        }

        assertEquals(saved, newRepository().findById(saved.id))
    }

    private fun newRepository(clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC)) =
        SqliteReportRepository(context, databaseName, clock).also { repositories.add(it) }

    private fun draft() = NewReport(
        type = IncidentType.RISK,
        priority = Priority.MEDIUM,
        description = "Hay una luminaria dañada frente al parque.",
        occurredAt = NOW.minusSeconds(60),
        location = ReportLocation("Frente al parque central"),
    )

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
        override fun instant(): Instant = current
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-03T03:00:00.987654321Z")
        val TEST_DATABASE_PATTERN = Regex("citizensecurity_test_[a-f0-9-]{36}\\.db")
    }
}
