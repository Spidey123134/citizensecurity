package com.example.citizensecurity.alerts

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Only an in-memory gesture may consume Android's callback; a new process has no pending choice. */
class ReportAlertsPermissionChoice : ViewModel() {
    data class Choice(val generation: Long, val granted: Boolean? = null)
    private val mutableState = MutableStateFlow<Choice?>(null)
    val state = mutableState.asStateFlow()
    private var generation = 0L

    fun begin(): Boolean {
        if (mutableState.value != null) return false
        mutableState.value = Choice(++generation)
        return true
    }
    fun complete(granted: Boolean): Boolean {
        val pending = mutableState.value ?: return false
        if (pending.granted != null) return false
        mutableState.value = pending.copy(granted = granted)
        return true
    }
    fun consume(expected: Choice): Boolean {
        if (mutableState.value !== expected || expected.granted == null) return false
        mutableState.value = null
        return true
    }
    fun abort() { ++generation; mutableState.value = null }
}
