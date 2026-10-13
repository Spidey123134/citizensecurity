package com.example.citizensecurity.reports

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.account.LocalReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.report.ReportQueryViewModel
import com.example.citizensecurity.report.ReportPagedSearchViewModel
import com.example.citizensecurity.report.ReportHistoryViewModel

class ReportsActivity : ReportWorkspaceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        val query = ViewModelProvider(this, ReportQueryViewModel.factory(reportWorkspace.repository))[ReportQueryViewModel::class.java]
        val pages = ViewModelProvider(this, ReportPagedSearchViewModel.factory(reportWorkspace.listPageRepository))[ReportPagedSearchViewModel::class.java]
        val history = ViewModelProvider(this, ReportHistoryViewModel.factory(reportWorkspace.historyRepository))[ReportHistoryViewModel::class.java]
        setContent {
            CompositionLocalProvider(LocalReportWorkspace provides reportWorkspace) {
                PreviewTheme {
                    ReportsScreen(
                        query = query,
                        pages = pages,
                        history = history,
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

    companion object {
        const val EXTRA_FOLIO = "report_folio"
    }
}
