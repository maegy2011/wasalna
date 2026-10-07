package com.app.wasalna.utils

/**
 * Phase 10.4.1: trial countdown text shared by the drawer header and the
 * paywall. Returns null when there is no active trial at [now].
 */
fun trialRemainingText(endMillis: Long, now: Long = System.currentTimeMillis()): String? {
    if (endMillis <= 0L || now >= endMillis) return null
    val remaining = endMillis - now
    val days = (remaining / (24L * 60L * 60L * 1000L)).toInt()
    val hrs = ((remaining % (24L * 60L * 60L * 1000L)) / (60L * 60L * 1000L)).toInt()
    return "Ends in $days days $hrs hrs"
}

/** Full plan line for headers: trial countdown or plain plan name. */
fun planLabel(isPremium: Boolean, trialEndMillis: Long, now: Long = System.currentTimeMillis()): String {
    if (!isPremium) return "Plan: The Ride (Free)"
    val remaining = trialRemainingText(trialEndMillis, now)
    return if (remaining != null) "Plan: First Class (Trial) - $remaining"
    else "Plan: First Class ✦"
}
