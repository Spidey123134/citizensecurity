package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportHistoryQuery
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Executes production history DDL/statements with the host SQLite engine; not Android acceptance. */
class ReportHistorySqlTest {
    @Test fun migrationFromOnePreservesFieldsPhotoNullsAndUnknownHistory() = verify("migration1")
    @Test fun migrationFromTwoPreservesPhotosStateAndUnknownHistory() = verify("migration2")
    @Test fun failedHistoryMigrationRollsBackColumnsVersionAndRows() = verify("migrationRollback")
    @Test fun creationAndHistoryInsertCommitTogetherOrNeither() = verify("creation")
    @Test fun statusCasAndEventInsertCommitTogetherOrNeither() = verify("statusAtomic")
    @Test fun staleWriterCannotAppendSecondEventOrOverwriteStatus() = verify("cas")
    @Test fun operationAndSequenceAreUniqueAndInvalidTransitionsRejected() = verify("constraints")
    @Test fun keysetPagesStayBoundedExclusiveAndOrderedBySequence() = verify("page")
    @Test fun historyKeepsNanosecondsAndIsOrderedEvenWhenClockMovesBackwards() = verify("clock")
    @Test fun queryAndOperationReceiptStayInsideTheirDatabasePartition() = verify("isolation")

    private fun verify(mode: String) {
        val python = System.getenv("REPORT_SQLITE_TEST_PYTHON").orEmpty()
        assumeTrue("Set REPORT_SQLITE_TEST_PYTHON for host SQLite history verification.", python.isNotBlank())
        val directory = Files.createTempDirectory("report-history-sqlite-")
        try {
            val script = directory.resolve("verify.py")
            requireNotNull(javaClass.getResourceAsStream("/sqlite_report_history.py")).use { Files.copy(it, script) }
            val blocks = listOf(ReportHistorySql.CREATE_TABLE, ReportHistorySql.INSERT, ReportHistorySql.HEAD,
                ReportHistorySql.FIND_OPERATION, ReportHistorySql.CAS_UPDATE,
                ReportHistorySql.page(ReportHistoryQuery(ID, limit = 2)),
                ReportHistorySql.page(ReportHistoryQuery(ID, 3, 2)), ReportActivitySql.CREATE_INDEX)
            blocks.forEachIndexed { index, sql -> Files.writeString(directory.resolve("statement$index.sql"), sql) }
            val process = ProcessBuilder(python, script.toString(), schemaSource().toString(), mode)
                .redirectErrorStream(true).start()
            assertTrue("SQLite history verification timed out.", process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.exitValue())
            assertTrue(output, "history-sqlite-ok" in output)
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
        error("The production report schema must be available.")
    }
    companion object { const val ID = "00000000-0000-0000-0000-000000000001" }
}
