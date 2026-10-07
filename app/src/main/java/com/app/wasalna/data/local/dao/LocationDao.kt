package com.app.wasalna.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Update
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.wasalna.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity): Long

    // Phase 10-Fix: rename support for My Places (Update CRUD).
    @Update
    suspend fun updateLocation(location: LocationEntity)

    @Delete
    suspend fun deleteLocation(location: LocationEntity)

    // Phase 10.4.1: duplicate detection (same name OR nearby coords).
    @Query("SELECT * FROM locations WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun findByName(name: String): LocationEntity?

    @Query(
        "SELECT * FROM locations WHERE " +
            "ABS(latitude - :lat) < :tolerance AND ABS(longitude - :lng) < :tolerance LIMIT 1",
    )
    suspend fun findNear(lat: Double, lng: Double, tolerance: Double): LocationEntity?

    @Query("SELECT * FROM locations ORDER BY id DESC")
    fun getAllLocations(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE isFavorite = 1 ORDER BY id DESC")
    fun getFavoriteLocations(): Flow<List<LocationEntity>>
}
