package com.example.citizensecurity

import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.activity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.citizensecurity.account.AccountAccessMode
import com.example.citizensecurity.account.AccountScreen
import com.example.citizensecurity.account.AccountSessionState
import com.example.citizensecurity.account.LoadingScreen
import com.example.citizensecurity.account.LocalReportWorkspace
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.account.ReportWorkspaceActivityBinding
import com.example.citizensecurity.administration.AdministrationSetupActivity
import com.example.citizensecurity.alerts.ReportAlertsRoute
import com.example.citizensecurity.help.EmergencyAccessActivity
import com.example.citizensecurity.help.HelpContactsRoute
import com.example.citizensecurity.message.ReportMessageRoute
import com.example.citizensecurity.preview.ReportDraftActivity
import com.example.citizensecurity.release.HomeScreen
import com.example.citizensecurity.reports.ReportActivityRoute
import com.example.citizensecurity.reports.ReportsActivity
import com.example.citizensecurity.reports.ReportsMapActivity
import com.example.citizensecurity.reports.ReportsRoute
import kotlinx.coroutines.delay

private object Route {
    const val Loading = "carga"
    const val Access = "acceso"
    const val Register = "registro"
    const val Home = "inicio"
    const val Reports = "reportes"
    const val ReportsWithFolio = "reportes?folio={folio}"
    const val History = "actividad"
    const val Contacts = "contactos"
    const val Draft = "nuevo-reporte"
    const val Map = "mapa"
    const val Message = "mensaje"
    const val MessageWithFolio = "mensaje/{folio}"
    const val Alerts = "avisos"
    const val Administration = "administracion"
    const val Emergency = "ayuda-inmediata"
}

