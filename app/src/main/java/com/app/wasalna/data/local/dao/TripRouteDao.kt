package com.app.wasalna.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.app.wasalna.data.local.entity.TripRouteEntity
import kotlinx.coroutines.flow.Flow

/**
 * Phase 10.4.3.7 (Part 2): breadcrumb persistence for the travelled path.
 */
@Dao
interface TripRouteDao {

    @Insert
    suspend fun insertPoint(point: TripRouteEntity)

    @Query("SELECT * FROM trip_route WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun pointsForTrip(tripId: Long): Flow<List<TripRouteEntity>>

    @Query("DELETE FROM trip_route WHERE tripId = :tripId")
    suspend fun clearTrip(tripId: Long)
}
