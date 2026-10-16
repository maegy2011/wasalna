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

    defaultConfig {
        applicationId = "com.app.wasalna"
        minSdk = 26
        targetSdk = 36
        // Versioning pattern (Wasalna): versionName = "<phase>" (e.g. 9.3.1
        // for Phase 9-3-1-fix), versionCode +1 on EVERY published APK build.
        // Phase 10.4.2.1 (trips history): same phase name, next build number.
        versionCode = 44
        versionName = "10.29.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
        release {
            isDebuggable = false
            // Phase 10.4.3.6-hotfix: R8 stripped 20MB incl. runtime classes
            // (app crashed on launch) — minify OFF until rules are proven.
            isMinifyEnabled = false
            isShrinkResources = false
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
    testOptions {
        // Plain-JVM unit tests: Android framework stubs return defaults
        // instead of throwing (Log.e etc. become no-ops under test).
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Compose BOM manages all Compose versions
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // Screen-density adaptivity (Oct 2026): WindowSizeClass drives
    // adaptive dimens (Compact/Medium/Expanded). Version via Compose BOM.
    implementation(libs.compose.material3.windowsize)
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

    // 100% MapLibre — no Google Maps Compose / Places SDK.
    // Kept: play-services-maps (LatLng value type in MapViewModel/MapScreen),
    // play-services-location (FusedLocation in TrackingService/MapViewModel),
    // coroutines-play-services (await()), ads + billing (actively used).
    // Phase 5: background location engine (FusedLocationProviderClient)
    implementation(libs.play.services.location)
    // Phase 10.10: smartwatch-only alert — phone-side Data Layer sender
    // (MessageClient/CapabilityClient small API, no Wear UI on the phone).
    implementation(libs.play.services.wearable)
    // LatLng value type (MapViewModel/MapScreen) + await() — no Map SDK UI.
    implementation(libs.play.services.maps)
    implementation(libs.coroutines.play.services)
    // Phase 8: Monetization — AdMob + Play Billing
    implementation(libs.play.services.ads)
    implementation(libs.billing.ktx)
    // Phase 10.13-exp: Google account sign-in (EXPERIMENTAL, local-only).
    // Classic GoogleSignInClient — lightest API, no server client-id,
    // no secrets; profile+email stored in DataStore on-device only.
    implementation(libs.play.services.auth)
    // Phase 10-1: Free map engine (MapLibre + OpenFreeMap vector tiles).
    implementation(libs.maplibre.sdk)
    implementation(libs.maplibre.annotation)
    // Phase 9-3-1-fix-1: Nominatim HTTP client (free search, decoupled from Google).
    implementation(libs.okhttp)
    // Phase 10.4.3.9: pure-JVM offline routing (Vela approach, Apache 2.0).
    implementation("com.graphhopper:graphhopper-core:11.1")

    debugImplementation(libs.compose.ui.tooling)

    // Offline-plan test stack (§6): JVM unit first, instrumented nightly.
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.test.runner)
}
