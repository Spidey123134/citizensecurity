package com.example.citizensecurity.data

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteReportQueryTest {

    @Test
    fun consultarFoliosTrasReabrirConservaDatosYOrdenSinModificarRegistros(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "citizensecurity_test_${UUID.randomUUID()}.db"
        val clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val missingFolio = UUID.randomUUID().toString()
        var repository = SqliteReportRepository(context, databaseName, clock)

        try {
            val initiallyEmpty = repository.list()
            assertEquals(emptyList<Report>(), initiallyEmpty)
            assertNull(repository.findById(missingFolio))
            val first = repository.create(
                NewReport(
                    type = IncidentType.THEFT,
                    priority = Priority.HIGH,
                    description = "Se reportó el robo de una bicicleta frente al parque.",
                    occurredAt = NOW.minusSeconds(120),
                    location = ReportLocation("Frente al parque central", 19.4326077, -99.1332088),
                ),
            )
            val second = repository.create(
                NewReport(
                    type = IncidentType.ACCIDENT,
                    priority = Priority.MEDIUM,
                    description = "Hay una colisión entre vehículos en la intersección.",
                    occurredAt = NOW.minusSeconds(90),
                    location = ReportLocation(latitude = 19.4335, longitude = -99.1342),
                ),
            )
            assertEquals(first.createdAt, second.createdAt)
            repository.close()
            repository = SqliteReportRepository(context, databaseName, clock)

            val expectedOrder = listOf(first, second).sortedByDescending { it.id }
            val beforeQueries = repository.list()
            assertEquals(expectedOrder, beforeQueries)
            val found = listOf(first, second).map { saved ->
                val restored = repository.findById(saved.id)
                assertEquals(saved, restored)
                checkNotNull(restored).also { assertEquals(ReportStatus.REPORTED, it.status) }
            }
            val missing = repository.findById(missingFolio)
            assertNull(missing)
            assertNull(repository.findById("' OR 1=1 --"))
            val afterQueries = repository.list()
            assertEquals(beforeQueries, afterQueries)
            assertEquals(2, afterQueries.size)

            val evidence = JSONObject()
                .put("generatedAt", Instant.now().toString())
                .put("temporaryDatabase", true)
                .put("databaseName", databaseName)
                .put("reopened", true)
                .put("clock", "fixed")
                .put("found", JSONArray(found.map { it.toEvidence() }))
                .put("orderedFolios", JSONArray(afterQueries.map { it.id }))
                .put("missingFolio", missingFolio)
                .put("missingFound", missing != null)
                .put("countBefore", beforeQueries.size)
                .put("countAfter", afterQueries.size)
                .put("emptyCount", initiallyEmpty.size)
            Log.i("FASE4_CONSULTA", "FASE4_CONSULTA=$evidence")
        } finally {
            repository.close()
            check(databaseName.matches(Regex("citizensecurity_test_[a-f0-9-]{36}\\.db")))
            context.deleteDatabase(databaseName)
        }
    }

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

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-03T03:00:00.987654321Z")
    }
}
