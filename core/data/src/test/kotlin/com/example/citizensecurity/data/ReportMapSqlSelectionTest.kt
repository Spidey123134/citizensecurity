package com.example.citizensecurity.data

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.MapRegion
import com.example.citizensecurity.domain.ReportMapQuery
import com.example.citizensecurity.domain.ReportStatus
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Executes the production WHERE with SQLite, without Android, an emulator or new dependencies. */
class ReportMapSqlSelectionTest {
    private val region = MapRegion(-1.0, 1.0, -1.0, 1.0)

    @Test
    fun extremosComparanSegundosYNanosSinRedondear() {
        val query = ReportMapQuery(region,
            startInclusive = instant(1_000, 500), endExclusive = instant(1_001, 200))
        val rows = listOf(
            Row("before-second", 999, 999_999_999), Row("before-nano", 1_000, 499),
            Row("a-start", 1_000, 500), Row("b-inside", 1_000, 999_999_999),
            Row("c-last", 1_001, 199), Row("end", 1_001, 200), Row("after", 1_002),
        )
        assertEquals(listOf("c-last", "b-inside", "a-start"), execute(query, rows))
    }

    @Test
    fun cadaExtremoOpcionalFiltraPorSeparado() {
        val rows = listOf(Row("a-before", 1_000, 499), Row("b-at", 1_000, 500), Row("c-after", 1_001))
        assertEquals(listOf("c-after", "b-at"), execute(
            ReportMapQuery(region, startInclusive = instant(1_000, 500)), rows))
        assertEquals(listOf("a-before"), execute(
            ReportMapQuery(region, endExclusive = instant(1_000, 500)), rows))
    }

    @Test
    fun intervaloVacioNoDevuelveFilasNiExceso() {
        val boundary = instant(1_000, 500)
        val query = ReportMapQuery(region, limit = 1, startInclusive = boundary, endExclusive = boundary)
        val fetched = execute(query, listOf(Row("before", 1_000, 499), Row("at", 1_000, 500), Row("after", 1_000, 501)))
        assertTrue(fetched.isEmpty())
        assertFalse(fetched.size > query.limit)
    }

    @Test
    fun fechaSeAplicaAntesDelLimiteYExcesoCuentaSoloCoincidencias() {
        val query = ReportMapQuery(region, limit = 2,
            startInclusive = instant(1_000), endExclusive = instant(1_001))
        val excluded = (1..5).map { Row("outside-$it", 900, createdSeconds = 3_000 + it.toLong()) }
        val matching = (1..3).map { Row("match-$it", 1_000, createdSeconds = 2_000 + it.toLong()) }
        val fetched = execute(query, excluded + matching)
        assertEquals(listOf("match-3", "match-2", "match-1"), fetched)
        assertEquals(listOf("match-3", "match-2"), fetched.take(query.limit))
        assertTrue(fetched.size > query.limit)
        val exact = execute(query, excluded + matching.take(2))
        assertEquals(listOf("match-2", "match-1"), exact)
        assertFalse(exact.size > query.limit)
    }

    @Test
    fun antimeridianoTipoEstadoYFechaSeCombinanIncluyendoAmbasRepresentaciones() {
        val query = ReportMapQuery(MapRegion(-1.0, 1.0, 170.0, -170.0),
            types = linkedSetOf(IncidentType.RISK, IncidentType.FIRE),
            statuses = linkedSetOf(ReportStatus.IN_REVIEW, ReportStatus.REPORTED),
            startInclusive = instant(1_000, 100), endExclusive = instant(1_000, 200))
        val rows = listOf(
            Row("a-east", 1_000, 100, longitude = 179.0),
            Row("b-west", 1_000, 150, longitude = -179.0, type = "FIRE", status = "IN_REVIEW"),
            Row("c-positive", 1_000, 150, longitude = 180.0),
            Row("d-negative", 1_000, 150, longitude = -180.0),
            Row("wrong-lng", 1_000, 150), Row("wrong-lat", 1_000, 150, latitude = 2.0, longitude = 179.0),
            Row("wrong-type", 1_000, 150, longitude = 179.0, type = "THEFT"),
            Row("wrong-status", 1_000, 150, longitude = 179.0, status = "CLOSED"),
            Row("wrong-date", 1_000, 200, longitude = 179.0),
            Row("no-point", 1_000, 150, latitude = null, longitude = null),
        )
        assertEquals(listOf("d-negative", "c-positive", "b-west", "a-east"), execute(query, rows))
        val edge = ReportMapQuery(MapRegion(-1.0, 1.0, 180.0, 180.0),
            startInclusive = query.startInclusive, endExclusive = query.endExclusive)
        assertEquals(listOf("d-negative", "c-positive"), execute(edge, rows))
    }

    @Test
    fun sinFechasConservaOrdenPorGuardadoNanosYFolio() {
        val query = ReportMapQuery(region)
        val rows = listOf(
            Row("a", 1_000, createdNanos = 5), Row("b", 2_000, createdNanos = 5),
            Row("c", 500, createdNanos = 6), Row("d", 1, createdSeconds = 2_001),
            Row("outside", 1_000, longitude = 2.0, createdSeconds = 3_000),
        )
        assertEquals(listOf("d", "c", "b", "a"), execute(query, rows))
    }

    private fun execute(query: ReportMapQuery, rows: List<Row>): List<String> {
        val python = System.getenv("REPORT_SQLITE_TEST_PYTHON").orEmpty()
        assumeTrue("Set REPORT_SQLITE_TEST_PYTHON to run the host SQLite integration tests.", python.isNotBlank())
        val selection = mapSelection(query)
        val sql = "SELECT id FROM reports WHERE ${selection.selection} " +
            "ORDER BY created_at_seconds DESC, created_at_nanos DESC, id DESC LIMIT ${query.limit + 1}"
        val directory = Files.createTempDirectory("report-map-sqlite-")
        try {
            val script = directory.resolve("query.py")
            requireNotNull(javaClass.getResourceAsStream("/sqlite_report_map_selection.py")).use {
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
        val occurredSeconds: Long,
        val occurredNanos: Int = 0,
        val latitude: Double? = 0.0,
        val longitude: Double? = 0.0,
        val type: String = "RISK",
        val status: String = "REPORTED",
        val createdSeconds: Long = 2_000,
        val createdNanos: Int = 0,
    ) {
        fun tsv(): String = listOf(id, latitude ?: "NULL", longitude ?: "NULL", type, status,
            occurredSeconds, occurredNanos, createdSeconds, createdNanos).joinToString("\t")
    }
}
