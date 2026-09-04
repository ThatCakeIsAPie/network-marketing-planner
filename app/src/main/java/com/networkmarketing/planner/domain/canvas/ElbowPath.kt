package com.networkmarketing.planner.domain.canvas

/**
 * Faleth-style orthogonal connector: mid-X elbow (horizontal, vertical, horizontal).
 * Ports here are top/bottom instead of Faleth's left/right, but the path math is the same.
 */
data class WorldPoint(val x: Float, val y: Float)

object ElbowPath {
    fun midX(from: WorldPoint, to: WorldPoint): List<WorldPoint> {
        val midX = (from.x + to.x) / 2f
        return listOf(
            from,
            WorldPoint(midX, from.y),
            WorldPoint(midX, to.y),
            to,
        )
    }
}
