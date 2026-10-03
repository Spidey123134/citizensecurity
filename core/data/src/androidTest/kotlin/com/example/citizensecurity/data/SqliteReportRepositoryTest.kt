package com.example.citizensecurity.data

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
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
        val rejectedErrors = JSONObject()
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
            error.errors.errors.forEach { (field, message) ->
                rejectedErrors.put(field.name, message)
            }
        }
        repository.close()

        val reopenedReports = newRepository().list()
        assertEquals(listOf(saved), reopenedReports)
        emitEvidence(
            "FASE3_RECHAZO",
            JSONObject()
                .put("rejected", true)
                .put("inserted", false)
                .put("countBefore", 1)
                .put("countAfter", reopenedReports.size)
                .put("preservedFolio", saved.id)
                .put("errors", rejectedErrors),
        )
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
        emitEvidence(
            "FASE3_GUARDADO",
            JSONObject()
                .put("saved", saved.toEvidence())
                .put("restored", restored.toEvidence())
                .put("reopened", true)
                .put("clock", "fixed"),
        )
    }

    @Test
    fun unicodeNoRepresentableSeRechazaSinAlterarLosDatosGuardados() = runBlocking {
        val repository = newRepository()
        val saved = repository.create(draft())
        val invalidInputs = listOf(
            Triple(
                "Descripción con sustituto alto aislado",
                ReportField.DESCRIPTION,
                draft().copy(description = "Hay una luminaria \uD800 dañada frente al parque."),
            ),
            Triple(
                "Descripción con sustituto bajo aislado",
                ReportField.DESCRIPTION,
                draft().copy(description = "Hay una luminaria \uDC00 dañada frente al parque."),
            ),
            Triple(
                "Descripción con marca de orden de bytes inicial",
                ReportField.DESCRIPTION,
                draft().copy(description = "\uFEFFHay una luminaria dañada frente al parque."),
            ),
            Triple(
                "Descripción con marca de orden de bytes invertida inicial",
                ReportField.DESCRIPTION,
                draft().copy(description = "\uFFFEHay una luminaria dañada frente al parque."),
            ),
            Triple(
                "Referencia con sustituto alto aislado",
                ReportField.LOCATION_REFERENCE,
                draft().copy(location = ReportLocation("Frente \uD800 al parque central")),
            ),
            Triple(
                "Referencia con sustituto bajo aislado",
                ReportField.LOCATION_REFERENCE,
                draft().copy(location = ReportLocation("Frente \uDC00 al parque central")),
            ),
            Triple(
                "Referencia con marca de orden de bytes inicial",
                ReportField.LOCATION_REFERENCE,
                draft().copy(location = ReportLocation("\uFEFFFrente al parque central")),
            ),
            Triple(
                "Referencia con marca de orden de bytes invertida inicial",
                ReportField.LOCATION_REFERENCE,
                draft().copy(location = ReportLocation("\uFFFEFrente al parque central")),
            ),
        )
        val failures = mutableListOf<String>()
        invalidInputs.forEach { (label, field, input) ->
            try {
                val accepted = repository.create(input)
                val restored = repository.findById(accepted.id)
                failures.add(
                    "$label fue aceptado. Descripción recuperada: " +
                        "${restored?.description?.codeUnits()}; referencia recuperada: " +
                        "${restored?.location?.reference?.codeUnits()}.",
                )
            } catch (error: InvalidReportException) {
                if (field !in error.errors.errors) {
                    failures.add("$label no devolvió el error esperado para $field.")
                }
            } catch (error: Exception) {
                failures.add("$label produjo ${error.javaClass.simpleName}: ${error.message}.")
            }
        }
        repository.close()

        val reopenedReports = newRepository().list()
        if (reopenedReports != listOf(saved)) {
            failures.add(
                "La base reabierta contiene ${reopenedReports.size} registros; " +
                    "debe conservar solo el reporte válido inicial.",
            )
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun ceroNegativoSeNormalizaYSeConservaIgualAlReabrir() = runBlocking {
        val repository = newRepository()
        val saved = repository.create(
            draft().copy(location = ReportLocation(latitude = -0.0, longitude = -0.0)),
        )
        repository.close()
        val restored = newRepository().findById(saved.id)!!
        assertEquals(
            "Latitud guardada: ${saved.location.latitude!!.toRawBits()}; " +
                "latitud recuperada: ${restored.location.latitude!!.toRawBits()}.",
            0.0.toRawBits(),
            saved.location.latitude!!.toRawBits(),
        )
        assertEquals(0.0.toRawBits(), saved.location.longitude!!.toRawBits())
        assertEquals(0.0.toRawBits(), restored.location.latitude!!.toRawBits())
        assertEquals(0.0.toRawBits(), restored.location.longitude!!.toRawBits())
        assertEquals(saved, restored)
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

    private fun Report.toEvidence() = JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("priority", priority.name)
        .put("description", description)
        .put("occurredAt", occurredAt.toString())
        .put("createdAt", createdAt.toString())
        .put("status", status.name)
        .put(
            "location",
            JSONObject()
                .put("reference", location.reference)
                .put("latitude", location.latitude ?: JSONObject.NULL)
                .put("longitude", location.longitude ?: JSONObject.NULL),
        )

    private fun emitEvidence(marker: String, result: JSONObject) {
        result.put("package", context.packageName)
            .put("temporaryDatabase", true)
            .put("databaseName", databaseName)
            .put("generatedAt", Instant.now().toString())
        Log.i("ReporteLocal", "$marker=$result")
    }

    private fun String.codeUnits(): String =
        map { "U+${it.code.toString(16).uppercase().padStart(4, '0')}" }.joinToString(" ")

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
