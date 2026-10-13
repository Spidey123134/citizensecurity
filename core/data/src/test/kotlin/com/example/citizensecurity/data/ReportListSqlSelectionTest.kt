package com.example.citizensecurity.data

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportListCursor
import com.example.citizensecurity.domain.ReportListPageQuery
import com.example.citizensecurity.domain.ReportStatus
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Executes the production WHERE and order on SQLite, without Android or new dependencies. */
class ReportListSqlSelectionTest {
    @Test
    fun consultaAbiertaIncluyeFilasExistentesSinCoordenadasYConservaLaSeleccion() {
        val query = ReportListPageQuery()
        val selection = listPageSelection(query)
        assertEquals("1", selection.selection)
        assertTrue(selection.arguments.isEmpty())
        assertEquals(listOf("b", "a"), execute(query, listOf(Row("a"), Row("b"))))
        try {
            (selection.arguments as MutableList<String>).add("otro")
            fail("Los argumentos de una lectura pendiente deben ser inmutables.")
        } catch (_: UnsupportedOperationException) { /* Copia inmutable. */ }
    }

    @Test
    fun paginasSinOffsetRecorrenEmpatesDeSegundosNanosYFolioSinRepetirNiOmitir() {
        val rows = listOf(
            Row("a", createdNanos = 5), Row("b", createdNanos = 5),
            Row("c", createdNanos = 5), Row("d", createdNanos = 6),
            Row("e", createdSeconds = 2_001), Row("z", createdSeconds = 1_999, createdNanos = 999_999_999),
        )
        val expected = listOf("e", "d", "c", "b", "a", "z")
        val actual = mutableListOf<String>()
        var before: ReportListCursor? = null
        do {
            val query = ReportListPageQuery(before = before, limit = 2)
            val fetched = execute(query, rows)
            val visible = fetched.take(query.limit)
            actual += visible
            val last = rows.single { it.id == visible.last() }
            before = last.cursor()
        } while (fetched.size > query.limit)
        assertEquals(expected, actual)
        assertEquals(actual.size, actual.toSet().size)
        assertTrue(execute(ReportListPageQuery(before = before), rows).isEmpty())
    }

    @Test
    fun cursorEsExclusivoYComparaLosTresCamposEnOrden() {
        val query = ReportListPageQuery(before = ReportListCursor(instant(2_000, 500), "m"))
        val rows = listOf(
            Row("later-second", createdSeconds = 2_001), Row("later-nano", createdNanos = 501),
            Row("z", createdNanos = 500), Row("m", createdNanos = 500), Row("a", createdNanos = 500),
            Row("earlier-nano", createdNanos = 499), Row("earlier-second", createdSeconds = 1_999, createdNanos = 999_999_999),
        )
        assertEquals(listOf("a", "earlier-nano", "earlier-second"), execute(query, rows))
        val selection = listPageSelection(query)
        assertFalse(selection.selection.contains("OFFSET", ignoreCase = true))
        assertEquals(listOf("2000", "2000", "500", "2000", "500", "m"), selection.arguments)
    }

    @Test
    fun folioDelCursorEsUnArgumentoLiteralInclusoConComillas() {
        val id = "m' OR 1=1 --"
        val query = ReportListPageQuery(before = ReportListCursor(instant(2_000), id))
        assertEquals(listOf("m", "a"), execute(query, listOf(Row("z"), Row(id), Row("m"), Row("a"))))
        assertFalse(listPageSelection(query).selection.contains(id))
        assertEquals(id, listPageSelection(query).arguments.last())
    }

    @Test
    fun fechaComparaInstanteDelIncidenteConNanosegundosYNoElGuardado() {
        val query = ReportListPageQuery(startInclusive = instant(1_000, 500), endExclusive = instant(1_001, 200))
        val rows = listOf(
            Row("before-second", 999, 999_999_999), Row("before-nano", 1_000, 499),
            Row("a-start", 1_000, 500, createdSeconds = 9_000),
            Row("b-inside", 1_000, 999_999_999, createdSeconds = 8_000),
            Row("c-last", 1_001, 199, createdSeconds = 7_000), Row("end", 1_001, 200), Row("after", 1_002),
        )
        assertEquals(listOf("a-start", "b-inside", "c-last"), execute(query, rows))
    }

    @Test
    fun cadaExtremoOpcionalSeAplicaPorSeparado() {
        val rows = listOf(Row("a-before", 1_000, 499), Row("b-at", 1_000, 500), Row("c-after", 1_001))
        assertEquals(listOf("c-after", "b-at"), execute(ReportListPageQuery(startInclusive = instant(1_000, 500)), rows))
        assertEquals(listOf("a-before"), execute(ReportListPageQuery(endExclusive = instant(1_000, 500)), rows))
    }

