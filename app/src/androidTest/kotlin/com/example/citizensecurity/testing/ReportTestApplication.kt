package com.example.citizensecurity.testing

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.account.AccountSessionState
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceStorage
import com.example.citizensecurity.data.NearbySqliteReportGuard
import com.example.citizensecurity.data.PhoneLocationRefreshResult
import com.example.citizensecurity.data.ReportSubmissionStore
import com.example.citizensecurity.data.ReportPhotoStore
import com.example.citizensecurity.data.ReportDraftCheckpointStore
import com.example.citizensecurity.data.ReportAlertsStore
import com.example.citizensecurity.data.TrustedContactsStore
import com.example.citizensecurity.domain.ReportAlertsRepository
import com.example.citizensecurity.domain.TrustedContactsRepository
import com.example.citizensecurity.domain.ReportDraftCheckpointJournal
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ValidationErrors
import java.io.IOException
import java.io.File
import com.example.citizensecurity.data.SqliteReportRepository
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.IdempotentReportRepository
import com.example.citizensecurity.domain.AccountRepository
import com.example.citizensecurity.domain.ReportMapRepository
import com.example.citizensecurity.domain.ReportListPageRepository
import com.example.citizensecurity.domain.SimilarReportRepository
import com.example.citizensecurity.domain.ReportSubmissionJournal
import java.util.UUID
import org.junit.rules.ExternalResource

/** Delegates to the real application until a test explicitly opts into its isolated fixture. */
class ReportTestApplication : CitizenSecurityApplication() {
    var accountTestRepository: AccountRepository? = null
    override val accountRepository: AccountRepository
        get() = accountTestRepository ?: super.accountRepository
    @Volatile private var testFixture: ReportTestFixture? = null

    override fun openReportWorkspace(session: AccountSessionState): ReportWorkspace {
        val fixture = testFixture ?: return super.openReportWorkspace(session)
        // Account scopes retain real named production stores. Only the phone source is controlled.
        if (session.account != null) return buildReportWorkspace(session, fixture::refresh, fixture::currentFix)
        return ReportWorkspace(session, accountSession, ReportWorkspaceStorage(fixture.repository,
            fixture.repository, fixture.repository, fixture.similarLookup ?: fixture.repository,
            fixture.journal, fixture.checkpoint, fixture.photoStore), fixture::refresh, fixture::currentFix)
    }

    override fun openReportAlertsRepository(session: AccountSessionState, checkCurrent: () -> Unit): ReportAlertsRepository {
        val fixture = testFixture ?: return super.openReportAlertsRepository(session, checkCurrent)
        val checkScope = {
            check(accountSession.isCurrent(session)) { "This test's captured account session has ended" }
            checkCurrent()
        }
        checkScope()
        return ReportAlertsStore(this, session.account?.id, checkAccess = checkScope,
            filenameOverride = fixture.alertsFileNameFor(session.account?.id))
    }

    override fun openTrustedContactsRepository(session: AccountSessionState, checkCurrent: () -> Unit): TrustedContactsRepository {
        val fixture = testFixture ?: return super.openTrustedContactsRepository(session, checkCurrent)
        val checkScope = {
            check(accountSession.isCurrent(session)) { "This test's captured account session has ended" }
            checkCurrent()
        }
        checkScope()
        return TrustedContactsStore(fixture.contactsContext, session.account?.id, checkScope)
    }

    override val reportRepository: IdempotentReportRepository
        get() = testFixture?.repository ?: super.reportRepository
    override val reportListPageRepository: ReportListPageRepository
        get() = testFixture?.repository ?: super.reportListPageRepository
    override val reportMapRepository: ReportMapRepository
        get() = testFixture?.repository ?: super.reportMapRepository
    override val similarReportRepository: SimilarReportRepository
        get() = testFixture?.let { it.similarLookup ?: it.repository } ?: super.similarReportRepository
    override val reportSubmissionJournal: ReportSubmissionJournal
        get() = testFixture?.journal ?: super.reportSubmissionJournal
    override val reportPhotoStore: ReportPhotoStore
        get() = testFixture?.photoStore ?: super.reportPhotoStore
    override val reportDraftCheckpointJournal: ReportDraftCheckpointJournal
        get() = testFixture?.checkpoint ?: super.reportDraftCheckpointJournal

    override fun currentReportPhoneLocation(): DeviceLocationFix? =
        testFixture?.currentFix() ?: if (testFixture == null) super.currentReportPhoneLocation() else null

    override suspend fun refreshReportPhoneLocation(): PhoneLocationRefreshResult =
        testFixture?.refresh() ?: super.refreshReportPhoneLocation()

    fun enableFixture(): ReportTestFixture {
        check(testFixture == null) { "A report fixture is already active" }
        return ReportTestFixture(this).also { testFixture = it }
    }

