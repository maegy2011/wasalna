package com.app.wasalna.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phase 10.4.3.7 (Part 2): GPS breadcrumbs — the actual travelled path,
 * one row per location fix, drawn later over the planned route.
 */
@Entity(
    tableName = "trip_route",
    foreignKeys = [
        ForeignKey(
            entity = TripHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class TripRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
)
