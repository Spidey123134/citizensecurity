package com.example.citizensecurity.release

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import java.io.IOException
import java.time.Instant
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
class SaveExampleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun configureMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun releaseModelsAndRestoreMainDispatcher() {
        try {
            stores.forEach { it.clear() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun solicitudesRepetidasReutilizanUnSoloGuardadoDuranteYDespuesDelEjemplo() = runTest(dispatcher) {
        var calls = 0
        val completion = CompletableDeferred<Report>()
        val model = model({
            calls++
            completion.await()
        })
        val saved = report()
        assertEquals(SaveExampleState.Idle, model.state.value)

        model.runExample()
        assertEquals(SaveExampleState.Running, model.state.value)
        repeat(3) { model.runExample() }
        runCurrent()
        assertEquals(1, calls)
        assertEquals(SaveExampleState.Running, model.state.value)
        model.runExample()
        runCurrent()
        assertEquals(1, calls)

        completion.complete(saved)
        advanceUntilIdle()
        assertEquals(SaveExampleState.Saved(saved), model.state.value)
        repeat(3) { model.runExample() }
        runCurrent()
        assertEquals(1, calls)
        assertEquals(SaveExampleState.Saved(saved), model.state.value)
    }

    @Test
    fun falloPermiteReintentarYConservaElResultadoDelIntentoCorrecto() = runTest(dispatcher) {
        var calls = 0
        val saved = report()
        val model = model({
            if (++calls == 1) throw IOException("La demostración no pudo completarse.")
            saved
        })

        model.runExample()
        runCurrent()
        assertEquals(SaveExampleState.Failed, model.state.value)

        model.runExample()
        assertEquals(SaveExampleState.Running, model.state.value)
        runCurrent()
        assertEquals(SaveExampleState.Saved(saved), model.state.value)
        assertEquals(2, calls)
    }

    @Test
    fun reemplazarElPropietarioDelMismoStoreConservaElFolioSinEjecutarDeNuevo() = runTest(dispatcher) {
        var calls = 0
        val store = store()
        val saved = report()
        val factory = SaveExampleViewModel.factory {
            calls++
            saved
        }
        val first = ViewModelProvider(ownerOf(store), factory)[SaveExampleViewModel::class.java]
        first.runExample()
        runCurrent()

        val restored = ViewModelProvider(ownerOf(store), factory)[SaveExampleViewModel::class.java]
        assertSame(first, restored)
        assertEquals(SaveExampleState.Saved(saved), restored.state.value)
        assertEquals(saved.id, (restored.state.value as SaveExampleState.Saved).report.id)
        restored.runExample()
        runCurrent()
        assertEquals(1, calls)
    }

    @Test
    fun liberarElStoreAntesDeIniciarCancelaSinInvocarElEjemplo() = runTest(dispatcher) {
        var calls = 0
        val store = store()
        val model = model({
            calls++
            report()
        }, store)

        model.runExample()
        assertEquals(SaveExampleState.Running, model.state.value)
        assertEquals(0, calls)
        store.clear()
        runCurrent()

        assertEquals(SaveExampleState.Idle, model.state.value)
        assertEquals(0, calls)
        model.runExample()
        runCurrent()
        assertEquals(SaveExampleState.Idle, model.state.value)
        assertEquals(0, calls)
    }

    @Test
    fun liberarElStoreCancelaLaAccionSuspendidaYNoAdmiteOtraEjecucion() = runTest(dispatcher) {
        var calls = 0
        var savingJob: Job? = null
        val completion = CompletableDeferred<Report>()
        val store = store()
        val model = model({
            calls++
            savingJob = currentCoroutineContext()[Job]
            completion.await()
        }, store)

        model.runExample()
        runCurrent()
        assertEquals(SaveExampleState.Running, model.state.value)
        store.clear()
        assertTrue("El trabajo de la demostración debe cancelarse al salir.", savingJob!!.isCancelled)
        completion.complete(report())
        advanceUntilIdle()

        assertEquals(SaveExampleState.Idle, model.state.value)
        model.runExample()
        runCurrent()
        assertEquals(1, calls)
        assertEquals(SaveExampleState.Idle, model.state.value)
    }

    @Test
    fun resultadoOErrorPosteriorALaCancelacionNoSePublica() = runTest(dispatcher) {
        val outcomes = listOf<Pair<String, suspend () -> Report>>(
            "Reporte devuelto" to { report() },
            "Error tardío" to { throw IOException("La demostración falló después de cancelarse.") },
        )
        outcomes.forEach { (label, outcome) ->
            var calls = 0
            var savingJob: Job? = null
            val model = model({
                calls++
                savingJob = currentCoroutineContext()[Job]
                savingJob!!.cancel()
                outcome()
            })

            model.runExample()
            runCurrent()

            assertTrue("$label debe mantener cancelado el trabajo.", savingJob!!.isCancelled)
            assertEquals(label, SaveExampleState.Idle, model.state.value)
            assertEquals(label, 1, calls)
        }
    }

    private fun model(action: suspend () -> Report, store: ViewModelStore = store()) =
        ViewModelProvider(store, SaveExampleViewModel.factory(action))[SaveExampleViewModel::class.java]

    private fun store() = ViewModelStore().also(stores::add)

    private fun ownerOf(store: ViewModelStore) = object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = store
    }

    private fun report() = Report(
        id = "4b2c9510-0ca0-46fb-94f1-1c1f7ee79f0d",
        type = IncidentType.RISK,
        priority = Priority.MEDIUM,
        description = "Hay una luminaria dañada frente al parque.",
        occurredAt = Instant.parse("2026-10-03T03:00:00Z"),
        location = ReportLocation("Frente al parque central"),
        createdAt = Instant.parse("2026-10-03T03:01:00Z"),
    )
}
