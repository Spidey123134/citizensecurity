package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportAlertsSnapshot

/** Bounded, strict JSON: exact fields/types, canonical identities and no partial recovery. */
internal object ReportAlertsCodec {
    const val MAX_BYTES = 256 * 1024

    fun encode(snapshot: ReportAlertsSnapshot): String {
        val revision = requireNotNull(snapshot.revision)
        return "{\"schema\":1,\"revision\":\"$revision\",\"enabled\":${snapshot.enabled},\"presented\":[" +
            snapshot.presented.sorted().joinToString(",") { "\"$it\"" } + "]}"
    }

    fun decode(text: String): ReportAlertsSnapshot {
        require(text.length <= MAX_BYTES)
        return Parser(text).parse()
    }

    private class Parser(private val text: String) {
        private var position = 0
        fun parse(): ReportAlertsSnapshot {
            whitespace(); expect('{'); whitespace()
            var revision: String? = null
            var enabled: Boolean? = null
            var presented: Set<String>? = null
            val fields = hashSetOf<String>()
            do {
                whitespace()
                val key = string()
                require(fields.add(key)) { "Campo de avisos duplicado." }
                whitespace(); expect(':'); whitespace()
                when (key) {
                    "schema" -> expect('1')
                    "revision" -> revision = string()
                    "enabled" -> enabled = boolean()
                    "presented" -> presented = identities()
                    else -> error("Campo de avisos incompatible.")
                }
                whitespace()
                if (take('}')) break
                expect(',')
            } while (true)
            whitespace()
            require(position == text.length)
            require(fields == setOf("schema", "revision", "enabled", "presented"))
            return ReportAlertsSnapshot(requireNotNull(revision), requireNotNull(enabled), requireNotNull(presented))
        }

        private fun identities(): Set<String> {
            expect('['); whitespace()
            val result = linkedSetOf<String>()
            if (take(']')) return result
            do {
                whitespace()
                require(result.add(string())) { "Evento de aviso duplicado." }
                require(result.size <= ReportAlertsSnapshot.MAX_PRESENTED)
                whitespace()
                if (take(']')) return result
                expect(',')
            } while (true)
        }

        private fun boolean(): Boolean = when {
            text.startsWith("true", position) -> { position += 4; true }
            text.startsWith("false", position) -> { position += 5; false }
            else -> error("La preferencia de avisos no es booleana.")
        }

        private fun string(): String {
            expect('"')
            return buildString {
                while (true) {
                    require(position < text.length)
                    val current = text[position++]
                    if (current == '"') break
                    require(current >= ' ')
                    if (current != '\\') append(current) else {
                        require(position < text.length)
                        when (val escaped = text[position++]) {
                            '"', '\\', '/' -> append(escaped)
                            'b' -> append('\b')
                            'f' -> append('\u000c')
                            'n' -> append('\n')
                            'r' -> append('\r')
                            't' -> append('\t')
                            'u' -> {
                                require(position + 4 <= text.length)
                                val digits = text.substring(position, position + 4)
                                require(digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                                append(digits.toInt(16).toChar()); position += 4
                            }
                            else -> error("Escape de avisos inválido.")
                        }
                    }
                    // Keys/UUIDs are the only strings in this file; bound before allocation grows.
                    require(length <= 64)
                }
            }
        }

        private fun whitespace() { while (position < text.length && text[position] in " \t\r\n") position++ }
        private fun take(expected: Char): Boolean = if (position < text.length && text[position] == expected) {
            position++; true
        } else false
        private fun expect(expected: Char) { require(take(expected)) { "JSON de avisos inválido." } }
    }
}
