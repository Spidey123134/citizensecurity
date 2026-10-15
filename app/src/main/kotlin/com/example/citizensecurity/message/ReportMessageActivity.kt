package com.example.citizensecurity.message

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.account.ReportWorkspaceActivity
import com.example.citizensecurity.account.ReportWorkspaceActivityBinding
import com.example.citizensecurity.help.HelpContactsActivity
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.reports.ReportsActivity
import kotlinx.coroutines.CancellationException

class ReportMessageActivity : ReportWorkspaceActivity() {
    private lateinit var model: ReportMessageViewModel
    private var folio = ""
    private val contactsRoute = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (::model.isInitialized && hasReportWorkspace) model.load(folio)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        folio = intent.getStringExtra(ReportsActivity.EXTRA_FOLIO).orEmpty()
        val contacts = (application as CitizenSecurityApplication).openTrustedContactsRepository(
            reportWorkspace.session, reportWorkspace::checkActive)
        model = ViewModelProvider(this, ReportMessageViewModel.factory(
            reportWorkspace.repository, contacts, reportWorkspace::checkActive))[ReportMessageViewModel::class.java]
        setContent { PreviewTheme {
            ReportMessageScreen(model,
                onSelect = { shown, id -> act { model.selectContact(shown, id) } },
                onLocation = { shown, selected -> act { model.setIncludeLocation(shown, selected) } },
                onPrepare = { shown -> act { model.prepare(shown) } },
                onEdit = { shown -> act { model.edit(shown) } },
                onOpen = ::openComposer,
                onReload = { act { model.load(folio) } },
                onContacts = { act {
                    contactsRoute.launch(ReportWorkspaceActivityBinding.stamp(
                        Intent(this, HelpContactsActivity::class.java), reportWorkspace.session))
                } },
                onClose = { act { finish() } })
        } }
        model.activate(folio)
    }

    override fun onResume() {
        super.onResume()
        if (::model.isInitialized && hasReportWorkspace) model.activate(folio)
    }

    override fun onStop() {
        if (::model.isInitialized && !isChangingConfigurations && model.state.value is ReportMessageState.Opening) {
            model.cancel()
        }
        super.onStop()
    }

    private fun canAct() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && ensureReportWorkspaceCurrent()
    private fun act(action: () -> Unit) { if (canAct()) action() }

    private fun openComposer(review: ReportMessageState.Review) = act {
        model.open(review, ::canAct) { draft ->
            val outbound = ReportMessageComposer.intent(draft)
            reportWorkspace.checkActive()
            if (!canAct()) throw CancellationException("La pantalla ya no está disponible.")
            startActivity(outbound)
        }
    }
}