@Composable
internal fun CitizenSecurityNavigation(
    app: CitizenSecurityApplication,
    showLoading: Boolean,
    modifier: Modifier = Modifier,
    initialSession: AccountSessionState,
    onSessionBound: (AccountSessionState) -> Unit,
) {
    val nav = rememberNavController()
    var session by remember { mutableStateOf(initialSession) }
    val currentSession by app.accountSession.state.collectAsStateWithLifecycle()

    fun bindSession() {
        session = app.accountSession.capture()
        onSessionBound(session)
    }

    LaunchedEffect(currentSession) {
        if (!app.accountSession.isCurrent(session)) {
            bindSession()
            nav.replaceWith(Route.Access)
        }
    }

    NavHost(nav, startDestination = if (showLoading) Route.Loading else Route.Access, modifier = modifier) {
        composable(Route.Loading) {
            LoadingScreen()
            LaunchedEffect(Unit) {
                delay(500)
                nav.replaceWith(if (session.account == null) Route.Access else Route.Home)
            }
        }
        composable(Route.Access) { entry ->
            val createdUsername by entry.savedStateHandle.getStateFlow<String?>("created_username", null)
                .collectAsStateWithLifecycle()
            AccountScreen(
                repository = app.accountRepository,
                mode = AccountAccessMode.LOGIN,
                onAuthenticated = { account ->
                    if (nav.currentBackStackEntry == entry) {
                        app.accountSession.signIn(account)
                        bindSession()
                        nav.replaceWith(Route.Home)
                    }
                },
                onAcademicAccess = {
                    if (nav.currentBackStackEntry == entry) {
                        app.accountSession.signOut()
                        bindSession()
                        nav.replaceWith(Route.Home)
                    }
                },
                onCreateAccount = { nav.navigate(Route.Register) { launchSingleTop = true } },
                createdUsername = createdUsername,
            )
        }
        composable(Route.Register) { entry ->
            AccountScreen(
                repository = app.accountRepository,
                mode = AccountAccessMode.REGISTER,
                onAccountCreated = { username ->
                    if (nav.currentBackStackEntry == entry) {
                        nav.previousBackStackEntry?.savedStateHandle?.set("created_username", username)
                        nav.popBackStack()
                    }
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Route.Home) { entry ->
            WorkspaceRoute(app, session) { workspace ->
                fun open(action: () -> Unit) {
                    if (nav.currentBackStackEntry == entry && entry.lifecycle.currentState == Lifecycle.State.RESUMED &&
                        app.accountSession.isCurrent(session)
                    ) action()
                }
                HomeScreen(
                    workspace = workspace,
                    onReport = { open { nav.openActivity(Route.Draft, session) } },
                    onReports = { open { nav.navigate(Route.Reports) } },
                    onMap = { open { nav.openActivity(Route.Map, session) } },
                    onActivity = { open { nav.navigate(Route.History) } },
                    onContacts = { open { nav.navigate(Route.Contacts) } },
                    onImmediateHelp = { open { nav.openActivity(Route.Emergency, session) } },
                    onAdministration = { open { nav.openActivity(Route.Administration, session) } },
                    onClose = {
                        if (nav.currentBackStackEntry == entry && app.accountSession.signOutIfCurrent(session)) {
                            bindSession()
                            nav.replaceWith(Route.Access)
                        }
                    },
                )
            }
        }
        composable(Route.ReportsWithFolio, arguments = listOf(navArgument("folio") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        })) { entry ->
            WorkspaceRoute(app, session) { workspace ->
                ReportsRoute(
                    workspace = workspace,
                    initialFolio = entry.arguments?.getString("folio"),
                    onBack = { nav.popBackStack() },
                    onOpenMessage = { nav.navigate("${Route.Message}/${Uri.encode(it)}") },
                    onOpenMap = { nav.openActivity(Route.Map, session, it) },
                )
            }
        }
        composable(Route.History) {
            WorkspaceRoute(app, session) { workspace ->
                ReportActivityRoute(
                    workspace = workspace,
                    onBack = { nav.popBackStack() },
                    onOpenReport = { nav.navigate("${Route.Reports}?folio=${Uri.encode(it)}") },
                    onOpenAlerts = { nav.navigate(Route.Alerts) },
                )
            }
        }
        composable(Route.Contacts) {
            WorkspaceRoute(app, session) { workspace ->
                HelpContactsRoute(app, workspace, onBack = { nav.popBackStack() })
            }
        }
        composable(Route.MessageWithFolio, arguments = listOf(navArgument("folio") {
            type = NavType.StringType
        })) { entry ->
            WorkspaceRoute(app, session) { workspace ->
                ReportMessageRoute(
                    app, workspace, checkNotNull(entry.arguments?.getString("folio")),
                    onBack = { nav.popBackStack() },
                    onOpenContacts = { nav.navigate(Route.Contacts) },
                )
            }
        }
        composable(Route.Alerts) {
            WorkspaceRoute(app, session) { workspace ->
                ReportAlertsRoute(app, workspace, onBack = { nav.popBackStack() }, onOpenActivity = { nav.popBackStack() })
            }
        }

        activity(Route.Draft) { activityClass = ReportDraftActivity::class }
        activity(Route.Map) { activityClass = ReportsMapActivity::class }
        activity(Route.Administration) { activityClass = AdministrationSetupActivity::class }
        activity(Route.Emergency) { activityClass = EmergencyAccessActivity::class }
    }
}

private fun NavController.replaceWith(route: String) {
    navigate(route) { popUpTo(graph.id) { inclusive = true } }
}

private fun NavController.openActivity(route: String, session: AccountSessionState, folio: String? = null) {
    val arguments = Bundle()
    ReportWorkspaceActivityBinding.stamp(arguments, session)
    if (folio != null) arguments.putString(ReportsActivity.EXTRA_FOLIO, folio)
    navigate(checkNotNull(graph.findNode(route)).id, arguments)
}

@Composable
private fun WorkspaceRoute(
    app: CitizenSecurityApplication,
    session: AccountSessionState,
    content: @Composable (ReportWorkspace) -> Unit,
) {
    if (!app.accountSession.isCurrent(session)) return
    val holder: NavigationWorkspace = viewModel(
        key = "workspace:${session.processId}:${session.revision}",
        factory = viewModelFactory { initializer { NavigationWorkspace(app.openReportWorkspace(session)) } },
    )
    CompositionLocalProvider(LocalReportWorkspace provides holder.workspace) { content(holder.workspace) }
}

private class NavigationWorkspace(val workspace: ReportWorkspace) : ViewModel() {
    override fun onCleared() { workspace.close() }
}
