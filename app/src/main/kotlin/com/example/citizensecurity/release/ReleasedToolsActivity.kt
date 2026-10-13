package com.example.citizensecurity.release

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
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
class ReleasedToolsActivity : ComponentActivity() {
    private val homeModel: ReportHomeViewModel by lazy {
        val app = application as CitizenSecurityApplication
        ViewModelProvider(this, ReportHomeViewModel.factory(
            app.reportSubmissionJournal, app.reportDraftCheckpointJournal,
        ))[ReportHomeViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_released_tools)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<Button>(R.id.tools_preview_interface).setOnClickListener {
            startActivity(Intent(this, ReportDraftActivity::class.java))
        }
        findViewById<Button>(R.id.tools_reports).setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        findViewById<Button>(R.id.tools_reports_map).setOnClickListener {
            startActivity(Intent(this, ReportsMapActivity::class.java))
        }
        findViewById<Button>(R.id.tools_back).setOnClickListener { closeSession() }
        onBackPressedDispatcher.addCallback(this) { closeSession() }
        findViewById<Button>(R.id.home_retry).setOnClickListener { homeModel.refresh() }
        render(homeModel.state.value)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeModel.state.collect(::render)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val account = (application as CitizenSecurityApplication).accountSession.account
        findViewById<TextView>(R.id.home_account).text = if (account == null)
            getString(R.string.account_home_guest) else getString(R.string.account_home_signed_in, account.username)
        findViewById<Button>(R.id.tools_back).setText(if (account == null) R.string.home_back else R.string.account_sign_out)
        // Always supersede a read that could have started before another screen changed a file.
        homeModel.refresh()
    }

    private fun closeSession() {
        (application as CitizenSecurityApplication).accountSession.signOut()
        finish()
    }

    private fun render(state: ReportHomeState) {
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
