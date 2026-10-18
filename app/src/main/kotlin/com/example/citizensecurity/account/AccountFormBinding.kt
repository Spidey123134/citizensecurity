package com.example.citizensecurity.account

import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.citizensecurity.CitizenSecurityApplication
import com.example.citizensecurity.R
import com.example.citizensecurity.domain.AccountField
import com.example.citizensecurity.release.DemoLogin
import kotlinx.coroutines.launch

/** Binds the teammate's existing user/pass/button IDs without replacing either layout. */
internal class AccountFormBinding(
    private val activity: ComponentActivity,
    private val mode: AccountAccessMode,
    private val onSuccess: (AccountAccessState) -> Unit,
    private val onAcademicAccess: (() -> Unit)? = null,
) {
    private val dependencies = activity.application as CitizenSecurityApplication
    private val model = ViewModelProvider(activity, AccountAccessViewModel.factory(dependencies.accountRepository))[AccountAccessViewModel::class.java]
    val username: EditText = activity.findViewById(R.id.user)
    private val password: EditText = activity.findViewById(R.id.pass)
    private val submit: Button = activity.findViewById(R.id.login_button)
    private var createdNotice = false
    private val status = TextView(activity).apply {
        id = R.id.account_status
        setTextColor(Color.BLACK)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
    }

    init {
        // Android must not place the password in its hierarchy Bundle on rotation/process save.
        password.isSaveEnabled = false
        password.isSaveFromParentEnabled = false
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        // Insets belong to the window container, so ScrollView receives the real visible height.
        // Padding the scroller itself leaves requestRectangleOnScreen using its full height.
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(R.id.account_window)) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        val container = submit.parent as LinearLayout
        container.addView(status, container.indexOfChild(submit) + 1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        submit.setOnClickListener {
            createdNotice = false
            username.error = null
            password.error = null
            val secret = password.text.toString()
            val name = username.text.toString()
            password.text.clear()
            if (mode == AccountAccessMode.LOGIN && onAcademicAccess != null && DemoLogin.accepts(name, secret)) {
                model.cancel()
                dependencies.accountSession.signOut()
                onAcademicAccess.invoke()
            } else model.submit(mode, name, secret)
        }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                password.text.clear()
                if (!activity.isChangingConfigurations) model.cancel()
            }
        })
        render(model.state.value)
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                model.state.collect { state ->
                    render(state)
                    if (model.consumeSuccess(state)) onSuccess(state)
                }
            }
        }
    }

    fun showAccountCreated(name: String) {
        createdNotice = true
        username.setText(name)
        status.text = activity.getString(R.string.account_created)
    }

    fun leaveForm() { password.text.clear(); model.cancel() }

    private fun render(state: AccountAccessState) {
        val loading = state is AccountAccessState.Loading
        submit.isEnabled = !loading
        username.isEnabled = !loading
        password.isEnabled = !loading
        activity.findViewById<View>(R.id.create_account_link)?.isEnabled = !loading
        activity.findViewById<View>(R.id.released_tools_entry)?.isEnabled = !loading
        username.error = (state as? AccountAccessState.Invalid)?.errors?.get(AccountField.USERNAME)
        password.error = (state as? AccountAccessState.Invalid)?.errors?.get(AccountField.PASSWORD)
        status.setText(when (state) {
            AccountAccessState.Idle -> when {
                createdNotice -> R.string.account_created
                mode == AccountAccessMode.REGISTER -> R.string.account_register_hint
                else -> R.string.account_local_hint
            }
            is AccountAccessState.Loading -> R.string.account_working
            is AccountAccessState.Invalid -> R.string.account_invalid
            AccountAccessState.InvalidCredentials -> R.string.account_invalid_credentials
            AccountAccessState.AlreadyExists -> R.string.account_already_exists
            is AccountAccessState.Failed -> if (state.mode == AccountAccessMode.REGISTER) R.string.account_registration_unknown else R.string.account_login_failed
            is AccountAccessState.Interrupted -> if (state.mode == AccountAccessMode.REGISTER) R.string.account_registration_unknown else R.string.account_login_interrupted
            is AccountAccessState.Registered -> R.string.account_created
            is AccountAccessState.Authenticated -> R.string.account_authenticated
        })
    }
}
