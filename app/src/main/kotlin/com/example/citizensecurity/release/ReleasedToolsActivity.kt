package com.example.citizensecurity.release

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.citizensecurity.account.ReportWorkspaceActivity
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.R
import com.example.citizensecurity.preview.ReportDraftActivity
import com.example.citizensecurity.reports.ReportsActivity
import com.example.citizensecurity.reports.ReportsMapActivity
import kotlinx.coroutines.launch

/** Functional home. Opening or refreshing it only reads the private recovery journals. */
class ReleasedToolsActivity : ReportWorkspaceActivity() {
    private val homeModel: ReportHomeViewModel by lazy {
        ViewModelProvider(this, ReportHomeViewModel.factory(
            reportWorkspace.submissionJournal, reportWorkspace.checkpointJournal,
        ))[ReportHomeViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasReportWorkspace) return
        setContentView(R.layout.activity_released_tools)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<Button>(R.id.tools_preview_interface).setOnClickListener {
            startReportActivity(Intent(this, ReportDraftActivity::class.java))
        }
        findViewById<Button>(R.id.tools_reports).setOnClickListener {
            startReportActivity(Intent(this, ReportsActivity::class.java))
        }
        findViewById<Button>(R.id.home_activity).setOnClickListener {
            startReportActivity(Intent(this, com.example.citizensecurity.reports.ReportActivityActivity::class.java))
        }
        findViewById<Button>(R.id.tools_reports_map).setOnClickListener {
            startReportActivity(Intent(this, ReportsMapActivity::class.java))
        }
        findViewById<Button>(R.id.home_help).setOnClickListener {
            startReportActivity(Intent().setClassName(this, "$packageName.help.HelpContactsActivity"))
        }
        findViewById<Button>(R.id.home_quick_help).setOnClickListener {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && ensureReportWorkspaceCurrent()) {
                startActivity(com.example.citizensecurity.help.EmergencyHelpShortcut.entryIntent(this))
            }
        }
        findViewById<Button>(R.id.tools_back).setOnClickListener { closeSession() }
        onBackPressedDispatcher.addCallback(this) { closeSession() }
        findViewById<Button>(R.id.home_retry).setOnClickListener { if (ensureReportWorkspaceCurrent()) homeModel.refresh() }
        render(homeModel.state.value)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeModel.state.collect(::render)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!hasReportWorkspace) return
        val account = reportWorkspace.session.account
        findViewById<TextView>(R.id.home_account).text = if (account == null)
            getString(R.string.account_home_guest) else getString(R.string.account_home_signed_in, account.username)
        findViewById<Button>(R.id.tools_back).setText(if (account == null) R.string.home_back else R.string.account_sign_out)
        // Always supersede a read that could have started before another screen changed a file.
        homeModel.refresh()
    }

    private fun closeSession() {
        if (!ensureReportWorkspaceCurrent()) return
        (application as CitizenSecurityApplication).accountSession.signOutIfCurrent(reportWorkspace.session)
        finish()
    }

    private fun render(state: ReportHomeState) {
        if (!hasReportWorkspace) return
        val messages = if (state.loading) listOf(getString(R.string.home_checking)) else buildList {
            if (state.submissionReadFailed) add(getString(R.string.home_attempt_unknown))
            else if (state.pendingAttempt) add(getString(R.string.home_attempt_pending))
            if (state.draftReadFailed) add(getString(R.string.home_draft_unknown))
            else if (state.savedDraft) add(getString(
                if (state.draftHasFolio) R.string.home_draft_linked else R.string.home_draft_saved,
            ))
            if (isEmpty()) add(getString(R.string.home_ready))
        }
        findViewById<TextView>(R.id.home_status).text = messages.joinToString("\n\n")
        findViewById<Button>(R.id.home_retry).apply {
            visibility = if (!state.loading && (state.submissionReadFailed || state.draftReadFailed))
                View.VISIBLE else View.GONE
            isEnabled = !state.loading
        }
        findViewById<Button>(R.id.tools_preview_interface).setText(when {
            state.loading || state.submissionReadFailed || state.pendingAttempt -> R.string.home_open_report
            state.savedDraft && !state.draftHasFolio -> R.string.home_open_draft
            state.savedDraft -> R.string.home_open_report
            else -> R.string.home_new_report
        })
    }
}
