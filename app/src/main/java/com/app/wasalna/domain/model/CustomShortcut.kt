package com.app.wasalna.domain.model

/** Maximum premium custom shortcuts per user (user order Oct 2026). */
const val MAX_CUSTOM_SHORTCUTS = 5

/**
 * Premium custom shortcut (user order Oct 2026): a user-named quick
 * place beside Home & Work — tap starts a quick trip, the pencil (or
 * long-press) opens the set-location dialog. lat/lng null = unset.
 */
data class CustomShortcut(
    val name: String,
    val lat: Double?,
    val lng: Double?,
    val address: String,
)
