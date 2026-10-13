package com.example.citizensecurity.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant

class ReportListPageTest {
    @Test
    fun consultaPorDefectoAcotaLaPaginaSinImponerFiltros() {
        val query = ReportListPageQuery()
        assertEquals(100, ReportListPageQuery.DEFAULT_LIMIT)
        assertEquals(100, ReportListPageQuery.MAX_LIMIT)
        assertEquals(100, query.limit)
        assertEquals(null, query.type)
        assertEquals(null, query.status)
        assertEquals(null, query.priority)
        assertEquals(null, query.startInclusive)
        assertEquals(null, query.endExclusive)
        assertEquals(null, query.before)
        assertEquals(1, ReportListPageQuery(limit = 1).limit)
        assertEquals(100, ReportListPageQuery(limit = 100).limit)
        for (invalid in listOf(Int.MIN_VALUE, -1, 0, 101, Int.MAX_VALUE)) {
            rejects { ReportListPageQuery(limit = invalid) }
        }
    }

    @Test
    fun cursorRequiereFolioNoVacioConLongitudAcotada() {
        val time = Instant.ofEpochSecond(1_000, 123)
        for (invalid in listOf("", " ", "\t\n", "a".repeat(65))) {
            rejects { ReportListCursor(time, invalid) }
        }
        assertEquals("a", ReportListCursor(time, "a").id)
        assertEquals("a".repeat(64), ReportListCursor(time, "a".repeat(64)).id)
    }

    @Test
    fun cursorDelReporteUsaFechaDeCreacionYConservaNanosegundos() {
        val created = Instant.ofEpochSecond(1_000, 123_456_789)
        val report = report("folio", created)
        assertFalse(report.occurredAt == report.createdAt)
        assertEquals(ReportListCursor(created, report.id), ReportListCursor.from(report))
        assertEquals(created.nano, ReportListCursor.from(report).createdAt.nano)
    }

    @Test
    fun identidadDelCursorDistingueFolioYUnNanosegundo() {
        val time = Instant.ofEpochSecond(1_000, 123)
        val first = ReportListCursor(time, "folio")
        val same = ReportListCursor(time, "folio")
        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertFalse(first == first.copy(id = "otro"))
        assertFalse(first == first.copy(createdAt = time.plusNanos(1)))
    }

    @Test
    fun cadaExtremoDelIntervaloEsOpcional() {
        val time = Instant.ofEpochSecond(1_000, 123)
        val from = ReportListPageQuery(startInclusive = time)
        val until = ReportListPageQuery(endExclusive = time)
        assertEquals(time, from.startInclusive)
        assertEquals(null, from.endExclusive)
        assertEquals(null, until.startInclusive)
        assertEquals(time, until.endExclusive)
    }

    @Test
    fun intervaloInvertidoSeRechazaInclusoPorUnNanosegundo() {
        val time = Instant.ofEpochSecond(1_000, 123)
        rejects { ReportListPageQuery(startInclusive = time.plusNanos(1), endExclusive = time) }
        rejects { ReportListPageQuery(startInclusive = time.plusSeconds(1), endExclusive = time) }
    }

    @Test
    fun intervaloIgualEsValidoYExtremosConservanSuPrecision() {
        val start = Instant.ofEpochSecond(1_000, 999_999_998)
        val end = start.plusNanos(1)
        val empty = ReportListPageQuery(startInclusive = start, endExclusive = start)
        val oneNanosecond = ReportListPageQuery(startInclusive = start, endExclusive = end)
        assertEquals(empty.startInclusive, empty.endExclusive)
        assertEquals(start, oneNanosecond.startInclusive)
        assertEquals(end, oneNanosecond.endExclusive)
    }

    @Test
    fun identidadDeConsultaIncluyeTodosLosFiltrosElCursorYElLimite() {
        val start = Instant.ofEpochSecond(1_000, 123)
        val end = start.plusSeconds(1)
        val before = ReportListCursor(end.plusSeconds(1), "folio")
        val first = ReportListPageQuery(
            type = IncidentType.FIRE,
            status = ReportStatus.REPORTED,
            priority = Priority.HIGH,
            startInclusive = start,
            endExclusive = end,
            before = before,
            limit = 50,
        )
        val same = ReportListPageQuery(
            type = IncidentType.FIRE,
            status = ReportStatus.REPORTED,
            priority = Priority.HIGH,
            startInclusive = start,
            endExclusive = end,
            before = before,
            limit = 50,
        )
        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        for (different in listOf(
            first.copy(type = IncidentType.THEFT),
            first.copy(type = null),
            first.copy(status = ReportStatus.CLOSED),
            first.copy(status = null),
            first.copy(priority = Priority.LOW),
            first.copy(priority = null),
            first.copy(startInclusive = start.plusNanos(1)),
            first.copy(startInclusive = null),
            first.copy(endExclusive = end.plusNanos(1)),
            first.copy(endExclusive = null),
            first.copy(before = before.copy(createdAt = before.createdAt.plusNanos(1))),
            first.copy(before = before.copy(id = "otro")),
            first.copy(before = null),
            first.copy(limit = 51),
        )) {
            assertFalse(first == different)
        }
        assertTrue(first.toString().contains("startInclusive=$start"))
        assertTrue(first.toString().contains("endExclusive=$end"))
        assertTrue(first.toString().contains("before=$before"))
    }

    @Test
    fun paginaConservaElOrdenYUnaCopiaInmutableDeLosReportes() {
        val first = report("folio-a", Instant.ofEpochSecond(1_000, 123))
        val second = report("folio-b", first.createdAt.minusNanos(1))
        val original = mutableListOf(first, second)
        val page = ReportListPage(original, true)
        val hash = page.hashCode()
        original.clear()
        original.add(second)
        assertEquals(listOf(first, second), page.reports)
        assertTrue(page.hasMore)
        assertEquals(hash, page.hashCode())
        try {
            (page.reports as MutableList<Report>).clear()
            fail("Una página observada no puede cambiar fuera del modelo.")
        } catch (_: UnsupportedOperationException) { /* Copia inmutable. */ }
    }

    @Test
    fun identidadDePaginaIncluyeContenidoOrdenYContinuacion() {
        val first = report("folio-a", Instant.ofEpochSecond(1_000, 123))
        val second = report("folio-b", first.createdAt.minusNanos(1))
        val page = ReportListPage(listOf(first, second), true)
        val same = ReportListPage(listOf(first, second), true)
        assertEquals(page, same)
        assertEquals(page.hashCode(), same.hashCode())
        assertFalse(page == ReportListPage(listOf(second, first), true))
        assertFalse(page == ReportListPage(listOf(first), true))
        assertFalse(page == ReportListPage(listOf(first, second), false))
        assertTrue(page.toString().contains("reports="))
        assertTrue(page.toString().contains("hasMore=true"))
        val empty = ReportListPage(emptyList(), false)
        assertTrue(empty.reports.isEmpty())
        assertFalse(empty.hasMore)
        assertEquals(ReportListPage(emptyList(), false), empty)
    }

    private fun report(id: String, createdAt: Instant) = Report(
        id = id,
        type = IncidentType.FIRE,
        priority = Priority.HIGH,
        description = "Reporte guardado",
        occurredAt = createdAt.minusSeconds(60),
        location = ReportLocation("Referencia"),
        createdAt = createdAt,
    )

    private fun rejects(action: () -> Unit) {
        try { action(); fail("La entrada inválida debe rechazarse.") }
        catch (_: IllegalArgumentException) { /* Error de entrada esperado. */ }
    }
}
