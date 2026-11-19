package com.example.citizensecurity.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.citizensecurity.screen.Home
import com.example.citizensecurity.screen.LocationExample
import com.example.citizensecurity.screen.ReportDetails

@Composable
internal fun Navigation() {
    var selectedExample by rememberSaveable { mutableStateOf<LocationExample?>(null) }
    val example = selectedExample

    BackHandler(enabled = example != null) { selectedExample = null }

    Scaffold { insets ->
        val screen = Modifier.fillMaxSize().padding(insets)
        if (example == null) {
            Home(onExampleSelected = { selectedExample = it }, modifier = screen)
        } else {
            ReportDetails(example = example, onBack = { selectedExample = null }, modifier = screen)
        }
    }
}
