package com.app.wasalna.domain.model

/**
 * Phase 9-3-1-fix-1: unified search hit — the UI never cares which engine
 * produced it. OSM hits carry lat/lng directly; Google hits carry a
 * placeId resolved via FetchPlaceRequest on click.
 */
data class SearchResult(
    val name: String,
    val lat: Double?,
    val lng: Double?,
    val googlePlaceId: String? = null,
)
