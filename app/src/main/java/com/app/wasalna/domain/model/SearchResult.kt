package com.app.wasalna.domain.model

/**
 * Phase 9-3-1-fix-1: unified search hit — the UI never cares which engine
 * produced it. Hits carry display name + lat/lng directly.
 */
data class SearchResult(
    val name: String,
    val lat: Double?,
    val lng: Double?,
)
