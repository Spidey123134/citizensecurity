package com.example.citizensecurity.help

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.account.LocalReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.data.TrustedContactsStore
import com.example.citizensecurity.domain.TrustedContact
import com.example.citizensecurity.preview.PreviewTheme

class HelpContactsActivity : ReportWorkspaceActivity() {
    private lateinit var contacts: HelpContactsViewModel
    private var dialMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        val workspace = reportWorkspace
        val store = TrustedContactsStore(this, workspace.session.account?.id, workspace::checkActive)
        contacts = ViewModelProvider(this, HelpContactsViewModel.factory(store, workspace::checkActive))[
            HelpContactsViewModel::class.java]
        setContent {
            CompositionLocalProvider(LocalReportWorkspace provides workspace) {
                PreviewTheme {
                    HelpContactsScreen(contacts, dialMessage, workspace.session.account == null,
                        onEmergencyDial = ::openDialer,
                        onContactDial = { contact, shown ->
                            if (contacts.state.value === shown && shown.snapshot.contacts.contains(contact)) {
                                dialContact(contact)
                            }
                        },
                        onClose = ::finish,
                        checkGesture = {
                            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) false
                            else runCatching { workspace.checkActive(); true }.getOrDefault(false)
                        },
                    )
                }
            }
        }
    }

    private fun dialContact(contact: TrustedContact) = openDialer(contact.phoneNumber)

    private fun openDialer(number: String) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !ensureReportWorkspaceCurrent()) return
        try {
            reportWorkspace.checkActive()
            val intent = HelpDialer.intent(number)
            reportWorkspace.checkActive()
            startActivity(intent)
            dialMessage = "Se abrió el marcador. Revisa el número y pulsa llamar si deseas hacerlo."
        } catch (_: ActivityNotFoundException) {
            dialMessage = "No hay una aplicación de teléfono disponible. Marca el número desde otro teléfono."
        } catch (_: Exception) {
            dialMessage = "No se pudo abrir el marcador. Puedes marcar el número desde tu teléfono."
        }
    }
}
