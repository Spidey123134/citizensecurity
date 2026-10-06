package com.example.citizensecurity.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportValidatorTest {
    private val now = Instant.parse("2026-10-03T03:00:00Z")
    private val validator = ReportValidator()

    @Test
    fun validReferenceAllowsLocationWithoutCoordinates() {
        assertTrue(validate().isValid)
    }

    @Test
    fun descriptionAcceptsTenUnicodeCharacters() {
        assertTrue(validate(draft(description = "🔥".repeat(10))).isValid)
    }

    @Test
    fun descriptionAcceptsOneThousandUnicodeCharacters() {
        assertTrue(validate(draft(description = "🔥".repeat(1_000))).isValid)
    }

    @Test
    fun descriptionRejectsNineUnicodeCharactersEvenWhenUtf16LengthIsLonger() {
        assertError(draft(description = "🔥".repeat(9)), ReportField.DESCRIPTION)
    }

    @Test
    fun descriptionRejectsOneThousandAndOneUnicodeCharacters() {
        assertError(draft(description = "🔥".repeat(1_001)), ReportField.DESCRIPTION)
    }

    @Test
    fun surroundingWhitespaceDoesNotCompleteMinimumDescription() {
        assertError(draft(description = " \t123456789\n "), ReportField.DESCRIPTION)
    }

    @Test
    fun surroundingUnicodeWhitespaceDoesNotExceedDescriptionMaximum() {
        assertTrue(validate(draft(description = "\u2003" + "á".repeat(1_000) + "\u2003")).isValid)
    }

    @Test
    fun blankDescriptionIsRejected() {
        assertError(draft(description = " \t\n"), ReportField.DESCRIPTION)
    }

    @Test
    fun descriptionRejectsNullCharacterWithinAnOtherwiseValidText() {
        assertError(draft(description = "Hay un árbol\u0000 caído sobre la banqueta."), ReportField.DESCRIPTION)
    }

    @Test
    fun descriptionRejectsUnpairedSurrogates() {
        for (suffix in listOf("\uD800", "\uDC00", "\uD800x", "\uDC00\uD800")) {
            assertError(draft(description = "123456789$suffix"), ReportField.DESCRIPTION)
        }
    }

    @Test
    fun referenceRejectsUnpairedSurrogatesEvenWithValidCoordinates() {
        for (suffix in listOf("\uD800", "\uDC00", "\uD800x", "\uDC00\uD800")) {
            assertError(
                draft(location = ReportLocation("1234$suffix", latitude = 19.4, longitude = -99.1)),
                ReportField.LOCATION_REFERENCE,
            )
        }
    }

    @Test
    fun initialByteOrderMarksAreRejectedAfterTrimming() {
        for (mark in listOf('\uFEFF', '\uFFFE')) {
            val errors = validate(draft(
                description = "\t$mark" + "123456789",
                location = ReportLocation(" $mark" + "1234"),
            ))
            assertTrue(errors.errors.containsKey(ReportField.DESCRIPTION))
            assertTrue(errors.errors.containsKey(ReportField.LOCATION_REFERENCE))
        }
    }

    @Test
    fun validSurrogatePairsAndAnInternalByteOrderMarkRemainValid() {
        assertTrue(validate(draft(
            description = "Árbol 🔥 junto\uFEFF al parque.",
            location = ReportLocation("Parque 🏠 con\uFEFF entrada"),
        )).isValid)
    }

    @Test
    fun referenceAcceptsFiveUnicodeCharacters() {
        assertTrue(validate(draft(location = ReportLocation("🏠".repeat(5)))).isValid)
    }

    @Test
    fun referenceAcceptsTwoHundredUnicodeCharactersAfterTrimming() {
        assertTrue(validate(draft(location = ReportLocation("  " + "🏠".repeat(200) + "\n"))).isValid)
    }

    @Test
    fun referenceRejectsFourUnicodeCharacters() {
        assertError(draft(location = ReportLocation("🏠".repeat(4))), ReportField.LOCATION_REFERENCE)
    }

    @Test
    fun referenceRejectsTwoHundredAndOneUnicodeCharacters() {
        assertError(draft(location = ReportLocation("🏠".repeat(201))), ReportField.LOCATION_REFERENCE)
    }

    @Test
    fun referenceRejectsNullCharacterEvenWhenCoordinatesAreValid() {
        assertError(
            draft(location = ReportLocation("Frente\u0000 al parque", latitude = 19.4, longitude = -99.1)),
            ReportField.LOCATION_REFERENCE,
        )
    }

    @Test
    fun locationRequiresReferenceOrCoordinates() {
        assertError(draft(location = ReportLocation(" \t")), ReportField.LOCATION_REFERENCE)
    }

    @Test
    fun validCoordinatesAllowAnEmptyReference() {
        assertTrue(validate(draft(location = ReportLocation(latitude = 0.0, longitude = 0.0))).isValid)
    }

    @Test
    fun geographicCoordinateBoundariesAreAccepted() {
        for (latitude in listOf(-90.0, 90.0)) {
            for (longitude in listOf(-180.0, 180.0)) {
                assertTrue(
                    "Límites geográficos: $latitude, $longitude",
                    validate(draft(location = ReportLocation(latitude = latitude, longitude = longitude))).isValid,
                )
            }
        }
    }

    @Test
    fun partialCoordinatesAreRejectedEvenWithAReference() {
        for (location in listOf(
            ReportLocation("Frente al parque", latitude = 19.4),
            ReportLocation("Frente al parque", longitude = -99.1),
        )) {
            assertError(draft(location = location), ReportField.LOCATION_COORDINATES)
        }
    }

    @Test
    fun coordinateMessagesRemainStableForIncompleteAndInvalidPoints() {
        val incomplete = validate(draft(location = ReportLocation("Frente al parque", latitude = 19.4)))
        val invalid = validate(draft(location = ReportLocation("Frente al parque", latitude = 91.0, longitude = 0.0)))

        assertEquals(
            "Agrega tanto la latitud como la longitud.",
            incomplete.errors[ReportField.LOCATION_COORDINATES],
        )
        assertEquals(
            "Las coordenadas deben ser finitas: latitud entre -90 y 90, " +
                "y longitud entre -180 y 180.",
            invalid.errors[ReportField.LOCATION_COORDINATES],
        )
    }

    @Test
    fun outOfRangeCoordinatesAreRejectedEvenWithAReference() {
        for (location in listOf(
            ReportLocation("Frente al parque", latitude = 90.0001, longitude = 0.0),
            ReportLocation("Frente al parque", latitude = -90.0001, longitude = 0.0),
            ReportLocation("Frente al parque", latitude = 0.0, longitude = 180.0001),
            ReportLocation("Frente al parque", latitude = 0.0, longitude = -180.0001),
        )) {
            assertError(draft(location = location), ReportField.LOCATION_COORDINATES)
        }
    }

    @Test
    fun nonFiniteCoordinatesAreRejectedEvenWithAReference() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertError(
                draft(location = ReportLocation("Frente al parque", latitude = value, longitude = 0.0)),
                ReportField.LOCATION_COORDINATES,
            )
            assertError(
                draft(location = ReportLocation("Frente al parque", latitude = 0.0, longitude = value)),
                ReportField.LOCATION_COORDINATES,
            )
        }
    }

    @Test
    fun suppliedReferenceStillRequiresValidLengthWithCoordinates() {
        assertError(
            draft(location = ReportLocation("a".repeat(201), latitude = 19.4, longitude = -99.1)),
            ReportField.LOCATION_REFERENCE,
        )
        assertError(
            draft(location = ReportLocation("abc", latitude = 19.4, longitude = -99.1)),
            ReportField.LOCATION_REFERENCE,
        )
    }

    @Test
    fun epochIsAccepted() {
        assertTrue(validate(draft(occurredAt = Instant.EPOCH)).isValid)
    }

    @Test
    fun datesBeforeEpochAreRejected() {
        assertError(draft(occurredAt = Instant.EPOCH.minusNanos(1)), ReportField.OCCURRED_AT)
    }

    @Test
    fun incidentExactlyOneMinuteAheadIsAccepted() {
        assertTrue(validate(draft(occurredAt = now.plusSeconds(60))).isValid)
    }

    @Test
    fun incidentMoreThanOneMinuteAheadIsRejected() {
        assertError(draft(occurredAt = now.plusSeconds(60).plusNanos(1)), ReportField.OCCURRED_AT)
    }

    @Test
    fun maximumInstantDoesNotOverflowFutureValidation() {
        assertTrue(validator.validate(draft(occurredAt = Instant.MAX), Instant.MAX).isValid)
    }

    @Test
    fun allInvalidFieldsAreReturnedTogether() {
        val errors = validate(draft(
            description = "",
            occurredAt = now.plusSeconds(61),
            location = ReportLocation("abc", latitude = 100.0, longitude = 0.0),
        ))

        assertFalse(errors.isValid)
        assertEquals(
            setOf(
                ReportField.DESCRIPTION,
                ReportField.LOCATION_REFERENCE,
                ReportField.LOCATION_COORDINATES,
                ReportField.OCCURRED_AT,
            ),
            errors.errors.keys,
        )
    }

    @Test
    fun rejectionRetainsFieldErrorsAndReadableMessages() {
        val errors = validate(draft(description = ""))
        val exception = InvalidReportException(errors)

        assertSame(errors, exception.errors)
        assertEquals(errors.errors.values.joinToString(" "), exception.message)
        assertTrue(exception.message.orEmpty().contains("descripción"))
    }

    private fun draft(
        description: String = "Hay un árbol caído sobre la banqueta.",
        occurredAt: Instant = now,
        location: ReportLocation = ReportLocation("Frente a la biblioteca"),
    ) = NewReport(
        type = IncidentType.RISK,
        priority = Priority.MEDIUM,
        description = description,
        occurredAt = occurredAt,
        location = location,
    )

    private fun validate(draft: NewReport = draft()): ValidationErrors = validator.validate(draft, now)

    private fun assertError(draft: NewReport, field: ReportField) {
        val errors = validate(draft)
        assertFalse(errors.isValid)
        assertTrue("Falta el error de $field: ${errors.errors}", errors.errors.containsKey(field))
    }
}
