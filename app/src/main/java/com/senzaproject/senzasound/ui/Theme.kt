package com.senzaproject.senzasound.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF070A14)
val Surface1 = Color(0xFF0F1424)
val Surface2 = Color(0xFF1A2140)
val Accent = Color(0xFF3DD6F5)
val Accent2 = Color(0xFF8B6CFF)
val TextDim = Color(0xFF9AA3C2)
val AccentBrush = Brush.horizontalGradient(listOf(Accent, Accent2))

@Composable
fun SenzaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF00212B),
            secondary = Accent2,
            background = Bg,
            surface = Surface1,
            surfaceVariant = Surface2,
            onBackground = Color(0xFFE8ECFA),
            onSurface = Color(0xFFE8ECFA),
            onSurfaceVariant = TextDim,
            outline = Color(0xFF2A3354),
        ),
        content = content,
    )
}
