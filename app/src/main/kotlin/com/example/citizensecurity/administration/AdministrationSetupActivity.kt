package com.example.citizensecurity.administration

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.preview.PreviewTheme
import java.util.concurrent.atomic.AtomicBoolean

/** A local owner enrollment, not an identity or permission granted by a username. */
class AdministrationSetupActivity : ReportWorkspaceActivity() {
    internal lateinit var model: AdministrationSetupViewModel
        private set
    private val keyguard get() = getSystemService(KeyguardManager::class.java)
    private val foreground = AtomicBoolean(false)
    private val credential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (::model.isInitialized) model.credentialResult(result.resultCode == RESULT_OK, keyguard.isDeviceSecure)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        val account = reportWorkspace.session.account
        val store = (application as CitizenSecurityApplication).openLocalAdministratorStore()
        model = ViewModelProvider(this, viewModelFactory { initializer {
            AdministrationSetupViewModel(account, reportWorkspace::checkActive, store::read,
                { operation, at, check -> store.assignFirst(checkNotNull(account), operation, at, check) },
                elapsedNanos = SystemClock::elapsedRealtimeNanos)
        } })[AdministrationSetupViewModel::class.java]
        setContent {
            PreviewTheme {
                val state by model.state.collectAsStateWithLifecycle()
                LaunchedEffect(model) { model.activate() }
                Scaffold { insets ->
                    Column(Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState())
                        .padding(20.dp).testTag("admin_setup_screen"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = ::finish, modifier = Modifier.testTag("admin_setup_close")) { Text("Volver") }
                        Text("Administración local", style = MaterialTheme.typography.headlineSmall)
                        Text("Cuenta elegida: admin_local. La contraseña se crea al registrar esa cuenta en este teléfono.")
                        Text("El propietario confirma el bloqueo de Android antes de activar los permisos. La consulta se limita al espacio local de esta cuenta.")
                        when (val current = state) {
                            AdministrationSetupState.Idle, AdministrationSetupState.Checking -> {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Comprobando la asignación…")
                            }
                            is AdministrationSetupState.Available -> {
                                current.message?.let { Text(it, Modifier.testTag("admin_setup_message")) }
                                Button(onClick = ::confirmOwner, modifier = Modifier.fillMaxWidth().testTag("admin_setup_confirm_owner")) {
                                    Text("Confirmar propietario con Android")
                                }
                            }
                            AdministrationSetupState.AwaitingCredential -> Text("Completa o cancela la verificación en Android.")
                            AdministrationSetupState.OwnerConfirmed -> {
                                Text("Propietario confirmado. Puedes activar la administración para admin_local.", Modifier.testTag("admin_setup_confirmed"))
                                Button(onClick = { model.activateAdministrator(::canApply) },
                                    modifier = Modifier.fillMaxWidth().testTag("admin_setup_activate")) { Text("Activar administración local") }
                            }
                            AdministrationSetupState.Assigning -> {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Confirmando la asignación…")
                            }
                            is AdministrationSetupState.Assigned -> {
                                Text("Administración activada para admin_local en este teléfono.", Modifier.testTag("admin_setup_assigned"))
                                Button(onClick = {
                                    if (canApply()) startReportActivity(Intent(this@AdministrationSetupActivity,
                                        AdministrationConsultationActivity::class.java))
                                }, modifier = Modifier.fillMaxWidth().testTag("admin_setup_consult")) { Text("Consultar reportes") }
                            }
                            is AdministrationSetupState.Blocked -> Text(current.message, Modifier.testTag("admin_setup_blocked"))
                            is AdministrationSetupState.Error -> {
                                Text(current.message, Modifier.testTag("admin_setup_error"))
                                OutlinedButton(onClick = { model.reload() }, modifier = Modifier.testTag("admin_setup_retry")) {
                                    Text("Comprobar asignación guardada")
                                }
                            }
                        }
                        Text("Los reportes siguen en este teléfono. Esta cuenta no representa a una institución ni recibe reportes de otros teléfonos.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); foreground.set(hasReportWorkspace) }
    override fun onPause() { foreground.set(false); super.onPause() }

    /** Also called from the store's IO guard: never mutate views or finish an Activity here. */
    private fun canApply(): Boolean = try {
        reportWorkspace.checkActive()
        val unlocked = keyguard.isDeviceSecure && !keyguard.isKeyguardLocked
        reportWorkspace.checkActive()
        unlocked && foreground.get()
    } catch (_: Exception) { false }

    /** Native credential Intent supports the project's API26 minimum without another library. */
    @Suppress("DEPRECATION")
    private fun confirmOwner() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !ensureReportWorkspaceCurrent()) return
        val attempt = model.beginCredential(keyguard.isDeviceSecure) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                check(packageManager.hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN))
            }
            val intent = checkNotNull(keyguard.createConfirmDeviceCredentialIntent(
                "Administración local", "Confirma al propietario para preparar la activación de admin_local."))
            val target = checkNotNull(packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)).activityInfo
            check(target.exported && target.applicationInfo.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
            intent.component = ComponentName(target.packageName, target.name)
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !ensureReportWorkspaceCurrent()) {
                model.launchFailed(attempt); return
            }
            credential.launch(intent)
        } catch (_: Exception) { model.launchFailed(attempt) }
    }
}
