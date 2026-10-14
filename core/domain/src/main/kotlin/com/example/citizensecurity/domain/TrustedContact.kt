package com.example.citizensecurity.domain

import java.util.Collections
import java.util.UUID

/** A manually chosen local contact. A dial target can never contain URI commands. */
data class TrustedContact(val id: String, val name: String, val phoneNumber: String) {
    init {
        require(canonicalUuid(id)) { "Identificador de contacto inválido." }
        require(name == name.trim() && name.isNotBlank() && name.validText() &&
            name.codePointCount(0, name.length) in 1..60) { "El nombre debe tener de 1 a 60 caracteres." }
        require(phoneNumber == normalizePhone(phoneNumber)) { "Número de teléfono inválido." }
    }

    companion object {
        const val MAX_CONTACTS = 5

        fun create(name: String, phoneNumber: String, id: String = UUID.randomUUID().toString()): TrustedContact {
            require(name.validText()) { "El nombre contiene caracteres inválidos." }
            return TrustedContact(id, name.trim(), normalizePhone(phoneNumber))
        }

        /** Spaces, hyphens and parentheses are presentation only; ASCII digits are required. */
        fun normalizePhone(value: String): String {
            require(value.length <= 80 && value.all { it in '0'..'9' || it in " +()-" }) {
                "Usa de 10 a 15 dígitos; puedes incluir + al inicio, espacios, guiones o paréntesis."
            }
            val compact = value.filterNot { it in " ()-" }
            val digits = compact.removePrefix("+")
            require(digits.length in 10..15 && digits.all { it in '0'..'9' } &&
                compact.count { it == '+' } <= 1 && (!compact.contains('+') || compact.startsWith('+'))) {
                "Usa de 10 a 15 dígitos y, si hace falta, + al inicio."
            }
            return compact
        }

        internal fun canonicalUuid(value: String) = runCatching {
            UUID.fromString(value).toString() == value
        }.getOrDefault(false)

        private fun String.validText(): Boolean {
            var index = 0
            while (index < length) {
                val c = this[index]
                if (c.isHighSurrogate()) {
                    if (index + 1 >= length || !this[index + 1].isLowSurrogate()) return false
                    index += 2
                } else {
                    if (c.isLowSurrogate() || Character.isISOControl(c) || c == '\u2028' || c == '\u2029') return false
                    index++
                }
            }
            return true
        }
    }
}

/** A bounded immutable reading. UUID revisions prevent a stale writer from overwriting newer data. */
class TrustedContactsSnapshot(val revision: String?, contacts: List<TrustedContact>) {
    val contacts: List<TrustedContact> = Collections.unmodifiableList(ArrayList(contacts))
    init {
        require(revision == null || TrustedContact.canonicalUuid(revision))
        require(revision != null || contacts.isEmpty())
        validateContacts(this.contacts)
    }

    companion object {
        fun validateContacts(contacts: List<TrustedContact>) {
            require(contacts.size <= TrustedContact.MAX_CONTACTS) { "Puedes conservar hasta 5 contactos." }
            require(contacts.map { it.id }.toSet().size == contacts.size) { "Contacto repetido." }
            require(contacts.map { it.phoneNumber.removePrefix("+") }.toSet().size == contacts.size) {
                "Ese teléfono ya está en tus contactos."
            }
        }
    }
}

interface TrustedContactsRepository {
    suspend fun read(): TrustedContactsSnapshot
    suspend fun replace(expectedRevision: String?, contacts: List<TrustedContact>): TrustedContactsSnapshot
}

class TrustedContactsConflictException : IllegalStateException("Los contactos cambiaron; vuelve a leerlos.")
