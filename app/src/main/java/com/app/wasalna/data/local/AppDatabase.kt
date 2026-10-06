package com.app.wasalna.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.app.wasalna.data.local.dao.LocationDao
import com.app.wasalna.data.local.entity.LocationEntity

@Database(
    entities = [LocationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao
}
