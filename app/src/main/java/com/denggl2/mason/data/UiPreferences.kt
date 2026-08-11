package com.denggl2.mason.data

import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class InterfaceStyle {
    ACRYLIC,
    NATIVE,
    GLASS,
    // Retained as a hidden compatibility value for older builds.
    MATERIAL3,
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
    val glassRefractionEnabled: Boolean = false,
    val glassTransparency: Float = DEFAULT_GLASS_TRANSPARENCY,
    val glassFrost: Float = DEFAULT_GLASS_FROST,
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
const val DEFAULT_GLASS_TRANSPARENCY: Float = 0.58f
const val DEFAULT_GLASS_FROST: Float = 0f

internal fun normalizeGlassTransparency(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else DEFAULT_GLASS_TRANSPARENCY

internal fun normalizeGlassFrost(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else DEFAULT_GLASS_FROST

fun Long.toComposeColor(): Color = Color(this)
