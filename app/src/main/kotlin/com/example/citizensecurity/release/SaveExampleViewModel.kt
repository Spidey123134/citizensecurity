package com.example.citizensecurity.release

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.Report
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface SaveExampleState {
    data object Idle : SaveExampleState
    data object Running : SaveExampleState
    data class Saved(val report: Report) : SaveExampleState
    data object Failed : SaveExampleState
}

/** Ejecuta únicamente la demostración aislada; nunca recibe el repositorio del usuario. */
class SaveExampleViewModel(private val saveExample: suspend () -> Report) : ViewModel() {
    private val mutableState = MutableStateFlow<SaveExampleState>(SaveExampleState.Idle)
    val state: StateFlow<SaveExampleState> = mutableState.asStateFlow()

    fun runExample() {
        val scope = viewModelScope
        if (!scope.isActive) return
        val previous = mutableState.value
        if (previous is SaveExampleState.Running || previous is SaveExampleState.Saved ||
            !mutableState.compareAndSet(previous, SaveExampleState.Running)
        ) return

        scope.launch {
            try {
                val report = saveExample()
                ensureActive()
                mutableState.value = SaveExampleState.Saved(report)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                mutableState.value = SaveExampleState.Failed
            }
        }.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                mutableState.compareAndSet(SaveExampleState.Running, SaveExampleState.Idle)
            }
        }
    }

    companion object {
        fun factory(saveExample: suspend () -> Report): ViewModelProvider.Factory = viewModelFactory {
            initializer { SaveExampleViewModel(saveExample) }
        }
    }
}
