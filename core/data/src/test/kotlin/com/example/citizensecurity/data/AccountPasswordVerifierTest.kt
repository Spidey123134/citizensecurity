package com.example.citizensecurity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPasswordVerifierTest {
    private val verifier = AccountPasswordVerifier()
    private val password = "Una frase local sin secretos reales"

    @Test fun salAleatoriaDiferenciaLaMismaContrasenaYAmbasVerifican() {
        val first = verifier.create(password)
        val second = verifier.create(password)
        assertNotEquals(first, second)
        assertTrue(verifier.matches(password, first))
        assertTrue(verifier.matches(password, second))
        assertFalse(first.contains(password))
        assertEquals(16 * 2, first.split(':')[3].length)
        assertEquals(32 * 2, first.split(':')[4].length)
        assertEquals("600000", first.split(':')[2])
    }

    @Test fun contrasenaIncorrectaNoVerifica() {
        assertFalse(verifier.matches("Otra frase local sin secretos reales", verifier.create(password)))
    }

    @Test fun verificaUnVectorIndependienteDePbkdf2Sha256() {
        // Calculado independientemente con Python/OpenSSL hashlib.pbkdf2_hmac:
        // password UTF-8, salt bytes 00..0f, 600000 iteraciones, 32 bytes.
        val encoded = "pbkdf2-sha256:v1:600000:000102030405060708090a0b0c0d0e0f:" +
            "29333c279ae8184296f77604a72f0fc57761fa3251b4c7cdcc13f73c2772ca98"
        assertTrue(verifier.matches("phase25 known vector", encoded))
    }

    @Test fun conservaMayusculasYEspaciosSinRecortar() {
        val encoded = verifier.create("  Una frase exacta para guardar  ")
        assertTrue(verifier.matches("  Una frase exacta para guardar  ", encoded))
        assertFalse(verifier.matches("Una frase exacta para guardar", encoded))
        assertFalse(verifier.matches("  una frase exacta para guardar  ", encoded))
    }

    @Test fun unicodeCombinadoEmojiYNullConservanElTextoExacto() {
        val password = "Contraseña á 🧭 y \u0000 texto"
        val encoded = verifier.create(password)
        assertTrue(verifier.matches(password, encoded))
        assertFalse(verifier.matches("Contraseña a\u0301 🧭 y \u0000 texto", encoded))
        assertFalse(verifier.matches("Contraseña á 🧭 y  texto", encoded))
    }

    @Test fun limiteUnicodeDe128CaracteresNoTruncaElUltimo() {
        val password = "🧭".repeat(127) + "z"
        val encoded = verifier.create(password)
        assertTrue(verifier.matches(password, encoded))
        assertFalse(verifier.matches("🧭".repeat(127) + "y", encoded))
    }

    @Test fun parametrosYCodificacionDanadosSeRechazanAntesDelKdf() {
        val valid = AccountPasswordVerifier.MISSING_ACCOUNT_VERIFIER
        listOf(
            "", "x".repeat(161), valid.replace("v1", "v2"), valid.replace("sha256", "sha1"),
            valid.replace("600000", "0600000"), valid.replace("600000", "2147483647"),
            valid.replace("600000", "-1"), valid.replace("600000", "1"),
            valid + ":extra", valid.dropLast(1), valid.dropLast(1) + "G",
            valid.replace(":${"0".repeat(32)}:", ":${"A".repeat(32)}:"),
        ).forEach { malformed ->
            var rejected = false
            try { verifier.matches(password, malformed) }
            catch (failure: AccountRecordCorruptException) {
                rejected = true
                assertFalse(failure.toString().contains(password))
                assertFalse(failure.toString().contains(malformed.takeIf { it.isNotEmpty() } ?: "<empty>"))
            }
            assertTrue("Un verificador incompatible debe fallar cerrado.", rejected)
        }
    }

    @Test fun toStringDelServicioNoIncluyeCredencialesNiVerificadores() {
        val encoded = verifier.create(password)
        assertFalse(verifier.toString().contains(password))
        assertFalse(verifier.toString().contains(encoded))
    }
}
