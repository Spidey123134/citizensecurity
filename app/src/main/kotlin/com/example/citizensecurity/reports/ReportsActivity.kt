package com.example.citizensecurity.reports

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citizensecurity.account.LocalReportWorkspace
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.account.canInteract
import com.example.citizensecurity.evidence.ReportEvidenceExport
import com.example.citizensecurity.evidence.ReportEvidenceState
import com.example.citizensecurity.evidence.ReportEvidenceViewModel
import com.example.citizensecurity.message.ReportMessageActivity
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.report.ReportHistoryViewModel
import com.example.citizensecurity.report.ReportPagedSearchViewModel
import com.example.citizensecurity.report.ReportQueryViewModel
import com.example.citizensecurity.resolution.ReportResolutionViewModel
import com.example.citizensecurity.resolution.bindReportResolution
import com.example.citizensecurity.resolution.rememberReportResolution
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportsActivity : ReportWorkspaceActivity() {
    internal lateinit var resolutionModel: ReportResolutionViewModel
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        resolutionModel = bindReportResolution(this, reportWorkspace)
        setContent {
            PreviewTheme {
                ReportsRouteContent(
                    workspace = reportWorkspace,
                    initialFolio = intent.getStringExtra(EXTRA_FOLIO),
                    resolution = resolutionModel,
                    onBack = ::finish,
                    onOpenMessage = { folio ->
                        startReportActivity(Intent(this, ReportMessageActivity::class.java).putExtra(EXTRA_FOLIO, folio))
                    },
                    onOpenMap = { folio ->
                        startReportActivity(Intent(this, ReportsMapActivity::class.java).putExtra(EXTRA_FOLIO, folio))
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_FOLIO = "report_folio"
    }
}

@Composable
internal fun ReportsRoute(
    workspace: ReportWorkspace,
    initialFolio: String? = null,
    onBack: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onOpenMap: (String) -> Unit,
) {
    ReportsRouteContent(workspace, initialFolio, rememberReportResolution(workspace), onBack, onOpenMessage, onOpenMap)
}

@Composable
private fun ReportsRouteContent(
    workspace: ReportWorkspace,
    initialFolio: String?,
    resolution: ReportResolutionViewModel,
    onBack: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onOpenMap: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val query: ReportQueryViewModel = viewModel(factory = remember(workspace) {
        ReportQueryViewModel.factory(workspace.repository)
    })
    val pages: ReportPagedSearchViewModel = viewModel(factory = remember(workspace) {
        ReportPagedSearchViewModel.factory(workspace.listPageRepository)
    })
    val history: ReportHistoryViewModel = viewModel(factory = remember(workspace) {
        ReportHistoryViewModel.factory(workspace.historyRepository)
    })
    val exporter = remember(context.applicationContext, workspace) {
        ReportEvidenceExport(context.applicationContext, workspace)
    }
    val evidence: ReportEvidenceViewModel = viewModel(factory = remember(exporter) {
        ReportEvidenceViewModel.factory(exporter)
    })
    CompositionLocalProvider(LocalReportWorkspace provides workspace) {
        ReportsScreen(
            query = query,
            pages = pages,
            history = history,
            evidence = evidence,
            resolution = resolution,
            onShareEvidence = { ready, isCurrentDetail ->
                scope.launch {
                    shareEvidence(context, lifecycle, workspace, evidence, exporter, ready, isCurrentDetail)
                }
            },
            onMessage = { folio -> if (workspace.canInteract(lifecycle)) onOpenMessage(folio) },
            initialFolio = initialFolio,
            onClose = { if (workspace.canInteract(lifecycle)) onBack() },
            onMap = { folio -> if (workspace.canInteract(lifecycle)) onOpenMap(folio) },
        )
    }
}

private suspend fun shareEvidence(
    context: Context,
    lifecycle: Lifecycle,
    workspace: ReportWorkspace,
    model: ReportEvidenceViewModel,
    exporter: ReportEvidenceExport,
    ready: ReportEvidenceState.Ready,
    isCurrentDetail: () -> Boolean,
) {
    if (!isCurrentDetail() || !workspace.canInteract(lifecycle)) return
    val sharing = model.consumeForShare(ready) ?: return
    try {
        val file = withContext(Dispatchers.IO) {
            val scope = this
            exporter.fileForSharing(sharing.artifact) {
                scope.ensureActive()
                model.checkShareCurrent(sharing)
                workspace.checkActive()
            }
        }
        currentCoroutineContext().ensureActive()
        model.checkShareCurrent(sharing)
        if (!isCurrentDetail() || !workspace.canInteract(lifecycle)) {
            model.shareFailure(sharing)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.evidence", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Ficha del incidente", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        model.checkShareCurrent(sharing)
        workspace.checkActive()
        context.startActivity(Intent.createChooser(send, "Compartir ficha del incidente"))
        model.shareComplete(sharing)
    } catch (cancelled: CancellationException) {
        model.shareFailure(sharing)
        throw cancelled
    } catch (_: Exception) {
        model.shareFailure(sharing)
    }
}
