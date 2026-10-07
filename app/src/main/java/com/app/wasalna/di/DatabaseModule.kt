package com.app.wasalna.di

import android.content.Context
import androidx.room.Room
import com.app.wasalna.data.local.AppDatabase
import com.app.wasalna.data.local.dao.LocationDao
import com.app.wasalna.data.local.dao.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "wasalna_db",
    )
        // Phase 10.4.2.1: v1→v2 adds trips; destructive is safe pre-release.
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideLocationDao(database: AppDatabase): LocationDao =
        database.locationDao()

    @Provides
    fun provideTripDao(database: AppDatabase): TripDao =
        database.tripDao()
}
