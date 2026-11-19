package com.example.citizensecurity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.release.ReleasedToolsActivity
import com.example.citizensecurity.report.ReportViewModel
import com.example.citizensecurity.screen.CitizenSecurityTheme
import com.example.citizensecurity.screen.HomeScreen

class MainActivity : ComponentActivity() {
    val reportViewModel: ReportViewModel by lazy {
        val dependencies = application as CitizenSecurityApplication
        ViewModelProvider(this, dependencies.reportViewModelFactory)[ReportViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CitizenSecurityTheme {
                HomeScreen(onOpenTools = ::openTools)
            }
        }
    }

    private fun openTools() {
        startActivity(Intent(this, ReleasedToolsActivity::class.java))
    }
}
