package com.app.wasalna.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Phase 10.4.2.1 (trips history): one row per tracked trip.
 * ACTIVE = currently tracked (at most one open); PAST = finished
 * (reached or stopped). SAVED places live in [LocationEntity].
 */
@Entity(tableName = "trips")
data class TripHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val locationName: String,
    val destLat: Double,
    val destLng: Double,
    val startTime: Long,
    val endTime: Long? = null,
    val status: String = TripStatus.ACTIVE,
    val transportMode: String = "CAR",
    val etaMinutes: Int? = null,
)

object TripStatus {
    const val ACTIVE = "ACTIVE"
    const val PAST = "PAST"
}
