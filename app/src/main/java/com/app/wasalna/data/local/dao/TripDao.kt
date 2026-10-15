package com.app.wasalna.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    // Sim quarantine (Oct 2026): every read path excludes simulated
    // rows — history, counts, ETA sums and the active-trip slot.
    @Query("SELECT * FROM trips WHERE status = '${TripStatus.ACTIVE}' AND isSimulated = 0 ORDER BY startTime DESC LIMIT 1")
    fun activeTrip(): Flow<TripHistoryEntity?>

    // Phase 10.4.3.8: single-trip lookup for the route map screen.
    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun tripById(id: Long): TripHistoryEntity?

    @Query("SELECT * FROM trips WHERE status = '${TripStatus.PAST}' AND isSimulated = 0 ORDER BY endTime DESC")
    fun pastTrips(): Flow<List<TripHistoryEntity>>

    @Query("SELECT COUNT(*) FROM trips WHERE status = '${TripStatus.PAST}' AND isSimulated = 0")
    fun pastCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(etaMinutes), 0) FROM trips WHERE status = '${TripStatus.PAST}' AND isSimulated = 0")
    fun totalEtaMinutes(): Flow<Int>

    @Query("UPDATE trips SET etaMinutes = :etaMin WHERE id = :id")
    suspend fun updateEta(id: Long, etaMin: Int)

    @Query("UPDATE trips SET status = '${TripStatus.PAST}', endTime = :endTime WHERE id = :id")
    suspend fun finishTrip(id: Long, endTime: Long)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    @Query("UPDATE trips SET status = '${TripStatus.PAST}', endTime = :endTime WHERE status = '${TripStatus.ACTIVE}'")
    suspend fun closeOpenTrips(endTime: Long)

    // Atomic close-and-insert: closes stale ACTIVE trips and opens a new one
    // in a single transaction so a process death between the two operations
    // cannot leave the service with no active trip row.
    @Transaction
    suspend fun closeAndStartTrip(
        name: String,
        lat: Double,
        lng: Double,
        startTime: Long,
        endTime: Long,
        mode: String,
        // Sim quarantine: callers must pass true for simulated trips so
        // the row is born quarantined even before the DAO filters apply.
        isSimulated: Boolean = false,
    ): Long {
        closeOpenTrips(endTime)
        return insertTrip(
            TripHistoryEntity(
                locationName = name,
                destLat = lat,
                destLng = lng,
                startTime = startTime,
                status = TripStatus.ACTIVE,
                transportMode = mode,
                isSimulated = isSimulated,
            ),
        )
    }
}
