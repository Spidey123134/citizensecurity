package com.example.citizensecurity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.account.AccountSessionState
import com.example.citizensecurity.account.ReportWorkspaceActivityBinding
import com.example.citizensecurity.preview.PreviewTheme
import com.example.citizensecurity.report.ReportQueryViewModel
import com.example.citizensecurity.report.ReportViewModel

open class MainActivity : ComponentActivity() {
    protected open val showLoading = false
    private lateinit var boundSession: AccountSessionState

    val reportViewModel: ReportViewModel by lazy {
        val dependencies = application as CitizenSecurityApplication
        ViewModelProvider(this, dependencies.reportViewModelFactory)[ReportViewModel::class.java]
    }

    val reportQueryViewModel: ReportQueryViewModel by lazy {
        val dependencies = application as CitizenSecurityApplication
        ViewModelProvider(this, dependencies.reportQueryViewModelFactory)[ReportQueryViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as CitizenSecurityApplication
        boundSession = app.accountSession.capture()
        val restoredState = savedInstanceState?.takeIf {
            ReportWorkspaceActivityBinding.accepts(boundSession, Intent(), it)
        }
        super.onCreate(restoredState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.BLACK))
        setContent {
            PreviewTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CitizenSecurityNavigation(
                        app = app,
                        showLoading = showLoading,
                        modifier = Modifier.safeDrawingPadding(),
                        initialSession = boundSession,
                        onSessionBound = { boundSession = it },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        ReportWorkspaceActivityBinding.stamp(outState, boundSession)
        super.onSaveInstanceState(outState)
    }
}
