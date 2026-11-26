package com.example.citizensecurity.reports

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citizensecurity.account.LocalReportWorkspace
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.account.canInteract
import com.example.citizensecurity.alerts.ReportAlertsActivity
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.report.ReportActivityViewModel

/** Private, captured workspace. Activity entries never grant access to another account. */
class ReportActivityActivity : ReportWorkspaceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        setContent {
            PreviewTheme {
                ReportActivityRoute(
                    workspace = reportWorkspace,
                    onBack = ::finish,
                    onOpenReport = { folio ->
                        startReportActivity(Intent(this, ReportsActivity::class.java)
                            .putExtra(ReportsActivity.EXTRA_FOLIO, folio))
                    },
                    onOpenAlerts = { startReportActivity(Intent(this, ReportAlertsActivity::class.java)) },
                )
            }
        }
    }
}

@Composable
internal fun ReportActivityRoute(
    workspace: ReportWorkspace,
    onBack: () -> Unit,
    onOpenReport: (String) -> Unit,
    onOpenAlerts: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val model: ReportActivityViewModel = viewModel(factory = remember(workspace) {
        ReportActivityViewModel.factory(workspace.activityRepository, checkCurrent = workspace::checkActive)
    })
    CompositionLocalProvider(LocalReportWorkspace provides workspace) {
        ReportActivityScreen(
            model = model,
            onOpen = { entry ->
                if (workspace.canInteract(lifecycle) && model.canOpen(entry)) onOpenReport(entry.reportId)
            },
            onAlerts = { if (workspace.canInteract(lifecycle)) onOpenAlerts() },
            onClose = { if (workspace.canInteract(lifecycle)) onBack() },
        )
    }
}
