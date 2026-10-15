package com.example.citizensecurity.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.SimilarReportPage
import com.example.citizensecurity.domain.SimilarReportQuery
import com.example.citizensecurity.domain.SimilarReportRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteSimilarReportQueryTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private val repositories = mutableListOf<SqliteReportRepository>()

    @Before
    fun prepareIsolatedDatabase() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "citizensecurity_similar_query_test_${UUID.randomUUID()}.db"
    }

    @After
    fun closeAndDeleteOnlyTheTestDatabase() {
        repositories.forEach { it.close() }
        check(databaseName.matches(TEST_DATABASE_PATTERN))
        context.deleteDatabase(databaseName)
    }

    @Test
    fun baseVaciaDevuelveBusquedaCompletaSinCoincidencias(): Unit = runBlocking {
        val repository: SimilarReportRepository = repository()
        assertEquals(SimilarReportPage(emptyList(), false), repository.findSimilar(query()))
    }

    @Test
    fun referenciaSinCoordenadasNoSeComparaConUnPunto(): Unit = runBlocking {
        val repository = repository()
        repository.create(draft().copy(location = ReportLocation("Parque sin punto registrado")))
        assertEquals(SimilarReportPage(emptyList(), false), repository.findSimilar(query()))
        assertEquals(1, repository.list().size)
    }

    @Test
    fun mismoTipoTiempoYRadioPuedenCoincidirAunqueTextoPrioridadYEstadoDifieran(): Unit = runBlocking {
        val repository = repository(databaseName)
        val report = repository.create(draft().copy(description = "Otro hecho distinto cerca del mismo parque.", priority = Priority.HIGH))
        rawDatabase { database ->
            val values = ContentValues().apply { put("status", ReportStatus.CLOSED.name) }
            assertEquals(1, database.update("reports", values, "id = ?", arrayOf(report.id)))
        }
        repository.create(draft().copy(type = IncidentType.FIRE))
        val page = repository.findSimilar(query())
        assertEquals(listOf(report.id), page.matches.map { it.report.id })
        assertEquals(ReportStatus.CLOSED, page.matches.single().report.status)
        assertEquals(Priority.HIGH, page.matches.single().report.priority)
        assertFalse(page.searchIncomplete)
    }

    @Test
    fun ventanaIncluyeAmbosBordesYUsaNanosParaExcluirUnNanoFuera(): Unit = runBlocking {
        val repository = repository()
        val query = query()
        val before = repository.create(draft(occurredAt = query.earliest))
        val after = repository.create(draft(occurredAt = query.latest))
        val middle = repository.create(draft(occurredAt = query.occurredAt))
        repository.create(draft(occurredAt = query.earliest.minusNanos(1)))
        repository.create(draft(occurredAt = query.latest.plusNanos(1)))
        val page = repository.findSimilar(query)
        assertEquals(listOf(after.id, middle.id, before.id), page.matches.map { it.report.id })
        assertEquals(listOf(after.occurredAt, middle.occurredAt, before.occurredAt), page.matches.map { it.report.occurredAt })
        assertFalse(page.searchIncomplete)
    }

    @Test
    fun ventanaQueCruzaEpochNoConfundeSegundosConNanos(): Unit = runBlocking {
        val repository = repository(clock = Clock.fixed(Instant.EPOCH.plusSeconds(10_000), ZoneOffset.UTC))
        val atEpoch = repository.create(draft(occurredAt = Instant.EPOCH))
        repository.create(draft(occurredAt = Instant.EPOCH.plusNanos(1)))
        val query = query().copy(occurredAt = Instant.EPOCH.minusNanos(1), timeWindow = java.time.Duration.ofNanos(1))
        assertEquals(listOf(atEpoch.id), repository.findSimilar(query).matches.map { it.report.id })
    }

    @Test
    fun fechasExtremasNoDesbordanSQLiteNiDevuelvenUnaFechaAjena(): Unit = runBlocking {
        val repository = repository()
        repository.create(draft())
        assertEquals(SimilarReportPage(emptyList(), false), repository.findSimilar(query().copy(occurredAt = Instant.MIN)))
        assertEquals(SimilarReportPage(emptyList(), false), repository.findSimilar(query().copy(occurredAt = Instant.MAX)))
    }

    @Test
    fun radioEsCircularIncluyeBordeYExcluyeEsquinaDeLaCaja(): Unit = runBlocking {
        val repository = repository()
        val same = repository.create(draft())
        val boundary = repository.create(draft(latitude = Math.toDegrees(200.0 / EARTH_RADIUS)))
        repository.create(draft(latitude = Math.toDegrees(200.01 / EARTH_RADIUS)))
        repository.create(draft(latitude = 0.0017, longitude = 0.0017))
        val page = repository.findSimilar(query())
        assertEquals(listOf(same.id, boundary.id), page.matches.map { it.report.id })
        assertEquals(0.0, page.matches[0].distanceMeters, 0.0)
        assertEquals(200.0, page.matches[1].distanceMeters, 1e-8)
        assertFalse(page.searchIncomplete)
    }

    @Test
    fun meridianoCruzaAmbasBandasSinIncluirElCentro(): Unit = runBlocking {
        val repository = repository()
        val positive = repository.create(draft(longitude = 180.0))
        val negative = repository.create(draft(longitude = -180.0))
        val across = repository.create(draft(longitude = -179.9995))
        repository.create(draft(longitude = 0.0))
        repository.create(draft(longitude = -179.99))
        val page = repository.findSimilar(query(longitude = 179.9995))
        assertEquals(setOf(positive.id, negative.id, across.id), page.matches.map { it.report.id }.toSet())
        assertTrue(page.matches.all { it.distanceMeters <= 200.0 })
        assertFalse(page.searchIncomplete)
    }

    @Test
    fun polosNoOmitenPuntosCercanosConLongitudesOpuestas(): Unit = runBlocking {
        val repository = repository()
        val north = repository.create(draft(latitude = 89.9995, longitude = 180.0))
        val south = repository.create(draft(latitude = -89.9995, longitude = -180.0))
        repository.create(draft(latitude = 89.99))
        repository.create(draft(latitude = -89.99))
        assertEquals(listOf(north.id), repository.findSimilar(query(latitude = 89.9995)).matches.map { it.report.id })
        assertEquals(listOf(south.id), repository.findSimilar(query(latitude = -89.9995)).matches.map { it.report.id })
    }

    @Test
    fun excluyeLaSolicitudPropiaAntesDelLimiteDeCandidatos(): Unit = runBlocking {
        val repository = repository()
        val own = repository.create(draft())
        val other = repository.create(draft())
        val page = repository.findSimilar(query().copy(excludedId = own.id))
        assertEquals(listOf(other.id), page.matches.map { it.report.id })
        assertFalse(page.searchIncomplete)
        assertEquals(2, repository.list().size)
    }

    @Test
    fun cincoCoincidenciasSonCompletasYSeisAvisanTruncamiento(): Unit = runBlocking {
        val repository = repository()
        val firstFive = List(5) { index -> repository.create(draft(latitude = index * 0.0001)) }
        val full = repository.findSimilar(query())
        assertEquals(firstFive.map { it.id }, full.matches.map { it.report.id })
        assertFalse(full.searchIncomplete)
        repository.create(draft(latitude = 0.0006))
        val limited = repository.findSimilar(query())
        assertEquals(firstFive.map { it.id }, limited.matches.map { it.report.id })
        assertTrue(limited.searchIncomplete)
    }

    @Test
    fun ordenaPorDistanciaLuegoHoraConNanosYLuegoFolioDeFormaEstable(): Unit = runBlocking {
        val repository = repository()
        val tied = List(2) { repository.create(draft(latitude = 0.0001)) }
        val moreRecent = repository.create(draft(latitude = 0.0001, occurredAt = OCCURRED.plusNanos(1)))
        val closestButOlder = repository.create(draft(occurredAt = OCCURRED.minusSeconds(10)))
        val page = repository.findSimilar(query())
        val expected = listOf(closestButOlder, moreRecent) + tied.sortedByDescending { it.id }
        assertEquals(expected.map { it.id }, page.matches.map { it.report.id })
        assertEquals(page, repository.findSimilar(query()))
    }

    @Test
    fun doscientosCandidatosSinCoincidenciasSonBusquedaCompleta(): Unit = runBlocking {
        val repository = repository()
        repeat(200) { repository.create(draft(latitude = 0.0017, longitude = 0.0017)) }
        assertEquals(SimilarReportPage(emptyList(), false), repository.findSimilar(query()))
    }

    @Test
    fun limiteDeCandidatosAvisaInclusoSinCoincidenciasVisibles(): Unit = runBlocking {
        val repository = repository()
        val older = repository.create(draft(occurredAt = OCCURRED.minusNanos(1)))
        repeat(200) { repository.create(draft(latitude = 0.0017, longitude = 0.0017)) }
        val page = repository.findSimilar(query())
        assertTrue(page.matches.isEmpty())
        assertTrue(page.searchIncomplete)
        assertEquals(older, repository.findById(older.id))
        assertEquals(201, repository.list().size)
    }

    @Test
    fun filasFueraDelTipoOVentanaNoConsumenElLimite(): Unit = runBlocking {
        val repository = repository()
        repeat(201) { repository.create(draft().copy(type = IncidentType.FIRE)) }
        repeat(201) { repository.create(draft(occurredAt = OCCURRED.minusSeconds(7201))) }
        val matching = repository.create(draft())
        val page = repository.findSimilar(query())
        assertEquals(listOf(matching.id), page.matches.map { it.report.id })
        assertFalse(page.searchIncomplete)
    }

    @Test
    fun consultarYReabrirNoCambiaReportesNiFotosNiEsquema(): Unit = runBlocking {
        val repository = repository(databaseName)
        val photo = ReportPhoto(UUID.randomUUID().toString(), "a".repeat(64), 150, 20, 30)
        val first = repository.create(draft().copy(photo = photo, description = "Texto con comillas '; DROP TABLE reports; -- conservado."))
        repository.create(draft(longitude = 1.0))
        val before = repository.list()
        val result = repository.findSimilar(query())
        assertEquals(first, result.matches.single().report)
        assertEquals(photo, result.matches.single().report.photo)
        assertEquals(before, repository.list())
        rawDatabase { assertEquals(4, it.version) }
        repository.close()
        val reopened = repository(databaseName)
        assertEquals(result, reopened.findSimilar(query()))
        assertEquals(before, reopened.list())
        assertEquals(first, reopened.findById(first.id))
    }

    @Test
    fun coordenadasInvalidasDeFilasDanadasSeOmitenSinConvertirlasEnCero(): Unit = runBlocking {
        val repository = repository(databaseName)
        val valid = repository.create(draft())
        val text = repository.create(draft())
        val partial = repository.create(draft())
        val outside = repository.create(draft())
        rawDatabase { database ->
            database.execSQL("PRAGMA ignore_check_constraints = ON")
            database.update("reports", ContentValues().apply { put("latitude", "coordenada dañada") }, "id = ?", arrayOf(text.id))
            database.update("reports", ContentValues().apply { putNull("longitude") }, "id = ?", arrayOf(partial.id))
            database.update("reports", ContentValues().apply { put("longitude", 720.0) }, "id = ?", arrayOf(outside.id))
        }
        assertEquals(listOf(valid.id), repository.findSimilar(query()).matches.map { it.report.id })
        // La banda que cruza ±180 podría incluir 720 por su comparación SQL; el filtro esférico
        // debe rechazar el valor inválido en lugar de tratarlo como el mismo meridiano.
        assertTrue(repository.findSimilar(query(longitude = 180.0)).matches.isEmpty())
        assertTrue(repository.findSimilar(query(latitude = 90.0)).matches.isEmpty())
    }

    @Test
    fun metadatosDeFotoDanadosProducenErrorEnLugarDeAfirmarCeroCoincidencias(): Unit = runBlocking {
        val repository = repository(databaseName)
        val report = repository.create(draft())
        rawDatabase { database ->
            database.execSQL("PRAGMA ignore_check_constraints = ON")
            database.update("reports", ContentValues().apply { put("photo_id", UUID.randomUUID().toString()) }, "id = ?", arrayOf(report.id))
        }
        try { repository.findSimilar(query()); fail("Los datos dañados deben presentar fallo de lectura.") }
        catch (_: IllegalStateException) { /* No se ocultan como página vacía. */ }
        rawDatabase { database ->
            database.rawQuery("SELECT count(*) FROM reports", null).use { cursor -> cursor.moveToFirst(); assertEquals(1, cursor.getInt(0)) }
        }
    }

    @Test
    fun solicitudYaCanceladaNoAbreNiCreaLaBase(): Unit = runBlocking {
        val repository = repository(databaseName, dispatcher = Dispatchers.Unconfined)
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().job.cancel()
            repository.findSimilar(query())
        }
        pending.join()
        assertTrue(pending.isCancelled)
        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    @Test
    fun cancelarMientrasEsperaElMonitorNoAbreSQLiteAlAdquirirlo(): Unit = runBlocking {
        val workers = CopyOnWriteArrayList<Thread>()
        val dispatcher = Executors.newFixedThreadPool(2) { action ->
            Thread(action, "consulta-similares-${workers.size + 1}").also(workers::add)
        }.asCoroutineDispatcher()
        val clock = BlockingClock()
        val repository = repository(databaseName, clock, dispatcher)
        val saving = async(Dispatchers.Default) { repository.create(draft()) }
        var querying: Deferred<SimilarReportPage>? = null
        try {
            assertTrue(clock.firstRequested.await(10, TimeUnit.SECONDS))
            val pending = async(Dispatchers.Default) { repository.findSimilar(query()) }
            querying = pending
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (workers.none { it.state == Thread.State.BLOCKED && it.stackTrace.any { frame ->
                    frame.className.startsWith(SqliteReportRepository::class.java.name)
                } } && System.nanoTime() < deadline) delay(1)
            assertTrue(workers.any { it.state == Thread.State.BLOCKED })
            pending.cancel()
            saving.cancel()
            clock.releaseFirst.countDown()
            pending.join()
            saving.join()
            assertTrue(pending.isCancelled)
            assertTrue(saving.isCancelled)
            assertFalse(context.getDatabasePath(databaseName).exists())
        } finally {
            querying?.cancel()
            saving.cancel()
            clock.releaseFirst.countDown()
            querying?.join()
            saving.join()
            dispatcher.close()
        }
    }

    @Test
    fun repositorioCerradoRechazaConsultaYNoPuedeReabrirSuArchivo(): Unit = runBlocking {
        val repository = repository(databaseName)
        repository.close()
        try { repository.findSimilar(query()); fail("Una instancia cerrada no debe reabrirse.") }
        catch (_: IllegalStateException) { /* Contrato de cierre. */ }
        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    private fun repository(
        name: String? = null,
        clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) = SqliteReportRepository(context, name, clock, dispatcher).also(repositories::add)

    private fun draft(latitude: Double = 0.0, longitude: Double = 0.0, occurredAt: Instant = OCCURRED) = NewReport(
        IncidentType.RISK, Priority.MEDIUM, "Hay una luminaria dañada frente al parque.", occurredAt, ReportLocation("Parque de ejemplo", latitude, longitude),
    )

    private fun query(latitude: Double = 0.0, longitude: Double = 0.0) = SimilarReportQuery(IncidentType.RISK, OCCURRED, latitude, longitude)

    private fun rawDatabase(action: (SQLiteDatabase) -> Unit) {
        check(databaseName.matches(TEST_DATABASE_PATTERN))
        SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use(action)
    }

    private class BlockingClock : Clock() {
        val firstRequested = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(NOW, zone)
        override fun instant(): Instant {
            firstRequested.countDown()
            check(releaseFirst.await(10, TimeUnit.SECONDS))
            return NOW
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-10T12:00:00.987654321Z")
        val OCCURRED: Instant = NOW.minusSeconds(4 * 60 * 60)
        val TEST_DATABASE_PATTERN = Regex("citizensecurity_similar_query_test_[a-f0-9-]{36}\\.db")
        const val EARTH_RADIUS = 6_371_008.8
    }
}
