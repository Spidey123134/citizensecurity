package com.example.citizensecurity.message

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.account.canInteract
import com.example.citizensecurity.help.HelpContactsActivity
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.reports.ReportsActivity
import kotlinx.coroutines.CancellationException

class ReportMessageActivity : ReportWorkspaceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        val folio = intent.getStringExtra(ReportsActivity.EXTRA_FOLIO).orEmpty()
        setContent {
            PreviewTheme {
                ReportMessageRoute(
                    app = application as CitizenSecurityApplication,
                    workspace = reportWorkspace,
                    folio = folio,
                    onBack = ::finish,
                    onOpenContacts = { startReportActivity(Intent(this, HelpContactsActivity::class.java)) },
                )
            }
        }
    }
}

@Composable
internal fun ReportMessageRoute(
    app: CitizenSecurityApplication,
    workspace: ReportWorkspace,
    folio: String,
    onBack: () -> Unit,
    onOpenContacts: () -> Unit,
) {
    val context = LocalContext.current
    val hostActivity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val model: ReportMessageViewModel = viewModel(factory = remember(app, workspace) {
        val contacts = app.openTrustedContactsRepository(workspace.session, workspace::checkActive)
        ReportMessageViewModel.factory(workspace.repository, contacts, workspace::checkActive)
    })
    var reloadContactsOnResume by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (workspace.canInteract(lifecycle)) {
            if (reloadContactsOnResume) {
                reloadContactsOnResume = false
                model.load(folio)
            } else {
                model.activate(folio)
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (hostActivity?.isChangingConfigurations != true && model.state.value is ReportMessageState.Opening) {
            model.cancel()
        }
    }

    fun canAct() = workspace.canInteract(lifecycle)
    fun act(action: () -> Unit) {
        if (canAct()) action()
    }

    ReportMessageScreen(
        model = model,
        onSelect = { shown, id -> act { model.selectContact(shown, id) } },
        onLocation = { shown, selected -> act { model.setIncludeLocation(shown, selected) } },
        onPrepare = { shown -> act { model.prepare(shown) } },
        onEdit = { shown -> act { model.edit(shown) } },
        onOpen = { review ->
            act {
                model.open(review, ::canAct) { draft ->
                    val outbound = ReportMessageComposer.intent(draft)
                    workspace.checkActive()
                    if (!canAct()) throw CancellationException("La pantalla ya no está disponible.")
                    context.startActivity(outbound)
                }
            }
        },
        onReload = { act { model.load(folio) } },
        onContacts = {
            act {
                reloadContactsOnResume = true
                onOpenContacts()
            }
        },
        onClose = { act(onBack) },
    )
}
