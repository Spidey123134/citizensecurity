package com.example.citizensecurity.message

import android.content.Intent
import android.net.Uri
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.Report
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportMessageFormatter
import com.example.citizensecurity.domain.TrustedContact
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

/** Constructs Android intents only; this class never launches a messaging app or sends an SMS. */
class ReportMessageComposerTest {
    @Test fun exactReviewedUnicodeAndNormalizedRecipientBecomeAnSmsDraft() {
        val contact = TrustedContact.create("Persona de confianza", "+52 (55) 1234-5678")
        val draft = ReportMessageFormatter.prepare(report(), contact, zoneId = ZoneId.of("America/Mexico_City"))
        val intent = ReportMessageComposer.intent(draft)
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals(Uri.fromParts("smsto", "+525512345678", null), intent.data)
        assertEquals(draft.body, intent.getStringExtra("sms_body"))
        assertTrue(intent.getStringExtra("sms_body")!!.contains("Árbol caído 🌳\nEvitar el paso."))
    }

    @Test fun textCompositionCarriesNoPhotoGrantsPrivateRoutingOrForcedApplication() {
        val draft = ReportMessageFormatter.prepare(report(), TrustedContact.create("Contacto", "5512345678"),
            includeLocation = true, zoneId = ZoneId.of("UTC"))
        val intent = ReportMessageComposer.intent(draft)
        assertEquals(setOf("sms_body"), intent.extras!!.keySet())
        assertNull(intent.clipData)
        assertNull(intent.component)
        assertNull(intent.`package`)
        assertNull(intent.selector)
        assertNull(intent.type)
        assertEquals(0, intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION))
        assertFalse(intent.hasExtra(Intent.EXTRA_STREAM))
        assertFalse(intent.hasExtra("report_folio"))
    }

    @Test fun eachCompositionProducesAFreshIntentAndDoesNotRetainMutableAndroidState() {
        val draft = ReportMessageFormatter.prepare(report(), TrustedContact.create("Contacto", "5512345678"),
            zoneId = ZoneId.of("UTC"))
        val first = ReportMessageComposer.intent(draft)
        first.putExtra("sms_body", "Cambios locales del consumidor")
        first.data = Uri.parse("smsto:5599999999")
        first.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val second = ReportMessageComposer.intent(draft)
        assertNotSame(first, second)
        assertEquals(draft.body, second.getStringExtra("sms_body"))
        assertEquals(Uri.fromParts("smsto", draft.contact.phoneNumber, null), second.data)
        assertEquals(0, second.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    private fun report() = Report(UUID.randomUUID().toString(), IncidentType.RISK, Priority.MEDIUM,
        "Árbol caído 🌳\nEvitar el paso.", Instant.parse("2026-10-15T15:00:00Z"),
        ReportLocation("Frente al parque", 19.4326, -99.1332), Instant.parse("2026-10-15T15:03:00Z"))
}
