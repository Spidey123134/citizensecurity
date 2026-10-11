package com.example.citizensecurity.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SimilarReportQueryTest {
    @Test
    fun losValoresAcordadosBuscanDoscientosMetrosYDosHorasPorLado() {
        val query = SimilarReportQuery.fromDraft(draft())!!
        assertEquals(IncidentType.RISK, query.type)
        assertEquals(200.0, query.radiusMeters, 0.0)
        assertEquals(Duration.ofHours(2), query.timeWindow)
        assertEquals(TIME.minusSeconds(7200), query.earliest)
        assertEquals(TIME.plusSeconds(7200), query.latest)
        assertNull(query.excludedId)
    }

    @Test
    fun referenciaSinPuntoOPuntoIncompletoNoFabricanUnaConsulta() {
        for (location in listOf(ReportLocation("Solo referencia"), ReportLocation(latitude = 0.0), ReportLocation(longitude = 0.0))) {
            assertNull(SimilarReportQuery.fromDraft(draft().copy(location = location)))
        }
    }

    @Test
    fun unPuntoInvalidoNoSeBuscaAunqueTengaReferencia() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 91.0, -91.0)) {
            assertNull(SimilarReportQuery.fromDraft(draft().copy(location = ReportLocation("Referencia", invalid, 0.0))))
            rejects { query(latitude = invalid) }
        }
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 181.0, -181.0)) {
            assertNull(SimilarReportQuery.fromDraft(draft().copy(location = ReportLocation("Referencia", 0.0, invalid))))
            rejects { query(longitude = invalid) }
        }
    }

    @Test
    fun ceroPolosYAmbasRepresentacionesDelMeridianoSonPuntosValidos() {
        for (latitude in listOf(-90.0, -0.0, 0.0, 90.0)) for (longitude in listOf(-180.0, -0.0, 0.0, 180.0)) {
            assertNotNull(query(latitude, longitude))
            assertNotNull(SimilarReportQuery.fromDraft(draft().copy(location = ReportLocation(latitude = latitude, longitude = longitude))))
        }
    }

    @Test
    fun radioNoFinitoNegativoOCapazDeAmpliarSinLimiteSeRechaza() {
        for (radius in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 0.0, -0.0, 5000.001)) {
            rejects { query().copy(radiusMeters = radius) }
        }
        assertEquals(5000.0, query().copy(radiusMeters = 5000.0).radiusMeters, 0.0)
        assertNotNull(query().copy(radiusMeters = 0.001))
    }

    @Test
    fun ventanaDebeSerPositivaYAcotadaAUnDiaPorLado() {
        for (window in listOf(Duration.ZERO, Duration.ofNanos(-1), Duration.ofHours(24).plusNanos(1), Duration.ofSeconds(Long.MAX_VALUE))) {
            rejects { query().copy(timeWindow = window) }
        }
        assertNotNull(query().copy(timeWindow = Duration.ofNanos(1)))
        assertNotNull(query().copy(timeWindow = Duration.ofHours(24)))
    }

    @Test
    fun excluirElFolioPropioExigeUuidCanonicoCompleto() {
        assertEquals(ID, SimilarReportQuery.fromDraft(draft(), ID)!!.excludedId)
        for (id in listOf("", "1-1-1-1-1", ID.uppercase(), "' OR 1=1 --", "$ID ")) {
            rejects { query().copy(excludedId = id) }
        }
    }

    @Test
    fun fechasExtremasSeAcotanSinDesbordarNiPerderNanos() {
        val first = query().copy(occurredAt = Instant.MIN)
        assertEquals(Instant.MIN, first.earliest)
        assertEquals(Instant.MIN.plusSeconds(7200), first.latest)
        val last = query().copy(occurredAt = Instant.MAX)
        assertEquals(Instant.MAX.minusSeconds(7200), last.earliest)
        assertEquals(Instant.MAX, last.latest)
        val almostFirst = query().copy(occurredAt = Instant.MIN.plusSeconds(7200).minusNanos(1))
        assertEquals(Instant.MIN, almostFirst.earliest)
        val almostLast = query().copy(occurredAt = Instant.MAX.minusSeconds(7200).plusNanos(1))
        assertEquals(Instant.MAX, almostLast.latest)
        val beforeEpoch = query().copy(occurredAt = Instant.EPOCH.minusNanos(1), timeWindow = Duration.ofNanos(1))
        assertEquals(Instant.EPOCH.minusNanos(2), beforeEpoch.earliest)
        assertEquals(Instant.EPOCH, beforeEpoch.latest)
    }

    @Test
    fun laCajaContieneElCirculoTambienEnPolosYAlCruzarElMeridiano() {
        for (latitude in listOf(-90.0, -89.999, -70.0, 0.0, 19.4, 70.0, 89.999, 90.0)) {
            for (longitude in listOf(-180.0, -179.999, -99.0, 0.0, 179.999, 180.0)) {
                for (radius in listOf(200.0, 5000.0)) {
                    val query = query(latitude, longitude).copy(radiusMeters = radius)
                    for (bearing in 0 until 360 step 5) {
                        val (destinationLatitude, destinationLongitude) = destination(latitude, longitude, radius, bearing.toDouble())
                        assertTrue("La caja no debe omitir el borde de $query, rumbo $bearing.", query.region.contains(destinationLatitude, destinationLongitude))
                    }
                }
            }
        }
    }

    @Test
    fun cajaEnElMeridianoNoIncluyeElCentroDelMundo() {
        for (longitude in listOf(-180.0, -179.9995, 179.9995, 180.0)) {
            val region = query(0.0, longitude).region
            assertTrue(region.crossesAntimeridian)
            assertTrue(region.contains(0.0, -180.0))
            assertTrue(region.contains(0.0, 180.0))
            assertFalse(region.contains(0.0, 0.0))
        }
    }

    @Test
    fun cajaQueAlcanzaUnPoloAdmiteTodasLasLongitudesSinAmpliarLaLatitud() {
        val north = query(89.9995, 0.0).region
        val south = query(-89.9995, 0.0).region
        for (longitude in -180..180 step 10) {
            assertTrue(north.contains(90.0, longitude.toDouble()))
            assertTrue(south.contains(-90.0, longitude.toDouble()))
            assertFalse(north.contains(0.0, longitude.toDouble()))
            assertFalse(south.contains(0.0, longitude.toDouble()))
        }
    }

    @Test
    fun distanciaEsfericaReconoceElMismoPuntoYElBordeInclusivo() {
        val query = query(0.0, 0.0)
        assertEquals(0.0, query.distanceTo(0.0, 0.0)!!, 0.0)
        val atBoundary = Math.toDegrees(200.0 / EARTH_RADIUS)
        assertEquals(200.0, query.distanceTo(atBoundary, 0.0)!!, 1e-10)
        assertTrue(query.includesDistance(query.distanceTo(atBoundary, 0.0)!!))
        val outside = Math.toDegrees(200.01 / EARTH_RADIUS)
        assertFalse(query.includesDistance(query.distanceTo(outside, 0.0)!!))
        assertFalse(query.includesDistance(query.distanceTo(0.0017, 0.0017)!!))
    }

    @Test
    fun distanciaNoConfundeUnSaltoDeMeridianoConUnTrayectoDeLaVueltaAlMundo() {
        val query = query(0.0, 179.9995)
        assertEquals(111.195080, query.distanceTo(0.0, -179.9995)!!, 0.0001)
        assertTrue(query.includesDistance(query.distanceTo(0.0, -179.9995)!!))
        assertFalse(query.includesDistance(query.distanceTo(0.0, 0.0)!!))
        assertTrue(query(90.0, 180.0).includesDistance(query(90.0, 180.0).distanceTo(90.0, -180.0)!!))
    }

    @Test
    fun distanciaConValoresAusentesOInvalidosNoProduceUnaCoincidencia() {
        val query = query()
        for (invalid in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 91.0)) {
            assertNull(query.distanceTo(invalid, 0.0))
        }
        for (invalid in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 181.0)) {
            assertNull(query.distanceTo(0.0, invalid))
        }
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.01, 200.01)) {
            assertFalse(query.includesDistance(invalid))
        }
    }

    @Test
    fun paginaEsInmutableYNoAdmiteMasDeCincoResultados() {
        val report = report()
        val original = mutableListOf(SimilarReportMatch(report, 12.0))
        val page = SimilarReportPage(original, true)
        original.clear()
        assertEquals(listOf(SimilarReportMatch(report, 12.0)), page.matches)
        assertTrue(page.searchIncomplete)
        assertEquals(SimilarReportPage(page.matches, true), page)
        assertEquals(SimilarReportPage(page.matches, true).hashCode(), page.hashCode())
        try {
            (page.matches as MutableList<SimilarReportMatch>).clear()
            fail("La copia observada no debe poder modificarse fuera del repositorio.")
        } catch (_: UnsupportedOperationException) { /* Lista inmutable. */ }
        rejects { SimilarReportPage(List(6) { SimilarReportMatch(report, 0.0) }, false) }
        assertEquals(emptyList<SimilarReportMatch>(), SimilarReportPage(emptyList(), true).matches)
    }

    @Test
    fun coincidenciaRechazaDistanciasNoFinitasONegativas() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0)) {
            rejects { SimilarReportMatch(report(), invalid) }
        }
        assertNotNull(SimilarReportMatch(report(), 0.0))
    }

    @Test
    fun identidadDeConsultaIncluyeTodosLosParametrosQueCambianLaBusqueda() {
        val original = query()
        assertEquals(original, original.copy())
        assertEquals(original.hashCode(), original.copy().hashCode())
        for (changed in listOf(original.copy(type = IncidentType.FIRE), original.copy(occurredAt = TIME.plusNanos(1)),
            original.copy(latitude = 1.0), original.copy(longitude = 1.0), original.copy(radiusMeters = 100.0),
            original.copy(timeWindow = Duration.ofMinutes(30)), original.copy(excludedId = ID))) {
            assertFalse(original == changed)
        }
    }

    private fun query(latitude: Double = 0.0, longitude: Double = 0.0) = SimilarReportQuery(IncidentType.RISK, TIME, latitude, longitude)
    private fun draft() = NewReport(IncidentType.RISK, Priority.MEDIUM, "Hay una luminaria dañada frente al parque.", TIME, ReportLocation("Parque", 0.0, 0.0))
    private fun report() = Report(ID, IncidentType.RISK, Priority.MEDIUM, "Hay una luminaria dañada frente al parque.", TIME, ReportLocation("Parque", 0.0, 0.0), TIME)

    private fun destination(latitude: Double, longitude: Double, meters: Double, bearing: Double): Pair<Double, Double> {
        val angular = meters / EARTH_RADIUS
        val latitudeRadians = Math.toRadians(latitude)
        val bearingRadians = Math.toRadians(bearing)
        // El polo exacto tiene longitud indeterminada; usar el límite representable evita 0/0.
        val sourceLatitude = latitudeRadians.coerceIn(-Math.PI / 2.0 + 1e-12, Math.PI / 2.0 - 1e-12)
        val destinationLatitude = asin((sin(sourceLatitude) * cos(angular) + cos(sourceLatitude) * sin(angular) * cos(bearingRadians)).coerceIn(-1.0, 1.0))
        val destinationLongitude = Math.toRadians(longitude) + atan2(sin(bearingRadians) * sin(angular) * cos(sourceLatitude),
            cos(angular) - sin(sourceLatitude) * sin(destinationLatitude))
        return Math.toDegrees(destinationLatitude) to (((Math.toDegrees(destinationLongitude) + 180.0) % 360.0 + 360.0) % 360.0 - 180.0)
    }

    private fun rejects(action: () -> Unit) {
        try { action(); fail("La entrada inválida debe rechazarse antes de SQLite.") }
        catch (_: IllegalArgumentException) { /* Rechazo de contrato. */ }
    }

    private companion object {
        val TIME: Instant = Instant.parse("2026-10-10T12:00:00.987654321Z")
        const val ID = "f85e70fc-d6cd-494e-8de5-e5f0043c3362"
        const val EARTH_RADIUS = 6_371_008.8
    }
}
