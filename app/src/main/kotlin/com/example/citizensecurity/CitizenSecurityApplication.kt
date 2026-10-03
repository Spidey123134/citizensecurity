package com.example.citizensecurity

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.domain.ReportRepository
import com.example.citizensecurity.report.ReportViewModel

class CitizenSecurityApplication : Application() {
    val reportRepository: ReportRepository by lazy { SqliteReportRepository(this) }
    val reportViewModelFactory: ViewModelProvider.Factory by lazy {
        ReportViewModel.factory(reportRepository)
    }
}
