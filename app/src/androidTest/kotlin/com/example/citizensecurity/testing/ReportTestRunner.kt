package com.example.citizensecurity.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Instrumentation only: this application and its location fixture never enter the product APK. */
class ReportTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, ReportTestApplication::class.java.name, context)
}
