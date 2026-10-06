package com.example.citizensecurity.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDraftViewModelTest {
    @Test
    fun borradorNuevoNoInventaTipoPrioridadFechaUbicacionONumeroDeReporte() {
        val model = ReportDraftViewModel(SavedStateHandle())
        assertEquals(ReportDraftState(), model.state.value)
        val errors = invalid(model.review(NOW)).errors
        assertTrue(errors.keys.containsAll(listOf(ReportDraftField.TYPE, ReportDraftField.PRIORITY, ReportDraftField.OCCURRED_AT)))
    }

    @Test
    fun edicionesConservanTextoUnicodeEspaciosYFechaConNanosegundos() {
        val model = readyModel()
        assertEquals(ReportDraftEditResult.Accepted, model.setDescription("  Hay humo frente al café 🧯  "))
        assertEquals(ReportDraftEditResult.Accepted, model.setLocationReference("  Frente al árbol 🌳  "))
        val occurredAt = NOW.minusSeconds(20).plusNanos(123_456_789)
        model.setOccurredAt(occurredAt)
        val draft = valid(model.review(NOW)).draft
        assertEquals("  Hay humo frente al café 🧯  ", draft.description)
        assertEquals("  Frente al árbol 🌳  ", draft.location.reference)
        assertEquals(occurredAt, draft.occurredAt)
        assertEquals(IncidentType.FIRE, draft.type)
        assertEquals(Priority.HIGH, draft.priority)
    }

    @Test
    fun abrirMapaNoModificaCamposNiCoordenadas() {
        val model = readyModelWithCoordinates()
        val before = model.state.value
        val request = model.beginLocationSelection()!!
        assertEquals(before.location, request.originalLocation)
        assertEquals(before.copy(locationSelectionPending = true), model.state.value)
    }

    @Test
    fun confirmarSoloActualizaCoordenadasConservandoEdicionesDuranteMapa() {
        val model = readyModelWithCoordinates()
        val request = model.beginLocationSelection()!!
        model.setDescription("Se observa humo nuevo desde el mercado.")
        model.setType(IncidentType.RISK)
        model.setPriority(Priority.LOW)
        model.setOccurredAt(NOW.minusSeconds(300))
        model.setLocationReference("Referencia actual corregida")
        assertTrue(model.applyLocationSelection(request.token, confirmed(NEW_LOCATION)))
        val draft = valid(model.review(NOW)).draft
        assertEquals("Referencia actual corregida", draft.location.reference)
        assertEquals(NEW_LOCATION.latitude, draft.location.latitude)
        assertEquals(NEW_LOCATION.longitude, draft.location.longitude)
        assertEquals("Se observa humo nuevo desde el mercado.", draft.description)
        assertEquals(IncidentType.RISK, draft.type)
        assertEquals(Priority.LOW, draft.priority)
        assertEquals(NOW.minusSeconds(300), draft.occurredAt)
        assertFalse(model.state.value.locationSelectionPending)
    }

    @Test
    fun cancelarConservaCoordenadasInicialesYReferenciaEditadaDuranteMapa() {
        val model = readyModelWithCoordinates()
        val original = model.state.value.location
        val request = model.beginLocationSelection()!!
        model.setLocationReference("Referencia modificada mientras se abre el mapa")
        assertTrue(model.cancelLocationSelection(request.token))
        assertEquals(original.copy(reference = "Referencia modificada mientras se abre el mapa"), model.state.value.location)
    }

    @Test
    fun callbackTardioDespuesDeCancelarNoModificaBorrador() {
        val model = readyModelWithCoordinates()
        val request = model.beginLocationSelection()!!
        model.cancelLocationSelection(request.token)
        val expected = model.state.value
        assertFalse(model.applyLocationSelection(request.token, confirmed(NEW_LOCATION)))
        assertFalse(model.cancelLocationSelection(request.token))
        assertEquals(expected, model.state.value)
    }

    @Test
    fun seleccionNuevaInvalidaConfirmacionYCancelacionDeLaAnterior() {
        val model = readyModelWithCoordinates()
        val first = model.beginLocationSelection()!!
        val second = model.beginLocationSelection()!!
        assertNotSame(first.token, second.token)
        assertFalse(model.applyLocationSelection(first.token, confirmed(NEW_LOCATION)))
        assertFalse(model.cancelLocationSelection(first.token))
        assertTrue(model.state.value.locationSelectionPending)
        assertTrue(model.applyLocationSelection(second.token, confirmed(NEW_LOCATION)))
    }

    @Test
    fun resultadoConfirmadoSoloPuedeAplicarseUnaVez() {
        val model = readyModel()
        val request = model.beginLocationSelection()!!
        assertTrue(model.applyLocationSelection(request.token, confirmed(NEW_LOCATION)))
        assertFalse(model.applyLocationSelection(request.token, confirmed(OLD_LOCATION)))
        assertEquals(NEW_LOCATION.latitude, model.state.value.location.latitude)
    }

    @Test
    fun tokenDeOtroModeloNoAutorizaAplicarNiCancelarLaSeleccion() {
        val first = readyModel()
        val second = readyModel()
        val firstRequest = first.beginLocationSelection()!!
        val secondRequest = second.beginLocationSelection()!!
        assertFalse(second.applyLocationSelection(firstRequest.token, confirmed(NEW_LOCATION)))
        assertFalse(second.cancelLocationSelection(firstRequest.token))
        assertTrue(second.applyLocationSelection(secondRequest.token, confirmed(NEW_LOCATION)))
    }

    @Test
    fun resetInvalidaTokenYNuncaResucitaElBorradorAnterior() {
        val model = readyModelWithCoordinates()
        val request = model.beginLocationSelection()!!
        assertTrue(model.reset())
        assertEquals(ReportDraftState(), model.state.value)
        assertFalse(model.applyLocationSelection(request.token, confirmed(NEW_LOCATION)))
        assertFalse(model.cancelLocationSelection(request.token))
    }

    @Test
    fun coordenadasIncompletasNoFinitasOFueraDeRangoNoCierranSeleccion() {
        val cases = listOf(
            ReportLocation(latitude = 20.0), ReportLocation(longitude = -99.0), ReportLocation(),
            ReportLocation(latitude = Double.NaN, longitude = 1.0),
            ReportLocation(latitude = 20.0, longitude = Double.POSITIVE_INFINITY),
            ReportLocation(latitude = -90.1, longitude = 1.0),
            ReportLocation(latitude = 20.0, longitude = 180.1),
        )
        cases.forEach { location ->
            val model = readyModelWithCoordinates()
            val request = model.beginLocationSelection()!!
            val original = model.state.value.location
            assertFalse("No debe aplicar $location", model.applyLocationSelection(request.token, confirmed(location)))
            assertEquals(original, model.state.value.location)
            assertTrue(model.state.value.locationSelectionPending)
            assertTrue(model.applyLocationSelection(request.token, confirmed(NEW_LOCATION)))
        }
    }

    @Test
    fun distanciaCorruptaONegativaOMayorAlRadioNoSeAplica() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 5_000.01).forEach { distance ->
            val model = readyModel()
            val request = model.beginLocationSelection()!!
            assertFalse(model.applyLocationSelection(request.token, NearbyLocationConfirmation.Confirmed(NEW_LOCATION, distance)))
            assertTrue(model.state.value.locationSelectionPending)
        }
    }

    @Test
    fun revisarMientrasMapaEstaPendienteNoEntregaUnBorradorListo() {
        val model = readyModel()
        val request = model.beginLocationSelection()!!
        assertTrue(invalid(model.review(NOW)).errors.containsKey(ReportDraftField.LOCATION_COORDINATES))
        model.cancelLocationSelection(request.token)
        assertTrue(model.review(NOW) is ReportDraftReview.Valid)
    }

    @Test
    fun snapshotRecuperaTodosLosCamposPeroNoTokenNiSeleccionPendiente() {
        val handle = SavedStateHandle()
        val original = readyModelWithCoordinates(handle)
        val request = original.beginLocationSelection()!!
        original.setOccurredAt(NOW.minusSeconds(40).plusNanos(987_654_321))
        val restored = ReportDraftViewModel(copyHandle(handle))
        assertEquals(original.state.value.copy(locationSelectionPending = false), restored.state.value)
        assertFalse(restored.applyLocationSelection(request.token, confirmed(NEW_LOCATION)))
        assertFalse(restored.cancelLocationSelection(request.token))
        assertTrue(restored.review(NOW) is ReportDraftReview.Valid)
    }

    @Test
    fun snapshotSoloContienePrimitivosYNoEvidenciaPermisosObjetosDeModeloOSesiones() {
        val handle = SavedStateHandle()
        val model = readyModelWithCoordinates(handle)
        model.beginLocationSelection()
        val values = handle.keys().associateWith { handle.get<Any?>(it) }
        assertTrue(values.values.all { it == null || it is String || it is Int || it is Long || it is Double })
        assertTrue(values.keys.none { "gps" in it.lowercase() || "permission" in it.lowercase() || "token" in it.lowercase() })
        assertTrue(values.values.none { it is NearbyLocationConfirmation.Confirmed || it is ReportLocationSelectionToken })
    }

    @Test
    fun snapshotConFechaCorruptaOFueraDeInstantFallaCerradoYPermiteCorregir() {
        listOf(
            "texto" to 0, Long.MAX_VALUE to 0, NOW.epochSecond to -1,
            NOW.epochSecond to 1_000_000_000, NOW.epochSecond to "nanosegundos",
            NOW.epochSecond to null, null to 3,
        ).forEach { (seconds, nanos) ->
            val source = SavedStateHandle()
            readyModel(source)
            source["reportDraft.occurredAtSeconds"] = seconds
            source["reportDraft.occurredAtNanos"] = nanos
            val restored = ReportDraftViewModel(copyHandle(source))
            assertNull(restored.state.value.occurredAt)
            assertTrue(invalid(restored.review(NOW)).errors.containsKey(ReportDraftField.OCCURRED_AT))
            restored.setOccurredAt(NOW.minusSeconds(10))
            assertTrue(restored.review(NOW) is ReportDraftReview.Valid)
        }
    }

    @Test
    fun snapshotConEnumsDesconocidosNoLosSustituyePorValoresAceptados() {
        val handle = SavedStateHandle()
        readyModel(handle)
        handle["reportDraft.type"] = "INEXISTENTE"
        handle["reportDraft.priority"] = 123
        val restored = ReportDraftViewModel(copyHandle(handle))
        assertNull(restored.state.value.type)
        assertNull(restored.state.value.priority)
        assertTrue(restored.review(NOW) is ReportDraftReview.Invalid)
        restored.setType(IncidentType.FIRE)
        restored.setPriority(Priority.HIGH)
        assertTrue(restored.review(NOW) is ReportDraftReview.Valid)
    }

    @Test
    fun snapshotConCoordenadasCorruptasNoSeVuelveValidoPorTenerReferencia() {
        listOf("19.4" to "-99.1", Double.NaN to 20.0, 1.0 to null, null to 30.0, 91.0 to 180.0).forEach { (lat, lon) ->
            val handle = SavedStateHandle()
            readyModel(handle)
            handle["reportDraft.latitude"] = lat
            handle["reportDraft.longitude"] = lon
            val restored = ReportDraftViewModel(copyHandle(handle))
            assertTrue(invalid(restored.review(NOW)).errors.containsKey(ReportDraftField.LOCATION_COORDINATES))
            restored.setDescription("Otra descripción válida que no elimina el error del mapa.")
            assertTrue(restored.review(NOW) is ReportDraftReview.Invalid)
            val request = restored.beginLocationSelection()!!
            restored.applyLocationSelection(request.token, confirmed(NEW_LOCATION))
            assertTrue(restored.review(NOW) is ReportDraftReview.Valid)
        }
    }

    @Test
    fun snapshotDeVersionDesconocidaExigeIniciarOtroBorrador() {
        val handle = SavedStateHandle()
        readyModel(handle)
        handle["reportDraft.version"] = 99
        val restored = ReportDraftViewModel(copyHandle(handle))
        assertTrue(restored.state.value.inputErrors.containsKey(ReportDraftField.SNAPSHOT))
        restored.setType(IncidentType.OTHER)
        assertTrue(invalid(restored.review(NOW)).errors.containsKey(ReportDraftField.SNAPSHOT))
        restored.reset()
        assertEquals(ReportDraftState(), restored.state.value)
    }

    @Test
    fun snapshotSinVersionPeroConCamposDelBorradorNoSeAceptaComoUnoNuevo() {
        val handle = SavedStateHandle(mapOf("reportDraft.description" to "Descripción incompleta"))
        val model = ReportDraftViewModel(handle)
        assertTrue(invalid(model.review(NOW)).errors.containsKey(ReportDraftField.SNAPSHOT))
        model.reset()
        assertEquals(ReportDraftState(), model.state.value)
    }

    @Test
    fun snapshotConTextosGrandesOMalTipadosNoLosPersisteNiLosTrunca() {
        val handle = SavedStateHandle()
        readyModel(handle)
        handle["reportDraft.description"] = "x".repeat(100_000)
        handle["reportDraft.reference"] = 23
        val restoredHandle = copyHandle(handle)
        val restored = ReportDraftViewModel(restoredHandle)
        assertEquals("", restored.state.value.description)
        assertEquals("", restored.state.value.location.reference)
        assertTrue(restored.review(NOW) is ReportDraftReview.Invalid)
        assertTrue(restoredHandle.keys().mapNotNull { restoredHandle.get<Any?>(it) as? String }.all { it.length <= 2_000 })
    }

    @Test
    fun descripcionDemasiadoLargaDevuelveErrorYConservaTodoElTextoAnterior() {
        val handle = SavedStateHandle()
        val model = readyModel(handle)
        val before = model.state.value.description
        val result = model.setDescription("x".repeat(1_001))
        assertTrue(result is ReportDraftEditResult.Rejected)
        assertEquals(before, model.state.value.description)
        assertTrue(invalid(model.review(NOW)).errors.containsKey(ReportDraftField.DESCRIPTION))
        val restored = ReportDraftViewModel(copyHandle(handle))
        assertEquals(before, restored.state.value.description)
        assertTrue(restored.review(NOW) is ReportDraftReview.Invalid)
        restored.setDescription("Descripción corregida sin truncar ningún carácter.")
        assertTrue(restored.review(NOW) is ReportDraftReview.Valid)
    }

    @Test
    fun referenciaDemasiadoLargaConservaValorYCoordenadasHastaCorregir() {
        val model = readyModelWithCoordinates()
        val before = model.state.value.location
        assertTrue(model.setLocationReference("a".repeat(201)) is ReportDraftEditResult.Rejected)
        assertEquals(before, model.state.value.location)
        assertTrue(invalid(model.review(NOW)).errors.containsKey(ReportDraftField.LOCATION_REFERENCE))
        model.setLocationReference("Referencia corregida")
        assertTrue(model.review(NOW) is ReportDraftReview.Valid)
    }

    @Test
    fun limitesCuentanPuntosUnicodeYRechazanEspaciosExterioresExcesivosDeFormaExplicita() {
        val model = readyModel()
        assertEquals(ReportDraftEditResult.Accepted, model.setDescription("🧯".repeat(1_000)))
        assertTrue(model.review(NOW) is ReportDraftReview.Valid)
        assertTrue(model.setDescription("🧯".repeat(1_001)) is ReportDraftEditResult.Rejected)
        assertEquals(ReportDraftEditResult.Accepted, model.setLocationReference("🌳".repeat(200)))
        assertTrue(model.setLocationReference("🌳".repeat(201)) is ReportDraftEditResult.Rejected)
        assertTrue(model.setDescription(" ".repeat(1_001)) is ReportDraftEditResult.Rejected)
    }

    @Test
    fun revisionReutilizaReglasDelDominioParaUnicodeLongitudYFechas() {
        val model = readyModel()
        model.setDescription("Texto válido\u0000con carácter prohibido")
        model.setLocationReference("Referencia\uD800 no válida")
        model.setOccurredAt(NOW.plusSeconds(61))
        val errors = invalid(model.review(NOW)).errors
        assertTrue(errors.keys.containsAll(listOf(ReportDraftField.DESCRIPTION, ReportDraftField.LOCATION_REFERENCE, ReportDraftField.OCCURRED_AT)))
        model.setDescription("Corta")
        model.setLocationReference("corta")
        model.setOccurredAt(Instant.EPOCH.minusNanos(1))
        assertTrue(invalid(model.review(NOW)).errors.containsKey(ReportDraftField.OCCURRED_AT))
    }

    @Test
    fun quitarFechaTipoOPrioridadDespuesDeCompletarVuelveLaRevisionInvalida() {
        val model = readyModel()
        model.setOccurredAt(null)
        model.setType(null)
        model.setPriority(null)
        val errors = invalid(model.review(NOW)).errors
        assertTrue(errors.keys.containsAll(listOf(ReportDraftField.TYPE, ReportDraftField.PRIORITY, ReportDraftField.OCCURRED_AT)))
    }

    @Test
    fun referenciaSinCoordenadasPuedeValidarCamposSinAcreditarCercaniaNiGuardado() {
        val model = readyModel()
        val reviewed = valid(model.review(NOW)).draft
        assertNull(reviewed.location.latitude)
        assertNull(reviewed.location.longitude)
        assertEquals("Frente al mercado municipal", reviewed.location.reference)
        assertEquals(model.state.value.description, reviewed.description)
    }

    @Test
    fun mismoStoreYClaveConservanModeloBorradorYSesionAlCambiarDueno() {
        val store = ViewModelStore()
        try {
            val factory = viewModelFactory { initializer { ReportDraftViewModel(SavedStateHandle()) } }
            val original = ViewModelProvider(store, factory)[ReportDraftViewModel::class.java]
            original.setDescription("Una descripción editable antes de volver del mapa.")
            val request = original.beginLocationSelection()!!
            val replacement = ViewModelProvider(store, factory)[ReportDraftViewModel::class.java]
            assertSame(original, replacement)
            assertTrue(replacement.applyLocationSelection(request.token, confirmed(NEW_LOCATION)))
            assertEquals("Una descripción editable antes de volver del mapa.", replacement.state.value.description)
        } finally {
            store.clear()
        }
    }

    @Test
    fun liberarStoreCierraEdicionRevisionYTodoCallbackPendiente() {
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { readyModel() } }
        val model = ViewModelProvider(store, factory)[ReportDraftViewModel::class.java]
        val request = model.beginLocationSelection()!!
        val oldDescription = model.state.value.description
        store.clear()
        assertNull(model.beginLocationSelection())
        assertFalse(model.applyLocationSelection(request.token, confirmed(NEW_LOCATION)))
        assertFalse(model.cancelLocationSelection(request.token))
        assertFalse(model.reset())
        assertFalse(model.setType(IncidentType.ACCIDENT))
        assertFalse(model.setPriority(Priority.MEDIUM))
        assertFalse(model.setOccurredAt(NOW))
        assertEquals(ReportDraftEditResult.Closed, model.setDescription("Descripción nueva"))
        assertEquals(ReportDraftEditResult.Closed, model.setLocationReference("Nueva referencia"))
        assertEquals(oldDescription, model.state.value.description)
        assertTrue(invalid(model.review(NOW)).errors.containsKey(ReportDraftField.SNAPSHOT))
    }

    private fun readyModel(handle: SavedStateHandle = SavedStateHandle()): ReportDraftViewModel =
        ReportDraftViewModel(handle).also {
            it.setType(IncidentType.FIRE)
            it.setPriority(Priority.HIGH)
            it.setDescription("Hay humo visible saliendo del local del mercado.")
            it.setOccurredAt(NOW.minusSeconds(60))
            it.setLocationReference("Frente al mercado municipal")
        }

    private fun readyModelWithCoordinates(handle: SavedStateHandle = SavedStateHandle()): ReportDraftViewModel =
        readyModel(handle).also {
            val request = it.beginLocationSelection()!!
            assertTrue(it.applyLocationSelection(request.token, confirmed(OLD_LOCATION)))
        }

    private fun copyHandle(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
    private fun confirmed(location: ReportLocation) = NearbyLocationConfirmation.Confirmed(location, 30.0)
    private fun valid(review: ReportDraftReview) = review as ReportDraftReview.Valid
    private fun invalid(review: ReportDraftReview) = review as ReportDraftReview.Invalid

    private companion object {
        val NOW = Instant.parse("2026-10-06T17:00:00Z")
        val OLD_LOCATION = ReportLocation("Referencia anterior del mapa", 19.4326, -99.1332)
        val NEW_LOCATION = ReportLocation("Referencia del mapa que no sustituye el texto", 19.4350, -99.1320)
    }
}
