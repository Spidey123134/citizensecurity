package com.example.citizensecurity.release

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.ReportDraftCheckpointJournal
import com.example.citizensecurity.domain.ReportSubmissionJournal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A failed read is unknown, never evidence that its private journal is empty. */
data class ReportHomeState(
    val loading: Boolean = true,
    val pendingAttempt: Boolean = false,
    val savedDraft: Boolean = false,
    val draftHasFolio: Boolean = false,
    val submissionReadFailed: Boolean = false,
    val draftReadFailed: Boolean = false,
)

/**
 * Read-only home indicators. A durable attempt may already have committed: only the report
 * screen may check its result. This model never opens SQLite, obtains GPS or changes a journal.
 */
class ReportHomeViewModel(
    private val submissionJournal: ReportSubmissionJournal,
    private val checkpointJournal: ReportDraftCheckpointJournal,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReportHomeState())
    val state: StateFlow<ReportHomeState> = mutableState.asStateFlow()
    private var activeJob: Job? = null
    private var generation = 0L

    init { refresh() }

    /** Call on the main thread when returning home or explicitly retrying its indicators. */
    fun refresh(): Boolean {
        val scope = viewModelScope
        if (!scope.isActive) return false
        val expected = ++generation
        val previousJob = activeJob
        val job = scope.launch(start = CoroutineStart.LAZY) {
            coroutineScope {
                val submissionRead = async { readJournal(submissionJournal::read) }
                val checkpointRead = async { readJournal(checkpointJournal::read) }
                val submission = submissionRead.await()
                val checkpoint = checkpointRead.await()
                ensureActive()
                if (generation != expected) return@coroutineScope
                mutableState.value = ReportHomeState(
                    loading = false,
                    pendingAttempt = submission.value != null,
                    savedDraft = checkpoint.value != null,
                    draftHasFolio = checkpoint.value?.submittedFolio != null,
                    submissionReadFailed = submission.failed,
                    draftReadFailed = checkpoint.failed,
                )
            }
        }
        // Reserve before publishing state: a collector may synchronously request another read.
        activeJob = job
        previousJob?.cancel()
        job.invokeOnCompletion { cause ->
            if (generation == expected && activeJob === job) {
                activeJob = null
                if (cause is CancellationException) {
                    mutableState.value = ReportHomeState(
                        loading = false, submissionReadFailed = true, draftReadFailed = true,
                    )
                }
            }
        }
        mutableState.value = ReportHomeState()
        job.start()
        return true
    }

    private suspend fun <T> readJournal(read: suspend () -> T?): JournalRead<T> = try {
        val value = read()
        currentCoroutineContext().ensureActive()
        JournalRead(value, failed = false)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        JournalRead(value = null, failed = true)
    }

    private data class JournalRead<T>(val value: T?, val failed: Boolean)

    companion object {
        fun factory(
            submissionJournal: ReportSubmissionJournal,
            checkpointJournal: ReportDraftCheckpointJournal,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportHomeViewModel(submissionJournal, checkpointJournal) }
        }
    }
}
