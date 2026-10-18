package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportResolutionQuery
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Production SQL on host SQLite; native repository permissions are tested separately. */
class ReportResolutionSqlTest {
    @Test fun migrationFromFourPreservesReportsPhotosStatusHistoryAndIndex() = verify("migration")
    @Test fun failedAdditiveMigrationRollsBackSchemaRowsAndVersion() = verify("rollback")
    @Test fun newTableStartsEmptyRatherThanDerivingOutcomeFromClosedStatus() = verify("empty")
    @Test fun invalidTypesIdentityLengthStateReasonAndClockAreRejectedBySqlite() = verify("constraints")
    @Test fun operationAndPerReportSequenceAreUniqueAcrossReportRows() = verify("uniqueness")
    @Test fun boundedKeysetPagesUseExclusiveSequenceRatherThanMutableClock() = verify("pages")
    @Test fun chainQueryDetectsGapBrokenLinkAndInvalidFirstOrigin() = verify("chain")
    @Test fun exactOperationLookupRetainsUnicodeNanosecondsAndActorKind() = verify("receipt")
    @Test fun resolutionAndStatusTransactionsRemainIndependentAndRollbackOnlyTheirOwnWrite() = verify("atomicity")
    @Test fun pagedHistoryAndReceiptsStayInsideTheirDatabasePartition() = verify("isolation")

    private fun verify(mode: String) {
        val python = System.getenv("REPORT_SQLITE_TEST_PYTHON").orEmpty()
        assumeTrue("Set REPORT_SQLITE_TEST_PYTHON for host SQLite result verification.", python.isNotBlank())
        val directory = Files.createTempDirectory("report-resolution-sqlite-")
        try {
            val script = directory.resolve("verify.py")
            requireNotNull(javaClass.getResourceAsStream("/sqlite_report_resolution.py")).use { Files.copy(it, script) }
            val blocks = listOf(ReportResolutionSql.CREATE_TABLE, ReportResolutionSql.INSERT,
                ReportResolutionSql.FIND_OPERATION, ReportResolutionSql.CREATOR, ReportResolutionSql.LATEST,
                ReportResolutionSql.CHAIN, ReportResolutionSql.page(ReportResolutionQuery(ID, limit = 2)),
                ReportResolutionSql.page(ReportResolutionQuery(ID, 4, 2)), ReportHistorySql.CREATE_TABLE,
                ReportActivitySql.CREATE_INDEX, ReportHistorySql.INSERT)
            blocks.forEachIndexed { index, sql -> Files.writeString(directory.resolve("statement$index.sql"), sql) }
            val process = ProcessBuilder(python, script.toString(), schemaSource().toString(), mode)
                .redirectErrorStream(true).start()
            assertTrue("SQLite result verification timed out.", process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, 0, process.exitValue())
            assertTrue(output, "resolution-sqlite-ok" in output)
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
