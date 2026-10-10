package com.app.wasalna.domain.model

/**
 * DYNAMIC WAKE-UP (التنبيه المرتبط بالزحام) — alarm trigger mode.
 *
 * - [DISTANCE]: legacy behavior — fire when straight-line/adaptive distance
 *   drops below the trigger radius (default, unchanged).
 * - [TIME]: fire when live ETA (remaining distance ÷ smoothed speed)
 *   drops below the user-chosen minutes-before-arrival.
 */
enum class AlarmMode {
    DISTANCE,
    TIME;

    companion object {
        fun fromName(name: String?): AlarmMode =
            when (name?.trim()?.uppercase()) {
                TIME.name -> TIME
                else -> DISTANCE
            }
    }
}
