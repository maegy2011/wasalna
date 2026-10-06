package com.app.wasalna.data.repository

import com.app.wasalna.data.local.dao.LocationDao
import com.app.wasalna.data.mapper.toDomain
import com.app.wasalna.data.mapper.toEntity
import com.app.wasalna.domain.model.Location
import com.app.wasalna.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao,
) : LocationRepository {

    override fun getAllLocations(): Flow<List<Location>> =
        locationDao.getAllLocations().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getFavoriteLocations(): Flow<List<Location>> =
        locationDao.getFavoriteLocations().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun addLocation(location: Location): Long =
        locationDao.insertLocation(location.toEntity())

    override suspend fun removeLocation(location: Location) {
        locationDao.deleteLocation(location.toEntity())
    }
}
