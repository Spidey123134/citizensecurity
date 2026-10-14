package com.example.citizensecurity.data

import com.example.citizensecurity.domain.TrustedContact
import com.example.citizensecurity.domain.TrustedContactsSnapshot

/** Strict, bounded JSON for this private file; no duplicate keys, coercion or partial recovery. */
internal object TrustedContactsCodec {
    const val MAX_BYTES = 32_768

    fun encode(snapshot: TrustedContactsSnapshot): String {
        val revision = snapshot.revision
        require(revision != null)
        return "{\"schema\":1,\"revision\":${quote(revision)},\"contacts\":[" +
            snapshot.contacts.joinToString(",") { contact ->
                "{\"id\":${quote(contact.id)},\"name\":${quote(contact.name)},\"phoneNumber\":${quote(contact.phoneNumber)}}"
            } + "]}"
    }

    fun decode(text: String): TrustedContactsSnapshot {
        require(text.length <= MAX_BYTES)
        val root = Parser(text).parse().objectWith("schema", "revision", "contacts")
        require(root["schema"] == 1L) { "Versión de contactos desconocida." }
        val revision = root["revision"] as? String ?: error("Revisión inválida.")
        val list = root["contacts"] as? List<*> ?: error("Lista inválida.")
        require(list.size <= TrustedContact.MAX_CONTACTS)
        val contacts = list.map { value ->
            val item = value.objectWith("id", "name", "phoneNumber")
            TrustedContact(item.string("id"), item.string("name"), item.string("phoneNumber"))
        }
        return TrustedContactsSnapshot(revision, contacts)
    }

    private fun Any?.objectWith(vararg keys: String): Map<*, *> {
        val result = this as? Map<*, *> ?: error("Objeto inválido.")
        require(result.keys == keys.toSet()) { "Campos de contactos incompatibles." }
        return result
    }
    private fun Map<*, *>.string(key: String) = this[key] as? String ?: error("Texto inválido.")

    private fun quote(text: String): String = buildString {
        append('"')
        text.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                else -> append(c)
            }
        }
        append('"')
    }

    private class Parser(private val text: String) {
        private var index = 0
        fun parse(): Any? = value(0).also { whitespace(); require(index == text.length) }

        private fun value(depth: Int): Any? {
            require(depth <= 6)
            whitespace()
            require(index < text.length)
            return when (text[index]) {
                '{' -> obj(depth + 1)
                '[' -> array(depth + 1)
                '"' -> string()
                in '0'..'9', '-' -> number()
                else -> error("Valor JSON incompatible.")
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            expect('{')
            val result = linkedMapOf<String, Any?>()
            whitespace()
            if (take('}')) return result
            do {
                whitespace()
                val key = string()
                require(!result.containsKey(key)) { "Campo duplicado." }
                whitespace(); expect(':')
                result[key] = value(depth)
                require(result.size <= 8)
                whitespace()
                if (take('}')) return result
                expect(',')
            } while (true)
        }

        private fun array(depth: Int): List<Any?> {
            expect('[')
            val result = arrayListOf<Any?>()
            whitespace()
            if (take(']')) return result
            do {
                result.add(value(depth))
                require(result.size <= TrustedContact.MAX_CONTACTS)
                whitespace()
                if (take(']')) return result
                expect(',')
            } while (true)
        }

        private fun number(): Long {
            val start = index
            if (take('-')) require(index < text.length)
            if (!take('0')) {
                require(text[index] in '1'..'9')
                while (index < text.length && text[index] in '0'..'9') index++
            }
            return text.substring(start, index).toLong()
        }

        private fun string(): String {
            expect('"')
            return buildString {
                while (true) {
                    require(index < text.length)
                    val c = text[index++]
                    if (c == '"') break
                    require(c >= ' ')
                    if (c != '\\') append(c) else {
                        require(index < text.length)
                        when (val escaped = text[index++]) {
                            '"', '\\', '/' -> append(escaped)
                            'b' -> append('\b')
                            'f' -> append('\u000c')
                            'n' -> append('\n')
                            'r' -> append('\r')
                            't' -> append('\t')
                            'u' -> {
                                require(index + 4 <= text.length)
                                val digits = text.substring(index, index + 4)
                                require(digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                                append(digits.toInt(16).toChar()); index += 4
                            }
                            else -> error("Escape JSON inválido.")
                        }
                    }
                    require(length <= MAX_BYTES)
                }
            }
        }

        private fun whitespace() { while (index < text.length && text[index] in " \t\r\n") index++ }
        private fun take(c: Char): Boolean = if (index < text.length && text[index] == c) { index++; true } else false
        private fun expect(c: Char) { require(take(c)) { "JSON de contactos inválido." } }
    }
}
