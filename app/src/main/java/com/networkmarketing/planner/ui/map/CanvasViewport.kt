package com.networkmarketing.planner.ui.map

import androidx.compose.ui.geometry.Offset
import com.networkmarketing.planner.domain.canvas.CanvasMetrics
import com.networkmarketing.planner.domain.model.OrgNode
import kotlin.math.min

data class CanvasViewport(
    val panX: Float = 40f,
    val panY: Float = 24f,
    val zoom: Float = 0.82f,
) {
    fun panBy(dx: Float, dy: Float) = copy(panX = panX + dx, panY = panY + dy)

    fun zoomBy(factor: Float, pivot: Offset): CanvasViewport =
        withZoom((zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM), pivot)

    fun withZoom(newZoom: Float, pivot: Offset): CanvasViewport {
        val z = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (z == zoom) return this
        val worldX = (pivot.x - panX) / zoom
        val worldY = (pivot.y - panY) / zoom
        return copy(
            zoom = z,
            panX = pivot.x - worldX * z,
            panY = pivot.y - worldY * z,
        )
    }

    fun worldToScreen(xDp: Float, yDp: Float, density: Float): Offset =
        Offset(xDp * density * zoom + panX, yDp * density * zoom + panY)

    fun screenToWorld(x: Float, y: Float, density: Float): Offset =
        Offset((x - panX) / (zoom * density), (y - panY) / (zoom * density))

    fun visibleCenter(viewWidth: Float, viewHeight: Float, density: Float): Offset =
        screenToWorld(viewWidth / 2f, viewHeight / 2f, density)

    companion object {
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 2.0f

        fun fit(
            nodes: List<OrgNode>,
            coupleOf: (OrgNode) -> Boolean,
            viewWidth: Float,
            viewHeight: Float,
            density: Float,
        ): CanvasViewport {
            if (nodes.isEmpty() || viewWidth <= 0f || viewHeight <= 0f) return CanvasViewport()
            val minX = nodes.minOf { it.canvasX } - 32f
            val minY = nodes.minOf { it.canvasY } - 32f
            val maxX = nodes.maxOf { it.canvasX + CanvasMetrics.NODE_WIDTH } + 32f
            val maxY = nodes.maxOf { it.canvasY + CanvasMetrics.nodeHeight(coupleOf(it)) } + 32f
            val worldW = ((maxX - minX) * density).coerceAtLeast(1f)
            val worldH = ((maxY - minY) * density).coerceAtLeast(1f)
            val zoom = min(viewWidth / worldW, viewHeight / worldH).coerceIn(MIN_ZOOM, 1.15f)
            val cx = ((minX + maxX) / 2f) * density * zoom
            val cy = ((minY + maxY) / 2f) * density * zoom
            return CanvasViewport(
                panX = viewWidth / 2f - cx,
                panY = viewHeight / 2f - cy,
                zoom = zoom,
            )
        }
    }
}
