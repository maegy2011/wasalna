package com.app.wasalna.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.app.wasalna.data.local.entity.TripHistoryEntity
import com.app.wasalna.data.local.entity.TripStatus
import kotlinx.coroutines.flow.Flow

/**
 * Phase 10.4.2.1 (trips history): CRUD + stats for the Home widget,
 * Trips History screen and drawer "Your Impact" section.
 */
@Dao
interface TripDao {

    @Insert
    suspend fun insertTrip(trip: TripHistoryEntity): Long

    @Query("SELECT * FROM trips WHERE status = '${TripStatus.ACTIVE}' ORDER BY startTime DESC LIMIT 1")
    fun activeTrip(): Flow<TripHistoryEntity?>

    // Phase 10.4.3.8: single-trip lookup for the route map screen.
    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun tripById(id: Long): TripHistoryEntity?

    @Query("SELECT * FROM trips WHERE status = '${TripStatus.PAST}' ORDER BY endTime DESC")
    fun pastTrips(): Flow<List<TripHistoryEntity>>

    @Query("SELECT COUNT(*) FROM trips WHERE status = '${TripStatus.PAST}'")
    fun pastCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(etaMinutes), 0) FROM trips WHERE status = '${TripStatus.PAST}'")
    fun totalEtaMinutes(): Flow<Int>

    @Query("UPDATE trips SET etaMinutes = :etaMin WHERE id = :id")
    suspend fun updateEta(id: Long, etaMin: Int)

    @Query("UPDATE trips SET status = '${TripStatus.PAST}', endTime = :endTime WHERE id = :id")
    suspend fun finishTrip(id: Long, endTime: Long)

    @Query("UPDATE trips SET status = '${TripStatus.PAST}', endTime = :endTime WHERE status = '${TripStatus.ACTIVE}'")
    suspend fun closeOpenTrips(endTime: Long)
}
