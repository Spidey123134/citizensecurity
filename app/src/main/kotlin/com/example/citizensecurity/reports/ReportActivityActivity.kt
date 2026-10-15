package com.example.citizensecurity.reports

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.account.LocalReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.report.ReportActivityViewModel

/** Private, captured workspace. Activity entries never grant access to another account. */
class ReportActivityActivity : ReportWorkspaceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        val model = ViewModelProvider(this, ReportActivityViewModel.factory(
            reportWorkspace.activityRepository, checkCurrent = reportWorkspace::checkActive,
        ))[ReportActivityViewModel::class.java]
        setContent {
            CompositionLocalProvider(LocalReportWorkspace provides reportWorkspace) {
                PreviewTheme {
                    ReportActivityScreen(model, onOpen = { entry ->
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                            ensureReportWorkspaceCurrent() && model.canOpen(entry)) {
                            startReportActivity(Intent(this, ReportsActivity::class.java)
                                .putExtra(ReportsActivity.EXTRA_FOLIO, entry.reportId))
                        }
                    }, onClose = {
                        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                            ensureReportWorkspaceCurrent()) finish()
                    })
                }
            }
        }
    }
}
