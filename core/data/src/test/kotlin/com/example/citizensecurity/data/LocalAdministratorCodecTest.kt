package com.example.citizensecurity.data

import com.example.citizensecurity.domain.LocalAdministratorAssignment
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class LocalAdministratorCodecTest {
    private val account = "d0000000-0000-0000-0000-000000000001"
    private val operation = "d0000000-0000-0000-0000-000000000002"
    private val time = "2026-10-15T12:00:00.123456789Z"
    private val assignment = LocalAdministratorAssignment(account, "admin_local", operation, Instant.parse(time))
    private fun document() = LocalAdministratorCodec.encode(assignment)

    @Test fun strictReceiptRoundTripPreservesEveryFieldAndNanoseconds() {
        assertEquals(assignment, LocalAdministratorCodec.decode(document()))
        assertEquals(document(), LocalAdministratorCodec.encode(LocalAdministratorCodec.decode(document())))
        assertTrue(document().toByteArray(Charsets.UTF_8).size < LocalAdministratorCodec.MAX_BYTES)
    }

    @Test fun propertyOrderAndJsonWhitespaceDoNotChangeTheReceipt() {
        val reordered = " {\n\"grantedAt\":\"$time\",\"operationId\":\"$operation\", " +
            "\"schema\":1,\"username\":\"admin_local\",\"accountId\":\"$account\"}\r\n"
        assertEquals(assignment, LocalAdministratorCodec.decode(reordered))
    }

    @Test fun duplicateEscapedDuplicateUnknownMissingAndTrailingFieldsAreRejected() {
        listOf(document().replace("\"schema\":1", "\"schema\":1,\"schema\":1"),
            document().replace("\"schema\":1", "\"schema\":1,\"\\u0073chema\":1"),
            document().replace("\"schema\":1", "\"schema\":1,\"enabled\":true"),
            document().replace("\"schema\":1,", ""), document() + "{}", document().dropLast(1) + ",}")
            .forEach(::reject)
    }

    @Test fun schemaAndStringTypeCoercionsCannotGrantPermission() {
        listOf("0", "2", "1.0", "01", "\"1\"", "null", "true", "[]", "{}")
            .forEach { reject(document().replace("\"schema\":1", "\"schema\":$it")) }
        listOf("null", "true", "1", "[]", "{}")
            .forEach { reject(document().replace("\"$account\"", it)) }
    }

    @Test fun malformedIdentitiesUsernamesAndSurrogatesAreRejected() {
        listOf(document().replace(account, account.uppercase()), document().replace(operation, "1-1-1-1-1"),
            document().replace(account, "\\ud800"), document().replace("admin_local", "admin"),
            document().replace("admin_local", "admin_local "), document().replace("admin_local", "other_user"))
            .forEach(::reject)
    }

    @Test fun datesRequireCanonicalUtcPrecisionWithinTheSupportedRange() {
        listOf("invalid", "2026-10-15T12:00:00.123456789+00:00", "2026-10-15T12:00:00.123456789z",
            "1969-12-31T23:59:59Z", "+10000-01-01T00:00:00Z", "2026-02-30T00:00:00Z")
            .forEach { reject(document().replace(time, it)) }
    }

    @Test fun oversizedTruncatedControlCharactersAndWrongContainersCannotRecoverPartially() {
        listOf(" ".repeat(LocalAdministratorCodec.MAX_BYTES + 1), document().dropLast(1), "[]", "{}",
            document().replace("admin_local", "admin\nlocal"), document().replace("\"username\"", "username"),
            document().replace("admin_local", "a".repeat(65)))
            .forEach(::reject)
    }

    private fun reject(document: String) {
        try { LocalAdministratorCodec.decode(document); fail("Invalid assignment file accepted") }
        catch (_: Exception) { }
    }
}
