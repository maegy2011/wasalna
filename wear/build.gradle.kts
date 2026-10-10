// Phase 10.10: Wear OS companion — watch-only destination alert.
// Minimal module: alert screen + vibration + Data Layer listener. No Hilt,
// no database, no network — only MessageClient/CapabilityClient + Compose.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.app.wasalna.wear"
    compileSdk = 36

    defaultConfig {
        // Same applicationId as the phone app (standard companion pairing —
        // the watch APK installs on the watch, the phone APK on the phone).
        applicationId = "com.app.wasalna"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "10.10.0"
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.play.services)
    implementation(libs.play.services.wearable)

    debugImplementation(libs.compose.ui.tooling)
}
