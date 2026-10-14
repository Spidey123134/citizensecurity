package com.example.citizensecurity.data

import com.example.citizensecurity.domain.*
import java.time.Instant
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class ReportStatusChangeCodecTest {
    private fun request(owner: String? = UUID.randomUUID().toString(), reason: String = "Revisión del café 🌼") =
        ReportStatusChangeRequest(ReportStatusChangeScope(owner, UUID.randomUUID().toString()),
            ReportStatusChangeCommand(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1L,
                ReportStatus.REPORTED, ReportStatus.IN_REVIEW, reason), Instant.parse("2026-10-14T01:02:03.123456789Z"))
    private fun json(request: ReportStatusChangeRequest = request()) = ReportStatusChangeCodec.encode(request).toString(Charsets.UTF_8)
    private fun reject(text: String) = reject(text.toByteArray(Charsets.UTF_8))
    private fun reject(bytes: ByteArray) {
        try { ReportStatusChangeCodec.decode(bytes); fail("Expected incompatible request rejection") }
        catch (expected: Exception) { /* Full envelope rejected. */ }
    }

    @Test fun accountAndAcademicScopesRoundTripWithEveryFieldUnchanged() {
        listOf(request(), request(null)).forEach { assertEquals(it, ReportStatusChangeCodec.decode(ReportStatusChangeCodec.encode(it))) }
    }
    @Test fun escapedQuoteSlashAndUnicodeReasonRoundTrips() {
        val expected = request(reason = "Revisión \\\"del café\\\" / México 🌼")
        assertEquals(expected, ReportStatusChangeCodec.decode(ReportStatusChangeCodec.encode(expected)))
    }
    @Test fun largestReasonAndSequenceKeepExactIntegers() {
        val expected = request(reason = "🌼".repeat(500)).let { it.copy(command = it.command.copy(expectedSequence = Long.MAX_VALUE - 1L)) }
        assertEquals(expected, ReportStatusChangeCodec.decode(ReportStatusChangeCodec.encode(expected)))
    }
    @Test fun extremeInstantKeepsSecondsAndNanosecondsWithoutDoubleRounding() {
        listOf(Instant.MIN, Instant.MAX).forEach { time ->
            val expected = request().copy(requestedAt = time)
            assertEquals(expected, ReportStatusChangeCodec.decode(ReportStatusChangeCodec.encode(expected)))
        }
    }
    @Test fun unknownSchemaIsRejected() = reject(json().replace("\"schema\":1", "\"schema\":2"))
    @Test fun duplicateKeysAreRejectedEvenWhenTheyAgree() = reject(json().replace("\"schema\":1", "\"schema\":1,\"schema\":1"))
    @Test fun missingRequiredFieldIsRejected() = reject(json().replace("\"requestedAtNanos\":123456789", "\"notNanos\":123456789"))
    @Test fun unknownExtraFieldIsRejected() = reject(json().dropLast(1) + ",\"extra\":null}")
    @Test fun trailingJsonOrTextIsRejected() { listOf("{}", " garbage").forEach { reject(json() + it) } }
    @Test fun numbersCannotBeStringsFloatsExponentsOrBooleans() {
        listOf("\"1\"", "1.0", "1e0", "true", "[]", "{}").forEach { reject(json().replace("\"expectedSequence\":1", "\"expectedSequence\":$it")) }
    }
    @Test fun leadingZeroAndOverflowNumbersAreRejected() {
        listOf("01", "9223372036854775808", "-9223372036854775809").forEach { reject(json().replace("\"expectedSequence\":1", "\"expectedSequence\":$it")) }
    }
    @Test fun outOfBoundsSequenceAndNanosAreRejected() {
        listOf("-1", "9223372036854775807").forEach { reject(json().replace("\"expectedSequence\":1", "\"expectedSequence\":$it")) }
        listOf("-1", "1000000000").forEach { reject(json().replace("\"requestedAtNanos\":123456789", "\"requestedAtNanos\":$it")) }
    }
    @Test fun abbreviatedAndNoncanonicalOwnerAndActorIdsAreRejected() {
        val expected = request()
        listOf("1-1-1-1-1", "../owner", "876A378A-D8CE-4A21-A091-5C662008DDAA").forEach { bad ->
            reject(json(expected).replace(expected.scope.actorAccountId, bad))
        }
        reject(json(expected).replace(expected.scope.ownerAccountId!!, "../other"))
    }
    @Test fun malformedUtf8EmptyAndOversizedBodiesAreRejected() {
        reject(byteArrayOf(0xc3.toByte(), 0x28)); reject(byteArrayOf())
        reject(ByteArray(ReportStatusChangeCodec.MAX_BYTES + 1) { 0x20 })
    }
    @Test fun loneSurrogateEscapesAreRejected() {
        val body = json()
        listOf("\\ud800", "\\udfff").forEach { reject(body.replace("Revisión del café 🌼", "Texto $it")) }
    }
    @Test fun escapedControlCharactersCannotEnterTheReason() {
        listOf("\\n", "\\u0000", "\\t").forEach { reject(json().replace("Revisión del café 🌼", "Texto $it inválido")) }
    }
    @Test fun invalidTransitionEnumsAndShortReasonAreRejected() {
        reject(json().replace("\"targetStatus\":\"IN_REVIEW\"", "\"targetStatus\":\"CLOSED\""))
        reject(json().replace("\"targetStatus\":\"IN_REVIEW\"", "\"targetStatus\":\"OTHER\""))
        reject(json().replace("Revisión del café 🌼", "abc"))
    }
    @Test fun surroundingJsonWhitespaceDoesNotChangeTheRecoveredRequest() {
        val expected = request()
        assertEquals(expected, ReportStatusChangeCodec.decode(("\r\n " + json(expected) + " \t").toByteArray()))
    }
}
