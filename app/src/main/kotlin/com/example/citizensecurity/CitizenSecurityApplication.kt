package com.example.citizensecurity

import android.app.Application
import com.example.citizensecurity.account.LocalAccountSession
import com.example.citizensecurity.account.AccountSessionState
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceStorage
import com.example.citizensecurity.account.ReportAdministrationBinding
import com.example.citizensecurity.domain.ReportStatusChangeAccess
import com.example.citizensecurity.domain.ReportStatusChangeJournal
import com.example.citizensecurity.domain.ReportStatusChangeRequest
import com.example.citizensecurity.data.SqliteReportStatusChangeAccess
import com.example.citizensecurity.data.ReportStatusChangeStore
import com.example.citizensecurity.data.ReportStorageNames
import com.example.citizensecurity.data.SqliteAccountRepository
import com.example.citizensecurity.domain.AccountRepository
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
import com.example.citizensecurity.domain.ReportListPageRepository
import com.example.citizensecurity.domain.SimilarReportRepository
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.maps.IncidentLocationViewModel
import com.example.citizensecurity.maps.MapsConfiguration
import com.example.citizensecurity.maps.MapsReadinessChecker
import com.example.citizensecurity.maps.ReportMapViewModel
import com.example.citizensecurity.report.ReportQueryViewModel
import com.example.citizensecurity.report.ReportViewModel
import com.example.citizensecurity.alerts.AndroidReportAlertsPublisher
import com.example.citizensecurity.data.ReportAlertsStore
import com.example.citizensecurity.domain.ReportAlertsRepository
import com.example.citizensecurity.data.TrustedContactsStore
import com.example.citizensecurity.domain.TrustedContactsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

open class CitizenSecurityApplication : Application() {
    open val accountRepository: AccountRepository by lazy { SqliteAccountRepository(this) }
    val accountSession = LocalAccountSession()

    /** Installation assignment is local and is created only by the owner-confirmation flow. */
    open fun openLocalAdministratorStore(): com.example.citizensecurity.data.LocalAdministratorStore =
        com.example.citizensecurity.data.LocalAdministratorStore(this)

    open fun openReportAdministration(workspace: ReportWorkspace): com.example.citizensecurity.account.ReportAdministrationBinding =
        com.example.citizensecurity.account.ReportAdministrationBinding.capture(workspace, openLocalAdministratorStore().policy())

    /** Pure construction; all authority/file access starts inside the journal's IO operation. */
    open fun openReportStatusChangeJournal(binding: ReportAdministrationBinding, access: ReportStatusChangeAccess): ReportStatusChangeJournal =
        buildReportStatusChangeJournal({ this }, binding, access)

    protected fun buildReportStatusChangeJournal(
        context: () -> android.content.Context,
        binding: ReportAdministrationBinding,
        access: ReportStatusChangeAccess,
    ): ReportStatusChangeJournal = object : ReportStatusChangeJournal {
        private val journal: ReportStatusChangeJournal by lazy {
            binding.checkStatusChangeAccess(access)
            ReportStatusChangeStore(context(), binding.scope, { binding.checkStatusChangeAccess(access) })
                .also { binding.checkStatusChangeAccess(access) }
        }
        override suspend fun read(): ReportStatusChangeRequest? = withContext(Dispatchers.IO) { journal.read() }
        override suspend fun retain(request: ReportStatusChangeRequest): Unit = withContext(Dispatchers.IO) { journal.retain(request) }
        override suspend fun clear(expected: ReportStatusChangeRequest): Unit = withContext(Dispatchers.IO) { journal.clear(expected) }
        override suspend fun <T> exclusive(block: suspend () -> T): T = withContext(Dispatchers.IO) { journal.exclusive(block) }
    }

