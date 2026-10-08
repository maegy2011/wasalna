package com.app.wasalna.data.routing

import com.graphhopper.routing.ev.DecimalEncodedValue
import com.graphhopper.routing.weighting.TurnCostProvider
import com.graphhopper.routing.weighting.Weighting
import com.graphhopper.util.EdgeIteratorState

/**
 * Phase 10.4.3.9: Janino-free time weighting with CORRECT units.
 *
 * `car_average_speed` stores km/h (proven: `CustomWeighting` multiplies by
 * `SPEED_CONV = 3.6`); `SpeedWeighting` treats the raw value as m/s, which
 * understates every ETA by exactly 3.6x. This weighting is the same one-line
 * math with the conversion done right — pure JVM, zero code generation —
 * and matches `CustomWeighting` over a minimal fastest model exactly.
 */
class KmhTimeWeighting(
    private val speedEnc: DecimalEncodedValue,
) : Weighting {

    override fun calcEdgeWeight(edgeState: EdgeIteratorState, reverse: Boolean): Double {
        val kmh = if (reverse) edgeState.getReverse(speedEnc) else edgeState.get(speedEnc)
        if (kmh <= 0) return Double.POSITIVE_INFINITY
        return edgeState.distance / (kmh / 3.6)
    }

    override fun calcEdgeMillis(edgeState: EdgeIteratorState, reverse: Boolean): Long =
        (1000 * calcEdgeWeight(edgeState, reverse)).toLong()

    override fun calcMinWeightPerDistance(): Double =
        3.6 / speedEnc.maxStorableDecimal

    override fun calcTurnWeight(inEdge: Int, viaNode: Int, outEdge: Int): Double = 0.0

    override fun calcTurnMillis(inEdge: Int, viaNode: Int, outEdge: Int): Long = 0L

    override fun hasTurnCosts(): Boolean = false

    override fun getName(): String = "kmh-time"
}
