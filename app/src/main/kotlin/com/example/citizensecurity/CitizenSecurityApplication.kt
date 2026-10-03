package com.example.citizensecurity

import android.app.Application
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.domain.ReportRepository

class CitizenSecurityApplication : Application() {
    val reportRepository: ReportRepository by lazy { SqliteReportRepository(this) }
}
