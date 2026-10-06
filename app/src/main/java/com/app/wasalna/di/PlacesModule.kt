package com.app.wasalna.di

import android.content.Context
import android.util.Log
import com.app.wasalna.BuildConfig
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Phase 4: provides the Places API client.
 * Never crashes the Hilt graph: with no real key we still initialize with the
 * placeholder so createClient() succeeds; Places calls then fail gracefully
 * at request time and the ViewModel surfaces empty results.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlacesModule {

    @Provides
    @Singleton
    fun providePlacesClient(
        @ApplicationContext context: Context,
    ): PlacesClient {
        try {
            if (!Places.isInitialized()) {
                val key = BuildConfig.MAPS_API_KEY.trim()
                if (key.isNotEmpty() && key != "YOUR_API_KEY_HERE") {
                    Places.initialize(context.applicationContext, key)
                    Log.i(TAG, "Places initialized with real key")
                } else {
                    Places.initialize(context.applicationContext, "YOUR_API_KEY_HERE")
                    Log.w(TAG, "No real MAPS_API_KEY — Places in degraded mode")
                }
            }
            return Places.createClient(context.applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Places client creation failed: ${e.message}")
            throw e
        }
    }

    private const val TAG = "PlacesModule"
}
