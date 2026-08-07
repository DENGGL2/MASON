package com.denggl2.mason.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.denggl2.mason.data.InterfaceStyle
import com.denggl2.mason.data.ThemeMode
import kotlin.math.pow

val LocalInterfaceStyle = staticCompositionLocalOf { InterfaceStyle.ACRYLIC }
val LocalInterfaceEffects = staticCompositionLocalOf {
    resolveInterfaceEffects(
        requestedStyle = InterfaceStyle.ACRYLIC,
        requestedGlassRefraction = false,
        sdkInt = Build.VERSION.SDK_INT,
    )
}

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

private fun interactiveAccent(accentColor: Color, darkTheme: Boolean): Color {
    val brightness = (accentColor.red + accentColor.green + accentColor.blue) / 3f
    val spread = maxOf(accentColor.red, accentColor.green, accentColor.blue) -
        minOf(accentColor.red, accentColor.green, accentColor.blue)
    return if (brightness < 0.18f || brightness > 0.92f || spread < 0.08f) {
        if (darkTheme) Color(0xFFE1DED8) else Color(0xFF20201F)
    } else {
        accentColor
    }
}

private fun darkMasonColorScheme(accentColor: Color) = darkColorScheme(
    primary = interactiveAccent(accentColor, darkTheme = true),
    onPrimary = contentColorFor(interactiveAccent(accentColor, darkTheme = true)),
    secondary = Color(0xFFBAB7B1),
    background = Color(0xFF171716),
    onBackground = Color(0xFFE9E7E1),
    surface = Color(0xFF1E1E1C),
    onSurface = Color(0xFFE9E7E1),
    surfaceVariant = Color(0xFF2A2926),
    onSurfaceVariant = Color(0xFFC8C5BE),
    outline = Color(0xFF4A4944),
    error = Color(0xFFFF8A80),
)

private fun lightMasonColorScheme(accentColor: Color) = lightColorScheme(
    primary = interactiveAccent(accentColor, darkTheme = false),
    onPrimary = contentColorFor(interactiveAccent(accentColor, darkTheme = false)),
    secondary = Color(0xFF5B5A56),
    background = Color.White,
    onBackground = Color(0xFF20201F),
    surface = Color.White,
    onSurface = Color(0xFF20201F),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF6B6D72),
    outline = Color(0xFFDADCE0),
    error = Color(0xFFD32F2F),
)

@Composable
fun MasonTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentColor: Color = MasonAccent,
    interfaceStyle: InterfaceStyle = InterfaceStyle.ACRYLIC,
    glassRefractionEnabled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val interfaceEffects = resolveInterfaceEffects(
        requestedStyle = interfaceStyle,
        requestedGlassRefraction = glassRefractionEnabled,
        sdkInt = Build.VERSION.SDK_INT,
    )
    CompositionLocalProvider(
        LocalInterfaceStyle provides interfaceStyle,
        LocalInterfaceEffects provides interfaceEffects,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) {
                darkMasonColorScheme(accentColor)
            } else {
                lightMasonColorScheme(accentColor)
            },
            content = content,
        )
    }
}
