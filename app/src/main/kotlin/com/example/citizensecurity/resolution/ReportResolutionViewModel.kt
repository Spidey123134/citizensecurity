package com.example.citizensecurity.resolution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReportResolutionState {
    data object Idle : ReportResolutionState
    class Loading(val reportId: String) : ReportResolutionState
    class Ready(
        val page: ReportResolutionPage,
        val query: ReportResolutionQuery,
        val previous: List<ReportResolutionQuery> = emptyList(),
        val target: ReportResolution? = null,
        val reason: String = "",
        val message: String? = null,
        val blockedByPending: Boolean = false,
    ) : ReportResolutionState {
        val canReview get() = !blockedByPending && page.actor != null && target != null &&
            target != page.resolution && resolutionReasonValid(reason)
    }
    class Reviewing(val page: ReportResolutionPage, val command: ReportResolutionCommand) : ReportResolutionState
    class Working(val command: ReportResolutionCommand) : ReportResolutionState
    class Uncertain(
        val command: ReportResolutionCommand,
        val canRetry: Boolean = false,
        val canDiscard: Boolean = false,
        val message: String = "No se pudo confirmar el resultado. Comprueba el recibo antes de volver a intentarlo.",
    ) : ReportResolutionState
    class Confirmed(val command: ReportResolutionCommand, val event: ReportResolutionEvent) : ReportResolutionState
    class Missing(val reportId: String) : ReportResolutionState
    class Error(val reportId: String) : ReportResolutionState
    data object Denied : ReportResolutionState
}

