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
import com.example.citizensecurity.domain.ReportAdministrationPolicy
import com.example.citizensecurity.domain.ReportStatusChangeAccess
import com.example.citizensecurity.domain.ReportStatusChangeScope
import com.example.citizensecurity.domain.ReportResolutionRepository
import com.example.citizensecurity.domain.ReportResolutionQuery
import com.example.citizensecurity.domain.ReportResolutionCommand
import com.example.citizensecurity.domain.ReportResolutionPermissionDeniedException
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
    val statusChangeAccess: ((ReportStatusChangeScope, ReportAdministrationPolicy) -> ReportStatusChangeAccess)? = null,
    val resolutionAccess: ((() -> Unit) -> ReportResolutionRepository)? = null,
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
    private val statusChangeAccess = storage.statusChangeAccess
    private val resolutionAccess = storage.resolutionAccess
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
    val historyRepository = access.reportHistoryRepository
    val activityRepository = access.reportActivityRepository

    /** Construction never reads files. Every operation remains tied to this exact session. */
    fun openResolutionRepository(checkWrite: () -> Unit): ReportResolutionRepository {
        checkActive()
        val delegate = resolutionAccess?.invoke(checkWrite) ?: throw ReportResolutionPermissionDeniedException()
        checkActive()
        return object : ReportResolutionRepository {
            override suspend fun read(query: ReportResolutionQuery) = checked { delegate.read(query) }
            override suspend fun record(command: ReportResolutionCommand) = checked { delegate.record(command) }
            override suspend fun receipt(command: ReportResolutionCommand) = checked { delegate.receipt(command) }
        }
    }

    /** Factory only: the binding checks authority on IO before entering this captured source. */
    internal fun createStatusChangeAccess(scope: ReportStatusChangeScope, policy: ReportAdministrationPolicy): ReportStatusChangeAccess {
        checkActive()
        val accountId = session.account?.id ?: throw com.example.citizensecurity.domain.ReportStatusPermissionDeniedException()
        if (scope != ReportStatusChangeScope(accountId, accountId)) {
            throw com.example.citizensecurity.domain.ReportStatusPermissionDeniedException()
        }
        val factory = statusChangeAccess ?: throw com.example.citizensecurity.domain.ReportStatusPermissionDeniedException()
        return factory(scope, policy).also {
            checkActive()
            if (it.scope != scope) throw com.example.citizensecurity.domain.ReportStatusPermissionDeniedException()
        }
    }

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
