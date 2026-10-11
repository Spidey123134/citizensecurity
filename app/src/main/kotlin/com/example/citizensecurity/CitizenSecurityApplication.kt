package com.example.citizensecurity

import android.app.Application
import com.example.citizensecurity.data.AndroidPhoneLocationSource
import com.example.citizensecurity.data.PhoneLocationRefresh
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.data.NearbySqliteReportGuard
import com.example.citizensecurity.data.PhoneLocationEvidence
import com.example.citizensecurity.domain.IdempotentReportRepository
import com.example.citizensecurity.domain.ReportSubmissionJournal
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.data.ReportSubmissionStore
import com.example.citizensecurity.data.ReportPhotoStore
import com.example.citizensecurity.data.ReportDraftCheckpointStore
import com.example.citizensecurity.domain.ReportDraftCheckpointJournal
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ValidationErrors
import java.io.IOException
import com.example.citizensecurity.data.PhoneLocationRefreshResult
import com.example.citizensecurity.domain.ReportMapRepository
import com.example.citizensecurity.domain.SimilarReportRepository
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.maps.IncidentLocationViewModel
import com.example.citizensecurity.maps.MapsConfiguration
import com.example.citizensecurity.maps.MapsReadinessChecker
import com.example.citizensecurity.maps.ReportMapViewModel
import com.example.citizensecurity.report.ReportQueryViewModel
import com.example.citizensecurity.report.ReportViewModel

open class CitizenSecurityApplication : Application() {
    val mapsReadinessChecker by lazy {
        MapsReadinessChecker(
            { MapsConfiguration.initialize(this) },
        )
    }

    open fun incidentLocationViewModelFactory(originalLocation: ReportLocation): ViewModelProvider.Factory =
        IncidentLocationViewModel.factory(
            originalLocation,
            { refreshReportPhoneLocation() },
            { currentReportPhoneLocation() },
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

    open suspend fun refreshReportPhoneLocation(): PhoneLocationRefreshResult = phoneLocationRefresh.refresh()
    open fun currentReportPhoneLocation(): DeviceLocationFix? = phoneLocationEvidence.currentFix()
    open val reportSubmissionJournal: ReportSubmissionJournal by lazy { ReportSubmissionStore(this) }
    open val reportPhotoStore: ReportPhotoStore by lazy { ReportPhotoStore(this) }
    open val reportDraftCheckpointJournal: ReportDraftCheckpointJournal by lazy { ReportDraftCheckpointStore(this) }

    private val sharedReportRepository by lazy {
        val guard = NearbySqliteReportGuard(
            deviceLocation = ::currentReportPhoneLocation,
            elapsedRealtimeNanos = SystemClock::elapsedRealtimeNanos,
        )
        SqliteReportRepository(this, createGuard = { draft ->
            guard.check(draft)
            draft.photo?.let { photo ->
                try {
                    reportPhotoStore.check(photo)
                } catch (_: IOException) {
                    throw InvalidReportException(ValidationErrors(mapOf(
                        ReportField.PHOTO to "La fotografía no está disponible o está dañada. Vuelve a elegirla o quítala antes de guardar.",
                    )))
                }
            }
        })
    }
    open val reportRepository: IdempotentReportRepository get() = sharedReportRepository
    open val reportMapRepository: ReportMapRepository get() = sharedReportRepository
    open val similarReportRepository: SimilarReportRepository get() = sharedReportRepository
    val reportViewModelFactory: ViewModelProvider.Factory by lazy {
        ReportViewModel.factory(reportRepository)
    }
    val reportQueryViewModelFactory: ViewModelProvider.Factory by lazy {
        ReportQueryViewModel.factory(reportRepository)
    }
    val reportMapViewModelFactory: ViewModelProvider.Factory
        get() = ReportMapViewModel.factory(reportMapRepository)
}
