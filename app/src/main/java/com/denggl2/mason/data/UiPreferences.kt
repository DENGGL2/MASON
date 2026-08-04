package com.denggl2.mason.data

import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class InterfaceStyle {
    ACRYLIC,
    MATERIAL3,
    LIQUID_GLASS,
}

enum class FontSizePreference(val scale: Float) {
    SMALL(0.9f),
    MEDIUM(1f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
}

data class UiPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val interfaceStyle: InterfaceStyle = InterfaceStyle.ACRYLIC,
    val liquidGlassTransparency: Float = 0.72f,
    val accentColor: Long = DEFAULT_ACCENT_COLOR,
    val regularNotificationsEnabled: Boolean = false,
    val islandNotificationsEnabled: Boolean = false,
    val fontSize: FontSizePreference = FontSizePreference.MEDIUM,
)

data class AccentPreset(
    val name: String,
    val color: Long,
)

val MasonAccentPresets = listOf(
    AccentPreset("科技蓝", 0xFF4FC3F7),
    AccentPreset("电光蓝", 0xFF5B8CFF),
    AccentPreset("薄荷绿", 0xFF00C896),
    AccentPreset("紫罗兰", 0xFF8B5CF6),
    AccentPreset("暖橙", 0xFFFFA726),
    AccentPreset("纯黑", 0xFF111318),
    AccentPreset("纯白", 0xFFFFFFFF),
)

const val DEFAULT_ACCENT_COLOR: Long = 0xFF20201F

fun Long.toComposeColor(): Color = Color(this)
