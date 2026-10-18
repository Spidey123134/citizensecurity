package com.example.citizensecurity.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Paleta alineada con el diseño XML (#F4F5FB de fondo, #7C3AED morado principal, #8B5CF6 secundario, tarjetas blancas #FFFFFF)
private val Light = lightColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    secondary = Color(0xFF8B5CF6),
    onSecondary = Color.White,
    background = Color(0xFFF4F5FB),
    surface = Color.White,
    surfaceContainer = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF5B21B6),
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF6B21A8),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF3B0764),
    secondary = Color(0xFFC4B5FD),
    onSecondary = Color(0xFF4C1D95),
    background = Color(0xFF111827),
    surface = Color(0xFF1F2937),
    surfaceContainer = Color(0xFF1F2937),
    primaryContainer = Color(0xFF5B21B6),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondaryContainer = Color(0xFF6B21A8),
    onSecondaryContainer = Color(0xFFF3E8FF),
)

@Composable
fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(22.dp),
        ),
        content = content,
    )
}
