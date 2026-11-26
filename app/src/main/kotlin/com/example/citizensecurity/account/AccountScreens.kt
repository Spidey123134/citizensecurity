package com.example.citizensecurity.account

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citizensecurity.R
import com.example.citizensecurity.domain.AccountField
import com.example.citizensecurity.domain.AccountRepository
import com.example.citizensecurity.domain.LocalAccount
import com.example.citizensecurity.release.DemoLogin

@Composable
internal fun LoadingScreen() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    stringResource(R.string.access_app_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                CircularProgressIndicator()
                Text(stringResource(R.string.access_loading), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun AccountScreen(
    repository: AccountRepository,
    mode: AccountAccessMode,
    onAuthenticated: (LocalAccount) -> Unit = {},
    onAcademicAccess: () -> Unit = {},
    onCreateAccount: () -> Unit = {},
    onAccountCreated: (String) -> Unit = {},
    onBack: () -> Unit = {},
    createdUsername: String? = null,
) {
    val factory = remember(repository) { AccountAccessViewModel.factory(repository) }
    val model: AccountAccessViewModel = viewModel(factory = factory)
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalActivity.current
    val authenticated by rememberUpdatedState(onAuthenticated)
    val accountCreated by rememberUpdatedState(onAccountCreated)
    val focus = LocalFocusManager.current
    val registering = mode == AccountAccessMode.REGISTER
    val busy = state is AccountAccessState.Loading
    val errors = (state as? AccountAccessState.Invalid)?.errors.orEmpty()
    val usernameError = errors[AccountField.USERNAME]
    val passwordError = errors[AccountField.PASSWORD]
    var username by rememberSaveable { mutableStateOf(createdUsername.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var appliedUsername by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreatedNotice by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(createdUsername) {
        if (createdUsername != null && createdUsername != appliedUsername) {
            username = createdUsername
            appliedUsername = createdUsername
            showCreatedNotice = true
        }
    }

    DisposableEffect(lifecycleOwner, activity, model) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                password = ""
                if (activity?.isChangingConfigurations != true) model.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycleOwner, model) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            model.state.collect { result ->
                if (model.consumeSuccess(result)) {
                    when (result) {
                        is AccountAccessState.Authenticated -> authenticated(result.account)
                        is AccountAccessState.Registered -> accountCreated(result.account.username)
                        else -> Unit
                    }
                }
            }
        }
    }

    fun leave(action: () -> Unit) {
        password = ""
        focus.clearFocus()
        model.cancel()
        action()
    }

    fun submit() {
        if (busy) return
        val secret = password
        password = ""
        focus.clearFocus()
        showCreatedNotice = false
        if (!registering && DemoLogin.accepts(username, secret)) {
            model.cancel()
            onAcademicAccess()
        } else {
            model.submit(mode, username, secret)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .testTag("account_scroll")
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                shadowElevation = 4.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        stringResource(R.string.access_app_title),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        stringResource(if (registering) R.string.access_create else R.string.access_welcome),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth().testTag("user"),
                        enabled = !busy,
                        label = { Text(stringResource(R.string.access_username)) },
                        supportingText = usernameError?.let { message -> { Text(message) } },
                        isError = usernameError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth().testTag("pass"),
                        enabled = !busy,
                        label = { Text(stringResource(R.string.access_password)) },
                        supportingText = passwordError?.let { message -> { Text(message) } },
                        isError = passwordError != null,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                    )
                    Button(::submit, modifier = Modifier.fillMaxWidth().testTag("login_button"), enabled = !busy) {
                        Text(stringResource(if (registering) R.string.access_register else R.string.access_login))
                    }
                    Text(
                        stringResource(accountStatus(state, registering, showCreatedNotice)),
                        modifier = Modifier
                            .testTag("account_status")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (registering) {
                        TextButton({ leave(onBack) }, modifier = Modifier.fillMaxWidth().testTag("login_link")) {
                            Text(stringResource(R.string.access_back_to_login))
                        }
                    } else {
                        OutlinedButton(
                            { leave(onCreateAccount) },
                            modifier = Modifier.fillMaxWidth().testTag("create_account_link"),
                            enabled = !busy,
                        ) {
                            Text(stringResource(R.string.access_create))
                        }
                        TextButton(
                            { leave(onAcademicAccess) },
                            modifier = Modifier.fillMaxWidth().testTag("released_tools_entry"),
                            enabled = !busy,
                        ) {
                            Text(stringResource(R.string.released_tools_entry))
                        }
                        Text(
                            stringResource(R.string.demo_login_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private fun accountStatus(state: AccountAccessState, registering: Boolean, createdNotice: Boolean): Int =
    when (state) {
        AccountAccessState.Idle -> when {
            createdNotice -> R.string.account_created
            registering -> R.string.account_register_hint
            else -> R.string.account_local_hint
        }
        is AccountAccessState.Loading -> R.string.account_working
        is AccountAccessState.Invalid -> R.string.account_invalid
        AccountAccessState.InvalidCredentials -> R.string.account_invalid_credentials
        AccountAccessState.AlreadyExists -> R.string.account_already_exists
        is AccountAccessState.Failed ->
            if (registering) R.string.account_registration_unknown else R.string.account_login_failed
        is AccountAccessState.Interrupted ->
            if (registering) R.string.account_registration_unknown else R.string.account_login_interrupted
        is AccountAccessState.Registered -> R.string.account_created
        is AccountAccessState.Authenticated -> R.string.account_authenticated
    }
