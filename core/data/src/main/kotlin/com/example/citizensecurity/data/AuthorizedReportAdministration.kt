package com.example.citizensecurity.data

import com.example.citizensecurity.domain.DenyAllReportAdministrationPolicy
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportAdministrationPermission
import com.example.citizensecurity.domain.ReportAdministrationPermissionDeniedException
import com.example.citizensecurity.domain.ReportAdministrationPolicy
import com.example.citizensecurity.domain.ReportAdministrationReader
import com.example.citizensecurity.domain.ReportHistoryPage
import com.example.citizensecurity.domain.ReportHistoryQuery
import com.example.citizensecurity.domain.ReportHistoryRepository
import com.example.citizensecurity.domain.ReportListPage
import com.example.citizensecurity.domain.ReportListPageQuery
import com.example.citizensecurity.domain.ReportListPageRepository
import com.example.citizensecurity.domain.ReportStatusChangeScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Captured delegates only. Their scope is a storage binding, not a grant of authority. */
class ReportAdministrationSources(
    val scope: ReportStatusChangeScope,
    val findById: suspend (String) -> Report?,
    val listPageRepository: ReportListPageRepository,
    val historyRepository: ReportHistoryRepository,
)

/**
 * Foreground, read-only administration boundary. The provider must capture one workspace,
 * never resolve a new owner/session dynamically. It is initialized only after authorization;
 * closing the underlying workspace remains the owner's responsibility.
 */
class AuthorizedReportAdministration(
    override val scope: ReportStatusChangeScope,
    private val policy: ReportAdministrationPolicy = DenyAllReportAdministrationPolicy,
    sources: () -> ReportAdministrationSources,
    private val checkCurrent: () -> Unit = {},
) : ReportAdministrationReader {
    private val capturedSources by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        sources().also {
            if (it.scope != scope) throw ReportAdministrationPermissionDeniedException()
        }
    }

    override suspend fun findById(id: String): Report? = checked { captured ->
        require(id.isNotBlank() && id.length <= 64) { "La consulta necesita un folio válido." }
        captured.findById(id).also { report ->
            check(report == null || report.id == id) { "La consulta devolvió otro folio." }
        }
    }

    override suspend fun listPage(query: ReportListPageQuery): ReportListPage = checked { captured ->
        captured.listPageRepository.listPage(query).also { page ->
            check(page.reports.size <= query.limit) { "La consulta superó el límite de la página." }
        }
    }

    override suspend fun readHistory(query: ReportHistoryQuery): ReportHistoryPage? = checked { captured ->
        captured.historyRepository.readHistory(query).also { page ->
            check(page == null || (page.reportId == query.reportId && page.events.size <= query.limit)) {
                "La consulta devolvió un historial incompatible."
            }
        }
    }

    private suspend fun <T> checked(read: suspend (ReportAdministrationSources) -> T): T {
        guard()
        val captured = capturedSources
        guard()
        val result = read(captured)
        guard()
        return result
    }

    private suspend fun guard() {
        currentCoroutineContext().ensureActive()
        checkCurrent()
        policy.check(scope, ReportAdministrationPermission.READ_REPORTS)
        currentCoroutineContext().ensureActive()
    }
}
