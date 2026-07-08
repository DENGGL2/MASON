plugins {
    id("android-library")
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.denggl2.mason.tool"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    exclude(
        "**/AlarmTool.kt",
        "**/AppLauncherTool.kt",
        "**/AppManagerTool.kt",
        "**/AudioRecordTool.kt",
        "**/BatteryOptimizationTool.kt",
        "**/BatteryTool.kt",
        "**/BluetoothTool.kt",
        "**/CalendarTool.kt",
        "**/CallLogTool.kt",
        "**/CameraTool.kt",
        "**/ClipboardTool.kt",
        "**/ContactsTool.kt",
        "**/CpuTool.kt",
        "**/DeviceInfoTool.kt",
        "**/DnsLookupTool.kt",
        "**/FileDeleteTool.kt",
        "**/FileListTool.kt",
        "**/FileReadTool.kt",
        "**/FileWriteTool.kt",
        "**/GeocodingTool.kt",
        "**/GpuTool.kt",
        "**/HotspotTool.kt",
        "**/HttpRequestTool.kt",
        "**/LocationTool.kt",
        "**/MemoryTool.kt",
        "**/NetworkInfoTool.kt",
        "**/NotificationTool.kt",
        "**/ProcessTool.kt",
        "**/ScreenshotTool.kt",
        "**/SensorTool.kt",
        "**/ShellTool.kt",
        "**/SmsTool.kt",
        "**/StorageTool.kt",
        "**/SystemSettingTool.kt",
        "**/WifiTool.kt",
    )
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
