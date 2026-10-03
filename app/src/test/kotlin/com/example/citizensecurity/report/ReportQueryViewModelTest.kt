package com.example.citizensecurity.report

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportRepository
import com.example.citizensecurity.domain.ReportStatus
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportQueryViewModelTest {
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
    fun consultaNormalizaElFolioYRecuperaDatosYEstadoSinGuardar() = runTest(dispatcher) {
        val completion = CompletableDeferred<Report?>()
        val repository = QueryRepository { completion.await() }
        val model = model(repository)
        val saved = report()

        model.findByFolio(" \n${FOLIO.uppercase()}\t ")
        assertEquals(ReportQueryState.Loading(FOLIO), model.state.value)
        model.findByFolio(OTHER_FOLIO)
        runCurrent()
        assertEquals(listOf(FOLIO), repository.requests)
        completion.complete(saved)
        advanceUntilIdle()
        assertEquals(ReportQueryState.Found(saved), model.state.value)
        assertEquals(ReportStatus.REPORTED, (model.state.value as ReportQueryState.Found).report.status)
    }

    @Test
    fun folioInexistenteSeDistingueDeUnErrorYPermiteOtraConsulta() = runTest(dispatcher) {
        val repository = QueryRepository { if (it == FOLIO) report() else null }
        val model = model(repository)

        model.findByFolio(OTHER_FOLIO)
        runCurrent()
        assertEquals(ReportQueryState.NotFound(OTHER_FOLIO), model.state.value)
        model.findByFolio(FOLIO)
        runCurrent()
        assertEquals(ReportQueryState.Found(report()), model.state.value)
        assertEquals(listOf(OTHER_FOLIO, FOLIO), repository.requests)
    }

    @Test
    fun entradasVaciasMalFormadasOAbreviadasNoConsultanElRepositorio() = runTest(dispatcher) {
        val repository = QueryRepository { report() }
        val model = model(repository)

        listOf("", "   ", "1-1-1-1-1", "reporte", FOLIO.dropLast(1), "' OR 1=1 --").forEach {
            model.findByFolio(it)
            assertTrue(model.state.value is ReportQueryState.InvalidFolio)
        }
        runCurrent()
        assertTrue(repository.requests.isEmpty())
        model.findByFolio(FOLIO)
        runCurrent()
        assertEquals(ReportQueryState.Found(report()), model.state.value)
    }

    @Test
    fun falloDeLecturaNoSePresentaComoFolioInexistenteYPermiteReintentar() = runTest(dispatcher) {
        var attempts = 0
        val repository = QueryRepository {
            if (++attempts == 1) throw IOException("Ruta interna del archivo de datos.")
            report()
        }
        val model = model(repository)

        model.findByFolio(FOLIO)
        runCurrent()
        assertEquals(
            ReportQueryState.Error("No se pudo consultar el reporte. Intenta nuevamente."),
            model.state.value,
        )
        model.findByFolio(FOLIO)
        runCurrent()
        assertEquals(ReportQueryState.Found(report()), model.state.value)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun liberarElStoreAntesDeIniciarNoDejaUnaConsultaPendiente() = runTest(dispatcher) {
        val repository = QueryRepository { report() }
        val store = store()
        val model = model(repository, store)

        model.findByFolio(FOLIO)
        store.clear()
        runCurrent()
        assertEquals(ReportQueryState.Idle, model.state.value)
        assertTrue(repository.requests.isEmpty())
        model.findByFolio(FOLIO)
        runCurrent()
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun liberarElStoreCancelaLaConsultaSuspendidaYEvitaUnResultadoTardio() = runTest(dispatcher) {
        val completion = CompletableDeferred<Report?>()
        val repository = QueryRepository { completion.await() }
        val store = store()
        val model = model(repository, store)

        model.findByFolio(FOLIO)
        runCurrent()
        store.clear()
        assertTrue(repository.jobs.single()!!.isCancelled)
        completion.complete(report())
        advanceUntilIdle()
        assertEquals(ReportQueryState.Idle, model.state.value)
        assertEquals(listOf(FOLIO), repository.requests)
        assertEquals(report(), repository.findById(FOLIO))
    }

    @Test
    fun cancelacionSePropagaSinErrorYDejaDisponibleOtraConsulta() = runTest(dispatcher) {
        var attempts = 0
        val repository = QueryRepository {
            if (++attempts == 1) throw CancellationException("Consulta cancelada.")
            report()
        }
        val model = model(repository)

        model.findByFolio(FOLIO)
        runCurrent()
        assertTrue(repository.jobs.single()!!.isCancelled)
        assertEquals(ReportQueryState.Idle, model.state.value)
        model.findByFolio(FOLIO)
        runCurrent()
        assertEquals(ReportQueryState.Found(report()), model.state.value)
    }

    @Test
    fun falloDeLecturaTrasCancelarNoSePresentaComoError() = runTest(dispatcher) {
        val repository = QueryRepository {
            currentCoroutineContext()[Job]!!.cancel()
            throw IOException("La lectura falló después de cancelarse la solicitud.")
        }
        val model = model(repository)

        model.findByFolio(FOLIO)
        runCurrent()

        assertTrue(repository.jobs.single()!!.isCancelled)
        assertEquals(ReportQueryState.Idle, model.state.value)
        assertEquals(listOf(FOLIO), repository.requests)
    }

    private fun model(repository: ReportRepository, store: ViewModelStore = store()) =
        ViewModelProvider(store, ReportQueryViewModel.factory(repository))[ReportQueryViewModel::class.java]

    private fun store() = ViewModelStore().also { stores.add(it) }

    private fun report() = Report(
        id = FOLIO,
        type = IncidentType.RISK,
        priority = Priority.MEDIUM,
        description = "Hay una luminaria dañada frente al parque.",
        occurredAt = Instant.parse("2026-10-03T03:00:00Z"),
        location = ReportLocation(latitude = 19.4326077, longitude = -99.1332088),
        createdAt = Instant.parse("2026-10-03T03:00:10Z"),
    )

    private class QueryRepository(private val lookup: suspend (String) -> Report?) : ReportRepository {
        val requests = mutableListOf<String>()
        val jobs = mutableListOf<Job?>()

        override suspend fun findById(id: String): Report? {
            requests.add(id)
            jobs.add(currentCoroutineContext()[Job])
            return lookup(id)
        }

        override suspend fun create(draft: NewReport): Report =
            error("Consultar un folio no debe insertar un reporte.")

        override suspend fun list(): List<Report> =
            error("Consultar un folio no debe recuperar la lista completa.")
    }

    private companion object {
        const val FOLIO = "4b2c9510-0ca0-46fb-94f1-1c1f7ee79f0d"
        const val OTHER_FOLIO = "a2159b14-ae7d-4f73-815d-6d612b82027c"
    }
}
