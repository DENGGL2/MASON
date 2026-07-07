package com.denggl2.mason.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MasonColorScheme = darkColorScheme(
    primary = MasonAccent,
    surface = MasonDarkSurface,
    background = MasonDarkBackground,
    onSurface = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color.White,
)

@Composable
fun MasonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MasonColorScheme,
        content = content,
    )
}
