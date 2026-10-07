package com.app.wasalna.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.wasalna.data.local.dao.LocationDao
import com.app.wasalna.data.local.dao.TripDao
import com.app.wasalna.data.local.dao.TripRouteDao
import com.app.wasalna.data.local.entity.LocationEntity
import com.app.wasalna.data.local.entity.TripHistoryEntity
import com.app.wasalna.data.local.entity.TripRouteEntity

@Database(
    entities = [
        LocationEntity::class,
        TripHistoryEntity::class,
        TripRouteEntity::class,
    ],
    // Phase 10.4.3.7 (Part 2): v3 adds the trip_route breadcrumbs table.
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao

    abstract fun tripDao(): TripDao

    abstract fun tripRouteDao(): TripRouteDao

    companion object {
        /** Phase 10.4.3.7 (Part 2): v2 → v3 creates breadcrumbs (no data loss). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trip_route` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tripId` INTEGER NOT NULL, " +
                        "`lat` REAL NOT NULL, " +
                        "`lng` REAL NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_trip_route_tripId` " +
                        "ON `trip_route` (`tripId`)",
                )
            }
        }
    }
}
