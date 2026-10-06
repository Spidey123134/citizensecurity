package com.example.citizensecurity

import android.app.Application
import com.example.citizensecurity.data.AndroidPhoneLocationSource
import com.example.citizensecurity.data.PhoneLocationRefresh
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.data.NearbySqliteReportGuard
import com.example.citizensecurity.data.PhoneLocationEvidence
import com.example.citizensecurity.domain.ReportRepository
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.maps.IncidentLocationViewModel
import com.example.citizensecurity.maps.MapsConfiguration
import com.example.citizensecurity.maps.MapsReadinessChecker
import com.google.android.gms.common.GoogleApiAvailability
import com.example.citizensecurity.report.ReportQueryViewModel
import com.example.citizensecurity.report.ReportViewModel

class CitizenSecurityApplication : Application() {
    val mapsReadinessChecker by lazy {
        MapsReadinessChecker(
            { MapsConfiguration.isConfigured(this) },
            { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) },
        )
    }

    fun incidentLocationViewModelFactory(originalLocation: ReportLocation): ViewModelProvider.Factory =
        IncidentLocationViewModel.factory(
            originalLocation,
            { phoneLocationRefresh.refresh() },
            { phoneLocationEvidence.currentFix() },
            SystemClock::elapsedRealtimeNanos,
        )

    private val phoneLocationSource by lazy { AndroidPhoneLocationSource(this) }

    val phoneLocationEvidence: PhoneLocationEvidence by lazy {
        PhoneLocationEvidence(
            phoneLocationSource::hasPrecisePermission,
            phoneLocationSource::isLocationEnabled,
        )
    }
    val phoneLocationRefresh by lazy {
        PhoneLocationRefresh(
            phoneLocationSource, phoneLocationEvidence, SystemClock::elapsedRealtimeNanos,
        )
    }

    val reportRepository: ReportRepository by lazy {
        val guard = NearbySqliteReportGuard(
            deviceLocation = phoneLocationEvidence::currentFix,
            elapsedRealtimeNanos = SystemClock::elapsedRealtimeNanos,
        )
        SqliteReportRepository(this, createGuard = guard::check)
    }
    val reportViewModelFactory: ViewModelProvider.Factory by lazy {
        ReportViewModel.factory(reportRepository)
    }
    val reportQueryViewModelFactory: ViewModelProvider.Factory by lazy {
        ReportQueryViewModel.factory(reportRepository)
    }
}
