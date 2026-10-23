package com.personal.appvisualbuilder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFF7AA2FF),
    onPrimary = Color(0xFF0A1633),
    secondary = Color(0xFF9FB4D9),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF161A21),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF1F242D),
    onSurfaceVariant = Color(0xFFB4BAC6),
    error = Color(0xFFFF8A80)
)

/** Dark theme only, by design. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, content = content)
}
