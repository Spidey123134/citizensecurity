package com.example.citizensecurity.report

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportStatusChangeCommand
import com.example.citizensecurity.domain.ReportStatusConflictException
import com.example.citizensecurity.domain.ReportStatusEvent
import com.example.citizensecurity.domain.ReportStatusEventKind
import com.example.citizensecurity.domain.ReportStatusNotFoundException
import com.example.citizensecurity.domain.ReportStatusOperationConflictException
import com.example.citizensecurity.domain.ReportStatusPermissionDeniedException
import com.example.citizensecurity.domain.ReportStatusRepository
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
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
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportStatusChangeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun configureDispatcher() = Dispatchers.setMain(dispatcher)
    @After fun releaseModels() {
        try { stores.forEach { it.clear() } } finally { Dispatchers.resetMain() }
    }

    @Test fun constructorNoEscribeYElGestoConservaElComandoExactoHastaElRecibo() = runTest(dispatcher) {
        val repository = Repository { receipt(it) }
        val model = model(repository)
        runCurrent()
        assertTrue(model.state.value is ReportStatusChangeState.Idle)
        assertTrue(repository.commands.isEmpty())
        assertTrue(submit(model))
        val writing = model.state.value as ReportStatusChangeState.Writing
        runCurrent()
        val confirmed = model.state.value as ReportStatusChangeState.Confirmed
        assertSame(writing.command, confirmed.command)
        assertSame(confirmed.command, repository.commands.single())
        assertEquals(SEQUENCE + 1, confirmed.event.sequence)
        assertFalse(model.retry())
        assertFalse(submit(model))
        assertFalse(model.discardRejected())
        assertTrue(model.acknowledgeConfirmed())
        assertTrue(model.state.value is ReportStatusChangeState.Idle)
    }

    @Test fun entradasInvalidasNoLleganAlEscritorNiConservanUnaSolicitud() = runTest(dispatcher) {
        val repository = Repository { receipt(it) }
        val model = model(repository)
        assertFalse(submit(model, reportId = "1-1-1-1-1"))
        assertFalse(submit(model, sequence = -1))
        assertFalse(submit(model, sequence = Long.MAX_VALUE))
        assertFalse(submit(model, target = ReportStatus.CLOSED))
        listOf("", "corto" + '\u0000', " motivo ", "x".repeat(501), "\uD800motivo").forEach {
            assertFalse(submit(model, reason = it))
            assertTrue(model.state.value is ReportStatusChangeState.Invalid)
        }
        runCurrent()
        assertTrue(repository.commands.isEmpty())
        assertTrue(submit(model))
        runCurrent()
        assertTrue(model.state.value is ReportStatusChangeState.Confirmed)
    }

    @Test fun reservaAnteriorALaEmisionImpideEscriturasReentrantesYGestosDuplicados() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val repository = Repository { gate.await(); receipt(it) }
        val model = model(repository)
        var rejectedReentrant = false
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { state ->
                if (state is ReportStatusChangeState.Writing) {
                    rejectedReentrant = !model.retry() && !submit(model, reason = "Otro motivo")
                }
            }
        }
        assertTrue(submit(model))
        runCurrent()
        assertTrue(rejectedReentrant)
        assertFalse(submit(model))
        assertEquals(1, repository.commands.size)
        gate.complete(Unit)
        runCurrent()
        assertTrue(model.state.value is ReportStatusChangeState.Confirmed)
        observer.cancel()
    }

    @Test fun errorInciertoBloqueaOtroCuerpoYReintentaLaMismaIdentidad() = runTest(dispatcher) {
        var attempts = 0
        val repository = Repository {
            if (++attempts == 1) throw IOException("ruta privada y secreto")
            receipt(it)
        }
        val model = model(repository)
        submit(model)
        runCurrent()
        val uncertain = model.state.value as ReportStatusChangeState.Pending
        assertFalse(uncertain.message.contains("privada"))
        assertFalse(model.discardRejected())
        assertFalse(submit(model, reason = "Nueva petición"))
        assertTrue(model.retry())
        runCurrent()
        val confirmed = model.state.value as ReportStatusChangeState.Confirmed
        assertSame(uncertain.command, confirmed.command)
        assertSame(repository.commands[0], repository.commands[1])
    }

    @Test fun cancelarNoLiberaLaReservaDeUnEscritorQuePuedeSeguirConfirmando() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var committed: ReportStatusEvent? = null
        var attempts = 0
        val repository = Repository { command ->
            if (++attempts == 1) withContext(NonCancellable) {
                gate.await()
                receipt(command).also { committed = it }
            } else checkNotNull(committed)
        }
        val model = model(repository)
        submit(model)
        runCurrent()
        val writing = model.state.value as ReportStatusChangeState.Writing
        assertTrue(model.cancel())
        val retained = model.state.value as ReportStatusChangeState.Pending
        assertSame(writing.command, retained.command)
        assertFalse(model.retry())
        assertFalse(submit(model))
        gate.complete(Unit)
        runCurrent()
        // A late receipt cannot replace the cancellation; recovery still needs a gesture.
        assertSame(retained, model.state.value)
        assertEquals(1, repository.commands.size)
        assertTrue(model.retry())
        runCurrent()
        assertTrue(model.state.value is ReportStatusChangeState.Confirmed)
        assertSame(repository.commands[0], repository.commands[1])
    }

    @Test fun cancelarDesdeWritingAntesDeEjecutarConservaElUuidSinEscribir() = runTest(dispatcher) {
        val repository = Repository { receipt(it) }
        val model = model(repository)
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            model.state.collect { if (it is ReportStatusChangeState.Writing) model.cancel() }
        }
        assertTrue(submit(model))
        runCurrent()
        val retained = model.state.value as ReportStatusChangeState.Pending
        assertTrue(repository.commands.isEmpty())
        observer.cancel()
        assertTrue(model.retry())
        runCurrent()
        assertSame(retained.command, repository.commands.single())
        assertTrue(model.state.value is ReportStatusChangeState.Confirmed)
    }

    @Test fun permisoDenegadoInicialNoInventaAdministradorYPermiteDescartarElRechazo() = runTest(dispatcher) {
        val repository = Repository { throw ReportStatusPermissionDeniedException() }
        val model = model(repository)
        submit(model)
        runCurrent()
        val rejected = model.state.value as ReportStatusChangeState.Rejected
        assertEquals(ReportStatusChangeRejection.PERMISSION, rejected.reason)
        assertFalse(rejected.receiptUncertain)
        assertTrue(model.discardRejected())
        assertTrue(model.state.value is ReportStatusChangeState.Idle)
        submit(model, reason = "Revisión de otra solicitud")
        runCurrent()
        assertTrue(repository.commands[0].operationId != repository.commands[1].operationId)
    }

    @Test fun revisionConflictivaYFolioAusenteSeDistinguenComoRechazosIniciales() = runTest(dispatcher) {
        listOf(
            ReportStatusConflictException() to ReportStatusChangeRejection.REVISION,
            ReportStatusNotFoundException() to ReportStatusChangeRejection.NOT_FOUND,
        ).forEach { (exception, reason) ->
            val model = model(Repository { throw exception })
            submit(model)
            runCurrent()
            val rejected = model.state.value as ReportStatusChangeState.Rejected
            assertEquals(reason, rejected.reason)
            assertFalse(rejected.receiptUncertain)
            assertTrue(model.discardRejected())
        }
    }

    @Test fun rechazoAlRecuperarNoPruebaQueElIntentoPrevioNoSeGuardo() = runTest(dispatcher) {
        listOf(ReportStatusPermissionDeniedException(), ReportStatusConflictException(), ReportStatusNotFoundException())
            .forEach { rejectedException ->
                var attempts = 0
                val repository = Repository {
                    if (++attempts == 1) throw IOException("Respuesta perdida después de confirmar")
                    throw rejectedException
                }
                val model = model(repository)
                submit(model)
                runCurrent()
                val retained = model.state.value as ReportStatusChangeState.Pending
                assertTrue(model.retry())
                runCurrent()
                val rejected = model.state.value as ReportStatusChangeState.Rejected
                assertTrue(rejected.receiptUncertain)
                assertSame(retained.command, rejected.command)
                assertFalse(model.discardRejected())
                assertFalse(submit(model))
            }
    }

    @Test fun conflictoDeIdentidadDeOperacionNuncaAutorizaUnUuidNuevo() = runTest(dispatcher) {
        val model = model(Repository { throw ReportStatusOperationConflictException() })
        submit(model)
        runCurrent()
        val rejected = model.state.value as ReportStatusChangeState.Rejected
        assertEquals(ReportStatusChangeRejection.OPERATION, rejected.reason)
        assertTrue(rejected.receiptUncertain)
        assertFalse(model.discardRejected())
        assertFalse(submit(model))
    }

    @Test fun recibosDeOtraOperacionSecuenciaMotivoOTransicionNoSePresentanComoExito() = runTest(dispatcher) {
        val modifiers: List<(ReportStatusEvent) -> ReportStatusEvent> = listOf(
            { it.copy(operationId = UUID.randomUUID().toString()) },
            { it.copy(sequence = it.sequence + 1) },
            { it.copy(reason = "Otro motivo válido") },
            { it.copy(fromStatus = ReportStatus.IN_REVIEW, toStatus = ReportStatus.ATTENDED) },
            { it.copy(kind = ReportStatusEventKind.CREATED, sequence = 1, fromStatus = null,
                toStatus = ReportStatus.REPORTED, reason = "") },
        )
        modifiers.forEach { modify ->
            val repository = Repository { modify(receipt(it)) }
            val model = model(repository)
            submit(model)
            runCurrent()
            assertTrue(model.state.value is ReportStatusChangeState.Pending)
            assertFalse(model.discardRejected())
            assertFalse(submit(model))
        }
    }

    @Test fun modeloRetiradoNoIniciaNiReintentaOtraEscritura() = runTest(dispatcher) {
        val repository = Repository { receipt(it) }
        val model = model(repository)
        stores.last().clear()
        assertFalse(submit(model))
        assertFalse(model.retry())
        assertFalse(model.cancel())
        assertFalse(model.discardRejected())
        assertFalse(model.acknowledgeConfirmed())
        runCurrent()
        assertTrue(repository.commands.isEmpty())
    }

    private fun model(repository: ReportStatusRepository): ReportStatusChangeViewModel {
        val store = ViewModelStore().also(stores::add)
        val provider = ViewModelProvider.create(store, ReportStatusChangeViewModel.factory(repository, dispatcher))
        return provider[ReportStatusChangeViewModel::class]
    }

    private fun submit(
        model: ReportStatusChangeViewModel,
        reportId: String = FOLIO,
        sequence: Long = SEQUENCE,
        target: ReportStatus = ReportStatus.IN_REVIEW,
        reason: String = REASON,
    ) = model.submit(reportId, sequence, ReportStatus.REPORTED, target, reason)

    private class Repository(private val answer: suspend (ReportStatusChangeCommand) -> ReportStatusEvent) :
        ReportStatusRepository {
        val commands = mutableListOf<ReportStatusChangeCommand>()
        override suspend fun changeStatus(command: ReportStatusChangeCommand): ReportStatusEvent {
            commands += command
            return answer(command)
        }
    }

    companion object {
        private const val FOLIO = "5a816abd-df15-43c0-9f47-001ec670f0ad"
        private const val ACTOR = "0686e908-944a-4bff-864c-23fb78a884fd"
        private const val SEQUENCE = 1L
        private const val REASON = "Se revisará la información del reporte"
        private fun receipt(command: ReportStatusChangeCommand) = ReportStatusEvent(
            command.operationId, command.expectedSequence + 1, ReportStatusEventKind.STATUS_CHANGED,
            command.expectedStatus, command.targetStatus, Instant.parse("2026-10-13T15:00:00Z"), ACTOR,
            command.reason,
        )
    }
}
