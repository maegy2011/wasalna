package com.app.wasalna.domain.repository

import com.app.wasalna.domain.model.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {

    fun getAllLocations(): Flow<List<Location>>

    fun getFavoriteLocations(): Flow<List<Location>>

    suspend fun addLocation(location: Location): Long

    /** Phase 10.4.1: true when a same-named or nearby place already exists. */
    suspend fun isDuplicate(name: String, lat: Double, lng: Double): Boolean

    suspend fun updateLocation(location: Location)

    suspend fun removeLocation(location: Location)
}
