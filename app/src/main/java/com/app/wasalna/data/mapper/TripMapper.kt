package com.app.wasalna.data.mapper

import com.app.wasalna.data.local.entity.TripHistoryEntity
import com.app.wasalna.domain.model.TransportMode
import com.app.wasalna.domain.model.TripHistory

/** Phase 10.4.2.1: trip entity ↔ domain mapping. */
fun TripHistory.toEntity(): TripHistoryEntity = TripHistoryEntity(
    id = id,
    locationName = locationName,
    destLat = destLat,
    destLng = destLng,
    startTime = startTime,
    endTime = endTime,
    status = status,
    transportMode = transportMode.name,
    etaMinutes = etaMinutes,
)

fun TripHistoryEntity.toDomain(): TripHistory = TripHistory(
    id = id,
    locationName = locationName,
    destLat = destLat,
    destLng = destLng,
    startTime = startTime,
    endTime = endTime,
    status = status,
    transportMode = TransportMode.fromName(transportMode),
    etaMinutes = etaMinutes,
)
