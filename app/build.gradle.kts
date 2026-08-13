plugins {
    id("android-application")
    id("android-compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

val masonArm64Only = providers.gradleProperty("masonArm64Only")
    .map(String::toBoolean)
    .getOrElse(false)

android {
    namespace = "com.denggl2.mason"
    defaultConfig {
        applicationId = "com.denggl2.mason"
        versionCode = 9
        versionName = "0.2.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (masonArm64Only) {
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // llama.cpp discovers its CPU variants by scanning nativeLibraryDir at runtime.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.pdfbox.android)
    implementation(libs.haze)
    runtimeOnly(libs.litertlm.android)

    implementation(project(":llm-client"))
    implementation(project(":protocol"))
    implementation(project(":tool-runtime"))
    implementation(project(":sync"))
    implementation(project(":crash-guard"))
    implementation(project(":llama-runtime"))

    debugImplementation(libs.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
