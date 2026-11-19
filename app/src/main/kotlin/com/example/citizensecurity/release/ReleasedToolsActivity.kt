package com.example.citizensecurity.release

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.citizensecurity.R
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportValidator
import com.example.citizensecurity.screen.CitizenSecurityTheme
import com.example.citizensecurity.screen.ReleasedToolsScreen
import java.time.Instant

class ReleasedToolsActivity : ComponentActivity() {
    private val validator = ReportValidator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CitizenSecurityTheme {
                ReleasedToolsScreen(
                    onReceive = ::receive,
                    onTestLocations = ::demonstrateLocations,
                    onBack = ::finish,
                )
            }
        }
    }

    private fun receive(input: ReportInput, validate: Boolean): String {
        val draft = when (val received = Version02Tools.receive(input)) {
            is ReportInputResult.Invalid -> return formatErrors(received.errors)
            is ReportInputResult.Received -> received.draft
        }
        if (validate) {
            val validation = validator.validate(draft, Instant.now())
            if (!validation.isValid) {
                return formatErrors(validation.errors)
            }
        }
        val summary = if (validate) R.string.tools_valid else R.string.tools_received
        return getString(R.string.tools_result_summary, getString(summary), format(draft))
    }

    private fun demonstrateLocations(): String {
        val now = Instant.now()
        val draft = NewReport(
            IncidentType.RISK,
            Priority.MEDIUM,
            getString(R.string.tools_example_description),
            now.minusSeconds(30),
            ReportLocation(latitude = 19.4326077, longitude = -99.1332088),
        )
        val cases = listOf(
            R.string.tools_location_valid to draft.location,
            R.string.tools_location_incomplete to ReportLocation(latitude = 19.4326077),
            R.string.tools_location_outside to ReportLocation(latitude = 91.0, longitude = -99.1332088),
        )
        return cases.joinToString("\n\n") { (label, location) ->
            val errors = validator.validate(draft.copy(location = location), now)
            getString(label) + ": " + if (errors.isValid) getString(R.string.tools_approved)
            else errors.errors.values.joinToString(" ")
        }
    }

    private fun formatErrors(errors: Map<ReportField, String>) = errors.entries.joinToString("\n\n") {
        val label = when (it.key) {
            ReportField.DESCRIPTION -> R.string.tools_description_label
            ReportField.OCCURRED_AT -> R.string.tools_date_label
            ReportField.LOCATION_REFERENCE -> R.string.tools_reference_label
            ReportField.LOCATION_COORDINATES -> R.string.tools_coordinates_label
        }
        getString(label) + ": " + it.value
    }

    private fun format(draft: NewReport) = getString(
        R.string.tools_report,
        resources.getStringArray(R.array.tools_types)[draft.type.ordinal],
        resources.getStringArray(R.array.tools_priorities)[draft.priority.ordinal],
        draft.occurredAt.toString(),
        draft.description,
        draft.location.reference,
        draft.location.latitude?.toString().orEmpty(),
        draft.location.longitude?.toString().orEmpty(),
    )
}
