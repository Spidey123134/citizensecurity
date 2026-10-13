package com.example.citizensecurity.account

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.MainActivity
import java.util.UUID
import kotlinx.coroutines.launch

/** A routing stamp, never a credential or permission to open a different owner's files. */
internal data class ReportWorkspaceStamp(val owner: String, val processId: String, val revision: Long) {
    fun matches(current: AccountSessionState): Boolean {
        if (owner != ownerOf(current) || revision < 0L || !canonicalUuid(processId)) return false
        // The academic workspace has no account identity to recover after process death.
        if (processId != current.processId) return owner == ACADEMIC && current.account == null
        return revision == current.revision
    }

    companion object {
        const val ACADEMIC = "academic"
        fun from(session: AccountSessionState) = ReportWorkspaceStamp(ownerOf(session), session.processId, session.revision)
        private fun ownerOf(session: AccountSessionState) = session.account?.id ?: ACADEMIC
        private fun canonicalUuid(value: String): Boolean = runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
    }
}

internal val LocalReportWorkspace = staticCompositionLocalOf<ReportWorkspace?> { null }

/** Check routing before Android hands saved UI/model state to a report Activity. */
internal object ReportWorkspaceActivityBinding {
    private const val OWNER = "report_workspace.owner"
    private const val PROCESS = "report_workspace.process"
    private const val REVISION = "report_workspace.revision"

    fun stamp(intent: Intent, session: AccountSessionState): Intent = intent.apply {
        val stamp = ReportWorkspaceStamp.from(session)
        putExtra(OWNER, stamp.owner)
        putExtra(PROCESS, stamp.processId)
        putExtra(REVISION, stamp.revision)
    }

    fun stamp(state: Bundle, session: AccountSessionState) {
        val stamp = ReportWorkspaceStamp.from(session)
        state.putString(OWNER, stamp.owner)
        state.putString(PROCESS, stamp.processId)
        state.putLong(REVISION, stamp.revision)
    }

    fun accepts(current: AccountSessionState, intent: Intent, savedState: Bundle?): Boolean = runCatching {
        val extras = intent.extras
        val intentStamped = extras != null && hasAnyStamp(extras)
        val route = if (intentStamped) readStamp(extras) ?: return false else null
        if (route != null && !route.matches(current)) return false
        if (savedState != null) {
            val saved = readStamp(savedState) ?: return false
            if (!saved.matches(current) || route != null && saved != route) return false
        }
        true
    }.getOrDefault(false)

    private fun hasAnyStamp(state: Bundle) = state.containsKey(OWNER) || state.containsKey(PROCESS) || state.containsKey(REVISION)
    private fun readStamp(state: Bundle): ReportWorkspaceStamp? {
        if (!state.containsKey(OWNER) || !state.containsKey(PROCESS) || !state.containsKey(REVISION)) return null
        val owner = state.getString(OWNER) ?: return null
        val process = state.getString(PROCESS) ?: return null
        val revision = state.getLong(REVISION, -1L)
        return ReportWorkspaceStamp(owner, process, revision)
    }
}

/** Retains one fixed workspace over rotation; never rebinds an old screen to a new session. */
abstract class ReportWorkspaceActivity : ComponentActivity() {
    protected lateinit var reportWorkspace: ReportWorkspace
        private set
    private var revoked = false
    protected val hasReportWorkspace: Boolean
        get() = ::reportWorkspace.isInitialized && !revoked &&
            (application as CitizenSecurityApplication).accountSession.isCurrent(reportWorkspace.session)

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as CitizenSecurityApplication
        val session = app.accountSession.capture()
        val accepted = ReportWorkspaceActivityBinding.accepts(session, intent, savedInstanceState)
        super.onCreate(if (accepted) savedInstanceState else null)
        if (!accepted || !app.accountSession.isCurrent(session)) {
            // A restored account screen with no live account must return to login. Live
            // replacements merely retire their old screens; they must not clear a new task.
            revokeWorkspace(redirectToLogin = app.accountSession.account == null)
            return
        }
        ReportWorkspaceActivityBinding.stamp(intent, session)
        val factory = viewModelFactory { initializer { WorkspaceHolder(app.openReportWorkspace(session)) } }
        val key = "report-workspace:${session.processId}:${session.revision}:${session.account?.id.orEmpty()}"
        reportWorkspace = ViewModelProvider(this, factory)[key, WorkspaceHolder::class.java].workspace
        if (!ensureReportWorkspaceCurrent()) return
        lifecycleScope.launch {
            app.accountSession.state.collect {
                if (!app.accountSession.isCurrent(reportWorkspace.session)) revokeWorkspace()
            }
        }
    }

    override fun onStart() {
        ensureReportWorkspaceCurrent()
        super.onStart()
    }

    override fun onResume() {
        ensureReportWorkspaceCurrent()
        super.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::reportWorkspace.isInitialized) ReportWorkspaceActivityBinding.stamp(outState, reportWorkspace.session)
        super.onSaveInstanceState(outState)
    }

    protected fun ensureReportWorkspaceCurrent(): Boolean {
        if (!hasReportWorkspace) {
            revokeWorkspace()
            return false
        }
        return true
    }

    protected fun startReportActivity(intent: Intent) {
        if (ensureReportWorkspaceCurrent()) startActivity(ReportWorkspaceActivityBinding.stamp(intent, reportWorkspace.session))
    }

    private fun revokeWorkspace(redirectToLogin: Boolean = false) {
        if (revoked) return
        revoked = true
        // Detach Compose and native views immediately, before a new owner can see their state.
        setContentView(FrameLayout(this))
        viewModelStore.clear()
        if (redirectToLogin) startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private class WorkspaceHolder(val workspace: ReportWorkspace) : ViewModel() {
        override fun onCleared() { workspace.close() }
    }
}
