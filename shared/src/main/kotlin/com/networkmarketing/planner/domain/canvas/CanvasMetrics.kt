package com.networkmarketing.planner.domain.canvas

import kotlin.math.hypot
import kotlin.math.round

object CanvasMetrics {
    const val GRID = 20f
    /** Circle radius in world dp (tldraw / Obsidian-style graph nodes). */
    const val NODE_RADIUS = 56f
    const val NODE_DIAMETER = NODE_RADIUS * 2f
    const val GAP_X = 48f
    const val GAP_Y = 48f
    const val WORLD_WIDTH = 4200f
    const val WORLD_HEIGHT = 3200f
    /** How long (ms) to dwell on empty before detach is armed. */
    const val DETACH_DWELL_MS = 400L

    /** @deprecated Prefer [NODE_DIAMETER]; kept for callers during the circle migration. */
    const val NODE_WIDTH = NODE_DIAMETER
    /** @deprecated Circles use [NODE_DIAMETER]; height equals diameter. */
    const val NODE_HEIGHT = NODE_DIAMETER
    const val NODE_HEIGHT_COUPLE = NODE_DIAMETER

    fun nodeHeight(@Suppress("UNUSED_PARAMETER") couple: Boolean): Float = NODE_DIAMETER

    fun snap(value: Float): Float = round(value / GRID) * GRID

    /** Snap a circle-center point into the world. */
    fun snapPoint(x: Float, y: Float): Pair<Float, Float> =
        snap(x).coerceIn(NODE_RADIUS, WORLD_WIDTH - NODE_RADIUS) to
            snap(y).coerceIn(NODE_RADIUS, WORLD_HEIGHT - NODE_RADIUS)

    /**
     * Rim point on the circle at [center] facing toward [toward].
     * If centers coincide, returns [center].
     */
    fun rimPoint(centerX: Float, centerY: Float, towardX: Float, towardY: Float): Pair<Float, Float> {
        val dx = towardX - centerX
        val dy = towardY - centerY
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < 1e-3f) return centerX to centerY
        val ux = dx / len
        val uy = dy / len
        return (centerX + ux * NODE_RADIUS) to (centerY + uy * NODE_RADIUS)
    }
}
