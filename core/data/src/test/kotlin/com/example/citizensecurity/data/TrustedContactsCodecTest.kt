package com.example.citizensecurity.data

import com.example.citizensecurity.domain.TrustedContact
import com.example.citizensecurity.domain.TrustedContactsSnapshot
import org.junit.Assert.*
import org.junit.Test

class TrustedContactsCodecTest {
    private val revision = "876a378a-d8ce-4a21-a091-5c662008ddaa"
    private val contact = TrustedContact.create("Casa \"norte\" \\ 🌼", "+525512345678",
        "1900b97d-62c6-4580-9a9e-b1f510134e4d")
    private fun document() = TrustedContactsCodec.encode(TrustedContactsSnapshot(revision, listOf(contact)))

    @Test fun roundTripsEscapedUnicodeWithoutChangingThePrivateData() {
        val result = TrustedContactsCodec.decode(document())
        assertEquals(revision, result.revision)
        assertEquals(listOf(contact), result.contacts)
    }

    @Test fun rejectsDuplicateKeysUnknownFieldsSchemasAndTrailingPayload() {
        listOf(document().replace("\"schema\":1", "\"schema\":1,\"schema\":1"),
            document().replace("\"schema\":1", "\"schema\":2"),
            document().replace("\"schema\":1", "\"schema\":1,\"token\":\"x\""),
            document() + "{}", document().replace("\"schema\":1", "\"schema\":1.0"),
            document().replace("\"schema\":1", "\"schema\":01"),
            document().replace("\"schema\":1", "\"schema\":\"1\""),
            document().replace("\"schema\":1", "'schema':1"),
            document().replace("\"schema\":1", "schema:1"), document().dropLast(1) + ",}",
        ).forEach(::expectRejected)
    }

    @Test fun rejectsMissingOrMismatchedPrimitiveTypesAndNonCanonicalPhones() {
        listOf(document().replace("\"revision\":\"$revision\"", "\"revision\":null"),
            document().replace("\"revision\":\"$revision\",", ""),
            document().replace("\"contacts\":[", "\"contacts\":{\"a\":["),
            document().replace("+525512345678", "+52 5512345678"),
            document().replace("+525512345678", "tel:5512345678"),
            document().replace("+525512345678", "5512345678;911"),
            document().replace(revision, revision.uppercase()),
        ).forEach(::expectRejected)
    }

    @Test fun rejectsOversizedDeepTruncatedAndUnpairedUnicodeInputs() {
        listOf(" ".repeat(TrustedContactsCodec.MAX_BYTES + 1), "[".repeat(8) + "1" + "]".repeat(8),
            document().dropLast(7), document().replace("Casa", "\\ud800"),
            document().replace("Casa", "\\u0000"), document().replace("Casa", "\\u2028"),
        ).forEach(::expectRejected)
    }

    @Test fun rejectsDuplicateContactsAndAcceptsARevisionedEmptyList() {
        val contactJson = document().substringAfter("\"contacts\":[").dropLast(2)
        expectRejected("{\"schema\":1,\"revision\":\"$revision\",\"contacts\":[$contactJson,$contactJson]}")
        val empty = TrustedContactsCodec.decode("{\"contacts\":[],\"revision\":\"$revision\",\"schema\":1}")
        assertTrue(empty.contacts.isEmpty())
        assertEquals(revision, empty.revision)
    }

    private fun expectRejected(text: String) {
        try { TrustedContactsCodec.decode(text); fail("Archivo inválido aceptado.") }
        catch (_: Exception) { }
    }
}
