package com.example.citizensecurity.verification.location

import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.citizensecurity.data.PhoneLocationEvidence
import com.example.citizensecurity.data.PhoneLocationRefresh
import com.example.citizensecurity.domain.DeviceLocationFix
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.maps.IncidentLocationViewModel
import com.example.citizensecurity.maps.IncidentLocationFlowBinding
import com.example.citizensecurity.maps.LocationPermissionViewModel
import com.example.citizensecurity.maps.PreciseLocationPermissionBinding
import com.example.citizensecurity.report.ReportDraftViewModel
import com.example.citizensecurity.report.ReportLocationFlowBinding

/** Dueño Android técnico, sin mapa, controles, login, guardado o captura automática. */
class LocationFlowHarnessActivity : ComponentActivity() {
    val fixture: LocationFlowFixtureViewModel by lazy {
        ViewModelProvider(
            this,
            LocationFlowFixtureViewModel.factory(
                applicationContext,
                intent.getLongExtra(EXTRA_TIMEOUT_MILLIS, DEFAULT_LAB_TIMEOUT_MILLIS),
            ),
        ).get(LocationFlowFixtureViewModel::class.java)
    }

    val permissionModel: LocationPermissionViewModel by lazy {
        ViewModelProvider(this, LocationPermissionViewModel.factory())
            .get(LocationPermissionViewModel::class.java)
    }

    val locationModel: IncidentLocationViewModel by lazy {
        ViewModelProvider(
            this,
            IncidentLocationViewModel.factory(
                fixture.original,
                { fixture.refresh.refresh() },
                fixture.evidence::currentFix,
                { fixture.nowNanos },
            ),
        ).get(IncidentLocationViewModel::class.java)
    }

    val draftModel: ReportDraftViewModel by lazy {
        ViewModelProvider(this, ReportDraftViewModel.factory())
            .get(ReportDraftViewModel::class.java)
    }

    lateinit var flowBinding: IncidentLocationFlowBinding
        private set

    lateinit var reportLocationBinding: ReportLocationFlowBinding
        private set

    val permissionBinding: PreciseLocationPermissionBinding
        get() = flowBinding.permissionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Registro incondicional en el mismo orden y antes de STARTED.
        flowBinding = IncidentLocationFlowBinding.forActivity(this, permissionModel, locationModel)
        reportLocationBinding = ReportLocationFlowBinding(this, flowBinding, draftModel)
        setContentView(FrameLayout(this))
    }

    companion object {
        const val EXTRA_TIMEOUT_MILLIS = "lab_location_timeout_millis"
        const val DEFAULT_LAB_TIMEOUT_MILLIS = 2_000L
    }
}

/** Conserva dependencias coherentes al recrear la Activity; no retiene su instancia. */
class LocationFlowFixtureViewModel(
    context: Context,
    timeoutMillis: Long,
) : ViewModel() {
    val original = ReportLocation("  Frente al parque de la prueba  ", 19.4326077, -99.1332088)
    val source = ControllablePhoneLocationSource(context.applicationContext)
    val evidence = PhoneLocationEvidence(source::hasPrecisePermission, source::isLocationEnabled)

    @Volatile
    var nowNanos: Long = 300_000_000_000L

    val refresh = PhoneLocationRefresh(source, evidence, { nowNanos }, timeoutMillis = timeoutMillis)

    /** Lectura sintética del laboratorio: nunca es evidencia de GPS físico. */
    fun syntheticFix(
        accuracyMeters: Double = 20.0,
        elapsedRealtimeNanos: Long = nowNanos,
        isMock: Boolean = false,
    ) = DeviceLocationFix(
        latitude = 19.4326077,
        longitude = -99.1332088,
        accuracyMeters = accuracyMeters,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        isMock = isMock,
    )

    override fun onCleared() {
        evidence.clear()
    }

    companion object {
        fun factory(context: Context, timeoutMillis: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { LocationFlowFixtureViewModel(context.applicationContext, timeoutMillis) }
        }
    }
}
