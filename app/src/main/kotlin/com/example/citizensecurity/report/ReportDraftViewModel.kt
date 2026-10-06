package com.example.citizensecurity.report

import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NearbyLocationConfirmation
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportValidator
import java.time.DateTimeException
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReportDraftField {
    TYPE, PRIORITY, DESCRIPTION, OCCURRED_AT, LOCATION_REFERENCE, LOCATION_COORDINATES, SNAPSHOT,
}

data class ReportDraftState(
    val type: IncidentType? = null,
    val priority: Priority? = null,
    val description: String = "",
    val occurredAt: Instant? = null,
    val location: ReportLocation = ReportLocation(),
    val locationSelectionPending: Boolean = false,
    val inputErrors: Map<ReportDraftField, String> = emptyMap(),
)

sealed interface ReportDraftEditResult {
    data object Accepted : ReportDraftEditResult
    data object Closed : ReportDraftEditResult
    /** El control conserva la entrada rechazada; el modelo conserva su último texto aceptado. */
    data class Rejected(val field: ReportDraftField, val message: String) : ReportDraftEditResult
}

sealed interface ReportDraftReview {
    /** Valida campos, no cercanía actual ni autorización para insertar en SQLite. */
    data class Valid(val draft: NewReport) : ReportDraftReview
    data class Invalid(val errors: Map<ReportDraftField, String>) : ReportDraftReview
}

/** Identidad de esta apertura; no se serializa ni se puede reutilizar tras restaurar otro modelo. */
class ReportLocationSelectionToken internal constructor()

data class ReportLocationSelectionRequest(
    val token: ReportLocationSelectionToken,
    val originalLocation: ReportLocation,
)

/**
 * Borrador compartido por formulario y mapa con un mismo ViewModelStore y clave de modelo.
 * SavedStateHandle conserva solo campos primitivos cuando Android guarda el estado del dueño;
 * no es almacenamiento permanente, ni conserva una lectura GPS, permiso o confirmación vigente.
 * Ningún evento crea un Report, pide ubicación o llama al repositorio.
 */
