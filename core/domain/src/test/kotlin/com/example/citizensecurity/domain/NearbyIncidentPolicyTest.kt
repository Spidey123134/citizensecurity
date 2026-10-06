package com.example.citizensecurity.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NearbyIncidentPolicyTest {
    private val now = 300_000_000_000L
    private val policy = NearbyIncidentPolicy()
    private val mexicoCity = ReportLocation("Punto de referencia", 19.4326077, -99.1332088)
    private val mexicoFix = DeviceLocationFix(19.4326077, -99.1332088, 10.0, now, false)

    @Test
    fun defaultConfigurationUsesFiveKilometersOneHundredMetersAndTwoMinutes() {
        assertEquals(5_000.0, policy.maxDistanceMeters, 0.0)
        assertEquals(100.0, policy.maxAccuracyMeters, 0.0)
        assertEquals(120_000_000_000L, policy.maxFixAgeNanos)
    }

    @Test
    fun thePhonePositionAndANearbyIncidentAreAllowed() {
        val samePoint = allowed(policy.evaluate(mexicoCity, mexicoFix, now))
        val nearby = allowed(policy.evaluate(point(19.44, -99.13), mexicoFix, now))

        assertEquals(0.0, samePoint.distanceMeters, 0.0)
        assertEquals(10.0, samePoint.upperEstimateDistanceMeters, 0.0)
        assertTrue(nearby.distanceMeters in 800.0..1_000.0)
        assertEquals(nearby.distanceMeters + 10.0, nearby.upperEstimateDistanceMeters, 0.0)
    }

    @Test
    fun parisIsRejectedWhenTheDeviceLocationIsMexicoCity() {
        val rejection = rejected(policy.evaluate(point(48.8566, 2.3522), mexicoFix, now))

        assertEquals(NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS, rejection.reason)
        assertTrue(rejection.distanceMeters!! in 9_000_000.0..9_500_000.0)
        assertFalse(rejection.message.isBlank())
    }

    @Test
    fun evaluatingANewPointCannotReusePermissionForAnEarlierNearbyPoint() {
        assertTrue(policy.evaluate(mexicoCity, mexicoFix, now) is NearbyIncidentDecision.Allowed)
        assertReason(
            NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS,
            policy.evaluate(point(48.8566, 2.3522), mexicoFix, now),
        )
        assertTrue(policy.evaluate(mexicoCity, mexicoFix, now) is NearbyIncidentDecision.Allowed)
    }

    @Test
    fun aPhoneInFranceCanReportNearbyWithoutAnArbitraryCountryRestriction() {
        val fix = DeviceLocationFix(48.8566, 2.3522, 10.0, now, false)

        assertTrue(policy.evaluate(point(48.8584, 2.2945), fix, now) is NearbyIncidentDecision.Allowed)
    }

    @Test
    fun anIncidentWithinFiveKilometersIsRejectedIfAccuracyWouldExceedTheRadius() {
        // Un grado en el ecuador mide 111 195,0802335 m con el radio medio IUGG.
        // 0,0441 grados son aproximadamente 4 903,7 m: queda dentro de 5 km,
        // pero su suma con 100 m de precisión supera el límite.
        val fix = DeviceLocationFix(0.0, 0.0, 100.0, now, false)
        val decision = rejected(policy.evaluate(point(0.0, 0.0441), fix, now))

        assertEquals(NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS, decision.reason)
        assertTrue(decision.distanceMeters!! in 4_900.0..5_000.0)
    }

    @Test
    fun anIncidentWithinTheRadiusAndAccuracyMarginIsAllowed() {
        val fix = DeviceLocationFix(0.0, 0.0, 100.0, now, false)
        val decision = allowed(policy.evaluate(point(0.0, 0.044), fix, now))

        assertTrue(decision.distanceMeters in 4_890.0..4_900.0)
        assertTrue(decision.upperEstimateDistanceMeters <= 5_000.0)
    }

    @Test
    fun aSumExactlyAtTheConfiguredRadiusIsAllowedAndTheNextRepresentableValueIsRejected() {
        val strict = NearbyIncidentPolicy(maxDistanceMeters = 100.0, maxAccuracyMeters = 101.0)
        val exact = mexicoFix.copy(accuracyMeters = 100.0)
        val outside = exact.copy(accuracyMeters = Math.nextUp(100.0))

        assertEquals(100.0, allowed(strict.evaluate(mexicoCity, exact, now)).upperEstimateDistanceMeters, 0.0)
        assertReason(
            NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS,
            strict.evaluate(mexicoCity, outside, now),
        )
    }

    @Test
    fun aMissingDeviceLocationNeverApprovesTheIncident() {
        assertReason(
            NearbyIncidentRejection.DEVICE_LOCATION_REQUIRED,
            policy.evaluate(mexicoCity, null, now),
        )
    }

    @Test
    fun aWrittenReferenceOrAnIncompleteIncidentPointCannotBypassTheRule() {
        for (incident in listOf(
            ReportLocation("Frente al parque"),
            ReportLocation("Frente al parque", latitude = 19.4),
            ReportLocation("Frente al parque", longitude = -99.1),
        )) {
            assertReason(
                NearbyIncidentRejection.INCIDENT_COORDINATES_REQUIRED,
                policy.evaluate(incident, mexicoFix, now),
            )
        }
    }

    @Test
    fun invalidIncidentCoordinatesReuseTheSharedCoordinateValidation() {
        for ((latitude, longitude) in invalidCoordinates()) {
            val rejection = rejected(policy.evaluate(point(latitude, longitude), mexicoFix, now))

            assertEquals(NearbyIncidentRejection.INVALID_INCIDENT_COORDINATES, rejection.reason)
            assertEquals(coordinateError(latitude, longitude), rejection.message)
        }
    }

    @Test
    fun invalidDeviceCoordinatesAreRejectedInsteadOfProducingNaNDistance() {
        for ((latitude, longitude) in invalidCoordinates()) {
            assertReason(
                NearbyIncidentRejection.INVALID_DEVICE_COORDINATES,
                policy.evaluate(mexicoCity, mexicoFix.copy(latitude = latitude, longitude = longitude), now),
            )
        }
    }

    @Test
    fun zeroAccuracyIsValidWhenAndroidProvidesIt() {
        val decision = allowed(policy.evaluate(mexicoCity, mexicoFix.copy(accuracyMeters = 0.0), now))

        assertEquals(0.0, decision.upperEstimateDistanceMeters, 0.0)
    }

    @Test
    fun negativeOrNonFiniteAccuracyIsRejected() {
        for (accuracy in listOf(-Double.MIN_VALUE, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertReason(
                NearbyIncidentRejection.INVALID_ACCURACY,
                policy.evaluate(mexicoCity, mexicoFix.copy(accuracyMeters = accuracy), now),
            )
        }
    }

    @Test
    fun maximumAccuracyIsInclusiveAndTheNextRepresentableValueIsRejected() {
        assertTrue(
            policy.evaluate(mexicoCity, mexicoFix.copy(accuracyMeters = 100.0), now) is
                NearbyIncidentDecision.Allowed,
        )
        assertReason(
            NearbyIncidentRejection.INACCURATE_DEVICE_LOCATION,
            policy.evaluate(mexicoCity, mexicoFix.copy(accuracyMeters = Math.nextUp(100.0)), now),
        )
    }

    @Test
    fun aMockDeviceLocationIsRejectedEvenWhenThePointIsClose() {
        assertReason(
            NearbyIncidentRejection.MOCK_DEVICE_LOCATION,
            policy.evaluate(mexicoCity, mexicoFix.copy(isMock = true), now),
        )
    }

    @Test
    fun ageOfExactlyTwoMinutesIsAllowedAndOneNanosecondOlderIsRejected() {
        val oldestAllowed = mexicoFix.copy(elapsedRealtimeNanos = now - 120_000_000_000L)

        assertTrue(policy.evaluate(mexicoCity, oldestAllowed, now) is NearbyIncidentDecision.Allowed)
        assertReason(
            NearbyIncidentRejection.STALE_DEVICE_LOCATION,
            policy.evaluate(mexicoCity, oldestAllowed.copy(elapsedRealtimeNanos = oldestAllowed.elapsedRealtimeNanos - 1L), now),
        )
    }

    @Test
    fun aFutureTimestampIsRejectedEvenWhenOnlyOneNanosecondAhead() {
        for (timestamp in listOf(now + 1L, Long.MAX_VALUE)) {
            assertReason(
                NearbyIncidentRejection.FUTURE_DEVICE_LOCATION,
                policy.evaluate(mexicoCity, mexicoFix.copy(elapsedRealtimeNanos = timestamp), now),
            )
        }
    }

    @Test
    fun negativeMonotonicTimesAreRejectedBeforeSubtracting() {
        for (timestamp in listOf(-1L, Long.MIN_VALUE)) {
            assertReason(
                NearbyIncidentRejection.INVALID_MONOTONIC_TIME,
                policy.evaluate(mexicoCity, mexicoFix.copy(elapsedRealtimeNanos = timestamp), now),
            )
            assertReason(
                NearbyIncidentRejection.INVALID_MONOTONIC_TIME,
                policy.evaluate(mexicoCity, mexicoFix, timestamp),
            )
        }
    }

    @Test
    fun zeroMonotonicTimestampIsValidAtTheBeginningOfTheBoot() {
        val beginning = mexicoFix.copy(elapsedRealtimeNanos = 0L)

        assertTrue(policy.evaluate(mexicoCity, beginning, 0L) is NearbyIncidentDecision.Allowed)
        assertTrue(policy.evaluate(mexicoCity, beginning, 120_000_000_000L) is NearbyIncidentDecision.Allowed)
    }

    @Test
    fun extremeMonotonicTimesCannotOverflowIntoAFreshReading() {
        assertReason(
            NearbyIncidentRejection.STALE_DEVICE_LOCATION,
            policy.evaluate(mexicoCity, mexicoFix.copy(elapsedRealtimeNanos = 0L), Long.MAX_VALUE),
        )
        assertTrue(
            policy.evaluate(mexicoCity, mexicoFix.copy(elapsedRealtimeNanos = Long.MAX_VALUE), Long.MAX_VALUE) is
                NearbyIncidentDecision.Allowed,
        )
    }

    @Test
    fun maximumLongAgeCanBeConfiguredWithoutOverflow() {
        val longAgePolicy = NearbyIncidentPolicy(maxFixAgeNanos = Long.MAX_VALUE)

        assertTrue(
            longAgePolicy.evaluate(mexicoCity, mexicoFix.copy(elapsedRealtimeNanos = 0L), Long.MAX_VALUE) is
                NearbyIncidentDecision.Allowed,
        )
    }

    @Test
    fun aCustomAgeLimitIsCheckedInNanosecondsWithAnInclusiveBoundary() {
        val shortAgePolicy = NearbyIncidentPolicy(maxFixAgeNanos = 1L)

        assertTrue(shortAgePolicy.evaluate(mexicoCity, mexicoFix.copy(elapsedRealtimeNanos = now - 1L), now) is
            NearbyIncidentDecision.Allowed)
        assertReason(
            NearbyIncidentRejection.STALE_DEVICE_LOCATION,
            shortAgePolicy.evaluate(mexicoCity, mexicoFix.copy(elapsedRealtimeNanos = now - 2L), now),
        )
    }

    @Test
    fun radiusAndAccuracyConfigurationMustBePositiveAndFinite() {
        for (value in listOf(0.0, -0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertInvalidConfiguration { NearbyIncidentPolicy(maxDistanceMeters = value) }
            assertInvalidConfiguration { NearbyIncidentPolicy(maxAccuracyMeters = value) }
        }
    }

    @Test
    fun maximumFixAgeConfigurationMustBePositive() {
        for (age in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertInvalidConfiguration { NearbyIncidentPolicy(maxFixAgeNanos = age) }
        }
    }

    @Test
    fun oneEquatorialDegreeHasTheKnownSphericalArcLength() {
        val worldPolicy = NearbyIncidentPolicy(maxDistanceMeters = 25_000_000.0)
        val fix = DeviceLocationFix(0.0, 0.0, 0.0, now, false)
        val decision = allowed(worldPolicy.evaluate(point(0.0, 1.0), fix, now))

        // Arco analítico R * pi / 180; referencia numérica independiente de Haversine.
        assertEquals(111_195.0802335329, decision.distanceMeters, 0.000001)
    }

    @Test
    fun crossingTheInternationalDateLineUsesTheShortDistance() {
        val fix = DeviceLocationFix(0.0, 179.999, 10.0, now, false)
        val decision = allowed(policy.evaluate(point(0.0, -179.999), fix, now))

        // 0,002 grados en el ecuador, no 359,998 grados.
        assertEquals(222.3901604670658, decision.distanceMeters, 0.000001)
    }

    @Test
    fun plusAndMinusOneHundredEightyDegreesRepresentTheSameMeridian() {
        val fix = DeviceLocationFix(0.0, 180.0, 0.0, now, false)
        val decision = allowed(policy.evaluate(point(0.0, -180.0), fix, now))

        assertEquals(0.0, decision.distanceMeters, 0.00000001)
    }

    @Test
    fun theNorthPoleIsNearbyRegardlessOfItsLongitude() {
        val fix = DeviceLocationFix(89.99, 0.0, 10.0, now, false)
        val decision = allowed(policy.evaluate(point(90.0, 180.0), fix, now))

        // 0,01 grados a lo largo del meridiano hasta el polo.
        assertEquals(1_111.950802335329, decision.distanceMeters, 0.000001)
    }

    @Test
    fun differingLongitudesAtEitherPoleDoNotProduceADistantIncident() {
        for (latitude in listOf(-90.0, 90.0)) {
            val fix = DeviceLocationFix(latitude, -180.0, 0.0, now, false)
            val decision = allowed(policy.evaluate(point(latitude, 90.0), fix, now))

            assertEquals(0.0, decision.distanceMeters, 0.00000001)
        }
    }

    @Test
    fun antipodesProduceAFiniteHalfCircumferenceInsteadOfNaN() {
        val fix = DeviceLocationFix(0.0, 0.0, 10.0, now, false)
        val decision = rejected(policy.evaluate(point(0.0, 180.0), fix, now))
        val distance = decision.distanceMeters!!

        assertEquals(NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS, decision.reason)
        assertEquals(20_015_114.442035925, distance, 0.000001)
        assertTrue(distance.isFinite())
    }

    @Test
    fun nonEquatorialAntipodesAndNearAntipodesStayFiniteAndOutsideTheRadius() {
        val fix = DeviceLocationFix(19.4326077, -99.1332088, 10.0, now, false)
        for (incident in listOf(
            point(-19.4326077, 80.8667912),
            point(-19.4326076, 80.8667911),
        )) {
            val decision = rejected(policy.evaluate(incident, fix, now))
            val distance = decision.distanceMeters!!

            assertEquals(NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS, decision.reason)
            assertTrue(distance.isFinite())
            assertTrue(distance in 20_015_100.0..20_015_115.0)
        }
    }

    @Test
    fun distanceIsSymmetricAndDoesNotAlterEitherLocation() {
        val nearby = point(19.44, -99.13)
        val reverseFix = mexicoFix.copy(latitude = nearby.latitude!!, longitude = nearby.longitude!!)
        val forward = allowed(policy.evaluate(nearby, mexicoFix, now))
        val reverse = allowed(policy.evaluate(mexicoCity, reverseFix, now))

        assertEquals(forward.distanceMeters, reverse.distanceMeters, 0.000001)
        assertEquals(ReportLocation("Punto de referencia", 19.4326077, -99.1332088), mexicoCity)
        assertEquals(DeviceLocationFix(19.4326077, -99.1332088, 10.0, now, false), mexicoFix)
    }

    private fun point(latitude: Double, longitude: Double) =
        ReportLocation(latitude = latitude, longitude = longitude)

    private fun allowed(decision: NearbyIncidentDecision): NearbyIncidentDecision.Allowed {
        assertTrue("Se esperaba aprobación, llegó $decision", decision is NearbyIncidentDecision.Allowed)
        return decision as NearbyIncidentDecision.Allowed
    }

    private fun rejected(decision: NearbyIncidentDecision): NearbyIncidentDecision.Rejected {
        assertTrue("Se esperaba rechazo, llegó $decision", decision is NearbyIncidentDecision.Rejected)
        return decision as NearbyIncidentDecision.Rejected
    }

    private fun assertReason(reason: NearbyIncidentRejection, decision: NearbyIncidentDecision) {
        val rejection = rejected(decision)
        assertEquals(reason, rejection.reason)
        assertFalse(rejection.message.isBlank())
    }

    private fun assertInvalidConfiguration(action: () -> Unit) {
        try {
            action()
            fail("Se esperaba rechazo de una configuración inválida.")
        } catch (_: IllegalArgumentException) {
            // La configuración inválida se rechaza antes de evaluar un reporte.
        }
    }

    private fun invalidCoordinates(): List<Pair<Double, Double>> = listOf(
        Double.NaN to 0.0,
        0.0 to Double.NaN,
        Double.POSITIVE_INFINITY to 0.0,
        0.0 to Double.NEGATIVE_INFINITY,
        Math.nextUp(90.0) to 0.0,
        Math.nextDown(-90.0) to 0.0,
        0.0 to Math.nextUp(180.0),
        0.0 to Math.nextDown(-180.0),
    )
}
