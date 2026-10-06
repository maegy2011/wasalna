package com.app.wasalna.domain.repository

import com.app.wasalna.domain.model.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {

    fun getAllLocations(): Flow<List<Location>>

    fun getFavoriteLocations(): Flow<List<Location>>

    suspend fun addLocation(location: Location): Long

    suspend fun removeLocation(location: Location)
}
