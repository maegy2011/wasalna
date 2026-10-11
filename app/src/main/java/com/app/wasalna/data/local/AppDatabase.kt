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
    // Oct 2026: v4 adds trips.isSimulated (sim quarantine, default 0).
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao

    abstract fun tripDao(): TripDao

    abstract fun tripRouteDao(): TripRouteDao

    companion object {
        /** Oct 2026: v3 → v4 adds the sim-quarantine flag (existing rows = real). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `trips` ADD COLUMN `isSimulated` " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

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
