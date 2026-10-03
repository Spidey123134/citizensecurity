package com.example.citizensecurity.domain

import java.time.Instant

enum class IncidentType {
    EMERGENCY,
    THEFT,
    ACCIDENT,
    FIRE,
    RISK,
    OTHER,
}

enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
}

enum class ReportStatus {
    REPORTED,
    IN_REVIEW,
    ATTENDED,
    CLOSED,
}

data class ReportLocation(
    val reference: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class NewReport(
    val type: IncidentType,
    val priority: Priority,
    val description: String,
    val occurredAt: Instant,
    val location: ReportLocation,
)

data class Report(
    val id: String,
    val type: IncidentType,
    val priority: Priority,
    val description: String,
    val occurredAt: Instant,
    val location: ReportLocation,
    val createdAt: Instant,
    val status: ReportStatus = ReportStatus.REPORTED,
)
