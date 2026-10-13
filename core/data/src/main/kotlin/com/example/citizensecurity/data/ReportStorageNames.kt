package com.example.citizensecurity.data

import java.util.UUID

/** Immutable storage partition. Only an account's canonical UUID can select new paths. */
class ReportStorageNames private constructor(
    val databaseName: String,
    val submissionFileName: String,
    val checkpointFileName: String,
    val photoDirectoryName: String,
) {
    companion object {
        fun fromAccountId(id: String?): ReportStorageNames {
            if (id == null) return ReportStorageNames(
                SqliteReportRepository.DATABASE_NAME,
                ReportSubmissionStore.FILE_NAME,
                ReportDraftCheckpointStore.FILE_NAME,
                ReportPhotoStore.DIRECTORY_NAME,
            )
            require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
                "El espacio de reportes requiere un UUID de cuenta completo y canónico."
            }
            return ReportStorageNames(
                databaseName = "citizensecurity_account_$id.db",
                submissionFileName = "report_submission_$id.json",
                checkpointFileName = "report_draft_$id.json",
                photoDirectoryName = "report_photos_$id",
            )
        }
    }
}
