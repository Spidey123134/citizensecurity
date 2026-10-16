package com.example.citizensecurity.data

import com.example.citizensecurity.domain.LocalAdministratorAssignment
import java.time.Instant

/** Exact schema and types, with bounded allocation. No recovery from partial documents. */
internal object LocalAdministratorCodec {
    const val MAX_BYTES = 8 * 1024

    fun encode(assignment: LocalAdministratorAssignment): String =
        "{\"schema\":1,\"accountId\":\"${assignment.accountId}\",\"username\":\"${assignment.username}\"," +
            "\"operationId\":\"${assignment.operationId}\",\"grantedAt\":\"${assignment.grantedAt}\"}"

    fun decode(text: String): LocalAdministratorAssignment {
        require(text.length <= MAX_BYTES)
        return Parser(text).parse()
    }

    private class Parser(private val text: String) {
        private var position = 0
        fun parse(): LocalAdministratorAssignment {
            whitespace(); expect('{')
            val fields = linkedMapOf<String, String>()
            var schema = false
            val names = hashSetOf<String>()
            do {
                whitespace()
                val key = string()
                require(names.add(key)) { "Campo de asignación duplicado." }
                whitespace(); expect(':'); whitespace()
                when (key) {
                    "schema" -> { expect('1'); schema = true }
                    "accountId", "username", "operationId", "grantedAt" -> fields[key] = string()
                    else -> error("Campo de asignación incompatible.")
                }
                whitespace()
                if (take('}')) break
                expect(',')
            } while (true)
            whitespace()
            require(position == text.length && schema)
            require(names == setOf("schema", "accountId", "username", "operationId", "grantedAt"))
            val timestamp = fields.getValue("grantedAt")
            val instant = Instant.parse(timestamp)
            require(instant.toString() == timestamp) { "La fecha no es canónica." }
            return LocalAdministratorAssignment(fields.getValue("accountId"), fields.getValue("username"),
                fields.getValue("operationId"), instant)
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
                            else -> error("Escape de asignación inválido.")
                        }
                    }
                    require(length <= 64)
                }
            }
        }

        private fun whitespace() { while (position < text.length && text[position] in " \t\r\n") position++ }
        private fun take(expected: Char): Boolean = if (position < text.length && text[position] == expected) {
            position++; true
        } else false
        private fun expect(expected: Char) { require(take(expected)) { "JSON de asignación inválido." } }
    }
}
