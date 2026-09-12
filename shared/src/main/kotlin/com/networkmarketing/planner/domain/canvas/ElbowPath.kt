package com.networkmarketing.planner.domain.canvas

/**
 * Straight graph edges between circle rims (Obsidian / tldraw style).
 * [ElbowPath.midX] remains for legacy tests; the Map/Plan canvas uses [straight].
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

    fun straight(from: WorldPoint, to: WorldPoint): List<WorldPoint> = listOf(from, to)
}
