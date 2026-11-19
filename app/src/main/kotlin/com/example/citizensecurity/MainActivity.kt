package com.example.citizensecurity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.example.citizensecurity.navigation.Navigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
            MaterialTheme(colorScheme = colors) {
                Navigation()
            }
        }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF593C91),
    primaryContainer = Color(0xFFEDDDFF),
    onPrimaryContainer = Color(0xFF25104A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD7BFFF),
    primaryContainer = Color(0xFF442A72),
    onPrimaryContainer = Color(0xFFEDDDFF),
)
