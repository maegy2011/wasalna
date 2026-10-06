package com.app.wasalna.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Phase 1 module — DataStore provision moved to [DataStoreModule]
 * (single `wasalna_settings` file) to avoid duplicate bindings.
 * App-wide singletons land here in later phases.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
