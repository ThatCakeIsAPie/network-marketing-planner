package com.networkmarketing.planner.domain.canvas

import com.networkmarketing.planner.domain.model.OrgNode
import com.networkmarketing.planner.domain.model.OrgSnapshot
import com.networkmarketing.planner.domain.model.StructureKind

/**
 * Simple tidy-tree layout so sample (and auto-layout) orgs land as a navigable graph.
 * Positions are circle centers.
 */
object TreeLayout {
    fun applyPositions(snapshot: OrgSnapshot, kind: StructureKind): List<OrgNode> {
        val ofKind = snapshot.nodes(kind)
        if (ofKind.isEmpty()) return emptyList()
        val positions = positions(snapshot, kind)
        return ofKind.map { node ->
            val pos = positions[node.id] ?: (node.canvasX to node.canvasY)
            val snapped = CanvasMetrics.snapPoint(pos.first, pos.second)
            node.copy(canvasX = snapped.first, canvasY = snapped.second)
        }
    }

    fun positions(snapshot: OrgSnapshot, kind: StructureKind): Map<String, Pair<Float, Float>> {
        val ofKind = snapshot.nodes(kind)
        val root = snapshot.root(kind)
        val widths = mutableMapOf<String, Float>()
        fun widthOf(id: String): Float {
            widths[id]?.let { return it }
            val kids = snapshot.children(id)
            val w = if (kids.isEmpty()) {
                CanvasMetrics.NODE_DIAMETER + CanvasMetrics.GAP_X
            } else {
                kids.sumOf { widthOf(it.id).toDouble() }.toFloat()
                    .coerceAtLeast(CanvasMetrics.NODE_DIAMETER + CanvasMetrics.GAP_X)
            }
            widths[id] = w
            return w
        }
        val pos = mutableMapOf<String, Pair<Float, Float>>()
        if (root != null) {
            widthOf(root.id)
            fun place(id: String, left: Float, y: Float) {
                val w = widths.getValue(id)
                pos[id] = (left + w / 2f) to y
                var childLeft = left
                for (child in snapshot.children(id)) {
                    val cw = widthOf(child.id)
                    place(child.id, childLeft, y + CanvasMetrics.NODE_DIAMETER + CanvasMetrics.GAP_Y)
                    childLeft += cw
                }
            }
            place(root.id, 80f, 80f + CanvasMetrics.NODE_RADIUS)
        }
        var extraX = 80f + CanvasMetrics.NODE_RADIUS
        val extraY = (pos.values.maxOfOrNull { it.second } ?: 80f) +
            CanvasMetrics.NODE_DIAMETER + CanvasMetrics.GAP_Y * 2
        ofKind.filter { it.id !in pos }.forEach { node ->
            pos[node.id] = extraX to extraY
            extraX += CanvasMetrics.NODE_DIAMETER + CanvasMetrics.GAP_X
        }
        return pos
    }
}
