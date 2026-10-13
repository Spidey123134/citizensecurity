package com.example.citizensecurity.report

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.ReportHistoryPage
import com.example.citizensecurity.domain.ReportHistoryQuery
import com.example.citizensecurity.domain.ReportHistoryRepository
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportStatusEvent
import com.example.citizensecurity.domain.ReportStatusEventKind
import java.io.IOException
import java.time.Instant
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
class ReportHistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun configureDispatcher() = Dispatchers.setMain(dispatcher)

    @After
    fun releaseModels() {
        try {
            stores.forEach { it.clear() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun consultaSoloTrasGestoNormalizaElFolioYRetieneLaIdentidadDeLaSolicitud() = runTest(dispatcher) {
        val repository = Repository { page(it.reportId) }
        val model = model(repository)
        runCurrent()
        assertEquals(ReportHistoryState.Idle, model.state.value)
        assertTrue(repository.requests.isEmpty())

        model.load(" \n${FOLIO.uppercase()}\t ")
        val loading = model.state.value as ReportHistoryState.Loading
        assertEquals(FOLIO, loading.query.reportId)
        runCurrent()
        val ready = model.state.value as ReportHistoryState.Ready
        assertSame(loading.query, ready.query)
        assertSame(ready.query, repository.requests.single())
        assertEquals(listOf(4L, 3L, 2L, 1L), ready.page.events.map { it.sequence })
        assertTrue(ready.page.completeFromCreation)
        assertEquals(ReportStatus.CLOSED, ready.page.currentStatus)
    }

    @Test
    fun foliosVaciosAbreviadosOConInyeccionNoConsultanElRepositorio() = runTest(dispatcher) {
        val repository = Repository { page() }
        val model = model(repository)
        listOf("", "  ", "1-1-1-1-1", FOLIO.dropLast(1), "' OR 1=1 --", "folio").forEach {
            model.load(it)
            assertTrue(model.state.value is ReportHistoryState.InvalidFolio)
        }
        runCurrent()
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun folioAusenteSeDistingueDelFalloYSePuedeConsultarDeNuevo() = runTest(dispatcher) {
        var attempts = 0
        val repository = Repository {
            when (++attempts) {
                1 -> null
                2 -> throw IOException("Ruta privada que no debe aparecer en la UI.")
                else -> page()
            }
        }
        val model = model(repository)
        model.load(FOLIO)
        runCurrent()
        assertTrue(model.state.value is ReportHistoryState.NotFound)
        model.reload()
        runCurrent()
        val error = model.state.value as ReportHistoryState.Error
        assertFalse(error.message.contains("Ruta privada"))
        assertSame(repository.requests[0], repository.requests[1])
        model.reload()
        runCurrent()
        assertTrue(model.state.value is ReportHistoryState.Ready)
        assertSame(error.query, repository.requests[2])
    }

    @Test
    fun historialAntiguoVacioNoInventaCreacionNiOtroEstado() = runTest(dispatcher) {
        val repository = Repository {
            ReportHistoryPage(FOLIO, ReportStatus.ATTENDED, 0, emptyList(), false, false)
        }
        val model = model(repository)
        model.load(FOLIO)
        runCurrent()
        val ready = model.state.value as ReportHistoryState.Ready
        assertTrue(ready.page.events.isEmpty())
        assertFalse(ready.page.completeFromCreation)
        assertEquals(ReportStatus.ATTENDED, ready.page.currentStatus)
        model.nextPage()
        runCurrent()
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun historialAntiguoConCambiosAceptaUnaCadenaRealSinFabricarSuCreacion() = runTest(dispatcher) {
        val events = listOf(
            changed(3, ReportStatus.ATTENDED, ReportStatus.CLOSED),
            changed(2, ReportStatus.IN_REVIEW, ReportStatus.ATTENDED),
            changed(1, ReportStatus.REPORTED, ReportStatus.IN_REVIEW),
        )
        val repository = Repository {
            ReportHistoryPage(FOLIO, ReportStatus.CLOSED, 3, events, false, false)
        }
        val model = model(repository)
        model.load(FOLIO)
        runCurrent()
        val ready = model.state.value as ReportHistoryState.Ready
        assertFalse(ready.page.completeFromCreation)
        assertEquals(ReportStatusEventKind.STATUS_CHANGED, ready.page.events.last().kind)
    }

    @Test
    fun paginacionReemplazaLaPaginaReintentaElCursorFallidoYRegresaAlInicioConLecturaFresca() = runTest(dispatcher) {
        var olderAttempts = 0
        val repository = Repository { query ->
            if (query.beforeSequence == null) page(sequences = listOf(4L, 3L), hasMore = true)
            else if (++olderAttempts == 1) throw IOException("Fallo al leer eventos antiguos.")
            else page(sequences = listOf(2L, 1L))
        }
        val model = model(repository)
        model.load(FOLIO)
        runCurrent()
        model.nextPage()
        val loading = model.state.value as ReportHistoryState.Loading
        assertEquals(3L, loading.query.beforeSequence)
        model.nextPage()
        model.reload()
        runCurrent()
        val error = model.state.value as ReportHistoryState.Error
        assertSame(loading.query, error.query)
        model.reload()
        runCurrent()
        val older = model.state.value as ReportHistoryState.Ready
        assertSame(error.query, older.query)
        assertEquals(listOf(2L, 1L), older.page.events.map { it.sequence })
        assertEquals(listOf(null, 3L, 3L), repository.requests.map { it.beforeSequence })
        model.nextPage()
        runCurrent()
        assertEquals(3, repository.requests.size)
        model.firstPage()
        runCurrent()
        assertEquals(listOf(4L, 3L), (model.state.value as ReportHistoryState.Ready).page.events.map { it.sequence })
        assertEquals(listOf(null, 3L, 3L, null), repository.requests.map { it.beforeSequence })
    }

    @Test
    fun repetirFolioMientrasCargaNoDuplicaLecturaYUnGestoDespuesDeReadySiRefresca() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportHistoryPage?>()
        val repository = Repository { completion.await() }
        val model = model(repository)
        model.load(FOLIO)
        model.load(" ${FOLIO.uppercase()} ")
        runCurrent()
        assertEquals(1, repository.requests.size)
        completion.complete(page())
        runCurrent()
        model.load(FOLIO)
        runCurrent()
        assertEquals(2, repository.requests.size)
        assertTrue(model.state.value is ReportHistoryState.Ready)
    }

    @Test
    fun otroFolioRetiraElAnteriorYDescartaSuRespuestaTardia() = runTest(dispatcher) {
        val old = CompletableDeferred<ReportHistoryPage?>()
        val repository = Repository {
            if (it.reportId == FOLIO) withContext(NonCancellable) { old.await() } else page(OTHER_FOLIO)
        }
        val model = model(repository)
        model.load(FOLIO)
        runCurrent()
        model.load(OTHER_FOLIO)
        assertEquals(OTHER_FOLIO, (model.state.value as ReportHistoryState.Loading).query.reportId)
        runCurrent()
        old.complete(page())
        advanceUntilIdle()
        assertEquals(OTHER_FOLIO, (model.state.value as ReportHistoryState.Ready).query.reportId)
        assertTrue(repository.jobs.first()!!.isCancelled)
    }

    @Test
    fun limpiarOEntradaInvalidaDescartanRespuestaTardiaSinOtraLectura() = runTest(dispatcher) {
        listOf(false, true).forEach { invalid ->
            val old = CompletableDeferred<ReportHistoryPage?>()
            val repository = Repository { withContext(NonCancellable) { old.await() } }
            val model = model(repository)
            model.load(FOLIO)
            runCurrent()
            if (invalid) model.load("incompleto") else model.clear()
            old.complete(page())
            advanceUntilIdle()
            if (invalid) assertTrue(model.state.value is ReportHistoryState.InvalidFolio)
            else assertEquals(ReportHistoryState.Idle, model.state.value)
            model.reload()
            model.firstPage()
            runCurrent()
            assertEquals(1, repository.requests.size)
            assertTrue(repository.jobs.single()!!.isCancelled)
        }
    }

    @Test
    fun colectorDeLoadingPuedeCancelarOSustituirAntesDeIniciarLaLectura() = runTest(dispatcher) {
        listOf(false, true).forEach { replace ->
            val repository = Repository { page(it.reportId) }
            val model = model(repository)
            var reacted = false
            val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                model.state.collect { state ->
                    if (state is ReportHistoryState.Loading && !reacted) {
                        reacted = true
                        if (replace) model.load(OTHER_FOLIO) else model.cancel()
                    }
                }
            }
            model.load(FOLIO)
            runCurrent()
            assertTrue(reacted)
            if (replace) {
                assertEquals(listOf(OTHER_FOLIO), repository.requests.map { it.reportId })
                assertEquals(OTHER_FOLIO, (model.state.value as ReportHistoryState.Ready).query.reportId)
            } else {
                assertTrue(repository.requests.isEmpty())
                assertEquals(ReportHistoryState.Idle, model.state.value)
            }
            collector.cancel()
        }
    }

    @Test
    fun cancelacionDelRepositorioNoSeMuestraComoErrorYPermiteNuevoGesto() = runTest(dispatcher) {
        var attempts = 0
        val repository = Repository {
            if (++attempts == 1) throw CancellationException("Lectura cancelada.")
            page()
        }
        val model = model(repository)
        model.load(FOLIO)
        runCurrent()
        assertEquals(ReportHistoryState.Idle, model.state.value)
        model.load(FOLIO)
        runCurrent()
        assertTrue(model.state.value is ReportHistoryState.Ready)
    }

    @Test
    fun liberarStoreCancelaAntesDeIniciarYSuspendidaSinPublicarResultado() = runTest(dispatcher) {
        listOf(false, true).forEach { started ->
            val completion = CompletableDeferred<ReportHistoryPage?>()
            val repository = Repository { withContext(NonCancellable) { completion.await() } }
            val store = store()
            val model = model(repository, store)
            model.load(FOLIO)
            if (started) runCurrent()
            store.clear()
            completion.complete(page())
            advanceUntilIdle()
            assertEquals(ReportHistoryState.Idle, model.state.value)
            model.load(OTHER_FOLIO)
            runCurrent()
            assertEquals(if (started) 1 else 0, repository.requests.size)
        }
    }

    @Test
    fun noPublicaPaginasDeOtroFolioConEstadoCabezaCadenaOContinuacionIncoherentes() = runTest(dispatcher) {
        val malformed = listOf<() -> ReportHistoryPage>(
            { page(OTHER_FOLIO) },
            { page(sequences = listOf(3L, 2L, 1L)) },
            { ReportHistoryPage(FOLIO, ReportStatus.ATTENDED, 4, timeline(), false, true) },
            { page(sequences = listOf(4L, 2L, 1L)) },
            { page(sequences = listOf(4L, 3L), hasMore = false) },
            { ReportHistoryPage(FOLIO, ReportStatus.CLOSED, 4, emptyList(), false, true) },
            { ReportHistoryPage(FOLIO, ReportStatus.CLOSED, 4, timeline(), false, false) },
            {
                val events = timeline().toMutableList()
                events[1] = events[1].copy(operationId = events[0].operationId)
                ReportHistoryPage(FOLIO, ReportStatus.CLOSED, 4, events, false, true)
            },
            {
                val events = timeline().toMutableList()
                events[1] = changed(3, ReportStatus.REPORTED, ReportStatus.IN_REVIEW)
                ReportHistoryPage(FOLIO, ReportStatus.CLOSED, 4, events, false, true)
            },
        )
        malformed.forEach { makePage ->
            val model = model(Repository { makePage() })
            model.load(FOLIO)
            runCurrent()
            assertTrue(model.state.value is ReportHistoryState.Error)
        }
    }

    @Test
    fun paginaDemasiadoGrandeOCursorNoExclusivoSeRechazanSinExponerEventos() = runTest(dispatcher) {
        val oversizedEvents = (51L downTo 1L).map { changed(it, ReportStatus.REPORTED, ReportStatus.IN_REVIEW) }
        val oversized = model(Repository {
            ReportHistoryPage(FOLIO, ReportStatus.IN_REVIEW, 51, oversizedEvents, false, false)
        })
        oversized.load(FOLIO)
        runCurrent()
        assertTrue(oversized.state.value is ReportHistoryState.Error)

        val repository = Repository { query ->
            if (query.beforeSequence == null) page(sequences = listOf(4L, 3L), hasMore = true)
            else page()
        }
        val cursorModel = model(repository)
        cursorModel.load(FOLIO)
        runCurrent()
        cursorModel.nextPage()
        runCurrent()
        assertTrue(cursorModel.state.value is ReportHistoryState.Error)
        assertEquals(3L, (cursorModel.state.value as ReportHistoryState.Error).query.beforeSequence)
    }

    private fun model(repository: ReportHistoryRepository, store: ViewModelStore = store()) =
        ViewModelProvider(store, ReportHistoryViewModel.factory(repository, dispatcher))[ReportHistoryViewModel::class.java]

    private fun store() = ViewModelStore().also { stores.add(it) }

    private class Repository(private val lookup: suspend (ReportHistoryQuery) -> ReportHistoryPage?) : ReportHistoryRepository {
        val requests = mutableListOf<ReportHistoryQuery>()
        val jobs = mutableListOf<Job?>()
        override suspend fun readHistory(query: ReportHistoryQuery): ReportHistoryPage? {
            requests.add(query)
            jobs.add(currentCoroutineContext()[Job])
            return lookup(query)
        }
    }

    private fun page(
        reportId: String = FOLIO,
        sequences: List<Long> = listOf(4L, 3L, 2L, 1L),
        hasMore: Boolean = false,
    ) = ReportHistoryPage(
        reportId, ReportStatus.CLOSED, 4, timeline().filter { it.sequence in sequences }, hasMore, true,
    )

    private fun timeline() = listOf(
        changed(4, ReportStatus.ATTENDED, ReportStatus.CLOSED),
        changed(3, ReportStatus.IN_REVIEW, ReportStatus.ATTENDED),
        changed(2, ReportStatus.REPORTED, ReportStatus.IN_REVIEW),
        ReportStatusEvent(operationId(1), 1, ReportStatusEventKind.CREATED, null, ReportStatus.REPORTED, TIME, null),
    )

    private fun changed(sequence: Long, from: ReportStatus, to: ReportStatus) = ReportStatusEvent(
        operationId(sequence), sequence, ReportStatusEventKind.STATUS_CHANGED, from, to, TIME, ACTOR, "Se revisó el incidente.",
    )

    private fun operationId(sequence: Long) = "00000000-0000-4000-8000-${sequence.toString().padStart(12, '0')}"

    private companion object {
        const val FOLIO = "4b2c9510-0ca0-46fb-94f1-1c1f7ee79f0d"
        const val OTHER_FOLIO = "a2159b14-ae7d-4f73-815d-6d612b82027c"
        const val ACTOR = "76c1aa89-770e-4a7f-9e6b-3437db4369fd"
        val TIME: Instant = Instant.parse("2026-10-13T17:00:00Z")
    }
}
