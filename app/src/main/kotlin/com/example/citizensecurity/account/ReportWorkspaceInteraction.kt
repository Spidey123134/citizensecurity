package com.example.citizensecurity.account

import androidx.lifecycle.Lifecycle

internal fun ReportWorkspace.canInteract(lifecycle: Lifecycle): Boolean =
    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
        runCatching { checkActive() }.isSuccess
