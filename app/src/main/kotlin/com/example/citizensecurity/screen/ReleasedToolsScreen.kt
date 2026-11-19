package com.example.citizensecurity.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.citizensecurity.R
import com.example.citizensecurity.domain.IncidentType
import com.example.citizensecurity.domain.Priority
import com.example.citizensecurity.release.ReportInput
import java.time.Instant

@Composable
internal fun ReleasedToolsScreen(
    onReceive: (ReportInput, Boolean) -> String,
    onTestLocations: () -> String,
    onBack: () -> Unit,
) {
    val exampleDescription = stringResource(R.string.tools_example_description)
    val exampleReference = stringResource(R.string.tools_example_reference)
    var type by rememberSaveable { mutableStateOf(IncidentType.RISK) }
    var priority by rememberSaveable { mutableStateOf(Priority.MEDIUM) }
    var description by rememberSaveable { mutableStateOf(exampleDescription) }
    var date by rememberSaveable { mutableStateOf(Instant.now().minusSeconds(30).toString()) }
    var reference by rememberSaveable { mutableStateOf(exampleReference) }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    // El teclado permite el signo menos, el punto decimal y las letras T/Z de las fechas UTC.
    val asciiKeyboard = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false)

    fun receive(validate: Boolean) {
        focus.clearFocus()
        val input = ReportInput(type, priority, description, date, reference, latitude, longitude)
        result = onReceive(input, validate)
    }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.tools_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(stringResource(R.string.tools_explanation))
                OptionPicker(
                    label = stringResource(R.string.tools_type_label),
                    options = stringArrayResource(R.array.tools_types),
                    selectedIndex = type.ordinal,
                    onSelect = { type = IncidentType.entries[it] },
                )
                OptionPicker(
                    label = stringResource(R.string.tools_priority_label),
                    options = stringArrayResource(R.array.tools_priorities),
                    selectedIndex = priority.ordinal,
                    onSelect = { priority = Priority.entries[it] },
                )
                ReportField(description, { description = it }, R.string.tools_description_label, singleLine = false)
                ReportField(date, { date = it }, R.string.tools_date_label, keyboardOptions = asciiKeyboard)
                ReportField(reference, { reference = it }, R.string.tools_reference_label)
                ReportField(latitude, { latitude = it }, R.string.tools_latitude_label, keyboardOptions = asciiKeyboard)
                ReportField(longitude, { longitude = it }, R.string.tools_longitude_label, keyboardOptions = asciiKeyboard)
                OutlinedButton(onClick = { receive(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tools_receive))
                }
                Button(onClick = { receive(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tools_validate))
                }
                OutlinedButton(
                    onClick = {
                        focus.clearFocus()
                        result = onTestLocations()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.tools_locations)) }
                Card(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                    SelectionContainer {
                        Text(
                            text = result ?: stringResource(R.string.tools_ready),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tools_back))
                }
            }
        }
    }
}

@Composable
private fun OptionPicker(label: String, options: Array<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(options[selectedIndex])
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(index)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
    )
}
