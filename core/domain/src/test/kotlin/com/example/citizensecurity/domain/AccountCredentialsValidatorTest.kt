package com.example.citizensecurity.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCredentialsValidatorTest {
    private val password = "Una frase larga y fácil 🧭"

    @Test fun recortaYNormalizaSoloLetrasAsciiDelUsuario() {
        assertEquals("ana_123", AccountCredentialsValidator.normalizeUsername("  ANA_123\t"))
        assertTrue(AccountCredentialsValidator.validate("  ANA_123\t", password).isValid)
    }

    @Test fun usuariosCortosLargosAcentuadosYSeparadoresNoSonValidos() {
        listOf("ab", "a".repeat(31), "José", "ana maria", "../ana", "ana\u0000", "Key").forEach {
            assertTrue(AccountField.USERNAME in AccountCredentialsValidator.validate(it, password).errors)
        }
        assertTrue(AccountCredentialsValidator.validate("a".repeat(30), password).isValid)
    }

    @Test fun adminEstaReservadoTambienConMayusculasYEspacios() {
        listOf("admin", " ADMIN ").forEach {
            assertTrue(AccountField.USERNAME in AccountCredentialsValidator.validate(it, password).errors)
        }
    }

    @Test fun aplicaLimites15Y128SinReglasDeComposicion() {
        assertTrue(AccountCredentialsValidator.validate("ana", "a".repeat(15)).isValid)
        assertTrue(AccountCredentialsValidator.validate("ana", "a".repeat(128)).isValid)
        listOf("a".repeat(14), "a".repeat(129), " ".repeat(15)).forEach {
            assertTrue(AccountField.PASSWORD in AccountCredentialsValidator.validate("ana", it).errors)
        }
    }

    @Test fun cuentaCaracteresUnicodeYPermiteEspaciosAcentosYNullSinNormalizar() {
        assertTrue(AccountCredentialsValidator.validate("ana", "🧭".repeat(15)).isValid)
        assertTrue(AccountCredentialsValidator.validate("ana", "🧭".repeat(128)).isValid)
        assertFalse(AccountCredentialsValidator.validate("ana", "🧭".repeat(129)).isValid)
        assertTrue(AccountCredentialsValidator.validate("ana", "   áéíóú abc\u0000 🧭   ").isValid)
    }

    @Test fun rechazaUnicodeIncompletoParaEvitarReemplazosDelProveedor() {
        listOf("a".repeat(15) + '\uD800', "a".repeat(15) + '\uDC00').forEach {
            assertTrue(AccountField.PASSWORD in AccountCredentialsValidator.validate("ana", it).errors)
        }
    }

    @Test fun mensajesYExcepcionesNoIncluyenCredenciales() {
        val username = "usuario sensible @"
        val secret = "confidencial"
        val errors = AccountCredentialsValidator.validate(username, secret)
        val exception = AccountValidationException(errors)
        assertFalse(exception.toString().contains(username))
        assertFalse(exception.toString().contains(secret))
        assertTrue(AccountField.USERNAME in errors.errors)
        assertTrue(AccountField.PASSWORD in errors.errors)
    }

    @Test fun identidadSoloAdmiteUuidCanonicoYUsuarioNormalizado() {
        val id = "a142cefe-f76b-44de-890e-d3a8b6630190"
        assertEquals(id, LocalAccount(id, "ana_123").id)
        listOf("ANA", " admin ", "admin").forEach { username ->
            assertThrowsInvalid { LocalAccount(id, username) }
        }
        assertThrowsInvalid { LocalAccount("1-1-1-1-1", "ana") }
        assertThrowsInvalid { LocalAccount(id.uppercase(), "ana") }
    }

    private fun assertThrowsInvalid(action: () -> Unit) {
        var thrown = false
        try { action() } catch (_: IllegalArgumentException) { thrown = true }
        assertTrue("Debe rechazar una identidad inválida.", thrown)
    }
}
