package com.example.citizensecurity

import android.content.Intent
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.citizensecurity.account.AccountAccessMode
import com.example.citizensecurity.account.AccountAccessState
import com.example.citizensecurity.account.AccountFormBinding
import com.example.citizensecurity.account.CreateAccountActivity
import com.example.citizensecurity.release.ReleasedToolsActivity

internal object ReleasedEntry {
    fun attach(activity: ComponentActivity) {
        val dependencies = activity.application as CitizenSecurityApplication
        val form = AccountFormBinding(activity, AccountAccessMode.LOGIN, onSuccess = { state ->
            dependencies.accountSession.signIn((state as AccountAccessState.Authenticated).account)
            openTools(activity)
        }, onAcademicAccess = { openTools(activity) })
        val createAccount = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.getStringExtra(CreateAccountActivity.EXTRA_USERNAME)?.let(form::showAccountCreated)
            }
        }
        activity.findViewById<TextView>(R.id.create_account_link).setOnClickListener {
            form.leaveForm()
            createAccount.launch(Intent(activity, CreateAccountActivity::class.java))
        }
        val container = activity.findViewById<LinearLayout>(R.id.main_container)
        val button = Button(activity).apply {
            id = R.id.released_tools_entry
            setText(R.string.released_tools_entry)
            setOnClickListener {
                form.leaveForm()
                dependencies.accountSession.signOut()
                openTools(activity)
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = (12 * activity.resources.displayMetrics.density).toInt() }
        container.addView(button, container.childCount - 1, params)
        container.addView(TextView(activity).apply {
            setText(R.string.demo_login_hint)
            setTextColor(Color.BLACK)
        }, container.childCount - 1)
    }

    private fun openTools(activity: ComponentActivity) {
        activity.startActivity(Intent(activity, ReleasedToolsActivity::class.java))
    }
}
