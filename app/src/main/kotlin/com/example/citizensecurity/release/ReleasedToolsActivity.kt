package com.example.citizensecurity.release

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.citizensecurity.R
import com.example.citizensecurity.data.TemporaryReportSaveExample
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.NewReport
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.domain.ReportField
import com.example.citizensecurity.domain.ReportLocation
import com.example.citizensecurity.domain.ReportValidator
import java.time.Instant
import kotlinx.coroutines.launch

class ReleasedToolsActivity : ComponentActivity() {
    private lateinit var result: TextView
    private lateinit var type: Spinner
    private lateinit var priority: Spinner
    private val exampleModel: SaveExampleViewModel by lazy {
        val example = TemporaryReportSaveExample(applicationContext)
        ViewModelProvider(this, SaveExampleViewModel.factory(example::run))[SaveExampleViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_released_tools)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tools_container)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        result = findViewById(R.id.tools_result)
        type = findViewById(R.id.tools_type)
        priority = findViewById(R.id.tools_priority)
        type.adapter = ArrayAdapter.createFromResource(this, R.array.tools_types, android.R.layout.simple_spinner_dropdown_item)
        priority.adapter = ArrayAdapter.createFromResource(this, R.array.tools_priorities, android.R.layout.simple_spinner_dropdown_item)
        if (savedInstanceState == null) {
            type.setSelection(IncidentType.RISK.ordinal)
            priority.setSelection(Priority.MEDIUM.ordinal)
            findViewById<EditText>(R.id.tools_date).setText(Instant.now().minusSeconds(30).toString())
        }
        findViewById<Button>(R.id.tools_receive).setOnClickListener { receive(validate = false) }
        findViewById<Button>(R.id.tools_validate).setOnClickListener { receive(validate = true) }
        findViewById<Button>(R.id.tools_locations).setOnClickListener { demonstrateLocations() }
        findViewById<Button>(R.id.tools_save_example).setOnClickListener {
            exampleModel.runExample()
            showExample(exampleModel.state.value)
        }
        findViewById<Button>(R.id.tools_back).setOnClickListener { finish() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                exampleModel.state.collect(::showExample)
            }
        }
    }

    private fun showExample(state: SaveExampleState) {
        findViewById<Button>(R.id.tools_save_example).isEnabled = state != SaveExampleState.Running
        findViewById<TextView>(R.id.tools_example_result).text = when (state) {
            SaveExampleState.Idle -> getString(R.string.tools_example_ready)
            SaveExampleState.Running -> getString(R.string.tools_example_running)
            SaveExampleState.Failed -> getString(R.string.tools_example_failed)
            is SaveExampleState.Saved -> {
                val report = state.report
                getString(
                    R.string.tools_example_saved,
                    report.id,
                    format(NewReport(report.type, report.priority, report.description, report.occurredAt, report.location)),
                )
            }
        }
    }

    private fun receive(validate: Boolean) {
        fun text(id: Int) = findViewById<EditText>(id).text.toString()
        val received = Version02Tools.receive(
            ReportInput(
                IncidentType.entries[type.selectedItemPosition],
                Priority.entries[priority.selectedItemPosition],
                text(R.id.tools_description),
                text(R.id.tools_date),
                text(R.id.tools_reference),
                text(R.id.tools_latitude),
                text(R.id.tools_longitude),
            ),
        )
        when (received) {
            is ReportInputResult.Invalid -> result.text = formatErrors(received.errors)
            is ReportInputResult.Received -> {
                val draft = received.draft
                if (validate) {
                    val errors = ReportValidator().validate(draft, Instant.now())
                    result.text = if (errors.isValid) getString(
                        R.string.tools_result_summary, getString(R.string.tools_valid), format(draft),
                    )
                    else formatErrors(errors.errors)
                } else {
                    result.text = getString(
                        R.string.tools_result_summary, getString(R.string.tools_received), format(draft),
                    )
                }
            }
        }
    }

    private fun demonstrateLocations() {
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
        result.text = cases.joinToString("\n\n") { (label, location) ->
            val errors = ReportValidator().validate(draft.copy(location = location), now)
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
