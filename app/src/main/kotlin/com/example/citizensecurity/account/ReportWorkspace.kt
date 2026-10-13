package com.example.citizensecurity.account

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.data.PhoneLocationRefreshResult
import com.example.citizensecurity.data.ReportPhotoStore
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.IdempotentReportRepository
import com.example.citizensecurity.domain.ReportDraftCheckpointJournal
import com.example.citizensecurity.domain.ReportListPageRepository
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportMapRepository
import com.example.citizensecurity.domain.ReportPhoto
import com.example.citizensecurity.domain.ReportSubmissionJournal
import com.example.citizensecurity.domain.SimilarReportRepository
import com.example.citizensecurity.maps.IncidentLocationViewModel
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class ReportWorkspaceStorage(
    val repository: IdempotentReportRepository,
    val listPageRepository: ReportListPageRepository,
    val mapRepository: ReportMapRepository,
    val similarRepository: SimilarReportRepository,
    val submissionJournal: ReportSubmissionJournal,
    val checkpointJournal: ReportDraftCheckpointJournal,
    val photoStore: ReportPhotoStore,
    val release: () -> Unit = {},
)

/** One immutable owner for the whole Activity/model lifetime; no dynamic account lookups. */
class ReportWorkspace(
    val session: AccountSessionState,
    private val sessions: LocalAccountSession,
    storage: ReportWorkspaceStorage,
    private val locationRefresh: suspend () -> PhoneLocationRefreshResult,
    private val locationFix: () -> DeviceLocationFix?,
) {
    private val closed = AtomicBoolean(false)
    private val release = storage.release
    val photoStore = storage.photoStore
    private val access = ScopedReportAccess(storage.repository, storage.listPageRepository,
        storage.mapRepository, storage.similarRepository, storage.submissionJournal,
        storage.checkpointJournal, ::checkActive)
    val repository = access.reportRepository
    val listPageRepository = access.reportListPageRepository
    val mapRepository = access.reportMapRepository
    val similarRepository = access.similarReportRepository
    val submissionJournal = access.reportSubmissionJournal
    val checkpointJournal = access.reportDraftCheckpointJournal

    fun checkActive() {
        check(!closed.get() && sessions.isCurrent(session)) {
            "La sesión de este espacio terminó. Inicia sesión nuevamente para continuar."
        }
    }

    suspend fun importPhoto(uri: Uri): ReportPhoto = checked { photoStore.importPhoto(uri) }
    suspend fun readPhoto(photo: ReportPhoto): ByteArray = checked { photoStore.read(photo) }
    suspend fun refreshPhoneLocation(): PhoneLocationRefreshResult = checked { locationRefresh() }
    fun currentPhoneLocation(): DeviceLocationFix? {
        checkActive()
        return locationFix().also { checkActive() }
    }

    fun incidentLocationViewModelFactory(location: ReportLocation): ViewModelProvider.Factory =
        IncidentLocationViewModel.factory(location, ::refreshPhoneLocation, ::currentPhoneLocation,
            android.os.SystemClock::elapsedRealtimeNanos)

    private suspend fun <T> checked(operation: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        checkActive()
        return operation().also { currentCoroutineContext().ensureActive(); checkActive() }
    }

    /** Closing a database waits for its own transactions on IO, never blocks the login. */
    fun close() {
        if (closed.compareAndSet(false, true)) closer.launch {
            try { release() } catch (_: Exception) { /* Closing never repairs or deletes stored data. */ }
        }
    }

    private companion object { val closer = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
