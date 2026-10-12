package com.example.citizensecurity.report

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportSearchFiltersTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun noCriteriaRetainsEveryRecordInRepositoryOrderAndOriginalObjects() {
        val newest = report("newest")
        val older = report("older", occurredAt = Instant.parse("2020-01-01T00:00:00Z"))
        val input = listOf(newest, older)
        val output = filterLocalReports(input, filters())
        assertEquals(input, output)
        assertSame(newest, output[0])
        assertSame(older, output[1])
    }

    @Test
    fun searchFindsDescriptionIgnoringSpanishAccentsCaseAndOuterWhitespace() {
        val found = report("found", description = "Vehículo dañado en la estación")
        val other = report("other", description = "Luminaria averiada")
        assertEquals(listOf(found), filterLocalReports(listOf(found, other), filters(text = "  VEHICULO  ")))
    }

    @Test
    fun searchFindsReferenceWithoutRequiringAMatchingDescription() {
        val found = report("found", description = "Puerta rota", reference = "Avenida Constitución")
        val other = report("other", description = "Puerta rota", reference = "Calle Primera")
        assertEquals(
            listOf(found),
            filterLocalReports(listOf(other, found), filters(text = "CONSTITUCION")),
        )
    }

    @Test
    fun searchNormalizesCombiningAccentsAndRepeatedUnicodeWhitespace() {
        val found = report("found", reference = "Cafe\u0301\u00a0\u00a0A\u0301lamo")
        assertEquals(
            listOf(found),
            filterLocalReports(listOf(found), filters(text = "\u00a0 CAFÉ\t ÁLAMO\u2003")),
        )
    }

    @Test
    fun whitespaceOnlySearchDoesNotHideRecords() {
        val record = report("record", description = "", reference = "")
        assertEquals(listOf(record), filterLocalReports(listOf(record), filters(text = "\t\n\u00a0\u2003")))
    }

    @Test
    fun searchCannotFabricateAPhraseByJoiningDescriptionAndReference() {
        val record = report("record", description = "Plaza", reference = "Central")
        assertTrue(filterLocalReports(listOf(record), filters(text = "plaza central")).isEmpty())
    }

    @Test
    fun normalizationDoesNotDependOnTheDevicesTurkishDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val record = report("record", description = "INCIDENTE")
            assertEquals(listOf(record), filterLocalReports(listOf(record), filters(text = "incidente")))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun allCriteriaAreCombinedWithoutModifyingTypesStatusesOrInputList() {
        val match = report("match", description = "Vehículo dañado", type = IncidentType.ACCIDENT)
        val wrongText = match.copy(id = "wrong-text", description = "Puerta rota")
        val wrongType = match.copy(id = "wrong-type", type = IncidentType.THEFT)
        val wrongStatus = match.copy(id = "wrong-status", status = ReportStatus.CLOSED)
        val wrongDay = match.copy(id = "wrong-day", occurredAt = Instant.parse("2026-10-12T00:00:00Z"))
        val input = listOf(wrongText, wrongType, match, wrongStatus, wrongDay)
        val compiled = filters(
            text = "vehiculo", startDate = "11/10/2026", endDate = "11/10/2026",
            type = IncidentType.ACCIDENT, status = ReportStatus.REPORTED,
        )
        assertEquals(listOf(match), filterLocalReports(input, compiled))
        assertEquals(listOf(wrongText, wrongType, match, wrongStatus, wrongDay), input)
        assertEquals(IncidentType.ACCIDENT, match.type)
        assertEquals(ReportStatus.REPORTED, match.status)
    }

    @Test
    fun sameDateIncludesFirstAndLastInstantButExcludesAdjacentDays() {
        val first = report("first", occurredAt = Instant.parse("2026-10-11T00:00:00Z"))
        val last = report("last", occurredAt = Instant.parse("2026-10-11T23:59:59.999999999Z"))
        val before = report("before", occurredAt = first.occurredAt.minusNanos(1))
        val after = report("after", occurredAt = Instant.parse("2026-10-12T00:00:00Z"))
        assertEquals(
            listOf(last, first),
            filterLocalReports(listOf(last, before, first, after), filters(startDate = "11/10/2026", endDate = "11/10/2026")),
        )
    }

    @Test
    fun dateUsesIncidentTimeRatherThanCreationTime() {
        val inRange = report("in-range").copy(createdAt = Instant.parse("2026-10-13T12:00:00Z"))
        val outOfRange = report("out-of-range", occurredAt = Instant.parse("2026-10-10T18:00:00Z"))
            .copy(createdAt = Instant.parse("2026-10-11T12:00:00Z"))
        assertEquals(
            listOf(inRange),
            filterLocalReports(listOf(outOfRange, inRange), filters(startDate = "11/10/2026", endDate = "11/10/2026")),
        )
    }

    @Test
    fun startDateOnlyAllowsAllLaterDays() {
        val boundary = report("boundary", occurredAt = Instant.parse("2026-10-11T00:00:00Z"))
        val later = report("later", occurredAt = Instant.parse("2027-01-01T00:00:00Z"))
        val earlier = report("earlier", occurredAt = boundary.occurredAt.minusNanos(1))
        assertEquals(
            listOf(later, boundary),
            filterLocalReports(listOf(later, earlier, boundary), filters(startDate = "11/10/2026")),
        )
    }

    @Test
    fun endDateOnlyAllowsAllEarlierDaysAndTheFinalDay() {
        val earlier = report("earlier", occurredAt = Instant.parse("2020-01-01T00:00:00Z"))
        val last = report("last", occurredAt = Instant.parse("2026-10-11T23:59:59.999999999Z"))
        val after = report("after", occurredAt = last.occurredAt.plusNanos(1))
        assertEquals(
            listOf(last, earlier),
            filterLocalReports(listOf(last, after, earlier), filters(endDate = "11/10/2026")),
        )
    }

    @Test
    fun localCalendarDateUsesTheCallersZoneRatherThanUtc() {
        val mexicoCity = ZoneId.of("America/Mexico_City")
        val before = report("before", occurredAt = Instant.parse("2026-10-11T05:59:59.999999999Z"))
        val first = report("first", occurredAt = Instant.parse("2026-10-11T06:00:00Z"))
        val last = report("last", occurredAt = Instant.parse("2026-10-12T05:59:59.999999999Z"))
        val after = report("after", occurredAt = Instant.parse("2026-10-12T06:00:00Z"))
        assertEquals(
            listOf(first, last),
            filterLocalReports(listOf(before, first, last, after), filters(startDate = "11/10/2026", endDate = "11/10/2026", zoneId = mexicoCity)),
        )
    }

    @Test
    fun springDstDayContains23HoursAndDoesNotIncludeTheNextLocalDay() {
        val compiled = filters(startDate = "08/03/2026", endDate = "08/03/2026", zoneId = ZoneId.of("America/New_York"))
        assertEquals(23L, Duration.between(compiled.startInclusive, compiled.endExclusive).toHours())
        val end = requireNotNull(compiled.endExclusive)
        val last = report("last", occurredAt = end.minusNanos(1))
        val after = report("after", occurredAt = end)
        assertEquals(listOf(last), filterLocalReports(listOf(last, after), compiled))
    }

    @Test
    fun autumnDstDayContains25HoursAndIncludesBothRepeatedLocalHours() {
        val compiled = filters(startDate = "01/11/2026", endDate = "01/11/2026", zoneId = ZoneId.of("America/New_York"))
        assertEquals(25L, Duration.between(compiled.startInclusive, compiled.endExclusive).toHours())
        val firstOneThirty = report("first", occurredAt = Instant.parse("2026-11-01T05:30:00Z"))
        val secondOneThirty = report("second", occurredAt = Instant.parse("2026-11-01T06:30:00Z"))
        assertEquals(listOf(firstOneThirty, secondOneThirty), filterLocalReports(listOf(firstOneThirty, secondOneThirty), compiled))
    }

    @Test
    fun leapDayIsAcceptedOnlyInALeapYear() {
        assertTrue(parseReportSearchFilters(startDate = "29/02/2024", zoneId = utc) is ReportSearchFilterValidation.Valid)
        val invalid = parseReportSearchFilters(startDate = "29/02/2026", zoneId = utc)
        assertTrue(invalid is ReportSearchFilterValidation.Invalid)
        assertEquals("Fecha inicial inválida. Usa el formato dd/MM/aaaa.", (invalid as ReportSearchFilterValidation.Invalid).message)
    }

    @Test
    fun malformedOrImpossibleStartDatesProduceErrorsRatherThanUnfilteredSuccess() {
        for (date in listOf("31/04/2026", "00/10/2026", "11/13/2026", "11/10/0000", "1/10/2026", "11/1/2026", "2026-10-11", "11/10/26", "11/10/2026x", "11/ 10/2026")) {
            assertTrue(date, parseReportSearchFilters(startDate = date, zoneId = utc) is ReportSearchFilterValidation.Invalid)
        }
    }

    @Test
    fun invalidFinalDateIdentifiesTheFinalFieldEvenWithoutAStartDate() {
        val result = parseReportSearchFilters(endDate = "31/02/2026", zoneId = utc)
        assertEquals(
            ReportSearchFilterValidation.Invalid("Fecha final inválida. Usa el formato dd/MM/aaaa."),
            result,
        )
    }

    @Test
    fun descendingRangeIsAnErrorAndSameDayIsValid() {
        val result = parseReportSearchFilters(startDate = "12/10/2026", endDate = "11/10/2026", zoneId = utc)
        assertEquals(
            ReportSearchFilterValidation.Invalid("La fecha inicial debe ser anterior o igual a la fecha final."),
            result,
        )
        assertTrue(parseReportSearchFilters(startDate = "11/10/2026", endDate = "11/10/2026", zoneId = utc) is ReportSearchFilterValidation.Valid)
    }

    @Test
    fun datesAllowOuterUnicodeWhitespaceButNotAnUnexpectedFormat() {
        val compiled = filters(startDate = "\u00a0 11/10/2026\t", endDate = "\u200311/10/2026\u00a0")
        assertEquals(Instant.parse("2026-10-11T00:00:00Z"), compiled.startInclusive)
        assertEquals(Instant.parse("2026-10-12T00:00:00Z"), compiled.endExclusive)
        assertFalse(parseReportSearchFilters(startDate = "11 /10/2026", zoneId = utc) is ReportSearchFilterValidation.Valid)
    }

    @Test
    fun oldTypeAndStatusFilterApiStillWorksAlongsideTheNewOverload() {
        val matching = report("matching", type = IncidentType.THEFT, status = ReportStatus.CLOSED)
        val other = report("other")
        assertEquals(listOf(matching), filterLocalReports(listOf(other, matching), IncidentType.THEFT, ReportStatus.CLOSED))
    }

    @Test
    fun priorityIncludesOnlySelectedPriorityAndNullPreservesAllPriorities() {
        val low = report("low").copy(priority = Priority.LOW)
        val medium = report("medium")
        val high = report("high").copy(priority = Priority.HIGH)
        val source = listOf(high, low, medium)
        for (priority in Priority.entries) {
            val selected = filterLocalReports(source, filters(priority = priority))
            assertEquals(listOf(source.single { it.priority == priority }), selected)
        }
        assertEquals(source, filterLocalReports(source, filters()))
    }

    @Test
    fun priorityCombinesWithTypeStatusTextAndIncidentDateWithoutChangingRecords() {
        val match = report("match", description = "Vehículo dañado", type = IncidentType.ACCIDENT)
            .copy(priority = Priority.HIGH)
        val wrongPriority = match.copy(id = "wrong-priority", priority = Priority.LOW)
        val wrongType = match.copy(id = "wrong-type", type = IncidentType.RISK)
        val wrongStatus = match.copy(id = "wrong-status", status = ReportStatus.CLOSED)
        val wrongText = match.copy(id = "wrong-text", description = "Puerta rota")
        val wrongDate = match.copy(id = "wrong-date", occurredAt = Instant.parse("2026-10-12T00:00:00Z"))
        val source = listOf(wrongPriority, wrongType, match, wrongStatus, wrongText, wrongDate)
        val compiled = filters(
            text = "vehiculo", startDate = "11/10/2026", endDate = "11/10/2026",
            type = IncidentType.ACCIDENT, status = ReportStatus.REPORTED, priority = Priority.HIGH,
        )
        assertEquals(Priority.HIGH, compiled.priority)
        val result = filterLocalReports(source, compiled)
        assertEquals(listOf(match), result)
        assertSame(match, result.single())
        assertEquals(6, source.size)
        assertEquals(Priority.LOW, wrongPriority.priority)
    }

    private fun filters(
        text: String = "", startDate: String = "", endDate: String = "", zoneId: ZoneId = utc,
        type: IncidentType? = null, status: ReportStatus? = null, priority: Priority? = null,
    ): ReportSearchFilters {
        val result = parseReportSearchFilters(text, startDate, endDate, zoneId, type, status, priority)
        assertTrue("Expected valid filters but was $result", result is ReportSearchFilterValidation.Valid)
        return (result as ReportSearchFilterValidation.Valid).filters
    }

    private fun report(
        id: String, description: String = "Luminaria dañada", reference: String = "Frente al parque",
        occurredAt: Instant = Instant.parse("2026-10-11T18:00:00Z"),
        type: IncidentType = IncidentType.RISK, status: ReportStatus = ReportStatus.REPORTED,
    ) = Report(
        id = id, type = type, priority = Priority.MEDIUM, description = description,
        occurredAt = occurredAt, location = ReportLocation(reference = reference),
        createdAt = Instant.parse("2026-10-11T18:00:10Z"), status = status,
    )
}
