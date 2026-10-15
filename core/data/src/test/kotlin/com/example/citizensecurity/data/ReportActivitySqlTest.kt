package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportActivityCursor
import com.example.citizensecurity.domain.ReportActivityFilter
import com.example.citizensecurity.domain.ReportActivityQuery
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Executes the exact production statements with host SQLite; Android migration has native tests. */
class ReportActivitySqlTest {
    @Test fun recordedSecondsNanosecondsAndOperationTiesOrderGlobally() = verify("order")
    @Test fun exclusiveCursorKeepsOlderNanosecondsAndLowerUuidTies() = verify("cursor")
    @Test fun statusFilterRunsBeforeLimitEvenWithManyNewerCreations() = verify("filter")
    @Test fun legacyRowsHaveNoInventedActivity() = verify("legacy")
    @Test fun repeatedReadKeepsRowsFilesAndChangeCounters() = verify("readonly")
    @Test fun identicalFolioIdsRemainInsideSeparateDatabasePartitions() = verify("partition")
    @Test fun indexMigrationFromThreeKeepsPhotoFieldsAndEveryHistoryEvent() = verify("migration3")
    @Test fun maximumPageFetchesOnlyOneHundredPlusContinuationSentinel() = verify("bounded")

    private fun verify(mode: String) {
        val python = System.getenv("REPORT_SQLITE_TEST_PYTHON").orEmpty()
        assumeTrue("Set REPORT_SQLITE_TEST_PYTHON for host SQLite activity verification.", python.isNotBlank())
        val directory = Files.createTempDirectory("report-activity-sqlite-")
        try {
            val script = directory.resolve("verify.py")
            requireNotNull(javaClass.getResourceAsStream("/sqlite_report_activity.py")).use { Files.copy(it, script) }
            val queries = listOf(ReportActivityQuery(limit = 2), ReportActivityQuery(
                before = ReportActivityCursor(Instant.ofEpochSecond(2_000, 7), ID2), limit = 2),
                ReportActivityQuery(ReportActivityFilter.STATUS_CHANGES, limit = 2), ReportActivityQuery(limit = 100))
            val blocks = mutableListOf(ReportHistorySql.CREATE_TABLE, ReportHistorySql.INSERT, ReportActivitySql.CREATE_INDEX)
            queries.forEach { blocks += ReportActivitySql.page(it); blocks += ReportActivitySql.pageArguments(it).joinToString("\t") }
            blocks.forEachIndexed { index, sql -> Files.writeString(directory.resolve("statement$index.txt"), sql) }
            val process = ProcessBuilder(python, script.toString(), schemaSource().toString(), mode)
                .redirectErrorStream(true).start()
            assertTrue("SQLite activity verification timed out.", process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.exitValue())
            assertTrue(output, "activity-sqlite-ok" in output)
        } finally {
            val ownedRoot = directory.toRealPath()
            check(ownedRoot.parent == Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
            check(ownedRoot.fileName.toString().startsWith("report-activity-sqlite-"))
            Files.walk(ownedRoot).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { path ->
                check(path.toAbsolutePath().normalize().startsWith(ownedRoot))
                Files.delete(path)
            } }
        }
    }

    private fun schemaSource(): Path {
        var root: Path? = Path.of("").toAbsolutePath()
        while (root != null) {
            val source = root.resolve("core/data/src/main/kotlin/com/example/citizensecurity/data/ReportDatabase.kt")
            if (Files.isRegularFile(source)) return source
            root = root.parent
        }
        error("The production ReportDatabase schema must be available.")
    }
    companion object { const val ID2 = "b0000000-0000-0000-0000-000000000002" }
}
