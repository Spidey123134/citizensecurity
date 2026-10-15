package com.example.citizensecurity.account

import com.example.citizensecurity.domain.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A lease over already captured storage; session changes never replace its delegates. */
class ScopedReportAccess(
    repo: IdempotentReportRepository,
    list: ReportListPageRepository,
    map: ReportMapRepository,
    similar: SimilarReportRepository,
    submission: ReportSubmissionJournal,
    checkpoint: ReportDraftCheckpointJournal,
    history: ReportHistoryRepository?,
    private val checkAccess: () -> Unit,
) {
    /** Preserve the original trailing-lambda API for callers without a separate history reader. */
    constructor(
        repo: IdempotentReportRepository,
        list: ReportListPageRepository,
        map: ReportMapRepository,
        similar: SimilarReportRepository,
        submission: ReportSubmissionJournal,
        checkpoint: ReportDraftCheckpointJournal,
        checkAccess: () -> Unit,
    ) : this(repo, list, map, similar, submission, checkpoint, repo as? ReportHistoryRepository, checkAccess)

    private val checkpointStore = checkpoint
    private val activityReader = repo as? ReportActivityRepository
    val reportRepository: IdempotentReportRepository = object : IdempotentReportRepository {
        override suspend fun create(draft: NewReport): Report = checked { repo.create(draft) }
        override suspend fun createOnce(requestId: String, draft: NewReport): Report =
            checked { repo.createOnce(requestId, draft) }
        override suspend fun findById(id: String): Report? = checked { repo.findById(id) }
        override suspend fun list(): List<Report> = checked { repo.list() }
    }
    val reportListPageRepository = ReportListPageRepository { query -> checked { list.listPage(query) } }
    val reportMapRepository = ReportMapRepository { query -> checked { map.queryMarkers(query) } }
    val similarReportRepository = SimilarReportRepository { query -> checked { similar.findSimilar(query) } }
    val reportSubmissionJournal: ReportSubmissionJournal = object : ReportSubmissionJournal {
        override suspend fun read(): ReportSubmissionRequest? = checked { submission.read() }
        override suspend fun write(request: ReportSubmissionRequest) = checked { submission.write(request) }
        override suspend fun clear(requestId: String) = checked { submission.clear(requestId) }
    }
    val reportDraftCheckpointJournal: ReportDraftCheckpointJournal = object : ReportDraftCheckpointJournal {
        override suspend fun read(): ReportDraftCheckpoint? = checked { checkpointStore.read() }
        override suspend fun write(checkpoint: ReportDraftCheckpoint, expectedId: String?, expectedSubmittedFolio: String?) =
            checked { checkpointStore.write(checkpoint, expectedId, expectedSubmittedFolio) }
        override suspend fun markSubmitted(id: String, folio: String) = checked { checkpointStore.markSubmitted(id, folio) }
    }
    val reportHistoryRepository = ReportHistoryRepository { query ->
        checked { checkNotNull(history) { "El historial no está disponible en este espacio." }.readHistory(query) }
    }
    val reportActivityRepository = ReportActivityRepository { query ->
        checked { checkNotNull(activityReader) { "La actividad no está disponible en este espacio." }.readActivity(query) }
    }

    private suspend fun <T> checked(action: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        checkAccess()
        val result = action()
        currentCoroutineContext().ensureActive()
        checkAccess()
        return result
    }
}
