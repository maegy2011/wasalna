package com.app.wasalna.data.routing

import com.graphhopper.GraphHopper
import com.graphhopper.json.Statement
import com.graphhopper.routing.WeightingFactory
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.routing.ev.VehicleSpeed
import com.graphhopper.util.CustomModel
import com.graphhopper.config.Profile
import com.graphhopper.util.PMap

/**
 * Phase 10.4.3.9 (Vela approach, fix #2 — Janino-free weighting).
 *
 * `graphhopper-core` 11.x resolves every profile through `CustomWeighting`,
 * which Janino-subclasses at runtime — and runtime Java compilation fails on
 * ART (slow at best, fatal at worst). This factory intercepts our offline
 * profile and returns [KmhTimeWeighting] built directly from the
 * `car_average_speed` encoded value: pure JVM math, zero code generation.
 * (NOT `SpeedWeighting`: it treats the km/h value as m/s and understates
 * every ETA by exactly 3.6x — proven against `CustomWeighting.SPEED_CONV`.)
 * Both `speed` and `custom` names are intercepted: the device must declare
 * the SAME profile the graph was baked with (GraphHopper compares the
 * stored profile fingerprint at load), while the factory guarantees Janino
 * is never invoked for either. Anything else fails fast.
 */
class JaninoFreeWeightingFactory(
    private val speedEncProvider: () -> com.graphhopper.routing.ev.DecimalEncodedValue,
) : WeightingFactory {

    override fun createWeighting(
        profile: Profile,
        hints: PMap,
        disableTurnCosts: Boolean,
    ): Weighting {
        val w = profile.weighting
        if (!SPEED_WEIGHTING.equals(w, ignoreCase = true) &&
            !CUSTOM_WEIGHTING.equals(w, ignoreCase = true)
        ) {
            throw IllegalArgumentException(
                "Offline engine supports '$SPEED_WEIGHTING'/'$CUSTOM_WEIGHTING' " +
                    "weightings only (requested '$w'); Janino-backed custom " +
                    "weightings are disabled on Android.",
            )
        }
        // No turn costs in the offline car profile.
        return KmhTimeWeighting(speedEncProvider())
    }

    companion object {
        /** Weighting key our offline profiles must declare. */
        const val SPEED_WEIGHTING = "speed"

        /** Also intercepted: baked graphs declare this (factory still bypasses Janino). */
        const val CUSTOM_WEIGHTING = "custom"

        /**
         * Minimal fastest model, byte-identical to the server-side bake so
         * the stored profile fingerprint matches at load. Never compiled —
         * the factory above resolves it to [KmhTimeWeighting].
         */
        fun fastestModel(): CustomModel = CustomModel()
            .addToSpeed(Statement.If("true", Statement.Op.LIMIT, "car_average_speed"))
    }
}

/**
 * Phase 10.4.3.9: GraphHopper subclass whose router (and CH preparation —
 * same factory feeds both) never touches Janino. Drop-in replacement for
 * `GraphHopper()` wherever the on-device engine runs.
 */
class NoJaninoGraphHopper : GraphHopper() {

    override fun createWeightingFactory(): WeightingFactory =
        JaninoFreeWeightingFactory {
            encodingManager.getDecimalEncodedValue(VehicleSpeed.key("car"))
        }
}
