package com.example.citizensecurity

import android.app.Application
import com.example.citizensecurity.data.AdminReportes
import com.example.citizensecurity.data.ReportRepository

class CitizenSecurityApplication : Application() {
    val reportRepository: ReportRepository by lazy { AdminReportes(this) }
}
