package com.example.citizensecurity.reports

import android.content.Intent
import android.content.ClipData
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.core.content.FileProvider
import com.example.citizensecurity.evidence.ReportEvidenceExport
import com.example.citizensecurity.evidence.ReportEvidenceState
import com.example.citizensecurity.evidence.ReportEvidenceViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.citizensecurity.account.LocalReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.report.ReportQueryViewModel
import com.example.citizensecurity.report.ReportPagedSearchViewModel
import com.example.citizensecurity.report.ReportHistoryViewModel

class ReportsActivity : ReportWorkspaceActivity() {
    internal lateinit var resolutionModel: com.example.citizensecurity.resolution.ReportResolutionViewModel
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        resolutionModel = com.example.citizensecurity.resolution.bindReportResolution(this, reportWorkspace)
        val query = ViewModelProvider(this, ReportQueryViewModel.factory(reportWorkspace.repository))[ReportQueryViewModel::class.java]
        val pages = ViewModelProvider(this, ReportPagedSearchViewModel.factory(reportWorkspace.listPageRepository))[ReportPagedSearchViewModel::class.java]
        val history = ViewModelProvider(this, ReportHistoryViewModel.factory(reportWorkspace.historyRepository))[ReportHistoryViewModel::class.java]
        val exporter = ReportEvidenceExport(applicationContext, reportWorkspace)
        val evidence = ViewModelProvider(this, ReportEvidenceViewModel.factory(exporter))[ReportEvidenceViewModel::class.java]
        setContent {
            CompositionLocalProvider(LocalReportWorkspace provides reportWorkspace) {
                PreviewTheme {
                    ReportsScreen(
                        query = query,
                        pages = pages,
                        history = history,
                        evidence = evidence,
                        resolution = resolutionModel,
                        onShareEvidence = { ready, isCurrent -> shareEvidence(evidence, exporter, ready, isCurrent) },
                        onMessage = { folio ->
                            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && ensureReportWorkspaceCurrent()) {
                                startReportActivity(Intent(this, com.example.citizensecurity.message.ReportMessageActivity::class.java)
                                    .putExtra(EXTRA_FOLIO, folio))
                            }
                        },
                        initialFolio = intent.getStringExtra(EXTRA_FOLIO),
                        onClose = ::finish,
                        onMap = { folio ->
                            startReportActivity(
                                Intent().setClassName(this, "$packageName.reports.ReportsMapActivity")
                                    .putExtra(EXTRA_FOLIO, folio),
                            )
                        },
                    )
                }
            }
        }
    }

    private fun shareEvidence(model: ReportEvidenceViewModel, exporter: ReportEvidenceExport,
        ready: ReportEvidenceState.Ready, isCurrentDetail: () -> Boolean) {
        if (!isCurrentDetail() || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !ensureReportWorkspaceCurrent()) return
        val sharing = model.consumeForShare(ready) ?: return
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val scope = this
                    exporter.fileForSharing(sharing.artifact) {
                        scope.ensureActive()
                        model.checkShareCurrent(sharing)
                        reportWorkspace.checkActive()
                    }
                }
                ensureActive()
                model.checkShareCurrent(sharing)
                reportWorkspace.checkActive()
                if (!isCurrentDetail() || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    model.shareFailure(sharing)
                    return@launch
                }
                val uri = FileProvider.getUriForFile(this@ReportsActivity, "$packageName.evidence", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri("Ficha del incidente", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                model.checkShareCurrent(sharing)
                reportWorkspace.checkActive()
                startActivity(Intent.createChooser(send, "Compartir ficha del incidente"))
                model.shareComplete(sharing)
            } catch (cancelled: CancellationException) {
                model.shareFailure(sharing)
                throw cancelled
            } catch (_: Exception) {
                model.shareFailure(sharing)
            }
        }
    }

    companion object {
        const val EXTRA_FOLIO = "report_folio"
    }
}
