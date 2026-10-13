package com.example.citizensecurity.account

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import com.example.citizensecurity.R

class CreateAccountActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_createaccount)
        val form = AccountFormBinding(this, AccountAccessMode.REGISTER, onSuccess = { state ->
            val account = (state as AccountAccessState.Registered).account
            setResult(RESULT_OK, Intent().putExtra(EXTRA_USERNAME, account.username))
            finish()
        })
        findViewById<View>(R.id.login_link).setOnClickListener { form.leaveForm(); finish() }
    }

    companion object { const val EXTRA_USERNAME = "created_local_username" }
}
