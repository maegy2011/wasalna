package com.app.wasalna.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.app.wasalna.data.local.dao.LocationDao
import com.app.wasalna.data.local.dao.TripDao
import com.app.wasalna.data.local.entity.LocationEntity
import com.app.wasalna.data.local.entity.TripHistoryEntity

@Database(
    entities = [LocationEntity::class, TripHistoryEntity::class],
    // Phase 10.4.2.1: v2 adds the trips table (destructive — pre-release).
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao

    abstract fun tripDao(): TripDao
}
