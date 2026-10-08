package com.example.citizensecurity.maps

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.MapRegion
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportMapMarker
import com.example.citizensecurity.domain.ReportMapPage
import com.example.citizensecurity.domain.ReportMapQuery
import com.example.citizensecurity.domain.ReportMapRepository
import com.example.citizensecurity.domain.ReportRepository
import com.example.citizensecurity.domain.ReportStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportMapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun configureDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun releaseModels() {
        try {
            stores.forEach { it.clear() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun crearElModeloNoConsultaNiGuardaReportes() = runTest(dispatcher) {
        val repository = MapRepository { page() }
        val model = model(repository)

        runCurrent()

        assertEquals(ReportMapState.Idle, model.state.value)
        assertTrue(repository.requests.isEmpty())
        assertEquals(0, repository.otherOperations)
    }

    @Test
    fun consultaExplicitaEntregaZonaFiltrosLimiteYMarcadoresSinLeerLaListaCompleta() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportMapPage>()
        val repository = MapRepository { completion.await() }
        val model = model(repository)
        val query = query(
            types = setOf(IncidentType.THEFT, IncidentType.RISK),
            statuses = setOf(ReportStatus.REPORTED, ReportStatus.IN_REVIEW),
            limit = 17,
        )
        val page = page(hasMore = true)

        model.load(query)
        assertEquals(ReportMapState.Loading(query), model.state.value)
        runCurrent()
        assertEquals(listOf(query), repository.requests)
        assertSame(query, repository.requests.single())
        completion.complete(page)
        advanceUntilIdle()

        assertEquals(ReportMapState.Ready(query, page), model.state.value)
        assertEquals(ReportStatus.REPORTED, page.markers.single().status)
        assertTrue((model.state.value as ReportMapState.Ready).page.hasMore)
        assertEquals(0, repository.otherOperations)
    }

    @Test
    fun losFiltrosMutablesOriginalesNoCambianUnaConsultaPendiente() = runTest(dispatcher) {
        val types = mutableSetOf(IncidentType.RISK)
        val statuses = mutableSetOf(ReportStatus.IN_REVIEW)
        val query = query(types = types, statuses = statuses)
        val repository = MapRepository { page() }
        val model = model(repository)

        model.load(query)
        types.clear()
        types.add(IncidentType.FIRE)
        statuses.clear()
        statuses.add(ReportStatus.CLOSED)
        runCurrent()

        assertEquals(setOf(IncidentType.RISK), repository.requests.single().types)
        assertEquals(setOf(ReportStatus.IN_REVIEW), repository.requests.single().statuses)
        assertEquals(ReportMapState.Ready(query, page()), model.state.value)
    }

    @Test
    fun repetirLaMismaConsultaPendienteNoIniciaOtraLectura() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportMapPage>()
        val repository = MapRepository { completion.await() }
        val model = model(repository)
        val query = query()

        model.load(query)
        model.load(query())
        runCurrent()
        model.load(query())
        runCurrent()
        assertEquals(listOf(query), repository.requests)
        assertFalse(repository.jobs.single()!!.isCancelled)
        completion.complete(page())
        advanceUntilIdle()

        assertEquals(ReportMapState.Ready(query, page()), model.state.value)
    }

    @Test
    fun repetirUnaConsultaTerminadaRenuevaLosDatos() = runTest(dispatcher) {
        var attempts = 0
        val repository = MapRepository { page(id = "reporte-${++attempts}") }
        val model = model(repository)
        val query = query()

        model.load(query)
        runCurrent()
        val previous = ReportMapState.Ready(query, page(id = "reporte-1"))
        assertEquals(previous, model.state.value)
        model.load(query)
        assertEquals(ReportMapState.Loading(query, previous), model.state.value)
        runCurrent()

        assertEquals(ReportMapState.Ready(query, page(id = "reporte-2")), model.state.value)
        assertEquals(listOf(query, query), repository.requests)
    }

    @Test
    fun cambiarDeZonaAntesDeIniciarSoloLeeLaSolicitudMasReciente() = runTest(dispatcher) {
        val repository = MapRepository { page() }
        val model = model(repository)
        val first = query()
        val latest = query(SECOND_REGION)

        model.load(first)
        model.load(latest)
        assertEquals(ReportMapState.Loading(latest), model.state.value)
        runCurrent()

        assertEquals(listOf(latest), repository.requests)
        assertEquals(ReportMapState.Ready(latest, page()), model.state.value)
    }

    @Test
    fun limpiarDesdeUnObservadorInmediatoDeLoadingEvitaIniciarLaLectura() = runTest(dispatcher) {
        val repository = MapRepository { page() }
        val model = model(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { state ->
                if (state is ReportMapState.Loading) model.clear()
            }
        }

        model.load(query())
        assertEquals(ReportMapState.Idle, model.state.value)
        runCurrent()

        assertTrue(repository.requests.isEmpty())
        assertEquals(ReportMapState.Idle, model.state.value)
    }

    @Test
    fun cambiarZonaDesdeLoadingNoLeeLaAnteriorYLaNuevaConservaSuCancelacion() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportMapPage>()
        val repository = MapRepository { completion.await() }
        val model = model(repository)
        val first = query()
        val latest = query(SECOND_REGION)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { state ->
                if (state is ReportMapState.Loading && state.query == first) model.load(latest)
            }
        }

        model.load(first)
        assertEquals(ReportMapState.Loading(latest), model.state.value)
        runCurrent()
        assertEquals(listOf(latest), repository.requests)
        assertFalse(repository.jobs.single()!!.isCancelled)
        model.clear()
        assertTrue(repository.jobs.single()!!.isCancelled)
        completion.complete(page())
        advanceUntilIdle()

        assertEquals(ReportMapState.Idle, model.state.value)
        assertEquals(listOf(latest), repository.requests)
    }

    @Test
    fun unResultadoAnteriorQueIgnoraCancelacionNoReemplazaLaZonaActual() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val first = query()
        val latest = query(SECOND_REGION)
        val repository = MapRepository {
            if (it == first) withContext(NonCancellable) {
                completion.await()
                page(id = "anterior")
            } else page(id = "actual")
        }
        val model = model(repository)

        model.load(first)
        runCurrent()
        model.load(latest)
        runCurrent()
        assertTrue(repository.jobs.first()!!.isCancelled)
        val expected = ReportMapState.Ready(latest, page(id = "actual"))
        assertEquals(expected, model.state.value)
        completion.complete(Unit)
        advanceUntilIdle()

        assertEquals(expected, model.state.value)
        assertEquals(listOf(first, latest), repository.requests)
    }

    @Test
    fun unErrorAnteriorQueIgnoraCancelacionNoOcultaElResultadoActual() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val first = query()
        val latest = query(SECOND_REGION)
        val repository = MapRepository {
            if (it == first) withContext(NonCancellable) {
                completion.await()
                throw IOException("Error de una lectura anterior.")
            } else page(id = "actual")
        }
        val model = model(repository)

        model.load(first)
        runCurrent()
        model.load(latest)
        runCurrent()
        val expected = ReportMapState.Ready(latest, page(id = "actual"))
        assertEquals(expected, model.state.value)
        completion.complete(Unit)
        advanceUntilIdle()

        assertEquals(expected, model.state.value)
    }

    @Test
    fun fallarConservaLaUltimaZonaCorrectaConSuIdentidadYPermiteReintentar() = runTest(dispatcher) {
        val first = query()
        val latest = query(SECOND_REGION)
        var attempts = 0
        val repository = MapRepository {
            if (it == latest && ++attempts == 1) throw IOException("Ruta privada de la base.")
            page(id = if (it == first) "anterior" else "actual")
        }
        val model = model(repository)

        model.load(first)
        runCurrent()
        val previous = ReportMapState.Ready(first, page(id = "anterior"))
        model.load(latest)
        assertEquals(ReportMapState.Loading(latest, previous), model.state.value)
        runCurrent()
        assertEquals(ReportMapState.Error(latest, ERROR_MESSAGE, previous), model.state.value)
        assertEquals(first, (model.state.value as ReportMapState.Error).previous!!.query)
        model.load(latest)
        assertEquals(ReportMapState.Loading(latest, previous), model.state.value)
        runCurrent()

        assertEquals(ReportMapState.Ready(latest, page(id = "actual")), model.state.value)
        assertEquals(listOf(first, latest, latest), repository.requests)
    }

    @Test
    fun unFalloInicialSePresentaSinMarcadoresYPermiteReintentar() = runTest(dispatcher) {
        var attempts = 0
        val repository = MapRepository {
            if (++attempts == 1) throw IOException("Datos internos que no deben mostrarse.")
            ReportMapPage(emptyList(), hasMore = false)
        }
        val model = model(repository)
        val query = query()

        model.load(query)
        runCurrent()
        assertEquals(ReportMapState.Error(query, ERROR_MESSAGE), model.state.value)
        model.load(query)
        runCurrent()

        assertEquals(ReportMapState.Ready(query, ReportMapPage(emptyList(), false)), model.state.value)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun cancelarLaLecturaNoPublicaErrorNiBloqueaUnReintento() = runTest(dispatcher) {
        var attempts = 0
        val repository = MapRepository {
            if (++attempts == 1) throw CancellationException("Lectura cancelada.")
            page()
        }
        val model = model(repository)
        val query = query()

        model.load(query)
        runCurrent()
        assertTrue(repository.jobs.single()!!.isCancelled)
        assertEquals(ReportMapState.Idle, model.state.value)
        model.load(query)
        runCurrent()

        assertEquals(ReportMapState.Ready(query, page()), model.state.value)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun unFalloDespuesDeCancelarseRecuperaLaUltimaConsultaCorrecta() = runTest(dispatcher) {
        val first = query()
        val latest = query(SECOND_REGION)
        val repository = MapRepository {
            if (it == latest) {
                currentCoroutineContext()[Job]!!.cancel()
                throw IOException("La solicitud ya estaba cancelada.")
            }
            page()
        }
        val model = model(repository)

        model.load(first)
        runCurrent()
        val previous = ReportMapState.Ready(first, page())
        model.load(latest)
        runCurrent()

        assertTrue(repository.jobs.last()!!.isCancelled)
        assertEquals(previous, model.state.value)
    }

    @Test
    fun limpiarAntesDeIniciarNoLeeNiDejaCargaPendiente() = runTest(dispatcher) {
        val repository = MapRepository { page() }
        val model = model(repository)

        model.load(query())
        model.clear()
        runCurrent()

        assertEquals(ReportMapState.Idle, model.state.value)
        assertTrue(repository.requests.isEmpty())
        model.load(query())
        runCurrent()
        assertEquals(ReportMapState.Ready(query(), page()), model.state.value)
    }

    @Test
    fun limpiarDescartaResultadosRetenidosYUnResultadoTardioNoLosRecupera() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val first = query()
        val latest = query(SECOND_REGION)
        var latestAttempts = 0
        val repository = MapRepository {
            if (it == latest && ++latestAttempts == 1) withContext(NonCancellable) {
                completion.await()
                page(id = "tardio")
            } else page()
        }
        val model = model(repository)

        model.load(first)
        runCurrent()
        model.load(latest)
        runCurrent()
        model.clear()
        assertTrue(repository.jobs.last()!!.isCancelled)
        assertEquals(ReportMapState.Idle, model.state.value)
        completion.complete(Unit)
        advanceUntilIdle()
        assertEquals(ReportMapState.Idle, model.state.value)
        model.load(latest)
        assertEquals(ReportMapState.Loading(latest), model.state.value)
        runCurrent()

        assertEquals(ReportMapState.Ready(latest, page()), model.state.value)
    }

    @Test
    fun limpiarIgnoraErroresTardiosAunqueLaLecturaNoCoopere() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val repository = MapRepository {
            withContext(NonCancellable) {
                completion.await()
                throw IOException("Error posterior a limpiar.")
            }
        }
        val model = model(repository)

        model.load(query())
        runCurrent()
        model.clear()
        completion.complete(Unit)
        advanceUntilIdle()

        assertEquals(ReportMapState.Idle, model.state.value)
    }

    @Test
    fun liberarElStoreAntesDeIniciarDescartaLaSolicitudEIgnoraNuevasCargas() = runTest(dispatcher) {
        val repository = MapRepository { page() }
        val store = store()
        val model = model(repository, store)

        model.load(query())
        store.clear()
        runCurrent()
        assertEquals(ReportMapState.Idle, model.state.value)
        assertTrue(repository.requests.isEmpty())
        model.load(query(SECOND_REGION))
        runCurrent()

        assertTrue(repository.requests.isEmpty())
        assertEquals(ReportMapState.Idle, model.state.value)
    }

    @Test
    fun liberarElStoreCancelaLaLecturaEIgnoraUnResultadoNoCancelable() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val repository = MapRepository {
            withContext(NonCancellable) {
                completion.await()
                page()
            }
        }
        val store = store()
        val model = model(repository, store)

        model.load(query())
        runCurrent()
        store.clear()
        assertTrue(repository.jobs.single()!!.isCancelled)
        assertEquals(ReportMapState.Idle, model.state.value)
        completion.complete(Unit)
        advanceUntilIdle()
        model.load(query(SECOND_REGION))
        runCurrent()

        assertEquals(ReportMapState.Idle, model.state.value)
        assertEquals(1, repository.requests.size)
    }

    private fun model(repository: ReportMapRepository, store: ViewModelStore = store()) =
        ViewModelProvider(store, ReportMapViewModel.factory(repository))[ReportMapViewModel::class.java]

    private fun store() = ViewModelStore().also { stores.add(it) }

    private fun query(
        region: MapRegion = FIRST_REGION,
        types: Set<IncidentType> = emptySet(),
        statuses: Set<ReportStatus> = emptySet(),
        limit: Int = 200,
    ) = ReportMapQuery(region, types, statuses, limit)

    private fun page(id: String = "reporte-local", hasMore: Boolean = false) = ReportMapPage(
        markers = listOf(
            ReportMapMarker(
                id = id,
                type = IncidentType.RISK,
                priority = Priority.MEDIUM,
                status = ReportStatus.REPORTED,
                latitude = 19.43,
                longitude = -99.13,
            ),
        ),
        hasMore = hasMore,
    )

    private class MapRepository(
        private val lookup: suspend (ReportMapQuery) -> ReportMapPage,
    ) : ReportMapRepository, ReportRepository {
        val requests = mutableListOf<ReportMapQuery>()
        val jobs = mutableListOf<Job?>()
        var otherOperations = 0
            private set

        override suspend fun queryMarkers(query: ReportMapQuery): ReportMapPage {
            requests.add(query)
            jobs.add(currentCoroutineContext()[Job])
            return lookup(query)
        }

        override suspend fun create(draft: NewReport): Report {
            otherOperations++
            error("Consultar marcadores no debe guardar reportes.")
        }

        override suspend fun findById(id: String): Report? {
            otherOperations++
            error("Consultar una zona no debe buscar folios por separado.")
        }

        override suspend fun list(): List<Report> {
            otherOperations++
            error("Consultar una zona no debe recuperar todos los reportes.")
        }
    }

    private companion object {
        val FIRST_REGION = MapRegion(south = 19.0, north = 20.0, west = -100.0, east = -99.0)
        val SECOND_REGION = MapRegion(south = 20.0, north = 21.0, west = -101.0, east = -100.0)
        const val ERROR_MESSAGE = "No se pudieron consultar los incidentes de esta zona. Intenta nuevamente."
    }
}
