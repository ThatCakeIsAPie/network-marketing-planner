package com.networkmarketing.planner.domain.canvas

import kotlin.math.round

object CanvasMetrics {
    const val GRID = 20f
    const val NODE_WIDTH = 220f
    const val NODE_HEIGHT = 140f
    const val NODE_HEIGHT_COUPLE = 160f
    const val DOCK = 16f
    const val DOCK_HIT = 28f
    const val GAP_X = 40f
    const val GAP_Y = 100f
    const val WORLD_WIDTH = 4200f
    const val WORLD_HEIGHT = 3200f

    fun nodeHeight(couple: Boolean): Float = if (couple) NODE_HEIGHT_COUPLE else NODE_HEIGHT

    fun snap(value: Float): Float = round(value / GRID) * GRID

    fun snapPoint(x: Float, y: Float): Pair<Float, Float> =
        snap(x).coerceIn(0f, WORLD_WIDTH - NODE_WIDTH) to
            snap(y).coerceIn(0f, WORLD_HEIGHT - NODE_HEIGHT_COUPLE)

    fun uplineDock(x: Float, y: Float): Pair<Float, Float> =
        (x + NODE_WIDTH / 2f) to (y + DOCK / 2f)

    fun downlinePortCount(frontline: Int): Int = frontline + 1

    fun downlineDock(x: Float, y: Float, height: Float, index: Int, count: Int): Pair<Float, Float> {
        val safeCount = count.coerceAtLeast(1)
        val inset = 16f
        val usable = NODE_WIDTH - inset * 2f
        val t = if (safeCount == 1) 0.5f else index.toFloat() / (safeCount - 1).toFloat()
        return (x + inset + t * usable) to (y + height - DOCK / 2f)
    }
}
