package com.example.citizensecurity.report

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportReviewSnapshotTest {
    @Test
    fun fechaUsaZonaExplicitaInclusoAlCambiarDeDia() {
        val mexico = ReportReviewSnapshot.from(DRAFT, Locale.forLanguageTag("es-MX"), ZoneId.of("America/Mexico_City"))
        val utc = ReportReviewSnapshot.from(DRAFT, Locale.forLanguageTag("es-MX"), ZoneId.of("UTC"))
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.forLanguageTag("es-MX"))
        assertEquals(LocalDateTime.of(2026, 10, 8, 19, 30), LocalDateTime.parse(mexico.occurredAtText, formatter))
        assertEquals(LocalDateTime.of(2026, 10, 9, 1, 30), LocalDateTime.parse(utc.occurredAtText, formatter))
        assertNotEquals(utc.occurredAtText, mexico.occurredAtText)
        assertEquals("America/Mexico_City", mexico.timeZoneText)
        assertEquals("UTC", utc.timeZoneText)
    }

    @Test
    fun coordenadasSeLocalizanPorSeparadoConservandoSignosYSinCambiarElPunto() {
        val point = DRAFT.copy(location = ReportLocation("Referencia", -19.4326, -99.1332))
        val spanish = ReportReviewSnapshot.from(point, Locale.forLanguageTag("es-ES"), ZoneId.of("UTC"))
        val english = ReportReviewSnapshot.from(point, Locale.US, ZoneId.of("UTC"))
        assertEquals("-19,432600", spanish.latitudeText)
        assertEquals("-99,133200", spanish.longitudeText)
        assertEquals("-19.432600", english.latitudeText)
        assertEquals("-99.133200", english.longitudeText)
        assertEquals(ReportLocation("Referencia", -19.4326, -99.1332), point.location)
    }

    @Test
    fun coordenadasAusentesNoInventanUnPuntoNiUnCero() {
        val review = ReportReviewSnapshot.from(DRAFT, Locale.US, ZoneId.of("UTC"))
        assertNull(review.latitudeText)
        assertNull(review.longitudeText)
    }

    @Test
    fun parejaIncompletaNoSePresentaComoUnPuntoCompleto() {
        listOf(ReportLocation("Referencia", 19.0, null), ReportLocation("Referencia", null, -99.0)).forEach { location ->
            val review = ReportReviewSnapshot.from(DRAFT.copy(location = location), Locale.US, ZoneId.of("UTC"))
            assertNull(review.latitudeText)
            assertNull(review.longitudeText)
        }
    }

    @Test
    fun puntoCeroEsValidoParaElResumenYSuAusenciaSeDistingue() {
        val review = ReportReviewSnapshot.from(DRAFT.copy(location = ReportLocation("", 0.0, 0.0)), Locale.US, ZoneId.of("UTC"))
        assertEquals("0.000000", review.latitudeText)
        assertEquals("0.000000", review.longitudeText)
    }

    @Test
    fun revisionConservaTextoCompletoUnicodeSaltosYEspacios() {
        val description = "  Hay humo frente al café 🧯\n" + "Detalle visible. ".repeat(40)
        val reference = "  Frente al árbol 🌳\nColonia Centro  "
        val draft = DRAFT.copy(description = description, location = ReportLocation(reference))
        val review = ReportReviewSnapshot.from(draft, Locale.US, ZoneId.of("UTC"))
        assertEquals(description, review.description)
        assertEquals(reference, review.locationReference)
        assertEquals(IncidentType.FIRE, review.type)
        assertEquals(Priority.HIGH, review.priority)
    }

    companion object {
        private val DRAFT = NewReport(
            IncidentType.FIRE, Priority.HIGH, "Hay humo frente al mercado.",
            Instant.parse("2026-10-09T01:30:00Z"), ReportLocation("Frente al mercado del centro"),
        )
    }
}
