package com.example.citizensecurity.report

import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportListCursor
import com.example.citizensecurity.domain.ReportStatus
import java.time.Instant
import java.time.ZoneOffset

internal const val MAX_REPORT_CURSOR_HISTORY = 50

internal enum class ReportPageNavigation { SEARCH, NEXT, PREVIOUS, RELOAD }

internal data class ReportPageStart(val before: ReportListCursor?, val pageNumber: Int)

internal data class ReportPageRequest(
    val filters: ReportSearchFilters,
    val before: ReportListCursor?,
    val pageNumber: Int,
    val navigation: ReportPageNavigation,
    val historyIndex: Int = 0,
)

internal data class ReportSearchCheckpoint(
    val request: ReportPageRequest,
    val history: List<ReportPageStart>,
    val historyIndex: Int,
)

/** Only bounded strings cross Android's saved-state boundary; no result or device evidence. */
internal object ReportSearchCheckpointCodec {
    const val KEY = "reportPagedSearch.checkpoint"
    const val MAX_CHARACTERS = 15_000
    private const val VERSION = "1"
    private const val HEADER_FIELDS = 17
    private const val START_FIELDS = 4
    private const val MAX_FIELDS = HEADER_FIELDS + START_FIELDS * MAX_REPORT_CURSOR_HISTORY
    // Canonical Unicode decomposition can expand the UI's 512 UTF-16 input units.
    private const val MAX_TEXT = 4_096

    fun encode(checkpoint: ReportSearchCheckpoint): ArrayList<String>? = runCatching {
        validate(checkpoint)
        val request = checkpoint.request
        val filters = request.filters
        val fields = arrayListOf(
            VERSION, filters.type?.name.orEmpty(), filters.status?.name.orEmpty(),
            filters.priority?.name.orEmpty(), filters.normalizedText,
        )
        fields.addInstant(filters.startInclusive)
        fields.addInstant(filters.endExclusive)
        fields.addCursor(request.before)
        fields.add(request.pageNumber.toString())
        fields.add(request.navigation.name)
        fields.add(request.historyIndex.toString())
        fields.add(checkpoint.historyIndex.toString())
        fields.add(checkpoint.history.size.toString())
        checkpoint.history.forEach { start ->
            fields.addCursor(start.before)
            fields.add(start.pageNumber.toString())
        }
        require(fields.size <= MAX_FIELDS && fields.sumOf { it.length } <= MAX_CHARACTERS)
        fields
    }.getOrNull()

    /** Reject the entire checkpoint, including its filters, when any field is incompatible. */
    fun decode(raw: Any?): ReportSearchCheckpoint? = runCatching {
        require(raw is List<*> && raw.size in HEADER_FIELDS..MAX_FIELDS)
        var total = 0
        val fields = raw.map {
            require(it is String && it.length <= MAX_TEXT)
            total += it.length
            require(total <= MAX_CHARACTERS)
            it
        }
        val reader = Reader(fields)
        require(reader.next() == VERSION)
        val type = reader.enumOrNull(IncidentType.entries)
        val status = reader.enumOrNull(ReportStatus.entries)
        val priority = reader.enumOrNull(Priority.entries)
        val text = reader.next()
        val filters = ReportSearchFilters(type, status, text, reader.instant(), reader.instant(), priority)
        val cursor = reader.cursor()
        val pageNumber = reader.int()
        val navigation = reader.enumOrNull(ReportPageNavigation.entries)
        require(navigation != null)
        val requestIndex = reader.int()
        val historyIndex = reader.int()
        val count = reader.int()
        require(count in 0..MAX_REPORT_CURSOR_HISTORY)
        require(fields.size == HEADER_FIELDS + count * START_FIELDS)
        val history = List(count) { ReportPageStart(reader.cursor(), reader.int()) }
        val checkpoint = ReportSearchCheckpoint(
            ReportPageRequest(filters, cursor, pageNumber, navigation, requestIndex), history, historyIndex,
        )
        validate(checkpoint)
        // Numeric fields, optional sentinels and enum names have one canonical representation.
        require(encode(checkpoint) == fields)
        checkpoint
    }.getOrNull()

