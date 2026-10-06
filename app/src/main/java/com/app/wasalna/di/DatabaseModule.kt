package com.app.wasalna.di

import android.content.Context
import androidx.room.Room
import com.app.wasalna.data.local.AppDatabase
import com.app.wasalna.data.local.dao.LocationDao
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
    ).build()

    @Provides
    fun provideLocationDao(database: AppDatabase): LocationDao =
        database.locationDao()
}
