package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.SimilarReportPage
import com.example.citizensecurity.domain.SimilarReportQuery
import com.example.citizensecurity.domain.SimilarReportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A result authorizes only the complete, unmodified report that the user reviewed. */
data class ReportSimilarityKey(val draft: NewReport, val excludedId: String?)

sealed interface ReportSimilarityState {
    data object Idle : ReportSimilarityState
    data class Checking(val key: ReportSimilarityKey) : ReportSimilarityState
    data class Ready(val key: ReportSimilarityKey, val page: SimilarReportPage) : ReportSimilarityState
    data class Failed(val key: ReportSimilarityKey, val message: String) : ReportSimilarityState
}

/**
 * Reads local similarities after a gesture; never saves a report, starts GPS or changes a folio.
 * Authorization is transient and consumed once. A changed field requires a fresh check, including
 * fields that the query itself does not use, so a stale dialog cannot confirm a corrected draft.
 */
class ReportSimilarityViewModel(private val repository: SimilarReportRepository) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportSimilarityState>(ReportSimilarityState.Idle)
    val state: StateFlow<ReportSimilarityState> = mutableState.asStateFlow()
    private var pending: Job? = null
    private var generation = 0L

    fun check(draft: NewReport, excludedId: String? = null): Boolean {
        val scope = viewModelScope
        if (!scope.isActive) return false
        val key = ReportSimilarityKey(draft, excludedId)
        val request = ++generation
        pending?.cancel()
        val checking = ReportSimilarityState.Checking(key)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val query = SimilarReportQuery.fromDraft(draft, excludedId)
                    ?: throw IllegalArgumentException("The reviewed point is missing or invalid")
                val page = repository.findSimilar(query)
                ensureActive()
                if (generation == request) mutableState.value = ReportSimilarityState.Ready(key, page)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                if (generation == request) mutableState.value = ReportSimilarityState.Failed(
                    key,
                    "No se pudo comprobar si hay reportes parecidos en este teléfono. Puedes reintentar o guardar de todos modos.",
                )
            }
        }
        // Reserve first: a reentrant collector can replace or clear this request before it starts.
        pending = job
        job.invokeOnCompletion { cause ->
            if (generation == request) {
                pending = null
                if (cause is CancellationException) mutableState.compareAndSet(checking, ReportSimilarityState.Idle)
            }
        }
        mutableState.value = checking
        if (generation == request) job.start() else job.cancel()
        return true
    }

    fun clear() {
        ++generation
        pending?.cancel()
        pending = null
        mutableState.value = ReportSimilarityState.Idle
    }

    /** Neither an incomplete empty search nor a failed search can silently authorize saving. */
    fun consumeAuthorization(
        draft: NewReport,
        excludedId: String? = null,
        explicitOverride: Boolean = false,
    ): Boolean {
        if (!viewModelScope.isActive) return false
        val key = ReportSimilarityKey(draft, excludedId)
        val allowed = when (val current = mutableState.value) {
            is ReportSimilarityState.Ready -> current.key == key &&
                (explicitOverride || (current.page.matches.isEmpty() && !current.page.searchIncomplete))
            is ReportSimilarityState.Failed -> current.key == key && explicitOverride
            else -> false
        }
        if (allowed) clear()
        return allowed
    }

    /** Stop a background callback from silently saving; preserve a warning for the user's return. */
    fun pauseAutomaticSave() {
        when (val current = mutableState.value) {
            is ReportSimilarityState.Checking -> clear()
            is ReportSimilarityState.Ready -> if (current.page.matches.isEmpty() && !current.page.searchIncomplete) clear()
            else -> Unit
        }
    }

    override fun onCleared() {
        clear()
        super.onCleared()
    }

    companion object {
        fun factory(repository: SimilarReportRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportSimilarityViewModel(repository) }
        }
    }
}
