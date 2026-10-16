package com.example.citizensecurity.report

import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportStatusChangeAccess
import com.example.citizensecurity.domain.ReportStatusChangeCommand
import com.example.citizensecurity.domain.ReportStatusChangeJournal
import com.example.citizensecurity.domain.ReportStatusChangeReceipt
import com.example.citizensecurity.domain.ReportStatusChangeRequest
import com.example.citizensecurity.domain.ReportStatusChangeScope
import com.example.citizensecurity.domain.ReportStatusConflictException
import com.example.citizensecurity.domain.ReportStatusEvent
import com.example.citizensecurity.domain.ReportStatusEventKind
import com.example.citizensecurity.domain.ReportStatusNotFoundException
import com.example.citizensecurity.domain.ReportStatusOperationConflictException
import com.example.citizensecurity.domain.ReportStatusPermissionDeniedException
import com.example.citizensecurity.domain.ReportAdministrationPermissionDeniedException
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportStatusRecoveryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun configureDispatcher() = Dispatchers.setMain(dispatcher)
    @After fun releaseModels() {
        try { stores.forEach { it.clear() } } finally { Dispatchers.resetMain() }
    }

    @Test fun constructorEsSoloAwaitingRecoveryYNoConsultaRetieneOEscribe() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access()
        var ids = 0
        val model = model(journal, access) { ids++; OPERATION }
        runCurrent()
        assertSame(ReportStatusRecoveryState.AwaitingRecovery, model.state.value)
        assertFalse(submit(model))
        assertFalse(model.retry())
        assertFalse(model.acknowledgeConfirmed())
        assertEquals(0, journal.reads)
        assertEquals(0, journal.leases)
        assertEquals(0, ids)
        assertTrue(access.finds.isEmpty())
        assertTrue(access.changes.isEmpty())
    }

    @Test fun recuperarVacioHabilitaUnGestoQueRetieneAntesDeLeerReciboYEscribir() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access(journal.events)
        val model = model(journal, access)
        assertTrue(model.recover())
        runCurrent()
        assertSame(ReportStatusRecoveryState.Ready, model.state.value)
        assertTrue(journal.retains.isEmpty())
        assertTrue(access.finds.isEmpty())
        journal.events.clear()
        assertTrue(submit(model))
        val working = model.state.value as ReportStatusRecoveryState.Working
        runCurrent()
        val confirmed = model.state.value as ReportStatusRecoveryState.Confirmed
        assertSame(working.request, confirmed.request)
        assertSame(confirmed.request, journal.retains.single())
        assertSame(confirmed.request, access.changes.single())
        assertEquals(SCOPE, confirmed.request.scope)
        assertEquals(NOW, confirmed.request.requestedAt)
        assertEquals(listOf("read", "retain", "read", "find", "change"), journal.events)
        assertFalse(submit(model))
        assertFalse(model.retry())
        assertTrue(model.acknowledgeConfirmed())
        runCurrent()
        assertNull(journal.body)
        assertEquals(listOf(confirmed.request), journal.clears)
        assertSame(ReportStatusRecoveryState.Ready, model.state.value)
    }

    @Test fun recuperarSolicitudPersistidaSoloLeeYElReintentoConservaTodoElCuerpo() = runTest(dispatcher) {
        val saved = request()
        val journal = Journal(saved)
        val access = Access(journal.events)
        val model = model(journal, access) { error("Recuperar no genera UUID") }
        model.recover()
        runCurrent()
        val pending = model.state.value as ReportStatusRecoveryState.Pending
        assertSame(saved, pending.request)
        assertFalse(pending.requiresRecovery)
        assertEquals(listOf("read", "find"), journal.events)
        assertTrue(journal.retains.isEmpty())
        assertTrue(access.changes.isEmpty())
        journal.events.clear()
        assertTrue(model.retry())
        runCurrent()
        assertEquals(listOf("read", "find", "change"), journal.events)
        assertSame(saved, access.changes.single())
        assertSame(saved, (model.state.value as ReportStatusRecoveryState.Confirmed).request)
    }

    @Test fun reciboPersistidoSeRecuperaSinOtraEscrituraNiRetencionAutomatica() = runTest(dispatcher) {
        val saved = request()
        val journal = Journal(saved)
        val access = Access().apply { receipts[saved.command.operationId] = receipt(saved) }
        val model = model(journal, access)
        model.recover()
        runCurrent()
        val confirmed = model.state.value as ReportStatusRecoveryState.Confirmed
        assertEquals(receipt(saved), confirmed.receipt)
        assertEquals(1, access.finds.size)
        assertTrue(access.changes.isEmpty())
        assertTrue(journal.retains.isEmpty())
        assertTrue(journal.clears.isEmpty())
        assertFalse(submit(model))
    }

    @Test fun procesoRecreadoRecuperaCommitCuyaRespuestaSePerdioSinSegundoCambio() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply {
            changeAnswer = { saved ->
                receipts[saved.command.operationId] = receipt(saved)
                throw IOException("commit confirmado, respuesta perdida")
            }
        }
        val first = readyModel(journal, access)
        submit(first)
        runCurrent()
        val pending = first.state.value as ReportStatusRecoveryState.Pending
        assertTrue(pending.requiresRecovery)
        assertFalse(first.retry())
        stores.last().clear()
        val recreated = model(journal, access) { error("No generar identidad") }
        assertSame(ReportStatusRecoveryState.AwaitingRecovery, recreated.state.value)
        recreated.recover()
        runCurrent()
        assertEquals(pending.request, (recreated.state.value as ReportStatusRecoveryState.Confirmed).request)
        assertEquals(1, access.changes.size)
        assertEquals(1, journal.retains.size)
    }

    @Test fun falloDeRetainAntesDeCommitExigeRecuperarVacioYReintentaElMismoUuid() = runTest(dispatcher) {
        val journal = Journal().apply { retainBefore = { throw IOException("ruta privada") } }
        val access = Access()
        var ids = 0
        val model = readyModel(journal, access) { ids++; OPERATION }
        submit(model)
        runCurrent()
        val first = model.state.value as ReportStatusRecoveryState.Pending
        assertTrue(first.requiresRecovery)
        assertTrue(access.changes.isEmpty())
        assertNull(journal.body)
        assertFalse(first.message.contains("privada"))
        assertFalse(model.retry())
        assertFalse(submit(model, reason = "Una identidad distinta"))
        journal.retainBefore = {}
        model.recover()
        runCurrent()
        val recovered = model.state.value as ReportStatusRecoveryState.Pending
        assertSame(first.request, recovered.request)
        assertFalse(recovered.requiresRecovery)
        assertTrue(journal.retains.isEmpty())
        assertTrue(model.retry())
        runCurrent()
        assertSame(first.request, access.changes.single())
        assertSame(first.request, journal.retains.single())
        assertEquals(1, ids)
        assertEquals(NOW, access.changes.single().requestedAt)
    }

    @Test fun retainQueConfirmaYLuegoFallaSeReleeSinDuplicarRetencion() = runTest(dispatcher) {
        val journal = Journal().apply { retainAfter = { throw IOException("respuesta perdida") } }
        val access = Access()
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        val saved = journal.body
        assertTrue(access.changes.isEmpty())
        journal.retainAfter = {}
        model.recover()
        runCurrent()
        assertEquals(saved, (model.state.value as ReportStatusRecoveryState.Pending).request)
        model.retry()
        runCurrent()
        assertSame(saved, access.changes.single())
        assertEquals(1, journal.retains.size)
    }

    @Test fun releerTrasRetainDebeEncontrarTodoElCuerpoAntesDeEntrarASql() = runTest(dispatcher) {
        val journal = Journal().apply { retainAfter = { body = request(reason = "Cuerpo alterado indebidamente") } }
        val access = Access()
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        assertTrue(access.changes.isEmpty())
        assertTrue(access.finds.isEmpty())
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        model.recover()
        runCurrent()
        assertFalse(model.retry())
        assertFalse(submit(model))
        assertTrue(access.changes.isEmpty())
    }

    @Test fun desaparicionDelDiarioTrasIniciarWriterNuncaAutorizaNuevoUuid() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply { changeAnswer = { throw IOException("resultado incierto") } }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        val first = model.state.value as ReportStatusRecoveryState.Pending
        journal.body = null
        model.recover()
        runCurrent()
        val blocked = model.state.value as ReportStatusRecoveryState.Pending
        assertSame(first.request, blocked.request)
        assertTrue(blocked.requiresRecovery)
        assertFalse(model.retry())
        assertFalse(submit(model))
        assertEquals(1, access.changes.size)
    }

    @Test fun diarioCorruptoNoSeTrataComoVacioNiPermiteSobrescribir() = runTest(dispatcher) {
        val journal = Journal().apply { readBefore = { throw IOException("JSON inválido") } }
        val access = Access()
        val model = model(journal, access)
        model.recover()
        runCurrent()
        assertTrue(model.state.value is ReportStatusRecoveryState.Error)
        assertFalse(submit(model))
        assertFalse(model.retry())
        assertTrue(journal.retains.isEmpty())
        assertTrue(access.finds.isEmpty())
        assertTrue(access.changes.isEmpty())
    }

    @Test fun diarioDeOtroScopeSeBloqueaAntesDeConsultarRecibos() = runTest(dispatcher) {
        listOf(SCOPE.copy(ownerAccountId = OTHER), SCOPE.copy(actorAccountId = OTHER)).forEach { wrongScope ->
            val journal = Journal(request().copy(scope = wrongScope))
            val access = Access()
            val model = model(journal, access)
            model.recover()
            runCurrent()
            assertTrue(model.state.value is ReportStatusRecoveryState.Error)
            assertFalse(submit(model))
            assertTrue(access.finds.isEmpty())
            assertTrue(access.changes.isEmpty())
            assertTrue(journal.clears.isEmpty())
        }
    }

    @Test fun cambioConcurrenteDelDiarioNoReemplazaUnCuerpoAjeno() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access()
        val model = readyModel(journal, access)
        val other = request(operationId = OTHER, reason = "Solicitud de otra revisión")
        journal.body = other
        submit(model)
        runCurrent()
        val blocked = model.state.value as ReportStatusRecoveryState.Pending
        assertNotEquals(other, blocked.request)
        assertSame(other, journal.body)
        assertTrue(journal.retains.isEmpty())
        assertTrue(access.finds.isEmpty())
        assertTrue(access.changes.isEmpty())
        model.recover()
        runCurrent()
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        assertFalse(model.retry())
    }

    @Test fun recibosConOtroFolioActorOperacionSecuenciaMotivoOTransicionNoConfirman() = runTest(dispatcher) {
        val changes: List<(ReportStatusChangeReceipt) -> ReportStatusChangeReceipt> = listOf(
            { it.copy(reportId = OTHER) },
            { it.copy(event = it.event.copy(actorAccountId = OTHER)) },
            { it.copy(event = it.event.copy(operationId = OTHER)) },
            { it.copy(event = it.event.copy(sequence = it.event.sequence + 1)) },
            { it.copy(event = it.event.copy(reason = "Otra razón diferente")) },
            { it.copy(event = it.event.copy(fromStatus = ReportStatus.IN_REVIEW, toStatus = ReportStatus.ATTENDED)) },
        )
        changes.forEach { modify ->
            val saved = request()
            val journal = Journal(saved)
            val access = Access().apply { receipts[OPERATION] = modify(receipt(saved)) }
            val model = model(journal, access)
            model.recover()
            runCurrent()
            val pending = model.state.value as ReportStatusRecoveryState.Pending
            assertSame(saved, pending.request)
            assertTrue(pending.requiresRecovery)
            assertFalse(model.retry())
            assertTrue(access.changes.isEmpty())
            assertTrue(journal.clears.isEmpty())
        }
    }

    @Test fun resultadoDelWriterConOtroActorNoConfirmaNiLimpiaLaSolicitud() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply { changeAnswer = { receipt(it).event.copy(actorAccountId = OTHER) } }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        assertEquals(SCOPE, journal.body?.scope)
        assertFalse(model.acknowledgeConfirmed())
        assertTrue(journal.clears.isEmpty())
    }

    @Test fun operacionReutilizadaOIoNuncaAutorizanDescartarCuerpoPersistido() = runTest(dispatcher) {
        listOf(ReportStatusOperationConflictException(), IOException("Incierto"))
            .forEach { failure ->
                val journal = Journal()
                val access = Access().apply { changeAnswer = { throw failure } }
                val model = readyModel(journal, access)
                submit(model)
                runCurrent()
                val pending = model.state.value as ReportStatusRecoveryState.Pending
                assertTrue(pending.requiresRecovery)
                assertFalse(model.retry())
                assertFalse(submit(model, reason = "No reemplazar una solicitud persistida"))
                assertSame(pending.request, journal.body)
                assertTrue(journal.clears.isEmpty())
                model.recover()
                runCurrent()
                assertSame(pending.request, (model.state.value as ReportStatusRecoveryState.Pending).request)
                assertTrue(model.retry())
                runCurrent()
                assertEquals(2, access.changes.size)
                assertSame(access.changes[0], access.changes[1])
                assertTrue(journal.clears.isEmpty())
            }
    }

    @Test fun conflictoDefinitivoConservaSolicitudYExigeOtroGestoAntesDeHabilitarUnaNueva() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access(journal.events).apply { changeAnswer = { throw ReportStatusConflictException() } }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        val rejected = model.state.value as ReportStatusRecoveryState.Rejected
        assertSame(journal.body, rejected.request)
        assertEquals(ReportStatusRejectionReason.CONFLICT, rejected.reason)
        assertEquals(listOf("read", "read", "retain", "read", "find", "change", "read", "find"), journal.events)
        assertFalse(model.retry())
        assertFalse(model.acknowledgeConfirmed())
        assertFalse(submit(model))
        assertTrue(journal.clears.isEmpty())
        val beforeChanges = access.changes.toList()
        assertTrue(model.acknowledgeRejected(rejected))
        runCurrent()
        assertSame(ReportStatusRecoveryState.Ready, model.state.value)
        assertNull(journal.body)
        assertEquals(listOf(rejected.request), journal.clears)
        assertEquals(beforeChanges, access.changes)
    }

    @Test fun folioAusenteTambienEsRechazoDefinitivoSinLimpiarAutomaticamente() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply { changeAnswer = { throw ReportStatusNotFoundException() } }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        val rejected = model.state.value as ReportStatusRecoveryState.Rejected
        assertEquals(ReportStatusRejectionReason.NOT_FOUND, rejected.reason)
        assertSame(rejected.request, journal.body)
        assertTrue(journal.clears.isEmpty())
        model.recover(rejected)
        runCurrent()
        assertEquals(rejected.request, (model.state.value as ReportStatusRecoveryState.Rejected).request)
        assertEquals(1, access.changes.size)
        assertTrue(model.acknowledgeRejected())
        runCurrent()
        assertNull(journal.body)
    }

    @Test fun reciboConfirmadoGanaAunqueElBackendDevuelvaUnaExceptionDeRechazoTipada() = runTest(dispatcher) {
        listOf(ReportStatusConflictException(), ReportStatusNotFoundException()).forEach { failure ->
            val journal = Journal()
            val access = Access().apply {
                changeAnswer = { saved -> receipts[OPERATION] = receipt(saved); throw failure }
            }
            val model = readyModel(journal, access)
            submit(model)
            runCurrent()
            val confirmed = model.state.value as ReportStatusRecoveryState.Confirmed
            assertEquals(receipt(confirmed.request), confirmed.receipt)
            assertFalse(model.acknowledgeRejected())
            assertSame(confirmed.request, journal.body)
            assertTrue(journal.clears.isEmpty())
            assertEquals(1, access.changes.size)
        }
    }

    @Test fun rechazoConReciboAjenoOConsultaInciertaSoloPuedeRecuperarse() = runTest(dispatcher) {
        listOf(false, true).forEach { uncertain ->
            val journal = Journal()
            val access = Access().apply {
                changeAnswer = { saved ->
                    if (uncertain) findAnswer = { throw IOException("No hay respuesta fiable") }
                    else receipts[OPERATION] = receipt(saved).copy(reportId = OTHER)
                    throw ReportStatusConflictException()
                }
            }
            val model = readyModel(journal, access)
            submit(model)
            runCurrent()
            assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
            assertFalse(model.acknowledgeRejected())
            assertFalse(model.retry())
            assertTrue(journal.clears.isEmpty())
        }
    }

    @Test fun rechazoNoPuedeOcultarUnDiarioCambiadoDuranteElBackend() = runTest(dispatcher) {
        val other = request(operationId = OTHER)
        val journal = Journal()
        val access = Access().apply {
            changeAnswer = { journal.body = other; throw ReportStatusConflictException() }
        }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        assertSame(other, journal.body)
        assertFalse(model.acknowledgeRejected())
        assertTrue(journal.clears.isEmpty())
    }

    @Test fun ackDeRechazoConReciboTardioPublicaConfirmedSinLimpiarNiRepetirCambio() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply { changeAnswer = { throw ReportStatusConflictException() } }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        val rejected = model.state.value as ReportStatusRecoveryState.Rejected
        access.receipts[OPERATION] = receipt(rejected.request)
        assertTrue(model.acknowledgeRejected(rejected))
        runCurrent()
        val confirmed = model.state.value as ReportStatusRecoveryState.Confirmed
        assertSame(rejected.request, confirmed.request)
        assertEquals(receipt(rejected.request), confirmed.receipt)
        assertSame(rejected.request, journal.body)
        assertTrue(journal.clears.isEmpty())
        assertEquals(1, access.changes.size)
        assertTrue(model.acknowledgeConfirmed(confirmed))
        runCurrent()
        assertNull(journal.body)
    }

    @Test fun ackDeRechazoConDiarioDistintoNoRetiraNingunaSolicitud() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply { changeAnswer = { throw ReportStatusConflictException() } }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        val rejected = model.state.value as ReportStatusRecoveryState.Rejected
        val other = rejected.request.copy(requestedAt = NOW.plusSeconds(1))
        journal.body = other
        assertTrue(model.acknowledgeRejected(rejected))
        runCurrent()
        assertSame(other, journal.body)
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        assertTrue(journal.clears.isEmpty())
        assertEquals(1, access.changes.size)
    }

    @Test fun revocacionAlResolverRechazoOAntesDeAckConservaSolicitudYReciboConfirmado() = runTest(dispatcher) {
        listOf(false, true).forEach { revokeBeforeAck ->
            val journal = Journal()
            val access = Access().apply {
                changeAnswer = { saved ->
                    if (!revokeBeforeAck) {
                        receipts[OPERATION] = receipt(saved)
                        active = false
                    }
                    throw ReportStatusConflictException()
                }
            }
            val model = readyModel(journal, access)
            submit(model)
            runCurrent()
            val saved = checkNotNull(journal.body)
            if (revokeBeforeAck) {
                assertTrue(model.state.value is ReportStatusRecoveryState.Rejected)
                access.active = false
                assertTrue(model.acknowledgeRejected())
                runCurrent()
            }
            assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
            assertSame(saved, journal.body)
            assertTrue(journal.clears.isEmpty())
            val newAccess = Access().apply { receipts.putAll(access.receipts) }
            val recreated = model(journal, newAccess)
            recreated.recover()
            runCurrent()
            if (revokeBeforeAck) assertTrue(recreated.state.value is ReportStatusRecoveryState.Pending)
            else assertEquals(receipt(saved), (recreated.state.value as ReportStatusRecoveryState.Confirmed).receipt)
        }
    }

    @Test fun revocacionDuranteLecturaDeAckRechazadoImpideClear() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply { changeAnswer = { throw ReportStatusNotFoundException() } }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        val saved = journal.body
        access.findAnswer = { access.active = false; null }
        assertTrue(model.acknowledgeRejected())
        runCurrent()
        assertSame(saved, journal.body)
        assertTrue(journal.clears.isEmpty())
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
    }

    @Test fun clearDeRechazoConfirmadoPeroRespuestaPerdidaExigeRecoveryDeSoloLectura() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply { changeAnswer = { throw ReportStatusConflictException() } }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        journal.clearAfter = { throw IOException("Respuesta perdida") }
        model.acknowledgeRejected()
        runCurrent()
        assertNull(journal.body)
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        assertFalse(model.retry())
        val beforeFinds = access.finds.size
        assertTrue(model.recover())
        runCurrent()
        assertSame(ReportStatusRecoveryState.Ready, model.state.value)
        assertEquals(beforeFinds + 1, access.finds.size)
        assertEquals(1, access.changes.size)
        assertEquals(1, journal.clears.size)
    }

    @Test fun reciboTardioTrasClearRechazadoInciertoSigueVisibleYSeReconoceSinSegundoClear() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply { changeAnswer = { throw ReportStatusConflictException() } }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        val request = (model.state.value as ReportStatusRecoveryState.Rejected).request
        journal.clearAfter = { throw IOException("Respuesta perdida") }
        model.acknowledgeRejected()
        runCurrent()
        assertNull(journal.body)
        access.receipts[OPERATION] = receipt(request)
        model.recover()
        runCurrent()
        val confirmed = model.state.value as ReportStatusRecoveryState.Confirmed
        assertEquals(receipt(request), confirmed.receipt)
        assertEquals(1, journal.clears.size)
        assertTrue(model.acknowledgeConfirmed(confirmed))
        runCurrent()
        assertSame(ReportStatusRecoveryState.Ready, model.state.value)
        assertEquals(1, journal.clears.size)
        assertEquals(1, access.changes.size)
    }

    @Test fun procesoRecreadoNoReconstruyePruebaDeRechazoPorAusenciaDeRecibo() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply { changeAnswer = { throw ReportStatusConflictException() } }
        val original = readyModel(journal, access)
        submit(original)
        runCurrent()
        assertTrue(original.state.value is ReportStatusRecoveryState.Rejected)
        stores.last().clear()
        val recreated = model(journal, access)
        recreated.recover()
        runCurrent()
        assertTrue(recreated.state.value is ReportStatusRecoveryState.Pending)
        assertFalse(recreated.acknowledgeRejected())
        assertEquals(1, access.changes.size)
        assertTrue(journal.clears.isEmpty())
        // Only another explicit retry can execute the unchanged command and obtain fresh proof.
        assertTrue(recreated.retry())
        runCurrent()
        assertTrue(recreated.state.value is ReportStatusRecoveryState.Rejected)
        assertSame(access.changes[0], access.changes[1])
    }

    @Test fun gestoObsoletoDeRechazoNoConfirmaUnaPantallaPosteriorIgual() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply { changeAnswer = { throw ReportStatusConflictException() } }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        val before = model.state.value as ReportStatusRecoveryState.Rejected
        model.recover(before)
        runCurrent()
        val after = model.state.value as ReportStatusRecoveryState.Rejected
        assertTrue(before !== after)
        assertFalse(model.acknowledgeRejected(before))
        assertFalse(model.recover(before))
        assertTrue(model.acknowledgeRejected(after))
        runCurrent()
        assertNull(journal.body)
    }

    @Test fun checkAccessSoloConsultaAutoridadEnDispatcherYReservaSingleFlight() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access()
        val model = model(journal, access)
        assertTrue(model.checkAccess())
        assertEquals(0, access.checks)
        assertFalse(model.recover())
        runCurrent()
        assertTrue(access.checks > 0)
        assertSame(ReportStatusRecoveryState.AwaitingRecovery, model.state.value)
        access.denial = ReportStatusPermissionDeniedException()
        assertTrue(model.checkAccess())
        runCurrent()
        assertSame(ReportStatusRecoveryState.Closed, model.state.value)
        assertFalse(model.recover())
        assertEquals(0, journal.reads)
    }

    @Test fun checkAccessConVentanaPausadaConservaElModeloHastaVolverSinConfundirloConRolRetirado() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply { active = false }
        val model = model(journal, access)
        assertTrue(model.checkAccess())
        runCurrent()
        assertSame(ReportStatusRecoveryState.AwaitingRecovery, model.state.value)
        assertEquals(0, journal.reads)
        access.active = true
        assertTrue(model.checkAccess())
        runCurrent()
        assertTrue(model.recover())
        runCurrent()
        assertSame(ReportStatusRecoveryState.Ready, model.state.value)
    }

    @Test fun submitNoConsultaAutoridadBloqueanteAntesDeDespacharIo() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access()
        val model = readyModel(journal, access)
        val priorChecks = access.checks
        assertTrue(submit(model))
        assertEquals(priorChecks, access.checks)
        assertTrue(access.changes.isEmpty())
        runCurrent()
        assertTrue(access.checks > priorChecks)
        assertTrue(model.state.value is ReportStatusRecoveryState.Confirmed)
    }

    @Test fun guardPuroDeSesionImpidePublicarUnCommitTrasRetirarLaSesion() = runTest(dispatcher) {
        var sessionActive = true
        val journal = Journal()
        val access = Access().apply {
            changeAnswer = { saved ->
                receipts[OPERATION] = receipt(saved)
                sessionActive = false
                receipt(saved).event
            }
        }
        val model = ReportStatusRecoveryViewModel(
            journal, access, dispatcher, Clock.fixed(NOW, ZoneOffset.UTC), { OPERATION },
            checkSession = { check(sessionActive) },
        ).also { model -> ViewModelStore().also { it.put("guarded", model); stores += it } }
        model.recover()
        runCurrent()
        submit(model)
        runCurrent()
        assertSame(ReportStatusRecoveryState.Closed, model.state.value)
        assertFalse(model.checkAccess())
        assertSame(ReportStatusRecoveryState.Closed, model.state.value)
        assertTrue(journal.clears.isEmpty())
        val fresh = model(journal, access)
        fresh.recover()
        runCurrent()
        assertEquals(receipt(checkNotNull(journal.body)), (fresh.state.value as ReportStatusRecoveryState.Confirmed).receipt)
    }

    @Test fun closeBorraDatosVisiblesPeroConservaDiarioYLeaseDelWriterNoCooperativo() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val journal = Journal()
        val access = Access().apply {
            changeAnswer = { saved -> withContext(NonCancellable) {
                gate.await(); receipts[OPERATION] = receipt(saved); receipt(saved).event
            } }
        }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        val saved = journal.body
        model.close()
        assertSame(ReportStatusRecoveryState.Closed, model.state.value)
        assertEquals(1, journal.activeLease)
        assertSame(saved, journal.body)
        assertFalse(model.recover())
        assertFalse(model.retry())
        assertFalse(model.acknowledgeRejected())
        assertFalse(model.acknowledgeConfirmed())
        assertFalse(model.cancel())
        assertFalse(submit(model))
        gate.complete(Unit)
        runCurrent()
        assertSame(ReportStatusRecoveryState.Closed, model.state.value)
        assertEquals(0, journal.activeLease)
        assertSame(saved, journal.body)
        val recreated = model(journal, access)
        recreated.recover()
        runCurrent()
        assertEquals(receipt(checkNotNull(saved)), (recreated.state.value as ReportStatusRecoveryState.Confirmed).receipt)
    }

    @Test fun denegacionTipadaDuranteCambioCierraSinExponerCuerpoNiRetirarLaSolicitudDurable() = runTest(dispatcher) {
        listOf(ReportStatusPermissionDeniedException(), ReportAdministrationPermissionDeniedException()).forEach { denial ->
            val journal = Journal()
            val access = Access().apply { changeAnswer = { throw denial } }
            val model = readyModel(journal, access)
            submit(model)
            runCurrent()
            val saved = checkNotNull(journal.body)
            assertSame(ReportStatusRecoveryState.Closed, model.state.value)
            assertFalse(model.recover())
            assertFalse(model.retry())
            assertFalse(model.acknowledgeConfirmed())
            assertFalse(model.acknowledgeRejected())
            assertTrue(journal.clears.isEmpty())
            val restored = model(journal, Access())
            restored.recover()
            runCurrent()
            assertSame(saved, (restored.state.value as ReportStatusRecoveryState.Pending).request)
            assertEquals(1, access.changes.size)
            assertTrue(restored.retry())
            runCurrent()
            assertSame(saved, (restored.state.value as ReportStatusRecoveryState.Confirmed).request)
        }
    }

    @Test fun denegacionTipadaDespuesDelCommitOcultaTodoYUnModeloNuevoRecuperaElReciboExacto() = runTest(dispatcher) {
        listOf(ReportStatusPermissionDeniedException(), ReportAdministrationPermissionDeniedException()).forEach { denied ->
            val journal = Journal()
            val access = Access().apply {
                changeAnswer = { saved ->
                    receipts[OPERATION] = receipt(saved)
                    denial = denied
                    receipt(saved).event
                }
            }
            val model = readyModel(journal, access)
            submit(model)
            runCurrent()
            val saved = checkNotNull(journal.body)
            assertSame(ReportStatusRecoveryState.Closed, model.state.value)
            assertTrue(journal.clears.isEmpty())
            assertEquals(1, access.changes.size)
            val allowed = Access().apply { receipts.putAll(access.receipts) }
            val restored = model(journal, allowed)
            restored.recover()
            runCurrent()
            val confirmed = restored.state.value as ReportStatusRecoveryState.Confirmed
            assertSame(saved, confirmed.request)
            assertEquals(receipt(saved), confirmed.receipt)
            assertTrue(allowed.changes.isEmpty())
            assertSame(saved, journal.body)
        }
    }

    @Test fun ioInciertoNoPublicaPendingPrivadoSiLaReconsultaDeAutoridadEsDenegada() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply {
            changeAnswer = { saved ->
                receipts[OPERATION] = receipt(saved)
                denial = ReportAdministrationPermissionDeniedException()
                throw IOException("Respuesta del commit perdida")
            }
        }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        val saved = checkNotNull(journal.body)
        assertSame(ReportStatusRecoveryState.Closed, model.state.value)
        assertTrue(journal.clears.isEmpty())
        val restoredAccess = Access().apply { receipts.putAll(access.receipts) }
        val restored = model(journal, restoredAccess)
        restored.recover()
        runCurrent()
        assertEquals(receipt(saved), (restored.state.value as ReportStatusRecoveryState.Confirmed).receipt)
        assertTrue(restoredAccess.changes.isEmpty())
    }

    @Test fun ioGenericoConSelloDeSesionRetiradoCierraSinRevelarLaSolicitud() = runTest(dispatcher) {
        var activeSession = true
        val journal = Journal()
        val access = Access().apply {
            changeAnswer = { activeSession = false; throw IOException("Resultado desconocido") }
        }
        val model = ReportStatusRecoveryViewModel(journal, access, dispatcher, Clock.fixed(NOW, ZoneOffset.UTC),
            { OPERATION }, checkSession = { check(activeSession) }).also { value ->
            ViewModelStore().also { it.put("stamp", value); stores += it }
        }
        model.recover()
        runCurrent()
        submit(model)
        runCurrent()
        val saved = checkNotNull(journal.body)
        assertSame(ReportStatusRecoveryState.Closed, model.state.value)
        assertTrue(journal.clears.isEmpty())
        val restored = model(journal, Access())
        restored.recover()
        runCurrent()
        assertSame(saved, (restored.state.value as ReportStatusRecoveryState.Pending).request)
    }

    @Test fun falloDeDiarioConAutoridadVigenteReconsultaPermisoYConservaPendingSinWriter() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access()
        val model = readyModel(journal, access)
        var checksAtFailure = 0
        journal.retainAfter = { checksAtFailure = access.checks; throw IOException("Recibo del diario perdido") }
        submit(model)
        runCurrent()
        val pending = model.state.value as ReportStatusRecoveryState.Pending
        assertTrue(pending.requiresRecovery)
        assertTrue(access.checks > checksAtFailure)
        assertSame(pending.request, journal.body)
        assertTrue(access.changes.isEmpty())
        assertTrue(journal.clears.isEmpty())
        journal.retainAfter = {}
        model.recover(pending)
        runCurrent()
        assertSame(pending.request, (model.state.value as ReportStatusRecoveryState.Pending).request)
        assertTrue(access.changes.isEmpty())
    }

    @Test fun permisoRetiradoAlRecuperarCierraAunqueYaHayaLeidoElCuerpoDelDiario() = runTest(dispatcher) {
        val saved = request()
        val journal = Journal(saved)
        val access = Access().apply {
            findAnswer = { denial = ReportStatusPermissionDeniedException(); null }
        }
        val model = model(journal, access)
        model.recover()
        runCurrent()
        assertSame(ReportStatusRecoveryState.Closed, model.state.value)
        assertSame(saved, journal.body)
        assertEquals(1, access.finds.size)
        assertTrue(journal.clears.isEmpty())
        val restored = model(journal, Access())
        restored.recover()
        runCurrent()
        assertSame(saved, (restored.state.value as ReportStatusRecoveryState.Pending).request)
    }

    @Test fun ventanaNoDisponibleConSesionVigenteConservaIncertidumbreYRecuperaSinDuplicarCommit() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access().apply {
            changeAnswer = { saved ->
                receipts[OPERATION] = receipt(saved)
                active = false // An unavailable foreground window, not a typed authority denial.
                throw IOException("Respuesta perdida al pausar")
            }
        }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        val pending = model.state.value as ReportStatusRecoveryState.Pending
        assertTrue(pending.requiresRecovery)
        assertSame(pending.request, journal.body)
        assertTrue(journal.clears.isEmpty())
        access.active = true
        model.recover(pending)
        runCurrent()
        assertEquals(receipt(pending.request), (model.state.value as ReportStatusRecoveryState.Confirmed).receipt)
        assertEquals(1, access.changes.size)
    }

    @Test fun retryReleeReciboAntesDeWriterYDetectaCommitQueLlegoTrasRecover() = runTest(dispatcher) {
        val saved = request()
        val journal = Journal(saved)
        val access = Access()
        val model = model(journal, access)
        model.recover()
        runCurrent()
        access.receipts[OPERATION] = receipt(saved)
        assertTrue(model.retry())
        runCurrent()
        assertTrue(model.state.value is ReportStatusRecoveryState.Confirmed)
        assertEquals(2, access.finds.size)
        assertTrue(access.changes.isEmpty())
        assertTrue(journal.retains.isEmpty())
    }

    @Test fun acknowledgeRevalidaReciboCompletoIncluidaHoraAntesDeLimpiar() = runTest(dispatcher) {
        val saved = request()
        val journal = Journal(saved)
        val original = receipt(saved)
        val access = Access().apply { receipts[OPERATION] = original }
        val model = model(journal, access)
        model.recover()
        runCurrent()
        access.receipts[OPERATION] = original.copy(event = original.event.copy(recordedAt = NOW.plusSeconds(1)))
        assertTrue(model.acknowledgeConfirmed())
        runCurrent()
        assertTrue(journal.clears.isEmpty())
        assertSame(saved, journal.body)
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        model.recover()
        runCurrent()
        assertTrue(model.acknowledgeConfirmed())
        runCurrent()
        assertEquals(listOf(saved), journal.clears)
    }

    @Test fun acknowledgeNoLimpiaDiarioQueCambioDeCuerpoMientrasSeVeiaConfirmed() = runTest(dispatcher) {
        val saved = request()
        val journal = Journal(saved)
        val access = Access().apply { receipts[OPERATION] = receipt(saved) }
        val model = model(journal, access)
        model.recover()
        runCurrent()
        val other = saved.copy(requestedAt = NOW.plusSeconds(1))
        journal.body = other
        model.acknowledgeConfirmed()
        runCurrent()
        assertSame(other, journal.body)
        assertTrue(journal.clears.isEmpty())
        assertFalse(model.retry())
    }

    @Test fun revokeAntesDeRecoverNoEntraAlDiarioYNoConcedePermisoPorUuidPersistido() = runTest(dispatcher) {
        val journal = Journal(request())
        val access = Access().apply { active = false }
        val model = model(journal, access)
        model.recover()
        runCurrent()
        assertEquals(0, journal.reads)
        assertTrue(model.state.value is ReportStatusRecoveryState.Error)
        assertTrue(access.finds.isEmpty())
        assertTrue(access.changes.isEmpty())
    }

    @Test fun revokeDespuesDeRetainImpideSqlYRecuperacionNecesitaUnAccessNuevo() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access()
        val model = readyModel(journal, access)
        journal.retainAfter = { access.active = false }
        submit(model)
        runCurrent()
        assertTrue(access.changes.isEmpty())
        assertTrue(access.finds.isEmpty())
        assertFalse(model.retry())
        model.recover()
        runCurrent()
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        journal.retainAfter = {}
        val newAccess = Access()
        val restored = model(journal, newAccess)
        restored.recover()
        runCurrent()
        restored.retry()
        runCurrent()
        assertEquals(journal.body, newAccess.changes.single())
        assertTrue(restored.state.value is ReportStatusRecoveryState.Confirmed)
    }

    @Test fun cambiosDeScopeEnUnAccessMutableSeRechazanAntesDeEscribir() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access()
        val model = readyModel(journal, access)
        journal.retainAfter = { access.scope = SCOPE.copy(actorAccountId = OTHER) }
        submit(model)
        runCurrent()
        assertTrue(access.changes.isEmpty())
        assertTrue(access.finds.isEmpty())
        assertSame(ReportStatusRecoveryState.Closed, model.state.value)
    }

    @Test fun cancelAntesDeEjecutarConservaCuerpoSinRetenerHastaRecoverYRetry() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access()
        val model = readyModel(journal, access)
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it is ReportStatusRecoveryState.Working) model.cancel() }
        }
        submit(model)
        val pending = model.state.value as ReportStatusRecoveryState.Pending
        runCurrent()
        assertTrue(access.changes.isEmpty())
        assertTrue(journal.retains.isEmpty())
        observer.cancel()
        assertFalse(model.retry())
        model.recover()
        runCurrent()
        assertTrue(model.retry())
        runCurrent()
        assertSame(pending.request, access.changes.single())
        assertTrue(model.state.value is ReportStatusRecoveryState.Confirmed)
    }

    @Test fun reservaAntesDeEmitirRechazaGestosReentrantesDuranteRecoverYWorking() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access()
        val model = model(journal, access)
        var recoverObserved = false
        var workingObserved = false
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { state ->
                when (state) {
                    is ReportStatusRecoveryState.Recovering -> {
                        recoverObserved = !model.recover() && !submit(model) && !model.retry()
                    }
                    is ReportStatusRecoveryState.Working -> {
                        workingObserved = !model.recover() && !submit(model) && !model.retry() && !model.acknowledgeConfirmed()
                    }
                    else -> Unit
                }
            }
        }
        model.recover()
        runCurrent()
        submit(model)
        runCurrent()
        assertTrue(recoverObserved)
        assertTrue(workingObserved)
        assertEquals(1, access.changes.size)
        observer.cancel()
    }

    @Test fun cancelMantieneSingleFlightYLeaseHastaQueWriterNoCooperativoTermina() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val journal = Journal()
        val access = Access().apply {
            changeAnswer = { saved -> withContext(NonCancellable) {
                gate.await()
                receipt(saved).also { receipts[OPERATION] = it }.event
            } }
        }
        val first = readyModel(journal, access)
        val secondAccess = Access().apply { receipts.putAll(access.receipts) }
        val second = model(journal, secondAccess)
        submit(first)
        runCurrent()
        assertTrue(first.cancel())
        val cancelled = first.state.value
        assertFalse(first.recover())
        assertFalse(first.retry())
        assertFalse(submit(first))
        second.recover()
        runCurrent()
        assertTrue(second.state.value is ReportStatusRecoveryState.Recovering)
        assertTrue(secondAccess.finds.isEmpty())
        assertEquals(1, journal.activeLease)
        gate.complete(Unit)
        runCurrent()
        assertSame(cancelled, first.state.value)
        assertEquals(0, journal.activeLease)
        assertTrue(first.recover())
        runCurrent()
        assertTrue(first.state.value is ReportStatusRecoveryState.Confirmed)
        assertEquals(1, access.changes.size)
        assertTrue(secondAccess.changes.isEmpty())
    }

    @Test fun dosModelsQueVieronVacioNoEjecutanDosCuerposBajoLaMismaLease() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val journal = Journal()
        val firstAccess = Access().apply { changeAnswer = { gate.await(); receipt(it).event } }
        val first = readyModel(journal, firstAccess)
        val secondAccess = Access()
        val second = readyModel(journal, secondAccess) { OTHER }
        submit(first)
        runCurrent()
        submit(second, reason = "Solicitud distinta pero concurrente")
        runCurrent()
        assertTrue(secondAccess.changes.isEmpty())
        gate.complete(Unit)
        runCurrent()
        assertTrue(first.state.value is ReportStatusRecoveryState.Confirmed)
        assertTrue((second.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        assertEquals(1, journal.retains.size)
        assertEquals(1, firstAccess.changes.size)
        assertTrue(secondAccess.finds.isEmpty())
        assertTrue(secondAccess.changes.isEmpty())
    }

    @Test fun modeloRetiradoNoAceptaGestosYUnCommitTardioNoPublicaConfirmed() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val journal = Journal()
        val access = Access().apply {
            changeAnswer = { saved -> withContext(NonCancellable) { gate.await(); receipt(saved).event } }
        }
        val model = readyModel(journal, access)
        submit(model)
        runCurrent()
        stores.last().clear()
        assertFalse(model.recover())
        assertFalse(model.retry())
        assertFalse(submit(model))
        assertFalse(model.cancel())
        assertFalse(model.acknowledgeConfirmed())
        gate.complete(Unit)
        runCurrent()
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        assertEquals(1, access.changes.size)
        assertEquals(0, journal.activeLease)
    }

    @Test fun entradaInvalidaNoRetieneYPermiteCorregirSinReleerUnDiarioQueSigueVacio() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access()
        val model = readyModel(journal, access)
        assertFalse(submit(model, reportId = "1-1-1-1-1"))
        assertFalse(submit(model, sequence = Long.MAX_VALUE))
        assertFalse(submit(model, reason = "\uD800razón"))
        assertTrue(model.state.value is ReportStatusRecoveryState.Invalid)
        assertTrue(journal.retains.isEmpty())
        assertTrue(access.changes.isEmpty())
        assertTrue(submit(model))
        runCurrent()
        assertTrue(model.state.value is ReportStatusRecoveryState.Confirmed)
    }

    @Test fun revokeAntesDeAckNoLimpiaElCuerpoYNoPublicaReady() = runTest(dispatcher) {
        val saved = request()
        val journal = Journal(saved)
        val access = Access().apply { receipts[OPERATION] = receipt(saved) }
        val model = model(journal, access)
        model.recover()
        runCurrent()
        access.active = false
        model.acknowledgeConfirmed()
        runCurrent()
        assertSame(saved, journal.body)
        assertTrue(journal.clears.isEmpty())
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
    }

    @Test fun clearConfirmadoPeroRespuestaPerdidaSeResuelveSoloConReciboFrescoCompleto() = runTest(dispatcher) {
        val saved = request()
        val journal = Journal(saved)
        val access = Access().apply { receipts[OPERATION] = receipt(saved) }
        val model = model(journal, access)
        model.recover()
        runCurrent()
        journal.clearAfter = { throw IOException("Respuesta del clear perdida") }
        model.acknowledgeConfirmed()
        runCurrent()
        assertNull(journal.body)
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        assertFalse(submit(model))
        val previousReads = access.finds.size
        model.recover()
        runCurrent()
        assertSame(ReportStatusRecoveryState.Ready, model.state.value)
        assertEquals(previousReads + 1, access.finds.size)
        assertEquals(1, journal.clears.size)
        assertTrue(access.changes.isEmpty())
    }

    @Test fun clearFallidoConDiarioIntactoRecuperaConfirmedSinLimpiarAutomaticamente() = runTest(dispatcher) {
        val saved = request()
        val journal = Journal(saved)
        val access = Access().apply { receipts[OPERATION] = receipt(saved) }
        val model = model(journal, access)
        model.recover()
        runCurrent()
        journal.clearBefore = { throw IOException("No se confirmó clear") }
        model.acknowledgeConfirmed()
        runCurrent()
        assertSame(saved, journal.body)
        assertTrue(journal.clears.isEmpty())
        model.recover()
        runCurrent()
        assertTrue(model.state.value is ReportStatusRecoveryState.Confirmed)
        assertSame(saved, journal.body)
        assertTrue(journal.clears.isEmpty())
    }

    @Test fun ackInciertoConDiarioVacioNoResuelveSiReciboCambioOConsultaFallo() = runTest(dispatcher) {
        listOf(false, true).forEach { throws ->
            val saved = request()
            val journal = Journal(saved)
            val access = Access().apply { receipts[OPERATION] = receipt(saved) }
            val model = model(journal, access)
            model.recover()
            runCurrent()
            journal.clearAfter = { throw IOException("Respuesta del clear perdida") }
            model.acknowledgeConfirmed()
            runCurrent()
            if (throws) access.findAnswer = { throw IOException("Lectura fallida") }
            else access.receipts[OPERATION] = receipt(saved).copy(event = receipt(saved).event.copy(recordedAt = NOW.plusSeconds(1)))
            model.recover()
            runCurrent()
            assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
            assertFalse(submit(model))
            assertFalse(model.retry())
        }
    }

    @Test fun ackInciertoNoResuelveAlRevokeAunqueElDiarioQuedoVacio() = runTest(dispatcher) {
        val saved = request()
        val journal = Journal(saved)
        val access = Access().apply { receipts[OPERATION] = receipt(saved) }
        val model = model(journal, access)
        model.recover()
        runCurrent()
        journal.clearAfter = { access.active = false }
        model.acknowledgeConfirmed()
        runCurrent()
        assertNull(journal.body)
        model.recover()
        runCurrent()
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        assertFalse(submit(model))
    }

    @Test fun cancelDeAckNoCooperativoNoPublicaReadyTardioNiLiberaLeaseAntesDeClear() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val saved = request()
        val journal = Journal(saved)
        val access = Access().apply { receipts[OPERATION] = receipt(saved) }
        val model = model(journal, access)
        model.recover()
        runCurrent()
        journal.clearBefore = { withContext(NonCancellable) { gate.await() } }
        model.acknowledgeConfirmed()
        runCurrent()
        assertTrue(model.cancel())
        val cancelled = model.state.value
        assertFalse(model.recover())
        assertFalse(model.acknowledgeConfirmed())
        assertEquals(1, journal.activeLease)
        gate.complete(Unit)
        runCurrent()
        assertNull(journal.body)
        assertSame(cancelled, model.state.value)
        assertEquals(0, journal.activeLease)
        model.recover()
        runCurrent()
        assertSame(ReportStatusRecoveryState.Ready, model.state.value)
        assertEquals(1, journal.clears.size)
        assertTrue(access.changes.isEmpty())
    }

    @Test fun cancelDeRecoverNoCooperativoDescartaReciboTardioSinModificarDiario() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val saved = request()
        val journal = Journal(saved)
        val access = Access().apply {
            findAnswer = { withContext(NonCancellable) { gate.await(); receipt(it) } }
        }
        val model = model(journal, access)
        model.recover()
        runCurrent()
        model.cancel()
        val cancelled = model.state.value
        assertFalse(model.recover())
        gate.complete(Unit)
        runCurrent()
        assertSame(cancelled, model.state.value)
        assertSame(saved, journal.body)
        assertTrue(journal.clears.isEmpty())
        assertTrue(access.changes.isEmpty())
        assertTrue(model.recover())
        runCurrent()
        assertTrue(model.state.value is ReportStatusRecoveryState.Confirmed)
    }

    @Test fun revokeMientrasFindDevuelveNullImpideEntrarAlWriter() = runTest(dispatcher) {
        val journal = Journal()
        val access = Access()
        val model = readyModel(journal, access)
        access.findAnswer = { access.active = false; null }
        submit(model)
        runCurrent()
        assertEquals(1, access.finds.size)
        assertTrue(access.changes.isEmpty())
        assertTrue((model.state.value as ReportStatusRecoveryState.Pending).requiresRecovery)
        assertEquals(1, journal.retains.size)
        assertTrue(journal.clears.isEmpty())
    }

    @Test fun errorDeReciboTrasLeerSolicitudConservaSuIdentidadAunqueLuegoFalteElArchivo() = runTest(dispatcher) {
        val saved = request()
        val journal = Journal(saved)
        val access = Access().apply { findAnswer = { throw IOException("Recibo no disponible") } }
        val model = model(journal, access)
        model.recover()
        runCurrent()
        val pending = model.state.value as ReportStatusRecoveryState.Pending
        assertSame(saved, pending.request)
        assertTrue(pending.requiresRecovery)
        journal.body = null
        model.recover()
        runCurrent()
        assertSame(saved, (model.state.value as ReportStatusRecoveryState.Pending).request)
        assertFalse(model.retry())
        assertFalse(submit(model))
        assertTrue(access.changes.isEmpty())
        assertTrue(journal.retains.isEmpty())
    }

    private fun model(
        journal: Journal,
        access: Access,
        id: () -> String = { OPERATION },
    ): ReportStatusRecoveryViewModel {
        val model = ReportStatusRecoveryViewModel(journal, access, dispatcher, Clock.fixed(NOW, ZoneOffset.UTC), id)
        ViewModelStore().also { it.put("recovery", model); stores += it }
        return model
    }

    private suspend fun readyModel(
        journal: Journal,
        access: Access,
        id: () -> String = { OPERATION },
    ): ReportStatusRecoveryViewModel = model(journal, access, id).also {
        assertTrue(it.recover())
        dispatcher.scheduler.runCurrent()
        assertSame(ReportStatusRecoveryState.Ready, it.state.value)
    }

    private fun submit(
        model: ReportStatusRecoveryViewModel,
        reportId: String = FOLIO,
        sequence: Long = 1,
        reason: String = REASON,
    ) = model.submit(reportId, sequence, ReportStatus.REPORTED, ReportStatus.IN_REVIEW, reason)

    private class Journal(var body: ReportStatusChangeRequest? = null) : ReportStatusChangeJournal {
        val events = mutableListOf<String>()
        val retains = mutableListOf<ReportStatusChangeRequest>()
        val clears = mutableListOf<ReportStatusChangeRequest>()
        var reads = 0
        var leases = 0
        var activeLease = 0
        var readBefore: suspend () -> Unit = {}
        var retainBefore: suspend () -> Unit = {}
        var retainAfter: suspend () -> Unit = {}
        var clearBefore: suspend () -> Unit = {}
        var clearAfter: suspend () -> Unit = {}
        private val mutex = Mutex()
        override suspend fun read(): ReportStatusChangeRequest? {
            reads++
            events += "read"
            readBefore()
            return body
        }
        override suspend fun retain(request: ReportStatusChangeRequest) {
            events += "retain"
            retainBefore()
            check(body == null || body == request)
            body = request
            retains += request
            retainAfter()
        }
        override suspend fun clear(expected: ReportStatusChangeRequest) {
            events += "clear"
            clearBefore()
            check(body == expected)
            body = null
            clears += expected
            clearAfter()
        }
        override suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock {
            leases++
            activeLease++
            try { block() } finally { activeLease-- }
        }
    }

    private class Access(private val events: MutableList<String> = mutableListOf()) : ReportStatusChangeAccess {
        override var scope = SCOPE
        var active = true
        var checks = 0
        var denial: Exception? = null
        val finds = mutableListOf<ReportStatusChangeRequest>()
        val changes = mutableListOf<ReportStatusChangeRequest>()
        val receipts = mutableMapOf<String, ReportStatusChangeReceipt>()
        var findAnswer: suspend (ReportStatusChangeRequest) -> ReportStatusChangeReceipt? = { receipts[it.command.operationId] }
        var changeAnswer: suspend (ReportStatusChangeRequest) -> ReportStatusEvent = {
            receipt(it).also { received -> receipts[it.command.operationId] = received }.event
        }
        override fun checkCurrent() { checks++; denial?.let { throw it }; check(active) { "sesión retirada" } }
        override suspend fun findReceipt(request: ReportStatusChangeRequest): ReportStatusChangeReceipt? {
            checkCurrent()
            events += "find"
            finds += request
            return findAnswer(request)
        }
        override suspend fun change(request: ReportStatusChangeRequest): ReportStatusEvent {
            checkCurrent()
            events += "change"
            changes += request
            return changeAnswer(request)
        }
    }

    companion object {
        private const val FOLIO = "5a816abd-df15-43c0-9f47-001ec670f0ad"
        private const val ACTOR = "0686e908-944a-4bff-864c-23fb78a884fd"
        private const val OPERATION = "180ab572-4051-4277-9eef-785da2c2c19e"
        private const val OTHER = "823dc85f-712a-42d0-bbd8-717c08b68824"
        private const val REASON = "Se revisará la información del reporte"
        private val NOW = Instant.parse("2026-10-14T15:00:00Z")
        private val SCOPE = ReportStatusChangeScope(ACTOR, ACTOR)
        private fun request(operationId: String = OPERATION, reason: String = REASON) = ReportStatusChangeRequest(
            SCOPE, ReportStatusChangeCommand(operationId, FOLIO, 1, ReportStatus.REPORTED, ReportStatus.IN_REVIEW, reason), NOW,
        )
        private fun receipt(request: ReportStatusChangeRequest) = ReportStatusChangeReceipt(
            request.command.reportId,
            ReportStatusEvent(request.command.operationId, request.command.expectedSequence + 1,
                ReportStatusEventKind.STATUS_CHANGED, request.command.expectedStatus, request.command.targetStatus,
                NOW, request.scope.actorAccountId, request.command.reason),
        )
    }
}