@MainThread
class ReportDraftViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val validator: ReportValidator = ReportValidator(),
) : ViewModel() {
    private val problems = linkedMapOf<ReportDraftField, InputProblem>()
    private var selection: ReportLocationSelectionRequest? = null
    private var closed = false
    private val mutableState = MutableStateFlow(restore())
    val state: StateFlow<ReportDraftState> = mutableState.asStateFlow()

    init {
        persist(mutableState.value)
    }

    fun setType(type: IncidentType?): Boolean = edit(ReportDraftField.TYPE) { copy(type = type) }

    fun setPriority(priority: Priority?): Boolean = edit(ReportDraftField.PRIORITY) { copy(priority = priority) }

    fun setOccurredAt(occurredAt: Instant?): Boolean =
        edit(ReportDraftField.OCCURRED_AT) { copy(occurredAt = occurredAt) }

    /** Límites brutos explícitos: también cuentan los espacios exteriores, sin truncar. */
    fun setDescription(description: String): ReportDraftEditResult =
        editText(description, MAX_DESCRIPTION_CHARACTERS, InputProblem.DESCRIPTION_TOO_LONG) {
            copy(description = description)
        }

    fun setLocationReference(reference: String): ReportDraftEditResult =
        editText(reference, MAX_REFERENCE_CHARACTERS, InputProblem.REFERENCE_TOO_LONG) {
            copy(location = location.copy(reference = reference))
        }

    /** Abrir otra selección invalida la anterior y no altera ningún campo del borrador. */
    fun beginLocationSelection(): ReportLocationSelectionRequest? {
        if (closed) return null
        val request = ReportLocationSelectionRequest(ReportLocationSelectionToken(), state.value.location)
        selection = request
        publish(state.value.copy(locationSelectionPending = true))
        return request
    }

    /**
     * Solo recibe el resultado de confirmNearby del flujo vigente. El DTO no es una credencial:
     * el repositorio productivo debe volver a comprobar evidencia actual antes de escribir.
     * Aplica coordenadas y conserva la referencia y los demás campos editados entretanto.
     */
    fun applyLocationSelection(
        token: ReportLocationSelectionToken,
        result: NearbyLocationConfirmation.Confirmed,
    ): Boolean {
        if (closed || selection?.token !== token) return false
        val location = result.location
        if (!validCoordinates(location.latitude, location.longitude) ||
            location.latitude == null || location.longitude == null ||
            !result.distanceMeters.isFinite() || result.distanceMeters !in 0.0..5_000.0
        ) return false
        selection = null
        problems.remove(ReportDraftField.LOCATION_COORDINATES)
        publish(
            state.value.copy(
                location = state.value.location.copy(latitude = location.latitude, longitude = location.longitude),
                locationSelectionPending = false,
            ),
        )
        return true
    }

    /** La propuesta nunca tocó el borrador; cancelar conserva sus coordenadas y ediciones. */
    fun cancelLocationSelection(token: ReportLocationSelectionToken): Boolean {
        if (closed || selection?.token !== token) return false
        selection = null
        publish(state.value.copy(locationSelectionPending = false))
        return true
    }

    fun reset(): Boolean {
        if (closed) return false
        selection = null
        problems.clear()
        publish(ReportDraftState())
        return true
    }

    fun review(now: Instant): ReportDraftReview {
        val current = state.value
        val errors = problems.mapValues { it.value.message }.toMutableMap()
        if (closed) errors[ReportDraftField.SNAPSHOT] = "El borrador ya no está disponible."
        if (current.locationSelectionPending) {
            errors[ReportDraftField.LOCATION_COORDINATES] = "Confirma o cancela la selección del mapa."
        }
        if (current.type == null) errors[ReportDraftField.TYPE] = "Selecciona el tipo de incidente."
        if (current.priority == null) errors[ReportDraftField.PRIORITY] = "Selecciona la prioridad."
        if (current.occurredAt == null) errors[ReportDraftField.OCCURRED_AT] = "Agrega una fecha válida para el incidente."
        if (current.type != null && current.priority != null && current.occurredAt != null) {
            val draft = NewReport(current.type, current.priority, current.description, current.occurredAt, current.location)
            validator.validate(draft, now).errors.forEach { (field, message) ->
                errors[ReportDraftField.valueOf(field.name)] = message
            }
            if (errors.isEmpty()) return ReportDraftReview.Valid(draft)
        }
        return ReportDraftReview.Invalid(errors.toMap())
    }

    override fun onCleared() {
        closed = true
        selection = null
        mutableState.value = state.value.copy(locationSelectionPending = false)
    }

    private fun edit(field: ReportDraftField, transform: ReportDraftState.() -> ReportDraftState): Boolean {
        if (closed) return false
        problems.remove(field)
        publish(state.value.transform())
        return true
    }

    private fun editText(
        text: String,
        limit: Int,
        problem: InputProblem,
        transform: ReportDraftState.() -> ReportDraftState,
    ): ReportDraftEditResult {
        if (closed) return ReportDraftEditResult.Closed
        // El límite UTF-16 evita recorrer entradas enormes; los emojis válidos usan dos unidades.
        if (text.length > limit * 2 || text.codePointCount(0, text.length) > limit) {
            problems[problem.field] = problem
            publish(state.value)
            return ReportDraftEditResult.Rejected(problem.field, problem.message)
        }
        edit(problem.field, transform)
        return ReportDraftEditResult.Accepted
    }

    private fun publish(value: ReportDraftState) {
        val next = value.copy(inputErrors = problems.mapValues { it.value.message })
        persist(next)
        mutableState.value = next
    }

    private fun persist(value: ReportDraftState) {
        savedStateHandle[KEY_VERSION] = SNAPSHOT_VERSION
        savedStateHandle[KEY_TYPE] = value.type?.name
        savedStateHandle[KEY_PRIORITY] = value.priority?.name
        savedStateHandle[KEY_DESCRIPTION] = value.description
        savedStateHandle[KEY_REFERENCE] = value.location.reference
        savedStateHandle[KEY_SECONDS] = value.occurredAt?.epochSecond
        savedStateHandle[KEY_NANOS] = value.occurredAt?.nano
        savedStateHandle[KEY_LATITUDE] = value.location.latitude
        savedStateHandle[KEY_LONGITUDE] = value.location.longitude
        ReportDraftField.entries.forEach { field ->
            savedStateHandle[problemKey(field)] = problems[field]?.name
        }
    }

    private fun restore(): ReportDraftState {
        if (!savedStateHandle.contains(KEY_VERSION)) {
            if (savedStateHandle.keys().any { it.startsWith(PREFIX) }) {
                problems[ReportDraftField.SNAPSHOT] = InputProblem.INVALID_SNAPSHOT
            }
            return ReportDraftState(inputErrors = problems.mapValues { it.value.message })
        }
        if (savedStateHandle.get<Any?>(KEY_VERSION) != SNAPSHOT_VERSION) {
            problems[ReportDraftField.SNAPSHOT] = InputProblem.INVALID_SNAPSHOT
            return ReportDraftState(inputErrors = problems.mapValues { it.value.message })
        }
        ReportDraftField.entries.forEach { field ->
            val raw = savedStateHandle.get<Any?>(problemKey(field))
            if (raw != null) {
                val restored = InputProblem.entries.firstOrNull { it.name == raw && it.field == field }
                if (restored != null) problems[field] = restored
                else problems[ReportDraftField.SNAPSHOT] = InputProblem.INVALID_SNAPSHOT
            }
        }
        val type = readEnum(KEY_TYPE, IncidentType.entries, InputProblem.INVALID_TYPE) { it.name }
        val priority = readEnum(KEY_PRIORITY, Priority.entries, InputProblem.INVALID_PRIORITY) { it.name }
        val description = readText(KEY_DESCRIPTION, MAX_DESCRIPTION_CHARACTERS, InputProblem.INVALID_DESCRIPTION)
        val reference = readText(KEY_REFERENCE, MAX_REFERENCE_CHARACTERS, InputProblem.INVALID_REFERENCE)
        val seconds = savedStateHandle.get<Any?>(KEY_SECONDS)
        val nanos = savedStateHandle.get<Any?>(KEY_NANOS)
        val occurredAt = if (seconds == null && nanos == null) null else {
            if (seconds is Long && nanos is Int && nanos in 0..999_999_999) {
                try {
                    Instant.ofEpochSecond(seconds, nanos.toLong())
                } catch (_: DateTimeException) {
                    problems[ReportDraftField.OCCURRED_AT] = InputProblem.INVALID_DATE
                    null
                }
            } else {
                problems[ReportDraftField.OCCURRED_AT] = InputProblem.INVALID_DATE
                null
            }
        }
        val rawLatitude = savedStateHandle.get<Any?>(KEY_LATITUDE)
        val rawLongitude = savedStateHandle.get<Any?>(KEY_LONGITUDE)
        val latitude = rawLatitude as? Double
        val longitude = rawLongitude as? Double
        if ((rawLatitude != null && latitude == null) || (rawLongitude != null && longitude == null) ||
            !validCoordinates(latitude, longitude)
        ) problems[ReportDraftField.LOCATION_COORDINATES] = InputProblem.INVALID_COORDINATES
        return ReportDraftState(
            type, priority, description, occurredAt, ReportLocation(reference, latitude, longitude),
            inputErrors = problems.mapValues { it.value.message },
        )
    }

    private fun readText(key: String, limit: Int, problem: InputProblem): String {
        val raw = savedStateHandle.get<Any?>(key)
        if (raw is String && raw.length <= limit * 2 && raw.codePointCount(0, raw.length) <= limit) return raw
        problems[problem.field] = problem
        return ""
    }

    private fun <T> readEnum(key: String, values: List<T>, problem: InputProblem, name: (T) -> String): T? {
        val raw = savedStateHandle.get<Any?>(key) ?: return null
        return values.firstOrNull { name(it) == raw }.also {
            if (it == null) problems[problem.field] = problem
        }
    }

    private enum class InputProblem(val field: ReportDraftField, val message: String) {
        DESCRIPTION_TOO_LONG(ReportDraftField.DESCRIPTION, "La descripción admite hasta 1000 caracteres, incluidos los espacios."),
        REFERENCE_TOO_LONG(ReportDraftField.LOCATION_REFERENCE, "La referencia admite hasta 200 caracteres, incluidos los espacios."),
        INVALID_SNAPSHOT(ReportDraftField.SNAPSHOT, "No se pudo recuperar el borrador. Inicia uno nuevo."),
        INVALID_TYPE(ReportDraftField.TYPE, "Vuelve a seleccionar el tipo de incidente."),
        INVALID_PRIORITY(ReportDraftField.PRIORITY, "Vuelve a seleccionar la prioridad."),
        INVALID_DESCRIPTION(ReportDraftField.DESCRIPTION, "Vuelve a escribir la descripción del incidente."),
        INVALID_DATE(ReportDraftField.OCCURRED_AT, "Vuelve a indicar una fecha válida para el incidente."),
        INVALID_REFERENCE(ReportDraftField.LOCATION_REFERENCE, "Vuelve a escribir la referencia de ubicación."),
        INVALID_COORDINATES(ReportDraftField.LOCATION_COORDINATES, "Vuelve a confirmar una ubicación válida en el mapa."),
    }

    companion object {
        const val MAX_DESCRIPTION_CHARACTERS = 1_000
        const val MAX_REFERENCE_CHARACTERS = 200
        private const val SNAPSHOT_VERSION = 1
        private const val PREFIX = "reportDraft."
        private const val KEY_VERSION = PREFIX + "version"
        private const val KEY_TYPE = PREFIX + "type"
        private const val KEY_PRIORITY = PREFIX + "priority"
        private const val KEY_DESCRIPTION = PREFIX + "description"
        private const val KEY_REFERENCE = PREFIX + "reference"
        private const val KEY_SECONDS = PREFIX + "occurredAtSeconds"
        private const val KEY_NANOS = PREFIX + "occurredAtNanos"
        private const val KEY_LATITUDE = PREFIX + "latitude"
        private const val KEY_LONGITUDE = PREFIX + "longitude"

        private fun problemKey(field: ReportDraftField): String = PREFIX + "problem." + field.name

        private fun validCoordinates(latitude: Double?, longitude: Double?): Boolean =
            if (latitude == null && longitude == null) true
            else latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0

        /** ComponentActivity proporciona CreationExtras; conserva el mismo dueño y clave en el mapa. */
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReportDraftViewModel(createSavedStateHandle()) }
        }
    }
}
