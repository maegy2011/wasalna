package com.app.wasalna.domain.model

data class Location(
    val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isFavorite: Boolean = false,
)
