package com.example.citizensecurity.reports

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.report.ReportListState
import com.example.citizensecurity.report.ReportListViewModel
import com.example.citizensecurity.report.ReportQueryViewModel

class ReportsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as CitizenSecurityApplication).reportRepository
        val list = ViewModelProvider(this, ReportListViewModel.factory(repository))[ReportListViewModel::class.java]
        val query = ViewModelProvider(this, ReportQueryViewModel.factory(repository))[ReportQueryViewModel::class.java]
        if (list.state.value == ReportListState.Idle) list.load()
        setContent {
            PreviewTheme {
                ReportsScreen(
                    list = list,
                    query = query,
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