    private fun validate(checkpoint: ReportSearchCheckpoint) {
        val request = checkpoint.request
        val filters = request.filters
        require(filters.normalizedText.length <= MAX_TEXT)
        val normalized = parseReportSearchFilters(text = filters.normalizedText, zoneId = ZoneOffset.UTC)
            as? ReportSearchFilterValidation.Valid
        require(normalized?.filters?.normalizedText == filters.normalizedText)
        require(filters.startInclusive == null || filters.endExclusive == null ||
            filters.startInclusive <= filters.endExclusive)
        require(checkpoint.history.size <= MAX_REPORT_CURSOR_HISTORY)
        validateStart(ReportPageStart(request.before, request.pageNumber))
        checkpoint.history.forEach(::validateStart)
        checkpoint.history.zipWithNext().forEach { (first, second) ->
            require(second.pageNumber.toLong() == first.pageNumber.toLong() + 1)
            require(second.before != null && (first.before == null || isBefore(second.before, first.before)))
        }
        if (request.navigation == ReportPageNavigation.SEARCH) {
            require(checkpoint.history.isEmpty() && checkpoint.historyIndex == 0 && request.historyIndex == 0)
            require(request.pageNumber == 1 && request.before == null)
            return
        }
        require(checkpoint.historyIndex in checkpoint.history.indices)
        val current = checkpoint.history[checkpoint.historyIndex]
        val target = ReportPageStart(request.before, request.pageNumber)
        when (request.navigation) {
            ReportPageNavigation.NEXT -> {
                require(request.historyIndex == 0)
                require(request.pageNumber.toLong() == current.pageNumber.toLong() + 1)
                require(request.before != null && (current.before == null || isBefore(request.before, current.before)))
            }
            ReportPageNavigation.PREVIOUS -> {
                require(checkpoint.historyIndex > 0 && request.historyIndex == checkpoint.historyIndex - 1)
                require(target == checkpoint.history[request.historyIndex])
            }
            ReportPageNavigation.RELOAD -> {
                require(request.historyIndex == checkpoint.historyIndex && target == current)
            }
            ReportPageNavigation.SEARCH -> error("Search already checked")
        }
    }

    private fun validateStart(start: ReportPageStart) {
        require(start.pageNumber >= 1)
        require((start.pageNumber == 1) == (start.before == null))
    }

    private fun isBefore(cursor: ReportListCursor, previous: ReportListCursor): Boolean =
        cursor.createdAt < previous.createdAt ||
            (cursor.createdAt == previous.createdAt && cursor.id < previous.id)

    private fun ArrayList<String>.addInstant(instant: Instant?) {
        add(instant?.epochSecond?.toString().orEmpty())
        add(instant?.nano?.toString().orEmpty())
    }

    private fun ArrayList<String>.addCursor(cursor: ReportListCursor?) {
        addInstant(cursor?.createdAt)
        add(cursor?.id.orEmpty())
    }

    private class Reader(private val fields: List<String>) {
        private var index = 0
        fun next(): String = fields[index++]
        fun int(): Int = next().toInt()
        fun <T : Enum<T>> enumOrNull(entries: List<T>): T? {
            val name = next()
            if (name.isEmpty()) return null
            return requireNotNull(entries.firstOrNull { it.name == name })
        }
        fun instant(): Instant? {
            val seconds = next()
            val nanos = next()
            if (seconds.isEmpty() && nanos.isEmpty()) return null
            require(seconds.isNotEmpty() && nanos.isNotEmpty())
            val parsedNanos = nanos.toInt()
            require(parsedNanos in 0..999_999_999)
            return Instant.ofEpochSecond(seconds.toLong(), parsedNanos.toLong())
        }
        fun cursor(): ReportListCursor? {
            val instant = instant()
            val id = next()
            if (instant == null && id.isEmpty()) return null
            require(instant != null && id.isNotBlank() && id.length <= 64)
            return ReportListCursor(instant, id)
        }
    }
}
