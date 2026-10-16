package com.example.citizensecurity.domain

import java.time.Instant
import java.util.UUID

/** One installation-local grant. A username alone, academic login or UUID is never a grant. */
data class LocalAdministratorAssignment(
    val accountId: String,
    val username: String,
    val operationId: String,
    val grantedAt: Instant,
) {
    init {
        requireCanonicalId(accountId)
        requireCanonicalId(operationId)
        require(username == USERNAME) { "La asignación requiere la cuenta local acordada." }
        require(grantedAt >= Instant.EPOCH && grantedAt <= MAX_GRANTED_AT) {
            "La asignación requiere una fecha válida."
        }
    }

    /** The identity must match both the captured workspace and its signed-in actor. */
    fun allows(scope: ReportStatusChangeScope): Boolean =
        scope.ownerAccountId == accountId && scope.actorAccountId == accountId

    companion object {
        const val USERNAME = "admin_local"
        private val MAX_GRANTED_AT = Instant.parse("9999-12-31T23:59:59.999999999Z")
        private fun requireCanonicalId(id: String) {
            require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) {
                "La asignación requiere identidades UUID completas y canónicas."
            }
        }
    }
}

/** A first assignment cannot be replaced, even with the same username or operation ID. */
class LocalAdministratorConflictException : IllegalStateException(
    "Ya existe una asignación local distinta. Se conserva la asignación anterior.",
)
