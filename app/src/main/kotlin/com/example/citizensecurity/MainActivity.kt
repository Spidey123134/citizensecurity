package com.example.citizensecurity

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import com.example.citizensecurity.navigation.Navigation

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Navigation(findViewById<FrameLayout>(R.id.main_container)).showHome()
    }
}
