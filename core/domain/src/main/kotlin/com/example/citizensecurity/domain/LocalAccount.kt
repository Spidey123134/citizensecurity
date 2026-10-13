package com.example.citizensecurity.domain

import java.util.UUID

/** Identidad de una cuenta de este teléfono; no acredita una identidad civil ni un rol. */
data class LocalAccount(val id: String, val username: String) {
    init {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
            "La cuenta requiere un UUID completo y canónico."
        }
        require(AccountCredentialsValidator.isCanonicalUsername(username)) {
            "La cuenta requiere un usuario local válido y normalizado."
        }
    }
}

/** Registro y comprobación local. No conserva sesión ni envía credenciales a un servidor. */
interface AccountRepository {
    suspend fun register(username: String, password: String): LocalAccount
    suspend fun authenticate(username: String, password: String): LocalAccount?
}

enum class AccountField { USERNAME, PASSWORD }

data class AccountValidationErrors(val errors: Map<AccountField, String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

class AccountValidationException(val errors: AccountValidationErrors) :
    IllegalArgumentException(errors.errors.values.joinToString(" "))

class AccountAlreadyExistsException : IllegalStateException("Ese usuario ya existe en este teléfono.")

/** No conserva las credenciales originales ni las incluye en sus mensajes. */
object AccountCredentialsValidator {
    const val MIN_PASSWORD_CHARACTERS = 15
    const val MAX_PASSWORD_CHARACTERS = 128

    fun normalizeUsername(username: String): String = username.trim().map { character ->
        if (character in 'A'..'Z') character + ('a' - 'A') else character
    }.joinToString("")

    fun isCanonicalUsername(username: String): Boolean =
        username.length in 3..30 && username != "admin" &&
            username.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }

    fun validate(username: String, password: String): AccountValidationErrors {
        val errors = linkedMapOf<AccountField, String>()
        val normalized = normalizeUsername(username)
        when {
            normalized == "admin" -> errors[AccountField.USERNAME] =
                "El usuario admin está reservado para el acceso académico."
            !isCanonicalUsername(normalized) -> errors[AccountField.USERNAME] =
                "El usuario debe tener de 3 a 30 letras sin acentos, números o guion bajo."
        }
        val passwordCharacters = password.codePointCount(0, password.length)
        when {
            passwordCharacters !in MIN_PASSWORD_CHARACTERS..MAX_PASSWORD_CHARACTERS ->
                errors[AccountField.PASSWORD] = "La contraseña debe tener de 15 a 128 caracteres."
            password.isBlank() -> errors[AccountField.PASSWORD] =
                "La contraseña no puede contener únicamente espacios."
            !password.hasWellFormedUnicode() -> errors[AccountField.PASSWORD] =
                "La contraseña contiene un carácter Unicode incompleto."
        }
        return AccountValidationErrors(errors)
    }

    // No restringe los caracteres Unicode ni su composición: evita que un sustituto
    // incompleto sea reemplazado por el proveedor criptográfico y colisione con otro texto.
    private fun String.hasWellFormedUnicode(): Boolean {
        var index = 0
        while (index < length) {
            val character = this[index++]
            if (Character.isHighSurrogate(character)) {
                if (index == length || !Character.isLowSurrogate(this[index++])) return false
            } else if (Character.isLowSurrogate(character)) return false
        }
        return true
    }
}
