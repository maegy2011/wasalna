package com.app.wasalna.di

import android.content.Context
import androidx.room.Room
import com.app.wasalna.data.local.AppDatabase
import com.app.wasalna.data.local.dao.LocationDao
import com.app.wasalna.data.local.dao.TripDao
import com.app.wasalna.data.local.dao.TripRouteDao
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
        // Phase 10.4.3.7 (Part 2): real v2→v3 migration; destructive stays
        // only as a last-resort safety net.
        .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
        .fallbackToDestructiveMigration()
        .build()

    @Provides
    fun provideLocationDao(database: AppDatabase): LocationDao =
        database.locationDao()

    @Provides
    fun provideTripDao(database: AppDatabase): TripDao =
        database.tripDao()

    @Provides
    fun provideTripRouteDao(database: AppDatabase): TripRouteDao =
        database.tripRouteDao()
}
