package com.example.citizensecurity.release

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportValidator
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Version02ToolsTest {
    private val now = Instant.parse("2026-10-03T12:00:00.123456789Z")

    @Test
    fun recibeLosDatosConFechaPrecisaYCoordenadasNumericas() {
        val result = Version02Tools.receive(input()) as ReportInputResult.Received
        assertEquals(IncidentType.RISK, result.draft.type)
        assertEquals(Priority.MEDIUM, result.draft.priority)
        assertEquals(now.minusSeconds(30), result.draft.occurredAt)
        assertEquals(19.4326077, result.draft.location.latitude!!, 0.0)
        assertEquals(-99.1332088, result.draft.location.longitude!!, 0.0)
        assertTrue(ReportValidator().validate(result.draft, now).isValid)
    }

    @Test
    fun coordenadasVaciasPermitenValidarLaReferenciaEscrita() {
        val result = Version02Tools.receive(input().copy(latitude = " ", longitude = "")) as ReportInputResult.Received
        assertEquals(null, result.draft.location.latitude)
        assertEquals(null, result.draft.location.longitude)
        assertTrue(ReportValidator().validate(result.draft, now).isValid)
    }

    @Test
    fun fechaYCoordenadasMalFormadasDevuelvenSusErroresSinCrearUnBorrador() {
        val result = Version02Tools.receive(input().copy(occurredAt = "ayer", latitude = "diecinueve")) as ReportInputResult.Invalid
        assertEquals(setOf(ReportField.OCCURRED_AT, ReportField.LOCATION_COORDINATES), result.errors.keys)
    }

    @Test
    fun coordenadasIncompletasLleganAlValidadorConElCampoCorrecto() {
        val result = Version02Tools.receive(input().copy(longitude = "")) as ReportInputResult.Received
        assertTrue(ReportField.LOCATION_COORDINATES in ReportValidator().validate(result.draft, now).errors)
    }

    @Test
    fun valoresNoFinitosYFueraDeRangoSeRechazanEnLaValidacion() {
        listOf("NaN", "Infinity", "91").forEach { value ->
            val result = Version02Tools.receive(input().copy(latitude = value)) as ReportInputResult.Received
            assertTrue(ReportField.LOCATION_COORDINATES in ReportValidator().validate(result.draft, now).errors)
        }
    }

    @Test
    fun descripcionInvalidaMantieneElErrorPorCampo() {
        val result = Version02Tools.receive(input().copy(description = "Corto")) as ReportInputResult.Received
        assertTrue(ReportField.DESCRIPTION in ReportValidator().validate(result.draft, now).errors)
    }

    @Test
    fun cuentaDeDemostracionPermiteAdminYRecortaSoloElUsuario() {
        assertTrue(DemoLogin.accepts("admin", "admin"))
        assertTrue(DemoLogin.accepts(" admin ", "admin"))
        assertFalse(DemoLogin.accepts("admin", " admin "))
    }

    @Test
    fun credencialesIncorrectasOVaciasNoAbrenElAccesoDeDemostracion() {
        listOf("admin" to "otra", "otro" to "admin", "" to "", "Admin" to "admin").forEach {
            assertFalse(DemoLogin.accepts(it.first, it.second))
        }
    }

    private fun input() = ReportInput(
        IncidentType.RISK,
        Priority.MEDIUM,
        "Hay una luminaria dañada frente al parque.",
        now.minusSeconds(30).toString(),
        "Frente al parque central",
        "19.4326077",
        "-99.1332088",
    )
}
