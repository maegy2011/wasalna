import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.app.wasalna"
    compileSdk = 36

    // Phase 4: Google Maps API key from local.properties (graceful default empty).
    // local.properties is gitignored and never committed.
    val mapsApiKey: String = run {
        val props = Properties()
        val propFile = rootProject.file("local.properties")
        if (propFile.exists()) {
            propFile.inputStream().use { props.load(it) }
        }
        (props.getProperty("MAPS_API_KEY") ?: System.getenv("MAPS_API_KEY") ?: "").trim()
    }

    defaultConfig {
        applicationId = "com.app.wasalna"
        minSdk = 26
        targetSdk = 36
        // Versioning pattern (Wasalna): versionName = "<phase>" (e.g. 9.3.1
        // for Phase 9-3-1-fix), versionCode +1 on EVERY published APK build.
        versionCode = 7
        versionName = "10.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey.ifEmpty { "YOUR_API_KEY_HERE" }
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
}

dependencies {
    // Compose BOM manages all Compose versions
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.core.ktx)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Phase 4: Google Maps + Places
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.places)
    implementation(libs.coroutines.play.services)
    // Phase 5: background location engine (FusedLocationProviderClient)
    implementation(libs.play.services.location)
    // Phase 8: Monetization — AdMob + Play Billing
    implementation(libs.play.services.ads)
    implementation(libs.billing.ktx)
    // Phase 10-1: Free map engine (MapLibre + OpenFreeMap vector tiles).
    implementation(libs.maplibre.sdk)
    implementation(libs.maplibre.annotation)
    // Phase 9-3-1-fix-1: Nominatim HTTP client (free search, decoupled from Google).
    implementation(libs.okhttp)

    debugImplementation(libs.compose.ui.tooling)
}
