package com.example.citizensecurity.domain

import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ReportMessageTest {
    private val contact = TrustedContact("d32d119f-a214-4ba2-bf67-628fc5709b7c", "Contacto privado", "+525598765432")
    private val folio = "d53d6ea3-1213-4255-a9e7-d2c2f928d6b0"
    private val time = Instant.parse("2026-10-15T18:24:30.123456789Z")

    @Test fun retainsSavedFieldsAndFullDescriptionWithoutRecipientOrPhotoMetadata() {
        val report = report().copy(photo = ReportPhoto(
            "9146d458-ecdc-4eec-8e67-01d95807eda2", "a".repeat(64), 100, 20, 10,
        ))
        val message = ReportMessageFormatter.prepare(report, contact, zoneId = ZoneOffset.UTC)
        assertEquals(folio, message.reportId)
        assertSame(contact, message.contact)
        assertTrue(message.body.startsWith("Registro local de CitizenSecurity\nFolio: $folio\n"))
        assertTrue(message.body.contains("Tipo: Riesgo\n"))
        assertTrue(message.body.contains("Prioridad indicada por el autor: Alta\n"))
        assertTrue(message.body.contains("Estado local: Guardado localmente\n"))
        assertTrue(message.body.contains("Fecha del incidente: 15/10/2026 18:24:30.123456789 +00:00 [Z]\n"))
        assertTrue(message.body.contains("Descripción:\n${report.description}\n"))
        assertTrue(message.body.endsWith(ReportMessageFormatter.LOCAL_NOTICE))
        listOf(contact.id, contact.name, contact.phoneNumber, report.photo!!.id, report.photo.sha256)
            .forEach { privateValue -> assertFalse(message.body.contains(privateValue)) }
    }

    @Test fun defaultLocationChoiceOmitsReferenceCoordinatesAndMapEvenWhenReportContainsThem() {
        val message = ReportMessageFormatter.prepare(report(), contact)
        listOf("Esquina confidencial", "19.432608", "-99.133209", "openstreetmap", "Coordenadas:", "Referencia de ubicación:")
            .forEach { locationValue -> assertFalse(message.body.contains(locationValue)) }
    }

    @Test fun explicitLocationChoiceContainsCompletePointAndHttpsMarkerWithDecimalPoints() {
        val message = ReportMessageFormatter.prepare(report(), contact, includeLocation = true)
        assertTrue(message.body.contains("Referencia de ubicación: Esquina confidencial\n"))
        assertTrue(message.body.contains("Coordenadas: 19.432608, -99.133209\n"))
        val map = message.body.lineSequence().single { it.startsWith("Mapa: ") }.removePrefix("Mapa: ")
        val uri = URI(map)
        assertEquals("https", uri.scheme)
        assertEquals("www.openstreetmap.org", uri.host)
        assertEquals("mlat=19.432608&mlon=-99.133209", uri.rawQuery)
        assertEquals("map=18/19.432608/-99.133209", uri.rawFragment)
        listOf(-90.0 to -180.0, 90.0 to 180.0, 0.0 to 0.0).forEach { (latitude, longitude) ->
            val boundary = ReportMessageFormatter.prepare(report().copy(location = ReportLocation(
                latitude = latitude, longitude = longitude,
            )), contact, includeLocation = true)
            assertTrue(boundary.body.contains("Coordenadas: $latitude, $longitude\n"))
        }
    }

    @Test fun referenceOnlyLocationDoesNotInventCoordinatesOrMapAndPreservesWhitespace() {
        val reference = "  Junto al parque\nfrente a la tienda  "
        val message = ReportMessageFormatter.prepare(report().copy(location = ReportLocation(reference)), contact, true)
        assertTrue(message.body.contains("Referencia de ubicación: $reference\n"))
        assertFalse(message.body.contains("Coordenadas:"))
        assertFalse(message.body.contains("Mapa:"))
    }

    @Test fun maximumSupplementaryUnicodeFieldsStayCompleteAndInsideUtf16IntentBound() {
        val description = "🔥".repeat(998) + "\n\t"
        val reference = "🌳".repeat(200)
        val report = report().copy(description = description, location = ReportLocation(reference, 90.0, -180.0))
        val message = ReportMessageFormatter.prepare(report, contact, true, ZoneId.of("America/Mexico_City"))
        assertTrue(message.body.contains("Descripción:\n$description\n"))
        assertTrue(message.body.contains("Referencia de ubicación: $reference\n"))
        assertTrue(message.body.length <= ReportMessageDraft.MAX_BODY_LENGTH)
        assertTrue(message.body.endsWith(ReportMessageFormatter.LOCAL_NOTICE))
    }

    @Test fun rejectsCorruptSavedIdentityTextLimitsControlsAndIncompleteUnicodeWithoutTruncating() {
        listOf(folio.uppercase(), "1-1-1-1-1", "../folio").forEach { bad ->
            expectRejected { ReportMessageFormatter.prepare(report().copy(id = bad), contact) }
        }
        listOf("", " \n\t", "🔥".repeat(1_001), "Texto\u0000inseguro", "Texto\u0007inseguro", "Texto\u007finseguro", "Texto\ud800", "Texto\udc00")
            .forEach { bad -> expectRejected { ReportMessageFormatter.prepare(report().copy(description = bad), contact) } }
        listOf("🌳".repeat(201), "Ubicación\u0000", "Ubicación\u0085", "Ubicación\ud800", "Ubicación\udc00")
            .forEach { bad -> expectRejected { ReportMessageFormatter.prepare(report().copy(location = ReportLocation(bad)), contact) } }
    }

    @Test fun corruptCoordinatesAreRejectedEvenWhenLocationWasNotSelected() {
        val invalidPoints = listOf(19.0 to null, null to -99.0, Double.NaN to 0.0,
            0.0 to Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY to 0.0, 90.00001 to 0.0,
            -90.00001 to 0.0, 0.0 to 180.00001, 0.0 to -180.00001)
        invalidPoints.forEach { (latitude, longitude) ->
            listOf(false, true).forEach { selected ->
                expectRejected { ReportMessageFormatter.prepare(report().copy(location = ReportLocation(
                    "Referencia completa", latitude, longitude,
                )), contact, selected) }
            }
        }
    }

    @Test fun everyTypePriorityAndStatusUsesSpanishLabelsAndKeepsStatusExplicitlyLocal() {
        val types = mapOf(IncidentType.EMERGENCY to "Emergencia", IncidentType.THEFT to "Robo",
            IncidentType.ACCIDENT to "Accidente", IncidentType.FIRE to "Incendio",
            IncidentType.RISK to "Riesgo", IncidentType.OTHER to "Otro")
        val priorities = mapOf(Priority.LOW to "Baja", Priority.MEDIUM to "Media", Priority.HIGH to "Alta")
        val statuses = mapOf(ReportStatus.REPORTED to "Guardado localmente", ReportStatus.IN_REVIEW to "En revisión",
            ReportStatus.ATTENDED to "Atendido", ReportStatus.CLOSED to "Cerrado")
        assertEquals(IncidentType.entries.toSet(), types.keys)
        assertEquals(Priority.entries.toSet(), priorities.keys)
        assertEquals(ReportStatus.entries.toSet(), statuses.keys)
        types.forEach { (type, typeLabel) -> priorities.forEach { (priority, priorityLabel) ->
            statuses.forEach { (status, statusLabel) ->
                val body = ReportMessageFormatter.prepare(report().copy(type = type, priority = priority, status = status), contact).body
                assertTrue(body.contains("Tipo: $typeLabel\n"))
                assertTrue(body.contains("Prioridad indicada por el autor: $priorityLabel\n"))
                assertTrue(body.contains("Estado local: $statusLabel\n"))
                assertTrue(body.endsWith(ReportMessageFormatter.LOCAL_NOTICE))
            }
        } }
    }

    @Test fun datesUseSavedInstantWithZoneOffsetAndNanosecondsWithoutCurrentClockOrInventedOrderRule() {
        val zone = ZoneId.of("America/New_York")
        val daylightSavingTime = Instant.parse("2026-11-01T05:30:00.000000001Z")
        val beforeTransition = ReportMessageFormatter.prepare(report().copy(occurredAt = daylightSavingTime), contact, zoneId = zone)
        val afterTransition = ReportMessageFormatter.prepare(report().copy(occurredAt = daylightSavingTime.plusSeconds(3_600)), contact, zoneId = zone)
        assertTrue(beforeTransition.body.contains("01/11/2026 01:30:00.000000001 -04:00 [America/New_York]"))
        assertTrue(afterTransition.body.contains("01/11/2026 01:30:00.000000001 -05:00 [America/New_York]"))
        val future = Instant.parse("2099-01-01T12:00:00Z")
        assertTrue(ReportMessageFormatter.prepare(report().copy(occurredAt = future, createdAt = future.minusSeconds(30)), contact,
            zoneId = ZoneOffset.UTC).body.contains("01/01/2099 12:00:00 +00:00"))
        ReportMessageFormatter.prepare(report().copy(occurredAt = Instant.EPOCH, createdAt = Instant.EPOCH), contact)
        listOf(Instant.EPOCH.minusNanos(1), Instant.MIN, Instant.MAX).forEach { bad ->
            expectRejected { ReportMessageFormatter.prepare(report().copy(occurredAt = bad), contact) }
            expectRejected { ReportMessageFormatter.prepare(report().copy(createdAt = bad), contact) }
        }
    }

    @Test fun draftCopiesCannotBypassCanonicalIdentityCoherentBodyOrUtf16AndUnicodeBounds() {
        val draft = ReportMessageFormatter.prepare(report(), contact)
        val other = TrustedContact("57838048-b2c1-4a20-8f61-82f3cd3c1ca6", "Otro contacto", "5512345678")
        assertEquals(other, draft.copy(contact = other).contact)
        assertEquals(draft.body, draft.copy(contact = other).body)
        expectRejected { draft.copy(reportId = "1-1-1-1-1") }
        expectRejected { draft.copy(reportId = other.id) }
        expectRejected { draft.copy(body = "Texto sin folio ni aviso") }
        expectRejected { draft.copy(body = draft.body + "\u0000") }
        expectRejected { draft.copy(body = draft.body.replace("Descripción:", "\ud800")) }
        val prefix = "${ReportMessageFormatter.TITLE}\nFolio: $folio\n"
        val suffix = ReportMessageFormatter.LOCAL_NOTICE
        val exactBound = prefix + "a".repeat(ReportMessageDraft.MAX_BODY_LENGTH - prefix.length - suffix.length) + suffix
        assertEquals(3_500, draft.copy(body = exactBound).body.length)
        expectRejected { draft.copy(body = prefix + "🔥".repeat(1_751) + suffix) }
    }

    private fun report() = Report(folio, IncidentType.RISK, Priority.HIGH,
        "Observé una lámpara rota.\nEl cable está sobre la banqueta.\tFin de la descripción.", time,
        ReportLocation("Esquina confidencial", 19.432608, -99.133209), time.plusSeconds(20))

    private inline fun expectRejected(block: () -> Unit) {
        try { block(); fail("Entrada inválida aceptada.") } catch (_: IllegalArgumentException) { }
    }
}
