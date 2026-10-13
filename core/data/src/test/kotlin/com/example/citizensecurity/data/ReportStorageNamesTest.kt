package com.example.citizensecurity.data

import org.junit.Assert.*
import org.junit.Test

class ReportStorageNamesTest {
    private val first = "00000000-0000-0000-0000-000000000001"
    private val second = "00000000-0000-0000-0000-000000000002"

    @Test fun academicPartitionKeepsEveryHistoricalPath() {
        val names = ReportStorageNames.fromAccountId(null)
        assertEquals("citizensecurity_local_v1.db", names.databaseName)
        assertEquals("report_submission_v1.json", names.submissionFileName)
        assertEquals("report_draft_v1.json", names.checkpointFileName)
        assertEquals("report_photos", names.photoDirectoryName)
    }

    @Test fun accountAndAcademicPartitionsHaveDisjointPaths() {
        val sets = listOf(null, first, second).map { paths(ReportStorageNames.fromAccountId(it)).toSet() }
        assertTrue(sets.all { it.size == 4 })
        assertEquals(12, sets.flatten().toSet().size)
    }

    @Test fun canonicalIdentityReopensTheSamePathsWithoutAnyUsername() {
        assertEquals(paths(ReportStorageNames.fromAccountId(first)), paths(ReportStorageNames.fromAccountId(first)))
        val names = ReportStorageNames.fromAccountId(first)
        assertTrue(paths(names).all { first in it && '/' !in it && '\\' !in it })
        assertTrue(names.photoDirectoryName.length <= 64)
        assertTrue(names.databaseName.endsWith(".db"))
        assertTrue(names.submissionFileName.endsWith(".json"))
        assertTrue(names.checkpointFileName.endsWith(".json"))
    }

    @Test fun namesRejectPathsWhitespaceNonCanonicalAndPartialIdentifiers() {
        listOf("", "alumna1", "1-1-1-1-1", "../$first", "$first/otra", " $first", "$first ",
            "ABCDEF00-0000-0000-0000-000000000001", "$first.json", first.replace("-", ""), "$first\u0000").forEach { id ->
            try {
                ReportStorageNames.fromAccountId(id)
                fail("Accepted an incompatible account identity.")
            } catch (_: IllegalArgumentException) { /* Reject rather than normalize to another path. */ }
        }
    }

    @Test fun partitionDoesNotCreateFilesOrOpenARepository() {
        // This pure construction runs without an Android Context, SQLite or filesystem access.
        val names = ReportStorageNames.fromAccountId(first)
        assertEquals("citizensecurity_account_$first.db", names.databaseName)
    }

    private fun paths(names: ReportStorageNames) = listOf(
        names.databaseName, names.submissionFileName, names.checkpointFileName, names.photoDirectoryName,
    )
}
