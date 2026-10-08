package com.example.citizensecurity.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.MapRegion
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportMapMarker
import com.example.citizensecurity.domain.ReportMapPage
import com.example.citizensecurity.domain.ReportMapQuery
import com.example.citizensecurity.domain.ReportMapRepository
import com.example.citizensecurity.domain.ReportStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SqliteReportMapQueryTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private val repositories = mutableListOf<SqliteReportRepository>()

    @Before
    fun prepareIsolatedDatabase() {
        context = ApplicationProvider.getApplicationContext()
        databaseName = "citizensecurity_map_query_test_${UUID.randomUUID()}.db"
    }

    @After
    fun closeAndDeleteOnlyTheTestDatabase() {
        repositories.forEach { it.close() }
        check(databaseName.matches(TEST_DATABASE_PATTERN))
        context.deleteDatabase(databaseName)
    }

    @Test
    fun unaBaseVaciaDevuelveUnaPaginaSinMasResultados(): Unit = runBlocking {
        val repository: ReportMapRepository = newRepository()
        assertEquals(ReportMapPage(emptyList(), false), repository.queryMarkers(ReportMapQuery(CITY)))
    }

    @Test
    fun incluyeLosBordesYExcluyeLosPuntosFueraYLasReferenciasSinCoordenadas(): Unit = runBlocking {
        val repository = newRepository()
        val inside = listOf(
            19.0 to -100.0,
            19.0 to -99.0,
            20.0 to -100.0,
            20.0 to -99.0,
            19.5 to -99.5,
        ).map { (latitude, longitude) -> repository.create(draft(latitude, longitude)) }
        listOf(
            18.999999 to -99.5,
            20.000001 to -99.5,
            19.5 to -100.000001,
            19.5 to -98.999999,
        ).forEach { (latitude, longitude) -> repository.create(draft(latitude, longitude)) }
        repository.create(draft().copy(location = ReportLocation("Referencia sin coordenadas")))

        val page = repository.queryMarkers(ReportMapQuery(CITY))
        assertMarkerIds(inside, page)
        assertEquals(inside.map { it.marker() }.sortedByDescending { it.id }, page.markers)
    }

    @Test
    fun losFiltrosVaciosDejanPasarTodasLasCategoriasYEstadosDeLaZona(): Unit = runBlocking {
        val repository = newRepository(databaseName)
        val reports = statusFixtures(repository)

        assertMarkerIds(reports.take(5), repository.queryMarkers(ReportMapQuery(CITY)))
    }

    @Test
    fun filtraPorVariasCategoriasYPorVariosEstados(): Unit = runBlocking {
        val repository = newRepository(databaseName)
        val reports = statusFixtures(repository)

        assertMarkerIds(
            reports.take(4),
            repository.queryMarkers(ReportMapQuery(CITY, types = setOf(IncidentType.FIRE, IncidentType.THEFT))),
        )
        assertMarkerIds(
            listOf(reports[1], reports[2], reports[3]),
            repository.queryMarkers(
                ReportMapQuery(CITY, statuses = setOf(ReportStatus.IN_REVIEW, ReportStatus.CLOSED)),
            ),
        )
        assertMarkerIds(
            listOf(reports[4]),
            repository.queryMarkers(ReportMapQuery(CITY, statuses = setOf(ReportStatus.ATTENDED))),
        )
    }

    @Test
    fun combinaZonaCategoriaYEstadoAntesDeAplicarElLimite(): Unit = runBlocking {
        val repository = newRepository(databaseName)
        val reports = statusFixtures(repository)
        val query = ReportMapQuery(
            CITY,
            types = setOf(IncidentType.THEFT, IncidentType.FIRE),
            statuses = setOf(ReportStatus.IN_REVIEW),
        )
        val expected = listOf(reports[1], reports[2]).map { it.marker() }.sortedByDescending { it.id }

        assertEquals(ReportMapPage(expected, false), repository.queryMarkers(query))
        assertEquals(
            ReportMapPage(expected.take(1), true),
            repository.queryMarkers(ReportMapQuery(CITY, query.types, query.statuses, limit = 1)),
        )
        assertEquals(
            ReportMapPage(emptyList(), false),
            repository.queryMarkers(
                ReportMapQuery(CITY, types = setOf(IncidentType.EMERGENCY), statuses = query.statuses, limit = 1),
            ),
        )
    }

    @Test
    fun ordenaPorSegundosNanosegundosYFolioYDetectaSoloElResultadoAdicional(): Unit = runBlocking {
        val clock = MutableClock(NOW)
        val repository = newRepository(clock = clock)
        val tied = listOf(repository.create(draft()), repository.create(draft()))
        clock.current = NOW.plusNanos(1)
        val laterNanos = repository.create(draft().copy(priority = Priority.HIGH))
        clock.current = NOW.plusSeconds(1)
        val laterSeconds = repository.create(draft().copy(type = IncidentType.ACCIDENT))
        val expected = (listOf(laterSeconds, laterNanos) + tied.sortedByDescending { it.id }).map { it.marker() }

        assertEquals(ReportMapPage(expected.take(1), true), repository.queryMarkers(ReportMapQuery(CITY, limit = 1)))
        assertEquals(ReportMapPage(expected.take(3), true), repository.queryMarkers(ReportMapQuery(CITY, limit = 3)))
        assertEquals(ReportMapPage(expected, false), repository.queryMarkers(ReportMapQuery(CITY, limit = 4)))
        assertEquals(ReportMapPage(expected, false), repository.queryMarkers(ReportMapQuery(CITY, limit = 5)))
    }

    @Test
    fun elLimitePredeterminadoYElMaximoAcotanLosMarcadores(): Unit = runBlocking {
        val repository = newRepository()
        val expected = buildList {
            repeat(501) { add(repository.create(draft()).marker()) }
        }.sortedByDescending { it.id }

        assertEquals(ReportMapPage(expected.take(200), true), repository.queryMarkers(ReportMapQuery(CITY)))
        assertEquals(
            ReportMapPage(expected.take(500), true),
            repository.queryMarkers(ReportMapQuery(CITY, limit = 500)),
        )
    }

    @Test
    fun unBordePositivoOUnBordeNegativoIncluyeAmbasRepresentacionesDelAntimeridiano(): Unit = runBlocking {
        val repository = newRepository()
        val reports = antimeridianFixtures(repository)

        assertMarkerIds(
            listOf(-180.0, 170.0, 179.0, 180.0).map(reports::getValue),
            repository.queryMarkers(ReportMapQuery(MapRegion(-1.0, 1.0, 170.0, 180.0))),
        )
        assertMarkerIds(
            listOf(-180.0, -179.0, -170.0, 180.0).map(reports::getValue),
            repository.queryMarkers(ReportMapQuery(MapRegion(-1.0, 1.0, -180.0, -170.0))),
        )
    }

    @Test
    fun cruzarElAntimeridianoIncluyeLasDosBandasYExcluyeElCentro(): Unit = runBlocking {
        val repository = newRepository()
        val reports = antimeridianFixtures(repository)

        assertMarkerIds(
            listOf(-180.0, -179.0, -170.0, 170.0, 179.0, 180.0).map(reports::getValue),
            repository.queryMarkers(ReportMapQuery(MapRegion(-1.0, 1.0, 170.0, -170.0))),
        )
        assertMarkerIds(
            listOf(-180.0, -179.0, 180.0).map(reports::getValue),
            repository.queryMarkers(ReportMapQuery(MapRegion(-1.0, 1.0, 180.0, -175.0))),
        )
    }

    @Test
    fun elMeridianoReducidoAUnaLineaConservaLosDosSignosSinAmpliarLaZona(): Unit = runBlocking {
        val repository = newRepository()
        val reports = antimeridianFixtures(repository)
        val expected = listOf(reports.getValue(-180.0), reports.getValue(180.0))

        listOf(180.0 to 180.0, -180.0 to -180.0, 180.0 to -180.0).forEach { (west, east) ->
            assertMarkerIds(expected, repository.queryMarkers(ReportMapQuery(MapRegion(0.0, 0.0, west, east))))
        }
        assertMarkerIds(
            reports.values.toList(),
            repository.queryMarkers(ReportMapQuery(MapRegion(-1.0, 1.0, -180.0, 180.0))),
        )
    }

    @Test
    fun consultarYReabrirConservaTodosLosReportesExistentes(): Unit = runBlocking {
        val repository = newRepository(databaseName)
        repository.create(draft().copy(description = "Descripción privada del reporte de ejemplo."))
        repository.create(draft(20.000001, -99.5))
        repository.create(draft().copy(location = ReportLocation("Referencia privada sin coordenadas")))
        val before = repository.list()
        val query = ReportMapQuery(CITY, types = setOf(IncidentType.RISK), limit = 1)
        val page = repository.queryMarkers(query)
        assertEquals(1, page.markers.size)
        assertFalse(page.hasMore)
        assertEquals(before, repository.list())
        repository.close()

        val reopened = newRepository(databaseName)
        assertEquals(page, reopened.queryMarkers(query))
        assertEquals(before, reopened.list())
        before.forEach { assertEquals(it, reopened.findById(it.id)) }
        assertEquals(before, reopened.list())
    }

    @Test
    fun unaConsultaYaCanceladaNoAbreLaBase(): Unit = runBlocking {
        val repository = newRepository(databaseName, dispatcher = Dispatchers.Unconfined)
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().job.cancel()
            repository.queryMarkers(ReportMapQuery(CITY))
        }

        pending.join()
        assertTrue(pending.isCancelled)
        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    @Test
    fun cancelarMientrasEsperaElMonitorNoAbreLaBaseAlAdquirirlo(): Unit = runBlocking {
        val workers = CopyOnWriteArrayList<Thread>()
        val dispatcher = Executors.newFixedThreadPool(2) { action ->
            Thread(action, "consulta-marcadores-${workers.size + 1}").also(workers::add)
        }.asCoroutineDispatcher()
        val clock = BlockingClock(NOW)
        val repository = newRepository(databaseName, clock, dispatcher)
        val saving = async(Dispatchers.Default) { repository.create(draft()) }
        var querying: Deferred<ReportMapPage>? = null

        try {
            assertTrue(clock.firstRequested.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val pending = async(Dispatchers.Default) { repository.queryMarkers(ReportMapQuery(CITY)) }
            querying = pending
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
            while (workers.none { it.isWaitingForRepository() } && System.nanoTime() < deadline) delay(1)
            assertTrue("La consulta debe estar esperando realmente el monitor.", workers.any { it.isWaitingForRepository() })
            pending.cancel()
            saving.cancel()
            clock.releaseFirst.countDown()
            pending.join()
            saving.join()

            assertTrue(pending.isCancelled)
            assertTrue(saving.isCancelled)
            assertFalse("Ninguna de las solicitudes canceladas debe abrir SQLite.", context.getDatabasePath(databaseName).exists())
        } finally {
            clock.releaseFirst.countDown()
            withContext(NonCancellable) {
                querying?.cancelAndJoin()
                saving.cancelAndJoin()
            }
            repository.close()
            dispatcher.close()
        }
    }

    @Test
    fun unaInstanciaCerradaRechazaLaConsultaSinReabrirLaBase(): Unit = runBlocking {
        val repository = newRepository(databaseName)
        repository.close()
        repository.close()

        try {
            repository.queryMarkers(ReportMapQuery(CITY))
            fail("Una consulta no debe reabrir una instancia cerrada.")
        } catch (error: IllegalStateException) {
            assertEquals("El repositorio de reportes está cerrado.", error.message)
        }
        assertFalse(context.getDatabasePath(databaseName).exists())
    }

    private suspend fun statusFixtures(repository: SqliteReportRepository): List<Report> {
        val fixtures = listOf(
            IncidentType.THEFT to ReportStatus.REPORTED,
            IncidentType.THEFT to ReportStatus.IN_REVIEW,
            IncidentType.FIRE to ReportStatus.IN_REVIEW,
            IncidentType.FIRE to ReportStatus.CLOSED,
            IncidentType.RISK to ReportStatus.ATTENDED,
        ).map { (type, status) -> repository.create(draft().copy(type = type)).copy(status = status) } + listOf(
            repository.create(draft(20.000001, -99.5).copy(type = IncidentType.FIRE)).copy(status = ReportStatus.IN_REVIEW),
            repository.create(draft().copy(type = IncidentType.FIRE, location = ReportLocation("Referencia sin punto")))
                .copy(status = ReportStatus.IN_REVIEW),
        )
        // Los estados son fixtures del archivo temporal; la consulta no ofrece escrituras.
        ReportDatabase(context, databaseName).use { helper ->
            fixtures.forEach { report ->
                assertEquals(
                    1,
                    helper.writableDatabase.update(
                        "reports",
                        ContentValues().apply { put("status", report.status.name) },
                        "id = ?",
                        arrayOf(report.id),
                    ),
                )
            }
        }
        return fixtures
    }

    private suspend fun antimeridianFixtures(repository: SqliteReportRepository): Map<Double, Report> {
        val reports = mutableMapOf<Double, Report>()
        listOf(-180.0, -179.0, -170.0, -169.0, 0.0, 169.0, 170.0, 179.0, 180.0).forEach { longitude ->
            reports[longitude] = repository.create(draft(0.0, longitude))
        }
        repository.create(draft(1.000001, 180.0))
        return reports
    }

    private fun assertMarkerIds(reports: List<Report>, page: ReportMapPage) {
        assertEquals(reports.map { it.id }.toSet(), page.markers.map { it.id }.toSet())
        assertEquals(reports.size, page.markers.size)
        assertFalse(page.hasMore)
    }

    private fun newRepository(
        name: String? = null,
        clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) = SqliteReportRepository(context, name, clock, dispatcher).also(repositories::add)

    private fun draft(latitude: Double = 19.5, longitude: Double = -99.5) = NewReport(
        type = IncidentType.RISK,
        priority = Priority.MEDIUM,
        description = "Hay una luminaria dañada frente al parque.",
        occurredAt = NOW.minusSeconds(60),
        location = ReportLocation("Parque de ejemplo", latitude, longitude),
    )

    private fun Report.marker() = ReportMapMarker(
        id, type, priority, status, checkNotNull(location.latitude), checkNotNull(location.longitude),
    )

    private fun Thread.isWaitingForRepository(): Boolean =
        state == Thread.State.BLOCKED && stackTrace.any { frame ->
            frame.className.startsWith(SqliteReportRepository::class.java.name)
        }

    private class MutableClock(var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
        override fun instant(): Instant = current
    }

    private class BlockingClock(private val now: Instant) : Clock() {
        val firstRequested = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        private val calls = AtomicInteger()
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
        override fun instant(): Instant {
            if (calls.incrementAndGet() == 1) {
                firstRequested.countDown()
                check(releaseFirst.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }
            return now
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-03T03:00:00.987654321Z")
        val CITY = MapRegion(19.0, 20.0, -100.0, -99.0)
        val TEST_DATABASE_PATTERN = Regex("citizensecurity_map_query_test_[a-f0-9-]{36}\\.db")
        const val TIMEOUT_SECONDS = 10L
    }
}
