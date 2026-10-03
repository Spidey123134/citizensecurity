package com.example.citizensecurity.report

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportRepository
import com.example.citizensecurity.domain.ValidationErrors
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun configureMainDispatcher() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun clearModelsAndRestoreMainDispatcher() {
        try {
            stores.forEach { it.clear() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun guardarMuestraElReporteDevueltoEIgnoraOtraSolicitudMientrasEspera() = runTest(mainDispatcher) {
        val completion = CompletableDeferred<Report>()
        val repository = FakeRepository { completion.await() }
        val model = newModel(repository)
        val requested = draft()
        val saved = savedReport(requested)
        assertEquals(ReportSaveState.Idle, model.state.value)

        model.save(requested)
        assertEquals(ReportSaveState.Saving, model.state.value)
        model.save(requested.copy(description = "Hay un poste dañado frente al mercado."))
        runCurrent()
        assertEquals(listOf(requested), repository.requests)
        assertEquals(ReportSaveState.Saving, model.state.value)

        completion.complete(saved)
        advanceUntilIdle()
        assertEquals(ReportSaveState.Saved(saved), model.state.value)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun erroresPorCampoSeConservanYPermitenCorregirElBorrador() = runTest(mainDispatcher) {
        val errors = mapOf(
            ReportField.DESCRIPTION to "La descripción debe contener entre 10 y 1000 caracteres.",
            ReportField.LOCATION_COORDINATES to "Agrega tanto la latitud como la longitud.",
        )
        var attempts = 0
        val repository = FakeRepository { requested ->
            if (++attempts == 1) throw InvalidReportException(ValidationErrors(errors))
            savedReport(requested)
        }
        val model = newModel(repository)
        val invalid = draft().copy(
            description = "Corta",
            location = ReportLocation(latitude = 19.4326),
        )

        model.save(invalid)
        runCurrent()
        assertEquals(ReportSaveState.Invalid(errors), model.state.value)
        assertEquals(listOf(invalid), repository.requests)

        val corrected = draft()
        model.save(corrected)
        runCurrent()
        assertEquals(ReportSaveState.Saved(savedReport(corrected)), model.state.value)
        assertEquals(listOf(invalid, corrected), repository.requests)
    }

    @Test
    fun errorDeAlmacenamientoMuestraUnMensajeUtilYPermiteReintentar() = runTest(mainDispatcher) {
        var attempts = 0
        val repository = FakeRepository { requested ->
            if (++attempts == 1) throw IOException("No se pudo abrir el archivo interno de datos.")
            savedReport(requested)
        }
        val model = newModel(repository)

        model.save(draft())
        runCurrent()
        assertEquals(
            ReportSaveState.Error("No se pudo guardar el reporte. Intenta nuevamente."),
            model.state.value,
        )

        model.save(draft())
        runCurrent()
        assertEquals(ReportSaveState.Saved(savedReport(draft())), model.state.value)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun cancelarNoSeConvierteEnErrorYDejaDisponibleUnaNuevaSolicitud() = runTest(mainDispatcher) {
        var attempts = 0
        val repository = FakeRepository { requested ->
            if (++attempts == 1) throw CancellationException("La solicitud se canceló.")
            savedReport(requested)
        }
        val model = newModel(repository)

        model.save(draft())
        runCurrent()
        assertTrue("La cancelación debe propagarse al trabajo de guardado.", repository.jobs.single()!!.isCancelled)
        assertEquals(ReportSaveState.Idle, model.state.value)

        model.save(draft())
        runCurrent()
        assertEquals(ReportSaveState.Saved(savedReport(draft())), model.state.value)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun liberarElStoreCancelaElGuardadoSinCancelarElRepositorioCompartido() = runTest(mainDispatcher) {
        val completion = CompletableDeferred<Report>()
        val repository = FakeRepository { completion.await() }
        val store = newStore()
        val model = ViewModelProvider(store, ReportViewModel.factory(repository))[ReportViewModel::class.java]
        val requested = draft()
        val saved = savedReport(requested)

        model.save(requested)
        runCurrent()
        assertEquals(ReportSaveState.Saving, model.state.value)
        val savingJob = repository.jobs.single()!!
        store.clear()
        assertTrue(savingJob.isCancelled)

        completion.complete(saved)
        advanceUntilIdle()
        assertEquals(ReportSaveState.Idle, model.state.value)
        model.save(requested)
        runCurrent()
        assertEquals(ReportSaveState.Idle, model.state.value)
        assertEquals(listOf(requested), repository.requests)

        assertEquals(saved, repository.create(requested))
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun propietariosDelMismoStoreCompartenModeloEstadoYRepositorio() = runTest(mainDispatcher) {
        val repository = FakeRepository { savedReport(it) }
        val store = newStore()
        val factory = ReportViewModel.factory(repository)
        val initialOwner = ownerOf(store)
        val first = ViewModelProvider(initialOwner, factory)[ReportViewModel::class.java]
        first.save(draft())
        runCurrent()

        val replacementOwner = ownerOf(store)
        val restored = ViewModelProvider(replacementOwner, factory)[ReportViewModel::class.java]
        assertSame(first, restored)
        assertEquals(ReportSaveState.Saved(savedReport(draft())), restored.state.value)
        assertEquals(listOf(draft()), repository.requests)
    }

    private fun newModel(repository: ReportRepository): ReportViewModel =
        ViewModelProvider(newStore(), ReportViewModel.factory(repository))[ReportViewModel::class.java]

    private fun newStore() = ViewModelStore().also { stores.add(it) }

    private fun ownerOf(store: ViewModelStore) = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = store
    }

    private fun draft() = NewReport(
        type = IncidentType.RISK,
        priority = Priority.MEDIUM,
        description = "Hay una luminaria dañada frente al parque.",
        occurredAt = CLOCK.instant().minusSeconds(60),
        location = ReportLocation(latitude = 19.4326077, longitude = -99.1332088),
    )

    private fun savedReport(draft: NewReport) = Report(
        id = "4b2c9510-0ca0-46fb-94f1-1c1f7ee79f0d",
        type = draft.type,
        priority = draft.priority,
        description = draft.description,
        occurredAt = draft.occurredAt,
        location = draft.location,
        createdAt = CLOCK.instant(),
    )

    private class FakeRepository(
        private val createReport: suspend (NewReport) -> Report,
    ) : ReportRepository {
        val requests = mutableListOf<NewReport>()
        val jobs = mutableListOf<Job?>()

        override suspend fun create(draft: NewReport): Report {
            requests.add(draft)
            jobs.add(currentCoroutineContext()[Job])
            return createReport(draft)
        }

        override suspend fun findById(id: String): Report? =
            error("La consulta no forma parte de esta prueba del guardado.")

        override suspend fun list(): List<Report> =
            error("La consulta no forma parte de esta prueba del guardado.")
    }

    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-03T03:00:00Z"), ZoneOffset.UTC)
    }
}
