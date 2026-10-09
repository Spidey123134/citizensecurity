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

// Tema del primer borrador conservado en EntregaApp/appmovile/ui/theme/Theme.kt.
private val Light = lightColorScheme(
    primary = Color(0xFF173F5F), onPrimary = Color.White,
    secondary = Color(0xFF007F78), background = Color(0xFFF5F7FA),
    secondaryContainer = Color(0xFFCBEAE5), onSecondaryContainer = Color(0xFF003E39),
    surface = Color.White, surfaceContainer = Color(0xFFEAF0F5),
    primaryContainer = Color(0xFFD6E7F5), onPrimaryContainer = Color(0xFF102F48),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFB5D5EE), secondary = Color(0xFF7BD7CC),
    secondaryContainer = Color(0xFF174A44), onSecondaryContainer = Color(0xFFCBEAE5),
    background = Color(0xFF101A22), surface = Color(0xFF15242F),
    primaryContainer = Color(0xFF25475F), onPrimaryContainer = Color(0xFFD6E7F5),
)

@Composable
fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp)),
        content = content,
    )
}