    @Test
    fun todosLosFiltrosYCursorSeAplicanAntesDelLimiteYExcesoCuentaSoloCoincidencias() {
        val query = ReportListPageQuery(
            type = IncidentType.RISK, status = ReportStatus.IN_REVIEW, priority = Priority.HIGH,
            startInclusive = instant(1_000, 100), endExclusive = instant(1_000, 200),
            before = ReportListCursor(instant(3_000), "cursor"), limit = 2,
        )
        val excluded = listOf(
            Row("wrong-type", 1_000, 150, type = "FIRE", status = "IN_REVIEW", priority = "HIGH", createdSeconds = 2_999),
            Row("wrong-status", 1_000, 150, status = "CLOSED", priority = "HIGH", createdSeconds = 2_998),
            Row("wrong-priority", 1_000, 150, status = "IN_REVIEW", priority = "LOW", createdSeconds = 2_997),
            Row("before-date", 1_000, 99, status = "IN_REVIEW", priority = "HIGH", createdSeconds = 2_996),
            Row("end-date", 1_000, 200, status = "IN_REVIEW", priority = "HIGH", createdSeconds = 2_995),
            Row("before-cursor", 1_000, 150, status = "IN_REVIEW", priority = "HIGH", createdSeconds = 3_001),
        )
        val matching = (1..3).map {
            Row("match-$it", 1_000, 150, status = "IN_REVIEW", priority = "HIGH", createdSeconds = 2_000 + it.toLong())
        }
        val fetched = execute(query, excluded + matching)
        assertEquals(listOf("match-3", "match-2", "match-1"), fetched)
        assertEquals(listOf("match-3", "match-2"), fetched.take(query.limit))
        assertTrue(fetched.size > query.limit)
        val exact = execute(query, excluded + matching.take(2))
        assertEquals(listOf("match-2", "match-1"), exact)
        assertFalse(exact.size > query.limit)
        assertTrue(execute(query, excluded).isEmpty())
    }

    @Test
    fun intervaloVacioNoDevuelveFilasNiExceso() {
        val boundary = instant(1_000, 500)
        val query = ReportListPageQuery(startInclusive = boundary, endExclusive = boundary, limit = 1)
        val fetched = execute(query, listOf(Row("before", 1_000, 499), Row("at", 1_000, 500), Row("after", 1_000, 501)))
        assertTrue(fetched.isEmpty())
        assertFalse(fetched.size > query.limit)
    }

    @Test
    fun limitesMinimoYMaximoLeenSoloUnaFilaAdicional() {
        val rows = (1..200).map { Row("row-${it.toString().padStart(3, '0')}") }
        val minimum = execute(ReportListPageQuery(limit = 1), rows)
        assertEquals(listOf("row-200", "row-199"), minimum)
        val maximum = execute(ReportListPageQuery(limit = 100), rows)
        assertEquals(101, maximum.size)
        assertEquals("row-200", maximum.first())
        assertEquals("row-100", maximum.last())
    }

    private fun execute(query: ReportListPageQuery, rows: List<Row>): List<String> {
        val python = System.getenv("REPORT_SQLITE_TEST_PYTHON").orEmpty()
        assumeTrue("Set REPORT_SQLITE_TEST_PYTHON to run the host SQLite integration tests.", python.isNotBlank())
        val selection = listPageSelection(query)
        val sql = "SELECT id FROM reports WHERE ${selection.selection} " +
            "ORDER BY $REPORT_LIST_PAGE_ORDER LIMIT ${query.limit + 1}"
        assertFalse(sql.contains("OFFSET", ignoreCase = true))
        val directory = Files.createTempDirectory("report-list-sqlite-")
        try {
            val script = directory.resolve("query.py")
            requireNotNull(javaClass.getResourceAsStream("/sqlite_report_list_selection.py")).use {
                Files.copy(it, script)
            }
            val input = directory.resolve("input.tsv")
            Files.writeString(input, buildString {
                appendLine(sql)
                appendLine(selection.arguments.joinToString("\t"))
                rows.forEach { appendLine(it.tsv()) }
            })
            val output = directory.resolve("output.txt")
            val process = ProcessBuilder(python, script.toString(), input.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start()
            try {
                assertTrue("SQLite test process timed out.", process.waitFor(10, TimeUnit.SECONDS))
                val result = Files.readString(output)
                assertEquals(result, 0, process.exitValue())
                return result.lineSequence().filter { it.isNotEmpty() }.toList()
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                    process.waitFor(5, TimeUnit.SECONDS)
                }
            }
        } finally {
            Files.walk(directory).use { files ->
                files.sorted(Comparator.reverseOrder<Path>()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private fun instant(seconds: Long, nanos: Long = 0): Instant = Instant.ofEpochSecond(seconds, nanos)

    private data class Row(
        val id: String,
        val occurredSeconds: Long = 1_000,
        val occurredNanos: Int = 0,
        val type: String = "RISK",
        val status: String = "REPORTED",
        val priority: String = "MEDIUM",
        val createdSeconds: Long = 2_000,
        val createdNanos: Int = 0,
    ) {
        fun cursor(): ReportListCursor = ReportListCursor(Instant.ofEpochSecond(createdSeconds, createdNanos.toLong()), id)

        fun tsv(): String = listOf(id, type, status, priority, occurredSeconds, occurredNanos,
            createdSeconds, createdNanos).joinToString("\t")
    }
}
