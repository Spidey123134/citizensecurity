package com.example.citizensecurity.screen

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val CitizenGreen = Color(0xFF207345)

private val lightColors = lightColorScheme(primary = CitizenGreen)
private val darkColors = darkColorScheme(
    primary = Color(0xFF96D6AC),
    onPrimary = Color(0xFF00391F),
)

@Composable
internal fun CitizenSecurityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColors else lightColors,
        content = content,
    )
}
