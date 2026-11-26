package com.example.citizensecurity.alerts

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.account.canInteract
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.reports.ReportActivityActivity
import kotlinx.coroutines.flow.combine

class ReportAlertsActivity : ReportWorkspaceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        setContent {
            PreviewTheme {
                ReportAlertsRoute(
                    app = application as CitizenSecurityApplication,
                    workspace = reportWorkspace,
                    onBack = ::finish,
                    onOpenActivity = { startReportActivity(Intent(this, ReportActivityActivity::class.java)) },
                )
            }
        }
    }
}

@Composable
internal fun ReportAlertsRoute(
    app: CitizenSecurityApplication,
    workspace: ReportWorkspace,
    onBack: () -> Unit,
    onOpenActivity: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val publisher = remember(app) { AndroidReportAlertsPublisher(app) }
    val model: ReportAlertsViewModel = viewModel(factory = remember(app, workspace, publisher) {
        val preferences = app.openReportAlertsRepository(workspace.session, workspace::checkActive)
        ReportAlertsViewModel.factory(
            workspace.activityRepository, preferences, publisher, checkCurrent = workspace::checkActive,
        )
    })
    val permissionChoice: ReportAlertsPermissionChoice = viewModel()
    var resumedOnce by remember { mutableStateOf(false) }
    var permissionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (runCatching { workspace.checkActive() }.isSuccess) permissionChoice.complete(granted)
    }

    fun canAct() = workspace.canInteract(lifecycle)
    fun act(action: () -> Unit) {
        if (canAct()) action()
    }

    fun enableWithCurrentPermission() {
        if (!canAct() || model.busy.value) return
        if (publisher.availability() != ReportAlertsAvailability.ALLOWED) {
            permissionMessage = "Android bloquea estos avisos. Revisa los permisos y el canal en sus ajustes."
            model.load()
            return
        }
        try {
            workspace.checkActive()
            publisher.prepare()
            if (canAct()) model.setEnabled(true)
        } catch (_: Exception) {
            permissionMessage = "No se pudieron preparar los avisos. Tus reportes se conservan."
        }
    }

    fun requestEnable() {
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

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (canAct()) {
            if (permissionChoice.state.value == null && !model.busy.value) {
                if (resumedOnce) model.load() else model.activate()
            }
            resumedOnce = true
        }
    }
    LaunchedEffect(lifecycle, model, permissionChoice, publisher, workspace) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
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

    ReportAlertsScreen(
        model = model,
        permissionChoice = permissionChoice,
        permissionMessage = permissionMessage,
        onEnable = ::requestEnable,
        onDisable = { act { permissionMessage = null; model.setEnabled(false) } },
        onReview = { act { permissionMessage = null; model.review() } },
        onContinue = { act { model.continueReview() } },
        onReload = { act { permissionMessage = null; model.load() } },
        onSettings = {
            act {
                try {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    permissionMessage = "No se pudieron abrir los ajustes de avisos de Android."
                }
            }
        },
        onActivity = { act(onOpenActivity) },
        onClose = { act(onBack) },
    )
}
