plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:8.5.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.51.1")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.0.20-1.0.24")
}
