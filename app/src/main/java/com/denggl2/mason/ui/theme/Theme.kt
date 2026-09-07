package com.denggl2.mason.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.denggl2.mason.data.DEFAULT_GLASS_FROST
import com.denggl2.mason.data.DEFAULT_GLASS_TRANSPARENCY
import com.denggl2.mason.data.InterfaceStyle
import com.denggl2.mason.data.ThemeMode

// Keep the host palette identical to the embedded Remote surface.
private val LightColors = lightColorScheme(
    primary = Color(0xFF18181A),
    onPrimary = Color.White,
    background = Color(0xFFF8F8FA),
    onBackground = Color(0xFF18181A),
    surface = Color.White,
    onSurface = Color(0xFF18181A),
    surfaceVariant = Color(0xFFEDEDF0),
    onSurfaceVariant = Color(0xFF6C6C73),
    outline = Color(0xFFD8D8DD),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF5F5F7),
    onPrimary = Color(0xFF18181A),
    background = Color(0xFF111113),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF1B1B1F),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF29292E),
    onSurfaceVariant = Color(0xFFA9A9B1),
    outline = Color(0xFF414147),
    error = Color(0xFFFFB4AB),
)

val LocalInterfaceStyle = staticCompositionLocalOf { InterfaceStyle.NATIVE }
val LocalInterfaceEffects = staticCompositionLocalOf {
    resolveInterfaceEffects(
        requestedStyle = InterfaceStyle.NATIVE,
        requestedGlassRefraction = true,
        requestedGlassTransparency = DEFAULT_GLASS_TRANSPARENCY,
        requestedGlassFrost = DEFAULT_GLASS_FROST,
        sdkInt = Build.VERSION.SDK_INT,
    )
}

@Composable
fun MasonTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Retained for source compatibility. Remote owns the fixed palette.
    @Suppress("UNUSED_PARAMETER") accentColor: Color = MasonAccent,
    interfaceStyle: InterfaceStyle = InterfaceStyle.NATIVE,
    glassRefractionEnabled: Boolean = true,
    glassTransparency: Float = DEFAULT_GLASS_TRANSPARENCY,
    glassFrost: Float = DEFAULT_GLASS_FROST,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val normalizedStyle = when (interfaceStyle) {
        InterfaceStyle.GLASS -> InterfaceStyle.GLASS
        else -> InterfaceStyle.NATIVE
    }
    val interfaceEffects = resolveInterfaceEffects(
        requestedStyle = normalizedStyle,
        requestedGlassRefraction = glassRefractionEnabled,
        requestedGlassTransparency = glassTransparency,
        requestedGlassFrost = glassFrost,
        sdkInt = Build.VERSION.SDK_INT,
    )
    CompositionLocalProvider(
        LocalInterfaceStyle provides normalizedStyle,
        LocalInterfaceEffects provides interfaceEffects,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography(),
            content = content,
        )
    }
}
