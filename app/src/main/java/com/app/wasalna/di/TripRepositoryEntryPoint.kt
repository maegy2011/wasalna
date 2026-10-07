package com.app.wasalna.di

import com.app.wasalna.domain.repository.TripRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Phase 10.4.2.1: TrackingService is a plain Service (not @AndroidEntryPoint),
 * so it reaches the singleton TripRepository through this entry point.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TripRepositoryEntryPoint {
    fun tripRepository(): TripRepository
}