/** Ephemeral preparation/recovery only. Reopening a process reads SQL and never repeats a write. */
class ReportResolutionViewModel(
    private val repository: ReportResolutionRepository,
    private val checkSession: () -> Unit = {},
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ReportResolutionState>(ReportResolutionState.Idle)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var visibleReport: String? = null
    private var reading: Job? = null
    private var writer: Job? = null
    private var checking: Job? = null
    private var pendingCommand: ReportResolutionCommand? = null
    private var closed = false

    fun activate(reportId: String): Boolean {
        if (!permitted()) return false
        try { ReportResolutionQuery(reportId) } catch (_: IllegalArgumentException) { return false }
        if (visibleReport == reportId && mutableState.value !== ReportResolutionState.Idle) return false
        retireReading()
        visibleReport = reportId
        val pending = pendingCommand
        if (pending?.reportId == reportId) {
            mutableState.value = if (writer != null) ReportResolutionState.Working(pending)
                else ReportResolutionState.Uncertain(pending)
            return true
        }
        return load(ReportResolutionQuery(reportId), emptyList())
    }

    fun clear() {
        if (closed) return
        retireReading()
        visibleReport = null
        mutableState.value = ReportResolutionState.Idle
        // A writer retains its exclusion and exact command until it returns. No second operation.
    }

    fun select(expected: ReportResolutionState.Ready, target: ReportResolution): Boolean {
        if (!accept(expected) || pendingCommand != null || expected.page.actor == null || target == expected.page.resolution) return false
        mutableState.value = ReportResolutionState.Ready(expected.page, expected.query, expected.previous, target, expected.reason)
        return true
    }

    fun setReason(expected: ReportResolutionState.Ready, reason: String): Boolean {
        if (!accept(expected) || pendingCommand != null || expected.page.actor == null || reason.length > 1000) return false
        mutableState.value = ReportResolutionState.Ready(expected.page, expected.query, expected.previous, expected.target, reason)
        return true
    }

    /** First gesture rereads authority and revision. UUID is generated once for this review. */
    fun review(expected: ReportResolutionState.Ready): Boolean {
        if (!accept(expected) || !expected.canReview || pendingCommand != null) return false
        return readAction(expected.page.reportId) {
            val query = ReportResolutionQuery(expected.page.reportId)
            val page = readPage(query) ?: return@readAction ReportResolutionState.Missing(query.reportId)
            if (!sameRevision(page, expected.page)) return@readAction ready(page, query, message = CHANGED)
            val actor = page.actor ?: throw ReportResolutionPermissionDeniedException()
            ReportResolutionState.Reviewing(page, ReportResolutionCommand(
                UUID.randomUUID().toString(), page.reportId, page.latestSequence, page.resolution,
                requireNotNull(expected.target), actor, expected.reason,
            ))
        }
    }

    fun edit(expected: ReportResolutionState.Reviewing): Boolean {
        if (!accept(expected) || pendingCommand != null) return false
        mutableState.value = ReportResolutionState.Ready(expected.page, ReportResolutionQuery(expected.page.reportId),
            target = expected.command.targetResolution, reason = expected.command.reason)
        return true
    }

    /** Only this second gesture may write; the backend independently checks ownership and commit authority. */
    fun confirm(expected: ReportResolutionState.Reviewing): Boolean {
        if (!accept(expected) || pendingCommand != null || writer != null) return false
        pendingCommand = expected.command
        return write(expected.command)
    }

    fun recover(expected: ReportResolutionState.Uncertain): Boolean {
        if (!accept(expected) || pendingCommand !== expected.command || writer != null) return false
        val command = expected.command
        return readAction(command.reportId) {
            val receipt = readReceipt(command)
            if (receipt != null) ReportResolutionState.Confirmed(command, receipt)
            else {
                val page = readPage(ReportResolutionQuery(command.reportId))
                val matches = page != null && matches(page, command)
                ReportResolutionState.Uncertain(command, canRetry = matches, canDiscard = !matches,
                    message = if (matches) "No hay recibo guardado. Puedes reintentar la misma declaración con su mismo identificador."
                    else "No hay recibo y el reporte o tu permiso cambió. Cierra esta solicitud y revisa el resultado actual.")
            }
        }
    }

    fun retry(expected: ReportResolutionState.Uncertain): Boolean {
        if (!accept(expected) || !expected.canRetry || pendingCommand !== expected.command || writer != null) return false
        return write(expected.command)
    }

    /** A separate gesture rereads a confirmed receipt before releasing the in-memory command. */
    fun acknowledge(expected: ReportResolutionState.Confirmed): Boolean {
        if (!accept(expected) || pendingCommand !== expected.command || writer != null) return false
        val command = expected.command
        return readAction(command.reportId, release = command) {
            val receipt = readReceipt(command)
            if (receipt == null) return@readAction ReportResolutionState.Uncertain(command)
            val query = ReportResolutionQuery(command.reportId)
            val page = readPage(query) ?: return@readAction ReportResolutionState.Missing(command.reportId)
            ready(page, query)
        }
    }

    /** A stale/not-found request may be retired only after another receipt + revision read. */
    fun discard(expected: ReportResolutionState.Uncertain): Boolean {
        if (!accept(expected) || !expected.canDiscard || pendingCommand !== expected.command || writer != null) return false
        val command = expected.command
        return readAction(command.reportId, release = command) {
            val receipt = readReceipt(command)
            if (receipt != null) return@readAction ReportResolutionState.Confirmed(command, receipt)
            val query = ReportResolutionQuery(command.reportId)
            val page = readPage(query) ?: return@readAction ReportResolutionState.Missing(command.reportId)
            if (matches(page, command)) ReportResolutionState.Uncertain(command, canRetry = true)
            else ready(page, query, message = CHANGED)
        }
    }

    fun refresh(expected: ReportResolutionState = mutableState.value): Boolean {
        if (!accept(expected) || pendingCommand != null) return false
        val id = visibleReport ?: return false
        return load(ReportResolutionQuery(id), emptyList())
    }

    fun next(expected: ReportResolutionState.Ready): Boolean {
        if (!accept(expected) || pendingCommand != null || expected.previous.size >= MAX_PREVIOUS) return false
        val before = expected.page.nextBeforeSequence ?: return false
        return load(ReportResolutionQuery(expected.page.reportId, before), expected.previous + expected.query)
    }

    fun previous(expected: ReportResolutionState.Ready): Boolean {
        if (!accept(expected) || pendingCommand != null || expected.previous.isEmpty()) return false
        return load(expected.previous.last(), expected.previous.dropLast(1))
    }

    /** A read-only authority check on resume. It does not retry or acknowledge any operation. */
    fun checkAccess(): Boolean {
        if (!permitted() || checking?.isActive == true) return false
        val id = visibleReport ?: return true
        val revision = generation
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val query = ReportResolutionQuery(id)
                val fresh = withContext(dispatcher) { readPage(query) }
                ensureActive()
                if (!closed && visibleReport == id && revision == generation) {
                    checkLease()
                    val current = mutableState.value
                    if (pendingCommand == null && current is ReportResolutionState.Ready &&
                        (fresh == null || !sameRevision(fresh, current.page))) {
                        mutableState.value = if (fresh == null) ReportResolutionState.Missing(id)
                            else ready(fresh, query, message = CHANGED)
                    } else if (pendingCommand == null && current is ReportResolutionState.Reviewing &&
                        (fresh == null || !matches(fresh, current.command))) {
                        mutableState.value = if (fresh == null) ReportResolutionState.Missing(id)
                            else ready(fresh, query, message = CHANGED)
                    } else if (current is ReportResolutionState.Uncertain &&
                        (fresh == null || !matches(fresh, current.command))) {
                        mutableState.value = ReportResolutionState.Uncertain(current.command,
                            message = "El resultado o el permiso cambió. Comprueba el recibo antes de continuar.")
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Revoked) { deny() }
            catch (_: ReportResolutionPermissionDeniedException) { deny() }
            catch (_: Exception) {
                ensureActive()
                if (!closed && visibleReport == id && revision == generation && writer == null) {
                    try {
                        checkLease()
                        val command = pendingCommand?.takeIf { it.reportId == id }
                        mutableState.value = if (command == null) ReportResolutionState.Error(id)
                            else ReportResolutionState.Uncertain(command)
                    } catch (_: Revoked) { deny() }
                }
            }
        }
        checking = job; job.start(); return true
    }

    private fun load(query: ReportResolutionQuery, previous: List<ReportResolutionQuery>): Boolean =
        readAction(query.reportId) {
            val page = readPage(query) ?: return@readAction ReportResolutionState.Missing(query.reportId)
            ready(page, query, previous)
        }

    private fun ready(page: ReportResolutionPage, query: ReportResolutionQuery,
        previous: List<ReportResolutionQuery> = emptyList(), message: String? = null) =
        ReportResolutionState.Ready(page, query, previous, message = message,
            blockedByPending = pendingCommand != null && pendingCommand?.reportId != page.reportId)

    private fun readAction(reportId: String, release: ReportResolutionCommand? = null,
        block: suspend () -> ReportResolutionState): Boolean {
        if (!permitted() || visibleReport != reportId) return false
        retireReading()
        val revision = generation
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = withContext(dispatcher) { checkLease(); block().also { ensureActive(); checkLease() } }
                ensureActive()
                if (revision == generation && visibleReport == reportId && !closed) {
                    checkLease()
                    if (release != null && result !is ReportResolutionState.Uncertain && result !is ReportResolutionState.Confirmed &&
                        pendingCommand === release) pendingCommand = null
                    mutableState.value = result
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Revoked) { if (revision == generation) deny() }
            catch (_: ReportResolutionPermissionDeniedException) { if (revision == generation) deny() }
            catch (_: Exception) {
                ensureActive()
                if (revision == generation && !closed) {
                    try {
                        checkLease()
                        val command = pendingCommand?.takeIf { it.reportId == reportId }
                        mutableState.value = if (command != null) ReportResolutionState.Uncertain(command)
                            else ReportResolutionState.Error(reportId)
                    } catch (_: Revoked) { deny() }
                }
            }
        }
        reading = job
        mutableState.value = ReportResolutionState.Loading(reportId)
        job.start(); return true
    }

    private fun write(command: ReportResolutionCommand): Boolean {
        if (!permitted() || pendingCommand !== command || writer != null) return false
        retireReading()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var result: ReportResolutionState = ReportResolutionState.Uncertain(command)
            try {
                val event = withContext(dispatcher) {
                    checkLease()
                    // A recovered receipt wins before revision checks and never causes another write.
                    val receipt = readReceipt(command)
                    if (receipt != null) receipt else {
                        val page = readPage(ReportResolutionQuery(command.reportId))
                            ?: throw ReportResolutionNotFoundException()
                        if (!matches(page, command)) throw ReportResolutionConflictException()
                        checkLease(); ensureActive()
                        val saved = repository.record(command)
                        ensureActive(); checkLease()
                        require(saved.matches(command)); saved
                    }
                }
                ensureActive(); checkLease()
                result = ReportResolutionState.Confirmed(command, event)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Revoked) { deny() }
            catch (_: ReportResolutionPermissionDeniedException) { deny() }
            catch (_: Exception) { if (!closed) try { checkLease() } catch (_: Revoked) { deny() } }
            finally {
                // Navigating away does not release this exclusion while a noncooperative writer runs.
                writer = null
                if (!closed && visibleReport == command.reportId && pendingCommand === command) mutableState.value = result
            }
        }
        writer = job
        mutableState.value = ReportResolutionState.Working(command)
        job.start(); return true
    }

    private suspend fun readPage(query: ReportResolutionQuery): ReportResolutionPage? {
        checkLease()
        val page = repository.read(query)
        currentCoroutineContext().ensureActive(); checkLease()
        if (page != null) {
            require(page.reportId == query.reportId && page.events.size <= query.limit)
            val before = query.beforeSequence
            require(before == null || page.events.all { it.sequence < before })
            if (query.beforeSequence == null && page.latestSequence > 0) {
                val newest = page.events.first()
                require(newest.sequence == page.latestSequence && newest.toResolution == page.resolution)
            }
            require(page.latestSequence != 0L || page.events.isEmpty())
        }
        return page
    }

    private suspend fun readReceipt(command: ReportResolutionCommand): ReportResolutionEvent? {
        checkLease()
        val result = repository.receipt(command)
        currentCoroutineContext().ensureActive(); checkLease()
        require(result == null || result.matches(command))
        return result
    }

    private fun sameRevision(a: ReportResolutionPage, b: ReportResolutionPage) =
        a.reportId == b.reportId && a.latestSequence == b.latestSequence && a.resolution == b.resolution && a.actor == b.actor
    private fun matches(page: ReportResolutionPage, command: ReportResolutionCommand) =
        page.reportId == command.reportId && page.latestSequence == command.expectedSequence &&
            page.resolution == command.expectedResolution && page.actor == command.actor
    private fun accept(expected: ReportResolutionState) = permitted() && mutableState.value === expected
    private fun permitted(): Boolean {
        if (closed || !viewModelScope.isActive) return false
        return try { checkLease(); true } catch (_: Revoked) { deny(); false }
    }
    private fun checkLease() {
        try { checkSession() } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { throw Revoked() }
    }
    private fun retireReading() { ++generation; reading?.cancel(); reading = null; checking?.cancel(); checking = null }
    private fun deny() {
        closed = true; retireReading(); visibleReport = null; pendingCommand = null
        mutableState.value = ReportResolutionState.Denied
    }
    override fun onCleared() { deny() }
    private class Revoked : IllegalStateException()
    companion object {
        private const val MAX_PREVIOUS = 50
        private const val CHANGED = "El resultado o el permiso cambió. Revisa los datos actuales y prepara otra declaración."
        fun factory(repo: ReportResolutionRepository, checkSession: () -> Unit = {},
            dispatcher: CoroutineDispatcher = Dispatchers.IO): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportResolutionViewModel(repo, checkSession, dispatcher) }
        }
    }
}

internal fun resolutionReasonValid(reason: String): Boolean {
    if (reason != reason.trim() || reason.codePointCount(0, reason.length) !in 5..500 ||
        reason.any { it == '\u0000' || Character.isISOControl(it) }) return false
    var index = 0
    while (index < reason.length) {
        val character = reason[index++]
        if (Character.isHighSurrogate(character)) {
            if (index >= reason.length || !Character.isLowSurrogate(reason[index++])) return false
        } else if (Character.isLowSurrogate(character)) return false
    }
    return true
}
