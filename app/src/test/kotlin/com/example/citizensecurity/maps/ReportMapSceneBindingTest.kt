package com.example.citizensecurity.maps

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.MapRegion
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportMapMarker
import com.example.citizensecurity.domain.ReportMapPage
import com.example.citizensecurity.domain.ReportMapQuery
import com.example.citizensecurity.domain.ReportMapRepository
import com.example.citizensecurity.domain.ReportStatus
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Proyección controlada y modelo real; estas pruebas no acreditan renderizado nativo ni GPS. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportMapSceneBindingTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val connections = mutableListOf<ReportMapSceneBinding>()

    @Before
    fun configureMain() { Dispatchers.setMain(dispatcher) }

    @After
    fun closeConnections() {
        try {
            connections.asReversed().forEach { it.close() }
            stores.forEach { it.clear() }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun construirReanudarYRecibirIdleDelSdkNoConsultaNiTocaLaProyeccion() = runTest(dispatcher) {
        val h = harness()
        runCurrent()
        h.host.idle()
        h.owner.resume()
        runCurrent()
        h.host.idle()
        h.owner.pause()
        h.owner.resume()
        runCurrent()
        h.host.idle()

        assertEquals(ReportMapSceneState.Idle, h.binding.state)
        assertEquals(ReportMapState.Idle, h.model.state.value)
        assertTrue(h.repository.requests.isEmpty())
        assertEquals(0, h.host.reads)
        assertEquals(0, h.host.clears)
        assertTrue(h.host.renders.isEmpty())
        assertEquals(1, h.host.registrations)
        assertTrue(h.states.isEmpty())
    }

    @Test
    fun soloRefreshExplicitoResumedConsultaYNoAceptaGestosOcultos() = runTest(dispatcher) {
        val h = harness()
        assertFalse(h.binding.refreshVisible())
        h.owner.resume()
        h.owner.pause()
        assertFalse(h.binding.refreshVisible())
        assertEquals(0, h.host.reads)
        h.owner.resume()
        assertTrue(h.binding.refreshVisible())
        runCurrent()

        val query = query()
        assertEquals(listOf(query), h.repository.requests)
        assertEquals(ReportMapSceneState.Ready(query, 1, false), h.binding.state)
        assertEquals(listOf(page().markers), h.host.renders)
    }

    @Test
    fun filtrosSeCopianYResultadoConservaLimiteYHasMoreSinFiltrarDatosEnLaVista() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportMapPage>()
        val h = harness { if (it.region == FIRST) completion.await() else page(region = it.region) }
        val types = mutableSetOf(IncidentType.RISK)
        val statuses = mutableSetOf(ReportStatus.REPORTED)
        h.owner.resume()
        h.binding.refreshVisible(types, statuses, 3)
        types.clear()
        types.add(IncidentType.FIRE)
        statuses.clear()
        statuses.add(ReportStatus.CLOSED)
        runCurrent()
        val expected = query(types = setOf(IncidentType.RISK), statuses = setOf(ReportStatus.REPORTED), limit = 3)
        assertEquals(listOf(expected), h.repository.requests)
        assertEquals(ReportMapSceneState.Loading(expected), h.binding.state)
        completion.complete(page(hasMore = true))
        advanceUntilIdle()

        assertEquals(ReportMapSceneState.Ready(expected, 1, true), h.binding.state)
        h.host.region = SECOND
        h.host.idle()
        runCurrent()
        assertEquals(query(SECOND, expected.types, expected.statuses, 3), h.repository.requests.last())
    }

    @Test
    fun cambiarZonaRetiraMarcadoresPreviosAntesDeLeerLaNueva() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportMapPage>()
        val h = harness { if (it.region == FIRST) page() else completion.await() }
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        assertEquals(page().markers, h.host.markers)
        h.host.region = SECOND
        h.host.idle()

        assertTrue(h.host.markers.isEmpty())
        assertEquals(ReportMapSceneState.Loading(query(SECOND)), h.binding.state)
        runCurrent()
        assertTrue(h.host.markers.isEmpty())
        completion.complete(page("segunda", SECOND))
        advanceUntilIdle()

        assertEquals(page("segunda", SECOND).markers, h.host.markers)
        assertEquals(ReportMapSceneState.Ready(query(SECOND), 1, false), h.binding.state)
    }

    @Test
    fun lecturaAnteriorQueIgnoraCancelacionNoPintaSobreZonaNueva() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val h = harness {
            if (it.region == FIRST) withContext(NonCancellable) {
                completion.await()
                page("anterior")
            } else page("actual", SECOND)
        }
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        h.host.region = SECOND
        h.host.idle()
        runCurrent()
        assertTrue(h.repository.jobs.first()!!.isCancelled)
        val expected = ReportMapSceneState.Ready(query(SECOND), 1, false)
        assertEquals(expected, h.binding.state)
        completion.complete(Unit)
        advanceUntilIdle()

        assertEquals(expected, h.binding.state)
        assertEquals(listOf(page("actual", SECOND).markers), h.host.renders)
        assertEquals(listOf(query(), query(SECOND)), h.repository.requests)
    }

    @Test
    fun cambiarCamaraAntesDelIdleImpidePintarElResultadoDeLaProyeccionAnterior() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportMapPage>()
        val h = harness { if (it.region == FIRST) completion.await() else page(region = it.region) }
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        h.host.region = SECOND
        completion.complete(page())
        advanceUntilIdle()

        assertEquals(ReportMapSceneState.ViewportUnavailable, h.binding.state)
        assertTrue(h.host.renders.isEmpty())
        assertEquals(ReportMapState.Idle, h.model.state.value)
        h.host.idle()
        runCurrent()
        assertEquals(listOf(query(), query(SECOND)), h.repository.requests)
        assertEquals(ReportMapSceneState.Ready(query(SECOND), 1, false), h.binding.state)
    }

    @Test
    fun estiloSinProyeccionNoConsultaYElIdlePosteriorSoloSigueElGestoPrevio() = runTest(dispatcher) {
        val h = harness()
        h.host.region = null
        h.owner.resume()
        h.host.idle()
        assertEquals(0, h.host.reads)
        assertTrue(h.binding.refreshVisible())
        runCurrent()
        assertEquals(ReportMapSceneState.ViewportUnavailable, h.binding.state)
        assertTrue(h.repository.requests.isEmpty())
        h.host.region = FIRST
        h.host.idle()
        runCurrent()

        assertEquals(listOf(query()), h.repository.requests)
        assertEquals(ReportMapSceneState.Ready(query(), 1, false), h.binding.state)
    }

    @Test
    fun falloDeProyeccionEsGenericoYExigeOtroRefreshAntesDeConsultar() = runTest(dispatcher) {
        val h = harness()
        h.host.failRead = true
        h.owner.resume()
        assertTrue(h.binding.refreshVisible())
        assertEquals(ReportMapSceneState.Error(null, MAP_ERROR), h.binding.state)
        assertTrue(h.repository.requests.isEmpty())
        h.host.failRead = false
        h.host.idle()
        runCurrent()
        assertTrue(h.repository.requests.isEmpty())
        h.binding.refreshVisible()
        runCurrent()

        assertEquals(ReportMapSceneState.Ready(query(), 1, false), h.binding.state)
    }

    @Test
    fun renderParcialQueFallaSeRetiraYNoPublicaReadyNiDatosInternos() = runTest(dispatcher) {
        val h = harness()
        h.host.failRender = true
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        assertTrue(h.host.markers.isEmpty())
        assertEquals(ReportMapSceneState.Error(query(), MAP_ERROR), h.binding.state)
        assertEquals(ReportMapState.Idle, h.model.state.value)
        assertFalse(h.states.any { it is ReportMapSceneState.Ready })
        h.host.failRender = false
        h.host.idle()
        runCurrent()
        assertEquals(1, h.repository.requests.size)
        h.binding.refreshVisible()
        runCurrent()

        assertEquals(ReportMapSceneState.Ready(query(), 1, false), h.binding.state)
        assertEquals(2, h.repository.requests.size)
    }

    @Test
    fun falloAlLimpiarAntesDeCargarNoIniciaLaLecturaYPermiteReintentar() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        h.host.failClear = true
        h.binding.refreshVisible()
        runCurrent()
        assertTrue(h.repository.requests.isEmpty())
        assertEquals(ReportMapSceneState.Error(query(), MAP_ERROR), h.binding.state)
        h.host.failClear = false
        h.binding.refreshVisible()
        runCurrent()

        assertEquals(ReportMapSceneState.Ready(query(), 1, false), h.binding.state)
    }

    @Test
    fun falloDeConsultaDeZonaNuevaNoRestauraMarcadoresDeOtraZona() = runTest(dispatcher) {
        val h = harness { if (it.region == SECOND) throw IOException("Ruta privada") else page() }
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        h.host.region = SECOND
        h.host.idle()
        runCurrent()

        assertEquals(ReportMapSceneState.Error(query(SECOND), QUERY_ERROR), h.binding.state)
        assertTrue(h.host.markers.isEmpty())
        assertEquals(listOf(page().markers), h.host.renders)
        assertNull((h.model.state.value as ReportMapState.Error).previous)
    }

    @Test
    fun zonaSinIncidentesReemplazaLaListaConVacioYPublicaConteoCero() = runTest(dispatcher) {
        val h = harness { if (it.region == SECOND) ReportMapPage(emptyList(), false) else page() }
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        h.host.region = SECOND
        h.host.idle()
        runCurrent()

        assertEquals(ReportMapSceneState.Ready(query(SECOND), 0, false), h.binding.state)
        assertTrue(h.host.markers.isEmpty())
        assertEquals(emptyList<ReportMapMarker>(), h.host.renders.last())
    }

    @Test
    fun idlesRepetidosEnMismaZonaNoAbrenLecturasDuplicadasNiRepintan() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportMapPage>()
        val h = harness { completion.await() }
        h.owner.resume()
        h.binding.refreshVisible()
        h.host.idle()
        h.host.idle()
        runCurrent()
        assertEquals(listOf(query()), h.repository.requests)
        completion.complete(page())
        advanceUntilIdle()
        h.host.idle()
        h.host.idle()
        runCurrent()

        assertEquals(listOf(query()), h.repository.requests)
        assertEquals(listOf(page().markers), h.host.renders)
    }

    @Test
    fun salirDeResumedCancelaLecturaYLimpiaSinRenovarAlVolver() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        var attempts = 0
        val h = harness {
            if (++attempts == 1) withContext(NonCancellable) { completion.await(); page("tardio") }
            else page("renovado")
        }
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        h.owner.pause()
        assertTrue(h.repository.jobs.first()!!.isCancelled)
        assertEquals(ReportMapState.Idle, h.model.state.value)
        assertEquals(ReportMapSceneState.Idle, h.binding.state)
        h.host.deliverLateIdle()
        completion.complete(Unit)
        advanceUntilIdle()
        h.owner.stop()
        h.owner.resume()
        h.host.idle()
        runCurrent()
        assertTrue(h.host.renders.isEmpty())
        assertEquals(1, h.repository.requests.size)
        h.binding.refreshVisible()
        runCurrent()

        assertEquals(listOf(page("renovado").markers), h.host.renders)
    }

    @Test
    fun cerrarCancelaUnaLecturaNoCooperativaYRetiraCallbacksUnaSolaVez() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        val h = harness { withContext(NonCancellable) { completion.await(); page() } }
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        h.binding.close()
        h.binding.close()
        assertTrue(h.repository.jobs.single()!!.isCancelled)
        assertEquals(ReportMapSceneState.Closed, h.binding.state)
        assertEquals(ReportMapState.Idle, h.model.state.value)
        assertNull(h.host.listener)
        assertEquals(1, h.host.removals)
        assertEquals(1, h.host.closes)
        h.host.region = SECOND
        h.host.deliverLateIdle()
        assertFalse(h.binding.refreshVisible())
        completion.complete(Unit)
        advanceUntilIdle()
        h.owner.pause()
        h.owner.resume()

        assertTrue(h.host.renders.isEmpty())
        assertEquals(1, h.repository.requests.size)
        assertEquals(1, h.host.closes)
    }

    @Test
    fun destruirElDuenoCierraModeloYHostSinEsperarOtroEvento() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        h.owner.destroy()

        assertEquals(ReportMapSceneState.Closed, h.binding.state)
        assertEquals(ReportMapState.Idle, h.model.state.value)
        assertTrue(h.host.markers.isEmpty())
        assertEquals(1, h.host.closes)
        assertFalse(h.binding.refreshVisible())
    }

    @Test
    fun destruirDuranteElRegistroDeCameraIdleDejaElCallbackInerte() = runTest(dispatcher) {
        val owner = Owner().also { it.resume() }
        val host = Host().also { it.onRegister = { owner.destroy() } }
        val repository = Repository { page() }
        val model = model(repository)
        val binding = connect(owner, model, host)
        host.deliverLateIdle()
        runCurrent()

        assertEquals(ReportMapSceneState.Closed, binding.state)
        assertTrue(repository.requests.isEmpty())
        assertEquals(1, host.closes)
    }

    @Test
    fun cerrarDesdeLoadingNoIniciaLaLecturaYaRetirada() = runTest(dispatcher) {
        lateinit var h: Harness
        h = harness(onState = { if (it is ReportMapSceneState.Loading) h.binding.close() })
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()

        assertEquals(ReportMapSceneState.Closed, h.binding.state)
        assertTrue(h.repository.requests.isEmpty())
        assertTrue(h.host.renders.isEmpty())
    }

    @Test
    fun otraConsultaDesdeLoadingSustituyeZonaYFiltrosAntesDeLaPrimeraLectura() = runTest(dispatcher) {
        lateinit var h: Harness
        var replace = true
        h = harness(onState = {
            if (it is ReportMapSceneState.Loading && replace) {
                replace = false
                h.host.region = SECOND
                h.binding.refreshVisible(setOf(IncidentType.FIRE), setOf(ReportStatus.CLOSED), 5)
            }
        }) { page("nueva", it.region) }
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        val expected = query(SECOND, setOf(IncidentType.FIRE), setOf(ReportStatus.CLOSED), 5)

        assertEquals(listOf(expected), h.repository.requests)
        assertEquals(ReportMapSceneState.Ready(expected, 1, false), h.binding.state)
        assertFalse(h.states.any { it is ReportMapSceneState.Ready && it.query.region == FIRST })
    }

    @Test
    fun refreshReentranteAlLimpiarNoDejaQueLaCargaAnteriorPiseLaNueva() = runTest(dispatcher) {
        val h = harness { page("actual", it.region) }
        h.owner.resume()
        h.host.onClear = {
            h.host.region = SECOND
            h.binding.refreshVisible()
        }
        h.binding.refreshVisible()
        runCurrent()

        assertEquals(listOf(query(SECOND)), h.repository.requests)
        assertEquals(ReportMapSceneState.Ready(query(SECOND), 1, false), h.binding.state)
        assertEquals(page("actual", SECOND).markers, h.host.markers)
    }

    @Test
    fun refreshReentranteAlRenderizarNoPublicaReadyDeLaCargaRetirada() = runTest(dispatcher) {
        val h = harness { page("actual", it.region) }
        h.owner.resume()
        h.host.onRender = {
            h.host.region = SECOND
            h.binding.refreshVisible()
        }
        h.binding.refreshVisible()
        runCurrent()

        assertEquals(listOf(query(), query(SECOND)), h.repository.requests)
        assertEquals(ReportMapSceneState.Ready(query(SECOND), 1, false), h.binding.state)
        assertFalse(h.states.any { it is ReportMapSceneState.Ready && it.query.region == FIRST })
        assertEquals(page("actual", SECOND).markers, h.host.markers)
    }

    @Test
    fun refreshReentranteAlLeerProyeccionNoCancelaLaConsultaMasReciente() = runTest(dispatcher) {
        val h = harness { page("actual", it.region) }
        h.owner.resume()
        h.host.onRead = {
            h.host.region = SECOND
            h.binding.refreshVisible()
        }
        h.binding.refreshVisible()
        runCurrent()

        assertEquals(listOf(query(SECOND)), h.repository.requests)
        assertEquals(ReportMapSceneState.Ready(query(SECOND), 1, false), h.binding.state)
    }

    @Test
    fun refreshAlComprobarLaProyeccionDelResultadoDescartaEseRenderAnterior() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportMapPage>()
        val h = harness { if (it.region == FIRST) completion.await() else page("actual", SECOND) }
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        h.host.onRead = {
            h.host.region = SECOND
            h.binding.refreshVisible()
        }
        completion.complete(page("anterior"))
        advanceUntilIdle()

        assertEquals(listOf(page("actual", SECOND).markers), h.host.renders)
        assertEquals(ReportMapSceneState.Ready(query(SECOND), 1, false), h.binding.state)
    }

    @Test
    fun fallosDuranteCierreAunCancelanLecturaRetiranHostYDejanTodosLosCallbacksInertes() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportMapPage>()
        val h = harness { completion.await() }
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        h.host.failRemove = true
        h.host.failClear = true
        h.host.failClose = true
        try {
            h.binding.close()
            fail("El cierre conserva el primer fallo del SDK después de intentar toda la limpieza.")
        } catch (failure: IOException) {
            assertEquals(2, failure.suppressed.size)
        }
        assertEquals(ReportMapSceneState.Closed, h.binding.state)
        assertEquals(ReportMapState.Idle, h.model.state.value)
        assertEquals(1, h.host.closes)
        h.host.deliverLateIdle()
        completion.complete(page())
        advanceUntilIdle()

        assertTrue(h.host.renders.isEmpty())
        assertEquals(1, h.repository.requests.size)
        h.host.failClear = false
    }

    @Test
    fun crearVistaNuevaReclamaElModeloAntesDeCerrarLaViejaSinCancelarLaNuevaLectura() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportMapPage>()
        val old = harness { if (it.region == SECOND) completion.await() else page() }
        old.owner.resume()
        old.binding.refreshVisible()
        runCurrent()
        val owner = Owner().also { it.resume() }
        val host = Host().also { it.region = SECOND }
        val states = mutableListOf<ReportMapSceneState>()
        val latest = connect(owner, old.model, host, states::add)
        latest.refreshVisible()
        runCurrent()
        val job = old.repository.jobs.last()!!
        old.binding.close()
        old.host.deliverLateIdle()
        assertFalse(job.isCancelled)
        assertEquals(ReportMapState.Loading(query(SECOND)), old.model.state.value)
        completion.complete(page("actual", SECOND))
        advanceUntilIdle()

        assertEquals(ReportMapSceneState.Ready(query(SECOND), 1, false), latest.state)
        assertEquals(page("actual", SECOND).markers, host.markers)
        assertEquals(0, host.closes)
        assertEquals(1, old.host.closes)
        assertFalse(old.states.contains(ReportMapSceneState.Closed))
    }

    @Test
    fun pauseYCallbacksDeVistaViejaNoLimpianNiPintanLaConsultaDeSuSucesora() = runTest(dispatcher) {
        val old = harness { page("actual", it.region) }
        old.owner.resume()
        old.binding.refreshVisible()
        runCurrent()
        val host = Host().also { it.region = SECOND }
        val latest = connect(Owner().also { it.resume() }, old.model, host)
        latest.refreshVisible()
        runCurrent()
        val expected = old.model.state.value
        val oldReads = old.host.reads
        val oldRenders = old.host.renders.size
        old.owner.pause()
        old.owner.stop()
        old.owner.resume()
        old.host.region = FIRST
        old.host.deliverLateIdle()
        assertFalse(old.binding.refreshVisible())
        runCurrent()

        assertSame(expected, old.model.state.value)
        assertEquals(ReportMapSceneState.Ready(query(SECOND), 1, false), latest.state)
        assertEquals(oldReads, old.host.reads)
        assertEquals(oldRenders, old.host.renders.size)
        assertEquals(page("actual", SECOND).markers, host.markers)
    }

    @Test
    fun mismaConsultaEnOtraVistaNoResucitaLecturaAnteriorAunqueIgnoreCancelacion() = runTest(dispatcher) {
        val completion = CompletableDeferred<Unit>()
        var attempts = 0
        val old = harness {
            if (++attempts == 1) withContext(NonCancellable) { completion.await(); page("vieja") }
            else page("nueva")
        }
        old.owner.resume()
        old.binding.refreshVisible()
        runCurrent()
        val host = Host()
        val latest = connect(Owner().also { it.resume() }, old.model, host)
        latest.refreshVisible()
        runCurrent()
        assertTrue(old.repository.jobs.first()!!.isCancelled)
        old.host.deliverLateIdle()
        old.binding.close()
        completion.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(query(), query()), old.repository.requests)
        assertTrue(old.host.renders.isEmpty())
        assertEquals(listOf(page("nueva").markers), host.renders)
        assertEquals(ReportMapSceneState.Ready(query(), 1, false), latest.state)
        assertEquals(ReportMapState.Ready(query(), page("nueva")), old.model.state.value)
    }

    @Test
    fun callbackDeCierrePuedeCrearSucesoraSinQueLaLiberacionAnteriorLaLimpie() = runTest(dispatcher) {
        lateinit var old: Harness
        lateinit var latest: ReportMapSceneBinding
        val host = Host().also { it.region = SECOND }
        old = harness(onState = {
            if (it == ReportMapSceneState.Closed) {
                latest = connect(Owner().also { it.resume() }, old.model, host)
                latest.refreshVisible()
            }
        }) { page("actual", it.region) }
        old.owner.resume()
        old.binding.refreshVisible()
        runCurrent()
        old.binding.close()
        runCurrent()

        assertEquals(ReportMapSceneState.Ready(query(SECOND), 1, false), latest.state)
        assertEquals(ReportMapState.Ready(query(SECOND), page("actual", SECOND)), old.model.state.value)
        assertEquals(page("actual", SECOND).markers, host.markers)
    }

    @Test
    fun limitesInvalidosNoRetiranUnaConsultaValidaNiTocanElSdk() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        val expected = h.binding.state
        val reads = h.host.reads
        val clears = h.host.clears
        for (limit in listOf(0, -1, 501, Int.MAX_VALUE)) {
            try {
                h.binding.refreshVisible(limit = limit)
                fail("El límite $limit debe rechazarse antes de cambiar la consulta.")
            } catch (_: IllegalArgumentException) { /* Contrato de ReportMapQuery. */ }
        }

        assertEquals(expected, h.binding.state)
        assertEquals(reads, h.host.reads)
        assertEquals(clears, h.host.clears)
        assertEquals(1, h.repository.requests.size)
    }

    @Test
    fun duenoDestruidoSeRechazaSinReclamarElModeloDeUnaVistaVigente() = runTest(dispatcher) {
        val h = harness()
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        val owner = Owner().also { it.resume(); it.destroy() }
        val host = Host()
        try {
            ReportMapSceneBinding(owner, h.model, host)
            fail("Una pantalla destruida no puede tomar la propiedad del modelo.")
        } catch (_: IllegalStateException) { /* El dueño vigente sigue intacto. */ }
        h.host.region = SECOND
        h.host.idle()
        runCurrent()

        assertEquals(0, host.registrations)
        assertEquals(ReportMapSceneState.Ready(query(SECOND), 1, false), h.binding.state)
        assertEquals(2, h.repository.requests.size)
    }

    @Test
    fun limpiarElModeloDuranteLaLecturaRetiraLaEscenaSinRenovarPorIdle() = runTest(dispatcher) {
        val completion = CompletableDeferred<ReportMapPage>()
        val h = harness { completion.await() }
        h.owner.resume()
        h.binding.refreshVisible()
        runCurrent()
        h.model.clear()
        runCurrent()
        assertEquals(ReportMapSceneState.Idle, h.binding.state)
        h.host.idle()
        completion.complete(page())
        advanceUntilIdle()

        assertTrue(h.host.renders.isEmpty())
        assertEquals(1, h.repository.requests.size)
    }

    private fun harness(
        onState: (ReportMapSceneState) -> Unit = {},
        lookup: suspend (ReportMapQuery) -> ReportMapPage = { page(region = it.region) },
    ): Harness {
        val repository = Repository(lookup)
        val model = model(repository)
        val owner = Owner()
        val host = Host()
        val states = mutableListOf<ReportMapSceneState>()
        val binding = connect(owner, model, host) { states.add(it); onState(it) }
        return Harness(owner, host, repository, model, binding, states)
    }

    private fun model(repository: ReportMapRepository): ReportMapViewModel {
        val store = ViewModelStore().also(stores::add)
        return ViewModelProvider(store, ReportMapViewModel.factory(repository))[ReportMapViewModel::class.java]
    }

    private fun connect(
        owner: Owner,
        model: ReportMapViewModel,
        host: Host,
        onState: (ReportMapSceneState) -> Unit = {},
    ) = ReportMapSceneBinding(owner, model, host, onState).also(connections::add)

    private class Harness(
        val owner: Owner,
        val host: Host,
        val repository: Repository,
        val model: ReportMapViewModel,
        val binding: ReportMapSceneBinding,
        val states: List<ReportMapSceneState>,
    )

    private class Repository(private val lookup: suspend (ReportMapQuery) -> ReportMapPage) : ReportMapRepository {
        val requests = mutableListOf<ReportMapQuery>()
        val jobs = mutableListOf<Job?>()
        override suspend fun queryMarkers(query: ReportMapQuery): ReportMapPage {
            requests.add(query)
            jobs.add(currentCoroutineContext()[Job])
            return lookup(query)
        }
    }

    private class Host : ReportMapSceneHost {
        var region: MapRegion? = FIRST
        var listener: (() -> Unit)? = null
        private var previous: (() -> Unit)? = null
        var reads = 0
        var clears = 0
        var registrations = 0
        var removals = 0
        var closes = 0
        var failRead = false
        var failRender = false
        var failClear = false
        var failRemove = false
        var failClose = false
        var onRead: (() -> Unit)? = null
        var onClear: (() -> Unit)? = null
        var onRender: (() -> Unit)? = null
        var onRegister: (() -> Unit)? = null
        var markers = emptyList<ReportMapMarker>()
            private set
        val renders = mutableListOf<List<ReportMapMarker>>()

        override fun visibleRegion(): MapRegion? {
            reads++
            if (failRead) throw IOException("Proyección privada del SDK")
            val callback = onRead.also { onRead = null }
            callback?.invoke()
            return region
        }

        override fun setCameraIdleListener(listener: (() -> Unit)?) {
            if (listener == null) {
                removals++
                if (failRemove) throw IOException("No se retiró el listener")
            } else {
                registrations++
                previous = listener
            }
            this.listener = listener
            val callback = onRegister.also { onRegister = null }
            callback?.invoke()
        }

        override fun renderMarkers(markers: List<ReportMapMarker>) {
            this.markers = markers.toList()
            renders.add(this.markers)
            if (failRender) throw IOException("Render parcialmente aplicado")
            val callback = onRender.also { onRender = null }
            callback?.invoke()
        }

        override fun clearMarkers() {
            clears++
            if (failClear) throw IOException("No se retiró la capa")
            markers = emptyList()
            val callback = onClear.also { onClear = null }
            callback?.invoke()
        }

        override fun close() {
            closes++
            if (failClose) throw IOException("No se retiró el host")
        }

        fun idle() { listener?.invoke() }
        fun deliverLateIdle() { previous?.invoke() }
    }

    private class Owner : LifecycleOwner {
        private val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
        fun resume() { registry.currentState = Lifecycle.State.RESUMED }
        fun pause() { registry.currentState = Lifecycle.State.STARTED }
        fun stop() { registry.currentState = Lifecycle.State.CREATED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
    }

    private companion object {
        val FIRST = MapRegion(19.0, 20.0, -100.0, -99.0)
        val SECOND = MapRegion(20.0, 21.0, -101.0, -100.0)
        const val MAP_ERROR = "No se pudieron mostrar los incidentes de esta zona. Intenta nuevamente."
        const val QUERY_ERROR = "No se pudieron consultar los incidentes de esta zona. Intenta nuevamente."
        fun query(
            region: MapRegion = FIRST,
            types: Set<IncidentType> = emptySet(),
            statuses: Set<ReportStatus> = emptySet(),
            limit: Int = 200,
        ) = ReportMapQuery(region, types, statuses, limit)

        fun page(id: String = "reporte-local", region: MapRegion = FIRST, hasMore: Boolean = false) = ReportMapPage(
            listOf(ReportMapMarker(
                id, IncidentType.RISK, Priority.MEDIUM, ReportStatus.REPORTED,
                (region.south + region.north) / 2, (region.west + region.east) / 2,
            )), hasMore,
        )
    }
}
