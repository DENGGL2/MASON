package com.denggl2.masonremote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.denggl2.masonremote.ui.settings.RemoteInterfaceStyle
import com.denggl2.masonremote.ui.theme.LocalInterfaceEffects
import com.denggl2.masonremote.ui.theme.resolveInterfaceEffects

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
)

@Composable
fun MasonRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    interfaceStyle: RemoteInterfaceStyle = RemoteInterfaceStyle.NATIVE,
    glassRefractionEnabled: Boolean = true,
    glassTransparency: Float = 0.90f,
    glassFrost: Float = 0.10f,
    content: @Composable () -> Unit,
) {
    val interfaceEffects = resolveInterfaceEffects(
        requestedStyle = interfaceStyle,
        requestedGlassRefraction = glassRefractionEnabled,
        requestedGlassTransparency = glassTransparency,
        requestedGlassFrost = glassFrost,
        sdkInt = android.os.Build.VERSION.SDK_INT,
    )
    androidx.compose.runtime.CompositionLocalProvider(
        LocalInterfaceEffects provides interfaceEffects,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography(),
            content = content,
        )
    }
}

/** Provides Remote material effects to host-owned surfaces such as previews. */
@Composable
fun RemoteInterfaceEffectsProvider(
    interfaceStyle: RemoteInterfaceStyle = RemoteInterfaceStyle.NATIVE,
    glassRefractionEnabled: Boolean = true,
    glassTransparency: Float = 0.90f,
    glassFrost: Float = 0.10f,
    content: @Composable () -> Unit,
) {
    val effects = resolveInterfaceEffects(
        requestedStyle = interfaceStyle,
        requestedGlassRefraction = glassRefractionEnabled,
        requestedGlassTransparency = glassTransparency,
        requestedGlassFrost = glassFrost,
        sdkInt = android.os.Build.VERSION.SDK_INT,
    )
    androidx.compose.runtime.CompositionLocalProvider(
        LocalInterfaceEffects provides effects,
        content = content,
    )
}
