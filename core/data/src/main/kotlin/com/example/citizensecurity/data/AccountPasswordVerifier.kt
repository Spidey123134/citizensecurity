package com.example.citizensecurity.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Solo contiene el algoritmo; no retiene una contraseña o su verificador en la instancia. */
internal interface AccountPasswordDerivation {
    fun create(password: String): String
    fun matches(password: String, encoded: String): Boolean
    fun validate(encoded: String)
}

internal class AccountPasswordVerifier : AccountPasswordDerivation {
    override fun create(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt, ITERATIONS)
        return try {
            "$PREFIX:$ITERATIONS:${salt.toHex()}:${hash.toHex()}"
        } finally {
            hash.fill(0)
        }
    }

    override fun matches(password: String, encoded: String): Boolean {
        val parsed = decode(encoded)
        val actual = derive(password, parsed.salt, parsed.iterations)
        return try {
            MessageDigest.isEqual(parsed.hash, actual)
        } finally {
            actual.fill(0)
            parsed.hash.fill(0)
        }
    }

    /** Valida antes del KDF: un registro dañado no puede pedir memoria o trabajo sin límite. */
    override fun validate(encoded: String) { decode(encoded).hash.fill(0) }

    private fun decode(encoded: String): ParsedVerifier {
        if (encoded.length !in 1..MAX_ENCODED_CHARACTERS) throw AccountRecordCorruptException()
        val fields = encoded.split(':')
        if (fields.size != 5 || fields[0] != "pbkdf2-sha256" || fields[1] != "v1") {
            throw AccountRecordCorruptException()
        }
        val iterations = fields[2].toIntOrNull()
        // Esta versión admite exactamente el coste que escribe; futuros costes requieren
        // una versión explícita, no se ejecutan parámetros arbitrarios de la base.
        if (iterations != ITERATIONS || fields[2] != ITERATIONS.toString()) {
            throw AccountRecordCorruptException()
        }
        return ParsedVerifier(iterations, fields[3].fromHex(SALT_BYTES), fields[4].fromHex(HASH_BYTES))
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val characters = password.toCharArray()
        val specification = PBEKeySpec(characters, salt, iterations, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).encoded
        } finally {
            characters.fill('\u0000')
            specification.clearPassword()
        }
    }

    // Clase ordinaria: toString no debe mostrar salt/hash ni generar copias accidentales.
    private class ParsedVerifier(val iterations: Int, val salt: ByteArray, val hash: ByteArray)

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        val unsigned = byte.toInt() and 0xff
        "${HEX[unsigned ushr 4]}${HEX[unsigned and 15]}"
    }

    private fun String.fromHex(bytes: Int): ByteArray {
        if (length != bytes * 2 || any { it !in '0'..'9' && it !in 'a'..'f' }) {
            throw AccountRecordCorruptException()
        }
        return ByteArray(bytes) { index ->
            ((HEX.indexOf(this[index * 2]) shl 4) or HEX.indexOf(this[index * 2 + 1])).toByte()
        }
    }

    companion object {
        const val ITERATIONS = 600_000
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32
        const val MAX_ENCODED_CHARACTERS = 160
        private const val PREFIX = "pbkdf2-sha256:v1"
        private const val HEX = "0123456789abcdef"

        // El usuario inexistente consume el mismo KDF, sin crear una cuenta ni un archivo.
        val MISSING_ACCOUNT_VERIFIER = "$PREFIX:$ITERATIONS:${"0".repeat(SALT_BYTES * 2)}:${"0".repeat(HASH_BYTES * 2)}"
    }
}

internal class AccountRecordCorruptException : IllegalStateException(
    "La cuenta local contiene datos incompatibles o dañados. No se ha modificado.",
)
