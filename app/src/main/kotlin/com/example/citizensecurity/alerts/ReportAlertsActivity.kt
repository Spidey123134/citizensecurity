package com.example.citizensecurity.alerts

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.reports.ReportActivityActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class ReportAlertsActivity : ReportWorkspaceActivity() {
    private lateinit var model: ReportAlertsViewModel
    private lateinit var publisher: AndroidReportAlertsPublisher
    private val permissionChoice by lazy { ViewModelProvider(this)[ReportAlertsPermissionChoice::class.java] }
    private var resumedOnce = false
    private var permissionMessage by mutableStateOf<String?>(null)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (::model.isInitialized && hasReportWorkspace) permissionChoice.complete(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        publisher = AndroidReportAlertsPublisher(applicationContext)
        val preferences = (application as CitizenSecurityApplication).openReportAlertsRepository(
            reportWorkspace.session, reportWorkspace::checkActive)
        model = ViewModelProvider(this, ReportAlertsViewModel.factory(
            reportWorkspace.activityRepository, preferences, publisher,
            checkCurrent = reportWorkspace::checkActive,
        ))[ReportAlertsViewModel::class.java]
        setContent { PreviewTheme {
            ReportAlertsScreen(model, permissionChoice, permissionMessage,
                onEnable = ::requestEnable,
                onDisable = { act { permissionMessage = null; model.setEnabled(false) } },
                onReview = { act { permissionMessage = null; model.review() } },
                onContinue = { act { model.continueReview() } },
                onReload = { act { permissionMessage = null; model.load() } },
                onSettings = ::openAndroidSettings,
                onActivity = { act { startReportActivity(Intent(this, ReportActivityActivity::class.java)) } },
                onClose = { act { finish() } })
        } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(model.state, model.busy, permissionChoice.state) { state, busy, choice -> Triple(state, busy, choice) }
                    .collect { (state, busy, choice) ->
                        if (choice?.granted == null || busy || state !is ReportAlertsState.Ready || !canAct()) return@collect
                        if (!permissionChoice.consume(choice)) return@collect
                        if (choice.granted && publisher.availability() == ReportAlertsAvailability.ALLOWED) {
                            enableWithCurrentPermission()
                        } else {
                            permissionMessage = "Android no autorizó los avisos. Puedes seguir consultando la actividad y revisar los ajustes."
                            model.load()
                        }
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::model.isInitialized || !hasReportWorkspace) return
        if (permissionChoice.state.value == null && !model.busy.value) {
            if (resumedOnce) model.load() else model.activate()
        }
        resumedOnce = true
    }

    private fun canAct() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && ensureReportWorkspaceCurrent()
    private fun act(action: () -> Unit) { if (canAct()) action() }

    private fun requestEnable() {
        if (!canAct() || model.busy.value || model.state.value !is ReportAlertsState.Ready ||
            permissionChoice.state.value != null) return
        permissionMessage = null
        if (Build.VERSION.SDK_INT >= 33 && publisher.availability() == ReportAlertsAvailability.RUNTIME_PERMISSION_DENIED) {
            if (!canAct() || !permissionChoice.begin()) return
            try { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
            catch (_: Exception) {
                permissionChoice.abort()
                permissionMessage = "No se pudo abrir la solicitud. Revisa los ajustes de Android."
            }
        } else enableWithCurrentPermission()
    }

    private fun enableWithCurrentPermission() {
        if (!canAct() || model.busy.value) return
        if (publisher.availability() != ReportAlertsAvailability.ALLOWED) {
            permissionMessage = "Android bloquea estos avisos. Revisa los permisos y el canal en sus ajustes."
            model.load()
            return
        }
        try {
            reportWorkspace.checkActive()
            publisher.prepare()
            if (!canAct()) return
            model.setEnabled(true)
        } catch (_: Exception) {
            permissionMessage = "No se pudieron preparar los avisos. Tus reportes se conservan."
        }
    }

    private fun openAndroidSettings() = act {
        try { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
        catch (_: ActivityNotFoundException) { permissionMessage = "No se pudieron abrir los ajustes de avisos de Android." }
    }
}
