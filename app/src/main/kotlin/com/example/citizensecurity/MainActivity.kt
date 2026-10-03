package com.example.citizensecurity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.citizensecurity.report.ReportQueryViewModel
import com.example.citizensecurity.report.ReportViewModel

class MainActivity : ComponentActivity() {
    val reportViewModel: ReportViewModel by lazy {
        val dependencies = application as CitizenSecurityApplication
        ViewModelProvider(this, dependencies.reportViewModelFactory)[ReportViewModel::class.java]
    }

    val reportQueryViewModel: ReportQueryViewModel by lazy {
        val dependencies = application as CitizenSecurityApplication
        ViewModelProvider(this, dependencies.reportQueryViewModelFactory)[ReportQueryViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ReleasedEntry.attach(this)
    }
}
