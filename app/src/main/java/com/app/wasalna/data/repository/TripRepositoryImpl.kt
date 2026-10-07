package com.app.wasalna.data.repository

import com.app.wasalna.data.local.dao.TripDao
import com.app.wasalna.data.local.entity.TripHistoryEntity
import com.app.wasalna.data.local.entity.TripStatus
import com.app.wasalna.data.mapper.toDomain
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.domain.model.TripHistory
import com.app.wasalna.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Phase 10.4.2.1: trip persistence used by the service, Home and history. */
@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripDao: TripDao,
) : TripRepository {

    override fun activeTrip(): Flow<TripHistory?> =
        tripDao.activeTrip().map { it?.toDomain() }

    override fun pastTrips(): Flow<List<TripHistory>> =
        tripDao.pastTrips().map { list -> list.map { it.toDomain() } }

    override fun pastCount(): Flow<Int> = tripDao.pastCount()

    override fun totalEtaMinutes(): Flow<Int> = tripDao.totalEtaMinutes()

    override suspend fun startTrip(
        name: String,
        lat: Double,
        lng: Double,
        mode: TransportMode,
    ): Long {
        val now = System.currentTimeMillis()
        tripDao.closeOpenTrips(now)
        return tripDao.insertTrip(
            TripHistoryEntity(
                locationName = name,
                destLat = lat,
                destLng = lng,
                startTime = now,
                status = TripStatus.ACTIVE,
                transportMode = mode.name,
            ),
        )
    }

    override suspend fun updateEta(id: Long, etaMin: Int) {
        tripDao.updateEta(id, etaMin)
    }

    override suspend fun finishTrip(id: Long) {
        tripDao.finishTrip(id, System.currentTimeMillis())
    }

    override suspend fun closeOpenTrips() {
        tripDao.closeOpenTrips(System.currentTimeMillis())
    }
}
