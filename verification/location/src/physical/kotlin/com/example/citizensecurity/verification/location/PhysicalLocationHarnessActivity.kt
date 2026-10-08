package com.example.citizensecurity.verification.location

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.data.AndroidPhoneLocationSource
import com.example.citizensecurity.data.PhoneLocationEvidence
import com.example.citizensecurity.data.PhoneLocationRefresh
import com.example.citizensecurity.data.PhoneLocationRefreshResult
import com.example.citizensecurity.domain.NearbyIncidentDecision
import com.example.citizensecurity.domain.NearbyIncidentPolicy
import com.example.citizensecurity.domain.NearbyIncidentRejection
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.maps.LocationPermissionViewModel
import com.example.citizensecurity.maps.PreciseLocationPermissionBinding
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Paquete técnico separado: GPS real por gesto, sin mapa, reportes o coordenadas persistidas. */
class PhysicalLocationHarnessActivity : ComponentActivity() {
    private val fixture by lazy {
        ViewModelProvider(this, viewModelFactory {
            initializer { PhysicalLocationDependencies(applicationContext) }
        })[PhysicalLocationDependencies::class.java]
    }
    private val permissions by lazy {
        ViewModelProvider(this, LocationPermissionViewModel.factory())[LocationPermissionViewModel::class.java]
    }
    private lateinit var permissionBinding: PreciseLocationPermissionBinding
    private lateinit var status: TextView
    private var capture: Job? = null
    private var attemptId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionBinding = PreciseLocationPermissionBinding.forActivity(this, permissions)
        status = TextView(this).apply { setText(R.string.physical_idle) }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding * 2, padding, padding)
            addView(TextView(context).apply {
                setText(R.string.physical_description)
            })
            addView(Button(context).apply {
                setText(R.string.physical_permission)
                setOnClickListener { permissionBinding.request(explanationAccepted = true) }
            })
            addView(Button(context).apply {
                setText(R.string.physical_capture)
                setOnClickListener { checkPhysicalLocation() }
            })
            addView(status)
        }
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        permissionBinding.check()
    }

    private fun checkPhysicalLocation() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || capture?.isActive == true) return
        status.setText(R.string.physical_waiting)
        val currentAttempt = UUID.randomUUID().toString()
        attemptId = currentAttempt
        writeEvidence(summary(currentAttempt, "PENDING"))
        capture = lifecycleScope.launch {
            val result = fixture.refresh.refresh()
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
            val summary = summary(currentAttempt, "BLOCKED")
            if (result is PhoneLocationRefreshResult.Ready) {
                val fix = fixture.evidence.currentFix()
                val now = SystemClock.elapsedRealtimeNanos()
                val nearby = fix?.let {
                    fixture.policy.evaluate(ReportLocation("Comprobación en memoria", it.latitude, it.longitude), it, now)
                }
                val distant = fix?.let {
                    val latitude = if (it.latitude <= 85.0) it.latitude + 0.1 else it.latitude - 0.1
                    fixture.policy.evaluate(ReportLocation("Comprobación distante", latitude, it.longitude), it, now)
                }
                val nearbyAllowed = nearby is NearbyIncidentDecision.Allowed
                val distantBlocked = distant is NearbyIncidentDecision.Rejected &&
                    distant.reason == NearbyIncidentRejection.OUTSIDE_ALLOWED_RADIUS
                val accepted = fix != null && !fix.isMock && nearbyAllowed && distantBlocked
                summary.put("result", if (accepted) "VALIDATED" else "BLOCKED")
                summary.put("nearbyAllowed", nearbyAllowed)
                summary.put("distantBlocked", distantBlocked)
                summary.put("notMock", fix?.isMock == false)
                fix?.let {
                    summary.put("accuracyMeters", it.accuracyMeters)
                    summary.put("ageMillis", (now - it.elapsedRealtimeNanos) / 1_000_000L)
                }
                status.setText(if (accepted) R.string.physical_validated else R.string.physical_invalid)
            } else {
                val reason = when (result) {
                    PhoneLocationRefreshResult.PermissionRequired -> getString(R.string.physical_permission_required)
                    PhoneLocationRefreshResult.LocationDisabled -> getString(R.string.physical_disabled)
                    PhoneLocationRefreshResult.TimedOut -> getString(R.string.physical_timeout)
                    is PhoneLocationRefreshResult.Rejected -> result.rejection.message
                    PhoneLocationRefreshResult.AlreadyRunning -> getString(R.string.physical_pending)
                    else -> getString(R.string.physical_unavailable)
                }
                summary.put("result", "BLOCKED")
                summary.put("reason", reason)
                status.text = reason
            }
            // El resumen conserva únicamente métricas y decisiones, nunca latitud/longitud.
            writeEvidence(summary)
        }
    }

    private fun summary(id: String, result: String) = JSONObject().apply {
        put("attemptId", id)
        put("recordedElapsedMillis", SystemClock.elapsedRealtime())
        put("physicalDeviceTest", true)
        put("coordinatesPersisted", false)
        put("reportsCreated", 0)
        put("result", result)
        put("timeoutMillis", fixture.refresh.timeoutMillis)
    }

    private fun writeEvidence(summary: JSONObject) {
        File(filesDir, "physical-evidence.json").writeText(summary.toString(2))
    }

    override fun onStop() {
        if (capture?.isActive == true) attemptId?.let { writeEvidence(summary(it, "CANCELLED")) }
        capture?.cancel()
        capture = null
        fixture.evidence.clear()
        super.onStop()
    }

    override fun onDestroy() {
        permissionBinding.close()
        super.onDestroy()
    }
}

private class PhysicalLocationDependencies(context: Context) : ViewModel() {
    private val source = AndroidPhoneLocationSource(context.applicationContext)
    val evidence = PhoneLocationEvidence(source::hasPrecisePermission, source::isLocationEnabled)
    val policy = NearbyIncidentPolicy()
    val refresh = PhoneLocationRefresh(source, evidence, SystemClock::elapsedRealtimeNanos, policy)
    override fun onCleared() { evidence.clear() }
}
