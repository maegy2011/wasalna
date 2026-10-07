package com.app.wasalna.di

import com.app.wasalna.data.repository.LocationRepositoryImpl
import com.app.wasalna.data.repository.SettingsRepositoryImpl
import com.app.wasalna.data.repository.TripRepositoryImpl
import com.app.wasalna.domain.repository.LocationRepository
import com.app.wasalna.domain.repository.SettingsRepository
import com.app.wasalna.domain.repository.TripRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl,
    ): LocationRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl,
    ): SettingsRepository

    // Phase 10.4.2.1: trip-history repository binding.
    @Binds
    @Singleton
    abstract fun bindTripRepository(
        impl: TripRepositoryImpl,
    ): TripRepository
}
