package com.example.citizensecurity.reports

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.report.ReportQueryViewModel
import com.example.citizensecurity.report.ReportPagedSearchViewModel

class ReportsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dependencies = application as CitizenSecurityApplication
        val query = ViewModelProvider(this, ReportQueryViewModel.factory(dependencies.reportRepository))[ReportQueryViewModel::class.java]
        val pages = ViewModelProvider(this, ReportPagedSearchViewModel.factory(dependencies.reportListPageRepository))[ReportPagedSearchViewModel::class.java]
        setContent {
            PreviewTheme {
                ReportsScreen(
                    query = query,
                    pages = pages,
                    initialFolio = intent.getStringExtra(EXTRA_FOLIO),
                    onClose = ::finish,
                    onMap = { folio ->
                        startActivity(
                            Intent().setClassName(this, "$packageName.reports.ReportsMapActivity")
                                .putExtra(EXTRA_FOLIO, folio),
                        )
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_FOLIO = "report_folio"
    }
}
