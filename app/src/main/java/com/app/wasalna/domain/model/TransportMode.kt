package com.app.wasalna.domain.model

/**
 * Phase 10.4.0: user-selected transport mode for the Vela-style ETA.
 * Car and Train both route on the OSRM `driving` profile (train as a
 * proxy); Walk uses the `foot` profile.
 * Phase 10.4.2: moved to domain so the tracking service, view models
 * and UI share one canonical definition (with notification icons).
 */
enum class TransportMode(val osrmProfile: String) {
    WALK("foot"),
    CAR("driving"),
    TRAIN("driving");

    /** Emoji shown next to the ETA in UI + notification. */
    val icon: String
        get() = when (this) {
            WALK -> "🚶"
            CAR -> "🚗"
            TRAIN -> "🚆"
        }

    companion object {
        fun fromName(name: String?): TransportMode =
            runCatching { valueOf(name ?: "") }.getOrDefault(CAR)
    }
}