    fun disableFixture(fixture: ReportTestFixture) {
        check(testFixture === fixture) { "The active fixture belongs to another test" }
        testFixture = null
        fixture.repository.close()
    }
}

/**
 * Real, uniquely named SQLite and AtomicFile stores. Only the phone reading is a controlled
 * CDMX fixture, and it is available only while Android's actual precise permission/GPS allow it.
 * This acceptance harness is not evidence of physical GPS or spoof detection.
 */
class ReportTestFixture internal constructor(private val context: Context) {
    private val identity = UUID.randomUUID().toString()
    val databaseName = "reports_ui_$identity.db"
    val journalName = "submission_ui_$identity.json"
    val journal = ReportSubmissionStore(context, journalName)
    val photoStore = ReportPhotoStore(context, "photos_ui_$identity")
    val checkpoint = ReportDraftCheckpointStore(context, "draft_ui_$identity.json")
    fun alertsFileNameFor(ownerAccountId: String?) = "report_alerts_test_${identity}_${ownerAccountId ?: "academic"}.json"
    /** Retains the real owner file names without touching this emulator's other contact stores. */
    val contactsContext: Context by lazy {
        val parent = context.filesDir.canonicalFile
        val directory = File(parent, "trusted_contacts_ui_$identity").canonicalFile
        check(directory.parentFile == parent) { "Contact fixture escaped its private parent" }
        check(directory.isDirectory || directory.mkdir()) { "Cannot create the owned contact fixture" }
        object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
    }
    var similarLookup: SimilarReportRepository? = null
    @Volatile private var published: DeviceLocationFix? = null
    @Volatile var refreshCalls: Int = 0
        private set

    private val guard = NearbySqliteReportGuard(::currentFix, SystemClock::elapsedRealtimeNanos)
    val repository = SqliteReportRepository(context, databaseName, createGuard = { draft ->
        guard.check(draft)
        draft.photo?.let {
            try { photoStore.check(it) } catch (_: IOException) {
                throw InvalidReportException(ValidationErrors(mapOf(ReportField.PHOTO to "La fotografía está dañada. Vuelve a elegirla o quítala.")))
            }
        }
    })

    fun currentFix(): DeviceLocationFix? {
        if (!preciseAllowed() || !locationEnabled()) {
            published = null
            return null
        }
        return published
    }

    fun publishFreshFix(): DeviceLocationFix {
        check(preciseAllowed()) { "Fixture requires Android's actual precise permission" }
        check(locationEnabled()) { "Fixture requires Android's actual location service" }
        return DeviceLocationFix(
            latitude = LATITUDE,
            longitude = LONGITUDE,
            accuracyMeters = 20.0,
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            isMock = false,
        ).also { published = it }
    }

    fun expireFix() {
        published = published?.copy(elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() - 121_000_000_000L)
    }

    fun clearFix() { published = null }

    fun refresh(): PhoneLocationRefreshResult {
        ++refreshCalls
        published = null
        if (!preciseAllowed()) return PhoneLocationRefreshResult.PermissionRequired
        if (!locationEnabled()) return PhoneLocationRefreshResult.LocationDisabled
        return PhoneLocationRefreshResult.Ready(publishFreshFix())
    }

    private fun preciseAllowed() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    private fun locationEnabled() = context.getSystemService(LocationManager::class.java).isLocationEnabled

    companion object {
        const val LATITUDE = 19.4326
        const val LONGITUDE = -99.1332
    }
}

/** Runs only on the disposable read-only emulator and restores its location setting afterwards. */
class ReportTestFixtureRule : ExternalResource() {
    lateinit var fixture: ReportTestFixture
        private set
    private lateinit var application: ReportTestApplication
    private var previousLocationMode = ""

    override fun before() {
        application = ApplicationProvider.getApplicationContext()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        previousLocationMode = shell("settings get secure location_mode").trim()
        if (!granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            automation.grantRuntimePermission(application.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            automation.grantRuntimePermission(application.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        shell("settings put secure location_mode 3")
        check(granted(Manifest.permission.ACCESS_FINE_LOCATION)) { "Precise permission was not granted by Android" }
        check(application.getSystemService(LocationManager::class.java).isLocationEnabled) { "Android location is disabled" }
        fixture = application.enableFixture()
    }

    override fun after() {
        try {
            if (::fixture.isInitialized) application.disableFixture(fixture)
        } finally {
            if (::application.isInitialized) {
                // Android revocation kills the process under instrumentation. The executor restores
                // permissions by discarding its read-only emulator after the suite, not from this rule.
                if (previousLocationMode in listOf("0", "1", "2", "3")) {
                    shell("settings put secure location_mode $previousLocationMode")
                }
            }
        }
    }

    private fun granted(permission: String) = application.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    }
}
