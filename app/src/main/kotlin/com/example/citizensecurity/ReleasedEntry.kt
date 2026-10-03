package com.example.citizensecurity

import android.content.Intent
import android.graphics.Color
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.citizensecurity.release.DemoLogin
import com.example.citizensecurity.release.ReleasedToolsActivity

internal object ReleasedEntry {
    fun attach(activity: ComponentActivity) {
        val container = activity.findViewById<LinearLayout>(R.id.main_container)
        val button = Button(activity).apply {
            id = R.id.released_tools_entry
            setText(R.string.released_tools_entry)
            setOnClickListener { openTools(activity) }
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

        val username = activity.findViewById<EditText>(R.id.user)
        val password = activity.findViewById<EditText>(R.id.pass)
        activity.findViewById<Button>(R.id.login_button).setOnClickListener {
            if (DemoLogin.accepts(username.text.toString(), password.text.toString())) {
                password.error = null
                password.text.clear()
                openTools(activity)
            } else {
                password.error = activity.getString(R.string.demo_login_error)
            }
        }
    }

    private fun openTools(activity: ComponentActivity) {
        activity.startActivity(Intent(activity, ReleasedToolsActivity::class.java))
    }
}