    override fun onCreate() {
        super.onCreate()
        // Notifications carry no private payload. Retire our own summary on a new process
        // or identity transition without acknowledging events or changing preferences.
        val publisher = AndroidReportAlertsPublisher(this)
        runCatching(publisher::cancel)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            accountSession.state.drop(1).collect { runCatching(publisher::cancel) }
        }
    }

    open fun openReportAlertsRepository(session: AccountSessionState, checkCurrent: () -> Unit): ReportAlertsRepository {
        val checkScope = {
            check(accountSession.isCurrent(session)) { "La sesión de estos avisos terminó." }
            checkCurrent()
        }
        checkScope()
        return ReportAlertsStore(this, session.account?.id, checkAccess = checkScope)
    }

    open fun openTrustedContactsRepository(session: AccountSessionState, checkCurrent: () -> Unit): TrustedContactsRepository {
        val checkScope = {
            check(accountSession.isCurrent(session)) { "La sesión de estos contactos terminó." }
            checkCurrent()
        }
        checkScope()
        return TrustedContactsStore(this, session.account?.id, checkScope)
    }

    /** Every Activity captures its own owner and GPS evidence before creating any report model. */
    open fun openReportWorkspace(session: AccountSessionState): ReportWorkspace {
        check(accountSession.isCurrent(session)) { "La sesión cambió antes de abrir los reportes." }
        val evidence = PhoneLocationEvidence(phoneLocationSource::hasPrecisePermission,
            phoneLocationSource::isLocationEnabled)
        val refresh = PhoneLocationRefresh(phoneLocationSource, evidence, SystemClock::elapsedRealtimeNanos)
        return buildReportWorkspace(session, refresh::refresh, evidence::currentFix)
    }

    /** Test applications may inject their phone source; production always uses native location. */
    protected fun buildReportWorkspace(
        session: AccountSessionState,
        refreshLocation: suspend () -> PhoneLocationRefreshResult,
        currentLocation: () -> DeviceLocationFix?,
    ): ReportWorkspace {
        val checkSession = {
            check(accountSession.isCurrent(session)) { "La sesión de estos reportes terminó." }
        }
        checkSession()
        val names = ReportStorageNames.fromAccountId(session.account?.id)
        val administrationScope = session.account?.id?.let {
            com.example.citizensecurity.domain.ReportStatusChangeScope(it, it)
        }
        val administrationPolicy by lazy { openLocalAdministratorStore().policy() }
        val photos = ReportPhotoStore(this, names.photoDirectoryName, checkSession)
        val guard = NearbySqliteReportGuard(deviceLocation = {
            checkSession()
            currentLocation().also { checkSession() }
        }, elapsedRealtimeNanos = SystemClock::elapsedRealtimeNanos)
        val repository = SqliteReportRepository(this, names.databaseName, authorAccountId = session.account?.id,
            statusAuthority = {
                checkSession()
                val scope = administrationScope ?: throw com.example.citizensecurity.domain.ReportStatusPermissionDeniedException()
                try { administrationPolicy.check(scope, com.example.citizensecurity.domain.ReportAdministrationPermission.CHANGE_STATUS) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    throw com.example.citizensecurity.domain.ReportStatusPermissionDeniedException()
                }
                checkSession()
                scope.actorAccountId
            }, createGuard = { draft ->
            checkSession()
            guard.check(draft)
            draft.photo?.let { photo ->
                try { photos.check(photo) } catch (_: IOException) {
                    throw InvalidReportException(ValidationErrors(mapOf(ReportField.PHOTO to
                        "La fotografía no está disponible o está dañada. Vuelve a elegirla o quítala antes de guardar.")))
                }
            }
            checkSession()
        })
        return ReportWorkspace(session, accountSession, ReportWorkspaceStorage(repository, repository,
            repository, repository, ReportSubmissionStore(this, names.submissionFileName),
            ReportDraftCheckpointStore(this, names.checkpointFileName), photos, repository::close,
            statusChangeAccess = { scope, policy -> SqliteReportStatusChangeAccess(scope, repository, policy) }),
            refreshLocation, currentLocation)
    }

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
    open val reportListPageRepository: ReportListPageRepository get() = sharedReportRepository
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
