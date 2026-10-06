package com.app.wasalna.data.mapper

import com.app.wasalna.data.local.entity.LocationEntity
import com.app.wasalna.domain.model.Location

fun Location.toEntity(): LocationEntity = LocationEntity(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    isFavorite = isFavorite,
)

fun LocationEntity.toDomain(): Location = Location(
    id = id,
    name = name,
    latitude = latitude,
    longitude = longitude,
    isFavorite = isFavorite,
)
