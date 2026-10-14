package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportStatus
import com.example.citizensecurity.domain.ReportStatusChangeCommand
import com.example.citizensecurity.domain.ReportStatusChangeRequest
import com.example.citizensecurity.domain.ReportStatusChangeScope
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant

/** Exact, bounded versioned body. A journal never recovers only a subset of a request. */
internal object ReportStatusChangeCodec {
    const val MAX_BYTES = 8_192

    fun encode(request: ReportStatusChangeRequest): ByteArray {
        val command = request.command
        val document = "{\"schema\":1,\"ownerAccountId\":" +
            (request.scope.ownerAccountId?.let(::quote) ?: "null") +
            ",\"actorAccountId\":${quote(request.scope.actorAccountId)}," +
            "\"operationId\":${quote(command.operationId)},\"reportId\":${quote(command.reportId)}," +
            "\"expectedSequence\":${command.expectedSequence},\"expectedStatus\":${quote(command.expectedStatus.name)}," +
            "\"targetStatus\":${quote(command.targetStatus.name)},\"reason\":${quote(command.reason)}," +
            "\"requestedAtSeconds\":${request.requestedAt.epochSecond},\"requestedAtNanos\":${request.requestedAt.nano}}"
        return document.toByteArray(Charsets.UTF_8).also {
            require(it.size <= MAX_BYTES) { "La solicitud supera el tamaño permitido." }
        }
    }

    fun decode(bytes: ByteArray): ReportStatusChangeRequest {
        require(bytes.size <= MAX_BYTES) { "La solicitud supera el tamaño permitido." }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val root = Parser(text).parse()
        require(root.keys == setOf("schema", "ownerAccountId", "actorAccountId", "operationId", "reportId",
            "expectedSequence", "expectedStatus", "targetStatus", "reason", "requestedAtSeconds", "requestedAtNanos")) {
            "Campos de solicitud incompatibles."
        }
        require(root["schema"] == 1L) { "Versión de solicitud desconocida." }
        val owner = root["ownerAccountId"].let { value ->
            require(value == null || value is String) { "Propietario inválido." }
            value
        }
        val nanos = root.long("requestedAtNanos")
        require(nanos in 0..999_999_999L) { "Nanosegundos inválidos." }
        return ReportStatusChangeRequest(
            scope = ReportStatusChangeScope(owner, root.string("actorAccountId")),
            command = ReportStatusChangeCommand(
                root.string("operationId"), root.string("reportId"), root.long("expectedSequence"),
                ReportStatus.valueOf(root.string("expectedStatus")), ReportStatus.valueOf(root.string("targetStatus")),
                root.string("reason"),
            ),
            requestedAt = Instant.ofEpochSecond(root.long("requestedAtSeconds"), nanos),
        )
    }

    private fun Map<String, Any?>.string(key: String): String = this[key] as? String
        ?: error("Texto inválido: $key")
    private fun Map<String, Any?>.long(key: String): Long = this[key] as? Long
        ?: error("Entero inválido: $key")

    private fun quote(text: String): String = buildString {
        append('"')
        text.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u" + char.code.toString(16).padStart(4, '0')) else append(char)
            }
        }
        append('"')
    }

    /** This envelope contains only flat scalar fields; containers and coercions are rejected. */
    private class Parser(private val text: String) {
        private var index = 0

        fun parse(): Map<String, Any?> {
            whitespace(); expect('{')
            val result = linkedMapOf<String, Any?>()
            whitespace()
            if (take('}')) return finish(result)
            while (true) {
                whitespace()
                val key = string()
                require(!result.containsKey(key)) { "Campo duplicado." }
                whitespace(); expect(':'); whitespace()
                require(index < text.length)
                result[key] = when (text[index]) {
                    '"' -> string()
                    '-', in '0'..'9' -> number()
                    'n' -> { require(text.startsWith("null", index)); index += 4; null }
                    else -> error("Valor JSON incompatible.")
                }
                require(result.size <= 12)
                whitespace()
                if (take('}')) return finish(result)
                expect(',')
            }
        }

        private fun finish(result: Map<String, Any?>): Map<String, Any?> {
            whitespace(); require(index == text.length) { "Contenido adicional en la solicitud." }
            return result
        }

        private fun number(): Long {
            val start = index
            if (take('-')) require(index < text.length)
            if (!take('0')) {
                require(index < text.length && text[index] in '1'..'9')
                while (index < text.length && text[index] in '0'..'9') index++
            }
            return text.substring(start, index).toLong()
        }

        private fun string(): String {
            expect('"')
            val value = buildString {
                while (true) {
                    require(index < text.length)
                    val char = text[index++]
                    if (char == '"') break
                    require(char >= ' ') { "Control JSON sin escape." }
                    if (char != '\\') append(char) else {
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
            var cursor = 0
            while (cursor < value.length) {
                val char = value[cursor++]
                if (Character.isHighSurrogate(char)) {
                    require(cursor < value.length && Character.isLowSurrogate(value[cursor++])) { "Unicode inválido." }
                } else require(!Character.isLowSurrogate(char)) { "Unicode inválido." }
            }
            return value
        }

        private fun whitespace() { while (index < text.length && text[index] in " \t\r\n") index++ }
        private fun take(char: Char): Boolean = if (index < text.length && text[index] == char) { index++; true } else false
        private fun expect(char: Char) { require(take(char)) { "JSON de solicitud inválido." } }
    }
}
