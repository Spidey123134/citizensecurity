package com.example.citizensecurity.help

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citizensecurity.account.LocalReportWorkspace
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.account.canInteract
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.preview.PreviewTheme

class HelpContactsActivity : ReportWorkspaceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        setContent {
            PreviewTheme {
                Surface(Modifier.fillMaxSize()) {
                    HelpContactsRoute(application as CitizenSecurityApplication, reportWorkspace, ::finish)
                }
            }
        }
    }
}

@Composable
internal fun HelpContactsRoute(
    app: CitizenSecurityApplication,
    workspace: ReportWorkspace,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val contacts: HelpContactsViewModel = viewModel(factory = remember(app, workspace) {
        val store = app.openTrustedContactsRepository(workspace.session, workspace::checkActive)
        HelpContactsViewModel.factory(store, workspace::checkActive)
    })
    var dialMessage by rememberSaveable { mutableStateOf<String?>(null) }

    fun openDialer(number: String) {
        if (!workspace.canInteract(lifecycle)) return
        try {
            val intent = HelpDialer.intent(number)
            workspace.checkActive()
            context.startActivity(intent)
            dialMessage = "Se abrió el marcador. Revisa el número y pulsa llamar si deseas hacerlo."
        } catch (_: ActivityNotFoundException) {
            dialMessage = "No hay una aplicación de teléfono disponible. Marca el número desde otro teléfono."
        } catch (_: Exception) {
            dialMessage = "No se pudo abrir el marcador. Puedes marcar el número desde tu teléfono."
        }
    }

    CompositionLocalProvider(LocalReportWorkspace provides workspace) {
        HelpContactsScreen(
            model = contacts,
            dialMessage = dialMessage,
            academic = workspace.session.account == null,
            onEmergencyDial = ::openDialer,
            onContactDial = { contact, shown ->
                if (contacts.state.value === shown && shown.snapshot.contacts.contains(contact)) {
                    openDialer(contact.phoneNumber)
                }
            },
            onClose = onBack,
            checkGesture = { workspace.canInteract(lifecycle) },
        )
    }
}
