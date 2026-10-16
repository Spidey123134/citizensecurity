package com.example.citizensecurity.administration

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.report.ReportStatusRecoveryViewModel
import com.example.citizensecurity.alerts.ReportAlertsActivity
import com.example.citizensecurity.reports.ReportActivityActivity
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Private route; Intent carries only a folio hint, never a reviewed command or authority. */
class AdministrationStatusChangeActivity : ReportWorkspaceActivity() {
    internal lateinit var preparation: AdministrationStatusChangeViewModel
        private set
    internal lateinit var recovery: ReportStatusRecoveryViewModel
        private set
    private lateinit var route: AdministrationStatusRouteLease
    private val owner = Any()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        val app = application as CitizenSecurityApplication
        val binding = try { app.openReportAdministration(reportWorkspace) } catch (_: Exception) {
            setContent { PreviewTheme { Text("Inicia sesión con la cuenta administrativa local para continuar.") } }
            return
        }
        route = ViewModelProvider(this, viewModelFactory { initializer {
            AdministrationStatusRouteLease(reportWorkspace, app.getSystemService(KeyguardManager::class.java))
        } })[AdministrationStatusRouteLease::class.java]
        val access = binding.openStatusChangeAccess(route::checkWrite)
        val journal = app.openReportStatusChangeJournal(binding, access)
        recovery = ViewModelProvider(this, ReportStatusRecoveryViewModel.factory(journal, access,
            checkSession = reportWorkspace::checkActive))[ReportStatusRecoveryViewModel::class.java]
        preparation = ViewModelProvider(this, AdministrationStatusChangeViewModel.factory(binding,
            reportWorkspace::checkActive))[AdministrationStatusChangeViewModel::class.java]
        val folio = intent.getStringExtra(EXTRA_REPORT_ID)?.takeIf {
            runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false)
        }
        setContent { PreviewTheme {
            AdministrationStatusChangeScreen(preparation, recovery, folio, { route.canApply(owner) }, ::finish,
                onAlerts = { openAccountRoute(ReportAlertsActivity::class.java) },
                onActivity = { openAccountRoute(ReportActivityActivity::class.java) })
        } }
    }

    override fun onResume() {
        super.onResume()
        if (!::route.isInitialized || !hasReportWorkspace) return
        route.resume(owner)
        if (::preparation.isInitialized) preparation.checkAccess()
        if (::recovery.isInitialized) recovery.checkAccess()
    }

    override fun onPause() {
        if (::route.isInitialized) route.pause(owner)
        super.onPause()
    }

    /** Destinations reread this captured account. Navigation never consumes the durable receipt. */
    private fun openAccountRoute(destination: Class<out android.app.Activity>) {
        if (::route.isInitialized && route.canApply(owner) && ensureReportWorkspaceCurrent()) {
            startReportActivity(Intent(this, destination))
        }
    }

    companion object { const val EXTRA_REPORT_ID = "administration.report_id" }
}

/** Retained over rotation. An old Activity's pause/gesture cannot authorize the new window. */
internal class AdministrationStatusRouteLease(
    private val workspace: ReportWorkspace,
    private val keyguard: KeyguardManager,
) : ViewModel() {
    private val resumed = AtomicReference<Any?>()
    fun resume(owner: Any) { workspace.checkActive(); resumed.set(owner) }
    fun pause(owner: Any) { resumed.compareAndSet(owner, null) }
    fun checkWrite() {
        workspace.checkActive()
        val current = checkNotNull(resumed.get()) { "Vuelve a la pantalla desbloqueada para continuar." }
        check(!keyguard.isKeyguardLocked) { "Desbloquea el dispositivo para continuar." }
        check(resumed.get() === current) { "La pantalla activa cambió. Comprueba la solicitud al volver." }
        workspace.checkActive()
    }
    fun canApply(owner: Any): Boolean = try {
        checkWrite()
        resumed.get() === owner
    } catch (_: Exception) { false }
    override fun onCleared() { resumed.set(null) }
}
