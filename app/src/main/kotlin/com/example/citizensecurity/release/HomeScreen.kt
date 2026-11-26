package com.example.citizensecurity.release

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.citizensecurity.R
import com.example.citizensecurity.account.ReportWorkspace
import com.example.citizensecurity.tutorial.GuidedTourButton
import com.example.citizensecurity.tutorial.TourTopic

@Composable
internal fun HomeScreen(
    workspace: ReportWorkspace,
    onReport: () -> Unit,
    onReports: () -> Unit,
    onMap: () -> Unit,
    onActivity: () -> Unit,
    onContacts: () -> Unit,
    onImmediateHelp: () -> Unit,
    onAdministration: () -> Unit,
    onClose: () -> Unit,
) {
    val model: ReportHomeViewModel = viewModel(factory = ReportHomeViewModel.factory(
        workspace.submissionJournal, workspace.checkpointJournal,
    ))
    val state by model.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refresh() }
    BackHandler(onBack = onClose)

    val account = workspace.session.account
    val messages = if (state.loading) listOf(R.string.home_checking) else buildList {
        if (state.submissionReadFailed) add(R.string.home_attempt_unknown)
        else if (state.pendingAttempt) add(R.string.home_attempt_pending)
        if (state.draftReadFailed) add(R.string.home_draft_unknown)
        else if (state.savedDraft) add(if (state.draftHasFolio) R.string.home_draft_linked else R.string.home_draft_saved)
        if (isEmpty()) add(R.string.home_ready)
    }
    val reportAction = when {
        state.loading || state.submissionReadFailed || state.pendingAttempt -> R.string.home_open_report
        state.savedDraft && !state.draftHasFolio -> R.string.home_open_draft
        state.savedDraft -> R.string.home_open_report
        else -> R.string.home_new_report
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxSize().testTag("tools_container")
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.home_brand),
                        Modifier.weight(1f).semantics { heading() }.testTag("home_title"),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    GuidedTourButton(TourTopic.HOME)
                }
                Text(
                    if (account == null) stringResource(R.string.account_home_guest)
                    else stringResource(R.string.account_home_signed_in, account.username),
                    Modifier.testTag("home_account"), color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        messages.map { stringResource(it) }.joinToString("\n\n"),
                        Modifier.testTag("home_status").semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (!state.loading && (state.submissionReadFailed || state.draftReadFailed)) {
                        TextButton(onClick = model::refresh, modifier = Modifier.testTag("home_retry")) {
                            Text(stringResource(R.string.home_retry))
                        }
                    }
                    Button(onClick = onReport, modifier = Modifier.fillMaxWidth().testTag("tools_preview_interface")) {
                        Text(stringResource(reportAction))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HomeAction(R.string.home_list_heading, "tools_reports", onReports)
                HomeAction(R.string.home_map_heading, "tools_reports_map", onMap)
                HomeAction(R.string.home_activity, "home_activity", onActivity)
                HomeAction(R.string.home_help, "home_help", onContacts)
                HomeAction(R.string.home_quick_help, "home_quick_help", onImmediateHelp)
                if (account?.username == "admin_local") {
                    HomeAction(R.string.home_administration, "home_administration", onAdministration)
                }
            }
            Text(stringResource(R.string.home_local_notice), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally).testTag("tools_back")) {
                Text(stringResource(if (account == null) R.string.home_back else R.string.account_sign_out))
            }
        }
    }
}

@Composable
private fun HomeAction(label: Int, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Text(stringResource(label))
    }
}
