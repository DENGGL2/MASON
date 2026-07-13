package com.denggl2.mason.data

import androidx.compose.ui.graphics.Color

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class IslandVendorMode {
    AUTO,
    XIAOMI,
    VIVO,
    OPPO,
}

enum class NotificationDeliveryMode {
    REGULAR,
    ISLAND,
}

data class UiPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: Long = DEFAULT_ACCENT_COLOR,
    val notificationIslandEnabled: Boolean = false,
    val notificationDeliveryMode: NotificationDeliveryMode = NotificationDeliveryMode.REGULAR,
    val notifyOnTaskComplete: Boolean = true,
    val notifyOnPaymentSuccess: Boolean = true,
    val islandVendorMode: IslandVendorMode = IslandVendorMode.AUTO,
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

const val DEFAULT_ACCENT_COLOR: Long = 0xFF4FC3F7

fun Long.toComposeColor(): Color = Color(this)
