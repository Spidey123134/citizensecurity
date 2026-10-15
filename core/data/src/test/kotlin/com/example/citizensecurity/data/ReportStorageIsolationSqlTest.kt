package com.example.citizensecurity.data

import com.example.citizensecurity.domain.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Host SQLite uses the production schema/selection, not Android's SQLiteOpenHelper. */
class ReportStorageIsolationSqlTest {
    @Test fun folioCannotReadAnotherPartitionAndTheSameUuidCanExistIndependently() = verify("folio")
    @Test fun pagedListSeesOnlyItsPartitionWithProductionFilters() = verify("list")
    @Test fun mapSeesOnlyItsPartitionWithProductionFilters() = verify("map")
    @Test fun closingAndReopeningEveryPartitionKeepsHistoricalRowsSchemaAndFiles() = verify("reopen")

    private fun verify(mode: String) {
        val python = System.getenv("REPORT_SQLITE_TEST_PYTHON").orEmpty()
        assumeTrue("Set REPORT_SQLITE_TEST_PYTHON for host SQLite isolation verification.", python.isNotBlank())
        val directory = Files.createTempDirectory("report-partition-sqlite-")
        try {
            val script = directory.resolve("verify.py")
            requireNotNull(javaClass.getResourceAsStream("/sqlite_report_storage_isolation.py")).use {
                Files.copy(it, script)
            }
            val names = listOf(null, "00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002")
                .map { ReportStorageNames.fromAccountId(it).databaseName }
            val listQuery = ReportListPageQuery(priority = Priority.HIGH, limit = 1)
            val list = listPageSelection(listQuery)
            val mapQuery = ReportMapQuery(MapRegion(18.0, 20.0, -100.0, -98.0), limit = 1,
                priorities = setOf(Priority.HIGH))
            val map = mapSelection(mapQuery)
            val input = directory.resolve("input.tsv")
            Files.writeString(input, buildString {
                appendLine(names.joinToString("\t"))
                appendLine("SELECT id, description FROM reports WHERE ${list.selection} ORDER BY $REPORT_LIST_PAGE_ORDER LIMIT ${listQuery.limit + 1}")
                appendLine(list.arguments.joinToString("\t"))
                appendLine("SELECT id, description FROM reports WHERE ${map.selection} ORDER BY $REPORT_LIST_PAGE_ORDER LIMIT ${mapQuery.limit + 1}")
                appendLine(map.arguments.joinToString("\t"))
                appendLine(ReportActivitySql.CREATE_INDEX)
            })
            val process = ProcessBuilder(python, script.toString(), input.toString(), schemaSource().toString(), mode)
                .redirectErrorStream(true).start()
            assertTrue("SQLite isolation verification timed out.", process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.exitValue())
            assertTrue(output, "scope-isolation-ok" in output)
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
        }
    }

    private fun schemaSource(): Path {
        var root: Path? = Path.of("").toAbsolutePath()
        while (root != null) {
            val source = root.resolve("core/data/src/main/kotlin/com/example/citizensecurity/data/ReportDatabase.kt")
            if (Files.isRegularFile(source)) return source
            root = root.parent
        }
        error("The production ReportDatabase schema must be available to verify isolation.")
    }
}
