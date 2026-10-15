package com.example.citizensecurity.data

import com.example.citizensecurity.domain.ReportAlertsSnapshot
import org.junit.Assert.*
import org.junit.Test

class ReportAlertsCodecTest {
    private val revision = id(9999)
    private fun document() = ReportAlertsCodec.encode(ReportAlertsSnapshot(revision, true, setOf(id(1), id(2))))

    @Test fun roundTripKeepsRevisionPreferenceAndEveryIdWithDeterministicSetEncoding() {
        val snapshot = ReportAlertsSnapshot(revision, true, linkedSetOf(id(2), id(1)))
        assertEquals(snapshot, ReportAlertsCodec.decode(ReportAlertsCodec.encode(snapshot)))
        assertEquals(document(), ReportAlertsCodec.encode(snapshot))
    }
    @Test fun revisionedDisabledEmptyFileIsValidButMissingRevisionCannotBeEncoded() {
        val snapshot = ReportAlertsSnapshot(revision)
        assertEquals(snapshot, ReportAlertsCodec.decode(ReportAlertsCodec.encode(snapshot)))
        reject { ReportAlertsCodec.encode(ReportAlertsSnapshot(null)) }
    }
    @Test fun rejectsDuplicateUnknownMissingKeysAndTrailingObjects() {
        listOf(document().replace("\"schema\":1", "\"schema\":1,\"schema\":1"),
            document().replace("\"schema\":1", "\"schema\":1,\"owner\":\"x\""),
            document().replace("\"schema\":1,", ""), document() + "{}", document().dropLast(1) + ",}",
            document().replace("\"schema\":1", "\"schema\":1,\"\\u0073chema\":1"))
            .forEach { reject { ReportAlertsCodec.decode(it) } }
    }
    @Test fun rejectsSchemaAndBooleanCoercionsOrNestedContainers() {
        listOf("0", "2", "1.0", "01", "\"1\"", "null", "true", "[]").forEach { value ->
            reject { ReportAlertsCodec.decode(document().replace("\"schema\":1", "\"schema\":$value")) }
        }
        listOf("1", "\"true\"", "null", "{}", "[]", "truee").forEach { value ->
            reject { ReportAlertsCodec.decode(document().replace("\"enabled\":true", "\"enabled\":$value")) }
        }
    }
    @Test fun rejectsNonCanonicalRevisionMalformedIdsAndDuplicatePresentationIds() {
        listOf(document().replace(revision, revision.uppercase()), document().replace(id(1), "1-1-1-1-1"),
            document().replace(id(2), id(1)), document().replace("\"$revision\"", "null"),
            document().replace("\"${id(1)}\"", "3"), document().replace(id(1), "\\ud800"))
            .forEach { reject { ReportAlertsCodec.decode(it) } }
    }
    @Test fun rejectsOversizedTruncatedAndInvalidJsonWithoutPartialPreferences() {
        listOf(" ".repeat(ReportAlertsCodec.MAX_BYTES + 1), document().dropLast(1), "[]", "{}",
            document().replace("\"presented\":[", "\"presented\":{\"ids\":["),
            document().replace("\"enabled\"", "enabled"))
            .forEach { reject { ReportAlertsCodec.decode(it) } }
    }
    @Test fun acceptsAllFourThousandNinetySixIdsWithinTheByteBound() {
        val snapshot = ReportAlertsSnapshot(revision, true, (1..4096).map(::id).toSet())
        val json = ReportAlertsCodec.encode(snapshot)
        assertTrue(json.toByteArray(Charsets.UTF_8).size <= ReportAlertsCodec.MAX_BYTES)
        assertEquals(snapshot, ReportAlertsCodec.decode(json))
    }
    @Test fun rejectsArrayOverCapacityRatherThanDiscardingOldIds() {
        val array = (1..4097).joinToString(",") { "\"${id(it)}\"" }
        reject { ReportAlertsCodec.decode("{\"schema\":1,\"revision\":\"$revision\",\"enabled\":true,\"presented\":[$array]}") }
    }
    private fun id(number: Int) = "e0000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
    private fun reject(action: () -> Unit) {
        try { action(); fail("Invalid alert file accepted") } catch (_: Exception) { }
    }
}
