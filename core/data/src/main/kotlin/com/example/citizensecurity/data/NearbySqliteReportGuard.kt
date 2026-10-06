package com.example.citizensecurity.data

import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.InvalidReportException
import com.example.citizensecurity.domain.NearbyIncidentDecision
import com.example.citizensecurity.domain.NearbyIncidentPolicy
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ValidationErrors

/** Se evalúa bajo el monitor del repositorio, antes de comenzar y de confirmar la transacción. */
class NearbySqliteReportGuard(
    private val deviceLocation: () -> DeviceLocationFix?,
    private val elapsedRealtimeNanos: () -> Long,
    private val policy: NearbyIncidentPolicy = NearbyIncidentPolicy(),
) {
    fun check(draft: NewReport) {
        val decision = policy.evaluate(draft.location, deviceLocation(), elapsedRealtimeNanos())
        if (decision is NearbyIncidentDecision.Rejected) {
            throw InvalidReportException(
                ValidationErrors(mapOf(ReportField.LOCATION_COORDINATES to decision.message)),
            )
        }
    }
}
