package com.example.citizensecurity.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportDraftCheckpoint
import com.example.citizensecurity.domain.ReportDraftCheckpointJournal
import com.example.citizensecurity.domain.ReportValidator
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ReportDraftCheckpointState(
    val checkpoint: ReportDraftCheckpoint? = null,
    val loaded: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val failure: Boolean = false,
    val restoreReady: ReportDraftCheckpoint? = null,
    val uncertainWrite: ReportDraftCheckpoint? = null,
)

/**
 * Conserva un borrador por un gesto explícito. La lectura inicial no restaura el formulario,
 * pide ubicación ni crea un reporte. Cada escritura usa la revisión leída como condición:
 * ante una respuesta incierta hay que recargar, nunca sobrescribir desde una revisión vieja.
 */
class ReportDraftCheckpointViewModel(
    private val journal: ReportDraftCheckpointJournal,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReportDraftCheckpointState())
    val state: StateFlow<ReportDraftCheckpointState> = mutableState.asStateFlow()
    private var activeJob: Job? = null
    private var generation = 0L

    init {
        reload()
    }

    /** Solo lee el checkpoint; la pantalla decide si el usuario solicita restaurarlo. */
    fun reload(): Boolean {
        if (!canBegin()) return false
        val previous = mutableState.value
        launchOperation(
            initial = previous.copy(
                loaded = false, busy = true, message = null, failure = false, restoreReady = null,
            ),
            failureMessage = "No se pudo leer el borrador guardado. Recarga antes de guardar o restaurar.",
        ) {
            val persisted = journal.read()
            ensureActive()
            mutableState.value = ReportDraftCheckpointState(checkpoint = persisted, loaded = true)
        }
        return true
    }

    /** Revalida la revisión después de que el usuario confirme la restauración. */
    fun requestRestore(expectedId: String): Boolean {
        if (!canBegin() || !expectedId.isCanonicalUuid()) return false
        val previous = mutableState.value
        launchOperation(
            initial = previous.copy(
                loaded = false, busy = true, message = null, failure = false, restoreReady = null,
            ),
            failureMessage = "No se pudo comprobar el borrador guardado. Recarga antes de restaurarlo.",
        ) {
            val current = journal.read()
            ensureActive()
            val ready = current?.takeIf { it.id == expectedId && it.submittedFolio == null }
            mutableState.value = ReportDraftCheckpointState(
                checkpoint = current,
                loaded = true,
                restoreReady = ready,
                message = when {
                    current == null -> "Ya no hay un borrador guardado para restaurar."
                    current.id != expectedId -> "El borrador guardado cambió. Revisa la copia actual antes de restaurarla."
                    current.submittedFolio != null -> "Este borrador ya tiene un reporte guardado. Abre su folio."
                    else -> null
                },
            )
        }
        return true
    }

    /** Consume el evento una vez; recrear la pantalla no vuelve a sustituir las correcciones. */
    fun acknowledgeRestore(id: String): Boolean {
        if (!viewModelScope.isActive) return false
        val previous = mutableState.value
        if (previous.restoreReady?.id != id) return false
        return mutableState.compareAndSet(previous, previous.copy(restoreReady = null))
    }

    /** Reutiliza la identidad de la copia coincidente, incluso ante una escritura incierta. */
    fun preferredRequestId(draft: NewReport): String? {
        val current = mutableState.value
        val normalized = draft.normalized()
        val candidate = current.uncertainWrite?.takeIf { it.draft.normalized() == normalized }
            ?: current.checkpoint?.takeIf { current.loaded && it.draft.normalized() == normalized }
        return candidate?.submittedFolio ?: candidate?.id
    }

    /**
     * Un guardado local no es una solicitud de reporte ni fabrica evidencia GPS. Volver a
     * guardar el mismo cuerpo conserva la identidad completa y comprueba de nuevo su CAS;
     * solo cambiar campos crea una revisión nueva.
     */
    fun save(draft: NewReport): Boolean {
        if (!canBegin()) return false
        val previous = mutableState.value
        if (!previous.loaded) return false
        val normalized = draft.normalized()
        val now = clock.instant()
        val validation = ReportValidator().validate(normalized, now)
        if (!validation.isValid) {
            mutableState.value = previous.copy(
                message = validation.errors.values.joinToString(" "),
                failure = true,
                restoreReady = null,
            )
            return false
        }
        val next = previous.checkpoint?.takeIf { it.draft.normalized() == normalized }
            ?: ReportDraftCheckpoint(UUID.randomUUID().toString(), normalized, now)
        launchOperation(
            initial = previous.copy(
                busy = true, message = null, failure = false, restoreReady = null, uncertainWrite = next,
            ),
            failureMessage = "No se pudo confirmar el borrador guardado. Recarga antes de intentarlo otra vez.",
        ) {
            journal.write(
                next,
                expectedId = previous.checkpoint?.id,
                expectedSubmittedFolio = previous.checkpoint?.submittedFolio,
            )
            ensureActive()
            mutableState.value = ReportDraftCheckpointState(
                checkpoint = next,
                loaded = true,
                message = if (next.submittedFolio == null) {
                    "Borrador guardado en este teléfono. Todavía no es un reporte."
                } else {
                    "Este borrador ya tiene un reporte guardado. Puedes abrir su folio."
                },
            )
        }
        return true
    }

    /**
     * Vincula un folio confirmado al mismo contenido, sin depender del UUID del checkpoint.
     * Solo lo muestra como enviado después de que el diario confirma la escritura. Un fallo
     * conserva el checkpoint y se informa; no altera el reporte que ya está confirmado.
     */
    fun markSubmittedIfMatching(report: Report): Boolean {
        if (!canBegin()) return false
        val previous = mutableState.value
        if (!previous.loaded) return false
        val checkpoint = previous.checkpoint ?: return false
        if (checkpoint.submittedFolio != null || !checkpoint.draft.matches(report)) return false
        launchOperation(
            initial = previous.copy(busy = true, message = null, failure = false, restoreReady = null),
            failureMessage = "El reporte está guardado, pero no se pudo vincular su folio al borrador. Recarga para comprobarlo.",
        ) {
            journal.markSubmitted(checkpoint.id, report.id)
            ensureActive()
            mutableState.value = ReportDraftCheckpointState(
                checkpoint = checkpoint.copy(submittedFolio = report.id),
                loaded = true,
                message = "Este borrador ya tiene un reporte guardado. Puedes abrir su folio.",
            )
        }
        return true
    }

    private fun canBegin(): Boolean = viewModelScope.isActive && activeJob == null

    private fun launchOperation(
        initial: ReportDraftCheckpointState,
        failureMessage: String,
        operation: suspend CoroutineScope.() -> Unit,
    ) {
        val token = ++generation
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                mutableState.value = mutableState.value.copy(
                    loaded = false,
                    busy = false,
                    message = failureMessage,
                    failure = true,
                )
            }
        }
        activeJob = job
        job.invokeOnCompletion { cause ->
            if (generation == token) {
                activeJob = null
                if (cause is CancellationException) {
                    mutableState.value = mutableState.value.copy(
                        loaded = false,
                        busy = false,
                        message = "La operación del borrador se interrumpió. Recarga para comprobar su estado.",
                        failure = true,
                    )
                }
            }
        }
        // Reservar primero evita dos operaciones ante un colector reentrante de busy.
        mutableState.value = initial
        job.start()
    }

    private fun NewReport.normalized() = copy(
        description = description.trim(),
        location = location.copy(
            reference = location.reference.trim(),
            latitude = location.latitude?.let { if (it == 0.0) 0.0 else it },
            longitude = location.longitude?.let { if (it == 0.0) 0.0 else it },
        ),
    )

    private fun NewReport.matches(report: Report): Boolean {
        val normalized = normalized()
        return report.type == normalized.type && report.priority == normalized.priority &&
            report.description == normalized.description && report.occurredAt == normalized.occurredAt &&
            report.location == normalized.location && report.photo == normalized.photo
    }

    private fun String.isCanonicalUuid(): Boolean =
        runCatching { UUID.fromString(this).toString() == this }.getOrDefault(false)

    companion object {
        fun factory(journal: ReportDraftCheckpointJournal): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportDraftCheckpointViewModel(journal) }
        }
    }
}
