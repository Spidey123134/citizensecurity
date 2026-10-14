package com.example.citizensecurity.domain

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class TrustedContactTest {
    @Test fun normalizesOnlyPresentationAndKeepsInternationalPrefix() {
        assertEquals("+525512345678", TrustedContact.create(" Mamá 🌼 ", "+52 (55) 1234-5678").phoneNumber)
        assertEquals("Mamá 🌼", TrustedContact.create(" Mamá 🌼 ", "5512345678").name)
        assertEquals("5512345678", TrustedContact.normalizePhone("(55) 1234-5678"))
    }

    @Test fun rejectsDialCommandsUrisMultipleNumbersUnicodeDigitsAndMisplacedPlus() {
        listOf("tel:5512345678", "5512345678;911", "5512345678,911", "5512345678#", "*5512345678",
            "5512345678\n", "５５１２３４５６７８", "55+12345678", "++5512345678", "911", "089",
            "1".repeat(16), " ".repeat(81)).forEach { bad ->
            expectRejected { TrustedContact.create("Contacto", bad) }
        }
    }

    @Test fun validatesUnicodeNamesWithoutSplittingSurrogatesOrAllowingControlCharacters() {
        assertEquals(60, TrustedContact.create("🌼".repeat(60), "5512345678").name.codePointCount(0, 120))
        listOf("", " ", "a".repeat(61), "Hola\u0000", "Hola\n", "Hola\u2028", "a\ud800", "a\udc00")
            .forEach { bad -> expectRejected { TrustedContact.create(bad, "5512345678") } }
    }

    @Test fun requiresCanonicalIdsAndCanonicalStoredTelephone() {
        val id = "876a378a-d8ce-4a21-a091-5c662008ddaa"
        listOf(id.uppercase(), "1-1-1-1-1", "../contacts.json").forEach { bad ->
            expectRejected { TrustedContact(bad, "Contacto", "5512345678") }
        }
        expectRejected { TrustedContact(id, " Contacto", "5512345678") }
        expectRejected { TrustedContact(id, "Contacto", "55 1234 5678") }
    }

    @Test fun limitsAndDeduplicatesByPhoneAndId() {
        val contact = TrustedContact.create("Casa", "5512345678")
        expectRejected { TrustedContactsSnapshot(UUID.randomUUID().toString(), listOf(contact, contact)) }
        expectRejected { TrustedContactsSnapshot(UUID.randomUUID().toString(), listOf(contact,
            TrustedContact.create("Casa otra", "+5512345678"))) }
        val six = (0..5).map { TrustedContact.create("Contacto $it", "551234567$it") }
        expectRejected { TrustedContactsSnapshot(UUID.randomUUID().toString(), six) }
    }

    @Test fun capturesAnImmutableSnapshotAndRequiresRevisionForPersistedContacts() {
        val list = mutableListOf(TrustedContact.create("Casa", "5512345678"))
        val snapshot = TrustedContactsSnapshot(UUID.randomUUID().toString(), list)
        list.clear()
        assertEquals(1, snapshot.contacts.size)
        expectRejected { TrustedContactsSnapshot(null, snapshot.contacts) }
        assertTrue(TrustedContactsSnapshot(null, emptyList()).contacts.isEmpty())
        try { (snapshot.contacts as MutableList<TrustedContact>).clear(); fail("Lista mutable.") }
        catch (_: UnsupportedOperationException) { }
    }

    private inline fun expectRejected(block: () -> Unit) {
        try { block(); fail("Entrada inválida aceptada.") } catch (_: IllegalArgumentException) { }
    }
}
