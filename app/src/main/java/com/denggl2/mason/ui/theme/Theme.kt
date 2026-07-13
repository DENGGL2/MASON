package com.denggl2.mason.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.denggl2.mason.data.ThemeMode
import kotlin.math.pow

private fun contentColorFor(background: Color): Color {
    fun linear(channel: Float): Double {
        return if (channel <= 0.03928f) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    val luminance = 0.2126 * linear(background.red) +
        0.7152 * linear(background.green) +
        0.0722 * linear(background.blue)
    return if (luminance > 0.54) Color(0xFF071015) else Color.White
}

private fun interactiveAccent(accentColor: Color): Color {
    val brightness = (accentColor.red + accentColor.green + accentColor.blue) / 3f
    val spread = maxOf(accentColor.red, accentColor.green, accentColor.blue) -
        minOf(accentColor.red, accentColor.green, accentColor.blue)
    return if (brightness < 0.18f || brightness > 0.92f || spread < 0.08f) {
        Color(0xFF4FC3F7)
    } else {
        accentColor
    }
}

private fun darkMasonColorScheme(accentColor: Color) = darkColorScheme(
    primary = interactiveAccent(accentColor),
    onPrimary = contentColorFor(interactiveAccent(accentColor)),
    secondary = Color(0xFF9AD7D0),
    background = Color(0xFF111419),
    onBackground = Color(0xFFECEFF3),
    surface = Color(0xFF191D23),
    onSurface = Color(0xFFEDEFF2),
    surfaceVariant = Color(0xFF242A31),
    onSurfaceVariant = Color(0xFFB3BBC6),
    outline = Color(0xFF444C57),
    error = Color(0xFFFF8A80),
)

private fun lightMasonColorScheme(accentColor: Color) = lightColorScheme(
    primary = interactiveAccent(accentColor),
    onPrimary = contentColorFor(interactiveAccent(accentColor)),
    secondary = Color(0xFF296D91),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF171A1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A1F),
    surfaceVariant = Color(0xFFE9EDF2),
    onSurfaceVariant = Color(0xFF5F6670),
    outline = Color(0xFFD0D6DE),
    error = Color(0xFFD32F2F),
)

@Composable
fun MasonTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: Color = MasonAccent,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) {
            darkMasonColorScheme(accentColor)
        } else {
            lightMasonColorScheme(accentColor)
        },
        content = content,
    )
}
