# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class com.app.wasalna.** { *; }

# Hilt standard keeps
-keepclasseswithmembernames class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep class androidx.hilt.** { *; }
-dontwarn dagger.hilt.**

# Room standard keeps
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.**

# Play Billing (Phase 8 monetization)
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# Maps / Places / Ads
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.android.libraries.places.** { *; }
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.**
# R8 full-mode: Places shades gRPC — missing provider is reflective-only
-dontwarn io.grpc.**
-dontwarn com.google.common.**
