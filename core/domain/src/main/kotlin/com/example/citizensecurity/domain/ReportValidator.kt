package com.example.citizensecurity.domain

import java.time.Duration
import java.time.Instant

enum class ReportField {
    DESCRIPTION,
    LOCATION_REFERENCE,
    LOCATION_COORDINATES,
    OCCURRED_AT,
}

/** Los campos permiten que cada interfaz ubique el mensaje junto al dato correspondiente. */
data class ValidationErrors(val errors: Map<ReportField, String>) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

class InvalidReportException(val errors: ValidationErrors) :
    IllegalArgumentException(errors.errors.values.joinToString(" "))

/** Reglas independientes de Android; [now] hace que la validación de fechas sea comprobable. */
class ReportValidator {
    fun validate(draft: NewReport, now: Instant): ValidationErrors {
        val errors = linkedMapOf<ReportField, String>()
        val description = draft.description.trim()
        val descriptionLength = description.characterCount()
        when {
            '\u0000' in description -> {
                errors[ReportField.DESCRIPTION] = "La descripción contiene un carácter no permitido."
            }
            descriptionLength !in 10..1_000 -> {
                errors[ReportField.DESCRIPTION] =
                    "La descripción debe contener entre 10 y 1000 caracteres."
            }
        }

        val location = draft.location
        val reference = location.reference.trim()
        val referenceLength = reference.characterCount()
        val latitude = location.latitude
        val longitude = location.longitude
        val hasLatitude = latitude != null
        val hasLongitude = longitude != null

        when {
            hasLatitude != hasLongitude -> {
                errors[ReportField.LOCATION_COORDINATES] =
                    "Agrega tanto la latitud como la longitud."
            }
            latitude != null && longitude != null &&
                (!latitude.isFinite() || !longitude.isFinite() ||
                    latitude !in -90.0..90.0 || longitude !in -180.0..180.0) -> {
                errors[ReportField.LOCATION_COORDINATES] =
                    "Las coordenadas deben ser finitas: latitud entre -90 y 90, " +
                    "y longitud entre -180 y 180."
            }
        }

        when {
            '\u0000' in reference -> {
                errors[ReportField.LOCATION_REFERENCE] =
                    "La referencia de ubicación contiene un carácter no permitido."
            }
            referenceLength == 0 && !hasLatitude && !hasLongitude -> {
                errors[ReportField.LOCATION_REFERENCE] =
                    "Agrega una referencia de ubicación o coordenadas completas."
            }
            referenceLength != 0 && referenceLength !in 5..200 -> {
                errors[ReportField.LOCATION_REFERENCE] =
                    "La referencia de ubicación debe contener entre 5 y 200 caracteres."
            }
        }

        when {
            draft.occurredAt.isBefore(Instant.EPOCH) -> {
                errors[ReportField.OCCURRED_AT] =
                    "La fecha del incidente debe ser igual o posterior al 1 de enero de 1970."
            }
            Duration.between(now, draft.occurredAt) > Duration.ofSeconds(60) -> {
                errors[ReportField.OCCURRED_AT] =
                    "La fecha del incidente no puede estar más de un minuto en el futuro."
            }
        }

        return ValidationErrors(errors.toMap())
    }

    private fun String.characterCount(): Int = codePointCount(0, length)
}
